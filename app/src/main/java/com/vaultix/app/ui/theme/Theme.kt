package com.vaultix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vaultix.app.ui.viewmodel.ThemeMode

private fun getDarkColorScheme(accentColor: Color) = darkColorScheme(
    primary = accentColor,
    onPrimary = Color.Black,
    primaryContainer = accentColor.copy(alpha = 0.3f),
    onPrimaryContainer = Color.White,

    secondary = VaultNavyLight,
    onSecondary = VaultTextPrimary,
    secondaryContainer = VaultNavy,
    onSecondaryContainer = VaultTextPrimary,

    tertiary = VaultInfo,
    onTertiary = Color.Black,

    background = VaultBlack,
    onBackground = VaultTextPrimary,

    surface = VaultSurface,
    onSurface = VaultTextPrimary,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = VaultTextSecondary,

    error = VaultError,
    onError = VaultTextPrimary,
    errorContainer = Color(0xFF4D0000),
    onErrorContainer = VaultError,

    outline = VaultBorder,
    outlineVariant = VaultDivider,

    inverseSurface = VaultTextPrimary,
    inverseOnSurface = Color.Black,
    inversePrimary = accentColor,

    scrim = Color(0xCC000000)
)

private fun getLightColorScheme(accentColor: Color) = lightColorScheme(
    primary = accentColor,
    onPrimary = Color.White,
    primaryContainer = accentColor.copy(alpha = 0.1f),
    onPrimaryContainer = accentColor,

    secondary = accentColor.copy(alpha = 0.7f),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F2F5),
    onSecondaryContainer = VaultLightTextPrimary,

    tertiary = VaultInfo,
    onTertiary = Color.White,

    background = VaultLightBackground,
    onBackground = VaultLightTextPrimary,

    surface = VaultLightSurface,
    onSurface = VaultLightTextPrimary,
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = VaultLightTextSecondary,

    error = VaultError,
    onError = Color.White,

    outline = Color(0xFFCFD8DC),
    outlineVariant = Color(0xFFEEEEEE),

    inverseSurface = VaultLightTextPrimary,
    inverseOnSurface = Color.White,
    inversePrimary = accentColor,

    scrim = Color(0x33000000)
)

@Composable
fun VaultixTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColorHex: String = "#FF9800",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val accentColor = try {
        Color(android.graphics.Color.parseColor(accentColorHex))
    } catch (e: Exception) {
        Color(0xFFFF9800) // Fallback to safe AccentOrange constant to prevent recursive property calling
    }

    // DynamicThemeAccent update propagates color changes to all hardcoded VaultOrange elements!
    DynamicThemeAccent = accentColor

    val colorScheme = if (darkTheme) {
        getDarkColorScheme(accentColor)
    } else {
        getLightColorScheme(accentColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaultixTypography,
        content = content
    )
}
