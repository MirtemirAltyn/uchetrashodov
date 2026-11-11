package com.moifinansy.data

import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class MonthTotalsTest : DbTest() {
    @Test
    fun `month totals include fees as expense and net covers adjustments`() = runTest {
        val zone = ZoneId.systemDefault()
        fun at(month: YearMonth, day: Int) = month.atDay(day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val oct = YearMonth.of(2026, 10)
        val nov = YearMonth.of(2026, 11)
        val card = account("Карта", initial = 10_000)
        val cash = account("Наличные")
        val food = categoryId("Продукты")
        val taxi = categoryId("Такси")
        val transport = categoryId("Транспорт")
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        transactions.save(Transaction(type = TransactionType.INCOME, amount = 5_000, accountId = card, categoryId = salary, date = at(oct, 5)))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 1_000, accountId = card, categoryId = food, date = at(oct, 6)))
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 300, accountId = card, categoryId = taxi, date = at(nov, 2)))
        transactions.save(Transaction(type = TransactionType.TRANSFER, amount = 2_000, fee = 50, accountId = card, toAccountId = cash, date = at(nov, 3)))
        transactions.adjustBalance(cash, 1_900, at(nov, 4))

        val from = oct.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val totals = transactions.observeMonthTotals(from, Long.MAX_VALUE).first().associateBy { it.month }
        assertEquals(5_000L, totals[oct]!!.income)
        assertEquals(1_000L, totals[oct]!!.expense)
        assertEquals(4_000L, totals[oct]!!.net)
        assertEquals(350L, totals[nov]!!.expense)
        assertEquals(-300L - 50 - 100, totals[nov]!!.net)

        val months = transactions.observeCategoryMonths(transport, from, Long.MAX_VALUE).first()
        assertEquals(mapOf(nov to 300L), months)
    }
}
