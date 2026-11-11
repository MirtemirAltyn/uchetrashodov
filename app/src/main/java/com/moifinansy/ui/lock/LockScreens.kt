package com.moifinansy.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moifinansy.R
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.security.PinHasher
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinInput(val digits: String = "", val error: Boolean = false)

@HiltViewModel
class LockViewModel @Inject constructor(private val lock: LockManager, settings: SettingsRepository) : ViewModel() {
    private val _input = MutableStateFlow(PinInput())
    val input: StateFlow<PinInput> = _input.asStateFlow()
    val biometric: StateFlow<Boolean> = settings.security.map { it.biometric }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun press(d: Char) {
        val next = _input.value.digits + d
        if (next.length > PinHasher.LENGTH) return
        _input.value = PinInput(next)
        if (next.length == PinHasher.LENGTH) {
            viewModelScope.launch {
                if (!lock.tryPin(next)) _input.value = PinInput(error = true)
            }
        }
    }

    fun erase() {
        _input.value = PinInput(_input.value.digits.dropLast(1))
    }

    fun biometricSuccess() = lock.unlockedByBiometric()
}

fun canUseBiometric(context: android.content.Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometricPrompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(activity.getString(R.string.action_cancel))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val input by viewModel.input.collectAsStateWithLifecycle()
    val biometric by viewModel.biometric.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val title = stringResource(R.string.lock_biometric_title)
    val activity = context as? FragmentActivity
    val showBio = biometric && activity != null && canUseBiometric(context)
    LaunchedEffect(showBio) { if (showBio) showBiometricPrompt(activity!!, title, viewModel::biometricSuccess) }

    PinPad(
        title = stringResource(R.string.lock_title),
        input = input,
        error = if (input.error) stringResource(R.string.lock_wrong) else null,
        onDigit = viewModel::press,
        onErase = viewModel::erase,
        onBiometric = if (showBio) ({ showBiometricPrompt(activity!!, title, viewModel::biometricSuccess) }) else null,
    )
}

/** Экран ввода PIN: точки и цифровая клавиатура (без системной клавиатуры). */
@Composable
fun PinPad(
    title: String,
    input: PinInput,
    error: String?,
    onDigit: (Char) -> Unit,
    onErase: () -> Unit,
    onBiometric: (() -> Unit)? = null,
) {
    val colors = LocalFinanceColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(Spacing.grid * 3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.grid * 3))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 2)) {
            repeat(PinHasher.LENGTH) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i < input.digits.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Text(error.orEmpty(), color = colors.expense, modifier = Modifier.padding(vertical = Spacing.grid * 2))
        val rows = listOf("123", "456", "789")
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 3)) { row.forEach { d -> Key(d.toString()) { onDigit(d) } } }
            Spacer(Modifier.height(Spacing.grid * 2))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 3), verticalAlignment = Alignment.CenterVertically) {
            if (onBiometric != null) {
                KeyIcon(onBiometric) { Icon(Icons.Rounded.Fingerprint, contentDescription = stringResource(R.string.lock_biometric)) }
            } else {
                Spacer(Modifier.size(72.dp))
            }
            Key("0") { onDigit('0') }
            KeyIcon(onErase) { Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = stringResource(R.string.lock_erase)) }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    KeyIcon(onClick) { Text(label, style = MaterialTheme.typography.headlineMedium) }
}

@Composable
private fun KeyIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
