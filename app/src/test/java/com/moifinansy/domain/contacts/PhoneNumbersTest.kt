package com.moifinansy.domain.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {
    @Test
    fun `local numbers get the country code`() {
        assertEquals("+996555123456", PhoneNumbers.normalize("0555 123 456"))
        assertEquals("+996555123456", PhoneNumbers.normalize("555-123-456"))
        assertEquals("+996555123456", PhoneNumbers.normalize("996 555 123 456"))
        assertEquals("+996555123456", PhoneNumbers.normalize("00996555123456"))
        assertEquals("+996555123456", PhoneNumbers.normalize("+996 (555) 12-34-56"))
        assertEquals("+79991234567", PhoneNumbers.normalize("+7 999 123-45-67"))
        assertEquals("+79991234567", PhoneNumbers.normalize("89991234567".drop(1), "+7"))
        assertNull(PhoneNumbers.normalize("123"))
        assertNull(PhoneNumbers.normalize(null))
    }

    @Test
    fun `whatsapp link has digits only and encoded text`() {
        assertEquals(
            "https://wa.me/996555123456?text=%D0%9F%D1%80%D0%B8%D0%B2%D0%B5%D1%82%2C%20%D0%B4%D0%BE%D0%BB%D0%B3%3A%201%C2%A0000%20%D1%81%D0%BE%D0%BC",
            PhoneNumbers.whatsAppLink("0555 123 456", "Привет, долг: 1 000 сом"),
        )
        assertNull(PhoneNumbers.whatsAppLink("", "текст"))
    }
}
