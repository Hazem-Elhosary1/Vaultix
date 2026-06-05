package com.vaultix.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.AuthState
import com.vaultix.app.debug.DebugCategory
import com.vaultix.app.debug.DebugEventBus
import com.vaultix.app.debug.DebugSeverity
import com.vaultix.app.security.KeyDerivationManager
import com.vaultix.app.security.KeystoreManager
import com.vaultix.app.security.SecurePreferences
import com.vaultix.app.security.SecurityChecker
import com.vaultix.app.data.local.VaultixDatabase
import com.vaultix.app.data.repository.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val keyDerivationManager: KeyDerivationManager,
    private val securityChecker: SecurityChecker,
    private val securityRepository: SecurityRepository,
    private val database: VaultixDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val PANIC_THRESHOLD_DEFAULT = 10
        const val AUTO_LOCK_DEFAULT_SECONDS = 60
        const val GRACE_PERIOD_DEFAULT_SECONDS = 30
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSetupComplete = MutableStateFlow(false)
    val isSetupComplete: StateFlow<Boolean> = _isSetupComplete.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(false)
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _isPanicMode = MutableStateFlow(false)
    val isPanicMode: StateFlow<Boolean> = _isPanicMode.asStateFlow()

    private val _securityThreats = MutableStateFlow<List<com.vaultix.app.security.SecurityThreatInfo>>(emptyList())
    val securityThreats: StateFlow<List<com.vaultix.app.security.SecurityThreatInfo>> = _securityThreats.asStateFlow()

    // Brute force protection
    private val _isLockedOut = MutableStateFlow(false)
    val isLockedOut: StateFlow<Boolean> = _isLockedOut.asStateFlow()

    private val _lockoutRemainingSeconds = MutableStateFlow(0)
    val lockoutRemainingSeconds: StateFlow<Int> = _lockoutRemainingSeconds.asStateFlow()

    // Auto-lock and Grace period
    private var lastActivityTime = System.currentTimeMillis()
    private var lastAppExitTime = 0L
    
    private val _autoLockTimeoutSeconds = MutableStateFlow(AUTO_LOCK_DEFAULT_SECONDS)
    val autoLockTimeoutSeconds: StateFlow<Int> = _autoLockTimeoutSeconds.asStateFlow()

    private val _gracePeriodSeconds = MutableStateFlow(GRACE_PERIOD_DEFAULT_SECONDS)
    val gracePeriodSeconds: StateFlow<Int> = _gracePeriodSeconds.asStateFlow()

    private val _pendingShortcutAction = MutableStateFlow<String?>(null)
    val pendingShortcutAction: StateFlow<String?> = _pendingShortcutAction.asStateFlow()

    private val _isLanguageChosen = MutableStateFlow(false)
    val isLanguageChosen: StateFlow<Boolean> = _isLanguageChosen.asStateFlow()

    // Flag to temporarily bypass auto-lock when launching system/external intents (e.g. ML Kit Document Scanner)
    private var isSystemActivityActive = false

    fun setSystemActivityActive(active: Boolean) {
        isSystemActivityActive = active
        if (active) {
            updateActivity()
        }
    }

    fun isSystemActivityActive(): Boolean = isSystemActivityActive

    fun setPendingShortcutAction(action: String?) {
        _pendingShortcutAction.value = action
    }

    init {
        initializeApp()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            DebugEventBus.log(
                category  = DebugCategory.SYSTEM,
                eventType = "APP_INIT",
                details   = "Vaultix initializing — running security checks",
                source    = "AuthViewModel"
            )

            // Check security threats
            val threats = securityChecker.performSecurityChecks()
            _securityThreats.value = threats

            // Event-based Wipe for Tamper Detection
            if (threats.any { it.severity == com.vaultix.app.security.SecuritySeverity.CRITICAL }) {
                securityRepository.logEvent("TAMPER_DETECTED", "Critical tamper threat detected. Initiating Emergency Wipe.", "CRITICAL")
                triggerPanicMode()
                return@launch
            }

            // Load saved state
            _isSetupComplete.value = securePreferences.getBoolean(SecurePreferences.KEY_IS_SETUP_COMPLETE)
            _isOnboardingComplete.value = securePreferences.getBoolean(SecurePreferences.KEY_ONBOARDING_COMPLETE)
            _isLanguageChosen.value = securePreferences.getBoolean(SecurePreferences.KEY_LANGUAGE_CHOSEN)
            _isBiometricEnabled.value = securePreferences.getBoolean(SecurePreferences.KEY_BIOMETRIC_ENABLED)
            _failedAttempts.value = securePreferences.getInt(SecurePreferences.KEY_FAILED_ATTEMPTS)
            _autoLockTimeoutSeconds.value = securePreferences.getInt(
                SecurePreferences.KEY_AUTO_LOCK_TIMEOUT,
                AUTO_LOCK_DEFAULT_SECONDS
            )
            _gracePeriodSeconds.value = securePreferences.getInt(
                SecurePreferences.KEY_GRACE_PERIOD,
                GRACE_PERIOD_DEFAULT_SECONDS
            )

            // Check for persistent lockout
            val lockoutEndTime = securePreferences.getLong(SecurePreferences.KEY_LOCKOUT_END_TIME)
            val currentTime = System.currentTimeMillis()
            if (lockoutEndTime > currentTime) {
                val remainingSeconds = ((lockoutEndTime - currentTime) / 1000).toInt()
                startLockout(remainingSeconds, saveToPrefs = false) // Already saved
            }

            // Initial auth state
            _authState.value = AuthState.Unauthenticated
            _isLoading.value = false

            DebugEventBus.log(
                category  = DebugCategory.SYSTEM,
                eventType = "APP_READY",
                details   = "setup=${_isSetupComplete.value}, onboarding=${_isOnboardingComplete.value}, biometric=${_isBiometricEnabled.value}",
                source    = "AuthViewModel"
            )

            // Start inactivity monitor
            startInactivityMonitor()
        }
    }

    private fun startInactivityMonitor() {
        viewModelScope.launch {
            while (true) {
                delay(15000L) // Check every 15 seconds
                checkAutoLock()
            }
        }
    }

    /**
     * Verify master password.
     */
    fun verifyPassword(password: CharArray, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val storedHash = securePreferences.getString(SecurePreferences.KEY_PASSWORD_HASH)
            val storedSalt = securePreferences.getString(SecurePreferences.KEY_PASSWORD_SALT)
            
            val fakeHash = securePreferences.getString(SecurePreferences.KEY_FAKE_PASSWORD_HASH)
            val fakeSalt = securePreferences.getString(SecurePreferences.KEY_FAKE_PASSWORD_SALT)

            if (storedHash == null || storedSalt == null) {
                _authState.value = AuthState.Error("No password set")
                password.fill('\u0000') // Zeroization
                onFailure()
                return@launch
            }

            val (isRealValid, isFakeValid) = withContext(Dispatchers.Default) {
                val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
                val realValid = keyDerivationManager.verifyPassword(password, salt, storedHash)

                var fakeValid = false
                if (!realValid && fakeHash != null && fakeSalt != null) {
                    val fSalt = Base64.decode(fakeSalt, Base64.NO_WRAP)
                    fakeValid = keyDerivationManager.verifyPassword(password, fSalt, fakeHash)
                }
                Pair(realValid, fakeValid)
            }

            // Zeroization: Clear the input array immediately after use
            password.fill('\u0000')

            if (isRealValid) {
                com.vaultix.app.security.VaultSession.isFakeVaultActive = false
                handleAuthSuccess()
                onSuccess()
            } else if (isFakeValid) {
                com.vaultix.app.security.VaultSession.isFakeVaultActive = true
                handleAuthSuccess() // Silently act like a normal success
                onSuccess()
            } else {
                handleAuthFailure("Incorrect credentials")
                onFailure()
            }
        }
    }

    fun verifyPin(pin: CharArray, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val storedHash = securePreferences.getString(SecurePreferences.KEY_PIN_HASH)
            val storedSalt = securePreferences.getString(SecurePreferences.KEY_PIN_SALT)

            if (storedHash == null || storedSalt == null) {
                _authState.value = AuthState.Unauthenticated
                pin.fill('\u0000') // Zeroization
                onFailure()
                return@launch
            }

            val isValid = withContext(Dispatchers.Default) {
                val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
                keyDerivationManager.verifyPassword(pin, salt, storedHash)
            }
            
            // Zeroization
            pin.fill('\u0000')

            if (isValid) {
                handleAuthSuccess()
                onSuccess()
            } else {
                handleAuthFailure("Incorrect PIN")
                onFailure()
            }
        }
    }

    /**
     * Changes the unlock PIN after verifying the current PIN.
     */
    fun changePin(currentPin: CharArray, newPin: CharArray, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val storedHash = securePreferences.getString(SecurePreferences.KEY_PIN_HASH)
            val storedSalt = securePreferences.getString(SecurePreferences.KEY_PIN_SALT)

            if (storedHash == null || storedSalt == null) {
                currentPin.fill('\u0000')
                newPin.fill('\u0000')
                onFailure("No existing PIN found")
                return@launch
            }

            val isValid = withContext(Dispatchers.Default) {
                val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
                keyDerivationManager.verifyPassword(currentPin, salt, storedHash)
            }
            currentPin.fill('\u0000')

            if (!isValid) {
                newPin.fill('\u0000')
                onFailure("Incorrect current PIN")
                return@launch
            }

            try {
                val result = withContext(Dispatchers.Default) {
                    val newSalt = keyDerivationManager.generateSalt()
                    val newHash = keyDerivationManager.hashPin(newPin, newSalt)
                    Pair(newSalt, newHash)
                }
                newPin.fill('\u0000')

                val newSalt = result.first
                val newHash = result.second

                securePreferences.putString(SecurePreferences.KEY_PIN_HASH, newHash)
                securePreferences.putString(SecurePreferences.KEY_PIN_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
                securePreferences.putInt(SecurePreferences.KEY_FAILED_ATTEMPTS, 0)
                securePreferences.putLong(SecurePreferences.KEY_LOCKOUT_END_TIME, 0L)

                _failedAttempts.value = 0
                _isLockedOut.value = false
                _lockoutRemainingSeconds.value = 0

                securityRepository.logEvent("PIN_CHANGED", "User changed unlock PIN successfully")
                onSuccess()
            } catch (e: Exception) {
                newPin.fill('\u0000')
                onFailure("Failed to change PIN: ${e.message}")
            }
        }
    }

    private suspend fun checkPanicPin(pin: CharArray): Boolean {
        val panicHash = securePreferences.getString(SecurePreferences.KEY_PANIC_PIN_HASH)
        val panicSalt = securePreferences.getString(SecurePreferences.KEY_PANIC_PIN_SALT)

        if (panicHash != null && panicSalt != null) {
            val salt = Base64.decode(panicSalt, Base64.NO_WRAP)
            val isPanic = keyDerivationManager.verifyPassword(pin, salt, panicHash)
            if (isPanic) {
                securityRepository.logEvent("PANIC_PIN_ENTERED", "User entered the panic PIN", "CRITICAL")
                triggerPanicMode()
                return true
            }
        }
        return false
    }

    /**
     * Priority 1: Key Rotation & Re-encryption
     * Changes the master password and re-encrypts the underlying SQLCipher database
     */
    fun changeMasterPassword(currentPwd: CharArray, newPwd: CharArray, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // 1. Verify current password
            val storedHash = securePreferences.getString(SecurePreferences.KEY_PASSWORD_HASH)
            val storedSalt = securePreferences.getString(SecurePreferences.KEY_PASSWORD_SALT)

            if (storedHash == null || storedSalt == null) {
                currentPwd.fill('\u0000')
                newPwd.fill('\u0000')
                _isLoading.value = false
                onFailure("No existing password found")
                return@launch
            }

            val isValid = withContext(Dispatchers.Default) {
                val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
                keyDerivationManager.verifyPassword(currentPwd, salt, storedHash)
            }
            
            // Zeroize current password immediately
            currentPwd.fill('\u0000')

            if (!isValid) {
                newPwd.fill('\u0000')
                _isLoading.value = false
                onFailure("Incorrect current password")
                return@launch
            }

            try {
                // 2. Generate new Master Password Hash and derive DB key in background thread
                val result = withContext(Dispatchers.Default) {
                    val newMasterSalt = keyDerivationManager.generateSalt()
                    val newMasterHash = keyDerivationManager.deriveKey(newPwd, newMasterSalt)
                    
                    val newDbSalt = keyDerivationManager.generateSalt()
                    val keystoreKey = KeystoreManager.getOrCreateDatabaseKey()
                    val newDbPassphraseRaw = keyDerivationManager.deriveKey(
                        password = "vaultix_db_${keystoreKey.algorithm}_${keystoreKey.format}".toCharArray(),
                        salt = newDbSalt
                    )
                    val newDbPassphraseBytes = android.util.Base64.decode(newDbPassphraseRaw, android.util.Base64.NO_WRAP)
                    val hexString = newDbPassphraseBytes.joinToString("") { "%02x".format(it) }
                    
                    Triple(newMasterSalt, newMasterHash, Pair(newDbSalt, hexString))
                }
                
                // Zeroize new password
                newPwd.fill('\u0000')

                val newMasterSalt = result.first
                val newMasterHash = result.second
                val newDbSalt = result.third.first
                val hexString = result.third.second

                // Rekey the SQLCipher database (must be on a background thread as well)
                withContext(Dispatchers.IO) {
                    database.openHelper.writableDatabase.execSQL("PRAGMA rekey = \"x'$hexString'\"")
                }

                // 4. Save new credentials to SecurePreferences
                securePreferences.putString(SecurePreferences.KEY_PASSWORD_HASH, newMasterHash)
                securePreferences.putString(SecurePreferences.KEY_PASSWORD_SALT, Base64.encodeToString(newMasterSalt, Base64.NO_WRAP))
                securePreferences.putString(SecurePreferences.KEY_DB_KEY_ENCRYPTED, Base64.encodeToString(newDbSalt, Base64.NO_WRAP))

                securityRepository.logEvent("KEY_ROTATION", "Master password changed and database re-encrypted successfully")
                
                _isLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                _isLoading.value = false
                onFailure("Failed to rotate keys: ${e.message}")
            }
        }
    }

    /**
     * Mark biometric authentication as successful.
     */
    fun onBiometricSuccess() {
        DebugEventBus.log(
            category  = DebugCategory.AUTH,
            eventType = "BIOMETRIC_AUTH",
            details   = "Biometric prompt accepted",
            source    = "AuthViewModel"
        )
        handleAuthSuccess()
    }

    private fun handleAuthSuccess() {
        _authState.value = AuthState.Authenticated
        com.vaultix.app.security.VaultSession.isAuthenticated = true
        _failedAttempts.value = 0
        lastActivityTime = System.currentTimeMillis()
        viewModelScope.launch {
            securePreferences.putInt(SecurePreferences.KEY_FAILED_ATTEMPTS, 0)
            securityRepository.logEvent("AUTH_SUCCESS", "User authenticated successfully")
        }
    }

    private fun handleAuthFailure(error: String) {
        val attempts = _failedAttempts.value + 1
        _failedAttempts.value = attempts
        _authState.value = AuthState.Unauthenticated

        viewModelScope.launch {
            securePreferences.putInt(SecurePreferences.KEY_FAILED_ATTEMPTS, attempts)

            val panicThreshold = securePreferences.getInt(
                SecurePreferences.KEY_PANIC_THRESHOLD,
                PANIC_THRESHOLD_DEFAULT
            )

            if (attempts >= panicThreshold) {
                securityRepository.logEvent("PANIC_TRIGGERED", "Panic threshold reached after $attempts failed attempts", "CRITICAL")
                triggerPanicMode()
                return@launch
            }

            securityRepository.logEvent("AUTH_FAILED", "Failed attempt #$attempts: $error", "WARNING")

            // Exponential backoff after 3 failed attempts
            if (attempts >= 3) {
                val lockoutSeconds = when (attempts) {
                    3 -> 5
                    4 -> 15
                    5 -> 30
                    6 -> 60
                    else -> 300 // 5 minutes for 7+
                }
                startLockout(lockoutSeconds)
            }
        }
    }

    /**
     * Starts a temporary lockout with countdown.
     */
    private fun startLockout(seconds: Int, saveToPrefs: Boolean = true) {
        _isLockedOut.value = true
        _lockoutRemainingSeconds.value = seconds

        viewModelScope.launch {
            if (saveToPrefs) {
                val endTime = System.currentTimeMillis() + (seconds * 1000L)
                securePreferences.putLong(SecurePreferences.KEY_LOCKOUT_END_TIME, endTime)
            }

            for (i in seconds downTo 1) {
                // Double check if we are still locked out (could be reset externally)
                if (!_isLockedOut.value) break
                _lockoutRemainingSeconds.value = i
                delay(1000L)
            }
            
            _isLockedOut.value = false
            _lockoutRemainingSeconds.value = 0
            if (saveToPrefs) {
                securePreferences.putLong(SecurePreferences.KEY_LOCKOUT_END_TIME, 0L)
            }
        }
    }

    /**
     * Lock the app immediately.
     */
    fun lock(isManual: Boolean = true) {
        if (!isManual && _gracePeriodSeconds.value > 0) {
            // If locking due to backgrounding, set the exit time for grace period check
            lastAppExitTime = System.currentTimeMillis()
            DebugEventBus.log(
                category  = DebugCategory.AUTH,
                eventType = "GRACE_PERIOD_STARTED",
                details   = "gracePeriod=${_gracePeriodSeconds.value}s",
                source    = "AuthViewModel"
            )
        } else {
            // Manual lock or grace period disabled: reset exit time and lock immediately
            lastAppExitTime = 0L
            _authState.value = AuthState.Unauthenticated
            com.vaultix.app.security.VaultSession.isAuthenticated = false
            DebugEventBus.log(
                category  = DebugCategory.AUTH,
                eventType = "VAULT_LOCKED",
                details   = "manual=$isManual",
                severity  = DebugSeverity.WARNING,
                source    = "AuthViewModel"
            )
        }
    }

    /**
     * Check if the user can bypass login due to grace period.
     */
    fun checkGracePeriod(): Boolean {
        if (lastAppExitTime == 0L || _gracePeriodSeconds.value <= 0) return false
        
        val elapsed = (System.currentTimeMillis() - lastAppExitTime) / 1000
        val isWithinGrace = elapsed < _gracePeriodSeconds.value && _authState.value is AuthState.Authenticated
        
        if (isWithinGrace) {
            // User returned within grace period, keep authenticated
            lastAppExitTime = 0L
            updateActivity()
            return true
        } else {
            // Grace period expired, lock the app
            lock(isManual = true)
            return false
        }
    }

    /**
     * Update activity timestamp (call on user interaction to reset auto-lock timer).
     */
    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
    }

    /**
     * Check if auto-lock should trigger.
     */
    fun checkAutoLock() {
        val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
        if (elapsed >= _autoLockTimeoutSeconds.value && _authState.value is AuthState.Authenticated) {
            lock()
        }
    }

    /**
     * PANIC MODE: Deletes everything.
     */
    fun triggerPanicMode() {
        viewModelScope.launch {
            _isPanicMode.value = true
            _authState.value = AuthState.Unauthenticated

            // Delete all Keystore keys (makes data permanently inaccessible)
            KeystoreManager.deleteAllKeys()

            // Clear all preferences
            securePreferences.clearAll()

            // Delete encrypted vault files
            val vaultFilesDir = java.io.File(context.filesDir, "vault_files")
            vaultFilesDir.deleteRecursively()

            // Delete encrypted identity images
            val vaultImagesDir = java.io.File(context.filesDir, "vault_images")
            vaultImagesDir.deleteRecursively()

            // Delete database
            context.deleteDatabase(com.vaultix.app.data.local.VaultixDatabase.DB_NAME)

            _isPanicMode.value = false
        }
    }

    /**
     * Setup: Save master password.
     */
    suspend fun setupMasterPassword(password: CharArray): Boolean {
        return withContext(Dispatchers.Default) {
            val salt = keyDerivationManager.generateSalt()
            val hash = keyDerivationManager.deriveKey(password, salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            securePreferences.putString(SecurePreferences.KEY_PASSWORD_HASH, hash)
            securePreferences.putString(SecurePreferences.KEY_PASSWORD_SALT, saltB64)
            
            password.fill('\u0000') // Zeroization
            true
        }
    }

    /**
     * Setup: Save PIN.
     */
    suspend fun setupPin(pin: CharArray): Boolean {
        return withContext(Dispatchers.Default) {
            val salt = keyDerivationManager.generateSalt()
            val hash = keyDerivationManager.hashPin(pin, salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            securePreferences.putString(SecurePreferences.KEY_PIN_HASH, hash)
            securePreferences.putString(SecurePreferences.KEY_PIN_SALT, saltB64)
            
            pin.fill('\u0000') // Zeroization
            true
        }
    }

    /**
     * Setup: Save panic PIN.
     */
    suspend fun setupPanicPin(pin: CharArray): Boolean {
        return withContext(Dispatchers.Default) {
            val salt = keyDerivationManager.generateSalt()
            val hash = keyDerivationManager.hashPin(pin, salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            securePreferences.putString(SecurePreferences.KEY_PANIC_PIN_HASH, hash)
            securePreferences.putString(SecurePreferences.KEY_PANIC_PIN_SALT, saltB64)
            
            pin.fill('\u0000') // Zeroization
            true
        }
    }

    /**
     * Mark setup as complete.
     */
    suspend fun completeSetup() {
        securePreferences.putBoolean(SecurePreferences.KEY_IS_SETUP_COMPLETE, true)
        _isSetupComplete.value = true
        DebugEventBus.log(
            category  = DebugCategory.SYSTEM,
            eventType = "SETUP_COMPLETE",
            details   = "Vault setup finished successfully",
            source    = "AuthViewModel"
        )
    }

    /**
     * Mark onboarding as complete.
     */
    suspend fun completeOnboarding() {
        securePreferences.putBoolean(SecurePreferences.KEY_ONBOARDING_COMPLETE, true)
        _isOnboardingComplete.value = true
        DebugEventBus.log(
            category  = DebugCategory.SYSTEM,
            eventType = "ONBOARDING_COMPLETE",
            details   = "User completed onboarding flow",
            source    = "AuthViewModel"
        )
    }

    /**
     * Mark language selection as complete (first-launch).
     */
    suspend fun completeLanguageSelection() {
        securePreferences.putBoolean(SecurePreferences.KEY_LANGUAGE_CHOSEN, true)
        _isLanguageChosen.value = true
        DebugEventBus.log(
            category  = DebugCategory.SYSTEM,
            eventType = "LANGUAGE_CHOSEN",
            details   = "User completed language selection",
            source    = "AuthViewModel"
        )
    }

    /**
     * Toggle biometric.
     */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        securePreferences.putBoolean(SecurePreferences.KEY_BIOMETRIC_ENABLED, enabled)
        _isBiometricEnabled.value = enabled
    }

    /**
     * Set auto-lock timeout in seconds.
     */
    suspend fun setAutoLockTimeout(seconds: Int) {
        securePreferences.putInt(SecurePreferences.KEY_AUTO_LOCK_TIMEOUT, seconds)
        _autoLockTimeoutSeconds.value = seconds
    }

    /**
     * Set grace period in seconds.
     */
    suspend fun setGracePeriod(seconds: Int) {
        securePreferences.putInt(SecurePreferences.KEY_GRACE_PERIOD, seconds)
        _gracePeriodSeconds.value = seconds
    }

    fun setFakePassword(password: CharArray, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val salt = keyDerivationManager.generateSalt()
            val hash = keyDerivationManager.deriveKey(password, salt)
            
            securePreferences.putString(SecurePreferences.KEY_FAKE_PASSWORD_HASH, hash)
            securePreferences.putString(
                SecurePreferences.KEY_FAKE_PASSWORD_SALT,
                android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
            )
            
            password.fill('\u0000')
            onSuccess()
        }
    }

    /**
     * Generates a 24-character random recovery key.
     */
    fun generateRecoveryKey(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Readable chars (no 0/O, 1/I)
        return (1..24)
            .map { chars.random() }
            .chunked(4)
            .joinToString("-")
    }

    /**
     * Sets up the recovery key by hashing and storing it.
     */
    suspend fun setupRecoveryKey(key: String): Boolean {
        return withContext(Dispatchers.Default) {
            val normalizedKey = key.replace("-", "").uppercase()
            val salt = keyDerivationManager.generateSalt()
            val hash = keyDerivationManager.deriveKey(normalizedKey.toCharArray(), salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            securePreferences.putString(SecurePreferences.KEY_RECOVERY_HASH, hash)
            securePreferences.putString(SecurePreferences.KEY_RECOVERY_SALT, saltB64)
            
            // Store encrypted version for viewing in Settings (protected by Keystore)
            securePreferences.putString(SecurePreferences.KEY_RECOVERY_ENCRYPTED, key)
            
            securityRepository.logEvent("RECOVERY_KEY_SETUP", "User set up a new recovery key")
            true
        }
    }

    suspend fun getRecoveryKey(): String? {
        return securePreferences.getString(SecurePreferences.KEY_RECOVERY_ENCRYPTED)
    }

    /**
     * Verifies the recovery key.
     */
    suspend fun verifyRecoveryKey(key: String): Boolean {
        return withContext(Dispatchers.Default) {
            val normalizedKey = key.replace("-", "").uppercase()
            val storedHash = securePreferences.getString(SecurePreferences.KEY_RECOVERY_HASH)
            val storedSalt = securePreferences.getString(SecurePreferences.KEY_RECOVERY_SALT)

            if (storedHash == null || storedSalt == null) false
            else {
                val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
                keyDerivationManager.verifyPassword(normalizedKey.toCharArray(), salt, storedHash)
            }
        }
    }

    /**
     * Resets the master password using a verified recovery key.
     */
    fun resetPasswordWithRecoveryKey(recoveryKey: String, newPassword: CharArray, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            val isVerified = withContext(Dispatchers.Default) {
                verifyRecoveryKey(recoveryKey)
            }
            if (!isVerified) {
                onFailure("Invalid recovery key")
                return@launch
            }

            try {
                val result = withContext(Dispatchers.Default) {
                    // 1. Generate new Master Password Hash
                    val newMasterSalt = keyDerivationManager.generateSalt()
                    val newMasterHash = keyDerivationManager.deriveKey(newPassword, newMasterSalt)
                    Pair(newMasterSalt, newMasterHash)
                }
                
                val newMasterSalt = result.first
                val newMasterHash = result.second
                
                securePreferences.putString(SecurePreferences.KEY_PASSWORD_HASH, newMasterHash)
                securePreferences.putString(SecurePreferences.KEY_PASSWORD_SALT, Base64.encodeToString(newMasterSalt, Base64.NO_WRAP))
                
                securityRepository.logEvent("PASSWORD_RESET_RECOVERY", "User reset master password using recovery key")
                onSuccess()
            } catch (e: Exception) {
                onFailure("Reset failed: ${e.message}")
            } finally {
                newPassword.fill('\u0000')
            }
        }
    }
}
