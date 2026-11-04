package com.ivy.base.palettes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's card-palette choice (preset or per-card custom colours)
 * in its own SharedPreferences file — same pattern as the other HeyMoney
 * stores. Exposed as a [StateFlow] so every screen using the palette
 * (Accounts tab, eSIM, configuration screen) updates immediately.
 */
@Singleton
class CardPaletteStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selection = MutableStateFlow(read())
    val selection: StateFlow<CardPaletteSelection> = _selection

    /** The fully resolved colours for the current selection. */
    fun colors(): CardPaletteColors = CardPalettes.colors(_selection.value)

    fun selectPreset(presetId: String) {
        prefs.edit().putString(KEY_PRESET, presetId).apply()
        _selection.value = read()
    }

    /**
     * Picks a colour for one card role. The first pick switches the palette
     * to "Custom", seeded with the previously effective preset's colours so
     * the other roles keep their look.
     */
    fun setCustomColor(role: PaletteRole, argb: Int) {
        val current = _selection.value
        var total = current.customTotal
        var accounts = current.customAccounts
        var esim = current.customEsim
        if (!current.isCustom) {
            total = CardPalettes.baseColor(current.presetId, PaletteRole.TOTAL)
            accounts = CardPalettes.baseColor(current.presetId, PaletteRole.ACCOUNTS)
            esim = CardPalettes.baseColor(current.presetId, PaletteRole.ESIM)
        }
        when (role) {
            PaletteRole.TOTAL -> total = argb
            PaletteRole.ACCOUNTS -> accounts = argb
            PaletteRole.ESIM -> esim = argb
        }
        prefs.edit()
            .putString(KEY_PRESET, CardPalettes.PRESET_CUSTOM)
            .putInt(KEY_CUSTOM_TOTAL, total ?: CardPalettes.baseColor(
                CardPalettes.DEFAULT_PRESET, PaletteRole.TOTAL
            ))
            .putInt(KEY_CUSTOM_ACCOUNTS, accounts ?: CardPalettes.baseColor(
                CardPalettes.DEFAULT_PRESET, PaletteRole.ACCOUNTS
            ))
            .putInt(KEY_CUSTOM_ESIM, esim ?: CardPalettes.baseColor(
                CardPalettes.DEFAULT_PRESET, PaletteRole.ESIM
            ))
            .apply()
        _selection.value = read()
    }

    /** Back to the default HeyMoney Blue preset; custom colours are dropped. */
    fun reset() {
        prefs.edit().clear().apply()
        _selection.value = read()
    }

    private fun read(): CardPaletteSelection {
        val unset = Int.MIN_VALUE
        fun custom(key: String): Int? =
            prefs.getInt(key, unset).takeIf { it != unset }
        return CardPaletteSelection(
            presetId = prefs.getString(KEY_PRESET, CardPalettes.DEFAULT_PRESET)
                ?: CardPalettes.DEFAULT_PRESET,
            customTotal = custom(KEY_CUSTOM_TOTAL),
            customAccounts = custom(KEY_CUSTOM_ACCOUNTS),
            customEsim = custom(KEY_CUSTOM_ESIM)
        )
    }

    private companion object {
        const val PREFS_NAME = "heymoney_card_palettes"
        const val KEY_PRESET = "preset_id"
        const val KEY_CUSTOM_TOTAL = "custom_total"
        const val KEY_CUSTOM_ACCOUNTS = "custom_accounts"
        const val KEY_CUSTOM_ESIM = "custom_esim"
    }
}
