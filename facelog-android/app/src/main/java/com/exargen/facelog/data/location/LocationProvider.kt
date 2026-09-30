package com.exargen.facelog.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val locationName: String? = null,
)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "LocationProvider"
    }

    suspend fun getLastLocation(): LocationInfo? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Location permission not granted")
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var bestLocation: Location? = null

        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) {
                    if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                        bestLocation = location
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to get location from $provider: ${e.message}")
            }
        }

        val location = bestLocation ?: return null
        val locationName = reverseGeocode(location.latitude, location.longitude)
        return LocationInfo(
            latitude = location.latitude,
            longitude = location.longitude,
            locationName = locationName,
        )
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Async API on Android 33+
                suspendCoroutine { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        cont.resume(addresses.firstOrNull()?.let { formatAddress(it) })
                    }
                }
            } else {
                // Sync API on older versions — already on background dispatcher
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    addresses?.firstOrNull()?.let { formatAddress(it) }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Reverse geocoding failed: ${e.message}")
            null
        }
    }

    private fun formatAddress(address: android.location.Address): String? {
        val parts = listOfNotNull(
            address.subLocality,
            address.locality,
            address.adminArea,
        )
        return parts.joinToString(", ").ifEmpty { null }
    }
}
