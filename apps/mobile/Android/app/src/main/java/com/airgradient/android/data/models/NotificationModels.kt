package com.airgradient.android.data.models

import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.NotificationAlarmType
import com.airgradient.android.domain.models.NotificationRegistration
import com.airgradient.android.domain.models.NotificationRepeatCycle
import com.airgradient.android.domain.models.NotificationUpsertRequest
import com.airgradient.android.domain.models.NotificationWeekday
import com.airgradient.android.domain.models.ThresholdAlertFrequency
import com.google.gson.annotations.SerializedName

/**
 * Data-layer representations for notification registrations, matching the backend contract.
 *
 * Gson in minified builds lost the enum {@code @SerializedName} metadata, so we serialize the
 * enum values manually as primitive strings to guarantee release builds match the backend schema.
 */

data class NotificationRegistrationPayload(
    @SerializedName("player_id") val playerId: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("alarm_type") val alarmType: String,
    @SerializedName("location_id") val locationId: Int,
    @SerializedName("threshold_ug_m3") val thresholdUgM3: Double? = null,
    @SerializedName("threshold_category") val thresholdCategory: String? = null,
    @SerializedName("threshold_cycle") val thresholdCycle: String? = null,
    @SerializedName("scheduled_days") val scheduledDays: List<String>? = null,
    @SerializedName("scheduled_time") val scheduledTime: String? = null,
    @SerializedName("scheduled_timezone") val scheduledTimezone: String? = null,
    @SerializedName("active") val active: Boolean,
    @SerializedName("unit") val unit: String
)

data class NotificationRegistrationDTO(
    @SerializedName("id") val id: Int?,
    @SerializedName("player_id") val playerId: String?,
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("alarm_type") val alarmTypeRaw: String?,
    @SerializedName("location_id") val locationId: Int?,
    @SerializedName("location_name") val locationName: String?,
    @SerializedName("threshold_ug_m3") val thresholdUgM3: Any?,
    @SerializedName("threshold_category") val thresholdCategory: String?,
    @SerializedName("threshold_cycle") val thresholdCycleRaw: String?,
    @SerializedName("scheduled_days") val scheduledDays: List<String>?,
    @SerializedName("scheduled_time") val scheduledTime: String?,
    @SerializedName("scheduled_timezone") val scheduledTimezone: String?,
    @SerializedName("active") val active: Boolean?,
    @SerializedName("unit") val unit: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("was_exceeded") val wasExceeded: Boolean?,
    @SerializedName("last_notified_at") val lastNotifiedAt: String?
) {
    val alarmType: NotificationAlarmType?
        get() = NotificationAlarmType.fromRaw(alarmTypeRaw)

    val thresholdCycle: NotificationRepeatCycle?
        get() = NotificationRepeatCycle.fromRaw(thresholdCycleRaw)

    val thresholdUgValue: Double?
        get() = when (thresholdUgM3) {
            is Number -> thresholdUgM3.toDouble()
            is String -> thresholdUgM3.toDoubleOrNull()
            else -> null
        }
}

data class NotificationRegistrationsEnvelope(
    @SerializedName("data") val data: List<NotificationRegistrationDTO>?,
    @SerializedName("registrations") val registrations: List<NotificationRegistrationDTO>?
) {
    fun registrationsList(): List<NotificationRegistrationDTO> = data ?: registrations ?: emptyList()
}

fun NotificationRegistrationDTO.toDomain(): NotificationRegistration? {
    val type = alarmType ?: return null
    val locId = locationId ?: return null
    val weekdays = scheduledDays
        ?.mapNotNull { NotificationWeekday.fromRaw(it) }
        ?.toSet()

    return NotificationRegistration(
        id = id,
        playerId = playerId,
        locationId = locId,
        locationName = locationName,
        alarmType = type,
        active = active != false,
        unit = unit,
        thresholdValueUg = thresholdUgValue,
        thresholdCycle = thresholdCycle,
        scheduledDays = weekdays,
        scheduledTime = scheduledTime,
        scheduledTimezone = scheduledTimezone,
        createdAtIso = createdAt,
        updatedAtIso = updatedAt,
        wasExceeded = wasExceeded,
        lastNotifiedAtIso = lastNotifiedAt
    )
}

fun NotificationUpsertRequest.toPayload(playerId: String): NotificationRegistrationPayload {
    val cycle = thresholdFrequency?.toRepeatCycle()
    val days = scheduledDays
        ?.sortedBy { it.sortOrder }
        ?.map { it.rawValue }

    return NotificationRegistrationPayload(
        playerId = playerId,
        userId = userId,
        alarmType = alarmType.rawValue,
        locationId = locationId,
        thresholdUgM3 = thresholdValueUg,
        thresholdCategory = null,
        thresholdCycle = cycle?.rawValue,
        scheduledDays = days,
        scheduledTime = scheduledTime,
        scheduledTimezone = scheduledTimezone,
        active = isActive,
        unit = unit.toApiUnit()
    )
}

private fun AQIDisplayUnit.toApiUnit(): String = when (this) {
    AQIDisplayUnit.USAQI -> "us_aqi"
    AQIDisplayUnit.UGM3 -> "ug"
}
