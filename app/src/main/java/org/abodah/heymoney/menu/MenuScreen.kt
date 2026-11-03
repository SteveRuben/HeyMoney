package org.abodah.heymoney.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.legacy.Theme
import com.ivy.base.model.TransactionType
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.l1_buildingBlocks.IconScale
import com.ivy.design.l1_buildingBlocks.IvyIconScaled
import com.ivy.legacy.data.model.MainTab
import com.ivy.legacy.ivyWalletCtx
import com.ivy.legacy.rootScreen
import com.ivy.navigation.AiSettingsScreen
import com.ivy.navigation.AlertsScreen
import com.ivy.navigation.BudgetScreen
import com.ivy.navigation.CategoriesScreen
import com.ivy.navigation.CategoryRulesScreen
import com.ivy.navigation.ExchangeRatesScreen
import com.ivy.navigation.FeaturesScreen
import com.ivy.navigation.ImportScreen
import com.ivy.navigation.LoansScreen
import com.ivy.navigation.MessageScanScreen
import com.ivy.navigation.MonthlyChartScreen
import com.ivy.navigation.NetWorthScreen
import com.ivy.navigation.PieChartStatisticScreen
import com.ivy.navigation.PlannedPaymentsScreen
import com.ivy.navigation.ReportScreen
import com.ivy.navigation.ScanReceiptScreen
import com.ivy.navigation.SearchScreen
import com.ivy.navigation.SettingsScreen
import com.ivy.navigation.SetupScreen
import com.ivy.navigation.ShareScreen
import com.ivy.navigation.AssistantScreen
import com.ivy.navigation.StatementAnalysisScreen
import com.ivy.navigation.SubscriptionsScreen
import com.ivy.navigation.VaultScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gradient
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.modal.BufferModal
import com.ivy.wallet.ui.theme.modal.BufferModalData
import org.abodah.heymoney.networth.CurrencyNetWorth
import org.abodah.heymoney.setup.SetupProgress
import org.abodah.heymoney.subscriptions.formatMoney

/**
 * The single Menu: every feature of the app reachable from here, grouped by
 * what it is for — the money itself, tracking it, tools, settings. Headline
 * cards show the real net worth and subscriptions yearly cost, computed by
 * the same engines as their own screens (see [MenuViewModel]).
 *
 * Rendered with the app's own design system — the same toolbar, big title,
 * icon rows, typography and shapes as the native Settings screen — so the
 * Menu looks like part of the app, not an add-on. Every row routes to an
 * existing screen or triggers an existing action (export, backup, theme);
 * this screen adds no feature of its own.
 */
