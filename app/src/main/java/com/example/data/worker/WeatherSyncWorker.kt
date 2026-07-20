package com.example.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.api.ExternalWeatherService
import com.example.data.api.OpenMeteoService
import com.example.data.db.WeatherDatabase
import com.example.data.manager.ApiQuotaManager
import com.example.data.manager.LocationManager
import com.example.data.model.WeatherSource
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class WeatherSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "meteo_notifications"
        const val NOTIFICATION_ID_ALERTS = 101
        const val NOTIFICATION_ID_QUOTA = 102
    }

    override suspend fun doWork(): Result {
        Log.d("WeatherSyncWorker", "Background sync started...")
        
        try {
            // Build dependency instances manually since we are using simple constructor injection
            val okHttpClient = OkHttpClient.Builder().build()
            
            val openMeteoService = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/v1/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(OpenMeteoService::class.java)

            val externalService = Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(ExternalWeatherService::class.java)

            val database = WeatherDatabase.getDatabase(appContext)
            val weatherDao = database.weatherDao()
            val quotaManager = ApiQuotaManager(appContext)
            val locationManager = LocationManager(appContext)

            val repository = WeatherRepository(
                openMeteoService = openMeteoService,
                externalService = externalService,
                weatherDao = weatherDao,
                quotaManager = quotaManager
            )

            // 1. Get location coordinates
            val locationResult = locationManager.getCurrentLocation()
            
            // 2. Fetch comparative weather
            val result = repository.fetchComparativeWeather(
                latitude = locationResult.lat,
                longitude = locationResult.lon,
                cityName = locationResult.name
            )

            // 3. Look for severe alerts and post notification
            val prioritySource = quotaManager.appSettingsFlow.first().prioritySource
            val selectedWeather = result.sourcesData[prioritySource] ?: result.sourcesData.values.firstOrNull()
            
            if (selectedWeather != null && selectedWeather.alerts.isNotEmpty()) {
                val severeAlert = selectedWeather.alerts.firstOrNull { 
                    it.severity.equals("Severe", ignoreCase = true) || 
                    it.severity.equals("Extreme", ignoreCase = true) 
                } ?: selectedWeather.alerts.first()

                showNotification(
                    NOTIFICATION_ID_ALERTS,
                    "Alerte Météo: ${severeAlert.title}",
                    "${severeAlert.senderName}: ${severeAlert.description}"
                )
            }

            // 4. Also check if quota manager has any warning (already handled in Repository call increments,
            // but we can check if any quota is disabled)
            val settings = quotaManager.appSettingsFlow.first()
            settings.quotaCalls.forEach { entry ->
                val source = entry.key
                val calls = entry.value
                val limit = source.defaultDailyLimit
                val threshold90 = (limit * 0.9).toInt()
                if (calls >= limit) {
                    showNotification(
                        NOTIFICATION_ID_QUOTA,
                        "Quota API Épuisé: ${source.displayName}",
                        "La source ${source.displayName} a été désactivée pour aujourd'hui."
                    )
                } else if (calls >= threshold90) {
                    showNotification(
                        NOTIFICATION_ID_QUOTA,
                        "Avertissement Quota: ${source.displayName}",
                        "La source ${source.displayName} est proche de sa limite d'appels gratuits (${calls}/${limit})."
                    )
                }
            }

            Log.d("WeatherSyncWorker", "Background sync completed successfully.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("WeatherSyncWorker", "Background sync failed: ${e.message}")
            return Result.retry()
        }
    }

    private fun showNotification(id: Int, title: String, content: String) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notifications Météo",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alertes météorologiques et alertes de quotas de l'application"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }
}
