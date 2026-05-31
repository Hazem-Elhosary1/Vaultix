package com.vaultix.app.util

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

object DocumentScannerHelper {

    /**
     * Create scanning options for different scanning targets.
     * @param pageLimit Maximum number of pages to scan (0 for unlimited/default)
     * @param galleryImportAllowed Allow selecting images from gallery within scanner
     * @param isPdfEnabled Whether PDF format output should also be generated
     */
    fun createScannerOptions(
        pageLimit: Int = 1,
        galleryImportAllowed: Boolean = true,
        isPdfEnabled: Boolean = false
    ): GmsDocumentScannerOptions {
        val builder = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(galleryImportAllowed)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)

        if (pageLimit > 0) {
            builder.setPageLimit(pageLimit)
        }

        if (isPdfEnabled) {
            builder.setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
        } else {
            builder.setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        }

        return builder.build()
    }

    /**
     * Get the StartScanIntent Sender from ML Kit Client
     */
    fun startScan(
        context: Context,
        options: GmsDocumentScannerOptions,
        onIntentSenderReady: (IntentSender) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val activity = context as? Activity ?: findActivity(context)
        if (activity == null) {
            onFailure(Exception("Context is not an Activity and could not find Activity context"))
            return
        }

        val client = GmsDocumentScanning.getClient(options)
        client.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                onIntentSenderReady(intentSender)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    /**
     * Utility to extract Activity from ContextWrapper if needed (like in Compose preview or inside some wrappers)
     */
    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }
}
