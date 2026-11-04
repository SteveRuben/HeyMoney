package org.abodah.heymoney.statement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.navigation.CSVScreen
import com.ivy.navigation.SubscriptionsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import org.abodah.heymoney.subscriptions.DetectedSubscription
import org.abodah.heymoney.subscriptions.formatDate
import org.abodah.heymoney.subscriptions.formatMoney
import org.abodah.heymoney.subscriptions.frequencyLabel

/**
 * "Analyse a statement": the user picks the CSV a bank or card statement
 * was exported to, and HeyMoney reads it on the spot — period, money in
 * and out, fees and interest, top merchants, categories, recurring
 * payments (found by the same detector as "My subscriptions").
 *
 * The analysis writes nothing: importing the operations into an account
 * is handed to the existing CSV import flow (button at the bottom), which
 * owns column mapping, account choice and the actual writes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementAnalysisScreen(
    viewModel: StatementAnalysisViewModel = screenScopedViewModel(),
) {
    val nav = navigation()
    val state by viewModel.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.analyse(uri)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.statement_title),
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
            when (val current = state) {
                StatementUiState.Idle -> {
                    item {
                        Text(
                            text = stringResource(R.string.statement_intro),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.statement_pick_file))
                        }
                    }
                }

                StatementUiState.Loading -> {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.statement_reading))
                        }
                    }
                }

                StatementUiState.Unreadable -> {
                    item {
                        Text(
                            text = stringResource(R.string.statement_unreadable),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.statement_pick_another))
                        }
                    }
                }

                is StatementUiState.Ready -> {
                    analysisItems(
                        state = current,
                        onPickAnother = { picker.launch(arrayOf("*/*")) },
                        onOpenSubscriptions = { nav.navigateTo(SubscriptionsScreen) },
                        onImport = {
                            nav.navigateTo(CSVScreen(launchedFromOnboarding = false))
                        },
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.analysisItems(
    state: StatementUiState.Ready,
    onPickAnother: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onImport: () -> Unit,
) {
    val analysis = state.analysis

    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val period = if (analysis.periodStart != null && analysis.periodEnd != null) {
                    stringResource(
                        R.string.statement_period,
                        formatDate(analysis.periodStart),
                        formatDate(analysis.periodEnd)
                    )
                } else {
                    stringResource(R.string.statement_period_unknown)
                }
                Text(period, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                AmountRow(stringResource(R.string.statement_total_in), analysis.totalIn)
                AmountRow(stringResource(R.string.statement_total_out), -analysis.totalOut)
                AmountRow(
                    stringResource(R.string.statement_net),
                    analysis.net,
                    bold = true
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.statement_transactions,
                        analysis.transactionCount
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.skippedRows > 0) {
                    Text(
                        text = stringResource(R.string.statement_skipped, state.skippedRows),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.statement_fees_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                if (analysis.feesCount == 0) {
                    Text(
                        text = stringResource(R.string.statement_fees_none),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    AmountRow(
                        stringResource(R.string.statement_fees_total, analysis.feesCount),
                        -analysis.feesTotal
                    )
                }
            }
        }
    }

    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.statement_recurring_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                if (state.recurring.isEmpty()) {
                    Text(
                        text = stringResource(R.string.statement_recurring_none),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    state.recurring.forEach { subscription ->
                        RecurringRow(subscription)
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedButton(
                        onClick = onOpenSubscriptions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.statement_open_subscriptions))
                    }
                }
            }
        }
    }

    if (analysis.topMerchants.isNotEmpty()) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.statement_top_merchants),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    analysis.topMerchants.forEach { merchant ->
                        AmountRow(
                            label = if (merchant.count > 1) {
                                "${merchant.name} ×${merchant.count}"
                            } else {
                                merchant.name
                            },
                            amount = -merchant.total
                        )
                    }
                }
            }
        }
    }

    if (analysis.byCategory.any { it.category != null }) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.statement_by_category),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    analysis.byCategory.forEach { category ->
                        AmountRow(
                            label = category.category
                                ?: stringResource(R.string.statement_no_category),
                            amount = category.totalIn - category.totalOut
                        )
                    }
                }
            }
        }
    }

    if (analysis.largest.isNotEmpty()) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.statement_largest),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    analysis.largest.forEach { line ->
                        AmountRow(
                            label = buildString {
                                append(line.title)
                                line.date?.let { append(" · ").append(formatDate(it)) }
                            },
                            amount = line.amount
                        )
                    }
                }
            }
        }
    }

    item {
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.statement_import_button))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.statement_import_note),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPickAnother, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.statement_pick_another))
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Double, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatMoney(amount, null),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun RecurringRow(subscription: DetectedSubscription) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = subscription.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = frequencyLabel(subscription.frequency),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = formatMoney(-subscription.amount, subscription.currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
