package org.abodah.heymoney.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Feeds [ZeroBasedBudgetCalculator] from the wallet's Room data.
 *
 * Conventions (the calculator stays currency-free, so they live here):
 *
 * - **Base currency only.** A transaction amount is expressed in its
 *   account's currency; mixing currencies in one budget would add numbers
 *   that do not add up. Only transactions of accounts in the base currency
 *   (or without a currency, which the app reads as base) take part — the
 *   same rule the Net worth screen applies. Assignments and goals are
 *   therefore base-currency amounts.
 * - **Transfers are left out**: moving money between own accounts is
 *   neither income nor spending, and counting it would double-count.
 * - Transactions without a date cannot be placed in a month and are left
 *   out, as are deleted ones.
 */
@HiltViewModel
class MonthlyBudgetViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val store: MonthlyBudgetStore,
    private val calculator: ZeroBasedBudgetCalculator,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month

    /** Category display names by id (includes ids whose category was deleted). */
    private val _categoryNames = MutableStateFlow<Map<UUID, String>>(emptyMap())
    val categoryNames: StateFlow<Map<UUID, String>> = _categoryNames

    private val _currency = MutableStateFlow("")
    val currency: StateFlow<String> = _currency

    private val _ageOfMoney = MutableStateFlow<Int?>(null)
    val ageOfMoney: StateFlow<Int?> = _ageOfMoney

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val _budget = MutableStateFlow<MonthlyBudget?>(null)
    val budget: StateFlow<MonthlyBudget?> = _budget

    private var categoryIds: List<UUID> = emptyList()
    private var budgetTxs: List<BudgetTx> = emptyList()

    init {
        viewModelScope.launch {
            val categories = withContext(Dispatchers.IO) { categoryDao.findAll() }
            _categoryNames.value = categories.associate { it.id to it.name }
            categoryIds = categories.map { it.id }

            withContext(Dispatchers.IO) {
                val baseCurrency = settingsDao.findFirst().currency
                _currency.value = baseCurrency
                val accounts = accountDao.findAll()
                val currencyByAccount = accounts.associate {
                    it.id to (it.currency ?: baseCurrency)
                }
                budgetTxs = transactionDao.findAll()
                    .filter { !it.isDeleted && it.dateTime != null }
                    .filter { (currencyByAccount[it.accountId] ?: baseCurrency) == baseCurrency }
                    .mapNotNull { tx ->
                        val type = when (tx.type) {
                            TransactionType.INCOME -> BudgetTxType.INCOME
                            TransactionType.EXPENSE -> BudgetTxType.EXPENSE
                            TransactionType.TRANSFER -> return@mapNotNull null
                        }
                        BudgetTx(
                            date = tx.dateTime!!
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate(),
                            type = type,
                            amount = tx.amount,
                            categoryId = tx.categoryId,
                        )
                    }
                _ageOfMoney.value = calculator.ageOfMoney(budgetTxs, LocalDate.now())
            }
            _loaded.value = true

            combine(_month, store.assignments, store.goals) { month, assignments, goals ->
                Triple(month, assignments, goals)
            }.collectLatest { (month, assignments, goals) ->
                _budget.value = withContext(Dispatchers.Default) {
                    calculator.month(
                        month = month,
                        categoryIds = categoryIds,
                        assignments = assignments.map { (key, amount) ->
                            CategoryAssignment(key.categoryId, key.month, amount)
                        },
                        transactions = budgetTxs,
                        goals = goals.values.toList(),
                    )
                }
            }
        }
    }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun setAssignment(categoryId: UUID, amount: Double) {
        store.setAssignment(categoryId, _month.value, amount)
    }

    fun setGoal(categoryId: UUID, targetAmount: Double, targetMonth: YearMonth) {
        store.setGoal(categoryId, targetAmount, targetMonth)
    }

    fun clearGoal(categoryId: UUID) {
        store.clearGoal(categoryId)
    }
}
