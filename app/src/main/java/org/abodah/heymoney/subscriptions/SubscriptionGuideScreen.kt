package org.abodah.heymoney.subscriptions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import kotlinx.coroutines.delay

/**
 * One subscription, in full: what it costs, when it renews, how to cancel it
 * (guided, inside the service), how to negotiate it (ready-to-copy templates),
 * a free note, and the renewal reminder setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionGuideScreen(
    subscriptionKey: String,
    viewModel: SubscriptionsViewModel = screenScopedViewModel(),
) {
    val nav = navigation()
    val items by viewModel.items.collectAsState()
    val item = items.firstOrNull { it.subscription.key == subscriptionKey }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(item?.subscription?.name ?: stringResource(R.string.subs_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (item == null) {
            Text(
                stringResource(R.string.subs_empty),
                modifier = Modifier.padding(padding).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            SubscriptionDetail(
                item = item,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun SubscriptionDetail(
    item: SubscriptionItem,
    viewModel: SubscriptionsViewModel,
    modifier: Modifier = Modifier,
) {
    val subscription = item.subscription
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var note by remember(subscription.key) { mutableStateOf(item.decision.note) }
    var noteSaved by remember(subscription.key) { mutableStateOf(false) }
    var copiedTemplate by remember(subscription.key) { mutableStateOf<Int?>(null) }
    LaunchedEffect(copiedTemplate) {
        if (copiedTemplate != null) {
            delay(2000)
            copiedTemplate = null
        }
    }

    val guide = CancellationGuides.find(subscription.name)
    val priceText = formatMoney(subscription.amount, subscription.currency)
    val tenure = tenureText(subscription.firstChargeDate)

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        formatMoney(subscription.amount, subscription.currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        listOf(
                            frequencyLabel(subscription.frequency),
                            stringResource(
                                R.string.subs_per_year,
                                formatMoney(subscription.yearlyCost, subscription.currency)
                            )
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.subs_next_due, formatDate(subscription.nextDueDate)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    subscription.lastChargeDate?.let {
                        Text(
                            stringResource(R.string.subs_last_charge, formatDate(it)) +
                                if (subscription.chargeCount > 0) {
                                    " • " + stringResource(
                                        R.string.subs_charges_count, subscription.chargeCount
                                    )
                                } else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (item.decision.status) {
                            SubscriptionStatus.CONFIRMED -> TextButton(
                                onClick = { viewModel.ignore(subscription.key) }
                            ) { Text(stringResource(R.string.subs_action_ignore)) }
                            SubscriptionStatus.IGNORED -> TextButton(
                                onClick = { viewModel.restore(subscription.key) }
                            ) { Text(stringResource(R.string.subs_action_restore)) }
                            null -> {
                                Button(onClick = { viewModel.confirm(subscription.key) }) {
                                    Text(stringResource(R.string.subs_action_confirm))
                                }
                                TextButton(onClick = { viewModel.ignore(subscription.key) }) {
                                    Text(stringResource(R.string.subs_action_ignore))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (item.decision.status == SubscriptionStatus.CONFIRMED) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.subs_reminder_label),
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 1, 3, 7).forEach { days ->
                                FilterChip(
                                    selected = item.decision.reminderDays == days,
                                    onClick = { viewModel.setReminderDays(subscription.key, days) },
                                    label = {
                                        Text(
                                            if (days == 0) stringResource(R.string.subs_reminder_off)
                                            else stringResource(
                                                R.string.subs_reminder_days_before, days
                                            )
                                        )
                                    }
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.subs_reminder_explainer),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.subs_note_label),
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = {
                            note = it
                            noteSaved = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.subs_note_hint)) },
                        minLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.saveNote(subscription.key, note)
                            noteSaved = true
                        }) { Text(stringResource(R.string.subs_note_save)) }
                        if (noteSaved) {
                            Text(
                                stringResource(R.string.subs_note_saved),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.subs_cancel_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.subs_cancel_intro),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(guide?.stepsRes ?: R.string.subs_cancel_generic),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (guide?.url != null) {
                        Button(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(guide.url))
                            )
                        }) { Text(stringResource(R.string.subs_cancel_open_page)) }
                    }
                    Text(
                        stringResource(R.string.subs_cancel_done_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.subs_negotiate_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.subs_negotiate_intro),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        NegotiationTemplates.all.forEachIndexed { index, template ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(template.titleRes),
                            fontWeight = FontWeight.SemiBold
                        )
                        val filled = NegotiationTemplates.fill(
                            body = stringResource(template.bodyRes),
                            provider = subscription.name,
                            price = priceText,
                            tenure = tenure,
                        )
                        Text(filled, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(filled))
                                copiedTemplate = index
                            }) { Text(stringResource(R.string.subs_template_copy)) }
                            if (copiedTemplate == index) {
                                Text(
                                    stringResource(R.string.subs_template_copied),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
