package com.vaultix.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.security.CryptoManager
import com.vaultix.app.security.KeystoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val fileRepository: com.vaultix.app.data.repository.FileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Loading)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var tempFile: File? = null

    init {
        // Clean up any leaked temp files from previous sessions
        try {
            File(context.cacheDir, "temp_preview.pdf").delete()
        } catch (e: Exception) { }
    }

    fun loadPdf(fileId: String, fileName: String) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading
            try {
                val vaultFile = fileRepository.getFileById(fileId) ?: throw Exception("File record not found in DB")
                val filePath = vaultFile.encryptedFilePath

                withContext(Dispatchers.IO) {
                    val encryptedFile = File(filePath)
                    if (!encryptedFile.exists()) throw Exception("Encrypted file not found on disk")

                    // 1. Decrypt to a temporary file in internal cache
                    val decryptedBytes = cryptoManager.decryptBytes(
                        encryptedFile.readBytes(),
                        KeystoreManager.getOrCreateFilesKey()
                    )

                    tempFile = File(context.cacheDir, "temp_preview.pdf").apply {
                        deleteOnExit()
                        FileOutputStream(this).use { it.write(decryptedBytes) }
                    }

                    // 2. Initialize PdfRenderer
                    fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(fileDescriptor!!)

                    val pageCount = pdfRenderer?.pageCount ?: 0
                    _uiState.value = PdfUiState.Success(fileName, pageCount)
                }
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.message ?: "Failed to load PDF")
            }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val page = pdfRenderer?.openPage(pageIndex)
            val bitmap = page?.let {
                val b = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
                it.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                it.close()
                b
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeRenderer()
    }

    fun closeRenderer() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            tempFile?.delete()
        } catch (e: Exception) {
            // Ignore
        }
    }
}

sealed class PdfUiState {
    object Loading : PdfUiState()
    data class Success(val fileName: String, val pageCount: Int) : PdfUiState()
    data class Error(val message: String) : PdfUiState()
}
