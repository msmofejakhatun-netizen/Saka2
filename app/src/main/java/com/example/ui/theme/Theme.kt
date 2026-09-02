package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = VyaparRed,
    onPrimary = Color.White,
    primaryContainer = VyaparWarningLight,
    onPrimaryContainer = VyaparRedDark,
    secondary = VyaparDeepBlue,
    onSecondary = Color.White,
    secondaryContainer = VyaparLightBlue,
    onSecondaryContainer = VyaparDeepBlue,
    tertiary = VyaparSuccess,
    onTertiary = Color.White,
    background = VyaparBg,
    onBackground = VyaparTextPrimary,
    surface = VyaparSurface,
    onSurface = VyaparTextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = VyaparTextSecondary,
    outline = VyaparBorder,
    outlineVariant = VyaparDivider,
    error = VyaparRed,
    onError = Color.White
)

// Vyapar Red/White Theme Color Constants
val VyaparPrimary = VyaparRed
val VyaparSecondary = VyaparDeepBlue

private val DarkColorScheme = LightColorScheme

@Composable
fun SmartPOSTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Clean Business Red & White Theme by default
    content: @Composable () -> Unit
) {
    SmartPOSTheme(darkTheme = darkTheme, content = content)
}

