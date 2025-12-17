package com.airgradient.android.data.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float
)

sealed class LocationServiceResult {
    data class Success(val location: UserLocation) : LocationServiceResult()
    data class Error(val message: String) : LocationServiceResult()
    object PermissionDenied : LocationServiceResult()
    object LocationDisabled : LocationServiceResult()
}

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCurrentLocation(): Flow<LocationServiceResult> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(LocationServiceResult.PermissionDenied)
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L // 10 seconds
        ).apply {
            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            setWaitForAccurateLocation(false)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val userLocation = UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy
                    )
                    trySend(LocationServiceResult.Success(userLocation))
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    trySend(LocationServiceResult.LocationDisabled)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            trySend(LocationServiceResult.PermissionDenied)
        } catch (e: Exception) {
            trySend(LocationServiceResult.Error("Failed to get location: ${e.message}"))
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    suspend fun getLastKnownLocation(): LocationServiceResult {
        if (!hasLocationPermission()) {
            return LocationServiceResult.PermissionDenied
        }

        return try {
            // Use suspendCancellableCoroutine to properly await the Task
            val location = suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(null)
                    }
            }

            if (location != null) {
                LocationServiceResult.Success(
                    UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy
                    )
                )
            } else {
                // If no last known location, try to get a fresh location
                getCurrentLocationOnce()
            }
        } catch (e: SecurityException) {
            LocationServiceResult.PermissionDenied
        } catch (e: Exception) {
            LocationServiceResult.Error("Failed to get location: ${e.message}")
        }
    }

    /**
     * Check if location services (GPS/Network) are enabled on the device
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Open device location settings screen
     */
    fun openLocationSettings(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Get ONLY the current GPS location - no cached/last known location
     * This method requests a fresh GPS fix and waits for it
     */
    suspend fun getCurrentLocationOnly(): LocationServiceResult {
        if (!hasLocationPermission()) {
            return LocationServiceResult.PermissionDenied
        }

        // Check if location services are enabled
        if (!isLocationEnabled()) {
            return LocationServiceResult.LocationDisabled
        }

        return try {
            Log.d(TAG, "Starting getCurrentLocationOnly request")

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L // Request location update after 1 second
            ).apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(false) // Don't wait for perfect accuracy, get first available
                setMaxUpdates(1) // Only get one location update
                setMinUpdateIntervalMillis(500L) // Minimum 500ms between updates
                setMaxUpdateDelayMillis(2000L) // Deliver update within 2 seconds
            }.build()

            // Use withTimeoutOrNull to add a timeout with user-friendly message
            val location = withTimeoutOrNull(20000L) { // Increased to 20 second timeout
                suspendCancellableCoroutine<Location?> { continuation ->
                    var hasResumed = false

                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            Log.d(TAG, "LocationResult received with ${result.locations.size} locations")

                            // Try to get the best location from the result
                            val bestLocation = result.lastLocation ?: result.locations.firstOrNull()

                            if (bestLocation != null && !hasResumed) {
                                hasResumed = true
                                fusedLocationClient.removeLocationUpdates(this)
                                Log.d(TAG, "Got fresh GPS location: ${bestLocation.latitude}, ${bestLocation.longitude}, accuracy: ${bestLocation.accuracy}m")
                                continuation.resume(bestLocation)
                            } else if (bestLocation == null) {
                                Log.w(TAG, "LocationResult received but no location data available")
                            }
                        }

                        override fun onLocationAvailability(availability: LocationAvailability) {
                            Log.d(TAG, "Location availability changed: isAvailable=${availability.isLocationAvailable}")
                            if (!availability.isLocationAvailable && !hasResumed) {
                                hasResumed = true
                                fusedLocationClient.removeLocationUpdates(this)
                                Log.d(TAG, "Location not available")
                                continuation.resume(null)
                            }
                        }
                    }

                    try {
                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.getMainLooper()
                        )

                        continuation.invokeOnCancellation {
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                        }
                    } catch (e: SecurityException) {
                        continuation.resume(null)
                    }
                }
            }

            if (location != null) {
                LocationServiceResult.Success(
                    UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy
                    )
                )
            } else {
                Log.d(TAG, "Fresh location timed out, attempting fallback to last known location")

                val fallbackResult = getLastKnownLocation()

                when (fallbackResult) {
                    is LocationServiceResult.Success -> {
                        Log.d(TAG, "Fallback last known location acquired: ${fallbackResult.location.latitude}, ${fallbackResult.location.longitude}")
                        fallbackResult
                    }
                    is LocationServiceResult.Error -> {
                        Log.d(TAG, "Fallback last known location failed: ${fallbackResult.message}")

                        // Re-check if location services were disabled during the request
                        if (!isLocationEnabled()) {
                            LocationServiceResult.LocationDisabled
                        } else {
                            LocationServiceResult.Error(
                                "Unable to determine your current location. Please ensure location services are enabled and try again. (${fallbackResult.message})"
                            )
                        }
                    }
                    else -> {
                        // Propagate permission/location disabled results as-is
                        fallbackResult
                    }
                }
            }
        } catch (e: SecurityException) {
            LocationServiceResult.PermissionDenied
        } catch (e: Exception) {
            LocationServiceResult.Error("Failed to get your location: ${e.message}")
        }
    }

    suspend fun getCurrentLocationOnce(): LocationServiceResult {
        return try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                0L // Get location immediately
            ).apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(false)
                setMaxUpdates(1) // Only get one location update
            }.build()

            // Use withTimeoutOrNull to add a timeout
            val location = withTimeoutOrNull(10000L) { // 10 second timeout
                suspendCancellableCoroutine<Location?> { continuation ->
                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { location ->
                                fusedLocationClient.removeLocationUpdates(this)
                                continuation.resume(location)
                            }
                        }

                        override fun onLocationAvailability(availability: LocationAvailability) {
                            if (!availability.isLocationAvailable) {
                                fusedLocationClient.removeLocationUpdates(this)
                                continuation.resume(null)
                            }
                        }
                    }

                    try {
                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.getMainLooper()
                        )

                        continuation.invokeOnCancellation {
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                        }
                    } catch (e: SecurityException) {
                        continuation.resume(null)
                    }
                }
            }

            if (location != null) {
                LocationServiceResult.Success(
                    UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy
                    )
                )
            } else {
                LocationServiceResult.Error("Unable to get current location. Please check if location services are enabled.")
            }
        } catch (e: SecurityException) {
            LocationServiceResult.PermissionDenied
        } catch (e: Exception) {
            LocationServiceResult.Error("Failed to get current location: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LocationService"
    }
}
