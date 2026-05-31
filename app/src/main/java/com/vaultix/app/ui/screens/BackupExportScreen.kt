package com.vaultix.app.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.vaultix.app.ui.viewmodel.ExportFormat
import com.vaultix.app.ui.viewmodel.ExportResult
import com.vaultix.app.ui.viewmodel.QRCodeBackupViewModel
import com.vaultix.app.ui.viewmodel.QRBackupUIState
import com.vaultix.app.util.BackupScope
import com.vaultix.app.util.QRUtility
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupExportScreen(
    onBack: () -> Unit,
    backupViewModel: BackupViewModel = hiltViewModel(),
    qrBackupViewModel: QRCodeBackupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val qrState by qrBackupViewModel.uiState.collectAsStateWithLifecycle()
    val qrCodes by qrBackupViewModel.qrCodes.collectAsStateWithLifecycle()

    var currentStep by remember { mutableIntStateOf(0) }

    // Step 1 state
    val selectedScopes = remember { mutableStateListOf<BackupScope>() }
    var isFullVault by remember { mutableStateOf(true) }
    var scopeCounts by remember { mutableStateOf(mapOf<BackupScope, Int>()) }

    // Step 2 state
    var selectedFormat by remember { mutableStateOf(ExportFormat.VBK_FILE) }

    // Step 3 state
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportDone by remember { mutableStateOf(false) }
    var exportResultMessage by remember { mutableStateOf("") }

    // SAF launcher for .vbk export
    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            val scopes = if (isFullVault) setOf(BackupScope.FULL) else selectedScopes.toSet()
            backupViewModel.exportBackup(it, password.toCharArray(), scopes)
            scope.launch {
                backupViewModel.updateExportProgress(0.2f)
                delay(500)
                backupViewModel.updateExportProgress(0.5f)
                delay(500)
                backupViewModel.updateExportProgress(0.8f)
                delay(400)
                backupViewModel.updateExportProgress(1f)
                val totalItems = if (isFullVault) {
                    scopeCounts.values.sum()
                } else {
                    selectedScopes.sumOf { s -> scopeCounts[s] ?: 0 }
                }
                exportDone = true
                exportResultMessage = "Successfully exported $totalItems items as .VBK file"
                isExporting = false
            }
        }
    }

    // Load scope counts on first composition
    LaunchedEffect(Unit) {
        backupViewModel.clearResults()
        scopeCounts = backupViewModel.getAllScopeCounts()
        selectedScopes.addAll(BackupScope.entries.filter { it != BackupScope.FULL })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Data", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            StepIndicator(currentStep = currentStep, totalSteps = 3)

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { if (targetState > initialState) it else -it } + fadeIn() togetherWith
                    slideOutHorizontally { if (targetState > initialState) -it else it } + fadeOut()
                },
                label = "step_transition"
            ) { step ->
                when (step) {
                    0 -> ExportStep1_SelectData(
                        isFullVault = isFullVault,
                        onFullVaultChanged = { isFullVault = it },
                        selectedScopes = selectedScopes,
                        scopeCounts = scopeCounts,
                        onNext = { currentStep = 1 }
                    )
                    1 -> ExportStep2_ChooseFormat(
                        selectedFormat = selectedFormat,
                        onFormatSelected = { selectedFormat = it },
                        onBack = { currentStep = 0 },
                        onNext = { currentStep = 2 }
                    )
                    2 -> ExportStep3_PasswordAndExport(
                        password = password,
                        onPasswordChanged = { password = it },
                        confirmPassword = confirmPassword,
                        onConfirmPasswordChanged = { confirmPassword = it },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        isExporting = isExporting,
                        exportDone = exportDone,
                        exportResultMessage = exportResultMessage,
                        exportProgress = backupState.exportProgress,
                        selectedFormat = selectedFormat,
                        onBack = { currentStep = 1 },
                        onExport = {
                            if (password.length < 6) {
                                Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                return@ExportStep3_PasswordAndExport
                            }
                            if (password != confirmPassword) {
                                Toast.makeText(context, "Passwords don't match", Toast.LENGTH_SHORT).show()
                                return@ExportStep3_PasswordAndExport
                            }
                            isExporting = true
                            val scopes = if (isFullVault) setOf(BackupScope.FULL) else selectedScopes.toSet()
                            when (selectedFormat) {
                                ExportFormat.VBK_FILE -> {
                                    exportFileLauncher.launch("vaultix_backup_${System.currentTimeMillis()}.vbk")
                                }
                                ExportFormat.QR_IMAGES -> {
                                    qrBackupViewModel.generateQRCodesFromBackup(password, scopes)
                                    scope.launch {
                                        backupViewModel.updateExportProgress(0.3f)
                                        delay(600)
                                        backupViewModel.updateExportProgress(0.7f)
                                        // Wait for QR generation to complete
                                        var isDone = false
                                        while (!isDone) {
                                            val state = qrBackupViewModel.uiState.value
                                            if (state is QRBackupUIState.Success) {
                                                isDone = true
                                            } else if (state is QRBackupUIState.Error) {
                                                isExporting = false
                                                backupViewModel.updateExportProgress(0f)
                                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                                                return@launch
                                            }
                                            delay(200)
                                        }
                                        // Save QR codes to gallery
                                        val qrUtil = QRUtility(context)
                                        val images = qrBackupViewModel.exportQRCodesToImages()
                                        qrUtil.saveQRCodesToGallery(images.map { it.second })
                                        backupViewModel.updateExportProgress(1f)
                                        exportDone = true
                                        exportResultMessage = "Successfully saved ${images.size} QR code images to gallery"
                                        isExporting = false
                                    }
                                }
                                ExportFormat.QR_PDF -> {
                                    qrBackupViewModel.generateQRCodesFromBackup(password, scopes)
                                    scope.launch {
                                        backupViewModel.updateExportProgress(0.3f)
                                        delay(600)
                                        backupViewModel.updateExportProgress(0.7f)
                                        var isDone = false
                                        while (!isDone) {
                                            val state = qrBackupViewModel.uiState.value
                                            if (state is QRBackupUIState.Success) {
                                                isDone = true
                                            } else if (state is QRBackupUIState.Error) {
                                                isExporting = false
                                                backupViewModel.updateExportProgress(0f)
                                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                                                return@launch
                                            }
                                            delay(200)
                                        }
                                        val qrUtil = QRUtility(context)
                                        qrUtil.printQRCodes(qrBackupViewModel.qrCodes.value)
                                        backupViewModel.updateExportProgress(1f)
                                        exportDone = true
                                        exportResultMessage = "QR code PDF sent to print service"
                                        isExporting = false
                                    }
                                }
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
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
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
private fun ExportStep1_SelectData(
    isFullVault: Boolean,
    onFullVaultChanged: (Boolean) -> Unit,
    selectedScopes: MutableList<BackupScope>,
    scopeCounts: Map<BackupScope, Int>,
    onNext: () -> Unit
) {
    val totalCount = scopeCounts.values.sum()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Select Data to Export", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Choose what you'd like to include in your backup.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        // Full vault option
        ScopeCard(
            icon = Icons.Default.Inventory2,
            title = "Full Vault",
            subtitle = "$totalCount total items",
            isSelected = isFullVault,
            onClick = { onFullVaultChanged(true) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

        Text("Or select specific categories:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val categories = listOf(
            Triple(BackupScope.PASSWORDS, Icons.Default.Lock, "Passwords"),
            Triple(BackupScope.CARDS, Icons.Default.CreditCard, "Cards"),
            Triple(BackupScope.NOTES, Icons.Default.StickyNote2, "Notes"),
            Triple(BackupScope.FILES, Icons.Default.InsertDriveFile, "Files"),
            Triple(BackupScope.IDENTITIES, Icons.Default.Badge, "Identities")
        )
        categories.forEach { (scopeItem, icon, label) ->
            val count = scopeCounts[scopeItem] ?: 0
            val isSelected = !isFullVault && selectedScopes.contains(scopeItem)
            ScopeCard(
                icon = icon,
                title = label,
                subtitle = "$count items",
                isSelected = isSelected,
                onClick = {
                    if (isFullVault) {
                        onFullVaultChanged(false)
                        selectedScopes.clear()
                        selectedScopes.add(scopeItem)
                    } else {
                        if (selectedScopes.contains(scopeItem)) {
                            selectedScopes.remove(scopeItem)
                        } else {
                            selectedScopes.add(scopeItem)
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = isFullVault || selectedScopes.isNotEmpty()
        ) {
            Text("Next", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScopeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) BorderStroke(2.dp, primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isSelected) primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ExportStep2_ChooseFormat(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose Export Format", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Select how you'd like to export your data.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        FormatCard(
            icon = Icons.Default.Description,
            title = "Encrypted .VBK File",
            subtitle = "Save a portable encrypted backup file for transfer to another device",
            isSelected = selectedFormat == ExportFormat.VBK_FILE,
            onClick = { onFormatSelected(ExportFormat.VBK_FILE) }
        )
        FormatCard(
            icon = Icons.Default.QrCode,
            title = "QR Code Images",
            subtitle = "Generate and save QR codes to your gallery for offline backup",
            isSelected = selectedFormat == ExportFormat.QR_IMAGES,
            onClick = { onFormatSelected(ExportFormat.QR_IMAGES) }
        )
        FormatCard(
            icon = Icons.Default.PictureAsPdf,
            title = "QR Code PDF",
            subtitle = "Print or save QR codes as a PDF document",
            isSelected = selectedFormat == ExportFormat.QR_PDF,
            onClick = { onFormatSelected(ExportFormat.QR_PDF) }
        )

        Spacer(Modifier.height(16.dp))

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
                Icon(Icons.Default.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text("Back")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Next", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, null)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormatCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) BorderStroke(2.dp, primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isSelected) primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = primary)
            )
        }
    }
}

@Composable
private fun ExportStep3_PasswordAndExport(
    password: String,
    onPasswordChanged: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChanged: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    isExporting: Boolean,
    exportDone: Boolean,
    exportResultMessage: String,
    exportProgress: Float,
    selectedFormat: ExportFormat,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDone: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = exportProgress,
        animationSpec = tween(durationMillis = 400),
        label = "progress"
    )
    val primary = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (exportDone) {
            // Success card
            Spacer(Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = VaultSuccess.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, VaultSuccess.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(VaultSuccess.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = VaultSuccess, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Export Complete!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        exportResultMessage,
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
                Text("Back to Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        } else if (isExporting) {
            // Exporting progress
            Spacer(Modifier.height(48.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Exporting...", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
            Text("Secure Your Backup", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Enter a password to encrypt your backup. You'll need this password to restore.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))

            val formatLabel = when (selectedFormat) {
                ExportFormat.VBK_FILE -> "Encrypted .VBK File"
                ExportFormat.QR_IMAGES -> "QR Code Images"
                ExportFormat.QR_PDF -> "QR Code PDF"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, primary.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Format: $formatLabel", fontSize = 13.sp, color = primary, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                label = { Text("Backup Password") },
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
                Text("Password must be at least 6 characters", color = VaultError, fontSize = 12.sp)
            }
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChanged,
                label = { Text("Confirm Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    focusedLabelColor = primary,
                    cursorColor = primary
                )
            )
            if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                Text("Passwords don't match", color = VaultError, fontSize = 12.sp)
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
                    Icon(Icons.Default.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Back")
                }
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    enabled = password.length >= 6 && password == confirmPassword
                ) {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
