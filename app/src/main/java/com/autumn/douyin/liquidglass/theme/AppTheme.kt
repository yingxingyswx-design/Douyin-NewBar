package com.autumn.douyin.liquidglass.theme

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

private val Ink = Color(0xFF0A0A0C)
private val Paper = Color(0xFFF7F7F8)
private val Panel = Color(0xFF161619)
private val PanelHigh = Color(0xFF202024)

private val DarkScheme = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2C2C31),
    onPrimaryContainer = Paper,
    secondary = Color(0xFFB9B9C0),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF26262B),
    onSecondaryContainer = Paper,
    tertiary = Color(0xFFFFC857),
    onTertiary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Panel,
    onSurfaceVariant = Color(0xFFA8A8B0),
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = Color(0xFF2A2A2F),
    outline = Color(0xFF45454C),
    outlineVariant = Color(0xFF2B2B30),
    errorContainer = Color(0xFF3A1518),
    onErrorContainer = Color(0xFFFFB4AB),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF111114),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E2E7),
    onPrimaryContainer = Color(0xFF111114),
    secondary = Color(0xFF56565E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8ED),
    onSecondaryContainer = Color(0xFF1A1A1E),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF0B0B0D),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF0B0B0D),
    surfaceVariant = Color(0xFFEDEDF0),
    onSurfaceVariant = Color(0xFF5D5D66),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0F3),
    surfaceContainerHighest = Color(0xFFE4E4E9),
    outline = Color(0xFFB9B9C0),
    outlineVariant = Color(0xFFDDDDE2),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val ExpressiveTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun M3BarSettingsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ExpressiveTypography,
        content = content,
    )
}
