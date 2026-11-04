package org.abodah.heymoney.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

/** One piece of outside information, with where it came from. */
data class WebResult(
    val text: String,
    val sourceLabel: String
)

/**
 * The assistant's read-only window to the internet. Only ever called
 * with an already-sanitized query ([WebQuerySanitizer]): a generic
 * topic, never the user's amounts, accounts or transactions.
 *
 * Two key-less sources: open.er-api.com for exchange rates (the one
 * outside fact a money assistant genuinely needs) and the DuckDuckGo
 * Instant Answer API for generic questions. Both are best-effort —
 * no result is a normal outcome, not an error.
 */
class WebLookup @Inject constructor() {

    suspend fun lookup(sanitizedQuery: String): WebResult? =
        withContext(Dispatchers.IO) {
            val codes = WebQuerySanitizer.currencyCodesIn(sanitizedQuery)
            if (codes.size >= 2) {
                exchangeRate(codes[0], codes[1])?.let { return@withContext it }
            }
            instantAnswer(sanitizedQuery)
        }

    private fun exchangeRate(from: String, to: String): WebResult? {
        val body = get("https://open.er-api.com/v6/latest/$from") ?: return null
        return try {
            val json = JSONObject(body)
            val rates = json.optJSONObject("rates") ?: return null
            if (!rates.has(to)) return null
            val rate = rates.getDouble(to)
            val updated = json.optString("time_last_update_utc")
            WebResult(
                text = "1 $from = $rate $to (indicative rate, updated $updated)",
                sourceLabel = "open.er-api.com"
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun instantAnswer(query: String): WebResult? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = get(
            "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
        ) ?: return null
        return try {
            val json = JSONObject(body)
            val abstract = json.optString("AbstractText")
            if (abstract.isNotBlank()) {
                return WebResult(abstract.take(MAX_TEXT_LENGTH), "DuckDuckGo")
            }
            val topics = json.optJSONArray("RelatedTopics")
            if (topics != null) {
                for (i in 0 until topics.length()) {
                    val text = topics.optJSONObject(i)?.optString("Text").orEmpty()
                    if (text.isNotBlank()) {
                        return WebResult(text.take(MAX_TEXT_LENGTH), "DuckDuckGo")
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "HeyMoney/1.0 (assistant web lookup)")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_TEXT_LENGTH = 700
    }
}
