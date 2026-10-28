package org.abodah.heymoney.receipts

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.abodah.heymoney.alerts.BankAlert
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Anthropic API token for the optional AI reading, kept encrypted on the device. */
@Singleton
class AiTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                "heymoney_ai",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    fun get(): String? = prefs?.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun set(token: String) {
        prefs?.edit()?.apply {
            if (token.isBlank()) remove(KEY) else putString(KEY, token.trim())
        }?.apply()
    }

    fun model(): AiModel = AiModel.entries.firstOrNull { it.id == prefs?.getString(MODEL_KEY, null) }
        ?: AiModel.HAIKU

    fun setModel(model: AiModel) {
        prefs?.edit()?.putString(MODEL_KEY, model.id)?.apply()
    }

    private companion object {
        const val KEY = "anthropic_token"
        const val MODEL_KEY = "anthropic_model"
    }
}

/** Claude models offered for receipt reading. */
enum class AiModel(val id: String, val label: String, val hint: String) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "Most accurate, highest cost per receipt"),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Good accuracy, lower cost"),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "Fastest and cheapest, enough for most receipts"),
}

sealed interface ScanResult {
    data class Found(val alert: BankAlert, val usedAi: Boolean) : ScanResult
    data object NothingFound : ScanResult
}

/** Reads a receipt photo: on the device first, and with Claude too when a token is set. */
class ReceiptScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: AiTokenStore,
    private val claude: ClaudeReceiptReader,
) {
    suspend fun scan(uri: Uri): ScanResult {
        tokens.get()?.let { token ->
            claude.read(uri, token, tokens.model().id)?.let { return ScanResult.Found(it, usedAi = true) }
        }
        val text = recognize(uri)
        val alert = ReceiptParser.parse(text)
        return if (alert != null) ScanResult.Found(alert, usedAi = false) else ScanResult.NothingFound
    }

    private suspend fun recognize(uri: Uri): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromFilePath(context, uri)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            recognizer.close()
        }
    }
}
