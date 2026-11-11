package com.moifinansy.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.OperationFilter
import com.moifinansy.domain.model.Periods
import com.moifinansy.domain.model.Tag
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.TagRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Что выбрал пользователь в шапке списка. */
data class OperationsQuery(
    val month: YearMonth,
    val allTime: Boolean = false,
    val search: String = "",
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val tagId: Long? = null,
    val minText: String = "",
    val maxText: String = "",
    val showFilters: Boolean = false,
) {
    val hasExtraFilters: Boolean
        get() = accountId != null || categoryId != null || tagId != null || minText.isNotBlank() || maxText.isNotBlank()
}

data class DayGroup(val date: LocalDate, val total: Long, val items: List<OperationItem>)

data class OperationsState(
    val loading: Boolean = true,
    val query: OperationsQuery,
    val days: List<DayGroup> = emptyList(),
    val baseCurrency: String = Currencies.DEFAULT,
    val accounts: List<AccountWithBalance> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OperationsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    tags: TagRepository,
    settings: SettingsRepository,
    private val undo: UndoDeleteHolder,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow(OperationsQuery(month = YearMonth.now(clock)))

    private val dictionaries = combine(
        accounts.observeAccounts(includeArchived = true),
        categories.observe(CategoryKind.EXPENSE, includeArchived = true),
        categories.observe(CategoryKind.INCOME, includeArchived = true),
        tags.observeAll(),
        settings.baseCurrency,
    ) { a, e, i, t, base -> Dictionaries(a, e + i, t, base ?: Currencies.DEFAULT) }

    val state: StateFlow<OperationsState> = combine(query, dictionaries) { q, d -> q to d }
        .flatMapLatest { (q, d) ->
            transactions.observeFiltered(q.toFilter()).map { list ->
                val accountsById = d.accounts.associateBy { it.account.id }
                val categoriesById = d.categories.associateBy { it.id }
                val items = OperationItem.build(list, accountsById, categoriesById).filter { it.matches(q.search, categoriesById) }
                OperationsState(
                    loading = false,
                    query = q,
                    days = group(items),
                    baseCurrency = d.baseCurrency,
                    accounts = d.accounts.filterNot { it.account.isArchived },
                    categories = d.categories.filterNot { it.isArchived },
                    tags = d.tags,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OperationsState(query = query.value))

    private data class Dictionaries(
        val accounts: List<AccountWithBalance>,
        val categories: List<Category>,
        val tags: List<Tag>,
        val baseCurrency: String,
    )

    private fun OperationsQuery.toFilter(): OperationFilter {
        val range = if (allTime) Periods.ALL else Periods.month(month, clock.zone)
        return OperationFilter(
            from = range.from,
            to = range.to,
            type = type,
            accountId = accountId,
            categoryId = categoryId,
            tagId = tagId,
            minAmount = MoneyFormat.parse(minText),
            maxAmount = MoneyFormat.parse(maxText),
        )
    }

    /** Поиск по комментарию и названию категории без учёта регистра (в том числе кириллицы). */
    private fun OperationItem.matches(search: String, categories: Map<Long, Category>): Boolean {
        val q = search.trim().lowercase()
        if (q.isEmpty()) return true
        val note = transaction.note.orEmpty().lowercase()
        val category = transaction.categoryId?.let { categories[it]?.name }.orEmpty().lowercase()
        return q in note || q in category
    }

    private fun group(items: List<OperationItem>): List<DayGroup> = items
        .groupBy { Instant.ofEpochMilli(it.transaction.date).atZone(clock.zone).toLocalDate() }
        .map { (date, dayItems) ->
            // Итог дня: доходы минус расходы; переводы, корректировки и долги не входят.
            val total = dayItems.sumOf {
                when (it.type) {
                    TransactionType.EXPENSE, TransactionType.INCOME -> it.displayAmount
                    else -> 0L
                }
            }
            DayGroup(date, total, dayItems)
        }

    fun prevMonth() = query.update { it.copy(month = it.month.minusMonths(1), allTime = false) }
    fun nextMonth() = query.update { it.copy(month = it.month.plusMonths(1), allTime = false) }
    fun toggleAllTime() = query.update { it.copy(allTime = !it.allTime) }
    fun setSearch(text: String) = query.update { it.copy(search = text) }
    fun setType(type: TransactionType?) = query.update { it.copy(type = type) }
    fun setAccount(id: Long?) = query.update { it.copy(accountId = id) }
    fun setCategory(id: Long?) = query.update { it.copy(categoryId = id) }
    fun setTag(id: Long?) = query.update { it.copy(tagId = id) }
    fun setMin(text: String) = query.update { it.copy(minText = text) }
    fun setMax(text: String) = query.update { it.copy(maxText = text) }
    fun toggleFilters() = query.update { it.copy(showFilters = !it.showFilters) }
    fun resetFilters() = query.update { OperationsQuery(month = it.month, allTime = it.allTime, showFilters = it.showFilters) }

    fun delete(id: Long) {
        viewModelScope.launch { undo.delete(id) }
    }
}
