package org.abodah.heymoney.alerts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.db.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.abodah.heymoney.receipts.AiTokenStore
import org.abodah.heymoney.receipts.ReceiptScanner
import org.abodah.heymoney.receipts.ScanResult
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class AlertDraft(
    val alert: BankAlert,
    val accountName: String?,
    val categoryName: String?,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val store: AlertStore,
    private val saver: AlertSaver,
    private val scanner: ReceiptScanner,
    private val tokens: AiTokenStore,
) : ViewModel() {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _hasToken = MutableStateFlow(tokens.get() != null)
    val hasToken: StateFlow<Boolean> = _hasToken

    /** Reads a receipt photo and adds it to the pending list as a draft. */
    fun scanReceipt(uri: Uri) {
        viewModelScope.launch {
            _scanning.value = true
            val result = runCatching { scanner.scan(uri) }.getOrNull()
            _scanning.value = false
            _message.value = when (result) {
                is ScanResult.Found -> if (store.add(result.alert)) null else "This receipt is already in the list."
                else -> "Could not find a total on this receipt. Try a sharper, well-lit photo."
            }
        }
    }

    val pending: StateFlow<List<BankAlert>> = store.pending

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    /** Parses a message pasted by the user, the same way an incoming SMS is handled. */
    fun addPasted(text: String) {
        val alert = BankAlertParser.parse(BankAlert.Source.MANUAL, "", text)
        _message.value = when {
            alert == null -> "No amount or transaction found in this text."
            !store.add(alert) -> "This message is already in the list."
            else -> null
        }
    }

    fun dismiss(alert: BankAlert) = store.resolve(alert)

    fun confirm(alert: BankAlert) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { saver.save(alert) }
            if (saved) store.resolve(alert) else {
                _message.value = "Create an account first, then confirm this alert."
            }
        }
    }

    fun confirmAll() {
        pending.value.forEach(::confirm)
    }

    suspend fun preview(alert: BankAlert): AlertDraft = saver.preview(alert)
}

/** Turns a confirmed [BankAlert] into a saved transaction (account and category picked for it). */
class AlertSaver @Inject constructor(
    private val transactionDao: TransactionDao,
    private val writeTransactionDao: WriteTransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) {
    /** Where the alert would land, shown on the card before the user confirms. */
    suspend fun preview(alert: BankAlert): AlertDraft = withContext(Dispatchers.IO) {
        val account = pickAccount(alert)
        AlertDraft(alert, account?.name, pickCategory(alert)?.name)
    }

    suspend fun save(alert: BankAlert): Boolean {
        val account = pickAccount(alert) ?: return false
        val category = pickCategory(alert)
        writeTransactionDao.save(
            TransactionEntity(
                accountId = account.id,
                type = if (alert.isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                amount = alert.amount,
                title = alert.merchant,
                description = "${alert.source.label}${alert.sender.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}",
                dateTime = Instant.ofEpochMilli(alert.date),
                categoryId = category?.id,
                id = UUID.randomUUID()
            )
        )
        return true
    }

    // account in the alert's currency if there is one, otherwise the first account
    private suspend fun pickAccount(alert: BankAlert) = accountDao.findAll().let { accounts ->
        accounts.firstOrNull { alert.currency != null && it.currency == alert.currency }
            ?: accounts.minByOrNull { it.orderNum }
    }

    // 1) category already used for this merchant, 2) category whose name matches the wording
    private suspend fun pickCategory(alert: BankAlert) = categoryDao.findAll().let { categories ->
        val merchant = alert.merchant.trim().lowercase(Locale.ROOT)
        val remembered = transactionDao.findAll()
            .filter { it.categoryId != null && it.title?.trim()?.lowercase(Locale.ROOT) == merchant }
            .groupingBy { it.categoryId }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        categories.firstOrNull { it.id == remembered }
            ?: CategoryHints.match(alert.raw, categories.map { it.name to it })
    }
}

/** Keyword hints between bank wording and the names users give to categories. */
object CategoryHints {
    private val hints: List<Pair<List<String>, List<String>>> = listOf(
        listOf("food", "groc", "restaurant", "nourriture", "alimentation", "courses") to
            listOf("restaurant", "café", "cafe", "supermarch", "grocer", "carrefour", "pizza", "boulang"),
        listOf("transport", "car", "fuel", "voiture") to
            listOf("uber", "taxi", "bolt", "essence", "fuel", "shell", "total", "bus", "parking"),
        listOf("entertain", "fun", "loisir", "divertissement") to
            listOf("netflix", "spotify", "cinéma", "cinema", "steam", "youtube"),
        listOf("bill", "utilit", "factures", "home", "housing", "rent", "loyer") to
            listOf("loyer", "rent", "électricité", "electric", "water", "eau", "internet", "camtel", "orange", "mtn"),
        listOf("health", "santé", "sante") to listOf("pharmac", "clinic", "hospital", "médecin", "doctor"),
        listOf("salary", "salaire", "income", "revenu") to listOf("salaire", "salary"),
    )

    fun <T> match(text: String, categories: List<Pair<String, T>>): T? {
        val lower = text.lowercase(Locale.ROOT)
        hints.forEach { (categoryWords, wording) ->
            if (wording.none { lower.contains(it) }) return@forEach
            categories.firstOrNull { (name, _) ->
                categoryWords.any { name.lowercase(Locale.ROOT).contains(it) }
            }?.let { return it.second }
        }
        return null
    }
}
