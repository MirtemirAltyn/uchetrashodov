package com.moifinansy.data

import com.moifinansy.data.demo.DemoDataGenerator
import com.moifinansy.data.repository.RecurringRepositoryImpl
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.OperationFilter
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.recurring.RecurringDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class DemoDataTest : DbTest() {

    @Test
    fun `a year of demo data, then deletion keeps user records`() = runTest {
        val settings = FakeSettings().apply { currency.value = "KGS" }
        val demo = DemoDataGenerator(db, settings, clock)
        demo.generate()

        val all = transactions.observeFiltered(OperationFilter(0, Long.MAX_VALUE)).first()
        assertTrue("операций: ${all.size}", all.size > 500)
        val months = all.map { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(clock.zone)) }.toSet()
        assertTrue(months.size >= 12)
        assertEquals(3, accounts.observeAccounts().first().size)
        assertNotNull(db.currencyRateDao().get("USD"))
        val debtList = debts.observeDebts().first()
        assertEquals(4, debtList.size)
        assertTrue(debtList.any { it.debt.status == DebtStatus.CLOSED })
        assertTrue(debtList.any { it.debt.dueDate != null && it.debt.dueDate!! < now && it.debt.status == DebtStatus.ACTIVE })
        assertTrue(debtList.any { !it.debt.affectsAccounts })
        // Отчёты сходятся: сумма по категориям = общий расход.
        val yearFrom = now - 366L * 86_400_000
        assertEquals(transactions.totalExpense(yearFrom, Long.MAX_VALUE, "KGS"), transactions.expenseByCategory(yearFrom, Long.MAX_VALUE, "KGS").sumOf { it.total })
        // Подсказка регулярного платежа: интернет (правила нет), а не аренда (правило есть).
        val recurring = RecurringRepositoryImpl(db.recurringRuleDao(), db.transactionDao(), clock)
        val suggestions = RecurringDetector.detect(all, recurring.observeAll().first(), emptySet(), now, clock.zone)
        val internet = categoryId("Связь и интернет")
        assertTrue(suggestions.any { it.categoryId == internet && it.amount == 990_00L })
        assertTrue(suggestions.none { it.categoryId == categoryId("Жильё и коммуналка") && it.amount == 25_000_00L })

        // Пользователь записал свою операцию на демо-карту и завёл свой счёт.
        val card = accounts.observeAccounts().first().first { it.account.name == "Карта Мбанк" }.account.id
        val food = categoryId("Продукты")
        val mine = transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 777, accountId = card, categoryId = food, date = now, note = "моё"))
        val myAccount = accounts.create(Account(name = "Мой счёт", type = AccountType.CASH, currencyCode = "KGS", initialBalance = 5_000, color = 0, icon = "payments"))
        // Правка демо-операции не делает её «своей».
        val demoTx = all.first { it.type == TransactionType.EXPENSE }
        transactions.save(demoTx.copy(note = "поправил"))

        demo.delete()
        val left = transactions.observeFiltered(OperationFilter(0, Long.MAX_VALUE)).first()
        assertEquals(listOf(mine), left.map { it.id })
        assertEquals(setOf("Карта Мбанк", "Мой счёт"), accounts.observeAccounts().first().map { it.account.name }.toSet())
        assertEquals(5_000L, accounts.balanceOf(myAccount))
        assertTrue(debts.observeDebts().first().isEmpty())
        assertTrue(debts.observeContacts().first().isEmpty())
        assertNull(db.currencyRateDao().get("USD"))
        assertTrue(categories.observe(CategoryKind.EXPENSE).first().isNotEmpty())
        assertEquals(0, db.demoDao().observeDemoCount().first())
    }
}
