package com.moifinansy.data.repository

import com.moifinansy.data.local.dao.DebtDao
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.ContactBalance
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtPayment
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtTotals
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.repository.DebtRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class DebtRepositoryImpl @Inject constructor(
    private val dao: DebtDao,
    private val clock: Clock,
) : DebtRepository {

    override fun observeContacts(): Flow<List<Contact>> = dao.observeContacts().map { rows -> rows.map { it.toModel() } }

    override suspend fun createContact(contact: Contact): Long {
        require(contact.name.isNotBlank()) { "Введите имя" }
        val now = clock.millis()
        return dao.insertContact(contact.toEntity(now, now))
    }

    override suspend fun updateContact(contact: Contact) {
        require(contact.name.isNotBlank()) { "Введите имя" }
        val old = requireNotNull(dao.getContact(contact.id)) { "Контакт ${contact.id} не найден" }
        dao.updateContact(contact.toEntity(old.createdAt, clock.millis()).copy(isDemo = old.isDemo))
    }

    override fun observeDebts(): Flow<List<DebtWithRemaining>> = dao.observeWithRemaining().map { rows -> rows.map { it.toModel() } }

    override fun observePayments(debtId: Long): Flow<List<DebtPayment>> =
        dao.observePayments(debtId).map { rows -> rows.map { it.toModel() } }

    override fun observeTotals(currencyCode: String): Flow<DebtTotals> =
        dao.observeTotals(currencyCode).map { DebtTotals(iOwe = it.iOwe, owedToMe = it.owedToMe) }

    override fun observeContactBalances(): Flow<List<ContactBalance>> =
        dao.observeContactBalances().map { rows -> rows.map { it.toModel() } }

    override suspend fun get(id: Long): DebtWithRemaining? = dao.getWithRemaining(id)?.toModel()
    override fun observe(id: Long): Flow<DebtWithRemaining?> = dao.observeWithRemaining(id).map { it?.toModel() }
    override suspend fun getContact(id: Long): Contact? = dao.getContact(id)?.toModel()
    override fun observeContact(id: Long): Flow<Contact?> = dao.observeContact(id).map { it?.toModel() }
    override suspend fun deleteDebt(id: Long) = dao.deleteDebt(id)
    override suspend fun setAffectsAccounts(debtId: Long, affects: Boolean, accountId: Long?) =
        dao.setAffectsAccounts(debtId, affects, accountId, clock.millis())
    override suspend fun deleteContact(id: Long): Boolean = dao.deleteContactIfUnused(id) > 0

    override suspend fun createDebt(debt: Debt, accountId: Long?): Long {
        require(debt.currencyCode.length == 3) { "Код валюты — три буквы" }
        val now = clock.millis()
        return dao.createDebt(debt.toEntity(now, now), accountId, now)
    }

    override suspend fun addPayment(
        debtId: Long,
        kind: DebtPaymentKind,
        amount: Long,
        accountId: Long?,
        date: Long,
        note: String?,
    ): Long = dao.addPayment(debtId, kind, amount, accountId, date, note, clock.millis())
}
