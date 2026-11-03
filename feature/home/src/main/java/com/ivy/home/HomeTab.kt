package com.ivy.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ivy.base.legacy.Theme
import com.ivy.base.legacy.Transaction
import com.ivy.base.legacy.TransactionHistoryItem
import com.ivy.base.legacy.stringRes
import com.ivy.design.api.LocalTimeConverter
import com.ivy.design.api.LocalTimeFormatter
import com.ivy.design.api.LocalTimeProvider
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.frp.forward
import com.ivy.frp.then2
import com.ivy.home.Constants.SWIPE_HORIZONTAL_THRESHOLD
import com.ivy.home.customerjourney.CustomerJourney
import com.ivy.home.customerjourney.CustomerJourneyCardModel
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.data.AppBaseData
import com.ivy.legacy.data.BufferInfo
import com.ivy.legacy.data.LegacyDueSection
import com.ivy.legacy.data.model.MainTab
import com.ivy.legacy.data.model.Month
import com.ivy.legacy.data.model.TimePeriod
import com.ivy.legacy.ivyWalletCtx
import com.ivy.legacy.ui.component.transaction.TransactionsDividerLine
import com.ivy.legacy.ui.component.transaction.transactions
import com.ivy.legacy.utils.horizontalSwipeListener
import com.ivy.legacy.utils.rememberSwipeListenerState
import com.ivy.legacy.utils.statusBarInset
import com.ivy.legacy.utils.toDensityPx
import com.ivy.legacy.utils.verticalSwipeListener
import com.ivy.navigation.IvyPreview
import com.ivy.navigation.MenuScreen
import com.ivy.navigation.MonthlyChartScreen
import com.ivy.navigation.NetWorthScreen
import com.ivy.navigation.SubscriptionsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.ui.rememberScrollPositionListState
import com.ivy.wallet.domain.pure.data.IncomeExpensePair
import com.ivy.wallet.ui.theme.components.CircleButtonFilled
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModal
import com.ivy.wallet.ui.theme.modal.ChoosePeriodModalData
import com.ivy.wallet.ui.theme.modal.DeleteModal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.math.BigDecimal
import kotlin.math.roundToInt

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.HomeTab() {
    val viewModel: HomeViewModel = screenScopedViewModel()
    val quickAccessViewModel: QuickAccessViewModel = screenScopedViewModel()
    val uiState = viewModel.uiState()
    val netWorthText by quickAccessViewModel.netWorthText.collectAsState()

    HomeUi(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        netWorthText = if (uiState.hideBalance) null else netWorthText
    )
}

@Suppress("LongMethod")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.HomeUi(
    uiState: HomeState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
    netWorthText: String? = null,
) {
    val ivyContext = ivyWalletCtx()
    val nav = navigation()

    var choosePeriodModal: ChoosePeriodModalData? by remember {
        mutableStateOf(null)
    }
    var skipAllModalVisible by remember { mutableStateOf(false) }

    val baseCurrency = uiState.baseData.baseCurrency

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalSwipeListener(
                sensitivity = Constants.SWIPE_DOWN_THRESHOLD_OPEN_MORE_MENU,
                state = rememberSwipeListenerState(),
                onSwipeDown = {
                    nav.navigateTo(MenuScreen)
                }
            )
            .horizontalSwipeListener(
                sensitivity = SWIPE_HORIZONTAL_THRESHOLD,
                state = rememberSwipeListenerState(),
                onSwipeLeft = {
                    ivyContext.selectMainTab(MainTab.ACCOUNTS)
                },
                onSwipeRight = {
                    ivyContext.selectMainTab(MainTab.ACCOUNTS)
                }
            )
    ) {
        val listState = rememberScrollPositionListState(
            key = "home_lazy_column",
            initialFirstVisibleItemIndex = ivyContext.transactionsListState
                ?.firstVisibleItemIndex ?: 0,
            initialFirstVisibleItemScrollOffset = ivyContext.transactionsListState
                ?.firstVisibleItemScrollOffset ?: 0
        )

        HomeHeader(
            expanded = uiState.expanded,
            name = uiState.name,
            period = uiState.period,
            currency = baseCurrency,
            balance = uiState.balance.toDouble(),
            hideBalance = uiState.hideBalance,

            onShowMonthModal = {
                choosePeriodModal = ChoosePeriodModalData(
                    period = uiState.period
                )
            },
            onBalanceClick = {
                onEvent(HomeEvent.BalanceClick)
            },
            onHiddenBalanceClick = {
                onEvent(HomeEvent.HiddenBalanceClick)
            },
            onSelectNextMonth = {
                onEvent(HomeEvent.SelectNextMonth)
            },
            onSelectPreviousMonth = {
                onEvent(HomeEvent.SelectPreviousMonth)
            }
        )

        HomeLazyColumn(
            hideBalance = uiState.hideBalance,
            hideIncome = uiState.hideIncome,
            onSetExpand = {
                onEvent(HomeEvent.SetExpanded(it))
            },
            balance = uiState.balance,
            onOpenMoreMenu = {
                nav.navigateTo(MenuScreen)
            },
            onBalanceClick = {
                onEvent(HomeEvent.BalanceClick)
            },
            onHiddenBalanceClick = {
                onEvent(HomeEvent.HiddenBalanceClick)
            },
            onHiddenIncomeClick = {
                onEvent(HomeEvent.HiddenIncomeClick)
            },

            period = uiState.period,
            listState = listState,

            netWorthText = netWorthText,

            baseData = uiState.baseData,

            upcoming = uiState.upcoming,
            overdue = uiState.overdue,

            stats = uiState.stats,
            history = uiState.history,

            customerJourneyCards = uiState.customerJourneyCards,
            shouldShowAccountSpecificColorInTransactions = uiState.shouldShowAccountSpecificColorInTransactions,

            onPayOrGet = forward<Transaction>() then2 {
                HomeEvent.PayOrGetPlanned(it)
            } then2 onEvent,
            onDismiss = forward<CustomerJourneyCardModel>() then2 {
                HomeEvent.DismissCustomerJourneyCard(it)
            } then2 onEvent,
            onSkipTransaction = forward<Transaction>() then2 {
                HomeEvent.SkipPlanned(it)
            } then2 onEvent,
            setUpcomingExpanded = forward<Boolean>() then2 {
                HomeEvent.SetUpcomingExpanded(it)
            } then2 onEvent,
            setOverdueExpanded = forward<Boolean>() then2 {
                HomeEvent.SetOverdueExpanded(it)
            } then2 onEvent,
            onSkipAllTransactions = {
                skipAllModalVisible = true
            }
        )
    }

    MenuArrowButton {
        nav.navigateTo(MenuScreen)
    }

    ChoosePeriodModal(
        modal = choosePeriodModal,
        dismiss = {
            choosePeriodModal = null
        },
        onPeriodSelected = forward<TimePeriod>() then2 {
            HomeEvent.SetPeriod(it)
        } then2 onEvent
    )

    DeleteModal(
        visible = skipAllModalVisible,
        title = stringResource(R.string.confirm_skip_all),
        description = stringResource(R.string.confirm_skip_all_description),
        dismiss = {
            skipAllModalVisible = false
        }
    ) {
        onEvent(HomeEvent.SkipAllPlanned(uiState.overdue.trns))
        skipAllModalVisible = false
    }
}

