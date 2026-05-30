package com.vaultix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Secure note entity - all content encrypted.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,       // Encrypted
    val content: String,     // Encrypted
    val color: String = "#1A2744",  // Not sensitive
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1,
    val isFake: Boolean = false
)
