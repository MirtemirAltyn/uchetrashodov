package com.moifinansy.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val parentId: Long? = null,
    val parentName: String? = null,
    val name: String = "",
    val color: Long = DEFAULT_COLOR,
    val icon: String = DEFAULT_ICON,
    val isArchived: Boolean = false,
    val isSystem: Boolean = false,
    val nameError: Boolean = false,
    val delete: DeleteDialog? = null,
    val done: Boolean = false,
) {
    /** Подкатегории есть только у категорий верхнего уровня. */
    val canHaveChildren: Boolean get() = !isNew && parentId == null

    data class DeleteDialog(
        val usageCount: Int,
        val childrenCount: Int,
        val targets: List<Category>,
        val targetId: Long? = null,
    ) {
        val blocked: Boolean get() = childrenCount > 0
        val needsTarget: Boolean get() = usageCount > 0
        val canConfirm: Boolean get() = !blocked && (!needsTarget || targetId != null)
    }

    companion object {
        const val DEFAULT_COLOR = 0xFF5B5BD6
        const val DEFAULT_ICON = "more_horiz"
    }
}

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val categories: CategoryRepository,
) : ViewModel() {
    private val categoryId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val argKind: CategoryKind = savedStateHandle.get<String>("kind")?.let(CategoryKind::valueOf) ?: CategoryKind.EXPENSE
    private val argParentId: Long? = savedStateHandle.get<Long>("parentId")?.takeIf { it > 0 }
    private var original: Category? = null

    private val _state = MutableStateFlow(CategoryEditState())
    val state: StateFlow<CategoryEditState> = _state.asStateFlow()

    /** Подкатегории этой категории (для списка с перетаскиванием). */
    val children: StateFlow<List<Category>> = categories.observe(argKind)
        .map { all -> if (categoryId == null) emptyList() else all.filter { it.parentId == categoryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val category = categoryId?.let { categories.get(it) }
            original = category
            val parentId = category?.parentId ?: argParentId
            val parent = parentId?.let { categories.get(it) }
            _state.value = if (category == null) {
                CategoryEditState(
                    loading = false,
                    kind = parent?.kind ?: argKind,
                    parentId = parentId,
                    parentName = parent?.name,
                    color = parent?.color ?: CategoryEditState.DEFAULT_COLOR,
                    icon = parent?.icon ?: CategoryEditState.DEFAULT_ICON,
                )
            } else {
                CategoryEditState(
                    loading = false,
                    isNew = false,
                    kind = category.kind,
                    parentId = category.parentId,
                    parentName = parent?.name,
                    name = category.name,
                    color = category.color,
                    icon = category.icon,
                    isArchived = category.isArchived,
                    isSystem = category.systemKey != null,
                )
            }
        }
    }

    fun categoryIdOrNull(): Long? = categoryId

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun setColor(value: Long) = _state.update { it.copy(color = value) }
    fun setIcon(value: String) = _state.update { it.copy(icon = value) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val siblingsCount = categories.observe(s.kind, includeArchived = true).first().count { it.parentId == s.parentId }
            val category = Category(
                id = categoryId ?: 0,
                name = s.name.trim(),
                kind = s.kind,
                parentId = s.parentId,
                icon = s.icon,
                color = s.color,
                // Новая категория — в конец списка.
                sortOrder = original?.sortOrder ?: siblingsCount,
                isArchived = s.isArchived,
                systemKey = original?.systemKey,
            )
            if (categoryId == null) categories.create(category) else categories.update(category)
            _state.update { it.copy(done = true) }
        }
    }

    fun toggleArchived() {
        val id = categoryId ?: return
        val archived = !_state.value.isArchived
        viewModelScope.launch {
            categories.setArchived(id, archived)
            _state.update { it.copy(isArchived = archived, done = true) }
        }
    }

    /** «В архив» вместо удаления (категория остаётся в старых операциях). */
    fun archiveInstead() {
        val id = categoryId ?: return
        viewModelScope.launch {
            categories.setArchived(id, true)
            _state.update { it.copy(isArchived = true, delete = null, done = true) }
        }
    }

    fun reorderChildren(ids: List<Long>) {
        viewModelScope.launch { categories.reorder(ids) }
    }

    fun askDelete() {
        val id = categoryId ?: return
        viewModelScope.launch {
            val kind = _state.value.kind
            val targets = categories.observe(kind).first().filter { it.id != id && it.parentId != id }
            _state.update {
                it.copy(
                    delete = CategoryEditState.DeleteDialog(
                        usageCount = categories.usageCount(id),
                        childrenCount = categories.childrenCount(id),
                        targets = targets,
                    ),
                )
            }
        }
    }

    fun chooseDeleteTarget(targetId: Long) = _state.update { s -> s.copy(delete = s.delete?.copy(targetId = targetId)) }
    fun dismissDelete() = _state.update { it.copy(delete = null) }

    fun confirmDelete() {
        val id = categoryId ?: return
        val dialog = _state.value.delete ?: return
        if (!dialog.canConfirm) return
        viewModelScope.launch {
            categories.delete(id, dialog.targetId)
            _state.update { it.copy(delete = null, done = true) }
        }
    }
}
