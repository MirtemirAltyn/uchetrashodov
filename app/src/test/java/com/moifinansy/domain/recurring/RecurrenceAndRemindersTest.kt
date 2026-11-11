package com.moifinansy.domain.recurring

import com.moifinansy.domain.budget.BudgetStatus
import com.moifinansy.domain.model.Budget
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.CategoryTotal
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.model.RecurrenceFrequency
import com.moifinansy.domain.reminders.ReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class RecurrenceAndRemindersTest {
    private val zone = ZoneOffset.UTC
    private fun at(d: LocalDate) = d.atTime(12, 0).toInstant(zone).toEpochMilli()

    @Test
    fun `monthly step clamps to end of short month`() {
        val next = RecurrenceMath.next(at(LocalDate.of(2026, 1, 31)), RecurrenceFrequency.MONTH, 1, zone)
        assertEquals(at(LocalDate.of(2026, 2, 28)), next)
        assertEquals(at(LocalDate.of(2026, 1, 15)), RecurrenceMath.next(at(LocalDate.of(2026, 1, 1)), RecurrenceFrequency.WEEK, 2, zone))
    }

    @Test
    fun `due dates catch up to now and respect end date`() {
        val start = at(LocalDate.of(2026, 8, 10))
        val now = at(LocalDate.of(2026, 11, 11))
        assertEquals(4, RecurrenceMath.dueDates(start, RecurrenceFrequency.MONTH, 1, null, now, zone).size)
        assertEquals(2, RecurrenceMath.dueDates(start, RecurrenceFrequency.MONTH, 1, at(LocalDate.of(2026, 9, 30)), now, zone).size)
        assertEquals(0, RecurrenceMath.dueDates(now + 1, RecurrenceFrequency.DAY, 1, null, now, zone).size)
    }

    @Test
    fun `budget levels and spending with subcategories`() {
        val cats = listOf(
            Category(id = 1, name = "Транспорт", kind = CategoryKind.EXPENSE, icon = "x", color = 0),
            Category(id = 2, name = "Такси", kind = CategoryKind.EXPENSE, parentId = 1, icon = "x", color = 0),
            Category(id = 3, name = "Еда", kind = CategoryKind.EXPENSE, icon = "x", color = 0),
        ).associateBy { it.id }
        val rows = listOf(CategoryTotal(1, 300), CategoryTotal(2, 550), CategoryTotal(3, 100))
        val statuses = BudgetStatus.compute(
            listOf(
                Budget(id = 10, categoryId = 1, limitAmount = 1_000, currencyCode = "KGS"),
                Budget(id = 11, categoryId = 3, limitAmount = 100, currencyCode = "KGS"),
                Budget(id = 12, categoryId = null, limitAmount = 5_000, currencyCode = "KGS"),
            ),
            rows, cats,
        )
        assertEquals(listOf(850L, 100L, 950L), statuses.map { it.spent })
        assertEquals(listOf(BudgetStatus.Level.WARNING, BudgetStatus.Level.EXCEEDED, BudgetStatus.Level.OK), statuses.map { it.level })
        assertEquals(listOf(10L, 11L), ReminderPlanner.budgets(statuses, YearMonth.of(2026, 11)).map { it.status.budget.id })
    }

    @Test
    fun `debt reminders N days before, on the day and once when overdue`() {
        val today = LocalDate.of(2026, 11, 11)
        fun debt(id: Long, due: LocalDate?, status: DebtStatus = DebtStatus.ACTIVE) = DebtWithRemaining(
            Debt(id = id, contactId = 1, direction = DebtDirection.OWED_TO_ME, principal = 100, currencyCode = "KGS", startDate = 0, dueDate = due?.let { at(it) }, status = status),
            remaining = 100,
        )
        val list = listOf(
            debt(1, today.plusDays(3)), debt(2, today), debt(3, today.minusDays(5)), debt(4, today.plusDays(2)),
            debt(5, null), debt(6, today, DebtStatus.CLOSED),
        )
        val reminders = ReminderPlanner.debts(list, today, zone, daysBefore = 3)
        assertEquals(listOf(1L to 3L, 2L to 0L, 3L to -1L), reminders.map { it.debt.debt.id to it.daysLeft })
    }
}
