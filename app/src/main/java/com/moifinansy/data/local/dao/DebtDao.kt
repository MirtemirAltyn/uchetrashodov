package com.moifinansy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.moifinansy.data.local.entity.ContactBalanceRow
import com.moifinansy.data.local.entity.ContactEntity
import com.moifinansy.data.local.entity.DebtEntity
import com.moifinansy.data.local.entity.DebtPaymentEntity
import com.moifinansy.data.local.entity.DebtTotalsRow
import com.moifinansy.data.local.entity.DebtWithRemainingRow
import com.moifinansy.data.local.entity.TransactionEntity
import com.moifinansy.domain.debt.DebtMath
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DebtDao {
    // Контакты

    @Insert
    abstract suspend fun insertContact(contact: ContactEntity): Long

    @Update
    abstract suspend fun updateContact(contact: ContactEntity)

    @Query("SELECT createdAt FROM contacts WHERE id = :id")
    abstract suspend fun contactCreatedAt(id: Long): Long?

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE")
    abstract fun observeContacts(): Flow<List<ContactEntity>>

    // Долги

    @Insert
    protected abstract suspend fun insertDebt(debt: DebtEntity): Long

    @Query("SELECT * FROM debts WHERE id = :id")
    abstract suspend fun getDebt(id: Long): DebtEntity?

    @Query("UPDATE debts SET status = :status, updatedAt = :now WHERE id = :id")
    protected abstract suspend fun setStatus(id: Long, status: DebtStatus, now: Long)

    @Query("SELECT d.*, $REMAINING_SQL AS remaining FROM debts d ORDER BY d.status, d.dueDate IS NULL, d.dueDate, d.id")
    abstract fun observeWithRemaining(): Flow<List<DebtWithRemainingRow>>

    @Query("SELECT d.*, $REMAINING_SQL AS remaining FROM debts d WHERE d.id = :id")
    abstract suspend fun getWithRemaining(id: Long): DebtWithRemainingRow?

    @Query("SELECT COALESCE(SUM(CASE WHEN kind IN ('INITIAL', 'INCREASE') THEN amount ELSE -amount END), 0) FROM debt_payments WHERE debtId = :debtId")
    abstract suspend fun remaining(debtId: Long): Long

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN d.direction = 'I_OWE' THEN $REMAINING_SQL ELSE 0 END), 0) AS iOwe, " +
            "COALESCE(SUM(CASE WHEN d.direction = 'OWED_TO_ME' THEN $REMAINING_SQL ELSE 0 END), 0) AS owedToMe " +
            "FROM debts d WHERE d.status = 'ACTIVE' AND d.currencyCode = :currencyCode",
    )
    abstract fun observeTotals(currencyCode: String): Flow<DebtTotalsRow>

    /** Взаимные долги одного контакта сводятся в один итог (по каждой валюте). */
    @Query(
        "SELECT d.contactId AS contactId, d.currencyCode AS currencyCode, " +
            "SUM(CASE WHEN d.direction = 'OWED_TO_ME' THEN $REMAINING_SQL ELSE -$REMAINING_SQL END) AS saldo " +
            "FROM debts d WHERE d.status = 'ACTIVE' GROUP BY d.contactId, d.currencyCode",
    )
    abstract fun observeContactBalances(): Flow<List<ContactBalanceRow>>

    // Движения

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY date, id")
    abstract fun observePayments(debtId: Long): Flow<List<DebtPaymentEntity>>

    @Insert
    protected abstract suspend fun insertPayment(payment: DebtPaymentEntity): Long

    @Insert
    protected abstract suspend fun insertTransaction(transaction: TransactionEntity): Long

    /** Долг и первое движение (INITIAL) — одной транзакцией БД. */
    @Transaction
    open suspend fun createDebt(debt: DebtEntity, accountId: Long?, now: Long): Long {
        require(debt.id == 0L) { "Новый долг не должен иметь id" }
        require(debt.principal > 0) { "Сумма долга должна быть больше нуля" }
        val debtId = insertDebt(debt.copy(status = DebtStatus.ACTIVE))
        recordPayment(debtId, DebtPaymentKind.INITIAL, debt.principal, accountId, debt.startDate, debt.note, now)
        return debtId
    }

    /** Погашение, дозайм или прощение: движение, связанная операция по счёту и новый статус — одной транзакцией БД. */
    @Transaction
    open suspend fun addPayment(
        debtId: Long,
        kind: DebtPaymentKind,
        amount: Long,
        accountId: Long?,
        date: Long,
        note: String?,
        now: Long,
    ): Long {
        require(kind != DebtPaymentKind.INITIAL) { "Первое движение создаётся вместе с долгом" }
        return recordPayment(debtId, kind, amount, accountId, date, note, now)
    }

    private suspend fun recordPayment(
        debtId: Long,
        kind: DebtPaymentKind,
        amount: Long,
        accountId: Long?,
        date: Long,
        note: String?,
        now: Long,
    ): Long {
        val debt = requireNotNull(getDebt(debtId)) { "Долг $debtId не найден" }
        require(amount > 0) { "Сумма должна быть больше нуля" }
        if (kind == DebtPaymentKind.REPAY || kind == DebtPaymentKind.FORGIVE) {
            require(amount <= remaining(debtId)) { "Сумма больше остатка долга" }
        }

        val movesMoney = debt.affectsAccounts && kind != DebtPaymentKind.FORGIVE
        val paymentAccountId = if (movesMoney) requireNotNull(accountId) { "Укажите счёт" } else null
        val transactionId = paymentAccountId?.let {
            insertTransaction(
                TransactionEntity(
                    type = TransactionType.DEBT,
                    amount = DebtMath.accountEffect(debt.direction, kind, amount),
                    accountId = it,
                    categoryId = null,
                    toAccountId = null,
                    toAmount = null,
                    fee = 0,
                    date = date,
                    note = note,
                    debtId = debtId,
                    recurringId = null,
                    receiptUri = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        val paymentId = insertPayment(
            DebtPaymentEntity(
                debtId = debtId,
                kind = kind,
                amount = amount,
                accountId = paymentAccountId,
                date = date,
                transactionId = transactionId,
                note = note,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val status = DebtMath.statusAfter(remaining(debtId), kind)
        if (status != debt.status) setStatus(debtId, status, now)
        return paymentId
    }

    companion object {
        /** Остаток долга `d` = INITIAL + INCREASE − REPAY − FORGIVE. */
        const val REMAINING_SQL =
            "(SELECT COALESCE(SUM(CASE WHEN p.kind IN ('INITIAL', 'INCREASE') THEN p.amount ELSE -p.amount END), 0)" +
                " FROM debt_payments p WHERE p.debtId = d.id)"
    }
}
