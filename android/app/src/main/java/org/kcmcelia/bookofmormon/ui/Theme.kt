package org.kcmcelia.bookofmormon.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Gold = Color(0xFFC9A25A)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF20180A),
    background = Color(0xFF0C121E),
    onBackground = Color(0xFFEDE8DC),
    surface = Color(0xFF141C2B),
    onSurface = Color(0xFFEDE8DC),
    surfaceVariant = Color(0xFF1E2838),
    onSurfaceVariant = Color(0xFFC3CBDA),
    secondaryContainer = Color(0xFF23324A),
    onSecondaryContainer = Color(0xFFEDE8DC),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6A24),
    onPrimary = Color.White,
    background = Color(0xFFFDF8F0),
    onBackground = Color(0xFF1A2230),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2230),
    surfaceVariant = Color(0xFFEFE7D8),
    onSurfaceVariant = Color(0xFF4A5568),
    secondaryContainer = Color(0xFFF0E4CC),
    onSecondaryContainer = Color(0xFF2B2416),
)

/**
 * Deliberately larger than Material defaults: base body text is 20sp and the smallest
 * style used in the app is 18sp, so the UI still reads for someone with reduced vision.
 * Sizes are in sp, so they also scale with the system font setting.
 */
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun BomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
