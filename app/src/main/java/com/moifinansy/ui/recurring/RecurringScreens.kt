package com.moifinansy.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.Dropdown
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.LocalDatePickerDialog
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.RecurrenceFrequency
import com.moifinansy.domain.model.RecurringMode
import com.moifinansy.domain.model.TransactionType
import com.moifinansy.ui.operations.labelRes
import java.time.ZoneId

@Composable
fun RecurrenceFrequency.unitText(): String = stringResource(
    when (this) {
        RecurrenceFrequency.DAY -> R.string.recurring_freq_day
        RecurrenceFrequency.WEEK -> R.string.recurring_freq_week
        RecurrenceFrequency.MONTH -> R.string.recurring_freq_month
        RecurrenceFrequency.YEAR -> R.string.recurring_freq_year
    },
)

@Composable
fun RecurringScreen(onBack: () -> Unit, onOpen: (Long?) -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalFinanceColors.current
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.more_recurring), onBack) },
        floatingActionButton = { FloatingActionButton(onClick = { onOpen(null) }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.recurring_add)) } },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            items(state.suggestions, key = { "s" + it.suggestion.key }) { item ->
                val sug = item.suggestion
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f)) {
                            IconBadge(item.category?.icon ?: "more_horiz", item.category?.color ?: 0xFF5B5BD6)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.suggest_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.suggest_text,
                                        item.category?.name.orEmpty(),
                                        MoneyFormat.format(sug.amount, item.account?.currencyCode ?: "KGS"),
                                        sug.dayOfMonth,
                                        sug.months,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                            Button(onClick = { viewModel.accept(sug) }) { Text(stringResource(R.string.suggest_add)) }
                            OutlinedButton(onClick = { viewModel.dismiss(sug) }) { Text(stringResource(R.string.suggest_dismiss)) }
                        }
                    }
                }
            }
            if (!state.loading && state.items.isEmpty() && state.suggestions.isEmpty()) {
                item { Text(stringResource(R.string.recurring_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.grid * 3)) }
            }
            items(state.items, key = { it.rule.id }) { item ->
                val r = item.rule
                Surface(Modifier.fillMaxWidth().clickable { onOpen(r.id) }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Column(Modifier.padding(Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f)) {
                            IconBadge(item.category?.icon ?: "account_balance", item.category?.color ?: (item.account?.color ?: 0xFF5B5BD6))
                            Column(Modifier.weight(1f)) {
                                val title = item.category?.name
                                    ?: (item.account?.name.orEmpty() + " → " + item.toAccount?.name.orEmpty())
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.recurring_every_text, r.interval, r.frequency.unitText()) + " · " +
                                        stringResource(if (r.mode == RecurringMode.AUTO) R.string.recurring_auto else R.string.recurring_remind),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val currency = item.account?.currencyCode ?: "KGS"
                            Text(
                                MoneyFormat.format(if (r.type == TransactionType.EXPENSE) -r.amount else r.amount, currency, signed = r.type != TransactionType.TRANSFER),
                                style = MaterialTheme.typography.titleMedium,
                                color = when (r.type) {
                                    TransactionType.EXPENSE -> colors.expense
                                    TransactionType.INCOME -> colors.income
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        Text(
                            stringResource(if (item.due) R.string.recurring_due else R.string.recurring_next, DateFormat.day(r.nextDate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.due) colors.overdue else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.due) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                                Button(onClick = { viewModel.post(item) }) { Text(stringResource(R.string.recurring_post)) }
                                OutlinedButton(onClick = { viewModel.skip(item) }) { Text(stringResource(R.string.recurring_skip)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringEditScreen(onDone: () -> Unit, viewModel: RecurringEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    var pickFirst by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            BackTopBar(stringResource(if (state.isNew) R.string.recurring_new else R.string.recurring_edit), onDone) {
                if (!state.isNew) IconButton(onClick = viewModel::delete) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            val types = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, t ->
                    SegmentedButton(selected = state.type == t, onClick = { viewModel.setType(t) }, shape = SegmentedButtonDefaults.itemShape(i, types.size)) {
                        Text(stringResource(t.labelRes()))
                    }
                }
            }
            MoneyField(state.amountText, viewModel::setAmount, stringResource(R.string.op_amount), isError = state.amountError,
                supportingText = if (state.amountError) stringResource(R.string.error_amount_invalid) else null)
            Dropdown(
                label = stringResource(if (state.type == TransactionType.TRANSFER) R.string.op_from_account else R.string.op_account),
                options = state.accounts, selected = state.accounts.firstOrNull { it.id == state.accountId },
                optionText = { it.name }, onSelect = { viewModel.setAccount(it.id) }, isError = state.accountError,
            )
            if (state.type == TransactionType.TRANSFER) {
                Dropdown(
                    label = stringResource(R.string.op_to_account),
                    options = state.accounts.filter { it.id != state.accountId }, selected = state.accounts.firstOrNull { it.id == state.toAccountId },
                    optionText = { it.name }, onSelect = { viewModel.setToAccount(it.id) }, isError = state.accountError,
                )
            } else {
                Dropdown(
                    label = stringResource(R.string.op_category),
                    options = state.categories, selected = state.categories.firstOrNull { it.id == state.categoryId },
                    optionText = { if (it.parentId != null) "   ${it.name}" else it.name }, onSelect = { viewModel.setCategory(it.id) },
                    isError = state.categoryError, supportingText = if (state.categoryError) stringResource(R.string.op_category_required) else null,
                )
            }
            SectionTitle(stringResource(R.string.recurring_every))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                OutlinedTextField(
                    value = state.intervalText, onValueChange = viewModel::setInterval, label = { Text(stringResource(R.string.recurring_interval)) },
                    isError = state.intervalError, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(110.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    RecurrenceFrequency.entries.forEach { f ->
                        FilterChip(selected = state.frequency == f, onClick = { viewModel.setFrequency(f) }, label = { Text(f.unitText()) })
                    }
                }
            }
            SectionTitle(stringResource(R.string.recurring_first_date))
            OutlinedButton(onClick = { pickFirst = true }) { Text(state.firstDate.fmt()) }
            SectionTitle(stringResource(R.string.recurring_end_date))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                FilterChip(selected = state.endDate == null, onClick = { viewModel.setEndDate(null) }, label = { Text(stringResource(R.string.recurring_end_never)) })
                FilterChip(selected = state.endDate != null, onClick = { pickEnd = true }, label = { Text(state.endDate?.fmt() ?: stringResource(R.string.op_pick_date)) })
            }
            listOf(RecurringMode.AUTO to R.string.recurring_mode_auto, RecurringMode.REMIND to R.string.recurring_mode_remind).forEach { (m, label) ->
                FilterChip(selected = state.mode == m, onClick = { viewModel.setMode(m) }, label = { Text(stringResource(label)) })
            }
            OutlinedTextField(state.note, viewModel::setNote, label = { Text(stringResource(R.string.op_note)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.grid * 2)) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (pickFirst) LocalDatePickerDialog(state.firstDate, viewModel::setFirstDate) { pickFirst = false }
    if (pickEnd) LocalDatePickerDialog(state.endDate ?: state.firstDate.plusYears(1), { viewModel.setEndDate(it) }) { pickEnd = false }
}

private fun java.time.LocalDate.fmt(): String = DateFormat.day(atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
