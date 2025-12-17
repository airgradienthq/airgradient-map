package com.airgradient.android.ui.settings.ViewModels

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.R
import com.airgradient.android.data.local.PushNotificationMetadataStore
import com.airgradient.android.data.local.UserProfilePreferences
import com.airgradient.android.data.services.AirQualityApiService
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.NotificationRegistration
import com.airgradient.android.domain.models.NotificationAlarmType
import com.airgradient.android.domain.models.UserProfile
import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.domain.repositories.AuthenticationRepository
import com.airgradient.android.domain.repositories.BookmarkRepository
import com.airgradient.android.domain.repositories.NotificationsRepository
import com.airgradient.android.domain.repositories.SettingsRepository
import com.airgradient.android.domain.repositories.WidgetLocationSettings
import java.util.Locale
import kotlinx.coroutines.flow.first
import com.airgradient.android.widget.AirQualityWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    val widgetLocation: String = "None",
    val availableLocations: List<String> = emptyList(),
    val notificationLocations: List<NotificationLocationItem> = emptyList(),
    val notificationsLoading: Boolean = false,
    val notificationsError: String? = null,
    val userProfile: UserProfile = UserProfile(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val authState: AuthState = AuthState.SignedOut
)

data class NotificationLocationItem(
    val locationId: Int,
    val displayName: String,
    val scheduledCount: Int,
    val hasThresholdAlert: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookmarkRepository: BookmarkRepository,
    private val notificationsRepository: NotificationsRepository,
    private val settingsRepository: SettingsRepository,
    private val userProfilePreferences: UserProfilePreferences,
    private val pushNotificationMetadataStore: PushNotificationMetadataStore,
    private val airQualityApiService: AirQualityApiService,
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {

    private val supportedDisplayUnits = setOf(AQIDisplayUnit.USAQI, AQIDisplayUnit.UGM3)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var bookmarkNameById: Map<Int, String> = emptyMap()
    private var latestNotificationRegistrations: List<NotificationRegistration> = emptyList()
    private var notificationsLoaded = false
    private var notificationsJob: Job? = null
    private val locationNameCache = mutableMapOf<Int, String>()

    private fun observeAuthentication() {
        viewModelScope.launch {
            authenticationRepository.authState.collect { state ->
                _uiState.update { current -> current.copy(authState = state) }
            }
        }
    }

    init {
        loadBookmarkedLocations()
        observeSettings()
        observeUserProfile()
        observeAuthentication()
        loadNotificationLocations()
    }
    
    private fun loadBookmarkedLocations() {
        viewModelScope.launch {
            bookmarkRepository.getAllBookmarks().collect { bookmarksWithData ->
                bookmarkNameById = bookmarksWithData.associate { it.bookmark.locationId to it.bookmark.locationName }
                val locationNames = bookmarkNameById.values.toList()
                _uiState.value = _uiState.value.copy(
                    availableLocations = locationNames
                )

                if (notificationsLoaded && latestNotificationRegistrations.isNotEmpty()) {
                    viewModelScope.launch {
                        publishNotificationLocations(latestNotificationRegistrations)
                    }
                }

                // If current widget location is not in bookmarks, reset to None
                val currentWidget = _uiState.value.widgetLocation
                if (currentWidget != "None" && !locationNames.contains(currentWidget)) {
                    updateWidgetLocation("None")
                }
            }
        }
    }

    private fun loadNotificationLocations() {
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            val playerId = pushNotificationMetadataStore.playerId()

            if (playerId.isNullOrBlank()) {
                latestNotificationRegistrations = emptyList()
                notificationsLoaded = true
                _uiState.value = _uiState.value.copy(
                    notificationLocations = emptyList(),
                    notificationsLoading = false,
                    notificationsError = null
                )
                return@launch
            }

            notificationsLoaded = false

            _uiState.value = _uiState.value.copy(
                notificationsLoading = true,
                notificationsError = null
            )

            locationNameCache.clear()
            notificationsRepository.fetchRegistrations(playerId, null)
                .onSuccess { registrations ->
                    latestNotificationRegistrations = registrations
                    notificationsLoaded = true
                    publishNotificationLocations(registrations)
                }
                .onFailure { error ->
                    latestNotificationRegistrations = emptyList()
                    notificationsLoaded = true
                    _uiState.value = _uiState.value.copy(
                        notificationLocations = emptyList(),
                        notificationsLoading = false,
                        notificationsError = error.message ?: context.getString(R.string.settings_notifications_error_generic)
                    )
                }
        }
    }

    fun refreshNotificationLocations() {
        loadNotificationLocations()
    }

    fun signOut() {
        viewModelScope.launch {
            authenticationRepository.signOut()
        }
    }

    private suspend fun publishNotificationLocations(
        registrations: List<NotificationRegistration>
    ) {
        val grouped = registrations.groupBy { it.locationId }
        val activeLocations = mutableListOf<NotificationLocationItem>()

        for ((locationId, regsForLocation) in grouped) {
            if (locationId <= 0) continue

            val resolvedName = resolveLocationName(locationId, regsForLocation)
            val scheduledCount = regsForLocation.count { registration ->
                registration.alarmType == NotificationAlarmType.SCHEDULED && registration.active
            }
            val hasThreshold = regsForLocation.any { registration ->
                registration.alarmType == NotificationAlarmType.THRESHOLD && registration.active
            }

            activeLocations += NotificationLocationItem(
                locationId = locationId,
                displayName = resolvedName,
                scheduledCount = scheduledCount,
                hasThresholdAlert = hasThreshold
            )
        }

        _uiState.value = _uiState.value.copy(
            notificationLocations = activeLocations.sortedBy { it.displayName.lowercase(Locale.getDefault()) },
            notificationsLoading = false,
            notificationsError = null
        )
    }

    private suspend fun resolveLocationName(
        locationId: Int,
        registrations: List<NotificationRegistration>
    ): String {
        locationNameCache[locationId]?.let { return it }

        val explicitName = registrations
            .mapNotNull { it.locationName?.takeIf { name -> name.isNotBlank() } }
            .firstOrNull()

        val resolved = explicitName
            ?: bookmarkNameById[locationId]
            ?: fetchLocationNameFromApi(locationId)
            ?: context.getString(R.string.unknown_location)

        locationNameCache[locationId] = resolved
        return resolved
    }

    private suspend fun fetchLocationNameFromApi(locationId: Int): String? {
        return try {
            val response = airQualityApiService.getLocationInfo(locationId)
            if (response.isSuccessful) {
                response.body()?.locationName?.takeIf { !it.isNullOrBlank() }?.trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    
    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.getDisplayUnit(),
                settingsRepository.getWidgetLocation()
            ) { displayUnit, widgetLocation ->
                Pair(displayUnit, widgetLocation)
            }.collect { (displayUnit, widgetLocation) ->
                val normalizedUnit = displayUnit.takeIf { it in supportedDisplayUnits }
                    ?: AQIDisplayUnit.USAQI

                if (normalizedUnit != displayUnit) {
                    settingsRepository.setDisplayUnit(normalizedUnit)
                }

                _uiState.value = _uiState.value.copy(
                    displayUnit = normalizedUnit,
                    widgetLocation = widgetLocation.locationName
                )
            }
        }
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            userProfilePreferences.userProfile().collect { profile ->
                _uiState.value = _uiState.value.copy(userProfile = profile)
            }
        }
    }

    fun updateDisplayUnit(unit: AQIDisplayUnit) {
        viewModelScope.launch {
            settingsRepository.setDisplayUnit(unit)
        }
    }

    fun updateWidgetLocation(location: String) {
        viewModelScope.launch {
            try {
                if (location == "None") {
                    settingsRepository.clearWidgetLocation()
                    Log.d("SettingsViewModel", "Cleared widget location")
                } else {
                    // Find the bookmark details
                    val bookmarks = bookmarkRepository.getAllBookmarks().first()
                    val bookmark = bookmarks.find { it.bookmark.locationName == location }?.bookmark

                    bookmark?.let {
                        val widgetSettings = WidgetLocationSettings(
                            locationName = it.locationName,
                            locationId = it.locationId,
                            latitude = it.coordinates.latitude,
                            longitude = it.coordinates.longitude
                        )
                        settingsRepository.setWidgetLocation(widgetSettings)
                        Log.d("SettingsViewModel", "Updated widget location to: ${it.locationName} (id: ${it.locationId})")
                    }
                }

                // Small delay to ensure DataStore write completes
                kotlinx.coroutines.delay(100)

                // Trigger widget update
                updateWidget()
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error updating widget location", e)
            }
        }
    }

    fun updateProfileFlag(flag: ProfileFlag, enabled: Boolean) {
        userProfilePreferences.updateProfile { profile ->
            when (flag) {
                ProfileFlag.VULNERABLE_GROUPS -> profile.copy(hasVulnerableGroups = enabled)
                ProfileFlag.PREEXISTING_CONDITIONS -> profile.copy(hasPreexistingConditions = enabled)
                ProfileFlag.EXERCISES_OUTDOORS -> profile.copy(exercisesOutdoors = enabled)
                ProfileFlag.OWNS_EQUIPMENT -> profile.copy(ownsProtectiveEquipment = enabled)
            }
        }
    }

    private fun updateWidget() {
        // Trigger widget update when location changes
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, AirQualityWidgetProvider::class.java)
        )
        if (widgetIds.isNotEmpty()) {
            val updateIntent = android.content.Intent(context, AirQualityWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(updateIntent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("SettingsViewModel", "SettingsViewModel cleared and resources cleaned up")
    }

}


enum class ProfileFlag {
    VULNERABLE_GROUPS,
    PREEXISTING_CONDITIONS,
    EXERCISES_OUTDOORS,
    OWNS_EQUIPMENT
}
