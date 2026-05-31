package com.vaultix.app.data.repository

import android.content.Context
import android.net.Uri
import com.vaultix.app.data.local.dao.FileDao
import com.vaultix.app.data.local.dao.FolderDao
import com.vaultix.app.data.local.entity.FileEntity
import com.vaultix.app.data.local.entity.FolderEntity
import com.vaultix.app.data.model.VaultFile
import com.vaultix.app.data.model.VaultFolder
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    private val fileDao: FileDao,
    private val folderDao: FolderDao,
    private val cryptoManager: CryptoManager,
    private val context: Context
) {
    private val key = KeystoreManager.getOrCreateFilesKey()
    private val vaultFilesDir: File
        get() = File(context.filesDir, "vault_files").also { it.mkdirs() }

    fun getAllFiles(): Flow<List<VaultFile>> {
        return fileDao.getAllFiles().map { entities ->
            entities.filter { it.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive }
                .mapNotNull { it.toVaultFile() }
        }
    }

    fun getAllFolders(): Flow<List<VaultFolder>> {
        return folderDao.getAllFolders(com.vaultix.app.security.VaultSession.isFakeVaultActive).map { entities ->
            entities.mapNotNull { it.toVaultFolder() }
        }
    }

    fun getFilesByFolder(folderId: String?): Flow<List<VaultFile>> {
        return fileDao.getAllFiles().map { entities ->
            entities.filter { it.folderId == folderId && it.isFake == com.vaultix.app.security.VaultSession.isFakeVaultActive }
                .mapNotNull { it.toVaultFile() }
        }
    }

    suspend fun getFileById(id: String): VaultFile? {
        val entity = fileDao.getFileById(id) ?: return null
        if (entity.isFake != com.vaultix.app.security.VaultSession.isFakeVaultActive) return null
        return entity.toVaultFile()
    }

    /**
     * Inserts a file metadata record. (Used for restore)
     */
    suspend fun insertFile(vaultFile: VaultFile) {
        fileDao.insertFile(vaultFile.toFileEntity())
    }

    /**
     * Imports a file from Uri, encrypts it, and stores metadata.
     */
    suspend fun importFile(uri: Uri, fileName: String, mimeType: String, folderId: String? = null): Result<VaultFile> {
        return try {
            val encryptedFileName = "${UUID.randomUUID()}.vlt"
            val encryptedFile = File(vaultFilesDir, encryptedFileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                encryptedFile.outputStream().use { output ->
                    cryptoManager.encryptStream(input, output, key)
                }
            }

            // Get original size (we can't easily get it from the encrypted file because of IV and GCM tag)
            val fileSizeBytes = File(context.cacheDir, "temp").let { temp ->
                 // Re-querying or using the URI is better
                 val fd = context.contentResolver.openFileDescriptor(uri, "r")
                 val size = fd?.statSize ?: 0L
                 fd?.close()
                 size
            }

            val now = System.currentTimeMillis()
            val vaultFile = VaultFile(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                mimeType = mimeType,
                encryptedFilePath = encryptedFile.absolutePath,
                fileSizeBytes = fileSizeBytes,
                notes = "",
                folderId = folderId,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                keyVersion = 1
            )

            fileDao.insertFile(vaultFile.toFileEntity())
            Result.success(vaultFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decrypts and writes file to an output stream.
     */
    suspend fun decryptFileToStream(vaultFile: VaultFile, outputStream: java.io.OutputStream): Result<Unit> {
        return try {
            val encryptedFile = File(vaultFile.encryptedFilePath)
            if (!encryptedFile.exists()) return Result.failure(Exception("File not found"))
            
            encryptedFile.inputStream().use { input ->
                cryptoManager.decryptStream(input, outputStream, key)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(id: String) {
        val entity = fileDao.getFileById(id)
        entity?.let {
            File(it.encryptedFilePath).delete()
            fileDao.deleteFileById(id)
        }
    }

    suspend fun toggleFavorite(file: VaultFile) {
        val fileEntity = fileDao.getFileById(file.id)
        fileEntity?.let {
            fileDao.updateFile(it.copy(isFavorite = !file.isFavorite, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteAllFiles() {
        vaultFilesDir.listFiles()?.forEach { it.delete() }
        fileDao.deleteAllFiles()
    }

    suspend fun getFileCount(): Int = fileDao.getFileCount()

    suspend fun deleteAllFolders() {
        folderDao.deleteAllFolders()
    }

    // --- Folder Management ---

    suspend fun createFolder(name: String, parentId: String? = null): VaultFolder {
        val now = System.currentTimeMillis()
        val folder = VaultFolder(
            id = UUID.randomUUID().toString(),
            name = name,
            parentFolderId = parentId,
            createdAt = now,
            updatedAt = now
        )
        folderDao.insertFolder(folder.toFolderEntity())
        return folder
    }

    suspend fun insertFolder(folder: VaultFolder) {
        folderDao.insertFolder(folder.toFolderEntity())
    }

    suspend fun deleteFolder(id: String) {
        // Move files in this folder to root? Or delete them? 
        // For safety, let's just null out their folderId.
        val filesInFolder = fileDao.getAllFiles().map { list -> list.filter { it.folderId == id } }.first()
        for (file in filesInFolder) {
            fileDao.updateFile(file.copy(folderId = null))
        }
        folderDao.deleteFolderById(id)
    }

    suspend fun updateFileFolder(fileId: String, folderId: String?) {
        val fileEntity = fileDao.getFileById(fileId)
        fileEntity?.let {
            fileDao.updateFile(it.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateFolderParent(folderId: String, parentFolderId: String?) {
        val folderEntity = folderDao.getFolderById(folderId)
        folderEntity?.let {
            folderDao.updateFolder(it.copy(parentFolderId = parentFolderId, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun FileEntity.toVaultFile(): VaultFile? {
        return try {
            VaultFile(
                id = id,
                fileName = cryptoManager.decrypt(fileName, key),
                mimeType = cryptoManager.decrypt(mimeType, key),
                encryptedFilePath = encryptedFilePath,
                fileSizeBytes = fileSizeBytes,
                notes = cryptoManager.decrypt(notes, key),
                folderId = folderId,
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = updatedAt,
                keyVersion = keyVersion
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun VaultFile.toFileEntity() = FileEntity(
        id = id,
        fileName = cryptoManager.encrypt(fileName, key),
        mimeType = cryptoManager.encrypt(mimeType, key),
        encryptedFilePath = encryptedFilePath,
        fileSizeBytes = fileSizeBytes,
        notes = cryptoManager.encrypt(notes, key),
        folderId = folderId,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        keyVersion = keyVersion,
        isFake = com.vaultix.app.security.VaultSession.isFakeVaultActive
    )

    private fun FolderEntity.toVaultFolder(): VaultFolder? {
        return try {
            VaultFolder(
                id = id,
                name = cryptoManager.decrypt(name, key),
                parentFolderId = parentFolderId,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun VaultFolder.toFolderEntity() = FolderEntity(
        id = id,
        name = cryptoManager.encrypt(name, key),
        parentFolderId = parentFolderId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFake = com.vaultix.app.security.VaultSession.isFakeVaultActive
    )
}
