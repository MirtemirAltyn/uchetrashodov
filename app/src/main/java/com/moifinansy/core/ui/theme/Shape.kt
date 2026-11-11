package com.moifinansy.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Карточки — 20 dp, кнопки и сегменты — 14 dp.
val MoiFinansyShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

object Spacing {
    val grid = 8.dp
    val screenHorizontal = 16.dp
}
