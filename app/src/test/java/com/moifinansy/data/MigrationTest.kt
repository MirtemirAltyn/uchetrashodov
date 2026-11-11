package com.moifinansy.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moifinansy.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `1 to 2 keeps data and marks it as not demo`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO accounts (id, name, type, currencyCode, initialBalance, color, icon, includeInTotal, isArchived, sortOrder, createdAt, updatedAt) " +
                    "VALUES (1, 'Карта', 'CARD', 'KGS', 1000, 0, 'credit_card', 1, 0, 0, 0, 0)",
            )
            db.execSQL(
                "INSERT INTO transactions (id, type, amount, accountId, categoryId, toAccountId, toAmount, fee, date, note, debtId, recurringId, receiptUri, createdAt, updatedAt) " +
                    "VALUES (1, 'ADJUSTMENT', -100, 1, NULL, NULL, NULL, 0, 0, NULL, NULL, NULL, NULL, 0, 0)",
            )
        }
        helper.runMigrationsAndValidate(DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name, isDemo FROM accounts").use { c ->
                c.moveToFirst()
                assertEquals("Карта", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM transactions WHERE isDemo = 0").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
