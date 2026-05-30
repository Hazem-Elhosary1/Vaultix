package com.vaultix.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Encrypted security logs for tracking sensitive events.
 */
@Entity(tableName = "security_logs")
data class SecurityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String,      // Encrypted (e.g. "AUTH_SUCCESS", "AUTH_FAILED", "BACKUP_EXPORT")
    val details: String,        // Encrypted context
    val timestamp: Long,
    val severity: String        // "INFO", "WARNING", "CRITICAL" (not sensitive)
)
