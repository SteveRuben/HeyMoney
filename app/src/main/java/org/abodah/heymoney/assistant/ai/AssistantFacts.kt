package org.abodah.heymoney.assistant.ai

import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.IntervalType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abodah.heymoney.alerts.AlertStore
import org.abodah.heymoney.budget.MonthlyBudgetStore
import org.abodah.heymoney.subscriptions.SubscriptionDetector
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class CategorySpend(val name: String, val amount: Double, val sharePercent: Int)

data class BiggestExpense(
    val label: String,
    val amount: Double,
    val currency: String,
    val date: LocalDate?
)

data class SubscriptionFact(
    val name: String,
    val amount: Double,
    val currency: String,
    val frequency: String,
    val nextDue: LocalDate,
    val yearlyCost: Double
)

data class PlannedFact(
    val title: String,
    val amount: Double,
    val currency: String,
    val type: String,
    val nextDate: LocalDate
)

/**
 * Everything the assistant is allowed to say about the user's money,
 * computed by code from the app's real data. The model receives
 * [contextText] as its only source of truth; [allowedNumbers] is the
 * set [FigureGuard] checks its phrasing against.
 */
data class AssistantFacts(
    val today: LocalDate,
    val primaryCurrency: String,
    val accountCount: Int,
    val balances: Map<String, Double>,
    val incomeThisMonth: Map<String, Double>,
    val expensesThisMonth: Map<String, Double>,
    val expensesLastMonth: Map<String, Double>,
    val expensesByCategoryThisMonth: Map<String, Map<String, Double>>,
    val topCategories: List<CategorySpend>,
    val biggestExpense: BiggestExpense?,
    val dailyAllowance: Double?,
    val daysLeftInMonth: Int,
    val subscriptions: List<SubscriptionFact>,
    val subscriptionYearly: Map<String, Double>,
    val plannedPayments: List<PlannedFact>,
    val budgetAssignedTotal: Double,
    val budgetSpentInAssigned: Double,
    val budgetUnassignedIncome: Double?,
    val budgetOverCategories: List<Pair<String, Double>>,
    val pendingAlerts: Int,
    val categoryNames: List<String>,
    val personalTerms: List<String>,
    val contextText: String,
    val allowedNumbers: Set<Double>
)

/**
 * The retrieval half of the RAG assistant: structured queries over the
 * existing Room DAOs and HeyMoney stores (accounts, transactions,
 * budgets, detected subscriptions, planned payments). Every amount is
 * aggregated here, in code — the model downstream never sees raw rows
 * and never does arithmetic.
 */
