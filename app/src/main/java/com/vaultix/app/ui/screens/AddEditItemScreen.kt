package com.vaultix.app.ui.screens

import android.app.Activity
import android.content.Context
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.IsoDep
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultix.app.data.model.Card
import com.vaultix.app.data.model.Note
import com.vaultix.app.data.model.Password
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.CardViewModel
import com.vaultix.app.ui.viewmodel.NoteViewModel
import com.vaultix.app.ui.viewmodel.PasswordViewModel
import com.vaultix.app.ui.viewmodel.AuthViewModel
import java.util.UUID
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddEditItemScreen(
    categoryType: String,
    itemId: String?,
    authViewModel: AuthViewModel,
    startNfcScanning: Boolean = false,
    onNavigateToScan: () -> Unit = {},
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    when (categoryType) {
        "passwords" -> AddEditPasswordScreen(itemId = itemId, onSaved = onSaved, onBack = onBack)
        "cards" -> AddEditCardScreen(itemId = itemId, startNfcScanning = startNfcScanning, onNavigateToScan = onNavigateToScan, onSaved = onSaved, onBack = onBack)
        "notes" -> AddEditNoteScreen(itemId = itemId, onSaved = onSaved, onBack = onBack)
        "wifi" -> AddEditWifiScreen(itemId = itemId, onSaved = onSaved, onBack = onBack)
        "identities" -> {
            val identityViewModel: com.vaultix.app.ui.viewmodel.IdentityViewModel = hiltViewModel()
            IdentityEditScreen(
                identityViewModel = identityViewModel,
                onClose = onSaved,
                existingId = itemId
            )
        }
        else -> Scaffold(
            containerColor = VaultBlack,
            topBar = {
                TopAppBar(
                    title = { Text("Coming Soon", color = VaultTextPrimary) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VaultTextPrimary) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
                )
            }
        ) { Box(Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) { Text("Coming soon", color = VaultTextSecondary) } }
    }
}

@Composable
private fun AddEditPasswordScreen(itemId: String?, onSaved: () -> Unit, onBack: () -> Unit) {
    val viewModel: PasswordViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Find the item to edit. Important: use all passwords from the DB, not just filtered ones
    val existingItem = remember(state.passwords, itemId) { 
        itemId?.let { id -> state.passwords.find { it.id == id } } 
    }

    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var username by remember { mutableStateOf(existingItem?.username ?: "") }
    var password by remember { mutableStateOf(existingItem?.password?.concatToString() ?: "") }
    var website by remember { mutableStateOf(existingItem?.website ?: "") }
    var appPackageName by remember { mutableStateOf(existingItem?.appPackageName ?: "") }
    var notes by remember { mutableStateOf(existingItem?.notes ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }

    val strength = remember(password) { viewModel.calculateStrength(password.toCharArray()) }

    // Sync with existing item only ONCE when it's first loaded
    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(existingItem) {
        if (existingItem != null && !hasInitialized) {
            title = existingItem.title
            username = existingItem.username
            password = existingItem.password.concatToString()
            website = existingItem.website
            appPackageName = existingItem.appPackageName
            notes = existingItem.notes
            hasInitialized = true
        }
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "Add Password" else "Edit Password", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VaultTextPrimary) } },
                actions = {
                    TextButton(onClick = {
                        if (title.isNotBlank() && password.isNotBlank()) {
                            val now = System.currentTimeMillis()
                            val item = Password(
                                id = existingItem?.id ?: UUID.randomUUID().toString(),
                                title = title, username = username, password = password.toCharArray(),
                                website = website, appPackageName = appPackageName, notes = notes, 
                                passwordStrength = strength.level,
                                isFavorite = false, createdAt = existingItem?.createdAt ?: now, updatedAt = now
                            )
                            if (itemId == null) viewModel.insertPassword(item) else viewModel.updatePassword(item)
                            onSaved()
                        }
                    }) { Text("Save", color = VaultOrange, fontWeight = FontWeight.SemiBold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VaultTextField("Title *", title, { title = it }, Icons.Default.Label)
            VaultTextField("Username / Email", username, { username = it }, Icons.Default.Person, keyboardType = KeyboardType.Email)

            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password *") },
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = VaultTextSecondary) },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = VaultTextSecondary)
                            }
                            IconButton(onClick = { showGeneratorDialog = true }) {
                                Icon(Icons.Default.AutoFixHigh, "Generate", tint = VaultOrange)
                            }
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = vaultTextFieldColors()
                )
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    PasswordStrengthBar(password = password.toCharArray())
                    
                    val isDuplicate = remember(password, itemId) { viewModel.isPasswordDuplicate(password.toCharArray(), excludeId = itemId) }
                    if (isDuplicate) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = VaultOrange, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Warning: You are already using this password for another account!", color = VaultOrange, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            VaultTextField("Website / URL", website, { website = it }, Icons.Default.Language, keyboardType = KeyboardType.Uri)
            VaultTextField("App Package (e.g. com.facebook.katana)", appPackageName, { appPackageName = it }, Icons.Default.Android)
            VaultTextField("Notes", notes, { notes = it }, Icons.Default.Note, singleLine = false, minLines = 3)

            // Show timestamps when editing an existing item
            existingItem?.let { itItem ->
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
                    Column(Modifier.padding(12.dp)) {
                        val fmt = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        Text("Created: ${fmt.format(Date(itItem.createdAt))}", fontSize = 12.sp, color = VaultTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("Last updated: ${fmt.format(Date(itItem.updatedAt))}", fontSize = 12.sp, color = VaultTextSecondary)
                        itItem.expiresAt?.let { exp ->
                            Spacer(Modifier.height(4.dp))
                            Text("Expires: ${fmt.format(Date(exp))}", fontSize = 12.sp, color = VaultTextSecondary)
                        }
                    }
                }
            }
        }
    }

    if (showGeneratorDialog) {
        PasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onUse = { generated -> password = generated; showGeneratorDialog = false },
            viewModel = viewModel
        )
    }
}

