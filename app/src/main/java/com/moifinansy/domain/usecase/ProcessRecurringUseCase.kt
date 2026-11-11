package com.moifinansy.domain.usecase

import com.moifinansy.domain.model.RecurringMode
import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.recurring.RecurrenceMath
import com.moifinansy.domain.repository.RecurringRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Проводит наступившие регулярные платежи. AUTO — создаёт операции (в том числе пропущенные дни),
 * REMIND — только возвращает список для напоминания. Каждая операция и сдвиг даты — одна транзакция БД.
 */
class ProcessRecurringUseCase @Inject constructor(
    private val recurring: RecurringRepository,
    private val clock: Clock,
) {
    data class Result(val posted: Int, val toConfirm: List<RecurringRule>)

    suspend operator fun invoke(): Result {
        val now = clock.millis()
        var posted = 0
        val toConfirm = mutableListOf<RecurringRule>()
        for (rule in recurring.dueBy(now)) {
            if (rule.mode == RecurringMode.REMIND) {
                toConfirm += rule
                continue
            }
            for (date in RecurrenceMath.dueDates(rule.nextDate, rule.frequency, rule.interval, rule.endDate, now, clock.zone)) {
                recurring.post(rule.id, date)
                posted++
            }
        }
        return Result(posted, toConfirm)
    }
}
