package org.abodah.heymoney.vault

import java.security.SecureRandom

/** Character-set switches for the vault's password generator. */
data class PasswordOptions(
    val length: Int = 16,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = false,
)

/**
 * Generates passwords for vault entries. Pure logic (the randomness source
 * is injectable) so the guarantees are unit-testable: exact length, only
 * the selected character sets, and at least one character from each
 * selected set.
 */
object PasswordGenerator {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 128

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}<>?"

    fun generate(
        options: PasswordOptions,
        random: SecureRandom = SecureRandom(),
    ): String {
        val sets = mutableListOf<String>()
        if (options.lower) sets += LOWER
        if (options.upper) sets += UPPER
        if (options.digits) sets += DIGITS
        if (options.symbols) sets += SYMBOLS
        if (sets.isEmpty()) sets += LOWER + UPPER + DIGITS

        val length = options.length.coerceIn(maxOf(MIN_LENGTH, sets.size), MAX_LENGTH)
        val union = sets.joinToString("")
        val chars = CharArray(length)

        // One guaranteed character per selected set, then fill from the union.
        sets.forEachIndexed { index, set ->
            chars[index] = set[random.nextInt(set.length)]
        }
        for (i in sets.size until length) {
            chars[i] = union[random.nextInt(union.length)]
        }
        // Fisher–Yates shuffle so the guaranteed characters are not first.
        for (i in length - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }
}
