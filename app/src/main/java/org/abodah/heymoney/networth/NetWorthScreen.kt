package org.abodah.heymoney.networth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import org.abodah.heymoney.subscriptions.formatMoney

/**
 * "Net worth": per currency, the accounts included in the balance, plus what
 * the loans still owe the user, minus what the user still owes on them —
 * with the detail of every element that composes the totals. The accounting
 * convention itself lives on [NetWorthCalculator].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(viewModel: NetWorthViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val report by viewModel.report.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nw_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                Text(
                    stringResource(R.string.nw_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (loaded && report.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.nw_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(report, key = { it.currency }) { currencyNetWorth ->
                CurrencyNetWorthCard(
                    currencyNetWorth = currencyNetWorth,
                    expanded = expanded[currencyNetWorth.currency] == true,
                    onToggleDetails = { expanded[currencyNetWorth.currency] =
                        expanded[currencyNetWorth.currency] != true },
                )
            }

            if (report.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.nw_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyNetWorthCard(
    currencyNetWorth: CurrencyNetWorth,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    val currency = currencyNetWorth.currency
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.nw_net_label) + " • " + currency,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                formatMoney(currencyNetWorth.netWorth, currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            TotalRow(
                label = stringResource(R.string.nw_accounts_label),
                amount = formatMoney(currencyNetWorth.accountsTotal, currency),
            )
            if (currencyNetWorth.lent.isNotEmpty()) {
                TotalRow(
                    label = stringResource(R.string.nw_lent_label),
                    amount = "+" + formatMoney(currencyNetWorth.lentTotal, currency),
                )
            }
            if (currencyNetWorth.borrowed.isNotEmpty()) {
                TotalRow(
                    label = stringResource(R.string.nw_borrowed_label),
                    amount = "−" + formatMoney(currencyNetWorth.borrowedTotal, currency),
                )
            }

            val hasDetails = currencyNetWorth.accounts.isNotEmpty() ||
                currencyNetWorth.lent.isNotEmpty() || currencyNetWorth.borrowed.isNotEmpty()
            if (hasDetails) {
                TextButton(onClick = onToggleDetails) {
                    Text(
                        stringResource(
                            if (expanded) R.string.nw_details_hide else R.string.subs_action_details
                        )
                    )
                }
            }

            if (expanded) {
                DetailGroup(
                    label = stringResource(R.string.nw_accounts_label),
                    lines = currencyNetWorth.accounts.map { it.name to it.balance },
                    currency = currency,
                )
                DetailGroup(
                    label = stringResource(R.string.nw_lent_label),
                    lines = currencyNetWorth.lent.map { it.name to it.remaining },
                    currency = currency,
                )
                DetailGroup(
                    label = stringResource(R.string.nw_borrowed_label),
                    lines = currencyNetWorth.borrowed.map { it.name to it.remaining },
                    currency = currency,
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DetailGroup(label: String, lines: List<Pair<String, Double>>, currency: String) {
    if (lines.isEmpty()) return
    Column(
        Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        lines.forEach { (name, amount) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    formatMoney(amount, currency),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
