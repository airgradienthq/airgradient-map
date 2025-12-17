package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.*
import com.airgradient.android.domain.repositories.AirQualityRepository
import com.airgradient.android.domain.repositories.GeographicBounds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for retrieving air quality data for map display
 * Encapsulates business logic for filtering, validating, and processing air quality locations
 */
class GetAirQualityDataUseCase @Inject constructor(
    private val repository: AirQualityRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Get air quality locations within specified bounds
     * Filters out invalid measurements and applies business rules
     */
    suspend operator fun invoke(
        bounds: GeographicBounds,
        measurementType: MeasurementType,
        filterCriteria: FilterCriteria = FilterCriteria()
    ): Result<List<Location>> = withContext(dispatcher) {

        if (!bounds.isValid) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid geographic bounds")
            )
        }

        try {
            val result = repository.getLocationsInBounds(bounds, measurementType)

            result.map { locations ->
                locations
                    .filter { location -> isValidLocation(location, filterCriteria) }
                    .sortedBy { location ->
                        // Sort by measurement quality and recency
                        when {
                            location.sensorInfo?.type == SensorType.REFERENCE -> 0
                            location.hasValidMeasurement -> 1
                            else -> 2
                        }
                    }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validate location based on business rules
     */
    private fun isValidLocation(location: Location, criteria: FilterCriteria): Boolean {
        // Must have valid coordinates
        if (!location.coordinates.isValid) return false

        // Apply sensor type filter
        if (criteria.sensorTypes.isNotEmpty()) {
            val sensorType = location.sensorInfo?.type
            if (sensorType !in criteria.sensorTypes) return false
        }

        // Apply measurement validity filter
        if (criteria.requireValidMeasurement && !location.hasValidMeasurement) {
            return false
        }

        // Apply measurement range filter
        location.currentMeasurement?.primaryValue?.let { value ->
            if (criteria.measurementRange != null) {
                if (value !in criteria.measurementRange) return false
            }
        }

        return true
    }
}

/**
 * Criteria for filtering air quality locations
 */
data class FilterCriteria(
    val sensorTypes: Set<SensorType> = emptySet(),
    val requireValidMeasurement: Boolean = true,
    val measurementRange: ClosedFloatingPointRange<Double>? = null,
    val organizationTypes: Set<OrganizationType> = emptySet()
)