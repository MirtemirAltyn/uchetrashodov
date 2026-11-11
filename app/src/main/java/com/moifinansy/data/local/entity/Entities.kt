package com.moifinansy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.BudgetPeriod
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.DebtDirection
import com.moifinansy.domain.model.DebtPaymentKind
import com.moifinansy.domain.model.DebtStatus
import com.moifinansy.domain.model.RecurrenceFrequency
import com.moifinansy.domain.model.RecurringMode
import com.moifinansy.domain.model.TransactionType

// Перечисления хранятся строкой (имя константы). Суммы — Long в минимальных единицах.

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currencyCode: String,
    val initialBalance: Long,
    val color: Long,
    val icon: String,
    val includeInTotal: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** Демо-запись: удаляется кнопкой «Удалить демо-данные». */
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("parentId"), Index("kind"), Index(value = ["systemKey"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long?,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
    val isArchived: Boolean,
    val systemKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RecurringRuleEntity::class, parentColumns = ["id"], childColumns = ["recurringId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("date"), Index("accountId"), Index("categoryId"),
        Index("toAccountId"), Index("debtId"), Index("recurringId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    /** EXPENSE/INCOME/TRANSFER — > 0; ADJUSTMENT/DEBT — со знаком (влияние на баланс счёта). */
    val amount: Long,
    val accountId: Long,
    val categoryId: Long?,
    val toAccountId: Long?,
    val toAmount: Long?,
    val fee: Long,
    val date: Long,
    val note: String?,
    val debtId: Long?,
    val recurringId: Long?,
    val receiptUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Демо-запись: удаляется кнопкой «Удалить демо-данные». */
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Демо-запись: удаляется кнопкой «Удалить демо-данные». */
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TransactionTagEntity(
    val transactionId: Long,
    val tagId: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Демо-запись: удаляется кнопкой «Удалить демо-данные». */
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contactId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("contactId"), Index("status")],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val direction: DebtDirection,
    val principal: Long,
    val currencyCode: String,
    val startDate: Long,
    val dueDate: Long?,
    val note: String?,
    val status: DebtStatus,
    val affectsAccounts: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Демо-запись: удаляется кнопкой «Удалить демо-данные». */
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(entity = DebtEntity::class, parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("debtId"), Index("accountId"), Index("transactionId")],
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val kind: DebtPaymentKind,
    /** Всегда > 0; направление задаёт [kind]. */
    val amount: Long,
    val accountId: Long?,
    val date: Long,
    val transactionId: Long?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,
    val limitAmount: Long,
    val period: BudgetPeriod,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Демо-запись: удаляется кнопкой «Удалить демо-данные». */
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["toAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId"), Index("nextDate")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val categoryId: Long?,
    val toAccountId: Long?,
    val toAmount: Long?,
    val fee: Long,
    val note: String?,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val nextDate: Long,
    val mode: RecurringMode,
    val endDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Демо-запись: удаляется кнопкой «Удалить демо-данные». */
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
)

@Entity(tableName = "currency_rates")
data class CurrencyRateEntity(
    @PrimaryKey val code: String,
    /** Курс к основной валюте × 1 000 000 (без Double). */
    val rateToBaseMicros: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

// Результаты запросов.

data class AccountWithBalanceRow(
    @Embedded val account: AccountEntity,
    val balance: Long,
)

data class DebtWithRemainingRow(
    @Embedded val debt: DebtEntity,
    val remaining: Long,
    /** Сколько всего дали/взяли: первоначальная сумма + дозаймы. */
    val totalAmount: Long,
    /** Счёт первого движения — по умолчанию для погашений. */
    val initialAccountId: Long?,
)

data class DebtTotalsRow(val iOwe: Long, val owedToMe: Long)

data class ContactBalanceRow(val contactId: Long, val currencyCode: String, val saldo: Long)

data class CategoryTotalRow(val categoryId: Long, val total: Long)

/** Месяц «2026-11»: доходы, расходы (с комиссиями) и чистое изменение денег на счетах. */
data class TagNameRow(val transactionId: Long, val name: String)

data class MonthAmountRow(val month: String, val total: Long)

data class MonthTotalsRow(val month: String, val income: Long, val expense: Long, val net: Long)
