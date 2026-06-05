package com.vaultix.app.ui.screens

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.BackupViewModel
import androidx.compose.ui.res.stringResource
import com.vaultix.app.R
import com.vaultix.app.ui.viewmodel.QRCodeBackupViewModel
import com.vaultix.app.ui.viewmodel.QRBackupUIState
import com.vaultix.app.ui.components.QRCodeScannerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.DialogProperties

private enum class ImportSource {
    NONE, FILE, QR_SCAN, HISTORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupImportScreen(
    onBack: () -> Unit,
    backupViewModel: BackupViewModel = hiltViewModel(),
    qrBackupViewModel: QRCodeBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val qrState by qrBackupViewModel.uiState.collectAsStateWithLifecycle()
    val restoreProgress by qrBackupViewModel.restoreProgress.collectAsStateWithLifecycle()
    val totalChunks by qrBackupViewModel.totalChunksToRestore.collectAsStateWithLifecycle()

    var currentStep by remember { mutableIntStateOf(0) }
    var selectedSource by remember { mutableStateOf(ImportSource.NONE) }

    // File import state
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    // QR scan state
    var showQRScanner by remember { mutableStateOf(false) }

    // History state
    var selectedHistoryFile by remember { mutableStateOf<File?>(null) }

    // Password & restore state
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreDone by remember { mutableStateOf(false) }
    var restoreResultMessage by remember { mutableStateOf("") }

    // SAF file picker
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingUri = it
            selectedSource = ImportSource.FILE
            currentStep = 1
        }
    }

    var isProcessingFile by remember { mutableStateOf(false) }

    val importPdfOrImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            isProcessingFile = true
            processPdfOrImageBackup(
                context = context,
                uri = it,
                qrBackupViewModel = qrBackupViewModel,
                scope = scope,
                onSuccess = {
                    isProcessingFile = false
                    selectedSource = ImportSource.QR_SCAN
                    currentStep = 1
                },
                onError = { errMsg ->
                    isProcessingFile = false
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Load local backups
    LaunchedEffect(Unit) {
        backupViewModel.clearResults()
        backupViewModel.refreshLocalBackups()
        qrBackupViewModel.clearScannedChunks()
    }

    // Handle backup state messages
    LaunchedEffect(backupState.message) {
        backupState.message?.let { msg ->
            if (isRestoring) {
                restoreDone = true
                restoreResultMessage = msg
                isRestoring = false
                backupViewModel.clearMessage()
            }
        }
    }
    LaunchedEffect(backupState.error) {
        backupState.error?.let { err ->
            if (isRestoring) {
                restoreDone = true
                restoreResultMessage = context.getString(R.string.restore_failed_error, err)
                isRestoring = false
                backupViewModel.clearMessage()
            }
        }
    }

    // QR Scanner Dialog
    if (showQRScanner) {
        QRCodeScannerDialog(
            onQRScanned = { data ->
                qrBackupViewModel.addScannedQRChunk(data)
            },
            scannedCount = restoreProgress.scannedChunks.size,
            totalChunks = totalChunks,
            onDismiss = {
                showQRScanner = false
                if (restoreProgress.scannedChunks.isNotEmpty()) {
                    selectedSource = ImportSource.QR_SCAN
                    currentStep = 1
                }
            }
        )
    }

    if (isProcessingFile) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.reading_pdf_qr_image), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_data), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // Step indicators
            ImportStepIndicator(currentStep = currentStep, totalSteps = 2)

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { if (targetState > initialState) it else -it } + fadeIn() togetherWith
                    slideOutHorizontally { if (targetState > initialState) -it else it } + fadeOut()
                },
                label = "import_step_transition"
            ) { step ->
                when (step) {
                    0 -> ImportStep1_ChooseSource(
                        localBackups = backupState.localBackups,
                        onPickFile = {
                            importFileLauncher.launch(arrayOf("*/*"))
                        },
                        onScanQR = {
                            showQRScanner = true
                        },
                        onPickPdfOrImage = {
                            importPdfOrImageLauncher.launch(arrayOf("application/pdf", "image/*"))
                        },
                        onSelectHistory = { file ->
                            selectedHistoryFile = file
                            selectedSource = ImportSource.HISTORY
                            currentStep = 1
                        }
                    )
                    1 -> ImportStep2_PasswordAndRestore(
                        password = password,
                        onPasswordChanged = { password = it },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        isRestoring = isRestoring,
                        restoreDone = restoreDone,
                        restoreResultMessage = restoreResultMessage,
                        importProgress = backupState.importProgress,
                        selectedSource = selectedSource,
                        scannedChunks = restoreProgress.scannedChunks.size,
                        totalChunks = totalChunks,
                        isHistoryRestore = selectedSource == ImportSource.HISTORY,
                        onBack = {
                            currentStep = 0
                            password = ""
                            restoreDone = false
                        },
                        onRestore = {
                            isRestoring = true
                            when (selectedSource) {
                                ImportSource.FILE -> {
                                    pendingUri?.let { uri ->
                                        backupViewModel.importBackup(uri, password.toCharArray())
                                        scope.launch {
                                            backupViewModel.updateImportProgress(0.2f)
                                            delay(500)
                                            backupViewModel.updateImportProgress(0.5f)
                                            delay(500)
                                            backupViewModel.updateImportProgress(0.8f)
                                        }
                                    }
                                }
                                ImportSource.QR_SCAN -> {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        qrBackupViewModel.restoreFromQRCodes(activity, password)
                                        scope.launch {
                                            backupViewModel.updateImportProgress(0.3f)
                                            delay(600)
                                            backupViewModel.updateImportProgress(0.6f)
                                            delay(600)
                                            backupViewModel.updateImportProgress(0.9f)
                                            // Wait for QR restore to complete
                                            while (qrBackupViewModel.uiState.value is QRBackupUIState.Restoring) {
                                                delay(200)
                                            }
                                            val state = qrBackupViewModel.uiState.value
                                            backupViewModel.updateImportProgress(1f)
                                            restoreDone = true
                                            restoreResultMessage = when (state) {
                                                is QRBackupUIState.RestoreSuccess -> state.message
                                                is QRBackupUIState.Error -> context.getString(R.string.restore_failed_error, state.message)
                                                else -> context.getString(R.string.restore_complete)
                                            }
                                            isRestoring = false
                                        }
                                    }
                                }
                                ImportSource.HISTORY -> {
                                    selectedHistoryFile?.let { file ->
                                        backupViewModel.restoreLocalBackup(file)
                                        scope.launch {
                                            backupViewModel.updateImportProgress(0.3f)
                                            delay(500)
                                            backupViewModel.updateImportProgress(0.7f)
                                        }
                                    }
                                }
                                ImportSource.NONE -> {}
                            }
                        },
                        onDone = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportStepIndicator(currentStep: Int, totalSteps: Int) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i <= currentStep
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isActive) primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (i < currentStep) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        "${i + 1}",
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            if (i < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .background(
                            if (i < currentStep) primary else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun ImportStep1_ChooseSource(
    localBackups: List<File>,
    onPickFile: () -> Unit,
    onScanQR: () -> Unit,
    onPickPdfOrImage: () -> Unit,
    onSelectHistory: (File) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.choose_import_source), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(R.string.select_import_source_desc), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // .VBK File option
        ImportSourceCard(
            icon = Icons.Default.Description,
            title = stringResource(R.string.import_format_vbk_title),
            subtitle = stringResource(R.string.import_format_vbk_desc),
            onClick = onPickFile
        )

        // Pick PDF/Image option
        ImportSourceCard(
            icon = Icons.Default.PictureAsPdf,
            title = stringResource(R.string.import_format_pdf_image_title),
            subtitle = stringResource(R.string.import_format_pdf_image_desc),
            onClick = onPickPdfOrImage
        )

        // QR Code scan option
        ImportSourceCard(
            icon = Icons.Default.QrCodeScanner,
            title = stringResource(R.string.scan_qr_codes),
            subtitle = stringResource(R.string.scan_qr_codes_desc),
            onClick = onScanQR
        )

        // History restore
        if (localBackups.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text(stringResource(R.string.backup_history_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.restore_local_backup_desc), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(4.dp))

            localBackups.take(5).forEach { file ->
                val dateStr = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.ENGLISH).format(Date(file.lastModified()))
                val sizeKb = file.length() / 1024
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectHistory(file) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.History, null, tint = primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dateStr, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.kb_format, sizeKb), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ImportSourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = primary)
        }
    }
}

