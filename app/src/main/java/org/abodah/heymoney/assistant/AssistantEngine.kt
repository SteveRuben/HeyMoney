package org.abodah.heymoney.assistant

import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abodah.heymoney.alerts.AlertStore
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.util.Currency
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * On-device assistant: understands a handful of finance questions (English or French),
 * and answers from the user's real accounts and transactions. No network, no account.
 */
class AssistantEngine @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val alertStore: AlertStore,
) {
    private val fr get() = Locale.getDefault().language == "fr"

    /** Questions offered as one-tap chips. */
    fun suggestions(): List<String> = if (fr) {
        listOf(
            "Quel est mon solde ?",
            "Combien ai-je dépensé ce mois-ci ?",
            "Où part mon argent ?",
            "Combien puis-je dépenser par jour ?",
            "Plus grosse dépense ce mois-ci ?",
        )
    } else {
        listOf(
            "What is my balance?",
            "How much did I spend this month?",
            "Where does my money go?",
            "How much can I spend per day?",
            "Biggest expense this month?",
        )
    }

    fun greeting(): String = if (fr) {
        "Bonjour 👋 Je réponds à vos questions sur vos comptes et vos dépenses. Touchez une suggestion ou écrivez la vôtre."
    } else {
        "Hi 👋 Ask me about your accounts and spending. Tap a suggestion or type your own question."
    }

    suspend fun respond(question: String): String = withContext(Dispatchers.IO) {
        val q = question.lowercase(Locale.ROOT)
        val accounts = accountDao.findAll()
        val currencyOf = accounts.associate { it.id to (it.currency ?: "") }
        val transactions = transactionDao.findAll()
        val categories = categoryDao.findAll()
        val categoryNames = categories.associate { it.id to it.name }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        val range = when {
            has(q, "last month", "mois dernier", "mois précédent", "mois precedent") ->
                month.minusMonths(1)
            else -> month
        }
        val inRange = transactions.filter { it.dateTime != null && yearMonth(it, zone) == range }

        fun spent(list: List<TransactionEntity>) = totals(list, TransactionType.EXPENSE, currencyOf)
        fun earned(list: List<TransactionEntity>) = totals(list, TransactionType.INCOME, currencyOf)

        // a category named in the question wins: "how much did I spend on food?"
        val mentioned = categories.firstOrNull { q.contains(it.name.lowercase(Locale.ROOT)) }

        when {
            mentioned != null -> {
                val list = inRange.filter { it.categoryId == mentioned.id }
                val sum = spent(list)
                if (sum.isEmpty()) {
                    t("Nothing spent on ${mentioned.name} ${label(range, month)}.",
                        "Rien dépensé en ${mentioned.name} ${label(range, month)}.")
                } else {
                    t("${mentioned.name} ${label(range, month)}: ${money(sum)}.",
                        "${mentioned.name} ${label(range, month)} : ${money(sum)}.")
                }
            }

            has(q, "balance", "solde", "how much do i have", "combien ai-je") -> {
                val balances = balances(accounts.filter { it.includeInBalance }.map { it.id }, transactions, currencyOf)
                if (balances.isEmpty()) t("No accounts yet.", "Aucun compte pour l'instant.")
                else t("Your balance: ${money(balances)}.", "Votre solde : ${money(balances)}.")
            }

            has(q, "per day", "par jour", "daily", "can i spend", "puis-je dépenser", "puis-je depenser", "reste") -> {
                val left = earned(inRange).mapValues { (cur, inc) ->
                    inc - (spent(inRange)[cur] ?: 0.0)
                }
                val daysLeft = (month.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
                if (left.isEmpty()) {
                    t("Add this month's income first, then I can estimate a daily budget.",
                        "Ajoutez d'abord vos revenus du mois pour estimer un budget par jour.")
                } else {
                    val perDay = left.mapValues { (it.value / daysLeft).coerceAtLeast(0.0) }
                    t("Left this month: ${money(left)}. About ${money(perDay)} per day for the next $daysLeft days.",
                        "Reste ce mois-ci : ${money(left)}. Environ ${money(perDay)} par jour pour les $daysLeft jours à venir.")
                }
            }

            has(q, "where", "où part", "ou part", "categor", "catégor", "top") -> {
                val byCategory = inRange
                    .filter { it.type == TransactionType.EXPENSE }
                    .groupBy { it.categoryId }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .entries.sortedByDescending { it.value }.take(5)
                if (byCategory.isEmpty()) {
                    t("No spending ${label(range, month)}.", "Aucune dépense ${label(range, month)}.")
                } else {
                    val total = byCategory.sumOf { it.value }
                    val lines = byCategory.joinToString("\n") { (id, amount) ->
                        val name = categoryNames[id as UUID?] ?: t("No category", "Sans catégorie")
                        "• $name — ${(amount / total * 100).toInt()}%"
                    }
                    t("Top spending ${label(range, month)}:\n$lines", "Principales dépenses ${label(range, month)} :\n$lines")
                }
            }

            has(q, "biggest", "largest", "plus grosse", "plus grande", "plus gros") -> {
                val top = inRange.filter { it.type == TransactionType.EXPENSE }.maxByOrNull { it.amount }
                if (top == null) t("No expense ${label(range, month)}.", "Aucune dépense ${label(range, month)}.")
                else {
                    val what = top.title?.takeIf { it.isNotBlank() } ?: categoryNames[top.categoryId] ?: "?"
                    val cur = currencyOf[top.accountId].orEmpty()
                    t("Biggest expense ${label(range, month)}: $what, ${format(top.amount, cur)}.",
                        "Plus grosse dépense ${label(range, month)} : $what, ${format(top.amount, cur)}.")
                }
            }

            has(q, "income", "earn", "revenu", "gagné", "gagne", "salary", "salaire") -> {
                val sum = earned(inRange)
                if (sum.isEmpty()) t("No income ${label(range, month)}.", "Aucun revenu ${label(range, month)}.")
                else t("Income ${label(range, month)}: ${money(sum)}.", "Revenus ${label(range, month)} : ${money(sum)}.")
            }

            has(q, "spent", "spend", "expense", "dépens", "depens") -> {
                val sum = spent(inRange)
                val previous = spent(transactions.filter { it.dateTime != null && yearMonth(it, zone) == range.minusMonths(1) })
                if (sum.isEmpty()) t("No spending ${label(range, month)}.", "Aucune dépense ${label(range, month)}.")
                else {
                    val trend = sum.entries.firstOrNull()?.let { (cur, now) ->
                        val before = previous[cur] ?: 0.0
                        if (before > 0) {
                            val pct = ((now - before) / before * 100).toInt()
                            when {
                                pct > 0 -> t(" That is $pct% more than the month before.", " Soit $pct% de plus que le mois précédent.")
                                pct < 0 -> t(" That is ${-pct}% less than the month before.", " Soit ${-pct}% de moins que le mois précédent.")
                                else -> ""
                            }
                        } else ""
                    }.orEmpty()
                    t("Spent ${label(range, month)}: ${money(sum)}.$trend", "Dépensé ${label(range, month)} : ${money(sum)}.$trend")
                }
            }

            has(q, "alert", "alerte", "sms", "notification") -> {
                val n = alertStore.pending.value.size
                if (n == 0) t("No bank alert is waiting.", "Aucune alerte bancaire en attente.")
                else t("$n bank alert(s) are waiting for your confirmation (Settings › Bank alerts).",
                    "$n alerte(s) bancaire(s) attendent votre confirmation (Réglages › Alertes bancaires).")
            }

            else -> t(
                "I can answer about your balance, spending, income, categories, a daily budget or a category by name (for example “food”). Try a suggestion below.",
                "Je peux répondre sur votre solde, vos dépenses, revenus, catégories, un budget par jour ou une catégorie par son nom (par exemple « courses »). Essayez une suggestion."
            )
        }
    }

    private fun has(q: String, vararg words: String) = words.any { q.contains(it) }

    private fun t(en: String, frText: String) = if (fr) frText else en

    private fun label(range: YearMonth, current: YearMonth) = when (range) {
        current -> t("this month", "ce mois-ci")
        else -> t("last month", "le mois dernier")
    }

    private fun yearMonth(t: TransactionEntity, zone: ZoneId) =
        YearMonth.from(Instant.ofEpochMilli(t.dateTime!!.toEpochMilli()).atZone(zone))

    private fun totals(
        list: List<TransactionEntity>,
        type: TransactionType,
        currencyOf: Map<UUID, String>
    ): Map<String, Double> = list.filter { it.type == type }
        .groupBy { currencyOf[it.accountId].orEmpty() }
        .mapValues { (_, l) -> l.sumOf { it.amount } }

    private fun balances(
        accountIds: List<UUID>,
        all: List<TransactionEntity>,
        currencyOf: Map<UUID, String>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        accountIds.forEach { id ->
            val cur = currencyOf[id].orEmpty()
            var sum = 0.0
            all.forEach { tr ->
                // planned transactions (no date yet) are not part of the balance
                if (tr.dateTime == null) return@forEach
                when {
                    tr.accountId == id && tr.type == TransactionType.INCOME -> sum += tr.amount
                    tr.accountId == id && tr.type == TransactionType.EXPENSE -> sum -= tr.amount
                    tr.accountId == id && tr.type == TransactionType.TRANSFER -> sum -= tr.amount
                    tr.toAccountId == id && tr.type == TransactionType.TRANSFER ->
                        sum += tr.toAmount ?: tr.amount
                }
            }
            result[cur] = (result[cur] ?: 0.0) + sum
        }
        return result
    }

    private fun money(map: Map<String, Double>) =
        map.entries.joinToString(" • ") { (cur, value) -> format(value, cur) }

    private fun format(value: Double, currency: String): String {
        val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
        val symbol = runCatching { Currency.getInstance(currency).currencyCode }.getOrDefault(currency)
        return "${nf.format(value)} $symbol".trim()
    }
}
