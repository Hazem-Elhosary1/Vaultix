package com.vaultix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.SecurityAuditViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    onBack: () -> Unit,
    onNavigateToPremium: () -> Unit,
    viewModel: SecurityAuditViewModel = hiltViewModel(),
    appConfigViewModel: com.vaultix.app.ui.viewmodel.AppConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val configState by appConfigViewModel.configState.collectAsState()

    Scaffold(
        containerColor = VaultBlack,
        topBar = {
            TopAppBar(
                title = { Text("Security Audit", fontWeight = FontWeight.Bold, color = VaultTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = VaultTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultBlack)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Overall Score Card
            ScoreCard(uiState.passwordReport.overallScore)

            // Password Issues
            SectionHeader("Password Health")
            
            var showDuplicateDialog by remember { mutableStateOf(false) }
            
            AuditItem(
                title = "Weak Passwords",
                count = uiState.passwordReport.weakCount,
                icon = Icons.Default.Warning,
                color = VaultError,
                description = "Passwords that are easy to guess."
            )
            AuditItem(
                title = "Reused Passwords",
                count = uiState.passwordReport.duplicateCount,
                icon = Icons.Default.CopyAll,
                color = VaultOrange,
                description = "Same password used for multiple accounts.",
                onClick = { showDuplicateDialog = true }
            )
            
            if (showDuplicateDialog) {
                DuplicateDetailsDialog(
                    duplicates = uiState.passwordReport.duplicateItems,
                    onDismiss = { showDuplicateDialog = false }
                )
            }

            AuditItem(
                title = "Old Passwords",
                count = uiState.passwordReport.oldCount,
                icon = Icons.Default.History,
                color = VaultTextSecondary,
                description = "Passwords not changed in over 90 days."
            )

            // Expiry Issues
            SectionHeader("Document Health")
            AuditItem(
                title = "Expiring Cards",
                count = uiState.expiringCardsCount,
                icon = Icons.Default.CreditCard,
                color = VaultError,
                description = "Cards expiring within 30 days."
            )
            AuditItem(
                title = "Expiring Identities",
                count = uiState.expiringIdentitiesCount,
                icon = Icons.Default.Badge,
                color = VaultOrange,
                description = "IDs/Passports near expiration."
            )

            // Activity Logs
            SectionHeader("Security Activity Logs")
            if (!configState.isPremium) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPremium() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Lock, null, tint = VaultOrange, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Detailed logs require Pro", fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                        Text("Upgrade to see every security event.", fontSize = 12.sp, color = VaultTextSecondary)
                        Spacer(Modifier.height(16.dp))
                        Text("UPGRADE NOW", color = VaultOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            } else {
                val logs by viewModel.logs.collectAsState()
                if (logs.isEmpty()) {
                    Text("No recent security events", color = VaultTextSecondary, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = VaultSurface)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            logs.forEach { log ->
                                SecurityLogItem(log)
                                if (log != logs.last()) {
                                    HorizontalDivider(color = VaultDivider, modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityLogItem(log: com.vaultix.app.data.repository.SecurityLog) {
    val color = when (log.severity) {
        "CRITICAL" -> VaultError
        "WARNING" -> VaultOrange
        else -> VaultInfo
    }
    
    val icon = when (log.eventType) {
        "AUTH_SUCCESS" -> Icons.Default.LockOpen
        "AUTH_FAILED" -> Icons.Default.Lock
        "PANIC_TRIGGERED" -> Icons.Default.DeleteForever
        "BACKUP_EXPORT" -> Icons.Default.Upload
        "BACKUP_IMPORT" -> Icons.Default.Download
        else -> Icons.Default.Info
    }

    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(log.eventType.replace("_", " "), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)),
                    fontSize = 11.sp,
                    color = VaultTextSecondary
                )
            }
            Text(log.details, fontSize = 12.sp, color = VaultTextSecondary)
        }
    }
}

@Composable
fun ScoreCard(score: Int) {
    val color = when {
        score >= 80 -> VaultSuccess
        score >= 50 -> VaultOrange
        else -> VaultError
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Overall Security Score", color = VaultTextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = score / 100f,
                    modifier = Modifier.size(100.dp),
                    color = color,
                    strokeWidth = 8.dp,
                    trackColor = color.copy(0.1f)
                )
                Text(
                    text = "$score%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (score >= 80) "Looking Good!" else "Improvements Needed",
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AuditItem(title: String, count: Int, icon: ImageVector, color: Color, description: String, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = count > 0) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VaultSurface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = VaultTextPrimary)
                Text(description, fontSize = 12.sp, color = VaultTextSecondary)
            }
            if (count > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = color,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = VaultTextSecondary, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Default.CheckCircle, null, tint = VaultSuccess)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = VaultOrange,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}
@Composable
fun DuplicateDetailsDialog(duplicates: List<com.vaultix.app.data.model.Password>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        title = { Text("Reused Passwords", color = VaultOrange, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("The following accounts share the same password. Consider using unique passwords for each.", 
                    fontSize = 12.sp, color = VaultTextSecondary, modifier = Modifier.padding(bottom = 16.dp))
                
                // Group by password content to show them together
                val groups = duplicates.groupBy { it.password.concatToString() }
                groups.forEach { (pwd, items) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = VaultBlack.copy(0.3f))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, null, tint = VaultOrange, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VaultTextPrimary)
                                        Text(item.username, fontSize = 11.sp, color = VaultTextSecondary)
                                    }
                                }
                                if (item != items.last()) Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = VaultOrange) }
        }
    )
}
