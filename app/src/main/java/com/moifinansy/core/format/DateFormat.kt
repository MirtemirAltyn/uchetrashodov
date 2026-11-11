package com.moifinansy.core.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormat {
    private val russian = Locale.forLanguageTag("ru")
    private val day = DateTimeFormatter.ofPattern("d MMMM yyyy", russian)

    private val dayHeader = DateTimeFormatter.ofPattern("d MMMM, EEEE", russian)
    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", russian)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM", russian)

    /** «11 ноября, среда» */
    fun dayHeader(date: java.time.LocalDate): String = date.format(dayHeader)

    /** «Ноябрь 2026» */
    fun monthYear(month: java.time.YearMonth): String = month.format(monthYear).replaceFirstChar { it.uppercase() }

    /** «11 нояб.» */
    fun shortDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(millis).atZone(zone).format(shortDay)

    private val dayYear = DateTimeFormatter.ofPattern("d MMMM yyyy", russian)
    private val monthShort = DateTimeFormatter.ofPattern("LLL", russian)

    /** Подпись периода отчёта: «11 ноября 2026», «9 нояб. – 15 нояб.», «Ноябрь 2026», «2026». */
    fun period(p: com.moifinansy.domain.model.ReportPeriod, firstDay: java.time.DayOfWeek): String {
        val (from, to) = p.days(firstDay)
        return when (p.kind) {
            com.moifinansy.domain.model.PeriodKind.DAY -> from.format(dayYear)
            com.moifinansy.domain.model.PeriodKind.MONTH -> monthYear(java.time.YearMonth.from(from))
            com.moifinansy.domain.model.PeriodKind.YEAR -> from.year.toString()
            else -> from.format(shortDay) + " – " + to.format(shortDay) + if (from.year != to.year || from.year != java.time.LocalDate.now().year) " " + to.year else ""
        }
    }

    /** «нояб» для подписи столбика. */
    fun monthShort(month: java.time.YearMonth): String = month.atDay(1).format(monthShort).trimEnd('.').take(3)

    fun day(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(day)
}
