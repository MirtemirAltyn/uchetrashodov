package com.moifinansy.data

import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtRepositoryTest : DbTest() {

    private suspend fun debt(
        contactId: Long,
        direction: DebtDirection,
        amount: Long,
        accountId: Long?,
        affectsAccounts: Boolean = true,
        currency: String = "KGS",
    ): Long = debts.createDebt(
        Debt(
            contactId = contactId, direction = direction, principal = amount, currencyCode = currency,
            startDate = now, affectsAccounts = affectsAccounts,
        ),
        accountId,
    )

    @Test
    fun `debt 14000 repaid with 4000 and 10000 closes`() = runTest {
        val card = account("Карта", initial = 50_000)
        val bob = debts.createContact(Contact(name = "Бакыт"))
        val id = debt(bob, DebtDirection.OWED_TO_ME, 14_000, card)

        debts.addPayment(id, DebtPaymentKind.REPAY, 4_000, card, now)
        assertEquals(10_000L, debts.get(id)!!.remaining)
        assertEquals(DebtStatus.ACTIVE, debts.get(id)!!.debt.status)

        debts.addPayment(id, DebtPaymentKind.REPAY, 10_000, card, now)
        val closed = debts.get(id)!!
        assertEquals(0L, closed.remaining)
        assertEquals(DebtStatus.CLOSED, closed.debt.status)

        // Закрытый долг не входит в итоги, баланс счёта вернулся.
        assertEquals(0L, debts.observeTotals("KGS").first().owedToMe)
        assertEquals(50_000L, accounts.balanceOf(card))
        assertEquals(3, debts.observePayments(id).first().size)
    }

    @Test
    fun `saldo equals owed to me minus I owe`() = runTest {
        val card = account("Карта", initial = 100_000)
        val a = debts.createContact(Contact(name = "Айгуль"))
        val b = debts.createContact(Contact(name = "Бек"))
        debt(a, DebtDirection.OWED_TO_ME, 30_000, card)
        debt(b, DebtDirection.OWED_TO_ME, 5_000, card)
        debt(b, DebtDirection.I_OWE, 12_000, card)
        debt(a, DebtDirection.OWED_TO_ME, 999, card, currency = "USD") // другая валюта — не в итогах KGS

        val totals = debts.observeTotals("KGS").first()
        assertEquals(35_000L, totals.owedToMe)
        assertEquals(12_000L, totals.iOwe)
        assertEquals(23_000L, totals.saldo)

        // Взаимные долги Бека сводятся: 5 000 − 12 000 = −7 000 (я ему должен).
        val byContact = debts.observeContactBalances().first().filter { it.currencyCode == "KGS" }.associate { it.contactId to it.saldo }
        assertEquals(30_000L, byContact[a])
        assertEquals(-7_000L, byContact[b])
    }

    @Test
    fun `old debt that does not affect accounts creates no account operations`() = runTest {
        val card = account("Карта", initial = 1_000)
        val c = debts.createContact(Contact(name = "Старый знакомый"))
        val id = debt(c, DebtDirection.I_OWE, 8_000, accountId = null, affectsAccounts = false)
        debts.addPayment(id, DebtPaymentKind.REPAY, 3_000, accountId = null, date = now)

        assertEquals(1_000L, accounts.balanceOf(card))
        assertEquals(5_000L, debts.get(id)!!.remaining)
        assertTrue(debts.observePayments(id).first().all { it.transactionId == null && it.accountId == null })
    }

    @Test
    fun `debt operations change balance but stay out of category reports`() = runTest {
        val card = account("Карта", initial = 0)
        val c = debts.createContact(Contact(name = "Банк"))
        val id = debt(c, DebtDirection.I_OWE, 20_000, card)
        debts.addPayment(id, DebtPaymentKind.INCREASE, 5_000, card, now)
        debts.addPayment(id, DebtPaymentKind.REPAY, 7_000, card, now)

        assertEquals(18_000L, accounts.balanceOf(card))
        assertEquals(18_000L, debts.get(id)!!.remaining)
        val ops = transactions.observeRange(now, now + 1).first()
        assertTrue(ops.all { it.type == TransactionType.DEBT && it.debtId == id && it.categoryId == null })
        assertEquals(0L, transactions.totalExpense(now, now + 1))
        assertEquals(0L, transactions.totalIncome(now, now + 1))
    }

    @Test
    fun `forgiving the rest marks debt forgiven without moving money`() = runTest {
        val card = account("Карта", initial = 10_000)
        val c = debts.createContact(Contact(name = "Друг"))
        val id = debt(c, DebtDirection.OWED_TO_ME, 6_000, card)
        debts.addPayment(id, DebtPaymentKind.REPAY, 1_000, card, now)
        val forgiveId = debts.addPayment(id, DebtPaymentKind.FORGIVE, 5_000, card, now)

        assertEquals(DebtStatus.FORGIVEN, debts.get(id)!!.debt.status)
        assertEquals(5_000L, accounts.balanceOf(card)) // 10 000 − 6 000 + 1 000
        assertNull(debts.observePayments(id).first().first { it.id == forgiveId }.transactionId)
    }

    @Test
    fun `cannot repay more than remaining or without account`() = runTest {
        val card = account("Карта", initial = 10_000)
        val c = debts.createContact(Contact(name = "Друг"))
        val id = debt(c, DebtDirection.OWED_TO_ME, 3_000, card)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { debts.addPayment(id, DebtPaymentKind.REPAY, 3_001, card, now) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { debts.addPayment(id, DebtPaymentKind.REPAY, 1_000, null, now) }
        }
        // Неудачные попытки ничего не записали.
        assertEquals(3_000L, debts.get(id)!!.remaining)
        assertEquals(7_000L, accounts.balanceOf(card))
    }

    @Test
    fun `increase reopens a closed debt`() = runTest {
        val card = account("Карта", initial = 10_000)
        val c = debts.createContact(Contact(name = "Друг"))
        val id = debt(c, DebtDirection.I_OWE, 2_000, card)
        debts.addPayment(id, DebtPaymentKind.REPAY, 2_000, card, now)
        assertEquals(DebtStatus.CLOSED, debts.get(id)!!.debt.status)

        debts.addPayment(id, DebtPaymentKind.INCREASE, 500, card, now)
        assertEquals(DebtStatus.ACTIVE, debts.get(id)!!.debt.status)
        assertEquals(500L, debts.get(id)!!.remaining)
    }
}

