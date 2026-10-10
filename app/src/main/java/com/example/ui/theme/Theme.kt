package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TravelNavyPrimary,
    onPrimary = Color.White,
    primaryContainer = TravelNavyContainer,
    onPrimaryContainer = TravelNavyDark,
    secondary = TravelTerracotta,
    onSecondary = Color.White,
    secondaryContainer = TravelAmberLight,
    onSecondaryContainer = TravelNavyDark,
    background = TravelSandBackground,
    onBackground = TextPrimary,
    surface = TravelSurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = TravelSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TravelBorderSubtle
)

private val LightColorScheme = lightColorScheme(
    primary = TravelNavyPrimary,
    onPrimary = Color.White,
    primaryContainer = TravelNavyContainer,
    onPrimaryContainer = TravelNavyDark,
    secondary = TravelTerracotta,
    onSecondary = Color.White,
    secondaryContainer = TravelAmberLight,
    onSecondaryContainer = TravelNavyDark,
    tertiary = TravelAmberAccent,
    onTertiary = Color.White,
    background = TravelSandBackground,
    onBackground = TextPrimary,
    surface = TravelSurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = TravelSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TravelBorderSubtle
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep bespoke editorial branding
    content: @Composable () -> Unit
) {
    // Keep the bespoke travel palette consistent with readable dark text
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
