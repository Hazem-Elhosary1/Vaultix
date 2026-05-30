package com.vaultix.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.print.PrintHelper
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Utility for printing and exporting QR codes as images or PDF
 */
class QRUtility(private val context: Context) {

    /**
     * Print all QR codes as a PDF document
     */
    fun printQRCodes(qrCodes: List<Bitmap>) {
        if (qrCodes.isEmpty()) return

        val printHelper = PrintHelper(context)
        printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
        
        // If only one QR, print it directly
        if (qrCodes.size == 1) {
            printHelper.printBitmap("Vaultix QR Backup", qrCodes[0])
            return
        }

        // For multiple QR codes, create a combined document or a PDF
        // Here we simplify by printing them one by one if requested, 
        // but a PDF is better for "Print All".
        // For this implementation, we'll generate a PDF and share/print it.
        generateAndPrintPdf(qrCodes)
    }

    private fun generateAndPrintPdf(qrCodes: List<Bitmap>) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 36f
            isFakeBoldText = true
        }
        val subTitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 24f
        }

        // A4 size in points (72 dpi): 595 x 842
        val pageWidth = 595
        val pageHeight = 842
        
        qrCodes.chunked(2).forEachIndexed { pageIndex, pair ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            canvas.drawText("Vaultix QR Backup - Page ${pageIndex + 1}", 50f, 80f, titlePaint)
            
            pair.forEachIndexed { index, bitmap ->
                val yOffset = 150f + (index * 350f)
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true)
                
                canvas.drawBitmap(scaledBitmap, (pageWidth - 300) / 2f, yOffset, paint)
                canvas.drawText("Chunk ${pageIndex * 2 + index + 1} of ${qrCodes.size}", (pageWidth - 150) / 2f, yOffset + 330f, subTitlePaint)
            }

            pdfDocument.finishPage(page)
        }

        val file = File(context.cacheDir, "vaultix_qr_backup.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            // In a real app, we would use a FileProvider to share/print the PDF.
            // For now, since PrintHelper only takes Bitmaps, we'll notify the user 
            // or use a generic print manager.
            Toast.makeText(context, "PDF generated. Use 'Export' to save or share.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to generate PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Save all QR codes to the gallery
     */
    fun saveQRCodesToGallery(qrCodes: List<Bitmap>) {
        var successCount = 0
        qrCodes.forEachIndexed { index, bitmap ->
            val fileName = "Vaultix_QR_Chunk_${index + 1}_${System.currentTimeMillis()}.png"
            if (saveBitmapToGallery(bitmap, fileName)) {
                successCount++
            }
        }
        
        if (successCount == qrCodes.size) {
            Toast.makeText(context, "All $successCount images saved to Gallery", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Saved $successCount of ${qrCodes.size} images", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, fileName: String): Boolean {
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vaultix")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        return try {
            val uri = context.contentResolver.insert(imageCollection, contentValues)
            uri?.let {
                context.contentResolver.openOutputStream(it).use { outputStream ->
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
