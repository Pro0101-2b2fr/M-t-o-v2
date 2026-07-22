package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.manager.AppSettings
import com.example.data.model.ComparativeWeatherResult
import com.example.data.model.ForecastDay
import com.example.data.model.ForecastHour
import com.example.data.model.UnifiedWeather
import com.example.data.model.WeatherSource
import com.example.ui.theme.*
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: WeatherViewModel,
    onNavigateToCities: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.weatherState.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val selectedCity by viewModel.selectedCity.collectAsState()

    val backgroundGradient = getBackgroundGradient(uiState, settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigateToCities() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Position",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedCity?.name ?: "Chargement...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (selectedCity?.isCurrentLocation == true) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "GPS actif",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Changer de ville",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadWeather() },
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualiser",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Réglages",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(brush = backgroundGradient)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is WeatherUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("loading_indicator")
                    )
                }
                is WeatherUiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.loadWeather() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is WeatherUiState.Success -> {
                    DashboardContent(
                        result = state.data,
                        settings = settings,
                        onHideWidget = { widgetKey ->
                            val updated = settings.visibleWidgets.toMutableSet().apply { remove(widgetKey) }
                            viewModel.updateVisibleWidgets(updated)
                        },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    result: ComparativeWeatherResult,
    settings: AppSettings,
    onHideWidget: (String) -> Unit,
    viewModel: WeatherViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_scroll_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Offline Cache Badge Indicator
        if (result.isOffline) {
            item {
                OfflineBadgeView(timestamp = result.timestamp, onRetry = { viewModel.loadWeather() })
            }
        }

        // Global Consensus Temperature Card
        item {
            MainConsensusCard(result = result, settings = settings)
        }

        // Global Reliability diagnostics
        item {
            ReliabilityDiagnosticCard(result = result)
        }

        // Side-by-Side Comparison Grid
        item {
            ComparativeGridCard(result = result, settings = settings)
        }

        // Hourly forecast
        item {
            HourlyForecastCard(result = result, settings = settings)
        }

        // Daily forecast
        item {
            DailyForecastCard(result = result, settings = settings)
        }

        // Modulable extra cards based on user settings
        if (settings.visibleWidgets.contains("aqi")) {
            item {
                ExtraAqiCard(result = result, onHide = { onHideWidget("aqi") })
            }
        }

        if (settings.visibleWidgets.contains("uv")) {
            item {
                ExtraUvCard(result = result, onHide = { onHideWidget("uv") })
            }
        }

        if (settings.visibleWidgets.contains("wind")) {
            item {
                ExtraWindCard(result = result, settings = settings, onHide = { onHideWidget("wind") })
            }
        }

        if (settings.visibleWidgets.contains("alerts")) {
            val alerts = result.sourcesData[settings.prioritySource]?.alerts 
                ?: result.sourcesData.values.flatMap { it.alerts }
            
            if (alerts.isNotEmpty()) {
                item {
                    AlertsWidgetCard(alerts = alerts, onHide = { onHideWidget("alerts") })
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ------------------- UI Component Views -------------------

@Composable
fun OfflineBadgeView(timestamp: Long, onRetry: () -> Unit) {
    val dateText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Hors ligne",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Mode hors-ligne",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Dernière mise à jour : $dateText",
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
            TextButton(onClick = onRetry) {
                Text(text = "Réessayer", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun MainConsensusCard(result: ComparativeWeatherResult, settings: AppSettings) {
    val avg = result.averageWeather
    val tempText = formatTemperature(avg.temperature, settings.unitTemp)
    val feelsLikeText = formatTemperature(avg.feelsLike, settings.unitTemp)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Weather icon
            Icon(
                imageVector = getWeatherIconVector(avg.conditionIcon),
                contentDescription = avg.conditionText,
                tint = getWeatherIconColor(avg.conditionIcon),
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Temperature consensus
            Text(
                text = tempText,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("consensus_temp")
            )

            // Weather status text
            Text(
                text = avg.conditionText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Feels like
            Text(
                text = "Ressenti : $feelsLikeText",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Divider
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickInfoItem(
                    icon = Icons.Outlined.WaterDrop,
                    label = "Humidité",
                    value = "${avg.humidity}%"
                )
                QuickInfoItem(
                    icon = Icons.Outlined.Air,
                    label = "Vent",
                    value = formatWindSpeed(avg.windSpeed, settings.unitWind)
                )
                QuickInfoItem(
                    icon = Icons.Outlined.Compress,
                    label = "Pression",
                    value = formatPressure(avg.pressure, settings.unitPressure)
                )
            }
        }
    }
}

@Composable
fun QuickInfoItem(icon: ImageVector, label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
fun ReliabilityDiagnosticCard(result: ComparativeWeatherResult) {
    // Determine reliability based on maximum temp deviation of active sources
    val maxDev = result.sourceDeviations.values.maxOfOrNull { abs(it.temperatureDeviation) } ?: 0f
    
    val (status, description, color) = when {
        maxDev <= 1.0f -> Triple("Fiabilité Excellente", "Toutes les sources s'accordent presque parfaitement.", ReliabilityHighColor)
        maxDev <= 2.2f -> Triple("Fiabilité Moyenne", "Légères divergences sur les prévisions.", ReliabilityMediumColor)
        else -> Triple("Fiabilité Faible", "Divergence importante constatée entre les sources.", ReliabilityLowColor)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color = color, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$status • Écart max: ${"%.1f".format(maxDev)}°C",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun ComparativeGridCard(result: ComparativeWeatherResult, settings: AppSettings) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comparatif Côte-à-Côte",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${result.sourcesData.size} sources en ligne",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable list of sources side by side
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Show successful ones
                items(result.sourcesData.keys.toList()) { source ->
                    val data = result.sourcesData[source]!!
                    val deviation = result.sourceDeviations[source]
                    val isUnreliable = deviation?.isTemperatureUnreliable == true

                    val cardBorderColor = if (isUnreliable) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    }

                    val cardBgColor = if (isUnreliable) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }

                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .background(cardBgColor, RoundedCornerShape(16.dp))
                            .border(
                                width = if (isUnreliable) 2.dp else 1.dp,
                                color = cardBorderColor,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = getWeatherIconVector(data.current.conditionIcon),
                                contentDescription = null,
                                tint = getWeatherIconColor(data.current.conditionIcon),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = source.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Temp & Deviation
                        Text(
                            text = formatTemperature(data.current.temperature, settings.unitTemp),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val devValue = deviation?.temperatureDeviation ?: 0f
                        val devSign = if (devValue >= 0) "+" else ""
                        val devColor = if (isUnreliable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        
                        Text(
                            text = "Écart: $devSign${"%.1f".format(devValue)}°C",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = devColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Secondary comparison attributes
                        SourceDetailRow(label = "Ressenti", value = formatTemperature(data.current.feelsLike, settings.unitTemp))
                        SourceDetailRow(label = "Humidité", value = "${data.current.humidity}%")
                        SourceDetailRow(label = "Vent", value = formatWindSpeed(data.current.windSpeed, settings.unitWind))
                        SourceDetailRow(label = "Pression", value = formatPressure(data.current.pressure, settings.unitPressure))
                    }
                }

                // Show failed/disabled ones at the end
                items(result.sourceErrors.keys.toList()) { source ->
                    val errorMsg = result.sourceErrors[source] ?: ""
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface
                                    .copy(alpha = 0.03f), RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Indisponible",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = source.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMsg,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourceDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(text = value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun HourlyForecastCard(result: ComparativeWeatherResult, settings: AppSettings) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Prévisions Horaires (Moyenne)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(result.averageHourly) { hour ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = hour.time,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Icon(
                            imageVector = getWeatherIconVector(hour.conditionIcon),
                            contentDescription = null,
                            tint = getWeatherIconColor(hour.conditionIcon),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatTemperature(hour.temp, settings.unitTemp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (hour.precipitationProb > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "${hour.precipitationProb}%",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp)) // Maintain alignment height
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyForecastCard(result: ComparativeWeatherResult, settings: AppSettings) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Prévisions sur 7 jours (Moyenne)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            result.averageDaily.forEach { day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = day.date,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(100.dp)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = getWeatherIconVector(day.conditionIcon),
                            contentDescription = day.conditionText,
                            tint = getWeatherIconColor(day.conditionIcon),
                            modifier = Modifier.size(24.dp)
                        )
                        if (day.precipitationProb > 15) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${day.precipitationProb}%",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row {
                        Text(
                            text = formatTemperature(day.minTemp, settings.unitTemp),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatTemperature(day.maxTemp, settings.unitTemp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            }
        }
    }
}

@Composable
fun ExtraAqiCard(result: ComparativeWeatherResult, onHide: () -> Unit) {
    val aqiVal = result.averageWeather.aqi
    val (aqiLabel, aqiDesc, aqiColor) = when (aqiVal) {
        1 -> Triple("Excellent", "Qualité de l'air idéale pour les activités extérieures.", ReliabilityHighColor)
        2 -> Triple("Bon", "Qualité satisfaisante, peu ou pas de risques.", ReliabilityHighColor)
        3 -> Triple("Modéré", "Les personnes sensibles devraient limiter les efforts intenses.", ReliabilityMediumColor)
        4 -> Triple("Mauvais", "Qualité de l'air dégradée, réduction des sorties recommandée.", ReliabilityLowColor)
        else -> Triple("Très Mauvais", "Alerte santé! Évitez toute activité prolongée en plein air.", ReliabilityLowColor)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = aqiColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Qualité de l'air (AQI)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onHide, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Masquer",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$aqiVal",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = aqiColor
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = aqiLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = aqiDesc,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ExtraUvCard(result: ComparativeWeatherResult, onHide: () -> Unit) {
    val uv = result.averageWeather.uvIndex
    val (uvLabel, uvDesc, uvColor) = when {
        uv <= 2f -> Triple("Faible", "Pas de protection nécessaire.", ReliabilityHighColor)
        uv <= 5f -> Triple("Modéré", "Crème solaire recommandée.", ReliabilityMediumColor)
        uv <= 7f -> Triple("Élevé", "Protection solaire indispensable.", AmberPrimary)
        else -> Triple("Très Élevé", "Évitez l'exposition directe en milieu de journée.", ReliabilityLowColor)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = uvColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Indice UV",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onHide, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Masquer",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${"%.1f".format(uv)}",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = uvColor
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = uvLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uvDesc,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun ExtraWindCard(result: ComparativeWeatherResult, settings: AppSettings, onHide: () -> Unit) {
    val avg = result.averageWeather
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Soleil & Vent",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onHide, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Masquer",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Lever de soleil",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WbSunny,
                            contentDescription = null,
                            tint = WeatherSunnyColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = avg.sunrise,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Coucher de soleil",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WbTwilight,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = avg.sunset,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Direction du vent",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "${avg.windDirection.toInt()}°",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vitesse",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatWindSpeed(avg.windSpeed, settings.unitWind),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AlertsWidgetCard(alerts: List<com.example.data.model.WeatherAlert>, onHide: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alertes météo",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Alertes Météo Officielles",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                IconButton(onClick = onHide, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Masquer",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            alerts.forEach { alert ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = alert.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "${alert.senderName} (${alert.severity})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = alert.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
fun ErrorStateView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Erreur",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Une erreur est survenue",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(text = "Réessayer")
            }
        }
    }
}

// ------------------- Utility Formatters -------------------

fun formatTemperature(temp: Float, unit: String): String {
    return if (unit == "F") {
        "${"%.0f".format((temp * 9 / 5) + 32)}°F"
    } else {
        "${"%.0f".format(temp)}°C"
    }
}

fun formatWindSpeed(speed: Float, unit: String): String {
    return when (unit) {
        "mph" -> "${"%.1f".format(speed * 0.621371f)} mph"
        "knots" -> "${"%.1f".format(speed * 0.539957f)} kt"
        else -> "${"%.0f".format(speed)} km/h"
    }
}

fun formatPressure(pressure: Float, unit: String): String {
    return if (unit == "mmhg") {
        "${"%.0f".format(pressure * 0.750062f)} mmHg"
    } else {
        "${"%.0f".format(pressure)} hPa"
    }
}

// Choose an icon based on name
fun getWeatherIconVector(iconName: String): ImageVector {
    return when (iconName) {
        "sunny" -> Icons.Default.WbSunny
        "cloudy" -> Icons.Default.Cloud
        "rainy" -> Icons.Default.WaterDrop
        "snowy" -> Icons.Default.AcUnit
        "thunderstorm" -> Icons.Default.FlashOn
        else -> Icons.Default.Cloud
    }
}

fun getWeatherIconColor(iconName: String): Color {
    return when (iconName) {
        "sunny" -> WeatherSunnyColor
        "cloudy" -> WeatherCloudyColor
        "rainy" -> WeatherRainyColor
        "snowy" -> WeatherSnowyColor
        "thunderstorm" -> WeatherThunderstormColor
        else -> WeatherCloudyColor
    }
}

@Composable
fun getBackgroundGradient(uiState: WeatherUiState, settings: AppSettings): Brush {
    val darkTheme = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val (color1, color2) = if (darkTheme) {
        // High-contrast deep elegant dark themes for maximum readability
        val deepNightBlue = Color(0xFF090D1A)
        val spaceBlack = Color(0xFF03050A)
        
        val darkStormBlue = Color(0xFF0E1422)
        val darkAbyss = Color(0xFF05080E)

        val twilightAmber = Color(0xFF1D130A)
        val shadowDusk = Color(0xFF0A0704)

        when (uiState) {
            is WeatherUiState.Success -> {
                when (uiState.data.averageWeather.conditionIcon) {
                    "sunny" -> twilightAmber to shadowDusk
                    "rainy", "thunderstorm" -> darkStormBlue to darkAbyss
                    else -> deepNightBlue to spaceBlack
                }
            }
            else -> deepNightBlue to spaceBlack
        }
    } else {
        // High-contrast, fresh light theme gradients
        val lightBlue = Color(0xFFD9F4F6)
        val darkBlue = Color(0xFF70CFDC)
        
        val graySky = Color(0xFFE5E9EC)
        val stormSky = Color(0xFFB0BEC5)

        val sunsetAmber = Color(0xFFFFF1D6)
        val orangeSky = Color(0xFFFFCC80)

        when (uiState) {
            is WeatherUiState.Success -> {
                when (uiState.data.averageWeather.conditionIcon) {
                    "sunny" -> sunsetAmber to orangeSky
                    "rainy", "thunderstorm" -> stormSky to graySky
                    else -> lightBlue to darkBlue
                }
            }
            else -> lightBlue to darkBlue
        }
    }

    return Brush.verticalGradient(
        colors = listOf(
            animateColorAsState(targetValue = color1).value,
            animateColorAsState(targetValue = color2).value
        )
    )
}
