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
    val showTypes = MutableStateFlow(true)
    override val showAccountTypes: Flow<Boolean> = showTypes
    val firstDay = MutableStateFlow(java.time.DayOfWeek.MONDAY)
    override val firstDayOfWeek: Flow<java.time.DayOfWeek> = firstDay
    override suspend fun setFirstDayOfWeek(day: java.time.DayOfWeek) {
        firstDay.value = day
    }
    val debtDays = MutableStateFlow(1)
    override val debtReminderDays: Flow<Int> = debtDays
    override suspend fun setDebtReminderDays(days: Int) {
        debtDays.value = days
    }
    val notified = mutableSetOf<String>()
    override suspend fun wasNotified(key: String) = key in notified
    override suspend fun markNotified(key: String) {
        notified += key
    }
    val themeFlow = MutableStateFlow(com.moifinansy.domain.settings.ThemeMode.SYSTEM)
    override val theme: Flow<com.moifinansy.domain.settings.ThemeMode> = themeFlow
    override suspend fun setTheme(mode: com.moifinansy.domain.settings.ThemeMode) {
        themeFlow.value = mode
    }
    val hide = MutableStateFlow(false)
    override val hideAmounts: Flow<Boolean> = hide
    override suspend fun setHideAmounts(hide: Boolean) {
        this.hide.value = hide
    }
    val sec = MutableStateFlow(com.moifinansy.domain.settings.SecuritySettings())
    override val security: Flow<com.moifinansy.domain.settings.SecuritySettings> = sec
    override suspend fun setPin(hash: String?, salt: String?) {
        sec.value = if (hash == null) com.moifinansy.domain.settings.SecuritySettings() else sec.value.copy(pinHash = hash, pinSalt = salt)
    }
    override suspend fun setBiometric(enabled: Boolean) {
        sec.value = sec.value.copy(biometric = enabled)
    }
    override suspend fun setLockAfterMinutes(minutes: Int) {
        sec.value = sec.value.copy(lockAfterMinutes = minutes)
    }
    val lastAccount = MutableStateFlow<Long?>(null)
    override val lastAccountId: Flow<Long?> = lastAccount
    override suspend fun setLastAccountId(id: Long) {
        lastAccount.value = id
    }
    override suspend fun setShowAccountTypes(show: Boolean) {
        showTypes.value = show
    }
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
