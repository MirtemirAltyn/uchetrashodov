package com.moifinansy.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.R
import com.moifinansy.data.backup.BackupManager
import com.moifinansy.data.backup.CsvExporter
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.Currencies
import com.moifinansy.domain.model.OperationFilter
import com.moifinansy.domain.model.Periods
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.CurrencyRateRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.domain.settings.SecuritySettings
import com.moifinansy.domain.settings.SettingsRepository
import com.moifinansy.domain.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.YearMonth
import javax.inject.Inject

enum class ExportPeriod { MONTH, YEAR, ALL }

data class SettingsState(
    val showAccountTypes: Boolean = true,
    val debtReminderDays: Int = 1,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val hideAmounts: Boolean = false,
    val baseCurrency: String = Currencies.DEFAULT,
    val missingRates: List<String> = emptyList(),
    val security: SecuritySettings = SecuritySettings(),
    val debtsAffectBalance: Boolean = true,
    val demoCount: Int = 0,
    val userTransactions: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val rates: CurrencyRateRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val backup: BackupManager,
    private val demo: com.moifinansy.data.demo.DemoDataGenerator,
    private val demoDao: com.moifinansy.data.local.dao.DemoDao,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val basic = combine(settings.showAccountTypes, settings.debtReminderDays, settings.theme, settings.firstDayOfWeek, settings.hideAmounts) {
            types, days, theme, firstDay, hide ->
        SettingsState(showAccountTypes = types, debtReminderDays = days, theme = theme, firstDay = firstDay, hideAmounts = hide)
    }

    val state: StateFlow<SettingsState> = combine(basic, settings.baseCurrency, settings.security, settings.debtsAffectBalance) { b, base, security, debts ->
        b.copy(baseCurrency = base ?: Currencies.DEFAULT, security = security, debtsAffectBalance = debts)
    }.flatMapLatest { s ->
        combine(accounts.observeCurrenciesWithoutRate(s.baseCurrency), demoDao.observeDemoCount(), demoDao.observeUserTransactionCount()) { missing, demo, user ->
            s.copy(missingRates = missing, demoCount = demo, userTransactions = user)
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() {
        _message.value = null
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun setShowAccountTypes(value: Boolean) = launch { settings.setShowAccountTypes(value) }
    fun setDebtReminderDays(days: Int) = launch { settings.setDebtReminderDays(days) }
    fun setDebtsAffectBalance(value: Boolean) = launch { settings.setDebtsAffectBalance(value) }
    fun setTheme(mode: ThemeMode) = launch { settings.setTheme(mode) }
    fun setFirstDay(day: DayOfWeek) = launch { settings.setFirstDayOfWeek(day) }
    fun setHideAmounts(value: Boolean) = launch { settings.setHideAmounts(value) }
    fun setBiometric(value: Boolean) = launch { settings.setBiometric(value) }
    fun setLockAfter(minutes: Int) = launch { settings.setLockAfterMinutes(minutes) }
    fun disablePin() = launch { settings.setPin(null, null) }

    /** Курсы задаются к основной валюте — при её смене старые курсы теряют смысл. */
    fun setBaseCurrency(code: String) = launch {
        if (code == state.value.baseCurrency) return@launch
        settings.setBaseCurrency(code)
        rates.deleteAll()
    }

    fun exportCsv(uri: Uri, period: ExportPeriod) = launch {
        runCatching {
            val range = when (period) {
                ExportPeriod.MONTH -> Periods.month(YearMonth.now(clock), clock.zone)
                ExportPeriod.YEAR -> Periods.year(YearMonth.now(clock).year, clock.zone)
                ExportPeriod.ALL -> Periods.ALL
            }
            val list = transactions.observeFiltered(OperationFilter(range.from, range.to)).first()
            val accs = accounts.observeAccounts(includeArchived = true).first().associate { it.account.id to it.account }
            val cats = (categories.observe(CategoryKind.EXPENSE, true).first() + categories.observe(CategoryKind.INCOME, true).first()).associateBy { it.id }
            val csv = CsvExporter.build(list, accs, cats, transactions.tagNamesByTransaction(), clock.zone)
            write(uri, csv)
            list.size
        }.onSuccess { _message.value = context.getString(R.string.data_export_done, it) }
            .onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
    }

    fun fillDemo() = launch {
        runCatching { demo.generate() }
            .onSuccess { _message.value = context.getString(R.string.demo_filled) }
            .onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
    }

    fun deleteDemo() = launch {
        runCatching { demo.delete() }
            .onSuccess { _message.value = context.getString(R.string.demo_deleted) }
            .onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
    }

    fun backup(uri: Uri) = launch {
        runCatching { write(uri, backup.export()) }
            .onSuccess { _message.value = context.getString(R.string.data_backup_done) }
            .onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
    }

    fun restore(uri: Uri) = launch {
        runCatching {
            val json = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("файл не открылся")
            }
            backup.restore(json)
        }.onSuccess { _message.value = context.getString(R.string.data_restore_done) }
            .onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
    }

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("файл не создан")
    }
}
