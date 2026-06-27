package com.vaultix.app.security

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Locale

object TotpGenerator {

    private val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Generates a 6-digit TOTP code for a given Base32 secret and time step (default 30 seconds).
     * Returns null if secret is invalid.
     */
    fun generateTotp(secret: String, timeMs: Long = System.currentTimeMillis()): String? {
        val cleanSecret = secret.replace(" ", "").replace("-", "").uppercase(Locale.US)
        if (cleanSecret.isEmpty()) return null
        
        val keyBytes = try {
            decodeBase32(cleanSecret)
        } catch (e: Exception) {
            return null
        }
        if (keyBytes.isEmpty()) return null

        val timeStep = timeMs / 1000 / 30
        
        return try {
            val data = ByteBuffer.allocate(8).putLong(timeStep).array()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
            val hash = mac.doFinal(data)

            val offset = hash[hash.size - 1].toInt() and 0xf
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                    ((hash[offset + 1].toInt() and 0xff) shl 16) or
                    ((hash[offset + 2].toInt() and 0xff) shl 8) or
                    (hash[offset + 3].toInt() and 0xff)

            val otp = binary % 1000000
            String.format(Locale.US, "%06d", otp)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes a Base32 string to byte array.
     */
    private fun decodeBase32(base32: String): ByteArray {
        val cleaned = base32.trimEnd('=')
        val byteCount = (cleaned.length * 5) / 8
        val result = ByteArray(byteCount)
        
        var buffer = 0
        var bitsLeft = 0
        var count = 0
        
        for (char in cleaned) {
            val valIndex = BASE32_CHARS.indexOf(char)
            if (valIndex < 0) {
                throw IllegalArgumentException("Illegal character in Base32: $char")
            }
            
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                result[count++] = (buffer shr (bitsLeft - 8)).toByte()
                bitsLeft -= 8
            }
        }
        return result
    }

    /**
     * Returns the number of seconds remaining in the current 30-second window.
     */
    fun getSecondsRemaining(timeMs: Long = System.currentTimeMillis()): Int {
        val seconds = timeMs / 1000
        return (30 - (seconds % 30)).toInt()
    }
}
