package org.abodah.heymoney.share

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(viewModel: ShareViewModel = screenScopedViewModel()) {
    val nav = navigation()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Share with a friend") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Send") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Receive") })
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tab == 0) SendTab(viewModel) else ReceiveTab(viewModel)
            }
        }
    }
}

@Composable
private fun SendTab(viewModel: ShareViewModel) {
    val state by viewModel.send.collectAsState()
    val context = LocalContext.current

    Text(
        "Your friend scans the code or opens the link. Everything stays between your two phones, " +
            "encrypted with a one-time code. No server, no account.",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SharePeriod.entries.forEach { period ->
            FilterChip(
                selected = state.period == period,
                onClick = { viewModel.selectPeriod(period) },
                label = { Text(period.label) }
            )
        }
    }
    Text("${state.count} transaction(s) in this period", style = MaterialTheme.typography.labelLarge)

    if (state.link == null) {
        Button(onClick = viewModel::generate, enabled = state.count > 0, modifier = Modifier.fillMaxWidth()) {
            Text("Create share code")
        }
    } else {
        val qr = state.qr
        if (qr != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "QR code",
                    modifier = Modifier.size(260.dp)
                )
            }
        } else {
            Text(
                "Too many transactions for a QR code. Send the link instead, or pick a shorter period.",
                color = MaterialTheme.colorScheme.error
            )
        }
        Text("Code: ${state.code}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Give the code to your friend separately (say it out loud). The link alone cannot be read.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, state.link)
                context.startActivity(Intent.createChooser(send, "Share link"))
            }
        ) { Text("Send the link") }
    }
}

@Composable
private fun ReceiveTab(viewModel: ShareViewModel) {
    val state by viewModel.receive.collectAsState()
    val scanner = rememberLauncherForScan { viewModel.onScanned(it) }

    state.imported?.let {
        Text(
            if (it > 0) "$it transaction(s) added." else "Nothing new to add (or no account yet).",
            style = MaterialTheme.typography.titleMedium
        )
    }

    Button(onClick = { scanner() }, modifier = Modifier.fillMaxWidth()) { Text("Scan a QR code") }
    Text("or paste what your friend sent", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = state.link,
        onValueChange = viewModel::onLinkChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Link") }
    )
    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Code") },
        singleLine = true
    )
    OutlinedButton(
        onClick = viewModel::preview,
        enabled = state.link.isNotBlank() && state.code.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Check") }

    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    state.preview?.let { batch ->
        Text(
            "${batch.items.size} transaction(s) found, ${state.newCount} new.",
            style = MaterialTheme.typography.titleMedium
        )
        Button(
            onClick = viewModel::import,
            enabled = state.newCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add ${state.newCount} to my accounts") }
    }
}

@Composable
private fun rememberLauncherForScan(onResult: (String) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onResult)
    }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan your friend's HeyMoney code")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        )
    }
}
