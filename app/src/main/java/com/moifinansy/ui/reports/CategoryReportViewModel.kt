package com.moifinansy.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.OperationFilter
import com.moifinansy.domain.model.PeriodKind
import com.moifinansy.domain.model.ReportPeriod
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.model.changePercent
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.ui.operations.OperationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CategoryReportState(
    val loading: Boolean = true,
    val category: Category? = null,
    val period: ReportPeriod,
    val baseCurrency: String = Currencies.DEFAULT,
    val total: Long = 0,
    val sharePercent: Int = 0,
    val change: Int? = null,
    val subcategories: List<Pair<Category, Long>> = emptyList(),
    val months: List<Pair<YearMonth, Long>> = emptyList(),
    val operations: List<OperationItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    accounts: AccountRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val categoryId: Long = checkNotNull(savedStateHandle.get<Long>("id"))
    private val kind: CategoryKind = CategoryKind.valueOf(checkNotNull(savedStateHandle.get<String>("kind")))
    private val period = ReportPeriod(
        PeriodKind.valueOf(checkNotNull(savedStateHandle.get<String>("period"))),
        LocalDate.ofEpochDay(checkNotNull(savedStateHandle.get<Long>("anchor"))),
        savedStateHandle.get<Long>("from")?.takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
        savedStateHandle.get<Long>("to")?.takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
    )

    val state: StateFlow<CategoryReportState> = combine(settings.baseCurrency, settings.firstDayOfWeek) { b, f -> (b ?: Currencies.DEFAULT) to f }
        .flatMapLatest { (base, firstDay) ->
            val zone = clock.zone
            val range = period.range(zone, firstDay)
            val prev = period.previous(firstDay).range(zone, firstDay)
            val endMonth = YearMonth.from(period.days(firstDay).second)
            val window = (11 downTo 0).map { endMonth.minusMonths(it.toLong()) }
            val windowFrom = window.first().atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val windowTo = endMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val byCategory = { from: Long, to: Long ->
                if (kind == CategoryKind.EXPENSE) transactions.observeExpenseByCategory(from, to) else transactions.observeIncomeByCategory(from, to)
            }
            val type = if (kind == CategoryKind.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME
            val dict = combine(categories.observe(kind, includeArchived = true), accounts.observeAccounts(includeArchived = true)) { c, a -> c to a }
            combine(
                dict,
                byCategory(range.from, range.to),
                byCategory(prev.from, prev.to),
                transactions.observeCategoryMonths(categoryId, windowFrom, windowTo),
                transactions.observeFiltered(OperationFilter(range.from, range.to, type = type, categoryId = categoryId)),
            ) { (cats, accs), rows, prevRows, months, ops ->
                val byId = cats.associateBy { it.id }
                val ids = setOf(categoryId) + cats.filter { it.parentId == categoryId }.map { it.id }
                val total = rows.filter { it.categoryId in ids }.sumOf { it.total }
                val all = rows.sumOf { it.total }
                CategoryReportState(
                    loading = false,
                    category = byId[categoryId],
                    period = period,
                    baseCurrency = base,
                    total = total,
                    sharePercent = if (all > 0) (total * 100 / all).toInt() else 0,
                    change = changePercent(total, prevRows.filter { it.categoryId in ids }.sumOf { it.total }),
                    subcategories = rows.filter { it.categoryId in ids && it.categoryId != categoryId }
                        .mapNotNull { r -> byId[r.categoryId]?.let { it to r.total } } +
                        listOfNotNull(rows.firstOrNull { it.categoryId == categoryId }?.takeIf { ids.size > 1 }?.let { r -> byId[categoryId]?.let { it to r.total } }),
                    months = window.map { it to (months[it] ?: 0L) },
                    operations = OperationItem.build(ops, accs.associateBy { it.account.id }, byId),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryReportState(period = period))
}
