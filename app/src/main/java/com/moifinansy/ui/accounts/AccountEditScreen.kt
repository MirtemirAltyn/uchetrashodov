package com.moifinansy.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.AccountTypeChips
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.ColorPicker
import com.moifinansy.core.ui.CurrencyDropdown
import com.moifinansy.core.ui.IconPicker
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.Spacing

@Composable
fun AccountEditScreen(onDone: () -> Unit, viewModel: AccountEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = { BackTopBar(stringResource(if (state.isNew) R.string.account_new else R.string.account_edit), onDone) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.field_name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.error_name_required)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle(stringResource(R.string.account_field_type))
            AccountTypeChips(selected = state.type, onSelect = viewModel::setType)
            CurrencyDropdown(
                selected = state.currencyCode,
                onSelect = viewModel::setCurrency,
                enabled = !state.currencyLocked,
                supportingText = if (state.currencyLocked) stringResource(R.string.account_currency_locked) else null,
            )
            MoneyField(
                value = state.balanceText,
                onValueChange = viewModel::setBalance,
                label = stringResource(R.string.account_field_initial_balance),
                isError = state.balanceError,
                supportingText = stringResource(
                    if (state.balanceError) R.string.error_amount_invalid else R.string.account_field_initial_balance_hint,
                ),
                allowNegative = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.account_field_include_in_total), modifier = Modifier.weight(1f))
                Switch(checked = state.includeInTotal, onCheckedChange = viewModel::setIncludeInTotal)
            }
            SectionTitle(stringResource(R.string.field_color))
            ColorPicker(selected = state.color, onSelect = viewModel::setColor)
            SectionTitle(stringResource(R.string.field_icon))
            IconPicker(selected = state.icon, color = state.color, onSelect = viewModel::setIcon)

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(top = Spacing.grid * 2)) {
                Text(stringResource(R.string.action_save))
            }
            if (!state.isNew) {
                OutlinedButton(onClick = viewModel::toggleArchived, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.isArchived) R.string.action_unarchive else R.string.action_archive))
                }
                TextButton(onClick = viewModel::askDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        val blocked = state.usageCount > 0
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(if (blocked) R.string.account_delete_blocked else R.string.account_delete_text)) },
            confirmButton = {
                if (blocked) {
                    if (state.isArchived) {
                        TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) }
                    } else {
                        TextButton(onClick = viewModel::archiveInstead) { Text(stringResource(R.string.action_archive)) }
                    }
                } else {
                    TextButton(onClick = viewModel::delete) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
