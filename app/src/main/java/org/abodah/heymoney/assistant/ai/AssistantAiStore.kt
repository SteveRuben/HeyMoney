package org.abodah.heymoney.assistant.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The assistant-AI switches the user controls, kept in SharedPreferences
 * (same pattern as the subscription and monthly-budget stores).
 *
 * The internet switch defaults to OFF: the on-device model works fully
 * offline, and web lookups are an explicit opt-in. Nothing here stores
 * financial data — only this one boolean.
 */
@Singleton
class AssistantAiStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _internetEnabled = MutableStateFlow(prefs.getBoolean(KEY_INTERNET, false))
    val internetEnabled: StateFlow<Boolean> = _internetEnabled

    fun setInternetEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTERNET, enabled).apply()
        _internetEnabled.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "heymoney_assistant_ai"
        const val KEY_INTERNET = "internet_enabled"
    }
}