@Composable
private fun PasswordGeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit, viewModel: PasswordViewModel) {
    var length by remember { mutableStateOf(20f) }
    var useUppercase by remember { mutableStateOf(true) }
    var useLowercase by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var generated by remember { mutableStateOf(viewModel.generatePassword()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text("Password Generator", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = VaultCard), shape = RoundedCornerShape(8.dp)) {
                    Text(generated, Modifier.padding(12.dp), fontSize = 14.sp, color = VaultOrange, fontWeight = FontWeight.Medium)
                }
                Text("Length: ${length.toInt()}", fontSize = 13.sp, color = VaultTextSecondary)
                Slider(value = length, onValueChange = {
                    length = it
                    generated = viewModel.generatePassword(it.toInt(), useUppercase, useLowercase, useNumbers, useSymbols)
                }, valueRange = 8f..64f, colors = SliderDefaults.colors(thumbColor = VaultOrange, activeTrackColor = VaultOrange))
                GenToggleRow("Uppercase (A-Z)", useUppercase) { useUppercase = it; generated = viewModel.generatePassword(length.toInt(), useUppercase, useLowercase, useNumbers, useSymbols) }
                GenToggleRow("Lowercase (a-z)", useLowercase) { useLowercase = it; generated = viewModel.generatePassword(length.toInt(), useUppercase, useLowercase, useNumbers, useSymbols) }
                GenToggleRow("Numbers (0-9)", useNumbers) { useNumbers = it; generated = viewModel.generatePassword(length.toInt(), useUppercase, useLowercase, useNumbers, useSymbols) }
                GenToggleRow("Symbols (!@#...)", useSymbols) { useSymbols = it; generated = viewModel.generatePassword(length.toInt(), useUppercase, useLowercase, useNumbers, useSymbols) }
            }
        },
        confirmButton = {
            Button(onClick = { onUse(generated) }, colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)) {
                Text("Use This Password", color = VaultBlack)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = VaultTextSecondary) } }
    )
}

@Composable
private fun GenToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = VaultTextSecondary)
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = VaultOrange, checkedTrackColor = VaultOrange.copy(0.3f)))
    }
}

@Composable
private fun AddEditCardScreen(itemId: String?, startNfcScanning: Boolean, onNavigateToScan: () -> Unit, onSaved: () -> Unit, onBack: () -> Unit) {
    val viewModel: CardViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val existingItem = remember(state.cards, itemId) { 
        itemId?.let { id -> state.cards.find { it.id == id } } 
    }

    var cardName by remember { mutableStateOf(existingItem?.cardName ?: "") }
    var holderName by remember { mutableStateOf(existingItem?.holderName ?: "") }
    var cardNumber by remember { mutableStateOf(existingItem?.cardNumber ?: "") }
    var expiryMonth by remember { mutableStateOf(existingItem?.expiryMonth ?: "") }
    var expiryYear by remember { mutableStateOf(existingItem?.expiryYear ?: "") }
    var cvv by remember { mutableStateOf(existingItem?.cvv ?: "") }
    var cardType by remember { mutableStateOf(existingItem?.cardType ?: "Visa") }
    var isNfcScanning by remember { mutableStateOf(startNfcScanning && itemId == null) }
    var nfcStatus by remember { mutableStateOf("Tap an NFC card or tag to prefill supported fields.") }

    LaunchedEffect(startNfcScanning, itemId) {
        if (startNfcScanning && itemId == null) {
            nfcStatus = "Scanning for NFC card..."
            isNfcScanning = true
        }
    }

    DisposableEffect(isNfcScanning, activity, nfcAdapter) {
        if (!isNfcScanning || activity == null || nfcAdapter == null) {
            onDispose { }
        } else {
            val readerFlags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

            val callback = NfcAdapter.ReaderCallback { tag ->
                val parseResult = parseNfcCardDraft(tag)
                activity.runOnUiThread {
                    Log.d("VaultixNFC", buildNfcDebugInfo(tag, parseResult))

                    val draft = parseResult.draft

                    if (draft != null) {
                        if (draft.cardName.isNotBlank()) cardName = draft.cardName
                        if (draft.holderName.isNotBlank()) holderName = draft.holderName
                        if (draft.cardNumber.isNotBlank()) cardNumber = draft.cardNumber
                        if (draft.expiryMonth.isNotBlank()) expiryMonth = draft.expiryMonth
                        if (draft.expiryYear.isNotBlank()) expiryYear = draft.expiryYear
                        if (draft.cvv.isNotBlank()) cvv = draft.cvv
                        if (draft.cardNumber.isNotBlank()) {
                            cardType = detectNfcCardType(draft.cardNumber)
                        }
                        nfcStatus = draft.notes
                    } else {
                        nfcStatus = if (tag.techList.contains(IsoDep::class.java.name)) {
                            parseResult.message.ifBlank { "Bank card detected, but this card did not expose readable EMV data. Nothing was autofilled." }
                        } else {
                            parseResult.message.ifBlank { "NFC tag detected, but no supported card payload was found. Nothing was autofilled." }
                        }
                    }
                    isNfcScanning = false
                }
            }

            nfcAdapter.enableReaderMode(activity, callback, readerFlags, null)
            onDispose { nfcAdapter.disableReaderMode(activity) }
        }
    }

    // Sync with existing item only ONCE when it's first loaded
    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(existingItem) {
        if (existingItem != null && !hasInitialized) {
            cardName = existingItem.cardName
            holderName = existingItem.holderName
            cardNumber = existingItem.cardNumber
            expiryMonth = existingItem.expiryMonth
            expiryYear = existingItem.expiryYear
            cvv = existingItem.cvv
            cardType = existingItem.cardType
            hasInitialized = true
        }
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "Add Card" else "Edit Card", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VaultTextPrimary) } },
                actions = {
                    TextButton(onClick = {
                        if (cardName.isNotBlank() && cardNumber.isNotBlank()) {
                            val now = System.currentTimeMillis()
                            val item = Card(
                                id = existingItem?.id ?: UUID.randomUUID().toString(),
                                cardName = cardName, holderName = holderName, cardNumber = cardNumber,
                                expiryMonth = expiryMonth, expiryYear = expiryYear, cvv = cvv,
                                cardType = cardType, notes = "", isFavorite = false,
                                createdAt = existingItem?.createdAt ?: now, updatedAt = now
                            )
                            if (itemId == null) viewModel.insertCard(item) else viewModel.updateCard(item)
                            onSaved()
                        }
                    }) { Text("Save", color = VaultOrange, fontWeight = FontWeight.SemiBold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VaultTextField("Card Name *", cardName, { cardName = it }, Icons.Default.CreditCard)
            VaultTextField("Card Holder Name *", holderName, { holderName = it }, Icons.Default.Person)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        nfcStatus = "Scanning for NFC card..."
                        isNfcScanning = true
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultOrange),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultOrange)
                ) {
                    Icon(Icons.Default.Nfc, null)
                    Spacer(Modifier.width(8.dp))
                    Text("NFC")
                }

                OutlinedButton(
                    onClick = onNavigateToScan,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultOrange),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultOrange)
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }

            VaultTextField("Card Number *", cardNumber, { if (it.length <= 16 && it.all { c -> c.isDigit() }) cardNumber = it }, Icons.Default.Numbers, keyboardType = KeyboardType.Number)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VaultTextField("MM", expiryMonth, { if (it.length <= 2) expiryMonth = it }, modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                VaultTextField("YY", expiryYear, { if (it.length <= 2) expiryYear = it }, modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                VaultTextField("CVV", cvv, { if (it.length <= 4) cvv = it }, modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, visualTransformation = PasswordVisualTransformation())
            }
            Text("Card Type", fontSize = 12.sp, color = VaultTextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Visa", "Mastercard", "Amex", "Other").forEach { type ->
                    FilterChip(selected = cardType == type, onClick = { cardType = type }, label = { Text(type, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VaultOrange.copy(0.2f), selectedLabelColor = VaultOrange))
                }
            }

            Text(
                nfcStatus,
                color = if (nfcAdapter == null) VaultError else VaultTextSecondary,
                fontSize = 12.sp
            )

            if (nfcAdapter == null) {
                Text("This device does not support NFC.", color = VaultError, fontSize = 12.sp)
            } else if (!isNfcScanning) {
                Text(
                    "Manual entry is available now. Use NFC or Scan anytime from the buttons above.",
                    color = VaultTextSecondary,
                    fontSize = 12.sp
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = VaultOrange, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Listening for NFC...", color = VaultTextPrimary)
                }
                OutlinedButton(
                    onClick = { isNfcScanning = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VaultOrange),
                    border = androidx.compose.foundation.BorderStroke(1.dp, VaultOrange)
                ) {
                    Text("Stop Scan")
                }
            }
        }
    }
}

