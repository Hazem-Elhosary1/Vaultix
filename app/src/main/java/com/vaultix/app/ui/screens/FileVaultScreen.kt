package com.vaultix.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultix.app.R
import com.vaultix.app.data.model.VaultFile
import com.vaultix.app.data.model.VaultFolder
import com.vaultix.app.ui.theme.*
import androidx.activity.result.IntentSenderRequest
import com.vaultix.app.ui.viewmodel.AuthViewModel
import com.vaultix.app.ui.viewmodel.FileViewModel
import com.vaultix.app.util.DocumentScannerHelper
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt

/**
 * State class to manage the active Drag and Drop session inside the File Vault.
 */
class DragDropState {
    var draggedItemId by mutableStateOf<String?>(null)
    var draggedItemType by mutableStateOf<String?>(null) // "file" or "folder"
    var draggedItemName by mutableStateOf("")
    var dragOffset by mutableStateOf(Offset.Zero)
    var dragPosition by mutableStateOf(Offset.Zero)
    var isDragging by mutableStateOf(false)
    
    // Bounds of potential drop targets (folders/root) on screen
    val folderBounds = mutableStateMapOf<String, Rect>()
    var currentHoveredFolderId by mutableStateOf<String?>(null)
}

/**
 * File Vault screen: import files, encrypt them with AES-256-GCM,
 * store encrypted copies in the app's internal storage, and navigate using breadcrumbs.
 */
