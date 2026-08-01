package com.example.ui.screens.dashboard.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
            Icon(
                imageVector = getWeatherIconVector(avg.conditionIcon),
                contentDescription = avg.conditionText,
                tint = getWeatherIconColor(avg.conditionIcon),
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = tempText,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("consensus_temp")
            )

            Text(
                text = avg.conditionText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Ressenti : $feelsLikeText",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Spacer(modifier = Modifier.height(16.dp))

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
