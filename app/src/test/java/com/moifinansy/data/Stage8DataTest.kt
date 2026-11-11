package com.moifinansy.data

import com.moifinansy.data.backup.BackupManager
import com.moifinansy.data.backup.CsvExporter
import com.moifinansy.data.repository.CurrencyRateRepositoryImpl
import com.moifinansy.data.repository.TagRepositoryImpl
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.CurrencyRate
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class Stage8DataTest : DbTest() {

    @Test
    fun `other currencies are converted by rate in totals and reports`() = runTest {
        val rates = CurrencyRateRepositoryImpl(db.currencyRateDao(), clock)
        val kgs = account("Сом", initial = 100_000)
        val usd = account("Доллары", initial = 10_000, currency = "USD") // 100 $
        val food = categoryId("Продукты")
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 1_000, accountId = usd, categoryId = food, date = now)) // 10 $
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 5_000, accountId = kgs, categoryId = food, date = now))

        // Без курса долларовый счёт не попадает в итог.
        assertEquals(95_000L, accounts.observeTotal("KGS").first())
        assertEquals(5_000L, transactions.totalExpense(now, now + 1, "KGS"))

        rates.save(CurrencyRate("USD", 87_500_000)) // 1 $ = 87,5 сом
        assertEquals(95_000L + 787_500, accounts.observeTotal("KGS").first())
        assertEquals(5_000L + 87_500, transactions.totalExpense(now, now + 1, "KGS"))
        assertEquals(listOf(food to 92_500L), transactions.expenseByCategory(now, now + 1, "KGS").map { it.categoryId to it.total })
        // Без основной валюты (null) — без пересчёта, как раньше.
        assertEquals(6_000L, transactions.totalExpense(now, now + 1))

        val c = debts.createContact(Contact(name = "Друг"))
        debts.createDebt(Debt(contactId = c, direction = DebtDirection.OWED_TO_ME, principal = 2_000, currencyCode = "USD", startDate = now), usd)
        assertEquals(175_000L, debts.observeTotals("KGS").first().owedToMe)
    }

    @Test
    fun `backup then wipe then restore gives the same data`() = runTest {
        val settings = FakeSettings().apply { currency.value = "KGS" }
        val backup = BackupManager(db, settings)
        val card = account("Карта", initial = 50_000)
        val cash = account("Наличные", initial = 1_000)
        val taxi = categoryId("Такси")
        val salary = categoryId("Зарплата", CategoryKind.INCOME)
        val tag = TagRepositoryImpl(db.tagDao(), clock).create("работа")
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 700, accountId = card, categoryId = taxi, date = now, note = "в офис; \"быстро\""), listOf(tag))
        transactions.save(Transaction(type = TransactionType.INCOME, amount = 90_000, accountId = card, categoryId = salary, date = now))
        transactions.save(Transaction(type = TransactionType.TRANSFER, amount = 5_000, fee = 100, accountId = card, toAccountId = cash, date = now))
        val c = debts.createContact(Contact(name = "Айбек", phone = "+996 555 000 000"))
        val debt = debts.createDebt(Debt(contactId = c, direction = DebtDirection.I_OWE, principal = 3_000, currencyCode = "KGS", startDate = now), cash)
        debts.addPayment(debt, DebtPaymentKind.REPAY, 1_000, cash, now)
        val balancesBefore = accounts.observeAccounts().first().associate { it.account.name to it.balance }
        val categoriesBefore = categories.observe(CategoryKind.EXPENSE, true).first().map { it.name to it.parentId }

        val json = backup.export()
        db.clearAllTables()
        assertEquals(0, accounts.observeAccounts().first().size)

        backup.restore(json)
        assertEquals(balancesBefore, accounts.observeAccounts().first().associate { it.account.name to it.balance })
        assertEquals(categoriesBefore, categories.observe(CategoryKind.EXPENSE, true).first().map { it.name to it.parentId })
        assertEquals(2_000L, debts.get(debt)!!.remaining)
        assertEquals(listOf(tag), transactions.tagIdsOf(transactions.observeForAccount(card).first().first { it.type == TransactionType.EXPENSE }.id))
        assertTrue(settings.completed.value)

        // Испорченный файл не трогает данные.
        runCatching { backup.restore("""{"format":"other"}""") }
        assertEquals(balancesBefore, accounts.observeAccounts().first().associate { it.account.name to it.balance })
    }

    @Test
    fun `csv has header, escaped notes, tags and comma decimals`() = runTest {
        val card = account("Карта", initial = 0)
        val taxi = categoryId("Такси")
        val tag = TagRepositoryImpl(db.tagDao(), clock).create("работа")
        transactions.save(Transaction(type = TransactionType.EXPENSE, amount = 70_050, accountId = card, categoryId = taxi, date = now, note = "в офис; \"быстро\""), listOf(tag))
        val csv = CsvExporter.build(
            transactions.observeForAccount(card).first(),
            accounts.observeAccounts().first().associate { it.account.id to it.account },
            categories.observe(CategoryKind.EXPENSE, true).first().associateBy { it.id },
            transactions.tagNamesByTransaction(),
            ZoneOffset.UTC,
        )
        val lines = csv.removePrefix("﻿").trim().lines()
        assertEquals("Дата;Тип;Сумма;Валюта;Счёт;На счёт;Сумма зачисления;Комиссия;Категория;Подкатегория;Комментарий;Теги", lines[0])
        assertEquals("2026-11-01 10:00;Расход;700,50;KGS;Карта;;;;Транспорт;Такси;\"в офис; \"\"быстро\"\"\";работа", lines[1])
    }
}
