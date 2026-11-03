package org.abodah.heymoney.subscriptions

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.IntervalType
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/**
 * Finds subscriptions in what the user already has in HeyMoney:
 *
 * 1. the transaction history — a subscription is a group of expense charges with
 *    the same title, similar amounts and a regular rhythm;
 * 2. the recurring planned payments — those are subscriptions by declaration.
 *
 * When both describe the same service (same title), the planned payment wins
 * for the schedule: it is what the user declared, while the history only
 * estimates it. Pure functions, no Android dependency, so they stay testable.
 */
class SubscriptionDetector @Inject constructor() {

    fun detect(
        transactions: List<TransactionEntity>,
        plannedRules: List<PlannedPaymentRuleEntity>,
        accountCurrencies: Map<UUID, String?> = emptyMap(),
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DetectedSubscription> {
        val fromRules = plannedRules.mapNotNull {
            ruleToSubscription(it, transactions, accountCurrencies, today, zone)
        }
        val ruleNames = fromRules.map { normalizeMerchantName(it.name) }.toSet()
        val fromHistory = detectFromHistory(transactions, accountCurrencies, today, zone)
            .filter { normalizeMerchantName(it.name) !in ruleNames }
        return (fromRules + fromHistory)
            .sortedWith(compareByDescending<DetectedSubscription> { it.yearlyCost })
    }

    // region Transaction history

    private fun detectFromHistory(
        transactions: List<TransactionEntity>,
        accountCurrencies: Map<UUID, String?>,
        today: LocalDate,
        zone: ZoneId,
    ): List<DetectedSubscription> {
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE && !it.title.isNullOrBlank() && it.dateTime != null
        }
        return expenses
            .groupBy { normalizeMerchantName(it.title!!) }
            .values
            .mapNotNull { group -> groupToSubscription(group, accountCurrencies, today, zone) }
    }

