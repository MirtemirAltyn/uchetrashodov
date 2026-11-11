package com.moifinansy.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moifinansy.R
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.budget.BudgetStatus
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.Periods
import com.moifinansy.domain.reminders.Reminder
import com.moifinansy.domain.reminders.ReminderPlanner
import com.moifinansy.domain.repository.BudgetRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.DebtRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.domain.usecase.ProcessRecurringUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Раз в день (и после сохранения расхода): проводит регулярные платежи и показывает напоминания —
 * сроки долгов, бюджеты, платежи на подтверждение. Каждое напоминание — один раз.
 */
@HiltWorker
class DailyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processRecurring: ProcessRecurringUseCase,
    private val debts: DebtRepository,
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.onboardingCompleted.first()) return Result.success()
        val recurring = processRecurring()
        val reminders = mutableListOf<Reminder>()
        recurring.toConfirm.forEach { reminders += Reminder.Recurring(it.id, it.nextDate) }

        val today = LocalDate.now(clock)
        reminders += ReminderPlanner.debts(debts.observeDebts().first(), today, clock.zone, settings.debtReminderDays.first())

        val month = YearMonth.now(clock)
        val range = Periods.month(month, clock.zone)
        val cats = categories.observe(CategoryKind.EXPENSE, includeArchived = true).first().associateBy { it.id }
        val base = settings.baseCurrency.first() ?: Currencies.DEFAULT
        val statuses = BudgetStatus.compute(budgets.observeAll().first(), transactions.expenseByCategory(range.from, range.to, base), cats)
        reminders += ReminderPlanner.budgets(statuses, month)

        val contacts = debts.observeContacts().first().associate { it.id to it.name }
        for (r in reminders) {
            if (settings.wasNotified(r.key)) continue
            val (title, text) = describe(r, contacts, base)
            notifier.show(r.key, title, text)
            settings.markNotified(r.key)
        }
        return Result.success()
    }

    private fun describe(r: Reminder, contacts: Map<Long, String>, base: String): Pair<String, String> {
        val c = applicationContext
        return when (r) {
            is Reminder.DebtDue -> {
                val name = contacts[r.debt.debt.contactId].orEmpty()
                val amount = MoneyFormat.format(r.debt.remaining, r.debt.debt.currencyCode)
                val title = when {
                    r.daysLeft < 0 -> c.getString(R.string.notif_debt_overdue, name)
                    r.daysLeft == 0L -> c.getString(R.string.notif_debt_today, name)
                    else -> c.getString(R.string.notif_debt_soon, r.daysLeft.toInt(), name)
                }
                val text = if (r.debt.debt.direction == DebtDirection.OWED_TO_ME) {
                    c.getString(R.string.notif_debt_text_owed, name, amount)
                } else {
                    c.getString(R.string.notif_debt_text_owe, name, amount)
                }
                title to text
            }
            is Reminder.Budget -> {
                val name = r.status.category?.name ?: c.getString(R.string.budget_all_expenses)
                val title = if (r.status.level == BudgetStatus.Level.EXCEEDED) {
                    c.getString(R.string.notif_budget_exceeded, name)
                } else {
                    c.getString(R.string.notif_budget_warning, name)
                }
                title to c.getString(R.string.notif_budget_text, MoneyFormat.format(r.status.spent, base), MoneyFormat.format(r.status.limit, base))
            }
            is Reminder.Recurring -> c.getString(R.string.notif_recurring) to
                c.getString(R.string.notif_recurring_text, com.moifinansy.core.format.DateFormat.day(r.date))
        }
    }

    companion object {
        private const val PERIODIC = "daily"
        private const val NOW = "daily-now"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build())
            runNow(context)
        }

        /** Проверить сразу (например, после сохранения расхода — для бюджетов). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DailyWorker>().build())
        }
    }
}
