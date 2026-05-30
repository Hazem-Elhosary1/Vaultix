package com.vaultix.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system fonts as fallback (Inter-like)
val VaultixFontFamily = FontFamily.Default

val VaultixTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        color = VaultTextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        color = VaultTextPrimary
    ),
    displaySmall = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        color = VaultTextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = VaultTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = VaultTextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = VaultTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = VaultTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = VaultTextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = VaultTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = VaultTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = VaultTextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = VaultTextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = VaultTextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = VaultTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = VaultixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = VaultTextSecondary
    )
)
