package org.abodah.heymoney.share

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** One transaction as it travels between two devices. Identity is derived from its content. */
data class SharedTransaction(
    val title: String,
    val amount: Double,
    val currency: String,
    val isIncome: Boolean,
    val categoryName: String,
    val dateTime: Long,
)

/** A decrypted batch of transactions. */
data class ShareBatch(
    val sender: String,
    val exportedAt: Long,
    val items: List<SharedTransaction>,
)

class ShareException(
    val reason: Reason,
    message: String,
) : Exception(message) {
    enum class Reason {
        /** The pasted text is not a HeyMoney share link. */
        MALFORMED,

        /** Link truncated, or built by a newer app version. */
        UNSUPPORTED,

        /** Wrong team code, or the payload was altered in transit. */
        BAD_CODE,

        /** Decryption worked but the content is not usable. */
        EMPTY,
    }
}

/**
 * Server-less team exchange.
 *
 * A snapshot is serialised to JSON, gzipped, then encrypted with AES-256-GCM.
 * The key is derived with PBKDF2-HMAC-SHA256 from the team code that the two
 * people exchange out of band (spoken, or in a separate message). Because GCM
 * authenticates the payload, a modified link fails to decrypt: the tag *is*
 * the signature. Nothing is ever uploaded anywhere.
 *
 * Envelope layout (then Base64URL, no padding):
 *   [version:1][salt:16][iv:12][ciphertext + 16-byte GCM tag]
 *
 * The token lives after the `#` of an https URL, so even if a chat app
 * previews the link, the fragment is never sent to the (never contacted) host.
 */
object ShareCodec {

    const val LINK_PREFIX = "https://heymoney.app/s#"

    // prefix used by the QR code, which also carries the team code
    private const val QR_PREFIX = "HMQ2|"

    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val MAX_LINK_LENGTH = 512_000
    private const val MAX_EXPENSES = 5_000
    private const val MIN_CODE_LENGTH = 8

    private const val CODE_LENGTH = 12

    // no 0/O, 1/I: the code is read aloud and typed by hand
    private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    private val AAD = "heymoney-team-v1".toByteArray(Charsets.UTF_8)
    private val secureRandom = SecureRandom()

    /**
     * Team codes are case-insensitive and tolerate dashes/spaces, so
     * "hm-abcd-efgh-jklm" and "HMABCDEFGHJKLM" unlock the same snapshot.
     */
    fun normalizeCode(raw: String): String =
        raw.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun isValidCode(raw: String): Boolean = normalizeCode(raw).length >= MIN_CODE_LENGTH

    /**
     * Generates a fresh team code such as HM-4K7P-9XQ2-M3ZR: 12 symbols over a
     * 32-character alphabet (60 bits), readable aloud and typed by hand.
     */
    fun generateCode(): String {
        val bytes = ByteArray(8).also(secureRandom::nextBytes)
        var bits = 0L
        bytes.forEach { bits = (bits shl 8) or (it.toLong() and 0xFF) }
        val characters = StringBuilder(CODE_LENGTH)
        for (index in 0 until CODE_LENGTH) {
            val shift = 64 - (index + 1) * 5
            characters.append(CODE_ALPHABET[((bits ushr shift) and 0x1F).toInt()])
        }
        return formatCode(characters.toString())
    }

    /** "4K7P9XQ2M3ZR" -> "HM-4K7P-9XQ2-M3ZR" (no-op for other formats). */
    private fun formatCode(body: String): String =
        "HM-${body.substring(0, 4)}-${body.substring(4, 8)}-${body.substring(8, 12)}"

