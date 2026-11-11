package com.moifinansy.ui.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.Spacing

@Composable
fun ContactScreen(onBack: () -> Unit, onOpenDebt: (Long) -> Unit, onAddDebt: (Long) -> Unit, viewModel: ContactViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onBack() }
    val contact = state.contact ?: return
    var name by remember(contact.id) { mutableStateOf(contact.name) }
    var phone by remember(contact.id) { mutableStateOf(contact.phone.orEmpty()) }
    val colors = LocalFinanceColors.current

    Scaffold(
        topBar = { BackTopBar(contact.name, onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            item(key = "head") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 2)) {
                    Avatar(contact.name, size = 56.dp)
                    Column {
                        if (state.balances.isEmpty()) Text(stringResource(R.string.contact_saldo_zero), style = MaterialTheme.typography.titleMedium)
                        state.balances.forEach { b ->
                            val amount = MoneyFormat.format(kotlin.math.abs(b.saldo), b.currencyCode)
                            Text(
                                stringResource(if (b.saldo >= 0) R.string.contact_saldo_plus else R.string.contact_saldo_minus, amount),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (b.saldo >= 0) colors.income else colors.expense,
                            )
                        }
                    }
                }
            }
            item(key = "form") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.debt_contact_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.debt_contact_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.save(name, phone) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
            item(key = "debtsTitle") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.contact_debts), Modifier.weight(1f))
                    TextButton(onClick = { onAddDebt(contact.id) }) { Text(stringResource(R.string.debts_add)) }
                }
            }
            items(state.debts, key = { it.debt.debt.id }) { card ->
                DebtCardView(card, onClick = { onOpenDebt(card.debt.debt.id) }, onPart = { onOpenDebt(card.debt.debt.id) })
            }
            if (state.debts.isEmpty()) {
                item(key = "delete") {
                    TextButton(onClick = viewModel::delete, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.contact_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
