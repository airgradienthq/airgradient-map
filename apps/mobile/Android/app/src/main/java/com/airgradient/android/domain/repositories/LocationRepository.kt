package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.Coordinates
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for device location services
 */
interface LocationRepository {

    /**
     * Get current device location
     */
    suspend fun getCurrentLocation(): Result<Coordinates>

    /**
     * Observe location changes
     */
    fun observeLocationUpdates(): Flow<Coordinates>

    /**
     * Check if location permission is granted
     */
    suspend fun hasLocationPermission(): Boolean

    /**
     * Check if location services are enabled
     */
    suspend fun isLocationEnabled(): Boolean
}

/**
 * Location service results
 */
sealed class LocationResult {
    data class Success(val coordinates: Coordinates) : LocationResult()
    data class Error(val message: String) : LocationResult()
    object PermissionDenied : LocationResult()
    object LocationDisabled : LocationResult()
}