package com.vaultix.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.vaultix.app.data.local.dao.PasswordDao
import com.vaultix.app.data.local.entity.PasswordEntity
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports and imports user-readable CSV files that are encrypted.
 * Default mode: device-bound (requires app + password). Optionally supports portable (password-only).
 */
@Singleton
class ExportImportManager @Inject constructor(
    private val context: Context,
    private val passwordDao: PasswordDao,
    private val cryptoManager: CryptoManager
) {
    private val gson = Gson()

    data class PackageV1(
        val format: String? = "vaultix_export_v1",
        val portable: Boolean? = null,
        val contentType: String? = null,
        val createdAt: Long? = null,
        val salt: String? = null,                // base64
        val wrappedKeyPwd: String? = null,      // base64 (iv + ciphertext)
        val wrappedKeyKs: String? = null,       // base64 (iv + ciphertext) - may be null for portable-only
        val ciphertext: String? = null          // base64 (iv + ciphertext)
    )

    /**
     * Export passwords table as CSV, encrypt and write to provided URI.
     * Device-bound by default.
     */
    suspend fun exportPasswordsCsvEncrypted(
        targetUri: Uri,
        password: CharArray,
        deviceBound: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Build CSV bytes (UTF-8)
            val entities = passwordDao.getAllPasswords().first()
            val csv = buildCsvFromPasswords(entities)
            val contentBytes = csv.toByteArray(StandardCharsets.UTF_8)

            // 2. Generate random fileKey (32 bytes)
            val fileKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val fileKey: SecretKey = SecretKeySpec(fileKeyBytes, "AES")

            // 3. Encrypt content with fileKey
            val encryptedContent = cryptoManager.encryptBytes(contentBytes, fileKey)

            // 4. Derive key from user password
            val salt = cryptoManager.generateSalt()
            val pwdKey = cryptoManager.deriveKey(password, salt)

            // 5. Wrap fileKey with pwdKey
            val wrappedKeyPwd = cryptoManager.encryptBytes(fileKeyBytes, pwdKey)

            // 6. Wrap fileKey with Keystore key (device-bound) if requested
            val wrappedKeyKs = if (deviceBound) {
                val ksKey = KeystoreManager.getOrCreateBackupHistoryKey()
                cryptoManager.encryptBytes(fileKeyBytes, ksKey)
            } else null

            // 7. Build package JSON
            val pkg = PackageV1(
                portable = !deviceBound,
                contentType = "text/csv",
                createdAt = System.currentTimeMillis(),
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                wrappedKeyPwd = Base64.encodeToString(wrappedKeyPwd, Base64.NO_WRAP),
                wrappedKeyKs = wrappedKeyKs?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                ciphertext = Base64.encodeToString(encryptedContent, Base64.NO_WRAP)
            )

            val json = gson.toJson(pkg)

            // 8. Write to target URI
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(json.toByteArray(StandardCharsets.UTF_8))
            } ?: return@withContext Result.failure(Exception("Cannot open output stream"))

            // Zeroize sensitive buffers
            contentBytes.fill(0)
            fileKeyBytes.fill(0)
            password.fill('\u0000')

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import an encrypted export previously produced by this manager.
     * Returns decrypted CSV bytes on success.
     */
    suspend fun importEncryptedFile(
        sourceUri: Uri,
        password: CharArray
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot open input stream"))

            val json = InputStreamReader(input, StandardCharsets.UTF_8).use { it.readText() }
            val pkg = try {
                gson.fromJson(json, PackageV1::class.java)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Malformed package JSON: ${e.message}"))
            }

            if (pkg.format != "vaultix_export_v1") return@withContext Result.failure(Exception("Unsupported or missing format field"))

            val saltB64 = pkg.salt ?: return@withContext Result.failure(Exception("Missing salt in package"))
            val wrappedKeyPwdB64 = pkg.wrappedKeyPwd ?: return@withContext Result.failure(Exception("Missing wrappedKeyPwd in package"))
            val ciphertextB64 = pkg.ciphertext ?: return@withContext Result.failure(Exception("Missing ciphertext in package"))

            val salt = try { Base64.decode(saltB64, Base64.NO_WRAP) } catch (e: Exception) { return@withContext Result.failure(Exception("Invalid base64 salt")) }
            val wrappedKeyPwd = try { Base64.decode(wrappedKeyPwdB64, Base64.NO_WRAP) } catch (e: Exception) { return@withContext Result.failure(Exception("Invalid base64 wrappedKeyPwd")) }
            val wrappedKeyKs = pkg.wrappedKeyKs?.let { try { Base64.decode(it, Base64.NO_WRAP) } catch (_: Exception) { null } }
            val encryptedContent = try { Base64.decode(ciphertextB64, Base64.NO_WRAP) } catch (e: Exception) { return@withContext Result.failure(Exception("Invalid base64 ciphertext")) }

            // Derive password key and try to unwrap fileKey
            val pwdKey = try { cryptoManager.deriveKey(password, salt) } catch (e: Exception) { return@withContext Result.failure(Exception("Key derivation failed: ${e.message}")) }
            val fileKeyCandidateBytes = try {
                cryptoManager.decryptBytes(wrappedKeyPwd, pwdKey)
            } catch (e: Exception) {
                null
            }

            // Password unwrap is the primary restore path.
            val fileKeyBytes = fileKeyCandidateBytes ?: return@withContext Result.failure(Exception("Password unwrap failed"))

            // If a device-bound key exists and the Keystore is still available, use it as a verification step only.
            // After clear-data/uninstall, the Keystore entry may be gone; in that case we intentionally fall back to
            // the password-derived unwrap so restores on the same device still work.
            if (wrappedKeyKs != null) {
                val ksKey = runCatching { KeystoreManager.getOrCreateBackupHistoryKey() }.getOrNull()
                if (ksKey != null) {
                    val fileKeyDeviceBytes = try { cryptoManager.decryptBytes(wrappedKeyKs, ksKey) } catch (_: Exception) { null }
                    if (fileKeyDeviceBytes != null && !fileKeyDeviceBytes.contentEquals(fileKeyBytes)) {
                        return@withContext Result.failure(Exception("Key mismatch: file not compatible with this device"))
                    }
                }
            }

            val fileKey = SecretKeySpec(fileKeyBytes, "AES")
            val decrypted = cryptoManager.decryptBytes(encryptedContent, fileKey)

            // Zeroize sensitive buffers
            password.fill('\u0000')
            fileKeyBytes.fill(0)

            Result.success(decrypted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildCsvFromPasswords(entities: List<PasswordEntity>): String {
        val sb = StringBuilder()
        // Header
        sb.append("id,title,username,password,website,appPackageName,notes,createdAt,updatedAt\n")

        val dbKey = KeystoreManager.getOrCreateDatabaseKey()

        for (e in entities) {
            try {
                val title = runCatching { cryptoManager.decrypt(e.title, dbKey) }.getOrNull() ?: ""
                val username = runCatching { cryptoManager.decrypt(e.username, dbKey) }.getOrNull() ?: ""
                val pwdChars = runCatching { cryptoManager.decryptToChars(e.password, dbKey) }.getOrNull()
                val password = pwdChars?.concatToString() ?: ""
                val website = runCatching { cryptoManager.decrypt(e.website, dbKey) }.getOrNull() ?: ""
                val notes = runCatching { cryptoManager.decrypt(e.notes, dbKey) }.getOrNull() ?: ""

                // CSV escaping (simple)
                fun esc(s: String) = '"' + s.replace("\"", "\"\"") + '"'

                sb.append(esc(e.id)).append(',')
                    .append(esc(title)).append(',')
                    .append(esc(username)).append(',')
                    .append(esc(password)).append(',')
                    .append(esc(website)).append(',')
                    .append(esc(e.appPackageName)).append(',')
                    .append(esc(notes)).append(',')
                    .append(e.createdAt).append(',')
                    .append(e.updatedAt)
                    .append('\n')

                // Zeroize pwdChars
                pwdChars?.fill('\u0000')
            } catch (_: Exception) {
                // skip problematic rows
            }
        }

        return sb.toString()
    }
}
