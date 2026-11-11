package com.moifinansy.ui.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(
    onOpenDebt: (Long) -> Unit,
    onOpenContact: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val q = state.query
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.grid, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.grid),
    ) {
        item(key = "header") { TotalsHeader(state) }
        item(key = "direction") {
            val directions = listOf(DebtDirection.OWED_TO_ME, DebtDirection.I_OWE)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                directions.forEachIndexed { index, direction ->
                    SegmentedButton(
                        selected = q.direction == direction,
                        onClick = { viewModel.setDirection(direction) },
                        shape = SegmentedButtonDefaults.itemShape(index, directions.size),
                    ) {
                        Text(stringResource(if (direction == DebtDirection.OWED_TO_ME) R.string.debts_owed_to_me else R.string.debts_i_owe))
                    }
                }
            }
        }
        item(key = "sort") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                listOf(
                    DebtSort.DUE to R.string.debts_sort_due,
                    DebtSort.DATE to R.string.debts_sort_date,
                    DebtSort.AMOUNT to R.string.debts_sort_amount,
                    DebtSort.OPEN_FIRST to R.string.debts_sort_open_first,
                ).forEach { (sort, label) ->
                    FilterChip(selected = !q.byPerson && q.sort == sort, onClick = { viewModel.setSort(sort) }, label = { Text(stringResource(label)) })
                }
                FilterChip(selected = q.byPerson, onClick = viewModel::toggleByPerson, label = { Text(stringResource(R.string.debts_by_person)) })
                FilterChip(selected = q.showClosed, onClick = viewModel::toggleClosed, label = { Text(stringResource(R.string.debts_closed)) })
            }
        }
        item(key = "add") {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.debts_add), modifier = Modifier.padding(start = Spacing.grid))
            }
        }

        if (q.byPerson) {
            if (state.persons.isEmpty()) item(key = "empty") { EmptyText() }
            items(state.persons, key = { "p" + it.contact.id }) { person ->
                PersonCard(person, onClick = { onOpenContact(person.contact.id) })
            }
        } else {
            if (!state.loading && state.cards.isEmpty()) item(key = "empty") { EmptyText() }
            items(state.cards, key = { it.debt.debt.id }) { card ->
                if (card.debt.debt.status != DebtStatus.ACTIVE) {
                    DebtCardView(card, onClick = { onOpenDebt(card.debt.debt.id) }, onPart = { onOpenDebt(card.debt.debt.id) })
                } else {
                    val swipe = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    viewModel.repayAllAsync(card) { onOpenDebt(card.debt.debt.id) }
                                    true
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    shareReminder(context, reminderText(context, card.debt, card.contactName))
                                    false
                                }
                                else -> false
                            }
                        },
                    )
                    SwipeToDismissBox(state = swipe, backgroundContent = { SwipeBackground(swipe.dismissDirection) }) {
                        DebtCardView(card, onClick = { onOpenDebt(card.debt.debt.id) }, onPart = { onOpenDebt(card.debt.debt.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyText() {
    Text(
        stringResource(R.string.debts_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.grid * 3),
    )
}

@Composable
private fun TotalsHeader(state: DebtsState) {
    val colors = LocalFinanceColors.current
    val onGradient = MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(colors.headerGradientStart, colors.headerGradientEnd)))
            .padding(Spacing.grid * 2.5f),
    ) {
        Text(stringResource(R.string.debts_saldo), style = MaterialTheme.typography.bodySmall, color = onGradient.copy(alpha = 0.85f))
        Text(MoneyFormat.format(state.totals.saldo, state.baseCurrency, signed = true), style = MaterialTheme.typography.displaySmall, color = onGradient)
        HorizontalDivider(Modifier.padding(vertical = Spacing.grid * 1.5f), color = onGradient.copy(alpha = 0.3f))
        Row {
            HeaderColumn(stringResource(R.string.debts_owed_to_me), MoneyFormat.format(state.totals.owedToMe, state.baseCurrency), Modifier.weight(1f))
            HeaderColumn(stringResource(R.string.debts_i_owe), MoneyFormat.format(state.totals.iOwe, state.baseCurrency), Modifier.weight(1f))
            HeaderColumn(stringResource(R.string.debts_open), state.openCount.toString(), Modifier.weight(0.6f))
        }
    }
}

@Composable
private fun HeaderColumn(title: String, value: String, modifier: Modifier) {
    val onGradient = MaterialTheme.colorScheme.onPrimary
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = onGradient.copy(alpha = 0.85f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = onGradient)
    }
}

@Composable
fun DebtCardView(card: DebtCard, onClick: () -> Unit, onPart: () -> Unit) {
    val colors = LocalFinanceColors.current
    val debt = card.debt
    val d = debt.debt
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f)) {
                Avatar(card.contactName)
                Column(Modifier.weight(1f)) {
                    Text(card.contactName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(d.note?.takeIf { it.isNotBlank() }, DateFormat.shortDay(d.startDate)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(MoneyFormat.format(debt.remaining, d.currencyCode), style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(
                progress = { if (debt.total > 0) debt.repaid.toFloat() / debt.total else 0f },
                color = if (d.direction == DebtDirection.OWED_TO_ME) colors.income else colors.expense,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.debt_repaid_of, MoneyFormat.format(debt.repaid, d.currencyCode), MoneyFormat.format(debt.total, d.currencyCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DueBadge(card)
                }
                if (d.status == DebtStatus.ACTIVE) {
                    OutlinedButton(onClick = onPart) {
                        Text(stringResource(if (d.direction == DebtDirection.OWED_TO_ME) R.string.debt_return_part else R.string.debt_give_part))
                    }
                }
            }
        }
    }
}

@Composable
fun DueBadge(card: DebtCard) {
    val colors = LocalFinanceColors.current
    val d = card.debt.debt
    val text = when {
        d.status == DebtStatus.CLOSED -> stringResource(R.string.debt_status_closed)
        d.status == DebtStatus.FORGIVEN -> stringResource(R.string.debt_status_forgiven)
        card.overdue -> stringResource(R.string.debt_overdue) + " · " + DateFormat.shortDay(d.dueDate ?: 0)
        d.dueDate != null -> stringResource(R.string.debt_due, DateFormat.shortDay(d.dueDate!!))
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (card.overdue) colors.overdue else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PersonCard(person: PersonRow, onClick: () -> Unit) {
    val colors = LocalFinanceColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(Spacing.screenHorizontal), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f)) {
            Avatar(person.contact.name)
            Text(person.contact.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                person.balances.forEach { b ->
                    Text(
                        MoneyFormat.format(b.saldo, b.currencyCode, signed = true),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (b.saldo >= 0) colors.income else colors.expense,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val colors = LocalFinanceColors.current
    val repay = direction == SwipeToDismissBoxValue.StartToEnd
    Box(
        Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(if (repay) colors.income else MaterialTheme.colorScheme.primary)
            .padding(horizontal = Spacing.screenHorizontal),
        contentAlignment = if (repay) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            Icon(if (repay) Icons.Rounded.Done else Icons.Rounded.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            Text(stringResource(if (repay) R.string.debt_swipe_repay else R.string.debt_swipe_remind), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
