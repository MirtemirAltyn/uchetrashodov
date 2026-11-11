package com.moifinansy.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.theme.Spacing

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SwitchRow(
                title = stringResource(R.string.settings_account_types),
                subtitle = stringResource(R.string.settings_account_types_hint),
                checked = state.showAccountTypes,
                onChange = viewModel::setShowAccountTypes,
            )
            com.moifinansy.core.ui.SectionTitle(
                stringResource(R.string.settings_debt_days),
                Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
            androidx.compose.foundation.layout.FlowRow(
                Modifier.padding(horizontal = Spacing.screenHorizontal),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.grid),
            ) {
                listOf(0, 1, 3, 7).forEach { days ->
                    androidx.compose.material3.FilterChip(
                        selected = state.debtReminderDays == days,
                        onClick = { viewModel.setDebtReminderDays(days) },
                        label = { Text(if (days == 0) stringResource(R.string.settings_debt_days_0) else stringResource(R.string.settings_debt_days_n, days)) },
                    )
                }
            }
        }
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
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
