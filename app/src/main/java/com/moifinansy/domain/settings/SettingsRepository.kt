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

    /** Первый день недели для отчётов по неделям. */
    val firstDayOfWeek: Flow<java.time.DayOfWeek>
    suspend fun setFirstDayOfWeek(day: java.time.DayOfWeek)

    /** За сколько дней до срока долга напоминать (0 — только в день срока). */
    val debtReminderDays: Flow<Int>
    suspend fun setDebtReminderDays(days: Int)

    /** Ключи уже показанных напоминаний (чтобы не повторять). */
    suspend fun wasNotified(key: String): Boolean
    suspend fun markNotified(key: String)

    val theme: Flow<ThemeMode>
    suspend fun setTheme(mode: ThemeMode)

    /** Скрывать суммы на главном экране. */
    val hideAmounts: Flow<Boolean>
    suspend fun setHideAmounts(hide: Boolean)

    val security: Flow<SecuritySettings>
    /** null — выключить PIN (и биометрию). */
    suspend fun setPin(hash: String?, salt: String?)
    suspend fun setBiometric(enabled: Boolean)
    suspend fun setLockAfterMinutes(minutes: Int)

    /** Счёт последней операции — подставляется в новую операцию. */
    val lastAccountId: Flow<Long?>
    suspend fun setLastAccountId(id: Long)
    suspend fun setOnboardingCompleted(completed: Boolean)
}
