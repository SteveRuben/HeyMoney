package org.abodah.heymoney.alerts

import java.security.MessageDigest
import java.util.Locale

/**
 * A bank / mobile-money alert detected in an SMS or a notification.
 * Alerts are *drafts*: nothing is saved as a transaction until the user confirms.
 */
data class BankAlert(
    val id: String,
    val source: Source,
    val sender: String,
    val raw: String,
    val amount: Double,
    val currency: String?,
    val isIncome: Boolean,
    val merchant: String,
    val date: Long,
) {
    enum class Source(val label: String) {
        SMS("SMS"),
        NOTIFICATION("Notification"),
        MANUAL("Pasted"),
        RECEIPT("Receipt"),
        EMAIL("Email")
    }

    companion object {
        fun idFor(source: Source, raw: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("${source.name}:$raw".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(20)
        }
    }
}

/**
 * Extracts amount, currency, direction and merchant from French / English bank wording.
 * Deliberately conservative: an alert needs both a money amount and a transaction verb,
 * otherwise [parse] returns null.
 */
object BankAlertParser {

    private val CURRENCY_ALIASES = mapOf(
        "FCFA" to "XAF", "F CFA" to "XAF", "FCFA." to "XAF", "XAF" to "XAF", "XOF" to "XOF",
        "EUR" to "EUR", "€" to "EUR",
        "USD" to "USD", "\$" to "USD",
        "GBP" to "GBP", "£" to "GBP",
        "NGN" to "NGN", "GHS" to "GHS", "KES" to "KES", "MAD" to "MAD",
        "CAD" to "CAD", "CHF" to "CHF"
    )

    private const val CURRENCY_TOKEN =
        "FCFA|F\\s?CFA|XAF|XOF|EUR|USD|GBP|NGN|GHS|KES|MAD|CAD|CHF|€|\\\$|£"

    private val MONEY_PATTERN = Regex(
        "(?i)(?:($CURRENCY_TOKEN)\\s*([0-9][0-9\\s.,]*)|([0-9][0-9\\s.,]*)\\s*($CURRENCY_TOKEN))"
    )

    private val EXPENSE_WORDS = listOf(
        "débit", "debit", "débité", "debited", "achat", "purchase", "paiement",
        "payment", "payé", "paid", "retrait", "withdraw", "withdrawal", "frais",
        "fee", "facture", "invoice", "envoyé", "sent", "transfer to", "spent",
        "retiré", "prélèvement", "charge"
    )

    private val INCOME_WORDS = listOf(
        "crédit", "credit", "crédité", "credited", "reçu", "received", "dépôt",
        "deposit", "versement", "remboursement", "refund", "salaire", "salary",
        "ajouté", "added", "incoming", "transfer received"
    )

    private val MERCHANT_PATTERN = Regex(
        "(?i)(?<!\\p{L})(?:chez|auprès de|aupres de|at|from|de la part de|à|pour|to)\\s+" +
            "([A-Za-z0-9À-ÿ'&.\\- ]{2,40})"
    )

    /** First currency mentioned in [text] (symbol or code), as an ISO code. */
    fun currencyIn(text: String): String? {
        val token = Regex("(?i)(?<![A-Za-z])($CURRENCY_TOKEN)(?![A-Za-z])").find(text)
            ?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.trim() ?: return null
        return CURRENCY_ALIASES[token]
    }

    fun looksLikeBankAlert(text: String): Boolean {
        if (text.isBlank() || text.length > 2000) return false
        val lower = text.lowercase(Locale.ROOT)
        return MONEY_PATTERN.containsMatchIn(text) &&
            (EXPENSE_WORDS + INCOME_WORDS).any { lower.contains(it) }
    }

    fun parse(
        source: BankAlert.Source,
        sender: String,
        text: String,
        now: Long = System.currentTimeMillis()
    ): BankAlert? {
        val cleaned = text.replace(' ', ' ').replace("\n", " ").trim()
        val money = MONEY_PATTERN.find(cleaned) ?: return null
        val amount = parseAmount(money.groupValues[2].ifBlank { money.groupValues[3] })
            ?: return null
        if (amount <= 0.0) return null

        val currencyToken = money.groupValues[1].ifBlank { money.groupValues[4] }
            .uppercase(Locale.ROOT).trim()
        val currency = CURRENCY_ALIASES[currencyToken]

        val lower = cleaned.lowercase(Locale.ROOT)
        val expenseHits = EXPENSE_WORDS.count { lower.contains(it) }
        val incomeHits = INCOME_WORDS.count { lower.contains(it) }
        if (expenseHits == 0 && incomeHits == 0) return null

        val merchant = MERCHANT_PATTERN.find(cleaned)
            ?.groupValues?.getOrNull(1)
            ?.substringBefore(". ")
            ?.let { cutAtTrailingInfo(it) }
            ?.trim()
            ?.trimEnd(' ', '.', ',', ';')
            ?.take(40)
            ?: sender.ifBlank { source.label }

        return BankAlert(
            id = BankAlert.idFor(source, cleaned),
            source = source,
            sender = sender,
            raw = cleaned.take(400),
            amount = amount,
            currency = currency,
            isIncome = incomeHits > expenseHits,
            merchant = merchant,
            date = now
        )
    }

 /** Drops what follows the merchant name: "Carrefour Solde 12 000" -> "Carrefour". */
    private fun cutAtTrailingInfo(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val cut = listOf(" solde", " balance", " avail", " ref", " le ", " on ", " du ")
            .map { lower.indexOf(it) }.filter { it > 0 }.minOrNull()
        return if (cut != null) text.substring(0, cut) else text
    }

    /** Handles "5 000", "12,50", "1.234,56" and "1,234.56". */
    internal fun parseAmount(raw: String): Double? {
        var value = raw.replace(" ", "").replace(" ", "").trim().trimEnd('.', ',')
        if (value.isBlank()) return null
        val lastDot = value.lastIndexOf('.')
        val lastComma = value.lastIndexOf(',')
        value = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) value.replace(",", "")
                else value.replace(".", "").replace(',', '.')
            lastComma >= 0 ->
                if (value.length - lastComma - 1 in 1..2) value.replace(',', '.')
                else value.replace(",", "")
            else -> value
        }
        return value.toDoubleOrNull()
    }
}
