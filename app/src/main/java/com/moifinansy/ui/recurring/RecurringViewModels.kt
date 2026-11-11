package com.moifinansy.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.AmountCalculator
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.RecurrenceFrequency
import com.moifinansy.domain.model.RecurringMode
import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class RecurringItem(val rule: RecurringRule, val account: Account?, val toAccount: Account?, val category: Category?, val due: Boolean)

data class RecurringListState(val loading: Boolean = true, val items: List<RecurringItem> = emptyList())

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val recurring: RecurringRepository,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<RecurringListState> = combine(
        recurring.observeAll(),
        accounts.observeAccounts(includeArchived = true),
        categories.observe(CategoryKind.EXPENSE, includeArchived = true),
        categories.observe(CategoryKind.INCOME, includeArchived = true),
    ) { rules, accs, e, i ->
        val a = accs.associate { it.account.id to it.account }
        val c = (e + i).associateBy { it.id }
        val now = clock.millis()
        RecurringListState(
            loading = false,
            items = rules.map { r ->
                RecurringItem(r, a[r.accountId], r.toAccountId?.let { a[it] }, r.categoryId?.let { c[it] }, r.nextDate <= now && (r.endDate == null || r.nextDate <= r.endDate!!))
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListState())

    fun post(item: RecurringItem) {
        viewModelScope.launch { recurring.post(item.rule.id, item.rule.nextDate) }
    }

    fun skip(item: RecurringItem) {
        viewModelScope.launch { recurring.skip(item.rule.id) }
    }
}

data class RecurringEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTH,
    val intervalText: String = "1",
    val firstDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val mode: RecurringMode = RecurringMode.REMIND,
    val note: String = "",
    val amountError: Boolean = false,
    val categoryError: Boolean = false,
    val accountError: Boolean = false,
    val intervalError: Boolean = false,
    val done: Boolean = false,
) {
    val categories: List<Category> get() = if (type == TransactionType.INCOME) incomeCategories else expenseCategories
}

@HiltViewModel
class RecurringEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurring: RecurringRepository,
    private val accountsRepo: AccountRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {
    private val ruleId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val _state = MutableStateFlow(RecurringEditState())
    val state: StateFlow<RecurringEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accs = accountsRepo.observeAccounts().first().map(AccountWithBalance::account)
            val e = categories.observe(CategoryKind.EXPENSE).first()
            val i = categories.observe(CategoryKind.INCOME).first()
            val r = ruleId?.let { recurring.get(it) }
            val base = RecurringEditState(loading = false, accounts = accs, expenseCategories = e, incomeCategories = i, accountId = accs.firstOrNull()?.id, firstDate = LocalDate.now(clock))
            _state.value = if (r == null) base else base.copy(
                isNew = false,
                type = r.type,
                amountText = MoneyFormat.toInput(r.amount),
                accountId = r.accountId,
                toAccountId = r.toAccountId,
                categoryId = r.categoryId,
                frequency = r.frequency,
                intervalText = r.interval.toString(),
                firstDate = Instant.ofEpochMilli(r.nextDate).atZone(clock.zone).toLocalDate(),
                endDate = r.endDate?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() },
                mode = r.mode,
                note = r.note.orEmpty(),
            )
        }
    }

    fun setType(t: TransactionType) = _state.update {
        it.copy(type = t, categoryId = if (it.type == t) it.categoryId else null, toAccountId = if (t == TransactionType.TRANSFER) it.accounts.firstOrNull { a -> a.id != it.accountId }?.id else null)
    }
    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountError = false) }
    fun setAccount(id: Long) = _state.update { it.copy(accountId = id, accountError = false) }
    fun setToAccount(id: Long) = _state.update { it.copy(toAccountId = id, accountError = false) }
    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id, categoryError = false) }
    fun setFrequency(f: RecurrenceFrequency) = _state.update { it.copy(frequency = f) }
    fun setInterval(text: String) = _state.update { it.copy(intervalText = text.filter(Char::isDigit).take(3), intervalError = false) }
    fun setFirstDate(d: LocalDate) = _state.update { it.copy(firstDate = d) }
    fun setEndDate(d: LocalDate?) = _state.update { it.copy(endDate = d) }
    fun setMode(m: RecurringMode) = _state.update { it.copy(mode = m) }
    fun setNote(text: String) = _state.update { it.copy(note = text) }

    fun save() {
        val s = _state.value
        val amount = AmountCalculator.evaluate(s.amountText)
        val interval = s.intervalText.toIntOrNull()
        val needsCategory = s.type != TransactionType.TRANSFER
        val transferOk = s.type != TransactionType.TRANSFER || (s.toAccountId != null && s.toAccountId != s.accountId)
        val errors = s.copy(
            amountError = amount == null || amount <= 0,
            categoryError = needsCategory && s.categoryId == null,
            accountError = s.accountId == null || !transferOk,
            intervalError = interval == null || interval <= 0,
        )
        if (errors.amountError || errors.categoryError || errors.accountError || errors.intervalError) {
            _state.value = errors
            return
        }
        val zone = clock.zone
        viewModelScope.launch {
            recurring.save(
                RecurringRule(
                    id = ruleId ?: 0,
                    type = s.type,
                    amount = amount!!,
                    accountId = s.accountId!!,
                    categoryId = if (needsCategory) s.categoryId else null,
                    toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccountId else null,
                    note = s.note.trim().ifEmpty { null },
                    frequency = s.frequency,
                    interval = interval!!,
                    // Платёж в 12:00 выбранного дня.
                    nextDate = s.firstDate.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                    mode = s.mode,
                    endDate = s.endDate?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1),
                ),
            )
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = ruleId ?: return
        viewModelScope.launch {
            recurring.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
