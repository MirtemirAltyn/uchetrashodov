package com.moifinansy.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatTest {
    private val nbsp = ' '

    @Test
    fun `formats with thousands separators and currency symbol`() {
        assertEquals("12${nbsp}500${nbsp}сом", MoneyFormat.format(1_250_000, "KGS"))
        assertEquals("12${nbsp}500,50${nbsp}сом", MoneyFormat.format(1_250_050, "KGS"))
        assertEquals("0,05${nbsp}$", MoneyFormat.format(5, "USD"))
        assertEquals("1${nbsp}000${nbsp}000${nbsp}₽", MoneyFormat.format(100_000_000, "RUB"))
    }

    @Test
    fun `sign is shown with minus sign and optional plus`() {
        assertEquals("−300${nbsp}сом", MoneyFormat.format(-30_000, "KGS"))
        assertEquals("+300${nbsp}сом", MoneyFormat.format(30_000, "KGS", signed = true))
        assertEquals("0${nbsp}сом", MoneyFormat.format(0, "KGS", signed = true))
    }

    @Test
    fun `parses user input into minor units`() {
        assertEquals(1_250_000L, MoneyFormat.parse("12500"))
        assertEquals(1_250_000L, MoneyFormat.parse("12 500"))
        assertEquals(1_250_050L, MoneyFormat.parse("12500,5"))
        assertEquals(1_250_025L, MoneyFormat.parse("12500.25"))
        assertEquals(50L, MoneyFormat.parse(",5"))
        assertEquals(-30_025L, MoneyFormat.parse("-300.25", allowNegative = true))
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(MoneyFormat.parse(""))
        assertNull(MoneyFormat.parse("abc"))
        assertNull(MoneyFormat.parse("1.234"))
        assertNull(MoneyFormat.parse("1.2.3"))
        assertNull(MoneyFormat.parse("."))
        assertNull(MoneyFormat.parse("-5"))
        assertNull(MoneyFormat.parse("1234567890123456"))
    }

    @Test
    fun `input text round-trips`() {
        listOf(0L, 5L, 1_250_000L, 1_250_050L, -30_025L).forEach {
            assertEquals(it, MoneyFormat.parse(MoneyFormat.toInput(it), allowNegative = true))
        }
    }
}
