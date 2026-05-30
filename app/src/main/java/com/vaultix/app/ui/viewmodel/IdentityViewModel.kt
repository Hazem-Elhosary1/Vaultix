package com.vaultix.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.Identity
import com.vaultix.app.data.repository.IdentityRepository
import com.vaultix.app.debug.DebugCategory
import com.vaultix.app.debug.DebugEventBus
import com.vaultix.app.debug.DebugSeverity
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val cryptoManager: CryptoManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val filesKey = KeystoreManager.getOrCreateFilesKey()

    // Holds a single Identity for edit/add screen
    private val _identity = MutableStateFlow(
        Identity(
            id = "",
            documentType = "",
            documentName = "",
            documentNumber = "",
            fullName = "",
            dateOfBirth = "",
            issuedBy = "",
            issuedDate = "",
            expiryDate = "",
            nationality = "",
            notes = "",
            imagePaths = emptyList(),
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    )
    val identity: StateFlow<Identity> = _identity.asStateFlow()

    // All identities for listing
    private val _allIdentities = MutableStateFlow<List<Identity>>(emptyList())
    val allIdentities: StateFlow<List<Identity>> = _allIdentities.asStateFlow()

    init {
        viewModelScope.launch {
            identityRepository.getAllIdentities().collect { _allIdentities.value = it }
        }
    }

    /** Load an existing identity from the repository */
    fun loadIdentity(id: String) {
        viewModelScope.launch {
            identityRepository.getIdentityById(id)?.let { _identity.value = it }
        }
    }

    // ── Field update helpers ──

    fun updateDocumentType(value: String) {
        _identity.value = _identity.value.copy(documentType = value)
    }

    fun updateDocumentName(value: String) {
        _identity.value = _identity.value.copy(documentName = value)
    }

    fun updateDocumentNumber(value: String) {
        _identity.value = _identity.value.copy(documentNumber = value)
    }

    fun updateFullName(value: String) {
        _identity.value = _identity.value.copy(fullName = value)
    }

    fun updateDateOfBirth(value: String) {
        _identity.value = _identity.value.copy(dateOfBirth = value)
    }

    fun updateNationality(value: String) {
        _identity.value = _identity.value.copy(nationality = value)
    }

    fun updateIssuedBy(value: String) {
        _identity.value = _identity.value.copy(issuedBy = value)
    }

    fun updateIssuedDate(value: String) {
        _identity.value = _identity.value.copy(issuedDate = value)
    }

    fun updateExpiryDate(value: String) {
        _identity.value = _identity.value.copy(expiryDate = value)
    }

    fun updateNotes(value: String) {
        _identity.value = _identity.value.copy(notes = value)
    }

    /**
     * Save (insert or update) the identity.
     * If an imageUri is provided, the image is encrypted and stored
     * in the app's internal vault_images directory.
     */
    fun addImage(imageUri: Uri) {
        if (_identity.value.imagePaths.size >= 3) return

        viewModelScope.launch {
            try {
                val imageDir = File(context.filesDir, "vault_images").also { it.mkdirs() }
                val encryptedFileName = "${UUID.randomUUID()}.vimg"
                val encryptedFile = File(imageDir, encryptedFileName)

                val inputStream = context.contentResolver.openInputStream(imageUri)
                val originalBytes = inputStream?.readBytes()
                inputStream?.close()

                if (originalBytes != null) {
                    val encryptedBytes = cryptoManager.encryptBytes(originalBytes, filesKey)
                    encryptedFile.writeBytes(encryptedBytes)
                    
                    val currentPaths = _identity.value.imagePaths.toMutableList()
                    currentPaths.add(encryptedFile.absolutePath)
                    _identity.value = _identity.value.copy(imagePaths = currentPaths)
                    
                    originalBytes.fill(0)
                }
            } catch (_: Exception) {}
        }
    }

    fun removeImage(path: String) {
        val currentPaths = _identity.value.imagePaths.toMutableList()
        if (currentPaths.remove(path)) {
            _identity.value = _identity.value.copy(imagePaths = currentPaths)
            // Optional: delete the file from storage
            try { File(path).delete() } catch (_: Exception) {}
        }
    }

    fun saveIdentity() {
        viewModelScope.launch {
            val current = _identity.value.copy(updatedAt = System.currentTimeMillis())
            val isNew = current.id.isEmpty()
            if (isNew) {
                identityRepository.insertIdentity(current)
                DebugEventBus.log(
                    category  = DebugCategory.CRUD,
                    eventType = "IDENTITY_CREATED",
                    details   = "name=${current.fullName}, type=${current.documentType}",
                    source    = "IdentityViewModel"
                )
            } else {
                identityRepository.updateIdentity(current)
                DebugEventBus.log(
                    category  = DebugCategory.CRUD,
                    eventType = "IDENTITY_UPDATED",
                    details   = "id=${current.id}, name=${current.fullName}",
                    source    = "IdentityViewModel"
                )
            }
            resetState()
        }
    }

    /** Decrypts and loads an image from the internal storage */
    fun decryptImage(path: String): android.graphics.Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val encryptedBytes = file.readBytes()
            val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, filesKey)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
            
            // Zeroize sensitive decrypted bytes
            decryptedBytes.fill(0)
            
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun resetState() {
        _identity.value = Identity(
            id = "",
            documentType = "",
            documentName = "",
            documentNumber = "",
            fullName = "",
            dateOfBirth = "",
            issuedBy = "",
            issuedDate = "",
            expiryDate = "",
            nationality = "",
            notes = "",
            imagePaths = emptyList(),
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun deleteIdentity(identity: Identity) {
        viewModelScope.launch {
            identityRepository.deleteIdentity(identity.id)
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "IDENTITY_DELETED",
                details   = "id=${identity.id}, name=${identity.fullName}",
                severity  = DebugSeverity.WARNING,
                source    = "IdentityViewModel"
            )
        }
    }

    fun toggleFavorite(identity: Identity) {
        viewModelScope.launch {
            identityRepository.updateIdentity(identity.copy(isFavorite = !identity.isFavorite))
            DebugEventBus.log(
                category  = DebugCategory.CRUD,
                eventType = "IDENTITY_FAVORITE_TOGGLED",
                details   = "id=${identity.id}, isFavorite=${!identity.isFavorite}",
                source    = "IdentityViewModel"
            )
        }
    }
}
