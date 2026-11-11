package com.moifinansy.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.AccountTypeChips
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.currencyLabel
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.Currencies

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.onboarding_account_default_name)
    LaunchedEffect(Unit) {
        viewModel.restart()
        viewModel.suggestName(defaultName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid * 3),
        verticalArrangement = Arrangement.spacedBy(Spacing.grid * 2),
    ) {
        Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.onboarding_step, state.step.ordinal + 1, OnboardingState.Step.entries.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state.step) {
            OnboardingState.Step.CURRENCY -> {
                Text(stringResource(R.string.onboarding_currency_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.onboarding_currency_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup()) {
                    Currencies.all.forEach { currency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { viewModel.selectCurrency(currency.code) }
                                .padding(vertical = Spacing.grid / 2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = currency.code == state.currencyCode, onClick = null)
                            Text(currencyLabel(currency.code), modifier = Modifier.padding(start = Spacing.grid))
                        }
                    }
                }
                Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_next))
                }
                TextButton(onClick = viewModel::startWithDemo, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_demo))
                }
                Text(
                    stringResource(R.string.onboarding_demo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OnboardingState.Step.ACCOUNT -> {
                Text(stringResource(R.string.onboarding_account_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.onboarding_account_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.accountName,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.field_name)) },
                    isError = state.nameError,
                    supportingText = if (state.nameError) ({ Text(stringResource(R.string.error_name_required)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AccountTypeChips(selected = state.accountType, onSelect = viewModel::setType)
                MoneyField(
                    value = state.balanceText,
                    onValueChange = viewModel::setBalance,
                    label = stringResource(R.string.account_field_initial_balance) + ", " + currencyLabel(state.currencyCode),
                    isError = state.balanceError,
                    supportingText = stringResource(
                        if (state.balanceError) R.string.error_amount_invalid else R.string.account_field_initial_balance_hint,
                    ),
                    allowNegative = true,
                )
                Spacer(Modifier.height(Spacing.grid))
                Button(onClick = viewModel::finish, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_start))
                }
                TextButton(onClick = viewModel::back, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
    }
}
