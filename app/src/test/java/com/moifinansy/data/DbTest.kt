package com.moifinansy.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moifinansy.data.local.AppDatabase
import com.moifinansy.data.local.DefaultCategories
import com.moifinansy.data.repository.AccountRepositoryImpl
import com.moifinansy.data.repository.CategoryRepositoryImpl
import com.moifinansy.data.repository.DebtRepositoryImpl
import com.moifinansy.data.repository.TransactionRepositoryImpl
import com.moifinansy.domain.model.Account
import com.moifinansy.domain.model.AccountType
import com.moifinansy.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Room в памяти с предустановленными категориями (через Robolectric, на JVM). */
@RunWith(AndroidJUnit4::class)
abstract class DbTest {
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)
    protected val now: Long get() = clock.millis()

    protected lateinit var db: AppDatabase
    protected lateinit var accounts: AccountRepositoryImpl
    protected lateinit var categories: CategoryRepositoryImpl
    protected lateinit var transactions: TransactionRepositoryImpl
    protected lateinit var debts: DebtRepositoryImpl

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(DefaultCategories.callback { clock.millis() })
            .allowMainThreadQueries()
            .build()
        accounts = AccountRepositoryImpl(db.accountDao(), clock)
        categories = CategoryRepositoryImpl(db.categoryDao(), clock)
        transactions = TransactionRepositoryImpl(db.transactionDao(), db.accountDao(), db.categoryDao(), clock)
        debts = DebtRepositoryImpl(db.debtDao(), clock)
    }

    @After
    fun closeDb() = db.close()

    protected suspend fun account(name: String, initial: Long = 0, currency: String = "KGS", inTotal: Boolean = true): Long =
        accounts.create(
            Account(
                name = name, type = AccountType.CARD, currencyCode = currency, initialBalance = initial,
                color = 0xFF5B5BD6, icon = "credit_card", includeInTotal = inTotal,
            ),
        )

    protected suspend fun categoryId(name: String, kind: CategoryKind = CategoryKind.EXPENSE): Long =
        categories.observe(kind).first().first { it.name == name }.id
}
