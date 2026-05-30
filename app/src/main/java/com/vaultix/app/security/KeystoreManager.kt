package com.vaultix.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages cryptographic keys using Android Keystore System.
 * Keys never leave the secure hardware element.
 */
object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_DB = "vaultix_db_master"
    private const val KEY_ALIAS_PREFS = "vaultix_prefs_key"
    private const val KEY_ALIAS_FILES = "vaultix_files_key"
    private const val KEY_ALIAS_BACKUP_HISTORY = "vaultix_backup_history_key"

    /**
     * Generates or retrieves the AES-256-GCM key for database encryption.
     */
    fun getOrCreateDatabaseKey(): SecretKey {
        return getOrCreateKey(KEY_ALIAS_DB)
    }

    fun getOrCreatePrefsKey(): SecretKey {
        return getOrCreateKey(KEY_ALIAS_PREFS)
    }

    fun getOrCreateFilesKey(): SecretKey {
        return getOrCreateKey(KEY_ALIAS_FILES)
    }

    fun getOrCreateBackupHistoryKey(): SecretKey {
        return getOrCreateKey(KEY_ALIAS_BACKUP_HISTORY)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Return existing key if present
        if (keyStore.containsAlias(alias)) {
            return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        }

        // Generate new AES-256-GCM key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // App-level auth handled separately
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Deletes all keystore entries (panic mode).
     */
    fun deleteAllKeys() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        listOf(KEY_ALIAS_DB, KEY_ALIAS_PREFS, KEY_ALIAS_FILES, KEY_ALIAS_BACKUP_HISTORY).forEach { alias ->
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }
}
