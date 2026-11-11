package com.moifinansy.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.GradientHeader
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.MoneyText
import com.moifinansy.core.ui.SectionCard
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.ui.operations.OperationItem
import com.moifinansy.ui.operations.OperationRow

@Composable
fun HomeScreen(
    onOpenAccount: (Long) -> Unit,
    onOpenOperation: (OperationItem) -> Unit,
    onAllOperations: () -> Unit,
    onDebts: () -> Unit,
    onOpenDebt: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalFinanceColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.grid, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
    ) {
        item(key = "total") {
            GradientHeader(title = stringResource(R.string.home_total), amount = MoneyFormat.format(state.total, state.baseCurrency))
        }
        if (state.accounts.isNotEmpty()) {
            item(key = "accounts") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    items(state.accounts, key = { it.account.id }) { AccountMiniCard(it, onClick = { onOpenAccount(it.account.id) }) }
                }
            }
        }
        item(key = "month") {
            SectionCard(title = DateFormat.monthYear(state.month)) {
                Row(Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_income), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(state.income, state.baseCurrency, signed = true)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_expense), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(-state.expense, state.baseCurrency, signed = true)
                    }
                }
            }
        }
        item(key = "top") {
            SectionCard(title = stringResource(R.string.home_top_categories)) {
                if (state.topCategories.isEmpty()) {
                    Text(
                        stringResource(R.string.home_no_expenses),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
                    )
                }
                state.topCategories.forEach { top ->
                    Row(
                        Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
                    ) {
                        IconBadge(top.category.icon, top.category.color)
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(top.category.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(MoneyFormat.format(top.total, state.baseCurrency), style = MaterialTheme.typography.titleMedium)
                            }
                            LinearProgressIndicator(
                                progress = { top.sharePercent / 100f },
                                color = Color(top.category.color),
                                trackColor = Color(top.category.color).copy(alpha = 0.15f),
                                strokeCap = StrokeCap.Round,
                                drawStopIndicator = {},
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.grid / 2),
                            )
                        }
                    }
                }
            }
        }
        item(key = "debts") {
            SectionCard(title = stringResource(R.string.home_debts), modifier = Modifier.clickable(onClick = onDebts)) {
                Row(Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_owed_to_me), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MoneyFormat.format(state.debtTotals.owedToMe, state.baseCurrency), style = MaterialTheme.typography.titleMedium, color = colors.income)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_i_owe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MoneyFormat.format(state.debtTotals.iOwe, state.baseCurrency), style = MaterialTheme.typography.titleMedium, color = colors.expense)
                    }
                }
                if (state.dueSoon.isNotEmpty()) {
                    Text(
                        stringResource(R.string.home_due_soon),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid / 2),
                    )
                    state.dueSoon.forEach { due ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDebt(due.debt.debt.id) }
                                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid / 2),
                        ) {
                            Text(
                                due.contactName + " · " + DateFormat.shortDay(due.debt.debt.dueDate ?: 0),
                                color = if (due.overdue) colors.overdue else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(MoneyFormat.format(due.debt.remaining, due.debt.debt.currencyCode))
                        }
                    }
                }
            }
        }
        item(key = "latest") {
            SectionCard(
                title = stringResource(R.string.home_latest),
                action = { TextButton(onClick = onAllOperations) { Text(stringResource(R.string.home_all_operations)) } },
            ) {
                if (state.latest.isEmpty()) {
                    Text(
                        stringResource(R.string.ops_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
                    )
                }
                state.latest.forEach { item -> OperationRow(item, onClick = { onOpenOperation(item) }, showDate = true) }
            }
        }
    }
}

@Composable
private fun AccountMiniCard(item: AccountWithBalance, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(156.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.grid * 1.5f), verticalArrangement = Arrangement.spacedBy(Spacing.grid / 2)) {
            IconBadge(item.account.icon, item.account.color, size = 32.dp)
            Text(item.account.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoneyText(item.balance, item.account.currencyCode)
        }
    }
}
