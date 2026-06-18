package com.example.birdfinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.birdfinder.settings.ThemeMode

// Blue "field guide" palette, drawn from the app's mountain/sky backdrop and logo.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6BA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE3F8),
    onPrimaryContainer = Color(0xFF0A2A47),
    secondary = Color(0xFF4A7FBF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E7FA),
    onSecondaryContainer = Color(0xFF0E2C49),
    tertiary = Color(0xFF2A8C9E),
    onTertiary = Color.White,
    background = Color(0xFFF1F5FB),
    onBackground = Color(0xFF121A24),
    surface = Color(0xFFF1F5FB),
    onSurface = Color(0xFF121A24),
    surfaceVariant = Color(0xFFD7E1EE),
    onSurfaceVariant = Color(0xFF424A56),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FBEF1),
    onPrimary = Color(0xFF0A2A47),
    primaryContainer = Color(0xFF1C4369),
    onPrimaryContainer = Color(0xFFCFE3F8),
    secondary = Color(0xFFA9C9EE),
    onSecondary = Color(0xFF11304E),
    secondaryContainer = Color(0xFF2A4866),
    onSecondaryContainer = Color(0xFFD8E7FA),
    tertiary = Color(0xFF80D0DE),
    onTertiary = Color(0xFF00363F),
    background = Color(0xFF0E1620),
    onBackground = Color(0xFFE1E8F1),
    surface = Color(0xFF0E1620),
    onSurface = Color(0xFFE1E8F1),
    surfaceVariant = Color(0xFF3A434F),
    onSurfaceVariant = Color(0xFFC1CAD7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * App theme. Uses a fixed branded blue palette (not wallpaper-derived dynamic color) so the
 * app reads as a dedicated field guide regardless of device.
 */
@Composable
fun BirdFinderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
