package com.moifinansy.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// Шрифт пока системный (Roboto); Inter подключим на этапе полировки.
// Главная сумма — 32 sp Bold, суммы в списке — 17 sp SemiBold, подписи — 13 sp.
val MoiFinansyTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelMedium = TextStyle(fontSize = 13.sp, textAlign = TextAlign.Unspecified),
)
