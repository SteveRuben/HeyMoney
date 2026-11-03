package org.abodah.heymoney.messages

import org.abodah.heymoney.alerts.BankAlertParser
import org.abodah.heymoney.subscriptions.SubscriptionFrequency
import java.util.Locale
import kotlin.math.abs

/** One SMS (or one email notification) reduced to what the matcher needs. */
data class RawMessage(
    val sender: String,
    val body: String,
    val dateMillis: Long,
)

/**
 * A subscription or recurring bill found in messages: a phone carrier, a
 * streaming service, any sender whose messages carry a charge with a rhythm.
 */
data class MessageSubscription(
    /** Display name: the known service, or the sender as printed. */
    val serviceName: String,
    /** Sender of the most recent charge message. */
    val sender: String,
    /** Typical (median) charge amount, positive; null when none was readable. */
    val amount: Double?,
    /** ISO currency code of the charges, when one was printed. */
    val currency: String?,
    val lastDateMillis: Long,
    /** How many charge messages were found for this service. */
    val occurrences: Int,
    /** Estimated rhythm; null under 2 occurrences or when too irregular. */
    val frequency: SubscriptionFrequency?,
) {
    /** Identity used to avoid tracking the same service twice. */
    val key: String get() = serviceName.lowercase(Locale.ROOT).trim()
}

/**
 * Finds subscriptions and recurring bills in SMS and email notifications,
 * entirely on the device.
 *
 * A message counts as a *charge* when three things meet: a recognisable
 * service (a known carrier or service, matched on the sender or in the
 * text — or a brand-like sender id), a bill word (facture, abonnement,
 * prélèvement, bill, subscription, payment…) and a readable amount.
 * Charges are then grouped per service: the median amount is the typical
 * charge, and the median gap between charges estimates the rhythm.
 *
 * Pure Kotlin, unit-testable off-device. The Android layers (SMS scan,
 * email notification listener) only feed it [RawMessage]s.
 */
object MessageSubscriptionMatcher {

    // region Known services

    /** A carrier or service whose bills are worth spotting. */
    data class KnownService(
        val displayName: String,
        /** Fragments matched against the sender id (case-insensitive). */
        val senderHints: List<String>,
        /** Fragments matched against the message text, when the sender is generic. */
        val bodyHints: List<String> = senderHints,
    )

    val KNOWN_SERVICES: List<KnownService> = listOf(
        // Phone carriers — Canada
        KnownService("Bell", listOf("bell"), listOf("bell canada", "bell mobilité", "bell mobility")),
        KnownService("Rogers", listOf("rogers")),
        KnownService("Telus", listOf("telus")),
        KnownService("Fido", listOf("fido")),
        KnownService("Koodo", listOf("koodo")),
        KnownService("Virgin Plus", listOf("virgin plus", "virgin mobile"), listOf("virgin plus", "virgin mobile")),
        KnownService("Vidéotron", listOf("videotron", "vidéotron")),
        KnownService("Freedom Mobile", listOf("freedom mobile", "freedom"), listOf("freedom mobile")),
        // Phone carriers — France
        KnownService("Orange", listOf("orange")),
        KnownService("SFR", listOf("sfr")),
        KnownService("Free", listOf("free mobile", "free"), listOf("free mobile")),
        KnownService("Bouygues Telecom", listOf("bouygues"), listOf("bouygues")),
        // Phone carriers — Africa (mobile money / operators)
        KnownService("MTN", listOf("mtn")),
        KnownService("Moov Africa", listOf("moov")),
        KnownService("Airtel", listOf("airtel")),
        KnownService("Wave", listOf("wave")),
        // Subscription services that bill by message / email receipt
        KnownService("Netflix", listOf("netflix")),
        KnownService("Spotify", listOf("spotify")),
        KnownService("YouTube Premium", listOf("youtube"), listOf("youtube premium", "youtube")),
        KnownService("Disney+", listOf("disney"), listOf("disney+", "disney plus")),
        KnownService("Crave", listOf("crave")),
        KnownService("DAZN", listOf("dazn")),
        KnownService("Apple", listOf("apple"), listOf("apple services", "icloud", "apple music", "apple tv")),
        KnownService("Google", listOf("google"), listOf("google one", "google play")),
        KnownService("Amazon Prime", listOf("amazon"), listOf("prime video", "amazon prime")),
        KnownService("Microsoft", listOf("microsoft"), listOf("microsoft 365", "xbox")),
        KnownService("Adobe", listOf("adobe")),
        KnownService("OpenAI", listOf("openai", "chatgpt"), listOf("openai", "chatgpt")),
    )

