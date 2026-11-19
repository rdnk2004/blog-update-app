package com.example.data.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hashes and verifies the user's 4-digit PIN locally using a per-device random salt
 * and SHA-256 so the PIN is never stored in plain text.
 */
object PinHasher {

    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(input)
        return Base64.encodeToString(hashed, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean {
        if (salt.isBlank() || expectedHash.isBlank()) return false
        val actual = hashPin(pin, salt).toByteArray(Charsets.UTF_8)
        val expected = expectedHash.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }
}
