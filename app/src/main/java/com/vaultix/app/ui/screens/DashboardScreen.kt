package com.vaultix.app.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.vaultix.app.R
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AuthViewModel
import com.vaultix.app.ui.viewmodel.AppConfigViewModel
import com.vaultix.app.ui.viewmodel.CardViewModel
import com.vaultix.app.ui.viewmodel.CardUiState
import com.vaultix.app.ui.viewmodel.NoteViewModel
import com.vaultix.app.ui.viewmodel.NoteUiState
import com.vaultix.app.ui.viewmodel.PasswordViewModel
import com.vaultix.app.ui.viewmodel.PasswordUiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    authViewModel: AuthViewModel,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onLocked: () -> Unit,
    onNavigateToScan: () -> Unit = {},
    onNavigateToFileVault: () -> Unit = {},
    onNavigateToGenerator: () -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToSecurityAudit: () -> Unit = {},
    onNavigateToDetail: (String, String) -> Unit = { _, _ -> },
    onViewPdf: (String, String) -> Unit = { _, _ -> },
    onViewImage: (String) -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    appConfigViewModel: AppConfigViewModel = hiltViewModel()
) {
    val passwordViewModel: PasswordViewModel = hiltViewModel()
    val cardViewModel: CardViewModel = hiltViewModel()
    val noteViewModel: NoteViewModel = hiltViewModel()

    val passwordState: PasswordUiState by passwordViewModel.uiState.collectAsStateWithLifecycle()
    val cardState: CardUiState by cardViewModel.uiState.collectAsStateWithLifecycle()
    val noteState: NoteUiState by noteViewModel.uiState.collectAsStateWithLifecycle()
    val identityViewModel: com.vaultix.app.ui.viewmodel.IdentityViewModel = hiltViewModel()
    val identityState: List<com.vaultix.app.data.model.Identity> by identityViewModel.allIdentities.collectAsStateWithLifecycle(emptyList())
    val healthViewModel: com.vaultix.app.ui.viewmodel.SecurityHealthViewModel = hiltViewModel()
    val healthState: com.vaultix.app.ui.viewmodel.SecurityHealthState by healthViewModel.healthState.collectAsStateWithLifecycle()
    val fileViewModel: com.vaultix.app.ui.viewmodel.FileViewModel = hiltViewModel()
    val fileState: List<com.vaultix.app.data.model.VaultFile> by fileViewModel.files.collectAsStateWithLifecycle(emptyList())
    val configState by appConfigViewModel.configState.collectAsStateWithLifecycle()

    // Aggregate favorites sorted by last modification (updatedAt)
    val favoriteItems = remember(passwordState.passwords, cardState.cards, noteState.notes, identityState) {
        val favs = mutableListOf<FavoriteDisplayItem>()
        passwordState.passwords.filter { it.isFavorite }.forEach { favs.add(FavoriteDisplayItem(it.id, it.title, "Password", Icons.Default.Key, CategoryPasswords, "passwords", it.updatedAt)) }
        cardState.cards.filter { it.isFavorite }.forEach { favs.add(FavoriteDisplayItem(it.id, it.cardName, "Card", Icons.Default.CreditCard, CategoryCards, "cards", it.updatedAt)) }
        noteState.notes.filter { it.isFavorite }.forEach { favs.add(FavoriteDisplayItem(it.id, it.title, "Note", Icons.Default.Note, CategoryNotes, "notes", it.updatedAt)) }
        identityState.filter { it.isFavorite }.forEach { favs.add(FavoriteDisplayItem(it.id, it.documentName, "ID", Icons.Default.Badge, CategoryIDs, "identities", it.updatedAt)) }
        favs.sortedByDescending { it.updatedAt } 
    }

    // Unified Recent Activities (Sorted by updatedAt)
    val recentActivities = remember(passwordState.passwords, cardState.cards, noteState.notes, fileState, identityState) {
        val items = mutableListOf<RecentActivityItem>()
        passwordState.passwords.take(5).forEach { items.add(RecentActivityItem(it.id, it.title, it.username, "passwords", Icons.Default.Key, CategoryPasswords, it.updatedAt)) }
        cardState.cards.take(5).forEach { items.add(RecentActivityItem(it.id, it.cardName, "**** ${it.cardNumber.takeLast(4)}", "cards", Icons.Default.CreditCard, CategoryCards, it.updatedAt)) }
        noteState.notes.take(5).forEach { items.add(RecentActivityItem(it.id, it.title, "Secure Note", "notes", Icons.Default.Note, CategoryNotes, it.updatedAt)) }
        identityState.take(3).forEach { items.add(RecentActivityItem(it.id, it.documentName, it.documentNumber, "identities", Icons.Default.Badge, CategoryIDs, it.updatedAt)) }
        fileState.take(3).forEach { items.add(RecentActivityItem(it.id, it.fileName, it.mimeType, "files", Icons.Default.Description, CategoryFiles, it.updatedAt)) }
        
        items.sortedByDescending { it.updatedAt }.take(6) 
    }

    val lastPasswords = remember(passwordState.passwords) {
        passwordState.passwords.sortedByDescending { it.createdAt }.take(3)
    }

    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 0..11 -> stringResource(R.string.good_morning)
        in 12..17 -> stringResource(R.string.good_afternoon)
        else -> stringResource(R.string.good_evening)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.app_name), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            if (configState.isPremium) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = VaultOrange,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.premium_badge),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { authViewModel.lock(); onLocked() }) {
                        Icon(Icons.Default.Lock, "Lock", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats overview
            item {
                DashboardStatsCard(
                    passwordCount = passwordState.passwords.size,
                    cardCount = cardState.cards.size,
                    noteCount = noteState.notes.size,
                    idCount = identityState.size,
                    fileCount = fileState.size
                )
            }

            // Security Health Card
            item {
                SecurityHealthCard(healthState, onClick = onNavigateToSecurityAudit)
            }

            // Favorites Carousel
            if (favoriteItems.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.quick_access),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            items(favoriteItems) { fav ->
                                FavoriteQuickCard(
                                    item = fav,
                                    onClick = { onNavigateToDetail(fav.id, fav.type) }
                                )
                            }
                        }
                    }
                }
            }

            // Password Timeline Section (NEW)
            if (lastPasswords.isNotEmpty()) {
                item {
                    PasswordTimelineSection(
                        passwords = lastPasswords,
                        isPremium = configState.isPremium,
                        onUpgrade = onNavigateToPremium,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }

            // Quick search
            item {
                QuickSearchBar(onClick = onNavigateToGlobalSearch)
            }

            // Categories
            item {
                Text(
                    stringResource(R.string.categories),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                CategoryGrid(onNavigateToCategory = onNavigateToCategory)
            }

            // Quick Actions
            item {
                Text(
                    stringResource(R.string.quick_actions),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Quick Actions (Optimized Grid Layout)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionCard(
                            icon = Icons.Default.CameraAlt,
                            title = stringResource(R.string.scan_card),
                            subtitle = stringResource(R.string.ocr),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToScan
                        )
                        QuickActionCard(
                            icon = Icons.Default.FolderOpen,
                            title = stringResource(R.string.file_vault),
                            subtitle = stringResource(R.string.encrypt_files),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToFileVault
                        )
                    }
                    QuickActionCard(
                        icon = Icons.Default.VpnKey,
                        title = stringResource(R.string.password_generator),
                        subtitle = stringResource(R.string.generate_strong_passwords),
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToGenerator
                    )
                }
            }

            // Recent Activities (Unified)
            if (recentActivities.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.recent_activities), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
                items(recentActivities) { activity ->
                    RecentActivityCard(
                        item = activity,
                        onClick = { 
                            if (activity.type == "files") {
                                val file = fileState.find { it.id == activity.id }
                                file?.let {
                                    if (it.mimeType.contains("pdf")) {
                                        onViewPdf(it.id, it.fileName)
                                    } else if (it.mimeType.startsWith("image/")) {
                                        onViewImage(it.encryptedFilePath)
                                    }
                                }
                            } else {
                                onNavigateToDetail(activity.id, activity.type)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStatsCard(
    passwordCount: Int,
    cardCount: Int,
    noteCount: Int,
    idCount: Int,
    fileCount: Int
) {
    val totalCount = passwordCount + cardCount + noteCount + idCount + fileCount
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            VaultSurface
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.vault_overview),
                        fontSize = 14.sp,
                        color = VaultTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "$totalCount ${stringResource(R.string.items_secured)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultTextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(VaultOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Analytics, null, tint = VaultOrange)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Proportional Distribution Bar
            if (totalCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(VaultBlack.copy(0.3f))
                ) {
                    val pWeight = (passwordCount.toFloat() / totalCount).coerceAtLeast(0.01f)
                    val cWeight = (cardCount.toFloat() / totalCount).coerceAtLeast(0.01f)
                    val nWeight = (noteCount.toFloat() / totalCount).coerceAtLeast(0.01f)
                    val iWeight = (idCount.toFloat() / totalCount).coerceAtLeast(0.01f)
                    val fWeight = (fileCount.toFloat() / totalCount).coerceAtLeast(0.01f)

                    Box(Modifier.fillMaxHeight().weight(pWeight).background(CategoryPasswords))
                    Box(Modifier.fillMaxHeight().weight(cWeight).background(CategoryCards))
                    Box(Modifier.fillMaxHeight().weight(nWeight).background(CategoryNotes))
                    Box(Modifier.fillMaxHeight().weight(iWeight).background(CategoryIDs))
                    Box(Modifier.fillMaxHeight().weight(fWeight).background(CategoryFiles))
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatMiniItem(count = passwordCount, label = stringResource(R.string.passwords), color = CategoryPasswords)
                StatMiniItem(count = cardCount, label = stringResource(R.string.cards), color = CategoryCards)
                StatMiniItem(count = noteCount, label = stringResource(R.string.notes), color = CategoryNotes)
                StatMiniItem(count = idCount, label = stringResource(R.string.identities), color = CategoryIDs)
                StatMiniItem(count = fileCount, label = stringResource(R.string.files), color = CategoryFiles)
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = VaultBorder.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(VaultSuccess, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.all_data_encrypted), fontSize = 12.sp, color = VaultSuccess)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = VaultOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.offline_100), fontSize = 12.sp, color = VaultOrange)
                }
            }
        }
    }
}

