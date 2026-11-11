package com.moifinansy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(val showAccountTypes: Boolean = true, val debtReminderDays: Int = 1)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<SettingsState> = kotlinx.coroutines.flow.combine(settings.showAccountTypes, settings.debtReminderDays) { types, days ->
        SettingsState(showAccountTypes = types, debtReminderDays = days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setDebtReminderDays(days: Int) {
        viewModelScope.launch { settings.setDebtReminderDays(days) }
    }

    fun setShowAccountTypes(value: Boolean) {
        viewModelScope.launch { settings.setShowAccountTypes(value) }
    }
}
