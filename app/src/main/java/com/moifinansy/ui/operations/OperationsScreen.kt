package com.moifinansy.ui.operations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.Dropdown
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.TransactionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(
    onOpen: (OperationItem) -> Unit,
    viewModel: OperationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val q = state.query

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::prevMonth) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.ops_prev))
                    }
                    Text(
                        if (q.allTime) stringResource(R.string.ops_all_time) else DateFormat.monthYear(q.month),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.ops_next))
                    }
                }
                OutlinedTextField(
                    value = q.search,
                    onValueChange = viewModel::setSearch,
                    placeholder = { Text(stringResource(R.string.ops_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    FilterChip(selected = q.type == null, onClick = { viewModel.setType(null) }, label = { Text(stringResource(R.string.ops_filter_type_all)) })
                    listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER).forEach { type ->
                        FilterChip(selected = q.type == type, onClick = { viewModel.setType(type) }, label = { Text(stringResource(type.labelRes())) })
                    }
                    FilterChip(selected = q.allTime, onClick = viewModel::toggleAllTime, label = { Text(stringResource(R.string.ops_all_time)) })
                    FilterChip(
                        selected = q.showFilters || q.hasExtraFilters,
                        onClick = viewModel::toggleFilters,
                        label = { Text(stringResource(R.string.ops_filters)) },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    )
                }
                AnimatedVisibility(q.showFilters) { FiltersPanel(state, viewModel) }
            }
        }

        if (!state.loading && state.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(if (q.search.isNotBlank() || q.type != null || q.hasExtraFilters) R.string.ops_empty_filtered else R.string.ops_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.grid * 4),
                )
            }
        }

        state.days.forEach { day ->
            item(key = "day-${day.date}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.grid * 2, bottom = Spacing.grid / 2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        DateFormat.dayHeader(day.date),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (day.total != 0L) {
                        Text(
                            MoneyFormat.format(day.total, state.baseCurrency, signed = true),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(day.items, key = { it.id }) { item ->
                if (item.type == TransactionType.DEBT) {
                    OperationRow(item, onClick = { onOpen(item) })
                } else {
                    SwipeToDelete(onDelete = { viewModel.delete(item.id) }) {
                        OperationRow(item, onClick = { onOpen(item) }, modifier = Modifier.background(MaterialTheme.colorScheme.background))
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersPanel(state: OperationsState, viewModel: OperationsViewModel) {
    val q = state.query
    val any = stringResource(R.string.ops_filter_any)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
        Dropdown(
            label = stringResource(R.string.ops_filter_account),
            options = listOf(null) + state.accounts.map { it.account },
            selected = state.accounts.firstOrNull { it.account.id == q.accountId }?.account,
            optionText = { it?.name ?: any },
            onSelect = { viewModel.setAccount(it?.id) },
            emptyText = any,
        )
        Dropdown(
            label = stringResource(R.string.ops_filter_category),
            options = listOf(null) + state.categories,
            selected = state.categories.firstOrNull { it.id == q.categoryId },
            optionText = { c -> c?.let { if (it.parentId != null) "   ${it.name}" else it.name } ?: any },
            onSelect = { viewModel.setCategory(it?.id) },
            emptyText = any,
        )
        if (state.tags.isNotEmpty()) {
            Dropdown(
                label = stringResource(R.string.ops_filter_tag),
                options = listOf(null) + state.tags,
                selected = state.tags.firstOrNull { it.id == q.tagId },
                optionText = { it?.name ?: any },
                onSelect = { viewModel.setTag(it?.id) },
                emptyText = any,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            MoneyField(q.minText, viewModel::setMin, stringResource(R.string.ops_filter_min), modifier = Modifier.weight(1f))
            MoneyField(q.maxText, viewModel::setMax, stringResource(R.string.ops_filter_max), modifier = Modifier.weight(1f))
        }
        TextButton(onClick = viewModel::resetFilters) { Text(stringResource(R.string.ops_filter_reset)) }
    }
}

/** Свайп влево — удалить (с отменой через Snackbar). */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalFinanceColors.current.expense)
                    .padding(horizontal = Spacing.screenHorizontal),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { content() }
}

@Composable
fun BottomSpacer() = Spacer(Modifier.height(88.dp))
