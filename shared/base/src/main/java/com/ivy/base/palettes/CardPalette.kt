package com.ivy.base.palettes

/**
 * Card colour palettes for the HeyMoney stacked-cards screens (Accounts tab
 * and the eSIM card/screen). Steve's design decision (3 Nov 2026): the
 * LemFi-style stacked cards (variant B, HeyMoney blue) get a user-configurable
 * palette — presets plus per-card custom colours.
 *
 * Colours are plain ARGB [Int]s so this file stays free of Compose/Android
 * dependencies and can be shared by the app module (configuration screen)
 * and feature modules (Accounts tab).
 */

enum class PaletteRole {
    TOTAL,
    ACCOUNTS,
    ESIM
}

/** Fully resolved colours for the three card roles. */
data class CardPaletteColors(
    /** Gradient start (top) of the total-balance card. */
    val totalGradientStart: Int,
    /** Gradient end (bottom) of the total-balance card. */
    val totalGradientEnd: Int,
    /** Text colour on the total-balance card. */
    val totalText: Int,
    /** Background tints cycled across the account cards. */
    val accountTints: List<Int>,
    /** Background of the eSIM entry card / banner. */
    val esimBackground: Int,
    /** Text colour on the eSIM card. */
    val esimText: Int
)

/** What the user picked: a preset id, or [CardPalettes.PRESET_CUSTOM] + colours. */
data class CardPaletteSelection(
    val presetId: String = CardPalettes.DEFAULT_PRESET,
    val customTotal: Int? = null,
    val customAccounts: Int? = null,
    val customEsim: Int? = null
) {
    val isCustom: Boolean get() = presetId == CardPalettes.PRESET_CUSTOM
}

object CardPalettes {
    const val PRESET_BLUE = "blue"
    const val PRESET_CORAL = "coral"
    const val PRESET_GREEN = "green"
    const val PRESET_VIOLET = "violet"
    const val PRESET_SAND = "sand"
    const val PRESET_CUSTOM = "custom"

    const val DEFAULT_PRESET = PRESET_BLUE

    val PRESET_IDS: List<String> =
        listOf(PRESET_BLUE, PRESET_CORAL, PRESET_GREEN, PRESET_VIOLET, PRESET_SAND)

    private const val WHITE: Int = -0x1 // 0xFFFFFFFF
    private val INK: Int = 0xFF1D1B18.toInt()

    /** Curated swatches offered by the per-card colour picker. */
    val SWATCHES: List<Int> = listOf(
        0xFF1E6FEB.toInt(), // HeyMoney blue (deep)
        0xFF5AA2FF.toInt(), // HeyMoney blue (light)
        0xFFF28B82.toInt(), // Coral
        0xFF1E9E4F.toInt(), // Green
        0xFF7ED491.toInt(), // Mint
        0xFF7B3FE4.toInt(), // Violet
        0xFFC4B5FD.toInt(), // Lavender
        0xFFD99A3D.toInt(), // Sand
        0xFFF6C453.toInt(), // Yellow
        0xFF5FE3DF.toInt(), // Cyan
        0xFFEF6FB0.toInt(), // Pink
        0xFF374151.toInt() // Slate
    )

    fun colors(selection: CardPaletteSelection): CardPaletteColors =
        if (selection.isCustom) {
            customColors(
                total = selection.customTotal ?: baseColor(DEFAULT_PRESET, PaletteRole.TOTAL),
                accounts = selection.customAccounts
                    ?: baseColor(DEFAULT_PRESET, PaletteRole.ACCOUNTS),
                esim = selection.customEsim ?: baseColor(DEFAULT_PRESET, PaletteRole.ESIM)
            )
        } else {
            preset(selection.presetId)
        }

