package org.abodah.heymoney.statement

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.abodah.heymoney.subscriptions.DetectedSubscription
import org.abodah.heymoney.subscriptions.SubscriptionDetector
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/** What the statement analysis screen shows. */
sealed interface StatementUiState {
    data object Idle : StatementUiState
    data object Loading : StatementUiState
    data object Unreadable : StatementUiState
    data class Ready(
        val analysis: StatementAnalysis,
        /** Recurring payments found by the app's subscription detector. */
        val recurring: List<DetectedSubscription>,
        val skippedRows: Int,
    ) : StatementUiState
}

/**
 * Reads one statement file picked by the user, runs [CsvStatementParser]
 * and [StatementAnalyzer] over it, and feeds the same lines to the existing
 * [SubscriptionDetector] so the recurring payments shown here are detected
 * by exactly the same rules as "My subscriptions".
 *
 * Nothing is written to the database here: importing the transactions into
 * an account stays the job of the existing CSV import flow, which the
 * screen opens when the user asks for it.
 */
@HiltViewModel
class StatementAnalysisViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: SubscriptionDetector,
) : ViewModel() {

    private val _state = MutableStateFlow<StatementUiState>(StatementUiState.Idle)
    val state: StateFlow<StatementUiState> = _state

    fun analyse(uri: Uri) {
        _state.value = StatementUiState.Loading
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) { readAndAnalyse(uri) }
        }
    }

    fun reset() {
        _state.value = StatementUiState.Idle
    }

    private fun readAndAnalyse(uri: Uri): StatementUiState {
        val text = readText(uri) ?: return StatementUiState.Unreadable
        val parsed = CsvStatementParser.parse(text)
        if (parsed.lines.isEmpty()) return StatementUiState.Unreadable

        val zone = ZoneId.systemDefault()
        // The detector works on transactions; the statement lines are fed to
        // it as throwaway entities on one scratch account — nothing persists.
        val scratchAccount = UUID.randomUUID()
        val entities = parsed.lines.map { line ->
            TransactionEntity(
                accountId = scratchAccount,
                type = if (line.amount < 0) TransactionType.EXPENSE else TransactionType.INCOME,
                amount = abs(line.amount),
                title = line.title,
                dateTime = line.date?.atStartOfDay(zone)?.toInstant(),
            )
        }
        return StatementUiState.Ready(
            analysis = StatementAnalyzer.analyse(parsed.lines),
            recurring = detector.detect(
                transactions = entities,
                plannedRules = emptyList(),
                zone = zone,
            ),
            skippedRows = parsed.skippedRows,
        )
    }

    private fun readText(uri: Uri): String? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                input.copyTo(out)
                out.toByteArray()
            }
        }.getOrNull() ?: return null
        val utf8 = bytes.decodeToString()
        // Bank exports from French software often arrive in Windows-1252;
        // a replacement character means UTF-8 was the wrong guess.
        return if (utf8.contains('�')) {
            runCatching { String(bytes, charset("windows-1252")) }.getOrDefault(utf8)
        } else {
            utf8
        }
    }
}
