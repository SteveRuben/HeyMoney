package org.abodah.heymoney.receipts

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.abodah.heymoney.alerts.BankAlert
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ScanResult {
    data class Found(val alert: BankAlert) : ScanResult
    data object NothingFound : ScanResult
}

/**
 * Reads a receipt photo entirely on the device: ML Kit OCR, then the local
 * [ReceiptParser] heuristics. Nothing ever leaves the phone.
 *
 * Seam for a future on-device light model: it would sit between [recognize]
 * and [ReceiptParser.parse] — same OCR text in, same [BankAlert] out,
 * with the parser as fallback when the model is absent or its output
 * does not validate.
 */
class ReceiptScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun scan(uri: Uri): ScanResult {
        val text = recognize(uri)
        val alert = ReceiptParser.parse(text)
        return if (alert != null) ScanResult.Found(alert) else ScanResult.NothingFound
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
