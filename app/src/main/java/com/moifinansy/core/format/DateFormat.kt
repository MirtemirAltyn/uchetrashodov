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

    fun day(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(day)
}