    /** The known service a message belongs to, from its sender or its text. */
    fun knownService(sender: String, text: String): KnownService? {
        val from = sender.lowercase(Locale.ROOT)
        val body = text.lowercase(Locale.ROOT)
        return KNOWN_SERVICES.firstOrNull { service ->
            service.senderHints.any { from.contains(it) }
        } ?: KNOWN_SERVICES.firstOrNull { service ->
            service.bodyHints.any { body.contains(it) }
        }
    }

    // endregion

    // region Charge recognition

    private val BILL_WORDS = listOf(
        "facture", "abonnement", "prélèvement", "prelevement", "renouvellement",
        "échéance", "echeance", "forfait", "recharge",
        "bill", "subscription", "payment", "paiement", "charged", "charge de",
        "renewal", "renewed", "invoice", "débit de", "debit de", "payé", "paid",
    )

    /** Strong words: on their own (with an amount) they mark a subscription. */
    private val SUBSCRIPTION_WORDS = listOf(
        "abonnement", "subscription", "facture", "bill", "invoice",
        "renouvellement", "renewal",
    )

    private data class Charge(
        val serviceName: String,
        val sender: String,
        val amount: Double,
        val currency: String?,
        val dateMillis: Long,
    )

    /** The charge inside one message, or null when it is not a bill. */
    fun chargeIn(message: RawMessage): MessageSubscription? {
        val charge = chargeOf(message) ?: return null
        return MessageSubscription(
            serviceName = charge.serviceName,
            sender = charge.sender,
            amount = charge.amount,
            currency = charge.currency,
            lastDateMillis = charge.dateMillis,
            occurrences = 1,
            frequency = null,
        )
    }

    private fun chargeOf(message: RawMessage): Charge? {
        val body = message.body
        if (body.isBlank() || body.length > 4000) return null
        val lower = body.lowercase(Locale.ROOT)
        val amount = extractAmount(body) ?: return null

        val service = knownService(message.sender, body)
        val hasBillWord = BILL_WORDS.any { lower.contains(it) }
        return when {
            service != null && hasBillWord -> Charge(
                serviceName = service.displayName,
                sender = message.sender,
                amount = amount,
                currency = BankAlertParser.currencyIn(body),
                dateMillis = message.dateMillis,
            )
            service == null && isBrandLikeSender(message.sender) &&
                SUBSCRIPTION_WORDS.any { lower.contains(it) } -> Charge(
                serviceName = message.sender.trim(),
                sender = message.sender,
                amount = amount,
                currency = BankAlertParser.currencyIn(body),
                dateMillis = message.dateMillis,
            )
            else -> null
        }
    }

    /** Alphanumeric sender ids ("NETFLIX", "InfoSMS") can name a service; numbers cannot. */
    private fun isBrandLikeSender(sender: String): Boolean {
        val cleaned = sender.trim()
        return cleaned.length >= 3 && cleaned.any { it.isLetter() }
    }

    // endregion

    // region Amount extraction

