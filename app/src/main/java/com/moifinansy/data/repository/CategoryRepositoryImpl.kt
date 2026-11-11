package com.moifinansy.data.repository

import com.moifinansy.data.local.dao.CategoryDao
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
    private val clock: Clock,
) : CategoryRepository {

    override fun observe(kind: CategoryKind, includeArchived: Boolean): Flow<List<Category>> =
        dao.observeByKind(kind, includeArchived).map { rows -> rows.map { it.toModel() } }

    override suspend fun get(id: Long): Category? = dao.getById(id)?.toModel()

    override suspend fun create(category: Category): Long {
        require(category.id == 0L) { "Новая категория не должна иметь id" }
        validate(category)
        val now = clock.millis()
        return dao.insert(category.copy(systemKey = null).toEntity(now, now))
    }

    override suspend fun update(category: Category) {
        validate(category)
        val old = requireNotNull(dao.getById(category.id)) { "Категория ${category.id} не найдена" }
        require(old.kind == category.kind) { "Нельзя поменять тип категории" }
        // Служебный ключ задаёт только приложение.
        dao.update(category.copy(systemKey = old.systemKey).toEntity(old.createdAt, clock.millis()))
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived, clock.millis())

    override suspend fun delete(id: Long, moveToId: Long?) = dao.deleteMovingTransactions(id, moveToId, clock.millis())

    override suspend fun reorder(ids: List<Long>) = dao.reorder(ids, clock.millis())

    override suspend fun usageCount(id: Long): Int = dao.countTransactions(id) + dao.countRecurring(id)

    override suspend fun childrenCount(id: Long): Int = dao.countChildren(id)

    /** Два уровня: родитель должен быть категорией верхнего уровня того же типа. */
    private suspend fun validate(category: Category) {
        require(category.name.isNotBlank()) { "Введите название категории" }
        category.parentId?.let { parentId ->
            require(parentId != category.id) { "Категория не может быть своей подкатегорией" }
            val parent = requireNotNull(dao.getById(parentId)) { "Родительская категория не найдена" }
            require(parent.parentId == null) { "Допускается только два уровня категорий" }
            require(parent.kind == category.kind) { "Подкатегория должна быть того же типа" }
        }
    }
}
