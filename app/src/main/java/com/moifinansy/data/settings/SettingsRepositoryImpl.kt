package com.moifinansy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.moifinansy.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val baseCurrency: Flow<String?> = dataStore.data.map { it[BASE_CURRENCY] }.distinctUntilChanged()

    override val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }.distinctUntilChanged()

    override val showAccountTypes: Flow<Boolean> =
        dataStore.data.map { it[SHOW_ACCOUNT_TYPES] ?: true }.distinctUntilChanged()

    override suspend fun setShowAccountTypes(show: Boolean) {
        dataStore.edit { it[SHOW_ACCOUNT_TYPES] = show }
    }

    override val firstDayOfWeek: Flow<java.time.DayOfWeek> = dataStore.data
        .map { prefs -> prefs[FIRST_DAY]?.let { runCatching { java.time.DayOfWeek.valueOf(it) }.getOrNull() } ?: java.time.DayOfWeek.MONDAY }
        .distinctUntilChanged()

    override suspend fun setFirstDayOfWeek(day: java.time.DayOfWeek) {
        dataStore.edit { it[FIRST_DAY] = day.name }
    }

    override val debtReminderDays: Flow<Int> = dataStore.data.map { it[DEBT_DAYS] ?: 1 }.distinctUntilChanged()

    override suspend fun setDebtReminderDays(days: Int) {
        dataStore.edit { it[DEBT_DAYS] = days }
    }

    override suspend fun wasNotified(key: String): Boolean = key in (dataStore.data.first()[NOTIFIED] ?: emptySet())

    override suspend fun markNotified(key: String) {
        // Храним последние ключи, чтобы набор не рос бесконечно.
        dataStore.edit { it[NOTIFIED] = ((it[NOTIFIED] ?: emptySet()) + key).toList().takeLast(500).toSet() }
    }

    override val theme: Flow<com.moifinansy.domain.settings.ThemeMode> = dataStore.data
        .map { p -> p[THEME]?.let { runCatching { com.moifinansy.domain.settings.ThemeMode.valueOf(it) }.getOrNull() } ?: com.moifinansy.domain.settings.ThemeMode.SYSTEM }
        .distinctUntilChanged()

    override suspend fun setTheme(mode: com.moifinansy.domain.settings.ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    override val hideAmounts: Flow<Boolean> = dataStore.data.map { it[HIDE_AMOUNTS] ?: false }.distinctUntilChanged()

    override suspend fun setHideAmounts(hide: Boolean) {
        dataStore.edit { it[HIDE_AMOUNTS] = hide }
    }

    override val security: Flow<com.moifinansy.domain.settings.SecuritySettings> = dataStore.data.map {
        com.moifinansy.domain.settings.SecuritySettings(it[PIN_HASH], it[PIN_SALT], it[BIOMETRIC] ?: false, it[LOCK_AFTER] ?: 1)
    }.distinctUntilChanged()

    override suspend fun setPin(hash: String?, salt: String?) {
        dataStore.edit {
            if (hash == null || salt == null) {
                it.remove(PIN_HASH)
                it.remove(PIN_SALT)
                it[BIOMETRIC] = false
            } else {
                it[PIN_HASH] = hash
                it[PIN_SALT] = salt
            }
        }
    }

    override suspend fun setBiometric(enabled: Boolean) {
        dataStore.edit { it[BIOMETRIC] = enabled }
    }

    override suspend fun setLockAfterMinutes(minutes: Int) {
        dataStore.edit { it[LOCK_AFTER] = minutes }
    }

    override val lastAccountId: Flow<Long?> = dataStore.data.map { it[LAST_ACCOUNT_ID] }.distinctUntilChanged()

    override suspend fun setLastAccountId(id: Long) {
        dataStore.edit { it[LAST_ACCOUNT_ID] = id }
    }

    override suspend fun setBaseCurrency(code: String) {
        dataStore.edit { it[BASE_CURRENCY] = code }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    private companion object {
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SHOW_ACCOUNT_TYPES = booleanPreferencesKey("show_account_types")
        val LAST_ACCOUNT_ID = longPreferencesKey("last_account_id")
        val FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val THEME = stringPreferencesKey("theme")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC = booleanPreferencesKey("biometric")
        val LOCK_AFTER = androidx.datastore.preferences.core.intPreferencesKey("lock_after_minutes")
        val DEBT_DAYS = androidx.datastore.preferences.core.intPreferencesKey("debt_reminder_days")
        val NOTIFIED = androidx.datastore.preferences.core.stringSetPreferencesKey("notified_keys")
    }
}
