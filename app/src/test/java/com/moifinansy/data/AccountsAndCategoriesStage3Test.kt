package com.moifinansy.data

import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.model.effectOn
import com.moifinansy.ui.categories.CategoriesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountsAndCategoriesStage3Test : DbTest() {

    @Test
    fun `history effects add up to the balance computed by the database`() = runTest {
        val card = account("Карта", initial = 10_000)
        val cash = account("Наличные", initial = 2_000)
        val food = categoryId("Продукты")
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        transactions.save(Transaction(type = TransactionType.INCOME, amount = 7_000, accountId = card, categoryId = salary, date = now))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 1_500, accountId = card, categoryId = food, date = now))
        transactions.save(Transaction(type = TransactionType.TRANSFER, amount = 3_000, fee = 50, accountId = card, toAccountId = cash, date = now))
        transactions.save(Transaction(type = TransactionType.TRANSFER, amount = 500, accountId = cash, toAccountId = card, date = now))
        transactions.adjustBalance(cash, 4_000, now)
        val friend = debts.createContact(Contact(name = "Друг"))
        debts.createDebt(Debt(contactId = friend, direction = DebtDirection.I_OWE, principal = 900, currencyCode = "KGS", startDate = now), card)

        for (id in listOf(card, cash)) {
            val history = transactions.observeForAccount(id).first()
            val initial = accounts.get(id)!!.initialBalance
            assertEquals(accounts.balanceOf(id), initial + history.sumOf { it.effectOn(id) })
        }
        // Входящий перевод виден в истории счёта-получателя.
        assertEquals(2, transactions.observeForAccount(cash).first().count { it.type == TransactionType.TRANSFER })
    }

    @Test
    fun `account with operations cannot be deleted, empty one can`() = runTest {
        val used = account("Карта", initial = 1_000)
        val empty = account("Пустой")
        transactions.adjustBalance(used, 500, now)

        assertEquals(1, accounts.usageCount(used))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { accounts.delete(used) } }

        assertEquals(0, accounts.usageCount(empty))
        accounts.delete(empty)
        assertNull(accounts.get(empty))
    }

    @Test
    fun `reorder changes order of siblings only`() = runTest {
        val top = categories.observe(CategoryKind.EXPENSE).first().filter { it.parentId == null }
        val reversed = top.map { it.id }.reversed()
        categories.reorder(reversed)
        assertEquals(reversed, categories.observe(CategoryKind.EXPENSE).first().filter { it.parentId == null }.map { it.id })

        val taxi = categoryId("Такси")
        val food = categoryId("Продукты")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { categories.reorder(listOf(taxi, food)) } }
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { categories.reorder(listOf(food, salary)) } }
    }

    @Test
    fun `usage and children counts drive the delete dialog`() = runTest {
        val card = account("Карта", initial = 1_000)
        val transport = categoryId("Транспорт")
        val cafe = categoryId("Кафе и рестораны")
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 100, accountId = card, categoryId = cafe, date = now))

        assertEquals(2, categories.childrenCount(transport))
        assertEquals(1, categories.usageCount(cafe))
        assertEquals(0, categories.usageCount(transport))
    }

    @Test
    fun `categories screen state groups subcategories and archived`() = runTest {
        val taxi = categoryId("Такси")
        categories.setArchived(categoryId("Дети"), true)
        categories.setArchived(taxi, true)

        val state = CategoriesViewModel.buildState(CategoryKind.EXPENSE, categories.observe(CategoryKind.EXPENSE, includeArchived = true).first())
        val transport = state.active.first { it.category.name == "Транспорт" }
        assertEquals(listOf("Общественный транспорт"), transport.childNames)
        assertEquals(setOf("Дети", "Такси"), state.archived.map { it.category.name }.toSet())
        assertEquals(false, state.active.any { it.category.name == "Дети" })
    }
}
