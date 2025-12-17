package com.airgradient.android.domain.usecases

import com.airgradient.android.data.models.HeatMapResponse
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.data.repositories.LocationDetailRepository
import com.airgradient.android.domain.models.MeasurementType
import javax.inject.Inject

class LoadHeatMapDataUseCase @Inject constructor(
    private val repository: LocationDetailRepository
) {
    suspend operator fun invoke(
        locationId: Int,
        measurementType: MeasurementType
    ): ApiResult<HeatMapResponse> = repository.getHeatMapData(locationId, measurementType)
}