@Composable
private fun StatMiniItem(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = VaultTextPrimary
        )
        Box(Modifier.size(width = 20.dp, height = 2.dp).background(color, RoundedCornerShape(1.dp)))
        Spacer(Modifier.height(4.dp))
        Text(label.take(4), fontSize = 10.sp, color = VaultTextSecondary)
    }
}

@Composable
private fun StatItem(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(label, fontSize = 12.sp, color = VaultTextSecondary)
    }
}

@Composable
private fun QuickSearchBar(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.search_vault), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), fontSize = 15.sp)
        }
    }
}

data class CategoryItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@Composable
private fun CategoryGrid(onNavigateToCategory: (String) -> Unit) {
    val categories = listOf(
        CategoryItem("Passwords", Icons.Default.Key, CategoryPasswords, "passwords"),
        CategoryItem("Cards", Icons.Default.CreditCard, CategoryCards, "cards"),
        CategoryItem("Notes", Icons.Default.Note, CategoryNotes, "notes"),
        CategoryItem("Files", Icons.Default.Folder, CategoryFiles, "files"),
        CategoryItem("IDs", Icons.Default.Badge, CategoryIDs, "identities")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.take(3).forEach { category ->
                CategoryCard(
                    item = category,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCategory(category.route) }
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.drop(3).forEach { category ->
                CategoryCard(
                    item = category,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCategory(category.route) }
                )
            }
            // Empty weight filler
            if (categories.drop(3).size < 3) {
                Spacer(modifier = Modifier.weight((3 - categories.drop(3).size).toFloat()))
            }
        }
    }
}

