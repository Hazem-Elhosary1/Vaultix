package com.vaultix.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vaultix.app.data.local.dao.*
import com.vaultix.app.data.local.entity.*
import com.vaultix.app.data.repository.*
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encrypted backup export and import.
 * All data is serialized to JSON, then encrypted with AES-256-GCM
 * using a dedicated Keystore key before being written to disk.
 *
 * Backup format: IV (12 bytes) + AES-GCM ciphertext
 * The backup is fully self-contained and offline.
 */
@Singleton
class BackupManager @Inject constructor(
    private val passwordRepository: PasswordRepository,
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val fileRepository: FileRepository,
    private val identityRepository: IdentityRepository,
    private val cryptoManager: CryptoManager,
    private val context: Context
) {
    private val gson = Gson()

    /**
     * Portable data container for all vault records.
     * Contains DECRYPTED data that will be encrypted with the backup password.
     */
    data class BackupPayload(
        val scopes: Set<BackupScope>? = setOf(BackupScope.FULL),
        val passwords: List<com.vaultix.app.data.model.Password>? = emptyList(),
        val cards: List<com.vaultix.app.data.model.Card>? = emptyList(),
        val notes: List<com.vaultix.app.data.model.Note>? = emptyList(),
        val files: List<com.vaultix.app.data.model.VaultFile>? = emptyList(),
        val folders: List<com.vaultix.app.data.model.VaultFolder>? = emptyList(),
        val identities: List<com.vaultix.app.data.model.Identity>? = emptyList(),
        val fileBinaries: Map<String, String>? = emptyMap(),
        val identityImageBinaries: Map<String, String>? = emptyMap(),
        val exportedAt: Long = 0,
        val appVersion: Int = 4,
        val checksum: String? = ""
    )

    suspend fun exportBackup(password: CharArray, scopes: Set<BackupScope>): Result<File> = withContext(Dispatchers.IO) {
        try {
            val rawPayload = buildBackupPayload(scopes)
            val rawJson = gson.toJson(rawPayload)
            
            val salt = cryptoManager.generateSalt()
            val derivedKey = cryptoManager.deriveKey(password, salt)
            
            val checksum = calculateHmac(rawJson, derivedKey)
            val finalPayload = rawPayload.copy(checksum = checksum)
            val finalJson = gson.toJson(finalPayload)
            val jsonBytes = finalJson.toByteArray(Charsets.UTF_8)

            val encryptedBytes = cryptoManager.encryptBytes(jsonBytes, derivedKey)
            jsonBytes.fill(0)
            password.fill('\u0000')

            val combined = salt + encryptedBytes
            val backupDir = File(context.filesDir, "backups").also { it.mkdirs() }
            val backupFile = File(backupDir, "vaultix_backup_${System.currentTimeMillis()}.vbk")
            backupFile.writeBytes(combined)

            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportBackupToUri(uri: Uri, password: CharArray, scopes: Set<BackupScope>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupResult = exportBackup(password, scopes).getOrThrow()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(backupResult.readBytes())
            } ?: return@withContext Result.failure(Exception("Cannot open output stream"))
            backupResult.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackupFromUri(uri: Uri, password: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open backup file"))
            val combined = inputStream.readBytes()
            inputStream.close()

            if (combined.size < 28) return@withContext Result.failure(Exception("Invalid backup file"))

            val salt = combined.copyOfRange(0, 16)
            val encryptedData = combined.copyOfRange(16, combined.size)
            
            val derivedKey = cryptoManager.deriveKey(password, salt)
            val decryptedBytes = cryptoManager.decryptBytes(encryptedData, derivedKey)
            password.fill('\u0000')
            
            val json = String(decryptedBytes, Charsets.UTF_8)
            decryptedBytes.fill(0)
            
            val payload = gson.fromJson(json, BackupPayload::class.java)

            // Verify integrity
            val payloadToVerify = payload.copy(checksum = "")
            val jsonToVerify = gson.toJson(payloadToVerify)
            val calculatedChecksum = calculateHmac(jsonToVerify, derivedKey)
            
            if (calculatedChecksum != payload.checksum) {
                return@withContext Result.failure(Exception("Integrity Check Failed"))
            }

            restorePayload(payload)
            val count = (payload.passwords?.size ?: 0) + (payload.cards?.size ?: 0) + 
                        (payload.notes?.size ?: 0) + (payload.files?.size ?: 0) + 
                        (payload.identities?.size ?: 0)

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun buildBackupPayload(scopes: Set<BackupScope>): BackupPayload {
        val includeFull = scopes.contains(BackupScope.FULL) || scopes.isEmpty()
        
        val files = if (includeFull || scopes.contains(BackupScope.FILES)) fileRepository.getAllFiles().first() else emptyList()
        val identities = if (includeFull || scopes.contains(BackupScope.IDENTITIES)) identityRepository.getAllIdentities().first() else emptyList()
        
        // Serialize physical file binaries
        val filesKey = KeystoreManager.getOrCreateFilesKey()
        val fileBinaries = mutableMapOf<String, String>()
        files.forEach { file ->
            try {
                val encryptedFile = File(file.encryptedFilePath)
                if (encryptedFile.exists()) {
                    val encryptedBytes = encryptedFile.readBytes()
                    val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, filesKey)
                    fileBinaries[file.id] = Base64.encodeToString(decryptedBytes, Base64.NO_WRAP)
                    decryptedBytes.fill(0)
                }
            } catch (e: Exception) {
                Log.w("BackupManager", "Failed to serialize file ${file.id}: ${e.message}")
            }
        }
        
        // Serialize identity image binaries
        val identityImageBinaries = mutableMapOf<String, String>()
        identities.forEach { identity ->
            identity.imagePaths.forEach { path ->
                try {
                    val imageFile = File(path)
                    if (imageFile.exists()) {
                        val encryptedBytes = imageFile.readBytes()
                        val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, filesKey)
                        identityImageBinaries[imageFile.name] = Base64.encodeToString(decryptedBytes, Base64.NO_WRAP)
                        decryptedBytes.fill(0)
                    }
                } catch (e: Exception) {
                    Log.w("BackupManager", "Failed to serialize identity image $path: ${e.message}")
                }
            }
        }
        
        return BackupPayload(
            scopes = scopes,
            passwords = if (includeFull || scopes.contains(BackupScope.PASSWORDS)) passwordRepository.getAllPasswords().first() else emptyList(),
            cards = if (includeFull || scopes.contains(BackupScope.CARDS)) cardRepository.getAllCards().first() else emptyList(),
            notes = if (includeFull || scopes.contains(BackupScope.NOTES)) noteRepository.getAllNotes().first() else emptyList(),
            files = files,
            folders = if (includeFull || scopes.contains(BackupScope.FILES)) fileRepository.getAllFolders().first() else emptyList(),
            identities = identities,
            fileBinaries = fileBinaries,
            identityImageBinaries = identityImageBinaries,
            exportedAt = System.currentTimeMillis()
        )
    }

    private suspend fun restorePayload(payload: BackupPayload) {
        val filesKey = KeystoreManager.getOrCreateFilesKey()
        val vaultFilesDir = File(context.filesDir, "vault_files").also { it.mkdirs() }
        val vaultImagesDir = File(context.filesDir, "vault_images").also { it.mkdirs() }

        // 1. Folders
        payload.folders?.forEach { folder ->
            val existing = fileRepository.getAllFolders().first().find { it.id == folder.id }
            if (existing == null) {
                fileRepository.insertFolder(folder)
            } else if (folder.updatedAt > existing.updatedAt) {
                fileRepository.insertFolder(folder)
            }
        }

        // 2. Passwords
        payload.passwords?.forEach { password ->
            val existing = passwordRepository.getPasswordById(password.id)
            if (existing == null) {
                passwordRepository.insertPassword(password)
            } else if (password.updatedAt > existing.updatedAt) {
                passwordRepository.updatePassword(password)
            }
        }

        // 3. Cards
        payload.cards?.forEach { card ->
            val existing = cardRepository.getCardById(card.id)
            if (existing == null) {
                cardRepository.insertCard(card)
            } else if (card.updatedAt > existing.updatedAt) {
                cardRepository.updateCard(card)
            }
        }

        // 4. Notes
        payload.notes?.forEach { note ->
            val existing = noteRepository.getNoteById(note.id)
            if (existing == null) {
                noteRepository.insertNote(note)
            } else if (note.updatedAt > existing.updatedAt) {
                noteRepository.updateNote(note)
            }
        }

        // 5. Files — restore physical file binaries
        payload.files?.forEach { file ->
            try {
                var restoredFile = file
                val base64Data = payload.fileBinaries?.get(file.id)
                if (base64Data != null) {
                    val decryptedBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                    val reEncryptedBytes = cryptoManager.encryptBytes(decryptedBytes, filesKey)
                    decryptedBytes.fill(0)
                    val newFileName = "${UUID.randomUUID()}.vlt"
                    val newFile = File(vaultFilesDir, newFileName)
                    newFile.writeBytes(reEncryptedBytes)
                    restoredFile = file.copy(encryptedFilePath = newFile.absolutePath)
                }
                val existing = fileRepository.getFileById(restoredFile.id)
                if (existing == null) {
                    fileRepository.insertFile(restoredFile)
                } else if (restoredFile.updatedAt > existing.updatedAt) {
                    // Clean up old physical file if path changed
                    if (existing.encryptedFilePath != restoredFile.encryptedFilePath) {
                        try { File(existing.encryptedFilePath).delete() } catch (_: Exception) {}
                    }
                    fileRepository.insertFile(restoredFile)
                }
            } catch (e: Exception) {
                Log.w("BackupManager", "Failed to restore file ${file.id}: ${e.message}")
            }
        }

        // 6. Identities — restore physical image binaries
        payload.identities?.forEach { identity ->
            try {
                var restoredIdentity = identity
                if (!payload.identityImageBinaries.isNullOrEmpty() && identity.imagePaths.isNotEmpty()) {
                    val newPaths = mutableListOf<String>()
                    identity.imagePaths.forEach { originalPath ->
                        val fileName = File(originalPath).name
                        val base64Data = payload.identityImageBinaries[fileName]
                        if (base64Data != null) {
                            try {
                                val decryptedBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                                val reEncryptedBytes = cryptoManager.encryptBytes(decryptedBytes, filesKey)
                                decryptedBytes.fill(0)
                                val newFileName = "${UUID.randomUUID()}.vimg"
                                val newImageFile = File(vaultImagesDir, newFileName)
                                newImageFile.writeBytes(reEncryptedBytes)
                                newPaths.add(newImageFile.absolutePath)
                            } catch (e: Exception) {
                                Log.w("BackupManager", "Failed to restore identity image $fileName: ${e.message}")
                                newPaths.add(originalPath) // Keep original path as fallback
                            }
                        } else {
                            newPaths.add(originalPath) // No binary data, keep original
                        }
                    }
                    restoredIdentity = identity.copy(imagePaths = newPaths)
                }
                val existing = identityRepository.getIdentityById(restoredIdentity.id)
                if (existing == null) {
                    identityRepository.insertIdentity(restoredIdentity)
                } else if (restoredIdentity.updatedAt > existing.updatedAt) {
                    // Clean up old physical images if updating
                    existing.imagePaths.forEach { oldPath ->
                        if (oldPath !in restoredIdentity.imagePaths) {
                            try { File(oldPath).delete() } catch (_: Exception) {}
                        }
                    }
                    identityRepository.updateIdentity(restoredIdentity)
                }
            } catch (e: Exception) {
                Log.w("BackupManager", "Failed to restore identity ${identity.id}: ${e.message}")
            }
        }
    }

    private fun calculateHmac(input: String, key: javax.crypto.SecretKey): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(key)
        val bytes = mac.doFinal(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getLocalBackups(): List<File> {
        val backupDir = File(context.filesDir, "backups")
        return if (backupDir.exists()) {
            backupDir.listFiles()?.filter { it.extension == "vbk" && it.name.startsWith("vaultix_history_") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else emptyList()
    }

    /**
     * Deletes old backups exceeding the limit to save space.
     */
    suspend fun cleanOldBackups(maxFiles: Int) = withContext(Dispatchers.IO) {
        val backups = getLocalBackups()
        if (backups.size > maxFiles) {
            backups.drop(maxFiles).forEach { it.delete() }
        }
    }

    fun deleteLocalBackup(file: File): Boolean = file.delete()

    suspend fun createHistoryBackup(): Result<File> = withContext(Dispatchers.IO) {
        // Local backups use a special Keystore key (device-bound)
        try {
            val payload = buildBackupPayload(setOf(BackupScope.FULL))
            val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
            val backupKey = KeystoreManager.getOrCreateBackupHistoryKey()
            val encryptedBytes = cryptoManager.encryptBytes(jsonBytes, backupKey)
            jsonBytes.fill(0)

            val backupDir = File(context.filesDir, "backups").also { it.mkdirs() }
            val backupFile = File(backupDir, "vaultix_history_${System.currentTimeMillis()}.vbk")
            backupFile.writeBytes(encryptedBytes)
            
            // Auto-cleanup old history backups (keep last 5)
            cleanOldBackups(5)
            
            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreDailyLocalBackup(file: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val encryptedBytes = file.readBytes()
            val backupKey = KeystoreManager.getOrCreateBackupHistoryKey()
            val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, backupKey)
            val json = String(decryptedBytes, Charsets.UTF_8)
            decryptedBytes.fill(0)
            val payload = gson.fromJson(json, BackupPayload::class.java)
            restorePayload(payload)
            val count = (payload.passwords?.size ?: 0) + (payload.cards?.size ?: 0) + 
                        (payload.notes?.size ?: 0) + (payload.files?.size ?: 0) + 
                        (payload.identities?.size ?: 0)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackupFromFile(file: File, password: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val combined = file.readBytes()
            if (combined.size < 28) return@withContext Result.failure(Exception("Invalid backup file"))

            val salt = combined.copyOfRange(0, 16)
            val encryptedData = combined.copyOfRange(16, combined.size)
            
            val derivedKey = cryptoManager.deriveKey(password, salt)
            val decryptedBytes = cryptoManager.decryptBytes(encryptedData, derivedKey)
            password.fill('\u0000')
            
            val json = String(decryptedBytes, Charsets.UTF_8)
            decryptedBytes.fill(0)
            
            val payload = gson.fromJson(json, BackupPayload::class.java)

            // Verify integrity
            val payloadToVerify = payload.copy(checksum = "")
            val jsonToVerify = gson.toJson(payloadToVerify)
            val calculatedChecksum = calculateHmac(jsonToVerify, derivedKey)
            
            if (calculatedChecksum != payload.checksum) {
                return@withContext Result.failure(Exception("Integrity Check Failed"))
            }

            restorePayload(payload)
            val count = (payload.passwords?.size ?: 0) + (payload.cards?.size ?: 0) + 
                        (payload.notes?.size ?: 0) + (payload.files?.size ?: 0) + 
                        (payload.identities?.size ?: 0)

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import backup from raw encrypted bytes (used by QR Code restore)
     * 
     * @param encryptedBackupData Raw encrypted backup bytes (from QR code chunks)
     * @param password Master password for decryption
     * @return Number of items restored
     */
    suspend fun importBackupFromBytes(encryptedBackupData: ByteArray, password: CharArray): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (encryptedBackupData.size < 28) {
                return@withContext Result.failure(Exception("Invalid backup data: too small"))
            }

            // Extract salt from first 16 bytes
            val salt = encryptedBackupData.copyOfRange(0, 16)
            val encryptedData = encryptedBackupData.copyOfRange(16, encryptedBackupData.size)
            
            // Derive key from password
            val derivedKey = cryptoManager.deriveKey(password, salt)
            val decryptedBytes = cryptoManager.decryptBytes(encryptedData, derivedKey)
            password.fill('\u0000')
            
            // Parse JSON payload
            val json = String(decryptedBytes, Charsets.UTF_8)
            decryptedBytes.fill(0)
            
            val payload = gson.fromJson(json, BackupPayload::class.java)

            // Verify integrity
            val payloadToVerify = payload.copy(checksum = "")
            val jsonToVerify = gson.toJson(payloadToVerify)
            val calculatedChecksum = calculateHmac(jsonToVerify, derivedKey)
            
            if (calculatedChecksum != payload.checksum) {
                return@withContext Result.failure(Exception("Integrity Check Failed: Backup may be corrupted"))
            }

            // Restore data
            restorePayload(payload)
            val count = (payload.passwords?.size ?: 0) + (payload.cards?.size ?: 0) + 
                        (payload.notes?.size ?: 0) + (payload.files?.size ?: 0) + 
                        (payload.identities?.size ?: 0)

            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create encrypted backup as raw bytes (for QR Code export)
     * Used by QRCodeBackupGenerator to generate QR codes
     */
    suspend fun createDailyLocalBackup(): ByteArray = withContext(Dispatchers.IO) {
        val payload = buildBackupPayload(setOf(BackupScope.FULL))
        val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val backupKey = KeystoreManager.getOrCreateBackupHistoryKey()
        val encryptedBytes = cryptoManager.encryptBytes(jsonBytes, backupKey)
        jsonBytes.fill(0)
        
        // Return encrypted bytes directly (no file)
        encryptedBytes
    }
}
