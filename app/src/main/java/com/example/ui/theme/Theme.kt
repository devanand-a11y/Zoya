package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ZoyaColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DarkObsidian,
    secondary = NeonMagenta,
    onSecondary = DarkObsidian,
    tertiary = ElectricViolet,
    background = DarkBackground,
    surface = DarkSurfaceCard,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceGlass,
    onSurfaceVariant = TextSecondary
)

@Composable
fun ZoyaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ZoyaColorScheme,
        typography = Typography,
        content = content
    )
}
