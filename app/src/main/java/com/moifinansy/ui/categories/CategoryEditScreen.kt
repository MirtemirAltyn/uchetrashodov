package com.moifinansy.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.ColorPicker
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.IconPicker
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun CategoryEditScreen(
    onDone: () -> Unit,
    onOpen: (Long) -> Unit,
    onAddSub: (parentId: Long) -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val children by viewModel.children.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    var localChildren by remember(children) { mutableStateOf(children) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = localChildren.indexOfFirst { it.id == from.key }
        val toIndex = localChildren.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            localChildren = localChildren.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val title = when {
        !state.isNew -> stringResource(R.string.category_edit)
        state.parentId != null -> stringResource(R.string.category_new_sub)
        else -> stringResource(R.string.category_new)
    }

    Scaffold(
        topBar = { BackTopBar(title, onDone) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            item(key = "form") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f)) {
                        IconBadge(state.icon, state.color, size = 56.dp)
                        state.parentName?.let {
                            Text(stringResource(R.string.category_parent, it), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        label = { Text(stringResource(R.string.field_name)) },
                        isError = state.nameError,
                        supportingText = if (state.nameError) ({ Text(stringResource(R.string.error_name_required)) }) else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.isSystem) {
                        Text(
                            stringResource(R.string.category_system_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SectionTitle(stringResource(R.string.field_color))
                    ColorPicker(selected = state.color, onSelect = viewModel::setColor)
                    SectionTitle(stringResource(R.string.field_icon))
                    IconPicker(selected = state.icon, color = state.color, onSelect = viewModel::setIcon)
                    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth().padding(top = Spacing.grid * 2)) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }

            if (state.canHaveChildren) {
                item(key = "subtitle") { SectionTitle(stringResource(R.string.category_subcategories)) }
                items(localChildren, key = { it.id }) { child ->
                    ReorderableItem(reorderState, key = child.id) { isDragging ->
                        CategoryRow(
                            category = child,
                            subtitle = null,
                            onClick = { onOpen(child.id) },
                            isDragging = isDragging,
                            dragHandle = Modifier.draggableHandle(
                                onDragStopped = { viewModel.reorderChildren(localChildren.map { it.id }) },
                            ),
                        )
                    }
                }
                item(key = "addSub") {
                    OutlinedButton(onClick = { onAddSub(requireNotNull(viewModel.categoryIdOrNull())) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.category_add_sub))
                    }
                }
            }

            if (!state.isNew) {
                item(key = "actions") {
                    Column(Modifier.padding(top = Spacing.grid * 2), verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                        OutlinedButton(onClick = viewModel::toggleArchived, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (state.isArchived) R.string.action_unarchive else R.string.action_archive))
                        }
                        if (!state.isSystem) {
                            TextButton(onClick = viewModel::askDelete, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    state.delete?.let { dialog -> DeleteCategoryDialog(dialog, viewModel) }
}

@Composable
private fun DeleteCategoryDialog(dialog: CategoryEditState.DeleteDialog, viewModel: CategoryEditViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissDelete,
        title = { Text(stringResource(R.string.category_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.grid)) {
                when {
                    dialog.blocked -> Text(stringResource(R.string.category_delete_has_children))
                    dialog.needsTarget -> {
                        Text(stringResource(R.string.category_delete_move, dialog.usageCount))
                        SectionTitle(stringResource(R.string.category_delete_move_to))
                        LazyColumn(Modifier.heightIn(max = 280.dp).selectableGroup()) {
                            items(dialog.targets, key = { it.id }) { target ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.RadioButton) { viewModel.chooseDeleteTarget(target.id) }
                                        .padding(vertical = Spacing.grid / 2),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.grid),
                                ) {
                                    RadioButton(selected = dialog.targetId == target.id, onClick = null)
                                    IconBadge(target.icon, target.color, size = 32.dp)
                                    Text(target.name)
                                }
                            }
                        }
                    }
                    else -> Text(stringResource(R.string.category_delete_empty))
                }
            }
        },
        confirmButton = {
            if (dialog.blocked) {
                TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) }
            } else {
                TextButton(onClick = viewModel::confirmDelete, enabled = dialog.canConfirm) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            if (!dialog.blocked) {
                Row {
                    if (dialog.needsTarget) {
                        TextButton(onClick = viewModel::archiveInstead) { Text(stringResource(R.string.action_archive)) }
                    }
                    TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        },
    )
}
