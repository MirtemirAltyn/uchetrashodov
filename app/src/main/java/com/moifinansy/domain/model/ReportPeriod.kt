package com.moifinansy.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { DAY, WEEK, MONTH, YEAR, CUSTOM }

/** Период отчёта: тип + опорная дата (для CUSTOM — явные границы, обе включительно). */
data class ReportPeriod(
    val kind: PeriodKind,
    val anchor: LocalDate,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
) {
    /** Первый и последний день периода (включительно). */
    fun days(firstDay: DayOfWeek = DayOfWeek.MONDAY): Pair<LocalDate, LocalDate> = when (kind) {
        PeriodKind.DAY -> anchor to anchor
        PeriodKind.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(firstDay)).let { it to it.plusDays(6) }
        PeriodKind.MONTH -> YearMonth.from(anchor).let { it.atDay(1) to it.atEndOfMonth() }
        PeriodKind.YEAR -> LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
        PeriodKind.CUSTOM -> (customFrom ?: anchor) to (customTo ?: anchor)
    }

    fun range(zone: ZoneId, firstDay: DayOfWeek = DayOfWeek.MONDAY): DateRange {
        val (start, end) = days(firstDay)
        return DateRange(start.atStartOfDay(zone).toInstant().toEpochMilli(), end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun dayCount(firstDay: DayOfWeek = DayOfWeek.MONDAY): Long = days(firstDay).let { ChronoUnit.DAYS.between(it.first, it.second) + 1 }

    fun shift(steps: Long, firstDay: DayOfWeek = DayOfWeek.MONDAY): ReportPeriod = when (kind) {
        PeriodKind.DAY -> copy(anchor = anchor.plusDays(steps))
        PeriodKind.WEEK -> copy(anchor = anchor.plusWeeks(steps))
        PeriodKind.MONTH -> copy(anchor = anchor.plusMonths(steps))
        PeriodKind.YEAR -> copy(anchor = anchor.plusYears(steps))
        PeriodKind.CUSTOM -> {
            val (from, to) = days(firstDay)
            val length = ChronoUnit.DAYS.between(from, to) + 1
            copy(customFrom = from.plusDays(length * steps), customTo = to.plusDays(length * steps))
        }
    }

    /** Предыдущий период такой же длины — для сравнения. */
    fun previous(firstDay: DayOfWeek = DayOfWeek.MONDAY): ReportPeriod = shift(-1, firstDay)

    companion object {
        fun month(today: LocalDate) = ReportPeriod(PeriodKind.MONTH, today)
    }
}

/** Изменение в процентах относительно прошлого периода; null — сравнивать не с чем. */
fun changePercent(current: Long, previous: Long): Int? =
    if (previous == 0L) null else (((current - previous) * 100) / previous).toInt()
