package com.vaultix.app.ui.screens

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
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.GlobalSearchViewModel

@Composable
fun GlobalSearchScreen(
    onNavigateToDetail: (String, String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: GlobalSearchViewModel = hiltViewModel()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val state by viewModel.searchState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = VaultTextPrimary)
                }
                TextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    placeholder = { Text("Search your vault...", color = VaultTextDisabled) },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = VaultSurface,
                        unfocusedContainerColor = VaultSurface,
                        focusedTextColor = VaultTextPrimary,
                        unfocusedTextColor = VaultTextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Close, null, tint = VaultTextSecondary)
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (query.length < 2) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Type at least 2 characters...", color = VaultTextSecondary)
            }
        } else if (state.passwordResults.isEmpty() && state.cardResults.isEmpty() && 
                   state.noteResults.isEmpty() && state.identityResults.isEmpty() &&
                   state.wifiResults.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No results found for '$query'", color = VaultTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Passwords
                if (state.passwordResults.isNotEmpty()) {
                    item { SearchSectionHeader("Passwords", CategoryPasswords) }
                    items(state.passwordResults) { password ->
                        SearchResultItem(password.title, password.username, Icons.Default.Key, CategoryPasswords) {
                            onNavigateToDetail(password.id, "passwords")
                        }
                    }
                }
                // Cards
                if (state.cardResults.isNotEmpty()) {
                    item { SearchSectionHeader("Cards", CategoryCards) }
                    items(state.cardResults) { card ->
                        SearchResultItem(card.cardName, card.maskedCardNumber, Icons.Default.CreditCard, CategoryCards) {
                            onNavigateToDetail(card.id, "cards")
                        }
                    }
                }
                // Notes
                if (state.noteResults.isNotEmpty()) {
                    item { SearchSectionHeader("Notes", CategoryNotes) }
                    items(state.noteResults) { note ->
                        SearchResultItem(note.title, "Note", Icons.Default.Note, CategoryNotes) {
                            onNavigateToDetail(note.id, "notes")
                        }
                    }
                }
                // Identities
                if (state.identityResults.isNotEmpty()) {
                    item { SearchSectionHeader("Identities", CategoryIDs) }
                    items(state.identityResults) { id ->
                        SearchResultItem(id.documentName, id.fullName, Icons.Default.Badge, CategoryIDs) {
                            onNavigateToDetail(id.id, "identities")
                        }
                    }
                }
                // Wi-Fi Networks
                if (state.wifiResults.isNotEmpty()) {
                    item { SearchSectionHeader("Wi-Fi Networks", VaultOrange) }
                    items(state.wifiResults) { wifi ->
                        SearchResultItem(wifi.title, "Wi-Fi Network", Icons.Default.Wifi, VaultOrange) {
                            onNavigateToDetail(wifi.id, "wifi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, color: Color) {
    Text(
        title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SearchResultItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(color.copy(0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VaultTextPrimary)
                Text(subtitle, fontSize = 12.sp, color = VaultTextSecondary)
            }
        }
    }
}