class AssistantContextBuilder @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val subscriptionDetector: SubscriptionDetector,
    private val monthlyBudgetStore: MonthlyBudgetStore,
    private val alertStore: AlertStore
) {

    suspend fun build(): AssistantFacts = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val month = YearMonth.from(today)
        val lastMonth = month.minusMonths(1)

        val accounts = accountDao.findAll().filter { !it.isDeleted }
        val currencyOf = accounts.associate { it.id to (it.currency ?: "") }
        val transactions = transactionDao.findAll()
        val categories = categoryDao.findAll()
        val categoryNamesById = categories.associate { it.id to it.name }
        val rules = plannedPaymentRuleDao.findAll()

        fun monthOf(t: TransactionEntity): YearMonth? =
            t.dateTime?.let { YearMonth.from(it.atZone(zone)) }

        val thisMonth = transactions.filter { monthOf(it) == month }
        val previousMonth = transactions.filter { monthOf(it) == lastMonth }

        fun totals(list: List<TransactionEntity>, type: TransactionType) =
            list.filter { it.type == type }
                .groupBy { currencyOf[it.accountId].orEmpty() }
                .filterKeys { it.isNotEmpty() }
                .mapValues { (_, l) -> l.sumOf { it.amount } }

        val balances = computeBalances(accounts, transactions, currencyOf)
        val incomeThis = totals(thisMonth, TransactionType.INCOME)
        val expensesThis = totals(thisMonth, TransactionType.EXPENSE)
        val expensesLast = totals(previousMonth, TransactionType.EXPENSE)

        val primary = primaryCurrency(thisMonth, currencyOf, balances, accounts)
        val primaryExpenses = thisMonth.filter {
            it.type == TransactionType.EXPENSE && currencyOf[it.accountId] == primary
        }

        val byCategory = primaryExpenses
            .groupBy { categoryNamesById[it.categoryId] ?: UNCATEGORIZED }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
        val categoryTotal = byCategory.values.sum()
        val topCategories = byCategory.entries
            .sortedByDescending { it.value }
            .take(5)
            .map {
                CategorySpend(
                    name = it.key,
                    amount = it.value,
                    sharePercent = if (categoryTotal > 0) {
                        (it.value / categoryTotal * 100).toInt()
                    } else {
                        0
                    }
                )
            }

        val expensesByCategory = thisMonth
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { categoryNamesById[it.categoryId] ?: UNCATEGORIZED }
            .mapValues { (_, list) ->
                list.groupBy { currencyOf[it.accountId].orEmpty() }
                    .filterKeys { it.isNotEmpty() }
                    .mapValues { (_, l) -> l.sumOf { it.amount } }
            }

        val biggest = primaryExpenses.maxByOrNull { it.amount }?.let { top ->
            BiggestExpense(
                label = top.title?.takeIf { it.isNotBlank() }
                    ?: categoryNamesById[top.categoryId] ?: UNCATEGORIZED,
                amount = top.amount,
                currency = primary,
                date = top.dateTime?.atZone(zone)?.toLocalDate()
            )
        }

        val daysLeft = (month.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
        val dailyAllowance = incomeThis[primary]?.let { income ->
            ((income - (expensesThis[primary] ?: 0.0)) / daysLeft).coerceAtLeast(0.0)
        }

        val detected = subscriptionDetector.detect(
            transactions = transactions,
            plannedRules = rules,
            accountCurrencies = accounts.associate { it.id to it.currency },
            today = today,
            zone = zone
        )
        val subscriptionFacts = detected.map {
            SubscriptionFact(
                name = it.name,
                amount = it.amount,
                currency = it.currency ?: primary,
                frequency = it.frequency.name.lowercase(Locale.ROOT),
                nextDue = it.nextDueDate,
                yearlyCost = it.yearlyCost
            )
        }
        val subscriptionYearly = detected
            .groupBy { it.currency ?: primary }
            .mapValues { (_, l) -> l.sumOf { it.yearlyCost } }

        val plannedFacts = rules.mapNotNull { rule ->
            val next = nextOccurrence(rule, today, zone) ?: return@mapNotNull null
            PlannedFact(
                title = rule.title?.takeIf { it.isNotBlank() }
                    ?: categoryNamesById[rule.categoryId] ?: PLANNED_FALLBACK,
                amount = rule.amount,
                currency = currencyOf[rule.accountId].orEmpty(),
                type = rule.type.name.lowercase(Locale.ROOT),
                nextDate = next
            )
        }.sortedBy { it.nextDate }.take(5)

        // Monthly zero-based budget (primary currency, like its own screen)
        val assignments = monthlyBudgetStore.assignments.value
            .filterKeys { it.month == month }
        val assignedTotal = assignments.values.sum()
        val assignedIds = assignments.keys.map { it.categoryId }.toSet()
        val spentInAssigned = primaryExpenses
            .filter { it.categoryId != null && it.categoryId in assignedIds }
            .sumOf { it.amount }
        val overCategories = assignments.entries.mapNotNull { (slot, assigned) ->
            val spent = primaryExpenses
                .filter { it.categoryId == slot.categoryId }
                .sumOf { it.amount }
            if (spent > assigned) {
                (categoryNamesById[slot.categoryId] ?: UNCATEGORIZED) to (spent - assigned)
            } else {
                null
            }
        }
        val unassignedIncome = incomeThis[primary]?.let { it - assignedTotal }

        val personalTerms = buildList {
            addAll(accounts.map { it.name })
            addAll(categories.map { it.name })
            addAll(subscriptionFacts.map { it.name })
            addAll(plannedFacts.map { it.title })
        }.distinct()

        val contextText = buildContextText(
            today = today,
            primary = primary,
            accountCount = accounts.count { it.includeInBalance },
            balances = balances,
            incomeThis = incomeThis,
            expensesThis = expensesThis,
            expensesLast = expensesLast,
            topCategories = topCategories,
            biggest = biggest,
            dailyAllowance = dailyAllowance,
            daysLeft = daysLeft,
            subscriptions = subscriptionFacts,
            subscriptionYearly = subscriptionYearly,
            planned = plannedFacts,
            assignedTotal = assignedTotal,
            spentInAssigned = spentInAssigned,
            unassignedIncome = unassignedIncome,
            overCategories = overCategories,
            pendingAlerts = alertStore.pending.value.size
        )

        val allowedNumbers = FigureGuard.extractNumbers(contextText) +
            (0..31).map { it.toDouble() } +
            setOf(100.0, today.year.toDouble(), subscriptionFacts.size.toDouble())

        AssistantFacts(
            today = today,
            primaryCurrency = primary,
            accountCount = accounts.size,
            balances = balances,
            incomeThisMonth = incomeThis,
            expensesThisMonth = expensesThis,
            expensesLastMonth = expensesLast,
            expensesByCategoryThisMonth = expensesByCategory,
            topCategories = topCategories,
            biggestExpense = biggest,
            dailyAllowance = dailyAllowance,
            daysLeftInMonth = daysLeft,
            subscriptions = subscriptionFacts,
            subscriptionYearly = subscriptionYearly,
            plannedPayments = plannedFacts,
            budgetAssignedTotal = assignedTotal,
            budgetSpentInAssigned = spentInAssigned,
            budgetUnassignedIncome = unassignedIncome,
            budgetOverCategories = overCategories,
            pendingAlerts = alertStore.pending.value.size,
            categoryNames = categories.map { it.name },
            personalTerms = personalTerms,
            contextText = contextText,
            allowedNumbers = allowedNumbers
        )
    }

    private fun computeBalances(
        accounts: List<com.ivy.data.db.entity.AccountEntity>,
        transactions: List<TransactionEntity>,
        currencyOf: Map<UUID, String>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        accounts.filter { it.includeInBalance }.forEach { account ->
            var sum = 0.0
            transactions.forEach { tr ->
                if (tr.dateTime == null) return@forEach
                when {
                    tr.accountId == account.id && tr.type == TransactionType.INCOME ->
                        sum += tr.amount
                    tr.accountId == account.id && tr.type == TransactionType.EXPENSE ->
                        sum -= tr.amount
                    tr.accountId == account.id && tr.type == TransactionType.TRANSFER ->
                        sum -= tr.amount
                    tr.toAccountId == account.id && tr.type == TransactionType.TRANSFER ->
                        sum += tr.toAmount ?: tr.amount
                }
            }
            val currency = currencyOf[account.id].orEmpty()
            if (currency.isNotEmpty()) {
                result[currency] = (result[currency] ?: 0.0) + sum
            }
        }
        return result
    }

    private fun primaryCurrency(
        thisMonth: List<TransactionEntity>,
        currencyOf: Map<UUID, String>,
        balances: Map<String, Double>,
        accounts: List<com.ivy.data.db.entity.AccountEntity>
    ): String =
        thisMonth.groupingBy { currencyOf[it.accountId].orEmpty() }
            .eachCount().entries
            .filter { it.key.isNotEmpty() }
            .maxByOrNull { it.value }?.key
            ?: balances.keys.firstOrNull()
            ?: accounts.firstOrNull()?.currency
            ?: ""

    private fun nextOccurrence(
        rule: PlannedPaymentRuleEntity,
        today: LocalDate,
        zone: ZoneId
    ): LocalDate? {
        val start = rule.startDate?.atZone(zone)?.toLocalDate() ?: return null
        if (rule.oneTime) return start.takeIf { !it.isBefore(today) }
        val n = (rule.intervalN ?: 1).coerceAtLeast(1).toLong()
        var date = start
        var guard = 0
        while (date.isBefore(today) && guard < MAX_ROLL_FORWARD) {
            date = when (rule.intervalType) {
                IntervalType.DAY -> date.plusDays(n)
                IntervalType.WEEK -> date.plusWeeks(n)
                IntervalType.YEAR -> date.plusYears(n)
                else -> date.plusMonths(n)
            }
            guard++
        }
        return date
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun moneyList(map: Map<String, Double>): String =
        map.entries.joinToString("; ") { (currency, value) -> "${fmt(value)} $currency" }

    private fun buildContextText(
        today: LocalDate,
        primary: String,
        accountCount: Int,
        balances: Map<String, Double>,
        incomeThis: Map<String, Double>,
        expensesThis: Map<String, Double>,
        expensesLast: Map<String, Double>,
        topCategories: List<CategorySpend>,
        biggest: BiggestExpense?,
        dailyAllowance: Double?,
        daysLeft: Int,
        subscriptions: List<SubscriptionFact>,
        subscriptionYearly: Map<String, Double>,
        planned: List<PlannedFact>,
        assignedTotal: Double,
        spentInAssigned: Double,
        unassignedIncome: Double?,
        overCategories: List<Pair<String, Double>>,
        pendingAlerts: Int
    ): String {
        val lines = mutableListOf<String>()
        lines += "TODAY: $today. PRIMARY CURRENCY: $primary."
        if (balances.isNotEmpty()) {
            lines += "BALANCES ($accountCount accounts): ${moneyList(balances)}."
        }
        if (incomeThis.isNotEmpty()) {
            lines += "INCOME THIS MONTH: ${moneyList(incomeThis)}."
        }
        if (expensesThis.isNotEmpty()) {
            lines += "EXPENSES THIS MONTH: ${moneyList(expensesThis)}."
        }
        if (expensesLast.isNotEmpty()) {
            lines += "EXPENSES LAST MONTH: ${moneyList(expensesLast)}."
        }
        if (topCategories.isNotEmpty()) {
            val cats = topCategories.joinToString("; ") {
                "${it.name} ${fmt(it.amount)} $primary (${it.sharePercent}%)"
            }
            lines += "TOP SPENDING CATEGORIES THIS MONTH: $cats."
        }
        biggest?.let {
            lines += "BIGGEST EXPENSE THIS MONTH: ${it.label}, " +
                "${fmt(it.amount)} ${it.currency} on ${it.date}."
        }
        dailyAllowance?.let {
            lines += "DAILY ALLOWANCE: ${fmt(it)} $primary per day, $daysLeft days left."
        }
        if (subscriptions.isNotEmpty()) {
            val next = subscriptions.sortedBy { it.nextDue }.take(3).joinToString("; ") {
                "${it.name} ${fmt(it.amount)} ${it.currency} ${it.frequency} due ${it.nextDue}"
            }
            lines += "SUBSCRIPTIONS: ${subscriptions.size} detected, " +
                "yearly total ${moneyList(subscriptionYearly)}. Next due: $next."
        }
        if (planned.isNotEmpty()) {
            val items = planned.joinToString("; ") {
                "${it.title} ${fmt(it.amount)} ${it.currency} ${it.type} next ${it.nextDate}"
            }
            lines += "PLANNED PAYMENTS: $items."
        }
        if (assignedTotal > 0) {
            var line = "MONTHLY BUDGET ($primary): assigned ${fmt(assignedTotal)}, " +
                "spent in assigned categories ${fmt(spentInAssigned)}"
            unassignedIncome?.let { line += ", unassigned income ${fmt(it)}" }
            if (overCategories.isNotEmpty()) {
                line += ". Over budget: " + overCategories.joinToString("; ") {
                    "${it.first} by ${fmt(it.second)}"
                }
            }
            lines += "$line."
        }
        lines += "PENDING BANK ALERTS: $pendingAlerts."
        return lines.joinToString("\n")
    }

    private companion object {
        const val UNCATEGORIZED = "No category"
        const val PLANNED_FALLBACK = "Planned payment"
        const val MAX_ROLL_FORWARD = 1_000
    }
}
