package com.ivy.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.LoanRecordType
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.LoanRecordDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.db.entity.LoanRecordEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.LoanType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Figures for the Home quick-access row.
 *
 * The net worth shown here follows exactly the accounting convention of
 * NetWorthCalculator (app module, phase 2): account balances are pure sums
 * of transactions, every loan adjusts the total by what is left of it,
 * amounts are grouped per currency and never converted, and the base
 * currency wins when several exist. The calculator itself cannot be used
 * from this module (the app module depends on feature modules, not the
 * other way round), so the totals are recomputed here from the same Room
 * entities — keep the two conventions in sync. The authoritative,
 * detailed computation stays on the Net worth screen.
 *
 * The subscriptions yearly cost is deliberately NOT recomputed here: it
 * needs the detection engine (SubscriptionDetector, app module), and a
 * second implementation would silently drift from the Subscriptions
 * screen. The row therefore shows a plain label for subscriptions; the
 * real figure is one tap away, on its screen and on the Menu.
 */
@HiltViewModel
class QuickAccessViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val loanDao: LoanDao,
    private val loanRecordDao: LoanRecordDao,
    private val settingsDao: SettingsDao,
) : ViewModel() {

    private val _netWorthText = MutableStateFlow<String?>(null)
    val netWorthText: StateFlow<String?> = _netWorthText

    init {
        viewModelScope.launch {
            _netWorthText.value = withContext(Dispatchers.IO) {
                computeNetWorth()?.let { (amount, currency) -> formatMoney(amount, currency) }
            }
        }
    }

    /** Net worth of the base currency (or of the first currency found). */
    private suspend fun computeNetWorth(): Pair<Double, String>? {
        val baseCurrency = settingsDao.findFirst().currency
        val accounts = accountDao.findAll()
        val transactions = transactionDao.findAll()
        val loans = loanDao.findAll()
        val records = loanRecordDao.findAll()

        val accountsById = accounts.associateBy { it.id }

        fun currencyOf(account: AccountEntity): String = account.currency ?: baseCurrency

        val perCurrency = mutableMapOf<String, Double>()
        accounts
            .filter { it.includeInBalance && !it.isDeleted }
            .forEach { account ->
                val currency = currencyOf(account)
                perCurrency[currency] =
                    (perCurrency[currency] ?: 0.0) + accountBalance(account.id, transactions)
            }

        val recordsByLoan = records.groupBy { it.loanId }
        loans
            .filter { !it.isDeleted }
            .forEach { loan ->
                val remaining = loanRemaining(loan, recordsByLoan[loan.id].orEmpty())
                if (remaining <= REMAINING_EPSILON) return@forEach
                val currency = loan.accountId
                    ?.let { accountsById[it] }
                    ?.let { currencyOf(it) }
                    ?: baseCurrency
                val signed = if (loan.type == LoanType.LEND) remaining else -remaining
                perCurrency[currency] = (perCurrency[currency] ?: 0.0) + signed
            }

        val chosen = perCurrency.entries.firstOrNull { it.key == baseCurrency }
            ?: perCurrency.entries.firstOrNull()
            ?: return null
        return chosen.value to chosen.key
    }

    private companion object {
        /** Below this, a loan remainder is rounding dust (same as NetWorthCalculator). */
        const val REMAINING_EPSILON = 0.005
    }
}

/** Same balance rule as NetWorthCalculator.accountBalance — keep in sync. */
private fun accountBalance(accountId: UUID, transactions: List<TransactionEntity>): Double =
    transactions.fold(0.0) { total, transaction ->
        total + when (transaction.type) {
            TransactionType.INCOME ->
                if (transaction.accountId == accountId) transaction.amount else 0.0

            TransactionType.EXPENSE ->
                if (transaction.accountId == accountId) -transaction.amount else 0.0

            TransactionType.TRANSFER -> {
                var delta = 0.0
                if (transaction.accountId == accountId) delta -= transaction.amount
                if (transaction.toAccountId == accountId) {
                    delta += transaction.toAmount ?: transaction.amount
                }
                delta
            }
        }
    }

/** Same remaining rule as NetWorthCalculator.loanRemaining — keep in sync. */
private fun loanRemaining(loan: LoanEntity, records: List<LoanRecordEntity>): Double {
    var paid = 0.0
    var total = loan.amount
    for (record in records) {
        if (record.loanId != loan.id || record.interest) continue
        val amount = record.convertedAmount ?: record.amount
        when (record.loanRecordType) {
            LoanRecordType.DECREASE -> paid += amount
            LoanRecordType.INCREASE -> total += amount
        }
    }
    return (total - paid).coerceAtLeast(0.0)
}

/** Same plain money style as the HeyMoney cards ("15.99 CAD"). */
private fun formatMoney(amount: Double, currency: String?): String =
    "%,.2f %s".format(Locale.getDefault(), amount, currency.orEmpty()).trim()
