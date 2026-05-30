package com.vaultix.app.di

import android.content.Context
import android.util.Log
import com.vaultix.app.data.local.VaultixDatabase
import com.vaultix.app.data.local.dao.*
import com.vaultix.app.security.KeyDerivationManager
import com.vaultix.app.security.KeystoreManager
import com.vaultix.app.security.SecurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val TAG = "VaultixDB"

    @Provides
    @Singleton
    fun provideVaultixDatabase(
        @ApplicationContext context: Context,
        securePreferences: SecurePreferences,
        keyDerivationManager: KeyDerivationManager
    ): VaultixDatabase {
        Log.d(TAG, "provideVaultixDatabase called")

        val passphrase = runBlocking {
            getDatabasePassphrase(context, securePreferences, keyDerivationManager)
        }
        Log.d(TAG, "Passphrase derived, length=${passphrase.size}")

        // Try to create & open the database; if passphrase mismatch, wipe and retry
        return try {
            val db = VaultixDatabase.create(context, passphrase)
            // Force-open to detect "file is not a database" immediately
            db.openHelper.writableDatabase
            Log.d(TAG, "Database opened successfully")
            db
        } catch (e: Exception) {
            Log.e(TAG, "Database open FAILED: ${e.message}. Wiping and recreating.", e)
            // Delete the corrupted/mismatched DB files
            deleteDatabase(context)
            
            runBlocking {
                // Also clear the stored salt so a fresh passphrase is generated
                securePreferences.remove(SecurePreferences.KEY_DB_KEY_ENCRYPTED)
                
                // Re-derive passphrase (will generate new salt)
                val freshPassphrase = getDatabasePassphrase(context, securePreferences, keyDerivationManager)
                Log.d(TAG, "Fresh passphrase derived, length=${freshPassphrase.size}")
                VaultixDatabase.create(context, freshPassphrase)
            }
        }
    }

    /**
     * Deletes all database files (main + WAL + SHM journals).
     */
    private fun deleteDatabase(context: Context) {
        val dbName = VaultixDatabase.DB_NAME
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            dbFile.delete()
            Log.w(TAG, "Deleted DB file: ${dbFile.absolutePath}")
        }
        // Also delete WAL and SHM journal files
        val walFile = java.io.File(dbFile.absolutePath + "-wal")
        val shmFile = java.io.File(dbFile.absolutePath + "-shm")
        val journalFile = java.io.File(dbFile.absolutePath + "-journal")
        walFile.delete()
        shmFile.delete()
        journalFile.delete()
    }

    private suspend fun getDatabasePassphrase(
        context: Context,
        securePreferences: SecurePreferences,
        keyDerivationManager: KeyDerivationManager
    ): ByteArray {
        // Use a stable, deterministic passphrase based on stored salt
        // NOT dependent on Keystore key properties (which can be null/change)
        val dbKeySalt = securePreferences.getString(SecurePreferences.KEY_DB_KEY_ENCRYPTED)
        val salt: ByteArray

        if (dbKeySalt == null) {
            // First run: generate and store salt
            salt = keyDerivationManager.generateSalt()
            val saltB64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
            securePreferences.putString(SecurePreferences.KEY_DB_KEY_ENCRYPTED, saltB64)
            Log.d(TAG, "Generated new DB salt")
        } else {
            salt = android.util.Base64.decode(dbKeySalt, android.util.Base64.NO_WRAP)
            Log.d(TAG, "Using existing DB salt")
        }

        // Use a STABLE password string — no dependency on Keystore key properties
        // The salt stored in EncryptedSharedPreferences provides the uniqueness
        val stablePassword = "vaultix_db_aes_256_gcm_master".toCharArray()

        val derivedKey = keyDerivationManager.deriveKey(
            password = stablePassword,
            salt = salt
        )

        // Clear sensitive data
        stablePassword.fill('\u0000')

        return android.util.Base64.decode(derivedKey, android.util.Base64.NO_WRAP)
    }

    @Provides
    @Singleton
    fun providePasswordDao(db: VaultixDatabase): PasswordDao = db.passwordDao()

    @Provides
    @Singleton
    fun provideCardDao(db: VaultixDatabase): CardDao = db.cardDao()

    @Provides
    @Singleton
    fun provideNoteDao(db: VaultixDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideFileDao(db: VaultixDatabase): FileDao = db.fileDao()

    @Provides
    @Singleton
    fun provideIdentityDao(db: VaultixDatabase): IdentityDao = db.identityDao()

    @Provides
    @Singleton
    fun provideSecurityLogDao(db: VaultixDatabase): SecurityLogDao = db.securityLogDao()

    @Provides
    @Singleton
    fun provideFolderDao(db: VaultixDatabase): FolderDao = db.folderDao()
}
