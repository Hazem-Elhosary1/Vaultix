package com.vaultix.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer

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
        "wifi" -> stringResource(R.string.wifi)
        else -> stringResource(R.string.app_name)
    }
    val accentColor = MaterialTheme.colorScheme.primary

    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf("date") }
    var isAscending by remember { mutableStateOf(false) }

    val sortOptions = remember(categoryType) {
        when (categoryType) {
            "passwords" -> listOf("date", "name", "strength")
            "cards" -> listOf("date", "name", "expiry")
            "notes" -> listOf("date", "name")
            "files" -> listOf("date", "name", "size")
            "identities" -> listOf("date", "name", "type", "expiry")
            "wifi" -> listOf("date", "name", "strength")
            else -> listOf("date", "name")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                // Sort Chip Row
                SortChipRow(
                    options = sortOptions,
                    selected = currentSort,
                    onSelect = { currentSort = it },
                    isAscending = isAscending,
                    onToggleOrder = { isAscending = !isAscending },
                    accentColor = accentColor
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
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.CameraAlt, "Scan Card")
                        }
                        // Add FAB
                        FloatingActionButton(
                            onClick = onNavigateToAdd,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add")
                        }
                    }
                }
                "files" -> {
                    FloatingActionButton(
                        onClick = onNavigateToFileVault,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Upload, "Import File")
                    }
                }
                "identities" -> {
                    FloatingActionButton(
                        onClick = { onNavigateToIdentityEdit(null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
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
                    sortKey = currentSort,
                    isAscending = isAscending,
                    onItemClick = onNavigateToDetail,
                    accentColor = accentColor
                )
                "cards" -> CardList(
                    searchQuery = searchQuery,
                    sortKey = currentSort,
                    isAscending = isAscending,
                    onItemClick = onNavigateToDetail,
                    accentColor = accentColor
                )
                "notes" -> NoteList(
                    searchQuery = searchQuery,
                    sortKey = currentSort,
                    isAscending = isAscending,
                    onItemClick = onNavigateToDetail,
                    accentColor = accentColor
                )
                "files" -> FileList(
                    searchQuery = searchQuery,
                    sortKey = currentSort,
                    isAscending = isAscending,
                    accentColor = accentColor
                )
                "identities" -> IdentityList(
                    searchQuery = searchQuery,
                    sortKey = currentSort,
                    isAscending = isAscending,
                    accentColor = accentColor,
                    onItemClick = { id -> onNavigateToDetail(id) }
                )
                "wifi" -> WifiList(
                    searchQuery = searchQuery,
                    sortKey = currentSort,
                    isAscending = isAscending,
                    onItemClick = onNavigateToDetail,
                    accentColor = accentColor
                )
                else -> EmptyState(categoryType, Icons.Default.Folder, accentColor)
            }
        }
    }
}

@Composable
private fun PasswordList(
    searchQuery: String,
    sortKey: String,
    isAscending: Boolean,
    onItemClick: (String) -> Unit,
    accentColor: Color
) {
    val viewModel: PasswordViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }

    val sorted = remember(state.passwords, sortKey, isAscending) {
        when (sortKey) {
            "name" -> if (!isAscending) state.passwords.sortedBy { it.title.lowercase() } else state.passwords.sortedByDescending { it.title.lowercase() }
            "strength" -> if (!isAscending) state.passwords.sortedByDescending { it.passwordStrength } else state.passwords.sortedBy { it.passwordStrength }
            else -> if (!isAscending) state.passwords.sortedByDescending { it.updatedAt } else state.passwords.sortedBy { it.updatedAt }
        }
    }

    if (sorted.isEmpty()) {
        EmptyState(stringResource(R.string.passwords), Icons.Default.VpnKey, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sorted, key = { it.id }) { password ->
            PasswordListItem(
                password = password,
                accentColor = accentColor,
                onClick = { onItemClick(password.id) },
                onDelete = { viewModel.deletePassword(password.id) },
                onToggleFavorite = { viewModel.toggleFavorite(password) }
            )
        }
    }
}

