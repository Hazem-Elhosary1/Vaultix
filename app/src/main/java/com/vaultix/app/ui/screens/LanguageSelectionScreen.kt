package com.vaultix.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultix.app.ui.theme.*

@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit
) {
    var selectedLang by remember { mutableStateOf<String?>(null) }

    val logoScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(VaultNavy, VaultBlack)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Globe icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(VaultOrange.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = VaultOrange,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Choose Your Language",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = VaultTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "اختر لغتك",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = VaultTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // English Option
            LanguageCard(
                languageName = "English",
                nativeName = "English",
                flag = "🇬🇧",
                isSelected = selectedLang == "en",
                onClick = { selectedLang = "en" }
            )

            Spacer(Modifier.height(16.dp))

            // Arabic Option
            LanguageCard(
                languageName = "Arabic",
                nativeName = "العربية",
                flag = "🇪🇬",
                isSelected = selectedLang == "ar",
                onClick = { selectedLang = "ar" }
            )

            Spacer(Modifier.height(48.dp))

            AnimatedVisibility(
                visible = selectedLang != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                Button(
                    onClick = { selectedLang?.let { onLanguageSelected(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultOrange)
                ) {
                    Text(
                        if (selectedLang == "ar") "متابعة" else "Continue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(
    languageName: String,
    nativeName: String,
    flag: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) VaultOrange else VaultBorder,
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) VaultOrange.copy(alpha = 0.1f) else VaultSurface,
        label = "bgColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(flag, fontSize = 36.sp)

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    nativeName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) VaultOrange else VaultTextPrimary
                )
                if (nativeName != languageName) {
                    Text(
                        languageName,
                        fontSize = 14.sp,
                        color = VaultTextSecondary
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(VaultOrange, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
