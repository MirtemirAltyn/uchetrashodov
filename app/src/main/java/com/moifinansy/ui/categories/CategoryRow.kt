package com.moifinansy.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moifinansy.R
import com.moifinansy.core.ui.IconBadge
import com.moifinansy.core.ui.theme.Spacing
import com.moifinansy.domain.model.Category

/** Строка категории; [dragHandle] — модификатор ручки перетаскивания (null — без ручки). */
@Composable
fun CategoryRow(
    category: Category,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
    isDragging: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isDragging) 6.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = Spacing.screenHorizontal, top = Spacing.grid * 1.5f, bottom = Spacing.grid * 1.5f, end = Spacing.grid),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.grid * 1.5f),
        ) {
            IconBadge(category.icon, category.color)
            Column(Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (dragHandle != null) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandle.padding(Spacing.grid * 1.5f),
                )
            }
        }
    }
}
