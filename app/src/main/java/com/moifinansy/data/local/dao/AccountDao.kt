package com.moifinansy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.moifinansy.data.local.entity.AccountEntity
import com.moifinansy.data.local.entity.AccountWithBalanceRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query(
        "SELECT (SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id)" +
            " + (SELECT COUNT(*) FROM debt_payments WHERE accountId = :id)" +
            " + (SELECT COUNT(*) FROM recurring_rules WHERE accountId = :id OR toAccountId = :id)",
    )
    suspend fun countUsages(id: Long): Int

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE accounts SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query(
        "SELECT a.*, $BALANCE_SQL AS balance FROM accounts a " +
            "WHERE :includeArchived OR a.isArchived = 0 ORDER BY a.sortOrder, a.id",
    )
    fun observeWithBalance(includeArchived: Boolean): Flow<List<AccountWithBalanceRow>>

    @Query("SELECT $BALANCE_SQL FROM accounts a WHERE a.id = :accountId")
    suspend fun balanceOf(accountId: Long): Long?

    /** Общий баланс в основной валюте: счета в других валютах — по курсу, без курса не учитываются. */
    @Query(
        "SELECT COALESCE(SUM($BALANCE_SQL * (CASE WHEN a.currencyCode = :currencyCode THEN 1000000 " +
            "ELSE COALESCE((SELECT r.rateToBaseMicros FROM currency_rates r WHERE r.code = a.currencyCode), 0) END) / 1000000), 0) " +
            "FROM accounts a WHERE a.includeInTotal = 1 AND a.isArchived = 0",
    )
    fun observeTotal(currencyCode: String): Flow<Long>

    /** Валюты счетов (не в архиве), для которых нет курса к основной. */
    @Query(
        "SELECT DISTINCT a.currencyCode FROM accounts a WHERE a.isArchived = 0 AND a.currencyCode != :base " +
            "AND NOT EXISTS (SELECT 1 FROM currency_rates r WHERE r.code = a.currencyCode)",
    )
    fun observeCurrenciesWithoutRate(base: String): Flow<List<String>>

    @Query("SELECT DISTINCT currencyCode FROM accounts WHERE isArchived = 0 UNION SELECT DISTINCT currencyCode FROM debts")
    fun observeUsedCurrencies(): Flow<List<String>>

    companion object {
        /**
         * Баланс счёта `a` = начальный баланс + операции по счёту + входящие переводы.
         * Баланс нигде не хранится, только считается.
         */
        const val BALANCE_SQL =
            "(a.initialBalance" +
                " + COALESCE((SELECT SUM(CASE t.type" +
                " WHEN 'INCOME' THEN t.amount" +
                " WHEN 'EXPENSE' THEN -t.amount" +
                " WHEN 'TRANSFER' THEN -(t.amount + t.fee)" +
                " ELSE t.amount END)" + // ADJUSTMENT и DEBT хранятся со знаком
                " FROM transactions t WHERE t.accountId = a.id), 0)" +
                " + COALESCE((SELECT SUM(COALESCE(t.toAmount, t.amount))" +
                " FROM transactions t WHERE t.type = 'TRANSFER' AND t.toAccountId = a.id), 0))"
    }
}
