package org.abodah.heymoney.assistant.ai

import org.json.JSONObject
import java.util.Locale

/** What the model decided the user is asking. Code computes the answer. */
enum class AssistantIntent {
    BALANCE,
    SPENDING,
    INCOME,
    CATEGORY_SPENDING,
    TOP_CATEGORIES,
    BIGGEST_EXPENSE,
    DAILY_BUDGET,
    SUBSCRIPTIONS,
    PLANNED_PAYMENTS,
    BUDGET_STATUS,
    ALERTS,
    WEB_INFO,
    GENERAL
}

data class ParsedIntent(
    val intent: AssistantIntent,
    val category: String? = null,
    val webQuery: String? = null
)

/**
 * Parses the model's strict-JSON intent answer. Tolerant about prose
 * around the JSON (small models ramble), strict about the schema: an
 * unknown intent or broken JSON returns null and the caller falls back.
 */
object IntentParser {

    fun parse(raw: String): ParsedIntent? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val json = JSONObject(raw.substring(start, end + 1))
            val name = json.optString("intent").trim().uppercase(Locale.ROOT)
            val intent = AssistantIntent.entries.firstOrNull { it.name == name }
                ?: return null
            ParsedIntent(
                intent = intent,
                category = json.optString("category").cleanNullable(),
                webQuery = json.optString("web_query").cleanNullable()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun String.cleanNullable(): String? =
        trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
