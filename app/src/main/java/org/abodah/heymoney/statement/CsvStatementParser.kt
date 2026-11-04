package org.abodah.heymoney.statement

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One row of a bank account or credit card statement, normalised.
 *
 * [amount] is signed: positive means money in (credit, deposit, payment
 * received), negative means money out (charge, debit, fee).
 */
data class StatementLine(
    val date: LocalDate?,
    /** Merchant / wording of the operation, as printed on the statement. */
    val title: String,
    val amount: Double,
    /** Category printed by the bank, when the statement carries one. */
    val category: String? = null,
)

/** Result of reading one statement file. */
data class ParsedStatement(
    val lines: List<StatementLine>,
    /** Data rows that could not be turned into a line (no readable amount). */
    val skippedRows: Int,
    /** Header names of the columns that were recognised, for the UI. */
    val detectedColumns: List<String>,
)

/**
 * Reads a bank or credit card statement exported as CSV.
 *
 * Bank exports are not standardised, so the reader works by recognition
 * rather than by fixed positions: it tokenises the file (quotes, escaped
 * quotes, `,` / `;` / tab / `|` delimiters), finds the header row, then
 * matches columns by name — date, description, amount, or a debit + credit
 * pair, plus an optional category column. French and English headers are
 * both recognised, as are the amount and date formats both languages use.
 *
 * Pure Kotlin (no Android, no CSV library) so the whole recognition logic
 * stays unit-testable off-device.
 */
object CsvStatementParser {

    fun parse(text: String): ParsedStatement {
        val records = tokenize(text.removePrefix("﻿"))
            .filter { record -> record.any { it.isNotBlank() } }
        if (records.isEmpty()) return ParsedStatement(emptyList(), 0, emptyList())

        val headerIndex = records.indexOfFirst { isHeaderRow(it) }
        if (headerIndex < 0) {
            return ParsedStatement(emptyList(), records.size, emptyList())
        }
        val header = records[headerIndex].map { normalizeHeader(it) }
        val rawHeader = records[headerIndex]

        val dateCol = findColumn(header, DATE_HEADERS)
        val titleCol = findColumn(header, TITLE_HEADERS, exclude = setOfNotNull(dateCol))
        val debitCol = findColumn(header, DEBIT_HEADERS)
        val creditCol = findColumn(header, CREDIT_HEADERS)
        val amountCol = findColumn(
            header, AMOUNT_HEADERS,
            exclude = setOfNotNull(debitCol, creditCol),
        )
        val categoryCol = findColumn(header, CATEGORY_HEADERS)
        val typeCol = findColumn(header, TYPE_HEADERS)

        if (titleCol == null || (amountCol == null && debitCol == null && creditCol == null)) {
            return ParsedStatement(emptyList(), records.size - headerIndex - 1, emptyList())
        }

        val detected = mutableListOf<String>()
        listOf(dateCol, titleCol, amountCol, debitCol, creditCol, categoryCol)
            .filterNotNull()
            .forEach { detected += rawHeader[it].trim() }

        var skipped = 0
        val lines = mutableListOf<StatementLine>()
        for (row in records.drop(headerIndex + 1)) {
            val amount = rowAmount(row, amountCol, debitCol, creditCol, typeCol)
            val title = titleCol.let { row.getOrNull(it)?.trim().orEmpty() }
            if (amount == null || title.isBlank()) {
                skipped++
                continue
            }
            lines += StatementLine(
                date = dateCol?.let { parseDate(row.getOrNull(it).orEmpty()) },
                title = title,
                amount = amount,
                category = categoryCol?.let { row.getOrNull(it)?.trim() }?.takeIf { it.isNotBlank() },
            )
        }
        return ParsedStatement(lines, skipped, detected)
    }

    // region Row interpretation

