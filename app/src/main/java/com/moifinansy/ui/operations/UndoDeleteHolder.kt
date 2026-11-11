package com.moifinansy.ui.operations

import com.moifinansy.domain.model.Transaction
import com.moifinansy.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Удалённая операция для Snackbar «Отменить». Живёт на всё приложение. */
@Singleton
class UndoDeleteHolder @Inject constructor(private val transactions: TransactionRepository) {
    data class Deleted(val transaction: Transaction, val tagIds: List<Long>)

    private val _deleted = MutableStateFlow<Deleted?>(null)
    val deleted: StateFlow<Deleted?> = _deleted.asStateFlow()

    suspend fun delete(id: Long) {
        val transaction = transactions.get(id) ?: return
        val tags = transactions.tagIdsOf(id)
        transactions.delete(id)
        _deleted.value = Deleted(transaction, tags)
    }

    suspend fun undo() {
        val deleted = _deleted.value ?: return
        _deleted.value = null
        transactions.save(deleted.transaction.copy(id = 0), deleted.tagIds)
    }

    fun dismiss() {
        _deleted.value = null
    }
}
