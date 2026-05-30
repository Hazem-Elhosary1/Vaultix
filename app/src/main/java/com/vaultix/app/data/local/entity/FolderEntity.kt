package com.vaultix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a folder in the File Vault.
 */
@Entity(tableName = "file_folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,         // Encrypted
    val parentFolderId: String?, // For nested folders
    val createdAt: Long,
    val updatedAt: Long,
    val isFake: Boolean = false
)
