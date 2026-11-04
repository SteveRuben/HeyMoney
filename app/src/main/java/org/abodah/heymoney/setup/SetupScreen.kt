package org.abodah.heymoney.setup

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R

/**
 * "Configuration": everything the user may still set up, with its real
 * state. The two permissions act for real (system dialog, system settings);
 * the three services that need an external provider or a HeyMoney server
 * say so plainly and explain what is missing — no fake connection flow.
 *
 * States are re-read from the OS every time the screen comes back to the
 * foreground, because both permissions live outside the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val context = LocalContext.current
    val progress by viewModel.progress.collectAsState()

    // Permissions change outside the app: re-read them on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    var expandedId by remember { mutableStateOf<SetupItemId?>(null) }
    val toggleExpanded: (SetupItemId) -> Unit = { id ->
        expandedId = if (expandedId == id) null else id
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.setup_title),
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
            item { ProgressHeader(progress) }

            item {
                PermissionItemCard(
                    icon = Icons.Filled.Sms,
                    title = stringResource(R.string.setup_sms_title),
                    description = stringResource(R.string.setup_sms_desc),
                    state = progress.stateOf(SetupItemId.SMS_PERMISSION),
                    actionLabel = stringResource(R.string.setup_action_allow),
                    onAction = { smsPermission.launch(Manifest.permission.RECEIVE_SMS) }
                )
            }

            item {
                PermissionItemCard(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.setup_notif_access_title),
                    description = stringResource(R.string.setup_notif_access_desc),
                    state = progress.stateOf(SetupItemId.NOTIFICATION_ACCESS),
                    actionLabel = stringResource(R.string.setup_action_allow),
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            item {
                UnavailableItemCard(
                    icon = Icons.Filled.AccountBalance,
                    title = stringResource(R.string.setup_bank_title),
                    description = stringResource(R.string.setup_bank_desc),
                    explanation = stringResource(R.string.setup_bank_explanation),
                    expanded = expandedId == SetupItemId.BANK_CONNECTION,
                    onToggleExpanded = { toggleExpanded(SetupItemId.BANK_CONNECTION) }
                )
            }

            item {
                UnavailableItemCard(
                    icon = Icons.Filled.Email,
                    title = stringResource(R.string.setup_email_title),
                    description = stringResource(R.string.setup_email_desc),
                    explanation = stringResource(R.string.setup_email_explanation),
                    expanded = expandedId == SetupItemId.EMAIL_SCAN,
                    onToggleExpanded = { toggleExpanded(SetupItemId.EMAIL_SCAN) }
                )
            }

            item {
                UnavailableItemCard(
                    icon = Icons.Filled.CreditCard,
                    title = stringResource(R.string.setup_card_title),
                    description = stringResource(R.string.setup_card_desc),
                    explanation = stringResource(R.string.setup_card_explanation),
                    expanded = expandedId == SetupItemId.STORED_CARD,
                    onToggleExpanded = { toggleExpanded(SetupItemId.STORED_CARD) }
                )
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: SetupProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.setup_progress_count,
                    progress.doneCount,
                    progress.actionableCount
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(
                progress = progress.percentComplete / 100f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    if (progress.isComplete) {
                        R.string.setup_complete
                    } else {
                        R.string.setup_progress_intro
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    state: SetupItemState,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemTitle(icon = icon, title = title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
            when (state) {
                SetupItemState.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.setup_state_done),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                SetupItemState.TODO -> Button(onClick = onAction) {
                    Text(text = actionLabel)
                }

                SetupItemState.UNAVAILABLE -> Unit // permissions are never unavailable
            }
        }
    }
}

@Composable
private fun UnavailableItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    explanation: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemTitle(icon = icon, title = title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.setup_state_unavailable),
                style = MaterialTheme.typography.labelLarge
            )
            if (expanded) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = onToggleExpanded) {
                Text(
                    text = stringResource(
                        if (expanded) {
                            R.string.setup_action_hide
                        } else {
                            R.string.setup_action_learn_more
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun ItemTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.W700
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
}
