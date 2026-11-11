package com.moifinansy.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moifinansy.R
import com.moifinansy.core.ui.BackTopBar
import com.moifinansy.core.ui.SectionTitle
import com.moifinansy.core.ui.labelRes
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.CategoryKind
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: (CategoryKind) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Локальная копия порядка: меняется во время перетаскивания, в базу уходит по отпусканию.
    var active by remember(state.active) { mutableStateOf(state.active) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = active.indexOfFirst { it.category.id == from.key }
        val toIndex = active.indexOfFirst { it.category.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            active = active.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.categories_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(state.kind) }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.category_new))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.grid),
            verticalArrangement = Arrangement.spacedBy(Spacing.grid),
        ) {
            item(key = "tabs") {
                PrimaryTabRow(selectedTabIndex = state.kind.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    CategoryKind.entries.forEach { kind ->
                        Tab(
                            selected = kind == state.kind,
                            onClick = { viewModel.selectKind(kind) },
                            text = { Text(stringResource(kind.labelRes())) },
                        )
                    }
                }
            }
            item(key = "hint") {
                Text(
                    stringResource(R.string.categories_reorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(active, key = { it.category.id }) { item ->
                ReorderableItem(reorderState, key = item.category.id) { isDragging ->
                    CategoryRow(
                        category = item.category,
                        subtitle = item.childNames.joinToString(", "),
                        onClick = { onOpen(item.category.id) },
                        isDragging = isDragging,
                        dragHandle = Modifier.draggableHandle(
                            onDragStopped = { viewModel.reorder(active.map { it.category.id }) },
                        ),
                    )
                }
            }
            if (state.archived.isNotEmpty()) {
                item(key = "archived") { SectionTitle(stringResource(R.string.section_archived)) }
                items(state.archived, key = { "a" + it.category.id }) { item ->
                    CategoryRow(
                        category = item.category,
                        subtitle = null,
                        onClick = { onOpen(item.category.id) },
                        modifier = Modifier.alpha(0.6f),
                    )
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(72.dp)) }
        }
    }
}
