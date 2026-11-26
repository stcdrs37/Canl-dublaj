package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AeroLightColorScheme = lightColorScheme(
    primary = StudioLightPrimary,
    onPrimary = Color.White,
    primaryContainer = StudioLightSurfaceHighlight,
    onPrimaryContainer = Color(0xFF11267A),
    secondary = StudioLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F7F2),
    onSecondaryContainer = Color(0xFF004C45),
    tertiary = StudioLightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECE6FF),
    onTertiaryContainer = Color(0xFF33158E),
    background = StudioLightBg,
    onBackground = StudioLightInkPrimary,
    surface = StudioLightSurface,
    onSurface = StudioLightInkPrimary,
    surfaceVariant = StudioLightSurfaceSoft,
    onSurfaceVariant = StudioLightInkSecondary,
    surfaceContainerLow = StudioLightBgMid,
    surfaceContainer = StudioLightSurfaceSoft,
    surfaceContainerHigh = StudioLightSurfaceHighlight,
    outline = StudioLightOutline,
    outlineVariant = StudioLightOutline,
    error = CrimsonRed,
    onError = Color.White
)

private val AeroDarkColorScheme = darkColorScheme(
    primary = StudioDarkPrimary,
    onPrimary = Color(0xFF091434),
    primaryContainer = StudioDarkSurfaceHighlight,
    onPrimaryContainer = Color(0xFFDEE7FF),
    secondary = StudioDarkSecondary,
    onSecondary = Color(0xFF002924),
    secondaryContainer = Color(0xFF103A37),
    onSecondaryContainer = Color(0xFFB5FAF0),
    tertiary = StudioDarkTertiary,
    onTertiary = Color(0xFF1E0B4A),
    tertiaryContainer = Color(0xFF2D205A),
    onTertiaryContainer = Color(0xFFE9DEFF),
    background = StudioDarkBg,
    onBackground = StudioDarkInkPrimary,
    surface = StudioDarkSurface,
    onSurface = StudioDarkInkPrimary,
    surfaceVariant = StudioDarkSurfaceSoft,
    onSurfaceVariant = StudioDarkInkSecondary,
    surfaceContainerLow = StudioDarkSurface,
    surfaceContainer = StudioDarkSurfaceElevated,
    surfaceContainerHigh = StudioDarkSurfaceHighlight,
    outline = StudioDarkOutline,
    outlineVariant = StudioDarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF3B0909)
)

@Composable
fun LiveDubTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AeroDarkColorScheme else AeroLightColorScheme,
        typography = Typography,
        content = content
    )
}
