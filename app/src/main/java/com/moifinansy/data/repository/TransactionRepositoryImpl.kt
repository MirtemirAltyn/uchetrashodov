package com.moifinansy.data.repository

import com.moifinansy.data.local.dao.AccountDao
import com.moifinansy.data.local.dao.CategoryDao
import com.moifinansy.data.local.dao.TransactionDao
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.CategoryTotal
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val clock: Clock,
) : TransactionRepository {

    override fun observeRange(from: Long, to: Long): Flow<List<Transaction>> =
        dao.observeRange(from, to).map { rows -> rows.map { it.toModel() } }

    override fun observeForAccount(accountId: Long): Flow<List<Transaction>> =
        dao.observeForAccount(accountId).map { rows -> rows.map { it.toModel() } }

    override fun observeFiltered(filter: com.moifinansy.domain.model.OperationFilter): Flow<List<Transaction>> =
        dao.observeFiltered(
            filter.from, filter.to, filter.type?.name, filter.accountId, filter.categoryId, filter.tagId,
            filter.minAmount, filter.maxAmount,
        ).map { rows -> rows.map { it.toModel() } }

    override fun observeLatest(limit: Int): Flow<List<Transaction>> = dao.observeLatest(limit).map { rows -> rows.map { it.toModel() } }
    override fun observeTotalExpense(from: Long, to: Long, base: String?): Flow<Long> = dao.observeTotalExpense(from, to, base)
    override fun observeTotalIncome(from: Long, to: Long, base: String?): Flow<Long> = dao.observeTotalIncome(from, to, base)
    override fun observeExpenseByCategory(from: Long, to: Long, base: String?): Flow<List<CategoryTotal>> =
        dao.observeExpenseByCategory(from, to, base).map { rows -> rows.map { it.toModel() } }

    override fun observeIncomeByCategory(from: Long, to: Long, base: String?): Flow<List<CategoryTotal>> =
        dao.observeIncomeByCategory(from, to, base).map { rows -> rows.map { it.toModel() } }

    override fun observeCategoryMonths(categoryId: Long, from: Long, to: Long, base: String?): Flow<Map<java.time.YearMonth, Long>> =
        dao.observeCategoryMonths(categoryId, from, to, base).map { rows -> rows.associate { java.time.YearMonth.parse(it.month) to it.total } }

    override fun observeMonthTotals(from: Long, to: Long, base: String?): Flow<List<com.moifinansy.domain.model.MonthTotals>> =
        dao.observeMonthTotals(from, to, base).map { rows ->
            rows.map { com.moifinansy.domain.model.MonthTotals(java.time.YearMonth.parse(it.month), it.income, it.expense, it.net) }
        }

    override suspend fun get(id: Long): Transaction? = dao.getById(id)?.toModel()

    override suspend fun save(transaction: Transaction, tagIds: List<Long>): Long {
        validate(transaction)
        val now = clock.millis()
        val createdAt = if (transaction.id == 0L) {
            now
        } else {
            val old = requireNotNull(dao.getById(transaction.id)) { "Операция ${transaction.id} не найдена" }
            require(old.type != TransactionType.DEBT) { "Движения по долгу меняются на экране долга" }
            old.createdAt
        }
        return dao.upsertWithTags(transaction.toEntity(createdAt, now), tagIds, now)
    }

    override suspend fun delete(id: Long) {
        val old = dao.getById(id) ?: return
        require(old.type != TransactionType.DEBT) { "Движения по долгу удаляются на экране долга" }
        dao.delete(id)
    }

    override suspend fun adjustBalance(accountId: Long, actualBalance: Long, date: Long): Long? {
        val current = requireNotNull(accountDao.balanceOf(accountId)) { "Счёт $accountId не найден" }
        val diff = actualBalance - current
        if (diff == 0L) return null
        val now = clock.millis()
        val adjustment = Transaction(type = TransactionType.ADJUSTMENT, amount = diff, accountId = accountId, date = date)
        return dao.insert(adjustment.toEntity(now, now))
    }

    override suspend fun tagIdsOf(transactionId: Long): List<Long> = dao.tagIdsOf(transactionId)
    override suspend fun tagNamesByTransaction(): Map<Long, List<String>> =
        dao.allTagNames().groupBy({ it.transactionId }, { it.name })

    override suspend fun totalExpense(from: Long, to: Long, base: String?): Long = dao.totalExpense(from, to, base)
    override suspend fun totalIncome(from: Long, to: Long, base: String?): Long = dao.totalIncome(from, to, base)
    override suspend fun expenseByCategory(from: Long, to: Long, base: String?): List<CategoryTotal> = dao.expenseByCategory(from, to, base).map { it.toModel() }
    override suspend fun incomeByCategory(from: Long, to: Long, base: String?): List<CategoryTotal> = dao.incomeByCategory(from, to, base).map { it.toModel() }

    private suspend fun validate(t: Transaction) {
        requireNotNull(accountDao.getById(t.accountId)) { "Счёт не найден" }
        when (t.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                require(t.amount > 0) { "Сумма должна быть больше нуля" }
                val categoryId = requireNotNull(t.categoryId) { "Выберите категорию" }
                val category = requireNotNull(categoryDao.getById(categoryId)) { "Категория не найдена" }
                val expected = if (t.type == TransactionType.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME
                require(category.kind == expected) { "Категория не подходит к типу операции" }
                require(t.toAccountId == null && t.toAmount == null && t.fee == 0L) { "Поля перевода только для перевода" }
            }
            TransactionType.TRANSFER -> {
                require(t.amount > 0) { "Сумма должна быть больше нуля" }
                val toId = requireNotNull(t.toAccountId) { "Выберите счёт получателя" }
                require(toId != t.accountId) { "Счета перевода должны различаться" }
                requireNotNull(accountDao.getById(toId)) { "Счёт получателя не найден" }
                require(t.toAmount == null || t.toAmount > 0) { "Сумма зачисления должна быть больше нуля" }
                require(t.fee >= 0) { "Комиссия не может быть отрицательной" }
                require(t.categoryId == null) { "У перевода нет категории" }
            }
            TransactionType.ADJUSTMENT -> {
                require(t.amount != 0L) { "Корректировка на ноль не нужна" }
                require(t.categoryId == null && t.toAccountId == null) { "У корректировки нет категории и получателя" }
            }
            TransactionType.DEBT -> throw IllegalArgumentException("Движения по долгу создаются через долги")
        }
    }
}
