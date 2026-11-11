package com.moifinansy.data

import com.moifinansy.data.backup.DataReset
import com.moifinansy.data.demo.DemoDataGenerator
import com.moifinansy.data.local.DefaultCategories
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.OperationFilter
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataResetTest : DbTest() {
    @Test
    fun `start over leaves an empty base with default categories and reset settings`() = runTest {
        val settings = FakeSettings().apply { currency.value = "KGS"; completed.value = true; themeFlow.value = ThemeMode.DARK }
        val mine = account("Мой счёт", initial = 1_000)
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 100, accountId = mine, categoryId = categoryId("Продукты"), date = now))
        DemoDataGenerator(db, settings, clock).generate()
        categories.delete(categoryId("Дети"), null) // пользователь менял категории

        DataReset(db, settings, clock).resetAll()

        assertTrue(accounts.observeAccounts(includeArchived = true).first().isEmpty())
        assertTrue(transactions.observeFiltered(OperationFilter(0, Long.MAX_VALUE)).first().isEmpty())
        assertTrue(debts.observeDebts().first().isEmpty())
        assertTrue(debts.observeContacts().first().isEmpty())
        assertTrue(db.budgetDao().observeAll().first().isEmpty())
        assertTrue(db.recurringRuleDao().observeAll().first().isEmpty())
        assertNull(db.currencyRateDao().get("USD"))
        val expenses = categories.observe(CategoryKind.EXPENSE).first()
        assertEquals(DefaultCategories.expenses.map { it.name }, expenses.filter { it.parentId == null }.map { it.name })
        assertEquals(DefaultCategories.incomes.size, categories.observe(CategoryKind.INCOME).first().size)
        assertFalse(settings.completed.value)
        assertNull(settings.currency.value)
    }
}
