package org.abodah.heymoney.messages

import android.app.Application
import android.content.Context
import android.provider.Telephony
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.IntervalType
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.abodah.heymoney.subscriptions.SubscriptionFrequency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** The phases of the message scan screen. */
sealed interface MessageScanPhase {
    /** Explanation shown before anything is read — the scan is opt-in. */
    data object Explanation : MessageScanPhase

    /** The READ_SMS permission was refused; nothing was read. */
    data object PermissionDenied : MessageScanPhase

    data object Scanning : MessageScanPhase

    data class Results(
        val found: List<MessageSubscription>,
        /** Carrier of the SIM card, when the phone reports one. */
        val simOperator: String?,
        val accounts: List<AccountEntity>,
        /** Keys of services already followed as planned payments. */
        val trackedKeys: Set<String>,
    ) : MessageScanPhase
}

/**
 * The retrospective SMS scan: reads the inbox through the system content
 * provider, runs [MessageSubscriptionMatcher] over it on the device, and
 * lists the carriers and subscriptions it found. Nothing is written
 * anywhere by the scan itself; following a service is an explicit tap
 * that creates a normal planned payment rule (the same object the
 * subscriptions screen and the reminders already use), never a fake
 * transaction.
 */
@HiltViewModel
class MessageScanViewModel @Inject constructor(
    application: Application,
    private val accountDao: AccountDao,
    private val plannedRuleDao: PlannedPaymentRuleDao,
    private val plannedRuleWriter: WritePlannedPaymentRuleDao,
) : AndroidViewModel(application) {

    private val _phase = MutableStateFlow<MessageScanPhase>(MessageScanPhase.Explanation)
    val phase: StateFlow<MessageScanPhase> = _phase

    private val _selectedAccountId = MutableStateFlow<java.util.UUID?>(null)
    val selectedAccountId: StateFlow<java.util.UUID?> = _selectedAccountId

    fun onPermissionDenied() {
        _phase.value = MessageScanPhase.PermissionDenied
    }

    fun selectAccount(id: java.util.UUID) {
        _selectedAccountId.value = id
    }

    /** Reads the SMS inbox and analyses it. Call only with READ_SMS granted. */
    fun scan() {
        if (_phase.value is MessageScanPhase.Scanning) return
        _phase.value = MessageScanPhase.Scanning
        viewModelScope.launch {
            val messages = withContext(Dispatchers.IO) { readInbox() }
            val found = withContext(Dispatchers.Default) {
                MessageSubscriptionMatcher.analyse(messages)
            }
            val accounts = accountDao.findAll()
            if (_selectedAccountId.value == null) {
                _selectedAccountId.value = accounts.firstOrNull()?.id
            }
            val tracked = plannedRuleDao.findAll()
                .mapNotNull { it.title?.lowercase()?.trim() }
                .toSet()
            _phase.value = MessageScanPhase.Results(
                found = found,
                simOperator = simOperatorName(),
                accounts = accounts,
                trackedKeys = tracked,
            )
        }
    }

    /**
     * Follows a found service: creates the planned payment rule the
     * subscription detection and the reminders already understand.
     */
    fun track(subscription: MessageSubscription) {
        val accountId = _selectedAccountId.value ?: return
        val frequency = subscription.frequency ?: SubscriptionFrequency.MONTHLY
        val (intervalType, intervalN) = when (frequency) {
            SubscriptionFrequency.WEEKLY -> IntervalType.WEEK to 1
            SubscriptionFrequency.BIWEEKLY -> IntervalType.WEEK to 2
            SubscriptionFrequency.MONTHLY -> IntervalType.MONTH to 1
            SubscriptionFrequency.QUARTERLY -> IntervalType.MONTH to 3
            SubscriptionFrequency.SEMIANNUAL -> IntervalType.MONTH to 6
            SubscriptionFrequency.YEARLY -> IntervalType.YEAR to 1
        }
        val zone = ZoneId.systemDefault()
        val last = Instant.ofEpochMilli(subscription.lastDateMillis).atZone(zone).toLocalDate()
        var next = frequency.nextAfter(last)
        val today = LocalDate.now(zone)
        while (next.isBefore(today)) next = frequency.nextAfter(next)

        viewModelScope.launch {
            plannedRuleWriter.save(
                PlannedPaymentRuleEntity(
                    startDate = next.atStartOfDay(zone).toInstant(),
                    intervalN = intervalN,
                    intervalType = intervalType,
                    oneTime = false,
                    type = TransactionType.EXPENSE,
                    accountId = accountId,
                    amount = subscription.amount ?: 0.0,
                    title = subscription.serviceName,
                    description = "HeyMoney SMS scan",
                )
            )
            val current = _phase.value
            if (current is MessageScanPhase.Results) {
                _phase.value = current.copy(
                    trackedKeys = current.trackedKeys + subscription.key
                )
            }
        }
    }

    private fun readInbox(): List<RawMessage> {
        val context = getApplication<Application>()
        val messages = mutableListOf<RawMessage>()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val address = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndex(Telephony.Sms.BODY)
                val date = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext() && messages.size < MAX_MESSAGES) {
                    messages += RawMessage(
                        sender = cursor.getString(address).orEmpty(),
                        body = cursor.getString(body).orEmpty(),
                        dateMillis = cursor.getLong(date),
                    )
                }
            }
        }
        return messages
    }

    private fun simOperatorName(): String? =
        runCatching {
            val manager = getApplication<Application>()
                .getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            manager?.simOperatorName?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private companion object {
        /** Most recent inbox messages considered — far beyond bill history. */
        const val MAX_MESSAGES = 5000
    }
}
