package com.moifinansy.domain.contacts

/** Номера телефонов: приведение к международному виду для WhatsApp и SMS. */
object PhoneNumbers {
    const val DEFAULT_COUNTRY = "+996"
    val COUNTRIES = listOf("+996", "+7", "+998", "+992", "+993", "+90")

    /**
     * «0555 123 456» → «+996555123456»; «555 123 456» → «+996555123456»; «+7 (999) 123-45-67» → «+79991234567»;
     * «00996…» → «+996…». null — если цифр слишком мало для номера.
     */
    fun normalize(raw: String?, countryCode: String = DEFAULT_COUNTRY): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 7) return null
        val country = countryCode.filter { it.isDigit() }
        val international = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith(country) && digits.length > 9 -> digits
            digits.startsWith("0") -> country + digits.drop(1)
            else -> country + digits
        }
        return "+$international"
    }

    /** Только цифры с кодом страны — формат номера в ссылках wa.me. */
    fun waDigits(raw: String?, countryCode: String = DEFAULT_COUNTRY): String? = normalize(raw, countryCode)?.drop(1)

    /** Ссылка WhatsApp: открывает чат с номером и вставленным текстом. Текст кодируется для URL. */
    fun whatsAppLink(raw: String?, text: String, countryCode: String = DEFAULT_COUNTRY): String? =
        waDigits(raw, countryCode)?.let { "https://wa.me/$it?text=" + java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20") }
}
