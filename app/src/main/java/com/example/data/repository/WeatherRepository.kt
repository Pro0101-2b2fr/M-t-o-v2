package com.example.data.repository

import android.util.Log
import com.example.data.api.ExternalWeatherService
import com.example.data.api.OpenMeteoService
import com.example.data.db.WeatherDao
import com.example.data.manager.ApiQuotaManager
import com.example.data.manager.AppSettings
import com.example.data.model.CachedWeatherEntity
import com.example.data.model.ComparativeWeatherResult
import com.example.data.model.FavoriteCity
import com.example.data.model.ForecastDay
import com.example.data.model.ForecastHour
import com.example.data.model.UnifiedWeather
import com.example.data.model.WeatherAlert
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherDeviation
import com.example.data.model.WeatherSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class WeatherRepository(
    private val openMeteoService: OpenMeteoService,
    private val externalService: ExternalWeatherService,
    private val weatherDao: WeatherDao,
    private val quotaManager: ApiQuotaManager
) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val resultAdapter = moshi.adapter(ComparativeWeatherResult::class.java)

    // Main fetch comparative weather flow
    suspend fun fetchComparativeWeather(
        latitude: Double,
        longitude: Double,
        cityName: String
    ): ComparativeWeatherResult = withContext(Dispatchers.IO) {
        val appSettings = quotaManager.appSettingsFlow.first()
        val results = mutableMapOf<WeatherSource, Result<UnifiedWeather>>()
        
        // 1. Fetch from each source concurrently
        coroutineScope {
            val jobs = WeatherSource.values().map { source ->
                async {
                    val isActive = appSettings.isSourceActive(source)
                    if (isActive) {
                        // Attempt call within 8 second timeout
                        val singleResult = withTimeoutOrNull(8000) {
                            try {
                                quotaManager.recordCall(source) // Increment quota
                                val weather = fetchFromSource(source, latitude, longitude, cityName, appSettings)
                                Result.success(weather)
                            } catch (e: Exception) {
                                Result.failure<UnifiedWeather>(e)
                            }
                        } ?: Result.failure<UnifiedWeather>(Exception("Timeout de 8s dépassé"))
                        
                        source to singleResult
                    } else {
                        // Source is not active (user disabled, no API key, or quota exceeded)
                        val reason = when {
                            appSettings.quotaDisabled[source] == true -> "Quota journalier dépassé"
                            appSettings.enabledSources[source] == false -> "Désactivé par l'utilisateur"
                            source.requiresKey && appSettings.apiKeys[source].isNullOrBlank() -> "Clé API manquante dans les réglages"
                            else -> "Inactif"
                        }
                        source to Result.failure<UnifiedWeather>(Exception(reason))
                    }
                }
            }
            jobs.forEach { job ->
                val pair = job.await()
                results[pair.first] = pair.second
            }
        }

        // Separate successful results and errors
        val successfulSources = mutableMapOf<WeatherSource, UnifiedWeather>()
        val sourceErrors = mutableMapOf<WeatherSource, String>()

        results.forEach { (source, res) ->
            res.fold(
                onSuccess = { successfulSources[source] = it },
                onFailure = { sourceErrors[source] = it.localizedMessage ?: "Erreur inconnue" }
            )
        }

        val cityId = "${latitude}_${longitude}"

        // 2. If ALL sources failed, load from local Room cache
        if (successfulSources.isEmpty()) {
            val cachedEntity = weatherDao.getCachedWeather(cityId)
            if (cachedEntity != null) {
                try {
                    val cachedResult = resultAdapter.fromJson(cachedEntity.serializedComparison)
                    if (cachedResult != null) {
                        return@withContext cachedResult.copy(
                            isOffline = true,
                            timestamp = cachedEntity.lastUpdated
                        )
                    }
                } catch (e: Exception) {
                    Log.e("WeatherRepo", "Error parsing cache: ${e.message}")
                }
            }
            val detailMsg = sourceErrors.entries.joinToString("; ") { "${it.key.displayName}: ${it.value}" }
            throw Exception("Aucune source météo n'a répondu et aucun cache n'est disponible. " +
                    "Veuillez vérifier votre connexion Internet.\n\nDétails :\n$detailMsg")
        }

        // 3. Calculate Average/Consensus Weather
        val averageWeather = calculateAverageWeather(successfulSources.values.toList())
        val averageHourly = calculateAverageHourly(successfulSources.values.toList())
        val averageDaily = calculateAverageDaily(successfulSources.values.toList())

        // 4. Calculate Deviations for each source
        val deviations = calculateDeviations(successfulSources, averageWeather)

        val comparativeResult = ComparativeWeatherResult(
            cityName = cityName,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis(),
            isOffline = false,
            sourcesData = successfulSources,
            sourceErrors = sourceErrors,
            averageWeather = averageWeather,
            averageHourly = averageHourly,
            averageDaily = averageDaily,
            sourceDeviations = deviations
        )

        // 5. Save to local Room Cache
        try {
            val serialized = resultAdapter.toJson(comparativeResult)
            weatherDao.insertCachedWeather(
                CachedWeatherEntity(
                    cityId = cityId,
                    cityName = cityName,
                    latitude = latitude,
                    longitude = longitude,
                    lastUpdated = System.currentTimeMillis(),
                    isOffline = false,
                    serializedComparison = serialized
                )
            )
        } catch (e: Exception) {
            Log.e("WeatherRepo", "Error saving cache: ${e.message}")
        }

        return@withContext comparativeResult
    }

    private suspend fun fetchFromSource(
        source: WeatherSource,
        lat: Double,
        lon: Double,
        cityName: String,
        settings: AppSettings
    ): UnifiedWeather {
        // ALWAYS fetch OpenMeteo as baseline
        val pmWeather = openMeteoService.getWeatherForecast(lat, lon)
        val pmAqi = try {
            openMeteoService.getAirQuality(lat, lon)
        } catch (e: Exception) {
            null
        }

        val baseWeather = mapOpenMeteoToUnified(pmWeather, pmAqi, cityName)

        if (source == WeatherSource.OPEN_METEO) {
            return baseWeather
        }

        // Direct real keyless API calls
        if (source == WeatherSource.METEO_FRANCE) {
            val response = openMeteoService.getMeteoFranceForecast(lat, lon)
            return mapOpenMeteoToUnified(response, pmAqi, cityName).copy(source = WeatherSource.METEO_FRANCE)
        }
        if (source == WeatherSource.DWD_ICON) {
            val response = openMeteoService.getDwdIconForecast(lat, lon)
            return mapOpenMeteoToUnified(response, pmAqi, cityName).copy(source = WeatherSource.DWD_ICON)
        }
        if (source == WeatherSource.NCEP_GFS) {
            val response = openMeteoService.getGfsForecast(lat, lon)
            return mapOpenMeteoToUnified(response, pmAqi, cityName).copy(source = WeatherSource.NCEP_GFS)
        }
        if (source == WeatherSource.ECMWF) {
            val response = openMeteoService.getEcmwfForecast(lat, lon)
            return mapOpenMeteoToUnified(response, pmAqi, cityName).copy(source = WeatherSource.ECMWF)
        }
        if (source == WeatherSource.MET_NORWAY) {
            val response = externalService.getMetNorwayForecast(
                "https://api.met.no/weatherapi/locationforecast/2.0/compact",
                lat, lon
            )
            return mapMetNorwayToUnified(response, cityName, lat, lon)
        }

        val apiKey = settings.apiKeys[source] ?: ""
        
        // If API key is missing, throw clear exception
        if (apiKey.isBlank()) {
            throw Exception("Clé API manquante dans les réglages")
        }

        return when (source) {
            WeatherSource.OPEN_WEATHER_MAP -> {
                val currentOw = externalService.getOpenWeatherCurrent(
                    "https://api.openweathermap.org/data/2.5/weather",
                    lat, lon, apiKey
                )
                val forecastOw = externalService.getOpenWeatherForecast(
                    "https://api.openweathermap.org/data/2.5/forecast",
                    lat, lon, apiKey
                )
                mapOpenWeatherToUnified(currentOw, forecastOw, cityName, lat, lon)
            }
            WeatherSource.WEATHER_API -> {
                val response = externalService.getWeatherApiForecast(
                    "https://api.weatherapi.com/v1/forecast.json",
                    apiKey, "$lat,$lon"
                )
                mapWeatherApiToUnified(response, cityName, lat, lon)
            }
            WeatherSource.TOMORROW_IO -> {
                val response = externalService.getTomorrowForecast(
                    "https://api.tomorrow.io/v4/weather/forecast",
                    "$lat,$lon", apiKey
                )
                mapTomorrowToUnified(response, cityName, lat, lon)
            }
            else -> throw Exception("Source non prise en charge: ${source.displayName}")
        }
    }

    // Map Open-Meteo to UnifiedWeather
    private fun mapOpenMeteoToUnified(
        response: com.example.data.api.OpenMeteoResponse,
        aqiResponse: com.example.data.api.OpenMeteoAqiResponse?,
        cityName: String
    ): UnifiedWeather {
        val currentDto = response.current ?: throw Exception("Données actuelles Open-Meteo absentes")
        val hourlyDto = response.hourly
        val dailyDto = response.daily
 
        val aqiRaw = aqiResponse?.current?.european_aqi
        val mappedAqi = aqiRaw?.let {
            when {
                it <= 20 -> 1
                it <= 40 -> 2
                it <= 60 -> 3
                it <= 80 -> 4
                else -> 5
            }
        }
 
        val conditionText = mapWmoCodeToText(currentDto.weather_code ?: 0)
        val conditionIcon = mapWmoCodeToIcon(currentDto.weather_code ?: 0)
 
        val currentCond = WeatherCondition(
            temperature = currentDto.temperature_2m ?: 0f,
            feelsLike = currentDto.apparent_temperature ?: (currentDto.temperature_2m ?: 0f),
            humidity = currentDto.relative_humidity_2m ?: 0,
            windSpeed = currentDto.wind_speed_10m ?: 0f,
            windDirection = currentDto.wind_direction_10m ?: 0f,
            pressure = currentDto.pressure_msl ?: 1013f,
            uvIndex = currentDto.uv_index,
            aqi = mappedAqi,
            precipitationProb = if (!hourlyDto?.precipitation_probability.isNullOrEmpty() && hourlyDto?.precipitation_probability?.get(0) != null) hourlyDto.precipitation_probability[0]!! else 0,
            precipitationQty = currentDto.precipitation ?: 0f,
            sunrise = if (!dailyDto?.sunrise.isNullOrEmpty() && dailyDto?.sunrise?.get(0) != null) formatSunTime(dailyDto.sunrise[0]!!) else "06:00",
            sunset = if (!dailyDto?.sunset.isNullOrEmpty() && dailyDto?.sunset?.get(0) != null) formatSunTime(dailyDto.sunset[0]!!) else "21:00",
            conditionText = conditionText,
            conditionIcon = conditionIcon
        )
 
        val hourlyList = mutableListOf<ForecastHour>()
        if (hourlyDto != null) {
            val times = hourlyDto.time
            val temps = hourlyDto.temperature_2m
            val codes = hourlyDto.weather_code
            val probs = hourlyDto.precipitation_probability
            if (times != null && temps != null && codes != null && probs != null) {
                val limit = minOf(times.size, temps.size, codes.size, probs.size, 24)
                for (i in 0 until limit) {
                    hourlyList.add(
                        ForecastHour(
                            time = times[i]?.let { formatHourlyTime(it) } ?: "00:00",
                            temp = temps[i] ?: 0f,
                            conditionIcon = mapWmoCodeToIcon(codes[i] ?: 0),
                            precipitationProb = probs[i] ?: 0
                        )
                    )
                }
            }
        }
 
        val dailyList = mutableListOf<ForecastDay>()
        if (dailyDto != null) {
            val times = dailyDto.time
            val minTemps = dailyDto.temperature_2m_min
            val maxTemps = dailyDto.temperature_2m_max
            val codes = dailyDto.weather_code
            val probs = dailyDto.precipitation_probability_max
            if (times != null && minTemps != null && maxTemps != null && codes != null && probs != null) {
                val limit = minOf(times.size, minTemps.size, maxTemps.size, codes.size, probs.size, 7)
                for (i in 0 until limit) {
                    dailyList.add(
                        ForecastDay(
                            date = times[i]?.let { formatDailyDate(it) } ?: "Jour",
                            minTemp = minTemps[i] ?: 0f,
                            maxTemp = maxTemps[i] ?: 0f,
                            conditionIcon = mapWmoCodeToIcon(codes[i] ?: 0),
                            precipitationProb = probs[i] ?: 0,
                            conditionText = mapWmoCodeToText(codes[i] ?: 0)
                        )
                    )
                }
            }
        }
 
        return UnifiedWeather(
            source = WeatherSource.OPEN_METEO,
            timestamp = System.currentTimeMillis(),
            cityName = cityName,
            latitude = response.latitude ?: 0.0,
            longitude = response.longitude ?: 0.0,
            current = currentCond,
            hourly = hourlyList,
            daily = dailyList,
            alerts = emptyList()
        )
    }

    // Map OpenWeatherMap to UnifiedWeather
    private fun mapOpenWeatherToUnified(
        current: com.example.data.api.OpenWeatherCurrentResponse,
        forecast: com.example.data.api.OpenWeatherForecastResponse,
        cityName: String,
        lat: Double,
        lon: Double
    ): UnifiedWeather {
        val main = current.main ?: throw Exception("Données OWM absentes")
        val weatherItem = current.weather?.firstOrNull()
        val wind = current.wind
        val sys = current.sys

        val iconType = mapOwmIconToType(weatherItem?.icon ?: "")

        val currentCond = WeatherCondition(
            temperature = main.temp,
            feelsLike = main.feels_like,
            humidity = main.humidity,
            windSpeed = (wind?.speed ?: 0f) * 3.6f, // m/s to km/h
            windDirection = wind?.deg ?: 0f,
            pressure = main.pressure,
            uvIndex = null,
            aqi = null,
            precipitationProb = 0,
            precipitationQty = 0f,
            sunrise = if (sys != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sys.sunrise * 1000)) else "06:00",
            sunset = if (sys != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sys.sunset * 1000)) else "21:00",
            conditionText = weatherItem?.description?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "Clair",
            conditionIcon = iconType
        )

        val hourlyList = mutableListOf<ForecastHour>()
        val dailyList = mutableListOf<ForecastDay>()

        forecast.list?.take(8)?.forEach { item ->
            val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.dt * 1000))
            hourlyList.add(
                ForecastHour(
                    time = timeString,
                    temp = item.main?.temp ?: 0f,
                    conditionIcon = mapOwmIconToType(item.weather?.firstOrNull()?.icon ?: ""),
                    precipitationProb = ((item.pop ?: 0f) * 100).toInt()
                )
            )
        }

        forecast.list?.chunked(8)?.take(5)?.forEach { dayItems ->
            val first = dayItems.first()
            val minTemp = dayItems.minOfOrNull { it.main?.temp ?: 15f } ?: 15f
            val maxTemp = dayItems.maxOfOrNull { it.main?.temp ?: 25f } ?: 25f
            val dateString = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(first.dt * 1000)).replaceFirstChar { it.titlecase(Locale.getDefault()) }

            dailyList.add(
                ForecastDay(
                    date = dateString,
                    minTemp = minTemp,
                    maxTemp = maxTemp,
                    conditionIcon = mapOwmIconToType(first.weather?.firstOrNull()?.icon ?: ""),
                    precipitationProb = ((dayItems.maxOfOrNull { it.pop } ?: 0f) * 100).toInt(),
                    conditionText = first.weather?.firstOrNull()?.description?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: ""
                )
            )
        }

        return UnifiedWeather(
            source = WeatherSource.OPEN_WEATHER_MAP,
            timestamp = System.currentTimeMillis(),
            cityName = cityName,
            latitude = lat,
            longitude = lon,
            current = currentCond,
            hourly = hourlyList,
            daily = dailyList,
            alerts = emptyList()
        )
    }

    // Map WeatherAPI to UnifiedWeather
    private fun mapWeatherApiToUnified(
        response: com.example.data.api.WeatherApiForecastResponse,
        cityName: String,
        lat: Double,
        lon: Double
    ): UnifiedWeather {
        val current = response.current ?: throw Exception("Données WeatherAPI absentes")
        val forecastDayList = response.forecast?.forecastday ?: emptyList()

        val conditionText = current.condition?.text ?: "Clair"
        val conditionIcon = mapWeatherApiCodeToIcon(current.condition?.code ?: 1000)

        val currentCond = WeatherCondition(
            temperature = current.temp_c,
            feelsLike = current.feelslike_c,
            humidity = current.humidity,
            windSpeed = current.wind_kph,
            windDirection = current.wind_degree,
            pressure = current.pressure_mb,
            uvIndex = current.uv,
            aqi = current.air_quality?.epaIndex,
            precipitationProb = if (forecastDayList.isNotEmpty()) forecastDayList[0].day?.daily_chance_of_rain ?: 0 else 0,
            precipitationQty = if (forecastDayList.isNotEmpty()) forecastDayList[0].day?.totalprecip_mm ?: 0f else 0f,
            sunrise = forecastDayList.firstOrNull()?.astro?.sunrise ?: "06:00",
            sunset = forecastDayList.firstOrNull()?.astro?.sunset ?: "21:00",
            conditionText = conditionText,
            conditionIcon = conditionIcon
        )

        val hourlyList = mutableListOf<ForecastHour>()
        if (forecastDayList.isNotEmpty()) {
            forecastDayList[0].hour?.take(24)?.forEach { hr ->
                val timeClean = try {
                    val rawTime = hr.time
                    rawTime.substringAfter(" ")
                } catch (e: Exception) {
                    "00:00"
                }
                hourlyList.add(
                    ForecastHour(
                        time = timeClean,
                        temp = hr.temp_c,
                        conditionIcon = mapWeatherApiCodeToIcon(hr.condition?.code ?: 1000),
                        precipitationProb = hr.chance_of_rain
                    )
                )
            }
        }

        val dailyList = forecastDayList.map { dayDto ->
            val dateStr = try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayDto.date)
                SimpleDateFormat("EEEE", Locale.getDefault()).format(parsed).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            } catch (e: Exception) {
                dayDto.date
            }
            ForecastDay(
                date = dateStr,
                minTemp = dayDto.day?.mintemp_c ?: 15f,
                maxTemp = dayDto.day?.maxtemp_c ?: 25f,
                conditionIcon = mapWeatherApiCodeToIcon(dayDto.day?.condition?.code ?: 1000),
                precipitationProb = dayDto.day?.daily_chance_of_rain ?: 0,
                conditionText = dayDto.day?.condition?.text ?: "Clair"
            )
        }

        val alertsList = response.alerts?.alert?.map { alertDto ->
            WeatherAlert(
                title = alertDto.headline,
                severity = alertDto.severity ?: "Moderate",
                description = alertDto.desc ?: "",
                senderName = alertDto.sender ?: "Météo Officielle"
            )
        } ?: emptyList()

        return UnifiedWeather(
            source = WeatherSource.WEATHER_API,
            timestamp = System.currentTimeMillis(),
            cityName = cityName,
            latitude = lat,
            longitude = lon,
            current = currentCond,
            hourly = hourlyList,
            daily = dailyList,
            alerts = alertsList
        )
    }

    // Map Tomorrow.io to UnifiedWeather
    private fun mapTomorrowToUnified(
        response: com.example.data.api.TomorrowForecastResponse,
        cityName: String,
        lat: Double,
        lon: Double
    ): UnifiedWeather {
        val timelines = response.timelines ?: throw Exception("Données Tomorrow.io absentes")
        val currentInterval = timelines.minutely?.firstOrNull() ?: timelines.hourly?.firstOrNull()
        val currentVals = currentInterval?.values ?: throw Exception("Données actuelles Tomorrow.io absentes")

        val conditionIcon = mapTomorrowCodeToIcon(currentVals.weatherCode ?: 1000)

        val currentCond = WeatherCondition(
            temperature = currentVals.temperature ?: 15f,
            feelsLike = currentVals.temperatureApparent ?: 15f,
            humidity = (currentVals.humidity ?: 60f).toInt(),
            windSpeed = (currentVals.windSpeed ?: 10f) * 3.6f,
            windDirection = currentVals.windDirection ?: 0f,
            pressure = currentVals.pressureSurfaceLevel ?: 1013f,
            uvIndex = null,
            aqi = null,
            precipitationProb = currentVals.precipitationProbability ?: 0,
            precipitationQty = currentVals.precipitationIntensity ?: 0f,
            sunrise = "06:00",
            sunset = "21:00",
            conditionText = mapTomorrowCodeToText(currentVals.weatherCode ?: 1000),
            conditionIcon = conditionIcon
        )

        val hourlyList = timelines.hourly?.take(24)?.map { hr ->
            val timeClean = try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(hr.time)
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                "00:00"
            }
            ForecastHour(
                time = timeClean,
                temp = hr.values?.temperature ?: 15f,
                conditionIcon = mapTomorrowCodeToIcon(hr.values?.weatherCode ?: 1000),
                precipitationProb = hr.values?.precipitationProbability ?: 0
            )
        } ?: emptyList()

        val dailyList = timelines.daily?.take(7)?.map { day ->
            val dateStr = try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(day.time)
                SimpleDateFormat("EEEE", Locale.getDefault()).format(date).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            } catch (e: Exception) {
                day.time
            }
            ForecastDay(
                date = dateStr,
                minTemp = day.values?.temperature ?: 10f,
                maxTemp = day.values?.temperature ?: 22f,
                conditionIcon = mapTomorrowCodeToIcon(day.values?.weatherCode ?: 1000),
                precipitationProb = day.values?.precipitationProbability ?: 0,
                conditionText = mapTomorrowCodeToText(day.values?.weatherCode ?: 1000)
            )
        } ?: emptyList()

        return UnifiedWeather(
            source = WeatherSource.TOMORROW_IO,
            timestamp = System.currentTimeMillis(),
            cityName = cityName,
            latitude = lat,
            longitude = lon,
            current = currentCond,
            hourly = hourlyList,
            daily = dailyList,
            alerts = emptyList()
        )
    }

    private suspend fun mapMetNorwayToUnified(
        response: com.example.data.api.MetNorwayResponse,
        cityName: String,
        lat: Double,
        lon: Double
    ): UnifiedWeather {
        val timeseries = response.properties?.timeseries ?: emptyList()
        val currentItem = timeseries.firstOrNull()
        val details = currentItem?.data?.instant?.details ?: throw Exception("Données instantanées MET Norway absentes")
        val summary = currentItem?.data?.next_1_hours?.summary ?: currentItem?.data?.next_6_hours?.summary
        val nextHoursDetails = currentItem?.data?.next_1_hours?.details

        val temp = details.air_temperature ?: throw Exception("Température MET Norway absente")
        val humidity = details.relative_humidity?.toInt() ?: throw Exception("Humidité MET Norway absente")
        val pressure = details.air_pressure_at_sea_level ?: throw Exception("Pression MET Norway absente")
        val windSpeedMs = details.wind_speed ?: throw Exception("Vitesse vent MET Norway absente")
        val windSpeedKmh = windSpeedMs * 3.6f
        val windDir = details.wind_from_direction ?: throw Exception("Direction vent MET Norway absente")
        val uv = details.ultraviolet_index_clear_sky

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sunriseSunset = try {
            externalService.getMetNorwaySunrise(
                "https://api.met.no/weatherapi/sunrise/3.0/sun",
                lat, lon, todayDate
            )
        } catch (e: Exception) {
            null
        }
        val sunrise = sunriseSunset?.properties?.sunrise?.time?.let { formatSunTime(it) } ?: "06:30"
        val sunset = sunriseSunset?.properties?.sunset?.time?.let { formatSunTime(it) } ?: "21:15"

        val symbolCode = summary?.symbol_code ?: "clearsky_day"
        val conditionText = symbolCode.replace("_", " ").replaceFirstChar { it.titlecase(Locale.getDefault()) }

        val currentCond = WeatherCondition(
            temperature = temp,
            feelsLike = temp,
            humidity = humidity,
            windSpeed = windSpeedKmh,
            windDirection = windDir,
            pressure = pressure,
            uvIndex = uv,
            aqi = null,
            precipitationProb = nextHoursDetails?.probability_of_precipitation?.toInt() ?: 0,
            precipitationQty = nextHoursDetails?.precipitation_amount ?: 0f,
            sunrise = sunrise,
            sunset = sunset,
            conditionText = conditionText,
            conditionIcon = mapMetNorwaySymbolToIcon(symbolCode)
        )

        val hourlyList = timeseries.take(24).map { item ->
            val timeStr = try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(item.time)
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                "00:00"
            }
            val hDetails = item.data?.instant?.details
            val hSummary = item.data?.next_1_hours?.summary
            ForecastHour(
                time = timeStr,
                temp = hDetails?.air_temperature ?: temp,
                conditionIcon = mapMetNorwaySymbolToIcon(hSummary?.symbol_code ?: "clearsky_day"),
                precipitationProb = item.data?.next_1_hours?.details?.probability_of_precipitation?.toInt() ?: 0
            )
        }

        val dailyMap = timeseries.groupBy { item ->
            try {
                item.time.substring(0, 10)
            } catch (e: Exception) {
                item.time
            }
        }

        val dailyList = dailyMap.entries.take(7).map { (dayDate, items) ->
            val minT = items.mapNotNull { it.data?.instant?.details?.air_temperature }.minOrNull() ?: temp
            val maxT = items.mapNotNull { it.data?.instant?.details?.air_temperature }.maxOrNull() ?: temp
            val noonItem = items.getOrNull(items.size / 2) ?: items.firstOrNull()
            val daySymbol = noonItem?.data?.next_6_hours?.summary?.symbol_code ?: noonItem?.data?.next_1_hours?.summary?.symbol_code ?: "clearsky_day"
            val dayDateFormatted = try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dayDate)
                SimpleDateFormat("EEEE", Locale.getDefault()).format(date).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            } catch (e: Exception) {
                dayDate
            }
            ForecastDay(
                date = dayDateFormatted,
                minTemp = minT,
                maxTemp = maxT,
                conditionIcon = mapMetNorwaySymbolToIcon(daySymbol),
                precipitationProb = noonItem?.data?.next_6_hours?.details?.probability_of_precipitation?.toInt() ?: 0,
                conditionText = daySymbol.replace("_", " ").replaceFirstChar { it.titlecase(Locale.getDefault()) }
            )
        }

        return UnifiedWeather(
            source = WeatherSource.MET_NORWAY,
            timestamp = System.currentTimeMillis(),
            cityName = cityName,
            latitude = lat,
            longitude = lon,
            current = currentCond,
            hourly = hourlyList,
            daily = dailyList,
            alerts = emptyList()
        )
    }

    private fun mapMetNorwaySymbolToIcon(symbolCode: String): String {
        return when {
            symbolCode.contains("rain") || symbolCode.contains("drizzle") -> "🌧️"
            symbolCode.contains("thunder") -> "⛈️"
            symbolCode.contains("snow") || symbolCode.contains("sleet") -> "❄️"
            symbolCode.contains("fog") -> "🌫️"
            symbolCode.contains("cloud") && symbolCode.contains("partly") -> "⛅"
            symbolCode.contains("cloud") -> "☁️"
            else -> "☀️"
        }
    }

    private fun calculateAverageWeather(sources: List<UnifiedWeather>): WeatherCondition {
        val count = sources.size.toFloat()
        val avgTemp = sources.sumOf { it.current.temperature.toDouble() }.toFloat() / count
        val avgFeels = sources.sumOf { it.current.feelsLike.toDouble() }.toFloat() / count
        val avgHum = (sources.sumOf { it.current.humidity } / sources.size)
        val avgWind = sources.sumOf { it.current.windSpeed.toDouble() }.toFloat() / count
        val avgWindDir = sources.sumOf { it.current.windDirection.toDouble() }.toFloat() / count
        val avgPres = sources.sumOf { it.current.pressure.toDouble() }.toFloat() / count
        val uvList = sources.mapNotNull { it.current.uvIndex }
        val avgUv = if (uvList.isNotEmpty()) uvList.sumOf { it.toDouble() }.toFloat() / uvList.size else null
        val aqiList = sources.mapNotNull { it.current.aqi }
        val avgAqi = if (aqiList.isNotEmpty()) aqiList.sum() / aqiList.size else null
        val avgPrecipProb = (sources.sumOf { it.current.precipitationProb } / sources.size)
        val avgPrecipQty = sources.sumOf { it.current.precipitationQty.toDouble() }.toFloat() / count

        val firstSource = sources.first()
        val representativeSunrise = sources.mapNotNull { it.current.sunrise }.firstOrNull() ?: "06:30"
        val representativeSunset = sources.mapNotNull { it.current.sunset }.firstOrNull() ?: "21:15"

        return WeatherCondition(
            temperature = avgTemp,
            feelsLike = avgFeels,
            humidity = avgHum,
            windSpeed = avgWind,
            windDirection = avgWindDir,
            pressure = avgPres,
            uvIndex = avgUv,
            aqi = avgAqi,
            precipitationProb = avgPrecipProb,
            precipitationQty = avgPrecipQty,
            sunrise = representativeSunrise,
            sunset = representativeSunset,
            conditionText = firstSource.current.conditionText,
            conditionIcon = firstSource.current.conditionIcon
        )
    }

    private fun calculateAverageHourly(sources: List<UnifiedWeather>): List<ForecastHour> {
        val firstSource = sources.first()
        return firstSource.hourly.indices.map { index ->
            val matchingHours = sources.mapNotNull { it.hourly.getOrNull(index) }
            val avgTemp = matchingHours.map { it.temp }.average().toFloat()
            val avgProb = matchingHours.map { it.precipitationProb }.average().toInt()
            ForecastHour(
                time = firstSource.hourly[index].time,
                temp = avgTemp,
                conditionIcon = firstSource.hourly[index].conditionIcon,
                precipitationProb = avgProb
            )
        }
    }

    private fun calculateAverageDaily(sources: List<UnifiedWeather>): List<ForecastDay> {
        val firstSource = sources.first()
        return firstSource.daily.indices.map { index ->
            val matchingDays = sources.mapNotNull { it.daily.getOrNull(index) }
            val avgMin = matchingDays.map { it.minTemp }.average().toFloat()
            val avgMax = matchingDays.map { it.maxTemp }.average().toFloat()
            val avgProb = matchingDays.map { it.precipitationProb }.average().toInt()
            ForecastDay(
                date = firstSource.daily[index].date,
                minTemp = avgMin,
                maxTemp = avgMax,
                conditionIcon = firstSource.daily[index].conditionIcon,
                precipitationProb = avgProb,
                conditionText = firstSource.daily[index].conditionText
            )
        }
    }

    private fun calculateDeviations(
        sources: Map<WeatherSource, UnifiedWeather>,
        avg: WeatherCondition
    ): Map<WeatherSource, WeatherDeviation> {
        return sources.mapValues { (_, weather) ->
            val tempDev = weather.current.temperature - avg.temperature
            val presDev = weather.current.pressure - avg.pressure
            val windDev = weather.current.windSpeed - avg.windSpeed

            WeatherDeviation(
                temperatureDeviation = tempDev,
                isTemperatureUnreliable = abs(tempDev) > 1.5f,
                pressureDeviation = presDev,
                isPressureUnreliable = abs(presDev) > 4.0f,
                windDeviation = windDev,
                isWindUnreliable = abs(windDev) > 6.0f
            )
        }
    }

    private fun mapWmoCodeToText(code: Int): String {
        return when (code) {
            0 -> "Ensoleillé"
            1, 2, 3 -> "Partiellement Nuageux"
            45, 48 -> "Brouillard"
            51, 53, 55 -> "Bruine"
            61, 63, 65 -> "Pluie"
            71, 73, 75 -> "Neige"
            80, 81, 82 -> "Averses de Pluie"
            95, 96, 99 -> "Orageux"
            else -> "Nuageux"
        }
    }

    private fun mapWmoCodeToIcon(code: Int): String {
        return when (code) {
            0 -> "sunny"
            1, 2, 3 -> "cloudy"
            45, 48 -> "cloudy"
            51, 53, 55 -> "rainy"
            61, 63, 65 -> "rainy"
            71, 73, 75 -> "snowy"
            80, 81, 82 -> "rainy"
            95, 96, 99 -> "thunderstorm"
            else -> "cloudy"
        }
    }

    private fun formatSunTime(raw: String): String {
        return try {
            raw.substringAfter("T").substring(0, 5)
        } catch (e: Exception) {
            "06:00"
        }
    }

    private fun formatHourlyTime(raw: String): String {
        return try {
            raw.substringAfter("T").substring(0, 5)
        } catch (e: Exception) {
            "00:00"
        }
    }

    private fun formatDailyDate(raw: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(raw)
            SimpleDateFormat("EEEE", Locale.getDefault()).format(parsed).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        } catch (e: Exception) {
            raw
        }
    }

    private fun mapOwmIconToType(icon: String): String {
        return when {
            icon.contains("01") -> "sunny"
            icon.contains("02") || icon.contains("03") || icon.contains("04") -> "cloudy"
            icon.contains("09") || icon.contains("10") -> "rainy"
            icon.contains("11") -> "thunderstorm"
            icon.contains("13") -> "snowy"
            else -> "cloudy"
        }
    }

    private fun mapWeatherApiCodeToIcon(code: Int): String {
        return when (code) {
            1000 -> "sunny"
            1003, 1006, 1009 -> "cloudy"
            1030, 1135, 1147 -> "cloudy"
            1063, 1150, 1153, 1180, 1183, 1186, 1189, 1192, 1195, 1240, 1243 -> "rainy"
            1087, 1273, 1276 -> "thunderstorm"
            1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1255, 1258 -> "snowy"
            else -> "cloudy"
        }
    }

    private fun mapTomorrowCodeToIcon(code: Int): String {
        return when (code) {
            1000, 1100 -> "sunny"
            1101, 1102, 1001 -> "cloudy"
            2000, 2100 -> "cloudy"
            4000, 4001, 4200, 4201 -> "rainy"
            8000 -> "thunderstorm"
            5000, 5001, 5100, 5101 -> "snowy"
            else -> "cloudy"
        }
    }

    private fun mapTomorrowCodeToText(code: Int): String {
        return when (code) {
            1000 -> "Clair"
            1100 -> "Plutôt Clair"
            1101 -> "Partiellement Nuageux"
            1102 -> "Plutôt Nuageux"
            1001 -> "Couvert"
            2000, 2100 -> "Brouillard"
            4000, 4001 -> "Pluie Légère"
            4200, 4201 -> "Pluie"
            8000 -> "Orageux"
            5000, 5001, 5100, 5101 -> "Neige"
            else -> "Nuageux"
        }
    }
}
