package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Vyapar Brand Red & White Color System
val VyaparRed = Color(0xFFD32F2F)
val VyaparRedDark = Color(0xFFB71C1C)
val VyaparSecondary = Color(0xFF1A237E)
val VyaparBg = Color(0xFFF5F7FB)
val VyaparSurface = Color(0xFFFFFFFF)
val VyaparTextPrimary = Color(0xFF1E293B)
val VyaparTextSecondary = Color(0xFF64748B)
val VyaparBorder = Color(0xFFE2E8F0)
val VyaparSuccess = Color(0xFF2E7D32)
val VyaparWarning = Color(0xFFED6C02)
val VyaparLightRed = Color(0xFFFEE2E2)
val VyaparLightGreen = Color(0xFFE8F5E9)

private val LightColorScheme = lightColorScheme(
    primary = VyaparRed,
    onPrimary = Color.White,
    primaryContainer = VyaparLightRed,
    onPrimaryContainer = VyaparRedDark,
    secondary = VyaparSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAF6),
    onSecondaryContainer = VyaparSecondary,
    tertiary = VyaparSuccess,
    onTertiary = Color.White,
    background = VyaparBg,
    onBackground = VyaparTextPrimary,
    surface = VyaparSurface,
    onSurface = VyaparTextPrimary,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = VyaparTextSecondary,
    outline = VyaparBorder,
    outlineVariant = Color(0xFFCBD5E1),
    error = VyaparRed,
    onError = Color.White
)

val SmartPOSTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)

val SmartPOSShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp)
)

@Composable
fun SmartPOSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = SmartPOSTypography,
        shapes = SmartPOSShapes,
        content = content
    )
}
