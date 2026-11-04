package org.abodah.heymoney.budget

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil

/** Income or expense, the only two kinds a monthly budget reasons about. */
enum class BudgetTxType { INCOME, EXPENSE }

/**
 * One transaction reduced to what the monthly budget needs. The ViewModel
 * builds these from Room entities (base-currency accounts only, transfers
 * left out — see [MonthlyBudgetViewModel]); the calculator itself never
 * sees Android or Room types, so it is unit-testable off-device.
 */
data class BudgetTx(
    val date: LocalDate,
    val type: BudgetTxType,
    /** Always positive; the kind is carried by [type]. */
    val amount: Double,
    val categoryId: UUID?,
)

/** What the user assigned to one category for one month (base currency). */
data class CategoryAssignment(
    val categoryId: UUID,
    val month: YearMonth,
    val amount: Double,
)

/** A savings goal on a category: reach [targetAmount] by [targetMonth]. */
data class CategoryGoal(
    val categoryId: UUID,
    val targetAmount: Double,
    val targetMonth: YearMonth,
)

/** One category's picture for the viewed month. */
data class CategoryBudgetLine(
    val categoryId: UUID,
    /** Available left over from last month (negative when last month overspent). */
    val carriedIn: Double,
    /** Assigned this month. */
    val assigned: Double,
    /** Spent this month. */
    val spent: Double,
    val goal: CategoryGoal?,
    /**
     * What to assign per month, from the viewed month on, to hit [goal] on
     * time — null without a goal, 0 once the goal is covered.
     */
    val goalMonthlyNeeded: Double?,
) {
    /** carriedIn + assigned − spent. Negative = overspent, shown honestly. */
    val available: Double get() = carriedIn + assigned - spent
}

/** The whole zero-based picture of one month. */
data class MonthlyBudget(
    val month: YearMonth,
    /** Income received in [month]. */
    val income: Double,
    /** Sum of every category's assignment for [month]. */
    val totalAssigned: Double,
    /** Expenses of [month] with no category: they belong to no line. */
    val uncategorizedSpent: Double,
    val lines: List<CategoryBudgetLine>,
) {
    /**
     * The heart of the zero-based method: income of the month minus what was
     * assigned. Zero means every dollar has a job; negative means the user
     * assigned more than the month brought in — displayed as-is, never hidden.
     */
    val toAssign: Double get() = income - totalAssigned
}

/**
 * The zero-based monthly budget math (the YNAB method), as pure functions.
 *
 * Rules — read before changing:
 *
 * 1. **Assignment is monthly.** Each (category, month) can carry its own
 *    assigned amount; nothing is inferred from the legacy Budgets screen,
 *    whose budgets are standalone caps unrelated to this method.
 * 2. **Rollover.** A category's available rolls from month to month:
 *    `carriedIn(M) = available(M−1) = carriedIn(M−1) + assigned(M−1) −
 *    spent(M−1)`. Underspending rolls forward as a cushion, overspending
 *    rolls forward as a hole to fill — both are shown, neither is forgiven.
 *    The walk starts at the earliest month present in the data (assignment,
 *    goal or transaction) so a category's history is never truncated.
 * 3. **To assign** is this month only: income of the month − assignments of
 *    the month. Last month's unassigned income does not leak into it (that
 *    money was last month's to give jobs to; assigning it now would mean
 *    assigning it in the month it arrived, which the user still can by
 *    navigating back).
 * 4. **Goals.** The monthly need spreads what is still missing over the
 *    months from the viewed month to the target month, both included:
 *    `needed = ceil-to-cent((target − available) / monthsLeft)`. Rounding
 *    *up* to the cent makes following the suggestion actually reach the
 *    goal. A target month already past means the rest is due now.
 * 5. Amounts are compared and summed raw (doubles, as the whole app does);
 *    only the goal suggestion is rounded, per rule 4.
 */
class ZeroBasedBudgetCalculator @Inject constructor() {

