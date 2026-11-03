package com.ivy

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import com.ivy.attributions.AttributionsScreenImpl
import org.abodah.heymoney.ai.AiSettingsScreen
import org.abodah.heymoney.alerts.AlertsScreen
import org.abodah.heymoney.receipts.LiveScanScreen
import org.abodah.heymoney.assistant.AssistantScreen
import org.abodah.heymoney.news.NewsScreen
import org.abodah.heymoney.share.ShareScreen
import org.abodah.heymoney.rules.CategoryRulesScreen
import org.abodah.heymoney.subscriptions.SubscriptionGuideScreen
import org.abodah.heymoney.subscriptions.SubscriptionsScreen
import org.abodah.heymoney.networth.NetWorthScreen
import org.abodah.heymoney.chart.MonthlyChartScreen
import org.abodah.heymoney.menu.MenuScreen
import com.ivy.balance.BalanceScreen
import com.ivy.budgets.BudgetScreen
import com.ivy.categories.CategoriesScreen
import com.ivy.contributors.ContributorsScreenImpl
import com.ivy.disclaimer.DisclaimerScreenImpl
import com.ivy.exchangerates.ExchangeRatesScreen
import com.ivy.features.FeaturesScreenImpl
import com.ivy.importdata.csv.CSVScreen
import com.ivy.importdata.csvimport.ImportCSVScreen
import com.ivy.loans.loan.LoansScreen
import com.ivy.loans.loandetails.LoanDetailsScreen
import com.ivy.main.MainScreen
import com.ivy.navigation.AiSettingsScreen
import com.ivy.navigation.AlertsScreen
import com.ivy.navigation.ScanReceiptScreen
import com.ivy.navigation.AssistantScreen
import com.ivy.navigation.NewsScreen
import com.ivy.navigation.ShareScreen
import com.ivy.navigation.AttributionsScreen
import com.ivy.navigation.BalanceScreen
import com.ivy.navigation.BudgetScreen
import com.ivy.navigation.CSVScreen
import com.ivy.navigation.CategoriesScreen
import com.ivy.navigation.CategoryRulesScreen
import com.ivy.navigation.ContributorsScreen
import com.ivy.navigation.DisclaimerScreen
import com.ivy.navigation.EditPlannedScreen
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.ExchangeRatesScreen
import com.ivy.navigation.FeaturesScreen
import com.ivy.navigation.ImportScreen
import com.ivy.navigation.LoanDetailsScreen
import com.ivy.navigation.LoansScreen
import com.ivy.navigation.MainScreen
import com.ivy.navigation.OnboardingScreen
import com.ivy.navigation.PieChartStatisticScreen
import com.ivy.navigation.PlannedPaymentsScreen
import com.ivy.navigation.PollScreen
import com.ivy.navigation.ReleasesScreen
import com.ivy.navigation.ReportScreen
import com.ivy.navigation.Screen
import com.ivy.navigation.SearchScreen
import com.ivy.navigation.SettingsScreen
import com.ivy.navigation.SubscriptionDetailsScreen
import com.ivy.navigation.SubscriptionsScreen
import com.ivy.navigation.NetWorthScreen
import com.ivy.navigation.MonthlyChartScreen
import com.ivy.navigation.MenuScreen
import com.ivy.navigation.TransactionsScreen
import com.ivy.onboarding.OnboardingScreen
import com.ivy.piechart.PieChartStatisticScreen
import com.ivy.planned.edit.EditPlannedScreen
import com.ivy.planned.list.PlannedPaymentsScreen
import com.ivy.poll.impl.ui.PollScreen
import com.ivy.releases.ReleasesScreenImpl
import com.ivy.reports.ReportScreen
import com.ivy.search.SearchScreen
import com.ivy.settings.SettingsScreen
import com.ivy.transaction.EditTransactionScreen
import com.ivy.transactions.TransactionsScreen

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
@Suppress("CyclomaticComplexMethod", "FunctionNaming")
fun BoxWithConstraintsScope.IvyNavGraph(screen: Screen?) {
    when (screen) {
        null -> {
            // show nothing
        }

        is MainScreen -> MainScreen(screen = screen)
        is AlertsScreen -> AlertsScreen()
        is ScanReceiptScreen -> LiveScanScreen()
        is AiSettingsScreen -> AiSettingsScreen()
        is AssistantScreen -> AssistantScreen()
        is NewsScreen -> NewsScreen()
        is ShareScreen -> ShareScreen()
        is CategoryRulesScreen -> CategoryRulesScreen()
        is SubscriptionsScreen -> SubscriptionsScreen()
        is SubscriptionDetailsScreen -> SubscriptionGuideScreen(subscriptionKey = screen.subscriptionKey)
        is NetWorthScreen -> NetWorthScreen()
        is MonthlyChartScreen -> MonthlyChartScreen()
        is MenuScreen -> MenuScreen()
        is OnboardingScreen -> OnboardingScreen(screen = screen)
        is ExchangeRatesScreen -> ExchangeRatesScreen()
        is EditTransactionScreen -> EditTransactionScreen(screen = screen)
        is TransactionsScreen -> TransactionsScreen(screen = screen)
        is PieChartStatisticScreen -> PieChartStatisticScreen(screen = screen)
        is CategoriesScreen -> CategoriesScreen(screen = screen)
        is SettingsScreen -> SettingsScreen()
        is PlannedPaymentsScreen -> PlannedPaymentsScreen(screen = screen)
        is EditPlannedScreen -> EditPlannedScreen(screen = screen)
        is BalanceScreen -> BalanceScreen(screen = screen)
        is ImportScreen -> ImportCSVScreen(screen = screen)
        is ReportScreen -> ReportScreen(screen = screen)
        is BudgetScreen -> BudgetScreen(screen = screen)
        is LoansScreen -> LoansScreen(screen = screen)
        is LoanDetailsScreen -> LoanDetailsScreen(screen = screen)
        is SearchScreen -> SearchScreen(screen = screen)
        is CSVScreen -> CSVScreen(screen = screen)
        FeaturesScreen -> FeaturesScreenImpl()
        AttributionsScreen -> AttributionsScreenImpl()
        ContributorsScreen -> ContributorsScreenImpl()
        ReleasesScreen -> ReleasesScreenImpl()
        DisclaimerScreen -> DisclaimerScreenImpl()
        PollScreen -> PollScreen()
    }
}
