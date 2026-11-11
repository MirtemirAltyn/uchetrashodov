package com.moifinansy.data

import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtAffectsToggleTest : DbTest() {
    @Test
    fun `switching off removes account operations, switching on re-posts them`() = runTest {
        val cash = account("Наличные", initial = 20_000)
        val card = account("Карта", initial = 0)
        val c = debts.createContact(Contact(name = "Бакыт"))
        val id = debts.createDebt(Debt(contactId = c, direction = DebtDirection.OWED_TO_ME, principal = 10_000, currencyCode = "KGS", startDate = now), cash)
        debts.addPayment(id, DebtPaymentKind.REPAY, 3_000, cash, now + 1)
        debts.addPayment(id, DebtPaymentKind.FORGIVE, 1_000, null, now + 2)
        assertEquals(20_000L - 10_000 + 3_000, accounts.balanceOf(cash))

        debts.setAffectsAccounts(id, false, null)
        assertEquals(20_000L, accounts.balanceOf(cash))
        assertEquals(false, debts.get(id)!!.debt.affectsAccounts)
        assertEquals(6_000L, debts.get(id)!!.remaining) // долг и история не тронуты
        assertTrue(debts.observePayments(id).first().all { it.accountId == null && it.transactionId == null })
        assertEquals(0, transactions.observeForAccount(cash).first().count { it.type == TransactionType.DEBT })

        // Включаем снова, но уже по карте: выдача и погашение, прощение денег не двигает.
        debts.setAffectsAccounts(id, true, card)
        assertEquals(20_000L, accounts.balanceOf(cash))
        assertEquals(-10_000L + 3_000, accounts.balanceOf(card))
        assertEquals(2, transactions.observeForAccount(card).first().count { it.type == TransactionType.DEBT && it.debtId == id })
        val payments = debts.observePayments(id).first()
        assertTrue(payments.filter { it.kind != DebtPaymentKind.FORGIVE }.all { it.accountId == card && it.transactionId != null })
        assertEquals(6_000L, debts.get(id)!!.remaining)
    }
}
