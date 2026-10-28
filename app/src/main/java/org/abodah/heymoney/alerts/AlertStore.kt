package org.abodah.heymoney.alerts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pending bank-alert drafts, kept in SharedPreferences until the user confirms or dismisses them.
 * Ids already handled are remembered so the same SMS or notification never comes back.
 */
@Singleton
class AlertStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("heymoney_alerts", Context.MODE_PRIVATE)

    private val _pending = MutableStateFlow(load())
    val pending: StateFlow<List<BankAlert>> = _pending

    /** Adds a draft unless it is already pending or was handled before. */
    @Synchronized
    fun add(alert: BankAlert): Boolean {
        if (alert.id in handledIds() || _pending.value.any { it.id == alert.id }) return false
        save(listOf(alert) + _pending.value)
        return true
    }

    @Synchronized
    fun resolve(alert: BankAlert) {
        prefs.edit().putStringSet(HANDLED, (handledIds() + alert.id).takeLast(MAX_HANDLED).toSet()).apply()
        save(_pending.value.filterNot { it.id == alert.id })
    }

    private fun handledIds(): List<String> = prefs.getStringSet(HANDLED, emptySet())!!.toList()

    private fun save(list: List<BankAlert>) {
        val capped = list.take(MAX_PENDING)
        val array = JSONArray()
        capped.forEach { a ->
            array.put(
                JSONObject()
                    .put("id", a.id)
                    .put("source", a.source.name)
                    .put("sender", a.sender)
                    .put("raw", a.raw)
                    .put("amount", a.amount)
                    .put("currency", a.currency ?: JSONObject.NULL)
                    .put("income", a.isIncome)
                    .put("merchant", a.merchant)
                    .put("date", a.date)
            )
        }
        prefs.edit().putString(PENDING, array.toString()).apply()
        _pending.value = capped
    }

    private fun load(): List<BankAlert> = runCatching {
        val array = JSONArray(prefs.getString(PENDING, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            BankAlert(
                id = o.getString("id"),
                source = BankAlert.Source.valueOf(o.getString("source")),
                sender = o.getString("sender"),
                raw = o.getString("raw"),
                amount = o.getDouble("amount"),
                currency = if (o.isNull("currency")) null else o.getString("currency"),
                isIncome = o.getBoolean("income"),
                merchant = o.getString("merchant"),
                date = o.getLong("date")
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PENDING = "pending"
        const val HANDLED = "handled"
        const val MAX_PENDING = 50
        const val MAX_HANDLED = 500
    }
}
