package org.abodah.heymoney.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class MonthlyBudgetCalculatorTest {

    private val calculator = ZeroBasedBudgetCalculator()

    private val catA: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val catB: UUID = UUID.fromString("00000000-0000-0000-0000-00000000000b")
    private val categories = listOf(catA, catB)

    private val jan: YearMonth = YearMonth.of(2026, 1)
    private val feb: YearMonth = YearMonth.of(2026, 2)
    private val mar: YearMonth = YearMonth.of(2026, 3)

    private fun income(month: YearMonth, day: Int, amount: Double) = BudgetTx(
        date = month.atDay(day),
        type = BudgetTxType.INCOME,
        amount = amount,
        categoryId = null,
    )

    private fun expense(month: YearMonth, day: Int, amount: Double, category: UUID?) = BudgetTx(
        date = month.atDay(day),
        type = BudgetTxType.EXPENSE,
        amount = amount,
        categoryId = category,
    )

    private fun budget(
        month: YearMonth,
        assignments: List<CategoryAssignment> = emptyList(),
        transactions: List<BudgetTx> = emptyList(),
        goals: List<CategoryGoal> = emptyList(),
    ): MonthlyBudget = calculator.month(month, categories, assignments, transactions, goals)

    private fun MonthlyBudget.line(category: UUID): CategoryBudgetLine =
        lines.first { it.categoryId == category }

    @Test
    fun `unspent money rolls over to the next month`() {
        val result = budget(
            month = feb,
            assignments = listOf(CategoryAssignment(catA, jan, 300.0)),
            transactions = listOf(
                income(jan, 5, 1000.0),
                expense(jan, 10, 100.0, catA),
            ),
        )

        val line = result.line(catA)
        assertEquals(200.0, line.carriedIn, DELTA)
        assertEquals(0.0, line.assigned, DELTA)
        assertEquals(200.0, line.available, DELTA)
    }

    @Test
    fun `overspending rolls over as a hole to fill`() {
        val result = budget(
            month = feb,
            assignments = listOf(
                CategoryAssignment(catA, jan, 100.0),
                CategoryAssignment(catA, feb, 200.0),
            ),
            transactions = listOf(
                income(jan, 5, 1000.0),
                expense(jan, 10, 250.0, catA),
            ),
        )

        val line = result.line(catA)
        assertEquals(-150.0, line.carriedIn, DELTA)
        assertEquals(200.0, line.assigned, DELTA)
        assertEquals(50.0, line.available, DELTA)
    }

    @Test
    fun `rollover feeds the viewed month spending`() {
        val result = budget(
            month = feb,
            assignments = listOf(CategoryAssignment(catA, jan, 300.0)),
            transactions = listOf(
                income(jan, 5, 1000.0),
                expense(feb, 3, 120.0, catA),
            ),
        )

        val line = result.line(catA)
        assertEquals(300.0, line.carriedIn, DELTA)
        assertEquals(120.0, line.spent, DELTA)
        assertEquals(180.0, line.available, DELTA)
    }

    @Test
    fun `to assign goes negative when assignments exceed the month income`() {
        val result = budget(
            month = mar,
            assignments = listOf(
                CategoryAssignment(catA, mar, 400.0),
                CategoryAssignment(catB, mar, 300.0),
            ),
            transactions = listOf(income(mar, 1, 500.0)),
        )

        assertEquals(500.0, result.income, DELTA)
        assertEquals(700.0, result.totalAssigned, DELTA)
        assertEquals(-200.0, result.toAssign, DELTA)
    }

    @Test
    fun `a month without income assigns against zero`() {
        val result = budget(
            month = mar,
            assignments = listOf(CategoryAssignment(catA, mar, 100.0)),
            transactions = listOf(income(jan, 5, 900.0)),
        )

        assertEquals(0.0, result.income, DELTA)
        assertEquals(-100.0, result.toAssign, DELTA)
    }

    @Test
    fun `every dollar assigned leaves zero to assign`() {
        val result = budget(
            month = mar,
            assignments = listOf(
                CategoryAssignment(catA, mar, 600.0),
                CategoryAssignment(catB, mar, 400.0),
            ),
            transactions = listOf(income(mar, 2, 1000.0)),
        )

        assertEquals(0.0, result.toAssign, DELTA)
    }

    @Test
    fun `future months data does not leak into the viewed month`() {
        val result = budget(
            month = jan,
            assignments = listOf(CategoryAssignment(catA, feb, 999.0)),
            transactions = listOf(
                income(jan, 5, 100.0),
                expense(feb, 1, 50.0, catA),
            ),
        )

        assertEquals(0.0, result.totalAssigned, DELTA)
        assertEquals(0.0, result.line(catA).spent, DELTA)
        assertEquals(100.0, result.toAssign, DELTA)
    }

    @Test
    fun `uncategorized spending is reported outside the lines`() {
        val result = budget(
            month = mar,
            transactions = listOf(
                income(mar, 1, 500.0),
                expense(mar, 4, 40.0, null),
                expense(mar, 4, 60.0, catA),
            ),
        )

        assertEquals(40.0, result.uncategorizedSpent, DELTA)
        assertEquals(60.0, result.line(catA).spent, DELTA)
    }

    @Test
    fun `goal monthly need spreads what is missing over the months left`() {
        val result = budget(
            month = mar,
            goals = listOf(CategoryGoal(catA, 1200.0, YearMonth.of(2026, 8))),
        )

        // March to August included = 6 months, nothing available yet.
        assertEquals(200.0, result.line(catA).goalMonthlyNeeded!!, DELTA)
    }

    @Test
    fun `goal monthly need shrinks with what is already available and rounds up`() {
        val result = budget(
            month = mar,
            assignments = listOf(CategoryAssignment(catA, mar, 300.0)),
            transactions = listOf(income(mar, 1, 1000.0)),
            goals = listOf(CategoryGoal(catA, 1000.0, YearMonth.of(2026, 5))),
        )

        // 700 still missing over March..May = 3 months -> 233.34 (rounded up).
        assertEquals(233.34, result.line(catA).goalMonthlyNeeded!!, 0.0001)
    }

    @Test
    fun `goal already covered needs nothing more`() {
        val result = budget(
            month = mar,
            assignments = listOf(CategoryAssignment(catA, mar, 500.0)),
            transactions = listOf(income(mar, 1, 1000.0)),
            goals = listOf(CategoryGoal(catA, 400.0, YearMonth.of(2026, 12))),
        )

        assertEquals(0.0, result.line(catA).goalMonthlyNeeded!!, DELTA)
    }

    @Test
    fun `goal whose target month passed is due in full now`() {
        val result = budget(
            month = mar,
            assignments = listOf(CategoryAssignment(catA, mar, 100.0)),
            transactions = listOf(income(mar, 1, 1000.0)),
            goals = listOf(CategoryGoal(catA, 450.0, feb)),
        )

        assertEquals(350.0, result.line(catA).goalMonthlyNeeded!!, DELTA)
    }

    @Test
    fun `age of money is the age of the income funding the spending`() {
        val transactions = listOf(
            income(mar, 1, 1000.0),
            expense(mar, 11, 400.0, catA),
        )

        assertEquals(10, calculator.ageOfMoney(transactions, mar.atDay(20)))
    }

    @Test
    fun `age of money is amount weighted across income lots`() {
        val transactions = listOf(
            income(mar, 1, 100.0),
            income(mar, 6, 100.0),
            expense(mar, 11, 150.0, catA),
        )

        // 100 dollars aged 10 days + 50 dollars aged 5 days = 1250 / 150 = 8.33 -> 8.
        assertEquals(8, calculator.ageOfMoney(transactions, mar.atDay(20)))
    }

    @Test
    fun `age of money ignores income received after the expense`() {
        val transactions = listOf(
            expense(mar, 5, 100.0, catA),
            income(mar, 10, 100.0),
        )

        assertNull(calculator.ageOfMoney(transactions, mar.atDay(20)))
    }

    @Test
    fun `age of money ignores spending outside the last 30 days`() {
        val transactions = listOf(
            income(jan, 5, 1000.0),
            expense(jan, 20, 500.0, catA),
        )

        assertNull(calculator.ageOfMoney(transactions, mar.atDay(31)))
    }

    private companion object {
        const val DELTA = 0.001
    }
}
