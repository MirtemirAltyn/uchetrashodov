package com.moifinansy.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.GradientHeader
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.MoneyText
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.labelRes
import com.moifinansy.core.ui.theme.Spacing

@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adjust by viewModel.adjust.collectAsStateWithLifecycle()
    val item = state.account

    // Счёт удалили на экране редактирования — возвращаемся к списку.
    LaunchedEffect(state.loading, item) { if (!state.loading && item == null) onBack() }
    if (item == null) return
    val account = item.account

    Scaffold(
        topBar = {
            BackTopBar(account.name, onBack) {
                IconButton(onClick = { onEdit(account.id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            item {
                GradientHeader(
                    title = stringResource(R.string.account_balance),
                    amount = MoneyFormat.format(item.balance, account.currencyCode),
                    subtitle = stringResource(account.type.labelRes()),
                )
            }
            item(key = "adjust") {
                val dialog = adjust
                if (dialog == null) {
                    OutlinedButton(onClick = viewModel::openAdjust, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.account_adjust))
                    }
                } else {
                    AdjustPanel(dialog, viewModel)
                }
            }
            item { SectionTitle(stringResource(R.string.account_history)) }
            if (state.history.isEmpty()) {
                item {
                    Text(stringResource(R.string.account_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.history, key = { it.id }) { row -> HistoryItem(row, account.currencyCode) }
        }
    }
}

/** Корректировка баланса прямо на карточке счёта (без отдельного окна). */
@Composable
private fun AdjustPanel(dialog: AdjustDialogState, viewModel: AccountDetailViewModel) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            Text(stringResource(R.string.account_adjust), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.account_adjust_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyField(
                value = dialog.text,
                onValueChange = viewModel::setAdjustText,
                label = stringResource(R.string.account_adjust_field),
                isError = dialog.error,
                supportingText = if (dialog.error) stringResource(R.string.error_amount_invalid) else null,
                allowNegative = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = viewModel::closeAdjust, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = viewModel::applyAdjust, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.account_adjust_apply))
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, currencyCode: String) {
    val unknownAccount = stringResource(R.string.operation_unknown_account)
    val title = when (val label = row.label) {
        is HistoryRow.Label.Category -> label.name ?: stringResource(R.string.operation_unknown_category)
        is HistoryRow.Label.TransferOut -> stringResource(R.string.operation_type_transfer_out, label.accountName ?: unknownAccount)
        is HistoryRow.Label.TransferIn -> stringResource(R.string.operation_type_transfer_in, label.accountName ?: unknownAccount)
        HistoryRow.Label.Adjustment -> stringResource(R.string.operation_type_adjustment)
        HistoryRow.Label.Debt -> stringResource(R.string.operation_type_debt)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.grid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
    ) {
        IconBadge(row.icon, row.color)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            val subtitle = listOfNotNull(DateFormat.day(row.date), row.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(row.effect, currencyCode, signed = true)
    }
}
