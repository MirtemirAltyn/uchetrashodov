package com.moifinansy.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.CategoryTotal
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.MonthTotals
import com.moifinansy.domain.model.PeriodKind
import com.moifinansy.domain.model.ReportPeriod
import com.moifinansy.domain.model.changePercent
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class ReportTab { EXPENSE, INCOME, DYNAMICS }

data class Slice(val category: Category, val total: Long, val percent: Int)

data class MonthPoint(val month: YearMonth, val income: Long, val expense: Long, val balanceEnd: Long)

data class ReportsState(
    val loading: Boolean = true,
    val period: ReportPeriod,
    val tab: ReportTab = ReportTab.EXPENSE,
    val baseCurrency: String = Currencies.DEFAULT,
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val expenseSlices: List<Slice> = emptyList(),
    val incomeSlices: List<Slice> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val prevIncome: Long = 0,
    val prevExpense: Long = 0,
    val avgDailyExpense: Long = 0,
    val months: List<MonthPoint> = emptyList(),
) {
    val difference: Long get() = income - expense
}

/** Сворачивает суммы подкатегорий в родителя и считает доли (в сумме ровно 100 % не гарантируется из-за округления). */
fun rollUp(rows: List<CategoryTotal>, categories: Map<Long, Category>): List<Slice> {
    val byParent = rows.groupBy { row -> categories[row.categoryId]?.let { it.parentId ?: it.id } ?: row.categoryId }
        .mapNotNull { (id, list) -> categories[id]?.let { it to list.sumOf { r -> r.total } } }
        .sortedByDescending { it.second }
    val total = byParent.sumOf { it.second }
    return byParent.map { (category, sum) -> Slice(category, sum, if (total > 0) (sum * 100 / total).toInt() else 0) }
}

/** Баланс на конец каждого месяца: от текущего общего баланса отнимаем изменения после месяца. */
fun monthPoints(window: List<YearMonth>, totals: List<MonthTotals>, totalNow: Long): List<MonthPoint> {
    val byMonth = totals.associateBy { it.month }
    return window.map { month ->
        val after = totals.filter { it.month > month }.sumOf { it.net }
        val m = byMonth[month]
        MonthPoint(month, m?.income ?: 0, m?.expense ?: 0, totalNow - after)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val selection = MutableStateFlow(ReportPeriod.month(LocalDate.now(clock)) to ReportTab.EXPENSE)

    val state: StateFlow<ReportsState> = combine(selection, settings.baseCurrency, settings.firstDayOfWeek) { sel, base, firstDay ->
        Triple(sel, base ?: Currencies.DEFAULT, firstDay)
    }.flatMapLatest { (sel, base, firstDay) -> reportFlow(sel.first, sel.second, base, firstDay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState(period = selection.value.first))

    private fun reportFlow(period: ReportPeriod, tab: ReportTab, base: String, firstDay: DayOfWeek): Flow<ReportsState> {
        val zone = clock.zone
        val range = period.range(zone, firstDay)
        val prev = period.previous(firstDay).range(zone, firstDay)
        val endMonth = YearMonth.from(period.days(firstDay).second)
        val window = (11 downTo 0).map { endMonth.minusMonths(it.toLong()) }
        val windowFrom = window.first().atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val cats = combine(
            categories.observe(CategoryKind.EXPENSE, includeArchived = true),
            categories.observe(CategoryKind.INCOME, includeArchived = true),
        ) { e, i -> (e + i).associateBy { it.id } }
        val current = combine(
            transactions.observeExpenseByCategory(range.from, range.to),
            transactions.observeIncomeByCategory(range.from, range.to),
            transactions.observeTotalIncome(range.from, range.to),
            transactions.observeTotalExpense(range.from, range.to),
            cats,
        ) { e, i, income, expense, c -> Current(rollUp(e, c), rollUp(i, c), income, expense) }
        val previous = combine(transactions.observeTotalIncome(prev.from, prev.to), transactions.observeTotalExpense(prev.from, prev.to)) { i, e -> i to e }
        val dynamics = combine(transactions.observeMonthTotals(windowFrom, Long.MAX_VALUE), accounts.observeTotal(base)) { totals, now ->
            monthPoints(window, totals, now)
        }
        return combine(current, previous, dynamics) { c, (pi, pe), months ->
            ReportsState(
                loading = false,
                period = period,
                tab = tab,
                baseCurrency = base,
                firstDay = firstDay,
                expenseSlices = c.expenseSlices,
                incomeSlices = c.incomeSlices,
                income = c.income,
                expense = c.expense,
                prevIncome = pi,
                prevExpense = pe,
                avgDailyExpense = c.expense / period.dayCount(firstDay).coerceAtLeast(1),
                months = months,
            )
        }
    }

    private data class Current(val expenseSlices: List<Slice>, val incomeSlices: List<Slice>, val income: Long, val expense: Long)

    fun setTab(tab: ReportTab) = selection.update { it.first to tab }
    fun setKind(kind: PeriodKind) = selection.update { (p, t) ->
        val today = LocalDate.now(clock)
        val period = if (kind == PeriodKind.CUSTOM) {
            val (from, to) = p.days(state.value.firstDay)
            ReportPeriod(kind, today, from, to)
        } else {
            ReportPeriod(kind, if (p.kind == PeriodKind.CUSTOM) today else p.anchor)
        }
        period to t
    }
    fun setCustom(from: LocalDate, to: LocalDate) = selection.update { (_, t) -> ReportPeriod(PeriodKind.CUSTOM, from, from, to) to t }
    fun shift(steps: Long) = selection.update { (p, t) -> p.shift(steps, state.value.firstDay) to t }
}

fun ReportsState.expenseChange(): Int? = changePercent(expense, prevExpense)
fun ReportsState.incomeChange(): Int? = changePercent(income, prevIncome)
