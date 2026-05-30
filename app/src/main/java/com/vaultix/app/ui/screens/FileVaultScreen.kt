package com.vaultix.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultix.app.data.model.VaultFile
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.FileViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.graphics.Color

/**
 * File Vault screen: import files, encrypt them with AES-256-GCM,
 * and store encrypted copies in the app's internal storage.
 */
@Composable
fun FileVaultScreen(
    fileViewModel: FileViewModel,
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
    
    val currentFiles = files.filter { it.folderId == currentFolderId }
    val currentFolders = folders.filter { it.parentFolderId == currentFolderId }
    
    val currentFolderName = folders.find { it.id == currentFolderId }?.name ?: "File Vault"

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

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
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
                        Icon(if (currentFolderId != null) Icons.Default.ArrowUpward else Icons.Default.ArrowBack, "Back", tint = VaultTextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, "New Folder", tint = VaultOrange)
                    }
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.Add, "Import File", tint = VaultOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
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
            if (isEmpty) {
                // Empty state (centered in Box)
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
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
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
                // File explorer list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // Breadcrumbs / Info
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

                    // List Folders
                    items(currentFolders, key = { it.id }) { folder ->
                        FolderItem(
                            folder = folder,
                            onClick = { fileViewModel.navigateToFolder(folder.id) },
                            onDelete = { fileViewModel.deleteFolder(folder.id) }
                        )
                    }

                    // List Files
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
                            }
                        )
                    }
                }
            }

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
        }
    }
}

@Composable
private fun FolderItem(
    folder: com.vaultix.app.data.model.VaultFolder,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface.copy(alpha = 0.7f))
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
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = VaultSurface,
            title = { Text("Delete Folder?", color = VaultTextPrimary) },
            text = { Text("The folder will be deleted. Files inside will be moved to the root vault.", color = VaultTextSecondary) },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = VaultError)) { 
                    Text("Delete", color = VaultTextPrimary) 
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FileVaultItem(
    file: VaultFile,
    onDelete: () -> Unit,
    onView: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView)
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
            // File icon
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
