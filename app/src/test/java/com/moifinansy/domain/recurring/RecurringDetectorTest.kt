package com.moifinansy.domain.recurring

import com.moifinansy.domain.model.RecurrenceFrequency
import com.moifinansy.domain.model.RecurringMode
import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RecurringDetectorTest {
    private val zone = ZoneOffset.UTC
    private val now = at(LocalDate.of(2026, 11, 11))
    private fun at(d: LocalDate) = d.atTime(12, 0).toInstant(zone).toEpochMilli()
    private fun tx(d: LocalDate, amount: Long = 50_000, category: Long = 7, account: Long = 1, type: TransactionType = TransactionType.EXPENSE, recurringId: Long? = null) =
        Transaction(type = type, amount = amount, accountId = account, categoryId = category, date = at(d), recurringId = recurringId)

    @Test
    fun `same amount around the same day three months in a row is suggested`() {
        val list = listOf(
            tx(LocalDate.of(2026, 8, 5)), tx(LocalDate.of(2026, 9, 6)), tx(LocalDate.of(2026, 10, 4)), tx(LocalDate.of(2026, 11, 5)),
            tx(LocalDate.of(2026, 10, 20), amount = 1_234), // другая сумма — не мешает
        )
        val s = RecurringDetector.detect(list, emptyList(), emptySet(), now, zone).single()
        assertEquals(50_000L, s.amount)
        assertEquals(4, s.months)
        assertEquals(5, s.dayOfMonth)
        assertEquals(at(LocalDate.of(2026, 12, 5)), s.nextDate)
    }

    @Test
    fun `not suggested when days scatter, months have a gap, rule exists, dismissed or too old`() {
        val scattered = listOf(tx(LocalDate.of(2026, 9, 1)), tx(LocalDate.of(2026, 10, 15)), tx(LocalDate.of(2026, 11, 2)))
        assertTrue(RecurringDetector.detect(scattered, emptyList(), emptySet(), now, zone).isEmpty())

        val gap = listOf(tx(LocalDate.of(2026, 7, 5)), tx(LocalDate.of(2026, 8, 5)), tx(LocalDate.of(2026, 10, 5)), tx(LocalDate.of(2026, 11, 5)))
        assertTrue(RecurringDetector.detect(gap, emptyList(), emptySet(), now, zone).isEmpty())

        val good = listOf(tx(LocalDate.of(2026, 9, 5)), tx(LocalDate.of(2026, 10, 5)), tx(LocalDate.of(2026, 11, 5)))
        val rule = RecurringRule(type = TransactionType.EXPENSE, amount = 50_000, accountId = 1, categoryId = 7, frequency = RecurrenceFrequency.MONTH, nextDate = 0, mode = RecurringMode.AUTO)
        assertTrue(RecurringDetector.detect(good, listOf(rule), emptySet(), now, zone).isEmpty())
        val key = RecurringDetector.key(TransactionType.EXPENSE, 7, 1, 50_000)
        assertTrue(RecurringDetector.detect(good, emptyList(), setOf(key), now, zone).isEmpty())

        val old = listOf(tx(LocalDate.of(2026, 5, 5)), tx(LocalDate.of(2026, 6, 5)), tx(LocalDate.of(2026, 7, 5)))
        assertTrue(RecurringDetector.detect(old, emptyList(), emptySet(), now, zone).isEmpty())

        // Операции, уже созданные регулярным платежом, не считаются.
        val fromRule = good.map { it.copy(recurringId = 3) }
        assertTrue(RecurringDetector.detect(fromRule, emptyList(), emptySet(), now, zone).isEmpty())
    }

    @Test
    fun `end of month dates count as the same day`() {
        val list = listOf(tx(LocalDate.of(2026, 8, 31)), tx(LocalDate.of(2026, 9, 30)), tx(LocalDate.of(2026, 10, 31)))
        val now = at(LocalDate.of(2026, 11, 3))
        assertEquals(1, RecurringDetector.detect(list, emptyList(), emptySet(), now, zone).size)
    }
}
