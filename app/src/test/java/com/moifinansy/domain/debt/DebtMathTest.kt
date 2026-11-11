package com.moifinansy.domain.debt

import com.moifinansy.domain.model.DebtDirection.I_OWE
import com.moifinansy.domain.model.DebtDirection.OWED_TO_ME
import com.moifinansy.domain.model.DebtPaymentKind.FORGIVE
import com.moifinansy.domain.model.DebtPaymentKind.INCREASE
import com.moifinansy.domain.model.DebtPaymentKind.INITIAL
import com.moifinansy.domain.model.DebtPaymentKind.REPAY
import com.moifinansy.domain.model.DebtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtMathTest {

    @Test
    fun `I owe - borrowing brings money in, repaying takes it out`() {
        assertEquals(500L, DebtMath.accountEffect(I_OWE, INITIAL, 500))
        assertEquals(200L, DebtMath.accountEffect(I_OWE, INCREASE, 200))
        assertEquals(-300L, DebtMath.accountEffect(I_OWE, REPAY, 300))
    }

    @Test
    fun `owed to me - lending takes money out, getting it back brings it in`() {
        assertEquals(-500L, DebtMath.accountEffect(OWED_TO_ME, INITIAL, 500))
        assertEquals(-200L, DebtMath.accountEffect(OWED_TO_ME, INCREASE, 200))
        assertEquals(300L, DebtMath.accountEffect(OWED_TO_ME, REPAY, 300))
    }

    @Test
    fun `forgiving moves no money`() {
        assertEquals(0L, DebtMath.accountEffect(I_OWE, FORGIVE, 100))
        assertEquals(0L, DebtMath.accountEffect(OWED_TO_ME, FORGIVE, 100))
    }

    @Test
    fun `remaining delta`() {
        assertEquals(100L, DebtMath.remainingDelta(INITIAL, 100))
        assertEquals(100L, DebtMath.remainingDelta(INCREASE, 100))
        assertEquals(-100L, DebtMath.remainingDelta(REPAY, 100))
        assertEquals(-100L, DebtMath.remainingDelta(FORGIVE, 100))
    }

    @Test
    fun `status after movement`() {
        assertEquals(DebtStatus.ACTIVE, DebtMath.statusAfter(1, REPAY))
        assertEquals(DebtStatus.CLOSED, DebtMath.statusAfter(0, REPAY))
        assertEquals(DebtStatus.FORGIVEN, DebtMath.statusAfter(0, FORGIVE))
        assertEquals(DebtStatus.ACTIVE, DebtMath.statusAfter(50, INCREASE))
    }

    @Test
    fun `overdue only for active debts past due date`() {
        assertTrue(DebtMath.isOverdue(DebtStatus.ACTIVE, dueDate = 100, now = 101))
        assertFalse(DebtMath.isOverdue(DebtStatus.ACTIVE, dueDate = 100, now = 100))
        assertFalse(DebtMath.isOverdue(DebtStatus.ACTIVE, dueDate = null, now = 101))
        assertFalse(DebtMath.isOverdue(DebtStatus.CLOSED, dueDate = 100, now = 101))
    }
}