@Suppress("LongParameterList")
@ExperimentalAnimationApi
@Composable
fun HomeLazyColumn(
    hideBalance: Boolean,
    hideIncome: Boolean,
    onSetExpand: (Boolean) -> Unit,
    listState: LazyListState,
    period: TimePeriod,

    netWorthText: String? = null,

    baseData: AppBaseData,
    shouldShowAccountSpecificColorInTransactions: Boolean,

    upcoming: LegacyDueSection,
    overdue: LegacyDueSection,
    balance: BigDecimal,
    stats: IncomeExpensePair,
    history: ImmutableList<TransactionHistoryItem>,

    customerJourneyCards: ImmutableList<CustomerJourneyCardModel>,

    setUpcomingExpanded: (Boolean) -> Unit,
    setOverdueExpanded: (Boolean) -> Unit,

    onOpenMoreMenu: () -> Unit,
    onBalanceClick: () -> Unit,

    onPayOrGet: (Transaction) -> Unit,
    onDismiss: (CustomerJourneyCardModel) -> Unit,
    onHiddenBalanceClick: () -> Unit,
    onHiddenIncomeClick: () -> Unit,
    onSkipTransaction: (Transaction) -> Unit,
    onSkipAllTransactions: (List<Transaction>) -> Unit,
    modifier: Modifier = Modifier
) {
    val ivyContext = ivyWalletCtx()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                onSetExpand(listState.firstVisibleItemScrollOffset == 0)
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    val timeProvider = LocalTimeProvider.current
    val timeConverter = LocalTimeConverter.current
    val timeFormatter = LocalTimeFormatter.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .testTag("home_lazy_column"),
        state = listState
    ) {
        item {
            CashFlowInfo(
                currency = baseData.baseCurrency,
                balance = balance.toDouble(),

                hideBalance = hideBalance,

                monthlyIncome = stats.income.toDouble(),
                monthlyExpenses = stats.expense.toDouble(),

                onOpenMoreMenu = onOpenMoreMenu,
                onBalanceClick = onBalanceClick,
                onHiddenBalanceClick = onHiddenBalanceClick,
                percentExpanded = 1f,
                hideIncome = hideIncome,
                onHiddenIncomeClick = onHiddenIncomeClick
            )
        }
        item {
            QuickAccessRow(netWorthText = netWorthText)
        }
        item {
            Spacer(Modifier.height(16.dp))

            TransactionsDividerLine()
        }

        item {
            CustomerJourney(
                customerJourneyCards = customerJourneyCards,
                onDismiss = onDismiss
            )
        }

        transactions(
            baseData = baseData,
            upcoming = upcoming,
            setUpcomingExpanded = setUpcomingExpanded,
            overdue = overdue,
            setOverdueExpanded = setOverdueExpanded,
            history = history,
            onPayOrGet = onPayOrGet,
            emptyStateTitle = stringRes(R.string.no_transactions),
            emptyStateText = stringRes(
                R.string.no_transactions_description,
                period.toDisplayLong(
                    startDateOfMonth = ivyContext.startDayOfMonth,
                    timeProvider = timeProvider,
                    timeConverter = timeConverter,
                    timeFormatter = timeFormatter,
                )
            ),
            shouldShowAccountSpecificColorInTransactions = shouldShowAccountSpecificColorInTransactions,
            onSkipTransaction = onSkipTransaction,
            onSkipAllTransactions = onSkipAllTransactions
        )
    }
}

