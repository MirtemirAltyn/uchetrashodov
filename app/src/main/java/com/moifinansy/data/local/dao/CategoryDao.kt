package com.moifinansy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.moifinansy.data.local.entity.CategoryEntity
import com.moifinansy.domain.model.CategoryKind
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CategoryDao {
    @Insert
    abstract suspend fun insert(category: CategoryEntity): Long

    @Insert
    abstract suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    abstract suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    abstract suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE systemKey = :key")
    abstract suspend fun getBySystemKey(key: String): CategoryEntity?

    @Query(
        "SELECT * FROM categories WHERE kind = :kind AND (:includeArchived OR isArchived = 0) " +
            "ORDER BY sortOrder, id",
    )
    abstract fun observeByKind(kind: CategoryKind, includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    abstract suspend fun count(): Int

    @Query("UPDATE categories SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    abstract suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :id")
    protected abstract suspend fun countChildren(id: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :id")
    abstract suspend fun countTransactions(id: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE categoryId = :id")
    protected abstract suspend fun countRecurring(id: Long): Int

    @Query("UPDATE transactions SET categoryId = :to, updatedAt = :now WHERE categoryId = :from")
    protected abstract suspend fun moveTransactions(from: Long, to: Long, now: Long)

    @Query("UPDATE recurring_rules SET categoryId = :to, updatedAt = :now WHERE categoryId = :from")
    protected abstract suspend fun moveRecurring(from: Long, to: Long, now: Long)

    @Query("DELETE FROM categories WHERE id = :id")
    protected abstract suspend fun deleteById(id: Long)

    /**
     * Удаляет категорию. Операции и регулярные платежи переносятся в [moveToId] (той же кухни: расход/доход).
     * Если операций нет, [moveToId] может быть null. Категорию с подкатегориями удалить нельзя.
     */
    @Transaction
    open suspend fun deleteMovingTransactions(id: Long, moveToId: Long?, now: Long) {
        val category = requireNotNull(getById(id)) { "Категория $id не найдена" }
        require(category.systemKey == null) { "Предустановленную служебную категорию удалить нельзя" }
        require(countChildren(id) == 0) { "Сначала удалите или перенесите подкатегории" }
        val inUse = countTransactions(id) + countRecurring(id) > 0
        if (inUse) {
            requireNotNull(moveToId) { "У категории есть операции: выберите, куда их перенести" }
            val target = requireNotNull(getById(moveToId)) { "Категория $moveToId не найдена" }
            require(target.id != id) { "Нельзя перенести операции в удаляемую категорию" }
            require(target.kind == category.kind) { "Категории разного типа" }
            moveTransactions(id, moveToId, now)
            moveRecurring(id, moveToId, now)
        }
        deleteById(id)
    }
}
