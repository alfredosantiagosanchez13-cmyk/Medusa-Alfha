package com.example.medusaalfha.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = TealAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF115E59),
    secondary = NavyPrimary,
    onSecondary = Color.White,
    background = GrayBackground,
    surface = GraySurface,
    onBackground = TextDark,
    onSurface = TextDark
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color.Black,
    secondary = NavyLight,
    onSecondary = Color.White,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MEDUSAALFHATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
