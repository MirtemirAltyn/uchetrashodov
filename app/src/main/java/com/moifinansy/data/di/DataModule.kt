package com.moifinansy.data.di

import android.content.Context
import androidx.room.Room
import com.moifinansy.data.local.AppDatabase
import com.moifinansy.data.local.DefaultCategories
import com.moifinansy.data.repository.AccountRepositoryImpl
import com.moifinansy.data.repository.BudgetRepositoryImpl
import com.moifinansy.data.repository.CategoryRepositoryImpl
import com.moifinansy.data.repository.CurrencyRateRepositoryImpl
import com.moifinansy.data.repository.DebtRepositoryImpl
import com.moifinansy.data.repository.RecurringRepositoryImpl
import com.moifinansy.data.repository.TagRepositoryImpl
import com.moifinansy.data.repository.TransactionRepositoryImpl
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.BudgetRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.CurrencyRateRepository
import com.moifinansy.domain.repository.DebtRepository
import com.moifinansy.domain.repository.RecurringRepository
import com.moifinansy.domain.repository.TagRepository
import com.moifinansy.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, clock: Clock): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(DefaultCategories.callback { clock.millis() })
            .build()

    @Provides fun accountDao(db: AppDatabase) = db.accountDao()
    @Provides fun categoryDao(db: AppDatabase) = db.categoryDao()
    @Provides fun transactionDao(db: AppDatabase) = db.transactionDao()
    @Provides fun debtDao(db: AppDatabase) = db.debtDao()
    @Provides fun tagDao(db: AppDatabase) = db.tagDao()
    @Provides fun budgetDao(db: AppDatabase) = db.budgetDao()
    @Provides fun recurringRuleDao(db: AppDatabase) = db.recurringRuleDao()
    @Provides fun currencyRateDao(db: AppDatabase) = db.currencyRateDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun accounts(impl: AccountRepositoryImpl): AccountRepository
    @Binds abstract fun categories(impl: CategoryRepositoryImpl): CategoryRepository
    @Binds abstract fun transactions(impl: TransactionRepositoryImpl): TransactionRepository
    @Binds abstract fun debts(impl: DebtRepositoryImpl): DebtRepository
    @Binds abstract fun tags(impl: TagRepositoryImpl): TagRepository
    @Binds abstract fun budgets(impl: BudgetRepositoryImpl): BudgetRepository
    @Binds abstract fun recurring(impl: RecurringRepositoryImpl): RecurringRepository
    @Binds abstract fun currencyRates(impl: CurrencyRateRepositoryImpl): CurrencyRateRepository
}
