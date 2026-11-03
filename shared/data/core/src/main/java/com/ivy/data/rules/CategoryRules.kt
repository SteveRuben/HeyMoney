package com.ivy.data.rules

import java.util.Locale
import java.util.UUID

/**
 * One user categorisation rule: when a transaction's wording contains
 * [keyword] (case-insensitive), file it under [categoryId].
 * Rules are evaluated in list order: the first match wins, so the user
 * controls priority by the order rules were added.
 */
data class CategoryRule(
    val keyword: String,
    val categoryId: UUID,
)

/** Pure matching logic for [CategoryRule]s, kept separate so it stays testable. */
object CategoryRules {

    fun match(rules: List<CategoryRule>, vararg texts: String?): UUID? {
        val haystack = texts.filterNotNull().joinToString(" ").lowercase(Locale.ROOT)
        if (haystack.isBlank()) return null
        return rules.firstOrNull { rule ->
            val needle = rule.keyword.trim().lowercase(Locale.ROOT)
            needle.isNotEmpty() && haystack.contains(needle)
        }?.categoryId
    }
}
