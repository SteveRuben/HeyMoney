package org.abodah.heymoney.news

import android.util.Xml
import androidx.annotation.WorkerThread
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches investment-related news (market moves, upcoming IPOs, macro events)
 * from public RSS feeds. Uses only android-sdk primitives:
 * HttpURLConnection + XmlPullParser, no extra dependencies required.
 *
 * Each category has a chain of mirror/backup feeds: the first one that returns
 * valid and non-empty items wins..Connection errors and 4xx/5xx responses are
 * treated as a source failure instead of crashing the whole feed.
 */
class InvestmentNewsService @Inject constructor() {

    // feed source chains (primary then fallbacks) -> category
    private val newsSources = mapOf(
        "Markets" to listOf(
            "https://news.google.com/rss/search?q=march%C3%A9s+financiers&hl=fr&gl=FR&ceid=FR:fr",
            "https://feeds.finance.yahoo.com/rss/2.0/headline?s=%5EGSPC&region=US&lang=en-US",
            "https://feeds.content.dowjones.io/public/rss/mw_topstories"
        ),
        "IPO" to listOf(
            "https://news.google.com/rss/search?q=IPO+introduction+en+bourse&hl=fr&gl=FR&ceid=FR:fr",
            "https://feeds.content.dowjones.io/public/rss/mw_topstories"
        ),
        "Economy" to listOf(
            "https://news.google.com/rss/search?q=banque+centrale+taux+int%C3%A9r%C3%AAt&hl=fr&gl=FR&ceid=FR:fr",
            "https://news.yahoo.com/rss/finance"
        )
    )

    /**
     * Fetches the merged news feed from all source chains, newest first.
     * A failing category falls back to its backup feeds; if every feed of a
     * chain fails the category is skipped so one failure does not break the feed.
     */
    @WorkerThread
    suspend fun fetchNewsFeed(): List<InvestmentNews> {
        val allNews = newsSources.flatMap { (type, urls) ->
            urls.firstNotNullOfOrNull { url ->
                runCatching { fetchRss(url, type) }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            } ?: emptyList()
        }

        return allNews
            .distinctBy { it.title }
            .sortedByDescending { it.publishedAt }
            .take(MAX_FEED_SIZE)
    }

    @Throws(IOException::class)
    private fun fetchRss(feedUrl: String, type: String): List<InvestmentNews> {
        val connection = URL(feedUrl).openConnection() as HttpsURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "HeyMoney/1.1 (Android)")
            connection.connect()
            // 4xx/5xx responses must surface, not be parsed as a broken feed
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Feed returned HTTP $code")
            }
            connection.inputStream.use { stream ->
                parseRss(stream, type)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRss(stream: InputStream, type: String): List<InvestmentNews> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val items = mutableListOf<InvestmentNews>()
        var title: String? = null
        var link: String? = null
        var description: String? = null
        var publishedAt: Long = 0L
        var source: String = ""
        var insideItem = false
        var tag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag.lowercase()) {
                        "item" -> {
                            insideItem = true
                            title = null
                            link = null
                            description = null
                            publishedAt = 0L
                            source = ""
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideItem && tag != null) {
                        when (tag.lowercase()) {
                            "title" -> title = parser.text?.trim()
                            "link" -> link = parser.text?.trim()
                            "description" -> description = parser.text?.trim()
                            "pubdate" -> publishedAt = parseDate(parser.text)
                            "source" -> source = parser.text?.trim() ?: ""
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "item" -> {
                            insideItem = false
                            title?.let { t ->
                                if (t.isNotBlank()) {
                                    items.add(
                                        InvestmentNews(
                                            title = t,
                                            summary = stripHtml(
                                                description.orEmpty().ifBlank { t }
                                            ),
                                            source = source.ifBlank { "Web" },
                                            url = link.orEmpty(),
                                            publishedAt = if (publishedAt > 0L) publishedAt
                                            else System.currentTimeMillis(),
                                            type = type
                                        )
                                    )
                                }
                            }
                        }
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        return items
    }

    // summaries often come wrapped in HTML (e.g. from Yahoo feeds)
    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    // RFC822 date used by RSS feeds, e.g. "Mon, 27 Oct 2026 08:00:00 GMT"
    private val rfc822Date: SimpleDateFormat =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

    private fun parseDate(value: String?): Long = try {
        rfc822Date.parse(value.orEmpty().trim())?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_FEED_SIZE = 50
    }
}
