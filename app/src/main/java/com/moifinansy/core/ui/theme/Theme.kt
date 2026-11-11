package com.moifinansy.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = Primary,
    secondaryContainer = PrimaryContainerLight,
    onSecondaryContainer = Primary,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceContainer = LightSurface,
    onSurfaceVariant = LightTextSecondary,
    error = Expense,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = DarkText,
    secondaryContainer = PrimaryContainerDark,
    onSecondaryContainer = DarkText,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceContainer = DarkSurface,
    onSurfaceVariant = DarkTextSecondary,
    error = ExpenseDark,
)

/** Цвета, которых нет в схеме Material: доход, расход, просрочка, градиент шапки. */
@Immutable
data class FinanceColors(
    val income: Color,
    val expense: Color,
    val overdue: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
)

private val LightFinanceColors = FinanceColors(Income, Expense, Overdue, Primary, PrimaryGradientEnd)
private val DarkFinanceColors = FinanceColors(IncomeDark, ExpenseDark, OverdueDark, Primary, PrimaryGradientEnd)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

@Composable
fun MoiFinansyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFinanceColors provides if (darkTheme) DarkFinanceColors else LightFinanceColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MoiFinansyTypography,
            shapes = MoiFinansyShapes,
            content = content,
        )
    }
}
