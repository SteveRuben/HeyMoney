package org.abodah.heymoney.chart

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Income and expenses of one calendar month, in one currency. */
data class MonthSummary(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
) {
    /** What the month added to (or took from) the money: income − expenses. */
    val net: Double get() = income - expense
}

/**
 * Aggregates transactions into monthly income/expense bars.
 *
 * Transactions carry no currency of their own: they are expressed in the
 * currency of their account. Just like the subscriptions screen and the net
 * worth, amounts are never converted — the chart shows one currency at a
 * time, the one the user picks among the currencies actually present. An
 * account without a currency (or a transaction whose account is gone) is
 * expressed in the base currency.
 *
 * Only real income and expenses count: transfers move money between the
 * user's own accounts, they are neither earned nor spent. Transactions
 * without a date cannot be placed in a month and are skipped.
 */
class MonthlyAggregator @Inject constructor() {

    /** Currencies that have at least one dated income or expense, base currency first. */
    fun currenciesPresent(
        transactions: List<TransactionEntity>,
        accountCurrencies: Map<UUID, String?>,
        baseCurrency: String,
    ): List<String> = transactions
        .filter { it.dateTime != null && it.type != TransactionType.TRANSFER }
        .map { effectiveCurrency(it, accountCurrencies, baseCurrency) }
        .toSet()
        .sortedWith(compareByDescending<String> { it == baseCurrency }.thenBy { it })

    /**
     * [monthCount] consecutive months ending at [endMonth] (inclusive),
     * oldest first, zero-filled where nothing happened.
     */
    fun aggregate(
        transactions: List<TransactionEntity>,
        accountCurrencies: Map<UUID, String?>,
        baseCurrency: String,
        currency: String,
        endMonth: YearMonth,
        monthCount: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthSummary> {
        val income = mutableMapOf<YearMonth, Double>()
        val expense = mutableMapOf<YearMonth, Double>()
        for (transaction in transactions) {
            val dateTime = transaction.dateTime ?: continue
            if (effectiveCurrency(transaction, accountCurrencies, baseCurrency) != currency) continue
            val month = YearMonth.from(dateTime.atZone(zone).toLocalDate())
            when (transaction.type) {
                TransactionType.INCOME -> income[month] = (income[month] ?: 0.0) + transaction.amount
                TransactionType.EXPENSE -> expense[month] = (expense[month] ?: 0.0) + transaction.amount
                TransactionType.TRANSFER -> Unit // moving money around is not earning or spending
            }
        }
        val firstMonth = endMonth.minusMonths((monthCount - 1).toLong())
        return (0 until monthCount).map { offset ->
            val month = firstMonth.plusMonths(offset.toLong())
            MonthSummary(
                month = month,
                income = income[month] ?: 0.0,
                expense = expense[month] ?: 0.0,
            )
        }
    }

    private fun effectiveCurrency(
        transaction: TransactionEntity,
        accountCurrencies: Map<UUID, String?>,
        baseCurrency: String,
    ): String = accountCurrencies[transaction.accountId] ?: baseCurrency
}
