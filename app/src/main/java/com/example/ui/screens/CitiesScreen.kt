package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitiesScreen(
    viewModel: WeatherViewModel,
    onNavigateBack: () -> Unit
) {
    val favoriteCities by viewModel.favoriteCities.collectAsState()
    val selectedCity by viewModel.selectedCity.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    // Location Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineGranted || coarseGranted) {
            viewModel.updateLocationPermission(true)
            Toast.makeText(context, "Localisation GPS activée!", Toast.LENGTH_SHORT).show()
            onNavigateBack()
        } else {
            Toast.makeText(context, "Permission refusée. Repli sur la ville par défaut.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestion des Villes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Use GPS Location Button
            Button(
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("use_gps_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Utiliser ma position actuelle (GPS)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Add City Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Ajouter une ville (ex: Nice)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("city_search_input"),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        isSearching = true
                        viewModel.searchAndAddCity(
                            query = searchQuery,
                            onSuccess = {
                                isSearching = false
                                searchQuery = ""
                                Toast.makeText(context, "Ville ajoutée !", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            },
                            onError = { error ->
                                isSearching = false
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("add_city_button"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = searchQuery.isNotBlank() && !isSearching
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Ajouter")
                    }
                }
            }

            Text(
                text = "Villes Favorites",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Scrollable list of favorites
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("favorites_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favoriteCities) { city ->
                    val isSelected = selectedCity?.name == city.name
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectCity(city)
                                onNavigateBack()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (city.isCurrentLocation) Icons.Default.MyLocation else Icons.Default.LocationCity,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = city.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Lat: ${"%.2f".format(city.latitude)} • Lon: ${"%.2f".format(city.longitude)}",
                                        fontSize = 11.sp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        }
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.removeCityFromFavorites(city) },
                                modifier = Modifier.testTag("delete_city_${city.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Supprimer de la liste",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
