package com.moifinansy.core.ui

import androidx.annotation.StringRes
import com.moifinansy.R
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.CategoryKind

@StringRes
fun AccountType.labelRes(): Int = when (this) {
    AccountType.CASH -> R.string.account_type_cash
    AccountType.CARD -> R.string.account_type_card
    AccountType.EWALLET -> R.string.account_type_ewallet
    AccountType.SAVINGS -> R.string.account_type_savings
    AccountType.OTHER -> R.string.account_type_other
}

@StringRes
fun CategoryKind.labelRes(): Int = when (this) {
    CategoryKind.EXPENSE -> R.string.categories_expenses
    CategoryKind.INCOME -> R.string.categories_incomes
}