/**
 * The ▼ button at the top-right of Home. It used to expand the old
 * overlay menu; there is only one menu now, so it opens the Menu screen
 * (same position, same look as before).
 */
@Composable
private fun BoxWithConstraintsScope.MenuArrowButton(
    onClick: () -> Unit
) {
    val ivyContext = ivyWalletCtx()

    val buttonSizePx = 40.dp.toDensityPx()
    val x = ivyContext.screenWidth - 24.dp.toDensityPx() - buttonSizePx
    val y = 20.dp.toDensityPx() + statusBarInset()

    CircleButtonFilled(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)

                layout(placeable.width, placeable.height) {
                    placeable.place(
                        x = x.roundToInt(),
                        y = y.roundToInt()
                    )
                }
            }
            .zIndex(520f)
            .testTag("home_more_menu_arrow"),
        backgroundColor = UI.colors.medium,
        icon = R.drawable.ic_expandarrow
    ) {
        onClick()
    }
}

/**
 * "Quick access" row on Home: the three flagship features, one tap away.
 * The net worth figure comes from [QuickAccessViewModel]; the other two
 * cards carry a plain descriptive subtitle (see its documentation).
 */
@Composable
private fun QuickAccessRow(
    netWorthText: String?
) {
    val nav = navigation()

    Column {
        Spacer(Modifier.height(24.dp))

        Text(
            modifier = Modifier.padding(start = 24.dp),
            text = stringResource(R.string.quick_access),
            style = UI.typo.b2.style()
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickAccessCard(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_vue_money_coins,
                label = stringResource(R.string.nw_title),
                value = netWorthText ?: stringResource(R.string.menu_net_worth_sub)
            ) {
                nav.navigateTo(NetWorthScreen)
            }

            QuickAccessCard(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_vue_money_receipt_items,
                label = stringResource(R.string.subs_title),
                value = stringResource(R.string.menu_subs_hint)
            ) {
                nav.navigateTo(SubscriptionsScreen)
            }

            QuickAccessCard(
                modifier = Modifier.weight(1f),
                icon = R.drawable.ic_vue_chart_status_up,
                label = stringResource(R.string.chart_title),
                value = stringResource(R.string.menu_chart_hint)
            ) {
                nav.navigateTo(MonthlyChartScreen)
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(UI.shapes.r4)
            .background(UI.colors.pure)
            .clickable {
                onClick()
            }
            .padding(12.dp)
    ) {
        IvyIcon(icon = icon)

        Spacer(Modifier.height(8.dp))

        Text(
            text = label,
            style = UI.typo.c.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = value,
            style = UI.typo.c.style(
                color = UI.colors.pureInverse
            )
        )
    }
}

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Preview
@Composable
private fun BoxWithConstraintsScope.PreviewHomeTab(isDark: Boolean = false) {
    IvyPreview(isDark) {
        HomeUi(
            uiState = HomeState(
                theme = Theme.AUTO,
                name = "",
                baseData = AppBaseData(
                    baseCurrency = "",
                    accounts = persistentListOf(),
                    categories = persistentListOf()
                ),
                balance = BigDecimal.ZERO,
                buffer = BufferInfo(
                    amount = BigDecimal.ZERO,
                    bufferDiff = BigDecimal.ZERO,
                ),
                customerJourneyCards = persistentListOf(),
                history = persistentListOf(),
                stats = IncomeExpensePair.zero(),
                upcoming = LegacyDueSection(
                    trns = persistentListOf(),
                    stats = IncomeExpensePair.zero(),
                    expanded = false,
                ),
                overdue = LegacyDueSection(
                    trns = persistentListOf(),
                    stats = IncomeExpensePair.zero(),
                    expanded = false,
                ),
                period = TimePeriod(month = Month.monthsList().first(), year = 2023),
                hideBalance = false,
                hideIncome = false,
                expanded = false,
                shouldShowAccountSpecificColorInTransactions = false
            ),
            onEvent = {}
        )
    }
}

/** For screenshot testing */
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun HomeUiTest(isDark: Boolean) {
    val theme = when (isDark) {
        true -> Theme.DARK
        false -> Theme.LIGHT
    }
    IvyWalletPreview(theme) {
        PreviewHomeTab(isDark)
    }
}
