package com.airgradient.android.ui.mymonitors.ViewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.domain.models.monitors.ChartTimeRange
import com.airgradient.android.domain.models.monitors.HistoryRequest
import com.airgradient.android.domain.models.monitors.HistorySample
import com.airgradient.android.domain.models.monitors.MonitorMeasurementKind
import com.airgradient.android.domain.models.monitors.temperature
import com.airgradient.android.domain.repositories.MyMonitorsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MonitorDetailViewModel @Inject constructor(
    private val repository: MyMonitorsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorDetailUiState())
    val uiState: StateFlow<MonitorDetailUiState> = _uiState.asStateFlow()

    private var hasInitialised = false

    fun setInitialData(placeId: Int, summary: MonitorSummaryUi) {
        val currentState = _uiState.value
        if (hasInitialised && currentState.locationId == summary.locationId && currentState.summary == summary) {
            return
        }

        val availableMetrics = buildAvailableMetrics(summary)
        val defaultMetric = availableMetrics.firstOrNull() ?: MonitorMeasurementKind.PM25

        _uiState.value = MonitorDetailUiState(
            placeId = placeId,
            locationId = summary.locationId,
            monitorName = summary.name,
            summary = summary,
            availableMetrics = availableMetrics,
            selectedMetric = defaultMetric,
            selectedTimeRange = ChartTimeRange.default,
            isLoading = false,
            errorMessage = null,
            history = emptyList()
        )

        hasInitialised = true

        loadHistory(placeId, summary.locationId, defaultMetric, ChartTimeRange.default)
    }

    fun selectMetric(metric: MonitorMeasurementKind) {
        val state = _uiState.value
        if (state.selectedMetric == metric) return
        _uiState.update { it.copy(selectedMetric = metric) }
        loadHistory(state.placeId ?: return, state.locationId ?: return, metric, state.selectedTimeRange)
    }

    fun selectTimeRange(range: ChartTimeRange) {
        val state = _uiState.value
        if (state.selectedTimeRange == range) return
        _uiState.update { it.copy(selectedTimeRange = range) }
        loadHistory(state.placeId ?: return, state.locationId ?: return, state.selectedMetric, range)
    }

    fun retry() {
        val state = _uiState.value
        val placeId = state.placeId ?: return
        val locationId = state.locationId ?: return
        loadHistory(placeId, locationId, state.selectedMetric, state.selectedTimeRange)
    }

    private fun loadHistory(
        placeId: Int,
        locationId: Int,
        metric: MonitorMeasurementKind,
        range: ChartTimeRange
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.fetchHistory(
                HistoryRequest(
                    placeId = placeId,
                    locationId = locationId,
                    measurement = metric,
                    timeRange = range
                )
            ).onSuccess { samples ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        history = samples,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.localizedMessage.orEmpty()
                    )
                }
            }
        }
    }

    private fun buildAvailableMetrics(summary: MonitorSummaryUi): List<MonitorMeasurementKind> {
        val metrics = summary.metrics
        val temperatureValue = metrics.temperature(summary.temperatureUnit)
        return MonitorMeasurementKind.values().filter { kind ->
            when (kind) {
                MonitorMeasurementKind.PM25 -> metrics.pm25 != null
                MonitorMeasurementKind.CO2 -> metrics.co2 != null
                MonitorMeasurementKind.TVOC_INDEX -> metrics.tvocIndex != null
                MonitorMeasurementKind.NOX_INDEX -> metrics.noxIndex != null
                MonitorMeasurementKind.TEMPERATURE -> temperatureValue != null
                MonitorMeasurementKind.HUMIDITY -> metrics.humidity != null
            }
        }
    }
}

data class MonitorDetailUiState(
    val placeId: Int? = null,
    val locationId: Int? = null,
    val monitorName: String = "",
    val summary: MonitorSummaryUi? = null,
    val availableMetrics: List<MonitorMeasurementKind> = emptyList(),
    val selectedMetric: MonitorMeasurementKind = MonitorMeasurementKind.PM25,
    val selectedTimeRange: ChartTimeRange = ChartTimeRange.default,
    val history: List<HistorySample> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
