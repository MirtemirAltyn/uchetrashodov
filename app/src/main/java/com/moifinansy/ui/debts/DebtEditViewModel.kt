package com.moifinansy.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.AmountCalculator
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.DebtRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class DebtEditState(
    val loading: Boolean = true,
    val contacts: List<Contact> = emptyList(),
    val contactId: Long? = null,
    /** Режим нового контакта (имя и телефон вводятся здесь же). */
    val newContact: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val direction: DebtDirection = DebtDirection.OWED_TO_ME,
    val amountText: String = "",
    val currencyCode: String = Currencies.DEFAULT,
    val allAccounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val startDate: LocalDate = LocalDate.now(),
    val dueDate: LocalDate? = null,
    val note: String = "",
    val affectsAccounts: Boolean = true,
    val contactError: Boolean = false,
    val amountError: Boolean = false,
    val accountError: Boolean = false,
    val savedId: Long? = null,
) {
    val accounts: List<Account> get() = allAccounts.filter { it.currencyCode == currencyCode }
}

@HiltViewModel
class DebtEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debts: DebtRepository,
    private val accountsRepo: AccountRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val argContactId: Long? = savedStateHandle.get<Long>("contactId")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(DebtEditState())
    val state: StateFlow<DebtEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val contacts = debts.observeContacts().first()
            val base = settings.baseCurrency.first() ?: Currencies.DEFAULT
            val accounts = accountsRepo.observeAccounts().first().map { it.account }
            val last = settings.lastAccountId.first()
            val inCurrency = accounts.filter { it.currencyCode == base }
            _state.value = DebtEditState(
                loading = false,
                contacts = contacts,
                contactId = argContactId,
                newContact = contacts.isEmpty(),
                currencyCode = base,
                allAccounts = accounts,
                accountId = inCurrency.firstOrNull { it.id == last }?.id ?: inCurrency.firstOrNull()?.id,
                startDate = LocalDate.now(clock),
            )
        }
    }

    fun selectContact(id: Long) = _state.update { it.copy(contactId = id, newContact = false, contactError = false) }
    fun startNewContact() = _state.update { it.copy(newContact = true, contactId = null) }
    fun setContactName(text: String) = _state.update { it.copy(contactName = text, contactError = false) }
    fun setContactPhone(text: String) = _state.update { it.copy(contactPhone = text) }

    /** Контакт из телефонной книги: если такой уже есть — выбираем его. */
    fun picked(name: String, phone: String?) {
        val digits = phone?.filter { it.isDigit() }
        val existing = _state.value.contacts.firstOrNull { c ->
            (digits != null && digits.isNotEmpty() && c.phone?.filter { it.isDigit() } == digits) || c.name.equals(name, ignoreCase = true)
        }
        if (existing != null) selectContact(existing.id) else _state.update {
            it.copy(newContact = true, contactId = null, contactName = name, contactPhone = phone.orEmpty(), contactError = false)
        }
    }

    fun setDirection(d: DebtDirection) = _state.update { it.copy(direction = d) }
    fun setAmount(text: String) = _state.update { it.copy(amountText = text, amountError = false) }
    fun setCurrency(code: String) = _state.update { s ->
        val first = s.allAccounts.firstOrNull { it.currencyCode == code }?.id
        s.copy(currencyCode = code, accountId = first, accountError = false)
    }
    fun setAccount(id: Long) = _state.update { it.copy(accountId = id, accountError = false) }
    fun setStartDate(date: LocalDate) = _state.update { it.copy(startDate = date) }
    fun setDueDate(date: LocalDate?) = _state.update { it.copy(dueDate = date) }
    fun setNote(text: String) = _state.update { it.copy(note = text) }
    fun setAffectsAccounts(value: Boolean) = _state.update { it.copy(affectsAccounts = value, accountError = false) }

    fun save() {
        val s = _state.value
        val amount = AmountCalculator.evaluate(s.amountText)
        val contactOk = if (s.newContact) s.contactName.isNotBlank() else s.contactId != null
        val amountOk = amount != null && amount > 0
        val accountOk = !s.affectsAccounts || (s.accountId != null && s.accounts.any { it.id == s.accountId })
        if (!contactOk || !amountOk || !accountOk) {
            _state.update { it.copy(contactError = !contactOk, amountError = !amountOk, accountError = !accountOk) }
            return
        }
        viewModelScope.launch {
            val contactId = if (s.newContact) {
                debts.createContact(Contact(name = s.contactName.trim(), phone = s.contactPhone.trim().ifEmpty { null }))
            } else {
                s.contactId!!
            }
            val time = LocalTime.now(clock)
            val id = debts.createDebt(
                Debt(
                    contactId = contactId,
                    direction = s.direction,
                    principal = amount!!,
                    currencyCode = s.currencyCode,
                    startDate = s.startDate.atTime(time).atZone(clock.zone).toInstant().toEpochMilli(),
                    // Срок — конец выбранного дня.
                    dueDate = s.dueDate?.plusDays(1)?.atStartOfDay(clock.zone)?.toInstant()?.toEpochMilli()?.minus(1),
                    note = s.note.trim().ifEmpty { null },
                    affectsAccounts = s.affectsAccounts,
                ),
                if (s.affectsAccounts) s.accountId else null,
            )
            _state.update { it.copy(savedId = id) }
        }
    }

    fun today(): LocalDate = LocalDate.now(clock)
}
