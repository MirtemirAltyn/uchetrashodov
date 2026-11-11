package com.moifinansy.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.model.effectOn
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Строка истории: подпись, иконка, дата и влияние на баланс этого счёта. */
data class HistoryRow(
    val id: Long,
    val label: Label,
    val icon: String,
    val color: Long,
    val date: Long,
    val effect: Long,
    val note: String?,
) {
    sealed interface Label {
        data class Category(val name: String?) : Label
        data class TransferOut(val accountName: String?) : Label
        data class TransferIn(val accountName: String?) : Label
        data object Adjustment : Label
        data object Debt : Label
    }
}

data class AccountDetailState(
    val loading: Boolean = true,
    val account: AccountWithBalance? = null,
    val history: List<HistoryRow> = emptyList(),
)

data class AdjustDialogState(val text: String = "", val error: Boolean = false)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accounts: AccountRepository,
    categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val accountId: Long = checkNotNull(savedStateHandle.get<Long>("id"))

    val state: StateFlow<AccountDetailState> = combine(
        accounts.observeAccounts(includeArchived = true),
        transactions.observeForAccount(accountId),
        categories.observe(CategoryKind.EXPENSE, includeArchived = true),
        categories.observe(CategoryKind.INCOME, includeArchived = true),
    ) { allAccounts, history, expenseCategories, incomeCategories ->
        val accountsById = allAccounts.associateBy { it.account.id }
        val categoriesById = (expenseCategories + incomeCategories).associateBy { it.id }
        val current = accountsById[accountId]
        AccountDetailState(
            loading = false,
            account = current,
            history = history.map { t ->
                val category = t.categoryId?.let { categoriesById[it] }
                val account = current?.account
                when (t.type) {
                    TransactionType.EXPENSE, TransactionType.INCOME -> HistoryRow(
                        t.id, HistoryRow.Label.Category(category?.name), category?.icon ?: "more_horiz",
                        category?.color ?: DEFAULT_COLOR, t.date, t.effectOn(accountId), t.note,
                    )
                    TransactionType.TRANSFER -> {
                        val outgoing = t.accountId == accountId
                        val otherId = if (outgoing) t.toAccountId else t.accountId
                        val otherName = otherId?.let { accountsById[it]?.account?.name }
                        HistoryRow(
                            t.id,
                            if (outgoing) HistoryRow.Label.TransferOut(otherName) else HistoryRow.Label.TransferIn(otherName),
                            "account_balance", account?.color ?: DEFAULT_COLOR, t.date, t.effectOn(accountId), t.note,
                        )
                    }
                    TransactionType.ADJUSTMENT -> HistoryRow(
                        t.id, HistoryRow.Label.Adjustment, "build", account?.color ?: DEFAULT_COLOR, t.date, t.effectOn(accountId), t.note,
                    )
                    TransactionType.DEBT -> HistoryRow(
                        t.id, HistoryRow.Label.Debt, "attach_money", account?.color ?: DEFAULT_COLOR, t.date, t.effectOn(accountId), t.note,
                    )
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailState())

    private val _adjust = MutableStateFlow<AdjustDialogState?>(null)
    val adjust: StateFlow<AdjustDialogState?> = _adjust.asStateFlow()

    fun openAdjust() {
        val balance = state.value.account?.balance ?: 0
        _adjust.value = AdjustDialogState(text = MoneyFormat.toInput(balance))
    }

    fun setAdjustText(text: String) = _adjust.update { it?.copy(text = text, error = false) }
    fun closeAdjust() {
        _adjust.value = null
    }

    fun applyAdjust() {
        val dialog = _adjust.value ?: return
        val actual = MoneyFormat.parse(dialog.text, allowNegative = true)
        if (actual == null) {
            _adjust.value = dialog.copy(error = true)
            return
        }
        viewModelScope.launch {
            transactions.adjustBalance(accountId, actual, clock.millis())
            _adjust.value = null
        }
    }

    private companion object {
        const val DEFAULT_COLOR = 0xFF9E9E9E
    }
}
