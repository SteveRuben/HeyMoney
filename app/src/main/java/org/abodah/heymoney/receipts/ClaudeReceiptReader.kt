package org.abodah.heymoney.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abodah.heymoney.alerts.BankAlert
import org.abodah.heymoney.alerts.BankAlertParser
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Optional receipt reading by Claude (vision). Only used when the user has set an API token;
 * the photo is then sent to Anthropic. Returns null on any failure so the caller can fall back
 * to the on-device reading.
 */
class ClaudeReceiptReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun read(uri: Uri, token: String, model: String): BankAlert? = withContext(Dispatchers.IO) {
        runCatching {
            val jpeg = loadScaledJpeg(uri)
            val client = AnthropicOkHttpClient.builder().apiKey(token).build()

            val image = ImageBlockParam.builder()
                .source(
                    Base64ImageSource.builder()
                        .data(Base64.encodeToString(jpeg, Base64.NO_WRAP))
                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                        .build()
                )
                .build()

            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4000L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessageOfBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(image),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build())
                    )
                )
                .build()

            val text = client.messages().create(params).content()
                .mapNotNull { it.text().orElse(null)?.text() }
                .joinToString("")
            toAlert(text)
        }.getOrNull()
    }

    private fun toAlert(reply: String): BankAlert? {
        // the model is asked for bare JSON; tolerate surrounding text
        val json = JSONObject(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1))
        val total = json.optDouble("total", Double.NaN)
        if (!total.isFinite() || total <= 0.0) return null
        val merchant = json.optString("merchant").trim().ifBlank { "Receipt" }.take(40)
        val currency = json.optString("currency").trim().uppercase().takeIf { it.length == 3 }
        val date = runCatching {
            LocalDate.parse(json.optString("date")).atTime(12, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() ?: System.currentTimeMillis()
        return BankAlert(
            id = BankAlert.idFor(BankAlert.Source.RECEIPT, "$merchant|$total|$date|ai"),
            source = BankAlert.Source.RECEIPT,
            sender = "AI",
            raw = "$merchant $total ${currency.orEmpty()}",
            amount = total,
            currency = currency ?: BankAlertParser.currencyIn(reply),
            isIncome = false,
            merchant = merchant,
            date = date
        )
    }

    // JPEG, longest side at most 1568 px, upright: keeps the upload small and readable
    private fun loadScaledJpeg(uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        var bitmap = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!

        val rotation = resolver.openInputStream(uri)!!.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (rotation != 0f || scale < 1f) {
            val matrix = Matrix().apply {
                if (rotation != 0f) postRotate(rotation)
                if (scale < 1f) postScale(scale, scale)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    private companion object {
        const val MAX_SIDE = 1568

        const val PROMPT = "Read this receipt or invoice. Reply with ONLY a JSON object, no other text: " +
            "{\"merchant\": string, \"total\": number (the final amount paid, tax included), " +
            "\"currency\": ISO 4217 code or null, \"date\": \"YYYY-MM-DD\" or null}. " +
            "If the image is not a receipt, reply {\"total\": null}."
    }
}
