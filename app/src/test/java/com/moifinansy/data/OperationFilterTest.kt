package com.moifinansy.data

import com.moifinansy.data.repository.TagRepositoryImpl
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.OperationFilter
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationFilterTest : DbTest() {

    @Test
    fun `filters by type, account, category with children, tag and amount`() = runTest {
        val card = account("Карта", initial = 100_000)
        val cash = account("Наличные", initial = 10_000)
        val transport = categoryId("Транспорт")
        val taxi = categoryId("Такси")
        val food = categoryId("Продукты")
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        val tag = TagRepositoryImpl(db.tagDao(), clock).create("отпуск")

        val a = transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 500, accountId = card, categoryId = taxi, date = now))
        val b = transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 2_000, accountId = cash, categoryId = transport, date = now), listOf(tag))
        val c = transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 9_000, accountId = card, categoryId = food, date = now))
        val d = transactions.save(Transaction(type = TransactionType.INCOME, amount = 50_000, accountId = card, categoryId = salary, date = now))
        val e = transactions.save(Transaction(type = TransactionType.TRANSFER, amount = 1_000, accountId = card, toAccountId = cash, date = now))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 700, accountId = card, categoryId = food, date = now + DAY * 40))

        val base = OperationFilter(from = now, to = now + DAY)
        suspend fun ids(f: OperationFilter) = transactions.observeFiltered(f).first().map { it.id }.toSet()

        assertEquals(setOf(a, b, c, d, e), ids(base))
        assertEquals(setOf(a, b, c), ids(base.copy(type = TransactionType.EXPENSE)))
        assertEquals(setOf(b, e), ids(base.copy(accountId = cash))) // входящий перевод тоже
        assertEquals(setOf(a, b), ids(base.copy(categoryId = transport))) // вместе с «Такси»
        assertEquals(setOf(a), ids(base.copy(categoryId = taxi)))
        assertEquals(setOf(b), ids(base.copy(tagId = tag)))
        assertEquals(setOf(b, c, e), ids(base.copy(minAmount = 1_000, maxAmount = 9_000)))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