@Composable
private fun AddEditNoteScreen(itemId: String?, onSaved: () -> Unit, onBack: () -> Unit) {
    val viewModel: NoteViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val existingItem = remember(state.notes, itemId) { 
        itemId?.let { id -> state.notes.find { it.id == id } } 
    }

    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var contentValue by remember { 
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(existingItem?.content ?: "")) 
    }
    var selectedColor by remember { mutableStateOf(existingItem?.color ?: "#1A2744") }

    val noteColors = listOf(
        "#1A3A5C", // Deep Blue
        "#4A1942", // Rich Purple
        "#1B4332", // Forest Green
        "#5C1A1A", // Deep Red
        "#5C3D1A", // Warm Amber
        "#1A4A4A", // Deep Teal
        "#3D1A5C", // Violet
        "#4A4A1A"  // Olive Gold
    )

    // Sync initialization
    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(existingItem) {
        if (existingItem != null && !hasInitialized) {
            title = existingItem.title
            contentValue = androidx.compose.ui.text.input.TextFieldValue(existingItem.content)
            selectedColor = existingItem.color
            hasInitialized = true
        }
    }

    val noteBackground = remember(selectedColor) { 
        try { Color(android.graphics.Color.parseColor(selectedColor)).copy(alpha = 0.22f) } 
        catch (_: Exception) { VaultSurface }
    }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "New Note" else "Edit Note", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = VaultTextPrimary) } },
                actions = {
                    TextButton(onClick = {
                        if (title.isNotBlank()) {
                            val now = System.currentTimeMillis()
                            val item = Note(
                                id = existingItem?.id ?: UUID.randomUUID().toString(),
                                title = title, content = contentValue.text, color = selectedColor,
                                isFavorite = existingItem?.isFavorite ?: false, 
                                createdAt = existingItem?.createdAt ?: now, updatedAt = now
                            )
                            if (itemId == null) viewModel.insertNote(item) else viewModel.updateNote(item)
                            onSaved()
                        }
                    }) { Text("Save", color = VaultOrange, fontWeight = FontWeight.SemiBold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(VaultBlack)
        ) {
            // Theme Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Theme:", fontSize = 12.sp, color = VaultTextSecondary, fontWeight = FontWeight.Medium)
                noteColors.forEach { colorHex ->
                    val color = Color(android.graphics.Color.parseColor(colorHex))
                    val isSelected = selectedColor == colorHex
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(color, RoundedCornerShape(10.dp))
                            .then(
                                if (isSelected) Modifier.background(
                                    color, RoundedCornerShape(10.dp)
                                ).padding(2.dp).background(
                                    Color.Black, RoundedCornerShape(8.dp)
                                ).padding(2.dp).background(
                                    color, RoundedCornerShape(6.dp)
                                )
                                else Modifier
                            )
                            .clickable { selectedColor = colorHex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Editor Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
                    .background(noteBackground, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                // Title Field
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VaultTextPrimary
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(VaultOrange),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) Text("Note Title", color = VaultTextDisabled, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        innerTextField()
                    }
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = VaultTextDisabled.copy(alpha = 0.1f), thickness = 1.dp)
                Spacer(Modifier.height(16.dp))

                // Content Field
                BasicTextField(
                    value = contentValue,
                    onValueChange = { contentValue = it },
                    modifier = Modifier.fillMaxSize().weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        color = VaultTextPrimary
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(VaultOrange),
                    visualTransformation = MarkdownVisualTransformation(),
                    decorationBox = { innerTextField ->
                        if (contentValue.text.isEmpty()) Text("Start writing your secure note...", color = VaultTextDisabled, fontSize = 17.sp)
                        innerTextField()
                    }
                )
            }

            // Smart Formatting Toolbar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = VaultSurface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Helper to insert text at cursor
                    fun insertFormatting(prefix: String, suffix: String, wrapSelection: Boolean = true) {
                        val selection = contentValue.selection
                        val text = contentValue.text
                        val before = text.substring(0, selection.start)
                        val selected = text.substring(selection.start, selection.end)
                        val after = text.substring(selection.end)

                        val newText = if (wrapSelection) {
                            "$before$prefix$selected$suffix$after"
                        } else {
                            "$before$prefix$after"
                        }
                        val newCursor = if (wrapSelection) {
                            selection.start + prefix.length + selected.length + suffix.length
                        } else {
                            selection.start + prefix.length
                        }

                        contentValue = androidx.compose.ui.text.input.TextFieldValue(
                            text = newText,
                            selection = androidx.compose.ui.text.TextRange(newCursor)
                        )
                    }

                    // Helper to insert line-level prefix
                    fun insertLinePrefix(marker: String) {
                        val text = contentValue.text
                        val cursorPos = contentValue.selection.start
                        // Find start of current line
                        val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
                        val before = text.substring(0, lineStart)
                        val lineAndAfter = text.substring(lineStart)
                        val newText = "$before$marker$lineAndAfter"
                        val newCursor = cursorPos + marker.length

                        contentValue = androidx.compose.ui.text.input.TextFieldValue(
                            text = newText,
                            selection = androidx.compose.ui.text.TextRange(newCursor)
                        )
                    }

                    // Heading (H) — cycles # / ## / ###
                    IconButton(
                        onClick = {
                            val text = contentValue.text
                            val cursorPos = contentValue.selection.start
                            val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1
                            val lineEnd = text.indexOf('\n', cursorPos).let { if (it == -1) text.length else it }
                            val currentLine = text.substring(lineStart, lineEnd)

                            val (newLine, headingLen) = when {
                                currentLine.startsWith("### ") -> currentLine.substring(4) to -4
                                currentLine.startsWith("## ") -> "### ${currentLine.substring(3)}" to 1
                                currentLine.startsWith("# ") -> "## ${currentLine.substring(2)}" to 1
                                else -> "# $currentLine" to 2
                            }

                            val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
                            contentValue = androidx.compose.ui.text.input.TextFieldValue(
                                text = newText,
                                selection = androidx.compose.ui.text.TextRange((cursorPos + headingLen).coerceAtLeast(lineStart))
                            )
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(VaultBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Text("H", color = VaultOrange, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }

                    // Bold
                    IconButton(
                        onClick = { insertFormatting("**", "**") },
                        modifier = Modifier
                            .size(44.dp)
                            .background(VaultBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.FormatBold, null, tint = VaultOrange, modifier = Modifier.size(20.dp))
                    }

                    // Italic
                    IconButton(
                        onClick = { insertFormatting("*", "*") },
                        modifier = Modifier
                            .size(44.dp)
                            .background(VaultBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.FormatItalic, null, tint = VaultOrange, modifier = Modifier.size(20.dp))
                    }

                    // Bullet list
                    IconButton(
                        onClick = { insertLinePrefix("• ") },
                        modifier = Modifier
                            .size(44.dp)
                            .background(VaultBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.FormatListBulleted, null, tint = VaultOrange, modifier = Modifier.size(20.dp))
                    }

                    // Blockquote
                    IconButton(
                        onClick = { insertLinePrefix("> ") },
                        modifier = Modifier
                            .size(44.dp)
                            .background(VaultBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.FormatQuote, null, tint = VaultOrange, modifier = Modifier.size(20.dp))
                    }

                    // Separator line
                    IconButton(
                        onClick = { insertFormatting("\n---\n", "", wrapSelection = false) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(VaultBlack.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.HorizontalRule, null, tint = VaultOrange, modifier = Modifier.size(20.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = { contentValue = androidx.compose.ui.text.input.TextFieldValue("") },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, tint = VaultError)
                    }
                }
            }
        }
    }
}

@Composable
fun VaultTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let { { Icon(it, null, tint = VaultTextSecondary) } },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine, minLines = minLines,
        visualTransformation = visualTransformation,
        colors = vaultTextFieldColors()
    )
}

@Composable
fun vaultTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VaultOrange,
    unfocusedBorderColor = VaultBorder,
    focusedLabelColor = VaultOrange,
    cursorColor = VaultOrange,
    focusedTextColor = VaultTextPrimary,
    unfocusedTextColor = VaultTextPrimary,
    focusedLeadingIconColor = VaultOrange
)

