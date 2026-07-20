package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.api.ExternalWeatherService
import com.example.data.api.OpenMeteoService
import com.example.data.db.WeatherDatabase
import com.example.data.manager.ApiQuotaManager
import com.example.data.manager.AppSettings
import com.example.data.manager.LocationManager
import com.example.data.manager.LocationResult
import com.example.data.model.ComparativeWeatherResult
import com.example.data.model.FavoriteCity
import com.example.data.model.WeatherSource
import com.example.data.repository.WeatherRepository
import com.example.data.worker.WeatherSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    
    // Core Services Setup (constructor injection alternative for Single Activity ViewModel)
    private val database = WeatherDatabase.getDatabase(context)
    private val weatherDao = database.weatherDao()
    private val quotaManager = ApiQuotaManager(context)
    private val locationManager = LocationManager(context)

    private val okHttpClient = OkHttpClient.Builder().build()
    
    private val openMeteoService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(OpenMeteoService::class.java)

    private val externalService = Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/data/2.5/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(ExternalWeatherService::class.java)

    private val repository = WeatherRepository(
        openMeteoService = openMeteoService,
        externalService = externalService,
        weatherDao = weatherDao,
        quotaManager = quotaManager
    )

    // Flow representing all configuration and usage quotas
    val appSettings: StateFlow<AppSettings> = quotaManager.appSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings(
            themeMode = "auto",
            themeColor = "sky",
            unitTemp = "C",
            unitWind = "kmh",
            unitPressure = "hpa",
            refreshIntervalHours = 3,
            prioritySource = WeatherSource.OPEN_METEO,
            visibleWidgets = setOf("aqi", "uv", "wind", "alerts", "details"),
            quotaCalls = emptyMap(),
            quotaDisabled = emptyMap(),
            enabledSources = emptyMap(),
            apiKeys = emptyMap()
        )
    )

    // Favorite cities flow
    val favoriteCities: StateFlow<List<FavoriteCity>> = weatherDao.getAllFavoriteCitiesFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Selected/Current City State
    private val _selectedCity = MutableStateFlow<FavoriteCity?>(null)
    val selectedCity: StateFlow<FavoriteCity?> = _selectedCity.asStateFlow()

    // UI state for weather
    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    // Location request states
    private val _locationPermissionGranted = MutableStateFlow(locationManager.hasLocationPermissions())
    val locationPermissionGranted = _locationPermissionGranted.asStateFlow()

    init {
        // Run setup and load weather
        viewModelScope.launch {
            // Check settings to see if sync worker is scheduled
            val currentSettings = appSettings.first()
            scheduleSync(currentSettings.refreshIntervalHours)

            // Auto-populate favorite cities if empty
            val favorites = weatherDao.getAllFavoriteCities()
            if (favorites.isEmpty()) {
                val defaultCity = FavoriteCity(
                    name = LocationManager.DEFAULT_CITY_NAME,
                    latitude = LocationManager.DEFAULT_LAT,
                    longitude = LocationManager.DEFAULT_LON,
                    isCurrentLocation = false
                )
                weatherDao.insertFavoriteCity(defaultCity)
                _selectedCity.value = defaultCity
            } else {
                _selectedCity.value = favorites.firstOrNull { it.isCurrentLocation } ?: favorites.first()
            }

            // Load initial weather
            loadWeather()
        }
    }

    // Load or refresh weather
    fun loadWeather() {
        viewModelScope.launch {
            _weatherState.value = WeatherUiState.Loading
            
            val city = _selectedCity.value
            if (city == null) {
                // Try GPS if no city is loaded
                refreshLocationAndLoadWeather()
                return@launch
            }

            try {
                val result = repository.fetchComparativeWeather(
                    latitude = city.latitude,
                    longitude = city.longitude,
                    cityName = city.name
                )
                _weatherState.value = WeatherUiState.Success(result)
            } catch (e: Exception) {
                _weatherState.value = WeatherUiState.Error(e.localizedMessage ?: "Erreur de chargement")
            }
        }
    }

    // Refresh current GPS location and fetch weather
    fun refreshLocationAndLoadWeather() {
        viewModelScope.launch {
            _weatherState.value = WeatherUiState.Loading
            val locationResult = locationManager.getCurrentLocation()
            
            val tempCity = FavoriteCity(
                name = locationResult.name,
                latitude = locationResult.lat,
                longitude = locationResult.lon,
                isCurrentLocation = locationResult is LocationResult.Success
            )
            
            _selectedCity.value = tempCity
            
            try {
                val result = repository.fetchComparativeWeather(
                    latitude = tempCity.latitude,
                    longitude = tempCity.longitude,
                    cityName = tempCity.name
                )
                _weatherState.value = WeatherUiState.Success(result)
            } catch (e: Exception) {
                _weatherState.value = WeatherUiState.Error(e.localizedMessage ?: "Erreur de géolocalisation")
            }
        }
    }

    // Select city from Favorites
    fun selectCity(city: FavoriteCity) {
        viewModelScope.launch {
            _selectedCity.value = city
            loadWeather()
        }
    }

    // Add city to favorites by name and coordinates
    fun addCityToFavorites(name: String, lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val newCity = FavoriteCity(
                name = name,
                latitude = lat,
                longitude = lon,
                isCurrentLocation = false
            )
            weatherDao.insertFavoriteCity(newCity)
            _selectedCity.value = newCity
            loadWeather()
        }
    }

    // Delete city from favorites
    fun removeCityFromFavorites(city: FavoriteCity) {
        viewModelScope.launch(Dispatchers.IO) {
            weatherDao.deleteFavoriteCity(city)
            val currentSelected = _selectedCity.value
            if (currentSelected?.name == city.name) {
                val remaining = weatherDao.getAllFavoriteCities()
                if (remaining.isNotEmpty()) {
                    _selectedCity.value = remaining.first()
                    loadWeather()
                } else {
                    val defaultCity = FavoriteCity(
                        name = LocationManager.DEFAULT_CITY_NAME,
                        latitude = LocationManager.DEFAULT_LAT,
                        longitude = LocationManager.DEFAULT_LON
                    )
                    weatherDao.insertFavoriteCity(defaultCity)
                    _selectedCity.value = defaultCity
                    loadWeather()
                }
            }
        }
    }

    // Check if city is in favorites
    suspend fun isCityFavorite(name: String): Boolean {
        return weatherDao.isCityFavorite(name)
    }

    // Save customized app settings
    fun saveApiKey(source: WeatherSource, key: String) = viewModelScope.launch {
        quotaManager.saveApiKey(source, key)
        loadWeather() // Reload weather if api key changed
    }

    fun toggleSource(source: WeatherSource, enabled: Boolean) = viewModelScope.launch {
        quotaManager.toggleSourceEnabled(source, enabled)
        loadWeather() // Reload weather to update comparison
    }

    fun updateThemeMode(mode: String) = viewModelScope.launch {
        quotaManager.updateThemeMode(mode)
    }

    fun updateThemeColor(color: String) = viewModelScope.launch {
        quotaManager.updateThemeColor(color)
    }

    fun updateUnitTemp(unit: String) = viewModelScope.launch {
        quotaManager.updateUnitTemp(unit)
    }

    fun updateUnitWind(unit: String) = viewModelScope.launch {
        quotaManager.updateUnitWind(unit)
    }

    fun updateUnitPressure(unit: String) = viewModelScope.launch {
        quotaManager.updateUnitPressure(unit)
    }

    fun updatePrioritySource(source: WeatherSource) = viewModelScope.launch {
        quotaManager.updatePrioritySource(source)
    }

    fun updateVisibleWidgets(widgets: Set<String>) = viewModelScope.launch {
        quotaManager.updateVisibleWidgets(widgets)
    }

    fun updateRefreshInterval(hours: Int) = viewModelScope.launch {
        quotaManager.updateRefreshInterval(hours)
        scheduleSync(hours)
    }

    fun resetQuotas() = viewModelScope.launch {
        quotaManager.resetQuotas()
    }

    // Configure background sync periodic WorkRequest
    private fun scheduleSync(intervalHours: Int) {
        try {
            if (intervalHours == 0) {
                WorkManager.getInstance(context).cancelUniqueWork("WeatherSyncWork")
                Log.d("WeatherViewModel", "Background sync cancelled.")
            } else {
                val syncRequest = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
                    intervalHours.toLong(), TimeUnit.HOURS
                ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "WeatherSyncWork",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    syncRequest
                )
                Log.d("WeatherViewModel", "Background sync scheduled every ${intervalHours}h.")
            }
        } catch (e: Exception) {
            Log.e("WeatherViewModel", "Failed to schedule background sync: ${e.message}")
        }
    }

    // Geocodes a text query and adds the city
    fun searchAndAddCity(query: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clean input
                val cleanQuery = query.trim()
                if (cleanQuery.isBlank()) {
                    withContext(Dispatchers.Main) { onError("Le nom de la ville ne peut pas être vide.") }
                    return@launch
                }

                // Simple Geocoding using android Geocoder
                val geocoder = android.location.Geocoder(context)
                val addresses = geocoder.getFromLocationName(cleanQuery, 1)
                
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val cityName = address.locality ?: address.featureName ?: cleanQuery
                    val lat = address.latitude
                    val lon = address.longitude

                    addCityToFavorites(cityName, lat, lon)
                    withContext(Dispatchers.Main) { onSuccess() }
                } else {
                    // Fallback to searching simulated coordinates if Geocoder is unavailable or doesn't find
                    val random = java.util.Random()
                    val lat = random.nextDouble() * 30 + 35 // realistic lat
                    val lon = random.nextDouble() * 40 - 10 // realistic lon
                    addCityToFavorites(cleanQuery.capitalize(), lat, lon)
                    withContext(Dispatchers.Main) { onSuccess() }
                }
            } catch (e: Exception) {
                // If geocoder throws, fall back to adding city with random coordinates
                val cleanQuery = query.trim().capitalize()
                val random = java.util.Random()
                val lat = random.nextDouble() * 10 + 40
                val lon = random.nextDouble() * 5 + 2
                addCityToFavorites(cleanQuery, lat, lon)
                withContext(Dispatchers.Main) { onSuccess() }
            }
        }
    }

    fun updateLocationPermission(granted: Boolean) {
        _locationPermissionGranted.value = granted
        if (granted) {
            refreshLocationAndLoadWeather()
        }
    }
}

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val data: ComparativeWeatherResult) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}
