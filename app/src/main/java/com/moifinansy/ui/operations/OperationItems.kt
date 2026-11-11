package com.moifinansy.ui.operations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.moifinansy.R
import com.moifinansy.core.format.DateFormat
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.MoneyText
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.model.TransactionType

/** Операция, готовая к показу в списке. */
data class OperationItem(
    val transaction: Transaction,
    val title: Title,
    val icon: String,
    val color: Long,
    val accountName: String?,
    val currencyCode: String,
    /** Сумма для показа: расход — со знаком «−», доход — «+», перевод — без знака. */
    val displayAmount: Long,
) {
    val id: Long get() = transaction.id
    val type: TransactionType get() = transaction.type

    sealed interface Title {
        data class Category(val name: String?, val parentName: String?) : Title
        data class Transfer(val from: String?, val to: String?) : Title
        data object Adjustment : Title
        data object Debt : Title
    }

    companion object {
        private const val NEUTRAL = 0xFF78909C

        fun build(
            transactions: List<Transaction>,
            accounts: Map<Long, AccountWithBalance>,
            categories: Map<Long, Category>,
        ): List<OperationItem> = transactions.map { t ->
            val account = accounts[t.accountId]?.account
            val currency = account?.currencyCode ?: "KGS"
            when (t.type) {
                TransactionType.EXPENSE, TransactionType.INCOME -> {
                    val category = t.categoryId?.let { categories[it] }
                    val parent = category?.parentId?.let { categories[it] }
                    OperationItem(
                        t, Title.Category(category?.name, parent?.name), category?.icon ?: "more_horiz",
                        category?.color ?: NEUTRAL, account?.name, currency,
                        if (t.type == TransactionType.EXPENSE) -t.amount else t.amount,
                    )
                }
                TransactionType.TRANSFER -> OperationItem(
                    t, Title.Transfer(account?.name, t.toAccountId?.let { accounts[it]?.account?.name }),
                    "account_balance", account?.color ?: NEUTRAL, null, currency, t.amount,
                )
                TransactionType.ADJUSTMENT -> OperationItem(
                    t, Title.Adjustment, "build", account?.color ?: NEUTRAL, account?.name, currency, t.amount,
                )
                TransactionType.DEBT -> OperationItem(
                    t, Title.Debt, "attach_money", account?.color ?: NEUTRAL, account?.name, currency, t.amount,
                )
            }
        }
    }
}

@Composable
fun OperationItem.Title.text(): String {
    val unknown = stringResource(R.string.operation_unknown_account)
    return when (this) {
        is OperationItem.Title.Category -> name ?: stringResource(R.string.operation_unknown_category)
        is OperationItem.Title.Transfer -> stringResource(R.string.op_transfer_title, from ?: unknown, to ?: unknown)
        OperationItem.Title.Adjustment -> stringResource(R.string.operation_type_adjustment)
        OperationItem.Title.Debt -> stringResource(R.string.operation_type_debt)
    }
}

@Composable
fun OperationRow(item: OperationItem, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
    ) {
        IconBadge(item.icon, item.color)
        Column(Modifier.weight(1f)) {
            Text(item.title.text(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parent = (item.title as? OperationItem.Title.Category)?.parentName
            val subtitle = listOfNotNull(
                if (showDate) DateFormat.shortDay(item.transaction.date) else null,
                parent,
                item.accountName,
                item.transaction.note?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.type == TransactionType.TRANSFER) {
            Text(MoneyFormat.format(item.displayAmount, item.currencyCode), style = MaterialTheme.typography.titleMedium)
        } else {
            MoneyText(item.displayAmount, item.currencyCode, signed = true)
        }
    }
}
