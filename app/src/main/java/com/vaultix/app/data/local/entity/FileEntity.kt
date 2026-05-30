package com.vaultix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encrypted file vault entry.
 */
@Entity(tableName = "files")
data class FileEntity(
    @PrimaryKey val id: String,
    val fileName: String,         // Encrypted
    val mimeType: String,         // Encrypted
    val encryptedFilePath: String, // Path to the encrypted file on disk
    val fileSizeBytes: Long,       // Original size (not sensitive)
    val notes: String,            // Encrypted
    val folderId: String? = null,  // Parent folder
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1,
    val isFake: Boolean = false
)
