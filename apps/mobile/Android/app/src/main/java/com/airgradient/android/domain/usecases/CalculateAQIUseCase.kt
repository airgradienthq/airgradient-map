package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.*
import com.airgradient.android.domain.services.AQIService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for calculating Air Quality Index (AQI) values and categories
 * Delegates to AQIService for all calculations
 */
class CalculateAQIUseCase @Inject constructor(
    private val aqiService: AQIService,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Calculate AQI category from PM2.5 measurement
     */
    suspend fun calculateAQICategory(
        measurement: AirQualityMeasurement,
        standard: AQIStandard = AQIStandard.US_EPA
    ): Result<AQICategory> = withContext(dispatcher) {

        val pm25 = measurement.pm25
            ?: return@withContext Result.failure(
                IllegalArgumentException("PM2.5 measurement required for AQI calculation")
            )

        if (!measurement.isValid) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid measurement data")
            )
        }

        val category = aqiService.getCategory(pm25, standard)
        Result.success(category)
    }

    /**
     * Calculate numerical AQI value from PM2.5 measurement
     */
    suspend fun calculateAQIValue(
        measurement: AirQualityMeasurement,
        standard: AQIStandard = AQIStandard.US_EPA
    ): Result<Int> = withContext(dispatcher) {

        val pm25 = measurement.pm25
            ?: return@withContext Result.failure(
                IllegalArgumentException("PM2.5 measurement required")
            )

        if (!measurement.isValid) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid measurement data")
            )
        }

        val aqiValue = aqiService.calculateAQI(pm25, standard)
        Result.success(aqiValue)
    }

    /**
     * Get color representation for AQI value
     */
    suspend fun getAQIColor(
        category: AQICategory
    ): Long = withContext(dispatcher) {
        aqiService.getCategoryColor(category)
    }
}