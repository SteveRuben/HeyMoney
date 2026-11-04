package org.abodah.heymoney.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.abodah.heymoney.assistant.ai.AssistantAiResponder
import org.abodah.heymoney.assistant.ai.AssistantAiStore
import org.abodah.heymoney.assistant.ai.LlmEngine
import org.abodah.heymoney.assistant.ai.ModelManager
import org.abodah.heymoney.assistant.ai.ModelState
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    /** True when the answer used a web lookup — the UI marks it "Source: web". */
    val usedWeb: Boolean = false
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val engine: AssistantEngine,
    private val aiResponder: AssistantAiResponder,
    private val modelManager: ModelManager,
    private val aiStore: AssistantAiStore,
    private val llmEngine: LlmEngine
) : ViewModel() {

    val suggestions: List<String> = engine.suggestions()

    val modelState: StateFlow<ModelState> = modelManager.state
    val internetEnabled: StateFlow<Boolean> = aiStore.internetEnabled

    private val _messages = MutableStateFlow(
        listOf(ChatMessage(engine.greeting(), fromUser = false))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    init {
        modelManager.refresh()
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _thinking.value) return
        _messages.update { it + ChatMessage(text, fromUser = true) }
        _thinking.value = true
        viewModelScope.launch {
            // AI path first (on-device model + RAG over the real data);
            // a missing model or any AI failure falls back to the
            // rule-based engine, so the assistant always answers.
            val ai = try {
                aiResponder.respondOrNull(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val answer = if (ai != null) {
                ChatMessage(ai.text, fromUser = false, usedWeb = ai.usedWeb)
            } else {
                ChatMessage(engine.respond(text), fromUser = false)
            }
            _messages.update { it + answer }
            _thinking.value = false
        }
    }

    // region Assistant AI settings (model lifecycle + web switch)

    fun downloadModel() = modelManager.startDownload()

    fun cancelModelDownload() = modelManager.cancelDownload()

    fun deleteModel() {
        // release the native engine first, then remove the file —
        // only the model directory is deleted, never app data.
        llmEngine.release()
        modelManager.deleteModel()
    }

    fun setInternetEnabled(enabled: Boolean) = aiStore.setInternetEnabled(enabled)

    // endregion
}
