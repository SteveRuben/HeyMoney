package org.abodah.heymoney.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Theme
import com.ivy.base.legacy.refreshWidget
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.LoanRecordDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.domain.RootScreen
import com.ivy.domain.usecase.csv.ExportCsvUseCase
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.domain.action.settings.UpdateSettingsAct
import com.ivy.legacy.utils.getISOFormattedDateTime
import com.ivy.legacy.utils.timeNowUTC
import com.ivy.legacy.utils.uiThread
import com.ivy.wallet.domain.action.settings.SettingsAct
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import com.ivy.wallet.domain.action.wallet.CalcWalletBalanceAct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.abodah.heymoney.alerts.AlertStore
import org.abodah.heymoney.networth.CurrencyNetWorth
import org.abodah.heymoney.networth.NetWorthCalculator
import org.abodah.heymoney.setup.SetupProgress
import org.abodah.heymoney.setup.SetupReminderScheduler
import org.abodah.heymoney.setup.SetupStatusProvider
import org.abodah.heymoney.subscriptions.DetectedSubscription
import org.abodah.heymoney.subscriptions.SubscriptionDetector
import org.abodah.heymoney.subscriptions.SubscriptionStatus
import org.abodah.heymoney.subscriptions.SubscriptionStore
import javax.inject.Inject

/**
 * Data for the Menu screen: the headline figures come from the very same
 * engines as their own screens — [NetWorthCalculator] for the net worth and
 * [SubscriptionDetector] (plus the user's decisions in [SubscriptionStore])
 * for the subscriptions yearly cost — so the numbers always agree.
 *
 * The export / backup / theme actions reuse the same use cases and acts as
 * the Settings screen; nothing is reimplemented here.
 */
