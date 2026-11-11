package com.moifinansy.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Категория верхнего уровня с названиями её подкатегорий. */
data class CategoryItem(val category: Category, val childNames: List<String>)

data class CategoriesState(
    val loading: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val active: List<CategoryItem> = emptyList(),
    val archived: List<CategoryItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
) : ViewModel() {
    private val kind = MutableStateFlow(CategoryKind.EXPENSE)

    val state: StateFlow<CategoriesState> = kind
        .flatMapLatest { k ->
            categories.observe(k, includeArchived = true).map { all -> buildState(k, all) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesState())

    fun selectKind(value: CategoryKind) {
        kind.value = value
    }

    /** Новый порядок категорий верхнего уровня после перетаскивания. */
    fun reorder(ids: List<Long>) {
        viewModelScope.launch { categories.reorder(ids) }
    }

    companion object {
        fun buildState(kind: CategoryKind, all: List<Category>): CategoriesState {
            val children = all.filter { it.parentId != null && !it.isArchived }.groupBy { it.parentId }
            val items = all.filter { it.parentId == null }.map { parent ->
                CategoryItem(parent, children[parent.id].orEmpty().sortedBy { it.sortOrder }.map { it.name })
            }
            return CategoriesState(
                loading = false,
                kind = kind,
                active = items.filterNot { it.category.isArchived },
                archived = items.filter { it.category.isArchived } +
                    // Подкатегории в архиве у активного родителя тоже показываем в архиве.
                    all.filter { it.parentId != null && it.isArchived }.map { CategoryItem(it, emptyList()) },
            )
        }
    }
}
