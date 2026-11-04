package org.abodah.heymoney.assistant.ai

import java.text.NumberFormat
import java.util.Currency

/**
 * Authoritative answers built from [AssistantFacts] by code alone —
 * the same job the rule-based [org.abodah.heymoney.assistant.AssistantEngine]
 * does, but from the pre-computed facts. The model may rephrase these
 * (checked by [FigureGuard]); it never originates the figures.
 */
object FactsAnswerer {

    fun answer(intent: ParsedIntent, facts: AssistantFacts, fr: Boolean): String? {
        fun t(en: String, frText: String) = if (fr) frText else en
        return when (intent.intent) {
            AssistantIntent.BALANCE ->
                if (facts.balances.isEmpty()) {
                    t("No accounts yet.", "Aucun compte pour l'instant.")
                } else {
                    t(
                        "Your balance: ${money(facts.balances)}.",
                        "Votre solde : ${money(facts.balances)}."
                    )
                }

            AssistantIntent.SPENDING -> {
                if (facts.expensesThisMonth.isEmpty()) {
                    t("No spending this month.", "Aucune dépense ce mois-ci.")
                } else {
                    val now = facts.expensesThisMonth[facts.primaryCurrency]
                    val before = facts.expensesLastMonth[facts.primaryCurrency]
                    val trend = if (now != null && before != null && before > 0) {
                        val pct = ((now - before) / before * 100).toInt()
                        when {
                            pct > 0 -> t(
                                " That is $pct% more than last month.",
                                " Soit $pct% de plus que le mois dernier."
                            )
                            pct < 0 -> t(
                                " That is ${-pct}% less than last month.",
                                " Soit ${-pct}% de moins que le mois dernier."
                            )
                            else -> ""
                        }
                    } else {
                        ""
                    }
                    t(
                        "Spent this month: ${money(facts.expensesThisMonth)}.$trend",
                        "Dépensé ce mois-ci : ${money(facts.expensesThisMonth)}.$trend"
                    )
                }
            }

            AssistantIntent.INCOME ->
                if (facts.incomeThisMonth.isEmpty()) {
                    t("No income this month.", "Aucun revenu ce mois-ci.")
                } else {
                    t(
                        "Income this month: ${money(facts.incomeThisMonth)}.",
                        "Revenus ce mois-ci : ${money(facts.incomeThisMonth)}."
                    )
                }

            AssistantIntent.CATEGORY_SPENDING -> {
                val category = intent.category ?: return null
                val byCurrency = facts.expensesByCategoryThisMonth[category]
                if (byCurrency.isNullOrEmpty()) {
                    t(
                        "Nothing spent on $category this month.",
                        "Rien dépensé en $category ce mois-ci."
                    )
                } else {
                    t(
                        "$category this month: ${money(byCurrency)}.",
                        "$category ce mois-ci : ${money(byCurrency)}."
                    )
                }
            }

            AssistantIntent.TOP_CATEGORIES ->
                if (facts.topCategories.isEmpty()) {
                    t("No spending this month.", "Aucune dépense ce mois-ci.")
                } else {
                    val lines = facts.topCategories.joinToString("\n") {
                        "• ${it.name} — ${format(it.amount, facts.primaryCurrency)} " +
                            "(${it.sharePercent}%)"
                    }
                    t(
                        "Top spending this month:\n$lines",
                        "Principales dépenses ce mois-ci :\n$lines"
                    )
                }

            AssistantIntent.BIGGEST_EXPENSE -> {
                val biggest = facts.biggestExpense
                if (biggest == null) {
                    t("No expense this month.", "Aucune dépense ce mois-ci.")
                } else {
                    t(
                        "Biggest expense this month: ${biggest.label}, " +
                            "${format(biggest.amount, biggest.currency)}.",
                        "Plus grosse dépense ce mois-ci : ${biggest.label}, " +
                            "${format(biggest.amount, biggest.currency)}."
                    )
                }
            }

            AssistantIntent.DAILY_BUDGET -> {
                val allowance = facts.dailyAllowance
                if (allowance == null) {
                    t(
                        "Add this month's income first, then I can estimate a daily budget.",
                        "Ajoutez d'abord vos revenus du mois pour estimer un budget par jour."
                    )
                } else {
                    val left = (facts.incomeThisMonth[facts.primaryCurrency] ?: 0.0) -
                        (facts.expensesThisMonth[facts.primaryCurrency] ?: 0.0)
                    t(
                        "Left this month: ${format(left, facts.primaryCurrency)}. " +
                            "About ${format(allowance, facts.primaryCurrency)} per day " +
                            "for the next ${facts.daysLeftInMonth} days.",
                        "Reste ce mois-ci : ${format(left, facts.primaryCurrency)}. " +
                            "Environ ${format(allowance, facts.primaryCurrency)} par jour " +
                            "pour les ${facts.daysLeftInMonth} jours à venir."
                    )
                }
            }

            AssistantIntent.SUBSCRIPTIONS ->
                if (facts.subscriptions.isEmpty()) {
                    t(
                        "No subscription detected in your transactions.",
                        "Aucun abonnement détecté dans vos transactions."
                    )
                } else {
                    val next = facts.subscriptions.sortedBy { it.nextDue }.take(3)
                        .joinToString("\n") {
                            "• ${it.name} — ${format(it.amount, it.currency)}, " +
                                "${t("due", "prévu le")} ${it.nextDue}"
                        }
                    t(
                        "${facts.subscriptions.size} subscription(s), about " +
                            "${money(facts.subscriptionYearly)} per year. Coming up:\n$next",
                        "${facts.subscriptions.size} abonnement(s), environ " +
                            "${money(facts.subscriptionYearly)} par an. Prochaines échéances :\n$next"
                    )
                }

            AssistantIntent.PLANNED_PAYMENTS ->
                if (facts.plannedPayments.isEmpty()) {
                    t(
                        "No planned payment coming up.",
                        "Aucun paiement planifié à venir."
                    )
                } else {
                    val lines = facts.plannedPayments.joinToString("\n") {
                        "• ${it.title} — ${format(it.amount, it.currency)}, " +
                            "${t("next on", "prochain le")} ${it.nextDate}"
                    }
                    t("Planned payments:\n$lines", "Paiements planifiés :\n$lines")
                }

            AssistantIntent.BUDGET_STATUS ->
                if (facts.budgetAssignedTotal <= 0.0) {
                    t(
                        "No monthly budget assigned yet this month. " +
                            "Open “Budget du mois” to assign your income.",
                        "Aucun budget mensuel assigné ce mois-ci. " +
                            "Ouvrez « Budget du mois » pour assigner vos revenus."
                    )
                } else {
                    val over = if (facts.budgetOverCategories.isEmpty()) {
                        ""
                    } else {
                        t(" Over budget: ", " Hors budget : ") +
                            facts.budgetOverCategories.joinToString(", ") {
                                "${it.first} (+${format(it.second, facts.primaryCurrency)})"
                            } + "."
                    }
                    t(
                        "Budget this month: assigned " +
                            "${format(facts.budgetAssignedTotal, facts.primaryCurrency)}, spent " +
                            "${format(facts.budgetSpentInAssigned, facts.primaryCurrency)} " +
                            "in those categories.$over",
                        "Budget ce mois-ci : assigné " +
                            "${format(facts.budgetAssignedTotal, facts.primaryCurrency)}, dépensé " +
                            "${format(facts.budgetSpentInAssigned, facts.primaryCurrency)} " +
                            "dans ces catégories.$over"
                    )
                }

            AssistantIntent.ALERTS ->
                if (facts.pendingAlerts == 0) {
                    t("No bank alert is waiting.", "Aucune alerte bancaire en attente.")
                } else {
                    t(
                        "${facts.pendingAlerts} bank alert(s) are waiting for your confirmation.",
                        "${facts.pendingAlerts} alerte(s) bancaire(s) attendent votre confirmation."
                    )
                }

            AssistantIntent.WEB_INFO, AssistantIntent.GENERAL -> null
        }
    }

    private fun money(map: Map<String, Double>): String =
        map.entries.joinToString(" • ") { (currency, value) -> format(value, currency) }

    private fun format(value: Double, currency: String): String {
        val nf = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
        val code = runCatching { Currency.getInstance(currency).currencyCode }
            .getOrDefault(currency)
        return "${nf.format(value)} $code".trim()
    }
}
