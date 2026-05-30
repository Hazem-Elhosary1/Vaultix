package com.vaultix.app.util

import android.graphics.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Generates actual QR code bitmaps from encrypted backup data using ZXing.
 * Chunks data to fit within QR code capacity limits.
 */
@Singleton
class QRCodeBackupGenerator @Inject constructor() {
    
    companion object {
        private const val MAX_BYTES_PER_QR = 1000 // Safer limit for scanning from screens
        private const val QR_CODE_SIZE = 512
        private const val HEADER_HEIGHT = 80
        private const val FOOTER_HEIGHT = 50
        private const val TOTAL_HEIGHT = QR_CODE_SIZE + HEADER_HEIGHT + FOOTER_HEIGHT
        
        // Header Signature for Vaultix QR codes
        private val SIGNATURE = byteArrayOf('V'.toByte(), 'L'.toByte(), 'X'.toByte())
        private const val HEADER_SIZE = 16 // 3 (Signature) + 1 (Version) + 4 (Total) + 4 (Index) + 4 (Size)
    }
    
    fun generateQRCodesFromBackup(encryptedBackupData: ByteArray, logo: Bitmap? = null): List<Bitmap> {
        val compressedData = compressData(encryptedBackupData)
        val chunks = splitIntoChunks(compressedData)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return chunks.mapIndexed { index, chunkData -> 
            generateQRBitmap(chunkData, index + 1, chunks.size, timestamp, logo) 
        }
    }
    
    fun reconstructBackupFromQRChunks(qrDataChunks: List<ByteArray>): ByteArray {
        if (qrDataChunks.isEmpty()) {
            throw IllegalArgumentException("No QR code data provided")
        }
        val mergedData = mergeChunks(qrDataChunks)
        return decompressData(mergedData)
    }
    
    private fun compressData(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        
        val output = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            output.write(buffer, 0, count)
        }
        deflater.end()
        
