package org.abodah.heymoney.assistant.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject

/** An AI-path answer. [usedWeb] drives the visible "Source: web" marker. */
data class AiAnswer(
    val text: String,
    val usedWeb: Boolean = false
)

/**
 * The RAG orchestration, in order:
 *
 *  1. **Retrieve** — [AssistantContextBuilder] computes the facts from
 *     the app's data (code only, no model).
 *  2. **Route** — the model classifies the question into a strict-JSON
 *     intent ([IntentParser]); it never computes anything.
 *  3. **Answer** — money intents are answered from the facts by
 *     [FactsAnswerer]; the model may *rephrase* that answer, and the
 *     rephrase is only kept when [FigureGuard] confirms every number
 *     in it exists in the facts. Open questions are answered by the
 *     model grounded on the facts block, under the same guard.
 *  4. **Web** — only when the user opted in ([AssistantAiStore]) and
 *     the intent needs outside information. The outgoing query is
 *     sanitized ([WebQuerySanitizer]) so no financial data leaves the
 *     phone, and the answer is flagged so the UI shows "Source: web".
 *
 * Any failure at any step returns null: the caller falls back to the
 * rule-based engine and the user never sees a broken AI.
 */
class AssistantAiResponder @Inject constructor(
    private val llm: LlmEngine,
    private val aiStore: AssistantAiStore,
    private val contextBuilder: AssistantContextBuilder,
    private val webLookup: WebLookup
) {
    private val fr get() = Locale.getDefault().language == "fr"
    private val languageName get() = if (fr) "French" else "English"

    suspend fun respondOrNull(question: String): AiAnswer? {
        if (!llm.isAvailable()) return null
        val facts = try {
            contextBuilder.build()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val parsed = classify(question, facts)
            ?: ParsedIntent(AssistantIntent.GENERAL)
        val resolved = resolveCategory(parsed, facts)
        return when (resolved.intent) {
            AssistantIntent.WEB_INFO -> webAnswer(question, resolved, facts)
            AssistantIntent.GENERAL -> groundedAnswer(question, facts, webBlock = null)
            else -> templateAnswer(question, resolved, facts)
        }
    }

    // region Routing

    private suspend fun classify(
        question: String,
        facts: AssistantFacts
    ): ParsedIntent? {
        val user = "CATEGORIES: ${facts.categoryNames.joinToString(", ")}\n" +
            "QUESTION: $question"
        val raw = generateOrNull(LlmPrompt.chat(INTENT_SYSTEM, user)) ?: return null
        return IntentParser.parse(LlmPrompt.clean(raw))
    }

    /** Maps the model's category wording onto a real category name. */
    private fun resolveCategory(
        parsed: ParsedIntent,
        facts: AssistantFacts
    ): ParsedIntent {
        if (parsed.intent != AssistantIntent.CATEGORY_SPENDING) return parsed
        val wanted = parsed.category?.trim() ?: return parsed
        val match = facts.expensesByCategoryThisMonth.keys.firstOrNull {
            it.equals(wanted, ignoreCase = true)
        } ?: facts.expensesByCategoryThisMonth.keys.firstOrNull {
            it.contains(wanted, ignoreCase = true) ||
                wanted.contains(it, ignoreCase = true)
        } ?: facts.categoryNames.firstOrNull { it.equals(wanted, ignoreCase = true) }
        return parsed.copy(category = match)
    }

    // endregion

    // region Answers

    private suspend fun templateAnswer(
        question: String,
        parsed: ParsedIntent,
        facts: AssistantFacts
    ): AiAnswer? {
        val template = FactsAnswerer.answer(parsed, facts, fr)
            ?: return groundedAnswer(question, facts, webBlock = null)
        val rephrased = rephrase(question, template, facts)
        return AiAnswer(rephrased ?: template)
    }

    private suspend fun rephrase(
        question: String,
        template: String,
        facts: AssistantFacts
    ): String? {
        val user = "QUESTION: $question\nANSWER: $template"
        val raw = generateOrNull(LlmPrompt.chat(rephraseSystem(), user)) ?: return null
        val text = LlmPrompt.clean(raw)
        if (text.isBlank() || text.length > MAX_ANSWER_LENGTH) return null
        val allowed = facts.allowedNumbers + FigureGuard.extractNumbers(template)
        return if (FigureGuard.isGrounded(text, allowed)) text else null
    }

    private suspend fun groundedAnswer(
        question: String,
        facts: AssistantFacts,
        webBlock: WebResult?
    ): AiAnswer? {
        val user = buildString {
            append("DATA:\n").append(facts.contextText)
            if (webBlock != null) {
                append("\nWEB (source: ").append(webBlock.sourceLabel).append("):\n")
                append(webBlock.text)
            }
            append("\nQUESTION: ").append(question)
        }
        val raw = generateOrNull(LlmPrompt.chat(groundedSystem(), user)) ?: return null
        val text = LlmPrompt.clean(raw)
        if (text.isBlank() || text.length > MAX_ANSWER_LENGTH) return null
        val allowed = facts.allowedNumbers +
            (webBlock?.let { FigureGuard.extractNumbers(it.text) } ?: emptySet())
        if (!FigureGuard.isGrounded(text, allowed)) return null
        return AiAnswer(text, usedWeb = webBlock != null)
    }

    // endregion

    // region Web

    private suspend fun webAnswer(
        question: String,
        parsed: ParsedIntent,
        facts: AssistantFacts
    ): AiAnswer {
        if (!aiStore.internetEnabled.value) {
            return AiAnswer(
                t(
                    "That needs the internet, and web lookups are off. " +
                        "Open the assistant settings (gear icon) to allow them — " +
                        "only a generic topic is ever sent, never your financial data.",
                    "Cela demande internet, et les consultations web sont désactivées. " +
                        "Ouvrez les réglages de l'assistant (icône engrenage) pour les " +
                        "autoriser — seul un sujet générique est envoyé, jamais vos " +
                        "données financières."
                )
            )
        }
        val query = WebQuerySanitizer.sanitize(
            parsed.webQuery ?: question,
            facts.personalTerms
        )
        if (query.isEmpty()) {
            return AiAnswer(
                t(
                    "I could not turn that into a safe, generic web query.",
                    "Je n'ai pas pu formuler une requête web générique et sûre."
                )
            )
        }
        val result = try {
            webLookup.lookup(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return AiAnswer(
            t(
                "I found nothing online for “$query”.",
                "Je n'ai rien trouvé en ligne pour « $query »."
            )
        )
        val grounded = groundedAnswer(question, facts, webBlock = result)
        if (grounded != null) return grounded.copy(usedWeb = true)
        // The model could not phrase it safely: quote the source as-is.
        return AiAnswer(
            text = t(
                "From the web (${result.sourceLabel}): ${result.text}",
                "Depuis le web (${result.sourceLabel}) : ${result.text}"
            ),
            usedWeb = true
        )
    }

    // endregion

    private suspend fun generateOrNull(prompt: String): String? =
        try {
            withTimeoutOrNull(GENERATE_TIMEOUT_MS) { llm.generate(prompt) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun t(en: String, frText: String) = if (fr) frText else en

    private fun rephraseSystem() =
        "Rewrite the ANSWER in one or two short, friendly sentences in $languageName. " +
            "Keep every number exactly as written in the ANSWER. Add nothing, remove nothing."

    private fun groundedSystem() =
        "You are HeyMoney, an on-device money assistant. Answer in $languageName, " +
            "briefly (3 sentences max). For anything about the user's money, use ONLY " +
            "the DATA block: never invent numbers, accounts or transactions. If DATA " +
            "does not contain the answer, say so plainly."

    private companion object {
        const val GENERATE_TIMEOUT_MS = 90_000L
        const val MAX_ANSWER_LENGTH = 900

        const val INTENT_SYSTEM =
            "You classify a personal-finance question. Reply with JSON only, no other text.\n" +
                "{\"intent\":\"...\",\"category\":null,\"web_query\":null}\n" +
                "intent is one of: balance, spending, income, category_spending, " +
                "top_categories, biggest_expense, daily_budget, subscriptions, " +
                "planned_payments, budget_status, alerts, web_info, general.\n" +
                "category_spending: the user asks about one spending category; set " +
                "\"category\" to the exact matching name from CATEGORIES, else null.\n" +
                "web_info: the answer needs the internet (exchange rates, prices, " +
                "outside facts); set \"web_query\" to a short generic English query " +
                "with no personal data."
    }
}
