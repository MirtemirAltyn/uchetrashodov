package com.moifinansy.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.moifinansy.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
/** Inter (переменный шрифт, лицензия OFL — third_party/inter/OFL.txt). */
val Inter = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.inter, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)

/** Табличные цифры: суммы в столбик не «прыгают». */
private const val TABULAR = "tnum"

private val base = Typography()

private fun TextStyle.inter(tabular: Boolean = false) = copy(fontFamily = Inter, fontFeatureSettings = if (tabular) TABULAR else null)

// Главная сумма — 32 sp Bold, суммы в списке — 17 sp SemiBold, подписи — 13 sp.
val MoiFinansyTypography = Typography(
    displayLarge = base.displayLarge.inter(),
    displayMedium = base.displayMedium.inter(),
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold).inter(tabular = true),
    headlineLarge = base.headlineLarge.inter(),
    headlineMedium = base.headlineMedium.inter(tabular = true),
    headlineSmall = base.headlineSmall.inter(),
    titleLarge = base.titleLarge.inter(),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold).inter(tabular = true),
    titleSmall = base.titleSmall.inter(),
    bodyLarge = base.bodyLarge.inter(),
    bodyMedium = base.bodyMedium.inter(),
    bodySmall = TextStyle(fontSize = 13.sp).inter(),
    labelLarge = base.labelLarge.inter(tabular = true),
    labelMedium = TextStyle(fontSize = 13.sp).inter(),
    labelSmall = base.labelSmall.inter(),
)
