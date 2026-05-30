package com.vaultix.app.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val cryptoManager: CryptoManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImageViewerUiState>(ImageViewerUiState.Loading)
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    fun loadImage(imagePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (!file.exists()) {
                    _uiState.value = ImageViewerUiState.Error("Image file not found")
                    return@launch
                }

                val key = KeystoreManager.getOrCreateFilesKey()
                val encryptedBytes = file.readBytes()
                val decryptedBytes = cryptoManager.decryptBytes(encryptedBytes, key)
                
                val bitmap = BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                
                if (bitmap != null) {
                    _uiState.value = ImageViewerUiState.Success(bitmap)
                } else {
                    _uiState.value = ImageViewerUiState.Error("Failed to decode image")
                }

                // Zeroize decrypted bytes from memory
                decryptedBytes.fill(0)
            } catch (e: Exception) {
                _uiState.value = ImageViewerUiState.Error("Failed to decrypt image: ${e.message}")
            }
        }
    }
}

sealed class ImageViewerUiState {
    object Loading : ImageViewerUiState()
    data class Success(val bitmap: Bitmap) : ImageViewerUiState()
    data class Error(val message: String) : ImageViewerUiState()
}
