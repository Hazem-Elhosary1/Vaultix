package com.vaultix.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.vaultix.app.R
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import com.vaultix.app.ui.viewmodel.BackupUiState
import com.vaultix.app.ui.viewmodel.BackupViewModel
import com.vaultix.app.util.BackupScope
import kotlinx.coroutines.launch

import com.vaultix.app.ui.viewmodel.AppConfigViewModel
import com.vaultix.app.ui.viewmodel.ThemeMode

@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onNavigateToQRCodeBackup: (String) -> Unit = {},
    onNavigateToQRCodeRestore: () -> Unit = {},
    onNavigateToDevelopment: () -> Unit = {},
    appConfigViewModel: AppConfigViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isBiometricEnabled by authViewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showPanicConfirmDialog by remember { mutableStateOf(false) }
    var showProLockDialog by remember { mutableStateOf(false) }
    var showFakeVaultDialog by remember { mutableStateOf(false) }
    var showRecoverySheetDialog by remember { mutableStateOf(false) }
    val autoLockSeconds by authViewModel.autoLockTimeoutSeconds.collectAsStateWithLifecycle()
    val gracePeriodSeconds by authViewModel.gracePeriodSeconds.collectAsStateWithLifecycle()

    val configState by appConfigViewModel.configState.collectAsStateWithLifecycle()

    var selectedAutoLock by remember(autoLockSeconds) { mutableStateOf(autoLockSeconds) }
    var selectedGracePeriod by remember(gracePeriodSeconds) { mutableStateOf(gracePeriodSeconds) }
    var showBackupHistoryDialog by remember { mutableStateOf(false) }
    var selectedHistoryFile by remember { mutableStateOf<java.io.File?>(null) }
    var showHistoryRestoreConfirmDialog by remember { mutableStateOf(false) }

    // Backup ViewModel
    val backupViewModel: BackupViewModel = hiltViewModel()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()

    // SAF launchers for backup
    var showBackupTypeDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var showQRCodePasswordDialog by remember { mutableStateOf(false) }
    var qrActionIsExport by remember { mutableStateOf(true) }
    var qrMasterPassword by remember { mutableStateOf("") }
    var backupActionIsExport by remember { mutableStateOf(true) }
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    val selectedBackupScopes = remember { mutableStateListOf<BackupScope>() }
    
    // Backup scope counts
    var scopeCounts by remember { mutableStateOf(mapOf<BackupScope, Int>()) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            pendingBackupUri = it
            backupActionIsExport = true
            showBackupPasswordDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingBackupUri = it
            backupActionIsExport = false
            showBackupPasswordDialog = true
        }
    }

    // Show toast for backup results
    LaunchedEffect(backupState.message, backupState.error) {
        backupState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            backupViewModel.clearMessage()
        }
        backupState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            backupViewModel.clearMessage()
        }
    }

    // Fetch backup scope counts when dialog opens
    LaunchedEffect(showBackupTypeDialog) {
        if (showBackupTypeDialog) {
            scope.launch {
                val counts = mutableMapOf<BackupScope, Int>()
                BackupScope.values().forEach { scope ->
                    counts[scope] = backupViewModel.getScopeCount(scope)
                }
                scopeCounts = counts
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Premium Card
            if (!configState.isPremium) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPremium() },
                    colors = CardDefaults.cardColors(containerColor = VaultOrange),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WorkspacePremium, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.go_pro), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                            Text(stringResource(R.string.premium_features), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                    }
                }
            }

            // Security Section
            SettingsSection(title = stringResource(R.string.security)) {
                SettingsToggleItem(
                    icon = Icons.Default.Fingerprint,
                    title = stringResource(R.string.biometric_unlock),
                    subtitle = stringResource(R.string.biometric_subtitle),
                    checked = isBiometricEnabled,
                    onToggle = { enabled -> scope.launch { authViewModel.setBiometricEnabled(enabled) } }
                )
                SettingsDivider()
                SettingsClickItem(Icons.Default.Lock, stringResource(R.string.change_master_password), stringResource(R.string.change_master_password_subtitle), CategoryPasswords) {
                    showChangePasswordDialog = true
                }
                SettingsDivider()
                SettingsClickItem(Icons.Default.Pin, stringResource(R.string.change_pin), stringResource(R.string.change_pin_subtitle), CategoryCards) {
                    showChangePinDialog = true
                }
                SettingsDivider()
                SettingsClickItem(Icons.Default.Security, "Setup Fake Vault", "Create a decoy vault with a secondary password", VaultError) {
                    if (configState.isPremium) {
                        showFakeVaultDialog = true
                    } else {
                        showProLockDialog = true
                    }
                }
                SettingsDivider()
                SettingsClickItem(Icons.Default.Article, "Emergency Recovery Sheet", "View or print your recovery key for emergency access", VaultOrange) {
                    showRecoverySheetDialog = true
                }
            }

            // Autofill Section
            SettingsSection(title = "Auto-fill Service") {
                SettingsClickItem(
                    icon = Icons.Default.FlashOn,
                    title = "Enable Auto-fill",
                    subtitle = "Fill passwords in other apps & browsers automatically",
                    iconTint = VaultOrange
                ) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                            data = Uri.parse("package:com.vaultix.app")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback for some OS versions
                        try {
                            context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Please enable Vaultix in System Settings > Languages & Input > Autofill", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            // Auto-lock & Grace Period Section
            SettingsSection(title = stringResource(R.string.auto_lock_grace)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.auto_lock_inactivity), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Pair("30s", 30), Pair("1m", 60), Pair("5m", 300), Pair("15m", 900), Pair("Never", -1)).forEach { (label, seconds) ->
                            FilterChip(
                                selected = selectedAutoLock == seconds,
                                onClick = {
                                    selectedAutoLock = seconds
                                    scope.launch { authViewModel.setAutoLockTimeout(seconds) }
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.grace_period_title), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.grace_period_subtitle), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Pair("Immediate", 0), Pair("15s", 15), Pair("30s", 30), Pair("1m", 60)).forEach { (label, seconds) ->
                            FilterChip(
                                selected = selectedGracePeriod == seconds,
                                onClick = {
                                    selectedGracePeriod = seconds
                                    scope.launch { authViewModel.setGracePeriod(seconds) }
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VaultInfo.copy(0.2f),
                                    selectedLabelColor = VaultInfo
                                )
                            )
                        }
                    }
                }
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // â•â•  Data Protection & Backup  â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            SettingsSection(title = "Data Protection") {

                // â”€â”€ 1. Auto Backup Status â”€â”€
                SettingsToggleItem(
                    icon = Icons.Default.Shield,
                    title = "Auto Backup",
                    subtitle = if (backupState.isBackupEnabled) {
                        when (backupState.backupFrequency) {
                            "DAILY" -> "Protected â€¢ Backing up daily"
                            "WEEKLY" -> "Protected â€¢ Backing up weekly"
                            "MONTHLY" -> "Protected â€¢ Backing up monthly"
                            else -> "Protected â€¢ Backing up daily"
                        }
                    } else "âš ï¸ Disabled â€” your data is at risk!",
                    checked = backupState.isBackupEnabled,
                    onToggle = { enabled -> backupViewModel.setBackupEnabled(enabled) }
                )

                // â”€â”€ Warning banner when disabled â”€â”€
                if (!backupState.isBackupEnabled) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VaultError.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Warning, null, tint = VaultError, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "If you lose your phone or uninstall the app, ALL your passwords, cards, notes, and files will be lost permanently with no way to recover them.",
                                    fontSize = 12.sp,
                                    color = VaultError,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                SettingsDivider()

                // â”€â”€ 2. Frequency picker (only when enabled) â”€â”€
                if (backupState.isBackupEnabled) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Backup Frequency", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple("Daily", "DAILY", "Best protection"),
                                Triple("Weekly", "WEEKLY", "Balanced"),
                                Triple("Monthly", "MONTHLY", "Saves storage")
                            ).forEach { (label, value, _) ->
                                FilterChip(
                                    selected = backupState.backupFrequency == value,
                                    onClick = { backupViewModel.setBackupFrequency(value) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    leadingIcon = if (backupState.backupFrequency == value) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = VaultOrange.copy(0.2f),
                                        selectedLabelColor = VaultOrange
                                    )
                                )
                            }
                        }
                    }
                    SettingsDivider()
                }

                // â”€â”€ 3. Last backup + Backup Now â”€â”€
                if (backupState.lastBackupTime > 0L) {
                    val lastDate = remember(backupState.lastBackupTime) {
                        java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(backupState.lastBackupTime))
                    }
                    SettingsInfoItem("Last Backup", lastDate)
                    SettingsDivider()
                }

                SettingsClickItem(
                    icon = Icons.Default.Backup,
                    title = "Backup Now",
                    subtitle = if (backupState.isExporting) "Creating backup..." else "Create an instant encrypted backup",
                    iconTint = CategoryPasswords
                ) {
                    backupViewModel.triggerHistoryBackup()
                }

                SettingsDivider()

                // â”€â”€ 4. Backup History (Premium) â”€â”€
                SettingsClickItem(
                    icon = Icons.Default.History,
                    title = "Backup History",
                    subtitle = "${backupState.localBackups.size} backups stored on device",
                    iconTint = VaultOrange
                ) {
                    if (configState.isPremium) {
                        backupViewModel.refreshLocalBackups()
                        showBackupHistoryDialog = true
                    } else {
                        showProLockDialog = true
                    }
                }
            }

            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // â•â•  Transfer Data  â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
            SettingsSection(title = "Transfer Data") {
                SettingsClickItem(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.export_backup),
                    subtitle = if (backupState.isExporting) "Exporting..." else "Save an encrypted file to move to another device",
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    if (configState.isPremium) {
                        selectedBackupScopes.clear()
                        selectedBackupScopes.add(BackupScope.FULL)
                        showBackupTypeDialog = true
                    } else {
                        showProLockDialog = true
                    }
                }
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.import_backup),
                    subtitle = if (backupState.isImporting) "Importing..." else "Restore from a .vbk backup file",
                    iconTint = VaultInfo
                ) {
                    importLauncher.launch(arrayOf("*/*"))
                }

                SettingsDivider()

                // QR Code Backup / Restore
                SettingsClickItem(
                    icon = Icons.Default.QrCode,
                    title = "Export as QR",
                    subtitle = "Generate printable QR codes for offline backup",
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    qrActionIsExport = true
                    qrMasterPassword = ""
                    showQRCodePasswordDialog = true
                }
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.QrCodeScanner,
                    title = "Restore from QR",
                    subtitle = "Scan QR codes to restore a backup",
                    iconTint = VaultInfo
                ) {
                    onNavigateToQRCodeRestore()
                }
            }

            // Appearance Section
            SettingsSection(title = stringResource(R.string.appearance)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.accent_color), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val colors = listOf(VaultOrange, AccentBlue, AccentGreen, AccentPurple, AccentRed)
                        colors.forEach { color ->
                            val hex = "#" + Integer.toHexString(color.toArgb()).substring(2).uppercase()
                            val isSelected = configState.accentColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, androidx.compose.foundation.shape.CircleShape)
                                    .clickable { 
                                        if (hex == "#FF9800" || configState.isPremium) {
                                            appConfigViewModel.setAccentColor(hex)
                                        } else {
                                            showProLockDialog = true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Dev Tools (develop branch only)
            SettingsSection(title = "\uD83D\uDD27 Developer") {
                SettingsClickItem(
                    icon = Icons.Default.Code,
                    title = "Development Tools",
                    subtitle = "Seed mock data, clear database & more",
                    iconTint = VaultInfo
                ) {
                    onNavigateToDevelopment()
                }
            }

            // App Info
            SettingsSection(title = stringResource(R.string.about)) {
                SettingsInfoItem("Version", if (configState.isPremium) "1.0.0 PRO" else "1.0.0")
                SettingsDivider()
                SettingsInfoItem("Encryption", "AES-256-GCM")
                SettingsDivider()
                SettingsInfoItem("Key Storage", "Android Keystore")
                SettingsDivider()
                SettingsInfoItem("Architecture", "Zero-Knowledge")
                SettingsDivider()
                SettingsInfoItem("Internet Access", "None (100% Offline)")
            }

            // Panic Mode Section
            SettingsSection(title = stringResource(R.string.panic_mode)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Warning, null, tint = VaultError, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.panic_mode_warning), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showPanicConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultError),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultError),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.trigger_panic_mode), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { authViewModel.lock(); onLogout() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lock_vault), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showPanicConfirmDialog) {
        var isPanicProcessing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!isPanicProcessing) showPanicConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = VaultError)
                    Spacer(Modifier.width(8.dp))
                    Text("\u26A0\uFE0F Panic Mode", color = VaultError, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("This will PERMANENTLY delete all passwords, cards, notes, files, and IDs.\n\nThere is NO recovery. Are you absolutely sure?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isPanicProcessing) {
                            scope.launch {
                                isPanicProcessing = true
                                authViewModel.triggerPanicMode()
                                onLogout()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultError),
                    enabled = !isPanicProcessing
                ) {
                    if (isPanicProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.delete).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (!isPanicProcessing) {
                    TextButton(onClick = { showPanicConfirmDialog = false }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        )
    }

    if (showProLockDialog) {
        AlertDialog(
            onDismissRequest = { showProLockDialog = false },
            containerColor = VaultSurface,
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = VaultOrange, modifier = Modifier.size(48.dp)) },
            title = { Text("Pro Feature Required", color = VaultTextPrimary) },
            text = { Text("This advanced feature requires a Pro subscription. Upgrade to unlock the full potential of Vaultix!", color = VaultTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { 
                        showProLockDialog = false
                        onNavigateToPremium()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) { Text("Upgrade Now", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showProLockDialog = false }) {
                    Text("Maybe Later", color = VaultTextSecondary)
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        var currentPwd by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        var confirmPwd by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var showPwd by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { if (!isSaving) showChangePasswordDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.change_master_password), color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text("This will generate a new database key and re-encrypt your entire vault securely.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = currentPwd,
                        onValueChange = { currentPwd = it; errorMsg = null },
                        label = { Text("Current Password") },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it; errorMsg = null },
                        label = { Text("New Password") },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPwd,
                        onValueChange = { confirmPwd = it; errorMsg = null },
                        label = { Text("Confirm New Password") },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPwd = !showPwd }, enabled = !isSaving) {
                                Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
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
                        if (newPwd != confirmPwd) {
                            errorMsg = "Passwords do not match"
                            return@Button
                        }
                        if (newPwd.length < 8) {
                            errorMsg = "Password must be at least 8 characters"
                            return@Button
                        }
                        
                        isSaving = true
                        authViewModel.changeMasterPassword(
                            currentPwd = currentPwd.toCharArray(),
                            newPwd = newPwd.toCharArray(),
                            onSuccess = {
                                isSaving = false
                                showChangePasswordDialog = false
                                android.widget.Toast.makeText(context, "Password changed and Vault re-encrypted! 🔐", android.widget.Toast.LENGTH_LONG).show()
                            },
                            onFailure = { error ->
                                isSaving = false
                                errorMsg = error
                            }
                        )
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.save))
                    }
                }
            },
            dismissButton = {
                if (!isSaving) {
                    TextButton(onClick = { showChangePasswordDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showChangePinDialog) {
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var showPin by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }

        fun normalizePinInput(value: String): String {
            return value.filter { it.isDigit() }.take(6)
        }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showChangePinDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.change_pin), color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text("This will update the PIN used for quick unlock.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = {
                            currentPin = normalizePinInput(it)
                            errorMsg = null
                        },
                        label = { Text("Current PIN") },
                        visualTransformation = if (showPin) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            newPin = normalizePinInput(it)
                            errorMsg = null
                        },
                        label = { Text("New PIN") },
                        visualTransformation = if (showPin) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = normalizePinInput(it)
                            errorMsg = null
                        },
                        label = { Text("Confirm New PIN") },
                        visualTransformation = if (showPin) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }, enabled = !isSaving) {
                                Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
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
                        when {
                            currentPin.isBlank() || newPin.isBlank() || confirmPin.isBlank() -> {
                                errorMsg = "All PIN fields are required"
                            }
                            newPin != confirmPin -> {
                                errorMsg = "PINs do not match"
                            }
                            newPin.length < 4 || newPin.length > 6 -> {
                                errorMsg = "PIN must be 4-6 digits"
                            }
                            else -> {
                                isSaving = true
                                authViewModel.changePin(
                                    currentPin = currentPin.toCharArray(),
                                    newPin = newPin.toCharArray(),
                                    onSuccess = {
                                        isSaving = false
                                        showChangePinDialog = false
                                        Toast.makeText(context, "PIN changed successfully!", Toast.LENGTH_LONG).show()
                                    },
                                    onFailure = { error ->
                                        isSaving = false
                                        errorMsg = error
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.save))
                    }
                }
            },
            dismissButton = {
                if (!isSaving) {
                    TextButton(onClick = { showChangePinDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showFakeVaultDialog) {
        var fakePwd by remember { mutableStateOf("") }
        var confirmFakePwd by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var showPwd by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showFakeVaultDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Setup Fake Vault", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text("Enter a decoy password. Logging in with this password will open an isolated, dummy vault to protect your real data from forced access.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = fakePwd,
                        onValueChange = { fakePwd = it; errorMsg = null },
                        label = { Text("Fake Password") },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmFakePwd,
                        onValueChange = { confirmFakePwd = it; errorMsg = null },
                        label = { Text("Confirm Fake Password") },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
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
                        if (fakePwd != confirmFakePwd) {
                            errorMsg = "Passwords do not match"
                            return@Button
                        }
                        if (fakePwd.length < 4) {
                            errorMsg = "Password too short"
                            return@Button
                        }
                        isSaving = true
                        authViewModel.setFakePassword(
                            password = fakePwd.toCharArray(),
                            onSuccess = {
                                isSaving = false
                                showFakeVaultDialog = false
                                Toast.makeText(context, "Fake Vault configured! Try logging in with the decoy password.", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Setup")
                    }
                }
            },
            dismissButton = {
                if (!isSaving) {
                    TextButton(onClick = { showFakeVaultDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
    }

    if (showBackupPasswordDialog) {
        var showPassword by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showBackupPasswordDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (backupActionIsExport) "Set Backup Password" else "Enter Backup Password", color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text(
                        if (backupActionIsExport) "This password will be required to restore your data on any device. Don't lose it!" 
                        else "Enter the password you used when creating this backup.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Backup Password") },
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = backupPassword.length >= 4,
                    onClick = {
                        pendingBackupUri?.let { uri ->
                            if (backupActionIsExport) {
                                backupViewModel.exportBackup(uri, backupPassword.toCharArray(), selectedBackupScopes.toSet())
                            } else {
                                backupViewModel.importBackup(uri, backupPassword.toCharArray())
                            }
                        }
                        showBackupPasswordDialog = false
                        backupPassword = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (backupActionIsExport) "Export" else "Import", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPasswordDialog = false; backupPassword = "" }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    if (showBackupTypeDialog) {
        AlertDialog(
            onDismissRequest = { showBackupTypeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Back up what?", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tap cards to include multiple sections in one backup.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    BackupScopeChoice("Full vault", "Passwords, cards, notes, files, and IDs", BackupScope.FULL, scopeCounts[BackupScope.FULL] ?: 0, selectedBackupScopes) { scope ->
                        selectedBackupScopes.clear()
                        selectedBackupScopes.add(scope)
                    }
                    BackupScopeChoice("Passwords", "Only saved logins", BackupScope.PASSWORDS, scopeCounts[BackupScope.PASSWORDS] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice("Cards", "Only payment cards", BackupScope.CARDS, scopeCounts[BackupScope.CARDS] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice("Notes", "Only secure notes", BackupScope.NOTES, scopeCounts[BackupScope.NOTES] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice("Files", "Only vault files", BackupScope.FILES, scopeCounts[BackupScope.FILES] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice("IDs", "Only identity documents", BackupScope.IDENTITIES, scopeCounts[BackupScope.IDENTITIES] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBackupTypeDialog = false
                    exportLauncher.launch("vaultix_backup_${if (selectedBackupScopes.contains(BackupScope.FULL)) "full" else "custom"}_${System.currentTimeMillis()}.vbk")
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupTypeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBackupHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showBackupHistoryDialog = false },
            containerColor = VaultSurface,
            title = { Text("Backup History", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (backupState.localBackups.isEmpty()) {
                        Text("No local backups found.", color = VaultTextSecondary, fontSize = 13.sp)
                    } else {
                        backupState.localBackups.forEach { file ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = VaultBlack.copy(0.3f))
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                                                .format(java.util.Date(file.lastModified())),
                                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VaultTextPrimary
                                        )
                                        Text("${file.length() / 1024} KB", fontSize = 11.sp, color = VaultTextSecondary)
                                    }
                                    IconButton(onClick = {
                                        selectedHistoryFile = file
                                        showHistoryRestoreConfirmDialog = true
                                    }) { Icon(Icons.Default.Restore, null, tint = VaultSuccess) }
                                    IconButton(onClick = { backupViewModel.deleteLocalBackup(file) }) { 
                                        Icon(Icons.Default.Delete, null, tint = VaultError) 
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupHistoryDialog = false }) { Text("Close", color = VaultOrange) }
            }
        )
    }

    if (showHistoryRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryRestoreConfirmDialog = false },
            containerColor = VaultSurface,
            title = { Text("Restore Backup", color = VaultOrange) },
            text = {
                Text("This will restore the selected daily encrypted backup and replace the current vault data.", fontSize = 12.sp, color = VaultTextSecondary)
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedHistoryFile?.let { backupViewModel.restoreLocalBackup(it) }
                        showHistoryRestoreConfirmDialog = false
                        showBackupHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) {
                    Text("Restore", color = VaultBlack)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryRestoreConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRecoverySheetDialog) {
        var isKeyVisible by remember { mutableStateOf(false) }
        var recoveryKey by remember { mutableStateOf<String?>(null) }
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        
        LaunchedEffect(showRecoverySheetDialog) {
            recoveryKey = authViewModel.getRecoveryKey()
        }
        
        AlertDialog(
            onDismissRequest = { showRecoverySheetDialog = false },
            containerColor = VaultSurface,
            title = { Text("Emergency Recovery Sheet", color = VaultOrange, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (recoveryKey == null) "You haven't set up a recovery key yet. This is your safety net if you forget your master password."
                        else "Your recovery key is the ONLY way to regain access if you forget your master password.",
                        fontSize = 13.sp, color = VaultTextSecondary
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(VaultBlack.copy(0.5f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (recoveryKey == null) {
                                Text("NOT CONFIGURED", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VaultError)
                            } else if (isKeyVisible) {
                                Text(recoveryKey!!, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VaultOrange, letterSpacing = 1.sp)
                            } else {
                                Text("â€¢â€¢â€¢â€¢-â€¢â€¢â€¢â€¢-â€¢â€¢â€¢â€¢-â€¢â€¢â€¢â€¢-â€¢â€¢â€¢â€¢-â€¢â€¢â€¢â€¢", fontSize = 18.sp, color = VaultTextSecondary)
                            }
                        }
                    }
                    
                    if (recoveryKey != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { 
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(recoveryKey!!))
                                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Copy Key")
                            }
                        }
                        
                        Text("Important Instructions:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("1. Write this key down physically.\n2. Store it in a separate location from your phone.\n3. Do NOT save it as a photo or digital file.", fontSize = 12.sp, color = VaultTextSecondary)
                    } else {
                        Button(
                            onClick = { 
                                scope.launch {
                                    val newKey = authViewModel.generateRecoveryKey()
                                    authViewModel.setupRecoveryKey(newKey)
                                    recoveryKey = newKey
                                    isKeyVisible = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                        ) {
                            Icon(Icons.Default.Autorenew, null, tint = VaultBlack)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate Recovery Key", color = VaultBlack)
                        }
                    }
                }
            },
            confirmButton = {
                if (recoveryKey != null) {
                    Button(onClick = { isKeyVisible = !isKeyVisible }) {
                        Text(if (isKeyVisible) "Hide Key" else "Reveal Key")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoverySheetDialog = false }) { Text("Close") }
            }
        )
    }
    
        if (showQRCodePasswordDialog) {
            var errorMsg by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showQRCodePasswordDialog = false },
                containerColor = VaultSurface,
                title = { Text("Export as QR", color = VaultOrange) },
                text = {
                    Column {
                        Text("Enter your master password to generate QR backup.", fontSize = 13.sp, color = VaultTextSecondary)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = qrMasterPassword,
                            onValueChange = { qrMasterPassword = it; errorMsg = null },
                            label = { Text("Master Password") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMsg != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (qrMasterPassword.isBlank()) {
                            errorMsg = "Password is required"
                            return@Button
                        }
                        showQRCodePasswordDialog = false
                        onNavigateToQRCodeBackup(qrMasterPassword)
                    }, colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)) {
                        Text("Generate", color = VaultBlack)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQRCodePasswordDialog = false }) { Text("Cancel") }
                }
            )
        }
    }

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))) {
        Column(content = content)
    }
}

@Composable
fun SettingsToggleItem(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
    }
}

@Composable
fun SettingsClickItem(icon: ImageVector, title: String, subtitle: String, iconTint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(iconTint.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
    }
}

@Composable
fun SettingsInfoItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
fun BackupScopeChoice(
    title: String,
    subtitle: String,
    scope: BackupScope,
    count: Int,
    selectedScopes: List<BackupScope>,
    onSelect: (BackupScope) -> Unit
) {
    val isSelected = selectedScopes.contains(scope)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(scope) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$title ($count)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

fun toggleBackupScope(selectedScopes: MutableList<BackupScope>, scope: BackupScope) {
    if (selectedScopes.contains(BackupScope.FULL)) {
        selectedScopes.clear()
    }

    if (selectedScopes.contains(scope)) {
        selectedScopes.remove(scope)
    } else {
        selectedScopes.add(scope)
    }

    if (selectedScopes.isEmpty()) {
        selectedScopes.add(BackupScope.FULL)
    }
}

