package com.moifinansy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CurrencyRateRepository
import com.moifinansy.domain.settings.SecuritySettings
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class SettingsState(
    val showAccountTypes: Boolean = true,
    val debtReminderDays: Int = 1,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val hideAmounts: Boolean = false,
    val baseCurrency: String = Currencies.DEFAULT,
    val missingRates: List<String> = emptyList(),
    val security: SecuritySettings = SecuritySettings(),
    val debtsAffectBalance: Boolean = true,
    val blockScreenshots: Boolean = false,
    val phoneCountry: String = com.moifinansy.domain.contacts.PhoneNumbers.DEFAULT_COUNTRY,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val rates: CurrencyRateRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val basic = combine(settings.showAccountTypes, settings.debtReminderDays, settings.theme, settings.firstDayOfWeek, settings.hideAmounts) {
            types, days, theme, firstDay, hide ->
        SettingsState(showAccountTypes = types, debtReminderDays = days, theme = theme, firstDay = firstDay, hideAmounts = hide)
    }

    val state: StateFlow<SettingsState> = combine(basic, settings.baseCurrency, settings.security, settings.debtsAffectBalance, settings.blockScreenshots) {
            b, base, security, debts, block ->
        b.copy(baseCurrency = base ?: Currencies.DEFAULT, security = security, debtsAffectBalance = debts, blockScreenshots = block)
    }.flatMapLatest { s ->
        combine(accounts.observeCurrenciesWithoutRate(s.baseCurrency), settings.phoneCountryCode) { missing, country ->
            s.copy(missingRates = missing, phoneCountry = country)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun setShowAccountTypes(value: Boolean) = launch { settings.setShowAccountTypes(value) }
    fun setDebtReminderDays(days: Int) = launch { settings.setDebtReminderDays(days) }
    fun setDebtsAffectBalance(value: Boolean) = launch { settings.setDebtsAffectBalance(value) }
    fun setTheme(mode: ThemeMode) = launch { settings.setTheme(mode) }
    fun setFirstDay(day: DayOfWeek) = launch { settings.setFirstDayOfWeek(day) }
    fun setHideAmounts(value: Boolean) = launch { settings.setHideAmounts(value) }
    fun setBiometric(value: Boolean) = launch { settings.setBiometric(value) }
    fun setLockAfter(minutes: Int) = launch { settings.setLockAfterMinutes(minutes) }
    fun disablePin() = launch { settings.setPin(null, null) }
    fun setPhoneCountry(code: String) = launch { settings.setPhoneCountryCode(code) }
    fun setBlockScreenshots(value: Boolean) = launch { settings.setBlockScreenshots(value) }

    /** Курсы задаются к основной валюте — при её смене старые курсы теряют смысл. */
    fun setBaseCurrency(code: String) = launch {
        if (code == state.value.baseCurrency) return@launch
        settings.setBaseCurrency(code)
        rates.deleteAll()
    }
}
