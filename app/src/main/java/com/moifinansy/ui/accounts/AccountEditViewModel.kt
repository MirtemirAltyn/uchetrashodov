package com.moifinansy.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.domain.usecase.AccountDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.CARD,
    val currencyCode: String = Currencies.DEFAULT,
    val balanceText: String = "",
    val color: Long = AccountDefaults.color,
    val icon: String = AccountDefaults.icon(AccountType.CARD),
    val includeInTotal: Boolean = true,
    val isArchived: Boolean = false,
    /** Сколько операций ссылается на счёт: при > 0 валюту не поменять и счёт не удалить. */
    val usageCount: Int = 0,
    val nameError: Boolean = false,
    val balanceError: Boolean = false,
    val showDeleteDialog: Boolean = false,
    /** Сохранено или удалено — экран закрывается. */
    val done: Boolean = false,
) {
    val currencyLocked: Boolean get() = usageCount > 0
}

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val accountId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var original: Account? = null

    private val _state = MutableStateFlow(AccountEditState())
    val state: StateFlow<AccountEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val account = accountId?.let { accounts.get(it) }
            original = account
            _state.value = if (account == null) {
                AccountEditState(loading = false, currencyCode = settings.baseCurrency.first() ?: Currencies.DEFAULT)
            } else {
                AccountEditState(
                    loading = false,
                    isNew = false,
                    name = account.name,
                    type = account.type,
                    currencyCode = account.currencyCode,
                    balanceText = MoneyFormat.toInput(account.initialBalance),
                    color = account.color,
                    icon = account.icon,
                    includeInTotal = account.includeInTotal,
                    isArchived = account.isArchived,
                    usageCount = accounts.usageCount(account.id),
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = false) }

    /** Смена типа меняет иконку, если пользователь её не выбирал сам. */
    fun setType(value: AccountType) = _state.update {
        val iconWasDefault = it.icon == AccountDefaults.icon(it.type)
        it.copy(type = value, icon = if (iconWasDefault) AccountDefaults.icon(value) else it.icon)
    }

    fun setCurrency(code: String) = _state.update { if (it.currencyLocked) it else it.copy(currencyCode = code) }
    fun setBalance(text: String) = _state.update { it.copy(balanceText = text, balanceError = false) }
    fun setColor(color: Long) = _state.update { it.copy(color = color) }
    fun setIcon(icon: String) = _state.update { it.copy(icon = icon) }
    fun setIncludeInTotal(value: Boolean) = _state.update { it.copy(includeInTotal = value) }

    fun save() {
        val s = _state.value
        val balance = if (s.balanceText.isBlank()) 0L else MoneyFormat.parse(s.balanceText, allowNegative = true)
        if (s.name.isBlank() || balance == null) {
            _state.update { it.copy(nameError = s.name.isBlank(), balanceError = balance == null) }
            return
        }
        val account = Account(
            id = accountId ?: 0,
            name = s.name.trim(),
            type = s.type,
            currencyCode = s.currencyCode,
            initialBalance = balance,
            color = s.color,
            icon = s.icon,
            includeInTotal = s.includeInTotal,
            isArchived = s.isArchived,
            sortOrder = original?.sortOrder ?: 0,
        )
        viewModelScope.launch {
            if (accountId == null) accounts.create(account) else accounts.update(account)
            _state.update { it.copy(done = true) }
        }
    }

    fun toggleArchived() {
        val id = accountId ?: return
        val archived = !_state.value.isArchived
        viewModelScope.launch {
            accounts.setArchived(id, archived)
            _state.update { it.copy(isArchived = archived, done = true) }
        }
    }

    /** «В архив» вместо удаления счёта с операциями. */
    fun archiveInstead() {
        val id = accountId ?: return
        viewModelScope.launch {
            accounts.setArchived(id, true)
            _state.update { it.copy(isArchived = true, showDeleteDialog = false, done = true) }
        }
    }

    fun askDelete() = _state.update { it.copy(showDeleteDialog = true) }
    fun dismissDelete() = _state.update { it.copy(showDeleteDialog = false) }

    fun delete() {
        val id = accountId ?: return
        if (_state.value.usageCount > 0) return
        viewModelScope.launch {
            accounts.delete(id)
            _state.update { it.copy(showDeleteDialog = false, done = true) }
        }
    }
}
