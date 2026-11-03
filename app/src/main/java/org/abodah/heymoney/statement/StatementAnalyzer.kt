package org.abodah.heymoney.statement

import org.abodah.heymoney.subscriptions.normalizeMerchantName
import java.time.LocalDate
import kotlin.math.abs

/** Money spent at one merchant over the statement period. [total] is positive. */
data class MerchantTotal(
    /** The most frequent original wording of the merchant on the statement. */
    val name: String,
    val total: Double,
    val count: Int,
)

/** Money in and out for one category printed by the bank. [category] null = none printed. */
data class CategoryTotal(
    val category: String?,
    val totalIn: Double,
    val totalOut: Double,
)

/** Everything the analysis screen shows about one statement. Pure data. */
data class StatementAnalysis(
    val transactionCount: Int,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    /** Sum of all money in (positive). */
    val totalIn: Double,
    /** Sum of all money out, as a positive number. */
    val totalOut: Double,
    /** Fees and interest charged over the period, as a positive number. */
    val feesTotal: Double,
    val feesCount: Int,
    val topMerchants: List<MerchantTotal>,
    val byCategory: List<CategoryTotal>,
    /** Largest operations by absolute amount, biggest first. */
    val largest: List<StatementLine>,
) {
    val net: Double get() = totalIn - totalOut
}

/**
 * Turns the lines of one statement into the numbers a person actually wants
 * from it: what came in, what went out, what the bank kept in fees and
 * interest, where the money went (merchants, categories) and the biggest
 * operations. Recurring payments are *not* computed here: the analysis
 * screen feeds the same lines to the existing `SubscriptionDetector`, so
 * statement analysis and "My subscriptions" can never disagree.
 *
 * Pure functions over [StatementLine] — unit-testable off-device.
 */
object StatementAnalyzer {

    fun analyse(lines: List<StatementLine>): StatementAnalysis {
        val dated = lines.mapNotNull { it.date }
        val expenses = lines.filter { it.amount < 0 }
        val fees = expenses.filter { isFeeOrInterest(it.title) }
        return StatementAnalysis(
            transactionCount = lines.size,
            periodStart = dated.minOrNull(),
            periodEnd = dated.maxOrNull(),
            totalIn = lines.filter { it.amount > 0 }.sumOf { it.amount },
            totalOut = expenses.sumOf { -it.amount },
            feesTotal = fees.sumOf { -it.amount },
            feesCount = fees.size,
            topMerchants = topMerchants(expenses),
            byCategory = byCategory(lines),
            largest = lines.sortedByDescending { abs(it.amount) }.take(LARGEST_COUNT),
        )
    }

    /**
     * A line counts as a fee or interest charge when its wording names one.
     * Only money-out lines are considered: interest *earned* on a savings
     * account is income, not a cost, and must not inflate the fees total.
     */
    fun isFeeOrInterest(title: String): Boolean {
        val wording = title.lowercase()
        return FEE_KEYWORDS.any { wording.contains(it) }
    }

    private fun topMerchants(expenses: List<StatementLine>): List<MerchantTotal> =
        expenses
            .groupBy { normalizeMerchantName(it.title) }
            .values
            .map { group ->
                MerchantTotal(
                    name = group.groupingBy { it.title.trim() }.eachCount()
                        .maxByOrNull { it.value }!!.key,
                    total = group.sumOf { -it.amount },
                    count = group.size,
                )
            }
            .sortedByDescending { it.total }
            .take(TOP_MERCHANTS_COUNT)

    private fun byCategory(lines: List<StatementLine>): List<CategoryTotal> =
        lines
            .groupBy { it.category?.trim()?.takeIf { c -> c.isNotBlank() } }
            .map { (category, group) ->
                CategoryTotal(
                    category = category,
                    totalIn = group.filter { it.amount > 0 }.sumOf { it.amount },
                    totalOut = group.filter { it.amount < 0 }.sumOf { -it.amount },
                )
            }
            .sortedByDescending { it.totalOut }
            .take(CATEGORIES_COUNT)

    private const val TOP_MERCHANTS_COUNT = 8
    private const val CATEGORIES_COUNT = 10
    private const val LARGEST_COUNT = 5

    private val FEE_KEYWORDS = listOf(
        "fee", "fees", "frais", "interest", "interet", "intérêt",
        "agios", "penalt", "pénal", "service charge",
    )
}
