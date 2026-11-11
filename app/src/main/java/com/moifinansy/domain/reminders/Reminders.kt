package com.moifinansy.domain.reminders

import com.moifinansy.domain.budget.BudgetStatus
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.DebtWithRemaining
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Что напомнить; [key] — чтобы одно и то же напоминание не приходило дважды. */
sealed interface Reminder {
    val key: String

    data class DebtDue(val debt: DebtWithRemaining, val daysLeft: Long) : Reminder {
        override val key = "debt-${debt.debt.id}-${debt.debt.dueDate}-$daysLeft"
    }

    data class Budget(val status: BudgetStatus, val month: YearMonth) : Reminder {
        override val key = "budget-${status.budget.id}-$month-${status.level}"
    }

    data class Recurring(val ruleId: Long, val date: Long) : Reminder {
        override val key = "recurring-$ruleId-$date"
    }
}

object ReminderPlanner {
    /** Долги: за [daysBefore] дней до срока и в день срока (и просроченные — один раз). */
    fun debts(debts: List<DebtWithRemaining>, today: LocalDate, zone: ZoneId, daysBefore: Int): List<Reminder.DebtDue> =
        debts.filter { it.debt.status == DebtStatus.ACTIVE && it.debt.dueDate != null }.mapNotNull { d ->
            val due = Instant.ofEpochMilli(d.debt.dueDate!!).atZone(zone).toLocalDate()
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            when {
                days == 0L -> Reminder.DebtDue(d, 0)
                daysBefore > 0 && days == daysBefore.toLong() -> Reminder.DebtDue(d, days)
                days < 0 -> Reminder.DebtDue(d, -1) // просрочен: ключ общий, придёт один раз
                else -> null
            }
        }

    fun budgets(statuses: List<BudgetStatus>, month: YearMonth): List<Reminder.Budget> =
        statuses.filter { it.level != BudgetStatus.Level.OK }.map { Reminder.Budget(it, month) }
}
