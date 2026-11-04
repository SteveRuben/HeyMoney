package org.abodah.heymoney.networth

import com.ivy.base.model.LoanRecordType
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.db.entity.LoanRecordEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.LoanType
import java.util.UUID
import javax.inject.Inject

/** One account composing the net worth, with its all-time balance. */
data class NetWorthAccountLine(
    val name: String,
    val balance: Double,
)

/** One loan composing the net worth, with what is left of it. */
data class NetWorthLoanLine(
    val name: String,
    /** What is left to receive (LEND) or to pay back (BORROW), always >= 0. */
    val remaining: Double,
    /** True when the loan is attached to an account (it only sets its currency, see below). */
    val linkedToAccount: Boolean,
)

/**
 * The net worth in one currency. Amounts are never converted between
 * currencies: each currency gets its own independent picture.
 */
data class CurrencyNetWorth(
    val currency: String,
    val accounts: List<NetWorthAccountLine>,
    /** Loans of type LEND: money other people still owe the user. */
    val lent: List<NetWorthLoanLine>,
    /** Loans of type BORROW: money the user still has to pay back. */
    val borrowed: List<NetWorthLoanLine>,
) {
    val accountsTotal: Double get() = accounts.sumOf { it.balance }
    val lentTotal: Double get() = lent.sumOf { it.remaining }
    val borrowedTotal: Double get() = borrowed.sumOf { it.remaining }

    /** accounts + still owed to the user − still owed by the user. */
    val netWorth: Double get() = accountsTotal + lentTotal - borrowedTotal
}

/**
 * All-time balance of one account, exactly as the rest of the app defines it
 * (see AccountBalanceUseCase): income and transfers in add up, expenses and
 * transfers out subtract. A transfer counts [TransactionEntity.toAmount] on
 * the receiving side when it is set (it is the amount in that account's
 * currency) and the plain amount otherwise.
 */
fun accountBalance(accountId: UUID, transactions: List<TransactionEntity>): Double =
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

/**
 * What is left of a loan: its amount, grown by the INCREASE records and
 * reduced by the DECREASE records — the same computation the Loans screen
 * makes (LoanViewModel.calculateAmountPaidAndTotalAmount):
 *
 * - records flagged as interest are ignored: interest is a fee, not principal;
 * - a record's convertedAmount, when set, replaces its amount: it expresses
 *   the record in the loan's own currency when the record's account differs;
 * - the result is clamped at zero: an overpaid loan is paid off, it no longer
 *   composes the net worth (the Loans screen likewise files it as completed).
 */
fun loanRemaining(loan: LoanEntity, records: List<LoanRecordEntity>): Double {
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

/**
 * Builds the net worth per currency from plain Room entities.
 *
 * Accounting convention — read before changing:
 *
 * 1. An account balance is a pure sum of transactions. A loan's remaining
 *    amount is never part of any account balance, so adding it (LEND) or
 *    subtracting it (BORROW) can never double-count a balance.
 * 2. A loan linked to an account (accountId set) only borrows that account's
 *    currency (the same rule the Loans screen uses to display a loan). The
 *    link does NOT mean the loan is inside the balance: the transactions a
 *    loan optionally creates (the "create transaction" toggles in the loan
 *    dialogs) are its cash leg — borrowed money landing in the account,
 *    repayments leaving it — and they never encode the outstanding claim.
 *    Whether those toggles were on or off, the remaining amount must still
 *    adjust the net worth:
 *      - BORROW with the cash transaction: the balance holds the borrowed
 *        cash that is not repaid yet; subtracting the remaining removes
 *        exactly that part.
 *      - BORROW without it: the balance never saw the money; subtracting
 *        states the debt against the tracked assets.
 *      - LEND is the mirror image (claim vs. cash already gone/come back).
 *    This also matches the Loans screen, whose "you owe / you're owed"
 *    totals already run over every loan, linked or not.
 * 3. Only accounts the user includes in their balance (includeInBalance)
 *    count, like everywhere else in the app. An account without a currency
 *    is expressed in the base currency, and so is a loan whose account is
 *    gone.
 * 4. Loans with nothing left (remaining below half a cent of rounding dust)
 *    are left out: they no longer compose anything.
 */
class NetWorthCalculator @Inject constructor() {

    fun calculate(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        loans: List<LoanEntity>,
        records: List<LoanRecordEntity>,
        baseCurrency: String,
    ): List<CurrencyNetWorth> {
        val accountsById = accounts.associateBy { it.id }

        fun currencyOf(account: AccountEntity): String = account.currency ?: baseCurrency

        val accountLines = accounts
            .filter { it.includeInBalance && !it.isDeleted }
            .map { account ->
                currencyOf(account) to NetWorthAccountLine(
                    name = account.name,
                    balance = accountBalance(account.id, transactions),
                )
            }

        val recordsByLoan = records.groupBy { it.loanId }
        val loanLines = loans
            .filter { !it.isDeleted }
            .mapNotNull { loan ->
                val remaining = loanRemaining(loan, recordsByLoan[loan.id].orEmpty())
                if (remaining <= REMAINING_EPSILON) return@mapNotNull null
                val currency = loan.accountId
                    ?.let { accountsById[it] }
                    ?.let { currencyOf(it) }
                    ?: baseCurrency
                currency to (loan.type to NetWorthLoanLine(
                    name = loan.name,
                    remaining = remaining,
                    linkedToAccount = loan.accountId != null,
                ))
            }

        val currencies = (accountLines.map { it.first } + loanLines.map { it.first }).toSortedSet()
        return currencies.map { currency ->
            CurrencyNetWorth(
                currency = currency,
                accounts = accountLines
                    .filter { it.first == currency }
                    .map { it.second }
                    .sortedByDescending { it.balance },
                lent = loanLines
                    .filter { it.first == currency && it.second.first == LoanType.LEND }
                    .map { it.second.second }
                    .sortedByDescending { it.remaining },
                borrowed = loanLines
                    .filter { it.first == currency && it.second.first == LoanType.BORROW }
                    .map { it.second.second }
                    .sortedByDescending { it.remaining },
            )
        }.sortedWith(compareByDescending<CurrencyNetWorth> { it.currency == baseCurrency }.thenBy { it.currency })
    }

    private companion object {
        /** Below this, a loan remainder is rounding dust, not money still owed. */
        const val REMAINING_EPSILON = 0.005
    }
}
