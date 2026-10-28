package org.abodah.heymoney.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.abodah.heymoney.receipts.AiModel
import org.abodah.heymoney.receipts.AiTokenStore
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val store: AiTokenStore
) : ViewModel() {
    private val _hasToken = MutableStateFlow(store.get() != null)
    val hasToken: StateFlow<Boolean> = _hasToken

    private val _model = MutableStateFlow(store.model())
    val model: StateFlow<AiModel> = _model

    fun saveToken(token: String) {
        store.set(token)
        _hasToken.value = store.get() != null
    }

    fun selectModel(model: AiModel) {
        store.setModel(model)
        _model.value = model
    }
}

/** Settings > AI: the optional Claude API connection used to read receipts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(viewModel: AiSettingsViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val hasToken by viewModel.hasToken.collectAsState()
    val model by viewModel.model.collectAsState()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI (Claude API)") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "HeyMoney works fully on your phone. Add your own Claude API token to read receipts that are " +
                    "hard to scan (crumpled, long, handwritten).",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "With a token, the receipt photos you scan are sent to Anthropic, billed to your own API account. " +
                    "Without a token nothing is ever sent.",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                if (hasToken) "Status: connected" else "Status: not connected (on-device reading only)",
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (hasToken) "New API token (replaces the saved one)" else "Anthropic API token") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = input.isNotBlank(),
                    onClick = {
                        viewModel.saveToken(input)
                        input = ""
                    }
                ) { Text("Save token") }
                if (hasToken) {
                    OutlinedButton(onClick = { viewModel.saveToken("") }) { Text("Remove token") }
                }
            }
            Text(
                "The token is stored encrypted on this device. Create one in the Anthropic Console.",
                style = MaterialTheme.typography.bodySmall
            )

            Text("Model", fontWeight = FontWeight.SemiBold)
            AiModel.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option == model, onClick = { viewModel.selectModel(option) })
                    Column {
                        Text(option.label)
                        Text(option.hint, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
