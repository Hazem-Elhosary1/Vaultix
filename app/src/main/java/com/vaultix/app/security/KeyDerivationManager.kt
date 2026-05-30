package com.vaultix.app.security

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Key derivation using PBKDF2-HMAC-SHA512.
 * Used to derive encryption keys from master password / PIN.
 */
@Singleton
class KeyDerivationManager @Inject constructor() {

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512"
        private const val SALT_LENGTH = 32       // 256 bits
        private const val ITERATIONS = 600_000   // NIST recommended minimum
        private const val KEY_LENGTH = 256       // AES-256
    }

    /**
     * Generates a cryptographically secure random salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Derives a key from password + salt using PBKDF2.
     * Returns Base64-encoded derived key.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derivedKey = factory.generateSecret(spec).encoded
        // Clear the spec securely
        spec.clearPassword()
        return Base64.encodeToString(derivedKey, Base64.NO_WRAP)
    }

    /**
     * Verifies password by deriving key and comparing.
     */
    fun verifyPassword(password: CharArray, salt: ByteArray, storedHash: String): Boolean {
        if (password.isEmpty()) return false
        val derivedHash = deriveKey(password, salt)
        return constantTimeEquals(derivedHash, storedHash)
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray()
        val bBytes = b.toByteArray()

        if (aBytes.size != bBytes.size) return false

        var result = 0
        for (i in aBytes.indices) {
            result = result or (aBytes[i].toInt() xor bBytes[i].toInt())
        }
        return result == 0
    }

    /**
     * Hashes a PIN for secure storage.
     */
    fun hashPin(pin: CharArray, salt: ByteArray): String {
        return deriveKey(pin, salt)
    }
}
