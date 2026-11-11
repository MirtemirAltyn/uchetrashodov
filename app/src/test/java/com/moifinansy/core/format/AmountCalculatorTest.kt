package com.moifinansy.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountCalculatorTest {
    @Test
    fun `plain amounts`() {
        assertEquals(12_000L, AmountCalculator.evaluate("120"))
        assertEquals(12_050L, AmountCalculator.evaluate("120,5"))
        assertEquals(1_250_000L, AmountCalculator.evaluate("12 500"))
    }

    @Test
    fun `arithmetic with precedence`() {
        assertEquals(15_500L, AmountCalculator.evaluate("120+35"))
        assertEquals(10_450L, AmountCalculator.evaluate("120 − 15,5"))
        assertEquals(75_000L, AmountCalculator.evaluate("3×250"))
        assertEquals(30_000L, AmountCalculator.evaluate("900÷3"))
        assertEquals(17_000L, AmountCalculator.evaluate("20+50*3"))
        assertEquals(3_333L, AmountCalculator.evaluate("100/3"))
        assertEquals(-500L, AmountCalculator.evaluate("-5"))
    }

    @Test
    fun `invalid expressions`() {
        assertNull(AmountCalculator.evaluate(""))
        assertNull(AmountCalculator.evaluate("120+"))
        assertNull(AmountCalculator.evaluate("10/0"))
        assertNull(AmountCalculator.evaluate("1..2"))
        assertNull(AmountCalculator.evaluate("abc"))
        assertNull(AmountCalculator.evaluate("*5"))
    }

    @Test
    fun `operator detection ignores leading minus`() {
        assertTrue(AmountCalculator.hasOperator("120+35"))
        assertFalse(AmountCalculator.hasOperator("-120"))
        assertFalse(AmountCalculator.hasOperator("120"))
    }
}
