package com.airgradient.android.ui.map.ViewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.data.models.AirQualityAnnotation
import com.airgradient.android.data.models.AirQualityLocation
import com.airgradient.android.data.models.ClusteredMeasurement
import com.airgradient.android.data.models.MapCameraFocus
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.data.network.NetworkError
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.usecases.GetMapMarkersUseCase
import com.airgradient.android.data.services.LocationService
import com.airgradient.android.data.services.LocationServiceResult
import com.airgradient.android.data.services.LocationSearchService
import com.airgradient.android.data.services.SearchResult
import com.airgradient.android.data.services.SensorLocation
import com.airgradient.android.data.local.PreferencesManager
import com.airgradient.android.data.local.RecentSearch
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.repositories.SettingsRepository
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.ui.map.Utils.MapViewportManager
import com.airgradient.android.ui.map.Utils.ViewportBounds
import com.airgradient.android.ui.map.Utils.ViewportThrottleEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import org.osmdroid.util.BoundingBox
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import javax.inject.Inject

// Extension function for BoundingBox
fun BoundingBox.toViewportBounds(): ViewportBounds {
    return ViewportBounds(
        north = latNorth,
        south = latSouth,
        east = lonEast,
        west = lonWest
    )
}

data class MapLocationItem(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val pm25: Double?,
    val co2: Double?,
    val sensorType: String,
    val dataSource: String?,
    val isCluster: Boolean = false,
    val sensorsCount: Int? = null,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val locationName: String = name,
    val ownerName: String? = null
)

