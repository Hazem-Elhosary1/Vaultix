package com.vaultix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Identity/ID document entity.
 */
@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val documentType: String,   // Encrypted (Passport, Driver License, etc.)
    val documentName: String,   // Encrypted
    val documentNumber: String, // Encrypted
    val fullName: String,       // Encrypted
    val dateOfBirth: String,    // Encrypted
    val issuedBy: String,       // Encrypted
    val issuedDate: String,     // Encrypted
    val expiryDate: String,     // Encrypted
    val nationality: String,    // Encrypted
    val notes: String,          // Encrypted
    val imagePaths: String?,    // Comma-separated paths to encrypted images (max 3)
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1,
    val expiryTimestamp: Long? = null, // Unencrypted for background alerts
    val isFake: Boolean = false
)
