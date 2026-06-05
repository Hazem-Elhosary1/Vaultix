package com.vaultix.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vaultix.app.R

// Font Size Scale for accessibility
enum class FontSizeScale(val multiplier: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f)
}

// Cairo Font Family — supports Arabic and Latin
val CairoFontFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold)
)

// Scalable Typography factory
fun createVaultixTypography(scale: FontSizeScale = FontSizeScale.MEDIUM): Typography {
    val m = scale.multiplier
    return Typography(
        displayLarge = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (57 * m).sp,
            lineHeight = (64 * m).sp
        ),
        displayMedium = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (45 * m).sp,
            lineHeight = (52 * m).sp
        ),
        displaySmall = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (36 * m).sp,
            lineHeight = (44 * m).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (32 * m).sp,
            lineHeight = (40 * m).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (28 * m).sp,
            lineHeight = (36 * m).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (24 * m).sp,
            lineHeight = (32 * m).sp
        ),
        titleLarge = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (22 * m).sp,
            lineHeight = (28 * m).sp
        ),
        titleMedium = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (16 * m).sp,
            lineHeight = (24 * m).sp
        ),
        titleSmall = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * m).sp,
            lineHeight = (20 * m).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * m).sp,
            lineHeight = (24 * m).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * m).sp,
            lineHeight = (20 * m).sp
        ),
        bodySmall = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * m).sp,
            lineHeight = (16 * m).sp
        ),
        labelLarge = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * m).sp,
            lineHeight = (20 * m).sp
        ),
        labelMedium = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * m).sp,
            lineHeight = (16 * m).sp
        ),
        labelSmall = TextStyle(
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * m).sp,
            lineHeight = (16 * m).sp
        )
    )
}

// Backward compatibility — default typography for places that reference VaultixTypography directly
val VaultixTypography = createVaultixTypography(FontSizeScale.MEDIUM)
