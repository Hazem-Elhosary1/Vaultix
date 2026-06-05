package com.vaultix.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.vaultix.app.R
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import com.vaultix.app.ui.viewmodel.CardViewModel
import com.vaultix.app.ui.viewmodel.IdentityViewModel
import com.vaultix.app.ui.viewmodel.NoteViewModel
import com.vaultix.app.ui.viewmodel.PasswordViewModel
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip

@Composable
fun ItemDetailScreen(
    itemId: String,
    categoryType: String,
    authViewModel: AuthViewModel,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onViewImage: (String) -> Unit
) {
    when (categoryType) {
        "passwords" -> PasswordDetailScreen(itemId = itemId, onEdit = onEdit, onBack = onBack)
        "cards" -> CardDetailScreen(itemId = itemId, onEdit = onEdit, onBack = onBack)
        "notes" -> NoteDetailScreen(itemId = itemId, onEdit = onEdit, onBack = onBack)
        "wifi" -> WifiDetailScreen(itemId = itemId, onEdit = onEdit, onBack = onBack)
        "identities" -> IdentityDetailScreen(itemId = itemId, onEdit = onEdit, onBack = onBack, onViewImage = onViewImage)
        else -> GenericDetailScreen(onBack = onBack)
    }
}

@Composable
private fun IdentityDetailScreen(itemId: String, onEdit: () -> Unit, onBack: () -> Unit, onViewImage: (String) -> Unit) {
    val viewModel: IdentityViewModel = hiltViewModel()
    val identities by viewModel.allIdentities.collectAsStateWithLifecycle(emptyList())
    val identity = remember(identities, itemId) { identities.find { it.id == itemId } }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copyMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(identity?.documentName ?: stringResource(R.string.identity_label), fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = VaultTextPrimary) } },
                actions = {
                    identity?.let { id ->
                        IconButton(onClick = { viewModel.toggleFavorite(id) }) {
                            Icon(
                                if (id.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(R.string.favorites),
                                tint = if (id.isFavorite) VaultError else VaultOrange
                            )
                        }
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = VaultOrange) }
                    IconButton(onClick = { identity?.let { viewModel.deleteIdentity(it); onBack() } }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = VaultError)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        identity?.let { id ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
                    Column {
                        if (id.imagePaths.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(id.imagePaths) { path ->
                                    val bitmap = remember(path) {
                                        viewModel.decryptImage(path)?.asImageBitmap()
                                    }
                                    bitmap?.let {
                                        Image(
                                            bitmap = it,
                                            contentDescription = stringResource(R.string.identity_photo_desc),
                                            modifier = Modifier
                                                .fillParentMaxWidth()
                                                .height(200.dp)
                                                .clickable { onViewImage(path) },
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(56.dp).background(CategoryIDs.copy(0.15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Badge, null, tint = CategoryIDs, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(id.documentName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                                Text(id.documentType, fontSize = 13.sp, color = VaultTextSecondary)
                            }
                        }
                    }
                }

                DetailField(stringResource(R.string.field_full_name), id.fullName) {
                    copyToClipboard(context, "Name", id.fullName)
                    scope.launch { copyMessage = context.getString(R.string.name_copied_toast); delay(2000); copyMessage = null }
                }

                DetailField(stringResource(R.string.field_id_number), id.documentNumber) {
                    copyToClipboard(context, "ID", id.documentNumber)
                    scope.launch { copyMessage = context.getString(R.string.id_number_copied_toast); delay(2000); copyMessage = null }
                }

                if (id.dateOfBirth.isNotEmpty()) {
                    DetailField(stringResource(R.string.field_date_of_birth), id.dateOfBirth) {
                        copyToClipboard(context, "Date of Birth", id.dateOfBirth)
                        scope.launch { copyMessage = context.getString(R.string.dob_copied_toast); delay(2000); copyMessage = null }
                    }
                }

                if (id.nationality.isNotEmpty()) {
                    DetailField(stringResource(R.string.field_nationality), id.nationality) {
                        copyToClipboard(context, "Nationality", id.nationality)
                        scope.launch { copyMessage = context.getString(R.string.nationality_copied_toast); delay(2000); copyMessage = null }
                    }
                }

                if (id.issuedBy.isNotEmpty()) {
                    DetailField(stringResource(R.string.field_issued_by), id.issuedBy) {
                        copyToClipboard(context, "Issued By", id.issuedBy)
                        scope.launch { copyMessage = context.getString(R.string.issued_by_copied_toast); delay(2000); copyMessage = null }
                    }
                }

                if (id.issuedDate.isNotEmpty() || id.expiryDate.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (id.issuedDate.isNotEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                DetailField(stringResource(R.string.field_issued_date), id.issuedDate, onCopy = null)
                            }
                        }
                        if (id.expiryDate.isNotEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                DetailField(stringResource(R.string.field_expiry_date), id.expiryDate, onCopy = null)
                            }
                        }
                    }
                }

                if (id.notes.isNotEmpty()) {
                    DetailField(stringResource(R.string.field_notes), id.notes) {
                        copyToClipboard(context, "Notes", id.notes)
                        scope.launch { copyMessage = context.getString(R.string.notes_copied_toast); delay(2000); copyMessage = null }
                    }
                }

                copyMessage?.let { msg ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = VaultSuccess.copy(0.12f)), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = VaultSuccess, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(msg, fontSize = 12.sp, color = VaultSuccess)
                        }
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.item_not_found), color = VaultTextSecondary)
        }
    }
}

