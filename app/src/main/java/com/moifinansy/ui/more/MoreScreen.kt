package com.moifinansy.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.currencyLabel
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.ui.accounts.MenuRow

@Composable
fun MoreScreen(
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
    onBudgets: () -> Unit,
    onRecurring: () -> Unit,
    onData: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary.toArgb().toLong() and 0xFFFFFFFFL
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            stringResource(R.string.tab_more),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid * 2),
        )
        MenuRow(
            stringResource(R.string.more_accounts),
            stringResource(R.string.more_accounts_hint),
            icon = "account_balance_wallet",
            color = accent,
            onClick = onAccounts,
        )
        MenuRow(
            stringResource(R.string.more_categories),
            stringResource(R.string.more_categories_hint),
            icon = "palette",
            color = accent,
            onClick = onCategories,
        )
        MenuRow(stringResource(R.string.more_budgets), stringResource(R.string.more_budgets_hint), icon = "savings", color = accent, onClick = onBudgets)
        MenuRow(stringResource(R.string.more_recurring), stringResource(R.string.more_recurring_hint), icon = "subscriptions", color = accent, onClick = onRecurring)
        MenuRow(stringResource(R.string.more_data), stringResource(R.string.more_data_hint), icon = "savings", color = accent, onClick = onData)
        MenuRow(
            stringResource(R.string.settings_title),
            stringResource(R.string.settings_hint),
            icon = "build",
            color = accent,
            onClick = onSettings,
        )
        Text(
            stringResource(R.string.more_base_currency, currencyLabel(baseCurrency)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid * 2),
        )
    }
}
