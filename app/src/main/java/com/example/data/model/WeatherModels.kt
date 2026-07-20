package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WeatherSource(val displayName: String, val requiresKey: Boolean, val defaultDailyLimit: Int) {
    OPEN_METEO("Open-Meteo", false, 10000),
    OPEN_WEATHER_MAP("OpenWeatherMap", true, 1000),
    WEATHER_API("WeatherAPI", true, 1000),
    TOMORROW_IO("Tomorrow.io", true, 500)
}

data class WeatherCondition(
    val temperature: Float,
    val feelsLike: Float,
    val humidity: Int, // %
    val windSpeed: Float, // km/h
    val windDirection: Float, // degrees
    val pressure: Float, // hPa
    val uvIndex: Float,
    val aqi: Int, // Air Quality Index (1-5 or US AQI)
    val precipitationProb: Int, // %
    val precipitationQty: Float, // mm
    val sunrise: String, // HH:MM
    val sunset: String, // HH:MM
    val conditionText: String,
    val conditionIcon: String // "sunny", "cloudy", "rainy", "snowy", "thunderstorm"
)

data class ForecastHour(
    val time: String, // "08:00", "09:00" etc
    val temp: Float,
    val conditionIcon: String,
    val precipitationProb: Int
)

data class ForecastDay(
    val date: String, // "Lundi", "Mardi" etc
    val minTemp: Float,
    val maxTemp: Float,
    val conditionIcon: String,
    val precipitationProb: Int,
    val conditionText: String
)

data class WeatherAlert(
    val title: String,
    val severity: String, // "Mild", "Moderate", "Severe", "Extreme"
    val description: String,
    val senderName: String
)

data class UnifiedWeather(
    val source: WeatherSource,
    val timestamp: Long,
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val current: WeatherCondition,
    val hourly: List<ForecastHour>,
    val daily: List<ForecastDay>,
    val alerts: List<WeatherAlert>
)

@Entity(tableName = "weather_cache")
data class CachedWeatherEntity(
    @PrimaryKey val cityId: String, // "latitude_longitude" or "cityName"
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val lastUpdated: Long,
    val isOffline: Boolean,
    // JSON representation of comparison results or separate fields
    val serializedComparison: String
)

@Entity(tableName = "favorite_cities")
data class FavoriteCity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

// Represents comparison results for UI display
data class ComparativeWeatherResult(
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isOffline: Boolean = false,
    val sourcesData: Map<WeatherSource, UnifiedWeather>, // Individual successful responses
    val sourceErrors: Map<WeatherSource, String>, // Unsuccessful responses with error messages
    val averageWeather: WeatherCondition,
    val averageHourly: List<ForecastHour>,
    val averageDaily: List<ForecastDay>,
    // Highlights deviations for visual alerts
    val sourceDeviations: Map<WeatherSource, WeatherDeviation>
)

data class WeatherDeviation(
    val temperatureDeviation: Float, // temp difference from average
    val isTemperatureUnreliable: Boolean, // diverges significantly (e.g. > 2°C)
    val pressureDeviation: Float,
    val isPressureUnreliable: Boolean,
    val windDeviation: Float,
    val isWindUnreliable: Boolean
)