@Composable
private fun PasswordListItem(
    password: Password,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val offsetXAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 1. Background Action Rows Layer
        if (kotlin.math.abs(offsetXAnim.value) > 1f) {
            val isSwipingRight = offsetXAnim.value > 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSwipingRight) {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            onToggleFavorite()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            if (password.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (password.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Password",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Foreground Card Layer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetXAnim.value.roundToInt(), 0) }
                .pointerInput(password.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = when {
                                    offsetXAnim.value > 40.dp.toPx() -> 70.dp.toPx()
                                    offsetXAnim.value < -40.dp.toPx() -> -70.dp.toPx()
                                    else -> 0f
                                }
                                offsetXAnim.animateTo(target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newOffset = (offsetXAnim.value + dragAmount).coerceIn(-90.dp.toPx(), 90.dp.toPx())
                                offsetXAnim.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(password.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        if (password.isFavorite) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(password.username, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (password.website.isNotEmpty()) {
                        Text(password.website, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                    }
                }

                StrengthDot(strength = password.passwordStrength)

                Spacer(Modifier.width(8.dp))

                IconButton(onClick = {
                    scope.launch {
                        val target = if (offsetXAnim.value == 0f) with(density) { -70.dp.toPx() } else 0f
                        offsetXAnim.animateTo(target)
                    }
                }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_password)) },
            text = { Text(stringResource(R.string.delete_confirm, password.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
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
    sortKey: String,
    isAscending: Boolean,
    onItemClick: (String) -> Unit,
    accentColor: Color
) {
    val viewModel: CardViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val filtered = remember(state.cards, searchQuery, sortKey, isAscending) {
        val base = if (searchQuery.isBlank()) state.cards
        else state.cards.filter { it.cardName.contains(searchQuery, true) || it.holderName.contains(searchQuery, true) }
        when (sortKey) {
            "name" -> if (!isAscending) base.sortedBy { it.cardName.lowercase() } else base.sortedByDescending { it.cardName.lowercase() }
            "expiry" -> {
                val selector: (Card) -> Int = { (it.expiryYear.toIntOrNull() ?: 99) * 100 + (it.expiryMonth.toIntOrNull() ?: 99) }
                if (!isAscending) base.sortedBy(selector) else base.sortedByDescending(selector)
            }
            else -> if (!isAscending) base.sortedByDescending { it.updatedAt } else base.sortedBy { it.updatedAt }
        }
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
            CardListItem(
                card = card,
                onClick = { onItemClick(card.id) },
                onDelete = { viewModel.deleteCard(card.id) },
                onToggleFavorite = { viewModel.toggleFavorite(card) }
            )
        }
    }
}

@Composable
private fun CardListItem(
    card: Card,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val offsetXAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 1. Background Action Rows Layer
        if (kotlin.math.abs(offsetXAnim.value) > 1f) {
            val isSwipingRight = offsetXAnim.value > 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSwipingRight) {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            onToggleFavorite()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            if (card.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (card.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Card",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Foreground Card Layer (Swipeable & Clickable)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetXAnim.value.roundToInt(), 0) }
                .pointerInput(card.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = when {
                                    offsetXAnim.value > 40.dp.toPx() -> 70.dp.toPx()
                                    offsetXAnim.value < -40.dp.toPx() -> -70.dp.toPx()
                                    else -> 0f
                                }
                                offsetXAnim.animateTo(target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newOffset = (offsetXAnim.value + dragAmount).coerceIn(-90.dp.toPx(), 90.dp.toPx())
                                offsetXAnim.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(card.cardName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                            if (card.isFavorite) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(card.cardType, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary.copy(0.8f), fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(card.maskedCardNumber, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stringResource(R.string.accent_color).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(0.6f), letterSpacing = 1.sp)
                            Text(card.holderName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.appearance).uppercase(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary.copy(0.6f), letterSpacing = 1.sp)
                            Text("${card.expiryMonth}/${card.expiryYear}", fontSize = 14.sp, color = if (card.isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_password)) },
            text = { Text(stringResource(R.string.delete_confirm, card.cardName)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun NoteList(
    searchQuery: String,
    sortKey: String,
    isAscending: Boolean,
    onItemClick: (String) -> Unit,
    accentColor: Color
) {
    val viewModel: NoteViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(searchQuery) { viewModel.setSearchQuery(searchQuery) }

    val sorted = remember(state.notes, sortKey, isAscending) {
        val pinned = state.notes.filter { it.isPinned }
        val unpinned = state.notes.filter { !it.isPinned }
        val sortedUnpinned = when (sortKey) {
            "name" -> if (!isAscending) unpinned.sortedBy { it.title.lowercase() } else unpinned.sortedByDescending { it.title.lowercase() }
            else -> if (!isAscending) unpinned.sortedByDescending { it.updatedAt } else unpinned.sortedBy { it.updatedAt }
        }
        pinned.sortedByDescending { it.updatedAt } + sortedUnpinned
    }

    if (sorted.isEmpty()) {
        EmptyState(stringResource(R.string.notes), Icons.Default.Description, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sorted, key = { it.id }) { note ->
            NoteListItem(
                note = note,
                onClick = { onItemClick(note.id) },
                onDelete = { viewModel.deleteNote(note.id) },
                onToggleFavorite = { viewModel.toggleFavorite(note) },
                onTogglePin = { viewModel.togglePin(note) }
            )
        }
    }
}

@Composable
private fun NoteListItem(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val noteAccentColor = remember(note.color) {
        try { Color(android.graphics.Color.parseColor(note.color)) }
        catch (_: Exception) { CategoryNotes }
    }

    val dateFmt = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }

    // Smooth Swipe Gestures State
    val offsetXAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 1. Background Action Rows Layer
        // Revealed only when the foreground card is actively swiped (to prevent visual bleed through semi-transparent cards at rest)
        if (kotlin.math.abs(offsetXAnim.value) > 1f) {
            val isSwipingRight = offsetXAnim.value > 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSwipingRight) {
                    // Left Side Actions (Pin, Favorite) - Revealed when swiping right (positive offset)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pin Action Button
                        IconButton(
                            onClick = {
                                scope.launch { offsetXAnim.animateTo(0f) }
                                onTogglePin()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Toggle Pin",
                                tint = if (note.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Favorite Action Button
                        IconButton(
                            onClick = {
                                scope.launch { offsetXAnim.animateTo(0f) }
                                onToggleFavorite()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                if (note.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Toggle Favorite",
                                tint = if (note.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    // Right Side Actions (Delete) - Revealed when swiping left (negative offset)
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Foreground Note Card Layer (Swipeable & Clickable)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetXAnim.value.roundToInt(), 0) }
                .pointerInput(note.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = when {
                                    offsetXAnim.value > 60.dp.toPx() -> 130.dp.toPx()  // Settle open right (fully reveal both buttons)
                                    offsetXAnim.value < -60.dp.toPx() -> -70.dp.toPx() // Settle open left
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
            colors = CardDefaults.cardColors(containerColor = noteAccentColor.copy(alpha = 0.10f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, noteAccentColor.copy(alpha = 0.18f))
        ) {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                // Left accent strip
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(noteAccentColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                        .defaultMinSize(minHeight = 90.dp)
                )

                Row(
                    Modifier
                        .weight(1f)
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Note avatar box
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(noteAccentColor.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = noteAccentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (note.isPinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(13.dp)
                                        .graphicsLayer(rotationZ = 45f)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                note.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (note.isFavorite) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        if (note.content.isNotBlank()) {
                            Text(
                                stripMarkdown(note.content.take(120).replace("\n", " ")),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(6.dp))
                        }

                        Text(
                            dateFmt.format(java.util.Date(note.updatedAt)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_password)) },
            text = { Text(stringResource(R.string.delete_confirm, note.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
private fun FileList(searchQuery: String, sortKey: String, isAscending: Boolean, accentColor: Color) {
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

    val sorted = remember(files, searchQuery, sortKey, isAscending) {
        val base = if (searchQuery.isBlank()) files
        else files.filter { it.fileName.contains(searchQuery, true) }
        when (sortKey) {
            "name" -> if (!isAscending) base.sortedBy { it.fileName.lowercase() } else base.sortedByDescending { it.fileName.lowercase() }
            "size" -> if (!isAscending) base.sortedByDescending { it.fileSizeBytes } else base.sortedBy { it.fileSizeBytes }
            else -> if (!isAscending) base.sortedByDescending { it.updatedAt } else base.sortedBy { it.updatedAt }
        }
    }

    if (sorted.isEmpty()) {
        EmptyState(stringResource(R.string.files), Icons.Default.FolderOpen, accentColor)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                stringResource(R.string.encrypted_files_count, sorted.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        items(sorted, key = { it.id }) { file ->
            FileListItem(
                file = file,
                accentColor = accentColor,
                onClick = {
                    selectedFile = file
                    showActionSheet = true
                },
                onDelete = { viewModel.deleteFile(file.id) },
                onToggleFavorite = { viewModel.toggleFavorite(file) }
            )
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
private fun FileListItem(
    file: com.vaultix.app.data.model.VaultFile,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val offsetXAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 1. Background Action Rows Layer
        if (kotlin.math.abs(offsetXAnim.value) > 1f) {
            val isSwipingRight = offsetXAnim.value > 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSwipingRight) {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            onToggleFavorite()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            if (file.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (file.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete File",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Foreground Card Layer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetXAnim.value.roundToInt(), 0) }
                .pointerInput(file.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = when {
                                    offsetXAnim.value > 40.dp.toPx() -> 70.dp.toPx()
                                    offsetXAnim.value < -40.dp.toPx() -> -70.dp.toPx()
                                    else -> 0f
                                }
                                offsetXAnim.animateTo(target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newOffset = (offsetXAnim.value + dragAmount).coerceIn(-90.dp.toPx(), 90.dp.toPx())
                                offsetXAnim.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(file.fileName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (file.isFavorite) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text("${(file.fileSizeBytes / 1024)} KB • 🔒 ${stringResource(R.string.encrypted)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    scope.launch {
                        val target = if (offsetXAnim.value == 0f) with(density) { -70.dp.toPx() } else 0f
                        offsetXAnim.animateTo(target)
                    }
                }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_password)) },
            text = { Text(stringResource(R.string.delete_confirm, file.fileName)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}


@Composable
private fun IdentityList(
    searchQuery: String,
    sortKey: String,
    isAscending: Boolean,
    accentColor: Color,
    onItemClick: (String) -> Unit
) {
    val viewModel: IdentityViewModel = hiltViewModel()
    val identities by viewModel.allIdentities.collectAsStateWithLifecycle()

    val filtered = remember(identities, searchQuery, sortKey, isAscending) {
        val base = if (searchQuery.isBlank()) identities
        else identities.filter {
            it.fullName.contains(searchQuery, true) ||
            it.documentName.contains(searchQuery, true) ||
            it.documentNumber.contains(searchQuery, true)
        }
        when (sortKey) {
            "name" -> if (!isAscending) base.sortedBy { it.fullName.lowercase() } else base.sortedByDescending { it.fullName.lowercase() }
            "type" -> if (!isAscending) base.sortedBy { it.documentType.lowercase() } else base.sortedByDescending { it.documentType.lowercase() }
            "expiry" -> if (!isAscending) base.sortedBy { it.expiryDate } else base.sortedByDescending { it.expiryDate }
            else -> if (!isAscending) base.sortedByDescending { it.updatedAt } else base.sortedBy { it.updatedAt }
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
            IdentityListItem(
                identity = identity,
                accentColor = accentColor,
                onClick = { onItemClick(identity.id) },
                onDelete = { viewModel.deleteIdentity(identity) },
                onToggleFavorite = { viewModel.toggleFavorite(identity) }
            )
        }
    }
}

@Composable
private fun IdentityListItem(
    identity: Identity,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val offsetXAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 1. Background Action Rows Layer
        if (kotlin.math.abs(offsetXAnim.value) > 1f) {
            val isSwipingRight = offsetXAnim.value > 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSwipingRight) {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            onToggleFavorite()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            if (identity.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Toggle Favorite",
                            tint = if (identity.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            scope.launch { offsetXAnim.animateTo(0f) }
                            showDeleteDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Identity",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Foreground Card Layer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetXAnim.value.roundToInt(), 0) }
                .pointerInput(identity.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = when {
                                    offsetXAnim.value > 40.dp.toPx() -> 70.dp.toPx()
                                    offsetXAnim.value < -40.dp.toPx() -> -70.dp.toPx()
                                    else -> 0f
                                }
                                offsetXAnim.animateTo(target)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newOffset = (offsetXAnim.value + dragAmount).coerceIn(-90.dp.toPx(), 90.dp.toPx())
                                offsetXAnim.snapTo(newOffset)
                            }
                        }
                    )
                }
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(identity.documentName.ifEmpty { identity.documentType }, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        if (identity.isFavorite) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(identity.fullName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_password)) },
            text = { Text(stringResource(R.string.delete_confirm, identity.documentName)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

private fun stripMarkdown(text: String): String {
    return try {
        text
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // Bold
            .replace(Regex("\\*([^*]+)\\*"), "$1")     // Italic
            .replace(Regex("(?m)^#{1,3}\\s+"), "")     // Headings
            .replace(Regex("(?m)^>\\s+"), "")          // Quotes
            .replace(Regex("(?m)^[•\\-*]\\s+"), "")     // Bullets
            .replace("---", "")
            .replace("***", "")
            .trim()
    } catch (_: Exception) {
        text
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SortChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    isAscending: Boolean,
    onToggleOrder: () -> Unit,
    accentColor: Color
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isAscending) 180f else 0f,
        label = "rotationAngle"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp), // Adjust padding slightly to make arrow fit nicely
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
            items(options) { key ->
                val labelRes = when (key) {
                    "name" -> R.string.sort_name
                    "date" -> if (isAscending) R.string.sort_date_oldest else R.string.sort_date_newest
                    "strength" -> R.string.sort_strength
                    "size" -> if (isAscending) R.string.sort_size_smallest else R.string.sort_size_largest
                    "expiry" -> R.string.sort_expiry
                    "type" -> R.string.sort_type
                    else -> R.string.sort_date_newest
                }
                val isSelected = key == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(key) },
                    label = { Text(stringResource(labelRes), fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                        selectedLabelColor = accentColor,
                        selectedLeadingIconColor = accentColor,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = accentColor,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    )
                )
            }
        }

        IconButton(
            onClick = onToggleOrder,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Toggle Sort Order",
                tint = accentColor,
                modifier = Modifier
                    .graphicsLayer(rotationZ = rotationAngle)
                    .size(22.dp)
            )
        }
    }
}

