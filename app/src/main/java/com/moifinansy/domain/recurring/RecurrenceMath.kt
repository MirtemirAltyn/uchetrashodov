package com.moifinansy.domain.recurring

import com.moifinansy.domain.model.RecurrenceFrequency
import java.time.Instant
import java.time.ZoneId

object RecurrenceMath {
    /** Следующая дата после [date] с шагом [interval] единиц (с учётом коротких месяцев: 31 янв → 28/29 фев). */
    fun next(date: Long, frequency: RecurrenceFrequency, interval: Int, zone: ZoneId): Long {
        require(interval > 0)
        val t = Instant.ofEpochMilli(date).atZone(zone)
        val n = interval.toLong()
        val next = when (frequency) {
            RecurrenceFrequency.DAY -> t.plusDays(n)
            RecurrenceFrequency.WEEK -> t.plusWeeks(n)
            RecurrenceFrequency.MONTH -> t.plusMonths(n)
            RecurrenceFrequency.YEAR -> t.plusYears(n)
        }
        return next.toInstant().toEpochMilli()
    }

    /** Все даты платежей правила, наступившие к [now] (начиная с [nextDate]), не больше [limit]. */
    fun dueDates(nextDate: Long, frequency: RecurrenceFrequency, interval: Int, endDate: Long?, now: Long, zone: ZoneId, limit: Int = 366): List<Long> {
        val result = mutableListOf<Long>()
        var d = nextDate
        while (d <= now && (endDate == null || d <= endDate) && result.size < limit) {
            result += d
            d = next(d, frequency, interval, zone)
        }
        return result
    }
}
