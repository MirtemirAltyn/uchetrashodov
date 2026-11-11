package com.moifinansy.domain.model

// Все суммы — Long в минимальных единицах валюты (тыйыны/копейки). Даты — epoch millis.

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val initialBalance: Long,
    val color: Long,
    val icon: String,
    val includeInTotal: Boolean = true,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

data class AccountWithBalance(val account: Account, val balance: Long)

data class Category(
    val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val icon: String,
    val color: Long,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    /** Служебный ключ предустановленной категории, например [SystemCategory.COMMISSIONS]. */
    val systemKey: String? = null,
)

object SystemCategory {
    /** Сюда в отчётах попадают комиссии переводов. */
    const val COMMISSIONS = "COMMISSIONS"
}

data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val toAccountId: Long? = null,
    /** Сколько пришло на счёт получателя (перевод в другой валюте). null — столько же, сколько [amount]. */
    val toAmount: Long? = null,
    val fee: Long = 0,
    val date: Long,
    val note: String? = null,
    val debtId: Long? = null,
    val recurringId: Long? = null,
    val receiptUri: String? = null,
)

data class Tag(val id: Long = 0, val name: String)

data class Contact(
    val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val note: String? = null,
)

data class Debt(
    val id: Long = 0,
    val contactId: Long,
    val direction: DebtDirection,
    val principal: Long,
    val currencyCode: String,
    val startDate: Long,
    val dueDate: Long? = null,
    val note: String? = null,
    val status: DebtStatus = DebtStatus.ACTIVE,
    val affectsAccounts: Boolean = true,
)

data class DebtWithRemaining(val debt: Debt, val remaining: Long)

data class DebtPayment(
    val id: Long = 0,
    val debtId: Long,
    val kind: DebtPaymentKind,
    val amount: Long,
    val accountId: Long?,
    val date: Long,
    val transactionId: Long? = null,
    val note: String? = null,
)

/** Итоги экрана долгов в одной валюте. */
data class DebtTotals(val iOwe: Long, val owedToMe: Long) {
    /** Сальдо = «Мне должны» − «Я должен». */
    val saldo: Long get() = owedToMe - iOwe
}

/** Сальдо по человеку: > 0 — он должен мне, < 0 — я ему. */
data class ContactBalance(val contactId: Long, val currencyCode: String, val saldo: Long)

data class CategoryTotal(val categoryId: Long, val total: Long)

data class Budget(
    val id: Long = 0,
    /** null — лимит на все расходы. */
    val categoryId: Long?,
    val limitAmount: Long,
    val period: BudgetPeriod = BudgetPeriod.MONTH,
    val currencyCode: String,
)

data class RecurringRule(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    val toAccountId: Long? = null,
    val toAmount: Long? = null,
    val fee: Long = 0,
    val note: String? = null,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val nextDate: Long,
    val mode: RecurringMode,
    val endDate: Long? = null,
)

/** Курс: сколько единиц основной валюты стоит одна единица [code], умноженное на 1 000 000. */
data class CurrencyRate(val code: String, val rateToBaseMicros: Long)
