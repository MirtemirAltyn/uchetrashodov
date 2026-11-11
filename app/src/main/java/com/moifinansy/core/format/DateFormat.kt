package com.moifinansy.core.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormat {
    private val russian = Locale.forLanguageTag("ru")
    private val day = DateTimeFormatter.ofPattern("d MMMM yyyy", russian)

    fun day(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).format(day)
}
