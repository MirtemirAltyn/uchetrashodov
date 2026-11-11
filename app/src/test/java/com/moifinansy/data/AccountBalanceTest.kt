package com.moifinansy.data

import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountBalanceTest : DbTest() {

    @Test
    fun `balance matches manual calculation after mixed operations`() = runTest {
        val card = account("Карта", initial = 100_000)
        val cash = account("Наличные", initial = 5_000)
        val food = categoryId("Продукты")
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        val contact = debts.createContact(com.moifinansy.domain.model.Contact(name = "Азамат"))

        transactions.save(Transaction(type = TransactionType.INCOME, amount = 50_000, accountId = card, categoryId = salary, date = now))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 12_500, accountId = card, categoryId = food, date = now))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 1_000, accountId = cash, categoryId = food, date = now))
        transactions.save(
            Transaction(type = TransactionType.TRANSFER, amount = 20_000, fee = 300, accountId = card, toAccountId = cash, date = now),
        )
        // Мне должны: дал в долг 7 000 с карты, вернули 2 000 на наличные.
        val debtId = debts.createDebt(
            Debt(contactId = contact, direction = DebtDirection.OWED_TO_ME, principal = 7_000, currencyCode = "KGS", startDate = now),
            accountId = card,
        )
        debts.addPayment(debtId, DebtPaymentKind.REPAY, 2_000, accountId = cash, date = now)

        // Карта: 100 000 + 50 000 − 12 500 − (20 000 + 300) − 7 000
        val expectedCard = 100_000L + 50_000 - 12_500 - 20_300 - 7_000
        // Наличные: 5 000 − 1 000 + 20 000 + 2 000
        val expectedCash = 5_000L - 1_000 + 20_000 + 2_000

        assertEquals(expectedCard, accounts.balanceOf(card))
        assertEquals(expectedCash, accounts.balanceOf(cash))

        val list = accounts.observeAccounts().first().associate { it.account.id to it.balance }
        assertEquals(expectedCard, list[card])
        assertEquals(expectedCash, list[cash])
        assertEquals(expectedCard + expectedCash, accounts.observeTotal("KGS").first())
    }

    @Test
    fun `transfer to another currency credits toAmount`() = runTest {
        val kgs = account("Сом", initial = 100_000)
        val usd = account("Доллары", currency = "USD")
        transactions.save(
            Transaction(type = TransactionType.TRANSFER, amount = 87_000, toAmount = 1_000, accountId = kgs, toAccountId = usd, date = now),
        )
        assertEquals(13_000L, accounts.balanceOf(kgs))
        assertEquals(1_000L, accounts.balanceOf(usd))
    }

    @Test
    fun `total skips accounts excluded from total, archived and other currencies`() = runTest {
        account("Основной", initial = 1_000)
        account("Копилка", initial = 500, inTotal = false)
        account("Доллары", initial = 700, currency = "USD")
        val old = account("Старый", initial = 300)
        accounts.setArchived(old, true)

        assertEquals(1_000L, accounts.observeTotal("KGS").first())
        assertEquals(3, accounts.observeAccounts().first().size)
        assertEquals(4, accounts.observeAccounts(includeArchived = true).first().size)
    }

    @Test
    fun `adjustment brings balance to the actual amount`() = runTest {
        val card = account("Карта", initial = 10_000)
        val id = transactions.adjustBalance(card, actualBalance = 9_250, date = now)
        assertEquals(9_250L, accounts.balanceOf(card))
        assertEquals(-750L, transactions.get(id!!)!!.amount)

        assertNull(transactions.adjustBalance(card, actualBalance = 9_250, date = now))
        transactions.adjustBalance(card, actualBalance = 12_000, date = now)
        assertEquals(12_000L, accounts.balanceOf(card))
    }

    @Test
    fun `deleting and editing an operation recalculates balance`() = runTest {
        val card = account("Карта", initial = 10_000)
        val food = categoryId("Продукты")
        val id = transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 4_000, accountId = card, categoryId = food, date = now))
        assertEquals(6_000L, accounts.balanceOf(card))

        transactions.save(transactions.get(id)!!.copy(amount = 1_000))
        assertEquals(9_000L, accounts.balanceOf(card))

        transactions.delete(id)
        assertEquals(10_000L, accounts.balanceOf(card))
    }
}
