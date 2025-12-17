package com.airgradient.android.domain.usecases

import com.airgradient.android.data.models.ClusteredMeasurement
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.repositories.AirQualityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for fetching map markers with clustering support.
 * This encapsulates the complexity of dealing with the API's clustering behavior
 * and provides a clean interface for the presentation layer.
 */
@Singleton
class GetMapMarkersUseCase @Inject constructor(
    private val repository: AirQualityRepository
) {
    /**
     * Fetches air quality markers for the specified map bounds.
     *
     * @param north Northern boundary latitude
     * @param south Southern boundary latitude
     * @param east Eastern boundary longitude
     * @param west Western boundary longitude
     * @param measurementType Type of measurement (PM2.5 or CO2)
     * @param zoomLevel Optional zoom level for clustering optimization
     * @return ApiResult containing list of clustered measurements or error
     */
    suspend operator fun invoke(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        measurementType: MeasurementType,
        zoomLevel: Int? = null
    ): ApiResult<List<ClusteredMeasurement>> {
        return repository.getClusteredMeasurements(
            north = north,
            south = south,
            east = east,
            west = west,
            measurementType = measurementType,
            zoomLevel = zoomLevel
        )
    }
}
