package com.moifinansy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Демо-данные: сколько их и удаление одной транзакцией. */
@Dao
abstract class DemoDao {
    @Query("SELECT (SELECT COUNT(*) FROM accounts WHERE isDemo = 1) + (SELECT COUNT(*) FROM transactions WHERE isDemo = 1)")
    abstract fun observeDemoCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE isDemo = 0")
    abstract fun observeUserTransactionCount(): Flow<Int>

    @Query("DELETE FROM transactions WHERE isDemo = 1")
    protected abstract suspend fun deleteTransactions()

    /** Долг с операциями пользователя тоже удаляется: его операции привязаны к выдуманному долгу (каскад). */
    @Query("DELETE FROM debts WHERE isDemo = 1")
    protected abstract suspend fun deleteDebts()

    @Query("DELETE FROM budgets WHERE isDemo = 1")
    protected abstract suspend fun deleteBudgets()

    @Query("DELETE FROM recurring_rules WHERE isDemo = 1")
    protected abstract suspend fun deleteRules()

    /** Демо-счёт, на который пользователь записал свои операции, остаётся. */
    @Query(
        "DELETE FROM accounts WHERE isDemo = 1 " +
            "AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.accountId = accounts.id OR t.toAccountId = accounts.id) " +
            "AND NOT EXISTS (SELECT 1 FROM debt_payments p WHERE p.accountId = accounts.id) " +
            "AND NOT EXISTS (SELECT 1 FROM recurring_rules r WHERE r.accountId = accounts.id OR r.toAccountId = accounts.id)",
    )
    protected abstract suspend fun deleteAccounts()

    @Query("DELETE FROM contacts WHERE isDemo = 1 AND NOT EXISTS (SELECT 1 FROM debts d WHERE d.contactId = contacts.id)")
    protected abstract suspend fun deleteContacts()

    @Query("DELETE FROM tags WHERE isDemo = 1 AND NOT EXISTS (SELECT 1 FROM transaction_tags tt WHERE tt.tagId = tags.id)")
    protected abstract suspend fun deleteTags()

    @Query("SELECT COUNT(*) FROM accounts WHERE currencyCode = :code")
    abstract suspend fun accountsInCurrency(code: String): Int

    @Query("UPDATE accounts SET isDemo = 0 WHERE isDemo = 1")
    protected abstract suspend fun adoptAccounts()

    @Query("UPDATE contacts SET isDemo = 0 WHERE isDemo = 1")
    protected abstract suspend fun adoptContacts()

    @Query("UPDATE tags SET isDemo = 0 WHERE isDemo = 1")
    protected abstract suspend fun adoptTags()

    /** Удаляет демо-данные; оставшиеся (с записями пользователя) становятся обычными. */
    @Transaction
    open suspend fun deleteDemo() {
        deleteTransactions()
        deleteDebts()
        deleteBudgets()
        deleteRules()
        deleteAccounts()
        deleteContacts()
        deleteTags()
        adoptAccounts()
        adoptContacts()
        adoptTags()
    }
}