private data class NfcCardDraft(
    val cardName: String,
    val holderName: String,
    val cardNumber: String,
    val expiryMonth: String,
    val expiryYear: String,
    val cvv: String,
    val notes: String
)

private data class NfcParseResult(
    val draft: NfcCardDraft?,
    val trace: List<String>,
    val message: String
)

private fun buildNfcDebugInfo(tag: Tag, result: NfcParseResult): String {
    val techList = tag.techList.joinToString(", ")
    val hasIsoDep = tag.techList.contains(IsoDep::class.java.name)
    val hasNdef = tag.techList.contains(Ndef::class.java.name)
    val tagId = tag.id.toHexString()
    val lines = mutableListOf(
        "tagId=$tagId",
        "techList=${if (techList.isBlank()) "<empty>" else techList}",
        "supportsIsoDep=$hasIsoDep",
        "supportsNdef=$hasNdef"
    )

    if (result.draft != null) {
        lines += "parseResult=success"
        lines += "cardName=${result.draft.cardName.ifBlank { "<empty>" }}"
        lines += "holderName=${result.draft.holderName.ifBlank { "<empty>" }}"
        lines += "cardNumber=${result.draft.cardNumber.ifBlank { "<empty>" }}"
        lines += "expiry=${result.draft.expiryMonth.ifBlank { "<empty>" }}/${result.draft.expiryYear.ifBlank { "<empty>" }}"
    } else {
        lines += "parseResult=null"
        if (result.message.isNotBlank()) {
            lines += "parseMessage=${result.message}"
        }
    }

    result.trace.takeLast(8).forEach { lines += "trace=$it" }

    return lines.joinToString("\n")
}

