package com.moifinansy.domain.security

import java.security.MessageDigest
import java.security.SecureRandom

/** PIN хранится только как SHA-256(соль + PIN). */
object PinHasher {
    const val LENGTH = 4

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray(Charsets.UTF_8)).toHex()

    fun verify(pin: String, salt: String, hash: String): Boolean = MessageDigest.isEqual(hash(pin, salt).toByteArray(), hash.toByteArray())

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it.isDigit() }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
