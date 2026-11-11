package com.moifinansy.core.format

import java.math.BigDecimal
import java.math.RoundingMode

/** Курс валюты хранится как Long «× 1 000 000», чтобы не было Double. */
object RateFormat {
    private const val SCALE = 6

    /** «87,45» → 87 450 000; «0.0071» → 7 100. null — не курс или ≤ 0. */
    fun parse(text: String): Long? {
        val s = text.trim().replace(',', '.').replace(" ", "").replace(" ", "")
        if (s.isEmpty() || s.count { it == '.' } > 1 || !s.all { it.isDigit() || it == '.' }) return null
        val value = runCatching { BigDecimal(if (s.startsWith('.')) "0$s" else s) }.getOrNull() ?: return null
        if (value.scale() > SCALE || value.signum() <= 0) return null
        return runCatching { value.movePointRight(SCALE).longValueExact() }.getOrNull()
    }

    fun format(micros: Long): String = BigDecimal(micros).movePointLeft(SCALE).setScale(SCALE, RoundingMode.UNNECESSARY)
        .stripTrailingZeros().toPlainString().replace('.', ',')
}
