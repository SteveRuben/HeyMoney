package org.abodah.heymoney.subscriptions

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.IntervalType
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class SubscriptionDetectorTest {

    private val detector = SubscriptionDetector()
    private val accountId: UUID = UUID.randomUUID()
    private val today: LocalDate = LocalDate.of(2026, 11, 2)

    private fun expense(title: String, amount: Double, date: LocalDate): TransactionEntity =
        TransactionEntity(
            accountId = accountId,
            type = TransactionType.EXPENSE,
            amount = amount,
            title = title,
            dateTime = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
        )

    private fun monthlyCharges(
        title: String,
        amount: Double,
        months: List<Int>,
        dayOfMonth: Int = 5,
    ): List<TransactionEntity> = months.map { month ->
        expense(title, amount, LocalDate.of(2026, month, dayOfMonth))
    }

    @Test
    fun `detects a monthly subscription and its yearly cost`() {
        val transactions = monthlyCharges("Netflix", 15.99, listOf(6, 7, 8, 9, 10))

        val result = detector.detect(transactions, emptyList(), today = today, zone = ZoneOffset.UTC)

        result.size shouldBe 1
        val netflix = result.single()
        netflix.name shouldBe "Netflix"
        netflix.frequency shouldBe SubscriptionFrequency.MONTHLY
        netflix.amount shouldBe 15.99
        netflix.yearlyCost shouldBe 15.99 * 12
        netflix.chargeCount shouldBe 5
        netflix.nextDueDate shouldBe LocalDate.of(2026, 11, 5)
        netflix.source shouldBe SubscriptionSource.DETECTED
    }

    @Test
    fun `detects a weekly subscription`() {
        val start = LocalDate.of(2026, 9, 7)
        val transactions = (0..5).map { expense("Gym", 10.0, start.plusWeeks(it.toLong())) }

        val result = detector.detect(transactions, emptyList(), today = today, zone = ZoneOffset.UTC)

        result.size shouldBe 1
        result.single().frequency shouldBe SubscriptionFrequency.WEEKLY
        result.single().yearlyCost shouldBe 520.0
    }

    @Test
    fun `ignores irregular charges to the same title`() {
        val transactions = listOf(
            expense("Restaurant", 42.0, LocalDate.of(2026, 6, 3)),
            expense("Restaurant", 18.5, LocalDate.of(2026, 6, 20)),
            expense("Restaurant", 67.0, LocalDate.of(2026, 9, 1)),
            expense("Restaurant", 25.0, LocalDate.of(2026, 10, 28)),
        )

        detector.detect(transactions, emptyList(), today = today, zone = ZoneOffset.UTC) shouldBe emptyList()
    }

    @Test
    fun `needs at least three charges`() {
        val transactions = monthlyCharges("Spotify", 11.99, listOf(9, 10))

        detector.detect(transactions, emptyList(), today = today, zone = ZoneOffset.UTC) shouldBe emptyList()
    }

    @Test
    fun `tolerates small price changes`() {
        val transactions = listOf(
            expense("iCloud", 3.99, LocalDate.of(2026, 7, 12)),
            expense("iCloud", 3.99, LocalDate.of(2026, 8, 12)),
            expense("iCloud", 4.49, LocalDate.of(2026, 9, 12)),
            expense("iCloud", 4.49, LocalDate.of(2026, 10, 12)),
        )

        val result = detector.detect(transactions, emptyList(), today = today, zone = ZoneOffset.UTC)

        result.size shouldBe 1
        result.single().frequency shouldBe SubscriptionFrequency.MONTHLY
    }

    @Test
    fun `groups titles ignoring case and extra spaces`() {
        val transactions = listOf(
            expense("NETFLIX", 15.99, LocalDate.of(2026, 8, 5)),
            expense("Netflix ", 15.99, LocalDate.of(2026, 9, 5)),
            expense(" netflix", 15.99, LocalDate.of(2026, 10, 5)),
        )

        val result = detector.detect(transactions, emptyList(), today = today, zone = ZoneOffset.UTC)

        result.size shouldBe 1
        result.single().chargeCount shouldBe 3
    }

    @Test
    fun `turns a recurring planned payment into a subscription`() {
        val rule = PlannedPaymentRuleEntity(
            startDate = Instant.parse("2026-08-15T12:00:00Z"),
            intervalN = 3,
            intervalType = IntervalType.MONTH,
            oneTime = false,
            type = TransactionType.EXPENSE,
            accountId = accountId,
            amount = 29.99,
            title = "Internet box"
        )

        val result = detector.detect(emptyList(), listOf(rule), today = today, zone = ZoneOffset.UTC)

        result.size shouldBe 1
        val internet = result.single()
        internet.frequency shouldBe SubscriptionFrequency.QUARTERLY
        internet.source shouldBe SubscriptionSource.PLANNED
        internet.nextDueDate shouldBe LocalDate.of(2026, 11, 15)
        internet.yearlyCost shouldBe 29.99 * 4
    }

    @Test
    fun `planned payment wins over history detection for the same title`() {
        val rule = PlannedPaymentRuleEntity(
            startDate = Instant.parse("2026-06-05T12:00:00Z"),
            intervalN = 1,
            intervalType = IntervalType.MONTH,
            oneTime = false,
            type = TransactionType.EXPENSE,
            accountId = accountId,
            amount = 15.99,
            title = "Netflix"
        )
        val transactions = monthlyCharges("Netflix", 15.99, listOf(6, 7, 8, 9, 10))

        val result = detector.detect(transactions, listOf(rule), today = today, zone = ZoneOffset.UTC)

        result.size shouldBe 1
        result.single().source shouldBe SubscriptionSource.PLANNED
    }

    @Test
    fun `ignores one-time planned payments and income`() {
        val oneTime = PlannedPaymentRuleEntity(
            startDate = Instant.parse("2026-08-15T12:00:00Z"),
            intervalN = 1,
            intervalType = IntervalType.MONTH,
            oneTime = true,
            type = TransactionType.EXPENSE,
            accountId = accountId,
            amount = 900.0,
            title = "Flight ticket"
        )
        val salaries = monthlyCharges("Salary", 3000.0, listOf(6, 7, 8, 9, 10)).map {
            it.copy(type = TransactionType.INCOME)
        }

        detector.detect(salaries, listOf(oneTime), today = today, zone = ZoneOffset.UTC) shouldBe emptyList()
    }
}
