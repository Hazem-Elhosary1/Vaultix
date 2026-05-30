package com.vaultix.app.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.vaultix.app.ui.components.QRCodeScannerDialog
import com.vaultix.app.ui.theme.VaultOrange
import com.vaultix.app.ui.viewmodel.QRBackupUIState
import com.vaultix.app.ui.viewmodel.QRCodeBackupViewModel

@Composable
fun QRCodeRestoreScreen(
    onRestoreComplete: () -> Unit,
    viewModel: QRCodeBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val uiState by viewModel.uiState.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()
    val totalChunks by viewModel.totalChunksToRestore.collectAsState()
    
    var showScanDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSourceOptions by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanDialog = true
        } else {
            // Show toast or snackbar
            android.widget.Toast.makeText(context, "Camera permission is required to scan QR codes", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-show password dialog after first scan
    LaunchedEffect(restoreProgress.scannedChunks.size) {
        if (restoreProgress.scannedChunks.size == 1 && masterPassword.isEmpty()) {
            showPasswordDialog = true
        }
    }
    
    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val scanner = BarcodeScanning.getClient()
            uris.forEach { uri ->
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.rawBytes?.let { bytes ->
                                viewModel.addScannedQRChunk(bytes)
                            }
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    when (uiState) {
        is QRBackupUIState.Idle, is QRBackupUIState.ScanningProgress -> {
            QRCodeRestoreIdleScreen(
                scannedCount = restoreProgress.scannedChunks.size,
                totalChunks = totalChunks,
                onScanQR = { showSourceOptions = true },
                onRestore = {
                    if (restoreProgress.scannedChunks.isNotEmpty()) {
                        if (masterPassword.isEmpty()) {
                            showPasswordDialog = true
                        } else {
                            viewModel.restoreFromQRCodes(activity, masterPassword)
                        }
                    }
                },
                onClear = { 
                    viewModel.clearScannedChunks()
                    masterPassword = ""
                },
                onCancel = onRestoreComplete
            )
        }
        
        is QRBackupUIState.Restoring -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = VaultOrange)
                    Text("Restoring from QR codes...")
                }
            }
        }
        
        is QRBackupUIState.RestoreSuccess -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(80.dp)
                    )
                    Text(
                        (uiState as QRBackupUIState.RestoreSuccess).message,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onRestoreComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            }
        }
        
        is QRBackupUIState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Error: ${(uiState as QRBackupUIState.Error).message}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = { viewModel.resetError() },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                    ) {
                        Text("Try Again", color = Color.White)
                    }
                }
            }
        }
        
        else -> {}
    }
    
    // Source Selection Dialog
    if (showSourceOptions) {
        AlertDialog(
            onDismissRequest = { showSourceOptions = false },
            title = { Text("Scan QR Code", fontWeight = FontWeight.Bold) },
            text = { Text("Select how you want to add your backup QR codes.") },
            confirmButton = {
                Button(
                    onClick = { 
                        showSourceOptions = false
                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.CAMERA
                        )
                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            showScanDialog = true
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showSourceOptions = false
                        galleryLauncher.launch("image/*") 
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("From Gallery")
                }
            }
        )
    }

    // Password Dialog
    if (showPasswordDialog) {
        var tempPassword by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Master Password Required", color = VaultOrange, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your master password to decrypt the QR backup data.", fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it; errorMsg = null },
                        label = { Text("Master Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempPassword.isBlank()) {
                            errorMsg = "Password is required"
                            return@Button
                        }
                        masterPassword = tempPassword
                        showPasswordDialog = false
                        // If we already have all chunks, we could auto-restore
                        if (restoreProgress.scannedChunks.size == totalChunks && totalChunks > 0) {
                             viewModel.restoreFromQRCodes(activity, masterPassword)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    // QR Scanner Dialog
    if (showScanDialog) {
        QRCodeScannerDialog(
            onQRScanned = { scannedData ->
                viewModel.addScannedQRChunk(scannedData)
                showScanDialog = false // Auto-close after scan
            },
            onDismiss = { showScanDialog = false }
        )
    }
}

@Composable
private fun QRCodeRestoreIdleScreen(
    scannedCount: Int,
    totalChunks: Int,
    onScanQR: () -> Unit,
    onRestore: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "QR Code",
                tint = VaultOrange,
                modifier = Modifier.size(72.dp)
            )
            
            Text(
                "Restore Vault",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        // Progress Card
        if (scannedCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Scanning Progress",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { if (totalChunks > 0) (scannedCount.toFloat() / totalChunks).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.size(100.dp),
                            color = VaultOrange,
                            strokeWidth = 8.dp,
                            trackColor = VaultOrange.copy(alpha = 0.1f),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$scannedCount",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (totalChunks > 0) {
                                Text(
                                    "of $totalChunks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Text(
                        if (totalChunks > 0 && scannedCount == totalChunks) 
                            "All chunks scanned! Ready to restore."
                        else 
                            "scanned $scannedCount of ${if (totalChunks > 0) totalChunks else "..."} chunks",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
             // Instructions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "📖 How to Restore",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "1. Click 'Scan QR Code' to begin adding your backup.\n" +
                        "2. Choose Camera for physical codes or Gallery for saved images.\n" +
                        "3. Enter your master password when prompted.\n" +
                        "4. Once all chunks are scanned, the vault will be restored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action Buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onScanQR,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(12.dp))
                Text("Scan QR Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onRestore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = scannedCount > 0 && (totalChunks == 0 || scannedCount == totalChunks),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.SettingsBackupRestore, null)
                Spacer(Modifier.width(12.dp))
                Text("Restore Vault", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            if (scannedCount > 0) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Progress", color = MaterialTheme.colorScheme.error)
                }
            }
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}
