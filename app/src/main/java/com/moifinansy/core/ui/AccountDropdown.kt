package com.moifinansy.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Выпадающий список «название — значение» (счета, категории в фильтрах и т. п.). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    leading: (@Composable (T) -> Unit)? = null,
    emptyText: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(optionText) ?: emptyText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            leadingIcon = if (leading != null && selected != null) ({ leading(selected) }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    leadingIcon = leading?.let { { it(option) } },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
