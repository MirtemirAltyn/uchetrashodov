package com.moifinansy.data

import com.moifinansy.data.local.DefaultCategories
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.SystemCategory
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TransactionAndCategoryTest : DbTest() {

    @Test
    fun `default categories are seeded on first launch`() = runTest {
        val expenses = categories.observe(CategoryKind.EXPENSE).first()
        val incomes = categories.observe(CategoryKind.INCOME).first()
        val topExpenses = expenses.filter { it.parentId == null }.map { it.name }
        assertEquals(DefaultCategories.expenses.map { it.name }, topExpenses)
        assertEquals(DefaultCategories.incomes.map { it.name }, incomes.map { it.name })

        val transport = expenses.first { it.name == "Транспорт" }
        assertEquals(setOf("Такси", "Общественный транспорт"), expenses.filter { it.parentId == transport.id }.map { it.name }.toSet())
        assertNotNull(db.categoryDao().getBySystemKey(SystemCategory.COMMISSIONS))
    }

    @Test
    fun `expense without category cannot be saved`() = runTest {
        val card = account("Карта", initial = 1_000)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 100, accountId = card, date = now)) }
        }
        // Категория дохода к расходу не подходит.
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 100, accountId = card, categoryId = salary, date = now))
            }
        }
        assertEquals(0, transactions.observeRange(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun `sum by categories equals total expense, debts and transfers excluded, fee goes to commissions`() = runTest {
        val card = account("Карта", initial = 100_000)
        val cash = account("Наличные")
        val food = categoryId("Продукты")
        val cafe = categoryId("Кафе и рестораны")
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        val from = now
        val to = now + 86_400_000

        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 3_000, accountId = card, categoryId = food, date = now))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 2_000, accountId = card, categoryId = food, date = now + 1))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 1_500, accountId = card, categoryId = cafe, date = now + 2))
        transactions.save(Transaction(type = TransactionType.INCOME, amount = 40_000, accountId = card, categoryId = salary, date = now))
        transactions.save(Transaction(type = TransactionType.TRANSFER, amount = 10_000, fee = 150, accountId = card, toAccountId = cash, date = now))
        transactions.adjustBalance(cash, 0, now)
        // Вне периода.
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 9_999, accountId = card, categoryId = food, date = to))

        val byCategory = transactions.expenseByCategory(from, to).associate { it.categoryId to it.total }
        val commissions = db.categoryDao().getBySystemKey(SystemCategory.COMMISSIONS)!!.id
        assertEquals(5_000L, byCategory[food])
        assertEquals(1_500L, byCategory[cafe])
        assertEquals(150L, byCategory[commissions])
        assertEquals(transactions.totalExpense(from, to), byCategory.values.sum())
        assertEquals(6_650L, transactions.totalExpense(from, to))

        assertEquals(40_000L, transactions.totalIncome(from, to))
        assertEquals(listOf(salary to 40_000L), transactions.incomeByCategory(from, to).map { it.categoryId to it.total })
    }

    @Test
    fun `tags are saved and replaced with the operation`() = runTest {
        val card = account("Карта", initial = 1_000)
        val food = categoryId("Продукты")
        val tags = com.moifinansy.data.repository.TagRepositoryImpl(db.tagDao(), clock)
        val t1 = tags.create("отпуск")
        val t2 = tags.create("семья")

        val id = transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 100, accountId = card, categoryId = food, date = now), listOf(t1, t2))
        assertEquals(listOf(t1, t2), transactions.tagIdsOf(id))

        transactions.save(transactions.get(id)!!, listOf(t2))
        assertEquals(listOf(t2), transactions.tagIdsOf(id))
    }

    @Test
    fun `category with operations is deleted only with moving them`() = runTest {
        val card = account("Карта", initial = 10_000)
        val cafe = categoryId("Кафе и рестораны")
        val food = categoryId("Продукты")
        val id = transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 700, accountId = card, categoryId = cafe, date = now))

        assertThrows(IllegalArgumentException::class.java) { runBlocking { categories.delete(cafe, moveToId = null) } }
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { categories.delete(cafe, moveToId = salary) } }

        categories.delete(cafe, moveToId = food)
        assertNull(categories.get(cafe))
        assertEquals(food, transactions.get(id)!!.categoryId)
        assertEquals(9_300L, accounts.balanceOf(card))
    }

    @Test
    fun `only two category levels and system category is protected`() = runTest {
        val taxi = categoryId("Такси")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { categories.create(Category(name = "Яндекс", kind = CategoryKind.EXPENSE, parentId = taxi, icon = "x", color = 0)) }
        }
        val commissions = db.categoryDao().getBySystemKey(SystemCategory.COMMISSIONS)!!.id
        assertThrows(IllegalArgumentException::class.java) { runBlocking { categories.delete(commissions, null) } }

        val unused = categories.create(Category(name = "Хобби", kind = CategoryKind.EXPENSE, icon = "palette", color = 0))
        categories.delete(unused, moveToId = null)
        assertNull(categories.get(unused))
    }
}
