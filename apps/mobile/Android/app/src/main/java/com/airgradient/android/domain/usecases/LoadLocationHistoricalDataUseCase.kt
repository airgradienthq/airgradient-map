package com.airgradient.android.domain.usecases

import com.airgradient.android.data.models.ChartTimeframe
import com.airgradient.android.data.models.HistoricalData
import com.airgradient.android.data.repositories.LocationDetailRepository
import com.airgradient.android.domain.models.MeasurementType
import javax.inject.Inject

class LoadLocationHistoricalDataUseCase @Inject constructor(
    private val repository: LocationDetailRepository
) {
    suspend operator fun invoke(
        locationId: Int,
        timeframe: ChartTimeframe,
        measurementType: MeasurementType
    ): HistoricalData = repository.getHistoricalData(locationId, timeframe, measurementType)
}
