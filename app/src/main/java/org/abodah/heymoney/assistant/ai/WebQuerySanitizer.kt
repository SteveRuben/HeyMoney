package org.abodah.heymoney.assistant.ai

/**
 * Makes an outgoing web query safe to send: the assistant may ask the
 * internet about a *generic topic*, never about the user's money.
 *
 * Defence in depth, applied in order:
 *  1. every personal term the caller knows (account names, category
 *     names, subscription and planned-payment titles) is removed;
 *  2. every token containing a digit is dropped — amounts, dates,
 *     balances — except 4-digit years and known currency codes, which
 *     are generic by nature;
 *  3. the result is capped in length. If nothing safe remains, the
 *     query is empty and no request is made at all.
 *
 * Pure Kotlin: unit-tested.
 */
object WebQuerySanitizer {

    private val CURRENCY_CODES = setOf(
        "CAD", "USD", "EUR", "GBP", "XAF", "XOF", "CHF", "JPY", "CNY",
        "MAD", "NGN", "GHS", "KES", "ZAR", "AED", "INR", "BRL", "MXN", "AUD"
    )

    private const val MAX_TOKENS = 10
    private const val MAX_TOKEN_LENGTH = 24
    private const val MAX_QUERY_LENGTH = 80

    fun sanitize(raw: String, personalTerms: List<String>): String {
        var query = raw
        personalTerms
            .filter { it.trim().length >= 3 }
            .sortedByDescending { it.length }
            .forEach { term -> query = query.replace(term.trim(), " ", ignoreCase = true) }

        val tokens = query
            .split(Regex("[^\\p{L}0-9]+"))
            .filter { it.isNotEmpty() }
            .filter { token ->
                when {
                    token.length > MAX_TOKEN_LENGTH -> false
                    token.uppercase() in CURRENCY_CODES -> true
                    token.any { it.isDigit() } ->
                        // only a plausible 4-digit year survives; a bare
                        // 4-digit number is treated as an amount
                        token.length == 4 && token.all { it.isDigit() } &&
                            (token.toIntOrNull() ?: 0) in 1900..2099
                    else -> true
                }
            }
            .take(MAX_TOKENS)
        return tokens.joinToString(" ").take(MAX_QUERY_LENGTH).trim()
    }

    /** Currency codes still present in a sanitized query, in order. */
    fun currencyCodesIn(sanitizedQuery: String): List<String> =
        sanitizedQuery.split(Regex("[^\\p{L}0-9]+"))
            .map { it.uppercase() }
            .filter { it in CURRENCY_CODES }
            .distinct()
}
