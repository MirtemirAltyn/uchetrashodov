package com.moifinansy.core.format

import com.moifinansy.domain.model.Currencies

/** Форматирование и разбор сумм. Суммы — Long в минимальных единицах (1/100). */
object MoneyFormat {
    private const val NBSP = ' '
    private const val MINUS = '−'
    private const val MAX_DIGITS = 15

    /** 1250000 → «12 500 сом»; 1250050 → «12 500,50 сом». [signed] — «+» у положительных. */
    fun format(minor: Long, currencyCode: String, signed: Boolean = false): String {
        val sign = when {
            minor < 0 -> MINUS.toString()
            signed && minor > 0 -> "+"
            else -> ""
        }
        return "$sign${formatNumber(minor)}$NBSP${Currencies.byCode(currencyCode).symbol}"
    }

    /** Число без знака и валюты: «12 500» или «12 500,50». */
    fun formatNumber(minor: Long): String {
        val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val whole = (abs / 100).toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
        val cents = abs % 100
        return if (cents == 0L) whole else "$whole,${cents.toString().padStart(2, '0')}"
    }

    /** Текст для поля ввода: без группировки, с точкой — то, что [parse] примет обратно. */
    fun toInput(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        val cents = abs % 100
        return if (cents == 0L) "$sign${abs / 100}" else "$sign${abs / 100}.${cents.toString().padStart(2, '0')}"
    }

    /**
     * «12 500», «12500,5», «-300.25» → минимальные единицы. Не больше двух знаков после запятой.
     * null — если строка не сумма или пустая.
     */
    fun parse(text: String, allowNegative: Boolean = false): Long? {
        val cleaned = text.filterNot { it.isWhitespace() || it == NBSP }.replace(',', '.').replace(MINUS, '-')
        if (cleaned.isEmpty()) return null
        val negative = cleaned.startsWith('-')
        if (negative && !allowNegative) return null
        val body = cleaned.removePrefix("-")
        val parts = body.split('.')
        if (parts.size > 2) return null
        val whole = parts[0]
        val fraction = parts.getOrElse(1) { "" }
        if (whole.isEmpty() && fraction.isEmpty()) return null
        if (!whole.all { it.isDigit() } || !fraction.all { it.isDigit() } || fraction.length > 2) return null
        if (whole.length > MAX_DIGITS) return null
        val minor = (whole.ifEmpty { "0" }.toLong()) * 100 + fraction.padEnd(2, '0').toLong()
        return if (negative) -minor else minor
    }
}
