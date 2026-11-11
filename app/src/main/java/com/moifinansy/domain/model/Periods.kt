package com.moifinansy.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Период отчёта/списка: полуоткрытый [from, to) в epoch millis. */
data class DateRange(val from: Long, val to: Long)

object Periods {
    fun day(date: LocalDate, zone: ZoneId) = DateRange(date.startMillis(zone), date.plusDays(1).startMillis(zone))

    fun week(date: LocalDate, zone: ZoneId, firstDay: DayOfWeek = DayOfWeek.MONDAY): DateRange {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
        return DateRange(start.startMillis(zone), start.plusWeeks(1).startMillis(zone))
    }

    fun month(month: YearMonth, zone: ZoneId) =
        DateRange(month.atDay(1).startMillis(zone), month.plusMonths(1).atDay(1).startMillis(zone))

    fun year(year: Int, zone: ZoneId) =
        DateRange(LocalDate.of(year, 1, 1).startMillis(zone), LocalDate.of(year + 1, 1, 1).startMillis(zone))

    /** Всё время: от начала эпохи до далёкого будущего. */
    val ALL = DateRange(0, Long.MAX_VALUE)

    private fun LocalDate.startMillis(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()
}
