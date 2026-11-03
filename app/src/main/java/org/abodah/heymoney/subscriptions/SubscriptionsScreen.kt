package org.abodah.heymoney.subscriptions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivy.navigation.SubscriptionDetailsScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R

/**
 * "My subscriptions": every recurring charge detected in the transactions or
 * declared as a planned payment, with its yearly cost and next due date.
 * The user confirms the real ones (that arms the renewal reminders) and
 * ignores the false positives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val context = LocalContext.current
    val items by viewModel.items.collectAsState()
    val loaded by viewModel.loaded.collectAsState()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* the worker checks again before posting; nothing to do here */ }
    val confirmWithReminder: (String) -> Unit = { key ->
        viewModel.confirm(key)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val confirmed = items.filter { it.decision.status == SubscriptionStatus.CONFIRMED }
    val detected = items.filter { it.decision.status == null }
    val ignored = items.filter { it.decision.status == SubscriptionStatus.IGNORED }
    val yearlyTotals = (confirmed + detected)
        .groupBy { it.subscription.currency }
        .mapValues { (_, group) -> group.sumOf { it.subscription.yearlyCost } }
        .toSortedMap(compareBy { it ?: "" })

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.subs_title)) },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.subs_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (loaded && items.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.subs_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (yearlyTotals.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.subs_yearly_cost_label),
                                style = MaterialTheme.typography.bodySmall
                            )
                            yearlyTotals.forEach { (currency, total) ->
                                Text(
                                    stringResource(
                                        R.string.subs_per_year,
                                        formatMoney(total, currency)
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            subscriptionSection(
                titleRes = R.string.subs_section_confirmed,
                sectionItems = confirmed,
                onDetails = { nav.navigateTo(SubscriptionDetailsScreen(it)) },
                onConfirm = confirmWithReminder,
                onIgnore = viewModel::ignore,
                onRestore = viewModel::restore,
            )
            subscriptionSection(
                titleRes = R.string.subs_section_detected,
                sectionItems = detected,
                onDetails = { nav.navigateTo(SubscriptionDetailsScreen(it)) },
                onConfirm = confirmWithReminder,
                onIgnore = viewModel::ignore,
                onRestore = viewModel::restore,
            )
            subscriptionSection(
                titleRes = R.string.subs_section_ignored,
                sectionItems = ignored,
                onDetails = { nav.navigateTo(SubscriptionDetailsScreen(it)) },
                onConfirm = confirmWithReminder,
                onIgnore = viewModel::ignore,
                onRestore = viewModel::restore,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.subscriptionSection(
    titleRes: Int,
    sectionItems: List<SubscriptionItem>,
    onDetails: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onIgnore: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    if (sectionItems.isEmpty()) return
    item {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    items(sectionItems, key = { it.subscription.key }) { item ->
        SubscriptionCard(
            item = item,
            onDetails = { onDetails(item.subscription.key) },
            onConfirm = { onConfirm(item.subscription.key) },
            onIgnore = { onIgnore(item.subscription.key) },
            onRestore = { onRestore(item.subscription.key) },
        )
    }
}

@Composable
private fun SubscriptionCard(
    item: SubscriptionItem,
    onDetails: () -> Unit,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
    onRestore: () -> Unit,
) {
    val subscription = item.subscription
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    subscription.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    formatMoney(subscription.amount, subscription.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                listOf(
                    frequencyLabel(subscription.frequency),
                    stringResource(
                        R.string.subs_per_year,
                        formatMoney(subscription.yearlyCost, subscription.currency)
                    ),
                    stringResource(
                        if (subscription.source == SubscriptionSource.PLANNED) {
                            R.string.subs_source_planned
                        } else {
                            R.string.subs_source_detected
                        }
                    )
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                buildString {
                    append(stringResource(R.string.subs_next_due, formatDate(subscription.nextDueDate)))
                    subscription.lastChargeDate?.let {
                        append(" • ")
                        append(stringResource(R.string.subs_last_charge, formatDate(it)))
                    }
                    if (subscription.chargeCount > 0) {
                        append(" • ")
                        append(stringResource(R.string.subs_charges_count, subscription.chargeCount))
                    }
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item.decision.status) {
                    SubscriptionStatus.CONFIRMED -> {
                        OutlinedButton(onClick = onDetails) {
                            Text(stringResource(R.string.subs_action_details))
                        }
                        TextButton(onClick = onIgnore) {
                            Text(stringResource(R.string.subs_action_ignore))
                        }
                    }
                    SubscriptionStatus.IGNORED -> {
                        TextButton(onClick = onRestore) {
                            Text(stringResource(R.string.subs_action_restore))
                        }
                    }
                    null -> {
                        Button(onClick = onConfirm) {
                            Text(stringResource(R.string.subs_action_confirm))
                        }
                        OutlinedButton(onClick = onDetails) {
                            Text(stringResource(R.string.subs_action_details))
                        }
                        TextButton(onClick = onIgnore) {
                            Text(stringResource(R.string.subs_action_ignore))
                        }
                    }
                }
            }
        }
    }
}