class DebtStage5Test : DbTest() {
    @org.junit.Test
    fun `total, initial account and deletion restore balances`() = kotlinx.coroutines.test.runTest {
        val card = account("Карта", initial = 20_000)
        val c = debts.createContact(com.moifinansy.domain.model.Contact(name = "Нурлан"))
        val id = debts.createDebt(
            com.moifinansy.domain.model.Debt(
                contactId = c, direction = com.moifinansy.domain.model.DebtDirection.OWED_TO_ME,
                principal = 10_000, currencyCode = "KGS", startDate = now,
            ),
            card,
        )
        debts.addPayment(id, com.moifinansy.domain.model.DebtPaymentKind.INCREASE, 4_000, card, now)
        debts.addPayment(id, com.moifinansy.domain.model.DebtPaymentKind.REPAY, 5_000, card, now)

        val d = debts.get(id)!!
        org.junit.Assert.assertEquals(14_000L, d.total)
        org.junit.Assert.assertEquals(9_000L, d.remaining)
        org.junit.Assert.assertEquals(5_000L, d.repaid)
        org.junit.Assert.assertEquals(card, d.initialAccountId)
        org.junit.Assert.assertEquals(11_000L, accounts.balanceOf(card))

        // Контакт с долгами не удаляется.
        org.junit.Assert.assertFalse(debts.deleteContact(c))
        debts.deleteDebt(id)
        org.junit.Assert.assertNull(debts.get(id))
        org.junit.Assert.assertEquals(20_000L, accounts.balanceOf(card))
        org.junit.Assert.assertTrue(debts.deleteContact(c))
    }
}
