package org.abodah.heymoney.subscriptions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** What the user recorded about one detected subscription. */
data class SubscriptionDecision(
    val status: SubscriptionStatus? = null,
    /** Free note kept on the detail screen (contract number, call outcome, ...). */
    val note: String = "",
    /** Days before the next due date the reminder should fire. 0 = reminders off. */
    val reminderDays: Int = DEFAULT_REMINDER_DAYS,
    /** Due date (epoch day) a reminder was already shown for, so it is not repeated daily. */
    val lastNotifiedDueEpochDay: Long? = null,
) {
    companion object {
        const val DEFAULT_REMINDER_DAYS = 3
    }
}

/**
 * The user's decisions about detected subscriptions, kept in SharedPreferences
 * (same pattern as the bank-alert store). Detection itself is stateless: it is
 * recomputed from the transactions, this store only remembers what the user
 * said about each detected subscription, keyed by [DetectedSubscription.key].
 */
@Singleton
class SubscriptionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("heymoney_subscriptions", Context.MODE_PRIVATE)

    private val _decisions = MutableStateFlow(load())
    val decisions: StateFlow<Map<String, SubscriptionDecision>> = _decisions

    fun decisionFor(key: String): SubscriptionDecision =
        _decisions.value[key] ?: SubscriptionDecision()

    @Synchronized
    fun setStatus(key: String, status: SubscriptionStatus?) {
        update(key) { it.copy(status = status) }
    }

    @Synchronized
    fun setNote(key: String, note: String) {
        update(key) { it.copy(note = note) }
    }

    @Synchronized
    fun setReminderDays(key: String, days: Int) {
        // A new lead time must be able to notify for the upcoming due date again.
        update(key) { it.copy(reminderDays = days, lastNotifiedDueEpochDay = null) }
    }

    @Synchronized
    fun markNotified(key: String, dueEpochDay: Long) {
        update(key) { it.copy(lastNotifiedDueEpochDay = dueEpochDay) }
    }

    private fun update(key: String, change: (SubscriptionDecision) -> SubscriptionDecision) {
        val next = _decisions.value.toMutableMap()
        next[key] = change(next[key] ?: SubscriptionDecision())
        save(next)
        _decisions.value = next
    }

    private fun save(map: Map<String, SubscriptionDecision>) {
        val root = JSONObject()
        map.forEach { (key, decision) ->
            root.put(
                key,
                JSONObject()
                    .put("status", decision.status?.name ?: JSONObject.NULL)
                    .put("note", decision.note)
                    .put("reminderDays", decision.reminderDays)
                    .put(
                        "lastNotifiedDueEpochDay",
                        decision.lastNotifiedDueEpochDay ?: JSONObject.NULL
                    )
            )
        }
        prefs.edit().putString(DECISIONS, root.toString()).apply()
    }

    private fun load(): Map<String, SubscriptionDecision> = runCatching {
        val root = JSONObject(prefs.getString(DECISIONS, "{}"))
        val result = mutableMapOf<String, SubscriptionDecision>()
        root.keys().forEach { key ->
            val o = root.getJSONObject(key)
            result[key] = SubscriptionDecision(
                status = if (o.isNull("status")) null
                else runCatching { SubscriptionStatus.valueOf(o.getString("status")) }.getOrNull(),
                note = o.optString("note", ""),
                reminderDays = o.optInt("reminderDays", SubscriptionDecision.DEFAULT_REMINDER_DAYS),
                lastNotifiedDueEpochDay = if (o.isNull("lastNotifiedDueEpochDay")) null
                else o.getLong("lastNotifiedDueEpochDay"),
            )
        }
        result
    }.getOrDefault(emptyMap())

    private companion object {
        const val DECISIONS = "decisions"
    }
}
