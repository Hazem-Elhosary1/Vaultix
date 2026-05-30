package com.vaultix.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.appcompat.content.res.AppCompatResources
import com.vaultix.app.ui.viewmodel.QRBackupUIState
import com.vaultix.app.ui.viewmodel.QRCodeBackupViewModel

@Composable
fun QRCodeBackupScreen(
    masterPassword: String,
    onBackupComplete: () -> Unit,
    viewModel: QRCodeBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val qrCodes by viewModel.qrCodes.collectAsState()
    
    LaunchedEffect(Unit) {
        val logo = AppCompatResources.getDrawable(context, com.vaultix.app.R.mipmap.ic_launcher)?.toBitmap()
        viewModel.generateQRCodesFromBackup(masterPassword, logo)
    }
    
    when (uiState) {
        is QRBackupUIState.Idle -> {
            // Initial state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        is QRBackupUIState.Generating -> {
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
                    CircularProgressIndicator()
                    Text("Generating QR codes...")
                }
            }
        }
        
        is QRBackupUIState.Success -> {
            QRCodeBackupSuccessScreen(
                qrCodes = qrCodes,
                totalChunks = (uiState as QRBackupUIState.Success).totalChunks,
                viewModel = viewModel,
                onBackupComplete = onBackupComplete
            )
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
                    Text(
                        "Error: ${(uiState as QRBackupUIState.Error).message}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { onBackupComplete() }) {
                        Text("Back")
                    }
                }
            }
        }
        
        else -> {}
    }
}

@Composable
private fun QRCodeBackupSuccessScreen(
    qrCodes: List<android.graphics.Bitmap>,
    totalChunks: Int,
    viewModel: QRCodeBackupViewModel,
    onBackupComplete: () -> Unit
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
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Success",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                "Backup Created Successfully",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                "Generated $totalChunks QR code(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        // Information Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "📌 Important: Store QR Codes Safely",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "• Print all QR codes and store in a safe location\n" +
                    "• Keep copies separate from your devices\n" +
                    "• Each QR code is an encrypted chunk\n" +
                    "• You need ALL chunks to restore your vault",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // QR Codes Display
        Text(
            "Backup QR Codes",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            qrCodes.forEachIndexed { index, bitmap ->
                QRCodeCard(
                    bitmap = bitmap,
                    chunkNumber = index + 1,
                    totalChunks = totalChunks
                )
            }
        }
        
        // Action Buttons
        val context = LocalContext.current
        val qrUtility = remember { com.vaultix.app.util.QRUtility(context) }
        val isExporting by viewModel.isExporting.collectAsState()
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Button(
                onClick = { qrUtility.printQRCodes(qrCodes) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Print,
                    contentDescription = "Print",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Print All QR Codes")
            }
            
            Button(
                onClick = { 
                    viewModel.setExporting(true)
                    qrUtility.saveQRCodesToGallery(qrCodes)
                    viewModel.setExporting(false)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isExporting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSecondary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isExporting) "Exporting..." else "Export as Images")
            }
            
            OutlinedButton(
                onClick = onBackupComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun QRCodeCard(
    bitmap: android.graphics.Bitmap,
    chunkNumber: Int,
    totalChunks: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chunk label
            Text(
                "Chunk $chunkNumber of $totalChunks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            // QR Code Image
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = "QR Code Chunk $chunkNumber",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(250.dp)
                    .background(androidx.compose.ui.graphics.Color(android.graphics.Color.WHITE))
                    .padding(8.dp)
            )
            
            // Note
            Text(
                "Keep this QR code safe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