    /**
     * Amount of one debit/credit cell: a blank or absent cell means "nothing
     * moved" (0), a non-blank cell that cannot be parsed makes the whole row
     * unreadable (null) instead of silently counting it as zero.
     */
    private fun cellAmount(row: List<String>, column: Int?): Double? {
        if (column == null) return 0.0
        val raw = row.getOrNull(column).orEmpty()
        if (raw.isBlank()) return 0.0
        return parseAmount(raw)
    }

    private fun rowAmount(
        row: List<String>,
        amountCol: Int?,
        debitCol: Int?,
        creditCol: Int?,
        typeCol: Int?,
    ): Double? {
        if (debitCol != null || creditCol != null) {
            val debit = cellAmount(row, debitCol) ?: return null
            val credit = cellAmount(row, creditCol) ?: return null
            if (debit == 0.0 && credit == 0.0) return null
            // Debit/credit columns sometimes carry signed values already;
            // the absolute value is the amount that moved each way.
            return kotlin.math.abs(credit) - kotlin.math.abs(debit)
        }
        val raw = amountCol?.let { row.getOrNull(it) } ?: return null
        val parsed = parseAmount(raw) ?: return null
        val typeToken = typeCol?.let { normalizeHeader(row.getOrNull(it).orEmpty()) }.orEmpty()
        return when {
            // A DR/CR style column decides the sign when amounts print unsigned.
            typeToken in DEBIT_TOKENS -> -kotlin.math.abs(parsed)
            typeToken in CREDIT_TOKENS -> kotlin.math.abs(parsed)
            else -> parsed
        }
    }

