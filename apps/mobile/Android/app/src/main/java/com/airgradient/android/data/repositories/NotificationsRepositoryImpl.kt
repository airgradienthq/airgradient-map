package com.airgradient.android.data.repositories

import android.util.Log
import com.airgradient.android.data.models.NotificationRegistrationDTO
import com.airgradient.android.data.models.NotificationRegistrationPayload
import com.airgradient.android.data.models.NotificationRegistrationsEnvelope
import com.airgradient.android.data.models.toDomain
import com.airgradient.android.data.models.toPayload
import com.airgradient.android.data.services.NotificationApiService
import com.airgradient.android.domain.models.LocationNotificationSettings
import com.airgradient.android.domain.models.NotificationAlarmType
import com.airgradient.android.domain.models.NotificationRegistration
import com.airgradient.android.domain.models.NotificationRepeatCycle
import com.airgradient.android.domain.models.NotificationUpsertRequest
import com.airgradient.android.domain.models.NotificationWeekday
import com.airgradient.android.domain.models.ScheduledNotification
import com.airgradient.android.domain.models.ThresholdAlertFrequency
import com.airgradient.android.domain.models.ThresholdAlertSettings
import com.airgradient.android.domain.repositories.NotificationsRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class NotificationsRepositoryImpl @Inject constructor(
    private val apiService: NotificationApiService,
    private val gson: Gson
) : NotificationsRepository {

    override suspend fun fetchRegistrations(
        playerId: String,
        locationId: Int?
    ): Result<List<NotificationRegistration>> = runCatching {
        val response = apiService.getRegistrations(playerId = playerId, locationId = locationId)
        if (!response.isSuccessful) {
            val code = response.code()
            val body = response.errorBody()?.string()
            throw NotificationApiException("Failed to fetch registrations", code, body)
        }

        val dtos = parseRegistrations(response.body()).getOrElse { throw it }
        dtos.mapNotNull { it.toDomain() }
    }

    override suspend fun fetchLocationSettings(
        playerId: String,
        locationId: Int
    ): Result<LocationNotificationSettings> = fetchRegistrations(playerId, locationId).map { registrations ->
        val locationName = registrations.firstOrNull { !it.locationName.isNullOrEmpty() }?.locationName ?: ""

        val schedules = registrations
            .filter { it.alarmType == NotificationAlarmType.SCHEDULED }
            .mapNotNull { it.toScheduledNotification() }

        val thresholdSettings = registrations
            .firstOrNull { it.alarmType == NotificationAlarmType.THRESHOLD }
            ?.toThresholdSettings()

        LocationNotificationSettings(
            locationId = locationId,
            locationName = locationName,
            schedules = schedules,
            threshold = thresholdSettings
        )
    }

    override suspend fun upsertRegistration(
        playerId: String,
        request: NotificationUpsertRequest
    ): Result<NotificationRegistration> = runCatching {
        val payload = request.toPayload(playerId)

        if (request.registrationId != null) {
            val response = apiService.updateRegistration(playerId, request.registrationId, payload)
            return@runCatching response.toDomainOrThrow()
        }

        val createResponse = apiService.createRegistration(payload)
        when {
            createResponse.isSuccessful -> createResponse.toDomainOrThrow()
            createResponse.code() == 409 -> resolveConflictAndUpdate(playerId, request, payload)
            else -> throw NotificationApiException(
                message = "Failed to create registration",
                statusCode = createResponse.code(),
                errorBody = createResponse.errorBody()?.string()
            )
        }
    }

    override suspend fun deleteRegistration(
        playerId: String,
        registrationId: Int
    ): Result<Unit> = runCatching {
        val response = apiService.deleteRegistration(playerId, registrationId)
        if (response.isSuccessful || response.code() == 404) {
            Unit
        } else {
            throw NotificationApiException(
                message = "Failed to delete registration",
                statusCode = response.code(),
                errorBody = response.errorBody()?.string()
            )
        }
    }

    private suspend fun resolveConflictAndUpdate(
        playerId: String,
        request: NotificationUpsertRequest,
        payload: NotificationRegistrationPayload
    ): NotificationRegistration {
        val registrations = fetchRegistrations(playerId, request.locationId).getOrElse { throw it }
        val matching = findMatchingRegistration(registrations, request)
            ?: throw NotificationApiException("Conflict but no matching registration found", 409, null)

        val response = apiService.updateRegistration(
            playerId = playerId,
            registrationId = matching.id ?: throw NotificationApiException("Missing registration id", 409, null),
            payload = payload
        )

        return response.toDomainOrThrow()
    }

    private fun findMatchingRegistration(
        registrations: List<NotificationRegistration>,
        request: NotificationUpsertRequest
    ): NotificationRegistration? {
        return registrations.firstOrNull { registration ->
            registration.alarmType == request.alarmType && when (request.alarmType) {
                NotificationAlarmType.THRESHOLD -> matchThreshold(registration, request)
                NotificationAlarmType.SCHEDULED -> matchSchedule(registration, request)
            }
        }
    }

    private fun matchThreshold(
        registration: NotificationRegistration,
        request: NotificationUpsertRequest
    ): Boolean {
        val expectedValue = request.thresholdValueUg
        val candidate = registration.thresholdValueUg
        val sameCycle = request.thresholdFrequency?.toRepeatCycle() == registration.thresholdCycle

        return sameCycle && when {
            expectedValue == null && candidate == null -> true
            expectedValue != null && candidate != null -> abs(expectedValue - candidate) < 0.01
            else -> false
        }
    }

    private fun matchSchedule(
        registration: NotificationRegistration,
        request: NotificationUpsertRequest
    ): Boolean {
        val reqDays = request.scheduledDays?.sortedBy { it.sortOrder }?.map { it.rawValue }
        val regDays = registration.scheduledDays?.sortedBy { it.sortOrder }?.map { it.rawValue }

        return registration.scheduledTime.equals(request.scheduledTime, ignoreCase = true) &&
            registration.scheduledTimezone.equals(request.scheduledTimezone, ignoreCase = true) &&
            reqDays == regDays
    }

    private fun parseRegistrations(json: JsonElement?): Result<List<NotificationRegistrationDTO>> {
        if (json == null || json.isJsonNull) return Result.success(emptyList())

        return when {
            json.isJsonArray -> parseArray(json.asJsonArray)
            json.isJsonObject -> parseObject(json.asJsonObject)
            else -> Result.failure(NotificationParsingException("Unsupported registrations payload format"))
        }
    }

    private fun parseArray(array: JsonArray): Result<List<NotificationRegistrationDTO>> = try {
        val results = array.mapIndexed { index, element ->
            runCatching { gson.fromJson(element, NotificationRegistrationDTO::class.java) }
                .getOrElse { throw NotificationParsingException("Failed to parse registration at index $index", it) }
        }
        Result.success(results)
    } catch (ex: NotificationParsingException) {
        Log.e(TAG, ex.message ?: "Failed to parse registrations array", ex)
        Result.failure(ex)
    }

    private fun parseObject(obj: JsonObject): Result<List<NotificationRegistrationDTO>> = try {
        val envelope = gson.fromJson(obj, NotificationRegistrationsEnvelope::class.java)
        Result.success(envelope.registrationsList())
    } catch (ex: Exception) {
        val wrapped = NotificationParsingException("Failed to parse notification envelope", ex)
        Log.e(TAG, wrapped.message, wrapped)
        Result.failure(wrapped)
    }

    private fun NotificationRegistration.toThresholdSettings(): ThresholdAlertSettings? {
        val value = thresholdValueUg ?: return null
        val frequency = ThresholdAlertFrequency.fromRepeatCycle(thresholdCycle)
        val unitValue = unit ?: "ug"

        return ThresholdAlertSettings(
            registrationId = id,
            isEnabled = active,
            thresholdValueUg = value,
            frequency = frequency,
            unit = unitValue
        )
    }

    private fun NotificationRegistration.toScheduledNotification(): ScheduledNotification? {
        val timeValue = scheduledTime ?: return null
        val timezoneValue = scheduledTimezone ?: return null
        val effectiveDays = scheduledDays?.takeIf { it.isNotEmpty() } ?: NotificationWeekday.values().toSet()
        val unitValue = unit ?: "ug"

        return ScheduledNotification(
            registrationId = id,
            time = timeValue,
            selectedDays = effectiveDays,
            timezone = timezoneValue,
            isActive = active,
            unit = unitValue
        )
    }

    private fun <T> Response<T>.toDomainOrThrow(): NotificationRegistration {
        if (!isSuccessful) {
            throw NotificationApiException(
                message = "Notification API call failed",
                statusCode = code(),
                errorBody = errorBody()?.string()
            )
        }

        val body = body()
        if (body !is NotificationRegistrationDTO) {
            throw NotificationApiException("Missing registration payload", code(), null)
        }

        return body.toDomain() ?: throw NotificationApiException("Invalid registration payload", code(), null)
    }

    private class NotificationApiException(
        message: String,
        val statusCode: Int,
        val errorBody: String?
    ) : Exception("$message (status=$statusCode, error=$errorBody)")

    private class NotificationParsingException(
        message: String,
        cause: Throwable? = null
    ) : Exception(message, cause)

    companion object {
        private const val TAG = "NotificationsRepo"
    }
}
