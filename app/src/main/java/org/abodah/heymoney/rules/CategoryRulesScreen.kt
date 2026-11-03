package org.abodah.heymoney.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import java.util.UUID

/**
 * "Category rules": the user's own keyword → category rules. They are applied,
 * in order, whenever a transaction would otherwise land uncategorised —
 * bank alerts first, then CSV statement imports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesScreen(viewModel: CategoryRulesViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val rules by viewModel.rules.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var keyword by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<UUID?>(null) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.rules_title)) },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.rules_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.rules_keyword_label)) },
                            placeholder = { Text(stringResource(R.string.rules_keyword_hint)) },
                            singleLine = true
                        )
                        Box {
                            OutlinedButton(
                                onClick = { categoryMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    selectedCategory?.name
                                        ?: stringResource(R.string.rules_category_label)
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = categoryMenuOpen,
                                onDismissRequest = { categoryMenuOpen = false }
                            ) {
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            selectedCategoryId = category.id
                                            categoryMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val categoryId = selectedCategoryId ?: return@Button
                                viewModel.add(keyword, categoryId)
                                keyword = ""
                            },
                            enabled = keyword.isNotBlank() && selectedCategoryId != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.rules_add)) }
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.rules_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(rules, key = { it.keyword + it.categoryId }) { rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(rule.keyword, fontWeight = FontWeight.SemiBold)
                                Text(
                                    viewModel.categoryName(rule.categoryId)
                                        ?: stringResource(R.string.rules_unknown_category),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { viewModel.remove(rule) }) {
                                Text(stringResource(R.string.rules_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}
