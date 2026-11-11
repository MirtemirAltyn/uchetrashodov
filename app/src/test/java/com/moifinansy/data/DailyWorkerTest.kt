package com.moifinansy.data

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.moifinansy.data.repository.BudgetRepositoryImpl
import com.moifinansy.data.repository.RecurringRepositoryImpl
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.usecase.ProcessRecurringUseCase
import com.moifinansy.work.DailyWorker
import com.moifinansy.work.Notifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class DailyWorkerTest : DbTest() {
    @Test
    fun `debt due today notifies once`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val settings = FakeSettings().apply { completed.value = true; currency.value = "KGS" }
        val card = account("Карта", initial = 10_000)
        val c = debts.createContact(Contact(name = "Эрмек"))
        debts.createDebt(
            Debt(contactId = c, direction = DebtDirection.OWED_TO_ME, principal = 3_000, currencyCode = "KGS", startDate = now - 86_400_000, dueDate = now + 3_600_000),
            card,
        )
        val recurring = RecurringRepositoryImpl(db.recurringRuleDao(), db.transactionDao(), clock)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                DailyWorker(
                    appContext, workerParameters, ProcessRecurringUseCase(recurring, clock), debts,
                    BudgetRepositoryImpl(db.budgetDao(), clock), transactions, categories, settings, Notifier(appContext), clock,
                )
        }
        val manager = app.getSystemService(NotificationManager::class.java)

        val worker = TestListenableWorkerBuilder<DailyWorker>(app).setWorkerFactory(factory).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val posted = shadowOf(manager).allNotifications
        assertEquals(1, posted.size)
        assertEquals("Сегодня срок долга: Эрмек", shadowOf(posted[0]).contentTitle)

        TestListenableWorkerBuilder<DailyWorker>(app).setWorkerFactory(factory).build().doWork()
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
}
