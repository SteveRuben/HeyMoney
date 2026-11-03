package org.abodah.heymoney.subscriptions

import java.time.LocalDate

/**
 * How often a subscription charges. [periodsPerYear] is what turns one charge
 * into the yearly cost shown in the UI.
 */
enum class SubscriptionFrequency(val periodsPerYear: Double) {
    WEEKLY(52.0),
    BIWEEKLY(26.0),
    MONTHLY(12.0),
    QUARTERLY(4.0),
    SEMIANNUAL(2.0),
    YEARLY(1.0);

    /** The date one period after [date]. */
    fun nextAfter(date: LocalDate): LocalDate = when (this) {
        WEEKLY -> date.plusWeeks(1)
        BIWEEKLY -> date.plusWeeks(2)
        MONTHLY -> date.plusMonths(1)
        QUARTERLY -> date.plusMonths(3)
        SEMIANNUAL -> date.plusMonths(6)
        YEARLY -> date.plusYears(1)
    }
}

/** Where a detected subscription comes from. */
enum class SubscriptionSource {
    /** Found by analysing the transaction history. */
    DETECTED,

    /** Declared by the user as a recurring planned payment. */
    PLANNED
}

/** What the user decided about a detected subscription. Absence means "not decided yet". */
enum class SubscriptionStatus {
    CONFIRMED,
    IGNORED
}

/**
 * One recurring charge, either detected in the transactions or coming from a
 * planned payment rule. Pure data: the detector builds these, the UI and the
 * reminder worker only read them.
 */
data class DetectedSubscription(
    /** Stable identity of this subscription, used to remember the user's decision. */
    val key: String,
    /** Display name: the transaction title / planned payment title, as the user wrote it. */
    val name: String,
    /** Typical charge amount (median of the observed charges), always positive. */
    val amount: Double,
    /** Currency code of the account the charges land on, when known. */
    val currency: String?,
    val frequency: SubscriptionFrequency,
    val source: SubscriptionSource,
    /** Number of charges observed in the history (0 for a planned payment not paid yet). */
    val chargeCount: Int,
    val lastChargeDate: LocalDate?,
    /** Estimated next charge date, rolled forward to today or later. */
    val nextDueDate: LocalDate,
    /** First observed charge, used for the "customer since" hint in negotiation templates. */
    val firstChargeDate: LocalDate?,
) {
    /** What one year of this subscription costs. */
    val yearlyCost: Double get() = amount * frequency.periodsPerYear
}

/** Builds the stable key of a subscription from its name and rhythm. */
fun subscriptionKey(name: String, frequency: SubscriptionFrequency): String =
    "${normalizeMerchantName(name)}|${frequency.name}"

/** Lowercases, trims and collapses whitespace so "Netflix ", "NETFLIX" and "Netflix" group together. */
fun normalizeMerchantName(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), " ")
