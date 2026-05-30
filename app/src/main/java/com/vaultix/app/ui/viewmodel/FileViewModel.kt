package com.vaultix.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.data.model.VaultFile
import com.vaultix.app.data.repository.FileRepository
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
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            fileRepository.deleteFolder(id)
        }
    }

    fun importFile(uri: Uri, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _isImporting.value = true
            fileRepository.importFile(uri, fileName, mimeType, _currentFolderId.value)
            _isImporting.value = false
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch {
            fileRepository.deleteFile(id)
        }
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
