package com.moifinansy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.moifinansy.data.local.entity.BudgetEntity
import com.moifinansy.data.local.entity.CurrencyRateEntity
import com.moifinansy.data.local.entity.RecurringRuleEntity
import com.moifinansy.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>
}

@Dao
interface BudgetDao {
    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM budgets ORDER BY categoryId IS NOT NULL, id")
    fun observeAll(): Flow<List<BudgetEntity>>
}

@Dao
interface RecurringRuleDao {
    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM recurring_rules ORDER BY nextDate, id")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE nextDate <= :date AND (endDate IS NULL OR nextDate <= endDate) ORDER BY nextDate, id")
    suspend fun dueBy(date: Long): List<RecurringRuleEntity>
}

@Dao
interface CurrencyRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: CurrencyRateEntity)

    @Query("SELECT * FROM currency_rates WHERE code = :code")
    suspend fun get(code: String): CurrencyRateEntity?

    @Query("DELETE FROM currency_rates WHERE code = :code")
    suspend fun delete(code: String)

    @Query("DELETE FROM currency_rates")
    suspend fun deleteAll()

    @Query("SELECT * FROM currency_rates ORDER BY code")
    fun observeAll(): Flow<List<CurrencyRateEntity>>
}
