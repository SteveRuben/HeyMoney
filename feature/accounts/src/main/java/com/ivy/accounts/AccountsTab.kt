package com.ivy.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.legacy.Theme
import com.ivy.base.palettes.CardPaletteColors
import com.ivy.base.palettes.CardPalettes
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.data.model.AccountData
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.horizontalSwipeListener
import com.ivy.legacy.utils.rememberSwipeListenerState
import com.ivy.navigation.EsimScreen
import com.ivy.navigation.TransactionsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.ui.rememberScrollPositionListState
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GreenLight
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.components.ReorderButton
import com.ivy.wallet.ui.theme.components.ReorderModalSingleType
import com.ivy.wallet.ui.theme.modal.edit.AccountModal
import com.ivy.wallet.ui.theme.modal.edit.AccountModalData
import kotlinx.collections.immutable.persistentListOf
import java.util.Currency
import java.util.UUID

/** Near-black used for card borders and text on pastel cards (LemFi stack look). */
private val CardInk = Color(0xFF1D1B18)
private val CardBorder = Color(0xFF1A1A1A)
private val CardShape = RoundedCornerShape(28.dp)

@Composable
fun BoxWithConstraintsScope.AccountsTab() {
    val viewModel: AccountsViewModel = screenScopedViewModel()
    val uiState = viewModel.uiState()

    UI(
        state = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun BoxWithConstraintsScope.UI(
    state: AccountsState,
    onEvent: (AccountsEvent) -> Unit = {}
) {
    val nav = navigation()
    val ivyContext = com.ivy.legacy.ivyWalletCtx()
    var listState = rememberLazyListState()
    if (!state.accountsData.isEmpty()) {
        listState = rememberScrollPositionListState(
            key = "accounts_lazy_column",
            initialFirstVisibleItemIndex = ivyContext.accountsListState?.firstVisibleItemIndex ?: 0,
            initialFirstVisibleItemScrollOffset = ivyContext.accountsListState?.firstVisibleItemScrollOffset
                ?: 0
        )
    }

    var accountModalData by remember { mutableStateOf<AccountModalData?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .horizontalSwipeListener(
                sensitivity = 200,
                state = rememberSwipeListenerState(),
                onSwipeLeft = {
                    ivyContext.selectMainTab(com.ivy.legacy.data.model.MainTab.HOME)
                },
                onSwipeRight = {
                    ivyContext.selectMainTab(com.ivy.legacy.data.model.MainTab.HOME)
                }
            ),
        state = listState
    ) {
        item {
            Spacer(Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(24.dp))

                Column {
                    Text(
                        text = stringResource(R.string.accounts),
                        style = TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = UI.colors.pureInverse
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.accounts_subtitle,
                            state.accountsData.size,
                            state.baseCurrency
                        ),
                        style = UI.typo.b2.style(
                            color = UI.colors.pureInverse.copy(alpha = 0.65f)
                        )
                    )
                }

                Spacer(Modifier.weight(1f))

                ReorderButton {
                    onEvent(
                        AccountsEvent.OnReorderModalVisible(reorderVisible = true)
                    )
                }

                Spacer(Modifier.width(24.dp))
            }
        }

        if (!state.hideTotalBalance) {
            item {
                Spacer(Modifier.height(20.dp))
                TotalBalanceCard(
                    palette = state.palette,
                    baseCurrency = state.baseCurrency,
                    totalBalance = state.totalBalanceWithoutExcluded.toDoubleOrNull() ?: 0.0
                )
                state.unconvertedSubtotals.forEach { subtotal ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(
                            R.string.accounts_unconverted,
                            subtotal.amount.amountWithSymbol(subtotal.currency)
                        ),
                        style = UI.typo.c.style(
                            color = UI.colors.pureInverse.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            EsimEntryCard(palette = state.palette) {
                nav.navigateTo(EsimScreen)
            }
        }

        itemsIndexed(state.accountsData) { index, accountData ->
            Spacer(Modifier.height(12.dp))
            val tints = state.palette.accountTints
            AccountStackCard(
                accountData = accountData,
                baseCurrency = state.baseCurrency,
                tint = if (tints.isEmpty()) {
                    state.palette.totalGradientEnd
                } else {
                    tints[index % tints.size]
                }
            ) {
                nav.navigateTo(
                    TransactionsScreen(
                        accountId = accountData.account.id.value,
                        categoryId = null
                    )
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))

            AddAccountButton {
                accountModalData = AccountModalData(
                    account = null,
                    baseCurrency = state.baseCurrency,
                    balance = 0.0
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                text = stringResource(R.string.accounts_local_note),
                style = UI.typo.c.style(
                    color = UI.colors.pureInverse.copy(alpha = 0.6f)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(150.dp)) // scroll hack
        }
    }

    AccountModal(
        modal = accountModalData,
        onCreateAccount = {
            onEvent(AccountsEvent.OnCreateAccount(it))
            accountModalData = null
        },
        onEditAccount = { _, _ -> },
        dismiss = { accountModalData = null }
    )

    ReorderModalSingleType(
        visible = state.reorderVisible,
        initialItems = state.accountsData,
        dismiss = {
            onEvent(AccountsEvent.OnReorderModalVisible(reorderVisible = false))
        },
        onReordered = {
            onEvent(AccountsEvent.OnReorder(reorderedList = it))
        }
    ) { _, item ->
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 24.dp)
                .padding(vertical = 8.dp),
            text = item.account.name.value,
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun TotalBalanceCard(
    palette: CardPaletteColors,
    baseCurrency: String,
    totalBalance: Double
) {
    val textColor = Color(palette.totalText)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(palette.totalGradientStart),
                        Color(palette.totalGradientEnd)
                    )
                ),
                shape = CardShape
            )
            .border(2.dp, CardBorder, CardShape)
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Text(
            text = stringResource(R.string.accounts_total_label),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.85f),
                letterSpacing = 1.2.sp
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = totalBalance.amountWithSymbol(baseCurrency),
            style = TextStyle(
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.accounts_total_converted, baseCurrency),
            style = UI.typo.b2.style(color = textColor.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun EsimEntryCard(
    palette: CardPaletteColors,
    onClick: () -> Unit
) {
    val textColor = Color(palette.esimText)
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color(palette.esimBackground), CardShape)
            .border(2.dp, CardBorder, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.esim_title),
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF141414))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.esim_new_badge),
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.esim_entry_sub),
                style = UI.typo.b2.style(color = textColor.copy(alpha = 0.75f))
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.esim_stay_connected),
                style = TextStyle(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.esim_from),
                style = UI.typo.b2.style(color = textColor.copy(alpha = 0.75f))
            )
        }
    }
}

