package org.abodah.heymoney.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import org.abodah.heymoney.subscriptions.formatMoney
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * "Monthly budget": the zero-based view (the YNAB method) next to the
 * legacy Budgets screen, which keeps its standalone caps. Assign this
 * month's income to categories, watch what is left "to assign", and let
 * each category's available roll into next month — in both directions.
 * The math lives in [ZeroBasedBudgetCalculator]; this screen only shows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetScreen(viewModel: MonthlyBudgetViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val month by viewModel.month.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val categoryNames by viewModel.categoryNames.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val ageOfMoney by viewModel.ageOfMoney.collectAsState()
    val loaded by viewModel.loaded.collectAsState()

    var assignTarget by remember { mutableStateOf<UUID?>(null) }
    var goalTarget by remember { mutableStateOf<UUID?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.zb_title)) },
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
                MonthSelector(
                    month = month,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }

            val current = budget
            if (current != null) {
                item {
                    ToAssignCard(
                        budget = current,
                        currency = currency,
                        ageOfMoney = ageOfMoney,
                    )
                }

                if (current.uncategorizedSpent > 0.0) {
                    item {
                        Text(
                            stringResource(
                                R.string.zb_uncategorized,
                                formatMoney(current.uncategorizedSpent, currency)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (loaded && current.lines.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.zb_no_categories),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                items(current.lines, key = { it.categoryId.toString() }) { line ->
                    CategoryBudgetCard(
                        line = line,
                        name = categoryNames[line.categoryId]
                            ?: stringResource(R.string.zb_unknown_category),
                        currency = currency,
                        onAssign = { assignTarget = line.categoryId },
                        onEditGoal = { goalTarget = line.categoryId },
                        onClearGoal = { viewModel.clearGoal(line.categoryId) },
                    )
                }
            }

            item {
                Text(
                    stringResource(R.string.zb_intro),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    val assignId = assignTarget
    if (assignId != null) {
        val line = budget?.lines?.firstOrNull { it.categoryId == assignId }
        AssignDialog(
            categoryName = categoryNames[assignId]
                ?: stringResource(R.string.zb_unknown_category),
            currentAmount = line?.assigned ?: 0.0,
            onSave = { amount ->
                viewModel.setAssignment(assignId, amount)
                assignTarget = null
            },
            onDismiss = { assignTarget = null },
        )
    }

    val goalId = goalTarget
    if (goalId != null) {
        val line = budget?.lines?.firstOrNull { it.categoryId == goalId }
        GoalDialog(
            categoryName = categoryNames[goalId]
                ?: stringResource(R.string.zb_unknown_category),
            existing = line?.goal,
            defaultTargetMonth = month.plusMonths(6),
            onSave = { targetAmount, targetMonth ->
                viewModel.setGoal(goalId, targetAmount, targetMonth)
                goalTarget = null
            },
            onDismiss = { goalTarget = null },
        )
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun ToAssignCard(budget: MonthlyBudget, currency: String, ageOfMoney: Int?) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.zb_to_assign) + " • " + currency,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                formatMoney(budget.toAssign, currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (budget.toAssign < 0.0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            AmountRow(
                label = stringResource(R.string.zb_income),
                amount = formatMoney(budget.income, currency),
            )
            AmountRow(
                label = stringResource(R.string.zb_total_assigned),
                amount = formatMoney(budget.totalAssigned, currency),
            )
            if (ageOfMoney != null) {
                AmountRow(
                    label = stringResource(R.string.zb_age_of_money),
                    amount = stringResource(R.string.zb_age_days, ageOfMoney),
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetCard(
    line: CategoryBudgetLine,
    name: String,
    currency: String,
    onAssign: () -> Unit,
    onEditGoal: () -> Unit,
    onClearGoal: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    formatMoney(line.available, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (line.available < 0.0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Text(
                stringResource(R.string.zb_available),
                style = MaterialTheme.typography.bodySmall
            )

            if (line.carriedIn != 0.0) {
                AmountRow(
                    label = stringResource(R.string.zb_carried),
                    amount = formatMoney(line.carriedIn, currency),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AmountRow(
                    label = stringResource(R.string.zb_total_assigned),
                    amount = formatMoney(line.assigned, currency),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAssign) {
                    Text(stringResource(R.string.zb_assign))
                }
            }
            AmountRow(
                label = stringResource(R.string.zb_spent),
                amount = formatMoney(line.spent, currency),
            )

            val goal = line.goal
            if (goal == null) {
                TextButton(onClick = onEditGoal) {
                    Text(stringResource(R.string.zb_goal_set))
                }
            } else {
                Text(
                    stringResource(
                        R.string.zb_goal_by,
                        formatMoney(goal.targetAmount, currency),
                        goal.targetMonth.format(
                            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val needed = line.goalMonthlyNeeded ?: 0.0
                Text(
                    if (needed > 0.0) {
                        stringResource(
                            R.string.zb_goal_needs,
                            formatMoney(needed, currency)
                        )
                    } else {
                        stringResource(R.string.zb_goal_reached)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Row {
                    TextButton(onClick = onEditGoal) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = onClearGoal) {
                        Text(stringResource(R.string.zb_goal_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountRow(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            amount,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AssignDialog(
    categoryName: String,
    currentAmount: Double,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(categoryName, currentAmount) {
        mutableStateOf(if (currentAmount == 0.0) "" else amountInputText(currentAmount))
    }
    val parsed = parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zb_assign_title, categoryName)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.zb_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onSave) },
                enabled = parsed != null
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.zb_cancel))
            }
        }
    )
}

@Composable
private fun GoalDialog(
    categoryName: String,
    existing: CategoryGoal?,
    defaultTargetMonth: YearMonth,
    onSave: (Double, YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(categoryName, existing) {
        mutableStateOf(
            if (existing == null || existing.targetAmount == 0.0) {
                ""
            } else {
                amountInputText(existing.targetAmount)
            }
        )
    }
    var targetMonth by remember(categoryName, existing) {
        mutableStateOf(existing?.targetMonth ?: defaultTargetMonth)
    }
    val parsed = parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zb_goal_title, categoryName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.zb_goal_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { targetMonth = targetMonth.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Earlier")
                    }
                    Text(
                        targetMonth.format(
                            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { targetMonth = targetMonth.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Later")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(it, targetMonth) } },
                enabled = parsed != null && parsed > 0.0
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.zb_cancel))
            }
        }
    )
}

/** "1234.5" typed by the user -> 1234.5; accepts a comma decimal separator. */
private fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }

/** Pre-fills an amount field without a useless trailing ".0". */
private fun amountInputText(amount: Double): String =
    if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        amount.toString()
    }