@Composable
private fun PasswordDetailScreen(itemId: String, onEdit: () -> Unit, onBack: () -> Unit) {
    val viewModel: PasswordViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val password = remember(state.passwords, itemId) { state.passwords.find { it.id == itemId } }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copyMessage by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var revealJob by remember { mutableStateOf<Job?>(null) }

    // Auto-blur/hide when app is backgrounded
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                showPassword = false
                revealJob?.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Strict Secure Reveal: No temporary reveal.
    // Passwords are only shown while actively holding the button.

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(password?.title ?: stringResource(R.string.password), fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = VaultTextPrimary) } },
                actions = {
                    password?.let { pwd ->
                        IconButton(onClick = { viewModel.toggleFavorite(pwd) }) {
                            Icon(
                                if (pwd.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(R.string.favorites),
                                tint = if (pwd.isFavorite) VaultError else VaultOrange
                            )
                        }
                    }
                    password?.let { pwd ->
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = VaultOrange) }
                        IconButton(onClick = { viewModel.deletePassword(pwd.id); onBack() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = VaultError)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        password?.let { pwd ->
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).background(CategoryPasswords.copy(0.15f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Key, null, tint = CategoryPasswords, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(pwd.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                            Text(pwd.username, fontSize = 14.sp, color = VaultTextSecondary)
                            Spacer(Modifier.height(4.dp))
                            val pfmt = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                            Text(stringResource(R.string.created_at, pfmt.format(Date(pwd.createdAt))), fontSize = 11.sp, color = VaultTextSecondary.copy(alpha = 0.8f))
                            Text(stringResource(R.string.updated_at, pfmt.format(Date(pwd.updatedAt))), fontSize = 11.sp, color = VaultTextSecondary.copy(alpha = 0.8f))
                        }
                    }
                }

                DetailField(stringResource(R.string.field_username), pwd.username) {
                    copyToClipboard(context, "Username", pwd.username)
                    scope.launch { copyMessage = context.getString(R.string.username_copied_toast); delay(2000); copyMessage = null }
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.password), fontSize = 11.sp, color = VaultTextSecondary, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(if (showPassword) pwd.password.concatToString() else "••••••••", fontSize = 15.sp, color = VaultTextPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            try {
                                                showPassword = true
                                                awaitRelease()
                                            } finally {
                                                showPassword = false
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = if (showPassword) VaultOrange else VaultTextSecondary)
                        }
                        IconButton(onClick = {
                            copyToClipboard(context, "Password", pwd.password)
                            scope.launch { copyMessage = context.getString(R.string.password_copied_toast); delay(2000); copyMessage = null }
                        }) {
                            Icon(Icons.Default.ContentCopy, null, tint = VaultOrange)
                        }
                    }
                }

                if (pwd.passwordHistory.isNotEmpty()) {
                    var showHistory by remember { mutableStateOf(false) }
                    Column {
                        TextButton(
                            onClick = { showHistory = !showHistory },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(if (showHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = VaultOrange)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.password_history_title, pwd.passwordHistory.size), color = VaultOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        if (showHistory) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                pwd.passwordHistory.forEachIndexed { index, oldPwd ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = VaultSurface.copy(alpha = 0.5f))
                                    ) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(stringResource(R.string.old_password_label, index + 1), fontSize = 9.sp, color = VaultTextDisabled, letterSpacing = 1.sp)
                                                Text("••••••••", fontSize = 14.sp, color = VaultTextSecondary)
                                            }
                                            IconButton(onClick = {
                                                copyToClipboard(context, "Old Password", oldPwd)
                                                scope.launch { copyMessage = context.getString(R.string.old_password_copied_toast); delay(2000); copyMessage = null }
                                            }) {
                                                Icon(Icons.Default.ContentCopy, null, tint = VaultTextSecondary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (pwd.website.isNotEmpty()) {
                    DetailField(stringResource(R.string.field_website), pwd.website) {
                        copyToClipboard(context, "Website", pwd.website)
                        scope.launch { copyMessage = context.getString(R.string.link_copied_toast); delay(2000); copyMessage = null }
                    }
                }

                if (pwd.appPackageName.isNotEmpty()) {
                    DetailField(stringResource(R.string.field_app_package), pwd.appPackageName) {
                        copyToClipboard(context, "App Package", pwd.appPackageName)
                        scope.launch { copyMessage = context.getString(R.string.package_copied_toast); delay(2000); copyMessage = null }
                    }
                }

                copyMessage?.let { msg ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = VaultSuccess.copy(0.12f)), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = VaultSuccess, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(msg, fontSize = 12.sp, color = VaultSuccess)
                        }
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.item_not_found), color = VaultTextSecondary)
        }
    }
}