@HiltViewModel
class MenuViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val loanDao: LoanDao,
    private val loanRecordDao: LoanRecordDao,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val settingsDao: SettingsDao,
    private val netWorthCalculator: NetWorthCalculator,
    private val subscriptionDetector: SubscriptionDetector,
    private val subscriptionStore: SubscriptionStore,
    private val alertStore: AlertStore,
    private val settingsAct: SettingsAct,
    private val updateSettingsAct: UpdateSettingsAct,
    private val calcWalletBalanceAct: CalcWalletBalanceAct,
    private val ivyContext: IvyWalletCtx,
    private val sharedPrefs: SharedPrefs,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val backupDataUseCase: BackupDataUseCase,
    private val setupStatusProvider: SetupStatusProvider,
    setupReminderScheduler: SetupReminderScheduler,
) : ViewModel() {

    private val _netWorthReport = MutableStateFlow<List<CurrencyNetWorth>>(emptyList())
    val netWorthReport: StateFlow<List<CurrencyNetWorth>> = _netWorthReport

    private val _detected = MutableStateFlow<List<DetectedSubscription>>(emptyList())

    /** Yearly cost of confirmed + still-undecided subscriptions, per currency. */
    private val _subsYearlyTotals = MutableStateFlow<Map<String?, Double>>(emptyMap())
    val subsYearlyTotals: StateFlow<Map<String?, Double>> = _subsYearlyTotals

    /** Bank alerts still waiting for the user to confirm or dismiss them. */
    val pendingAlertsCount: StateFlow<Int> = alertStore.pending
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, alertStore.pending.value.size)

    private val _theme = MutableStateFlow(Theme.AUTO)
    val theme: StateFlow<Theme> = _theme

    /** App lock on/off — the same SharedPrefs flag the Settings screen used. */
    private val _appLockEnabled = MutableStateFlow(false)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled

    private val _bufferAmount = MutableStateFlow(0.0)
    val bufferAmount: StateFlow<Double> = _bufferAmount

    private val _baseCurrency = MutableStateFlow("")
    val baseCurrency: StateFlow<String> = _baseCurrency

    private val _walletBalance = MutableStateFlow(0.0)
    val walletBalance: StateFlow<Double> = _walletBalance

    /** Remaining-configuration progress, shown on the Menu's setup card. */
    private val _setupProgress = MutableStateFlow(setupStatusProvider.progress())
    val setupProgress: StateFlow<SetupProgress> = _setupProgress

    init {
        // The setup reminder only makes sense once the app was opened; the
        // daily worker is unique, so scheduling again is a no-op.
        setupReminderScheduler.schedule()
        // Same totals rule as the Subscriptions screen: what the user
        // confirmed plus what is still waiting for a decision.
        viewModelScope.launch {
            combine(_detected, subscriptionStore.decisions) { detected, decisions ->
                detected
                    .filter { subscription ->
                        val status = decisions[subscription.key]?.status
                        status == SubscriptionStatus.CONFIRMED || status == null
                    }
                    .groupBy { it.currency }
                    .mapValues { (_, group) -> group.sumOf { it.yearlyCost } }
                    .toSortedMap(compareBy { it ?: "" })
            }.collect { _subsYearlyTotals.value = it }
        }
        refresh()
    }

    private fun refresh() {
        _setupProgress.value = setupStatusProvider.progress()
        _appLockEnabled.value = sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false)
        viewModelScope.launch {
            _netWorthReport.value = withContext(Dispatchers.IO) {
                netWorthCalculator.calculate(
                    accounts = accountDao.findAll(),
                    transactions = transactionDao.findAll(),
                    loans = loanDao.findAll(),
                    records = loanRecordDao.findAll(),
                    baseCurrency = settingsDao.findFirst().currency,
                )
            }
        }
        viewModelScope.launch {
            _detected.value = withContext(Dispatchers.IO) {
                subscriptionDetector.detect(
                    transactions = transactionDao.findAll(),
                    plannedRules = plannedPaymentRuleDao.findAll(),
                    accountCurrencies = accountDao.findAll().associate { it.id to it.currency },
                )
            }
        }
        viewModelScope.launch {
            val settings = settingsAct.getSettings()
            _theme.value = settings.theme
            _bufferAmount.value = settings.bufferAmount.toDouble()
            _baseCurrency.value = settings.baseCurrency
            _walletBalance.value = calcWalletBalanceAct(
                CalcWalletBalanceAct.Input(baseCurrency = settings.baseCurrency)
            ).toDouble()
        }
    }

    /**
     * Toggles the app lock, exactly like the Settings screen did: the same
     * [SharedPrefs.APP_LOCK_ENABLED] write and the same widget refresh, so
     * RootViewModel's start-up gate sees the identical state.
     */
    fun setAppLock(enabled: Boolean) {
        _appLockEnabled.value = enabled

        viewModelScope.launch {
            sharedPrefs.putBoolean(SharedPrefs.APP_LOCK_ENABLED, enabled)
            refreshWidget(WalletBalanceWidgetReceiver::class.java)
        }
    }

    /** Cycles the app theme, exactly like the Settings screen does. */
    fun switchTheme() {
        viewModelScope.launch {
            settingsAct.getSettingsWithNextTheme().run {
                updateSettingsAct(this)
                ivyContext.switchTheme(this.theme)
                _theme.value = this.theme
            }
        }
    }

    /** Saves the savings goal (buffer), exactly like the Home screen does. */
    fun setBuffer(newBuffer: Double) {
        viewModelScope.launch {
            val currentSettings =
                settingsAct.getSettings().copy(bufferAmount = newBuffer.toBigDecimal())
            updateSettingsAct(currentSettings)
            _bufferAmount.value = currentSettings.bufferAmount.toDouble()
        }
    }

    /** Exports all transactions to a CSV file and shares it (Settings action). */
    fun exportCsv(rootScreen: RootScreen) {
        ivyContext.createNewFile(
            "IvyWalletExport_${timeNowUTC().getISOFormattedDateTime()}.csv"
        ) { fileUri ->
            viewModelScope.launch {
                exportCsvUseCase.exportToFile(
                    outputFile = fileUri
                )

                rootScreen.shareCSVFile(
                    fileUri = fileUri
                )
            }
        }
    }

    /** Exports a full backup zip and shares it (Settings action). */
    fun backup(rootScreen: RootScreen) {
        ivyContext.createNewFile(
            "IvyWalletBackup_${timeNowUTC().getISOFormattedDateTime()}.zip"
        ) { fileUri ->
            viewModelScope.launch(Dispatchers.IO) {
                backupDataUseCase.exportToFile(zipFileUri = fileUri)

                sharedPrefs.putBoolean(SharedPrefs.DATA_BACKUP_COMPLETED, true)
                ivyContext.dataBackupCompleted = true

                uiThread {
                    rootScreen.shareZipFile(
                        fileUri = fileUri
                    )
                }
            }
        }
    }
}
