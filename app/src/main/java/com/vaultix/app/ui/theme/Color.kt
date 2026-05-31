package com.vaultix.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Global Dynamic Theme Accent Bridge
var DynamicThemeAccent by mutableStateOf(Color(0xFFFF7A00))

// Primary Brand Colors (linked dynamically to current accent)
val VaultNavy = Color(0xFF0A1F44)
val VaultNavyLight = Color(0xFF1A3460)
val VaultNavyDark = Color(0xFF060F22)
val VaultBlack = Color(0xFF000000)

val VaultOrange: Color
    get() = DynamicThemeAccent

val VaultOrangeLight: Color
    get() = DynamicThemeAccent.copy(alpha = 0.8f)

val VaultOrangeDark: Color
    get() = DynamicThemeAccent

// Surface Colors
val VaultSurface = Color(0xFF0D1B35)
val VaultSurfaceVariant = Color(0xFF142448)
val VaultCard = Color(0xFF111D3A)
val VaultCardElevated = Color(0xFF1A2D4E)

// Text Colors
val VaultTextPrimary = Color(0xFFFFFFFF)
val VaultTextSecondary = Color(0xFFB0BEC5)
val VaultTextDisabled = Color(0xFF546E7A)
val VaultTextHint = Color(0xFF607D8B)

// Status Colors
val VaultSuccess = Color(0xFF00E676)
val VaultWarning = Color(0xFFFFAB40)
val VaultError = Color(0xFFFF5252)
val VaultInfo = Color(0xFF40C4FF)

// Strength Colors
val StrengthVeryWeak = Color(0xFFFF1744)
val StrengthWeak = Color(0xFFFF6D00)
val StrengthFair = Color(0xFFFFD600)
val StrengthStrong = Color(0xFF69F0AE)
val StrengthVeryStrong = Color(0xFF00E676)

// Category Colors
val CategoryPasswords = Color(0xFF5C6BC0)
val CategoryCards = Color(0xFF26A69A)
val CategoryNotes = Color(0xFFEF9A9A)
val CategoryFiles = Color(0xFFFFCA28)
val CategoryIDs = Color(0xFF66BB6A)
val CategoryWifi = Color(0xFF0288D1)

// Divider / Border
val VaultDivider = Color(0xFF1E3054)
val VaultBorder = Color(0xFF1E3054)

// Light Mode Surface Colors
val VaultLightBackground = Color(0xFFF5F7FA)
val VaultLightSurface = Color(0xFFFFFFFF)
val VaultLightCard = Color(0xFFFFFFFF)
val VaultLightBorder = Color(0xFFE1E8F0)
val VaultLightDivider = Color(0xFFECEFF1)

// Light Mode Text
val VaultLightTextPrimary = Color(0xFF1A2133)
val VaultLightTextSecondary = Color(0xFF546E7A)
val VaultLightTextDisabled = Color(0xFF90A4AE)

// Dynamic Accent Options
val AccentOrange = Color(0xFFFF9800)
val AccentBlue = Color(0xFF2196F3)
val AccentGreen = Color(0xFF4CAF50)
val AccentPurple = Color(0xFF9C27B0)
val AccentRed = Color(0xFFF44336)
val AccentTeal = Color(0xFF009688)
val AccentPink = Color(0xFFE91E63)
val AccentIndigo = Color(0xFF3F51B5)
val AccentAmber = Color(0xFFFFC107)
val AccentCyan = Color(0xFF00BCD4)

val AccentOptions = mapOf(
    "Orange" to AccentOrange,
    "Blue" to AccentBlue,
    "Green" to AccentGreen,
    "Purple" to AccentPurple,
    "Red" to AccentRed,
    "Teal" to AccentTeal,
    "Pink" to AccentPink,
    "Indigo" to AccentIndigo,
    "Amber" to AccentAmber,
    "Cyan" to AccentCyan
)

// Gradient combinations
val GradientNavyOrange = listOf(VaultNavy, VaultOrangeDark)
val GradientDarkNavy = listOf(VaultBlack, VaultNavy)