private fun parseNfcCardDraft(tag: Tag): NfcParseResult {
    val trace = mutableListOf<String>()

    parseBankCardNfcDraft(tag, trace)?.let {
        return NfcParseResult(
            draft = it,
            trace = trace,
            message = "Bank card data imported successfully."
        )
    }

    val ndef = Ndef.get(tag)
    if (ndef != null) {
        val draft = runCatching {
            ndef.connect()
            val message = ndef.ndefMessage
            ndef.close()
            val payloadText = message?.records.orEmpty().joinToString(" ") { decodeNdefRecord(it) }
            parseNfcPayload(payloadText)
        }.getOrElse { exception ->
            trace += "ndefError=${exception.javaClass.simpleName}: ${exception.message.orEmpty()}"
            null
        }

        if (draft != null) {
            return NfcParseResult(
                draft = draft,
                trace = trace,
                message = "NDEF payload imported successfully."
            )
        }

        trace += "ndefPayload=empty"
    }

    if (tag.techList.contains(IsoDep::class.java.name)) {
        return NfcParseResult(
            draft = null,
            trace = trace,
            message = "EMV bank card detected, but the app could not extract PAN, expiry, or holder data from the card responses."
        )
    }

    return NfcParseResult(
        draft = null,
        trace = trace,
        message = "NFC tag detected, but no supported card payload was found."
    )
}

private fun parseBankCardNfcDraft(tag: Tag, trace: MutableList<String>): NfcCardDraft? {
    val isoDep = IsoDep.get(tag) ?: return null

    return try {
        isoDep.connect()
        isoDep.timeout = 2000

        val applications = readEmvApplications(isoDep, trace)
        if (applications.isEmpty()) {
            trace += "emvApplications=none"
        }
        for (application in applications.ifEmpty { defaultEmvApplications() }) {
            val draft = readEmvApplication(isoDep, application, trace)
            if (draft != null) {
                return draft
            }
        }

        trace += "emvDraft=null"
        null
    } catch (exception: Exception) {
        trace += "emvError=${exception.javaClass.simpleName}: ${exception.message.orEmpty()}"
        null
    } finally {
        runCatching { isoDep.close() }
    }
}

private data class EmvApplication(
    val aid: ByteArray,
    val label: String? = null
)

private fun readEmvApplications(isoDep: IsoDep, trace: MutableList<String>): List<EmvApplication> {
    val applications = mutableListOf<EmvApplication>()
    val ppseCandidates = listOf("2PAY.SYS.DDF01", "1PAY.SYS.DDF01")

    for (ppse in ppseCandidates) {
        val response = transmitApdu(isoDep, buildSelectByNameApdu(ppse.toByteArray(Charsets.US_ASCII)))
        if (response == null) {
            trace += "ppse=$ppse selectFailed"
            continue
        }
        val data = response.data
        val aidValues = findEmvTagValues(data, "4F")
        if (aidValues.isNotEmpty()) {
            val label = findEmvTagValues(data, "50").firstOrNull()?.let { decodeAsciiEmvValue(it) }
            aidValues.forEach { aid ->
                applications.add(EmvApplication(aid = aid, label = label))
            }
            trace += "ppse=$ppse aids=${applications.joinToString { it.aid.toHexString() }}"
            if (applications.isNotEmpty()) {
                return applications.distinctBy { it.aid.toHexString() }
            }
        } else {
            trace += "ppse=$ppse noAids"
        }
    }

    return applications
}

private fun defaultEmvApplications(): List<EmvApplication> = listOf(
    EmvApplication(hexToByteArray("A0000000031010"), "VISA"),
    EmvApplication(hexToByteArray("A0000000041010"), "Mastercard"),
    EmvApplication(hexToByteArray("A00000002501"), "Amex"),
    EmvApplication(hexToByteArray("A0000001523010"), "Discover")
)

private fun readEmvApplication(isoDep: IsoDep, application: EmvApplication, trace: MutableList<String>): NfcCardDraft? {
    val selectResponse = transmitApdu(isoDep, buildSelectByNameApdu(application.aid)) ?: run {
        trace += "aid=${application.aid.toHexString()} selectFailed"
        return null
    }
    val selectData = selectResponse.data
    trace += "aid=${application.aid.toHexString()} selectOk bytes=${selectData.size}"

    val firstPass = extractEmvCardDraft(
        payloadSources = listOf(selectData),
        cardNameFallback = application.label
    )
    if (firstPass?.cardNumber?.isNotBlank() == true) {
        trace += "aid=${application.aid.toHexString()} panFoundInSelect"
        return firstPass
    }

    val pdol = findEmvTagValue(selectData, "9F38")
    if (pdol == null) {
        trace += "aid=${application.aid.toHexString()} pdolMissing"
    }

    val gpoResponse = when {
        pdol != null -> transmitApdu(isoDep, buildGetProcessingOptionsApdu(pdol))
        else -> {
            trace += "aid=${application.aid.toHexString()} gpoRetryEmptyPdol"
            transmitApdu(isoDep, buildGetProcessingOptionsApdu(ByteArray(0)))
        }
    }

    if (gpoResponse == null) {
        trace += "aid=${application.aid.toHexString()} gpoFailed"
    } else {
        trace += "aid=${application.aid.toHexString()} gpoOk bytes=${gpoResponse.data.size}"
    }
    val gpoData = gpoResponse?.data ?: ByteArray(0)
    val afl = findEmvTagValue(gpoData, "94")

    val recordPayloads = mutableListOf<ByteArray>()
    if (afl != null) {
        recordPayloads += readEmvRecords(isoDep, afl, trace)
    } else {
        trace += "aid=${application.aid.toHexString()} aflMissing"
    }

    val draft = extractEmvCardDraft(
        payloadSources = listOf(selectData, gpoData) + recordPayloads,
        cardNameFallback = application.label
    )

    if (draft == null) {
        trace += "aid=${application.aid.toHexString()} noCardData"
    }

    return draft
}

private data class EmvExtractedCard(
    val cardNumber: String? = null,
    val expiryMonth: String? = null,
    val expiryYear: String? = null,
    val holderName: String? = null,
    val cardName: String? = null
)