@Composable
private fun ImportStep2_PasswordAndRestore(
    password: String,
    onPasswordChanged: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    isRestoring: Boolean,
    restoreDone: Boolean,
    restoreResultMessage: String,
    importProgress: Float,
    selectedSource: ImportSource,
    scannedChunks: Int,
    totalChunks: Int,
    isHistoryRestore: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onDone: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = importProgress,
        animationSpec = tween(durationMillis = 400),
        label = "import_progress"
    )
    val primary = MaterialTheme.colorScheme.primary
    val isSuccess = restoreDone && !restoreResultMessage.contains(stringResource(R.string.restore_failed), ignoreCase = true)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (restoreDone) {
            // Result card
            Spacer(Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess) VaultSuccess.copy(alpha = 0.1f) else VaultError.copy(alpha = 0.1f)
                ),
                border = BorderStroke(1.dp, if (isSuccess) VaultSuccess.copy(alpha = 0.3f) else VaultError.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                if (isSuccess) VaultSuccess.copy(alpha = 0.15f) else VaultError.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (isSuccess) VaultSuccess else VaultError,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                     Text(
                        if (isSuccess) stringResource(R.string.restore_complete) else stringResource(R.string.restore_failed),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        restoreResultMessage,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Text(stringResource(R.string.done), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        } else if (isRestoring) {
            // Restoring progress
            Spacer(Modifier.height(48.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.restoring), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${(animatedProgress * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // Password entry
            val sourceLabel = when (selectedSource) {
                ImportSource.FILE -> stringResource(R.string.import_source_file)
                ImportSource.QR_SCAN -> stringResource(R.string.import_source_qr_chunks, scannedChunks, totalChunks)
                ImportSource.HISTORY -> stringResource(R.string.import_source_history)
                ImportSource.NONE -> ""
            }

            Text(stringResource(R.string.decrypt_restore), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.decrypt_restore_desc), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.source_label, sourceLabel), fontSize = 13.sp, color = primary, fontWeight = FontWeight.Medium)
                }
            }

            if (!isHistoryRestore) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisible) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    isError = password.isNotEmpty() && password.length < 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        focusedLabelColor = primary,
                        cursorColor = primary
                    )
                )
                if (password.isNotEmpty() && password.length < 6) {
                    Text(stringResource(R.string.password_min_length_backup), color = VaultError, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.back))
                }
                Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    enabled = isHistoryRestore || password.length >= 6
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.restore_button), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

