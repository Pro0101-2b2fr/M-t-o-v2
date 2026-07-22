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

    @GET("meteofrance")
    suspend fun getMeteoFranceForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,uv_index",
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,weather_code,precipitation_probability",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    @GET("dwd-icon")
    suspend fun getDwdIconForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,uv_index",
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,weather_code,precipitation_probability",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    @GET("gfs")
    suspend fun getGfsForecast(
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val current: CurrentWeatherDto? = null,
    val hourly: HourlyForecastDto? = null,
    val daily: DailyForecastDto? = null
)

data class CurrentWeatherDto(
    val time: String? = null,
    val temperature_2m: Float? = null,
    val relative_humidity_2m: Int? = null,
    val apparent_temperature: Float? = null,
    val precipitation: Float? = null,
    val weather_code: Int? = null,
    val pressure_msl: Float? = null,
    val wind_speed_10m: Float? = null,
    val wind_direction_10m: Float? = null,
    val uv_index: Float? = null
)

data class HourlyForecastDto(
    val time: List<String>? = null,
    val temperature_2m: List<Float>? = null,
    val relative_humidity_2m: List<Int>? = null,
    val weather_code: List<Int>? = null,
    val precipitation_probability: List<Int>? = null
)

data class DailyForecastDto(
    val time: List<String>? = null,
    val weather_code: List<Int>? = null,
    val temperature_2m_max: List<Float>? = null,
    val temperature_2m_min: List<Float>? = null,
    val sunrise: List<String>? = null,
    val sunset: List<String>? = null,
    val uv_index_max: List<Float>? = null,
    val precipitation_probability_max: List<Int>? = null
)

// Open-Meteo Air Quality Response DTOs
data class OpenMeteoAqiResponse(
    val current: CurrentAqiDto? = null
)

data class CurrentAqiDto(
    val time: String? = null,
    val european_aqi: Int? = null
)
