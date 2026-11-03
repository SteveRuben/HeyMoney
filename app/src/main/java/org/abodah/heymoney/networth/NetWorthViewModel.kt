package org.abodah.heymoney.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.LoanRecordDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val loanDao: LoanDao,
    private val loanRecordDao: LoanRecordDao,
    private val settingsDao: SettingsDao,
    private val calculator: NetWorthCalculator,
) : ViewModel() {

    private val _report = MutableStateFlow<List<CurrencyNetWorth>>(emptyList())
    val report: StateFlow<List<CurrencyNetWorth>> = _report

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _report.value = withContext(Dispatchers.IO) {
                calculator.calculate(
                    accounts = accountDao.findAll(),
                    transactions = transactionDao.findAll(),
                    loans = loanDao.findAll(),
                    records = loanRecordDao.findAll(),
                    baseCurrency = settingsDao.findFirst().currency,
                )
            }
            _loaded.value = true
        }
    }
}
