package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.manager.AppSettings
import com.example.data.model.WeatherSource
import com.example.ui.theme.*
import com.example.ui.viewmodel.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.appSettings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages & Personnalisation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("settings_scroll_column"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Mode & Color
            item {
                ThemeSettingsSection(
                    settings = settings,
                    onUpdateMode = { viewModel.updateThemeMode(it) },
                    onUpdateColor = { viewModel.updateThemeColor(it) }
                )
            }

            // Unit conversions preference selector
            item {
                UnitSettingsSection(
                    settings = settings,
                    onUpdateTemp = { viewModel.updateUnitTemp(it) },
                    onUpdateWind = { viewModel.updateUnitWind(it) },
                    onUpdatePressure = { viewModel.updateUnitPressure(it) }
                )
            }

            // Background sync refresh frequency configuration
            item {
                RefreshIntervalSection(
                    settings = settings,
                    onUpdateInterval = { viewModel.updateRefreshInterval(it) }
                )
            }

            // Weather alerts source priority selector
            item {
                PrioritySourceSection(
                    settings = settings,
                    onUpdatePriority = { viewModel.updatePrioritySource(it) }
                )
            }

            // APIs multi-sources toggles & Keys Configuration
            item {
                MultiSourcesApiSection(
                    settings = settings,
                    onToggleSource = { src, enabled -> viewModel.toggleSource(src, enabled) },
                    onSaveApiKey = { src, key -> viewModel.saveApiKey(src, key) },
                    onResetQuotas = { viewModel.resetQuotas() }
                )
            }

            // Dashboard active widgets list config
            item {
                DashboardWidgetsSection(
                    settings = settings,
                    onUpdateWidgets = { viewModel.updateVisibleWidgets(it) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ------------------- Custom Sections Components -------------------

@Composable
fun ThemeSettingsSection(
    settings: AppSettings,
    onUpdateMode: (String) -> Unit,
    onUpdateColor: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Apparence & Thème", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            // Dark/Light/Auto choice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mode de thème", fontSize = 14.sp)
                val modes = listOf("auto" to "Auto", "light" to "Clair", "dark" to "Sombre")
                SingleChoiceSegmentedButtonRow {
                    modes.forEachIndexed { idx, (mode, label) ->
                        val selected = settings.themeMode == mode
                        Button(
                            onClick = { onUpdateMode(mode) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))

            // Theme Palette selector
            Text("Palette de couleurs", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ColorSelectorItem(name = "sky", label = "Bleu", baseColor = SkyPrimary, currentSelected = settings.themeColor, onClick = onUpdateColor)
                ColorSelectorItem(name = "emerald", label = "Vert", baseColor = EmeraldPrimary, currentSelected = settings.themeColor, onClick = onUpdateColor)
                ColorSelectorItem(name = "amber", label = "Orange", baseColor = AmberPrimary, currentSelected = settings.themeColor, onClick = onUpdateColor)
                ColorSelectorItem(name = "lavender", label = "Violet", baseColor = LavenderPrimary, currentSelected = settings.themeColor, onClick = onUpdateColor)
            }
        }
    }
}

@Composable
fun ColorSelectorItem(
    name: String,
    label: String,
    baseColor: Color,
    currentSelected: String,
    onClick: (String) -> Unit
) {
    val isSelected = currentSelected == name
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick(name) }
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(baseColor)
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun UnitSettingsSection(
    settings: AppSettings,
    onUpdateTemp: (String) -> Unit,
    onUpdateWind: (String) -> Unit,
    onUpdatePressure: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Unités de Mesure", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))

            // Temp
            UnitSelectorRow(
                label = "Température",
                options = listOf("C" to "°C", "F" to "°F"),
                selected = settings.unitTemp,
                onSelected = onUpdateTemp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Wind
            UnitSelectorRow(
                label = "Vitesse du Vent",
                options = listOf("kmh" to "km/h", "mph" to "mph", "knots" to "Nœuds"),
                selected = settings.unitWind,
                onSelected = onUpdateWind
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pressure
            UnitSelectorRow(
                label = "Pression",
                options = listOf("hpa" to "hPa", "mmhg" to "mmHg"),
                selected = settings.unitPressure,
                onSelected = onUpdatePressure
            )
        }
    }
}

@Composable
fun UnitSelectorRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Row(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { (value, title) ->
                val isSel = selected == value
                Box(
                    modifier = Modifier
                        .clickable { onSelected(value) }
                        .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun RefreshIntervalSection(
    settings: AppSettings,
    onUpdateInterval: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Fréquence de Mise à Jour", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("WorkManager synchronise les données en arrière-plan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            val options = listOf(
                1 to "1h",
                3 to "3h",
                6 to "6h",
                0 to "Manuel"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Intervalle", fontSize = 14.sp)
                Row(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    options.forEach { (hours, label) ->
                        val isSel = settings.refreshIntervalHours == hours
                        Box(
                            modifier = Modifier
                                .clickable { onUpdateInterval(hours) }
                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrioritySourceSection(
    settings: AppSettings,
    onUpdatePriority: (WeatherSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Source de Météo Prioritaire", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Source de référence pour les alertes météo et notifications.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = settings.prioritySource.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Changer")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    WeatherSource.values().forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.displayName) },
                            onClick = {
                                onUpdatePriority(source)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MultiSourcesApiSection(
    settings: AppSettings,
    onToggleSource: (WeatherSource, Boolean) -> Unit,
    onSaveApiKey: (WeatherSource, String) -> Unit,
    onResetQuotas: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Gestion des Sources & Quotas API", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onResetQuotas) {
                    Text("Réinitialiser Quotas", fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Toutes les requêtes sont limitées à un quota quotidien gratuit.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            WeatherSource.values().forEach { source ->
                ApiSourceConfigRow(
                    source = source,
                    settings = settings,
                    onToggle = { onToggleSource(source, it) },
                    onSaveKey = { onSaveApiKey(source, it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSourceConfigRow(
    source: WeatherSource,
    settings: AppSettings,
    onToggle: (Boolean) -> Unit,
    onSaveKey: (String) -> Unit
) {
    val isEnabled = settings.enabledSources[source] ?: (source == WeatherSource.OPEN_METEO)
    val quotaCalls = settings.quotaCalls[source] ?: 0
    val quotaLimit = source.defaultDailyLimit
    val isQuotaDisabled = settings.quotaDisabled[source] ?: false

    val keyState = remember { mutableStateOf(settings.apiKeys[source] ?: "") }
    var keyEditorVisible by remember { mutableStateOf(false) }

    LaunchedEffect(settings.apiKeys[source]) {
        keyState.value = settings.apiKeys[source] ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Source header + toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isQuotaDisabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (isQuotaDisabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("Quota Épuisé", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = "Appels : $quotaCalls / $quotaLimit par jour",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.requiresKey) {
                    IconButton(onClick = { keyEditorVisible = !keyEditorVisible }) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Clé API",
                            tint = if (settings.apiKeys[source].isNullOrBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    enabled = !isQuotaDisabled,
                    modifier = Modifier.scale(0.85f)
                )
            }
        }

        // Expanded key editor
        AnimatedVisibility(visible = keyEditorVisible && source.requiresKey) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                OutlinedTextField(
                    value = keyState.value,
                    onValueChange = { keyState.value = it },
                    label = { Text("Clé API ${source.displayName}") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { onSaveKey(keyState.value) }) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Enregistrer")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "L'application effectue des requêtes réelles si la clé est fournie.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// Extension to scale elements easily
fun Modifier.scale(scale: Float): Modifier = this.sizeIn(
    maxWidth = (48 * scale).dp,
    maxHeight = (24 * scale).dp
)

@Composable
fun DashboardWidgetsSection(
    settings: AppSettings,
    onUpdateWidgets: (Set<String>) -> Unit
) {
    val widgetNames = mapOf(
        "aqi" to "Qualité de l'Air (AQI)",
        "uv" to "Indice de rayonnement UV",
        "wind" to "Direction & Vitesse du Vent",
        "alerts" to "Alertes Météo Officielles"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Disposition de l'Interface", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Masquez ou affichez les widgets sur l'écran d'accueil.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            widgetNames.forEach { (key, title) ->
                val isVisible = settings.visibleWidgets.contains(key)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, fontSize = 13.sp)
                    Checkbox(
                        checked = isVisible,
                        onCheckedChange = { checked ->
                            val updated = settings.visibleWidgets.toMutableSet().apply {
                                if (checked) add(key) else remove(key)
                            }
                            onUpdateWidgets(updated)
                        }
                    )
                }
            }
        }
    }
}
