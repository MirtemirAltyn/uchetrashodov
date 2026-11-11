package com.moifinansy.ui.budgets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.AmountCalculator
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.budget.BudgetStatus
import com.moifinansy.domain.model.Budget
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.Periods
import com.moifinansy.domain.repository.BudgetRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

data class BudgetsState(
    val loading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val baseCurrency: String = Currencies.DEFAULT,
    val items: List<BudgetStatus> = emptyList(),
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    budgets: BudgetRepository,
    transactions: TransactionRepository,
    categories: CategoryRepository,
    settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val month = YearMonth.now(clock)
    private val range = Periods.month(month, clock.zone)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<BudgetsState> = settings.baseCurrency.map { it ?: Currencies.DEFAULT }.flatMapLatest { base ->
        combine(
            budgets.observeAll(),
            transactions.observeExpenseByCategory(range.from, range.to, base),
            categories.observe(CategoryKind.EXPENSE, includeArchived = true),
        ) { list, rows, cats ->
            BudgetsState(false, month, base, BudgetStatus.compute(list, rows, cats.associateBy { it.id }))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState())
}

data class BudgetEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val categories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val limitText: String = "",
    val limitError: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class BudgetEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgets: BudgetRepository,
    categories: CategoryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val budgetId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(BudgetEditState())
    val state: StateFlow<BudgetEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cats = categories.observe(CategoryKind.EXPENSE).first()
            val existing = budgetId?.let { id -> budgets.observeAll().first().firstOrNull { it.id == id } }
            _state.value = BudgetEditState(
                loading = false,
                isNew = existing == null,
                categories = cats,
                categoryId = existing?.categoryId,
                limitText = existing?.let { MoneyFormat.toInput(it.limitAmount) }.orEmpty(),
            )
        }
    }

    fun setCategory(id: Long?) = _state.update { it.copy(categoryId = id) }
    fun setLimit(text: String) = _state.update { it.copy(limitText = text, limitError = false) }

    fun save() {
        val s = _state.value
        val limit = AmountCalculator.evaluate(s.limitText)
        if (limit == null || limit <= 0) {
            _state.update { it.copy(limitError = true) }
            return
        }
        viewModelScope.launch {
            budgets.save(Budget(id = budgetId ?: 0, categoryId = s.categoryId, limitAmount = limit, currencyCode = settings.baseCurrency.first() ?: Currencies.DEFAULT))
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = budgetId ?: return
        viewModelScope.launch {
            budgets.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
