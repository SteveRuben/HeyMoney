package org.abodah.heymoney.receipts

import org.abodah.heymoney.alerts.BankAlert
import org.abodah.heymoney.alerts.BankAlertParser
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Turns the raw text of a receipt (OCR output) into an expense draft.
 * Heuristics only: the user always confirms before anything is saved.
 */
object ReceiptParser {

    private val TOTAL_WORDS = listOf(
        "net à payer", "net a payer", "total ttc", "grand total", "amount due", "balance due",
        "total", "montant", "à payer", "a payer", "amount", "ttc"
    )
    private val NOT_A_TOTAL = listOf(
        "sous-total", "sous total", "subtotal", "sub total", "tva", "tax", "vat", "rendu",
        "change", "monnaie", "espèces", "especes", "cash", "tendered", "reçu", "recu"
    )
    private val SKIP_AS_MERCHANT = listOf(
        "facture", "invoice", "receipt", "ticket", "reçu", "recu", "merci", "thank", "bienvenue",
        "welcome", "tel", "tél", "www", "http", "date", "caisse", "client"
    )

    // "1 234,56", "1.234,56", "1,234.56", "12.50", "5000"
    private val AMOUNT = Regex("""\d{1,3}(?:[ .,]\d{3})+(?:[.,]\d{1,2})?|\d+[.,]\d{1,2}|\d+""")

    private val DATE_DMY = Regex("""\b(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})\b""")
    private val DATE_YMD = Regex("""\b(\d{4})[/.\-](\d{1,2})[/.\-](\d{1,2})\b""")

    fun parse(text: String, now: Long = System.currentTimeMillis()): BankAlert? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val amount = findTotal(lines) ?: return null
        val merchant = findMerchant(lines)
        val date = findDate(text) ?: now

        return BankAlert(
            id = BankAlert.idFor(BankAlert.Source.RECEIPT, text.take(2000)),
            source = BankAlert.Source.RECEIPT,
            sender = "",
            raw = lines.take(8).joinToString(" ").take(400),
            amount = amount,
            currency = BankAlertParser.currencyIn(text),
            isIncome = false,
            merchant = merchant,
            date = date
        )
    }

    /** The amount on the last "total" line, else the largest amount on the receipt. */
    internal fun findTotal(lines: List<String>): Double? {
        val totalLines = lines.filter { line ->
            val lower = line.lowercase(Locale.ROOT)
            TOTAL_WORDS.any { lower.contains(it) } && NOT_A_TOTAL.none { lower.contains(it) }
        }
        val fromTotals = totalLines.asReversed().firstNotNullOfOrNull { line ->
            amountsIn(line).maxOrNull()
        }
        if (fromTotals != null && fromTotals > 0) return fromTotals
        return lines.flatMap(::amountsIn).filter { it > 0 }.maxOrNull()
    }

    private fun amountsIn(line: String): List<Double> =
        AMOUNT.findAll(line).mapNotNull { match ->
            val token = match.value
            // long digit runs are phone numbers, card or ticket numbers
            if (token.filter { it.isDigit() }.length > 9) null else BankAlertParser.parseAmount(token)
        }.toList()

    internal fun findMerchant(lines: List<String>): String {
        val candidate = lines.take(6).firstOrNull { line ->
            val lower = line.lowercase(Locale.ROOT)
            val letters = line.count { it.isLetter() }
            letters >= 3 &&
                letters >= line.length / 2 &&
                SKIP_AS_MERCHANT.none { lower.startsWith(it) || lower.contains(it) }
        }
        return (candidate ?: "Receipt").trim().take(40).trimEnd(' ', '.', ',', '-')
    }

    internal fun findDate(text: String): Long? {
        val zone = ZoneId.systemDefault()
        fun toMillis(year: Int, month: Int, day: Int): Long? = try {
            val y = if (year < 100) 2000 + year else year
            LocalDate.of(y, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        } catch (e: DateTimeException) {
            null
        }
        DATE_YMD.find(text)?.let { m ->
            toMillis(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        DATE_DMY.find(text)?.let { m ->
            toMillis(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { return it }
        }
        return null
    }
}
