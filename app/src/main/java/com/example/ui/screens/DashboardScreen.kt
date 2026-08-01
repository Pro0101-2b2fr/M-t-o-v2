package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.manager.AppSettings
import com.example.data.model.ComparativeWeatherResult
import com.example.ui.screens.dashboard.cards.*
import com.example.ui.screens.dashboard.components.ErrorStateView
import com.example.ui.screens.dashboard.components.OfflineBadgeView
import com.example.ui.screens.dashboard.utils.getBackgroundGradient
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel

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
        if (result.isOffline) {
            item {
                OfflineBadgeView(timestamp = result.timestamp, onRetry = { viewModel.loadWeather() })
            }
        }

        item {
            MainConsensusCard(result = result, settings = settings)
        }

        item {
            ReliabilityDiagnosticCard(result = result)
        }

        item {
            ComparativeGridCard(result = result, settings = settings)
        }

        item {
            HourlyForecastCard(result = result, settings = settings)
        }

        item {
            DailyForecastCard(result = result, settings = settings)
        }

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
