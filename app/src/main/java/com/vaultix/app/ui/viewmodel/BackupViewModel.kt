package com.vaultix.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.util.BackupManager
import com.vaultix.app.util.BackupScope
import com.vaultix.app.util.ExportImportManager
import com.vaultix.app.data.repository.PasswordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val localBackups: List<java.io.File> = emptyList(),
    val isBackupEnabled: Boolean = true,
    val backupFrequency: String = "DAILY",
    val lastBackupTime: Long = 0L,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val securityRepository: com.vaultix.app.data.repository.SecurityRepository,
    private val exportImportManager: ExportImportManager,
    private val passwordRepository: PasswordRepository,
    private val cardRepository: com.vaultix.app.data.repository.CardRepository,
    private val noteRepository: com.vaultix.app.data.repository.NoteRepository,
    private val fileRepository: com.vaultix.app.data.repository.FileRepository,
    private val identityRepository: com.vaultix.app.data.repository.IdentityRepository,
    private val securePreferences: com.vaultix.app.security.SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        loadBackupSettings()
        refreshLocalBackups()
    }

    private fun loadBackupSettings() {
        viewModelScope.launch {
            val enabled = securePreferences.getBoolean(
                com.vaultix.app.security.SecurePreferences.KEY_BACKUP_ENABLED, true
            )
            val freq = securePreferences.getString(
                com.vaultix.app.security.SecurePreferences.KEY_BACKUP_FREQUENCY
            ) ?: "DAILY"
            val last = securePreferences.getLong(
                com.vaultix.app.security.SecurePreferences.KEY_LAST_BACKUP_TIME, 0L
            )
            
            _uiState.value = _uiState.value.copy(
                isBackupEnabled = enabled,
                backupFrequency = freq,
                lastBackupTime = last
            )
        }
    }

    fun setBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securePreferences.putBoolean(
                com.vaultix.app.security.SecurePreferences.KEY_BACKUP_ENABLED, enabled
            )
            _uiState.value = _uiState.value.copy(isBackupEnabled = enabled)
            if (!enabled) {
                _uiState.value = _uiState.value.copy(error = "تنبيه: إيقاف النسخ الاحتياطي قد يؤدي لفقدان بياناتك!")
            }
        }
    }

    fun setBackupFrequency(frequency: String) {
        viewModelScope.launch {
            securePreferences.putString(
                com.vaultix.app.security.SecurePreferences.KEY_BACKUP_FREQUENCY, frequency
            )
            _uiState.value = _uiState.value.copy(backupFrequency = frequency)
        }
    }

    fun triggerHistoryBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            backupManager.createHistoryBackup().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isExporting = false, message = "تم إنشاء نسخة احتياطية بنجاح!")
                    refreshLocalBackups()
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isExporting = false, error = "فشل إنشاء النسخة: ${it.message}")
                }
            )
        }
    }

    fun exportBackup(uri: Uri, password: CharArray, scopes: Set<BackupScope> = setOf(BackupScope.FULL)) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            backupManager.exportBackupToUri(uri, password, scopes).fold(
                onSuccess = {
                    val scopeLabel = if (scopes.contains(BackupScope.FULL)) {
                        "full vault"
                    } else {
                        scopes.joinToString(", ") { scope ->
                            when (scope) {
                                BackupScope.PASSWORDS -> "passwords"
                                BackupScope.CARDS -> "cards"
                                BackupScope.NOTES -> "notes"
                                BackupScope.FILES -> "files"
                                BackupScope.IDENTITIES -> "IDs"
                                BackupScope.FULL -> "full vault"
                            }
                        }
                    }
                    _uiState.value = _uiState.value.copy(isExporting = false, message = "Backup exported successfully! ($scopeLabel)")
                    viewModelScope.launch {
                        securityRepository.logEvent("BACKUP_EXPORT", "Encrypted backup exported to URI (scopes=$scopes)")
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isExporting = false, error = "Export failed: ${it.message}")
                    viewModelScope.launch {
                        securityRepository.logEvent("BACKUP_EXPORT_FAILED", "Failed to export backup: ${it.message}", "WARNING")
                    }
                }
            )
        }
    }

    fun importBackup(uri: Uri, password: CharArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            backupManager.importBackupFromUri(uri, password).fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(isImporting = false, message = "Restored $count records successfully!")
                    viewModelScope.launch {
                        securityRepository.logEvent("BACKUP_IMPORT", "Backup restored: $count records imported", "WARNING")
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isImporting = false, error = "Import failed: ${it.message}")
                    viewModelScope.launch {
                        securityRepository.logEvent("BACKUP_IMPORT_FAILED", "Failed to import backup: ${it.message}", "CRITICAL")
                    }
                }
            )
        }
    }

    fun refreshLocalBackups() {
        _uiState.value = _uiState.value.copy(localBackups = backupManager.getLocalBackups())
    }

    fun deleteLocalBackup(file: java.io.File) {
        if (backupManager.deleteLocalBackup(file)) {
            refreshLocalBackups()
        }
    }

    fun restoreLocalBackup(file: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            backupManager.restoreDailyLocalBackup(file).fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(isImporting = false, message = "Restored $count records from backup history!")
                    viewModelScope.launch {
                        securityRepository.logEvent("BACKUP_HISTORY_RESTORE", "Backup history restored: $count records", "WARNING")
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isImporting = false, error = "Restore failed: ${it.message}")
                    viewModelScope.launch {
                        securityRepository.logEvent("BACKUP_HISTORY_RESTORE_FAILED", "Failed to restore history backup: ${it.message}", "CRITICAL")
                    }
                }
            )
        }
    }

    fun exportPasswordsEncrypted(uri: android.net.Uri, password: CharArray, deviceBound: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            exportImportManager.exportPasswordsCsvEncrypted(uri, password, deviceBound).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isExporting = false, message = "Passwords exported successfully!")
                    viewModelScope.launch { securityRepository.logEvent("EXPORT_PASSWORDS", "Passwords exported to URI") }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isExporting = false, error = "Export failed: ${it.message}")
                    viewModelScope.launch { securityRepository.logEvent("EXPORT_PASSWORDS_FAILED", "Failed to export passwords: ${it.message}", "WARNING") }
                }
            )
        }
    }

    fun importPasswordsEncrypted(uri: android.net.Uri, password: CharArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            exportImportManager.importEncryptedFile(uri, password).fold(
                onSuccess = { bytes ->
                    // parse CSV and insert into DB
                    val csv = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    val rows = parseCsv(csv)
                    var imported = 0
                    withContext(Dispatchers.IO) {
                        for (row in rows) {
                            try {
                                val id = row.getOrNull(0)?.ifEmpty { java.util.UUID.randomUUID().toString() } ?: java.util.UUID.randomUUID().toString()
                                val title = row.getOrNull(1) ?: ""
                                val username = row.getOrNull(2) ?: ""
                                val pwd = row.getOrNull(3) ?: ""
                                val website = row.getOrNull(4) ?: ""
                                val appPackageName = row.getOrNull(5) ?: ""
                                val notes = row.getOrNull(6) ?: ""
                                val createdAt = row.getOrNull(7)?.toLongOrNull() ?: System.currentTimeMillis()
                                val updatedAt = row.getOrNull(8)?.toLongOrNull() ?: createdAt

                                val model = com.vaultix.app.data.model.Password(
                                    id = id,
                                    title = title,
                                    username = username,
                                    password = pwd.toCharArray(),
                                    website = website,
                                    appPackageName = appPackageName,
                                    notes = notes,
                                    passwordStrength = 0,
                                    isFavorite = false,
                                    passwordHistory = emptyList(),
                                    createdAt = createdAt,
                                    updatedAt = updatedAt,
                                    expiresAt = null,
                                    keyVersion = 1
                                )

                                passwordRepository.insertPassword(model)
                                // zeroize
                                model.password.fill('\u0000')
                                imported++
                            } catch (_: Exception) {
                                // skip malformed rows
                            }
                        }
                    }

                    _uiState.value = _uiState.value.copy(isImporting = false, message = "Imported $imported passwords")
                    viewModelScope.launch { securityRepository.logEvent("IMPORT_PASSWORDS", "Imported $imported passwords", "WARNING") }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isImporting = false, error = "Import failed: ${it.message}")
                    viewModelScope.launch { securityRepository.logEvent("IMPORT_PASSWORDS_FAILED", "Failed to import passwords: ${it.message}", "CRITICAL") }
                }
            )
        }
    }

    // Very small CSV parser for our exported format (skips header)
    private fun parseCsv(csv: String): List<List<String>> {
        val lines = csv.lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val data = mutableListOf<List<String>>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val parsed = mutableListOf<String>()
            var cur = StringBuilder()
            var inQuotes = false
            var j = 0
            while (j < line.length) {
                val ch = line[j]
                when {
                    ch == '"' -> {
                        if (inQuotes && j + 1 < line.length && line[j + 1] == '"') {
                            cur.append('"'); j += 1
                        } else {
                            inQuotes = !inQuotes
                        }
                    }
                    ch == ',' && !inQuotes -> {
                        parsed.add(cur.toString()); cur = StringBuilder()
                    }
                    else -> cur.append(ch)
                }
                j++
            }
            parsed.add(cur.toString())
            data.add(parsed)
        }
        return data
    }

    fun importLocalBackup(file: java.io.File, password: CharArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            backupManager.importBackupFromFile(file, password).fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(isImporting = false, message = "Restored $count records from history!")
                    viewModelScope.launch {
                        securityRepository.logEvent("BACKUP_IMPORT_LOCAL", "Local history backup restored: $count records", "WARNING")
                    }
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isImporting = false, error = "Restore failed: ${it.message}")
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    suspend fun getScopeCount(scope: BackupScope): Int {
        return withContext(Dispatchers.IO) {
            when (scope) {
                BackupScope.FULL -> {
                    val passwords = passwordRepository.getPasswordCount()
                    val cards = cardRepository.getCardCount()
                    val notes = noteRepository.getNoteCount()
                    val files = fileRepository.getFileCount()
                    val identities = identityRepository.getIdentityCount()
                    passwords + cards + notes + files + identities
                }
                BackupScope.PASSWORDS -> passwordRepository.getPasswordCount()
                BackupScope.CARDS -> cardRepository.getCardCount()
                BackupScope.NOTES -> noteRepository.getNoteCount()
                BackupScope.FILES -> fileRepository.getFileCount()
                BackupScope.IDENTITIES -> identityRepository.getIdentityCount()
            }
        }
    }
}
