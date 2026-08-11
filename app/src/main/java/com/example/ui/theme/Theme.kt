package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NavyDark = Color(0xFF090D16)
val NavySurface = Color(0xFF111827)
val NavyCard = Color(0xFF1E293B)
val GoldPrimary = Color(0xFFFFD700)
val GoldAccent = Color(0xFFF59E0B)
val CyanNeon = Color(0xFF06B6D4)
val BlueNeon = Color(0xFF3B82F6)
val SuccessGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val WarningOrange = Color(0xFFF97316)
val TextWhite = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = NavyDark,
    primaryContainer = GoldAccent,
    onPrimaryContainer = TextWhite,
    secondary = CyanNeon,
    onSecondary = NavyDark,
    background = NavyDark,
    onBackground = TextWhite,
    surface = NavySurface,
    onSurface = TextWhite,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextMuted,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun MEDUSAALFHATheme(
    darkTheme: Boolean = true, // Force dark luxury tech theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