@Composable
private fun CategoryCard(
    item: CategoryItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(item.color.copy(alpha = 0.15f), item.color.copy(alpha = 0f))
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(item.color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, null, tint = item.color, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                val title = when(item.route) {
                    "passwords" -> stringResource(R.string.passwords)
                    "cards" -> stringResource(R.string.cards)
                    "notes" -> stringResource(R.string.notes)
                    "files" -> stringResource(R.string.files)
                    "identities" -> stringResource(R.string.identities)
                    else -> item.title
                }
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp)
                    .background(color.copy(0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

data class RecentActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val icon: ImageVector,
    val color: Color,
    val updatedAt: Long
)

@Composable
private fun RecentActivityCard(
    item: RecentActivityItem,
    onClick: () -> Unit
) {
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
                    .size(40.dp)
                    .background(item.color.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VaultTextPrimary)
                Text(item.subtitle, fontSize = 13.sp, color = VaultTextSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = VaultTextDisabled)
        }
    }
}

@Composable
private fun SecurityHealthCard(
    state: com.vaultix.app.ui.viewmodel.SecurityHealthState,
    onClick: () -> Unit
) {
    val scoreColor = when {
        state.score >= 80 -> VaultSuccess
        state.score >= 50 -> VaultWarning
        else -> VaultError
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = state.score / 100f,
                        modifier = Modifier.fillMaxSize(),
                        color = scoreColor,
                        strokeWidth = 4.dp,
                        trackColor = scoreColor.copy(alpha = 0.1f)
                    )
                    Text("${state.score}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column {
                    Text(stringResource(R.string.security_health), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        when {
                            state.score >= 80 -> stringResource(R.string.security_well_protected)
                            state.score >= 50 -> stringResource(R.string.security_improvements_needed)
                            else -> stringResource(R.string.security_risk_detected)
                        },
                        fontSize = 12.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.weakPasswordsCount > 0 || state.expiredItemsCount > 0) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                
                if (state.weakPasswordsCount > 0) {
                    HealthAlertItem(Icons.Default.Warning, "${state.weakPasswordsCount} weak passwords", VaultWarning)
                }
                if (state.expiredItemsCount > 0) {
                    HealthAlertItem(Icons.Default.ErrorOutline, "${state.expiredItemsCount} items expired", VaultError)
                }
            }
        }
    }
}

