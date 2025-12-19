package com.airgradient.android.ui.location.ViewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.BuildConfig
import com.airgradient.android.data.local.PushNotificationMetadataStore
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.LocationNotificationSettings
import com.airgradient.android.domain.models.NotificationAlarmType
import com.airgradient.android.domain.models.NotificationUpsertRequest
import com.airgradient.android.domain.models.NotificationWeekday
import com.airgradient.android.domain.repositories.SettingsRepository
import com.airgradient.android.domain.models.ScheduledNotification
import com.airgradient.android.domain.models.ThresholdAlertFrequency
import com.airgradient.android.domain.repositories.NotificationsRepository
import com.onesignal.OneSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
    private val metadataStore: PushNotificationMetadataStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    private var currentLocationId: Int? = null
    private var currentLocationName: String = ""
    private var currentPlayerId: String? = null
    private var displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI

    fun initialize(locationId: Int, locationName: String) {
        val shouldReload = currentLocationId != locationId
        currentLocationId = locationId
        currentLocationName = locationName
        if (shouldReload || _uiState.value.scheduledNotifications.isEmpty()) {
            loadSettings()
        }
    }

    fun refresh() {
        loadSettings()
    }

    private fun loadSettings() {
        var playerId = metadataStore.playerId()
        currentPlayerId = playerId

        viewModelScope.launch {
            if (playerId.isNullOrBlank()) {
                Log.d(TAG, "BuildConfig.ONE_SIGNAL_APP_ID='${'$'}{BuildConfig.ONE_SIGNAL_APP_ID}'")
                Log.d(TAG, "No cached OneSignal playerId; attempting to resolve from SDK")
                val resolvedPlayerId = resolveOneSignalPlayerId()
                if (!resolvedPlayerId.isNullOrBlank()) {
                    metadataStore.updatePlayerId(resolvedPlayerId)
                    playerId = resolvedPlayerId
                    currentPlayerId = resolvedPlayerId
                    Log.d(TAG, "Resolved playerId from OneSignal SDK: $resolvedPlayerId")
                }
            }

            displayUnit = settingsRepository.getDisplayUnit().first()

            if (playerId.isNullOrBlank()) {
                Log.w(
                    TAG,
                    "Unable to obtain OneSignal playerId; marking state as missing to trigger permission request"
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    locationName = currentLocationName,
                    playerIdMissing = true,
                    errorMessage = "Push notification registration is required before configuring alerts."
                )
                return@launch
            }

            val locationId = currentLocationId ?: return@launch

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                locationName = currentLocationName,
                errorMessage = null,
                playerIdMissing = false
            )

            repository.fetchLocationSettings(playerId, locationId)
                .onSuccess { settings ->
                    _uiState.value = mapSettingsToState(settings)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to fetch notification settings", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to load notifications"
                    )
                }
        }
    }

    private fun resolveOneSignalPlayerId(): String? {
        return if (OneSignal.isInitialized) {
            runCatching { OneSignal.User.pushSubscription.id }
                .onFailure { error ->
                    Log.e(TAG, "Failed to read OneSignal playerId", error)
                }
                .getOrNull()
        } else {
            Log.w(TAG, "OneSignal SDK not initialized when resolving playerId")
            null
        }
    }

    private fun mapSettingsToState(settings: LocationNotificationSettings): NotificationSettingsUiState {
        val schedules = settings.schedules.map { it.toUiModel() }
        val threshold = settings.threshold

        return NotificationSettingsUiState(
            isLoading = false,
            locationName = settings.locationName.ifEmpty { currentLocationName },
            scheduledNotifications = schedules,
            thresholdValueUg = threshold?.thresholdValueUg,
            thresholdFrequency = threshold?.frequency ?: ThresholdAlertFrequency.ONLY_ONCE,
            thresholdEnabled = threshold?.isEnabled ?: false,
            thresholdRegistrationId = threshold?.registrationId,
            displayUnit = displayUnit,
            errorMessage = null,
            playerIdMissing = false
        )
    }

    fun addSchedule(time: String, selectedDays: Set<NotificationWeekday>) {
        val playerId = currentPlayerId ?: return signalPlayerIdMissing()
        val locationId = currentLocationId ?: return

        val request = NotificationUpsertRequest(
            userId = DEFAULT_USER_ID,
            locationId = locationId,
            alarmType = NotificationAlarmType.SCHEDULED,
            isActive = true,
            unit = displayUnit,
            scheduledTime = time,
            scheduledTimezone = TimeZone.getDefault().id,
            scheduledDays = selectedDays
        )

        executeUpsert(playerId, request)
    }

    fun updateSchedule(
        original: ScheduledNotificationUiModel,
        time: String,
        selectedDays: Set<NotificationWeekday>
    ) {
        val playerId = currentPlayerId ?: return signalPlayerIdMissing()
        val locationId = currentLocationId ?: return

        val timezone = original.timezone.ifBlank { TimeZone.getDefault().id }

        val request = NotificationUpsertRequest(
            userId = DEFAULT_USER_ID,
            locationId = locationId,
            alarmType = NotificationAlarmType.SCHEDULED,
            isActive = original.isEnabled,
            unit = displayUnit,
            scheduledTime = time,
            scheduledTimezone = timezone,
            scheduledDays = selectedDays,
            registrationId = original.registrationId
        )

        executeUpsert(playerId, request)
    }

    fun toggleSchedule(model: ScheduledNotificationUiModel, isEnabled: Boolean) {
        val playerId = currentPlayerId ?: return signalPlayerIdMissing()
        val locationId = currentLocationId ?: return

        if (!isEnabled) {
            val registrationId = model.registrationId
            if (registrationId != null) {
                deleteSchedule(registrationId)
            } else {
                val updated = _uiState.value.scheduledNotifications.map {
                    if (it.registrationId == model.registrationId) it.copy(isEnabled = false) else it
                }
                _uiState.value = _uiState.value.copy(scheduledNotifications = updated)
            }
            return
        }

        val request = NotificationUpsertRequest(
            userId = DEFAULT_USER_ID,
            locationId = locationId,
            alarmType = NotificationAlarmType.SCHEDULED,
            isActive = isEnabled,
            unit = displayUnit,
            scheduledTime = model.time,
            scheduledTimezone = model.timezone,
            scheduledDays = model.days,
            registrationId = model.registrationId
        )

        executeUpsert(playerId, request)
    }

    fun deleteSchedule(registrationId: Int) {
        val playerId = currentPlayerId ?: return signalPlayerIdMissing()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.deleteRegistration(playerId, registrationId)
                .onSuccess { loadSettings() }
                .onFailure { error ->
                    Log.e(TAG, "Failed to delete schedule", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to delete schedule"
                    )
                }
        }
    }

    fun updateThreshold(
        thresholdValueUg: Double,
        frequency: ThresholdAlertFrequency,
        enabled: Boolean
    ) {
        val playerId = currentPlayerId ?: return signalPlayerIdMissing()
        val locationId = currentLocationId ?: return

        if (!enabled) {
            val registrationId = _uiState.value.thresholdRegistrationId
            if (registrationId != null) {
                deleteThreshold(playerId, registrationId)
            } else {
                _uiState.value = _uiState.value.copy(
                    thresholdEnabled = false,
                    thresholdRegistrationId = null,
                    thresholdValueUg = thresholdValueUg,
                    thresholdFrequency = frequency
                )
            }
            return
        }

        val request = NotificationUpsertRequest(
            userId = DEFAULT_USER_ID,
            locationId = locationId,
            alarmType = NotificationAlarmType.THRESHOLD,
            isActive = enabled,
            unit = displayUnit,
            thresholdValueUg = thresholdValueUg,
            thresholdFrequency = frequency,
            registrationId = _uiState.value.thresholdRegistrationId
        )

        // Optimistic update
        _uiState.value = _uiState.value.copy(
            thresholdValueUg = thresholdValueUg,
            thresholdFrequency = frequency,
            thresholdEnabled = enabled,
            isLoading = true
        )

        executeUpsert(playerId, request)
    }

    private fun executeUpsert(playerId: String, request: NotificationUpsertRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.upsertRegistration(playerId, request)
                .onSuccess {
                    loadSettings()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to upsert notification registration", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to save notification"
                    )
                }
        }
    }

    private fun deleteThreshold(playerId: String, registrationId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.deleteRegistration(playerId, registrationId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        thresholdEnabled = false,
                        thresholdRegistrationId = null
                    )
                    loadSettings()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to delete threshold registration", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Unable to update threshold"
                    )
                }
        }
    }

    private fun signalPlayerIdMissing() {
        _uiState.value = _uiState.value.copy(
            playerIdMissing = true,
            errorMessage = "Push notification registration is required before configuring alerts."
        )
    }

    private fun ScheduledNotification.toUiModel(): ScheduledNotificationUiModel {
        return ScheduledNotificationUiModel(
            registrationId = registrationId,
            time = time,
            timezone = timezone,
            days = selectedDays,
            isEnabled = isActive
        )
    }

    companion object {
        private const val TAG = "NotificationSettingsVM"
        private const val DEFAULT_USER_ID = "dummyUser"

    }
}

data class NotificationSettingsUiState(
    val isLoading: Boolean = false,
    val locationName: String = "",
    val scheduledNotifications: List<ScheduledNotificationUiModel> = emptyList(),
    val thresholdValueUg: Double? = null,
    val thresholdFrequency: ThresholdAlertFrequency = ThresholdAlertFrequency.ONLY_ONCE,
    val thresholdEnabled: Boolean = false,
    val thresholdRegistrationId: Int? = null,
    val displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    val errorMessage: String? = null,
    val playerIdMissing: Boolean = false
)

data class ScheduledNotificationUiModel(
    val registrationId: Int?,
    val time: String,
    val timezone: String,
    val days: Set<NotificationWeekday>,
    val isEnabled: Boolean
)
