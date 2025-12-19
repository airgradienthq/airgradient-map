package com.airgradient.android.ui.mylocations.ViewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.R
import com.airgradient.android.domain.models.AirQualityMeasurement
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.Location
import com.airgradient.android.domain.repositories.BookmarkRepository
import com.airgradient.android.domain.repositories.SettingsRepository
import com.airgradient.android.domain.usecases.GetCurrentMeasurementsUseCase
import com.airgradient.android.domain.usecases.GetLocationDetailsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.ZoneOffset
import androidx.annotation.StringRes

data class MyLocationsUiState(
    val isLoading: Boolean = false,
    val bookmarkedLocations: List<BookmarkedLocationWithData> = emptyList(),
    val error: String? = null,
    val sortOption: MyLocationsSortOption = MyLocationsSortOption.ALPHABETICAL,
    val displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI
)

@HiltViewModel
class MyLocationsViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val getCurrentMeasurementsUseCase: GetCurrentMeasurementsUseCase,
    private val getLocationDetailsUseCase: GetLocationDetailsUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyLocationsUiState())
    val uiState: StateFlow<MyLocationsUiState> = _uiState.asStateFlow()

    init {
        // Load bookmarks immediately on init
        loadBookmarkedLocations()
        observeDisplayUnit()

        // Also observe bookmark changes for updates
        viewModelScope.launch {
            bookmarkRepository.getAllBookmarks().collect { bookmarksWithData ->
                val bookmarks = bookmarksWithData.map { it.bookmark }
                Log.d(TAG, "Bookmarks updated: ${bookmarks.size} bookmarks")
                if (bookmarks.isNotEmpty()) {
                    loadBookmarkedLocations()
                } else {
                    _uiState.value = _uiState.value.copy(
                        bookmarkedLocations = emptyList(),
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeDisplayUnit() {
        viewModelScope.launch {
            settingsRepository.getDisplayUnit().collect { unit ->
                val normalizedUnit = when (unit) {
                    AQIDisplayUnit.UGM3 -> AQIDisplayUnit.UGM3
                    AQIDisplayUnit.USAQI -> AQIDisplayUnit.USAQI
                }
                _uiState.value = _uiState.value.copy(displayUnit = normalizedUnit)
            }
        }
    }

    fun loadBookmarkedLocations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Get current bookmarks using first() instead of collect() to avoid blocking
            val bookmarksFromRepo = bookmarkRepository.getAllBookmarks().first()
            val bookmarks: List<com.airgradient.android.domain.repositories.BookmarkedLocation> =
                bookmarksFromRepo.map { it.bookmark }

            if (bookmarks.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    bookmarkedLocations = emptyList(),
                    isLoading = false
                )
                return@launch
            }

            // Load current data and location info for each bookmarked location in parallel
            val bookmarkedLocationsWithData = bookmarks.map { domainBookmark ->
                async {
                    // Fetch current measurements
                    val measurementResult = try {
                        getCurrentMeasurementsUseCase(domainBookmark.locationId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading current data for location ${domainBookmark.locationId}", e)
                        Result.failure(e)
                    }

                    val locationResult = try {
                        getLocationDetailsUseCase(domainBookmark.locationId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading location info for ${domainBookmark.locationId}", e)
                        Result.failure(e)
                    }

                    val measurement = measurementResult.getOrNull().also { data ->
                        if (measurementResult.isSuccess && data != null) {
                            Log.d(TAG, "Loaded current data for ${domainBookmark.locationId}: pm25=${data.pm25}, pm10=${data.pm10}, co2=${data.co2}, temp=${data.temperature}")
                        } else if (measurementResult.isFailure) {
                            Log.e(TAG, "Failed to load current data for location ${domainBookmark.locationId}", measurementResult.exceptionOrNull())
                        }
                    }

                    val location = locationResult.getOrNull().also { info ->
                        if (locationResult.isSuccess && info != null) {
                            Log.d(TAG, "Loaded location info for ${domainBookmark.locationId}: owner=${info.organizationInfo?.publicName}")
                        } else if (locationResult.isFailure) {
                            Log.e(TAG, "Failed to load location info for ${domainBookmark.locationId}", locationResult.exceptionOrNull())
                        }
                    }

                    // Convert domain bookmark to old data model for UI compatibility
                    val oldBookmark = com.airgradient.android.data.services.BookmarkedLocation(
                        locationId = domainBookmark.locationId,
                        locationName = domainBookmark.locationName,
                        latitude = domainBookmark.coordinates.latitude,
                        longitude = domainBookmark.coordinates.longitude,
                        addedAt = domainBookmark.addedAt
                    )

                    BookmarkedLocationWithData(
                        bookmark = oldBookmark,
                        location = location,
                        currentMeasurement = measurement,
                        lastUpdate = measurement?.timestamp?.atOffset(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
                    )
                }
            }.awaitAll()

            _uiState.value = _uiState.value.copy(
                bookmarkedLocations = bookmarkedLocationsWithData,
                isLoading = false
            )
            
            Log.d(TAG, "Loaded ${bookmarkedLocationsWithData.size} bookmarked locations")
            bookmarkedLocationsWithData.forEach { loc ->
                Log.d(
                    TAG,
                    "Location ${loc.bookmark.locationId}: owner=${loc.location?.organizationInfo?.publicName}, pm25=${loc.currentMeasurement?.pm25}"
                )
            }
        }
    }
    
    fun removeBookmark(locationId: Int) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(locationId)
            Log.d(TAG, "Removed bookmark for location $locationId")
        }
    }
    
    fun refresh() {
        loadBookmarkedLocations()
    }

    fun updateSortOption(option: MyLocationsSortOption) {
        val currentState = _uiState.value
        if (currentState.sortOption == option) return

        _uiState.value = currentState.copy(
            sortOption = option
        )
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "MyLocationsViewModel cleared and resources cleaned up")
    }

    companion object {
        private const val TAG = "MyLocationsViewModel"
    }
}

data class BookmarkedLocationWithData(
    val bookmark: com.airgradient.android.data.services.BookmarkedLocation,
    val location: Location? = null,
    val currentMeasurement: AirQualityMeasurement? = null,
    val lastUpdate: Long? = null
)


enum class MyLocationsSortOption(@StringRes val labelRes: Int) {
    ALPHABETICAL(R.string.my_locations_sort_name),
    AIR_QUALITY(R.string.my_locations_sort_air_quality),
    OUTDOOR_FIRST(R.string.my_locations_sort_outdoor_first),
    GROUPED(R.string.my_locations_sort_grouped);

    companion object {
        val signedOutOptions: List<MyLocationsSortOption> = listOf(ALPHABETICAL, AIR_QUALITY)
        val signedInOptions: List<MyLocationsSortOption> = listOf(ALPHABETICAL, AIR_QUALITY, OUTDOOR_FIRST, GROUPED)
    }
}
