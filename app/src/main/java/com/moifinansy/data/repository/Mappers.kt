package com.moifinansy.data.repository

import com.moifinansy.data.local.entity.AccountEntity
import com.moifinansy.data.local.entity.AccountWithBalanceRow
import com.moifinansy.data.local.entity.BudgetEntity
import com.moifinansy.data.local.entity.CategoryEntity
import com.moifinansy.data.local.entity.CategoryTotalRow
import com.moifinansy.data.local.entity.ContactBalanceRow
import com.moifinansy.data.local.entity.ContactEntity
import com.moifinansy.data.local.entity.CurrencyRateEntity
import com.moifinansy.data.local.entity.DebtEntity
import com.moifinansy.data.local.entity.DebtPaymentEntity
import com.moifinansy.data.local.entity.DebtWithRemainingRow
import com.moifinansy.data.local.entity.RecurringRuleEntity
import com.moifinansy.data.local.entity.TagEntity
import com.moifinansy.data.local.entity.TransactionEntity
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountWithBalance
import com.moifinansy.domain.model.Budget
import com.moifinansy.domain.model.Category
import com.moifinansy.domain.model.CategoryTotal
import com.moifinansy.domain.model.Contact
import com.moifinansy.domain.model.ContactBalance
import com.moifinansy.domain.model.CurrencyRate
import com.moifinansy.domain.model.Debt
import com.moifinansy.domain.model.DebtPayment
import com.moifinansy.domain.model.DebtWithRemaining
import com.moifinansy.domain.model.RecurringRule
import com.moifinansy.domain.model.Tag
import com.moifinansy.domain.model.Transaction

// Сущность → модель. createdAt при обновлении берём из старой записи.

fun AccountEntity.toModel() = Account(id, name, type, currencyCode, initialBalance, color, icon, includeInTotal, isArchived, sortOrder)
fun Account.toEntity(createdAt: Long, updatedAt: Long) =
    AccountEntity(id, name, type, currencyCode, initialBalance, color, icon, includeInTotal, isArchived, sortOrder, createdAt, updatedAt)
fun AccountWithBalanceRow.toModel() = AccountWithBalance(account.toModel(), balance)

fun CategoryEntity.toModel() = Category(id, name, kind, parentId, icon, color, sortOrder, isArchived, systemKey)
fun Category.toEntity(createdAt: Long, updatedAt: Long) =
    CategoryEntity(id, name, kind, parentId, icon, color, sortOrder, isArchived, systemKey, createdAt, updatedAt)

fun TransactionEntity.toModel() =
    Transaction(id, type, amount, accountId, categoryId, toAccountId, toAmount, fee, date, note, debtId, recurringId, receiptUri)
fun Transaction.toEntity(createdAt: Long, updatedAt: Long) = TransactionEntity(
    id, type, amount, accountId, categoryId, toAccountId, toAmount, fee, date, note, debtId, recurringId, receiptUri, createdAt, updatedAt,
)

fun TagEntity.toModel() = Tag(id, name)

fun ContactEntity.toModel() = Contact(id, name, phone, note)
fun Contact.toEntity(createdAt: Long, updatedAt: Long) = ContactEntity(id, name, phone, note, createdAt, updatedAt)

fun DebtEntity.toModel() = Debt(id, contactId, direction, principal, currencyCode, startDate, dueDate, note, status, affectsAccounts)
fun Debt.toEntity(createdAt: Long, updatedAt: Long) =
    DebtEntity(id, contactId, direction, principal, currencyCode, startDate, dueDate, note, status, affectsAccounts, createdAt, updatedAt)
fun DebtWithRemainingRow.toModel() = DebtWithRemaining(debt.toModel(), remaining)
fun DebtPaymentEntity.toModel() = DebtPayment(id, debtId, kind, amount, accountId, date, transactionId, note)
fun ContactBalanceRow.toModel() = ContactBalance(contactId, currencyCode, saldo)

fun CategoryTotalRow.toModel() = CategoryTotal(categoryId, total)

fun BudgetEntity.toModel() = Budget(id, categoryId, limitAmount, period, currencyCode)
fun Budget.toEntity(createdAt: Long, updatedAt: Long) =
    BudgetEntity(id, categoryId, limitAmount, period, currencyCode, createdAt, updatedAt)

fun RecurringRuleEntity.toModel() = RecurringRule(
    id, type, amount, accountId, categoryId, toAccountId, toAmount, fee, note, frequency, interval, nextDate, mode, endDate,
)
fun RecurringRule.toEntity(createdAt: Long, updatedAt: Long) = RecurringRuleEntity(
    id, type, amount, accountId, categoryId, toAccountId, toAmount, fee, note, frequency, interval, nextDate, mode, endDate, createdAt, updatedAt,
)

fun CurrencyRateEntity.toModel() = CurrencyRate(code, rateToBaseMicros)
