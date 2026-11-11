package com.moifinansy.data.repository

import com.moifinansy.data.local.dao.BudgetDao
import com.moifinansy.data.local.dao.CurrencyRateDao
import com.moifinansy.data.local.dao.RecurringRuleDao
import com.moifinansy.data.local.dao.TagDao
import com.moifinansy.data.local.entity.CurrencyRateEntity
import com.moifinansy.data.local.entity.TagEntity
import com.moifinansy.domain.model.Budget
import com.moifinansy.domain.model.CurrencyRate
import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.model.Tag
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.repository.BudgetRepository
import com.moifinansy.domain.repository.CurrencyRateRepository
import com.moifinansy.domain.repository.RecurringRepository
import com.moifinansy.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class TagRepositoryImpl @Inject constructor(
    private val dao: TagDao,
    private val clock: Clock,
) : TagRepository {
    override fun observeAll(): Flow<List<Tag>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun create(name: String): Long {
        require(name.isNotBlank()) { "Введите название тега" }
        val now = clock.millis()
        return dao.insert(TagEntity(name = name.trim(), createdAt = now, updatedAt = now))
    }
}

class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
    private val clock: Clock,
) : BudgetRepository {
    override fun observeAll(): Flow<List<Budget>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun save(budget: Budget): Long {
        require(budget.limitAmount > 0) { "Лимит должен быть больше нуля" }
        val now = clock.millis()
        if (budget.id == 0L) return dao.insert(budget.toEntity(now, now))
        val old = requireNotNull(dao.getById(budget.id)) { "Бюджет ${budget.id} не найден" }
        dao.update(budget.toEntity(old.createdAt, now))
        return budget.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

class RecurringRepositoryImpl @Inject constructor(
    private val dao: RecurringRuleDao,
    private val transactionDao: com.moifinansy.data.local.dao.TransactionDao,
    private val clock: Clock,
) : RecurringRepository {
    override suspend fun get(id: Long): RecurringRule? = dao.getById(id)?.toModel()

    override suspend fun post(ruleId: Long, date: Long): Long {
        val rule = requireNotNull(dao.getById(ruleId)) { "Правило $ruleId не найдено" }
        val now = clock.millis()
        val next = com.moifinansy.domain.recurring.RecurrenceMath.next(date, rule.frequency, rule.interval, clock.zone)
        val t = com.moifinansy.domain.model.Transaction(
            type = rule.type,
            amount = rule.amount,
            accountId = rule.accountId,
            categoryId = rule.categoryId,
            toAccountId = rule.toAccountId,
            toAmount = rule.toAmount,
            fee = rule.fee,
            date = date,
            note = rule.note,
            recurringId = rule.id,
        )
        // Проведённая дата не раньше текущей «следующей» — сдвигаем правило за неё.
        val newNext = if (date >= rule.nextDate) next else rule.nextDate
        return transactionDao.insertForRule(t.toEntity(now, now), ruleId, newNext, now)
    }

    override suspend fun skip(ruleId: Long) {
        val rule = dao.getById(ruleId) ?: return
        val next = com.moifinansy.domain.recurring.RecurrenceMath.next(rule.nextDate, rule.frequency, rule.interval, clock.zone)
        dao.update(rule.copy(nextDate = next, updatedAt = clock.millis()))
    }

    override fun observeAll(): Flow<List<RecurringRule>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun dueBy(date: Long): List<RecurringRule> = dao.dueBy(date).map { it.toModel() }

    override suspend fun save(rule: RecurringRule): Long {
        require(rule.amount > 0) { "Сумма должна быть больше нуля" }
        require(rule.interval > 0) { "Интервал должен быть больше нуля" }
        require(rule.type != TransactionType.DEBT && rule.type != TransactionType.ADJUSTMENT) { "Этот тип нельзя повторять" }
        if (rule.type == TransactionType.EXPENSE || rule.type == TransactionType.INCOME) {
            requireNotNull(rule.categoryId) { "Выберите категорию" }
        }
        val now = clock.millis()
        if (rule.id == 0L) return dao.insert(rule.toEntity(now, now))
        val old = requireNotNull(dao.getById(rule.id)) { "Правило ${rule.id} не найдено" }
        dao.update(rule.toEntity(old.createdAt, now))
        return rule.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

class CurrencyRateRepositoryImpl @Inject constructor(
    private val dao: CurrencyRateDao,
    private val clock: Clock,
) : CurrencyRateRepository {
    override fun observeAll(): Flow<List<CurrencyRate>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun save(rate: CurrencyRate) {
        require(rate.rateToBaseMicros > 0) { "Курс должен быть больше нуля" }
        val now = clock.millis()
        val createdAt = dao.get(rate.code)?.createdAt ?: now
        dao.upsert(CurrencyRateEntity(rate.code, rate.rateToBaseMicros, createdAt, now))
    }
}
