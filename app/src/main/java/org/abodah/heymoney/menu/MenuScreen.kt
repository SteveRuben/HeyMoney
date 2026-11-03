package org.abodah.heymoney.menu

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.base.legacy.Theme
import com.ivy.base.model.TransactionType
import com.ivy.design.system.colors.IvyColors.Gray
import com.ivy.legacy.data.model.MainTab
import com.ivy.legacy.ivyWalletCtx
import com.ivy.legacy.rootScreen
import com.ivy.navigation.AlertsScreen
import com.ivy.navigation.BudgetScreen
import com.ivy.navigation.CategoriesScreen
import com.ivy.navigation.CategoryRulesScreen
import com.ivy.navigation.ExchangeRatesScreen
import com.ivy.navigation.FeaturesScreen
import com.ivy.navigation.ImportScreen
import com.ivy.navigation.LoansScreen
import com.ivy.navigation.MonthlyChartScreen
import com.ivy.navigation.NetWorthScreen
import com.ivy.navigation.PieChartStatisticScreen
import com.ivy.navigation.PlannedPaymentsScreen
import com.ivy.navigation.ReportScreen
import com.ivy.navigation.ScanReceiptScreen
import com.ivy.navigation.SearchScreen
import com.ivy.navigation.SettingsScreen
import com.ivy.navigation.SetupScreen
import com.ivy.navigation.MessageScanScreen
import com.ivy.navigation.StatementAnalysisScreen
import com.ivy.navigation.VaultScreen
import com.ivy.navigation.ShareScreen
import com.ivy.navigation.AssistantScreen
import com.ivy.navigation.SubscriptionsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvyIcon
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
 * Every row routes to an existing screen or triggers an existing action
 * (export, backup, theme); this screen adds no feature of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.menu_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SearchCard {
                    nav.navigateTo(SearchScreen)
                }
            }

            item {
                NetWorthCard(report = netWorthReport) {
                    nav.navigateTo(NetWorthScreen)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }

            if (!setupProgress.isComplete) {
                item {
                    SetupCard(progress = setupProgress) {
                        nav.navigateTo(SetupScreen)
                    }
                }
            }

            // ----------------------- Abonnements & alertes ------------------------
            item { SectionHeader(stringResource(R.string.menu_section_subs_alerts)) }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_messages_device_msg,
                    title = stringResource(R.string.menu_item_alerts),
                    badgeCount = pendingAlerts
                ) {
                    nav.navigateTo(AlertsScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_messages_msg_text,
                    title = stringResource(R.string.menu_item_messages),
                    subtitle = stringResource(R.string.menu_item_messages_sub)
                ) {
                    nav.navigateTo(MessageScanScreen)
                }
            }

            // ------------------------------ Mon argent ------------------------------
            item { SectionHeader(stringResource(R.string.menu_section_money)) }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_accounts,
                    title = stringResource(R.string.menu_item_accounts)
                ) {
                    ivyContext.selectMainTab(MainTab.ACCOUNTS)
                    nav.back()
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.home_more_menu_categories,
                    title = stringResource(R.string.menu_item_categories)
                ) {
                    nav.navigateTo(CategoriesScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.home_more_menu_planned_payments,
                    title = stringResource(R.string.menu_item_planned)
                ) {
                    nav.navigateTo(PlannedPaymentsScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.home_more_menu_loans,
                    title = stringResource(R.string.menu_item_loans)
                ) {
                    nav.navigateTo(LoansScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.home_more_menu_budgets,
                    title = stringResource(R.string.menu_item_budgets)
                ) {
                    nav.navigateTo(BudgetScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_money_coins,
                    title = stringResource(R.string.savings_goal),
                    subtitle = formatMoney(bufferAmount, baseCurrency)
                ) {
                    bufferModalData = BufferModalData(
                        balance = walletBalance,
                        currency = baseCurrency,
                        buffer = bufferAmount
                    )
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_media_setting,
                    title = stringResource(R.string.menu_item_rules),
                    subtitle = stringResource(R.string.menu_item_rules_sub)
                ) {
                    nav.navigateTo(CategoryRulesScreen)
                }
            }

            // -------------------------------- Suivi --------------------------------
            item { SectionHeader(stringResource(R.string.menu_section_tracking)) }
            item {
                MenuItemCard(
                    icon = R.drawable.home_more_menu_reports,
                    title = stringResource(R.string.menu_item_reports)
                ) {
                    nav.navigateTo(ReportScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_chart_chart,
                    title = stringResource(R.string.menu_item_stats)
                ) {
                    nav.navigateTo(
                        PieChartStatisticScreen(type = TransactionType.EXPENSE)
                    )
                }
            }

            // -------------------------------- Outils -------------------------------
            item { SectionHeader(stringResource(R.string.menu_section_tools)) }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_money_receipt_items,
                    title = stringResource(R.string.menu_item_scan)
                ) {
                    nav.navigateTo(ScanReceiptScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_custom_document_m,
                    title = stringResource(R.string.menu_item_statement_analysis),
                    subtitle = stringResource(R.string.menu_item_statement_analysis_sub)
                ) {
                    nav.navigateTo(StatementAnalysisScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_design_magicpen,
                    title = stringResource(R.string.menu_item_assistant)
                ) {
                    nav.navigateTo(AssistantScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_security_lock,
                    title = stringResource(R.string.menu_item_vault),
                    subtitle = stringResource(R.string.menu_item_vault_sub)
                ) {
                    nav.navigateTo(VaultScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_messages_direct,
                    title = stringResource(R.string.menu_item_qr_share)
                ) {
                    nav.navigateTo(ShareScreen)
                }
            }

            // ------------------------------- Données -------------------------------
            item { SectionHeader(stringResource(R.string.menu_section_data)) }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_export_csv,
                    title = stringResource(R.string.menu_item_import)
                ) {
                    nav.navigateTo(ImportScreen(launchedFromOnboarding = false))
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_pc_printer,
                    title = stringResource(R.string.menu_item_export_csv)
                ) {
                    viewModel.exportCsv(rootScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_vue_security_shield,
                    title = stringResource(R.string.menu_item_backup)
                ) {
                    viewModel.backup(rootScreen)
                }
            }

            // ------------------------------- Réglages ------------------------------
            item { SectionHeader(stringResource(R.string.menu_section_settings)) }
            item {
                MenuItemCard(
                    icon = R.drawable.home_more_menu_settings,
                    title = stringResource(R.string.menu_item_settings)
                ) {
                    nav.navigateTo(SettingsScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_currency,
                    title = stringResource(R.string.menu_item_exchange_rates)
                ) {
                    nav.navigateTo(ExchangeRatesScreen)
                }
            }
            item {
                MenuItemCard(
                    icon = when (theme) {
                        Theme.LIGHT -> R.drawable.home_more_menu_light_mode
                        Theme.DARK -> R.drawable.home_more_menu_dark_mode
                        Theme.AMOLED_DARK -> R.drawable.home_more_menu_amoled_dark_mode
                        Theme.AUTO -> R.drawable.home_more_menu_auto_mode
                    },
                    title = stringResource(R.string.menu_item_theme),
                    subtitle = stringResource(
                        when (theme) {
                            Theme.LIGHT -> R.string.light_mode
                            Theme.DARK -> R.string.dark_mode
                            Theme.AMOLED_DARK -> R.string.amoled_mode
                            Theme.AUTO -> R.string.auto_mode
                        }
                    )
                ) {
                    viewModel.switchTheme()
                }
            }
            item {
                MenuItemCard(
                    icon = R.drawable.ic_custom_programming_m,
                    title = stringResource(R.string.advanced_features),
                    subtitle = stringResource(R.string.menu_item_advanced_sub)
                ) {
                    nav.navigateTo(FeaturesScreen)
                }
            }
        }
    }

    BufferModal(
        modal = bufferModalData,
        dismiss = { bufferModalData = null },
        onBufferChanged = viewModel::setBuffer
    )
}

@Composable
private fun SearchCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.menu_search),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun NetWorthCard(
    report: List<CurrencyNetWorth>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.nw_net_label),
                style = MaterialTheme.typography.bodySmall
            )
            if (report.isEmpty()) {
                Text(
                    text = stringResource(R.string.menu_net_worth_sub),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                report.forEach { currencyNetWorth ->
                    Text(
                        text = formatMoney(currencyNetWorth.netWorth, currencyNetWorth.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsCard(
    yearlyTotals: Map<String?, Double>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.subs_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W700
            )
            if (yearlyTotals.isEmpty()) {
                Text(
                    text = stringResource(R.string.menu_subs_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                yearlyTotals.forEach { (currency, total) ->
                    Text(
                        text = stringResource(
                            R.string.subs_per_year,
                            formatMoney(total, currency)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyChartCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.chart_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W700
            )
            Text(
                text = stringResource(R.string.menu_chart_sub),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SetupCard(
    progress: SetupProgress,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W700
            )
            Text(
                text = if (progress.isComplete) {
                    stringResource(R.string.setup_menu_done)
                } else {
                    stringResource(
                        R.string.setup_progress_count,
                        progress.doneCount,
                        progress.actionableCount
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    color: Color = Gray
) {
    Column {
        Spacer(Modifier.height(16.dp))

        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                color = color,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun MenuItemCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IvyIcon(icon = icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.W700
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Badge {
                    Text(text = badgeCount.toString())
                }
            }
        }
    }
}