@Composable
fun FileVaultScreen(
    fileViewModel: FileViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onViewPdf: (String, String) -> Unit,
    onViewImage: (String) -> Unit,
    onNavigateToPremium: () -> Unit,
    appConfigViewModel: com.vaultix.app.ui.viewmodel.AppConfigViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val files by fileViewModel.files.collectAsState()
    val folders by fileViewModel.folders.collectAsState()
    val currentFolderId by fileViewModel.currentFolderId.collectAsState()
    val configState by appConfigViewModel.configState.collectAsState()
    
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showProRequiredDialog by remember(configState.isPremium) { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val dragDropState = remember { DragDropState() }
    
    val currentFiles = files.filter { 
        it.folderId == currentFolderId && 
        (searchQuery.isBlank() || it.fileName.contains(searchQuery, true))
    }
    val currentFolders = folders.filter { 
        it.parentFolderId == currentFolderId && 
        (searchQuery.isBlank() || it.name.contains(searchQuery, true))
    }
    
    val currentFolderName = folders.find { it.id == currentFolderId }?.name ?: "File Vault"

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanningResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            
            // Prioritize PDF if generated
            val pdf = scanningResult?.pdf
            if (pdf != null) {
                if (!configState.isPremium && files.size >= 5) {
                    showProRequiredDialog = true
                } else {
                    val pdfUri = pdf.uri
                    val fileName = "Scanned_Doc_${System.currentTimeMillis()}.pdf"
                    val mimeType = "application/pdf"
                    fileViewModel.importFile(pdfUri, fileName, mimeType)
                    Toast.makeText(context, "Scanned PDF imported & encrypted!", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Fallback to pages as JPEG
                val pages = scanningResult?.pages ?: emptyList()
                if (pages.isNotEmpty()) {
                    if (!configState.isPremium && (files.size + pages.size) > 5) {
                        showProRequiredDialog = true
                    } else {
                        pages.forEachIndexed { index, page ->
                            val pageUri = page.imageUri
                            val suffix = if (pages.size > 1) "_Page_${index + 1}" else ""
                            val fileName = "Scanned_Doc_${System.currentTimeMillis()}$suffix.jpg"
                            val mimeType = "image/jpeg"
                            fileViewModel.importFile(pageUri, fileName, mimeType)
                        }
                        Toast.makeText(context, "Scanned page(s) imported & encrypted!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // SAF file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (!configState.isPremium && files.size >= 5) {
                showProRequiredDialog = true
            } else {
                // Get file name from URI
                val cursor = context.contentResolver.query(it, null, null, null, null)
                val fileName = cursor?.use { c ->
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    c.moveToFirst()
                    if (nameIndex >= 0) c.getString(nameIndex) else "Unknown"
                } ?: "Unknown"
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"

                fileViewModel.importFile(it, fileName, mimeType)
                Toast.makeText(context, "File encrypted & imported!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showProRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showProRequiredDialog = false },
            containerColor = VaultSurface,
            icon = { Icon(Icons.Default.WorkspacePremium, null, tint = VaultOrange, modifier = Modifier.size(48.dp)) },
            title = { Text("Pro Feature Required", color = VaultTextPrimary) },
            text = { Text("Free version is limited to 5 files. Upgrade to Pro for unlimited file storage and more!", color = VaultTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { 
                        showProRequiredDialog = false
                        onNavigateToPremium()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) { Text("Upgrade Now", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showProRequiredDialog = false }) {
                    Text("Maybe Later", color = VaultTextSecondary)
                }
            }
        )
    }

    // Build the hierarchical breadcrumb trail from root up to current folder
    val breadcrumbs = remember(currentFolderId, folders) {
        val list = mutableListOf<Pair<String?, String>>()
        var currentId = currentFolderId
        while (currentId != null) {
            val folder = folders.find { it.id == currentId }
            if (folder != null) {
                list.add(0, Pair(folder.id, folder.name))
                currentId = folder.parentFolderId
            } else {
                break
            }
        }
        list.add(0, Pair(null, "File Vault")) // Root folder
        list
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("File Vault", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = { 
                            if (currentFolderId != null) {
                                val parentId = folders.find { it.id == currentFolderId }?.parentFolderId
                                fileViewModel.navigateToFolder(parentId)
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(if (currentFolderId != null) Icons.Default.ArrowUpward else Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VaultTextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, "New Folder", tint = VaultOrange)
                        }
                        IconButton(onClick = {
                            showImportSheet = true
                        }) {
                            Icon(Icons.Default.Add, "Import File", tint = VaultOrange)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
                )
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search files & folders...", color = VaultTextDisabled) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = VaultTextDisabled) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = VaultTextDisabled)
                        }}
                    } else null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultOrange,
                        unfocusedBorderColor = VaultBorder,
                        focusedContainerColor = VaultSurface.copy(0.3f),
                        unfocusedContainerColor = VaultSurface.copy(0.3f),
                        cursorColor = VaultOrange,
                        focusedTextColor = VaultTextPrimary,
                        unfocusedTextColor = VaultTextPrimary
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportSheet = true },
                containerColor = VaultOrange,
                contentColor = VaultBlack
            ) {
                Icon(Icons.Default.Add, "Import")
            }
        }
    ) { padding ->
        val isEmpty = currentFiles.isEmpty() && currentFolders.isEmpty()
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(VaultBlack, VaultNavy)))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Navigable breadcrumb trail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .background(VaultSurface.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breadcrumbs.forEachIndexed { index, segment ->
                        val isLast = index == breadcrumbs.lastIndex
                        val isHovered = dragDropState.currentHoveredFolderId == (segment.first ?: "root")
                        
                        Text(
                            text = segment.second,
                            color = when {
                                isHovered -> VaultOrange
                                isLast -> VaultOrangeLight
                                else -> VaultTextSecondary
                            },
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable(!isLast) {
                                    fileViewModel.navigateToFolder(segment.first)
                                }
                                .onGloballyPositioned { coords ->
                                    // Register breadcrumb coordinates as drop targets (so users can drag items back to parent folders)
                                    val targetId = segment.first ?: "root"
                                    if (coords.isAttached) {
                                        dragDropState.folderBounds[targetId] = coords.boundsInRoot()
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )
                        if (!isLast) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = VaultTextDisabled,
                                modifier = Modifier.padding(horizontal = 6.dp).size(14.dp)
                            )
                        }
                    }
                }

                // Main Content area
                Box(modifier = Modifier.weight(1f)) {
                    if (isEmpty) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (currentFolderId == null) Icons.Default.FolderOpen else Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = VaultTextDisabled,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (currentFolderId == null) "No files yet" else "This folder is empty",
                                color = VaultTextSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap + to import and encrypt files",
                                color = VaultTextDisabled,
                                fontSize = 14.sp
                            )
                            if (currentFolderId == null) {
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { showImportSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Upload, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Import File", color = VaultBlack, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        // File and Folder List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentFolderName,
                                        color = VaultTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "${currentFiles.size + currentFolders.size} items",
                                        color = VaultTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Folders
                            items(currentFolders, key = { it.id }) { folder ->
                                FolderItem(
                                    folder = folder,
                                    onClick = { fileViewModel.navigateToFolder(folder.id) },
                                    onDelete = { fileViewModel.deleteFolder(folder.id) },
                                    dragDropState = dragDropState,
                                    fileViewModel = fileViewModel,
                                    files = files,
                                    folders = folders
                                )
                            }

                            // Files
                            items(currentFiles, key = { it.id }) { file ->
                                FileVaultItem(
                                    file = file,
                                    onDelete = { fileViewModel.deleteFile(file.id) },
                                    onView = {
                                        when {
                                            file.mimeType.contains("pdf") -> onViewPdf(file.id, file.fileName)
                                            file.mimeType.startsWith("image/") -> onViewImage(file.encryptedFilePath)
                                            else -> Toast.makeText(context, "Cannot preview this type securely yet.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    dragDropState = dragDropState,
                                    fileViewModel = fileViewModel
                                )
                            }
                        }
                    }
                }
            }

            // Create New Folder Dialog
            if (showNewFolderDialog) {
                var folderName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showNewFolderDialog = false },
                    containerColor = VaultSurface,
                    title = { Text("New Folder", color = VaultOrange) },
                    text = {
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            label = { Text("Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VaultOrange, unfocusedBorderColor = VaultBorder)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { 
                                if (folderName.isNotBlank()) {
                                    fileViewModel.createFolder(folderName)
                                    showNewFolderDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                        ) { Text("Create", color = VaultBlack) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // Encrypting Overlay
            val isImporting by fileViewModel.isImporting.collectAsState()
            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VaultBlack.copy(alpha = 0.7f))
                        .clickable(enabled = false) { },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = VaultOrange)
                        Spacer(Modifier.height(16.dp))
                        Text("Encrypting & Importing...", color = VaultTextPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Floating drag preview item under the dragging finger (Enhanced Large Preview)
            if (dragDropState.isDragging && dragDropState.draggedItemId != null) {
                val cardWidthDp = 260.dp
                val cardHeightOffsetDp = 75.dp
                Card(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (dragDropState.dragPosition.x - cardWidthDp.toPx() / 2f).roundToInt(),
                                y = (dragDropState.dragPosition.y - cardHeightOffsetDp.toPx()).roundToInt()
                            )
                        }
                        .width(cardWidthDp)
                        .alpha(0.9f),
                    colors = CardDefaults.cardColors(containerColor = VaultOrange),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                    border = BorderStroke(2.dp, VaultBlack.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(VaultBlack.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (dragDropState.draggedItemType == "folder") Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = VaultBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = dragDropState.draggedItemName,
                            color = VaultBlack,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            containerColor = VaultSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = VaultBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = "Import / Add File",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ListItem(
                    headlineContent = { Text("Import File", color = VaultTextPrimary) },
                    supportingContent = { Text("Select any file or photo from your device", color = VaultTextSecondary) },
                    leadingContent = { Icon(Icons.Default.UploadFile, "Import", tint = VaultOrange) },
                    modifier = Modifier.clickable {
                        filePickerLauncher.launch(arrayOf("*/*"))
                        showImportSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                ListItem(
                    headlineContent = { Text("Scan Document", color = VaultTextPrimary) },
                    supportingContent = { Text("Scan paper documents or photos with auto-edge detection", color = VaultTextSecondary) },
                    leadingContent = { Icon(Icons.Default.DocumentScanner, "Scan", tint = VaultOrange) },
                    modifier = Modifier.clickable {
                        showImportSheet = false
                        authViewModel.setSystemActivityActive(true)
                        DocumentScannerHelper.startScan(
                            context = context,
                            options = DocumentScannerHelper.createScannerOptions(pageLimit = 5, isPdfEnabled = true),
                            onIntentSenderReady = { intentSender ->
                                scannerLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                                )
                            },
                            onFailure = { e ->
                                Toast.makeText(context, "Scanner error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun FolderItem(
    folder: VaultFolder,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dragDropState: DragDropState,
    fileViewModel: FileViewModel,
    files: List<VaultFile>,
    folders: List<VaultFolder>
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    
    // Safety check: check if the folder is empty
    val hasContents = remember(files, folders, folder.id) {
        files.any { it.folderId == folder.id } || folders.any { it.parentFolderId == folder.id }
    }
    
    val isHovered = dragDropState.currentHoveredFolderId == folder.id
    val borderStroke = if (isHovered) BorderStroke(2.dp, VaultOrange) else null
    val containerColor = if (isHovered) VaultOrange.copy(alpha = 0.15f) else VaultSurface.copy(alpha = 0.7f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .onGloballyPositioned { coordinates ->
                itemCoordinates = coordinates
                if (coordinates.isAttached) {
                    // Register the folder's bounding box as an active drop target
                    dragDropState.folderBounds[folder.id] = coordinates.boundsInRoot()
                }
            }
            .pointerInput(folder.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragDropState.draggedItemId = folder.id
                        dragDropState.draggedItemType = "folder"
                        dragDropState.draggedItemName = folder.name
                        dragDropState.dragOffset = Offset.Zero
                        dragDropState.dragPosition = itemCoordinates?.localToRoot(offset) ?: Offset.Zero
                        dragDropState.isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDropState.dragOffset += dragAmount
                        val rootPos = itemCoordinates?.localToRoot(change.position)
                        if (rootPos != null) {
                            dragDropState.dragPosition = rootPos
                            // Find which folder bounds contain the pointer, excluding itself and its descendants
                            dragDropState.currentHoveredFolderId = dragDropState.folderBounds.entries
                                .find { entry ->
                                    entry.key != folder.id && 
                                    !fileViewModel.isFolderDescendantOf(folder.id, entry.key) &&
                                    entry.value.contains(rootPos)
                                }?.key
                        }
                    },
                    onDragEnd = {
                        dragDropState.isDragging = false
                        val target = dragDropState.currentHoveredFolderId
                        if (target != null) {
                            val destId = if (target == "root") null else target
                            fileViewModel.moveFolderToFolder(folder.id, destId)
                        }
                        dragDropState.draggedItemId = null
                        dragDropState.currentHoveredFolderId = null
                    },
                    onDragCancel = {
                        dragDropState.isDragging = false
                        dragDropState.draggedItemId = null
                        dragDropState.currentHoveredFolderId = null
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, null, tint = VaultOrange, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Text(folder.name, color = VaultTextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, null, tint = VaultTextDisabled, modifier = Modifier.size(20.dp))
            }
        }
    }
    
    // Safety check Folder deletion alerts (Fully localized using strings.xml)
    if (showDeleteDialog) {
        if (hasContents) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = VaultSurface,
                title = { Text(stringResource(R.string.folder_not_empty_title), color = VaultOrange, fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.folder_not_empty_msg), color = VaultTextPrimary) },
                confirmButton = {
                    Button(
                        onClick = { showDeleteDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                    ) { Text(stringResource(R.string.ok_action), color = VaultBlack, fontWeight = FontWeight.SemiBold) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = VaultSurface,
                title = { Text(stringResource(R.string.delete_empty_folder_title), color = VaultTextPrimary) },
                text = { Text(stringResource(R.string.delete_empty_folder_msg), color = VaultTextSecondary) },
                confirmButton = {
                    Button(
                        onClick = { onDelete(); showDeleteDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = VaultError)
                    ) { Text(stringResource(R.string.delete), color = VaultTextPrimary) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
private fun FileVaultItem(
    file: VaultFile,
    onDelete: () -> Unit,
    onView: () -> Unit,
    dragDropState: DragDropState,
    fileViewModel: FileViewModel
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView)
            .onGloballyPositioned { coords ->
                itemCoordinates = coords
            }
            .pointerInput(file.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragDropState.draggedItemId = file.id
                        dragDropState.draggedItemType = "file"
                        dragDropState.draggedItemName = file.fileName
                        dragDropState.dragOffset = Offset.Zero
                        dragDropState.dragPosition = itemCoordinates?.localToRoot(offset) ?: Offset.Zero
                        dragDropState.isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDropState.dragOffset += dragAmount
                        val rootPos = itemCoordinates?.localToRoot(change.position)
                        if (rootPos != null) {
                            dragDropState.dragPosition = rootPos
                            // Check which folder bounds contain the finger pointer
                            dragDropState.currentHoveredFolderId = dragDropState.folderBounds.entries
                                .find { entry ->
                                    entry.value.contains(rootPos)
                                }?.key
                        }
                    },
                    onDragEnd = {
                        dragDropState.isDragging = false
                        val target = dragDropState.currentHoveredFolderId
                        if (target != null) {
                            val destId = if (target == "root") null else target
                            fileViewModel.moveFileToFolder(file.id, destId)
                        }
                        dragDropState.draggedItemId = null
                        dragDropState.currentHoveredFolderId = null
                    },
                    onDragCancel = {
                        dragDropState.isDragging = false
                        dragDropState.draggedItemId = null
                        dragDropState.currentHoveredFolderId = null
                    }
                )
            }
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(getFileIconColor(file.mimeType).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileIcon(file.mimeType),
                    contentDescription = null,
                    tint = getFileIconColor(file.mimeType),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    color = VaultTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = formatFileSize(file.fileSizeBytes),
                        color = VaultTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(" • ", color = VaultTextDisabled, fontSize = 12.sp)
                    Text(
                        text = "🔒 Encrypted",
                        color = VaultSuccess.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = VaultTextDisabled, modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = VaultSurface,
            title = { Text("Delete File?", color = VaultTextPrimary) },
            text = { Text("This will permanently delete the encrypted file.", color = VaultTextSecondary) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultError)
                ) { Text("Delete", color = VaultTextPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = VaultTextSecondary)
                }
            }
        )
    }
}

private fun getFileIcon(mimeType: String) = when {
    mimeType.startsWith("image/") -> Icons.Default.Image
    mimeType.startsWith("video/") -> Icons.Default.VideoFile
    mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
    mimeType.contains("document") || mimeType.contains("text") -> Icons.Default.Description
    else -> Icons.Default.InsertDriveFile
}

private fun getFileIconColor(mimeType: String) = when {
    mimeType.startsWith("image/") -> CategoryFiles
    mimeType.startsWith("video/") -> VaultError
    mimeType.startsWith("audio/") -> CategoryIDs
    mimeType.contains("pdf") -> VaultError
    else -> VaultOrange
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
