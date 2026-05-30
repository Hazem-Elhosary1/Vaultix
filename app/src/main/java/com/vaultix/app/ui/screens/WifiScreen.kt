package com.vaultix.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultix.app.R
import com.vaultix.app.data.model.Password
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.PasswordViewModel
import com.vaultix.app.util.WifiQRGenerator
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

// Custom class to store parsed Wifi Details from note string
data class WifiDetails(
    val routerIp: String = "",
    val routerUsername: String = "",
    val routerPassword: String = "",
    val generalNotes: String = "",
    val isPinned: Boolean = false
)

fun parseWifiNotes(notesStr: String): WifiDetails {
    val lines = notesStr.lines()
    val routerIp = lines.firstOrNull { it.startsWith("Router IP: ") }?.removePrefix("Router IP: ") ?: ""
    val routerUsername = lines.firstOrNull { it.startsWith("Router Username: ") }?.removePrefix("Router Username: ") ?: ""
    val routerPassword = lines.firstOrNull { it.startsWith("Router Password: ") }?.removePrefix("Router Password: ") ?: ""
    val isPinned = lines.firstOrNull { it.startsWith("Pinned: ") }?.removePrefix("Pinned: ")?.toBoolean() ?: false
    
    val notesPrefixIndex = lines.indexOfFirst { it.startsWith("Notes: ") }
    val generalNotes = if (notesPrefixIndex != -1) {
        lines.drop(notesPrefixIndex).joinToString("\n").removePrefix("Notes: ")
    } else {
        ""
    }
    
    return WifiDetails(routerIp, routerUsername, routerPassword, generalNotes, isPinned)
}

fun serializeWifiNotes(details: WifiDetails): String {
    return buildString {
        appendLine("Router IP: ${details.routerIp}")
        appendLine("Router Username: ${details.routerUsername}")
        appendLine("Router Password: ${details.routerPassword}")
        appendLine("Pinned: ${details.isPinned}")
        append("Notes: ${details.generalNotes}")
    }
}

/**
 * 1. Wifi List Screen View
 */
@Composable
fun WifiList(
    searchQuery: String,
    onItemClick: (String) -> Unit,
    accentColor: Color
) {
    val viewModel: PasswordViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }

    val sortedWifi = remember(state.wifiPasswords, searchQuery) {
        state.wifiPasswords.sortedWith(
            compareByDescending<Password> { parseWifiNotes(it.notes).isPinned }
                .thenByDescending { it.updatedAt }
        )
    }

    if (sortedWifi.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No Wi-Fi Networks",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Securely store your Wi-Fi details and generate offline connection QR codes.",
                    fontSize = 14.sp,
                    color = VaultTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sortedWifi, key = { it.id }) { wifi ->
            WifiListItem(
                password = wifi,
                accentColor = accentColor,
                onClick = { onItemClick(wifi.id) },
                onDelete = { viewModel.deletePassword(wifi.id) },
                onToggleFavorite = { viewModel.toggleFavorite(wifi) },
                onTogglePin = {
                    val details = parseWifiNotes(wifi.notes)
                    val updated = wifi.copy(
                        notes = serializeWifiNotes(details.copy(isPinned = !details.isPinned))
                    )
                    viewModel.updatePassword(updated)
                }
            )
        }
    }
}

/**
 * 2. Gestural Wi-Fi List Item
 */
