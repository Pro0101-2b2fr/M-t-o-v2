package com.example.data.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.WeatherSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meteo_settings")

class ApiQuotaManager(private val context: Context) {

    companion object {
        // Quota Keys
        private val KEY_QUOTA_DATE = stringPreferencesKey("quota_date")
        
        // Settings Keys
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // "auto", "light", "dark"
        val KEY_THEME_COLOR = stringPreferencesKey("theme_color") // "sky", "emerald", "amber", "lavender"
        val KEY_UNIT_TEMP = stringPreferencesKey("unit_temp") // "C", "F"
        val KEY_UNIT_WIND = stringPreferencesKey("unit_wind") // "kmh", "mph", "knots"
        val KEY_UNIT_PRESSURE = stringPreferencesKey("unit_pressure") // "hpa", "mmhg"
        val KEY_REFRESH_INTERVAL = intPreferencesKey("refresh_interval") // 1, 3, 6, 0 (manual)
        val KEY_PRIORITY_SOURCE = stringPreferencesKey("priority_source") // "OPEN_METEO" etc.
        val KEY_VISIBLE_WIDGETS = stringSetPreferencesKey("visible_widgets") // "aqi", "uv", "wind", "alerts", "details"
        
        // API Keys Preferences
        fun getApiKeyPreference(source: WeatherSource) = stringPreferencesKey("api_key_${source.name}")
        
        // Enabled Status Preferences (user explicitly toggles)
        fun getSourceEnabledPreference(source: WeatherSource) = booleanPreferencesKey("source_enabled_${source.name}")
        
        // Disabled by Quota Preferences
        fun getSourceQuotaDisabledPreference(source: WeatherSource) = booleanPreferencesKey("source_quota_disabled_${source.name}")
        
        // Call Count Preferences
        fun getSourceCallCountPreference(source: WeatherSource) = intPreferencesKey("source_calls_${source.name}")
    }

    // Get current date string (e.g., "2026-07-20")
    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // Flow representing all quotas and settings
    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val savedDate = prefs[KEY_QUOTA_DATE] ?: ""
        val today = getCurrentDateString()
        
        // Check if day changed to reset quotas
        val isNewDay = savedDate != today

        val quotaCalls = WeatherSource.values().associateWith { source ->
            if (isNewDay) 0 else prefs[getSourceCallCountPreference(source)] ?: 0
        }

        val quotaDisabled = WeatherSource.values().associateWith { source ->
            if (isNewDay) false else prefs[getSourceQuotaDisabledPreference(source)] ?: false
        }

        val enabledSources = WeatherSource.values().associateWith { source ->
            prefs[getSourceEnabledPreference(source)] ?: (source == WeatherSource.OPEN_METEO)
        }

        val apiKeys = WeatherSource.values().associateWith { source ->
            prefs[getApiKeyPreference(source)] ?: ""
        }

        AppSettings(
            themeMode = prefs[KEY_THEME_MODE] ?: "auto",
            themeColor = prefs[KEY_THEME_COLOR] ?: "sky",
            unitTemp = prefs[KEY_UNIT_TEMP] ?: "C",
            unitWind = prefs[KEY_UNIT_WIND] ?: "kmh",
            unitPressure = prefs[KEY_UNIT_PRESSURE] ?: "hpa",
            refreshIntervalHours = prefs[KEY_REFRESH_INTERVAL] ?: 3,
            prioritySource = WeatherSource.valueOf(prefs[KEY_PRIORITY_SOURCE] ?: WeatherSource.OPEN_METEO.name),
            visibleWidgets = prefs[KEY_VISIBLE_WIDGETS] ?: setOf("aqi", "uv", "wind", "alerts", "details"),
            quotaCalls = quotaCalls,
            quotaDisabled = quotaDisabled,
            enabledSources = enabledSources,
            apiKeys = apiKeys
        )
    }

    // Records an API call, increments quota, alerts/disables if limits are crossed
    suspend fun recordCall(source: WeatherSource): QuotaStatus {
        if (!source.requiresKey && source != WeatherSource.OPEN_METEO) {
            return QuotaStatus.OK
        }
        val today = getCurrentDateString()
        var status = QuotaStatus.OK

        context.dataStore.edit { prefs ->
            val savedDate = prefs[KEY_QUOTA_DATE] ?: ""
            val isNewDay = savedDate != today
            
            if (isNewDay) {
                prefs[KEY_QUOTA_DATE] = today
                // Reset all counts
                WeatherSource.values().forEach { src ->
                    prefs[getSourceCallCountPreference(src)] = 0
                    prefs[getSourceQuotaDisabledPreference(src)] = false
                }
            }

            val currentCalls = (if (isNewDay) 0 else prefs[getSourceCallCountPreference(source)] ?: 0) + 1
            prefs[getSourceCallCountPreference(source)] = currentCalls

            val limit = source.defaultDailyLimit
            val threshold90 = (limit * 0.9).toInt()

            if (currentCalls >= limit) {
                prefs[getSourceQuotaDisabledPreference(source)] = true
                status = QuotaStatus.EXCEEDED_AND_DISABLED
            } else if (currentCalls >= threshold90) {
                status = QuotaStatus.WARNING_NEAR_LIMIT
            }
        }
        return status
    }

    // Set configuration helper methods
    suspend fun saveApiKey(source: WeatherSource, key: String) {
        context.dataStore.edit { prefs ->
            prefs[getApiKeyPreference(source)] = key
        }
    }

    suspend fun toggleSourceEnabled(source: WeatherSource, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[getSourceEnabledPreference(source)] = enabled
        }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun updateThemeColor(color: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_COLOR] = color
        }
    }

    suspend fun updateUnitTemp(unit: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNIT_TEMP] = unit
        }
    }

    suspend fun updateUnitWind(unit: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNIT_WIND] = unit
        }
    }

    suspend fun updateUnitPressure(unit: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_UNIT_PRESSURE] = unit
        }
    }

    suspend fun updateRefreshInterval(hours: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REFRESH_INTERVAL] = hours
        }
    }

    suspend fun updatePrioritySource(source: WeatherSource) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRIORITY_SOURCE] = source.name
        }
    }

    suspend fun updateVisibleWidgets(widgets: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VISIBLE_WIDGETS] = widgets
        }
    }

    suspend fun resetQuotas() {
        context.dataStore.edit { prefs ->
            prefs[KEY_QUOTA_DATE] = getCurrentDateString()
            WeatherSource.values().forEach { src ->
                prefs[getSourceCallCountPreference(src)] = 0
                prefs[getSourceQuotaDisabledPreference(src)] = false
            }
        }
    }
}

enum class QuotaStatus {
    OK,
    WARNING_NEAR_LIMIT,
    EXCEEDED_AND_DISABLED
}

data class AppSettings(
    val themeMode: String,
    val themeColor: String,
    val unitTemp: String,
    val unitWind: String,
    val unitPressure: String,
    val refreshIntervalHours: Int,
    val prioritySource: WeatherSource,
    val visibleWidgets: Set<String>,
    val quotaCalls: Map<WeatherSource, Int>,
    val quotaDisabled: Map<WeatherSource, Boolean>,
    val enabledSources: Map<WeatherSource, Boolean>,
    val apiKeys: Map<WeatherSource, String>
) {
    fun isSourceActive(source: WeatherSource): Boolean {
        val userEnabled = enabledSources[source] ?: (source == WeatherSource.OPEN_METEO)
        val hasKey = !source.requiresKey || !apiKeys[source].isNullOrBlank()
        val quotaExceeded = quotaDisabled[source] ?: false
        return userEnabled && hasKey && !quotaExceeded
    }
}
