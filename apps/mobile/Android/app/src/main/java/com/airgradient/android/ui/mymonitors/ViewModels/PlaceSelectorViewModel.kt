package com.airgradient.android.ui.mymonitors.ViewModels

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.monitors.CurrentLocationReading
import com.airgradient.android.domain.models.monitors.MonitorMetrics
import com.airgradient.android.domain.models.monitors.MonitorsPlace
import com.airgradient.android.domain.models.monitors.PlaceLocation
import com.airgradient.android.domain.models.monitors.TemperatureUnit
import com.airgradient.android.domain.models.monitors.temperature
import com.airgradient.android.domain.repositories.MyMonitorsRepository
import com.airgradient.android.domain.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PlaceSelectorViewModel @Inject constructor(
    private val repository: MyMonitorsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaceSelectorUiState())
    val uiState: StateFlow<PlaceSelectorUiState> = _uiState.asStateFlow()

    private var locationsById: Map<Int, PlaceLocation> = emptyMap()
    private var readingsById: Map<Int, CurrentLocationReading> = emptyMap()
    private var observeSelectionJob: Job? = null

    init {
        observeSelectionChanges()
        observeDisplayUnit()
        refreshPlaces()
    }

    fun refreshPlaces() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingPlaces = true, placesError = null) }
            repository.fetchPlaces()
                .onSuccess { places ->
                    val resolvedPlaces = places.sortedBy { it.name.lowercase(Locale.getDefault()) }
                    val selectedId = repository.selectedPlaceId().value
                        ?: resolvedPlaces.firstOrNull()?.id
                    if (selectedId != null) {
                        repository.updateSelectedPlaceId(selectedId)
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingPlaces = false,
                            places = resolvedPlaces,
                            selectedPlaceId = selectedId,
                            placesError = null,
                            temperatureUnit = resolveTemperatureUnit(resolvedPlaces.firstOrNull { place -> place.id == selectedId })
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingPlaces = false,
                            placesError = error.localizedMessage ?: "Unable to load places"
                        )
                    }
                }
        }
    }

    fun refreshCurrentSelection() {
        val selectedPlaceId = _uiState.value.selectedPlaceId
        if (selectedPlaceId != null) {
            loadPlaceData(selectedPlaceId)
        } else {
            refreshPlaces()
        }
    }

    fun onPlaceSelected(placeId: Int) {
        repository.updateSelectedPlaceId(placeId)
    }

    fun retryLocations() {
        val placeId = _uiState.value.selectedPlaceId ?: return
        loadLocations(placeId)
    }

    fun retryReadings() {
        val placeId = _uiState.value.selectedPlaceId ?: return
        loadCurrentReadings(placeId)
    }

    private fun observeSelectionChanges() {
        observeSelectionJob?.cancel()
        observeSelectionJob = viewModelScope.launch {
            repository.selectedPlaceId().collectLatest { placeId ->
                _uiState.update { state ->
                    val place = state.places.firstOrNull { it.id == placeId }
                    state.copy(
                        selectedPlaceId = placeId,
                        temperatureUnit = resolveTemperatureUnit(place)
                    )
                }
                placeId?.let { loadPlaceData(it) }
            }
        }
    }

    private fun observeDisplayUnit() {
        viewModelScope.launch {
            settingsRepository.getDisplayUnit().collect { unit ->
                val normalized = when (unit) {
                    AQIDisplayUnit.USAQI -> AQIDisplayUnit.USAQI
                    AQIDisplayUnit.UGM3 -> AQIDisplayUnit.UGM3
                }
                _uiState.update { it.copy(aqiDisplayUnit = normalized) }
            }
        }
    }

    private fun loadPlaceData(placeId: Int) {
        locationsById = emptyMap()
        readingsById = emptyMap()
        _uiState.update { it.copy(monitors = emptyList()) }
        loadLocations(placeId)
    }

    private fun loadLocations(placeId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingLocations = true, locationsError = null) }
            repository.fetchPlaceLocations(placeId)
                .onSuccess { locations ->
                    locationsById = locations
                        .filter { it.active && !it.isPpsLocation() }
                        .associateBy { it.id }
                    _uiState.update { state ->
                        val selectedId = state.selectedPlaceId
                        val place = state.places.firstOrNull { it.id == selectedId }
                        state.copy(
                            isLoadingLocations = false,
                            locationsError = null,
                            temperatureUnit = resolveTemperatureUnit(place),
                            monitors = mergeDisplays(state, selectedId, locationsById, readingsById)
                        )
                    }
                    loadCurrentReadings(placeId)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingLocations = false,
                            locationsError = error.localizedMessage ?: "Unable to load monitors"
                        )
                    }
                }
        }
    }

    private fun loadCurrentReadings(placeId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingReadings = true, readingsError = null) }
            repository.fetchCurrentReadings(placeId)
                .onSuccess { readings ->
                    readingsById = readings.associateBy { it.locationId }
                    _uiState.update { state ->
                        val selectedId = state.selectedPlaceId
                        val place = state.places.firstOrNull { it.id == selectedId }
                        state.copy(
                            isLoadingReadings = false,
                            readingsError = null,
                            temperatureUnit = resolveTemperatureUnit(place),
                            monitors = mergeDisplays(state, selectedId, locationsById, readingsById)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingReadings = false,
                            readingsError = error.localizedMessage ?: "Unable to refresh readings"
                        )
                    }
                }
        }
    }

    private fun mergeDisplays(
        state: PlaceSelectorUiState,
        placeId: Int?,
        locations: Map<Int, PlaceLocation>,
        readings: Map<Int, CurrentLocationReading>
    ): List<MonitorSummaryUi> {
        if (placeId == null) return emptyList()
        val place = state.places.firstOrNull { it.id == placeId }
        val temperatureUnit = resolveTemperatureUnit(place)

        return locations.values.sortedWith(compareBy<PlaceLocation> {
            if (it.indoor == true) 1 else 0
        }.thenBy { it.name.lowercase(Locale.getDefault()) })
            .map { location ->
                val reading = readings[location.id]
                val mergedMetrics = mergeMetrics(location.metrics, reading?.metrics)
                val offline = location.offline || (reading?.offline == true)
                val lastUpdated = reading?.timestamp
                MonitorSummaryUi(
                    locationId = location.id,
                    placeId = placeId,
                    name = location.name,
                    metrics = mergedMetrics,
                    offline = offline,
                    indoor = location.indoor,
                    lastUpdatedInstant = lastUpdated,
                    lastUpdatedLabel = lastUpdated?.let { formatRelative(it) },
                    temperatureUnit = temperatureUnit
                )
            }
    }

    private fun mergeMetrics(primary: MonitorMetrics, secondary: MonitorMetrics?): MonitorMetrics {
        if (secondary == null) return primary
        return MonitorMetrics(
            pm25 = secondary.pm25 ?: primary.pm25,
            co2 = secondary.co2 ?: primary.co2,
            tvocIndex = secondary.tvocIndex ?: primary.tvocIndex,
            noxIndex = secondary.noxIndex ?: primary.noxIndex,
            temperatureCelsius = secondary.temperatureCelsius ?: primary.temperatureCelsius,
            humidity = secondary.humidity ?: primary.humidity
        )
    }

    private fun resolveTemperatureUnit(place: MonitorsPlace?): TemperatureUnit {
        place?.temperatureUnit?.let { return it }
        return when (Locale.getDefault().country.uppercase(Locale.ROOT)) {
            "US", "BS", "BZ", "KY", "PW", "FM", "MH", "LR" -> TemperatureUnit.FAHRENHEIT
            else -> TemperatureUnit.CELSIUS
        }
    }

    private fun formatRelative(instant: Instant): String {
        val now = System.currentTimeMillis()
        val time = instant.toEpochMilli()
        return DateUtils.getRelativeTimeSpanString(
            time,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }
}

data class MonitorSummaryUi(
    val locationId: Int,
    val placeId: Int,
    val name: String,
    val metrics: MonitorMetrics,
    val offline: Boolean,
    val indoor: Boolean?,
    val lastUpdatedInstant: Instant?,
    val lastUpdatedLabel: String?,
    val temperatureUnit: TemperatureUnit
)

data class PlaceSelectorUiState(
    val isLoadingPlaces: Boolean = false,
    val isLoadingLocations: Boolean = false,
    val isLoadingReadings: Boolean = false,
    val places: List<MonitorsPlace> = emptyList(),
    val selectedPlaceId: Int? = null,
    val monitors: List<MonitorSummaryUi> = emptyList(),
    val placesError: String? = null,
    val locationsError: String? = null,
    val readingsError: String? = null,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val aqiDisplayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI
)

private const val PPS_LOCATION_TYPE = "pps"

private fun PlaceLocation.isPpsLocation(): Boolean {
    return locationType?.equals(PPS_LOCATION_TYPE, ignoreCase = true) == true
}
