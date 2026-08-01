package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.ExternalWeatherService
import com.example.data.api.OpenMeteoService
import com.example.data.api.OpenMeteoResponse
import com.example.data.api.OpenMeteoAqiResponse
import com.example.data.api.OpenWeatherCurrentResponse
import com.example.data.api.OpenWeatherForecastResponse
import com.example.data.api.WeatherApiForecastResponse
import com.example.data.api.TomorrowForecastResponse
import com.example.data.api.MetNorwayResponse
import com.example.data.api.MetNorwaySunriseResponse
import com.example.data.db.WeatherDao
import com.example.data.manager.ApiQuotaManager
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherSource
import com.example.data.model.UnifiedWeather
import com.example.data.repository.WeatherRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherRepositoryTest {

    private lateinit var repository: WeatherRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyOpenMeteo = object : OpenMeteoService {
            override suspend fun getWeatherForecast(lat: Double, lon: Double, current: String, hourly: String, daily: String, timezone: String) = null as OpenMeteoResponse
            override suspend fun getMeteoFranceForecast(lat: Double, lon: Double, current: String, hourly: String, daily: String, timezone: String) = null as OpenMeteoResponse
            override suspend fun getDwdIconForecast(lat: Double, lon: Double, current: String, hourly: String, daily: String, timezone: String) = null as OpenMeteoResponse
            override suspend fun getGfsForecast(lat: Double, lon: Double, current: String, hourly: String, daily: String, timezone: String) = null as OpenMeteoResponse
            override suspend fun getEcmwfForecast(lat: Double, lon: Double, current: String, hourly: String, daily: String, timezone: String) = null as OpenMeteoResponse
            override suspend fun getAirQuality(lat: Double, lon: Double, current: String) = null as OpenMeteoAqiResponse
        }
        val dummyExternal = object : ExternalWeatherService {
            override suspend fun getOpenWeatherCurrent(url: String, lat: Double, lon: Double, apiKey: String, units: String, lang: String) = null as OpenWeatherCurrentResponse
            override suspend fun getOpenWeatherForecast(url: String, lat: Double, lon: Double, apiKey: String, units: String, lang: String) = null as OpenWeatherForecastResponse
            override suspend fun getWeatherApiForecast(url: String, apiKey: String, query: String, days: Int, aqi: String, alerts: String, lang: String) = null as WeatherApiForecastResponse
            override suspend fun getTomorrowForecast(url: String, location: String, apiKey: String, units: String) = null as TomorrowForecastResponse
            override suspend fun getMetNorwayForecast(url: String, lat: Double, lon: Double) = null as MetNorwayResponse
            override suspend fun getMetNorwaySunrise(url: String, lat: Double, lon: Double, date: String) = null as MetNorwaySunriseResponse
        }
        val dummyDao = object : WeatherDao {
            override fun getAllFavoriteCitiesFlow() = throw NotImplementedError()
            override suspend fun getAllFavoriteCities() = emptyList<com.example.data.model.FavoriteCity>()
            override suspend fun insertFavoriteCity(city: com.example.data.model.FavoriteCity) = 0L
            override suspend fun deleteFavoriteCity(city: com.example.data.model.FavoriteCity) {}
            override suspend fun deleteFavoriteCityByName(name: String) {}
            override suspend fun isCityFavorite(name: String) = false
            override suspend fun getCachedWeather(cityId: String) = null
            override suspend fun getLatestCachedWeather() = null
            override suspend fun insertCachedWeather(cache: com.example.data.model.CachedWeatherEntity) {}
            override suspend fun deleteCachedWeather(cityId: String) {}
            override suspend fun clearCache() {}
        }
        val quotaManager = ApiQuotaManager(context)
        repository = WeatherRepository(dummyOpenMeteo, dummyExternal, dummyDao, quotaManager)
    }

    @Test
    fun testCalculateAverageWeather_basic() {
        val weather1 = createDummyWeather(temp = 20f, humidity = 50, aqi = 2, uv = 5f)
        val weather2 = createDummyWeather(temp = 24f, humidity = 60, aqi = 4, uv = 7f)

        val avg = repository.calculateAverageWeather(listOf(weather1, weather2))

        assertEquals(22f, avg.temperature, 0.01f)
        assertEquals(55, avg.humidity)
        assertEquals(3, avg.aqi)
        assertEquals(6f, avg.uvIndex!!, 0.01f)
    }

    @Test
    fun testCalculateAverageWeather_nullsIgnored() {
        val weather1 = createDummyWeather(temp = 20f, aqi = null, uv = null)
        val weather2 = createDummyWeather(temp = 30f, aqi = 3, uv = 6f)

        val avg = repository.calculateAverageWeather(listOf(weather1, weather2))

        assertEquals(25f, avg.temperature, 0.01f)
        assertEquals(3, avg.aqi)
        assertEquals(6f, avg.uvIndex!!, 0.01f)
    }

    @Test
    fun testCalculateDeviations_thresholds() {
        val avgCondition = WeatherCondition(
            temperature = 20f,
            feelsLike = 20f,
            humidity = 50,
            windSpeed = 10f,
            windDirection = 0f,
            pressure = 1013f,
            uvIndex = 5f,
            aqi = 2,
            precipitationProb = 0,
            precipitationQty = 0f,
            sunrise = "06:00",
            sunset = "21:00",
            conditionText = "Clair",
            conditionIcon = "sunny"
        )

        // Temperature threshold: 1.5°C
        val unreliablesTempPlus = createDummyWeather(temp = 21.51f, pressure = 1013f, windSpeed = 10f)
        val reliablesTempPlus = createDummyWeather(temp = 21.5f, pressure = 1013f, windSpeed = 10f)

        val devUnreliable = repository.calculateDeviations(mapOf(WeatherSource.OPEN_METEO to unreliablesTempPlus), avgCondition)
        val devReliable = repository.calculateDeviations(mapOf(WeatherSource.OPEN_METEO to reliablesTempPlus), avgCondition)

        assertTrue(devUnreliable[WeatherSource.OPEN_METEO]!!.isTemperatureUnreliable)
        assertFalse(devReliable[WeatherSource.OPEN_METEO]!!.isTemperatureUnreliable)

        // Pressure threshold: 4.0 hPa
        val unreliablesPressure = createDummyWeather(temp = 20f, pressure = 1017.1f, windSpeed = 10f)
        val reliablesPressure = createDummyWeather(temp = 20f, pressure = 1017.0f, windSpeed = 10f)
        assertTrue(repository.calculateDeviations(mapOf(WeatherSource.OPEN_METEO to unreliablesPressure), avgCondition)[WeatherSource.OPEN_METEO]!!.isPressureUnreliable)
        assertFalse(repository.calculateDeviations(mapOf(WeatherSource.OPEN_METEO to reliablesPressure), avgCondition)[WeatherSource.OPEN_METEO]!!.isPressureUnreliable)

        // Wind threshold: 6.0 km/h
        val unreliablesWind = createDummyWeather(temp = 20f, pressure = 1013f, windSpeed = 16.1f)
        val reliablesWind = createDummyWeather(temp = 20f, pressure = 1013f, windSpeed = 16.0f)
        assertTrue(repository.calculateDeviations(mapOf(WeatherSource.OPEN_METEO to unreliablesWind), avgCondition)[WeatherSource.OPEN_METEO]!!.isWindUnreliable)
        assertFalse(repository.calculateDeviations(mapOf(WeatherSource.OPEN_METEO to reliablesWind), avgCondition)[WeatherSource.OPEN_METEO]!!.isWindUnreliable)
    }

    @Test
    fun testWmoMapping() {
        assertEquals("Ensoleillé", repository.mapWmoCodeToText(0))
        assertEquals("sunny", repository.mapWmoCodeToIcon(0))

        assertEquals("Pluie", repository.mapWmoCodeToText(61))
        assertEquals("rainy", repository.mapWmoCodeToIcon(61))

        assertEquals("Orageux", repository.mapWmoCodeToText(95))
        assertEquals("thunderstorm", repository.mapWmoCodeToIcon(95))
    }

    private fun createDummyWeather(
        temp: Float = 20f,
        humidity: Int = 50,
        windSpeed: Float = 10f,
        pressure: Float = 1013f,
        aqi: Int? = null,
        uv: Float? = null
    ): UnifiedWeather {
        return UnifiedWeather(
            source = WeatherSource.OPEN_METEO,
            timestamp = System.currentTimeMillis(),
            cityName = "Paris",
            latitude = 48.85,
            longitude = 2.35,
            current = WeatherCondition(
                temperature = temp,
                feelsLike = temp,
                humidity = humidity,
                windSpeed = windSpeed,
                windDirection = 0f,
                pressure = pressure,
                uvIndex = uv,
                aqi = aqi,
                precipitationProb = 0,
                precipitationQty = 0f,
                sunrise = "06:00",
                sunset = "21:00",
                conditionText = "Clair",
                conditionIcon = "sunny"
            ),
            hourly = emptyList(),
            daily = emptyList(),
            alerts = emptyList()
        )
    }
}
