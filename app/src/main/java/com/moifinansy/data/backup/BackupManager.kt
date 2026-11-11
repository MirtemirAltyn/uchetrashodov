package com.moifinansy.data.backup

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import com.moifinansy.data.local.AppDatabase
import com.moifinansy.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Резервная копия всей базы в JSON и восстановление из неё.
 * Восстановление заменяет все данные одной транзакцией: при ошибке ничего не меняется.
 */
class BackupManager @Inject constructor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun export(): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("schemaVersion", db.openHelper.readableDatabase.version)
        root.put("baseCurrency", settings.baseCurrency.first())
        root.put("showAccountTypes", settings.showAccountTypes.first())
        val tables = JSONObject()
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            TABLES.forEach { table -> sql.query("SELECT * FROM $table").use { tables.put(table, it.toJson()) } }
        }
        root.put("tables", tables)
        return root.toString()
    }

    /** Заменяет все данные содержимым копии. Бросает исключение на неверном файле. */
    suspend fun restore(json: String) {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Это не резервная копия «Мои финансы»" }
        val version = root.getInt("schemaVersion")
        require(version <= db.openHelper.readableDatabase.version) { "Копия из более новой версии приложения" }
        val tables = root.getJSONObject("tables")
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("PRAGMA defer_foreign_keys = ON")
            TABLES.reversed().forEach { sql.execSQL("DELETE FROM $it") }
            TABLES.forEach { table ->
                val rows = tables.optJSONArray(table) ?: return@forEach
                for (i in 0 until rows.length()) {
                    sql.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, rows.getJSONObject(i).toContentValues())
                }
            }
        }
        root.optString("baseCurrency").takeIf { it.isNotEmpty() && it != "null" }?.let { settings.setBaseCurrency(it) }
        if (root.has("showAccountTypes")) settings.setShowAccountTypes(root.getBoolean("showAccountTypes"))
        settings.setOnboardingCompleted(true)
    }

    private fun Cursor.toJson(): JSONArray {
        val array = JSONArray()
        while (moveToNext()) {
            val row = JSONObject()
            for (i in 0 until columnCount) {
                when (getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> row.put(getColumnName(i), JSONObject.NULL)
                    Cursor.FIELD_TYPE_INTEGER -> row.put(getColumnName(i), getLong(i))
                    Cursor.FIELD_TYPE_FLOAT -> row.put(getColumnName(i), getDouble(i))
                    else -> row.put(getColumnName(i), getString(i))
                }
            }
            array.put(row)
        }
        return array
    }

    private fun JSONObject.toContentValues(): ContentValues {
        val values = ContentValues()
        keys().forEach { key ->
            when (val v = get(key)) {
                JSONObject.NULL -> values.putNull(key)
                is Int -> values.put(key, v.toLong())
                is Long -> values.put(key, v)
                is Double -> values.put(key, v)
                is Boolean -> values.put(key, if (v) 1L else 0L)
                else -> values.put(key, v.toString())
            }
        }
        return values
    }

    companion object {
        const val FORMAT = "moi-finansy-backup"

        /** Порядок вставки — от независимых таблиц к зависимым. */
        val TABLES = listOf(
            "accounts", "categories", "contacts", "tags", "currency_rates", "recurring_rules",
            "debts", "transactions", "transaction_tags", "debt_payments", "budgets",
        )
    }
}
