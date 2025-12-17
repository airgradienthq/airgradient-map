package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.Location
import com.airgradient.android.domain.repositories.AirQualityRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for fetching detailed information about a specific location.
 * Provides location metadata including owner, sensor type, and data source.
 */
@Singleton
class GetLocationDetailsUseCase @Inject constructor(
    private val repository: AirQualityRepository
) {
    /**
     * Gets detailed information for a specific location.
     *
     * @param locationId The ID of the location
     * @return ApiResult containing location info or error
     */
    suspend operator fun invoke(locationId: Int): Result<Location> {
        return repository.getLocationDetails(locationId)
    }
}