private fun extractEmvCardDraft(payloadSources: List<ByteArray>, cardNameFallback: String?): NfcCardDraft? {
    val cardNumber = payloadSources.asSequence().mapNotNull { extractCardNumberFromEmvData(it) }.firstOrNull()
    val expiry = payloadSources.asSequence().mapNotNull { extractExpiryFromEmvData(it) }.firstOrNull()
    val holderName = payloadSources.asSequence().mapNotNull { extractHolderNameFromEmvData(it) }.firstOrNull()
    val cardName = payloadSources.asSequence().mapNotNull { extractCardLabelFromEmvData(it) }.firstOrNull() ?: cardNameFallback

    if (cardNumber.isNullOrBlank()) {
        return null
    }

    return NfcCardDraft(
        cardName = cardName?.takeIf { it.isNotBlank() } ?: detectNfcCardType(cardNumber),
        holderName = holderName.orEmpty(),
        cardNumber = cardNumber.chunked(4).joinToString(" "),
        expiryMonth = expiry?.month.orEmpty(),
        expiryYear = expiry?.year.orEmpty(),
        cvv = "",
        notes = "Imported from bank card NFC"
    )
}

private data class EmvExpiry(
    val month: String,
    val year: String
)

private fun extractCardNumberFromEmvData(data: ByteArray): String? {
    findEmvTagValue(data, "5A")?.let { panBytes ->
        val pan = decodeBcdDigits(panBytes)
        if (pan.length in 13..19) return pan
    }

    findEmvTagValue(data, "57")?.let { track2Bytes ->
        val track2 = decodeTrack2Data(track2Bytes)
        val pan = track2.substringBefore('D').replace(Regex("[^0-9]"), "")
        if (pan.length in 13..19) return pan
    }

    return null
}

private fun extractExpiryFromEmvData(data: ByteArray): EmvExpiry? {
    findEmvTagValue(data, "5F24")?.let { expiryBytes ->
        val digits = decodeBcdDigits(expiryBytes)
        if (digits.length >= 4) {
            return EmvExpiry(month = digits.substring(2, 4), year = digits.substring(0, 2))
        }
    }

    findEmvTagValue(data, "57")?.let { track2Bytes ->
        val track2 = decodeTrack2Data(track2Bytes)
        val remainder = track2.substringAfter('D', "")
        if (remainder.length >= 4) {
            return EmvExpiry(month = remainder.substring(2, 4), year = remainder.substring(0, 2))
        }
    }

    return null
}

private fun extractHolderNameFromEmvData(data: ByteArray): String? {
    findEmvTagValue(data, "5F20")?.let { nameBytes ->
        val name = decodeAsciiEmvValue(nameBytes).trim()
        if (name.isNotBlank()) return name.uppercase()
    }

    return null
}

private fun extractCardLabelFromEmvData(data: ByteArray): String? {
    findEmvTagValue(data, "50")?.let { labelBytes ->
        val label = decodeAsciiEmvValue(labelBytes).trim()
        if (label.isNotBlank()) return label.uppercase()
    }

    return null
}

private fun readEmvRecords(isoDep: IsoDep, afl: ByteArray, trace: MutableList<String>): List<ByteArray> {
    val payloads = mutableListOf<ByteArray>()
    var index = 0

    while (index + 3 < afl.size) {
        val sfi = (afl[index].toInt() and 0xF8) shr 3
        val firstRecord = afl[index + 1].toInt() and 0xFF
        val lastRecord = afl[index + 2].toInt() and 0xFF
        index += 4

        for (recordNumber in firstRecord..lastRecord) {
            val p2 = ((sfi shl 3) or 0x04).toByte()
            val response = transmitApdu(isoDep, byteArrayOf(0x00, 0xB2.toByte(), recordNumber.toByte(), p2, 0x00))
            if (response != null) {
                payloads.add(response.data)
                trace += "record sfi=$sfi record=$recordNumber bytes=${response.data.size}"
            } else {
                trace += "record sfi=$sfi record=$recordNumber failed"
            }
        }
    }

    return payloads
}

private data class ApduResponse(
    val data: ByteArray,
    val sw1: Byte,
    val sw2: Byte
)

private fun transmitApdu(isoDep: IsoDep, apdu: ByteArray): ApduResponse? {
    var request = apdu
    val responseData = mutableListOf<Byte>()

    repeat(4) {
        val response = isoDep.transceive(request)
        if (response.size < 2) return null

        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]
        val data = response.copyOfRange(0, response.size - 2)
        if (data.isNotEmpty()) {
            responseData.addAll(data.toList())
        }

        when (sw1) {
            0x90.toByte() -> {
                if (sw2 == 0x00.toByte()) {
                    return ApduResponse(responseData.toByteArray(), sw1, sw2)
                }
                return null
            }

            0x61.toByte() -> {
                request = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2)
            }

            0x6C.toByte() -> {
                request = request.copyOf()
                request[request.lastIndex] = sw2
            }

            else -> return null
        }
    }

    return null
}

private fun buildSelectByNameApdu(name: ByteArray): ByteArray {
    return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, name.size.toByte()) + name + byteArrayOf(0x00)
}

private fun buildGetProcessingOptionsApdu(pdol: ByteArray): ByteArray {
    val pdolData = buildPdolData(pdol)
    val commandData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
    return byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, commandData.size.toByte()) + commandData + byteArrayOf(0x00)
}

private fun buildPdolData(pdol: ByteArray): ByteArray {
    val builder = ArrayList<Byte>()
    var index = 0

    while (index < pdol.size) {
        val tagResult = readEmvTag(pdol, index) ?: break
        index += tagResult.consumed
        if (index >= pdol.size) break

        val length = pdol[index].toInt() and 0xFF
        index += 1

        repeat(length) { builder.add(0x00) }
    }

    return builder.toByteArray()
}

private data class EmvTagResult(
    val tagHex: String,
    val consumed: Int,
    val constructed: Boolean
)

private data class EmvLengthResult(
    val length: Int,
    val consumed: Int
)

private data class EmvTlv(
    val tagHex: String,
    val value: ByteArray,
    val constructed: Boolean
)

