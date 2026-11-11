package com.moifinansy.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Snackbar «Операция удалена · Отменить» на уровне всего приложения. */
@HiltViewModel
class UndoViewModel @Inject constructor(private val holder: UndoDeleteHolder) : ViewModel() {
    val deleted = holder.deleted
    fun undo() {
        viewModelScope.launch { holder.undo() }
    }
    fun dismiss() = holder.dismiss()
}
