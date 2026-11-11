package com.moifinansy.ui.lock

import com.moifinansy.domain.security.PinHasher
import com.moifinansy.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Блокировка входа: при запуске (если PIN задан) и при возврате из фона дольше заданного времени.
 * Состояние живёт в памяти процесса: новый запуск приложения всегда начинается с блокировки.
 */
@Singleton
class LockManager @Inject constructor(
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundSince: Long? = null
    private var checkedAtStart = false

    /** Вызывается при каждом показе приложения. */
    suspend fun onForeground() {
        val security = settings.security.first()
        if (!security.pinEnabled) {
            _locked.value = false
            return
        }
        if (!checkedAtStart) {
            checkedAtStart = true
            _locked.value = true
            return
        }
        val since = backgroundSince ?: return
        backgroundSince = null
        if (clock.millis() - since >= security.lockAfterMinutes * 60_000L) _locked.value = true
    }

    fun onBackground() {
        backgroundSince = clock.millis()
    }

    suspend fun tryPin(pin: String): Boolean {
        val s = settings.security.first()
        val ok = s.pinHash != null && s.pinSalt != null && PinHasher.verify(pin, s.pinSalt, s.pinHash)
        if (ok) _locked.value = false
        return ok
    }

    fun unlockedByBiometric() {
        _locked.value = false
    }

    /** После включения PIN в настройках текущий сеанс остаётся открытым. */
    fun keepUnlocked() {
        checkedAtStart = true
        _locked.value = false
    }
}
