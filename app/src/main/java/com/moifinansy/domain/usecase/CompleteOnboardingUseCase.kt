package com.moifinansy.domain.usecase

import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.settings.SettingsRepository
import javax.inject.Inject

/** Первый запуск: основная валюта + первый счёт, затем онбординг считается пройденным. */
class CompleteOnboardingUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(currencyCode: String, accountName: String, accountType: AccountType, initialBalance: Long): Long {
        require(Currencies.all.any { it.code == currencyCode }) { "Неизвестная валюта $currencyCode" }
        val accountId = accounts.create(
            Account(
                name = accountName.trim(),
                type = accountType,
                currencyCode = currencyCode,
                initialBalance = initialBalance,
                color = AccountDefaults.color,
                icon = AccountDefaults.icon(accountType),
            ),
        )
        settings.setBaseCurrency(currencyCode)
        settings.setOnboardingCompleted(true)
        return accountId
    }
}

object AccountDefaults {
    /** Цвет основного акцента (#5B5BD6). */
    const val color: Long = 0xFF5B5BD6

    fun icon(type: AccountType): String = when (type) {
        AccountType.CASH -> "payments"
        AccountType.CARD -> "credit_card"
        AccountType.EWALLET -> "account_balance_wallet"
        AccountType.SAVINGS -> "savings"
        AccountType.OTHER -> "account_balance"
    }
}
