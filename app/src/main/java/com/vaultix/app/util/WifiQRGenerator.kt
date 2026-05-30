package com.vaultix.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Utility to generate standard offline Wi-Fi connection QR codes.
 * Format: WIFI:S:SSID;T:WPA;P:PASSWORD;;
 */
object WifiQRGenerator {

    private const val QR_CODE_SIZE = 512

    fun generate(ssid: String, password: String, securityType: String): Bitmap? {
        if (ssid.isBlank()) return null

        // Map security type to standard format
        // Security types: "WPA/WPA2/WPA3", "WEP", "Open"
        val mappedType = when (securityType.uppercase()) {
            "WPA/WPA2/WPA3", "WPA", "WPA2", "WPA3" -> "WPA"
            "WEP" -> "WEP"
            "OPEN", "NOPASS" -> "nopass"
            else -> "WPA"
        }

        val escapedSsid = escape(ssid)
        val escapedPassword = if (mappedType == "nopass") "" else escape(password)

        // Build connection string
        val qrContent = if (mappedType == "nopass") {
            "WIFI:S:$escapedSsid;T:nopass;;"
        } else {
            "WIFI:S:$escapedSsid;T:$mappedType;P:$escapedPassword;;"
        }

        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                qrContent,
                BarcodeFormat.QR_CODE,
                QR_CODE_SIZE,
                QR_CODE_SIZE,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun escape(str: String): String {
        return str.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")
    }
}
