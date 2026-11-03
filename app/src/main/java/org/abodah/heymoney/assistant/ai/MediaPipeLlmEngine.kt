package org.abodah.heymoney.assistant.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LlmEngine] on MediaPipe Tasks GenAI (the LiteRT-based LLM runtime).
 *
 * Every question runs in a *fresh* session: the engine's implicit
 * session keeps its KV cache across calls, which would leak one
 * question's context into the next and overflow the bundle's small
 * 1280-token cache. CPU backend only — it is the one configuration
 * that works on every device the app supports.
 */
@Singleton
class MediaPipeLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager
) : LlmEngine {

    private var inference: LlmInference? = null

    override fun isAvailable(): Boolean =
        modelManager.state.value is ModelState.Ready

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(TOP_K)
            .setTopP(TOP_P)
            .setTemperature(TEMPERATURE)
            .setRandomSeed(RANDOM_SEED)
            .build()
        LlmInferenceSession.createFromOptions(obtainEngine(), sessionOptions).use { session ->
            session.addQueryChunk(prompt)
            session.generateResponse()
        }
    }

    @Synchronized
    private fun obtainEngine(): LlmInference {
        inference?.let { return it }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelManager.modelFile().absolutePath)
            .setMaxTokens(AssistantModelInfo.MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        return LlmInference.createFromOptions(context, options)
            .also { inference = it }
    }

    @Synchronized
    override fun release() {
        inference?.close()
        inference = null
    }

    private companion object {
        const val TOP_K = 40
        const val TOP_P = 0.9f
        const val TEMPERATURE = 0.3f
        const val RANDOM_SEED = 7
    }
}
