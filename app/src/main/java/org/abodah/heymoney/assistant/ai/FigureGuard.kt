package org.abodah.heymoney.assistant.ai

import kotlin.math.abs

/**
 * The numeric guard-rail of the AI assistant: the model may phrase and
 * route, but it may never invent a figure. Every number appearing in a
 * model-written answer must trace back to a number the code computed (the
 * facts block, a template answer, or a web result) — allowing for the
 * rounding a human would do (58.49 may be phrased as 58 or 58.5).
 *
 * Pure Kotlin, no Android dependency: unit-tested.
 */
object FigureGuard {

    private val NUMBER = Regex("""\d[\d\s.,]*\d|\d""")

    /** All numbers in [text], normalised across FR/EN formats. */
    fun extractNumbers(text: String): Set<Double> =
        NUMBER.findAll(text).mapNotNull { parseToken(it.value) }.toSet()

    /** True when every number in [answer] is grounded in [allowed]. */
    fun isGrounded(answer: String, allowed: Set<Double>): Boolean =
        extractNumbers(answer).all { n -> allowed.any { a -> closeEnough(n, a) } }

    private fun closeEnough(candidate: Double, allowed: Double): Boolean {
        val tolerance = maxOf(0.51, abs(allowed) * 0.01)
        return abs(candidate - allowed) <= tolerance
    }

    internal fun parseToken(raw: String): Double? {
        var token = raw.replace(Regex("[\\s\\u00A0\\u202F]"), "")
        if (token.isEmpty()) return null
        val hasDot = '.' in token
        val hasComma = ',' in token
        token = when {
            hasDot && hasComma -> {
                // the last separator is the decimal one: 1,234.56 / 1.234,56
                if (token.lastIndexOf('.') > token.lastIndexOf(',')) {
                    token.replace(",", "")
                } else {
                    token.replace(".", "").replace(',', '.')
                }
            }
            hasComma -> {
                // 58,49 is a decimal comma; 1,234 is a thousands group
                if (Regex("""^\d{1,3}(,\d{3})+$""").matches(token)) {
                    token.replace(",", "")
                } else {
                    token.replace(',', '.')
                }
            }
            else -> token
        }
        // a trailing separator left by the regex ("58." from "58. Next")
        token = token.trimEnd('.', ',')
        return token.toDoubleOrNull()
    }
}
