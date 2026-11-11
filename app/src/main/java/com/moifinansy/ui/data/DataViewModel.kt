package com.moifinansy.ui.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moifinansy.R
import com.moifinansy.data.backup.BackupManager
import com.moifinansy.data.backup.CsvExporter
import com.moifinansy.data.backup.DataReset
import com.moifinansy.data.demo.DemoDataGenerator
import com.moifinansy.data.local.dao.DemoDao
import com.moifinansy.domain.model.CategoryKind
import com.moifinansy.domain.model.OperationFilter
import com.moifinansy.domain.model.Periods
import com.moifinansy.domain.repository.AccountRepository
import com.moifinansy.domain.repository.CategoryRepository
import com.moifinansy.domain.repository.TransactionRepository
import com.moifinansy.ui.lock.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

enum class ExportPeriod { MONTH, YEAR, ALL }

/** Шаг «начать заново»: предложение копии → подтверждение. */
enum class ResetStep { NONE, OFFER_BACKUP, CONFIRM }

data class DataState(
    val demoCount: Int = 0,
    val userTransactions: Int = 0,
    val confirmAddDemo: Boolean = false,
    val confirmDeleteDemo: Boolean = false,
    val reset: ResetStep = ResetStep.NONE,
    val busy: Boolean = false,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val backup: BackupManager,
    private val demo: DemoDataGenerator,
    private val reset: DataReset,
    private val lock: LockManager,
    demoDao: DemoDao,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val ui = MutableStateFlow(DataState())

    val state: StateFlow<DataState> = combine(ui, demoDao.observeDemoCount(), demoDao.observeUserTransactionCount()) { s, demo, user ->
        s.copy(demoCount = demo, userTransactions = user)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() {
        _message.value = null
    }

    private fun run(done: Int, block: suspend () -> Unit) = viewModelScope.launch {
        ui.update { it.copy(busy = true) }
        runCatching { block() }
            .onSuccess { _message.value = context.getString(done) }
            .onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
        ui.update { it.copy(busy = false) }
    }

    // Демо

    /** Без своих записей — сразу; со своими — сначала спросить. */
    fun requestDemo() {
        if (state.value.userTransactions > 0) ui.update { it.copy(confirmAddDemo = true) } else fillDemo()
    }

    fun fillDemo() {
        ui.update { it.copy(confirmAddDemo = false) }
        run(R.string.demo_filled) { demo.generate() }
    }

    fun requestDeleteDemo() = ui.update { it.copy(confirmDeleteDemo = true) }
    fun deleteDemo() {
        ui.update { it.copy(confirmDeleteDemo = false) }
        run(R.string.demo_deleted) { demo.delete() }
    }

    fun dismissDialogs() = ui.update { it.copy(confirmAddDemo = false, confirmDeleteDemo = false, reset = ResetStep.NONE) }

    // Начать заново

    fun requestReset() = ui.update { it.copy(reset = ResetStep.OFFER_BACKUP) }
    fun skipBackup() = ui.update { it.copy(reset = ResetStep.CONFIRM) }

    /** Копия перед сбросом: после сохранения — сразу к подтверждению. */
    fun backupBeforeReset(uri: Uri) = viewModelScope.launch {
        runCatching { write(uri, backup.export()) }
            .onSuccess {
                _message.value = context.getString(R.string.data_backup_done)
                ui.update { it.copy(reset = ResetStep.CONFIRM) }
            }
            .onFailure {
                _message.value = context.getString(R.string.data_error, it.message.orEmpty())
                ui.update { it.copy(reset = ResetStep.NONE) }
            }
    }

    fun confirmReset() {
        ui.update { it.copy(reset = ResetStep.NONE) }
        viewModelScope.launch {
            runCatching {
                reset.resetAll()
                lock.keepUnlocked()
            }.onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
        }
    }

    // Экспорт и копия

    fun exportCsv(uri: Uri, period: ExportPeriod) = viewModelScope.launch {
        runCatching {
            val range = when (period) {
                ExportPeriod.MONTH -> Periods.month(YearMonth.now(clock), clock.zone)
                ExportPeriod.YEAR -> Periods.year(YearMonth.now(clock).year, clock.zone)
                ExportPeriod.ALL -> Periods.ALL
            }
            val list = transactions.observeFiltered(OperationFilter(range.from, range.to)).first()
            val accs = accounts.observeAccounts(includeArchived = true).first().associate { it.account.id to it.account }
            val cats = (categories.observe(CategoryKind.EXPENSE, true).first() + categories.observe(CategoryKind.INCOME, true).first()).associateBy { it.id }
            write(uri, CsvExporter.build(list, accs, cats, transactions.tagNamesByTransaction(), clock.zone))
            list.size
        }.onSuccess { _message.value = context.getString(R.string.data_export_done, it) }
            .onFailure { _message.value = context.getString(R.string.data_error, it.message.orEmpty()) }
    }

    fun backup(uri: Uri) = run(R.string.data_backup_done) { write(uri, backup.export()) }

    fun restore(uri: Uri) = run(R.string.data_restore_done) {
        val json = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("файл не открылся")
        }
        backup.restore(json)
    }

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("файл не создан")
    }
}
