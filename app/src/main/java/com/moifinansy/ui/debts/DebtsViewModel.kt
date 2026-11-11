package com.moifinansy.ui.debts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.debt.DebtMath
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.ContactBalance
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.DebtTotals
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.DebtRepository
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

enum class DebtSort { DATE, AMOUNT, DUE, OPEN_FIRST }

data class DebtsQuery(
    val direction: DebtDirection = DebtDirection.OWED_TO_ME,
    val showClosed: Boolean = false,
    val sort: DebtSort = DebtSort.DUE,
    val byPerson: Boolean = false,
)

data class DebtCard(val debt: DebtWithRemaining, val contactName: String, val overdue: Boolean)

data class PersonRow(val contact: Contact, val balances: List<ContactBalance>)

data class DebtsState(
    val loading: Boolean = true,
    val query: DebtsQuery = DebtsQuery(),
    val baseCurrency: String = Currencies.DEFAULT,
    val totals: DebtTotals = DebtTotals(0, 0),
    val openCount: Int = 0,
    val cards: List<DebtCard> = emptyList(),
    val persons: List<PersonRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DebtsViewModel @Inject constructor(
    private val debts: DebtRepository,
    private val accounts: AccountRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow(DebtsQuery())

    val state: StateFlow<DebtsState> = settings.baseCurrency.map { it ?: Currencies.DEFAULT }
        .flatMapLatest { base ->
            combine(query, debts.observeDebts(), debts.observeContacts(), debts.observeTotals(base), debts.observeContactBalances()) {
                    q, list, contacts, totals, balances ->
                build(q, base, list, contacts, totals, balances)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebtsState())

    private fun build(
        q: DebtsQuery,
        base: String,
        list: List<DebtWithRemaining>,
        contacts: List<Contact>,
        totals: DebtTotals,
        balances: List<ContactBalance>,
    ): DebtsState {
        val now = clock.millis()
        val names = contacts.associate { it.id to it.name }
        val filtered = list
            .filter { it.debt.direction == q.direction }
            .filter { if (q.showClosed) true else it.debt.status == DebtStatus.ACTIVE }
            .map { DebtCard(it, names[it.debt.contactId].orEmpty(), DebtMath.isOverdue(it.debt.status, it.debt.dueDate, now)) }
        val sorted = when (q.sort) {
            DebtSort.DATE -> filtered.sortedByDescending { it.debt.debt.startDate }
            DebtSort.AMOUNT -> filtered.sortedByDescending { it.debt.remaining }
            DebtSort.DUE -> filtered.sortedWith(compareBy({ it.debt.debt.dueDate == null }, { it.debt.debt.dueDate }))
            DebtSort.OPEN_FIRST -> filtered.sortedWith(compareBy({ it.debt.debt.status != DebtStatus.ACTIVE }, { -it.debt.debt.startDate }))
        }
        val byContact = balances.groupBy { it.contactId }
        val persons = contacts.filter { byContact.containsKey(it.id) }
            .map { PersonRow(it, byContact[it.id].orEmpty()) }
            .sortedByDescending { row -> row.balances.sumOf { kotlin.math.abs(it.saldo) } }
        return DebtsState(
            loading = false,
            query = q,
            baseCurrency = base,
            totals = totals,
            openCount = list.count { it.debt.status == DebtStatus.ACTIVE },
            cards = sorted,
            persons = persons,
        )
    }

    fun setDirection(direction: DebtDirection) = query.update { it.copy(direction = direction) }
    fun setSort(sort: DebtSort) = query.update { it.copy(sort = sort, byPerson = false) }
    fun toggleClosed() = query.update { it.copy(showClosed = !it.showClosed) }
    fun toggleByPerson() = query.update { it.copy(byPerson = !it.byPerson) }

    /**
     * Свайп вправо: погасить остаток целиком на счёт первого движения.
     * false — счёт не определить (тогда открываем карточку долга).
     */
    suspend fun repayAll(card: DebtCard): Boolean {
        val debt = card.debt
        val accountId = if (debt.debt.affectsAccounts) {
            debt.initialAccountId?.takeIf { accounts.get(it)?.isArchived == false }
                ?: accounts.observeAccounts().first().firstOrNull { it.account.currencyCode == debt.debt.currencyCode }?.account?.id
                ?: return false
        } else {
            null
        }
        debts.addPayment(debt.debt.id, DebtPaymentKind.REPAY, debt.remaining, accountId, clock.millis())
        return true
    }

    fun repayAllAsync(card: DebtCard, onNeedsCard: () -> Unit) {
        viewModelScope.launch { if (!repayAll(card)) onNeedsCard() }
    }
}