data class MapUiState(
    val isLoading: Boolean = false,
    val locations: List<MapLocationItem> = emptyList(),
    val annotations: List<AirQualityAnnotation> = emptyList(),
    val measurementType: MeasurementType = MeasurementType.PM25,
    val aqiDisplayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    val error: String? = null,
    val lastRefreshTime: Long = 0L,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val isSearching: Boolean = false,
    val mapCenter: Pair<Double, Double>? = null, // lat, lng
    val targetZoomLevel: Double? = null, // Target zoom when centering map
    val currentViewport: ViewportBounds? = null,
    val apiCallsThisMinute: Int = 0,
    val lastZoomLevel: Double = 8.0,
    val showLocationSettingsDialog: Boolean = false, // Show dialog to enable location
    val selectedMarkerKey: String? = null,
    val pendingCameraFocus: MapCameraFocus? = null,
    val isMapFrozen: Boolean = false,
    val viewportThrottle: ViewportThrottleEvent? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val getMapMarkersUseCase: GetMapMarkersUseCase,
    private val searchService: LocationSearchService,
    private val locationService: LocationService,
    private val preferencesManager: PreferencesManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()
    
    // Viewport management system
    private val viewportManager = MapViewportManager(viewModelScope)
    private var searchJob: Job? = null
    private var lastBounds: MapBounds? = null
    private var lastClusteredMeasurements: List<ClusteredMeasurement> = emptyList()
    
    // Legacy MapBounds for compatibility
    data class MapBounds(
        val north: Double,
        val south: Double,
        val east: Double,
        val west: Double
    ) {
        fun toViewportBounds() = ViewportBounds(north, south, east, west)
    }
    
    init {
        // Initialize debug system
        debugLog { "MapViewModel initialized with viewport management" }

        // Set up viewport manager data fetching listener
        viewportManager.setDataFetchListener { bounds, isZoomChange ->
            fetchDataForViewport(bounds, isZoomChange)
        }

        viewportManager.setThrottleListener { event ->
            _uiState.value = _uiState.value.copy(
                viewportThrottle = event,
                apiCallsThisMinute = event.callsThisMinute
            )
        }

        // Initialize map position based on user preferences
        initializeMapPosition()

        // Load recent searches
        loadRecentSearches()

        observeDisplayUnit()
    }

    private fun loadRecentSearches() {
        val recentSearches = preferencesManager.getRecentSearches()
        _uiState.value = _uiState.value.copy(recentSearches = recentSearches)
    }

    private fun initializeMapPosition() {
        viewModelScope.launch {
            val savedPosition = preferencesManager.getLastMapPosition()

            when {
                // First launch - try to get user's current location
                preferencesManager.isFirstLaunch() -> {
                    debugLog { "First launch detected - attempting to get user location" }
                    preferencesManager.setFirstLaunchComplete()

                    if (locationService.hasLocationPermission()) {
                        when (val result = locationService.getLastKnownLocation()) {
                            is LocationServiceResult.Success -> {
                                val location = result.location
                                debugLog { "Got user location: ${location.latitude}, ${location.longitude}" }

                                // Center on user's location with city-level zoom
                                _uiState.value = _uiState.value.copy(
                                    mapCenter = location.latitude to location.longitude,
                                    targetZoomLevel = 10.0
                                )

                                // Load data around user location
                                val span = 1.0
                                val bounds = ViewportBounds(
                                    north = location.latitude + span,
                                    south = location.latitude - span,
                                    east = location.longitude + span,
                                    west = location.longitude - span
                                )
                                viewportManager.forceFetch(bounds, 10.0)

                                // Save this as the default position
                                saveCurrentMapPosition(location.latitude, location.longitude, 10.0)
                            }
                            else -> {
                                // Fallback to Southeast Asia if location fails
                                loadDefaultPosition()
                            }
                        }
                    } else {
                        // No permission, load default Southeast Asia view
                        loadDefaultPosition()
                    }
                }

                // Subsequent launches - use saved position
                savedPosition != null -> {
                    debugLog { "Loading saved position: ${savedPosition.latitude}, ${savedPosition.longitude}, zoom: ${savedPosition.zoom}" }

                    _uiState.value = _uiState.value.copy(
                        mapCenter = savedPosition.latitude to savedPosition.longitude,
                        targetZoomLevel = savedPosition.zoom
                    )

                    // Load data for saved position
                    val span = 2.0 / savedPosition.zoom  // Adjust span based on zoom level
                    val bounds = ViewportBounds(
                        north = savedPosition.latitude + span,
                        south = savedPosition.latitude - span,
                        east = savedPosition.longitude + span,
                        west = savedPosition.longitude - span
                    )
                    viewportManager.forceFetch(bounds, savedPosition.zoom)
                }

                // No saved position and not first launch - use default
                else -> {
                    loadDefaultPosition()
                }
            }
        }
    }

    private fun loadDefaultPosition() {
        debugLog { "Loading default Southeast Asia position" }

        // Default to Southeast Asia region (Thailand focus)
        val initialBounds = ViewportBounds(
            north = 25.0,    // North Thailand/Myanmar border
            south = 5.0,     // South Thailand/Malaysia border
            east = 110.0,    // East boundary (Vietnam/Philippines)
            west = 95.0      // West boundary (Myanmar/India)
        )

        val defaultCenterLat = (initialBounds.north + initialBounds.south) / 2.0
        val defaultCenterLon = (initialBounds.east + initialBounds.west) / 2.0
        val defaultZoom = 8.0

        _uiState.value = _uiState.value.copy(
            mapCenter = defaultCenterLat to defaultCenterLon,
            targetZoomLevel = defaultZoom
        )

        saveCurrentMapPosition(defaultCenterLat, defaultCenterLon, defaultZoom)

        // Force initial data fetch
        viewportManager.forceFetch(initialBounds, defaultZoom)
    }

    private fun observeDisplayUnit() {
        viewModelScope.launch {
            settingsRepository.getDisplayUnit().collect { unit ->
                val normalizedUnit = when (unit) {
                    AQIDisplayUnit.UGM3 -> AQIDisplayUnit.UGM3
                    AQIDisplayUnit.USAQI -> AQIDisplayUnit.USAQI
                }
                setAqiDisplayUnit(normalizedUnit)
            }
        }
    }

    fun saveCurrentMapPosition(latitude: Double, longitude: Double, zoom: Double) {
        preferencesManager.saveMapPosition(latitude, longitude, zoom)
        debugLog { "Saved map position: $latitude, $longitude, zoom: $zoom" }
    }
    
    fun setMeasurementType(type: MeasurementType) {
        debugLog { "Measurement type change requested: current=${_uiState.value.measurementType.displayName}, new=${type.displayName}" }
        if (_uiState.value.measurementType != type) {
            debugLog { "Measurement type changing from ${_uiState.value.measurementType.displayName} to ${type.displayName}" }

            _uiState.value = _uiState.value.copy(
                measurementType = type,
                selectedMarkerKey = null,
                pendingCameraFocus = null
            )

            // Force data reload with current viewport for new measurement type
            _uiState.value.currentViewport?.let { viewport ->
                viewportManager.forceFetch(viewport, _uiState.value.lastZoomLevel)
            }
        }
    }

    fun setMapFrozen(frozen: Boolean) {
        if (_uiState.value.isMapFrozen == frozen) return
        _uiState.value = _uiState.value.copy(isMapFrozen = frozen)
    }

fun onMarkerSelected(annotation: AirQualityAnnotation) {
    _uiState.value = _uiState.value.copy(
        selectedMarkerKey = annotation.key,
        pendingCameraFocus = createCameraFocus(annotation)
    )
}

    fun clearMarkerSelection() {
        _uiState.value = _uiState.value.copy(selectedMarkerKey = null)
    }

    fun consumeCameraFocus() {
        if (_uiState.value.pendingCameraFocus != null) {
            _uiState.value = _uiState.value.copy(pendingCameraFocus = null)
        }
    }

    private fun createCameraFocus(annotation: AirQualityAnnotation): MapCameraFocus? {
        val viewport = _uiState.value.currentViewport ?: return null
        val latDelta = abs(viewport.north - viewport.south).coerceAtLeast(0.01)
        val lonDelta = abs(viewport.east - viewport.west).coerceAtLeast(0.01)
        val verticalOffset = latDelta * 0.12

        val adjustedCenter = annotation.coordinate.first - verticalOffset to annotation.coordinate.second

        return MapCameraFocus(
            center = adjustedCenter,
            spanLatitudeDelta = latDelta,
            spanLongitudeDelta = lonDelta,
            zoomMultiplier = 1.0,
            verticalOvershootFraction = 0.04
        )
    }

    fun setAqiDisplayUnit(unit: AQIDisplayUnit) {
        if (_uiState.value.aqiDisplayUnit == unit) return

        _uiState.value = _uiState.value.copy(aqiDisplayUnit = unit)

        if (lastClusteredMeasurements.isNotEmpty()) {
            val annotations = buildAnnotations(lastClusteredMeasurements, _uiState.value.measurementType, unit)
            _uiState.value = _uiState.value.copy(annotations = annotations)
        }
    }
    
    /**
     * Called when map viewport changes (zoom or pan)
     * This is the main entry point for dynamic data fetching
     */
    fun onMapViewportChange(mapView: org.osmdroid.views.MapView) {
        val currentZoom = mapView.zoomLevelDouble
        val center = mapView.mapCenter

        // Update UI state with current viewport and zoom
        _uiState.value = _uiState.value.copy(
            currentViewport = mapView.boundingBox.toViewportBounds(),
            lastZoomLevel = currentZoom
        )

        // Save the current position to preferences
        saveCurrentMapPosition(center.latitude, center.longitude, currentZoom)

        // Trigger viewport manager processing
        viewportManager.onViewportChange(mapView)
    }
    
    /**
     * Fetch data for a specific viewport (called by viewport manager)
     */
    private fun fetchDataForViewport(bounds: ViewportBounds, isZoomChange: Boolean) {
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentViewport = bounds
            )
            
            try {
                // Use bounds as-is without optimization
                val result = getMapMarkersUseCase(
                    north = bounds.north,
                    south = bounds.south,
                    east = bounds.east,
                    west = bounds.west,
                    measurementType = _uiState.value.measurementType
                )
                
                when (result) {
                    is ApiResult.Success<List<ClusteredMeasurement>> -> {
                        val currentMeasurement = _uiState.value.measurementType
                        val currentDisplayUnit = _uiState.value.aqiDisplayUnit

                        val mapItems = transformApiDataToMapItems(result.data)
                        val annotations = buildAnnotations(result.data, currentMeasurement, currentDisplayUnit)
                        lastClusteredMeasurements = result.data

                        // API already returns pre-clustered data - no need for client-side clustering
                        debugLog { "API success: ${mapItems.size} items (${result.data.count { it.isCluster }} clusters, ${result.data.count { !it.isCluster }} sensors)" }

                        // Create a new state with unique reference to force recomposition
                        val adjustedSelection = _uiState.value.selectedMarkerKey?.takeIf { selectedKey ->
                            annotations.any { it.key == selectedKey }
                        }

                        val newState = _uiState.value.copy(
                            isLoading = false,
                            locations = mapItems,
                            annotations = annotations,
                            selectedMarkerKey = adjustedSelection,
                            error = null,
                            lastRefreshTime = System.currentTimeMillis(),
                            apiCallsThisMinute = viewportManager.getStats().apiCallsThisMinute
                        )
                        
                        _uiState.value = newState
                        
                        debugLog { "Map annotations updated" }
                    }
                    
                    is ApiResult.Error -> {
                        handleApiError(result.error)
                    }
                    
                    is ApiResult.Loading -> {
                        // Loading state already set above
                    }
                }
                
            } catch (e: Exception) {
                mapOf(
                    "bounds" to bounds.toString(),
                    "zoomChange" to isZoomChange,
                    "measurementType" to _uiState.value.measurementType.displayName
                )
                debugLog { "API error handled: $e" }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Unexpected error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Transform API data to internal MapLocationItem format
     */
    private fun transformApiDataToMapItems(clusteredMeasurements: List<ClusteredMeasurement>): List<MapLocationItem> {
        return clusteredMeasurements.map { measurement ->
            MapLocationItem(
                id = measurement.locationId ?: measurement.hashCode(),
                name = measurement.locationName ?: UNKNOWN_LOCATION_LABEL,
                latitude = measurement.validLatitude,  // Use proper coordinates from model
                longitude = measurement.validLongitude,
                pm25 = if (_uiState.value.measurementType == MeasurementType.PM25) measurement.value else null,
                co2 = if (_uiState.value.measurementType == MeasurementType.CO2) measurement.value else null,
                sensorType = measurement.sensorType ?: "Unknown",
                dataSource = measurement.dataSource,
                isCluster = measurement.isCluster,
                sensorsCount = measurement.sensorsCount,
                locationName = measurement.locationName ?: UNKNOWN_LOCATION_LABEL,
                ownerName = measurement.ownerName
            )
        }.filter { item ->
            // Filter out items without valid measurement data
            (item.pm25 != null && item.pm25 >= 0) || (item.co2 != null && item.co2 >= 0)
        }
    }

    private fun buildAnnotations(
        clusteredMeasurements: List<ClusteredMeasurement>,
        measurementType: MeasurementType,
        displayUnit: AQIDisplayUnit
    ): List<AirQualityAnnotation> {
        val measurementUnit = if (measurementType == MeasurementType.PM25) displayUnit else AQIDisplayUnit.UGM3

        return clusteredMeasurements.mapNotNull { measurement ->
            val coordinate = measurement.coordinate
            val locationId = measurement.locationId ?: if (measurement.isCluster) 0 else return@mapNotNull null

            val location = AirQualityLocation(
                locationId = locationId,
                locationName = measurement.locationName,
                latitude = coordinate.first,
                longitude = coordinate.second,
                pm02 = if (measurementType == MeasurementType.PM25) measurement.value else null,
                pm25 = if (measurementType == MeasurementType.PM25) measurement.value else null,
                co2 = if (measurementType == MeasurementType.CO2) measurement.value else null,
                rco2 = if (measurementType == MeasurementType.CO2) measurement.value else null,
                sensorType = measurement.sensorType,
                ownerName = measurement.ownerName,
                dataSource = measurement.dataSource
            )

            val key = if (measurement.isCluster || locationId == 0) {
                "cluster_${"%.6f".format(coordinate.first)}_${"%.6f".format(coordinate.second)}"
            } else {
                "sensor_$locationId"
            }

            AirQualityAnnotation(
                key = key,
                locationId = locationId,
                coordinate = coordinate,
                title = measurement.locationName ?: UNKNOWN_LOCATION_LABEL,
                subtitle = buildAnnotationSubtitle(measurement.value, measurementType, measurementUnit),
                pm25 = if (measurementType == MeasurementType.PM25) measurement.value else null,
                co2 = if (measurementType == MeasurementType.CO2) measurement.value else null,
                isCluster = measurement.isCluster || locationId == 0,
                clusterCount = measurement.sensorsCount,
                sensorType = measurement.sensorType,
                measurementType = measurementType,
                airQualityLocation = location
            )
        }
    }

    private fun buildAnnotationSubtitle(
        value: Double?,
        measurementType: MeasurementType,
        displayUnit: AQIDisplayUnit
    ): String {
        val displayValue = EPAColorCoding.getDisplayValueForMeasurement(value, measurementType, displayUnit)

        return when (measurementType) {
            MeasurementType.PM25 -> when (displayUnit) {
                AQIDisplayUnit.UGM3 -> "PM2.5: $displayValue µg/m³"
                AQIDisplayUnit.USAQI -> "PM2.5: $displayValue US AQI"
            }

            MeasurementType.CO2 -> "CO2: $displayValue ppm"
        }
    }
    
    /**
     * Handle API errors with detailed logging
     */
    private fun handleApiError(error: NetworkError) {
        val errorMessage = when (error) {
            is NetworkError.NoInternetConnection -> "No internet connection available."
            is NetworkError.Timeout -> "Request timed out. Please check your connection."
            is NetworkError.ServerError -> "Server error (${error.code}). Please try again later."
            is NetworkError.UnknownError -> "Failed to load air quality data: ${error.throwable.message}"
        }
        
        debugLog { "API error surfaced: $errorMessage" }
        
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = errorMessage
        )
    }
    
    /**
     * Get debug info about current state
     */
    fun getDebugInfo(): String {
        val state = _uiState.value
        return """
            Debug Info:
            - Locations: ${state.locations.size}
            - Last Refresh: ${state.lastRefreshTime}
            - Measurement Type: ${state.measurementType.displayName}
            - Current Viewport: ${state.currentViewport}
            - API Calls This Minute: ${state.apiCallsThisMinute}
            - Is Loading: ${state.isLoading}
            - Error: ${state.error ?: "None"}
        """.trimIndent()
    }

    fun loadMapData(north: Double, south: Double, east: Double, west: Double) {
        loadMapDataWithZoom(north, south, east, west, zoomLevel = 8)
    }

    fun loadMapDataWithZoom(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        zoomLevel: Int,
        forceRefresh: Boolean = false
    ) {
        val bounds = MapBounds(north, south, east, west)
        lastBounds = bounds

        val viewportBounds = bounds.toViewportBounds()
        if (forceRefresh) {
            viewportManager.reset()
        }
        viewportManager.forceFetch(viewportBounds, zoomLevel.toDouble())
    }
    
    fun retry() {
        val viewport = _uiState.value.currentViewport ?: lastBounds?.toViewportBounds()
        viewport?.let {
            viewportManager.forceFetch(it, _uiState.value.lastZoomLevel)
        }
    }
    
    // Auto-refresh functionality
    fun startAutoRefresh() {
        viewModelScope.launch {
            delay(300000) // 5 minutes
            
            if (System.currentTimeMillis() - _uiState.value.lastRefreshTime > 60000) {
                lastBounds?.let { bounds ->
                    debugLog { "Auto-refreshing map data" }
                    loadMapData(bounds.north, bounds.south, bounds.east, bounds.west)
                }
            }
        }
    }
    
    // Search functionality
    fun updateSearchQuery(query: String) {
        debugLog { "Search query updated: '$query'" }
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        searchJob?.cancel()
        
        if (query.isEmpty()) {
            debugLog { "Empty search query - clearing results" }
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                isSearching = false
            )
            return
        }
        
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true)
            
            delay(300) // Debounce
            
            try {
                // Convert current map locations to sensor locations for search
                _uiState.value.locations.map { mapItem ->
                    SensorLocation(
                        id = mapItem.id,
                        name = mapItem.name,
                        latitude = mapItem.latitude,
                        longitude = mapItem.longitude,
                        pm25 = mapItem.pm25,
                        co2 = mapItem.co2,
                        isCluster = mapItem.isCluster,
                        sensorsCount = mapItem.sensorsCount,
                        ownerName = mapItem.ownerName
                    )
                }
                
                val results = searchService.search(query)
                debugLog { "Search completed: found ${results.size} results for '$query'" }
                _uiState.value = _uiState.value.copy(
                    searchResults = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
                Log.e(TAG, "Search error", e)
            }
        }
    }
    
    fun onLocationSelected(location: SearchResult) {
        debugLog { "Location selected: ${location.name} at (${location.latitude}, ${location.longitude})" }

        // Save to recent searches
        preferencesManager.saveRecentSearch(
            name = location.name,
            subtitle = location.subtitle,
            latitude = location.latitude,
            longitude = location.longitude
        )

        // Reload recent searches
        loadRecentSearches()

        // Clear search UI
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList()
        )

        when (location.type) {
            com.airgradient.android.data.services.LocationType.SENSOR -> {
                if (location.isCluster) {
                    // For clusters, zoom in to see individual sensors
                    _uiState.value = _uiState.value.copy(
                        mapCenter = location.latitude to location.longitude,
                        targetZoomLevel = 14.0 // High zoom for clusters to see individual sensors
                    )
                    
                    // Zoom to a smaller area to reveal individual sensors
                    val span = 0.02 // Smaller span for tighter zoom on cluster
                    val north = location.latitude + span
                    val south = location.latitude - span
                    val east = location.longitude + span
                    val west = location.longitude - span
                    
                    loadMapDataWithZoom(north, south, east, west, 14) // Higher zoom for individual sensors
                } else {
                    // For individual sensors, center map with very high zoom for detailed view
                    _uiState.value = _uiState.value.copy(
                        mapCenter = location.latitude to location.longitude,
                        targetZoomLevel = 17.0 // Very high zoom for individual sensors
                    )
                    
                    // Load data with higher zoom level for detailed individual sensor view
                    val span = 0.005 // Very small span for close-up view of individual sensor
                    val north = location.latitude + span
                    val south = location.latitude - span
                    val east = location.longitude + span
                    val west = location.longitude - span
                    
                    loadMapDataWithZoom(north, south, east, west, 17) // Very high zoom for individual sensor
                }
            }
            else -> {
                // Handle predefined cities/countries with appropriate zoom levels
                val zoomLevel = when (location.type) {
                    com.airgradient.android.data.services.LocationType.COUNTRY -> 6.0 // Country-level zoom
                    com.airgradient.android.data.services.LocationType.CITY -> 12.0 // City-level zoom
                    else -> 14.0 // Default high zoom for other locations
                }
                
                _uiState.value = _uiState.value.copy(
                    mapCenter = location.latitude to location.longitude,
                    targetZoomLevel = zoomLevel
                )
                
                val span = when (location.type) {
                    com.airgradient.android.data.services.LocationType.COUNTRY -> 3.0 // Smaller span for countries
                    com.airgradient.android.data.services.LocationType.CITY -> 0.2 // Smaller span for cities
                    else -> 0.05 // Smaller span for other locations
                }
                
                val north = location.latitude + span
                val south = location.latitude - span
                val east = location.longitude + span
                val west = location.longitude - span
                
                loadMapData(north, south, east, west)
            }
        }
    }

    fun onRecentSearchSelected(recentSearch: RecentSearch) {
        debugLog { "Recent search selected: ${recentSearch.name} at (${recentSearch.latitude}, ${recentSearch.longitude})" }

        // Clear search UI
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList()
        )

        // Center map on the location with city-level zoom
        _uiState.value = _uiState.value.copy(
            mapCenter = recentSearch.latitude to recentSearch.longitude,
            targetZoomLevel = 12.0
        )

        val span = 0.1
        val north = recentSearch.latitude + span
        val south = recentSearch.latitude - span
        val east = recentSearch.longitude + span
        val west = recentSearch.longitude - span

        loadMapDataWithZoom(north, south, east, west, 12)
    }

    fun onCurrentLocationRequested() {
        viewModelScope.launch {
            when (val result = locationService.getCurrentLocationOnly()) {
                is LocationServiceResult.Success -> {
                    val location = result.location
                    debugLog { "My location retrieved: ${location.latitude}, ${location.longitude}" }
                    _uiState.value = _uiState.value.copy(
                        mapCenter = location.latitude to location.longitude,
                        targetZoomLevel = 14.0, // City-level zoom for current location
                        error = null
                    )

                    // Load data around current location
                    val span = 0.5 // 0.5 degree span for current location
                    loadMapData(
                        north = location.latitude + span,
                        south = location.latitude - span,
                        east = location.longitude + span,
                        west = location.longitude - span
                    )

                    debugLog { "Moved to current location: ${location.latitude}, ${location.longitude}" }
                }
                is LocationServiceResult.PermissionDenied -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Location permission denied"
                    )
                }
                is LocationServiceResult.LocationDisabled -> {
                    _uiState.value = _uiState.value.copy(
                        showLocationSettingsDialog = true,
                        error = null
                    )
                }
                is LocationServiceResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message
                    )
                }
            }
        }
    }
    
    /**
     * Clear the target zoom level after it has been applied
     */
    fun clearTargetZoomLevel() {
        _uiState.value = _uiState.value.copy(targetZoomLevel = null)
    }

    /**
     * Get intent to open device location settings
     */
    fun getLocationSettingsIntent() = locationService.openLocationSettings()

    /**
     * Dismiss the location settings dialog
     */
    fun dismissLocationSettingsDialog() {
        _uiState.value = _uiState.value.copy(showLocationSettingsDialog = false)
    }

    fun onLocationPermissionDenied() {
        _uiState.value = _uiState.value.copy(error = "Location permission denied")
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        viewportManager.dispose()
        debugLog { "MapViewModel cleared and resources cleaned up" }
    }

    private inline fun debugLog(message: () -> String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message())
        }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val UNKNOWN_LOCATION_LABEL = "Unknown Location"
    }
}