private fun findEmvTagValues(data: ByteArray, tagHex: String): List<ByteArray> {
    return parseEmvTlvs(data).filter { it.tagHex.equals(tagHex, ignoreCase = true) }.map { it.value }
}

private fun findEmvTagValue(data: ByteArray, tagHex: String): ByteArray? {
    return findEmvTagValues(data, tagHex).firstOrNull()
}

private fun parseEmvTlvs(data: ByteArray): List<EmvTlv> {
    val tlvs = mutableListOf<EmvTlv>()

    fun parseRange(start: Int, end: Int) {
        var index = start
        while (index < end) {
            val tag = readEmvTag(data, index, end) ?: break
            index += tag.consumed

            val length = readEmvLength(data, index, end) ?: break
            index += length.consumed

            val valueEnd = minOf(end, index + length.length)
            if (valueEnd < index) break

            val value = data.copyOfRange(index, valueEnd)
            tlvs.add(EmvTlv(tag.tagHex, value, tag.constructed))

            if (tag.constructed && value.isNotEmpty()) {
                parseRange(index, valueEnd)
            }

            index = valueEnd
        }
    }

    parseRange(0, data.size)
    return tlvs
}

private fun readEmvTag(data: ByteArray, start: Int, end: Int = data.size): EmvTagResult? {
    if (start >= end) return null

    var index = start
    val bytes = mutableListOf<Byte>()
    val first = data[index]
    bytes.add(first)
    index += 1

    val constructed = first.toInt() and 0x20 != 0
    if (first.toInt() and 0x1F == 0x1F) {
        while (index < end) {
            val current = data[index]
            bytes.add(current)
            index += 1
            if (current.toInt() and 0x80 == 0) break
        }
    }

    return EmvTagResult(bytes.toByteArray().toHexString(), index - start, constructed)
}

private fun readEmvLength(data: ByteArray, start: Int, end: Int = data.size): EmvLengthResult? {
    if (start >= end) return null

    val first = data[start].toInt() and 0xFF
    if (first and 0x80 == 0) {
        return EmvLengthResult(first, 1)
    }

    val byteCount = first and 0x7F
    if (byteCount <= 0 || byteCount > 3 || start + byteCount >= end) return null

    var length = 0
    for (i in 1..byteCount) {
        length = (length shl 8) or (data[start + i].toInt() and 0xFF)
    }

    return EmvLengthResult(length, 1 + byteCount)
}

private fun decodeBcdDigits(data: ByteArray): String {
    val builder = StringBuilder()
    data.forEach { byte ->
        val high = (byte.toInt() shr 4) and 0x0F
        val low = byte.toInt() and 0x0F

        if (high != 0x0F) builder.append(high.toString(16).uppercase())
        if (low != 0x0F) builder.append(low.toString(16).uppercase())
    }

    return builder.toString().replace(Regex("[^0-9]"), "")
}

private fun decodeTrack2Data(data: ByteArray): String {
    val builder = StringBuilder()
    data.forEach { byte ->
        val high = (byte.toInt() shr 4) and 0x0F
        val low = byte.toInt() and 0x0F

        when (high) {
            0x0F -> return builder.toString()
            0x0D -> builder.append('D')
            else -> builder.append(high.toString(16).uppercase())
        }

        when (low) {
            0x0F -> return builder.toString()
            0x0D -> builder.append('D')
            else -> builder.append(low.toString(16).uppercase())
        }
    }

    return builder.toString()
}

private fun decodeAsciiEmvValue(data: ByteArray): String {
    return data.toString(Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
}

private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02X".format(byte) }

private fun hexToByteArray(hex: String): ByteArray {
    val clean = hex.replace(" ", "")
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun parseNfcPayload(payloadText: String): NfcCardDraft? {
    val normalized = payloadText.trim()
    if (normalized.isBlank()) return null

    if (normalized.startsWith("{")) {
        runCatching {
            val json = JSONObject(normalized)
            val cardNumber = json.optString("cardNumber").ifBlank { json.optString("number") }
            val holderName = json.optString("holderName").ifBlank { json.optString("name") }
            val expiryMonth = json.optString("expiryMonth").ifBlank { json.optString("month") }
            val expiryYear = json.optString("expiryYear").ifBlank { json.optString("year") }
            val cvv = json.optString("cvv")
            val cardType = json.optString("cardType").ifBlank { if (cardNumber.isNotBlank()) detectNfcCardType(cardNumber) else "Card" }
            val cardName = json.optString("cardName").ifBlank { cardType }
            return NfcCardDraft(
                cardName = cardName,
                holderName = holderName,
                cardNumber = cardNumber.replace(" ", ""),
                expiryMonth = expiryMonth,
                expiryYear = expiryYear.takeLast(2),
                cvv = cvv,
                notes = json.optString("notes").ifBlank { "Imported from NFC" }
            )
        }.getOrNull()
    }

    parseNfcTextCardInfo(normalized)?.let { info ->
        val type = detectNfcCardType(info.number)
        return NfcCardDraft(
            cardName = type,
            holderName = info.holderName,
            cardNumber = info.number.replace(" ", ""),
            expiryMonth = info.expiryMonth,
            expiryYear = info.expiryYear,
            cvv = "",
            notes = "Imported from NFC text"
        )
    }

    return null
}

private fun decodeNdefRecord(record: NdefRecord): String {
    return try {
        when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val payload = record.payload
                if (payload.isEmpty()) "" else {
                    val languageCodeLength = payload[0].toInt() and 0x3F
                    String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, Charsets.UTF_8)
                }
            }
            else -> String(record.payload, Charsets.UTF_8)
        }
    } catch (_: Exception) {
        ""
    }
}

private data class NfcParsedCardInfo(
    val number: String,
    val expiryMonth: String,
    val expiryYear: String,
    val holderName: String
)

