package org.abodah.heymoney.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val fromUser: Boolean)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val engine: AssistantEngine
) : ViewModel() {

    val suggestions: List<String> = engine.suggestions()

    private val _messages = MutableStateFlow(listOf(ChatMessage(engine.greeting(), fromUser = false)))
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _thinking.value) return
        _messages.update { it + ChatMessage(text, fromUser = true) }
        _thinking.value = true
        viewModelScope.launch {
            val answer = engine.respond(text)
            _messages.update { it + ChatMessage(answer, fromUser = false) }
            _thinking.value = false
        }
    }
}
