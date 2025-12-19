package com.airgradient.android.domain.repositories

import com.airgradient.android.data.models.ClusteredMeasurement
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.domain.models.*
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for air quality data
 * Defines the contract for accessing air quality information without implementation details
 */
interface AirQualityRepository {

    /**
     * Get air quality locations within specified geographic bounds
     */
    suspend fun getLocationsInBounds(
        bounds: GeographicBounds,
        measurementType: MeasurementType
    ): Result<List<Location>>

    /**
     * Get detailed information for a specific location
     */
    suspend fun getLocationDetails(locationId: Int): Result<Location>

    /**
     * Get current air quality measurement for a location
     */
    suspend fun getCurrentMeasurement(locationId: Int): Result<AirQualityMeasurement>

    /**
     * Get historical air quality data for a location
     */
    suspend fun getHistoricalData(
        locationId: Int,
        timeRange: TimeRange
    ): Result<List<AirQualityMeasurement>>

    /**
     * Search for locations by name or other criteria
     */
    suspend fun searchLocations(query: String): Result<List<Location>>

    /**
     * Get real-time updates for a location
     */
    fun observeLocationUpdates(locationId: Int): Flow<AirQualityMeasurement>

    /**
     * Get clustered map measurements for the specified viewport
     */
    suspend fun getClusteredMeasurements(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        measurementType: MeasurementType,
        zoomLevel: Int? = null
    ): ApiResult<List<ClusteredMeasurement>>
}

/**
 * Geographic bounds for location queries
 */
data class GeographicBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    val isValid: Boolean
        get() = north > south && east > west &&
                north in -90.0..90.0 && south in -90.0..90.0 &&
                east in -180.0..180.0 && west in -180.0..180.0
}

/**
 * Time range for historical data queries
 */
data class TimeRange(
    val startTime: Long,
    val endTime: Long,
    val interval: TimeInterval
) {
    val isValid: Boolean
        get() = endTime > startTime && startTime > 0
}

/**
 * Time intervals for data aggregation
 */
enum class TimeInterval(val displayName: String, val hours: Int) {
    HOURLY("Hourly", 1),
    DAILY("Daily", 24),
    WEEKLY("Weekly", 168)
}
