package com.moifinansy.domain.model

/**
 * Насколько операция меняет баланс счёта [accountId]. Та же формула, что в `AccountDao.BALANCE_SQL`,
 * — для отображения истории счёта; сам баланс всегда считает база.
 */
fun Transaction.effectOn(accountId: Long): Long {
    var effect = 0L
    if (this.accountId == accountId) {
        effect += when (type) {
            TransactionType.INCOME -> amount
            TransactionType.EXPENSE -> -amount
            TransactionType.TRANSFER -> -(amount + fee)
            TransactionType.ADJUSTMENT, TransactionType.DEBT -> amount
        }
    }
    if (type == TransactionType.TRANSFER && toAccountId == accountId) {
        effect += toAmount ?: amount
    }
    return effect
}
