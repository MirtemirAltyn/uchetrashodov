package com.moifinansy.domain.model

/** Валюта: код ISO 4217 и короткий символ. Все валюты списка — с двумя знаками после запятой. */
data class CurrencyInfo(val code: String, val symbol: String)

object Currencies {
    const val DEFAULT = "KGS"

    val all = listOf(
        CurrencyInfo("KGS", "сом"),
        CurrencyInfo("USD", "$"),
        CurrencyInfo("EUR", "€"),
        CurrencyInfo("RUB", "₽"),
        CurrencyInfo("KZT", "₸"),
        CurrencyInfo("UZS", "сўм"),
        CurrencyInfo("CNY", "¥"),
        CurrencyInfo("TRY", "₺"),
        CurrencyInfo("GBP", "£"),
    )

    fun byCode(code: String): CurrencyInfo = all.firstOrNull { it.code == code } ?: CurrencyInfo(code, code)
}
