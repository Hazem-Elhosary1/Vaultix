package com.vaultix.app.ui.screens

import androidx.compose.animation.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vaultix.app.R
import com.vaultix.app.data.model.*
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.*

@Composable
fun CategoryScreen(
    categoryType: String,
    authViewModel: AuthViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAdd: () -> Unit,
    onBack: () -> Unit,
    onNavigateToScan: () -> Unit = {},
    onNavigateToFileVault: () -> Unit = {},
    onNavigateToIdentityEdit: (String?) -> Unit = {}
) {
    val title = when (categoryType) {
        "passwords" -> stringResource(R.string.passwords)
        "cards" -> stringResource(R.string.cards)
        "notes" -> stringResource(R.string.notes)
        "files" -> stringResource(R.string.files)
        "identities" -> stringResource(R.string.identities)
        else -> stringResource(R.string.app_name)
    }
    val accentColor = MaterialTheme.colorScheme.primary

    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_vault), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }}
                    } else null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                        cursorColor = accentColor,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        floatingActionButton = {
            when (categoryType) {
                "cards" -> {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Scan FAB
                        SmallFloatingActionButton(
                            onClick = onNavigateToScan,
                            containerColor = CategoryCards,
                            contentColor = VaultBlack
                        ) {
                            Icon(Icons.Default.CameraAlt, "Scan Card")
                        }
                        // Add FAB
                        FloatingActionButton(
                            onClick = onNavigateToAdd,
                            containerColor = VaultOrange,
                            contentColor = VaultBlack,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add")
                        }
                    }
                }
                "files" -> {
                    FloatingActionButton(
                        onClick = onNavigateToFileVault,
                        containerColor = VaultOrange,
                        contentColor = VaultBlack,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Upload, "Import File")
                    }
                }
                "identities" -> {
                    FloatingActionButton(
                        onClick = { onNavigateToIdentityEdit(null) },
                        containerColor = VaultOrange,
                        contentColor = VaultBlack,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, "Add ID")
                    }
                }
                else -> {
                    FloatingActionButton(
                        onClick = onNavigateToAdd,
                        containerColor = accentColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.add))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (categoryType) {
                "passwords" -> PasswordList(
                    searchQuery = searchQuery,
                    onItemClick = onNavigateToDetail,
                    accentColor = accentColor
                )
                "cards" -> CardList(
                    searchQuery = searchQuery,
                    onItemClick = onNavigateToDetail,
                    accentColor = accentColor
                )
                "notes" -> NoteList(
                    searchQuery = searchQuery,
                    onItemClick = onNavigateToDetail,
                    accentColor = accentColor
                )
                "files" -> FileList(accentColor = accentColor)
                "identities" -> IdentityList(
                    searchQuery = searchQuery,
                    accentColor = accentColor,
                    onItemClick = { id -> onNavigateToDetail(id) }
                )
                else -> EmptyState(categoryType, Icons.Default.Folder, accentColor)
            }
        }
    }
}

@Composable
private fun PasswordList(
    searchQuery: String,
    onItemClick: (String) -> Unit,
    accentColor: Color
) {
    val viewModel: PasswordViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }

    if (state.passwords.isEmpty()) {
        EmptyState(stringResource(R.string.passwords), Icons.Default.VpnKey, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.passwords, key = { it.id }) { password ->
            PasswordListItem(
                password = password,
                accentColor = accentColor,
                onClick = { onItemClick(password.id) },
                onDelete = { viewModel.deletePassword(password.id) }
            )
        }
    }
}

