package com.moifinansy.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateFormatTest {
    @Test
    fun `parses rates with up to six decimals`() {
        assertEquals(87_450_000L, RateFormat.parse("87,45"))
        assertEquals(7_100L, RateFormat.parse("0.0071"))
        assertEquals(1_000_000L, RateFormat.parse("1"))
        assertNull(RateFormat.parse("0"))
        assertNull(RateFormat.parse("1,1234567"))
        assertNull(RateFormat.parse("abc"))
        assertEquals("87,45", RateFormat.format(87_450_000))
        assertEquals("0,0071", RateFormat.format(7_100))
    }
}
