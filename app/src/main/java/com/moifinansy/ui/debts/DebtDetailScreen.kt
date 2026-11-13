package com.moifinansy.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.Dropdown
import com.moifinansy.core.ui.GradientHeader
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.LocalDatePickerDialog
import com.moifinansy.core.ui.MoneyField
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtStatus

@Composable
fun DebtDetailScreen(
    onBack: () -> Unit,
    onOpenContact: (Long) -> Unit,
    viewModel: DebtDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reminder = rememberReminder()
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmAffectsOff by remember { mutableStateOf(false) }
    val colors = LocalFinanceColors.current

    LaunchedEffect(deleted, state.loading, state.debt) { if (deleted || (!state.loading && state.debt == null)) onBack() }
    val debt = state.debt ?: return
    val d = debt.debt
    val name = state.contact?.name.orEmpty()
    val accountNames = state.accounts.associate { it.id to it.name }

    Scaffold(
        topBar = {
            BackTopBar(name, onBack) {
                IconButton(onClick = { reminder.open(context, debt, name, state.contact?.phone) }) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.debt_remind))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            item(key = "header") {
                GradientHeader(
                    title = stringResource(if (d.direction == DebtDirection.OWED_TO_ME) R.string.debt_title_owed_to_me else R.string.debt_title_i_owe, name),
                    amount = MoneyFormat.format(debt.remaining, d.currencyCode),
                    subtitle = stringResource(R.string.debt_repaid_of, MoneyFormat.format(debt.repaid, d.currencyCode), MoneyFormat.format(debt.total, d.currencyCode)),
                )
            }
            item(key = "progress") {
                val p = com.moifinansy.core.ui.animatedProgress(if (debt.total > 0) debt.repaid.toFloat() / debt.total else 0f)
                LinearProgressIndicator(
                    progress = { p },
                    color = if (d.direction == DebtDirection.OWED_TO_ME) colors.income else colors.expense,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
            item(key = "info") {
                Column {
                    Text(stringResource(R.string.debt_started, DateFormat.day(d.startDate)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DueBadge(DebtCard(debt, name, state.overdue))
                    if (d.dueDate == null && d.status == DebtStatus.ACTIVE) {
                        Text(stringResource(R.string.debt_no_due), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    d.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    TextButton(onClick = { onOpenContact(d.contactId) }) { Text(stringResource(R.string.contact_title) + ": " + name) }
                    val phone = state.contact?.phone
                    val inPhone by androidx.compose.runtime.produceState<Boolean?>(null, phone) { value = viewModel.inPhoneContacts(phone) }
                    if (!phone.isNullOrBlank() && inPhone != true) {
                        TextButton(onClick = { saveToPhoneContacts(context, name, phone) }) { Text(stringResource(R.string.contacts_save_to_phone)) }
                    }
                }
            }
            item(key = "affects") {
                val panelOpen by viewModel.affectsPanelOpen.collectAsStateWithLifecycle()
                val panelAccount by viewModel.affectsPanel.collectAsStateWithLifecycle()
                val initialName = state.accounts.firstOrNull { it.id == debt.initialAccountId }?.name.orEmpty()
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(value = d.affectsAccounts, role = Role.Switch) { on ->
                                if (on) viewModel.openAffectsPanel() else confirmAffectsOff = true
                            }
                            .padding(vertical = Spacing.grid),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.debt_affects))
                            Text(
                                if (d.affectsAccounts) stringResource(R.string.debt_affects_on_hint, initialName) else stringResource(R.string.debt_affects_off_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = d.affectsAccounts, onCheckedChange = null)
                    }
                    if (panelOpen && !d.affectsAccounts) {
                        val options = viewModel.accountsForDebt(state)
                        Dropdown(
                            label = stringResource(R.string.debt_affects_pick_account),
                            options = options,
                            selected = options.firstOrNull { it.id == panelAccount },
                            optionText = { it.name },
                            onSelect = { viewModel.setAffectsAccount(it.id) },
                            supportingText = if (options.isEmpty()) stringResource(R.string.debt_no_account_currency) else null,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                            TextButton(onClick = viewModel::closeAffectsPanel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                            Button(onClick = viewModel::enableAffects, enabled = panelAccount != null, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.debt_affects_apply))
                            }
                        }
                    }
                }
            }
            item(key = "actions") {
                val current = panel
                if (current == null) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                        if (d.status == DebtStatus.ACTIVE) {
                            Button(onClick = { viewModel.open(DebtPaymentKind.REPAY) }) { Text(stringResource(R.string.debt_action_repay)) }
                        }
                        OutlinedButton(onClick = { viewModel.open(DebtPaymentKind.INCREASE) }) { Text(stringResource(R.string.debt_action_increase)) }
                        if (d.status == DebtStatus.ACTIVE) {
                            OutlinedButton(onClick = { viewModel.open(DebtPaymentKind.FORGIVE) }) { Text(stringResource(R.string.debt_action_forgive)) }
                        }
                    }
                } else {
                    PaymentPanelView(current, viewModel, state, d.affectsAccounts, d.currencyCode)
                }
            }
            item(key = "historyTitle") { SectionTitle(stringResource(R.string.debt_history)) }
            items(state.payments, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.grid / 2), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(p.kind.labelRes()))
                        Text(
                            listOfNotNull(DateFormat.day(p.date), p.accountId?.let { accountNames[it] }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val sign = if (p.kind == DebtPaymentKind.INITIAL || p.kind == DebtPaymentKind.INCREASE) "+" else "−"
                    Text(sign + MoneyFormat.format(p.amount, d.currencyCode), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (confirmAffectsOff) {
        AlertDialog(
            onDismissRequest = { confirmAffectsOff = false },
            title = { Text(stringResource(R.string.debt_affects_off_title)) },
            text = { Text(stringResource(R.string.debt_affects_off_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAffectsOff = false
                    viewModel.disableAffects()
                }) { Text(stringResource(R.string.debt_affects_off_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmAffectsOff = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.debt_delete_title)) },
            text = { Text(stringResource(R.string.debt_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentPanelView(
    p: PaymentPanel,
    viewModel: DebtDetailViewModel,
    state: DebtDetailState,
    affectsAccounts: Boolean,
    currency: String,
) {
    var pickDate by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(Spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
            Text(stringResource(p.kind.labelRes()), style = MaterialTheme.typography.titleMedium)
            MoneyField(
                value = p.amountText,
                onValueChange = viewModel::setAmount,
                label = stringResource(R.string.op_amount) + ", " + com.moifinansy.core.ui.currencyLabel(currency),
                isError = p.amountError || p.tooMuch,
                supportingText = when {
                    p.tooMuch -> stringResource(R.string.debt_too_much)
                    p.amountError -> stringResource(R.string.error_amount_invalid)
                    else -> null
                },
            )
            if (affectsAccounts && p.kind != DebtPaymentKind.FORGIVE) {
                val options = viewModel.accountsForDebt(state)
                Dropdown(
                    label = stringResource(R.string.op_account),
                    options = options,
                    selected = options.firstOrNull { it.id == p.accountId },
                    optionText = { it.name },
                    onSelect = { viewModel.setAccount(it.id) },
                    leading = { IconBadge(it.icon, it.color, size = 28.dp) },
                    isError = p.accountError,
                    supportingText = if (p.accountError) stringResource(R.string.debt_no_account_currency) else null,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                val today = java.time.LocalDate.now()
                FilterChip(selected = p.date == today, onClick = { viewModel.setDate(today) }, label = { Text(stringResource(R.string.op_today)) })
                FilterChip(selected = p.date == today.minusDays(1), onClick = { viewModel.setDate(today.minusDays(1)) }, label = { Text(stringResource(R.string.op_yesterday)) })
                val other = p.date != today && p.date != today.minusDays(1)
                FilterChip(
                    selected = other,
                    onClick = { pickDate = true },
                    label = { Text(if (other) p.date.toString() else stringResource(R.string.op_pick_date)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                TextButton(onClick = viewModel::close, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = viewModel::apply, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.debt_action_apply)) }
            }
        }
    }
    if (pickDate) LocalDatePickerDialog(p.date, viewModel::setDate) { pickDate = false }
}

fun DebtPaymentKind.labelRes(): Int = when (this) {
    DebtPaymentKind.INITIAL -> R.string.debt_kind_initial
    DebtPaymentKind.REPAY -> R.string.debt_kind_repay
    DebtPaymentKind.INCREASE -> R.string.debt_kind_increase
    DebtPaymentKind.FORGIVE -> R.string.debt_kind_forgive
}
