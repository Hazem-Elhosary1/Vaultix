package com.vaultix.app.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vaultix.app.ui.theme.VaultOrange
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun QRCodeScannerDialog(
    onQRScanned: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    scannedCount: Int = 0,
    totalChunks: Int = 0
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreview(
                    onBarcodeDetected = { barcode ->
                        val rawBytes = barcode.rawBytes
                        if (rawBytes != null) {
                            // Vibrate for feedback
                            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            
                            onQRScanned(rawBytes)
                        } else {
                            // Try to decode from string if raw bytes are null
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                try {
                                    val bytes = android.util.Base64.decode(rawValue, android.util.Base64.NO_WRAP)
                                    onQRScanned(bytes)
                                } catch (e: Exception) {
                                    Log.e("QRScanner", "Failed to decode base64: ${e.message}")
                                }
                            }
                        }
                    }
                )

                // Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top section with title and progress
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Scan Backup QR Code",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Progress indicator
                        if (totalChunks > 0 || scannedCount > 0) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Black.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (scannedCount > 0 && scannedCount == totalChunks) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            progress = { if (totalChunks > 0) scannedCount.toFloat() / totalChunks else 0f },
                                            modifier = Modifier.size(20.dp),
                                            color = VaultOrange,
                                            strokeWidth = 3.dp,
                                            trackColor = Color.White.copy(alpha = 0.2f)
                                        )
                                    }
                                    Text(
                                        if (totalChunks > 0) "Scanned $scannedCount of $totalChunks chunks"
                                        else "Scanned $scannedCount chunk(s)",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    
                    // Center frame indicator
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .padding(2.dp)
                            .background(Color.Transparent)
                            .border(2.dp, VaultOrange.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        // Corner indicators
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = VaultOrange,
                            modifier = Modifier.fillMaxSize().padding(80.dp).alpha(0.1f)
                        )
                    }

                    // Bottom section
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (scannedCount > 0 && totalChunks > 0 && scannedCount < totalChunks) {
                            Text(
                                "Point at the next QR code",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPreview(
    onBarcodeDetected: (Barcode) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    var lastScannedTime by remember { mutableLongStateOf(0L) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(1280, 720))
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (barcodes.isNotEmpty()) {
                                    for (barcode in barcodes) {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastScannedTime > 500) {
                                            lastScannedTime = currentTime
                                            onBarcodeDetected(barcode)
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.close()
        }
    }
}
