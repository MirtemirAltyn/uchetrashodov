package com.moifinansy.ui.operations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.AmountCalculator
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Tag
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.TagRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class OperationEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val toAmountText: String = "",
    val feeText: String = "",
    val expenseCategories: List<Category> = emptyList(),
    val incomeCategories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val date: Long = 0,
    val note: String = "",
    val tags: List<Tag> = emptyList(),
    val tagIds: Set<Long> = emptySet(),
    val newTag: String = "",
    val amountError: Boolean = false,
    val categoryError: Boolean = false,
    val toAccountError: Boolean = false,
    val toAmountError: Boolean = false,
    val feeError: Boolean = false,
    val done: Boolean = false,
) {
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val toAccount: Account? get() = accounts.firstOrNull { it.id == toAccountId }

    /** Счета перевода в разных валютах — нужна сумма зачисления. */
    val needsToAmount: Boolean
        get() = type == TransactionType.TRANSFER && toAccount != null && account != null && toAccount!!.currencyCode != account!!.currencyCode

    val categories: List<Category>
        get() = if (type == TransactionType.INCOME) incomeCategories else expenseCategories

    val selectedCategory: Category? get() = categories.firstOrNull { it.id == categoryId }

    /** Выбранная категория верхнего уровня (сама или родитель выбранной подкатегории). */
    val selectedParentId: Long? get() = selectedCategory?.let { it.parentId ?: it.id }

    val evaluatedAmount: Long? get() = AmountCalculator.evaluate(amountText)
}

