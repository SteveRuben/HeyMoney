package org.abodah.heymoney.chart

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import org.abodah.heymoney.subscriptions.formatMoney
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PERIOD_OPTIONS = listOf(6, 12, 24)

/**
 * "Monthly chart": income vs expenses per month as grouped bars, drawn on a
 * Compose Canvas (same Canvas drawing model as the legacy pie chart view).
 * One currency at a time — amounts are never converted — and the exact
 * figures, net included, are listed under the chart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyChartScreen(viewModel: MonthlyChartViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val state by viewModel.state.collectAsState()
    val hasData = state.months.any { it.income > 0.0 || it.expense > 0.0 }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chart_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.chart_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.currencies.size > 1) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.currencies.forEach { currency ->
                            FilterChip(
                                selected = currency == state.currency,
                                onClick = { viewModel.setCurrency(currency) },
                                label = { Text(currency) }
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { viewModel.shiftPeriods(-1) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chart_previous_period)
                        )
                    }
                    PERIOD_OPTIONS.forEach { months ->
                        FilterChip(
                            selected = months == state.periodMonths,
                            onClick = { viewModel.setPeriod(months) },
                            label = { Text(stringResource(R.string.chart_period_months, months)) }
                        )
                    }
                    IconButton(
                        onClick = { viewModel.shiftPeriods(1) },
                        enabled = state.canGoNext
                    ) {
                        Icon(
                            Icons.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.chart_next_period)
                        )
                    }
                }
            }

            if (state.months.isNotEmpty()) {
                item {
                    Text(
                        periodRangeLabel(state.months),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (state.loaded && !hasData) {
                item {
                    Text(
                        stringResource(R.string.chart_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (hasData) {
                item {
                    LegendRow()
                }
                item {
                    MonthlyBarsChart(months = state.months)
                }
                items(state.months.asReversed(), key = { it.month }) { summary ->
                    MonthRow(summary = summary, currency = state.currency)
                }
            }
        }
    }
}

@Composable
private fun LegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(color = MaterialTheme.colorScheme.tertiary, label = stringResource(R.string.chart_income))
        LegendDot(color = MaterialTheme.colorScheme.error, label = stringResource(R.string.chart_expenses))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MonthlyBarsChart(months: List<MonthSummary>) {
    val incomeColor = MaterialTheme.colorScheme.tertiary
    val expenseColor = MaterialTheme.colorScheme.error
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = months.maxOf { maxOf(it.income, it.expense) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        val labelHeight = 16.dp.toPx()
        val baselineY = size.height - labelHeight
        val slotWidth = size.width / months.size
        val barWidth = slotWidth * 0.32f
        val groupGap = slotWidth * 0.06f
        val labelPaint = Paint().apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        // Every month gets a label on wide layouts; every other month on the 24-month view.
        val labelStep = if (months.size > 12) 2 else 1

        drawLine(
            color = axisColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx()
        )

        months.forEachIndexed { index, summary ->
            val groupStart = index * slotWidth + (slotWidth - 2 * barWidth - groupGap) / 2
            if (maxValue > 0.0) {
                val incomeHeight = (summary.income / maxValue).toFloat() * baselineY
                drawRect(
                    color = incomeColor,
                    topLeft = Offset(groupStart, baselineY - incomeHeight),
                    size = Size(barWidth, incomeHeight)
                )
                val expenseHeight = (summary.expense / maxValue).toFloat() * baselineY
                drawRect(
                    color = expenseColor,
                    topLeft = Offset(groupStart + barWidth + groupGap, baselineY - expenseHeight),
                    size = Size(barWidth, expenseHeight)
                )
            }
            if (index % labelStep == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    summary.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    index * slotWidth + slotWidth / 2,
                    size.height - 2.dp.toPx(),
                    labelPaint
                )
            }
        }
    }
}

@Composable
private fun MonthRow(summary: MonthSummary, currency: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            summary.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                .replaceFirstChar { it.uppercase() } + " " + summary.month.year,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            listOf(
                stringResource(R.string.chart_income) + " " + formatMoney(summary.income, currency),
                stringResource(R.string.chart_expenses) + " " + formatMoney(summary.expense, currency),
                stringResource(R.string.chart_net, formatMoney(summary.net, currency)),
            ).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun periodRangeLabel(months: List<MonthSummary>): String {
    val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
    val first = months.first().month.format(formatter)
    val last = months.last().month.format(formatter)
    return if (first == last) first else "$first – $last"
}
