package com.vaultix.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.VaultFile
import com.vaultix.app.data.repository.FileRepository
import com.vaultix.app.debug.DebugCategory
import com.vaultix.app.debug.DebugEventBus
import com.vaultix.app.debug.DebugSeverity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {
    private val _folders = MutableStateFlow<List<com.vaultix.app.data.model.VaultFolder>>(emptyList())
    val folders: StateFlow<List<com.vaultix.app.data.model.VaultFolder>> = _folders.asStateFlow()

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _files = MutableStateFlow<List<VaultFile>>(emptyList())
    val files: StateFlow<List<VaultFile>> = _files.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        viewModelScope.launch {
            fileRepository.getAllFolders().collect { _folders.value = it }
        }
        viewModelScope.launch {
            fileRepository.getAllFiles().collect { list ->
                _files.value = list
            }
        }
    }

    fun navigateToFolder(id: String?) {
        _currentFolderId.value = id
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            fileRepository.createFolder(name, _currentFolderId.value)
            DebugEventBus.log(
                category  = DebugCategory.FILE,
                eventType = "FOLDER_CREATED",
                details   = "name=$name, parentId=${_currentFolderId.value ?: "root"}",
                source    = "FileViewModel"
            )
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            fileRepository.deleteFolder(id)
            DebugEventBus.log(
                category  = DebugCategory.FILE,
                eventType = "FOLDER_DELETED",
                details   = "id=$id",
                severity  = DebugSeverity.WARNING,
                source    = "FileViewModel"
            )
        }
    }

    fun importFile(uri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _isImporting.value = true
            DebugEventBus.log(
                category  = DebugCategory.FILE,
                eventType = "FILE_IMPORT_STARTED",
                details   = "name=$fileName, mime=$mimeType",
                source    = "FileViewModel"
            )
            fileRepository.importFile(uri, fileName, mimeType, _currentFolderId.value)
            DebugEventBus.log(
                category  = DebugCategory.FILE,
                eventType = "FILE_IMPORT_COMPLETE",
                details   = "name=$fileName",
                source    = "FileViewModel"
            )
            _isImporting.value = false
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch {
            fileRepository.deleteFile(id)
            DebugEventBus.log(
                category  = DebugCategory.FILE,
                eventType = "FILE_DELETED",
                details   = "id=$id",
                severity  = DebugSeverity.WARNING,
                source    = "FileViewModel"
            )
        }
    }

    fun moveFileToFolder(fileId: String, targetFolderId: String?) {
        viewModelScope.launch {
            fileRepository.updateFileFolder(fileId, targetFolderId)
            DebugEventBus.log(
                category  = DebugCategory.FILE,
                eventType = "FILE_MOVED",
                details   = "fileId=$fileId, targetFolderId=${targetFolderId ?: "root"}",
                source    = "FileViewModel"
            )
        }
    }

    fun moveFolderToFolder(folderId: String, targetFolderId: String?) {
        if (folderId == targetFolderId || isFolderDescendantOf(folderId, targetFolderId)) {
            return
        }
        viewModelScope.launch {
            fileRepository.updateFolderParent(folderId, targetFolderId)
            DebugEventBus.log(
                category  = DebugCategory.FILE,
                eventType = "FOLDER_MOVED",
                details   = "folderId=$folderId, targetFolderId=${targetFolderId ?: "root"}",
                source    = "FileViewModel"
            )
        }
    }

    fun isFolderDescendantOf(folderId: String, potentialParentId: String?): Boolean {
        if (potentialParentId == null) return false
        if (folderId == potentialParentId) return true
        
        var currentParentId = _folders.value.find { it.id == potentialParentId }?.parentFolderId
        while (currentParentId != null) {
            if (currentParentId == folderId) return true
            currentParentId = _folders.value.find { it.id == currentParentId }?.parentFolderId
        }
        return false
    }

    suspend fun getDecryptedBytes(file: VaultFile): ByteArray? {
        val bos = java.io.ByteArrayOutputStream()
        return if (fileRepository.decryptFileToStream(file, bos).isSuccess) {
            bos.toByteArray()
        } else {
            null
        }
    }

    fun exportFile(file: VaultFile, destinationUri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                fileRepository.decryptFileToStream(file, output)
            }
        }
    }
}
