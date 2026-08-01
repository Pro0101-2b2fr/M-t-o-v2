package com.example.data.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.db.WeatherDatabase
import com.example.data.manager.ApiQuotaManager
import com.example.data.model.ComparativeWeatherResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = WeatherDatabase.getDatabase(context)
        val dao = database.weatherDao()
        val quotaManager = ApiQuotaManager(context)
        val appSettings = quotaManager.appSettingsFlow.firstOrNull()
        val unitTemp = appSettings?.unitTemp ?: "C"

        val recentCache = dao.getLatestCachedWeather()
        
        var cityName = recentCache?.cityName ?: "Météo"
        var tempStr = "--°C"
        var conditionText = "Synchronisation..."
        var lastUpdated = "Jamais"

        if (recentCache != null) {
            try {
                val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(ComparativeWeatherResult::class.java)
                val result = adapter.fromJson(recentCache.serializedComparison)
                if (result != null) {
                    cityName = result.cityName
                    val tempVal = result.averageWeather.temperature
                    tempStr = if (unitTemp == "F") {
                        "${"%.0f".format((tempVal * 9 / 5) + 32)}°F"
                    } else {
                        "${"%.0f".format(tempVal)}°C"
                    }
                    conditionText = result.averageWeather.conditionText
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    lastUpdated = sdf.format(Date(result.timestamp))
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        text = cityName,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = tempStr,
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = conditionText,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Mis à jour: $lastUpdated",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
