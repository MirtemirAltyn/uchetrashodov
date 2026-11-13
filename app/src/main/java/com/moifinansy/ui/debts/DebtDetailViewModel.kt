package com.moifinansy.ui.debts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.AmountCalculator
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.debt.DebtMath
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.DebtPayment
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.DebtRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class DebtDetailState(
    val loading: Boolean = true,
    val debt: DebtWithRemaining? = null,
    val contact: Contact? = null,
    val payments: List<DebtPayment> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val overdue: Boolean = false,
)

data class PaymentPanel(
    val kind: DebtPaymentKind,
    val amountText: String = "",
    val accountId: Long? = null,
    val date: LocalDate,
    val amountError: Boolean = false,
    val tooMuch: Boolean = false,
    val accountError: Boolean = false,
)

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DebtDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debts: DebtRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
    private val phoneContacts: com.moifinansy.data.contacts.PhoneContacts,
) : ViewModel() {
    suspend fun inPhoneContacts(phone: String?): Boolean? = phoneContacts.exists(phone)

    private val debtId: Long = checkNotNull(savedStateHandle.get<Long>("id"))

    val state: StateFlow<DebtDetailState> = debts.observe(debtId)
        .flatMapLatest { debt ->
            if (debt == null) {
                flowOf(DebtDetailState(loading = false))
            } else {
                combine(debts.observeContact(debt.debt.contactId), debts.observePayments(debtId), accounts.observeAccounts(includeArchived = true)) {
                        contact, payments, accountList ->
                    DebtDetailState(
                        loading = false,
                        debt = debt,
                        contact = contact,
                        payments = payments.sortedByDescending { it.date },
                        accounts = accountList.map { it.account },
                        overdue = DebtMath.isOverdue(debt.debt.status, debt.debt.dueDate, clock.millis()),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtDetailState())

    private val _panel = MutableStateFlow<PaymentPanel?>(null)
    val panel: StateFlow<PaymentPanel?> = _panel.asStateFlow()

    /** Панель «провести долг по счёту» при включении влияния на баланс; значение — выбранный счёт. */
    private val _affectsPanel = MutableStateFlow<Long?>(null)
    val affectsPanel: StateFlow<Long?> = _affectsPanel.asStateFlow()
    private val _affectsPanelOpen = MutableStateFlow(false)
    val affectsPanelOpen: StateFlow<Boolean> = _affectsPanelOpen.asStateFlow()

    fun openAffectsPanel() {
        val s = state.value
        _affectsPanel.value = accountsForDebt(s).let { list -> list.firstOrNull { it.id == s.debt?.initialAccountId } ?: list.firstOrNull() }?.id
        _affectsPanelOpen.value = true
    }

    fun closeAffectsPanel() {
        _affectsPanelOpen.value = false
    }

    fun setAffectsAccount(id: Long) {
        _affectsPanel.value = id
    }

    fun enableAffects() {
        val account = _affectsPanel.value ?: return
        viewModelScope.launch {
            debts.setAffectsAccounts(debtId, true, account)
            _affectsPanelOpen.value = false
        }
    }

    fun disableAffects() {
        viewModelScope.launch { debts.setAffectsAccounts(debtId, false, null) }
    }

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    /** Счета в валюте долга, не в архиве — для движения. */
    fun accountsForDebt(s: DebtDetailState): List<Account> =
        s.accounts.filter { !it.isArchived && it.currencyCode == s.debt?.debt?.currencyCode }

    fun open(kind: DebtPaymentKind) {
        val s = state.value
        val debt = s.debt ?: return
        val candidates = accountsForDebt(s)
        _panel.value = PaymentPanel(
            kind = kind,
            amountText = if (kind == DebtPaymentKind.FORGIVE) MoneyFormat.toInput(debt.remaining) else "",
            accountId = candidates.firstOrNull { it.id == debt.initialAccountId }?.id ?: candidates.firstOrNull()?.id,
            date = LocalDate.now(clock),
        )
    }

    fun close() {
        _panel.value = null
    }

    fun setAmount(text: String) = _panel.update { it?.copy(amountText = text, amountError = false, tooMuch = false) }
    fun setAccount(id: Long) = _panel.update { it?.copy(accountId = id, accountError = false) }
    fun setDate(date: LocalDate) = _panel.update { it?.copy(date = date) }

    fun apply() {
        val p = _panel.value ?: return
        val debt = state.value.debt ?: return
        val amount = AmountCalculator.evaluate(p.amountText)
        val amountOk = amount != null && amount > 0
        val reduces = p.kind == DebtPaymentKind.REPAY || p.kind == DebtPaymentKind.FORGIVE
        val tooMuch = amountOk && reduces && amount!! > debt.remaining
        val needsAccount = debt.debt.affectsAccounts && p.kind != DebtPaymentKind.FORGIVE
        val accountOk = !needsAccount || p.accountId != null
        if (!amountOk || tooMuch || !accountOk) {
            _panel.value = p.copy(amountError = !amountOk, tooMuch = tooMuch, accountError = !accountOk)
            return
        }
        val date = p.date.atTime(LocalTime.now(clock)).atZone(clock.zone).toInstant().toEpochMilli()
        viewModelScope.launch {
            debts.addPayment(debtId, p.kind, amount!!, if (needsAccount) p.accountId else null, date)
            _panel.value = null
        }
    }

    fun delete() {
        viewModelScope.launch {
            debts.deleteDebt(debtId)
            _deleted.value = true
        }
    }

    suspend fun debtForShare(): DebtWithRemaining? = debts.get(debtId)
    suspend fun contactName(): String = state.value.contact?.name ?: debts.observeContact(state.value.debt?.debt?.contactId ?: 0).first()?.name.orEmpty()
}
