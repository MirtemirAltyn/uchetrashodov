package com.moifinansy.ui.debts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moifinansy.R
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.contacts.PhoneNumbers
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val countryCode: StateFlow<String> = settings.phoneCountryCode.stateIn(viewModelScope, SharingStarted.Eagerly, PhoneNumbers.DEFAULT_COUNTRY)
}

/** Что напомнить и кому. */
data class ReminderRequest(val name: String, val phone: String?, val text: String)

@Stable
class ReminderLauncher internal constructor() {
    var request by mutableStateOf<ReminderRequest?>(null)
        internal set

    fun open(context: Context, debt: DebtWithRemaining, name: String, phone: String?) {
        request = ReminderRequest(name, phone, reminderText(context, debt, name))
    }

    fun close() {
        request = null
    }
}

/** Кнопка «Напомнить»: выбор WhatsApp (сразу чат с человеком), SMS или другого приложения. */
@Composable
fun rememberReminder(viewModel: ReminderViewModel = hiltViewModel()): ReminderLauncher {
    val launcher = remember { ReminderLauncher() }
    val country by viewModel.countryCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    launcher.request?.let { r -> ReminderDialog(r, country, context, onDone = launcher::close) }
    return launcher
}

private val WHATSAPP_PACKAGES = listOf("com.whatsapp" to R.string.remind_whatsapp, "com.whatsapp.w4b" to R.string.remind_whatsapp_business)

private fun installed(context: Context, pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

@Composable
private fun ReminderDialog(r: ReminderRequest, country: String, context: Context, onDone: () -> Unit) {
    val phone = PhoneNumbers.normalize(r.phone, country)
    fun start(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { shareReminder(context, r.text) }
        onDone()
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.remind_title)) },
        text = {
            Column {
                Text(
                    if (phone != null) stringResource(R.string.remind_to, r.name, phone) else r.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (phone != null) {
                    WHATSAPP_PACKAGES.filter { installed(context, it.first) }.forEach { (pkg, label) ->
                        Option(stringResource(label)) {
                            val link = PhoneNumbers.whatsAppLink(phone, r.text, country)!!
                            start(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(pkg))
                        }
                    }
                    Option(stringResource(R.string.remind_sms)) {
                        start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).putExtra("sms_body", r.text))
                    }
                } else {
                    Text(stringResource(R.string.remind_no_phone), modifier = Modifier.padding(top = Spacing.grid))
                }
                Option(stringResource(R.string.remind_other)) {
                    shareReminder(context, r.text)
                    onDone()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.grid * 1.5f),
    )
}