@HiltViewModel
class OperationEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val settings: SettingsRepository,
    private val undo: UndoDeleteHolder,
    private val clock: Clock,
    private val checks: com.moifinansy.work.BackgroundChecks,
) : ViewModel() {
    private val editId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private val copyFromId: Long? = savedStateHandle.get<Long>("copyFrom")?.takeIf { it > 0 }
    private val argType: TransactionType =
        savedStateHandle.get<String>("type")?.let(TransactionType::valueOf) ?: TransactionType.EXPENSE
    private var original: Transaction? = null

    private val _state = MutableStateFlow(OperationEditState())
    val state: StateFlow<OperationEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val accountList = accounts.observeAccounts().first().map { it.account }
            val expense = categories.observe(CategoryKind.EXPENSE).first()
            val income = categories.observe(CategoryKind.INCOME).first()
            val tagList = tags.observeAll().first()
            val source = (editId ?: copyFromId)?.let { transactions.get(it) }
            original = if (editId != null) source else null
            val base = OperationEditState(
                loading = false,
                accounts = accountList,
                expenseCategories = expense,
                incomeCategories = income,
                tags = tagList,
                date = clock.millis(),
            )
            _state.value = if (source != null) {
                val sourceTags = transactions.tagIdsOf(source.id).toSet()
                base.copy(
                    isNew = editId == null,
                    type = source.type,
                    amountText = MoneyFormat.toInput(source.amount),
                    accountId = source.accountId,
                    toAccountId = source.toAccountId,
                    toAmountText = source.toAmount?.let(MoneyFormat::toInput).orEmpty(),
                    feeText = if (source.fee > 0) MoneyFormat.toInput(source.fee) else "",
                    categoryId = source.categoryId,
                    date = if (editId != null) source.date else clock.millis(),
                    note = source.note.orEmpty(),
                    tagIds = sourceTags,
                    // Архивный счёт старой операции тоже должен быть в списке.
                    accounts = accountList + listOfNotNull(source.accountId, source.toAccountId)
                        .filter { id -> accountList.none { it.id == id } }
                        .mapNotNull { accounts.get(it) },
                )
            } else {
                val last = settings.lastAccountId.first()
                base.copy(
                    type = argType,
                    accountId = accountList.firstOrNull { it.id == last }?.id ?: accountList.firstOrNull()?.id,
                )
            }
        }
    }

    fun editIdOrNull(): Long? = editId

    fun setType(type: TransactionType) = _state.update {
        val keepCategory = it.type == type
        it.copy(
            type = type,
            categoryId = if (keepCategory) it.categoryId else null,
            categoryError = false,
            toAccountId = if (type == TransactionType.TRANSFER) it.toAccountId ?: it.accounts.firstOrNull { a -> a.id != it.accountId }?.id else null,
        )
    }

    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountError = false) }
    fun appendOperator(op: Char) = _state.update {
        val trimmed = it.amountText.trimEnd()
        val withoutTrailing = if (trimmed.lastOrNull()?.let { c -> c in "+−×÷" } == true) trimmed.dropLast(1) else trimmed
        if (withoutTrailing.isEmpty()) it else it.copy(amountText = withoutTrailing + op, amountError = false)
    }

    fun setAccount(id: Long) = _state.update { it.copy(accountId = id, toAccountError = false) }
    fun setToAccount(id: Long) = _state.update { it.copy(toAccountId = id, toAccountError = false) }
    fun setToAmount(text: String) = _state.update { it.copy(toAmountText = text, toAmountError = false) }
    fun setFee(text: String) = _state.update { it.copy(feeText = text, feeError = false) }
    fun selectCategory(id: Long) = _state.update { it.copy(categoryId = id, categoryError = false) }
    fun setNote(text: String) = _state.update { it.copy(note = text) }
    fun toggleTag(id: Long) = _state.update { it.copy(tagIds = if (id in it.tagIds) it.tagIds - id else it.tagIds + id) }
    fun setNewTag(text: String) = _state.update { it.copy(newTag = text) }

    fun setDate(date: LocalDate) {
        val zone = clock.zone
        val time = LocalTime.now(clock)
        _state.update { it.copy(date = date.atTime(time).atZone(zone).toInstant().toEpochMilli()) }
    }

    fun localDate(): LocalDate = Instant.ofEpochMilli(_state.value.date).atZone(clock.zone).toLocalDate()
    fun today(): LocalDate = LocalDate.now(clock)

    fun addTag() {
        val name = _state.value.newTag.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val existing = _state.value.tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val id = existing?.id ?: tags.create(name)
            val list = if (existing == null) tags.observeAll().first() else _state.value.tags
            _state.update { it.copy(tags = list, tagIds = it.tagIds + id, newTag = "") }
        }
    }

    fun save() {
        val s = _state.value
        val amount = s.evaluatedAmount
        val isAdjustment = s.type == TransactionType.ADJUSTMENT
        val amountOk = amount != null && (if (isAdjustment) amount != 0L else amount > 0)
        val categoryOk = s.type != TransactionType.EXPENSE && s.type != TransactionType.INCOME || s.selectedCategory != null
        val transfer = s.type == TransactionType.TRANSFER
        val toAccountOk = !transfer || (s.toAccountId != null && s.toAccountId != s.accountId)
        val toAmount = if (s.needsToAmount) AmountCalculator.evaluate(s.toAmountText) else null
        val toAmountOk = !s.needsToAmount || (toAmount != null && toAmount > 0)
        val fee = if (transfer && s.feeText.isNotBlank()) AmountCalculator.evaluate(s.feeText) else 0L
        val feeOk = fee != null && fee >= 0
        if (!amountOk || !categoryOk || !toAccountOk || !toAmountOk || !feeOk || s.accountId == null) {
            _state.update {
                it.copy(
                    amountError = !amountOk, categoryError = !categoryOk, toAccountError = !toAccountOk,
                    toAmountError = !toAmountOk, feeError = !feeOk,
                )
            }
            return
        }
        val transaction = Transaction(
            id = editId ?: 0,
            type = s.type,
            amount = amount!!,
            accountId = s.accountId,
            categoryId = if (s.type == TransactionType.EXPENSE || s.type == TransactionType.INCOME) s.categoryId else null,
            toAccountId = if (transfer) s.toAccountId else null,
            toAmount = toAmount,
            fee = if (transfer) fee!! else 0,
            date = s.date,
            note = s.note.trim().ifEmpty { null },
            debtId = original?.debtId,
            recurringId = original?.recurringId,
            receiptUri = original?.receiptUri,
        )
        viewModelScope.launch {
            transactions.save(transaction, s.tagIds.toList())
            settings.setLastAccountId(s.accountId)
            if (s.type == TransactionType.EXPENSE) checks.requestCheck()
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = editId ?: return
        viewModelScope.launch {
            undo.delete(id)
            _state.update { it.copy(done = true) }
        }
    }
}
