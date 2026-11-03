package org.abodah.heymoney.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class MonthlyChartUiState(
    val currencies: List<String> = emptyList(),
    val currency: String? = null,
    val periodMonths: Int = DEFAULT_PERIOD_MONTHS,
    val endMonth: YearMonth = YearMonth.now(),
    val months: List<MonthSummary> = emptyList(),
    val loaded: Boolean = false,
) {
    /** The "next period" button makes no sense beyond the current month. */
    val canGoNext: Boolean get() = endMonth < YearMonth.now()

    companion object {
        const val DEFAULT_PERIOD_MONTHS = 12
    }
}

@HiltViewModel
class MonthlyChartViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val aggregator: MonthlyAggregator,
) : ViewModel() {

    private val _state = MutableStateFlow(MonthlyChartUiState())
    val state: StateFlow<MonthlyChartUiState> = _state

    // Raw data, loaded once; every state change is a pure re-aggregation.
    private var transactions: List<TransactionEntity> = emptyList()
    private var accountCurrencies: Map<UUID, String?> = emptyMap()
    private var baseCurrency: String = ""

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                transactions = transactionDao.findAll()
                accountCurrencies = accountDao.findAll().associate { it.id to it.currency }
                baseCurrency = settingsDao.findFirst().currency
            }
            val currencies = aggregator.currenciesPresent(transactions, accountCurrencies, baseCurrency)
            _state.value = _state.value.copy(
                currencies = currencies,
                currency = currencies.firstOrNull(),
            )
            recompute(loaded = true)
        }
    }

    fun setPeriod(months: Int) {
        if (months == _state.value.periodMonths) return
        _state.value = _state.value.copy(periodMonths = months)
        recompute()
    }

    fun setCurrency(currency: String) {
        if (currency == _state.value.currency) return
        _state.value = _state.value.copy(currency = currency)
        recompute()
    }

    /** Moves the window one whole period earlier or later, never past the current month. */
    fun shiftPeriods(steps: Int) {
        val current = _state.value
        val shifted = current.endMonth.plusMonths(steps.toLong() * current.periodMonths)
        val clamped = if (shifted > YearMonth.now()) YearMonth.now() else shifted
        if (clamped == current.endMonth) return
        _state.value = current.copy(endMonth = clamped)
        recompute()
    }

    private fun recompute(loaded: Boolean = _state.value.loaded) {
        val current = _state.value
        val currency = current.currency
        _state.value = current.copy(
            months = if (currency == null) {
                emptyList()
            } else {
                aggregator.aggregate(
                    transactions = transactions,
                    accountCurrencies = accountCurrencies,
                    baseCurrency = baseCurrency,
                    currency = currency,
                    endMonth = current.endMonth,
                    monthCount = current.periodMonths,
                )
            },
            loaded = loaded,
        )
    }
}
