package org.abodah.heymoney.budget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Identifies one (category, month) assignment slot. */
data class CategoryMonth(
    val categoryId: UUID,
    val month: YearMonth,
)

/**
 * The user's monthly-budget decisions: how much they assigned to each
 * category for each month, and the goal (if any) of each category.
 *
 * Kept in SharedPreferences as JSON, the same pattern as the subscription
 * and bank-alert stores: the numbers are few (one per category and month),
 * they are the user's own planning data, and the computations themselves
 * stay stateless in [ZeroBasedBudgetCalculator]. An assignment of exactly
 * zero is dropped rather than stored — zero is the absence of an assignment.
 */
@Singleton
class MonthlyBudgetStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("heymoney_monthly_budget", Context.MODE_PRIVATE)

    private val _assignments = MutableStateFlow(loadAssignments())
    val assignments: StateFlow<Map<CategoryMonth, Double>> = _assignments

    private val _goals = MutableStateFlow(loadGoals())
    val goals: StateFlow<Map<UUID, CategoryGoal>> = _goals

    @Synchronized
    fun setAssignment(categoryId: UUID, month: YearMonth, amount: Double) {
        val key = CategoryMonth(categoryId, month)
        val next = _assignments.value.toMutableMap()
        if (amount == 0.0) next.remove(key) else next[key] = amount
        saveAssignments(next)
        _assignments.value = next
    }

    @Synchronized
    fun setGoal(categoryId: UUID, targetAmount: Double, targetMonth: YearMonth) {
        val next = _goals.value.toMutableMap()
        next[categoryId] = CategoryGoal(categoryId, targetAmount, targetMonth)
        saveGoals(next)
        _goals.value = next
    }

    @Synchronized
    fun clearGoal(categoryId: UUID) {
        val next = _goals.value.toMutableMap()
        next.remove(categoryId)
        saveGoals(next)
        _goals.value = next
    }

    // ------------------------------------------------------------------ JSON

    private fun saveAssignments(map: Map<CategoryMonth, Double>) {
        val root = JSONObject()
        map.forEach { (key, amount) ->
            root.put("${key.categoryId}|${key.month}", amount)
        }
        prefs.edit().putString(ASSIGNMENTS, root.toString()).apply()
    }

    private fun loadAssignments(): Map<CategoryMonth, Double> = runCatching {
        val root = JSONObject(prefs.getString(ASSIGNMENTS, "{}"))
        val result = mutableMapOf<CategoryMonth, Double>()
        root.keys().forEach { rawKey ->
            val parts = rawKey.split("|")
            if (parts.size != 2) return@forEach
            val categoryId = runCatching { UUID.fromString(parts[0]) }.getOrNull()
                ?: return@forEach
            val month = runCatching { YearMonth.parse(parts[1]) }.getOrNull()
                ?: return@forEach
            result[CategoryMonth(categoryId, month)] = root.getDouble(rawKey)
        }
        result
    }.getOrDefault(emptyMap())

    private fun saveGoals(map: Map<UUID, CategoryGoal>) {
        val root = JSONObject()
        map.forEach { (categoryId, goal) ->
            root.put(
                categoryId.toString(),
                JSONObject()
                    .put("target", goal.targetAmount)
                    .put("targetMonth", goal.targetMonth.toString())
            )
        }
        prefs.edit().putString(GOALS, root.toString()).apply()
    }

    private fun loadGoals(): Map<UUID, CategoryGoal> = runCatching {
        val root = JSONObject(prefs.getString(GOALS, "{}"))
        val result = mutableMapOf<UUID, CategoryGoal>()
        root.keys().forEach { rawKey ->
            val categoryId = runCatching { UUID.fromString(rawKey) }.getOrNull()
                ?: return@forEach
            val o = root.getJSONObject(rawKey)
            val targetMonth = runCatching { YearMonth.parse(o.getString("targetMonth")) }
                .getOrNull() ?: return@forEach
            result[categoryId] = CategoryGoal(
                categoryId = categoryId,
                targetAmount = o.getDouble("target"),
                targetMonth = targetMonth,
            )
        }
        result
    }.getOrDefault(emptyMap())

    private companion object {
        const val ASSIGNMENTS = "assignments"
        const val GOALS = "goals"
    }
}
