package com.ivy.data.rules

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's categorisation rules, kept in SharedPreferences (same storage
 * pattern as HeyMoney's bank-alert store). Shared by every feature that files
 * a transaction automatically: bank alerts today, CSV statement imports too —
 * a rule applies whenever a transaction would otherwise land uncategorised.
 */
@Singleton
class CategoryRuleStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("heymoney_category_rules", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<List<CategoryRule>> = _rules

    /** First rule matching [texts] (merchant, title, raw wording...), if any. */
    fun matchCategoryId(vararg texts: String?): UUID? =
        CategoryRules.match(_rules.value, *texts)

    @Synchronized
    fun add(keyword: String, categoryId: UUID) {
        val cleaned = keyword.trim()
        if (cleaned.isEmpty()) return
        if (_rules.value.any { it.keyword.equals(cleaned, ignoreCase = true) && it.categoryId == categoryId }) {
            return
        }
        save(_rules.value + CategoryRule(keyword = cleaned, categoryId = categoryId))
    }

    @Synchronized
    fun remove(rule: CategoryRule) {
        save(_rules.value - rule)
    }

    private fun save(rules: List<CategoryRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("keyword", rule.keyword)
                    .put("categoryId", rule.categoryId.toString())
            )
        }
        prefs.edit().putString(RULES, array.toString()).apply()
        _rules.value = rules
    }

    private fun load(): List<CategoryRule> = runCatching {
        val array = JSONArray(prefs.getString(RULES, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            runCatching {
                CategoryRule(
                    keyword = o.getString("keyword"),
                    categoryId = UUID.fromString(o.getString("categoryId"))
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val RULES = "rules"
    }
}
