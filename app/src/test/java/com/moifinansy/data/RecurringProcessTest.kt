package com.moifinansy.data

import com.moifinansy.data.repository.RecurringRepositoryImpl
import com.moifinansy.domain.model.RecurrenceFrequency
import com.moifinansy.domain.model.RecurringMode
import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.usecase.ProcessRecurringUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecurringProcessTest : DbTest() {
    @Test
    fun `auto rules post missed payments, remind rules wait for confirmation`() = runTest {
        val recurring = RecurringRepositoryImpl(db.recurringRuleDao(), db.transactionDao(), clock)
        val card = account("Карта", initial = 100_000)
        val rent = categoryId("Жильё и коммуналка")
        val subs = categoryId("Подписки")
        val start = Instant.parse("2026-08-05T12:00:00Z").toEpochMilli() // now = 2026-11-01
        val auto = recurring.save(
            RecurringRule(type = TransactionType.EXPENSE, amount = 20_000, accountId = card, categoryId = rent, frequency = RecurrenceFrequency.MONTH, nextDate = start, mode = RecurringMode.AUTO),
        )
        val remind = recurring.save(
            RecurringRule(type = TransactionType.EXPENSE, amount = 500, accountId = card, categoryId = subs, frequency = RecurrenceFrequency.MONTH, nextDate = start, mode = RecurringMode.REMIND),
        )

        val result = ProcessRecurringUseCase(recurring, clock)()
        assertEquals(3, result.posted) // 5 авг, 5 сен, 5 окт
        assertEquals(listOf(remind), result.toConfirm.map { it.id })
        assertEquals(100_000L - 3 * 20_000, accounts.balanceOf(card))
        assertTrue(recurring.get(auto)!!.nextDate > now)
        assertTrue(transactions.observeForAccount(card).first().all { it.recurringId == auto })

        // Повторный запуск ничего не дублирует.
        assertEquals(0, ProcessRecurringUseCase(recurring, clock)().posted)

        // Подтверждение напоминания и пропуск.
        recurring.post(remind, recurring.get(remind)!!.nextDate)
        assertEquals(100_000L - 60_000 - 500, accounts.balanceOf(card))
        val before = recurring.get(remind)!!.nextDate
        recurring.skip(remind)
        assertTrue(recurring.get(remind)!!.nextDate > before)
    }
}
