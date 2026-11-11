package com.moifinansy.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.CurrencyDropdown
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.settings.ThemeMode
import com.moifinansy.ui.lock.canUseBiometric
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRates: () -> Unit,
    onSetPin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.settings_section_look))
            Chips {
                listOf(ThemeMode.SYSTEM to R.string.settings_theme_system, ThemeMode.LIGHT to R.string.settings_theme_light, ThemeMode.DARK to R.string.settings_theme_dark)
                    .forEach { (mode, label) -> FilterChip(state.theme == mode, { viewModel.setTheme(mode) }, label = { Text(stringResource(label)) }) }
            }
            Label(stringResource(R.string.settings_first_day))
            Chips {
                listOf(DayOfWeek.MONDAY to R.string.settings_monday, DayOfWeek.SUNDAY to R.string.settings_sunday)
                    .forEach { (day, label) -> FilterChip(state.firstDay == day, { viewModel.setFirstDay(day) }, label = { Text(stringResource(label)) }) }
            }
            SwitchRow(stringResource(R.string.settings_hide_amounts), stringResource(R.string.settings_hide_amounts_hint), state.hideAmounts, viewModel::setHideAmounts)

            Section(stringResource(R.string.settings_section_currency))
            Column(Modifier.padding(horizontal = Spacing.screenHorizontal)) {
                CurrencyDropdown(state.baseCurrency, viewModel::setBaseCurrency, supportingText = stringResource(R.string.settings_base_currency_hint))
            }
            LinkRow(
                stringResource(R.string.settings_rates),
                if (state.missingRates.isEmpty()) null else stringResource(R.string.settings_rates_missing, state.missingRates.joinToString(", ")),
                onRates,
            )

            Section(stringResource(R.string.settings_section_security))
            SwitchRow(stringResource(R.string.settings_pin), stringResource(R.string.settings_pin_hint), state.security.pinEnabled) { on ->
                if (on) onSetPin() else viewModel.disablePin()
            }
            SwitchRow(
                stringResource(R.string.settings_block_screenshots),
                stringResource(R.string.settings_block_screenshots_hint),
                state.blockScreenshots,
                viewModel::setBlockScreenshots,
            )
            if (state.security.pinEnabled) {
                val bioAvailable = canUseBiometric(context)
                SwitchRow(
                    stringResource(R.string.settings_biometric),
                    if (bioAvailable) null else stringResource(R.string.settings_biometric_unavailable),
                    state.security.biometric && bioAvailable,
                ) { if (bioAvailable) viewModel.setBiometric(it) }
                Label(stringResource(R.string.settings_lock_after))
                Chips {
                    listOf(0, 1, 5, 15).forEach { m ->
                        FilterChip(state.security.lockAfterMinutes == m, { viewModel.setLockAfter(m) }, label = {
                            Text(if (m == 0) stringResource(R.string.settings_lock_now) else stringResource(R.string.settings_lock_minutes, m))
                        })
                    }
                }
            }

            Section(stringResource(R.string.settings_section_debts))
            SwitchRow(
                stringResource(R.string.settings_debts_affect),
                stringResource(R.string.settings_debts_affect_hint),
                state.debtsAffectBalance,
                viewModel::setDebtsAffectBalance,
            )

            Section(stringResource(R.string.settings_section_reminders))
            Label(stringResource(R.string.settings_debt_days))
            Chips {
                listOf(0, 1, 3, 7).forEach { days ->
                    FilterChip(state.debtReminderDays == days, { viewModel.setDebtReminderDays(days) }, label = {
                        Text(if (days == 0) stringResource(R.string.settings_debt_days_0) else stringResource(R.string.settings_debt_days_n, days))
                    })
                }
            }

            Section(stringResource(R.string.settings_section_accounts))
            SwitchRow(
                title = stringResource(R.string.settings_account_types),
                subtitle = stringResource(R.string.settings_account_types_hint),
                checked = state.showAccountTypes,
                onChange = viewModel::setShowAccountTypes,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = Spacing.grid))
    SectionTitle(title, Modifier.padding(horizontal = Spacing.screenHorizontal))
}

@Composable
private fun Label(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid / 2))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(Modifier.padding(horizontal = Spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) { content() }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid * 1.5f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid * 1.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.grid * 2)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
