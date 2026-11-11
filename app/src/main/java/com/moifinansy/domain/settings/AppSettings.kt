package com.moifinansy.domain.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Настройки защиты входа. */
data class SecuritySettings(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometric: Boolean = false,
    /** Через сколько минут в фоне снова спрашивать PIN (0 — сразу). */
    val lockAfterMinutes: Int = 1,
) {
    val pinEnabled: Boolean get() = pinHash != null && pinSalt != null
}
