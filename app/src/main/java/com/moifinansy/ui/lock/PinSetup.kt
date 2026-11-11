package com.moifinansy.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moifinansy.R
import com.moifinansy.domain.security.PinHasher
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinSetupState(val first: String? = null, val input: PinInput = PinInput(), val mismatch: Boolean = false, val done: Boolean = false)

@HiltViewModel
class PinSetupViewModel @Inject constructor(private val settings: SettingsRepository, private val lock: LockManager) : ViewModel() {
    private val _state = MutableStateFlow(PinSetupState())
    val state: StateFlow<PinSetupState> = _state.asStateFlow()

    fun press(d: Char) {
        val s = _state.value
        val digits = s.input.digits + d
        if (digits.length > PinHasher.LENGTH) return
        if (digits.length < PinHasher.LENGTH) {
            _state.value = s.copy(input = PinInput(digits), mismatch = false)
            return
        }
        when {
            s.first == null -> _state.value = PinSetupState(first = digits)
            s.first == digits -> viewModelScope.launch {
                val salt = PinHasher.newSalt()
                settings.setPin(PinHasher.hash(digits, salt), salt)
                lock.keepUnlocked()
                _state.value = s.copy(done = true)
            }
            else -> _state.value = PinSetupState(mismatch = true)
        }
    }

    fun erase() {
        val s = _state.value
        _state.value = s.copy(input = PinInput(s.input.digits.dropLast(1)))
    }
}

@Composable
fun PinSetupScreen(onDone: () -> Unit, viewModel: PinSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    PinPad(
        title = stringResource(if (state.first == null) R.string.pin_setup_enter else R.string.pin_setup_repeat),
        input = state.input,
        error = if (state.mismatch) stringResource(R.string.pin_setup_mismatch) else null,
        onDigit = viewModel::press,
        onErase = viewModel::erase,
    )
}
