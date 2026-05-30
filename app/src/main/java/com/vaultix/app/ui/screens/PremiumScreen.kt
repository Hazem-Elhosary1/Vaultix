package com.vaultix.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultix.app.R
import com.vaultix.app.ui.theme.*
import com.vaultix.app.ui.viewmodel.AppConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    viewModel: AppConfigViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.configState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.go_pro), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultBlack,
                    titleContentColor = VaultTextPrimary,
                    navigationIconContentColor = VaultTextPrimary
                )
            )
        },
        containerColor = VaultBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Illustration / Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(VaultOrange, Color(0xFFFFB74D))),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (state.isPremium) stringResource(R.string.pro_unlocked) else stringResource(R.string.premium_features),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = VaultTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Features List
            val features = listOf(
                PremiumFeature(Icons.Default.CloudUpload, stringResource(R.string.feature_unlimited)),
                PremiumFeature(Icons.Default.Security, stringResource(R.string.feature_fake_vault)),
                PremiumFeature(Icons.Default.CloudDone, stringResource(R.string.feature_cloud_backup)),
                PremiumFeature(Icons.Default.Palette, stringResource(R.string.feature_custom_themes))
            )

            features.forEach { feature ->
                FeatureItem(feature)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!state.isPremium) {
                Text(
                    "Select a Plan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = VaultTextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(Modifier.height(16.dp))

                var selectedPlanId by remember { mutableStateOf(state.availablePlans.getOrNull(1)?.id ?: "") }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.availablePlans.forEach { plan ->
                        val isSelected = selectedPlanId == plan.id
                        PlanItem(plan, isSelected) { selectedPlanId = plan.id }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = { viewModel.unlockPremium() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        stringResource(R.string.buy_pro_now),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Surface(
                    color = Color.Green.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.pro_unlocked), color = Color.Green, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun PlanItem(plan: com.vaultix.app.ui.viewmodel.PremiumPlan, isSelected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) VaultOrange.copy(alpha = 0.15f) else VaultSurface,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, VaultOrange) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = VaultOrange)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.name, fontWeight = FontWeight.Bold, color = VaultTextPrimary, fontSize = 16.sp)
                    if (plan.description.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = VaultOrange, shape = RoundedCornerShape(4.dp)) {
                            Text(plan.description, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(plan.period, color = VaultTextSecondary, fontSize = 12.sp)
            }
            Text(plan.price, fontWeight = FontWeight.ExtraBold, color = VaultOrange, fontSize = 18.sp)
        }
    }
}

data class PremiumFeature(val icon: ImageVector, val title: String)

@Composable
fun FeatureItem(feature: PremiumFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultSurface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(VaultOrange.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(feature.icon, contentDescription = null, tint = VaultOrange, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = feature.title,
            color = VaultTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