@Composable
private fun HealthAlertItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = VaultTextSecondary)
    }
}

@Composable
private fun FavoriteQuickCard(item: FavoriteDisplayItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(item.color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = VaultTextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                item.categoryName,
                fontSize = 11.sp,
                color = VaultTextSecondary
            )
        }
    }
}

@Composable
fun PasswordTimelineSection(
    passwords: List<com.vaultix.app.data.model.Password>,
    isPremium: Boolean,
    onUpgrade: () -> Unit,
    onNavigateToDetail: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Password Timeline",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = VaultTextPrimary
            )
            Spacer(Modifier.width(8.dp))
            if (!isPremium) {
                Surface(color = VaultOrange, shape = RoundedCornerShape(4.dp)) {
                    Text("PRO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VaultSurface)
        ) {
            Box {
                Column(modifier = Modifier.padding(16.dp)) {
                    passwords.forEachIndexed { index, password ->
                        TimelineItem(
                            password = password,
                            isLast = index == passwords.size - 1,
                            onClick = { if (isPremium) onNavigateToDetail(password.id, "passwords") else onUpgrade() }
                        )
                    }
                }

                if (!isPremium) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = VaultBlack.copy(alpha = 0.6f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = VaultOrange)
                            Spacer(Modifier.height(8.dp))
                            Text("Upgrade to Pro to see timeline", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = onUpgrade) {
                                Text("GET PRO", color = VaultOrange, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineItem(password: com.vaultix.app.data.model.Password, isLast: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(VaultOrange, androidx.compose.foundation.shape.CircleShape)
            )
            if (!isLast) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .background(VaultOrange.copy(alpha = 0.3f))
                )
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(password.title, fontWeight = FontWeight.Bold, color = VaultTextPrimary, fontSize = 15.sp)
            Text(
                java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault()).format(java.util.Date(password.createdAt)),
                fontSize = 12.sp,
                color = VaultTextSecondary
            )
        }
        
        Icon(Icons.Default.ChevronRight, null, tint = VaultTextDisabled, modifier = Modifier.size(20.dp))
    }
}

data class FavoriteDisplayItem(
    val id: String,
    val title: String,
    val categoryName: String,
    val icon: ImageVector,
    val color: Color,
    val type: String,
    val updatedAt: Long
)
