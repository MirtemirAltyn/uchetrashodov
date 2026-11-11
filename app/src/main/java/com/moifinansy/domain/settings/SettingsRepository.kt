package com.moifinansy.domain.settings

import kotlinx.coroutines.flow.Flow

/** Настройки приложения (DataStore). */
interface SettingsRepository {
    /** Основная валюта; null — ещё не выбрана (онбординг не пройден). */
    val baseCurrency: Flow<String?>
    val onboardingCompleted: Flow<Boolean>

    /** Показывать ли тип счёта (наличные, карта…). Выключено — тип не спрашивается и не показывается. */
    val showAccountTypes: Flow<Boolean>

    suspend fun setBaseCurrency(code: String)
    suspend fun setShowAccountTypes(show: Boolean)

    /** Счёт последней операции — подставляется в новую операцию. */
    val lastAccountId: Flow<Long?>
    suspend fun setLastAccountId(id: Long)
    suspend fun setOnboardingCompleted(completed: Boolean)
}
