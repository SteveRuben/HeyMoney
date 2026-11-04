package org.abodah.heymoney.esim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.palettes.CardPaletteColors
import com.ivy.base.palettes.CardPaletteStore
import com.ivy.base.palettes.CardPalettes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The eSIM screens only need the current card palette (banner + entry colours). */
@HiltViewModel
class EsimViewModel @Inject constructor(
    private val cardPaletteStore: CardPaletteStore
) : ViewModel() {
    val palette: StateFlow<CardPaletteColors> = cardPaletteStore.selection
        .map { CardPalettes.colors(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            cardPaletteStore.colors()
        )
}
