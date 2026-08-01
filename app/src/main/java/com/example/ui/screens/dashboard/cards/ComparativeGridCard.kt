package com.example.ui.screens.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.manager.AppSettings
import com.example.data.model.ComparativeWeatherResult
import com.example.ui.screens.dashboard.utils.formatPressure
import com.example.ui.screens.dashboard.utils.formatTemperature
import com.example.ui.screens.dashboard.utils.formatWindSpeed
import com.example.ui.screens.dashboard.utils.getWeatherIconColor
import com.example.ui.screens.dashboard.utils.getWeatherIconVector

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

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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

                        SourceDetailRow(label = "Ressenti", value = formatTemperature(data.current.feelsLike, settings.unitTemp))
                        SourceDetailRow(label = "Humidité", value = "${data.current.humidity}%")
                        SourceDetailRow(label = "Vent", value = formatWindSpeed(data.current.windSpeed, settings.unitWind))
                        SourceDetailRow(label = "Pression", value = formatPressure(data.current.pressure, settings.unitPressure))
                    }
                }

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
