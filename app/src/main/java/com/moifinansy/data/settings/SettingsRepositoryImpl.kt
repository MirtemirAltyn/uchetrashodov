package com.moifinansy.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.moifinansy.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val baseCurrency: Flow<String?> = dataStore.data.map { it[BASE_CURRENCY] }.distinctUntilChanged()

    override val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }.distinctUntilChanged()

    override suspend fun setBaseCurrency(code: String) {
        dataStore.edit { it[BASE_CURRENCY] = code }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    private companion object {
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
