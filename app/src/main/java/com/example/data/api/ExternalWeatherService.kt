package com.example.data.api

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface ExternalWeatherService {

    // OpenWeatherMap Current Conditions
    @GET
    suspend fun getOpenWeatherCurrent(
        @Url url: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "fr"
    ): OpenWeatherCurrentResponse

    // OpenWeatherMap 5-Day / 3-Hour Forecast
    @GET
    suspend fun getOpenWeatherForecast(
        @Url url: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "fr"
    ): OpenWeatherForecastResponse

    // WeatherAPI Forecast (includes current, hourly, daily, and alerts/aqi)
    @GET
    suspend fun getWeatherApiForecast(
        @Url url: String,
        @Query("key") apiKey: String,
        @Query("q") query: String, // "latitude,longitude"
        @Query("days") days: Int = 7,
        @Query("aqi") aqi: String = "yes",
        @Query("alerts") alerts: String = "yes",
        @Query("lang") lang: String = "fr"
    ): WeatherApiForecastResponse

    // Tomorrow.io Forecast (includes current, hourly, and daily)
    @GET
    suspend fun getTomorrowForecast(
        @Url url: String,
        @Query("location") location: String, // "latitude,longitude"
        @Query("apikey") apiKey: String,
        @Query("units") units: String = "metric"
    ): TomorrowForecastResponse
}

// ------------------- OpenWeatherMap DTOs -------------------
data class OpenWeatherCurrentResponse(
    val main: OwMain?,
    val wind: OwWind?,
    val weather: List<OwWeather>?,
    val sys: OwSys?,
    val dt: Long
)

data class OwMain(
    val temp: Float,
    val feels_like: Float,
    val humidity: Int,
    val pressure: Float
)

data class OwWind(
    val speed: Float,
    val deg: Float
)

data class OwWeather(
    val description: String,
    val icon: String
)

data class OwSys(
    val sunrise: Long,
    val sunset: Long
)

data class OpenWeatherForecastResponse(
    val list: List<OwForecastItem>?
)

data class OwForecastItem(
    val dt: Long,
    val main: OwMain?,
    val weather: List<OwWeather>?,
    val wind: OwWind?,
    val pop: Float // precipitation probability
)


// ------------------- WeatherAPI DTOs -------------------
data class WeatherApiForecastResponse(
    val location: WaLocation?,
    val current: WaCurrent?,
    val forecast: WaForecast?,
    val alerts: WaAlerts?
)

data class WaLocation(
    val name: String
)

data class WaCurrent(
    val temp_c: Float,
    val feelslike_c: Float,
    val humidity: Int,
    val wind_kph: Float,
    val wind_degree: Float,
    val pressure_mb: Float,
    val uv: Float,
    val air_quality: WaAqi?,
    val condition: WaCondition?
)

data class WaCondition(
    val text: String,
    val code: Int
)

data class WaAqi(
    @com.squareup.moshi.Json(name = "us-epa-index") val epaIndex: Int?
)

data class WaForecast(
    val forecastday: List<WaForecastDay>?
)

data class WaForecastDay(
    val date: String,
    val day: WaDay?,
    val astro: WaAstro?,
    val hour: List<WaHour>?
)

data class WaDay(
    val maxtemp_c: Float,
    val mintemp_c: Float,
    val daily_chance_of_rain: Int,
    val totalprecip_mm: Float,
    val uv: Float,
    val condition: WaCondition?
)

data class WaAstro(
    val sunrise: String,
    val sunset: String
)

data class WaHour(
    val time: String,
    val temp_c: Float,
    val condition: WaCondition?,
    val chance_of_rain: Int
)

data class WaAlerts(
    val alert: List<WaAlert>?
)

data class WaAlert(
    val headline: String,
    val severity: String?,
    val desc: String?,
    val sender: String?
)


// ------------------- Tomorrow.io DTOs -------------------
data class TomorrowForecastResponse(
    val timelines: TomorrowTimelines?
)

data class TomorrowTimelines(
    val minutely: List<TomorrowInterval>?,
    val hourly: List<TomorrowInterval>?,
    val daily: List<TomorrowInterval>?
)

data class TomorrowInterval(
    val time: String,
    val values: TomorrowValues?
)

data class TomorrowValues(
    val temperature: Float?,
    val temperatureApparent: Float?,
    val humidity: Float?,
    val windSpeed: Float?,
    val windDirection: Float?,
    val pressureSurfaceLevel: Float?,
    val uvIndex: Int?,
    val precipitationProbability: Int?,
    val precipitationIntensity: Float?,
    val sunriseTime: String?,
    val sunsetTime: String?,
    val weatherCode: Int?
)
