package com.moifinansy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ReportPeriodTest {
    private val wed = LocalDate.of(2026, 11, 11)

    @Test
    fun `week respects first day of week`() {
        assertEquals(LocalDate.of(2026, 11, 9) to LocalDate.of(2026, 11, 15), ReportPeriod(PeriodKind.WEEK, wed).days(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 8) to LocalDate.of(2026, 11, 14), ReportPeriod(PeriodKind.WEEK, wed).days(DayOfWeek.SUNDAY))
    }

    @Test
    fun `month, year and day`() {
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30), ReportPeriod(PeriodKind.MONTH, wed).days())
        assertEquals(30L, ReportPeriod(PeriodKind.MONTH, wed).dayCount())
        assertEquals(365L, ReportPeriod(PeriodKind.YEAR, wed).dayCount())
        assertEquals(wed to wed, ReportPeriod(PeriodKind.DAY, wed).days())
        assertEquals(LocalDate.of(2026, 10, 1), ReportPeriod(PeriodKind.MONTH, wed).previous().days().first)
    }

    @Test
    fun `custom period shifts by its own length`() {
        val p = ReportPeriod(PeriodKind.CUSTOM, wed, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10))
        assertEquals(LocalDate.of(2026, 10, 22) to LocalDate.of(2026, 10, 31), p.previous().days())
        assertEquals(LocalDate.of(2026, 11, 11) to LocalDate.of(2026, 11, 20), p.shift(1).days())
    }

    @Test
    fun `change percent`() {
        assertEquals(50, changePercent(150, 100))
        assertEquals(-25, changePercent(75, 100))
        assertNull(changePercent(10, 0))
    }
}
