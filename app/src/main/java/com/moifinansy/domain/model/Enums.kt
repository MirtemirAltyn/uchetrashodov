package com.moifinansy.domain.model

enum class AccountType { CASH, CARD, EWALLET, SAVINGS, OTHER }

enum class CategoryKind { EXPENSE, INCOME }

/**
 * Тип операции и знак суммы.
 *
 * EXPENSE, INCOME, TRANSFER — `amount` всегда > 0, направление задаёт тип.
 * ADJUSTMENT, DEBT — `amount` со знаком: насколько операция меняет баланс счёта
 * (+ деньги пришли на счёт, − ушли).
 */
enum class TransactionType { EXPENSE, INCOME, TRANSFER, ADJUSTMENT, DEBT }

enum class DebtDirection { I_OWE, OWED_TO_ME }

/** Хранимый статус. «Просрочен» не хранится: он вычисляется из срока (см. [com.moifinansy.domain.debt.DebtMath]). */
enum class DebtStatus { ACTIVE, CLOSED, FORGIVEN }

enum class DebtPaymentKind { INITIAL, REPAY, INCREASE, FORGIVE }

enum class BudgetPeriod { MONTH }

enum class RecurrenceFrequency { DAY, WEEK, MONTH, YEAR }

enum class RecurringMode { AUTO, REMIND }