    /** Encrypts a snapshot and returns the shareable https link. */
    fun encode(payload: ShareBatch, code: String): String {
        require(isValidCode(code)) { "team code too short" }
        val json = toJson(payload).toByteArray(Charsets.UTF_8)
        val plain = gzip(json)

        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val key = deriveKey(normalizeCode(code), salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(AAD)
        val cipherText = cipher.doFinal(plain)

        val envelope = ByteArrayOutputStream(1 + SALT_BYTES + IV_BYTES + cipherText.size)
        envelope.write(VERSION)
        envelope.write(salt)
        envelope.write(iv)
        envelope.write(cipherText)

        return LINK_PREFIX + Base64Url.encode(envelope.toByteArray())
    }

    /**
     * Decrypts a link pasted from any channel (chat, email, QR text).
     *
     * @throws ShareException with a reason suitable for a user message.
     */
    fun decode(link: String, code: String): ShareBatch {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) throw ShareException(ShareException.Reason.MALFORMED, "empty link")
        if (trimmed.length > MAX_LINK_LENGTH) throw ShareException(ShareException.Reason.MALFORMED, "link too long")
        if (!isValidCode(code)) throw ShareException(ShareException.Reason.BAD_CODE, "invalid code")

        val token = when {
            trimmed.startsWith(LINK_PREFIX) -> trimmed.removePrefix(LINK_PREFIX)
            // tolerate a link that picked up surrounding words when copied
            trimmed.contains('#') -> trimmed.substringAfterLast('#')
            else -> trimmed
        }.trim().trimEnd('=', '/')

        val envelope = try {
            Base64Url.decode(token)
        } catch (e: IllegalArgumentException) {
            throw ShareException(ShareException.Reason.MALFORMED, "bad base64")
        }
        if (envelope.size <= 1 + SALT_BYTES + IV_BYTES) {
            throw ShareException(ShareException.Reason.MALFORMED, "truncated")
        }
        val version = envelope[0].toInt() and 0xFF
        if (version != VERSION) {
            throw ShareException(ShareException.Reason.UNSUPPORTED, "version $version")
        }

        val salt = envelope.copyOfRange(1, 1 + SALT_BYTES)
        val iv = envelope.copyOfRange(1 + SALT_BYTES, 1 + SALT_BYTES + IV_BYTES)
        val cipherText = envelope.copyOfRange(1 + SALT_BYTES + IV_BYTES, envelope.size)

        val plain = try {
            val key = deriveKey(normalizeCode(code), salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(AAD)
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            // AEADBadTagException: wrong code or tampered payload — deliberately
            // indistinguishable, so an attacker learns nothing from the error.
            throw ShareException(ShareException.Reason.BAD_CODE, "cannot decrypt")
        }

        val payload = try {
            fromJson(String(ungzip(plain), Charsets.UTF_8))
        } catch (e: Exception) {
            throw ShareException(ShareException.Reason.MALFORMED, "bad payload")
        }
        if (payload.items.isEmpty()) {
            throw ShareException(ShareException.Reason.EMPTY, "nothing to import")
        }
        return payload
    }

    /** QR text embeds both the link and the team code, for a face-to-face handoff. */
    fun qrPayload(link: String, code: String): String = "$QR_PREFIX${normalizeCode(code)}|$link"

    /** @return (code, link) when [text] is a HeyMoney QR, null otherwise. */
    fun parseQrPayload(text: String): Pair<String, String>? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(QR_PREFIX)) return null
        val rest = trimmed.removePrefix(QR_PREFIX)
        val separator = rest.indexOf('|')
        if (separator <= 0) return null
        val code = rest.substring(0, separator)
        val link = rest.substring(separator + 1)
        if (code.isBlank() || !link.startsWith(LINK_PREFIX)) return null
        return code to link
    }

    private fun toJson(payload: ShareBatch): String {
        val items = JSONArray()
        payload.items.take(MAX_EXPENSES).forEach { item ->
            items.put(
                JSONObject()
                    .put("d", item.title.take(200))
                    .put("a", item.amount)
                    .put("c", item.currency.uppercase(Locale.ROOT).take(6))
                    .put("i", item.isIncome)
                    .put("k", item.categoryName.take(80))
                    .put("t", item.dateTime)
            )
        }
        return JSONObject()
            .put("v", VERSION)
            .put("from", payload.sender.take(80))
            .put("at", payload.exportedAt)
            .put("items", items)
            .toString()
    }

    private fun fromJson(json: String): ShareBatch {
        val root = JSONObject(json)
        val array = root.optJSONArray("items") ?: JSONArray()
        val items = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val amount = item.optDouble("a", Double.NaN)
            if (!amount.isFinite() || amount <= 0.0) return@mapNotNull null
            SharedTransaction(
                title = item.optString("d").trim(),
                amount = amount,
                currency = item.optString("c").uppercase(Locale.ROOT),
                isIncome = item.optBoolean("i", false),
                categoryName = item.optString("k").trim(),
                dateTime = item.optLong("t", 0L),
            )
        }
        return ShareBatch(
            sender = root.optString("from"),
            exportedAt = root.optLong("at", 0L),
            items = items,
        )
    }

    private fun deriveKey(code: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(encoded, "AES")
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    private fun ungzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}

/**
 * Minimal Base64URL (RFC 4648 §5, unpadded). Implemented locally because
 * java.util.Base64 needs API 26 while the app supports API 24, and because it
 * keeps the codec testable on the plain JVM.
 */
internal object Base64Url {

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val REVERSE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    fun encode(input: ByteArray): String {
        val output = StringBuilder((input.size + 2) / 3 * 4)
        var index = 0
        while (index < input.size) {
            val b0 = input[index].toInt() and 0xFF
            val b1 = if (index + 1 < input.size) input[index + 1].toInt() and 0xFF else 0
            val b2 = if (index + 2 < input.size) input[index + 2].toInt() and 0xFF else 0
            val chunk = (b0 shl 16) or (b1 shl 8) or b2

            output.append(ALPHABET[(chunk ushr 18) and 0x3F])
            output.append(ALPHABET[(chunk ushr 12) and 0x3F])
            if (index + 1 < input.size) output.append(ALPHABET[(chunk ushr 6) and 0x3F])
            if (index + 2 < input.size) output.append(ALPHABET[chunk and 0x3F])
            index += 3
        }
        return output.toString()
    }

    fun decode(text: String): ByteArray {
        val cleaned = StringBuilder(text.length)
        text.forEach { char ->
            when {
                char == '=' || char == '\n' || char == '\r' || char == ' ' || char == '\t' -> Unit
                else -> cleaned.append(char)
            }
        }
        val value = cleaned.toString()
        if (value.length % 4 == 1) throw IllegalArgumentException("invalid base64 length")

        val output = ByteArrayOutputStream(value.length * 3 / 4)
        var buffer = 0
        var bits = 0
        value.forEach { char ->
            val code = char.code
            val decoded = if (code < 128) REVERSE[code] else -1
            if (decoded < 0) throw IllegalArgumentException("invalid base64 char")
            buffer = (buffer shl 6) or decoded
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output.write((buffer ushr bits) and 0xFF)
            }
        }
        return output.toByteArray()
    }
}
