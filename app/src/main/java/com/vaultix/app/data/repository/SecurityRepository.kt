package com.vaultix.app.data.repository

import com.vaultix.app.data.local.dao.SecurityLogDao
import com.vaultix.app.data.local.entity.SecurityLogEntity
import com.vaultix.app.debug.DebugCategory
import com.vaultix.app.debug.DebugEventBus
import com.vaultix.app.debug.DebugSeverity
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRepository @Inject constructor(
    private val securityLogDao: SecurityLogDao,
    private val cryptoManager: CryptoManager
) {
    private val key: javax.crypto.SecretKey?
        get() = try {
            KeystoreManager.getOrCreateDatabaseKey()
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            // Key was invalidated (biometric change, etc.) — delete and regenerate
            android.util.Log.w("SecurityRepo", "Key invalidated, regenerating", e)
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.deleteEntry("vaultix_db_master")
                KeystoreManager.getOrCreateDatabaseKey()
            } catch (e2: Exception) {
                android.util.Log.e("SecurityRepo", "Key regeneration failed", e2)
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SecurityRepo", "Key access failed", e)
            null
        }

    fun getRecentLogs(): Flow<List<SecurityLog>> {
        return securityLogDao.getRecentLogs().map { entities ->
            entities.mapNotNull { entity ->
                entity.toDecrypted()
            }
        }
    }

    suspend fun logEvent(eventType: String, details: String, severity: String = "INFO") {
        // Emit to in-memory debug bus first (no-op in release builds)
        val debugSeverity = when (severity.uppercase()) {
            "CRITICAL" -> DebugSeverity.CRITICAL
            "WARNING"  -> DebugSeverity.WARNING
            else       -> DebugSeverity.INFO
        }
        DebugEventBus.log(
            category  = DebugCategory.SECURITY,
            eventType = eventType,
            details   = details,
            severity  = debugSeverity,
            source    = "SecurityRepository"
        )

        // Persist encrypted log to Room DB
        try {
            val databaseKey = key ?: return
            val entity = SecurityLogEntity(
                eventType = cryptoManager.encrypt(eventType, databaseKey),
                details   = cryptoManager.encrypt(details, databaseKey),
                timestamp = System.currentTimeMillis(),
                severity  = severity
            )
            securityLogDao.insertLog(entity)
        } catch (e: Exception) {
            // Silently fail for security logging
        }
    }

    suspend fun clearLogs() {
        securityLogDao.clearLogs()
        DebugEventBus.clear()
    }

    private fun SecurityLogEntity.toDecrypted(): SecurityLog? {
        val databaseKey = key ?: return null
        return try {
            SecurityLog(
                id = id,
                eventType = cryptoManager.decrypt(eventType, databaseKey),
                details = cryptoManager.decrypt(details, databaseKey),
                timestamp = timestamp,
                severity = severity
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class SecurityLog(
    val id: Long,
    val eventType: String,
    val details: String,
    val timestamp: Long,
    val severity: String
)
