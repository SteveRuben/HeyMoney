package org.abodah.heymoney.assistant.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Lifecycle of the downloaded on-device model. */
sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelState
    data class Ready(val sizeBytes: Long) : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * Downloads, verifies and deletes the assistant's model.
 *
 * The model lives alone in `files/assistant_model/` — a dedicated
 * directory, away from the Room database and the preferences. Deleting
 * it frees the ~547 MB and touches nothing else; the user's accounts,
 * transactions and settings survive, and the assistant simply falls
 * back to its rule-based answers until the model is downloaded again.
 *
 * A file is only trusted when its size matches [AssistantModelInfo]
 * exactly: a truncated or corrupted download is never handed to the
 * inference engine.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val modelDir = File(context.filesDir, MODEL_DIR_NAME)
    private val modelFile = File(modelDir, AssistantModelInfo.FILE_NAME)
    private val partFile = File(modelDir, AssistantModelInfo.FILE_NAME + ".part")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    @Volatile
    private var cancelRequested = false

    private val _state = MutableStateFlow(detectState())
    val state: StateFlow<ModelState> = _state

    fun modelFile(): File = modelFile

    /** Re-reads the on-disk truth (e.g. after the engine failed to load). */
    fun refresh() {
        if (downloadJob?.isActive != true) _state.value = detectState()
    }

    private fun detectState(): ModelState =
        if (modelFile.isFile && modelFile.length() == AssistantModelInfo.EXPECTED_SIZE_BYTES) {
            ModelState.Ready(modelFile.length())
        } else {
            ModelState.NotDownloaded
        }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        cancelRequested = false
        downloadJob = scope.launch { download() }
    }

    fun cancelDownload() {
        cancelRequested = true
        downloadJob?.cancel()
    }

    /**
     * The "clean the model" action: removes the model directory only.
     * App data lives elsewhere and is never touched.
     */
    fun deleteModel() {
        cancelRequested = true
        downloadJob?.cancel()
        modelDir.deleteRecursively()
        _state.value = ModelState.NotDownloaded
    }

    private fun download() {
        var connection: HttpURLConnection? = null
        try {
            modelDir.mkdirs()
            partFile.delete()
            connection = (URL(AssistantModelInfo.DOWNLOAD_URL).openConnection()
                as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "HeyMoney/1.0 (on-device model download)")
                setRequestProperty("Accept-Encoding", "identity")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
                .takeIf { it > 0 } ?: AssistantModelInfo.EXPECTED_SIZE_BYTES
            _state.value = ModelState.Downloading(0, total)
            var downloaded = 0L
            var lastPercent = -1
            connection.inputStream.buffered().use { input ->
                partFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancelRequested) {
                            partFile.delete()
                            _state.value = ModelState.NotDownloaded
                            return
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val percent = (downloaded * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = ModelState.Downloading(downloaded, total)
                        }
                    }
                }
            }
            if (partFile.length() != AssistantModelInfo.EXPECTED_SIZE_BYTES) {
                val received = partFile.length()
                partFile.delete()
                _state.value = ModelState.Failed(
                    "Incomplete download ($received bytes)"
                )
                return
            }
            if (!partFile.renameTo(modelFile)) {
                partFile.delete()
                _state.value = ModelState.Failed("Could not finalize the download")
                return
            }
            _state.value = ModelState.Ready(modelFile.length())
        } catch (e: Exception) {
            partFile.delete()
            _state.value = if (cancelRequested) {
                ModelState.NotDownloaded
            } else {
                ModelState.Failed(e.message ?: "Download failed")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val MODEL_DIR_NAME = "assistant_model"
        const val BUFFER_SIZE = 64 * 1024
    }
}
