package com.moifinansy.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.Dropdown
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.budget.BudgetStatus

@Composable
fun BudgetsScreen(onBack: () -> Unit, onOpen: (Long?) -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.budgets_title, DateFormat.monthYear(state.month)), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(null) }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.budgets_add)) }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            if (!state.loading && state.items.isEmpty()) {
                item { Text(stringResource(R.string.budgets_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.grid * 3)) }
            }
            items(state.items, key = { it.budget.id }) { BudgetCard(it, state.baseCurrency, onClick = { onOpen(it.budget.id) }) }
        }
    }
}

@Composable
fun BudgetCard(status: BudgetStatus, currency: String, onClick: () -> Unit) {
    val colors = LocalFinanceColors.current
    val color = when (status.level) {
        BudgetStatus.Level.OK -> MaterialTheme.colorScheme.primary
        BudgetStatus.Level.WARNING -> colors.overdue
        BudgetStatus.Level.EXCEEDED -> colors.expense
    }
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f)) {
                IconBadge(status.category?.icon ?: "account_balance_wallet", status.category?.color ?: 0xFF5B5BD6)
                Text(status.category?.name ?: stringResource(R.string.budget_all_expenses), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${status.percent} %", style = MaterialTheme.typography.titleMedium, color = color)
            }
            LinearProgressIndicator(
                progress = { (status.percent / 100f).coerceIn(0f, 1f) },
                color = color,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Row {
                Text(
                    stringResource(R.string.budget_spent, MoneyFormat.format(status.spent, currency), MoneyFormat.format(status.limit, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (status.left >= 0) stringResource(R.string.budget_left, MoneyFormat.format(status.left, currency))
                    else stringResource(R.string.budget_over, MoneyFormat.format(-status.left, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
    }
}

@Composable
fun BudgetEditScreen(onDone: () -> Unit, viewModel: BudgetEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    Scaffold(
        topBar = {
            BackTopBar(stringResource(if (state.isNew) R.string.budget_new else R.string.budget_edit), onDone) {
                if (!state.isNew) IconButton(onClick = viewModel::delete) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(Modifier.padding(padding).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            val all = stringResource(R.string.budget_all_expenses)
            Dropdown(
                label = stringResource(R.string.budget_category),
                options = listOf(null) + state.categories,
                selected = state.categories.firstOrNull { it.id == state.categoryId },
                optionText = { c -> c?.let { if (it.parentId != null) "   ${it.name}" else it.name } ?: all },
                onSelect = { viewModel.setCategory(it?.id) },
                emptyText = all,
            )
            MoneyField(
                value = state.limitText,
                onValueChange = viewModel::setLimit,
                label = stringResource(R.string.budget_limit),
                isError = state.limitError,
                supportingText = if (state.limitError) stringResource(R.string.error_amount_invalid) else null,
            )
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}
