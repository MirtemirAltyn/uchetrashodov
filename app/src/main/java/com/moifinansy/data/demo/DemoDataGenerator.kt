package com.moifinansy.data.demo

import androidx.room.withTransaction
import com.moifinansy.data.local.AppDatabase
import com.moifinansy.data.local.entity.AccountEntity
import com.moifinansy.data.local.entity.BudgetEntity
import com.moifinansy.data.local.entity.ContactEntity
import com.moifinansy.data.local.entity.CurrencyRateEntity
import com.moifinansy.data.local.entity.DebtEntity
import com.moifinansy.data.local.entity.RecurringRuleEntity
import com.moifinansy.data.local.entity.TagEntity
import com.moifinansy.data.local.entity.TransactionEntity
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.BudgetPeriod
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.RecurrenceFrequency
import com.moifinansy.domain.model.RecurringMode
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import javax.inject.Inject
import kotlin.random.Random

/**
 * Выдуманные данные «как будто приложением пользовались год»: счета, операции, переводы, долги,
 * бюджеты, регулярные платежи, теги. Всё помечено isDemo и удаляется [DemoDataGenerator.delete].
 * Случайность с фиксированным зерном — картина одинаковая при каждом запуске.
 */
class DemoDataGenerator @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun generate() {
        val base = settings.baseCurrency.first() ?: Currencies.DEFAULT
        val now = clock.millis()
        val today = LocalDate.now(clock)
        val start = today.minusYears(1).plusDays(1)
        val r = Random(42)
        val zone = clock.zone
        fun at(d: LocalDate, hour: Int = 9 + r.nextInt(12)): Long =
            d.atTime(LocalTime.of(hour, r.nextInt(60))).atZone(zone).toInstant().toEpochMilli()
        /** Сумма в основной валюте «как в жизни»: случайная, округлённая до 10 единиц. */
        fun money(from: Int, to: Int) = (from + r.nextInt(to - from + 1)) / 10 * 10 * 100L

