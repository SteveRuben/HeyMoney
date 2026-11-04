package org.abodah.heymoney.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import kotlinx.coroutines.launch

/**
 * The local password vault: created, unlocked and used entirely on the
 * phone. The master password is never stored (see [VaultCrypto]); leaving
 * this screen locks the vault, and so does spending more than two minutes
 * in the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(viewModel: VaultViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val session by viewModel.session.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Auto-lock policy: background time is measured between ON_STOP and
    // ON_RESUME; leaving the screen locks immediately.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                Lifecycle.Event.ON_RESUME -> viewModel.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.lock() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.vault_title),
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
                },
                actions = {
                    if (session is VaultSession.Unlocked) {
                        IconButton(onClick = { viewModel.lock() }) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.vault_lock)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (val current = session) {
                VaultSession.NoVault -> CreateVaultForm(viewModel)
                VaultSession.Locked -> UnlockForm(viewModel)
                is VaultSession.Unlocked -> UnlockedVault(
                    viewModel = viewModel,
                    entries = current.entries,
                    snackbar = snackbar,
                )
            }
        }
    }
}

// region Create

@Composable
private fun CreateVaultForm(viewModel: VaultViewModel) {
    val createError by viewModel.createError.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.vault_create_intro),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.vault_master_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = { Text(stringResource(R.string.vault_master_confirm)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        when (createError) {
            VaultCreateError.TOO_SHORT -> Text(
                text = stringResource(R.string.vault_error_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            VaultCreateError.MISMATCH -> Text(
                text = stringResource(R.string.vault_error_mismatch),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            null -> Unit
        }
        Button(
            onClick = { viewModel.create(password, confirmation) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.vault_create_button))
        }
        Text(
            text = stringResource(R.string.vault_create_warning),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// endregion

// region Unlock

@Composable
private fun UnlockForm(viewModel: VaultViewModel) {
    val unlockFailed by viewModel.unlockFailed.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    var password by remember { mutableStateOf("") }
    var biometricFailed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val promptTitle = stringResource(R.string.vault_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.vault_biometric_prompt_subtitle)
    val promptCancel = stringResource(R.string.vault_biometric_cancel)

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.vault_unlock_intro),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.vault_master_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (unlockFailed) {
            Text(
                text = stringResource(R.string.vault_error_wrong),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = { viewModel.unlock(password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.vault_unlock_button))
        }
        if (biometricEnabled && activity != null && VaultBiometric.isAvailable(context)) {
            OutlinedButton(
                onClick = {
                    biometricFailed = false
                    val iv = viewModel.wrappedKeyIv()
                    val cipher = iv?.let {
                        runCatching { VaultBiometric.cipherForUnwrap(it) }.getOrNull()
                    }
                    if (cipher == null) {
                        biometricFailed = true
                    } else {
                        VaultBiometric.authenticate(
                            activity = activity,
                            title = promptTitle,
                            subtitle = promptSubtitle,
                            cancelText = promptCancel,
                            cipher = cipher,
                            onSuccess = { viewModel.unlockWithBiometrics(it) },
                            onError = { biometricFailed = true },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.vault_biometric_unlock))
            }
        }
        if (biometricFailed) {
            Text(
                text = stringResource(R.string.vault_biometric_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// endregion

// region Unlocked

private sealed interface EditorTarget {
    data object New : EditorTarget
    data class Existing(val entry: VaultEntry) : EditorTarget
}

@Composable
private fun UnlockedVault(
    viewModel: VaultViewModel,
    entries: List<VaultEntry>,
    snackbar: SnackbarHostState,
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(R.string.vault_copied)
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val promptTitle = stringResource(R.string.vault_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.vault_biometric_prompt_subtitle)
    val promptCancel = stringResource(R.string.vault_biometric_cancel)

    Column(Modifier.fillMaxWidth()) {
        if (activity != null && VaultBiometric.isAvailable(context)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.vault_biometric_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (biometricEnabled) {
                    OutlinedButton(onClick = { viewModel.disableBiometrics() }) {
                        Text(stringResource(R.string.vault_biometric_disable))
                    }
                } else {
                    Button(
                        onClick = {
                            val cipher =
                                runCatching { VaultBiometric.cipherForWrap() }.getOrNull()
                            if (cipher != null) {
                                VaultBiometric.authenticate(
                                    activity = activity,
                                    title = promptTitle,
                                    subtitle = promptSubtitle,
                                    cancelText = promptCancel,
                                    cipher = cipher,
                                    onSuccess = { viewModel.enableBiometrics(it) },
                                    onError = {},
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.vault_biometric_enable))
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.vault_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editorTarget = EditorTarget.Existing(entry) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.username.isNotBlank()) {
                                Text(
                                    text = entry.username,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(entry.password))
                                scope.launch { snackbar.showSnackbar(copiedText) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.vault_copy_password)
                            )
                        }
                        IconButton(
                            onClick = { editorTarget = EditorTarget.Existing(entry) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.vault_edit)
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { editorTarget = EditorTarget.New },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.vault_add))
        }
    }

    editorTarget?.let { target ->
        EntryEditorDialog(
            target = target,
            viewModel = viewModel,
            onDismiss = { editorTarget = null },
        )
    }
}

// endregion

// region Entry editor

@Composable
private fun EntryEditorDialog(
    target: EditorTarget,
    viewModel: VaultViewModel,
    onDismiss: () -> Unit,
) {
    val existing = (target as? EditorTarget.Existing)?.entry
    var title by remember(target) { mutableStateOf(existing?.title ?: "") }
    var username by remember(target) { mutableStateOf(existing?.username ?: "") }
    var password by remember(target) { mutableStateOf(existing?.password ?: "") }
    var url by remember(target) { mutableStateOf(existing?.url ?: "") }
    var notes by remember(target) { mutableStateOf(existing?.notes ?: "") }

    var genLength by remember(target) { mutableIntStateOf(16) }
    var genUpper by remember(target) { mutableStateOf(true) }
    var genDigits by remember(target) { mutableStateOf(true) }
    var genSymbols by remember(target) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.vault_new_title else R.string.vault_edit_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.vault_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.vault_field_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.vault_field_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.vault_gen_length, genLength),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { genLength = (genLength - 1).coerceAtLeast(8) }
                    ) { Text("−") }
                    TextButton(
                        onClick = { genLength = (genLength + 1).coerceAtMost(32) }
                    ) { Text("+") }
                }
                GeneratorOption(
                    checked = genUpper,
                    label = stringResource(R.string.vault_gen_upper),
                    onCheckedChange = { genUpper = it }
                )
                GeneratorOption(
                    checked = genDigits,
                    label = stringResource(R.string.vault_gen_digits),
                    onCheckedChange = { genDigits = it }
                )
                GeneratorOption(
                    checked = genSymbols,
                    label = stringResource(R.string.vault_gen_symbols),
                    onCheckedChange = { genSymbols = it }
                )
                OutlinedButton(
                    onClick = {
                        password = viewModel.generatePassword(
                            PasswordOptions(
                                length = genLength,
                                upper = genUpper,
                                digits = genDigits,
                                symbols = genSymbols,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.vault_generate))
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.vault_field_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.vault_field_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    if (existing == null) {
                        viewModel.addEntry(title, username, password, url, notes)
                    } else {
                        viewModel.updateEntry(existing, title, username, password, url, notes)
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.vault_save))
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(
                        onClick = {
                            viewModel.deleteEntry(existing)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.vault_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.vault_cancel))
                }
            }
        }
    )
}

@Composable
private fun GeneratorOption(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

// endregion
