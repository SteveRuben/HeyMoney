package org.abodah.heymoney.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.entity.CategoryEntity
import com.ivy.data.rules.CategoryRule
import com.ivy.data.rules.CategoryRuleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CategoryRulesViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    val store: CategoryRuleStore,
) : ViewModel() {

    val rules: StateFlow<List<CategoryRule>> = store.rules

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories

    init {
        viewModelScope.launch {
            _categories.value = categoryDao.findAll().sortedBy { it.orderNum }
        }
    }

    fun categoryName(categoryId: UUID): String? =
        _categories.value.firstOrNull { it.id == categoryId }?.name

    fun add(keyword: String, categoryId: UUID) = store.add(keyword, categoryId)

    fun remove(rule: CategoryRule) = store.remove(rule)
}
