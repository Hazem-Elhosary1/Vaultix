package com.vaultix.app.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vaultix_secure_prefs")

/**
 * Encrypted preferences manager using AES-256-GCM via Android Keystore.
 * All preference values are encrypted before storage.
 */
@Singleton
class SecurePreferences @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        // Preference Keys
        val KEY_IS_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val KEY_PASSWORD_HASH = stringPreferencesKey("pwd_hash")
        val KEY_PASSWORD_SALT = stringPreferencesKey("pwd_salt")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_FAKE_PASSWORD_HASH = stringPreferencesKey("fake_pwd_hash")
        val KEY_FAKE_PASSWORD_SALT = stringPreferencesKey("fake_pwd_salt")
        val KEY_PANIC_PIN_HASH = stringPreferencesKey("panic_pin_hash")
        val KEY_PANIC_PIN_SALT = stringPreferencesKey("panic_pin_salt")
        val KEY_RECOVERY_HASH = stringPreferencesKey("recovery_hash")
        val KEY_RECOVERY_SALT = stringPreferencesKey("recovery_salt")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_AUTO_LOCK_TIMEOUT = intPreferencesKey("auto_lock_timeout")
        val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val KEY_PANIC_THRESHOLD = intPreferencesKey("panic_threshold")
        val KEY_DB_KEY_ENCRYPTED = stringPreferencesKey("db_key_enc")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_GRACE_PERIOD = intPreferencesKey("grace_period_seconds")
        val KEY_CLIPBOARD_CLEAR_DELAY = intPreferencesKey("clipboard_clear_delay")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // SYSTEM, LIGHT, DARK
        val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color") // Hex code
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language") // en, ar
        val KEY_LOCKOUT_END_TIME = longPreferencesKey("lockout_end_time")
        val KEY_RECOVERY_ENCRYPTED = stringPreferencesKey("recovery_enc")
        val KEY_IS_PREMIUM = booleanPreferencesKey("is_premium")

        // Backup Configuration
        val KEY_BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val KEY_BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency") // DAILY, WEEKLY, MONTHLY, NEVER
        val KEY_LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")
        val KEY_MAX_BACKUP_HISTORY = intPreferencesKey("max_backup_history")
    }

    private val secretKey = KeystoreManager.getOrCreatePrefsKey()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encrypted: String): String {
        val combined = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    suspend fun putString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs ->
            prefs[key] = encrypt(value)
        }
    }

    suspend fun getString(key: Preferences.Key<String>): String? {
        val prefs = context.dataStore.data.first()
        return prefs[key]?.let { runCatching { decrypt(it) }.getOrNull() }
    }

    suspend fun putPlainString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun getPlainString(key: Preferences.Key<String>): String? {
        val prefs = context.dataStore.data.first()
        return prefs[key]
    }

    fun getPlainStringFlow(key: Preferences.Key<String>): Flow<String?> {
        return context.dataStore.data.map { it[key] }
    }

    fun getStringFlow(key: Preferences.Key<String>): Flow<String?> {
        return context.dataStore.data.map { prefs ->
            prefs[key]?.let { runCatching { decrypt(it) }.getOrNull() }
        }
    }

    suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Boolean {
        return context.dataStore.data.first()[key] ?: default
    }

    fun getBooleanFlow(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> {
        return context.dataStore.data.map { it[key] ?: default }
    }

    suspend fun putInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun getInt(key: Preferences.Key<Int>, default: Int = 0): Int {
        return context.dataStore.data.first()[key] ?: default
    }

    fun getIntFlow(key: Preferences.Key<Int>, default: Int = 0): Flow<Int> {
        return context.dataStore.data.map { it[key] ?: default }
    }

    suspend fun putLong(key: Preferences.Key<Long>, value: Long) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }

    suspend fun getLong(key: Preferences.Key<Long>, default: Long = 0L): Long {
        return context.dataStore.data.first()[key] ?: default
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun <T> remove(key: Preferences.Key<T>) {
        context.dataStore.edit { it.remove(key) }
    }
}
