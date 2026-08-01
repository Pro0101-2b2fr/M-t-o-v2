package com.example.ui.screens.dashboard.utils

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.manager.AppSettings
import com.example.ui.theme.*
import com.example.ui.viewmodel.WeatherUiState

fun formatTemperature(temp: Float, unit: String): String {
    return if (unit == "F") {
        "${"%.0f".format((temp * 9 / 5) + 32)}°F"
    } else {
        "${"%.0f".format(temp)}°C"
    }
}

fun formatWindSpeed(speed: Float, unit: String): String {
    return when (unit) {
        "mph" -> "${"%.1f".format(speed * 0.621371f)} mph"
        "knots" -> "${"%.1f".format(speed * 0.539957f)} kt"
        else -> "${"%.0f".format(speed)} km/h"
    }
}

fun formatPressure(pressure: Float, unit: String): String {
    return if (unit == "mmhg") {
        "${"%.0f".format(pressure * 0.750062f)} mmHg"
    } else {
        "${"%.0f".format(pressure)} hPa"
    }
}

fun getWeatherIconVector(iconName: String): ImageVector {
    return when (iconName) {
        "sunny" -> Icons.Default.WbSunny
        "cloudy" -> Icons.Default.Cloud
        "rainy" -> Icons.Default.WaterDrop
        "snowy" -> Icons.Default.AcUnit
        "thunderstorm" -> Icons.Default.FlashOn
        else -> Icons.Default.Cloud
    }
}

fun getWeatherIconColor(iconName: String): Color {
    return when (iconName) {
        "sunny" -> WeatherSunnyColor
        "cloudy" -> WeatherCloudyColor
        "rainy" -> WeatherRainyColor
        "snowy" -> WeatherSnowyColor
        "thunderstorm" -> WeatherThunderstormColor
        else -> WeatherCloudyColor
    }
}

@Composable
fun getBackgroundGradient(uiState: WeatherUiState, settings: AppSettings): Brush {
    val darkTheme = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val (color1, color2) = if (darkTheme) {
        val deepNightBlue = Color(0xFF090D1A)
        val spaceBlack = Color(0xFF03050A)
        val darkStormBlue = Color(0xFF0E1422)
        val darkAbyss = Color(0xFF05080E)
        val twilightAmber = Color(0xFF1D130A)
        val shadowDusk = Color(0xFF0A0704)

        when (uiState) {
            is WeatherUiState.Success -> {
                when (uiState.data.averageWeather.conditionIcon) {
                    "sunny" -> twilightAmber to shadowDusk
                    "rainy", "thunderstorm" -> darkStormBlue to darkAbyss
                    else -> deepNightBlue to spaceBlack
                }
            }
            else -> deepNightBlue to spaceBlack
        }
    } else {
        val lightBlue = Color(0xFFD9F4F6)
        val darkBlue = Color(0xFF70CFDC)
        val graySky = Color(0xFFE5E9EC)
        val stormSky = Color(0xFFB0BEC5)
        val sunsetAmber = Color(0xFFFFF1D6)
        val orangeSky = Color(0xFFFFCC80)

        when (uiState) {
            is WeatherUiState.Success -> {
                when (uiState.data.averageWeather.conditionIcon) {
                    "sunny" -> sunsetAmber to orangeSky
                    "rainy", "thunderstorm" -> stormSky to graySky
                    else -> lightBlue to darkBlue
                }
            }
            else -> lightBlue to darkBlue
        }
    }

    return Brush.verticalGradient(
        colors = listOf(
            animateColorAsState(targetValue = color1).value,
            animateColorAsState(targetValue = color2).value
        )
    )
}
