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

    @Query("SELECT tagId FROM transaction_tags WHERE transactionId = :transactionId ORDER BY tagId")
    abstract suspend fun tagIdsOf(transactionId: Long): List<Long>

    @Insert
    protected abstract suspend fun insertTags(tags: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :transactionId")
    protected abstract suspend fun clearTags(transactionId: Long)

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