        db.withTransaction {
            val accounts = db.accountDao()
            val categories = db.categoryDao()
            val tx = db.transactionDao()
            val debts = db.debtDao()

            fun account(name: String, type: AccountType, currency: String, initial: Long, icon: String, color: Long, order: Int) =
                AccountEntity(
                    name = name, type = type, currencyCode = currency, initialBalance = initial, color = color, icon = icon,
                    includeInTotal = true, isArchived = false, sortOrder = order, createdAt = now, updatedAt = now, isDemo = true,
                )
            val cash = accounts.insert(account("Кошелёк", AccountType.CASH, base, 15_000_00, "payments", 0xFF1F9D6B, 0))
            val card = accounts.insert(account("Карта Мбанк", AccountType.CARD, base, 40_000_00, "credit_card", 0xFF5B5BD6, 1))
            val savingsCurrency = if (base == "USD") base else "USD"
            val savings = accounts.insert(account("Накопления", AccountType.SAVINGS, savingsCurrency, 500_00, "savings", 0xFFF59E0B, 2))
            if (savingsCurrency != base && db.currencyRateDao().get(savingsCurrency) == null) {
                db.currencyRateDao().upsert(CurrencyRateEntity(savingsCurrency, 87_500_000, now, now))
                settings.markNotified(DEMO_RATE_KEY + savingsCurrency)
            }

            // Категории — предустановленные; если пользователь их переименовал, берём «Прочее» (или первую).
            val all = categories.observeByKind(CategoryKind.EXPENSE, true).first() + categories.observeByKind(CategoryKind.INCOME, true).first()
            fun cat(name: String, kind: CategoryKind = CategoryKind.EXPENSE): Long =
                (all.firstOrNull { it.name == name && it.kind == kind } ?: all.firstOrNull { it.name == "Прочее" && it.kind == kind } ?: all.first { it.kind == kind }).id

            val tags = listOf("семья", "работа", "отпуск").associateWith { db.tagDao().insert(TagEntity(name = it + DEMO_TAG_SUFFIX, createdAt = now, updatedAt = now, isDemo = true)) }

            suspend fun op(
                type: TransactionType, amount: Long, accountId: Long, date: Long, categoryId: Long? = null, note: String? = null,
                toAccountId: Long? = null, toAmount: Long? = null, fee: Long = 0, tag: String? = null,
            ) {
                tx.upsertWithTags(
                    TransactionEntity(
                        type = type, amount = amount, accountId = accountId, categoryId = categoryId, toAccountId = toAccountId,
                        toAmount = toAmount, fee = fee, date = date, note = note, debtId = null, recurringId = null, receiptUri = null,
                        createdAt = now, updatedAt = now, isDemo = true,
                    ),
                    listOfNotNull(tag?.let { tags.getValue(it) }),
                    now,
                )
            }
            suspend fun expense(name: String, amount: Long, accountId: Long, d: LocalDate, note: String? = null, tag: String? = null) =
                op(TransactionType.EXPENSE, amount, accountId, at(d), cat(name), note, tag = tag)

            var d = start
            while (!d.isAfter(today)) {
                val dom = d.dayOfMonth
                val monthIndex = (d.year - start.year) * 12 + d.monthValue - start.monthValue
                // Ежемесячное.
                if (dom == 5) op(TransactionType.INCOME, if (monthIndex < 6) 85_000_00 else 95_000_00, card, at(d, 10), cat("Зарплата", CategoryKind.INCOME), "Зарплата")
                if (dom == 3) expense("Жильё и коммуналка", 25_000_00, card, d, "Аренда квартиры")
                if (dom == 15) expense("Жильё и коммуналка", money(2_500, 4_500), card, d, "Коммуналка")
                if (dom == 10) expense("Связь и интернет", 990_00, card, d, "Интернет")
                if (dom == 20) expense("Подписки", 499_00, card, d, "Музыка")
                if (dom == 7) op(TransactionType.TRANSFER, 10_000_00, card, at(d), toAccountId = cash, note = "Снял наличные")
                if (dom == 25 && monthIndex % 3 == 0) {
                    op(TransactionType.TRANSFER, 17_500_00, card, at(d), toAccountId = savings, toAmount = 200_00, fee = 50_00, note = "Отложил в долларах")
                }
                // Будни и выходные.
                val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
                if (d.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)) {
                    val big = d.dayOfWeek == DayOfWeek.SATURDAY
                    expense("Продукты", if (big) money(2_000, 4_500) else money(500, 1_800), if (r.nextInt(10) < 7) card else cash, d, tag = if (big && r.nextBoolean()) "семья" else null)
                }
                if (!weekend && r.nextInt(10) < 6) expense("Общественный транспорт", 40_00, cash, d)
                if (r.nextInt(10) < 3) expense("Такси", money(150, 450), if (r.nextBoolean()) card else cash, d, tag = if (!weekend && r.nextInt(3) == 0) "работа" else null)
                if (r.nextInt(10) < 2) expense("Кафе и рестораны", money(350, 1_800), if (r.nextBoolean()) card else cash, d)
                if (weekend && r.nextInt(10) < 2) expense("Развлечения", money(500, 2_000), card, d)
                if (r.nextInt(100) < 3) expense("Здоровье", money(400, 3_500), card, d)
                if (r.nextInt(100) < 3) expense("Дом", money(300, 2_500), card, d)
                if (r.nextInt(100) < 2) expense("Одежда", money(2_000, 9_000), card, d)
                if (r.nextInt(100) < 2) op(TransactionType.INCOME, money(5_000, 15_000), cash, at(d), cat("Подработка", CategoryKind.INCOME), "Подработка")
                // Праздники и отпуск.
                if ((d.month == Month.DECEMBER && dom == 28) || (d.month == Month.MARCH && dom == 7)) expense("Подарки", money(3_000, 8_000), card, d)
                if (d.month == Month.JULY && dom == 12) {
                    expense("Путешествия", 45_000_00, card, d, "Иссык-Куль", "отпуск")
                    expense("Кафе и рестораны", money(2_000, 4_000), card, d.plusDays(1), "Ужин на берегу", "отпуск")
                }
                d = d.plusDays(1)
            }
            // Пара корректировок наличных.
            op(TransactionType.ADJUSTMENT, -350_00, cash, at(today.minusMonths(7)), note = "Пересчитал кошелёк")
            op(TransactionType.ADJUSTMENT, 200_00, cash, at(today.minusMonths(2)), note = "Нашёл в куртке")

