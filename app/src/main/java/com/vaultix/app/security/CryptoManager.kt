package com.vaultix.app.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import java.security.SecureRandom
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encryption/decryption helper.
 * All data is encrypted before storage, decrypted only in memory.
 */
@Singleton
class CryptoManager @Inject constructor() {

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12    // 96 bits
        private const val GCM_TAG_LENGTH = 128  // bits
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH = 256
    }

    /**
     * Encrypts plaintext (CharArray) and returns Base64-encoded "IV:ciphertext".
     * Using CharArray allows for zeroization after encryption.
     */
    fun encrypt(plaintext: CharArray, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        
        // Convert CharArray to ByteArray securely
        val encoder = Charsets.UTF_8.newEncoder()
        val charBuffer = java.nio.CharBuffer.wrap(plaintext)
        val byteBuffer = encoder.encode(charBuffer)
        val plaintextBytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(plaintextBytes)

        val ciphertext = cipher.doFinal(plaintextBytes)

        // Zeroization: Clear the temporary byte array
        plaintextBytes.fill(0)
        
        // Combine IV + ciphertext
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts to CharArray for secure handling.
     */
    fun decryptToChars(encryptedData: String, key: SecretKey): CharArray {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        
        // Convert to CharArray
        val decoder = Charsets.UTF_8.newDecoder()
        val byteBuffer = java.nio.ByteBuffer.wrap(decryptedBytes)
        val charBuffer = decoder.decode(byteBuffer)
        val result = CharArray(charBuffer.remaining())
        charBuffer.get(result)

        // Zeroization: Clear decrypted bytes
        decryptedBytes.fill(0)
        
        return result
    }

    /**
     * Clears sensitive data from memory.
     */
    fun clearSensitiveData(data: CharArray?) {
        data?.fill('\u0000')
    }

    /**
     * Decrypts to String (used for non-sensitive fields like titles, usernames, etc.)
     */
    fun decrypt(encryptedData: String, key: SecretKey): String {
        return String(decryptToChars(encryptedData, key))
    }

    /**
     * String-based encrypt (convenience wrapper)
     */
    fun encrypt(plaintext: String, key: SecretKey): String = encrypt(plaintext.toCharArray(), key)

    /**
     * Encrypts raw bytes (for file encryption).
     */
    fun encryptBytes(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)

        return iv + ciphertext
    }

    /**
     * Decrypts raw bytes.
     */
    fun decryptBytes(encryptedData: ByteArray, key: SecretKey): ByteArray {
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Derives a 256-bit AES key from a password and salt using PBKDF2.
     */
    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, ALGORITHM)
    }

    /**
     * Encrypts a stream of data (for large files).
     */
    fun encryptStream(inputStream: InputStream, outputStream: OutputStream, key: SecretKey) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        outputStream.write(iv) // Write IV first

        CipherOutputStream(outputStream, cipher).use { cos ->
            inputStream.copyTo(cos)
        }
    }

    /**
     * Decrypts a stream of data.
     */
    fun decryptStream(inputStream: InputStream, outputStream: OutputStream, key: SecretKey) {
        val iv = ByteArray(GCM_IV_LENGTH)
        inputStream.read(iv) // Read IV first

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        CipherInputStream(inputStream, cipher).use { cis ->
            cis.copyTo(outputStream)
        }
    }

    /**
     * Generates a random 16-byte salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }
}
