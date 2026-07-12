package com.example.chineseforme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightParchmentScheme = lightColorScheme(
    primary = AccentSeal,
    onPrimary = Color.White,
    secondary = InkMuted,
    onSecondary = Color.White,
    tertiary = GroupBand,
    background = Parchment,
    onBackground = InkBrown,
    surface = TileFace,
    onSurface = InkBrown,
    surfaceVariant = ParchmentDeep,
    onSurfaceVariant = InkMuted,
    outline = TileEdge
)

private val DarkParchmentScheme = darkColorScheme(
    primary = Color(0xFFD4A090),
    onPrimary = Color(0xFF2A1810),
    secondary = Color(0xFFC4B0A0),
    onSecondary = Color(0xFF2A1810),
    background = Color(0xFF2A2118),
    onBackground = Color(0xFFF3E6C8),
    surface = Color(0xFF3A3024),
    onSurface = Color(0xFFF3E6C8),
    surfaceVariant = Color(0xFF4A3E30),
    onSurfaceVariant = Color(0xFFD4C4B0),
    outline = Color(0xFF8A7860)
)

@Composable
fun ChineseForMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkParchmentScheme else LightParchmentScheme,
        typography = Typography,
        content = content
    )
}
