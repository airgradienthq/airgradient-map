package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.monitors.ChartTimeRange
import com.airgradient.android.domain.models.monitors.CurrentLocationReading
import com.airgradient.android.domain.models.monitors.HistoryRequest
import com.airgradient.android.domain.models.monitors.HistorySample
import com.airgradient.android.domain.models.monitors.MonitorsPlace
import com.airgradient.android.domain.models.monitors.PlaceLocation
import kotlinx.coroutines.flow.StateFlow

interface MyMonitorsRepository {
    suspend fun fetchPlaces(): Result<List<MonitorsPlace>>
    suspend fun fetchPlaceLocations(placeId: Int): Result<List<PlaceLocation>>
    suspend fun fetchCurrentReadings(placeId: Int): Result<List<CurrentLocationReading>>
    suspend fun fetchHistory(request: HistoryRequest): Result<List<HistorySample>>
    suspend fun registerMonitor(placeId: Int, serial: String, model: String, locationName: String): Result<Unit>
    fun selectedPlaceId(): StateFlow<Int?>
    fun updateSelectedPlaceId(placeId: Int?)
    fun clearSelection()
}
