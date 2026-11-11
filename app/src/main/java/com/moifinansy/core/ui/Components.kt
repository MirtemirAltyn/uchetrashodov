package com.moifinansy.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moifinansy.core.format.MoneyFormat
import com.moifinansy.core.ui.theme.LocalFinanceColors
import com.moifinansy.core.ui.theme.PickerColors
import com.moifinansy.core.ui.theme.Spacing

/** Иконка в цветном круге 40 dp с прозрачностью 15 % (раздел 6.1). */
@Composable
fun IconBadge(icon: String, color: Long, modifier: Modifier = Modifier, size: Dp = 40.dp, contentDescription: String? = null) {
    val tint = Color(color)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.get(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Градиентная шапка с итогом (#5B5BD6 → #8E8CF0 по диагонали). */
@Composable
fun GradientHeader(title: String, amount: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = LocalFinanceColors.current
    val onGradient = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(colors.headerGradientStart, colors.headerGradientEnd)))
            .padding(Spacing.grid * 2.5f),
    ) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = onGradient.copy(alpha = 0.85f))
        Text(amount, style = MaterialTheme.typography.displaySmall, color = onGradient)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onGradient.copy(alpha = 0.85f))
        }
    }
}

/** Сумма с цветом по знаку: отрицательная — цвет расхода, положительная со знаком — цвет дохода. */
@Composable
fun MoneyText(
    minor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    neutralPositive: Boolean = !signed,
) {
    val colors = LocalFinanceColors.current
    val color = when {
        minor < 0 -> colors.expense
        minor > 0 && !neutralPositive -> colors.income
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        MoneyFormat.format(minor, currencyCode, signed),
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = color,
    )
}

/** Поле ввода суммы с цифровой клавиатурой; [isError] — сумма не разобрана. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    allowNegative: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val allowed = text.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' || (allowNegative && it == '-') }
            onValueChange(allowed)
        },
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.grid),
        verticalArrangement = Arrangement.spacedBy(Spacing.grid),
    ) {
        PickerColors.forEach { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String, color: Long, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.grid),
        verticalArrangement = Arrangement.spacedBy(Spacing.grid),
    ) {
        AppIcons.byName.keys.forEach { name ->
            val isSelected = name == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(if (isSelected) Modifier.border(2.dp, Color(color), CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                IconBadge(name, if (isSelected) color else 0xFF9E9E9E)
            }
        }
    }
}

/** Заголовок раздела в списке. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = Spacing.grid * 2, bottom = Spacing.grid),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Белая карточка-раздел со скруглением 20 dp и заголовком. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(vertical = Spacing.grid * 1.5f)) {
            if (title != null || action != null) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.screenHorizontal),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
            }
            content()
        }
    }
}
