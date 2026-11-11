package com.moifinansy.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val step: Step = Step.CURRENCY,
    val currencyCode: String = Currencies.DEFAULT,
    val accountName: String = "",
    val accountType: AccountType = AccountType.CASH,
    val balanceText: String = "",
    val nameError: Boolean = false,
    val balanceError: Boolean = false,
    val saving: Boolean = false,
) {
    enum class Step { CURRENCY, ACCOUNT }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val settings: com.moifinansy.domain.settings.SettingsRepository,
    private val demo: com.moifinansy.data.demo.DemoDataGenerator,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    /** Онбординг всегда начинается с первого шага (в том числе после «начать с чистого листа»). */
    fun restart() {
        _state.value = OnboardingState()
    }

    /** Подставляет название счёта по умолчанию, если пользователь ещё ничего не ввёл. */
    fun suggestName(name: String) = _state.update { if (it.accountName.isEmpty()) it.copy(accountName = name) else it }

    fun selectCurrency(code: String) = _state.update { it.copy(currencyCode = code) }
    fun next() = _state.update { it.copy(step = OnboardingState.Step.ACCOUNT) }
    fun back() = _state.update { it.copy(step = OnboardingState.Step.CURRENCY) }
    fun setName(name: String) = _state.update { it.copy(accountName = name, nameError = false) }
    fun setType(type: AccountType) = _state.update { it.copy(accountType = type) }
    fun setBalance(text: String) = _state.update { it.copy(balanceText = text, balanceError = false) }

    /** Пропустить настройку и посмотреть приложение с демо-данными в выбранной валюте. */
    fun startWithDemo() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                settings.setBaseCurrency(_state.value.currencyCode)
                demo.generate()
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /** Сохраняет валюту и первый счёт. Экран сменится сам, когда онбординг отметится пройденным. */
    fun finish() {
        val s = _state.value
        if (s.saving) return
        val balance = if (s.balanceText.isBlank()) 0L else MoneyFormat.parse(s.balanceText, allowNegative = true)
        val nameError = s.accountName.isBlank()
        if (nameError || balance == null) {
            _state.update { it.copy(nameError = nameError, balanceError = balance == null) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                completeOnboarding(s.currencyCode, s.accountName, s.accountType, balance)
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
