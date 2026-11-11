package com.moifinansy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.debt.DebtMath
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.DebtTotals
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.model.Periods
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.DebtRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.ui.operations.OperationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

data class TopCategory(val category: Category, val total: Long, val sharePercent: Int)

data class DueDebt(val debt: DebtWithRemaining, val contactName: String, val overdue: Boolean)

data class HomeState(
    val loading: Boolean = true,
    val baseCurrency: String = Currencies.DEFAULT,
    val month: YearMonth = YearMonth.now(),
    val total: Long = 0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val topCategories: List<TopCategory> = emptyList(),
    val debtTotals: DebtTotals = DebtTotals(0, 0),
    val dueSoon: List<DueDebt> = emptyList(),
    val latest: List<OperationItem> = emptyList(),
    val hideAmounts: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val debts: DebtRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val settingsRepo = settings

    val state: StateFlow<HomeState> = settings.baseCurrency
        .map { it ?: Currencies.DEFAULT }
        .flatMapLatest { base -> homeFlow(base) }
        .combine(settings.hideAmounts) { s, hide -> s.copy(hideAmounts = hide) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun homeFlow(base: String): Flow<HomeState> {
        val month = YearMonth.now(clock)
        val range = Periods.month(month, clock.zone)
        val allCategories = combine(
            categories.observe(CategoryKind.EXPENSE, includeArchived = true),
            categories.observe(CategoryKind.INCOME, includeArchived = true),
        ) { e, i -> (e + i).associateBy { it.id } }

        val money = combine(
            accounts.observeAccounts(),
            accounts.observeTotal(base),
            transactions.observeTotalIncome(range.from, range.to, base),
            transactions.observeTotalExpense(range.from, range.to, base),
            transactions.observeExpenseByCategory(range.from, range.to, base),
        ) { accountList, total, income, expense, byCategory -> Money(accountList, total, income, expense, byCategory) }

        val debtInfo = combine(debts.observeTotals(base), debts.observeDebts(), debts.observeContacts()) { totals, list, contacts ->
            Triple(totals, list, contacts)
        }

        return combine(money, debtInfo, allCategories, transactions.observeLatest(5)) { m, (totals, debtList, contacts), cats, latest ->
            HomeState(
                loading = false,
                baseCurrency = base,
                month = month,
                total = m.total,
                accounts = m.accounts,
                income = m.income,
                expense = m.expense,
                topCategories = topCategories(m.byCategory, cats, m.expense),
                debtTotals = totals,
                dueSoon = dueSoon(debtList, contacts),
                latest = OperationItem.build(latest, (m.accounts).associateBy { it.account.id }, cats),
            )
        }
    }

    fun toggleHideAmounts() {
        viewModelScope.launch { settingsRepo.setHideAmounts(!state.value.hideAmounts) }
    }

    private data class Money(
        val accounts: List<AccountWithBalance>,
        val total: Long,
        val income: Long,
        val expense: Long,
        val byCategory: List<com.moifinansy.domain.model.CategoryTotal>,
    )

    /** Топ-3 категорий расходов: подкатегории сворачиваются в родителя. */
    private fun topCategories(
        rows: List<com.moifinansy.domain.model.CategoryTotal>,
        categories: Map<Long, Category>,
        totalExpense: Long,
    ): List<TopCategory> = rows
        .groupBy { row -> categories[row.categoryId]?.let { it.parentId ?: it.id } ?: row.categoryId }
        .mapNotNull { (id, list) -> categories[id]?.let { it to list.sumOf { r -> r.total } } }
        .sortedByDescending { it.second }
        .take(3)
        .map { (category, total) -> TopCategory(category, total, if (totalExpense > 0) (total * 100 / totalExpense).toInt() else 0) }

    /** Активные долги со сроком в ближайшие 7 дней или уже просроченные. */
    private fun dueSoon(list: List<DebtWithRemaining>, contacts: List<Contact>): List<DueDebt> {
        val now = clock.millis()
        val horizon = now + 7L * 24 * 60 * 60 * 1000
        val names = contacts.associate { it.id to it.name }
        return list
            .filter { it.debt.status == DebtStatus.ACTIVE && it.debt.dueDate != null && it.debt.dueDate!! <= horizon }
            .sortedBy { it.debt.dueDate }
            .take(3)
            .map { DueDebt(it, names[it.debt.contactId].orEmpty(), DebtMath.isOverdue(it.debt.status, it.debt.dueDate, now)) }
    }
}
