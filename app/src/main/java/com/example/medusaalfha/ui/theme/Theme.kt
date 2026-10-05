package com.example.medusaalfha.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MedusaDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = NavyDark,
    primaryContainer = NavyCard,
    onPrimaryContainer = GoldLight,
    secondary = CyanNeon,
    onSecondary = NavyDark,
    secondaryContainer = NavySurface,
    onSecondaryContainer = CyanNeon,
    tertiary = GoldLight,
    background = NavyDark,
    onBackground = TextWhite,
    surface = NavySurface,
    onSurface = TextWhite,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextMuted,
    outline = NavyBorder,
    outlineVariant = Color(0xFF243452),
    error = StatusDeniedRed,
    errorContainer = StatusDeniedContainer,
    onError = TextWhite,
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun MEDUSAALFHATheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MedusaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
