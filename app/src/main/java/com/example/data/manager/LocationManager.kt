package com.example.data.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.Locale

class LocationManager(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Default fallback city: Paris (France)
        const val DEFAULT_CITY_NAME = "Paris"
        const val DEFAULT_LAT = 48.8566
        const val DEFAULT_LON = 2.3522
    }

    // Check if location permissions are granted
    fun hasLocationPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Get current device coordinates. If fails, returns Paris coordinates.
    suspend fun getCurrentLocation(): LocationResult {
        if (!hasLocationPermissions()) {
            return LocationResult.Denied(DEFAULT_CITY_NAME, DEFAULT_LAT, DEFAULT_LON)
        }

        return try {
            // First attempt: get last known location
            var location = fusedLocationClient.lastLocation.await()

            // Second attempt: if last location is null, request current location
            if (location == null) {
                location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
            }

            if (location != null) {
                val cityName = reverseGeocode(location.latitude, location.longitude)
                LocationResult.Success(cityName, location.latitude, location.longitude)
            } else {
                LocationResult.Unavailable("Localisation GPS indisponible", DEFAULT_CITY_NAME, DEFAULT_LAT, DEFAULT_LON)
            }
        } catch (e: SecurityException) {
            LocationResult.Error("Permission manquante ou révoquée", DEFAULT_CITY_NAME, DEFAULT_LAT, DEFAULT_LON)
        } catch (e: Exception) {
            LocationResult.Unavailable("Erreur lors de la localisation : ${e.localizedMessage}", DEFAULT_CITY_NAME, DEFAULT_LAT, DEFAULT_LON)
        }
    }

    // Perform reverse-geocoding using Android Geocoder
    fun reverseGeocode(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Tiramisu+ has async geocoder. But to keep it simple, we do a synchronous call inside try block
                // (which runs on dispatcher thread anyway when called from a coroutine scope)
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.locality ?: address.subAdminArea ?: address.adminArea ?: "Position Actuelle"
                } else {
                    "Position Actuelle"
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.locality ?: address.subAdminArea ?: address.adminArea ?: "Position Actuelle"
                } else {
                    "Position Actuelle"
                }
            }
        } catch (e: IOException) {
            // Geocoder service not available or no internet
            "Position Actuelle"
        } catch (e: Exception) {
            "Position Actuelle"
        }
    }
}

sealed class LocationResult {
    data class Success(val cityName: String, val latitude: Double, val longitude: Double) : LocationResult()
    data class Denied(val fallbackCity: String, val latitude: Double, val longitude: Double) : LocationResult()
    data class Unavailable(val reason: String, val fallbackCity: String, val latitude: Double, val longitude: Double) : LocationResult()
    data class Error(val error: String, val fallbackCity: String, val latitude: Double, val longitude: Double) : LocationResult()

    val lat: Double
        get() = when (this) {
            is Success -> latitude
            is Denied -> latitude
            is Unavailable -> latitude
            is Error -> latitude
        }

    val lon: Double
        get() = when (this) {
            is Success -> longitude
            is Denied -> longitude
            is Unavailable -> longitude
            is Error -> longitude
        }

    val name: String
        get() = when (this) {
            is Success -> cityName
            is Denied -> fallbackCity
            is Unavailable -> fallbackCity
            is Error -> fallbackCity
        }
}