@Composable
private fun CardDetailScreen(itemId: String, onEdit: () -> Unit, onBack: () -> Unit) {
    val viewModel: CardViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val card = remember(state.cards, itemId) { state.cards.find { it.id == itemId } }

    var showCardNumber by remember { mutableStateOf(false) }
    var showCvv by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(card?.cardName ?: stringResource(R.string.card_label), fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = VaultTextPrimary) } },
                actions = {
                    card?.let { c ->
                        IconButton(onClick = { viewModel.toggleFavorite(c) }) {
                            Icon(
                                if (c.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(R.string.favorites),
                                tint = if (c.isFavorite) VaultError else VaultOrange
                            )
                        }
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = VaultOrange) }
                        IconButton(onClick = { viewModel.deleteCard(c.id); onBack() }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = VaultError)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        card?.let { c ->
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = VaultNavy)) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(VaultNavy, VaultNavyLight))).padding(24.dp)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(c.cardName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                                val displayCardType = when (c.cardType) {
                                    "Visa" -> stringResource(R.string.card_type_visa)
                                    "Mastercard" -> stringResource(R.string.card_type_mastercard)
                                    "Amex" -> stringResource(R.string.card_type_amex)
                                    "Other" -> stringResource(R.string.card_type_other)
                                    else -> c.cardType
                                }
                                Text(displayCardType, fontSize = 16.sp, color = VaultOrange, fontWeight = FontWeight.SemiBold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val scope = rememberCoroutineScope()
                                val context = LocalContext.current
                                var copyMessage by remember { mutableStateOf<String?>(null) }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (showCardNumber) c.cardNumber.chunked(4).joinToString(" ") else "•••• •••• •••• ${c.cardNumber.takeLast(4)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VaultTextPrimary,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier.pointerInput(Unit) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    copyToClipboard(context, "Card Number", c.cardNumber)
                                                    scope.launch {
                                                        copyMessage = context.getString(R.string.card_number_copied_toast)
                                                        delay(2000)
                                                        copyMessage = null
                                                    }
                                                }
                                            )
                                        }
                                    )
                                    AnimatedVisibility(visible = copyMessage != null) {
                                        Text(copyMessage ?: "", color = VaultOrange, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    try {
                                                        showCardNumber = true
                                                        awaitRelease()
                                                    } finally {
                                                        showCardNumber = false
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(if (showCardNumber) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = VaultTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text(stringResource(R.string.field_holder), fontSize = 9.sp, color = VaultTextSecondary, letterSpacing = 1.sp); Text(c.holderName, fontSize = 13.sp, color = VaultTextPrimary) }
                                Column(horizontalAlignment = Alignment.End) { Text(stringResource(R.string.field_expires), fontSize = 9.sp, color = VaultTextSecondary, letterSpacing = 1.sp); Text("${c.expiryMonth}/${c.expiryYear}", fontSize = 13.sp, color = VaultTextPrimary) }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.cvv), fontSize = 9.sp, color = VaultTextSecondary, letterSpacing = 1.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (showCvv) c.cvv else "•••", fontSize = 13.sp, color = VaultTextPrimary)
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onPress = {
                                                            try {
                                                                showCvv = true
                                                                awaitRelease()
                                                            } finally {
                                                                showCvv = false
                                                            }
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(if (showCvv) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = VaultTextSecondary, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (c.isExpired) {
                    Card(colors = CardDefaults.cardColors(containerColor = VaultError.copy(0.12f)), shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = VaultError)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.card_expired), color = VaultError, fontSize = 14.sp)
                        }
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.item_not_found), color = VaultTextSecondary)
        }
    }
}

