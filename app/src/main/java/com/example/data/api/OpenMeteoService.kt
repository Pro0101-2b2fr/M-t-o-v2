package com.example.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {

    @GET("forecast")
    suspend fun getWeatherForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,uv_index",
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,weather_code,precipitation_probability",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    @GET("https://air-quality-api.open-meteo.com/v1/forecast")
    suspend fun getAirQuality(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "european_aqi"
    ): OpenMeteoAqiResponse
}

// Open-Meteo Weather Response DTOs
data class OpenMeteoResponse(
    val latitude: Double,
    val longitude: Double,
    val current: CurrentWeatherDto?,
    val hourly: HourlyForecastDto?,
    val daily: DailyForecastDto?
)

data class CurrentWeatherDto(
    val time: String,
    val temperature_2m: Float,
    val relative_humidity_2m: Int,
    val apparent_temperature: Float,
    val precipitation: Float,
    val weather_code: Int,
    val pressure_msl: Float,
    val wind_speed_10m: Float,
    val wind_direction_10m: Float,
    val uv_index: Float
)

data class HourlyForecastDto(
    val time: List<String>,
    val temperature_2m: List<Float>,
    val relative_humidity_2m: List<Int>,
    val weather_code: List<Int>,
    val precipitation_probability: List<Int>
)

data class DailyForecastDto(
    val time: List<String>,
    val weather_code: List<Int>,
    val temperature_2m_max: List<Float>,
    val temperature_2m_min: List<Float>,
    val sunrise: List<String>,
    val sunset: List<String>,
    val uv_index_max: List<Float>,
    val precipitation_probability_max: List<Int>
)

// Open-Meteo Air Quality Response DTOs
data class OpenMeteoAqiResponse(
    val current: CurrentAqiDto?
)

data class CurrentAqiDto(
    val time: String,
    val european_aqi: Int
)
