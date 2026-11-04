package org.abodah.heymoney.assistant.ai

/**
 * Prompt formatting for the Qwen2.5 bundle: the .task file ships the raw
 * model, the ChatML template is applied by the caller (same convention as
 * Google's AI Edge Gallery). Output is cut at the first end-of-turn marker.
 */
object LlmPrompt {

    fun chat(system: String, user: String): String =
        "<|im_start|>system\n$system<|im_end|>\n" +
            "<|im_start|>user\n$user<|im_end|>\n" +
            "<|im_start|>assistant\n"

    fun clean(raw: String): String = raw
        .substringBefore("<|im_end|>")
        .substringBefore("<|endoftext|>")
        .trim()
}