@Composable
fun BoxWithConstraintsScope.MenuScreen(
    viewModel: MenuViewModel = screenScopedViewModel()
) {
    val nav = navigation()
    val rootScreen = rootScreen()
    val ivyContext = ivyWalletCtx()

    val netWorthReport by viewModel.netWorthReport.collectAsState()
    val subsYearlyTotals by viewModel.subsYearlyTotals.collectAsState()
    val pendingAlerts by viewModel.pendingAlertsCount.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val bufferAmount by viewModel.bufferAmount.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val walletBalance by viewModel.walletBalance.collectAsState()
    val setupProgress by viewModel.setupProgress.collectAsState()

    var bufferModalData: BufferModalData? by remember { mutableStateOf(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        stickyHeader {
            IvyToolbar(
                onBack = { nav.onBackPressed() }
            )
        }

        item {
            Spacer(Modifier.height(8.dp))

            Text(
                modifier = Modifier.padding(start = 32.dp),
                text = stringResource(R.string.menu_title),
                style = UI.typo.h2.style(
                    fontWeight = FontWeight.Black
                )
            )

            Spacer(Modifier.height(24.dp))

            MenuRow(
                icon = R.drawable.ic_search,
                title = stringResource(R.string.menu_search),
                titleColorGray = true
            ) {
                nav.navigateTo(SearchScreen)
            }

            Spacer(Modifier.height(12.dp))

            NetWorthPanel(report = netWorthReport) {
                nav.navigateTo(NetWorthScreen)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                SubscriptionsCard(
                    modifier = Modifier.weight(1f),
                    yearlyTotals = subsYearlyTotals
                ) {
                    nav.navigateTo(SubscriptionsScreen)
                }
                MonthlyChartCard(
                    modifier = Modifier.weight(1f)
                ) {
                    nav.navigateTo(MonthlyChartScreen)
                }
            }

            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_vue_money_wallet_money,
                title = stringResource(R.string.savings_goal),
                description = formatMoney(bufferAmount, baseCurrency)
            ) {
                bufferModalData = BufferModalData(
                    balance = walletBalance,
                    currency = baseCurrency,
                    buffer = bufferAmount
                )
            }

            Spacer(Modifier.height(12.dp))

            SetupCard(progress = setupProgress) {
                nav.navigateTo(SetupScreen)
            }
        }

        // ------------------------------ Mon argent ------------------------------
        item {
            MenuSectionDivider(text = stringResource(R.string.menu_section_money))

            Spacer(Modifier.height(16.dp))

            MenuRow(
                icon = R.drawable.ic_accounts,
                title = stringResource(R.string.menu_item_accounts)
            ) {
                ivyContext.selectMainTab(MainTab.ACCOUNTS)
                nav.back()
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.home_more_menu_categories,
                title = stringResource(R.string.menu_item_categories)
            ) {
                nav.navigateTo(CategoriesScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.home_more_menu_planned_payments,
                title = stringResource(R.string.menu_item_planned)
            ) {
                nav.navigateTo(PlannedPaymentsScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.home_more_menu_loans,
                title = stringResource(R.string.menu_item_loans)
            ) {
                nav.navigateTo(LoansScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.home_more_menu_budgets,
                title = stringResource(R.string.menu_item_budgets)
            ) {
                nav.navigateTo(BudgetScreen)
            }
        }

        // -------------------------------- Suivi --------------------------------
        item {
            MenuSectionDivider(text = stringResource(R.string.menu_section_tracking))

            Spacer(Modifier.height(16.dp))

            MenuRow(
                icon = R.drawable.home_more_menu_reports,
                title = stringResource(R.string.menu_item_reports)
            ) {
                nav.navigateTo(ReportScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_custom_stats_m,
                title = stringResource(R.string.menu_item_stats)
            ) {
                nav.navigateTo(
                    PieChartStatisticScreen(type = TransactionType.EXPENSE)
                )
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_notification_m,
                title = stringResource(R.string.menu_item_alerts),
                badgeCount = pendingAlerts
            ) {
                nav.navigateTo(AlertsScreen)
            }
        }

        // -------------------------------- Outils -------------------------------
        item {
            MenuSectionDivider(text = stringResource(R.string.menu_section_tools))

            Spacer(Modifier.height(16.dp))

            MenuRow(
                icon = R.drawable.ic_vue_money_receipt_items,
                title = stringResource(R.string.menu_item_scan)
            ) {
                nav.navigateTo(ScanReceiptScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_custom_atom_m,
                title = stringResource(R.string.menu_item_assistant)
            ) {
                nav.navigateTo(AssistantScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_share,
                title = stringResource(R.string.menu_item_qr_share)
            ) {
                nav.navigateTo(ShareScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_filter_l,
                title = stringResource(R.string.menu_item_rules),
                description = stringResource(R.string.menu_item_rules_sub)
            ) {
                nav.navigateTo(CategoryRulesScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_export_csv,
                title = stringResource(R.string.menu_item_import)
            ) {
                nav.navigateTo(ImportScreen(launchedFromOnboarding = false))
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_custom_document_m,
                title = stringResource(R.string.menu_item_statement_analysis),
                description = stringResource(R.string.menu_item_statement_analysis_sub)
            ) {
                nav.navigateTo(StatementAnalysisScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_vue_security_lock,
                title = stringResource(R.string.menu_item_vault),
                description = stringResource(R.string.menu_item_vault_sub)
            ) {
                nav.navigateTo(VaultScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_vue_messages_msg_text,
                title = stringResource(R.string.menu_item_messages),
                description = stringResource(R.string.menu_item_messages_sub)
            ) {
                nav.navigateTo(MessageScanScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_vue_pc_printer,
                title = stringResource(R.string.menu_item_export_csv)
            ) {
                viewModel.exportCsv(rootScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_vue_security_shield,
                title = stringResource(R.string.menu_item_backup),
                iconPadding = 8.dp
            ) {
                viewModel.backup(rootScreen)
            }
        }

        // ------------------------------- Réglages ------------------------------
        item {
            MenuSectionDivider(text = stringResource(R.string.menu_section_settings))

            Spacer(Modifier.height(16.dp))

            MenuRow(
                icon = R.drawable.home_more_menu_settings,
                title = stringResource(R.string.menu_item_settings)
            ) {
                nav.navigateTo(SettingsScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_currency,
                title = stringResource(R.string.menu_item_exchange_rates)
            ) {
                nav.navigateTo(ExchangeRatesScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_vue_pc_setting,
                title = stringResource(R.string.menu_item_ai),
                description = stringResource(R.string.menu_item_ai_sub)
            ) {
                nav.navigateTo(AiSettingsScreen)
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = when (theme) {
                    Theme.LIGHT -> R.drawable.home_more_menu_light_mode
                    Theme.DARK -> R.drawable.home_more_menu_dark_mode
                    Theme.AMOLED_DARK -> R.drawable.home_more_menu_amoled_dark_mode
                    Theme.AUTO -> R.drawable.home_more_menu_auto_mode
                },
                title = stringResource(R.string.menu_item_theme),
                description = stringResource(
                    when (theme) {
                        Theme.LIGHT -> R.string.light_mode
                        Theme.DARK -> R.string.dark_mode
                        Theme.AMOLED_DARK -> R.string.amoled_mode
                        Theme.AUTO -> R.string.auto_mode
                    }
                ),
                iconPadding = 6.dp
            ) {
                viewModel.switchTheme()
            }
        }
        item {
            Spacer(Modifier.height(12.dp))

            MenuRow(
                icon = R.drawable.ic_custom_programming_m,
                title = stringResource(R.string.advanced_features),
                description = stringResource(R.string.menu_item_advanced_sub)
            ) {
                nav.navigateTo(FeaturesScreen)
            }
        }

        item {
            Spacer(modifier = Modifier.height(120.dp)) // last item spacer, as in Settings
        }
    }

    BufferModal(
        modal = bufferModalData,
        dismiss = { bufferModalData = null },
        onBufferChanged = viewModel::setBuffer
    )
}

/**
 * One Menu row, built exactly like the native Settings rows: medium
 * background, r4 shape, line icon on the left, Raleway bold label and an
 * optional gray description — compare SettingsDefaultButton in
 * feature/settings, which is private to that module and cannot be called
 * from here, so its recipe is reproduced with the same design-system parts.
 */
@Composable
private fun MenuRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    iconPadding: Dp = 0.dp,
    description: String? = null,
    badgeCount: Int = 0,
    titleColorGray: Boolean = false,
    horizontalPadding: Dp = 16.dp,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .padding(horizontal = horizontalPadding)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(
                Gradient.solid(UI.colors.medium).asHorizontalBrush(),
                UI.shapes.r4
            )
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))

        IvyIconScaled(
            icon = icon,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = iconPadding
        )

        Spacer(Modifier.width(8.dp))

        Column(
            Modifier
                .weight(1f)
                .padding(top = 20.dp, bottom = 20.dp, end = 8.dp)
        ) {
            Text(
                text = title,
                style = UI.typo.b2.style(
                    color = if (titleColorGray) UI.colors.gray else UI.colors.pureInverse,
                    fontWeight = if (titleColorGray) FontWeight.Normal else FontWeight.Bold
                )
            )
            if (!description.isNullOrEmpty()) {
                Text(
                    modifier = Modifier.padding(end = 8.dp),
                    text = description,
                    style = UI.typo.nB2.style(
                        color = Gray,
                        fontWeight = FontWeight.Normal
                    ).copy(fontSize = 14.sp)
                )
            }
        }

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(UI.shapes.circle)
                    .background(Red),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = UI.typo.c.style(
                        color = White,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun MenuSectionDivider(
    text: String
) {
    Column {
        Spacer(Modifier.height(20.dp))

        Text(
            modifier = Modifier.padding(start = 32.dp),
            text = text,
            style = UI.typo.b2.style(
                color = Gray,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * The net worth headline, treated like the native Settings account card:
 * r2 panel on the medium background, small gray label on top, icon and the
 * real figures in the app's bold number typography.
 */
@Composable
private fun NetWorthPanel(
    report: List<CurrencyNetWorth>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r2)
            .background(UI.colors.medium, UI.shapes.r2)
            .clickable { onClick() }
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(24.dp))

            Text(
                text = stringResource(R.string.nw_net_label),
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Black,
                    color = UI.colors.gray
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(20.dp))

            IvyIconScaled(
                icon = R.drawable.ic_vue_money_wallet,
                iconScale = IconScale.M
            )

            Spacer(Modifier.width(12.dp))

            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                if (report.isEmpty()) {
                    Text(
                        text = stringResource(R.string.menu_net_worth_sub),
                        style = UI.typo.b2.style(
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    report.forEach { currencyNetWorth ->
                        Text(
                            text = formatMoney(
                                currencyNetWorth.netWorth,
                                currencyNetWorth.currency
                            ),
                            style = UI.typo.b1.style(
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SubscriptionsCard(
    yearlyTotals: Map<String?, Double>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val yearlyLines = mutableListOf<String>()
    yearlyTotals.forEach { (currency, total) ->
        yearlyLines += stringResource(
            R.string.subs_per_year,
            formatMoney(total, currency)
        )
    }
    MenuRow(
        icon = R.drawable.ic_add_recurring,
        title = stringResource(R.string.subs_title),
        modifier = modifier,
        description = if (yearlyTotals.isEmpty()) {
            stringResource(R.string.menu_subs_hint)
        } else {
            yearlyLines.joinToString(separator = "\n")
        },
        horizontalPadding = 8.dp,
        onClick = onClick
    )
}

@Composable
private fun MonthlyChartCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    MenuRow(
        icon = R.drawable.ic_vue_chart_status_up,
        title = stringResource(R.string.chart_title),
        modifier = modifier,
        description = stringResource(R.string.menu_chart_sub),
        horizontalPadding = 8.dp,
        onClick = onClick
    )
}

@Composable
private fun SetupCard(
    progress: SetupProgress,
    onClick: () -> Unit
) {
    MenuRow(
        icon = R.drawable.ic_custom_gears_m,
        title = stringResource(R.string.setup_title),
        description = if (progress.isComplete) {
            stringResource(R.string.setup_menu_done)
        } else {
            stringResource(
                R.string.setup_progress_count,
                progress.doneCount,
                progress.actionableCount
            )
        },
        onClick = onClick
    )
}
