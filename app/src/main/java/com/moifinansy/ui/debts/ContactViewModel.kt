package com.moifinansy.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.debt.DebtMath
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.ContactBalance
import com.moifinansy.domain.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class ContactState(
    val loading: Boolean = true,
    val contact: Contact? = null,
    val balances: List<ContactBalance> = emptyList(),
    val debts: List<DebtCard> = emptyList(),
)

@HiltViewModel
class ContactViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debts: DebtRepository,
    private val clock: Clock,
) : ViewModel() {
    private val contactId: Long = checkNotNull(savedStateHandle.get<Long>("id"))

    val state: StateFlow<ContactState> = combine(
        debts.observeContact(contactId),
        debts.observeContactBalances(),
        debts.observeDebts(),
    ) { contact, balances, list ->
        val now = clock.millis()
        ContactState(
            loading = false,
            contact = contact,
            balances = balances.filter { it.contactId == contactId },
            debts = list.filter { it.debt.contactId == contactId }
                .map { DebtCard(it, contact?.name.orEmpty(), DebtMath.isOverdue(it.debt.status, it.debt.dueDate, now)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactState())

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun save(name: String, phone: String) {
        val c = state.value.contact ?: return
        if (name.isBlank()) return
        viewModelScope.launch { debts.updateContact(c.copy(name = name.trim(), phone = phone.trim().ifEmpty { null })) }
    }

    fun delete() {
        viewModelScope.launch { if (debts.deleteContact(contactId)) _deleted.value = true }
    }
}
