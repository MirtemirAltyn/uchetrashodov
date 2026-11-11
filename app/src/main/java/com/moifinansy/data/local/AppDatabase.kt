package com.moifinansy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moifinansy.data.local.dao.AccountDao
import com.moifinansy.data.local.dao.BudgetDao
import com.moifinansy.data.local.dao.CategoryDao
import com.moifinansy.data.local.dao.CurrencyRateDao
import com.moifinansy.data.local.dao.DebtDao
import com.moifinansy.data.local.dao.RecurringRuleDao
import com.moifinansy.data.local.dao.TagDao
import com.moifinansy.data.local.dao.TransactionDao
import com.moifinansy.data.local.entity.AccountEntity
import com.moifinansy.data.local.entity.BudgetEntity
import com.moifinansy.data.local.entity.CategoryEntity
import com.moifinansy.data.local.entity.ContactEntity
import com.moifinansy.data.local.entity.CurrencyRateEntity
import com.moifinansy.data.local.entity.DebtEntity
import com.moifinansy.data.local.entity.DebtPaymentEntity
import com.moifinansy.data.local.entity.RecurringRuleEntity
import com.moifinansy.data.local.entity.TagEntity
import com.moifinansy.data.local.entity.TransactionEntity
import com.moifinansy.data.local.entity.TransactionTagEntity

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TagEntity::class,
        TransactionTagEntity::class,
        ContactEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        CurrencyRateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun debtDao(): DebtDao
    abstract fun tagDao(): TagDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun currencyRateDao(): CurrencyRateDao

    companion object {
        const val NAME = "moi_finansy.db"
    }
}
