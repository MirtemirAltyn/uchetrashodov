package com.moifinansy.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun `hash verifies only the right pin and depends on salt`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.verify("1234", salt, hash))
        assertFalse(PinHasher.verify("1235", salt, hash))
        assertNotEquals(hash, PinHasher.hash("1234", PinHasher.newSalt()))
        assertFalse(hash.contains("1234"))
        assertTrue(PinHasher.isValid("0042"))
        assertFalse(PinHasher.isValid("12a4"))
    }
}
