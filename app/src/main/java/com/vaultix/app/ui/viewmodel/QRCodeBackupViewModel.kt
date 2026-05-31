package com.vaultix.app.ui.viewmodel

import android.app.Activity
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.repository.VaultRepository
import com.vaultix.app.security.BiometricManager
import com.vaultix.app.util.BackupManager
import com.vaultix.app.util.QRCodeBackupGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QRCodeBackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val biometricManager: BiometricManager,
    private val vaultRepository: VaultRepository,
    private val qrCodeGenerator: QRCodeBackupGenerator
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<QRBackupUIState>(QRBackupUIState.Idle)
    val uiState: StateFlow<QRBackupUIState> = _uiState.asStateFlow()
    
    private val _qrCodes = MutableStateFlow<List<Bitmap>>(emptyList())
    val qrCodes: StateFlow<List<Bitmap>> = _qrCodes.asStateFlow()
    
    private val _restoreProgress = MutableStateFlow<RestoreProgress>(RestoreProgress())
    val restoreProgress: StateFlow<RestoreProgress> = _restoreProgress.asStateFlow()
    
    private val _totalChunksToRestore = MutableStateFlow(0)
    val totalChunksToRestore: StateFlow<Int> = _totalChunksToRestore.asStateFlow()
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /**
     * Generate QR codes from current vault backup with app logo
     */
    fun generateQRCodesFromBackup(masterPassword: String, scopes: Set<com.vaultix.app.util.BackupScope> = setOf(com.vaultix.app.util.BackupScope.FULL), logo: Bitmap? = null) {
        viewModelScope.launch {
            try {
                _uiState.value = QRBackupUIState.Generating
                
                // Step 1: Create encrypted backup
                val backupResult = backupManager.exportBackup(
                    masterPassword.toCharArray(),
                    scopes
                )
                val backupFile = backupResult.getOrThrow()
                val encryptedBackup = backupFile.readBytes()
                backupFile.delete() // Clean up local file after reading bytes
                
                // Step 2: Generate QR codes with logo
                val qrCodes = qrCodeGenerator.generateQRCodesFromBackup(encryptedBackup, logo)
                
                _qrCodes.value = qrCodes
                _uiState.value = QRBackupUIState.Success(
                    message = "Generated ${qrCodes.size} QR codes",
                    totalChunks = qrCodes.size
                )
            } catch (e: Exception) {
                _uiState.value = QRBackupUIState.Error(
                    message = "Failed to generate QR codes: ${e.message}"
                )
            }
        }
    }

    fun setExporting(loading: Boolean) {
        _isExporting.value = loading
    }
    
    /**
     * Add scanned QR code chunk, ensuring no duplicates
     */
    fun addScannedQRChunk(scannedData: ByteArray) {
        viewModelScope.launch {
            try {
                // Step 1: Parse header to get metadata
                val metadata = qrCodeGenerator.parseChunkHeader(scannedData)
                _totalChunksToRestore.value = metadata.totalChunks
                
                val currentChunks = _restoreProgress.value.scannedChunks.toMutableList()
                
                // Step 2: Check if this chunk index was already scanned
                val alreadyScanned = currentChunks.any { 
                    try {
                        qrCodeGenerator.parseChunkHeader(it).chunkIndex == metadata.chunkIndex
                    } catch (e: Exception) { false }
                }
                
                if (alreadyScanned) {
                    _uiState.value = QRBackupUIState.ScanningProgress(
                        currentChunks = currentChunks.size,
                        message = "Chunk ${metadata.chunkIndex + 1} already scanned"
                    )
                    return@launch
                }
                
                // Step 3: Add new chunk
                currentChunks.add(scannedData)
                
                val progress = _restoreProgress.value.copy(
                    scannedChunks = currentChunks,
                    progress = currentChunks.size
                )
                
                _restoreProgress.value = progress
                _uiState.value = QRBackupUIState.ScanningProgress(
                    currentChunks = currentChunks.size,
                    message = "Scanned ${currentChunks.size}/${metadata.totalChunks} QR code(s)"
                )
            } catch (e: Exception) {
                _uiState.value = QRBackupUIState.Error(
                    message = "Invalid QR code: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Reset error state back to idle or scanning progress
     */
    fun resetError() {
        if (_restoreProgress.value.scannedChunks.isNotEmpty()) {
            _uiState.value = QRBackupUIState.ScanningProgress(
                currentChunks = _restoreProgress.value.scannedChunks.size,
                message = "Ready to restore"
            )
        } else {
            _uiState.value = QRBackupUIState.Idle
        }
    }

    /**
     * Clear scanned chunks
     */
    fun clearScannedChunks() {
        _restoreProgress.value = RestoreProgress()
        _uiState.value = QRBackupUIState.Idle
    }
    
    /**
     * Restore vault from scanned QR codes
     */
    fun restoreFromQRCodes(activity: Activity, masterPassword: String) {
        viewModelScope.launch {
            try {
                val chunks = _restoreProgress.value.scannedChunks
                
                if (chunks.isEmpty()) {
                    _uiState.value = QRBackupUIState.Error(
                        message = "No QR codes scanned"
                    )
                    return@launch
                }
                
                _uiState.value = QRBackupUIState.Restoring
                
                // Step 1: Reconstruct backup
                val encryptedBackup = qrCodeGenerator.reconstructBackupFromQRChunks(chunks)
                
                // Step 2: Biometric authentication
                val isBiometricValid = biometricManager.verifyBiometric(activity)
                if (!isBiometricValid) {
                    _uiState.value = QRBackupUIState.Error(
                        message = "Biometric verification failed"
                    )
                    return@launch
                }
                
                // Step 3: Import backup
                val restoredCount = backupManager.importBackupFromBytes(
                    encryptedBackup,
                    masterPassword.toCharArray()
                ).getOrThrow()
                
                _uiState.value = QRBackupUIState.RestoreSuccess(
                    message = "Successfully restored $restoredCount items to your vault"
                )
            } catch (e: Exception) {
                // Security: Use a generic error message to avoid giving hints about password correctness
                val displayMessage = if (e is javax.crypto.AEADBadTagException || e.message?.contains("Integrity", ignoreCase = true) == true) {
                    "Restore failed. Please ensure you are using the correct backup and password."
                } else {
                    "An error occurred during restoration: ${e.message}"
                }
                
                _uiState.value = QRBackupUIState.Error(
                    message = displayMessage
                )
            }
        }
    }
    
    /**
     * Export QR codes as images (for printing/sharing)
     */
    fun exportQRCodesToImages(): List<Pair<String, Bitmap>> {
        return _qrCodes.value.mapIndexed { index, bitmap ->
            "vaultix_backup_chunk_${index + 1}_of_${_qrCodes.value.size}" to bitmap
        }
    }
}

/**
 * QR Backup UI State
 */
sealed class QRBackupUIState {
    object Idle : QRBackupUIState()
    object Generating : QRBackupUIState()
    object Restoring : QRBackupUIState()
    data class Success(val message: String, val totalChunks: Int) : QRBackupUIState()
    data class ScanningProgress(val currentChunks: Int, val message: String) : QRBackupUIState()
    data class RestoreSuccess(val message: String) : QRBackupUIState()
    data class Error(val message: String) : QRBackupUIState()
}

/**
 * Restore progress tracking
 */
data class RestoreProgress(
    val scannedChunks: List<ByteArray> = emptyList(),
    val progress: Int = 0
)