    fun month(
        month: YearMonth,
        categoryIds: List<UUID>,
        assignments: List<CategoryAssignment>,
        transactions: List<BudgetTx>,
        goals: List<CategoryGoal>,
    ): MonthlyBudget {
        val assignedByKey = assignments
            .filter { it.month <= month }
            .groupBy({ it.categoryId to it.month }, { it.amount })
            .mapValues { it.value.sum() }
        val goalsByCategory = goals.associateBy { it.categoryId }

        // Every category that must get a line: the ones the caller lists
        // (all existing categories), plus any id the data mentions.
        val allCategories = LinkedHashSet<UUID>().apply {
            addAll(categoryIds)
            addAll(assignedByKey.keys.map { it.first })
            addAll(goalsByCategory.keys)
            transactions.mapNotNullTo(this) { it.categoryId }
        }

        val txByMonth = transactions
            .filter { YearMonth.from(it.date) <= month }
            .groupBy { YearMonth.from(it.date) }

        // Walk month by month from the earliest data up to the viewed month,
        // carrying each category's available forward (rule 2).
        val startMonth = listOfNotNull(
            month,
            assignments.minOfOrNull { it.month },
            transactions.minOfOrNull { YearMonth.from(it.date) },
        ).minOrNull() ?: month

        var available = allCategories.associateWith { 0.0 }
        var carriedIn = available
        var current = startMonth
        while (true) {
            carriedIn = available
            val monthTx = txByMonth[current].orEmpty()
            val next = HashMap<UUID, Double>(allCategories.size)
            for (categoryId in allCategories) {
                val spent = monthTx
                    .filter { it.type == BudgetTxType.EXPENSE && it.categoryId == categoryId }
                    .sumOf { it.amount }
                val assigned = assignedByKey[categoryId to current] ?: 0.0
                next[categoryId] = carriedIn.getValue(categoryId) + assigned - spent
            }
            available = next
            if (current == month) break
            current = current.plusMonths(1)
        }

        val monthTx = txByMonth[month].orEmpty()
        val income = monthTx
            .filter { it.type == BudgetTxType.INCOME }
            .sumOf { it.amount }
        val uncategorizedSpent = monthTx
            .filter { it.type == BudgetTxType.EXPENSE && it.categoryId == null }
            .sumOf { it.amount }

        val lines = allCategories.map { categoryId ->
            val spent = monthTx
                .filter { it.type == BudgetTxType.EXPENSE && it.categoryId == categoryId }
                .sumOf { it.amount }
            val assigned = assignedByKey[categoryId to month] ?: 0.0
            val goal = goalsByCategory[categoryId]
            CategoryBudgetLine(
                categoryId = categoryId,
                carriedIn = carriedIn.getValue(categoryId),
                assigned = assigned,
                spent = spent,
                goal = goal,
                goalMonthlyNeeded = goal?.let {
                    monthlyNeeded(
                        target = it.targetAmount,
                        available = available.getValue(categoryId),
                        fromMonth = month,
                        targetMonth = it.targetMonth,
                    )
                },
            )
        }

        return MonthlyBudget(
            month = month,
            income = income,
            totalAssigned = lines.sumOf { it.assigned },
            uncategorizedSpent = uncategorizedSpent,
            lines = lines,
        )
    }

    /**
     * Age of money, simplified FIFO estimate (the indicator YNAB shows):
     * every expense is funded by the oldest income still "in the pile", and
     * the age of a spent dollar is the number of days between the income
     * that funded it and the expense. The result is the amount-weighted
     * average age, in whole days, over the expenses of the last 30 days
     * (today included). Expenses are matched against income received on or
     * before them — including income older than the window, because old
     * money is exactly what the age measures; spending that no recorded
     * income covers is ignored (its age is unknowable).
     *
     * This is deliberately simpler than a bank-grade computation: transfers
     * between own accounts are absent from [transactions] by construction
     * (the ViewModel leaves them out), and intra-day ordering follows the
     * calendar date only. Returns null when no expense of the window could
     * be matched to income.
     */
    fun ageOfMoney(transactions: List<BudgetTx>, today: LocalDate): Int? {
        val windowStart = today.minusDays(AGE_WINDOW_DAYS - 1)
        // Income lots, oldest first: [date, amount still unspent].
        val lots = ArrayDeque<Pair<LocalDate, Double>>()
        var matchedAmount = 0.0
        var ageSum = 0.0

        val chronological = transactions
            .filter { it.date <= today }
            .sortedBy { it.date }
        for (tx in chronological) {
            when (tx.type) {
                BudgetTxType.INCOME -> lots.addLast(tx.date to tx.amount)
                BudgetTxType.EXPENSE -> {
                    var remaining = tx.amount
                    while (remaining > EPSILON && lots.isNotEmpty()) {
                        val (lotDate, lotAmount) = lots.first()
                        val used = minOf(lotAmount, remaining)
                        if (tx.date >= windowStart) {
                            ageSum += used * ChronoUnit.DAYS.between(lotDate, tx.date)
                            matchedAmount += used
                        }
                        remaining -= used
                        lots.removeFirst()
                        val left = lotAmount - used
                        if (left > EPSILON) lots.addFirst(lotDate to left)
                    }
                }
            }
        }

        if (matchedAmount <= EPSILON) return null
        return (ageSum / matchedAmount).toInt()
    }

    private fun monthlyNeeded(
        target: Double,
        available: Double,
        fromMonth: YearMonth,
        targetMonth: YearMonth,
    ): Double {
        val remaining = target - available
        if (remaining <= 0.0) return 0.0
        val monthsLeft = ChronoUnit.MONTHS.between(fromMonth, targetMonth) + 1
        if (monthsLeft <= 0) return remaining
        // Round up to the cent so following the number reaches the goal.
        return ceil(remaining / monthsLeft * 100.0) / 100.0
    }

    private companion object {
        const val AGE_WINDOW_DAYS = 30L
        const val EPSILON = 0.000001
    }
}
