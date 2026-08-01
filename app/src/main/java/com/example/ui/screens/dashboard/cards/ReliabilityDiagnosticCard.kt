package com.example.ui.screens.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ComparativeWeatherResult
import com.example.ui.theme.ReliabilityHighColor
import com.example.ui.theme.ReliabilityLowColor
import com.example.ui.theme.ReliabilityMediumColor
import kotlin.math.abs

@Composable
fun ReliabilityDiagnosticCard(result: ComparativeWeatherResult) {
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
