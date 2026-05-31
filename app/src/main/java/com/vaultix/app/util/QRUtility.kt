package com.vaultix.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.ceil

/**
 * Utility for printing and exporting QR codes as images or PDF.
 * Uses Android PrintManager to display the system print dialog.
 */
class QRUtility(private val context: Context) {

    /**
     * Print all QR codes as a PDF document via the system print dialog.
     */
    fun printQRCodes(qrCodes: List<Bitmap>) {
        if (qrCodes.isEmpty()) return

        // Generate PDF file first
        val pdfFile = generatePdf(qrCodes) ?: return

        // Use PrintManager to show system print dialog
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service not available", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "Vaultix QR Backup"
        printManager.print(jobName, PdfPrintAdapter(pdfFile, jobName), null)
    }

    /**
     * Generates a professional A4 PDF with centered QR codes (2 per page).
     */
    private fun generatePdf(qrCodes: List<Bitmap>): File? {
        val pdfDocument = PdfDocument()

        // A4 size in points (72 dpi): 595 x 842
        val pageWidth = 595
        val pageHeight = 842
        val totalPages = ceil(qrCodes.size / 2.0).toInt()

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1A1A2E")
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val chunkLabelPaint = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val captionPaint = Paint().apply {
            color = Color.parseColor("#777777")
            textSize = 14f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val footerPaint = Paint().apply {
            color = Color.parseColor("#999999")
            textSize = 12f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
            isAntiAlias = true
        }

        val centerX = pageWidth / 2f
        val qrSize = 260

        qrCodes.chunked(2).forEachIndexed { pageIndex, pair ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // White background
            canvas.drawColor(Color.WHITE)

            // Page title
            canvas.drawText("Vaultix Secure QR Backup", centerX, 50f, titlePaint)

            // Divider line
            canvas.drawLine(50f, 70f, pageWidth - 50f, 70f, linePaint)

            // First QR code
            val qr1 = pair[0]
            val globalIndex1 = pageIndex * 2 + 1
            val scaledQR1 = Bitmap.createScaledBitmap(qr1, qrSize, qrSize, true)
            val qr1X = (pageWidth - qrSize) / 2f
            val qr1Y = 100f
            canvas.drawBitmap(scaledQR1, qr1X, qr1Y, Paint().apply { isAntiAlias = true })
            canvas.drawText("Chunk $globalIndex1 of ${qrCodes.size}", centerX, qr1Y + qrSize + 25f, chunkLabelPaint)
            canvas.drawText("Scan this QR code in Vaultix to restore", centerX, qr1Y + qrSize + 45f, captionPaint)

            // Second QR code (if exists)
            if (pair.size > 1) {
                val qr2 = pair[1]
                val globalIndex2 = pageIndex * 2 + 2
                val scaledQR2 = Bitmap.createScaledBitmap(qr2, qrSize, qrSize, true)
                val qr2Y = 430f
                canvas.drawBitmap(scaledQR2, qr1X, qr2Y, Paint().apply { isAntiAlias = true })
                canvas.drawText("Chunk $globalIndex2 of ${qrCodes.size}", centerX, qr2Y + qrSize + 25f, chunkLabelPaint)
                canvas.drawText("Scan this QR code in Vaultix to restore", centerX, qr2Y + qrSize + 45f, captionPaint)
            }

            // Footer
            canvas.drawLine(50f, pageHeight - 35f, pageWidth - 50f, pageHeight - 35f, linePaint)
            canvas.drawText(
                "Vaultix Secure Offline Vault Manager — Page ${pageIndex + 1} of $totalPages",
                centerX,
                pageHeight - 18f,
                footerPaint
            )

            pdfDocument.finishPage(page)
        }

        val file = File(context.cacheDir, "vaultix_qr_backup.pdf")
        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            Toast.makeText(context, "Failed to generate PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    /**
     * Custom PrintDocumentAdapter that streams a pre-generated PDF file to the system print spooler.
     */
    private class PdfPrintAdapter(
        private val pdfFile: File,
        private val jobName: String
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }

            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()

            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }

                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                } else {
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                }
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
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
