package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Color Schemes
private val SkyLightScheme = lightColorScheme(
    primary = SkyPrimary,
    secondary = SkySecondary,
    tertiary = SkyTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

private val EmeraldLightScheme = lightColorScheme(
    primary = EmeraldPrimary,
    secondary = EmeraldSecondary,
    tertiary = EmeraldTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

private val AmberLightScheme = lightColorScheme(
    primary = AmberPrimary,
    secondary = AmberSecondary,
    tertiary = AmberTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

private val LavenderLightScheme = lightColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = LavenderTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

// Dark Color Schemes
private val SkyDarkScheme = darkColorScheme(
    primary = SkyPrimaryDark,
    secondary = SkySecondaryDark,
    tertiary = SkyTertiaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFECE6F0),
    onSurface = Color(0xFFECE6F0)
)

private val EmeraldDarkScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    secondary = EmeraldSecondaryDark,
    tertiary = EmeraldTertiaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFECE6F0),
    onSurface = Color(0xFFECE6F0)
)

private val AmberDarkScheme = darkColorScheme(
    primary = AmberPrimaryDark,
    secondary = AmberSecondaryDark,
    tertiary = AmberTertiaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFECE6F0),
    onSurface = Color(0xFFECE6F0)
)

private val LavenderDarkScheme = darkColorScheme(
    primary = LavenderPrimaryDark,
    secondary = LavenderSecondaryDark,
    tertiary = LavenderTertiaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFECE6F0),
    onSurface = Color(0xFFECE6F0)
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "auto",
    themeColor: String = "sky",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) {
        when (themeColor) {
            "emerald" -> EmeraldDarkScheme
            "amber" -> AmberDarkScheme
            "lavender" -> LavenderDarkScheme
            else -> SkyDarkScheme
        }
    } else {
        when (themeColor) {
            "emerald" -> EmeraldLightScheme
            "amber" -> AmberLightScheme
            "lavender" -> LavenderLightScheme
            else -> SkyLightScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