@Composable
private fun PasswordListItem(
    password: Password,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    password.title.take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(password.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(password.username, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (password.website.isNotEmpty()) {
                    Text(password.website, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                }
            }

            // Strength indicator
            StrengthDot(strength = password.passwordStrength)

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = { showDelete = !showDelete }) {
                Icon(Icons.Default.MoreVert, null, tint = VaultTextSecondary)
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_password)) },
            text = { Text(stringResource(R.string.delete_confirm, password.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun StrengthDot(strength: Int) {
    val color = when (strength) {
        0 -> StrengthVeryWeak
        1 -> StrengthWeak
        2 -> StrengthFair
        3 -> StrengthStrong
        else -> StrengthVeryStrong
    }
    Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
}

@Composable
private fun CardList(
    searchQuery: String,
    onItemClick: (String) -> Unit,
    accentColor: Color
) {
    val viewModel: CardViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filtered = remember(state.cards, searchQuery) {
        if (searchQuery.isBlank()) state.cards
        else state.cards.filter { it.cardName.contains(searchQuery, true) || it.holderName.contains(searchQuery, true) }
    }

    if (filtered.isEmpty()) {
        EmptyState(stringResource(R.string.cards), Icons.Default.CreditCard, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filtered, key = { it.id }) { card ->
            CardListItem(card = card, onClick = { onItemClick(card.id) }, onDelete = { viewModel.deleteCard(card.id) })
        }
    }
}

@Composable
private fun CardListItem(card: Card, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(VaultNavy, VaultNavyLight)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(card.cardName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondary)
                    Text(card.cardType, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondary.copy(0.8f), fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(16.dp))
                Text(card.maskedCardNumber, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondary, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(R.string.accent_color).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondary.copy(0.6f), letterSpacing = 1.sp)
                        Text(card.holderName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Medium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.appearance).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondary.copy(0.6f), letterSpacing = 1.sp)
                        Text("${card.expiryMonth}/${card.expiryYear}", fontSize = 14.sp, color = if (card.isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteList(
    searchQuery: String,
    onItemClick: (String) -> Unit,
    accentColor: Color
) {
    val viewModel: NoteViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }

    if (state.notes.isEmpty()) {
        EmptyState(stringResource(R.string.notes), Icons.Default.Description, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.notes, key = { it.id }) { note ->
            NoteListItem(note = note, onClick = { onItemClick(note.id) }, onDelete = { viewModel.deleteNote(note.id) })
        }
    }
}

@Composable
private fun NoteListItem(note: Note, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(note.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                note.content.take(80) + if (note.content.length > 80) "..." else "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun EmptyState(category: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accentColor: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = accentColor.copy(alpha = 0.2f), modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.nothing_here), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_category_desc, category), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun FileList(accentColor: Color) {
    val viewModel: FileViewModel = hiltViewModel()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<com.vaultix.app.data.model.VaultFile?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var previewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isPreviewing by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { selectedFile?.let { file -> viewModel.exportFile(file, it, context) } }
    }

    if (files.isEmpty()) {
        EmptyState(stringResource(R.string.files), Icons.Default.FolderOpen, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "${files.size} encrypted file${if (files.size > 1) "s" else ""}",
                color = VaultTextSecondary,
                fontSize = 13.sp
            )
        }
        items(files, key = { it.id }) { file ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { 
                    selectedFile = file
                    showActionSheet = true 
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).background(accentColor.copy(0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                file.mimeType.startsWith("image") -> Icons.Default.Image
                                file.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.fileName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text("${(file.fileSizeBytes / 1024)} KB • 🔒 Encrypted", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        }
    }

    // Action Sheet
    if (showActionSheet && selectedFile != null) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(
                    selectedFile!!.fileName,
                    Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // View (Images only for now)
                if (selectedFile!!.mimeType.startsWith("image")) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.ocr)) },
                        leadingContent = { Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            scope.launch {
                                previewBytes = viewModel.getDecryptedBytes(selectedFile!!)
                                isPreviewing = true
                                showActionSheet = false
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = MaterialTheme.colorScheme.onSurface)
                    )
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_backup)) },
                    leadingContent = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        exportLauncher.launch(selectedFile!!.fileName)
                        showActionSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = MaterialTheme.colorScheme.onSurface)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        viewModel.deleteFile(selectedFile!!.id)
                        showActionSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    // Image Preview Overlay
    if (isPreviewing && previewBytes != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isPreviewing = false; previewBytes = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes!!.size)
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                
                IconButton(
                    onClick = { isPreviewing = false; previewBytes = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}


@Composable
private fun IdentityList(
    searchQuery: String,
    accentColor: Color,
    onItemClick: (String) -> Unit
) {
    val viewModel: IdentityViewModel = hiltViewModel()
    val identities by viewModel.allIdentities.collectAsStateWithLifecycle()

    val filtered = remember(identities, searchQuery) {
        if (searchQuery.isBlank()) identities
        else identities.filter {
            it.fullName.contains(searchQuery, true) ||
            it.documentName.contains(searchQuery, true) ||
            it.documentNumber.contains(searchQuery, true)
        }
    }

    if (filtered.isEmpty()) {
        EmptyState(stringResource(R.string.identities), Icons.Default.Badge, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filtered, key = { it.id }) { identity ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onItemClick(identity.id) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).background(accentColor.copy(0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (identity.documentType.lowercase()) {
                                "passport" -> Icons.Default.Flight
                                "driver license" -> Icons.Default.DirectionsCar
                                else -> Icons.Default.Badge
                            },
                            null, tint = accentColor, modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(identity.documentName.ifEmpty { identity.documentType }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(identity.fullName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        
                        // Check if expired
                        val isExpired = remember(identity.expiryDate) {
                            try {
                                if (identity.expiryDate.isEmpty()) false
                                else {
                                    val parts = identity.expiryDate.split("/", "-")
                                    val calendar = java.util.Calendar.getInstance()
                                    if (parts[0].length == 4) {
                                        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                                    } else {
                                        calendar.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                                    }
                                    java.util.Calendar.getInstance().after(calendar)
                                }
                            } catch (_: Exception) { false }
                        }
                        
                        if (isExpired) {
                            Text("EXPIRED", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                        } else if (identity.expiryDate.isNotEmpty()) {
                            Text("Expires: ${identity.expiryDate}", fontSize = 11.sp, color = VaultWarning)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        }
    }
}
