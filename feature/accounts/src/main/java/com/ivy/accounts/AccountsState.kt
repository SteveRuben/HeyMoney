package com.ivy.accounts

import androidx.compose.runtime.Immutable
import com.ivy.base.palettes.CardPaletteColors
import com.ivy.base.palettes.CardPalettes
import com.ivy.legacy.data.model.AccountData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Sum of included accounts in one currency that has no exchange rate to the base currency. */
@Immutable
data class CurrencySubtotal(
    val currency: String,
    val amount: Double
)

@Immutable
data class AccountsState(
    val baseCurrency: String,
    val accountsData: ImmutableList<AccountData>,
    val totalBalanceWithExcluded: String,
    val totalBalanceWithExcludedText: String,
    val totalBalanceWithoutExcluded: String,
    val totalBalanceWithoutExcludedText: String,
    val reorderVisible: Boolean,
    val compactAccountsModeEnabled: Boolean,
    val hideTotalBalance: Boolean,
    /** Resolved card palette (Steve's configurable palettes, Nov 2026). */
    val palette: CardPaletteColors = CardPalettes.preset(CardPalettes.DEFAULT_PRESET),
    /**
     * Balances that could NOT be converted into [baseCurrency] (no rate set),
     * grouped per currency. They are excluded from the hero total and shown
     * separately — never silently merged with a made-up rate.
     */
    val unconvertedSubtotals: ImmutableList<CurrencySubtotal> = persistentListOf()
)
