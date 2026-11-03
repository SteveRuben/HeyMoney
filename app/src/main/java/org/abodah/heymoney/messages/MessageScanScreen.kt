package org.abodah.heymoney.messages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import org.abodah.heymoney.subscriptions.SubscriptionFrequency
import org.abodah.heymoney.subscriptions.formatMoney
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * "Analyse my messages": an on-demand, read-only scan of the SMS inbox
 * that finds phone carriers and subscriptions from their bill messages.
 * The explanation comes first, the READ_SMS permission is asked only when
 * the user starts the scan, and nothing leaves the phone. Found services
 * can be followed: that creates a planned payment rule, the mechanism the
 * subscriptions screen and the reminders already run on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScanScreen(viewModel: MessageScanViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val phase by viewModel.phase.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.scan() else viewModel.onPermissionDenied()
    }
    val startScan = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.scan()
        else permissionLauncher.launch(Manifest.permission.READ_SMS)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.msgscan_title),
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
        Column(Modifier.padding(padding)) {
            when (val current = phase) {
                MessageScanPhase.Explanation -> Explanation(onStart = startScan)
                MessageScanPhase.PermissionDenied -> Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.msgscan_denied),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = startScan, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.msgscan_retry))
                    }
                }

                MessageScanPhase.Scanning -> Text(
                    text = stringResource(R.string.msgscan_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )

                is MessageScanPhase.Results -> Results(
                    results = current,
                    viewModel = viewModel,
                    onRescan = startScan,
                )
            }
        }
    }
}

@Composable
private fun Explanation(onStart: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.msgscan_explain_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.msgscan_explain_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.msgscan_explain_privacy),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.msgscan_start))
        }
    }
}

@Composable
private fun Results(
    results: MessageScanPhase.Results,
    viewModel: MessageScanViewModel,
    onRescan: () -> Unit,
) {
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    var accountMenuOpen by remember { mutableStateOf(false) }
    val selectedAccount = results.accounts.firstOrNull { it.id == selectedAccountId }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        results.simOperator?.let { operator ->
            item {
                Text(
                    text = stringResource(R.string.msgscan_your_operator, operator),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (results.found.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.msgscan_none),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.msgscan_found_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (results.accounts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.msgscan_no_account),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (results.accounts.size > 1) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.msgscan_account_label),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { accountMenuOpen = true }) {
                        Text(selectedAccount?.name ?: "")
                    }
                    DropdownMenu(
                        expanded = accountMenuOpen,
                        onDismissRequest = { accountMenuOpen = false }
                    ) {
                        results.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    viewModel.selectAccount(account.id)
                                    accountMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        items(results.found, key = { it.key }) { found ->
            FoundCard(
                found = found,
                tracked = found.key in results.trackedKeys,
                canTrack = results.accounts.isNotEmpty(),
                onTrack = { viewModel.track(found) },
            )
        }

        item {
            OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.msgscan_rescan))
            }
        }
    }
}

@Composable
private fun FoundCard(
    found: MessageSubscription,
    tracked: Boolean,
    canTrack: Boolean,
    onTrack: () -> Unit,
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    val lastDate = Instant.ofEpochMilli(found.lastDateMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate()

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = found.serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                found.amount?.let {
                    Text(
                        text = formatMoney(it, found.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            val detail = buildString {
                found.frequency?.let { append(frequencyLabel(it)); append(" · ") }
                append(stringResource(R.string.msgscan_last_on, dateFormatter.format(lastDate)))
                append(" · ")
                append(stringResource(R.string.msgscan_occurrences, found.occurrences))
            }
            Text(text = detail, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tracked) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.msgscan_tracked),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                } else {
                    Button(onClick = onTrack, enabled = canTrack) {
                        Text(stringResource(R.string.msgscan_track))
                    }
                }
            }
        }
    }
}

@Composable
private fun frequencyLabel(frequency: SubscriptionFrequency): String =
    stringResource(
        when (frequency) {
            SubscriptionFrequency.WEEKLY -> R.string.subs_freq_weekly
            SubscriptionFrequency.BIWEEKLY -> R.string.subs_freq_biweekly
            SubscriptionFrequency.MONTHLY -> R.string.subs_freq_monthly
            SubscriptionFrequency.QUARTERLY -> R.string.subs_freq_quarterly
            SubscriptionFrequency.SEMIANNUAL -> R.string.subs_freq_semiannual
            SubscriptionFrequency.YEARLY -> R.string.subs_freq_yearly
        }
    )
