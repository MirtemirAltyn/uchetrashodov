package com.moifinansy.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val baseCurrency: StateFlow<String> = settings.baseCurrency
        .map { it ?: Currencies.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Currencies.DEFAULT)
}