    /**
     * Parses one amount cell across the formats banks print:
     * `1,234.56`, `1.234,56`, `1 234,56`, `1234.56`, `-45.00`, `45.00-`,
     * `(45.00)` (accounting negative), with or without a currency symbol.
     */
    fun parseAmount(raw: String): Double? {
        var s = raw.trim()
            .replace(' ', ' ')
            .replace(' ', ' ')
            .replace('−', '-')
        if (s.isEmpty()) return null

        var negative = false
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true
            s = s.substring(1, s.length - 1)
        }
        if (s.endsWith("-")) {
            negative = true
            s = s.dropLast(1)
        }
        // Keep only digits and separators: drops currency symbols, letters, spaces.
        s = s.filter { it.isDigit() || it == ',' || it == '.' || it == '-' || it == '+' }
        if (s.startsWith("-")) negative = true
        if (s.startsWith("+")) s = s.drop(1)
        s = s.replace("-", "")
        if (s.isBlank() || s.none { it.isDigit() }) return null

        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val cleaned = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")
                else s.replace(".", "").replace(',', '.')
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                // "1,234" / "1,234,567" read as thousands groups;
                // otherwise the last comma is a decimal comma ("12,34", "1.234,56" style).
                if (decimals == 3) {
                    s.replace(",", "")
                } else {
                    s.substring(0, lastComma).replace(",", "") + "." +
                        s.substring(lastComma + 1)
                }
            }
            else -> s
        }
        val value = cleaned.toDoubleOrNull() ?: return null
        return if (negative) -value else value
    }

    /**
     * Parses one date cell: ISO first, then numeric `d/M/y` / `M/d/y`
     * (decided by whichever component cannot be a month, day-first by
     * default), then month-name formats in English and French.
     */
    fun parseDate(raw: String): LocalDate? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        runCatching { return LocalDate.parse(s.take(10), DateTimeFormatter.ISO_LOCAL_DATE) }

        val parts = s.split('/', '-', '.', ' ').filter { it.isNotBlank() }
        if (parts.size == 3 && parts.all { p -> p.all { it.isDigit() } }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            val c = parts[2].toInt()
            val (year, month, day) = when {
                parts[0].length == 4 -> Triple(a, b, c)
                a > 12 -> Triple(c, b, a)
                b > 12 -> Triple(c, a, b)
                else -> Triple(c, b, a) // day-first default
            }
            val fullYear = if (year < 100) 2000 + year else year
            return runCatching { LocalDate.of(fullYear, month, day) }.getOrNull()
        }

        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH, Locale.CANADA_FRENCH)) {
            for (pattern in MONTH_NAME_PATTERNS) {
                runCatching {
                    return LocalDate.parse(s, DateTimeFormatter.ofPattern(pattern, locale))
                }
            }
        }
        return null
    }

    // endregion

    // region Tokenising

    /** Splits CSV text into records of cells, honouring quoted cells. */
    fun tokenize(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        var cell = StringBuilder()
        var inQuotes = false
        var delimiter = ','
        var delimiterDecided = false

        fun endCell() {
            row.add(cell.toString())
            cell = StringBuilder()
        }

        fun endRow() {
            endCell()
            rows.add(row)
            row = mutableListOf()
        }

        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < n && text[i + 1] == '"') {
                            cell.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        cell.append(ch)
                    }
                }
                ch == '"' && cell.isEmpty() -> inQuotes = true
                !delimiterDecided && (ch == ';' || ch == '\t' || ch == '|') -> {
                    // A ;, tab or | before the first comma means that is the delimiter.
                    delimiter = ch
                    delimiterDecided = true
                    endCell()
                }
                ch == delimiter -> {
                    delimiterDecided = true
                    endCell()
                }
                ch == '\n' -> endRow()
                ch == '\r' -> Unit // handled by the \n, or a lone separator
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    // endregion

    // region Header recognition

    private fun isHeaderRow(row: List<String>): Boolean =
        row.any { cell ->
            val h = normalizeHeader(cell)
            h in ALL_HEADER_WORDS || ALL_HEADER_WORDS.any { h.contains(it) }
        }

    private fun findColumn(
        header: List<String>,
        keywords: List<String>,
        exclude: Set<Int> = emptySet(),
    ): Int? {
        // Exact header names win over "contains" matches.
        for ((index, h) in header.withIndex()) {
            if (index !in exclude && h in keywords) return index
        }
        for ((index, h) in header.withIndex()) {
            if (index !in exclude && keywords.any { h.contains(it) }) return index
        }
        return null
    }

    private fun normalizeHeader(raw: String): String {
        val withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase().trim().replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    private val DATE_HEADERS = listOf(
        "date", "transaction date", "date de transaction", "posting date",
        "date d operation", "operation date", "value date", "date de valeur",
    )
    private val TITLE_HEADERS = listOf(
        "description", "libelle", "merchant", "marchand", "payee", "name", "nom",
        "details", "detail", "wording", "beneficiaire", "counterparty",
        "transaction", "operation",
    )
    private val AMOUNT_HEADERS = listOf(
        "amount", "montant", "net amount", "montant net", "value", "valeur",
        "transaction amount",
    )
    private val DEBIT_HEADERS = listOf(
        "debit", "withdrawal", "retrait", "money out", "sorties", "charges",
    )
    private val CREDIT_HEADERS = listOf(
        "credit", "deposit", "depot", "money in", "entrees", "versement",
    )
    private val CATEGORY_HEADERS = listOf("category", "categorie", "cat")
    private val TYPE_HEADERS = listOf("type", "sens", "dr cr", "direction")

    private val DEBIT_TOKENS = setOf("dr", "d", "debit", "expense", "sortie", "out")
    private val CREDIT_TOKENS = setOf("cr", "c", "credit", "income", "entree", "in")

    private val ALL_HEADER_WORDS: Set<String> =
        (DATE_HEADERS + TITLE_HEADERS + AMOUNT_HEADERS + DEBIT_HEADERS +
            CREDIT_HEADERS + CATEGORY_HEADERS + TYPE_HEADERS).toSet()

    private val MONTH_NAME_PATTERNS = listOf(
        "d MMM yyyy", "MMM d, yyyy", "d MMMM yyyy", "MMMM d, yyyy", "dd MMM yyyy",
    )

    // endregion
}
