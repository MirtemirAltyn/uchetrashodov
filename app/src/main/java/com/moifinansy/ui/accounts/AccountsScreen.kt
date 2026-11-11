package com.moifinansy.ui.accounts

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.GradientHeader
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.MoneyText
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.labelRes
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.AccountWithBalance

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.accounts_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.accounts_add))
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
                    title = stringResource(R.string.accounts_total),
                    amount = MoneyFormat.format(state.total, state.baseCurrency),
                    subtitle = stringResource(R.string.accounts_total_hint),
                )
            }
            if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.accounts_empty),
                        modifier = Modifier.padding(vertical = Spacing.grid * 3),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.active, key = { it.account.id }) { AccountCard(it, onClick = { onOpen(it.account.id) }) }
            if (state.archived.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.section_archived)) }
                items(state.archived, key = { it.account.id }) {
                    AccountCard(it, onClick = { onOpen(it.account.id) }, modifier = Modifier.alpha(0.6f))
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 72.dp)) }
        }
    }
}

@Composable
fun AccountCard(item: AccountWithBalance, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val account = item.account
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
        ) {
            IconBadge(account.icon, account.color)
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                val subtitle = stringResource(account.type.labelRes()) +
                    if (!account.includeInTotal) " · " + stringResource(R.string.account_not_in_total) else ""
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MoneyText(item.balance, account.currencyCode)
        }
    }
}

/** Пункт меню «Ещё» для перехода в раздел. */
@Composable
fun MenuRow(title: String, subtitle: String?, icon: String, color: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid * 1.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
    ) {
        IconBadge(icon, color)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