@Composable
private fun NoteDetailScreen(itemId: String, onEdit: () -> Unit, onBack: () -> Unit) {
    val viewModel: NoteViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val note = remember(state.notes, itemId) { state.notes.find { it.id == itemId } }
    val context = LocalContext.current

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val noteAccentColor = remember(note?.color) {
        try { Color(android.graphics.Color.parseColor(note?.color ?: "#1A3A5C")) }
        catch (_: Exception) { VaultSurface }
    }

    val quickColors = listOf(
        "#1A3A5C", "#4A1942", "#1B4332", "#5C1A1A",
        "#5C3D1A", "#1A4A4A", "#3D1A5C", "#4A4A1A"
    )

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = VaultTextPrimary) } },
                actions = {
                    note?.let { n ->
                        // Copy content
                        IconButton(onClick = {
                            copyToClipboard(context, "Note Content", n.content)
                        }) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.copy), tint = VaultTextSecondary)
                        }
                        // Favorite toggle
                        IconButton(onClick = { viewModel.toggleFavorite(n) }) {
                            Icon(
                                if (n.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                stringResource(R.string.favorites),
                                tint = if (n.isFavorite) VaultError else VaultTextSecondary
                            )
                        }
                        // Edit
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = VaultOrange) }
                        // Delete
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = VaultError)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        note?.let { n ->
            val wordCount = remember(n.content) { n.content.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size }
            val charCount = remember(n.content) { n.content.length }
            val pattern = stringResource(R.string.note_date_format)
            val dateFmt = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Main scrollable content area
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 8.dp)
                        .background(noteAccentColor.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                        .padding(1.dp)
                        .background(noteAccentColor.copy(alpha = 0.06f), RoundedCornerShape(23.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp)
                ) {
                    // Title
                    Text(
                        n.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = VaultTextPrimary,
                        lineHeight = 32.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Metadata row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Favorite badge
                        if (n.isFavorite) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = VaultOrange, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(stringResource(R.string.favorites), fontSize = 11.sp, color = VaultOrange, fontWeight = FontWeight.Medium)
                            }
                            Box(Modifier.size(3.dp).background(VaultTextDisabled, androidx.compose.foundation.shape.CircleShape))
                        }
                        // Word count
                        Text(
                            stringResource(R.string.word_char_count, wordCount, charCount),
                            fontSize = 11.sp,
                            color = VaultTextSecondary.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Timestamps
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.created_at, dateFmt.format(Date(n.createdAt))),
                            fontSize = 11.sp,
                            color = VaultTextSecondary.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        stringResource(R.string.updated_at, dateFmt.format(Date(n.updatedAt))),
                        fontSize = 11.sp,
                        color = VaultTextSecondary.copy(alpha = 0.5f)
                    )

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = noteAccentColor.copy(alpha = 0.15f), thickness = 1.dp)
                    Spacer(Modifier.height(20.dp))

                    // Content
                    FormattedNoteContent(n.content)
                }

                // Quick color strip at the bottom
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = VaultSurface.copy(alpha = 0.6f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.field_theme), fontSize = 11.sp, color = VaultTextSecondary, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickColors.forEach { colorHex ->
                                val chipColor = remember(colorHex) {
                                    try { Color(android.graphics.Color.parseColor(colorHex)) }
                                    catch (_: Exception) { VaultSurface }
                                }
                                val isSelected = n.color == colorHex
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(chipColor, RoundedCornerShape(7.dp))
                                        .then(
                                            if (isSelected) Modifier
                                                .background(chipColor, RoundedCornerShape(7.dp))
                                            else Modifier
                                        )
                                        .clickable {
                                            viewModel.updateNote(n.copy(color = colorHex))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check, null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } ?: Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.item_not_found), color = VaultTextSecondary)
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm && note != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_note_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_note_confirm, note.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(note.id)
                    showDeleteConfirm = false
                    onBack()
                }) {
                    Text(stringResource(R.string.delete), color = VaultError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = VaultTextSecondary)
                }
            },
            containerColor = VaultSurface
        )
    }
}

@Composable
private fun DetailField(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VaultSurface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = VaultTextSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(value, fontSize = 15.sp, color = VaultTextPrimary)
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy", tint = VaultOrange) }
            }
        }
    }
}

