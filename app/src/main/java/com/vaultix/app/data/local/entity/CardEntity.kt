package com.vaultix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a credit card entry. All sensitive fields are encrypted.
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val cardName: String,        // Encrypted (e.g., "My Visa")
    val holderName: String,      // Encrypted
    val cardNumber: String,      // Encrypted
    val expiryMonth: String,     // Encrypted
    val expiryYear: String,      // Encrypted
    val cvv: String,             // Encrypted
    val cardType: String,        // Encrypted (Visa/MC/Amex)
    val notes: String,           // Encrypted
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val keyVersion: Int = 1,
    val expiryTimestamp: Long? = null, // Unencrypted for background alerts
    val isFake: Boolean = false
)
