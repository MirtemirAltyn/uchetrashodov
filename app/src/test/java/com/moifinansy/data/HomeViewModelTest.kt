package com.moifinansy.data

import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest : DbTest() {
    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `home updates after a debt is recorded`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val settings = FakeSettings().apply { currency.value = "KGS"; completed.value = true }
        val cash = account("Наличные", initial = 10_000)
        val vm = HomeViewModel(accounts, categories, transactions, debts, settings, clock)
        backgroundScope.launch { vm.state.collect {} }
        kotlinx.coroutines.withContext(Dispatchers.Default) { withTimeout(5_000) { vm.state.first { !it.loading && it.total == 10_000L } } }

        val c = debts.createContact(Contact(name = "Азамат"))
        debts.createDebt(Debt(contactId = c, direction = DebtDirection.OWED_TO_ME, principal = 4_000, currencyCode = "KGS", startDate = now), cash)

        val s = kotlinx.coroutines.withContext(Dispatchers.Default) { withTimeout(5_000) { vm.state.first { it.total == 6_000L && it.debtTotals.owedToMe == 4_000L } } }
        assertEquals(4_000L, s.debtTotals.owedToMe)
    }
}
