package com.lingoreader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ForestPrimary = Color(0xFF2E5E4E)
val ForestPrimaryDark = Color(0xFF6BB39B)
val WarmAmber = Color(0xFFD9822B)
val WarmAmberSoft = Color(0xFFFFF3E0)
val PaperIvory = Color(0xFFFBF8F3)
val StoneSurface = Color(0xFFF3EEE6)
val InkDark = Color(0xFF1C1B18)
val InkMuted = Color(0xFF6B655B)

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E8E0),
    onPrimaryContainer = Color(0xFF0E3226),
    secondary = WarmAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5C7),
    onSecondaryContainer = Color(0xFF4A2800),
    tertiary = Color(0xFF546372),
    background = PaperIvory,
    onBackground = InkDark,
    surface = PaperIvory,
    onSurface = InkDark,
    surfaceVariant = StoneSurface,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFD8D0C3),
    outlineVariant = Color(0xFFE9E2D6)
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestPrimaryDark,
    onPrimary = Color(0xFF0B2E22),
    primaryContainer = Color(0xFF1F473A),
    onPrimaryContainer = Color(0xFFD5E8E0),
    secondary = Color(0xFFF2AA5E),
    onSecondary = Color(0xFF3E2200),
    secondaryContainer = Color(0xFF5A3408),
    onSecondaryContainer = Color(0xFFFFE5C7),
    background = Color(0xFF141618),
    onBackground = Color(0xFFE6E4DE),
    surface = Color(0xFF141618),
    onSurface = Color(0xFFE6E4DE),
    surfaceVariant = Color(0xFF212529),
    onSurfaceVariant = Color(0xFFA7A298),
    outline = Color(0xFF3D4248),
    outlineVariant = Color(0xFF2C3136)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun LingoReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
