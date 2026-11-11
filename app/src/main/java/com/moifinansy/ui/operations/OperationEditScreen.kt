package com.moifinansy.ui.operations

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.AmountCalculator
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.Dropdown
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneOffset

private val editableTypes = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OperationEditScreen(
    onDone: () -> Unit,
    onCopy: (Long) -> Unit,
    viewModel: OperationEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(state.done) {
        if (state.done) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
            onDone()
        }
    }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopBar(stringResource(if (state.isNew) R.string.op_new_expense else R.string.op_edit), onDone) {
                val id = viewModel.editIdOrNull()
                if (!state.isNew && id != null) {
                    IconButton(onClick = { onCopy(id) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.op_copy))
                    }
                    IconButton(onClick = viewModel::delete) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
        },
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
            if (state.accounts.isEmpty()) {
                Text(stringResource(R.string.op_no_accounts), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            if (state.type in editableTypes) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    editableTypes.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index, editableTypes.size),
                        ) { Text(stringResource(type.labelRes())) }
                    }
                }
            }

            // Сумма с калькулятором.
            val currency = state.account?.currencyCode ?: "KGS"
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { text -> viewModel.setAmount(text.filter { it.isDigit() || it in ".,+-−×÷*/ " }) },
                label = {
                    Text(
                        stringResource(if (state.type == TransactionType.ADJUSTMENT) R.string.op_adjustment_amount else R.string.op_amount) +
                            ", " + com.moifinansy.core.ui.currencyLabel(currency),
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                isError = state.amountError,
                supportingText = {
                    val result = state.evaluatedAmount
                    Text(
                        when {
                            state.amountError -> stringResource(R.string.error_amount_invalid)
                            AmountCalculator.hasOperator(state.amountText) && result != null ->
                                stringResource(R.string.op_amount_result, MoneyFormat.format(result, currency))
                            else -> stringResource(R.string.op_amount_hint)
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                listOf('+', '−', '×', '÷').forEach { op ->
                    AssistChip(onClick = { viewModel.appendOperator(op) }, label = { Text(op.toString()) })
                }
            }

            // Счета.
            val accountText: (Account) -> String = { "${it.name} · ${it.currencyCode}" }
            val accountIcon: @Composable (Account) -> Unit = { IconBadge(it.icon, it.color, size = 28.dp) }
            Dropdown(
                label = stringResource(if (state.type == TransactionType.TRANSFER) R.string.op_from_account else R.string.op_account),
                options = state.accounts,
                selected = state.account,
                optionText = accountText,
                onSelect = { viewModel.setAccount(it.id) },
                leading = accountIcon,
            )
            if (state.type == TransactionType.TRANSFER) {
                Dropdown(
                    label = stringResource(R.string.op_to_account),
                    options = state.accounts.filter { it.id != state.accountId },
                    selected = state.toAccount,
                    optionText = accountText,
                    onSelect = { viewModel.setToAccount(it.id) },
                    leading = accountIcon,
                    isError = state.toAccountError,
                    supportingText = if (state.toAccountError) stringResource(R.string.op_same_account) else null,
                )
                if (state.needsToAmount) {
                    MoneyField(
                        value = state.toAmountText,
                        onValueChange = viewModel::setToAmount,
                        label = stringResource(R.string.op_to_amount, state.toAccount!!.currencyCode),
                        isError = state.toAmountError,
                        supportingText = stringResource(if (state.toAmountError) R.string.error_amount_invalid else R.string.op_to_amount_hint),
                    )
                }
                MoneyField(
                    value = state.feeText,
                    onValueChange = viewModel::setFee,
                    label = stringResource(R.string.op_fee),
                    isError = state.feeError,
                    supportingText = if (state.feeError) stringResource(R.string.error_amount_invalid) else null,
                )
            }

            // Категории.
            if (state.type == TransactionType.EXPENSE || state.type == TransactionType.INCOME) {
                SectionTitle(stringResource(R.string.op_category))
                if (state.categoryError) {
                    Text(stringResource(R.string.op_category_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                val parents = state.categories.filter { it.parentId == null }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    parents.forEach { category ->
                        CategoryTile(category, selected = state.selectedParentId == category.id, onClick = { viewModel.selectCategory(category.id) })
                    }
                }
                val parentId = state.selectedParentId
                val children = state.categories.filter { it.parentId != null && it.parentId == parentId }
                if (parentId != null && children.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                        FilterChip(
                            selected = state.categoryId == parentId,
                            onClick = { viewModel.selectCategory(parentId) },
                            label = { Text(stringResource(R.string.op_subcategory_any)) },
                        )
                        children.forEach { child ->
                            FilterChip(
                                selected = state.categoryId == child.id,
                                onClick = { viewModel.selectCategory(child.id) },
                                label = { Text(child.name) },
                            )
                        }
                    }
                }
            }

            // Дата.
            SectionTitle(stringResource(R.string.op_date))
            val selectedDate = viewModel.localDate()
            val today = viewModel.today()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                FilterChip(selected = selectedDate == today, onClick = { viewModel.setDate(today) }, label = { Text(stringResource(R.string.op_today)) })
                FilterChip(
                    selected = selectedDate == today.minusDays(1),
                    onClick = { viewModel.setDate(today.minusDays(1)) },
                    label = { Text(stringResource(R.string.op_yesterday)) },
                )
                val other = selectedDate != today && selectedDate != today.minusDays(1)
                FilterChip(
                    selected = other,
                    onClick = { showDatePicker = true },
                    label = { Text(if (other) DateFormat.day(state.date) else stringResource(R.string.op_pick_date)) },
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.op_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Теги.
            SectionTitle(stringResource(R.string.op_tags))
            if (state.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    state.tags.forEach { tag ->
                        FilterChip(selected = tag.id in state.tagIds, onClick = { viewModel.toggleTag(tag.id) }, label = { Text(tag.name) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                OutlinedTextField(
                    value = state.newTag,
                    onValueChange = viewModel::setNewTag,
                    label = { Text(stringResource(R.string.op_new_tag)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::addTag, enabled = state.newTag.isNotBlank()) { Text(stringResource(R.string.op_add_tag)) }
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.grid * 2)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.localDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.grid / 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            category.icon,
            category.color,
            size = 48.dp,
            modifier = if (selected) Modifier.border(2.dp, Color(category.color), CircleShape) else Modifier,
        )
        Text(
            category.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.EXPENSE -> R.string.op_type_expense
    TransactionType.INCOME -> R.string.op_type_income
    TransactionType.TRANSFER -> R.string.op_type_transfer
    TransactionType.ADJUSTMENT -> R.string.operation_type_adjustment
    TransactionType.DEBT -> R.string.operation_type_debt
}
