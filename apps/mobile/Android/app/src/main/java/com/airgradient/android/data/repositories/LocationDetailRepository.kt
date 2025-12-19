package com.airgradient.android.data.repositories

import com.airgradient.android.data.models.*
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.domain.models.MeasurementType

/**
 * Repository interface for fetching location detail data
 */
interface LocationDetailRepository {
    /**
     * Get detailed information about a specific location
     */
    suspend fun getLocationDetail(locationId: Int): LocationDetail

    /**
     * Get historical data for a location
     */
    suspend fun getHistoricalData(
        locationId: Int,
        timeframe: ChartTimeframe,
        measurementType: MeasurementType
    ): HistoricalData

    /**
     * Get 7x24 heat map data for the current measurement type
     */
    suspend fun getHeatMapData(
        locationId: Int,
        measurementType: MeasurementType
    ): ApiResult<HeatMapResponse>

    /**
     * Get organization information
     */
    suspend fun getOrganizationInfo(organizationId: Int): OrganizationInfo?

    /**
     * Get WHO compliance data for a location
     */
    suspend fun getWHOCompliance(locationId: Int): WHOCompliance?

    /**
     * Get cigarette equivalence data for a location
     */
    suspend fun getCigaretteEquivalence(locationId: Int): CigaretteData?

    /**
     * Get current measurement for a location
     */
    suspend fun getCurrentMeasurement(locationId: Int): ApiResult<AirQualityLocation>
}
