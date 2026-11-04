package org.abodah.heymoney.palettes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.palettes.CardPaletteColors
import com.ivy.base.palettes.CardPaletteSelection
import com.ivy.base.palettes.CardPaletteStore
import com.ivy.base.palettes.CardPalettes
import com.ivy.base.palettes.PaletteRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Configuration screen for the stacked-card palettes: preset palettes plus
 * per-card custom colours. All state lives in [CardPaletteStore] (persisted,
 * shared with the Accounts tab and the eSIM screens), so changes apply
 * immediately everywhere.
 */
@HiltViewModel
class CardPalettesViewModel @Inject constructor(
    private val store: CardPaletteStore
) : ViewModel() {
    val selection: StateFlow<CardPaletteSelection> = store.selection

    val colors: StateFlow<CardPaletteColors> = store.selection
        .map { CardPalettes.colors(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.colors())

    fun selectPreset(presetId: String) = store.selectPreset(presetId)

    fun pickColor(role: PaletteRole, argb: Int) = store.setCustomColor(role, argb)

    fun reset() = store.reset()

    /** The colour currently in effect for one role (for swatch highlighting). */
    fun roleColor(selection: CardPaletteSelection, role: PaletteRole): Int =
        if (selection.isCustom) {
            when (role) {
                PaletteRole.TOTAL -> selection.customTotal
                PaletteRole.ACCOUNTS -> selection.customAccounts
                PaletteRole.ESIM -> selection.customEsim
            } ?: CardPalettes.baseColor(CardPalettes.DEFAULT_PRESET, role)
        } else {
            CardPalettes.baseColor(selection.presetId, role)
        }
}
