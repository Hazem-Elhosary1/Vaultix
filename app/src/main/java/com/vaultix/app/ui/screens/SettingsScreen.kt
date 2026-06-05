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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    onNavigateToPremium: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onBackground)
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
            // Premium Card removed — all features are now free

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
                SettingsClickItem(Icons.Default.Security, stringResource(R.string.setup_fake_vault_title), stringResource(R.string.fake_vault_subtitle_custom), VaultError) {
                    showFakeVaultDialog = true
                }
                SettingsDivider()
                SettingsClickItem(Icons.Default.Article, stringResource(R.string.emergency_recovery_sheet), stringResource(R.string.recovery_sheet_subtitle), VaultOrange) {
                    showRecoverySheetDialog = true
                }
            }

            // Autofill Section
            SettingsSection(title = stringResource(R.string.autofill)) {
                SettingsClickItem(
                    icon = Icons.Default.FlashOn,
                    title = stringResource(R.string.enable_autofill),
                    subtitle = stringResource(R.string.autofill_subtitle),
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
                            Toast.makeText(context, context.getString(R.string.enable_autofill_toast), Toast.LENGTH_LONG).show()
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
                        val autoLockOptions = listOf(
                            Pair(stringResource(R.string.duration_30s), 30),
                            Pair(stringResource(R.string.duration_1m), 60),
                            Pair(stringResource(R.string.duration_5m), 300),
                            Pair(stringResource(R.string.duration_15m), 900),
                            Pair(stringResource(R.string.never), -1)
                        )
                        autoLockOptions.forEach { (label, seconds) ->
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
                        val gracePeriodOptions = listOf(
                            Pair(stringResource(R.string.immediate), 0),
                            Pair(stringResource(R.string.duration_15s), 15),
                            Pair(stringResource(R.string.duration_30s), 30),
                            Pair(stringResource(R.string.duration_1m), 60)
                        )
                        gracePeriodOptions.forEach { (label, seconds) ->
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

            SettingsSection(title = stringResource(R.string.data_protection)) {

                // ── 1. Auto Backup Status ──
                SettingsToggleItem(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.auto_backup),
                    subtitle = if (backupState.isBackupEnabled) {
                        when (backupState.backupFrequency) {
                            "DAILY" -> stringResource(R.string.backup_status_daily)
                            "WEEKLY" -> stringResource(R.string.backup_status_weekly)
                            "MONTHLY" -> stringResource(R.string.backup_status_monthly)
                            else -> stringResource(R.string.backup_status_daily)
                        }
                    } else stringResource(R.string.backup_status_disabled),
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
                                    stringResource(R.string.backup_disabled_warning),
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
                        Text(stringResource(R.string.backup_frequency), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple(stringResource(R.string.frequency_daily), "DAILY", stringResource(R.string.frequency_daily_desc)),
                                Triple(stringResource(R.string.frequency_weekly), "WEEKLY", stringResource(R.string.frequency_weekly_desc)),
                                Triple(stringResource(R.string.frequency_monthly), "MONTHLY", stringResource(R.string.frequency_monthly_desc))
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
                // ── 3. Last backup + Backup Now ──
                if (backupState.lastBackupTime > 0L) {
                    val lastDate = remember(backupState.lastBackupTime) {
                        java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(backupState.lastBackupTime))
                    }
                    SettingsInfoItem(stringResource(R.string.last_backup), lastDate)
                    SettingsDivider()
                }

                SettingsClickItem(
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.backup_now),
                    subtitle = if (backupState.isExporting) stringResource(R.string.creating_backup) else stringResource(R.string.backup_now_subtitle),
                    iconTint = CategoryPasswords
                ) {
                    backupViewModel.triggerHistoryBackup()
                }

                SettingsDivider()

                // ── 4. Backup History ──
                SettingsClickItem(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.backup_history_title),
                    subtitle = stringResource(R.string.backups_stored_format, backupState.localBackups.size),
                    iconTint = VaultOrange
                ) {
                    backupViewModel.refreshLocalBackups()
                    showBackupHistoryDialog = true
                }
            }

            // ╔══════════════════════════════════════════════
            // ║  Transfer Data  ═════════════════════════════
            // ╚══════════════════════════════════════════════
            SettingsSection(title = stringResource(R.string.transfer_data)) {
                SettingsClickItem(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.export_data),
                    subtitle = stringResource(R.string.export_data_desc),
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    onNavigateToExport()
                }
                SettingsDivider()
                SettingsClickItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.import_data),
                    subtitle = stringResource(R.string.import_data_desc),
                    iconTint = VaultInfo
                ) {
                    onNavigateToImport()
                }
            }

            // Appearance Section
            SettingsSection(title = stringResource(R.string.appearance)) {
                Column(Modifier.padding(16.dp)) {
                    // Theme Mode
                    Text(stringResource(R.string.theme_mode), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple(ThemeMode.SYSTEM, stringResource(R.string.theme_system), Icons.Default.Settings),
                            Triple(ThemeMode.LIGHT, stringResource(R.string.theme_light), Icons.Default.LightMode),
                            Triple(ThemeMode.DARK, stringResource(R.string.theme_dark), Icons.Default.DarkMode)
                        ).forEach { (mode, label, icon) ->
                            FilterChip(
                                selected = configState.themeMode == mode,
                                onClick = { appConfigViewModel.setThemeMode(mode) },
                                label = { Text(label, fontSize = 12.sp) },
                                leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
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

                    // Accent Color
                    Text(stringResource(R.string.accent_color), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val colors = listOf(
                            AccentOrange, AccentBlue, AccentGreen, AccentPurple, AccentRed,
                            AccentTeal, AccentPink, AccentIndigo, AccentAmber, AccentCyan
                        )
                        colors.forEach { color ->
                            val hex = "#" + Integer.toHexString(color.toArgb()).substring(2).uppercase()
                            val isSelected = configState.accentColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, CircleShape)
                                    .clickable {
                                        appConfigViewModel.setAccentColor(hex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(16.dp))

                    // Language
                    Text(stringResource(R.string.language), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Pair("en", stringResource(R.string.lang_english)),
                            Pair("ar", stringResource(R.string.lang_arabic))
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = configState.language == code,
                                onClick = { appConfigViewModel.setLanguage(code) },
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

                    // Font Size Scale
                    Text(stringResource(R.string.font_size), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Pair(FontSizeScale.SMALL, stringResource(R.string.font_size_small)),
                            Pair(FontSizeScale.MEDIUM, stringResource(R.string.font_size_medium)),
                            Pair(FontSizeScale.LARGE, stringResource(R.string.font_size_large))
                        ).forEach { (scale, label) ->
                            FilterChip(
                                selected = configState.fontSizeScale == scale,
                                onClick = { appConfigViewModel.setFontSizeScale(scale) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }



            // App Info
            SettingsSection(title = stringResource(R.string.about)) {
                SettingsInfoItem(stringResource(R.string.version), "1.0.0")
                SettingsDivider()
                SettingsInfoItem(stringResource(R.string.info_encryption), "AES-256-GCM")
                SettingsDivider()
                SettingsInfoItem(stringResource(R.string.info_key_storage), "Android Keystore")
                SettingsDivider()
                SettingsInfoItem(stringResource(R.string.info_architecture), "Zero-Knowledge")
                SettingsDivider()
                SettingsInfoItem(stringResource(R.string.info_internet_access), stringResource(R.string.info_no_internet_desc))
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
                    Text(stringResource(R.string.panic_mode_confirm_title), color = VaultError, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(stringResource(R.string.panic_mode_confirm_text), color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
            title = { Text(stringResource(R.string.pro_required_title), color = VaultTextPrimary) },
            text = { Text(stringResource(R.string.pro_required_text), color = VaultTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { 
                        showProLockDialog = false
                        onNavigateToPremium()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) { Text(stringResource(R.string.upgrade_now), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showProLockDialog = false }) {
                    Text(stringResource(R.string.maybe_later), color = VaultTextSecondary)
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
                    Text(stringResource(R.string.change_password_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = currentPwd,
                        onValueChange = { currentPwd = it; errorMsg = null },
                        label = { Text(stringResource(R.string.current_password)) },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPwd,
                        onValueChange = { newPwd = it; errorMsg = null },
                        label = { Text(stringResource(R.string.new_password_label)) },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPwd,
                        onValueChange = { confirmPwd = it; errorMsg = null },
                        label = { Text(stringResource(R.string.confirm_new_password_label)) },
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
                            errorMsg = context.getString(R.string.passwords_not_match)
                            return@Button
                        }
                        if (newPwd.length < 8) {
                            errorMsg = context.getString(R.string.password_too_short)
                            return@Button
                        }
                        
                        isSaving = true
                        authViewModel.changeMasterPassword(
                            currentPwd = currentPwd.toCharArray(),
                            newPwd = newPwd.toCharArray(),
                            onSuccess = {
                                isSaving = false
                                showChangePasswordDialog = false
                                Toast.makeText(context, context.getString(R.string.password_changed_success), Toast.LENGTH_LONG).show()
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
                    Text(stringResource(R.string.change_pin_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = {
                            currentPin = normalizePinInput(it)
                            errorMsg = null
                        },
                        label = { Text(stringResource(R.string.current_pin)) },
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
                        label = { Text(stringResource(R.string.new_pin)) },
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
                        label = { Text(stringResource(R.string.confirm_new_pin)) },
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
                                errorMsg = context.getString(R.string.all_pin_fields_required)
                            }
                            newPin != confirmPin -> {
                                errorMsg = context.getString(R.string.setup_pin_match_error)
                            }
                            newPin.length < 4 || newPin.length > 6 -> {
                                errorMsg = context.getString(R.string.pin_invalid_length)
                            }
                            else -> {
                                isSaving = true
                                authViewModel.changePin(
                                    currentPin = currentPin.toCharArray(),
                                    newPin = newPin.toCharArray(),
                                    onSuccess = {
                                        isSaving = false
                                        showChangePinDialog = false
                                        Toast.makeText(context, context.getString(R.string.pin_changed_success), Toast.LENGTH_LONG).show()
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
            title = { Text(stringResource(R.string.setup_fake_vault_title), color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text(stringResource(R.string.setup_fake_vault_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = fakePwd,
                        onValueChange = { fakePwd = it; errorMsg = null },
                        label = { Text(stringResource(R.string.fake_password_label)) },
                        visualTransformation = if (showPwd) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaving
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmFakePwd,
                        onValueChange = { confirmFakePwd = it; errorMsg = null },
                        label = { Text(stringResource(R.string.confirm_fake_password_label)) },
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
                            errorMsg = context.getString(R.string.passwords_not_match)
                            return@Button
                        }
                        if (fakePwd.length < 4) {
                            errorMsg = context.getString(R.string.password_too_short)
                            return@Button
                        }
                        isSaving = true
                        authViewModel.setFakePassword(
                            password = fakePwd.toCharArray(),
                            onSuccess = {
                                isSaving = false
                                showFakeVaultDialog = false
                                Toast.makeText(context, context.getString(R.string.fake_vault_success), Toast.LENGTH_LONG).show()
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
                        Text(stringResource(R.string.setup_action))
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
            title = { Text(if (backupActionIsExport) stringResource(R.string.backup_export_title) else stringResource(R.string.backup_import_title), color = MaterialTheme.colorScheme.primary) },
            text = {
                Column {
                    Text(
                        if (backupActionIsExport) stringResource(R.string.backup_export_desc)
                        else stringResource(R.string.backup_import_desc),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text(stringResource(R.string.backup_password_label)) },
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
                    Text(if (backupActionIsExport) stringResource(R.string.backup_export_button) else stringResource(R.string.backup_import_button), color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPasswordDialog = false; backupPassword = "" }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    if (showBackupTypeDialog) {
        AlertDialog(
            onDismissRequest = { showBackupTypeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.backup_scope_title), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.backup_scope_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    BackupScopeChoice(stringResource(R.string.backup_scope_full), stringResource(R.string.backup_scope_full_desc), BackupScope.FULL, scopeCounts[BackupScope.FULL] ?: 0, selectedBackupScopes) { scope ->
                        selectedBackupScopes.clear()
                        selectedBackupScopes.add(scope)
                    }
                    BackupScopeChoice(stringResource(R.string.passwords), stringResource(R.string.backup_scope_passwords_desc), BackupScope.PASSWORDS, scopeCounts[BackupScope.PASSWORDS] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice(stringResource(R.string.cards), stringResource(R.string.backup_scope_cards_desc), BackupScope.CARDS, scopeCounts[BackupScope.CARDS] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice(stringResource(R.string.notes), stringResource(R.string.backup_scope_notes_desc), BackupScope.NOTES, scopeCounts[BackupScope.NOTES] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice(stringResource(R.string.files), stringResource(R.string.backup_scope_files_desc), BackupScope.FILES, scopeCounts[BackupScope.FILES] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                    BackupScopeChoice(stringResource(R.string.identities), stringResource(R.string.backup_scope_ids_desc), BackupScope.IDENTITIES, scopeCounts[BackupScope.IDENTITIES] ?: 0, selectedBackupScopes) { scope ->
                        toggleBackupScope(selectedBackupScopes, scope)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showBackupTypeDialog = false
                    exportLauncher.launch("vaultix_backup_${if (selectedBackupScopes.contains(BackupScope.FULL)) "full" else "custom"}_${System.currentTimeMillis()}.vbk")
                }) {
                    Text(stringResource(R.string.continue_text))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupTypeDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showBackupHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showBackupHistoryDialog = false },
            containerColor = VaultSurface,
            title = { Text(stringResource(R.string.backup_history_title), fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    if (backupState.localBackups.isEmpty()) {
                        Text(stringResource(R.string.no_local_backups), color = VaultTextSecondary, fontSize = 13.sp)
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
                TextButton(onClick = { showBackupHistoryDialog = false }) { Text(stringResource(R.string.close), color = VaultOrange) }
            }
        )
    }

    if (showHistoryRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryRestoreConfirmDialog = false },
            containerColor = VaultSurface,
            title = { Text(stringResource(R.string.restore_backup_title), color = VaultOrange) },
            text = {
                Text(stringResource(R.string.restore_backup_desc), fontSize = 12.sp, color = VaultTextSecondary)
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
                    Text(stringResource(R.string.restore_button), color = VaultBlack)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryRestoreConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            title = { Text(stringResource(R.string.emergency_recovery_sheet), color = VaultOrange, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (recoveryKey == null) stringResource(R.string.recovery_sheet_not_set)
                        else stringResource(R.string.recovery_sheet_set),
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
                                Text(stringResource(R.string.not_configured), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VaultError)
                            } else if (isKeyVisible) {
                                Text(recoveryKey!!, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VaultOrange, letterSpacing = 1.sp)
                            } else {
                                Text("••••-••••-••••-••••-••••-••••", fontSize = 18.sp, color = VaultTextSecondary)
                            }
                        }
                    }
                    
                    if (recoveryKey != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { 
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(recoveryKey!!))
                                Toast.makeText(context, context.getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.copy_key))
                            }
                        }
                        
                        Text(stringResource(R.string.important_instructions_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.important_instructions_desc), fontSize = 12.sp, color = VaultTextSecondary)
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
                            Text(stringResource(R.string.generate_recovery_key), color = VaultBlack)
                        }
                    }
                }
            },
            confirmButton = {
                if (recoveryKey != null) {
                    Button(onClick = { isKeyVisible = !isKeyVisible }) {
                        Text(if (isKeyVisible) stringResource(R.string.hide_key) else stringResource(R.string.reveal_key))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoverySheetDialog = false }) { Text(stringResource(R.string.close)) }
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
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
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