    private fun groupToSubscription(
        group: List<TransactionEntity>,
        accountCurrencies: Map<UUID, String?>,
        today: LocalDate,
        zone: ZoneId,
    ): DetectedSubscription? {
        // One entry per day: a double charge on the same day must not fake a rhythm.
        val charges = group
            .map { it.dateTime!!.atZone(zone).toLocalDate() to it }
            .groupBy({ it.first }, { it.second })
            .map { (date, sameDay) -> date to sameDay.maxBy { it.amount } }
            .sortedBy { it.first }
        if (charges.size < MIN_CHARGES) return null

        val intervals = charges.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.first, b.first) }
        val medianInterval = intervals.sorted().median()
        val frequency = classify(medianInterval) ?: return null
        if (!isRegular(intervals, medianInterval)) return null

        val amounts = charges.map { it.second.amount }
        val medianAmount = amounts.sorted().median()
        if (!amountsConsistent(amounts, medianAmount)) return null

        val displayName = charges.last().second.title!!.trim()
        val lastDate = charges.last().first
        return DetectedSubscription(
            key = subscriptionKey(displayName, frequency),
            name = displayName,
            amount = medianAmount,
            currency = accountCurrencies[charges.last().second.accountId],
            frequency = frequency,
            source = SubscriptionSource.DETECTED,
            chargeCount = charges.size,
            lastChargeDate = lastDate,
            nextDueDate = rollForward(lastDate, frequency, today),
            firstChargeDate = charges.first().first,
        )
    }

    /** Median interval (in days) to rhythm. Bands are wide on purpose: bills drift by a few days. */
    private fun classify(medianIntervalDays: Double): SubscriptionFrequency? = when (medianIntervalDays) {
        in 6.0..8.5 -> SubscriptionFrequency.WEEKLY
        in 12.0..16.5 -> SubscriptionFrequency.BIWEEKLY
        in 25.0..35.0 -> SubscriptionFrequency.MONTHLY
        in 80.0..100.0 -> SubscriptionFrequency.QUARTERLY
        in 165.0..200.0 -> SubscriptionFrequency.SEMIANNUAL
        in 340.0..390.0 -> SubscriptionFrequency.YEARLY
        else -> null
    }

    private fun isRegular(intervals: List<Long>, medianInterval: Double): Boolean {
        if (intervals.isEmpty()) return false
        val tolerance = maxOf(3.0, medianInterval * 0.22)
        val onRhythm = intervals.count { abs(it - medianInterval) <= tolerance }
        return onRhythm.toDouble() / intervals.size >= 0.7
    }

    private fun amountsConsistent(amounts: List<Double>, medianAmount: Double): Boolean {
        if (medianAmount <= 0) return false
        val tolerance = maxOf(1.0, medianAmount * 0.2)
        val close = amounts.count { abs(it - medianAmount) <= tolerance }
        return close.toDouble() / amounts.size >= 0.7
    }

    // endregion

    // region Planned payments

    private fun ruleToSubscription(
        rule: PlannedPaymentRuleEntity,
        transactions: List<TransactionEntity>,
        accountCurrencies: Map<UUID, String?>,
        today: LocalDate,
        zone: ZoneId,
    ): DetectedSubscription? {
        if (rule.oneTime || rule.type != TransactionType.EXPENSE) return null
        val title = rule.title?.takeIf { it.isNotBlank() } ?: return null
        val frequency = ruleFrequency(rule.intervalType, rule.intervalN) ?: return null
        val start = rule.startDate?.atZone(zone)?.toLocalDate() ?: return null

        // What the rule actually produced so far, when it did.
        val paid = transactions.filter { it.recurringRuleId == rule.id && it.dateTime != null }
            .sortedBy { it.dateTime }
        val lastPaid = paid.lastOrNull()?.dateTime?.atZone(zone)?.toLocalDate()
        val anchor = lastPaid ?: start

        return DetectedSubscription(
            key = subscriptionKey(title, frequency),
            name = title.trim(),
            amount = if (rule.amount > 0) rule.amount else paid.lastOrNull()?.amount ?: 0.0,
            currency = accountCurrencies[rule.accountId],
            frequency = frequency,
            source = SubscriptionSource.PLANNED,
            chargeCount = paid.size,
            lastChargeDate = lastPaid,
            nextDueDate = rollForward(anchor, frequency, today, includeAnchor = lastPaid == null),
            firstChargeDate = paid.firstOrNull()?.dateTime?.atZone(zone)?.toLocalDate() ?: start,
        )
    }

    private fun ruleFrequency(type: IntervalType?, n: Int?): SubscriptionFrequency? {
        val count = n ?: 1
        return when (type) {
            IntervalType.WEEK -> when (count) {
                1 -> SubscriptionFrequency.WEEKLY
                2 -> SubscriptionFrequency.BIWEEKLY
                else -> null
            }
            IntervalType.MONTH -> when (count) {
                1 -> SubscriptionFrequency.MONTHLY
                3 -> SubscriptionFrequency.QUARTERLY
                6 -> SubscriptionFrequency.SEMIANNUAL
                12 -> SubscriptionFrequency.YEARLY
                else -> null
            }
            IntervalType.YEAR -> if (count == 1) SubscriptionFrequency.YEARLY else null
            else -> null
        }
    }

    // endregion

    /**
     * First date reached by adding periods to [anchor] that is not before [today].
     * When [includeAnchor] is set (a planned payment that was never paid), the
     * anchor itself counts as the next due date if it is still ahead.
     */
    private fun rollForward(
        anchor: LocalDate,
        frequency: SubscriptionFrequency,
        today: LocalDate,
        includeAnchor: Boolean = false,
    ): LocalDate {
        var next = if (includeAnchor) anchor else frequency.nextAfter(anchor)
        var guard = 0
        while (next.isBefore(today) && guard < MAX_ROLL_FORWARD) {
            next = frequency.nextAfter(next)
            guard++
        }
        return next
    }

    private fun List<Long>.median(): Double {
        if (isEmpty()) return 0.0
        val mid = size / 2
        return if (size % 2 == 1) this[mid].toDouble() else (this[mid - 1] + this[mid]) / 2.0
    }

    @JvmName("medianDouble")
    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val mid = size / 2
        return if (size % 2 == 1) this[mid] else (this[mid - 1] + this[mid]) / 2.0
    }

    private companion object {
        const val MIN_CHARGES = 3
        const val MAX_ROLL_FORWARD = 600
    }
}