        return output.toByteArray()
    }
    
    private fun decompressData(compressedData: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressedData)
        
        val output = ByteArrayOutputStream(compressedData.size)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            try {
                val count = inflater.inflate(buffer)
                output.write(buffer, 0, count)
            } catch (e: Exception) {
                break
            }
        }
        inflater.end()
        
        return output.toByteArray()
    }
    
    private fun splitIntoChunks(compressedData: ByteArray): List<ByteArray> {
        val totalChunks = ceil(compressedData.size.toDouble() / MAX_BYTES_PER_QR).toInt()
        val chunks = mutableListOf<ByteArray>()
        
        for (i in 0 until totalChunks) {
            val startIdx = i * MAX_BYTES_PER_QR
            val endIdx = minOf((i + 1) * MAX_BYTES_PER_QR, compressedData.size)
            val chunkData = compressedData.sliceArray(startIdx until endIdx)
            
            val header = buildChunkHeader(i, totalChunks, chunkData.size)
            val fullChunk = header + chunkData
            chunks.add(fullChunk)
        }
        
        return chunks
    }
    
    private fun buildChunkHeader(index: Int, total: Int, size: Int): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        
        // 0-2: Signature "VLX"
        header[0] = SIGNATURE[0]
        header[1] = SIGNATURE[1]
        header[2] = SIGNATURE[2]
        
        // 3: Version
        header[3] = 1 
        
        // 4-7: Total Chunks
        header[4] = (total shr 24).toByte()
        header[5] = (total shr 16).toByte()
        header[6] = (total shr 8).toByte()
        header[7] = total.toByte()
        
        // 8-11: Chunk Index
        header[8] = (index shr 24).toByte()
        header[9] = (index shr 16).toByte()
        header[10] = (index shr 8).toByte()
        header[11] = index.toByte()
        
        // 12-15: Chunk Size
        header[12] = (size shr 24).toByte()
        header[13] = (size shr 16).toByte()
        header[14] = (size shr 8).toByte()
        header[15] = size.toByte()
        
        return header
    }
    
    fun parseChunkHeader(chunk: ByteArray): ChunkMetadata {
        // Handle Base64 encoding
        val data = try {
            android.util.Base64.decode(chunk, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            chunk
        }

        if (data.size < HEADER_SIZE) {
            throw IllegalArgumentException("Invalid QR code: Data is too small to be a Vaultix backup")
        }
        
        // Validate Signature "VLX"
        if (data[0] != SIGNATURE[0] || data[1] != SIGNATURE[1] || data[2] != SIGNATURE[2]) {
            throw IllegalArgumentException("Not a valid Vaultix QR code")
        }
        
        val version = data[3].toInt()
        val totalChunks = ((data[4].toInt() and 0xFF) shl 24) or
                         ((data[5].toInt() and 0xFF) shl 16) or
                         ((data[6].toInt() and 0xFF) shl 8) or
                         (data[7].toInt() and 0xFF)
        
        val chunkIndex = ((data[8].toInt() and 0xFF) shl 24) or
                        ((data[9].toInt() and 0xFF) shl 16) or
                        ((data[10].toInt() and 0xFF) shl 8) or
                        (data[11].toInt() and 0xFF)
        
        val chunkSize = ((data[12].toInt() and 0xFF) shl 24) or
                       ((data[13].toInt() and 0xFF) shl 16) or
                       ((data[14].toInt() and 0xFF) shl 8) or
                       (data[15].toInt() and 0xFF)
        
        // Sanity checks
        if (totalChunks <= 0 || totalChunks > 10000 || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw IllegalArgumentException("Invalid QR code metadata")
        }
        
        return ChunkMetadata(version, totalChunks, chunkIndex, chunkSize)
    }

    private fun mergeChunks(chunks: List<ByteArray>): ByteArray {
        if (chunks.isEmpty()) throw IllegalArgumentException("No chunks provided")
        
        val decodedChunks = chunks.map { chunk ->
            try {
                android.util.Base64.decode(chunk, android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                chunk
            }
        }

        val sortedChunks = decodedChunks.sortedBy { parseChunkHeader(it).chunkIndex }
        val firstMeta = parseChunkHeader(sortedChunks[0])
        
        if (sortedChunks.size != firstMeta.totalChunks) {
            throw IllegalArgumentException("Incomplete backup: got ${sortedChunks.size}/${firstMeta.totalChunks} chunks")
        }
        
        val mergedBuffer = ByteArrayOutputStream()
        sortedChunks.forEachIndexed { i, chunk ->
            val meta = parseChunkHeader(chunk)
            if (meta.chunkIndex != i) throw IllegalArgumentException("Missing chunk at index $i")
            mergedBuffer.write(chunk.sliceArray(HEADER_SIZE until chunk.size))
        }
        
        return mergedBuffer.toByteArray()
    }

    private fun generateQRBitmap(
        chunkData: ByteArray, 
        current: Int, 
        total: Int, 
        timestamp: String,
        logo: Bitmap? = null
    ): Bitmap {
        // Use Base64 for safety
        val base64Data = android.util.Base64.encodeToString(chunkData, android.util.Base64.NO_WRAP)
        
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            base64Data,
            BarcodeFormat.QR_CODE,
            QR_CODE_SIZE,
            QR_CODE_SIZE,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
        )
        
        val bitmap = Bitmap.createBitmap(QR_CODE_SIZE, TOTAL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        
        // Header Text
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        canvas.drawText("Vaultix Secure Backup", QR_CODE_SIZE / 2f, 40f, textPaint)
        
        textPaint.textSize = 20f
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText("Chunk $current of $total", QR_CODE_SIZE / 2f, 70f, textPaint)
        
        // QR Code
        val pixels = IntArray(QR_CODE_SIZE * QR_CODE_SIZE)
        for (y in 0 until QR_CODE_SIZE) {
            for (x in 0 until QR_CODE_SIZE) {
                pixels[y * QR_CODE_SIZE + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, QR_CODE_SIZE, 0, HEADER_HEIGHT, QR_CODE_SIZE, QR_CODE_SIZE)

        // Footer Text
        textPaint.textSize = 16f
        textPaint.color = Color.DKGRAY
        canvas.drawText("Generated on: $timestamp", QR_CODE_SIZE / 2f, (TOTAL_HEIGHT - 20).toFloat(), textPaint)

        // Logo
        if (logo != null) {
            val logoSize = QR_CODE_SIZE / 5
            val x = (QR_CODE_SIZE - logoSize) / 2f
            val y = HEADER_HEIGHT + (QR_CODE_SIZE - logoSize) / 2f
            
            val bgPaint = Paint().apply { 
                color = Color.WHITE
                isAntiAlias = true 
            }
            canvas.drawCircle(QR_CODE_SIZE / 2f, HEADER_HEIGHT + QR_CODE_SIZE / 2f, (logoSize / 2f) + 10, bgPaint)
            
            val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
            canvas.drawBitmap(scaledLogo, x, y, Paint().apply { isAntiAlias = true })
        }

        return bitmap
    }
    
    data class ChunkMetadata(
        val version: Int,
        val totalChunks: Int,
        val chunkIndex: Int,
        val chunkSize: Int
    )
}