            // Долги.
            fun contact(name: String, phone: String?) = ContactEntity(name = name, phone = phone, note = null, createdAt = now, updatedAt = now, isDemo = true)
            suspend fun debt(contactId: Long, direction: DebtDirection, amount: Long, startDay: LocalDate, due: LocalDate?, accountId: Long?, note: String?): Long =
                debts.createDebt(
                    DebtEntity(
                        contactId = contactId, direction = direction, principal = amount, currencyCode = base, startDate = at(startDay),
                        dueDate = due?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1), note = note,
                        status = DebtStatus.ACTIVE, affectsAccounts = accountId != null, createdAt = now, updatedAt = now, isDemo = true,
                    ),
                    accountId, now,
                )
            val azamat = debts.insertContact(contact("Азамат", "+996 555 123 456"))
            val aigul = debts.insertContact(contact("Айгуль", "+996 700 987 654"))
            val bakyt = debts.insertContact(contact("Бакыт", null))
            val nurlan = debts.insertContact(contact("Нурлан", null))

            val d1 = debt(azamat, DebtDirection.OWED_TO_ME, 15_000_00, today.minusMonths(4), today.plusMonths(1), card, "На ремонт машины")
            debts.addPayment(d1, DebtPaymentKind.REPAY, 5_000_00, card, at(today.minusMonths(2)), null, now)
            val d2 = debt(aigul, DebtDirection.I_OWE, 30_000_00, today.minusMonths(8), today.minusDays(10), card, "На ноутбук")
            debts.addPayment(d2, DebtPaymentKind.REPAY, 10_000_00, card, at(today.minusMonths(6)), null, now)
            debts.addPayment(d2, DebtPaymentKind.REPAY, 10_000_00, card, at(today.minusMonths(3)), null, now)
            val d3 = debt(bakyt, DebtDirection.OWED_TO_ME, 3_000_00, today.minusMonths(6), null, cash, null)
            debts.addPayment(d3, DebtPaymentKind.REPAY, 3_000_00, cash, at(today.minusMonths(5)), null, now)
            debt(nurlan, DebtDirection.OWED_TO_ME, 2_000_00, today.minusMonths(14), null, null, "Старый долг, ещё до приложения")

            // Бюджеты и регулярные платежи (с подтверждением — сами ничего не создают).
            listOf(cat("Продукты") to 30_000_00L, cat("Кафе и рестораны") to 8_000_00L, null to 120_000_00L).forEach { (category, limit) ->
                db.budgetDao().insert(BudgetEntity(categoryId = category, limitAmount = limit, period = BudgetPeriod.MONTH, currencyCode = base, createdAt = now, updatedAt = now, isDemo = true))
            }
            fun nextDay(day: Int) = today.withDayOfMonth(day).let { if (it.isAfter(today)) it else it.plusMonths(1) }
            listOf(
                Triple(cat("Жильё и коммуналка"), 25_000_00L, nextDay(3)) to "Аренда квартиры",
                Triple(cat("Подписки"), 499_00L, nextDay(20)) to "Музыка",
            ).forEach { (t, note) ->
                db.recurringRuleDao().insert(
                    RecurringRuleEntity(
                        type = TransactionType.EXPENSE, amount = t.second, accountId = card, categoryId = t.first, toAccountId = null, toAmount = null,
                        fee = 0, note = note, frequency = RecurrenceFrequency.MONTH, interval = 1, nextDate = at(t.third, 12),
                        mode = RecurringMode.REMIND, endDate = null, createdAt = now, updatedAt = now, isDemo = true,
                    ),
                )
            }
            // Помечаем операции демо-долгов.
            db.openHelper.writableDatabase.execSQL("UPDATE transactions SET isDemo = 1 WHERE debtId IN (SELECT id FROM debts WHERE isDemo = 1)")
        }
        settings.setOnboardingCompleted(true)
    }

    /** Удаляет демо-данные; созданный для демо курс валюты — если им больше никто не пользуется. */
    suspend fun delete() {
        db.demoDao().deleteDemo()
        listOf("USD").forEach { code ->
            if (settings.wasNotified(DEMO_RATE_KEY + code) && db.demoDao().accountsInCurrency(code) == 0) {
                db.currencyRateDao().delete(code)
            }
        }
    }

    companion object {
        private const val DEMO_RATE_KEY = "demo-rate-"
        /** Теги уникальны по имени: демо-теги с пометкой, чтобы не столкнуться с тегами пользователя. */
        const val DEMO_TAG_SUFFIX = " (демо)"
    }
}
