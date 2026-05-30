package com.vaultix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a password entry. All sensitive fields are encrypted.
 */
@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey val id: String,
    val title: String,           // Encrypted
    val username: String,        // Encrypted
    val password: String,        // Encrypted
    val website: String,         // Encrypted
    val appPackageName: String = "", // Unencrypted for Autofill matching
    val notes: String,           // Encrypted
    val passwordStrength: Int,   // 0-4 (unencrypted metric)
    val isFavorite: Boolean = false,
    val passwordHistory: String = "", // Encrypted list of old passwords
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
    val keyVersion: Int = 1,
    val isFake: Boolean = false
)
