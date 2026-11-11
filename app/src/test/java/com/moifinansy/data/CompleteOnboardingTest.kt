package com.moifinansy.data

import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.domain.usecase.CompleteOnboardingUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSettings : SettingsRepository {
    val currency = MutableStateFlow<String?>(null)
    val completed = MutableStateFlow(false)
    override val baseCurrency: Flow<String?> = currency
    override val onboardingCompleted: Flow<Boolean> = completed
    override suspend fun setBaseCurrency(code: String) {
        currency.value = code
    }
    override suspend fun setOnboardingCompleted(completed: Boolean) {
        this.completed.value = completed
    }
}

class CompleteOnboardingTest : DbTest() {

    @Test
    fun `saves base currency, creates first account and marks onboarding done`() = runTest {
        val settings = FakeSettings()
        val useCase = CompleteOnboardingUseCase(settings, accounts)

        val id = useCase("USD", "  Кошелёк ", AccountType.CASH, 15_000)

        val account = accounts.get(id)!!
        assertEquals("Кошелёк", account.name)
        assertEquals("USD", account.currencyCode)
        assertEquals("payments", account.icon)
        assertEquals(15_000L, accounts.balanceOf(id))
        assertEquals("USD", settings.baseCurrency.first())
        assertTrue(settings.onboardingCompleted.first())
    }

    @Test
    fun `unknown currency or blank name leaves onboarding not done`() = runTest {
        val settings = FakeSettings()
        val useCase = CompleteOnboardingUseCase(settings, accounts)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { useCase("XXX", "Карта", AccountType.CARD, 0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { useCase("KGS", " ", AccountType.CARD, 0) } }
        assertFalse(settings.onboardingCompleted.first())
        assertEquals(0, accounts.observeAccounts().first().size)
    }
}