@Composable
fun WifiListItem(
    password: Password,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val details = remember(password.notes) { parseWifiNotes(password.notes) }

    val offsetXAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Background Actions Layer
        if (abs(offsetXAnim.value) > 1f) {
            val isSwipingRight = offsetXAnim.value > 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(VaultSurface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSwipingRight) {
                    // Left Actions: Pin & Favorite
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch { offsetXAnim.animateTo(0f) }
                                onTogglePin()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(VaultOrange.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pin",
                                tint = if (details.isPinned) VaultOrange else VaultTextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch { offsetXAnim.animateTo(0f) }
                                onToggleFavorite()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(VaultError.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                if (password.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (password.isFavorite) VaultError else VaultTextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    // Right Actions: Delete
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(VaultError.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = VaultError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Foreground Card Layer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetXAnim.value.roundToInt(), 0) }
                .pointerInput(password.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = when {
                                    offsetXAnim.value > 60.dp.toPx() -> 130.dp.toPx() // Settle Open Right
                                    offsetXAnim.value < -60.dp.toPx() -> -70.dp.toPx() // Settle Open Left
                                    else -> 0f
                                }
                                offsetXAnim.animateTo(target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newOffset = (offsetXAnim.value + dragAmount).coerceIn(-100.dp.toPx(), 170.dp.toPx())
                                offsetXAnim.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.15f))
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                // Left Vertical Colored Accent Strip
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(accentColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .defaultMinSize(minHeight = 84.dp)
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wi-Fi Icon Box
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(accentColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // Text Credentials Details
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (details.isPinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = VaultOrange,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .graphicsLayer(rotationZ = 45f)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = password.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VaultTextPrimary,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (password.isFavorite) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = VaultOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "SSID: ${password.username}",
                            fontSize = 13.sp,
                            color = VaultTextSecondary,
                            maxLines = 1
                        )

                        Text(
                            text = "Security: ${password.appPackageName}",
                            fontSize = 11.sp,
                            color = VaultTextSecondary.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = VaultTextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Wi-Fi Network?", color = VaultTextPrimary) },
            text = { Text("Are you sure you want to remove the credentials for '${password.title}'?", color = VaultTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = VaultError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

/**
 * 3. Add/Edit Wi-Fi Credentials Screen
 */
@Composable
fun AddEditWifiScreen(
    itemId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: PasswordViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentColor = MaterialTheme.colorScheme.primary

    val existingItem = remember(state.wifiPasswords, itemId) {
        itemId?.let { id -> state.wifiPasswords.find { it.id == id } }
    }

    // Input States
    var title by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var securityType by remember { mutableStateOf("WPA/WPA2/WPA3") }
    
    // Router Admin States
    var routerIp by remember { mutableStateOf("") }
    var routerUsername by remember { mutableStateOf("") }
    var routerPassword by remember { mutableStateOf("") }
    var wifiNotes by remember { mutableStateOf("") }

    var isPinned by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var routerAdminExpanded by remember { mutableStateOf(false) }
    var showRouterPassword by remember { mutableStateOf(false) }

    // Nearby Scanned SSIDs List Dialog
    var showScanDialog by remember { mutableStateOf(false) }
    var scannedSsids by remember { mutableStateOf<List<String>>(emptyList()) }

    val context = LocalContext.current
    val wifiManager = remember(context) {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    // Dynamic Permission Request Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            try {
                val results = wifiManager.scanResults
                scannedSsids = results.map { it.SSID }.filter { it.isNotBlank() }.distinct().sorted()
                showScanDialog = true
            } catch (e: SecurityException) {
                Toast.makeText(
                    context, 
                    "يرجى تفعيل الـ GPS وصلاحية الموقع الدقيق (Fine Location) للبحث عن الشبكات.", 
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(
                context, 
                "صلاحية الموقع الدقيق (Precise Location) مطلوبة لتمكين مسح شبكات الواي فاي.", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val scanAction = {
        val fineGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted) {
            try {
                val results = wifiManager.scanResults
                scannedSsids = results.map { it.SSID }.filter { it.isNotBlank() }.distinct().sorted()
                showScanDialog = true
            } catch (e: SecurityException) {
                Toast.makeText(
                    context, 
                    "يرجى التأكد من تفعيل الـ GPS وصلاحية الموقع الدقيق (Fine Location) للبحث عن الشبكات.", 
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Sync state with existing items once loaded
    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(existingItem) {
        if (existingItem != null && !hasInitialized) {
            title = existingItem.title
            ssid = existingItem.username
            wifiPassword = existingItem.password.concatToString()
            securityType = existingItem.appPackageName
            
            val details = parseWifiNotes(existingItem.notes)
            routerIp = details.routerIp
            routerUsername = details.routerUsername
            routerPassword = details.routerPassword
            wifiNotes = details.generalNotes
            isPinned = details.isPinned
            
            if (routerIp.isNotBlank() || routerUsername.isNotBlank() || routerPassword.isNotBlank()) {
                routerAdminExpanded = true
            }
            hasInitialized = true
        }
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "Add Wi-Fi" else "Edit Wi-Fi", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VaultTextPrimary) } },
                actions = {
                    TextButton(
                        onClick = {
                            if (ssid.isBlank()) {
                                Toast.makeText(context, "SSID is required", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            if (securityType != "Open" && wifiPassword.isBlank()) {
                                Toast.makeText(context, "Password is required for encrypted network", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }

                            val finalTitle = title.ifBlank { ssid }
                            val now = System.currentTimeMillis()
                            val combinedNotes = serializeWifiNotes(
                                WifiDetails(
                                    routerIp = routerIp,
                                    routerUsername = routerUsername,
                                    routerPassword = routerPassword,
                                    generalNotes = wifiNotes,
                                    isPinned = isPinned
                                )
                            )

                            val passwordItem = Password(
                                id = existingItem?.id ?: UUID.randomUUID().toString(),
                                title = finalTitle,
                                username = ssid,
                                password = wifiPassword.toCharArray(),
                                website = "vaultix://wifi",
                                appPackageName = securityType,
                                notes = combinedNotes,
                                passwordStrength = 0, // Auto calculated inside ViewModel
                                isFavorite = existingItem?.isFavorite ?: false,
                                createdAt = existingItem?.createdAt ?: now,
                                updatedAt = now
                            )

                            if (itemId == null) {
                                viewModel.insertPassword(passwordItem)
                            } else {
                                viewModel.updatePassword(passwordItem)
                            }
                            onSaved()
                        }
                    ) {
                        Text("Save", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
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
            // General Details Category Card
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Connection Details", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    
                    VaultTextField(
                        label = "Network Name (e.g. Home Wi-Fi)",
                        value = title,
                        onValueChange = { title = it },
                        leadingIcon = Icons.Default.Label
                    )

                    // SSID with scan button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            VaultTextField(
                                label = "SSID * (Network Name)",
                                value = ssid,
                                onValueChange = { ssid = it },
                                leadingIcon = Icons.Default.Wifi
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = scanAction,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(54.dp)
                                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Search, "Scan Nearby", tint = accentColor)
                        }
                    }

                    // Security Type Tabs Segmented Selector
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            "Security Type",
                            color = VaultTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val options = listOf("WPA/WPA2/WPA3", "WEP", "Open")
                            options.forEach { opt ->
                                val isSelected = securityType == opt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) accentColor.copy(alpha = 0.18f) else VaultSurfaceVariant,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) accentColor else VaultBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            securityType = opt
                                            if (opt == "Open") wifiPassword = ""
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opt,
                                        color = if (isSelected) accentColor else VaultTextSecondary,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Network Password Input
                    if (securityType != "Open") {
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = { Text("Password *") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = VaultTextSecondary) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null,
                                        tint = VaultTextSecondary
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = vaultTextFieldColors()
                        )
                    }
                }
            }

            // Accordion Router Admin Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { routerAdminExpanded = !routerAdminExpanded }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Router Admin Credentials",
                                color = VaultTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Icon(
                            if (routerAdminExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = VaultTextSecondary
                        )
                    }

                    AnimatedVisibility(visible = routerAdminExpanded) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            VaultTextField(
                                label = "Router IP / Address (e.g. 192.168.1.1)",
                                value = routerIp,
                                onValueChange = { routerIp = it },
                                leadingIcon = Icons.Default.SettingsInputComponent
                            )

                            VaultTextField(
                                label = "Admin Username",
                                value = routerUsername,
                                onValueChange = { routerUsername = it },
                                leadingIcon = Icons.Default.Person
                            )

                            OutlinedTextField(
                                value = routerPassword,
                                onValueChange = { routerPassword = it },
                                label = { Text("Admin Password") },
                                leadingIcon = { Icon(Icons.Default.LockOpen, null, tint = VaultTextSecondary) },
                                trailingIcon = {
                                    IconButton(onClick = { showRouterPassword = !showRouterPassword }) {
                                        Icon(
                                            if (showRouterPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            null,
                                            tint = VaultTextSecondary
                                        )
                                    }
                                },
                                visualTransformation = if (showRouterPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = vaultTextFieldColors()
                            )
                        }
                    }
                }
            }

            // General Notes Card
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VaultBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("General Notes", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    VaultTextField(
                        label = "Notes (e.g. router location, network limits)",
                        value = wifiNotes,
                        onValueChange = { wifiNotes = it },
                        leadingIcon = Icons.Default.Note,
                        singleLine = false,
                        minLines = 3
                    )
                }
            }
        }
    }

    // SSID Selection Dialog
    if (showScanDialog) {
        Dialog(
            onDismissRequest = { showScanDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 450.dp),
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Local Network", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                        IconButton(onClick = { showScanDialog = false }) {
                            Icon(Icons.Default.Close, null, tint = VaultTextSecondary)
                        }
                    }
                    Divider(color = VaultBorder, modifier = Modifier.padding(vertical = 10.dp))
                    
                    if (scannedSsids.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active scan results. Ensure Wi-Fi and Location are active, then scan again.",
                                color = VaultTextSecondary,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(scannedSsids) { ssidItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(VaultSurfaceVariant, RoundedCornerShape(10.dp))
                                        .clickable {
                                            ssid = ssidItem
                                            showScanDialog = false
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Wifi, null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(ssidItem, color = VaultTextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. Wifi Details View Screen
 */
@Composable
fun WifiDetailScreen(
    itemId: String,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: PasswordViewModel = hiltViewModel()
    val accentColor = MaterialTheme.colorScheme.primary
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wifi = remember(state.wifiPasswords, itemId) {
        state.wifiPasswords.find { it.id == itemId }
    }

    var showPassword by remember { mutableStateOf(false) }
    var showRouterPassword by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var copyMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    if (wifi == null) {
        Box(modifier = Modifier.fillMaxSize().background(VaultBlack), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accentColor)
        }
        return
    }

    val details = remember(wifi.notes) { parseWifiNotes(wifi.notes) }
    val isEncrypted = wifi.appPackageName != "Open"

    // Toast Copied Utility
    LaunchedEffect(copyMessage) {
        copyMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            copyMessage = null
        }
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(wifi.title, fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VaultTextPrimary) } },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = accentColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
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
            // Hero Connection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Wifi, null, tint = accentColor, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(wifi.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                            Text("Security: ${wifi.appPackageName}", fontSize = 13.sp, color = VaultTextSecondary)
                        }
                    }

                    Divider(color = VaultBorder, modifier = Modifier.padding(vertical = 16.dp))

                    // SSID Info Item
                    DetailItem(
                        label = "SSID (Network Name)",
                        value = wifi.username,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(wifi.username))
                            copyMessage = "SSID Copied"
                        }
                    )

                    // Password Info Item
                    if (isEncrypted) {
                        val passString = remember(wifi.password) { wifi.password.concatToString() }
                        Spacer(Modifier.height(14.dp))
                        DetailItem(
                            label = "Password",
                            value = passString,
                            isPassword = true,
                            showPassword = showPassword,
                            onToggleVisibility = { showPassword = !showPassword },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(passString))
                                copyMessage = "Password Copied"
                            }
                        )
                    }

                    // Share connection QR code button
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { showQrDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCode, null, tint = VaultBlack)
                        Spacer(Modifier.width(8.dp))
                        Text("Share via QR Code", color = VaultBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Router Admin Configurations Detail Card
            val hasRouterDetails = details.routerIp.isNotBlank() || details.routerUsername.isNotBlank() || details.routerPassword.isNotBlank()
            if (hasRouterDetails) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, VaultBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Router Admin Configuration", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Divider(color = VaultBorder, modifier = Modifier.padding(vertical = 12.dp))

                        if (details.routerIp.isNotBlank()) {
                            DetailItem(
                                label = "Router IP Address",
                                value = details.routerIp,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(details.routerIp))
                                    copyMessage = "Router IP Copied"
                                }
                            )
                        }

                        if (details.routerUsername.isNotBlank()) {
                            if (details.routerIp.isNotBlank()) Spacer(Modifier.height(14.dp))
                            DetailItem(
                                label = "Admin Username",
                                value = details.routerUsername,
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(details.routerUsername))
                                    copyMessage = "Admin Username Copied"
                                }
                            )
                        }

                        if (details.routerPassword.isNotBlank()) {
                            val ipAndUser = details.routerIp.isNotBlank() || details.routerUsername.isNotBlank()
                            if (ipAndUser) Spacer(Modifier.height(14.dp))
                            DetailItem(
                                label = "Admin Password",
                                value = details.routerPassword,
                                isPassword = true,
                                showPassword = showRouterPassword,
                                onToggleVisibility = { showRouterPassword = !showRouterPassword },
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(details.routerPassword))
                                    copyMessage = "Admin Password Copied"
                                }
                            )
                        }
                    }
                }
            }

            // General Notes Detail Card
            if (details.generalNotes.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VaultSurface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, VaultBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("General Notes", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Divider(color = VaultBorder, modifier = Modifier.padding(vertical = 12.dp))
                        Text(details.generalNotes, color = VaultTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }

    // Share Wifi Connection Dialog with standard QR Code format
    if (showQrDialog) {
        Dialog(
            onDismissRequest = { showQrDialog = false }
        ) {
            val passString = remember(wifi.password) { wifi.password.concatToString() }
            val qrBitmap = remember(wifi.username, passString, wifi.appPackageName) {
                WifiQRGenerator.generate(wifi.username, passString, wifi.appPackageName)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, accentColor)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Share Connection",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = VaultTextPrimary
                        )
                        IconButton(onClick = { showQrDialog = false }) {
                            Icon(Icons.Default.Close, null, tint = VaultTextSecondary)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    // QR Card Canvas Area (White Background for High Contrast Scanning)
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Wi-Fi QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: Text("QR Error", color = VaultBlack)
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = wifi.username,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultTextPrimary
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Scan with your phone's camera to connect to this Wi-Fi network instantly.",
                        fontSize = 12.sp,
                        color = VaultTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    if (isEncrypted) {
                        Divider(color = VaultBorder, modifier = Modifier.padding(vertical = 14.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(VaultSurfaceVariant, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Password", fontSize = 10.sp, color = VaultTextSecondary)
                                Text(passString, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                            }
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(passString))
                                    copyMessage = "Password Copied"
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, tint = accentColor, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper detail row template with copy/toggle visibility integration
 */
@Composable
private fun DetailItem(
    label: String,
    value: String,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onToggleVisibility: () -> Unit = {},
    onCopy: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultSurfaceVariant, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = VaultTextSecondary.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isPassword && !showPassword) "••••••••••••" else value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = VaultTextPrimary
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isPassword) {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
