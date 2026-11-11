package com.moifinansy.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Кольцевая диаграмма: доли по цветам, в центре — подпись и итог. */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Long>>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.14f
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset((this.size.width - diameter) / 2, (this.size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            val total = slices.sumOf { it.second }.toFloat()
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (slices.size > 1) 1.5f else 0f
            var start = -90f
            slices.forEach { (color, value) ->
                val sweep = value / total * 360f
                drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(centerValue, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

data class BarGroup(val label: String, val values: List<Long>, val line: Long? = null)

/**
 * Столбики по группам (например, доходы и расходы по месяцам) и необязательная линия
 * (например, общий баланс на конец месяца) поверх.
 */
@Composable
fun BarsChart(
    groups: List<BarGroup>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 180.dp,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (groups.isEmpty()) return@Canvas
            val maxBar = groups.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1) ?: 1
            val lines = groups.mapNotNull { it.line }
            val lineMin = (lines.minOrNull() ?: 0).coerceAtMost(0)
            val lineMax = (lines.maxOrNull() ?: 1).coerceAtLeast(lineMin + 1)
            val slot = this.size.width / groups.size
            val barsPerGroup = groups.maxOf { it.values.size }.coerceAtLeast(1)
            val barWidth = (slot * 0.7f) / barsPerGroup
            groups.forEachIndexed { g, group ->
                group.values.forEachIndexed { i, value ->
                    val h = value.toFloat() / maxBar * this.size.height
                    val x = g * slot + slot * 0.15f + i * barWidth
                    drawRoundRect(
                        colors[i % colors.size],
                        topLeft = Offset(x + barWidth * 0.1f, this.size.height - h),
                        size = Size(barWidth * 0.8f, h),
                        cornerRadius = CornerRadius(barWidth * 0.2f),
                    )
                }
            }
            if (lines.size >= 2) {
                val path = Path()
                var started = false
                groups.forEachIndexed { g, group ->
                    val v = group.line ?: return@forEachIndexed
                    val y = this.size.height - (v - lineMin).toFloat() / (lineMax - lineMin) * this.size.height
                    val x = g * slot + slot / 2
                    if (!started) path.moveTo(x, y).also { started = true } else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            groups.forEach { group ->
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
