package org.abodah.heymoney.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject

/** A detected subscription together with what the user decided about it. */
data class SubscriptionItem(
    val subscription: DetectedSubscription,
    val decision: SubscriptionDecision,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val accountDao: AccountDao,
    private val detector: SubscriptionDetector,
    private val store: SubscriptionStore,
    private val reminderScheduler: SubscriptionReminderScheduler,
) : ViewModel() {

    private val _detected = MutableStateFlow<List<DetectedSubscription>>(emptyList())

    private val _items = MutableStateFlow<List<SubscriptionItem>>(emptyList())
    val items: StateFlow<List<SubscriptionItem>> = _items

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        // Reminders only make sense once the feature was opened once; the
        // daily worker is unique, so scheduling again is a no-op.
        reminderScheduler.schedule()
        viewModelScope.launch {
            combine(_detected, store.decisions) { detected, decisions ->
                detected.map { subscription ->
                    SubscriptionItem(
                        subscription = subscription,
                        decision = decisions[subscription.key] ?: SubscriptionDecision()
                    )
                }
            }.collect { _items.value = it }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _detected.value = withContext(Dispatchers.IO) {
                detector.detect(
                    transactions = transactionDao.findAll(),
                    plannedRules = plannedPaymentRuleDao.findAll(),
                    accountCurrencies = accountDao.findAll().associate { it.id to it.currency },
                )
            }
            _loaded.value = true
        }
    }

    fun item(key: String): SubscriptionItem? = _items.value.firstOrNull { it.subscription.key == key }

    fun confirm(key: String) = store.setStatus(key, SubscriptionStatus.CONFIRMED)

    fun ignore(key: String) = store.setStatus(key, SubscriptionStatus.IGNORED)

    fun restore(key: String) = store.setStatus(key, null)

    fun saveNote(key: String, note: String) = store.setNote(key, note.trim())

    fun setReminderDays(key: String, days: Int) = store.setReminderDays(key, days)
}

/**
 * "Customer for how long", in whole months, from the first observed charge.
 * Used by the negotiation templates ("customer for {tenure}"); the UI turns
 * it into a localized phrase. Null when unknown or too recent to help.
 */
fun tenureMonths(firstChargeDate: LocalDate?, today: LocalDate = LocalDate.now()): Long? {
    if (firstChargeDate == null) return null
    val months = Period.between(firstChargeDate, today).toTotalMonths()
    return months.takeIf { it >= 2 }
}
