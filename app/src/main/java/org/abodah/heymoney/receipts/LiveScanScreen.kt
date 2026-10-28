package org.abodah.heymoney.receipts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.abodah.heymoney.alerts.AlertDraft
import org.abodah.heymoney.alerts.AlertSaver
import org.abodah.heymoney.alerts.BankAlert
import java.util.concurrent.Executors
import javax.inject.Inject

data class LiveScanState(
    val found: AlertDraft? = null,
    val saved: Boolean = false,
    val error: String? = null,
)

/**
 * Live receipt reading: every camera frame is read on the phone, and the same total seen on
 * several frames in a row is offered as an expense. Nothing is saved until the user taps Add.
 */
@HiltViewModel
class LiveScanViewModel @Inject constructor(
    private val saver: AlertSaver
) : ViewModel() {

    private val _state = MutableStateFlow(LiveScanState())
    val state: StateFlow<LiveScanState> = _state

    private var candidate: Double? = null
    private var seen = 0

    fun onFrameText(text: String) {
        if (_state.value.found != null || _state.value.saved) return
        val alert = ReceiptParser.parse(text)
        if (alert == null) {
            candidate = null
            seen = 0
            return
        }
        if (alert.amount == candidate) seen++ else {
            candidate = alert.amount
            seen = 1
        }
        if (seen >= STABLE_FRAMES) {
            viewModelScope.launch {
                _state.value = LiveScanState(found = saver.preview(alert))
            }
        }
    }

    fun add() {
        val draft = _state.value.found ?: return
        viewModelScope.launch {
            _state.value = if (saver.save(draft.alert)) {
                LiveScanState(saved = true)
            } else {
                LiveScanState(found = draft, error = "Create an account first, then scan again.")
            }
        }
    }

    /** Back to looking for a total: after a refusal or after an expense was added. */
    fun keepLooking() {
        candidate = null
        seen = 0
        _state.value = LiveScanState()
    }

    private companion object {
        const val STABLE_FRAMES = 3
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScanScreen(viewModel: LiveScanViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scan a receipt") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (granted) {
                CameraPreview(onText = viewModel::onFrameText)
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("The camera is needed to read your receipt. Nothing is recorded or uploaded.")
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow the camera") }
                }
            }

            Card(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val draft = state.found
                    when {
                        state.saved -> {
                            Text("Expense added ✓", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::keepLooking) { Text("Scan another") }
                                OutlinedButton(onClick = { nav.back() }) { Text("Done") }
                            }
                        }

                        draft != null -> {
                            Text(
                                "%,.2f %s".format(draft.alert.amount, draft.alert.currency.orEmpty()),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                listOfNotNull(draft.alert.merchant, draft.accountName, draft.categoryName)
                                    .joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::add) { Text("Add expense") }
                                OutlinedButton(onClick = viewModel::keepLooking) { Text("Not right") }
                            }
                        }

                        else -> Text(
                            "Film the receipt and hold steady on the total. It is added only after you tap.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onText: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val latestOnText by rememberUpdatedStateCompat(onText)

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var lastRun = 0L
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                val now = System.currentTimeMillis()
                val media = proxy.image
                if (media == null || now - lastRun < FRAME_INTERVAL_MS) {
                    proxy.close()
                    return@setAnalyzer
                }
                lastRun = now
                val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { latestOnText(it.text) }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
            recognizer.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private const val FRAME_INTERVAL_MS = 500L

@Composable
private fun <T> rememberUpdatedStateCompat(value: T) = androidx.compose.runtime.rememberUpdatedState(value)
