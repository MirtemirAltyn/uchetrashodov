package com.moifinansy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.moifinansy.data.local.entity.CategoryTotalRow
import com.moifinansy.data.local.entity.TransactionEntity
import com.moifinansy.data.local.entity.TransactionTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDao {
    @Insert
    abstract suspend fun insert(transaction: TransactionEntity): Long

    @Update
    abstract suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    abstract suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE date >= :from AND date < :to ORDER BY date DESC, id DESC")
    abstract fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    /** Операции периода с фильтрами; null в фильтре — «любой». Категория учитывает и её подкатегории. */
    @Query(
        "SELECT * FROM transactions t WHERE t.date >= :from AND t.date < :to " +
            "AND (:type IS NULL OR t.type = :type) " +
            "AND (:accountId IS NULL OR t.accountId = :accountId OR t.toAccountId = :accountId) " +
            "AND (:categoryId IS NULL OR t.categoryId = :categoryId " +
            "OR t.categoryId IN (SELECT c.id FROM categories c WHERE c.parentId = :categoryId)) " +
            "AND (:tagId IS NULL OR t.id IN (SELECT tt.transactionId FROM transaction_tags tt WHERE tt.tagId = :tagId)) " +
            "AND (:minAmount IS NULL OR ABS(t.amount) >= :minAmount) " +
            "AND (:maxAmount IS NULL OR ABS(t.amount) <= :maxAmount) " +
            "ORDER BY t.date DESC, t.id DESC",
    )
    abstract fun observeFiltered(
        from: Long,
        to: Long,
        type: String?,
        accountId: Long?,
        categoryId: Long?,
        tagId: Long?,
        minAmount: Long?,
        maxAmount: Long?,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC LIMIT :limit")
    abstract fun observeLatest(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId ORDER BY date DESC, id DESC")
    abstract fun observeForAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT tagId FROM transaction_tags WHERE transactionId = :transactionId ORDER BY tagId")
    abstract suspend fun tagIdsOf(transactionId: Long): List<Long>

    @Insert
    protected abstract suspend fun insertTags(tags: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId")
    protected abstract suspend fun clearTags(transactionId: Long)

    @Query("UPDATE recurring_rules SET nextDate = :nextDate, updatedAt = :now WHERE id = :ruleId")
    protected abstract suspend fun setRuleNextDate(ruleId: Long, nextDate: Long, now: Long)

    /** Платёж по регулярному правилу: операция и новая дата правила — одной транзакцией БД. */
    @Transaction
    open suspend fun insertForRule(transaction: TransactionEntity, ruleId: Long, nextDate: Long, now: Long): Long {
        val id = insert(transaction)
        setRuleNextDate(ruleId, nextDate, now)
        return id
    }

    /** Операция и её теги пишутся одной транзакцией БД. */
    @Transaction
    open suspend fun upsertWithTags(transaction: TransactionEntity, tagIds: List<Long>, now: Long): Long {
        val id = if (transaction.id == 0L) {
            insert(transaction)
        } else {
            update(transaction)
            clearTags(transaction.id)
            transaction.id
        }
        if (tagIds.isNotEmpty()) {
            insertTags(tagIds.distinct().map { TransactionTagEntity(id, it, now, now) })
        }
        return id
    }

    // Отчёты: только EXPENSE и INCOME; комиссия перевода считается расходом в категории «Комиссии».
    // Долги, переводы и корректировки в отчёты не попадают.

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM (" +
            "SELECT amount FROM transactions WHERE type = 'EXPENSE' AND date >= :from AND date < :to " +
            "UNION ALL " +
            "SELECT fee AS amount FROM transactions WHERE type = 'TRANSFER' AND fee > 0 AND date >= :from AND date < :to)",
    )
    abstract suspend fun totalExpense(from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME' AND date >= :from AND date < :to")
    abstract suspend fun totalIncome(from: Long, to: Long): Long

    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM (" +
            "SELECT amount FROM transactions WHERE type = 'EXPENSE' AND date >= :from AND date < :to " +
            "UNION ALL " +
            "SELECT fee AS amount FROM transactions WHERE type = 'TRANSFER' AND fee > 0 AND date >= :from AND date < :to)",
    )
    abstract fun observeTotalExpense(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE type = 'INCOME' AND date >= :from AND date < :to")
    abstract fun observeTotalIncome(from: Long, to: Long): Flow<Long>

    @Query(
        "SELECT categoryId, SUM(amount) AS total FROM (" +
            "SELECT categoryId, amount FROM transactions WHERE type = 'EXPENSE' AND date >= :from AND date < :to " +
            "UNION ALL " +
            "SELECT (SELECT id FROM categories WHERE systemKey = 'COMMISSIONS') AS categoryId, fee AS amount " +
            "FROM transactions WHERE type = 'TRANSFER' AND fee > 0 AND date >= :from AND date < :to) " +
            "GROUP BY categoryId ORDER BY total DESC",
    )
    abstract fun observeExpenseByCategory(from: Long, to: Long): Flow<List<CategoryTotalRow>>

    @Query(
        "SELECT categoryId, SUM(amount) AS total FROM transactions " +
            "WHERE type = 'INCOME' AND date >= :from AND date < :to GROUP BY categoryId ORDER BY total DESC",
    )
    abstract fun observeIncomeByCategory(from: Long, to: Long): Flow<List<CategoryTotalRow>>

    /** Помесячные суммы категории вместе с подкатегориями. */
    @Query(
        "SELECT strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') AS month, SUM(amount) AS total " +
            "FROM transactions WHERE type IN ('EXPENSE', 'INCOME') AND date >= :from AND date < :to " +
            "AND (categoryId = :categoryId OR categoryId IN (SELECT id FROM categories WHERE parentId = :categoryId)) " +
            "GROUP BY month ORDER BY month",
    )
    abstract fun observeCategoryMonths(categoryId: Long, from: Long, to: Long): Flow<List<com.moifinansy.data.local.entity.MonthAmountRow>>

    /** Помесячные итоги; месяц — по местному времени устройства. */
    @Query(
        "SELECT strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') AS month, " +
            "COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income, " +
            "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount WHEN type = 'TRANSFER' THEN fee ELSE 0 END), 0) AS expense, " +
            "COALESCE(SUM(CASE type WHEN 'INCOME' THEN amount WHEN 'EXPENSE' THEN -amount WHEN 'TRANSFER' THEN -fee ELSE amount END), 0) AS net " +
            "FROM transactions WHERE date >= :from AND date < :to GROUP BY month ORDER BY month",
    )
    abstract fun observeMonthTotals(from: Long, to: Long): Flow<List<com.moifinansy.data.local.entity.MonthTotalsRow>>

    @Query(
        "SELECT categoryId, SUM(amount) AS total FROM (" +
            "SELECT categoryId, amount FROM transactions WHERE type = 'EXPENSE' AND date >= :from AND date < :to " +
            "UNION ALL " +
            "SELECT (SELECT id FROM categories WHERE systemKey = 'COMMISSIONS') AS categoryId, fee AS amount " +
            "FROM transactions WHERE type = 'TRANSFER' AND fee > 0 AND date >= :from AND date < :to) " +
            "GROUP BY categoryId ORDER BY total DESC",
    )
    abstract suspend fun expenseByCategory(from: Long, to: Long): List<CategoryTotalRow>

    @Query(
        "SELECT categoryId, SUM(amount) AS total FROM transactions " +
            "WHERE type = 'INCOME' AND date >= :from AND date < :to GROUP BY categoryId ORDER BY total DESC",
    )
    abstract suspend fun incomeByCategory(from: Long, to: Long): List<CategoryTotalRow>
}
