package com.airgradient.android.ui.locationdetail.ViewModels

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.R
import com.airgradient.android.data.local.PushNotificationMetadataStore
import com.airgradient.android.data.local.UserProfilePreferences
import com.airgradient.android.data.models.*
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.Coordinates
import com.airgradient.android.domain.models.FeaturedCommunityProject
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.UserProfile
import com.airgradient.android.domain.usecases.GetFeaturedCommunityInfoUseCase
import com.airgradient.android.domain.usecases.GetFeaturedCommunityProjectDetailUseCase
import com.airgradient.android.domain.usecases.GetFeaturedCommunityProjectsUseCase
import com.airgradient.android.domain.usecases.GetBookmarkStatusUseCase
import com.airgradient.android.domain.usecases.LoadCigaretteEquivalenceUseCase
import com.airgradient.android.domain.usecases.LoadHeatMapDataUseCase
import com.airgradient.android.domain.usecases.LoadLocationDetailUseCase
import com.airgradient.android.domain.usecases.LoadLocationHistoricalDataUseCase
import com.airgradient.android.domain.usecases.LoadWhoComplianceUseCase
import com.airgradient.android.domain.usecases.ToggleBookmarkUseCase
import com.airgradient.android.domain.repositories.NotificationsRepository
import com.airgradient.android.domain.repositories.SettingsRepository
import com.airgradient.android.ui.community.ViewModels.FeaturedProjectsUiState
import com.airgradient.android.ui.community.ViewModels.ProjectDetailUiState
import com.airgradient.android.ui.locationdetail.Utils.AirQualityInsightsCalculator
import com.airgradient.android.ui.locationdetail.Utils.ShareCardGenerator
import com.airgradient.android.ui.shared.ShareEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@HiltViewModel
class LocationDetailViewModel @Inject constructor(
    private val loadLocationDetailUseCase: LoadLocationDetailUseCase,
    private val loadLocationHistoricalDataUseCase: LoadLocationHistoricalDataUseCase,
    private val loadHeatMapDataUseCase: LoadHeatMapDataUseCase,
    private val loadWhoComplianceUseCase: LoadWhoComplianceUseCase,
    private val loadCigaretteEquivalenceUseCase: LoadCigaretteEquivalenceUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val getBookmarkStatusUseCase: GetBookmarkStatusUseCase,
    private val userProfilePreferences: UserProfilePreferences,
    @ApplicationContext private val context: Context,
    private val getFeaturedCommunityProjectsUseCase: GetFeaturedCommunityProjectsUseCase,
    private val getFeaturedCommunityProjectDetailUseCase: GetFeaturedCommunityProjectDetailUseCase,
    private val getFeaturedCommunityInfoUseCase: GetFeaturedCommunityInfoUseCase,
    private val settingsRepository: SettingsRepository,
    private val notificationsRepository: NotificationsRepository,
    private val pushNotificationMetadataStore: PushNotificationMetadataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationDetailUiState())
    val uiState: StateFlow<LocationDetailUiState> = _uiState.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ShareEvent> = _shareEvents.asSharedFlow()

    // Track the current loading location to prevent multiple simultaneous loads
    private var currentLoadingLocationId: Int? = null
    private var loadingJob: Job? = null
    private var heatMapJob: Job? = null
    private var notificationsJob: Job? = null
    private var lastHeatMapFetchAt: Long = 0L
    private var currentUserProfile: UserProfile = userProfilePreferences.currentProfile()
    private var shareGenerationJob: Job? = null
    private var preparedShare: PreparedShareCard? = null
    private var lastSelectedCommunityProject: FeaturedCommunityProject? = null
    private var communityProjectsJob: Job? = null
    private var featuredCommunityJob: Job? = null
    private var communityProjectsRaw: List<FeaturedCommunityProject> = emptyList()

    init {
        observeUserProfile()
        loadCommunityProjects()
        observeDisplayUnit()
    }

    private fun observeDisplayUnit() {
        viewModelScope.launch {
            settingsRepository.getDisplayUnit().collect { unit ->
                val normalizedUnit = when (unit) {
                    AQIDisplayUnit.UGM3 -> AQIDisplayUnit.UGM3
                    else -> AQIDisplayUnit.USAQI
                }
                val previousUnit = _uiState.value.displayUnit
                _uiState.value = _uiState.value.copy(displayUnit = normalizedUnit)
                if (previousUnit != normalizedUnit) {
                    _uiState.value.location?.let { location ->
                        prepareShareContent(location)
                    }
                }
            }
        }
    }

    /**
     * Show location detail bottom sheet for a specific location
     */
    fun showLocationDetail(locationId: Int, measurementType: MeasurementType = MeasurementType.PM25) {
        Log.d("LocationDetailVM", "showLocationDetail called with locationId: $locationId")
        
        // If we're already loading this location, don't start another load
        if (currentLoadingLocationId == locationId && loadingJob?.isActive == true) {
            Log.d("LocationDetailVM", "Already loading location $locationId, skipping duplicate request")
            return
        }
        
        // Cancel any previous loading job
        loadingJob?.cancel()
        notificationsJob?.cancel()
        featuredCommunityJob?.cancel()
        currentLoadingLocationId = locationId
        
        // Don't dismiss dialog here - just update the content
        
        loadingJob = viewModelScope.launch {
            // Small delay to debounce rapid clicks
            delay(100)
            
            // Set or keep visible, show loading state, and use measurement type from map
            _uiState.value = _uiState.value.copy(
                isVisible = true,
                isLoading = true,
                error = null,
                currentLocationId = locationId,
                location = null,  // Clear previous location while loading new one
                hasActiveNotifications = false,
                measurementType = measurementType,  // Use measurement type from map
                cigaretteEquivalence = CigaretteEquivalenceState(isLoading = true),
                heatMap = HeatMapUiState(isLoading = true, measurementType = measurementType),
                communityProjectDetail = ProjectDetailUiState(),
                featuredCommunity = FeaturedCommunityUiState(),
                shareState = ShareUiState()
            )
            Log.d("LocationDetailVM", "State updated: isVisible=true, isLoading=true")

            updateInsights(null)
            ensureCommunityProjectsLoaded()
            
            try {
                // Fetch location details
                val locationDetail = loadLocationDetailUseCase(locationId)
                refreshNotificationStatus(locationDetail.id)
                Log.d("LocationDetailVM", "Location data loaded: ${locationDetail.name}")
                
                // Update bookmark status
                val isBookmarked = getBookmarkStatusUseCase(locationDetail.id)
                
                _uiState.value = _uiState.value.copy(
                    location = locationDetail,
                    isLoading = false,
                    isBookmarked = isBookmarked
                )
                Log.d("LocationDetailVM", "State updated with location data, isLoading=false, isBookmarked=$isBookmarked")

                updateInsights(locationDetail)
                recalculateCommunityProjectDistances()

                fetchCigaretteEquivalence(locationDetail.id)
                
                // Check WHO compliance
                fetchWhoCompliance(locationDetail.id)
                
                // Load historical data
                loadHistoricalData(locationId)

                // Load heat map data
                loadHeatMapData(locationId)

                prepareShareContent(locationDetail)

                fetchFeaturedCommunity(locationDetail)

            } catch (e: Exception) {
                clearShareState()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load location details: ${e.message}",
                    cigaretteEquivalence = CigaretteEquivalenceState(error = true),
                    communityProjectDetail = ProjectDetailUiState(),
                    featuredCommunity = FeaturedCommunityUiState(error = true),
                    shareState = ShareUiState()
                )
                updateInsights(null)
            } finally {
                // Clear the loading state
                currentLoadingLocationId = null
            }
        }
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            userProfilePreferences.userProfile().collect { profile ->
                currentUserProfile = profile
                updateInsights(_uiState.value.location)
            }
        }
    }

    private fun updateInsights(location: LocationDetail?) {
        val result = AirQualityInsightsCalculator.calculate(location?.currentPM25, currentUserProfile)
        val insightsState = AirQualityInsightsState(
            actions = result.actions,
            accentColor = result.accentColor,
            mascotAssetName = result.mascotAssetName,
            hasValidData = result.hasValidData
        )

        _uiState.value = _uiState.value.copy(airQualityInsights = insightsState)
    }

    fun retryCommunityProjects() {
        loadCommunityProjects()
    }

    private fun ensureCommunityProjectsLoaded() {
        val current = _uiState.value.communityProjects
        if (communityProjectsJob?.isActive != true && (current.projects.isEmpty() || current.hasError)) {
            loadCommunityProjects()
        }
    }

    fun onCommunityProjectSelected(project: FeaturedCommunityProject) {
        lastSelectedCommunityProject = project
        _uiState.value = _uiState.value.copy(
            communityProjectDetail = ProjectDetailUiState(
                isVisible = true,
                isLoading = true,
                projectTitle = project.title
            )
        )
        loadCommunityProjectDetail(project)
    }

    fun dismissCommunityProjectDetail() {
        lastSelectedCommunityProject = null
        _uiState.value = _uiState.value.copy(communityProjectDetail = ProjectDetailUiState())
    }

    fun retryCommunityProjectDetail() {
        lastSelectedCommunityProject?.let { project ->
            _uiState.value = _uiState.value.copy(
                communityProjectDetail = _uiState.value.communityProjectDetail.copy(
                    isLoading = true,
                    error = false
                )
            )
            loadCommunityProjectDetail(project)
        }
    }

    private fun loadCommunityProjects(category: String? = "Community") {
        _uiState.value = _uiState.value.copy(
            communityProjects = FeaturedProjectsUiState(isLoading = true)
        )

        communityProjectsJob?.cancel()
        communityProjectsJob = viewModelScope.launch {
            val projectsState = try {
                val result = getFeaturedCommunityProjectsUseCase(Locale.getDefault(), category)
                if (result.isSuccess) {
                    val projects = result.getOrNull().orEmpty()
                    communityProjectsRaw = projects
                    val activeCoordinates = _uiState.value.location?.let { Coordinates(it.latitude, it.longitude) }
                    val (sortedProjects, distanceMap) = buildProjectsWithDistances(projects, activeCoordinates)
                    FeaturedProjectsUiState(
                        isLoading = false,
                        projects = sortedProjects,
                        isEmpty = sortedProjects.isEmpty(),
                        hasError = false,
                        projectDistances = distanceMap
                    )
                } else {
                    communityProjectsRaw = emptyList()
                    FeaturedProjectsUiState(
                        isLoading = false,
                        hasError = true
                    )
                }
            } catch (ex: Exception) {
                Log.e("LocationDetailVM", "Failed to load community projects", ex)
                communityProjectsRaw = emptyList()
                FeaturedProjectsUiState(isLoading = false, hasError = true)
            } finally {
                communityProjectsJob = null
            }

            _uiState.value = _uiState.value.copy(communityProjects = projectsState)
        }
    }

    private fun loadCommunityProjectDetail(project: FeaturedCommunityProject) {
        viewModelScope.launch {
            val result = getFeaturedCommunityProjectDetailUseCase(
                Locale.getDefault(),
                project.id,
                project.projectUrl
            )

            val detailState = if (result.isSuccess) {
                val detail = result.getOrNull()
                _uiState.value.communityProjectDetail.copy(
                    isLoading = false,
                    detail = detail,
                    error = false,
                    projectTitle = detail?.title ?: _uiState.value.communityProjectDetail.projectTitle
                )
            } else {
                _uiState.value.communityProjectDetail.copy(
                    isLoading = false,
                    error = true
                )
            }

            _uiState.value = _uiState.value.copy(communityProjectDetail = detailState)
        }
    }

    private fun buildProjectsWithDistances(
        projects: List<FeaturedCommunityProject>,
        coordinates: Coordinates?
    ): Pair<List<FeaturedCommunityProject>, Map<String, Double>> {
        if (coordinates == null) {
            val sorted = projects.sortedBy { it.title.lowercase(Locale.getDefault()) }
            return sorted to emptyMap()
        }

        val distanceMap = mutableMapOf<String, Double>()
        val comparator = compareBy<FeaturedCommunityProject> { project ->
            val distance = project.location?.let { loc ->
                calculateDistanceMeters(
                    coordinates.latitude,
                    coordinates.longitude,
                    loc.latitude,
                    loc.longitude
                )
            }
            if (distance != null) {
                distanceMap[project.id] = distance
            }
            distance ?: Double.POSITIVE_INFINITY
        }.thenBy { it.title.lowercase(Locale.getDefault()) }

        val sortedProjects = projects.sortedWith(comparator)
        return sortedProjects to distanceMap
    }

    private fun recalculateCommunityProjectDistances() {
        val coordinates = _uiState.value.location?.let { Coordinates(it.latitude, it.longitude) } ?: return
        if (communityProjectsRaw.isEmpty()) return

        val (sortedProjects, distanceMap) = buildProjectsWithDistances(communityProjectsRaw, coordinates)
        _uiState.value = _uiState.value.copy(
            communityProjects = _uiState.value.communityProjects.copy(
                projects = sortedProjects,
                projectDistances = distanceMap,
                isEmpty = sortedProjects.isEmpty()
            )
        )
    }

    private fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2.0) + cos(radLat1) * cos(radLat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    private suspend fun fetchCigaretteEquivalence(locationId: Int) {
        val currentState = _uiState.value.cigaretteEquivalence.copy(isLoading = true, error = false)
        _uiState.value = _uiState.value.copy(cigaretteEquivalence = currentState)

        val data = runCatching { loadCigaretteEquivalenceUseCase(locationId) }
            .onFailure { Log.e("LocationDetailVM", "Failed to load cigarette equivalence", it) }
            .getOrNull()

        val updatedState = if (data != null) {
            CigaretteEquivalenceState(
                isLoading = false,
                value30Days = data.last30days,
                error = false
            )
        } else {
            CigaretteEquivalenceState(isLoading = false, value30Days = null, error = true)
        }

        _uiState.value = _uiState.value.copy(cigaretteEquivalence = updatedState)
    }

    /**
     * Load historical data for charts
     */
    private fun loadHistoricalData(locationId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistorical = true)
            
            try {
                val historicalData = loadLocationHistoricalDataUseCase(
                    locationId = locationId,
                    timeframe = _uiState.value.chartTimeframe,
                    measurementType = _uiState.value.measurementType
                )
                
                _uiState.value = _uiState.value.copy(
                    historicalData = historicalData,
                    isLoadingHistorical = false
                )
            } catch (e: Exception) {
                // Continue showing current data even if historical fails
                _uiState.value = _uiState.value.copy(
                    isLoadingHistorical = false
                )
            }
        }
    }

    private fun fetchFeaturedCommunity(locationDetail: LocationDetail) {
        featuredCommunityJob?.cancel()

        val ownerId = locationDetail.ownerId ?: locationDetail.organization?.id
        if (ownerId == null || ownerId <= 0) {
            _uiState.value = _uiState.value.copy(featuredCommunity = FeaturedCommunityUiState())
            return
        }

        _uiState.value = _uiState.value.copy(
            featuredCommunity = FeaturedCommunityUiState(isLoading = true)
        )

        featuredCommunityJob = viewModelScope.launch {
            val locale = currentLocale()
            val result = getFeaturedCommunityInfoUseCase(ownerId, locale)

            result.onSuccess { info ->
                _uiState.value = _uiState.value.copy(
                    featuredCommunity = FeaturedCommunityUiState(info = info)
                )
            }.onFailure {
                Log.e("LocationDetailVM", "Failed to load featured community", it)
                _uiState.value = _uiState.value.copy(
                    featuredCommunity = FeaturedCommunityUiState(error = true)
                )
            }
        }
    }

    private fun loadHeatMapData(locationId: Int) {
        val now = System.currentTimeMillis()
        if (now - lastHeatMapFetchAt < 500) {
            return
        }
        lastHeatMapFetchAt = now

        heatMapJob?.cancel()
        heatMapJob = viewModelScope.launch {
            val loadingState = _uiState.value.heatMap.copy(
                isLoading = true,
                error = false,
                measurementType = _uiState.value.measurementType
            )
            _uiState.value = _uiState.value.copy(heatMap = loadingState)

            when (val result = loadHeatMapDataUseCase(locationId, _uiState.value.measurementType)) {
                is ApiResult.Success -> {
                    val response = result.data
                    _uiState.value = _uiState.value.copy(
                        heatMap = HeatMapUiState(
                            cells = response.points,
                            isLoading = false,
                            error = false,
                            measurementType = _uiState.value.measurementType,
                            displayUnit = response.displayUnit,
                            baseEpochMillis = response.generatedAt
                        )
                    )
                }

                is ApiResult.Error -> {
                    Log.e("LocationDetailVM", "Failed to load heat map data: ${result.error}")
                    _uiState.value = _uiState.value.copy(
                        heatMap = _uiState.value.heatMap.copy(
                            isLoading = false,
                            error = true,
                            cells = emptyList(),
                            baseEpochMillis = System.currentTimeMillis()
                        )
                    )
                }

                is ApiResult.Loading -> {
                    // No additional action required
                }
            }
        }
    }
    /**
     * Update chart timeframe (Hourly/Daily)
     */
    fun updateChartTimeframe(timeframe: ChartTimeframe) {
        if (_uiState.value.chartTimeframe != timeframe) {
            _uiState.value = _uiState.value.copy(chartTimeframe = timeframe)
            
            // Reload historical data for new timeframe
            _uiState.value.location?.let {
                loadHistoricalData(it.id)
            }
        }
    }

    private fun fetchWhoCompliance(locationId: Int) {
        viewModelScope.launch {
            val compliance = runCatching { loadWhoComplianceUseCase(locationId) }
                .onFailure { Log.e("LocationDetailVM", "Failed to load WHO compliance", it) }
                .getOrNull()

            _uiState.value = _uiState.value.copy(
                whoCompliance = compliance
            )
        }
    }
    
    private fun refreshNotificationStatus(locationId: Int) {
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            val playerId = pushNotificationMetadataStore.playerId()
            if (playerId.isNullOrBlank()) {
                updateActiveNotifications(false, locationId)
                return@launch
            }

            notificationsRepository.fetchLocationSettings(playerId, locationId)
                .onSuccess { settings ->
                    val hasActive = settings.schedules.any { it.isActive } ||
                        (settings.threshold?.isEnabled == true)
                    updateActiveNotifications(hasActive, locationId)
                }
                .onFailure { error ->
                    Log.w("LocationDetailVM", "Failed to fetch notification status", error)
                    updateActiveNotifications(false, locationId)
                }
        }
    }

    private fun updateActiveNotifications(hasActive: Boolean, locationId: Int) {
        if (_uiState.value.currentLocationId == locationId) {
            _uiState.value = _uiState.value.copy(hasActiveNotifications = hasActive)
        }
    }

    /**
     * Toggle measurement type between PM2.5 and CO2
     */
    fun toggleMeasurementType() {
        val newType = if (_uiState.value.measurementType == MeasurementType.PM25) {
            MeasurementType.CO2
        } else {
            MeasurementType.PM25
        }
        
        _uiState.value = _uiState.value.copy(measurementType = newType)
        
        // Reload data for new measurement type
        _uiState.value.location?.let {
            loadHistoricalData(it.id)
        }
    }

    /**
     * Share location data
     */
    fun shareLocation() {
        Log.d("LocationDetailVM", "=== shareLocation() START ===")
        val location = _uiState.value.location
        if (location == null) {
            Log.w("LocationDetailVM", "shareLocation() called but location is null")
            return
        }
        Log.d("LocationDetailVM", "Location found: ${location.name}")

        val payload = preparedShare
        Log.d("LocationDetailVM", "Prepared share payload: ${if (payload == null) "NULL" else "EXISTS (uri=${payload.uri})"}")

        if (payload == null) {
            Log.d("LocationDetailVM", "Share payload not ready, preparing content...")
            prepareShareContent(location, autoShare = true)
            return
        }

        Log.d("LocationDetailVM", "Share payload ready, emitting share event")
        emitShareEvent(payload)
    }
    
    /**
     * Expand/Collapse the bottom sheet
     */
    fun setExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isExpanded = expanded)
    }
    
    /**
     * Dismiss the bottom sheet
     */
    fun dismissDialog() {
        // Cancel any ongoing loading
        loadingJob?.cancel()
        notificationsJob?.cancel()
        currentLoadingLocationId = null
        clearShareState()
        _uiState.value = LocationDetailUiState(displayUnit = _uiState.value.displayUnit) // Reset to initial state while preserving display unit
    }
    
    /**
     * Refresh current location data
     */
    fun refreshData() {
        _uiState.value.location?.let { location ->
            showLocationDetail(location.id)
        }
    }

    fun refreshNotificationStatus() {
        _uiState.value.currentLocationId?.let { locationId ->
            refreshNotificationStatus(locationId)
        }
    }

    
    /**
     * Check if current location is bookmarked
     */
    fun isBookmarked(locationId: Int): Boolean {
        val currentLocation = _uiState.value.location
        return currentLocation?.id == locationId && _uiState.value.isBookmarked
    }
    
    /**
     * Toggle bookmark for current location
     */
    fun toggleBookmark() {
        _uiState.value.location?.let { location ->
            viewModelScope.launch {
                val result = runCatching { toggleBookmarkUseCase(location, _uiState.value.isBookmarked) }
                result.onSuccess { updatedStatus ->
                    _uiState.value = _uiState.value.copy(isBookmarked = updatedStatus)
                }.onFailure {
                    Log.e("LocationDetailVM", "Failed to toggle bookmark", it)
                }
            }
        }
    }

    private fun prepareShareContent(location: LocationDetail, autoShare: Boolean = false) {
        Log.d("LocationDetailVM", "prepareShareContent() called for location: ${location.name}")
        val pm25 = location.currentPM25
        val latitude = location.latitude
        val longitude = location.longitude
        val hasValidPm = pm25.isFinite() && pm25 >= 0
        val hasValidCoordinates = latitude in -90.0..90.0 && longitude in -180.0..180.0

        if (!hasValidPm || !hasValidCoordinates) {
            Log.w(
                "LocationDetailVM",
                "Invalid data for share - pm=$pm25 (valid=$hasValidPm) coordsValid=$hasValidCoordinates"
            )
            shareGenerationJob?.cancel()
            preparedShare?.let { deleteFileSafe(it.file) }
            preparedShare = null
            updateShareState(
                ShareUiState(
                    isGenerating = false,
                    isReady = false,
                    errorMessage = context.getString(R.string.error_share_failed)
                )
            )
            return
        }

        shareGenerationJob?.cancel()
        shareGenerationJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            updateShareState(ShareUiState(isGenerating = true, isReady = false))

            runCatching { generateSharePayload(location) }
                .onSuccess { payload ->
                    preparedShare?.let { deleteFileSafe(it.file) }
                    preparedShare = payload
                    updateShareState(ShareUiState(isGenerating = false, isReady = true))
                    if (autoShare) emitShareEvent(payload)
                }
                .onFailure { throwable ->
                    Log.e("LocationDetailVM", "Failed to prepare share content", throwable)
                    preparedShare?.let { deleteFileSafe(it.file) }
                    preparedShare = null
                    updateShareState(
                        ShareUiState(
                            isGenerating = false,
                            isReady = false,
                            errorMessage = context.getString(R.string.error_share_failed)
                        )
                    )
                }

            if (preparedShare != null && autoShare) {
                emitShareEvent(preparedShare!!)
            }
        }
    }

    private suspend fun generateSharePayload(location: LocationDetail): PreparedShareCard {
        val displayUnit = _uiState.value.displayUnit
        val mapUrl = String.format(
            Locale.US,
            "https://www.airgradient.com/map/?zoom=10&lat=%1$.4f&long=%2$.4f",
            location.latitude,
            location.longitude
        )

        val subject = location.name.takeUnless { it.isBlank() }
            ?: context.getString(R.string.unknown_location)
        val message = context.getString(R.string.share_message_with_link, mapUrl)

        val mapTitle = context.getString(R.string.airgradient_map_title)
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", currentLocale())
        val dateLabel = LocalDate.now().format(dateFormatter)

        val bitmap = ShareCardGenerator.renderShareCard(
            context = context,
            location = location,
            displayUnit = displayUnit,
            mapTitle = mapTitle,
            dateLabel = dateLabel
        )

        val file = withContext(Dispatchers.IO) {
            ShareCardGenerator.saveShareCard(context, bitmap)
        }
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)

        return PreparedShareCard(subject = subject, message = message, uri = uri, file = file)
    }

    private fun updateShareState(state: ShareUiState) {
        _uiState.value = _uiState.value.copy(shareState = state)
    }

    private fun clearShareState() {
        preparedShare?.let { deleteFileSafe(it.file) }
        preparedShare = null
        shareGenerationJob?.cancel()
        updateShareState(ShareUiState())
    }

    private fun emitShareEvent(payload: PreparedShareCard) {
        viewModelScope.launch {
            val shareEvent = if (payload.uri != null) {
                Log.d("LocationDetailVM", "Creating Image share event")
                ShareEvent.Image(payload.subject, payload.message, payload.uri)
            } else {
                Log.d("LocationDetailVM", "Creating Text share event")
                ShareEvent.Text(payload.subject, payload.message)
            }
            Log.d("LocationDetailVM", "Emitting share event: ${shareEvent::class.simpleName}")
            _shareEvents.emit(shareEvent)
            Log.d("LocationDetailVM", "=== Share event emitted successfully ===")
        }
    }

    private fun currentLocale(): Locale {
        val configuration = context.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0] ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            configuration.locale ?: Locale.getDefault()
        }
    }

    private fun deleteFileSafe(file: File?) {
        file ?: return
        runCatching { file.delete() }
    }

    private data class PreparedShareCard(
        val subject: String,
        val message: String,
        val uri: Uri?,
        val file: File?
    )

    override fun onCleared() {
        super.onCleared()
        shareGenerationJob?.cancel()
        clearShareState()
        loadingJob?.cancel()
        currentLoadingLocationId = null
        heatMapJob?.cancel()
        featuredCommunityJob?.cancel()
        Log.d("LocationDetailVM", "LocationDetailViewModel cleared and resources cleaned up")
    }
}
