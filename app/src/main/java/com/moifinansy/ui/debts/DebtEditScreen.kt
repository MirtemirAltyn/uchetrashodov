package com.moifinansy.ui.debts

import android.app.Activity
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.CurrencyDropdown
import com.moifinansy.core.ui.Dropdown
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.LocalDatePickerDialog
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.DebtDirection
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebtEditScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, viewModel: DebtEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    var pickStart by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }

    // Выбор из телефонной книги без разрешения READ_CONTACTS: система даёт доступ к одной выбранной записи.
    val phonebook = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) viewModel.picked(c.getString(0).orEmpty(), c.getString(1)) }
        }
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.debt_new), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            val directions = listOf(DebtDirection.OWED_TO_ME, DebtDirection.I_OWE)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                directions.forEachIndexed { index, d ->
                    SegmentedButton(
                        selected = state.direction == d,
                        onClick = { viewModel.setDirection(d) },
                        shape = SegmentedButtonDefaults.itemShape(index, directions.size),
                    ) { Text(stringResource(if (d == DebtDirection.OWED_TO_ME) R.string.debts_owed_to_me else R.string.debts_i_owe)) }
                }
            }

            SectionTitle(stringResource(R.string.debt_contact))
            if (state.contacts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    state.contacts.forEach { c: Contact ->
                        FilterChip(selected = !state.newContact && state.contactId == c.id, onClick = { viewModel.selectContact(c.id) }, label = { Text(c.name) })
                    }
                    FilterChip(selected = state.newContact, onClick = viewModel::startNewContact, label = { Text(stringResource(R.string.debt_contact_new)) })
                }
            }
            if (state.newContact) {
                OutlinedTextField(
                    value = state.contactName,
                    onValueChange = viewModel::setContactName,
                    label = { Text(stringResource(R.string.debt_contact_name)) },
                    isError = state.contactError,
                    supportingText = if (state.contactError) ({ Text(stringResource(R.string.debt_contact_required)) }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.contactPhone,
                    onValueChange = viewModel::setContactPhone,
                    label = { Text(stringResource(R.string.debt_contact_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.contactError) {
                Text(stringResource(R.string.debt_contact_required), color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = {
                runCatching {
                    phonebook.launch(android.content.Intent(android.content.Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }
            }) { Text(stringResource(R.string.debt_contact_pick)) }

            MoneyField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = stringResource(R.string.op_amount),
                isError = state.amountError,
                supportingText = if (state.amountError) stringResource(R.string.error_amount_invalid) else null,
            )
            CurrencyDropdown(selected = state.currencyCode, onSelect = viewModel::setCurrency)

            SectionTitle(stringResource(R.string.debt_balance_question))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.affectsAccounts,
                    onClick = { viewModel.setAffectsAccounts(true) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(stringResource(if (state.direction == DebtDirection.OWED_TO_ME) R.string.debt_balance_out else R.string.debt_balance_in))
                }
                SegmentedButton(
                    selected = !state.affectsAccounts,
                    onClick = { viewModel.setAffectsAccounts(false) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.debt_balance_none)) }
            }
            if (!state.affectsAccounts) {
                Text(stringResource(R.string.debt_balance_none_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.affectsAccounts) {
                Dropdown(
                    label = stringResource(if (state.direction == DebtDirection.OWED_TO_ME) R.string.debt_account_out else R.string.debt_account_in),
                    options = state.accounts,
                    selected = state.accounts.firstOrNull { it.id == state.accountId },
                    optionText = { it.name },
                    onSelect = { viewModel.setAccount(it.id) },
                    leading = { IconBadge(it.icon, it.color, size = 28.dp) },
                    isError = state.accountError,
                    supportingText = when {
                        state.accounts.isEmpty() -> stringResource(R.string.debt_no_account_currency)
                        state.accountError -> stringResource(R.string.debt_no_account)
                        else -> null
                    },
                )
            }

            SectionTitle(stringResource(R.string.debt_start_date))
            val today = viewModel.today()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                FilterChip(selected = state.startDate == today, onClick = { viewModel.setStartDate(today) }, label = { Text(stringResource(R.string.op_today)) })
                FilterChip(selected = state.startDate == today.minusDays(1), onClick = { viewModel.setStartDate(today.minusDays(1)) }, label = { Text(stringResource(R.string.op_yesterday)) })
                val other = state.startDate != today && state.startDate != today.minusDays(1)
                FilterChip(selected = other, onClick = { pickStart = true }, label = { Text(if (other) state.startDate.fmt() else stringResource(R.string.op_pick_date)) })
            }
            SectionTitle(stringResource(R.string.debt_due_date))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                val week = state.startDate.plusWeeks(1)
                val month = state.startDate.plusMonths(1)
                FilterChip(selected = state.dueDate == null, onClick = { viewModel.setDueDate(null) }, label = { Text(stringResource(R.string.debt_due_none)) })
                FilterChip(selected = state.dueDate == week, onClick = { viewModel.setDueDate(week) }, label = { Text(stringResource(R.string.debt_due_week)) })
                FilterChip(selected = state.dueDate == month, onClick = { viewModel.setDueDate(month) }, label = { Text(stringResource(R.string.debt_due_month)) })
                val other = state.dueDate != null && state.dueDate != week && state.dueDate != month
                FilterChip(selected = other, onClick = { pickDue = true }, label = { Text(if (other) state.dueDate!!.fmt() else stringResource(R.string.op_pick_date)) })
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.op_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.grid * 2)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
    if (pickStart) LocalDatePickerDialog(state.startDate, viewModel::setStartDate) { pickStart = false }
    if (pickDue) LocalDatePickerDialog(state.dueDate ?: state.startDate.plusWeeks(2), { viewModel.setDueDate(it) }) { pickDue = false }
}

private fun java.time.LocalDate.fmt(): String = DateFormat.day(atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
