package com.moifinansy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.domain.settings.ThemeMode
import com.moifinansy.ui.lock.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class AppState { LOADING, ONBOARDING, READY }

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository, lock: LockManager) : ViewModel() {
    val state: StateFlow<AppState> = settings.onboardingCompleted
        .map { if (it) AppState.READY else AppState.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState.LOADING)

    val theme: StateFlow<ThemeMode> = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** Экран блокировки — только если PIN задан и сеанс заблокирован. */
    val locked: StateFlow<Boolean> = combine(lock.locked, settings.security) { locked, security -> locked && security.pinEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