@Composable
private fun AccountStackCard(
    accountData: AccountData,
    baseCurrency: String,
    tint: Int,
    onClick: () -> Unit
) {
    val account = accountData.account
    val currency = account.asset.code
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color(tint), CardShape)
            .border(2.dp, CardBorder, CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .border(1.5.dp, CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (account.icon != null) {
                ItemIconSDefaultIcon(
                    iconName = account.icon?.id,
                    defaultIcon = R.drawable.ic_custom_account_s,
                    tint = CardInk
                )
            } else {
                Text(
                    text = account.name.value.firstOrNull()?.uppercase() ?: "?",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CardInk
                    )
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name.value,
                style = UI.typo.b1.style(
                    color = CardInk,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (account.includeInBalance) {
                    currency
                } else {
                    "$currency · ${stringResource(R.string.excluded)}"
                },
                style = UI.typo.c.style(color = CardInk.copy(alpha = 0.6f))
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = accountData.balance.amountWithSymbol(currency),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CardInk
                )
            )
            if (currency != baseCurrency && accountData.balanceBaseCurrency != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "≈ ${accountData.balanceBaseCurrency.amountWithSymbol(baseCurrency)} " +
                        baseCurrency,
                    style = UI.typo.c.style(color = CardInk.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
private fun AddAccountButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF141414), RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.accounts_add_account),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
    }
}

/** "1 280,00 $"-style label: Ivy amount formatting + the currency's symbol. */
private fun Double.amountWithSymbol(currencyCode: String): String {
    val symbol = try {
        Currency.getInstance(currencyCode)?.symbol
    } catch (e: IllegalArgumentException) {
        null
    } ?: currencyCode
    return "${format(currencyCode)} $symbol"
}

@Preview
@Composable
private fun PreviewAccountsTab(theme: Theme = Theme.LIGHT) {
    IvyWalletPreview(theme = theme) {
        val acc1 = Account(
            id = AccountId(UUID.randomUUID()),
            name = NotBlankTrimmedString.unsafe("Desjardins"),
            color = ColorInt(Green.toArgb()),
            asset = AssetCode.unsafe("CAD"),
            icon = null,
            includeInBalance = true,
            orderNum = 0.0,
        )

        val acc2 = Account(
            id = AccountId(UUID.randomUUID()),
            name = NotBlankTrimmedString.unsafe("MTN MoMo"),
            color = ColorInt(GreenLight.toArgb()),
            asset = AssetCode.unsafe("XAF"),
            icon = null,
            includeInBalance = true,
            orderNum = 1.0,
        )

        val acc3 = Account(
            id = AccountId(UUID.randomUUID()),
            name = NotBlankTrimmedString.unsafe("Cash USD"),
            color = ColorInt(Green.toArgb()),
            asset = AssetCode.unsafe("USD"),
            icon = IconAsset.unsafe("cash"),
            includeInBalance = true,
            orderNum = 2.0,
        )
        val state = AccountsState(
            baseCurrency = "CAD",
            accountsData = persistentListOf(
                AccountData(
                    account = acc1,
                    balance = 8240.10,
                    balanceBaseCurrency = null,
                    monthlyExpenses = 920.0,
                    monthlyIncome = 3045.0
                ),
                AccountData(
                    account = acc2,
                    balance = 532480.0,
                    balanceBaseCurrency = 1280.0,
                    monthlyExpenses = 1350.50,
                    monthlyIncome = 8000.48
                ),
                AccountData(
                    account = acc3,
                    balance = 1200.0,
                    balanceBaseCurrency = null,
                    monthlyExpenses = 750.0,
                    monthlyIncome = 1000.30
                )
            ),
            totalBalanceWithExcluded = "9520.10",
            totalBalanceWithExcludedText = "CAD 9520.10",
            totalBalanceWithoutExcluded = "9520.10",
            totalBalanceWithoutExcludedText = "CAD 9520.10",
            reorderVisible = false,
            compactAccountsModeEnabled = false,
            hideTotalBalance = false,
            palette = CardPalettes.preset(CardPalettes.PRESET_BLUE),
            unconvertedSubtotals = persistentListOf(
                CurrencySubtotal(currency = "USD", amount = 1200.0)
            )
        )
        UI(state = state)
    }
}

/** For screen shot testing **/
@Composable
fun AccountsTabNonCompactUITest(dark: Boolean) {
    val theme = when (dark) {
        true -> Theme.DARK
        false -> Theme.LIGHT
    }
    PreviewAccountsTab(theme)
}

/** For screen shot testing **/
@Composable
fun AccountsTabCompactUITest(dark: Boolean) {
    val theme = when (dark) {
        true -> Theme.DARK
        false -> Theme.LIGHT
    }
    PreviewAccountsTab(theme)
}