private fun parseNfcTextCardInfo(text: String): NfcParsedCardInfo? {
    val lines = text
        .replace("\r", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val numberCandidate = lines.mapNotNull { line ->
        extractNfcCardNumber(line)?.takeIf { isLikelyNfcCardNumberLine(line, it) }
    }.firstOrNull()

    val expiryCandidate = lines.mapNotNull { line ->
        extractNfcExpiry(line)
    }.firstOrNull()

    val holderCandidate = lines.mapNotNull { line ->
        extractNfcHolderName(line)
    }.firstOrNull()

    if (numberCandidate == null) return null

    return NfcParsedCardInfo(
        number = numberCandidate.chunked(4).joinToString(" "),
        expiryMonth = expiryCandidate?.month.orEmpty(),
        expiryYear = expiryCandidate?.year.orEmpty(),
        holderName = holderCandidate.orEmpty()
    )
}

private data class NfcExpiryCandidate(
    val month: String,
    val year: String
)

private fun extractNfcCardNumber(text: String): String? {
    val compact = text.replace(Regex("[^0-9]"), "")
    if (compact.length !in 13..19) return null
    if (!nfcLuhnCheck(compact)) return null
    return compact
}

private fun isLikelyNfcCardNumberLine(text: String, number: String): Boolean {
    val normalized = text.uppercase()
    if (number.length !in 13..19) return false
    if (normalized.contains("TAG") || normalized.contains("UID")) return false
    if (normalized.contains("CARD") || normalized.contains("NUMBER") || normalized.contains("PAN") || normalized.contains("ACCOUNT")) return true
    val separatorCount = text.count { it == ' ' || it == '-' }
    return separatorCount >= 2
}

private fun extractNfcExpiry(text: String): NfcExpiryCandidate? {
    val normalized = text.uppercase()
    val regex = Regex("""(0[1-9]|1[0-2])\s?[/\-]\s?(\d{2}|\d{4})""")
    val match = regex.find(normalized) ?: return null
    var year = match.groupValues[2]
    if (year.length == 4) year = year.takeLast(2)
    if (!(normalized.contains("EXP") || normalized.contains("VALID") || normalized.contains("THRU") || normalized.contains("EXPIRES"))) return null
    return NfcExpiryCandidate(match.groupValues[1], year)
}

private fun extractNfcHolderName(text: String): String? {
    val normalized = text.trim().replace(Regex("\\s+"), " ")
    if (normalized.length !in 5..32) return null
    if (normalized.any { it.isDigit() }) return null
    val blacklist = listOf("CARD", "NUMBER", "VALID", "THRU", "THROUGH", "EXP", "EXPIRES", "UID", "TAG")
    if (blacklist.any { normalized.uppercase().contains(it) }) return null
    val nameRegex = Regex("""^[A-Z][A-Z'\-]+(?:\s+[A-Z][A-Z'\-]+){1,3}$""", RegexOption.IGNORE_CASE)
    return normalized.takeIf { nameRegex.matches(it) }?.uppercase()
}

private fun nfcLuhnCheck(number: String): Boolean {
    val digits = number.map { it.digitToInt() }
    var sum = 0
    var alternate = false
    for (i in digits.size - 1 downTo 0) {
        var d = digits[i]
        if (alternate) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
        alternate = !alternate
    }
    return sum % 10 == 0
}

private fun detectNfcCardType(number: String): String {
    val clean = number.replace(" ", "")
    return when {
        clean.startsWith("4") -> "Visa"
        clean.startsWith("5") || clean.startsWith("2") -> "Mastercard"
        clean.startsWith("34") || clean.startsWith("37") -> "Amex"
        clean.startsWith("6") -> "Discover"
        else -> "Card"
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

class MarkdownVisualTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
            val rawText = text.text
            append(rawText)

            // 1. Parse lines for Heading, Quote, Bullet, Divider
            val lines = rawText.split("\n")
            var currentOffset = 0
            lines.forEachIndexed { index, line ->
                val lineLength = line.length
                val nextOffset = currentOffset + lineLength + 1 // +1 for newline

                when {
                    line.startsWith("### ") -> {
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent, fontSize = 0.sp),
                            currentOffset, currentOffset + 4
                        )
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = VaultTextPrimary),
                            currentOffset + 4, currentOffset + lineLength
                        )
                    }
                    line.startsWith("## ") -> {
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent, fontSize = 0.sp),
                            currentOffset, currentOffset + 3
                        )
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = VaultTextPrimary),
                            currentOffset + 3, currentOffset + lineLength
                        )
                    }
                    line.startsWith("# ") -> {
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent, fontSize = 0.sp),
                            currentOffset, currentOffset + 2
                        )
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = VaultTextPrimary),
                            currentOffset + 2, currentOffset + lineLength
                        )
                    }
                    line.startsWith("> ") -> {
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent, fontSize = 0.sp),
                            currentOffset, currentOffset + 2
                        )
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = VaultTextSecondary, fontSize = 16.sp),
                            currentOffset + 2, currentOffset + lineLength
                        )
                    }
                    line.startsWith("• ") || line.startsWith("- ") -> {
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(color = VaultOrange, fontWeight = FontWeight.Bold),
                            currentOffset, currentOffset + 2
                        )
                    }
                    line.trim() == "---" || line.trim() == "***" -> {
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(color = VaultOrange, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                            currentOffset, currentOffset + lineLength
                        )
                    }
                }
                currentOffset = nextOffset
            }

            // 2. Make ALL '*' characters in the entire text completely transparent and 0.sp
            // This prevents any raw stars from ever flickering/appearing when formatting is incomplete or deleted!
            val starRegex = Regex("\\*")
            starRegex.findAll(rawText).forEach { match ->
                addStyle(
                    androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color.Transparent, fontSize = 0.sp),
                    match.range.first, match.range.first + 1
                )
            }

            // 3. Find and apply bold formatting spans (**text**)
            val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
            boldRegex.findAll(rawText).forEach { match ->
                val range = match.range
                addStyle(
                    androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold),
                    range.first + 2, range.last - 1
                )
            }

            // 4. Find and apply italic formatting spans (*text*)
            val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)")
            italicRegex.findAll(rawText).forEach { match ->
                val range = match.range
                addStyle(
                    androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    range.first + 1, range.last
                )
            }
        }
        return androidx.compose.ui.text.input.TransformedText(annotatedString, androidx.compose.ui.text.input.OffsetMapping.Identity)
    }
}
