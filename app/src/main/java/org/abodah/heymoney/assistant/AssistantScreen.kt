package org.abodah.heymoney.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import org.abodah.heymoney.assistant.ai.AssistantModelInfo
import org.abodah.heymoney.assistant.ai.ModelState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val messages by viewModel.messages.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val internetEnabled by viewModel.internetEnabled.collectAsState()
    var input by remember { mutableStateOf("") }
    var showAiSettings by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val fr = remember { Locale.getDefault().language == "fr" }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Assistant") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAiSettings = !showAiSettings }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = if (fr) "Réglages de l'assistant" else "Assistant settings"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
        ) {
            if (showAiSettings) {
                AiSettingsPanel(
                    state = modelState,
                    internetEnabled = internetEnabled,
                    fr = fr,
                    onDownload = viewModel::downloadModel,
                    onCancelDownload = viewModel::cancelModelDownload,
                    onAskDelete = { confirmDelete = true },
                    onInternetChange = viewModel::setInternetEnabled
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(message.text)
                                if (message.usedWeb) {
                                    Text(
                                        text = if (fr) "🌐 Source : web" else "🌐 Source: web",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (thinking) {
                    item { Text("…", style = MaterialTheme.typography.bodyLarge) }
                }
            }

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.suggestions) { suggestion ->
                    AssistChip(onClick = { viewModel.ask(suggestion) }, label = { Text(suggestion) })
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask a question") },
                    singleLine = true
                )
                IconButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        viewModel.ask(input)
                        input = ""
                    }
                ) { Icon(Icons.Filled.Send, contentDescription = "Send") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(if (fr) "Supprimer le modèle ?" else "Delete the model?")
            },
            text = {
                Text(
                    if (fr) {
                        "Le modèle IA (environ 547 Mo) sera retiré de l'appareil. " +
                            "Vos comptes, transactions et réglages ne sont pas touchés. " +
                            "L'assistant repassera à ses réponses intégrées, et vous " +
                            "pourrez retélécharger le modèle à tout moment."
                    } else {
                        "The AI model (about 547 MB) will be removed from this device. " +
                            "Your accounts, transactions and settings are not touched. " +
                            "The assistant will use its built-in answers, and you can " +
                            "download the model again at any time."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteModel()
                    }
                ) { Text(if (fr) "Supprimer" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(if (fr) "Annuler" else "Cancel")
                }
            }
        )
    }
}

/**
 * The assistant's own settings: on-device model lifecycle (download,
 * progress, delete — without ever touching app data) and the opt-in
 * switch for web lookups.
 */
@Composable
private fun AiSettingsPanel(
    state: ModelState,
    internetEnabled: Boolean,
    fr: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onAskDelete: () -> Unit,
    onInternetChange: (Boolean) -> Unit
) {
    val sizeMb = (AssistantModelInfo.EXPECTED_SIZE_BYTES / 1_000_000).toInt()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (fr) "IA sur l'appareil" else "On-device AI",
                style = MaterialTheme.typography.titleMedium
            )
            when (state) {
                ModelState.NotDownloaded -> {
                    Text(
                        if (fr) {
                            "Modèle non téléchargé : l'assistant utilise ses réponses " +
                                "intégrées. Téléchargez le modèle ($sizeMb Mo, " +
                                "${AssistantModelInfo.DISPLAY_NAME}) pour des réponses " +
                                "en langage naturel, 100 % hors ligne ensuite."
                        } else {
                            "Model not downloaded: the assistant uses its built-in " +
                                "answers. Download the model ($sizeMb MB, " +
                                "${AssistantModelInfo.DISPLAY_NAME}) for natural-language " +
                                "answers, fully offline afterwards."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onDownload) {
                        Text(if (fr) "Télécharger le modèle" else "Download the model")
                    }
                }

                is ModelState.Downloading -> {
                    val percent = if (state.totalBytes > 0) {
                        (state.downloadedBytes * 100 / state.totalBytes).toInt()
                    } else {
                        0
                    }
                    Text(
                        if (fr) "Téléchargement… $percent %" else "Downloading… $percent%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = percent / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = onCancelDownload) {
                        Text(if (fr) "Annuler" else "Cancel")
                    }
                }

                is ModelState.Ready -> {
                    Text(
                        if (fr) {
                            "Modèle prêt ($sizeMb Mo) : ${AssistantModelInfo.DISPLAY_NAME}. " +
                                "Il répond depuis vos données, sur l'appareil."
                        } else {
                            "Model ready ($sizeMb MB): ${AssistantModelInfo.DISPLAY_NAME}. " +
                                "It answers from your data, on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onAskDelete) {
                        Text(if (fr) "Supprimer le modèle" else "Delete the model")
                    }
                }

                is ModelState.Failed -> {
                    Text(
                        if (fr) {
                            "Échec du téléchargement (${state.reason}). " +
                                "L'assistant continue avec ses réponses intégrées."
                        } else {
                            "Download failed (${state.reason}). " +
                                "The assistant keeps using its built-in answers."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onDownload) {
                        Text(if (fr) "Réessayer" else "Try again")
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (fr) "Autoriser les consultations web" else "Allow web lookups",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (fr) {
                            "Désactivé par défaut. Si activé, l'assistant peut chercher " +
                                "un taux de change ou une info générale : seul un sujet " +
                                "générique est envoyé — jamais vos montants, comptes ou " +
                                "transactions. Les réponses concernées affichent « Source : web »."
                        } else {
                            "Off by default. When on, the assistant can look up an " +
                                "exchange rate or general information: only a generic " +
                                "topic is sent — never your amounts, accounts or " +
                                "transactions. Answers that used the web are marked “Source: web”."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = internetEnabled, onCheckedChange = onInternetChange)
            }
        }
    }
}
