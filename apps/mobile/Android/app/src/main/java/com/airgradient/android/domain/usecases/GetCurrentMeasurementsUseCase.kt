package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.AirQualityMeasurement
import com.airgradient.android.domain.repositories.AirQualityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for fetching current air quality measurements for a location.
 * Returns real-time PM2.5, temperature, humidity and other sensor data.
 */
@Singleton
class GetCurrentMeasurementsUseCase @Inject constructor(
    private val repository: AirQualityRepository
) {
    /**
     * Gets current measurements for a specific location.
     *
     * @param locationId The ID of the location
     * @return ApiResult containing current measurements or error
     */
    suspend operator fun invoke(locationId: Int): Result<AirQualityMeasurement> {
        return repository.getCurrentMeasurement(locationId)
    }
}
