package org.abodah.heymoney.chart

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

class MonthlyAggregatorTest {

    private val aggregator = MonthlyAggregator()
    private val baseCurrency = "CAD"
    private val cadAccount: UUID = UUID.randomUUID()
    private val eurAccount: UUID = UUID.randomUUID()
    private val currencies = mapOf(cadAccount to "CAD", eurAccount to "EUR")

    private fun transaction(
        accountId: UUID,
        type: TransactionType,
        amount: Double,
        date: LocalDate?,
    ): TransactionEntity = TransactionEntity(
        accountId = accountId,
        type = type,
        amount = amount,
        dateTime = date?.atTime(12, 0)?.toInstant(ZoneOffset.UTC),
    )

    @Test
    fun `buckets income and expenses by month and zero fills the gaps`() {
        val transactions = listOf(
            transaction(cadAccount, TransactionType.INCOME, 3000.0, LocalDate.of(2026, 9, 1)),
            transaction(cadAccount, TransactionType.EXPENSE, 1200.0, LocalDate.of(2026, 9, 15)),
            transaction(cadAccount, TransactionType.EXPENSE, 50.0, LocalDate.of(2026, 11, 2)),
        )

        val result = aggregator.aggregate(
            transactions = transactions,
            accountCurrencies = currencies,
            baseCurrency = baseCurrency,
            currency = "CAD",
            endMonth = YearMonth.of(2026, 11),
            monthCount = 3,
            zone = ZoneOffset.UTC,
        )

        result.map { it.month } shouldBe listOf(
            YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)
        )
        result[0].income shouldBe 3000.0
        result[0].expense shouldBe 1200.0
        result[0].net shouldBe 1800.0
        result[1].income shouldBe 0.0
        result[1].expense shouldBe 0.0
        result[2].net shouldBe -50.0
    }

    @Test
    fun `ignores transfers undated transactions and other currencies`() {
        val transactions = listOf(
            transaction(cadAccount, TransactionType.TRANSFER, 700.0, LocalDate.of(2026, 10, 5)),
            transaction(cadAccount, TransactionType.INCOME, 900.0, null),
            transaction(eurAccount, TransactionType.INCOME, 400.0, LocalDate.of(2026, 10, 5)),
            transaction(cadAccount, TransactionType.INCOME, 100.0, LocalDate.of(2026, 10, 5)),
        )

        val result = aggregator.aggregate(
            transactions = transactions,
            accountCurrencies = currencies,
            baseCurrency = baseCurrency,
            currency = "CAD",
            endMonth = YearMonth.of(2026, 10),
            monthCount = 1,
            zone = ZoneOffset.UTC,
        )

        result.single().income shouldBe 100.0
        result.single().expense shouldBe 0.0
    }

    @Test
    fun `currencies present lists only currencies with dated movements base first`() {
        val transactions = listOf(
            transaction(eurAccount, TransactionType.EXPENSE, 20.0, LocalDate.of(2026, 10, 3)),
            transaction(cadAccount, TransactionType.TRANSFER, 20.0, LocalDate.of(2026, 10, 3)),
            transaction(cadAccount, TransactionType.INCOME, 20.0, LocalDate.of(2026, 10, 3)),
        )

        aggregator.currenciesPresent(transactions, currencies, baseCurrency) shouldBe
            listOf("CAD", "EUR")
    }

    @Test
    fun `a 12 month window is the year ending at the end month`() {
        val result = aggregator.aggregate(
            transactions = emptyList(),
            accountCurrencies = currencies,
            baseCurrency = baseCurrency,
            currency = "CAD",
            endMonth = YearMonth.of(2026, 3),
            monthCount = 12,
            zone = ZoneOffset.UTC,
        )

        result.size shouldBe 12
        result.first().month shouldBe YearMonth.of(2025, 4)
        result.last().month shouldBe YearMonth.of(2026, 3)
    }
}
