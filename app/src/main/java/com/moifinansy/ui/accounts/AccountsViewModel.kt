package com.moifinansy.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountsState(
    val loading: Boolean = true,
    val baseCurrency: String = Currencies.DEFAULT,
    val total: Long = 0,
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountsViewModel @Inject constructor(
    accounts: AccountRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<AccountsState> = settings.baseCurrency
        .map { it ?: Currencies.DEFAULT }
        .flatMapLatest { base ->
            combine(accounts.observeAccounts(includeArchived = true), accounts.observeTotal(base)) { list, total ->
                AccountsState(
                    loading = false,
                    baseCurrency = base,
                    total = total,
                    active = list.filterNot { it.account.isArchived },
                    archived = list.filter { it.account.isArchived },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())
}