    private val AMOUNT_PATTERN = Regex(
        "(?:(\\$|€|£)\\s*)?" +
            "(\\d{1,3}(?:[ .,]\\d{3})+(?:[.,]\\d{2})?|\\d+(?:[.,]\\d{2})|\\d+)" +
            "\\s*(\\$|€|£|EUR|USD|CAD|FCFA|XOF|XAF|dollars?)?",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The most plausible charge amount in a bill text: a number that carries
     * a currency token or a decimal part wins; bare integers (references,
     * dates, quotas) are only a fallback.
     */
    fun extractAmount(text: String): Double? {
        val candidates = AMOUNT_PATTERN.findAll(text).mapNotNull { match ->
            val numeric = match.groupValues[2]
            val value = parseNumber(numeric) ?: return@mapNotNull null
            val hasCurrency = match.groupValues[1].isNotEmpty() ||
                match.groupValues[3].isNotEmpty()
            val hasDecimals = numeric.contains('.') || numeric.contains(',')
            Candidate(value, hasCurrency, hasDecimals)
        }.toList()
        val best = candidates.firstOrNull { it.hasCurrency }
            ?: candidates.firstOrNull { it.hasDecimals }
            ?: candidates.firstOrNull()
        return best?.value?.takeIf { it > 0 }
    }

    private data class Candidate(
        val value: Double,
        val hasCurrency: Boolean,
        val hasDecimals: Boolean,
    )

    /** "10 000" -> 10000, "24,99" -> 24.99, "1,234.56" -> 1234.56. */
    private fun parseNumber(raw: String): Double? {
        var s = raw.replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")
                else s.replace(".", "").replace(',', '.')
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                if (decimals == 3) s.replace(",", "") else s.replace(',', '.')
            }
            else -> s
        }
        return s.toDoubleOrNull()
    }

    // endregion

    // region Grouping & frequency

    /** All subscriptions found in a batch of messages, biggest yearly cost first. */
    fun analyse(messages: List<RawMessage>): List<MessageSubscription> {
        val charges = messages.mapNotNull { chargeOf(it) }
        return charges
            .groupBy { it.serviceName.lowercase(Locale.ROOT).trim() }
            .values
            .map { group ->
                val sorted = group.sortedBy { it.dateMillis }
                val amounts = sorted.map { it.amount }.sorted()
                MessageSubscription(
                    serviceName = sorted.last().serviceName,
                    sender = sorted.last().sender,
                    amount = amounts[amounts.size / 2],
                    currency = sorted.last().currency,
                    lastDateMillis = sorted.last().dateMillis,
                    occurrences = sorted.size,
                    frequency = estimateFrequency(sorted.map { it.dateMillis }),
                )
            }
            .sortedWith(
                compareByDescending<MessageSubscription> {
                    (it.amount ?: 0.0) * (it.frequency?.periodsPerYear ?: 12.0)
                }
            )
    }

    /** Median gap between charges, mapped to a rhythm; null when too irregular. */
    fun estimateFrequency(dateMillis: List<Long>): SubscriptionFrequency? {
        if (dateMillis.size < 2) return null
        val sorted = dateMillis.sorted()
        val gapsDays = sorted.zipWithNext { a, b -> (b - a) / MILLIS_PER_DAY.toDouble() }
            .filter { it >= 0.5 } // several texts about one bill are not a rhythm
        if (gapsDays.isEmpty()) return null
        val median = gapsDays.sorted()[gapsDays.size / 2]
        return when (median) {
            in 6.0..8.5 -> SubscriptionFrequency.WEEKLY
            in 12.0..16.5 -> SubscriptionFrequency.BIWEEKLY
            in 25.0..35.0 -> SubscriptionFrequency.MONTHLY
            in 80.0..100.0 -> SubscriptionFrequency.QUARTERLY
            in 165.0..200.0 -> SubscriptionFrequency.SEMIANNUAL
            in 340.0..390.0 -> SubscriptionFrequency.YEARLY
            else -> null
        }
    }

    private const val MILLIS_PER_DAY = 86_400_000L

    // endregion

    /** Keeps only digits/sign of an amount for comparisons in tests and UI. */
    internal fun amountsClose(a: Double, b: Double): Boolean = abs(a - b) < 0.01
}