fun processPdfOrImageBackup(
    context: android.content.Context,
    uri: Uri,
    qrBackupViewModel: QRCodeBackupViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            val isPdf = mimeType?.contains("pdf", ignoreCase = true) == true || 
                        uri.path?.contains(".pdf", ignoreCase = true) == true
            
            val scannedChunks = mutableListOf<ByteArray>()
            val scannerOptions = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(scannerOptions)
            
            // Clear any previously scanned chunks first
            qrBackupViewModel.clearScannedChunks()
            
            if (isPdf) {
                // Parse PDF file
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val pdfRenderer = PdfRenderer(pfd)
                    val pageCount = pdfRenderer.pageCount
                    
                    for (i in 0 until pageCount) {
                        val page = pdfRenderer.openPage(i)
                        // Render page to bitmap at high resolution (e.g. 1500 x 2000)
                        val width = 1500
                        val height = 2000
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        
                        // Fill canvas with white color before rendering
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        
                        // Scan rendered bitmap for QR codes
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        
                        // We use Tasks.await to wait for task completion
                        val barcodes = com.google.android.gms.tasks.Tasks.await(scanner.process(inputImage))
                        for (barcode in barcodes) {
                            val rawBytes = barcode.rawBytes
                            if (rawBytes != null) {
                                scannedChunks.add(rawBytes)
                            } else {
                                val rawValue = barcode.rawValue
                                if (rawValue != null) {
                                    try {
                                        val bytes = android.util.Base64.decode(rawValue, android.util.Base64.NO_WRAP)
                                        scannedChunks.add(bytes)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    }
                    pdfRenderer.close()
                    pfd.close()
                }
            } else {
                // Parse Image file
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    
                    if (bitmap != null) {
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        val barcodes = com.google.android.gms.tasks.Tasks.await(scanner.process(inputImage))
                        for (barcode in barcodes) {
                            val rawBytes = barcode.rawBytes
                            if (rawBytes != null) {
                                scannedChunks.add(rawBytes)
                            } else {
                                val rawValue = barcode.rawValue
                                if (rawValue != null) {
                                    try {
                                        val bytes = android.util.Base64.decode(rawValue, android.util.Base64.NO_WRAP)
                                        scannedChunks.add(bytes)
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            scanner.close()
            
            if (scannedChunks.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onError(context.getString(R.string.no_valid_qr_found))
                }
                return@launch
            }
            
            // Add all scanned chunks to the viewModel
            withContext(Dispatchers.Main) {
                scannedChunks.forEach { chunk ->
                    qrBackupViewModel.addScannedQRChunk(chunk)
                }
                onSuccess()
            }
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(context.getString(R.string.failed_read_file_error, e.localizedMessage ?: e.message))
            }
        }
    }
}
