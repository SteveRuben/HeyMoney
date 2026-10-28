package org.abodah.heymoney.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import com.ivy.navigation.AiSettingsScreen
import com.ivy.navigation.ScanReceiptScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(viewModel: AlertsViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val context = LocalContext.current
    val pending by viewModel.pending.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val hasToken by viewModel.hasToken.collectAsState()
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) viewModel.scanReceipt(uri)
    }
    val launchCamera = {
        val file = File(context.cacheDir, "receipts").apply { mkdirs() }
            .let { File(it, "receipt_${System.currentTimeMillis()}.jpg") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.scanReceipt(uri)
    }
    val message by viewModel.message.collectAsState()
    var pasted by remember { mutableStateOf("") }

    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { smsGranted = it }
    val notifAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Bank alerts") },
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Capture transactions automatically", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Bank SMS and notifications are read on your phone only. " +
                                "Nothing is saved until you confirm it.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (smsGranted) {
                                Text("SMS: on", style = MaterialTheme.typography.labelLarge)
                            } else {
                                OutlinedButton(onClick = {
                                    smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                                }) { Text("Allow SMS") }
                            }
                            if (notifAccess) {
                                Text("Notifications: on", style = MaterialTheme.typography.labelLarge)
                            } else {
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }) { Text("Allow notifications") }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scan a receipt", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Take a photo of a receipt or invoice. The total, the shop and the date are read " +
                                "on your phone, then waiting here for your confirmation.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { nav.navigateTo(ScanReceiptScreen) }) { Text("Film a receipt") }
                            OutlinedButton(
                                enabled = !scanning,
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                        PackageManager.PERMISSION_GRANTED
                                    ) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            ) { Text("Photo") }
                            OutlinedButton(enabled = !scanning, onClick = { pickImage.launch("image/*") }) {
                                Text("From gallery")
                            }
                        }
                        if (scanning) Text("Reading the receipt…", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (hasToken) "AI reading is ON: receipt photos are sent to Anthropic (Claude API)."
                            else "Reading is fully on your phone. Hard receipts can use the Claude API.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { nav.navigateTo(AiSettingsScreen) }) { Text("AI settings") }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste a bank message") },
                    trailingIcon = {
                        TextButton(
                            enabled = pasted.isNotBlank(),
                            onClick = {
                                viewModel.addPasted(pasted)
                                pasted = ""
                            }
                        ) { Text("Add") }
                    }
                )
                message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    LaunchedEffect(it) { viewModel.clearMessage() }
                }
            }

            if (pending.isEmpty()) {
                item {
                    Text(
                        "No alert waiting. New bank messages will show up here.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                item {
                    Button(onClick = viewModel::confirmAll, modifier = Modifier.fillMaxWidth()) {
                        Text("Confirm all (${pending.size})")
                    }
                }
                items(pending, key = { it.id }) { alert ->
                    AlertCard(alert, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: BankAlert, viewModel: AlertsViewModel) {
    val draft by produceState<AlertDraft?>(null, alert) { value = viewModel.preview(alert) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                (if (alert.isIncome) "+ " else "- ") +
                    "%,.2f %s".format(alert.amount, alert.currency.orEmpty()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(alert.merchant, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    draft?.accountName,
                    draft?.categoryName,
                    alert.source.label,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(alert.date))
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.confirm(alert) }) { Text("Confirm") }
                TextButton(onClick = { viewModel.dismiss(alert) }) { Text("Dismiss") }
            }
        }
    }
}