    fun preset(id: String): CardPaletteColors = when (id) {
        PRESET_CORAL -> CardPaletteColors(
            // Variant A (LemFi): flat coral total card, dark text, and the
            // multi-hue pastel stack (green / peach / yellow / blue / pink).
            totalGradientStart = 0xFFF6A297.toInt(),
            totalGradientEnd = 0xFFF28B82.toInt(),
            totalText = INK,
            accountTints = listOf(
                0xFFC9EAC2.toInt(),
                0xFFFBE3C2.toInt(),
                0xFFFAEFA3.toInt(),
                0xFFCFE4FB.toInt(),
                0xFFFBD9E8.toInt()
            ),
            esimBackground = 0xFF5FE3DF.toInt(),
            esimText = INK
        )

        PRESET_GREEN -> CardPaletteColors(
            totalGradientStart = 0xFF43C96F.toInt(),
            totalGradientEnd = 0xFF1E9E4F.toInt(),
            totalText = WHITE,
            accountTints = listOf(
                0xFFE4F6E6.toInt(),
                0xFFD5F0DA.toInt(),
                0xFFC6EACE.toInt(),
                0xFFB7E4C2.toInt()
            ),
            esimBackground = 0xFF7ED491.toInt(),
            esimText = INK
        )

        PRESET_VIOLET -> CardPaletteColors(
            totalGradientStart = 0xFFB07FE8.toInt(),
            totalGradientEnd = 0xFF7B3FE4.toInt(),
            totalText = WHITE,
            accountTints = listOf(
                0xFFF1E8FB.toInt(),
                0xFFE9DBF9.toInt(),
                0xFFE1CEF7.toInt(),
                0xFFD9C1F4.toInt()
            ),
            esimBackground = 0xFFC4B5FD.toInt(),
            esimText = INK
        )

        PRESET_SAND -> CardPaletteColors(
            totalGradientStart = 0xFFEDBE78.toInt(),
            totalGradientEnd = 0xFFD99A3D.toInt(),
            totalText = INK,
            accountTints = listOf(
                0xFFFBF0DA.toInt(),
                0xFFF8E8C9.toInt(),
                0xFFF5E0B8.toInt(),
                0xFFF2D8A7.toInt()
            ),
            esimBackground = 0xFFFFD98E.toInt(),
            esimText = INK
        )

        else -> CardPaletteColors(
            // Variant B (default): the HeyMoney logo gradient
            // #5AA2FF -> #1E6FEB, white text, pastel-blue account cards.
            totalGradientStart = 0xFF5AA2FF.toInt(),
            totalGradientEnd = 0xFF1E6FEB.toInt(),
            totalText = WHITE,
            accountTints = listOf(
                0xFFEAF2FF.toInt(),
                0xFFDFECFF.toInt(),
                0xFFD3E4FF.toInt(),
                0xFFC7DBFF.toInt()
            ),
            esimBackground = 0xFF5FE3DF.toInt(),
            esimText = INK
        )
    }

    /**
     * One saturated representative colour per role for a preset — used as the
     * seed when the user starts customising from that preset, and as the
     * preset's preview dot.
     */
    fun baseColor(presetId: String, role: PaletteRole): Int = when (role) {
        PaletteRole.TOTAL -> preset(presetId).totalGradientEnd
        PaletteRole.ESIM -> preset(presetId).esimBackground
        PaletteRole.ACCOUNTS -> when (presetId) {
            PRESET_CORAL -> 0xFF7FB069.toInt()
            PRESET_GREEN -> 0xFF1E9E4F.toInt()
            PRESET_VIOLET -> 0xFF7B3FE4.toInt()
            PRESET_SAND -> 0xFFD99A3D.toInt()
            else -> 0xFF1E6FEB.toInt()
        }
    }

    /** Derives a full palette from one picked colour per role. */
    fun customColors(total: Int, accounts: Int, esim: Int): CardPaletteColors =
        CardPaletteColors(
            totalGradientStart = mixWithWhite(total, 0.25f),
            totalGradientEnd = total,
            totalText = readableText(total),
            accountTints = listOf(
                mixWithWhite(accounts, 0.88f),
                mixWithWhite(accounts, 0.80f),
                mixWithWhite(accounts, 0.72f),
                mixWithWhite(accounts, 0.64f)
            ),
            esimBackground = esim,
            esimText = readableText(esim)
        )

    /** Blends [argb] toward white by [fraction] (0 = unchanged, 1 = white). */
    fun mixWithWhite(argb: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val c = (argb shr shift) and 0xFF
            return (c + (255 - c) * f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Dark ink on light colours, white on dark colours (perceived luminance). */
    fun readableText(argb: Int): Int {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        return if (luminance > 0.55f) INK else WHITE
    }
}
