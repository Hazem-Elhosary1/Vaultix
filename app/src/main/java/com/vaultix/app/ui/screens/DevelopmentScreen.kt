package com.vaultix.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultix.app.util.DataSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DevelopmentViewModel @Inject constructor(
    private val dataSeeder: DataSeeder
) : ViewModel() {
    
    var isLoading by mutableStateOf(false)
    var activeAction by mutableStateOf("")
    var statusMessage by mutableStateOf("")

    fun seedAll() {
        execute("seed_all") { dataSeeder.seedAll(); "All data seeded successfully!" }
    }

    fun seedPasswords() {
        execute("seed_passwords") { dataSeeder.seedPasswords(); "Passwords seeded successfully!" }
    }

    fun seedCards() {
        execute("seed_cards") { dataSeeder.seedCards(); "Cards seeded successfully!" }
    }

    fun seedNotes() {
        execute("seed_notes") { dataSeeder.seedNotes(); "Notes seeded successfully!" }
    }

    fun seedIdentities() {
        execute("seed_identities") { dataSeeder.seedIdentities(); "Identities seeded successfully!" }
    }

    fun clearAll() {
        execute("clear_all") { dataSeeder.clearAll(); "All data cleared successfully!" }
    }

    private fun execute(actionName: String, action: suspend () -> String) {
        viewModelScope.launch {
            isLoading = true
            activeAction = actionName
            statusMessage = ""
            try {
                statusMessage = action()
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
                activeAction = ""
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopmentScreen(
    onBack: () -> Unit,
    viewModel: DevelopmentViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔧 Development Tools") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (viewModel.statusMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (viewModel.statusMessage.startsWith("Error")) 
                            MaterialTheme.colorScheme.errorContainer 
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = viewModel.statusMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text("Database Seeding", style = MaterialTheme.typography.titleMedium)
            
            DevActionRow(
                title = "Seed All Data",
                icon = Icons.Default.Dataset,
                isLoading = viewModel.activeAction == "seed_all",
                enabled = !viewModel.isLoading,
                onClick = { viewModel.seedAll() }
            )

            Divider()

            DevActionRow(
                title = "Seed Passwords",
                icon = Icons.Default.Password,
                isLoading = viewModel.activeAction == "seed_passwords",
                enabled = !viewModel.isLoading,
                onClick = { viewModel.seedPasswords() }
            )

            DevActionRow(
                title = "Seed Cards",
                icon = Icons.Default.CreditCard,
                isLoading = viewModel.activeAction == "seed_cards",
                enabled = !viewModel.isLoading,
                onClick = { viewModel.seedCards() }
            )

            DevActionRow(
                title = "Seed Notes",
                icon = Icons.Default.Notes,
                isLoading = viewModel.activeAction == "seed_notes",
                enabled = !viewModel.isLoading,
                onClick = { viewModel.seedNotes() }
            )

            DevActionRow(
                title = "Seed Identities",
                icon = Icons.Default.Badge,
                isLoading = viewModel.activeAction == "seed_identities",
                enabled = !viewModel.isLoading,
                onClick = { viewModel.seedIdentities() }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Dangerous Actions", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = { viewModel.clearAll() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (viewModel.activeAction == "clear_all") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Clear All Data")
            }
        }
    }
}

@Composable
fun DevActionRow(
    title: String,
    icon: ImageVector,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        contentPadding = PaddingValues(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f))
        if (!isLoading) {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}
