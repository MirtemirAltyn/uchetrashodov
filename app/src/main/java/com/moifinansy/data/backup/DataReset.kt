package com.moifinansy.data.backup

import androidx.room.withTransaction
import com.moifinansy.data.local.AppDatabase
import com.moifinansy.data.local.DefaultCategories
import com.moifinansy.domain.settings.SettingsRepository
import java.time.Clock
import javax.inject.Inject

/** «Начать с чистого листа»: пустая база со стандартными категориями и сброшенные настройки. */
class DataReset @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun resetAll() {
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("PRAGMA defer_foreign_keys = ON")
            (BackupManager.TABLES.reversed() + "currency_rates").distinct().forEach { sql.execSQL("DELETE FROM $it") }
            DefaultCategories.seed(sql, clock.millis())
        }
        settings.resetAll()
    }
}
