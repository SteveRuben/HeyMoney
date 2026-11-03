package org.abodah.heymoney.assistant.ai

/**
 * The on-device LLM, hidden behind this interface: the assistant's
 * routing, RAG and guard-rails never import a concrete runtime, so the
 * engine can be swapped (or absent) without touching them.
 */
interface LlmEngine {

    /** True when a verified model is on disk and generation can be tried. */
    fun isAvailable(): Boolean

    /**
     * Runs one self-contained prompt and returns the raw completion.
     * Callers format the prompt ([LlmPrompt]) and validate the output.
     * Throws when the model is missing, corrupt or the runtime fails —
     * callers treat any throw as "no AI" and fall back.
     */
    suspend fun generate(prompt: String): String

    /** Releases the native engine so the model file can be deleted. */
    fun release()
}
