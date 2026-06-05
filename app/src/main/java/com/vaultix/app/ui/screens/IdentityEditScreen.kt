package com.vaultix.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.result.IntentSenderRequest
import androidx.compose.ui.graphics.Color
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import com.vaultix.app.ui.viewmodel.IdentityViewModel
import com.vaultix.app.util.DocumentScannerHelper
import androidx.compose.ui.res.stringResource
import com.vaultix.app.R

/**
 * Identity document add/edit screen with photo support.
 * Photos are encrypted before storage.
 */
@Composable
fun IdentityEditScreen(
    identityViewModel: IdentityViewModel,
    authViewModel: AuthViewModel,
    onClose: () -> Unit,
    existingId: String? = null
) {
    val context = LocalContext.current
    val identityState by identityViewModel.identity.collectAsState()
    var showSelectionSheet by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { identityViewModel.addImage(it) } }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanningResult = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                identityViewModel.addImage(uri)
            }
        }
    }

    LaunchedEffect(existingId) {
        if (existingId != null) {
            identityViewModel.loadIdentity(existingId)
        } else {
            identityViewModel.resetState()
        }
    }

    // Document type dropdown
    val documentTypes = listOf(
        Pair("Passport", R.string.doc_type_passport),
        Pair("Driver License", R.string.doc_type_driver_license),
        Pair("National ID", R.string.doc_type_national_id),
        Pair("Residence Permit", R.string.doc_type_residence_permit),
        Pair("Other", R.string.doc_type_other)
    )
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (existingId == null) R.string.add_new else R.string.edit_item),
                        fontWeight = FontWeight.Bold,
                        color = VaultTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, stringResource(R.string.close), tint = VaultTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(VaultBlack, VaultNavy)
                    )
                )
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Photo Section ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = VaultSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        identityState.imagePaths.forEach { path ->
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VaultSurfaceVariant)
                            ) {
                                val bitmap = remember(path) {
                                    identityViewModel.decryptImage(path)?.asImageBitmap()
                                }
                                bitmap?.let {
                                    Image(
                                        bitmap = it,
                                        contentDescription = "Identity photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                IconButton(
                                    onClick = { identityViewModel.removeImage(path) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(VaultBlack.copy(0.6f), RoundedCornerShape(bottomStart = 8.dp))
                                ) {
                                    Icon(Icons.Default.Close, null, tint = VaultError, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (identityState.imagePaths.size < 3) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VaultSurfaceVariant.copy(0.5f))
                                    .clickable { showSelectionSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddAPhoto, null, tint = VaultOrange)
                                    Text(stringResource(R.string.add), color = VaultOrange, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    if (identityState.imagePaths.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.add_photos_hint), color = VaultTextDisabled, fontSize = 12.sp)
                    }
                }
            }

            // ── Document Type Dropdown ──
            val displayedType = remember(identityState.documentType) {
                documentTypes.find { it.first == identityState.documentType }?.second?.let { context.getString(it) } 
                    ?: identityState.documentType.ifEmpty { context.getString(R.string.select_type) }
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = displayedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.document_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = identityFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    documentTypes.forEach { (englishKey, stringResId) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(stringResId)) },
                            onClick = {
                                identityViewModel.updateDocumentType(englishKey)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // ── Form Fields ──
            OutlinedTextField(
                value = identityState.documentName,
                onValueChange = { identityViewModel.updateDocumentName(it) },
                label = { Text(stringResource(R.string.document_name_label)) },
                placeholder = { Text(stringResource(R.string.document_name_placeholder), color = VaultTextHint) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.documentNumber,
                onValueChange = { identityViewModel.updateDocumentNumber(it) },
                label = { Text(stringResource(R.string.document_number_label)) },
                leadingIcon = { Icon(Icons.Default.Numbers, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.fullName,
                onValueChange = { identityViewModel.updateFullName(it) },
                label = { Text(stringResource(R.string.full_name_label)) },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.dateOfBirth,
                onValueChange = { identityViewModel.updateDateOfBirth(it) },
                label = { Text(stringResource(R.string.dob_label)) },
                placeholder = { Text(stringResource(R.string.date_format_placeholder), color = VaultTextHint) },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.nationality,
                onValueChange = { identityViewModel.updateNationality(it) },
                label = { Text(stringResource(R.string.nationality_label_field)) },
                leadingIcon = { Icon(Icons.Default.Flag, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.issuedBy,
                onValueChange = { identityViewModel.updateIssuedBy(it) },
                label = { Text(stringResource(R.string.issued_by_label)) },
                leadingIcon = { Icon(Icons.Default.AccountBalance, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = identityState.issuedDate,
                    onValueChange = { identityViewModel.updateIssuedDate(it) },
                    label = { Text(stringResource(R.string.issued_date_label)) },
                    placeholder = { Text(stringResource(R.string.date_format_placeholder), color = VaultTextHint) },
                    modifier = Modifier.weight(1f),
                    colors = identityFieldColors()
                )
                OutlinedTextField(
                    value = identityState.expiryDate,
                    onValueChange = { identityViewModel.updateExpiryDate(it) },
                    label = { Text(stringResource(R.string.expiry_date_label)) },
                    placeholder = { Text(stringResource(R.string.date_format_placeholder), color = VaultTextHint) },
                    modifier = Modifier.weight(1f),
                    colors = identityFieldColors()
                )
            }

            OutlinedTextField(
                value = identityState.notes,
                onValueChange = { identityViewModel.updateNotes(it) },
                label = { Text(stringResource(R.string.notes_optional_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 4,
                colors = identityFieldColors()
            )

            Spacer(Modifier.height(8.dp))

            // ── Save Button ──
            Button(
                onClick = {
                    identityViewModel.saveIdentity()
                    Toast.makeText(context, context.getString(R.string.identity_saved_success), Toast.LENGTH_SHORT).show()
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
            ) {
                Icon(Icons.Default.Save, null, tint = VaultBlack)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save_identity_button), color = VaultBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSelectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSelectionSheet = false },
            containerColor = VaultSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = VaultBorder) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_photo_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ListItem(
                    headlineContent = { Text(stringResource(R.string.upload_gallery_title), color = VaultTextPrimary) },
                    supportingContent = { Text(stringResource(R.string.upload_gallery_desc), color = VaultTextSecondary) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, "Gallery", tint = VaultOrange) },
                    modifier = Modifier.clickable {
                        pickImageLauncher.launch("image/*")
                        showSelectionSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                ListItem(
                    headlineContent = { Text(stringResource(R.string.scan_document_title), color = VaultTextPrimary) },
                    supportingContent = { Text(stringResource(R.string.scan_document_desc), color = VaultTextSecondary) },
                    leadingContent = { Icon(Icons.Default.DocumentScanner, "Scan", tint = VaultOrange) },
                    modifier = Modifier.clickable {
                        showSelectionSheet = false
                        authViewModel.setSystemActivityActive(true)
                        DocumentScannerHelper.startScan(
                            context = context,
                            options = DocumentScannerHelper.createScannerOptions(pageLimit = 1, isPdfEnabled = false),
                            onIntentSenderReady = { intentSender ->
                                scannerLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                                )
                            },
                            onFailure = { e ->
                                Toast.makeText(context, context.getString(R.string.scanner_error_format, e.message ?: ""), Toast.LENGTH_SHORT).show()
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
private fun identityFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = VaultTextPrimary,
    unfocusedTextColor = VaultTextPrimary,
    focusedBorderColor = VaultOrange,
    unfocusedBorderColor = VaultBorder,
    focusedLabelColor = VaultOrange,
    unfocusedLabelColor = VaultTextSecondary,
    cursorColor = VaultOrange
)
