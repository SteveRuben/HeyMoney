package org.abodah.heymoney.assistant.ai

/**
 * The on-device model the assistant can download on demand.
 *
 * Why not Gemma 3 1B, the architecture note's first pick? Both Google's
 * HuggingFace repo and the litert-community mirror are *gated*: an anonymous
 * request for the file answers HTTP 401 (verified 2026-11-03), so the app
 * could never download it without asking the user for a HuggingFace account
 * and token — exactly the bring-your-own-key friction HeyMoney removed when
 * Claude was dropped.
 *
 * Qwen2.5-0.5B-Instruct (litert-community conversion to a MediaPipe .task
 * bundle) is public — an anonymous ranged GET returns real bytes (HTTP 206,
 * verified 2026-11-03) — Apache-2.0, so it can ship inside a distributed
 * app, multilingual FR/EN, and the int8 bundle is ~547 MB. The trade-off is
 * raw capability: 0.5B is small, which is why the assistant only lets the
 * model phrase and route — every figure is computed by code and checked by
 * [FigureGuard].
 */
object AssistantModelInfo {
    const val DISPLAY_NAME = "Qwen2.5-0.5B-Instruct"
    const val FILE_NAME = "qwen2.5-0.5b-instruct-q8.task"
    const val DOWNLOAD_URL =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/" +
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"

    /** Exact size of the bundle; a file of any other size is not trusted. */
    const val EXPECTED_SIZE_BYTES = 546_660_344L

    /** KV-cache budget of this bundle: prompt + answer must fit inside. */
    const val MAX_TOKENS = 1280
}