@Composable
fun FormattedNoteContent(content: String) {
    val blocks = remember(content) { parseNoteBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> {
                    val (fontSize, lineH) = when (block.level) {
                        1 -> 24.sp to 30.sp
                        2 -> 20.sp to 26.sp
                        else -> 17.sp to 23.sp
                    }
                    Text(
                        text = applyInlineStyles(block.text),
                        fontSize = fontSize,
                        lineHeight = lineH,
                        fontWeight = FontWeight.Bold,
                        color = VaultTextPrimary
                    )
                    if (block.level == 1) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
                is NoteBlock.Quote -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .defaultMinSize(minHeight = 20.dp)
                                .fillMaxHeight()
                                .background(VaultOrange.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = applyInlineStyles(block.text),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = VaultTextSecondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
                is NoteBlock.Bullet -> {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                        Text(
                            "•",
                            fontSize = 16.sp,
                            color = VaultOrange,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(16.dp)
                        )
                        Text(
                            text = applyInlineStyles(block.text),
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            color = VaultTextPrimary
                        )
                    }
                }
                is NoteBlock.HorizontalRule -> {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        color = VaultTextDisabled.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                is NoteBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        Text(
                            text = applyInlineStyles(block.text),
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            color = VaultTextPrimary
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

sealed class NoteBlock {
    data class Heading(val level: Int, val text: String) : NoteBlock()
    data class Quote(val text: String) : NoteBlock()
    data class Bullet(val text: String) : NoteBlock()
    object HorizontalRule : NoteBlock()
    data class Paragraph(val text: String) : NoteBlock()
}

fun parseNoteBlocks(text: String): List<NoteBlock> {
    val lines = text.split("\n")
    return lines.map { line ->
        when {
            line.trimEnd() == "---" || line.trimEnd() == "***" || line.trimEnd() == "___" ->
                NoteBlock.HorizontalRule
            line.startsWith("### ") ->
                NoteBlock.Heading(3, line.substring(4))
            line.startsWith("## ") ->
                NoteBlock.Heading(2, line.substring(3))
            line.startsWith("# ") ->
                NoteBlock.Heading(1, line.substring(2))
            line.startsWith("> ") ->
                NoteBlock.Quote(line.substring(2))
            line.startsWith("• ") ->
                NoteBlock.Bullet(line.substring(2))
            line.startsWith("- ") ->
                NoteBlock.Bullet(line.substring(2))
            line.startsWith("* ") && !line.startsWith("**") ->
                NoteBlock.Bullet(line.substring(2))
            else ->
                NoteBlock.Paragraph(line)
        }
    }
}

fun applyInlineStyles(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var processedText = text
        val boldRanges = mutableListOf<IntRange>()
        val italicRanges = mutableListOf<IntRange>()

        // 1. Find and strip bold **text**
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        var match = boldRegex.find(processedText)
        while (match != null) {
            val content = match.groupValues[1]
            val range = match.range
            processedText = processedText.replaceRange(range, content)
            boldRanges.add(range.first until (range.first + content.length))
            match = boldRegex.find(processedText)
        }

        // 2. Find and strip italic *text*
        val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.*?)(?<!\\*)\\*(?!\\*)")
        var iMatch = italicRegex.find(processedText)
        while (iMatch != null) {
            val content = iMatch.groupValues[1]
            val range = iMatch.range
            processedText = processedText.replaceRange(range, content)
            italicRanges.add(range.first until (range.first + content.length))
            iMatch = italicRegex.find(processedText)
        }

        append(processedText)

        // Apply styles
        boldRanges.forEach { range ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold),
                range.first, (range.last + 1).coerceAtMost(processedText.length)
            )
        }
        italicRanges.forEach { range ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = VaultTextSecondary),
                range.first, (range.last + 1).coerceAtMost(processedText.length)
            )
        }
    }
}

@Composable
private fun GenericDetailScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.item_details), color = VaultTextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = VaultTextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.item_not_found), color = VaultTextSecondary)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: Any) {
    val stringValue = when (value) {
        is CharArray -> value.concatToString()
        else -> value.toString()
    }
    try {
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.vaultix.app.security.ClipboardSecurityManager.ClipboardSecurityEntryPoint::class.java
        )
        val manager = entryPoint.clipboardSecurityManager()
        manager.copyAndScheduleClear(label, stringValue)
    } catch (e: Exception) {
        // Fallback to basic clipboard if Hilt entry point fails
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, stringValue))
        
        // Manual auto-clear fallback
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            } catch (_: Exception) {}
        }, 30_000L)
        
        android.widget.Toast.makeText(context, context.getString(R.string.copied_auto_clear_toast, label), android.widget.Toast.LENGTH_SHORT).show()
    }
}
