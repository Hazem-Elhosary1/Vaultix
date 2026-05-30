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
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.IdentityViewModel

/**
 * Identity document add/edit screen with photo support.
 * Photos are encrypted before storage.
 */
@Composable
fun IdentityEditScreen(
    identityViewModel: IdentityViewModel,
    onClose: () -> Unit,
    existingId: String? = null
) {
    val context = LocalContext.current
    val identityState by identityViewModel.identity.collectAsState()

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { identityViewModel.addImage(it) } }

    LaunchedEffect(existingId) {
        if (existingId != null) {
            identityViewModel.loadIdentity(existingId)
        } else {
            identityViewModel.resetState()
        }
    }

    // Document type dropdown
    val documentTypes = listOf("Passport", "Driver License", "National ID", "Residence Permit", "Other")
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${if (existingId == null) "Add" else "Edit"} Identity Document",
                        fontWeight = FontWeight.Bold,
                        color = VaultTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close", tint = VaultTextPrimary)
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
                                    .clickable { pickImageLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddAPhoto, null, tint = VaultOrange)
                                    Text("Add", color = VaultOrange, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    if (identityState.imagePaths.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Add up to 3 photos (Front, Back, etc.)", color = VaultTextDisabled, fontSize = 12.sp)
                    }
                }
            }

            // ── Document Type Dropdown ──
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = identityState.documentType.ifEmpty { "Select Type" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Document Type") },
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
                    documentTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                identityViewModel.updateDocumentType(type)
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
                label = { Text("Document Name") },
                placeholder = { Text("e.g. My Passport", color = VaultTextHint) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.documentNumber,
                onValueChange = { identityViewModel.updateDocumentNumber(it) },
                label = { Text("Document Number") },
                leadingIcon = { Icon(Icons.Default.Numbers, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.fullName,
                onValueChange = { identityViewModel.updateFullName(it) },
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.dateOfBirth,
                onValueChange = { identityViewModel.updateDateOfBirth(it) },
                label = { Text("Date of Birth") },
                placeholder = { Text("DD/MM/YYYY", color = VaultTextHint) },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.nationality,
                onValueChange = { identityViewModel.updateNationality(it) },
                label = { Text("Nationality") },
                leadingIcon = { Icon(Icons.Default.Flag, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            OutlinedTextField(
                value = identityState.issuedBy,
                onValueChange = { identityViewModel.updateIssuedBy(it) },
                label = { Text("Issued By") },
                leadingIcon = { Icon(Icons.Default.AccountBalance, null, tint = VaultOrange) },
                modifier = Modifier.fillMaxWidth(),
                colors = identityFieldColors()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = identityState.issuedDate,
                    onValueChange = { identityViewModel.updateIssuedDate(it) },
                    label = { Text("Issued Date") },
                    placeholder = { Text("DD/MM/YYYY", color = VaultTextHint) },
                    modifier = Modifier.weight(1f),
                    colors = identityFieldColors()
                )
                OutlinedTextField(
                    value = identityState.expiryDate,
                    onValueChange = { identityViewModel.updateExpiryDate(it) },
                    label = { Text("Expiry Date") },
                    placeholder = { Text("DD/MM/YYYY", color = VaultTextHint) },
                    modifier = Modifier.weight(1f),
                    colors = identityFieldColors()
                )
            }

            OutlinedTextField(
                value = identityState.notes,
                onValueChange = { identityViewModel.updateNotes(it) },
                label = { Text("Notes (optional)") },
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
                    Toast.makeText(context, "Identity saved securely!", Toast.LENGTH_SHORT).show()
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
                Text("Save Identity", color = VaultBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(16.dp))
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
