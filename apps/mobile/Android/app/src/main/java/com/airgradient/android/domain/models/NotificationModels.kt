package com.airgradient.android.domain.models

import com.google.gson.annotations.SerializedName

enum class NotificationAlarmType(val rawValue: String) {
    @SerializedName("threshold") THRESHOLD("threshold"),
    @SerializedName("scheduled") SCHEDULED("scheduled");

    companion object {
        fun fromRaw(raw: String?): NotificationAlarmType? = values().firstOrNull { it.rawValue == raw }
    }
}

enum class NotificationRepeatCycle(val rawValue: String) {
    @SerializedName("once") ONCE("once"),
    @SerializedName("1h") HOURLY("1h"),
    @SerializedName("6h") SIX_HOURLY("6h"),
    @SerializedName("24h") DAILY("24h");

    companion object {
        fun fromRaw(raw: String?): NotificationRepeatCycle? = when (raw) {
            null -> null
            "0", "once", "only_once" -> ONCE
            "1h", "hourly" -> HOURLY
            "6h", "six_hourly" -> SIX_HOURLY
            "24h", "1d", "daily" -> DAILY
            else -> null
        }
    }
}

enum class ThresholdAlertFrequency {
    ONLY_ONCE,
    HOURLY,
    SIX_HOURLY,
    DAILY;

    fun toRepeatCycle(): NotificationRepeatCycle = when (this) {
        ONLY_ONCE -> NotificationRepeatCycle.ONCE
        HOURLY -> NotificationRepeatCycle.HOURLY
        SIX_HOURLY -> NotificationRepeatCycle.SIX_HOURLY
        DAILY -> NotificationRepeatCycle.DAILY
    }

    companion object {
        fun fromRepeatCycle(cycle: NotificationRepeatCycle?): ThresholdAlertFrequency = when (cycle) {
            NotificationRepeatCycle.ONCE -> ONLY_ONCE
            NotificationRepeatCycle.HOURLY -> HOURLY
            NotificationRepeatCycle.SIX_HOURLY -> SIX_HOURLY
            NotificationRepeatCycle.DAILY -> DAILY
            null -> ONLY_ONCE
        }
    }
}

enum class NotificationWeekday(val rawValue: String, val sortOrder: Int) {
    MONDAY("monday", 0),
    TUESDAY("tuesday", 1),
    WEDNESDAY("wednesday", 2),
    THURSDAY("thursday", 3),
    FRIDAY("friday", 4),
    SATURDAY("saturday", 5),
    SUNDAY("sunday", 6);

    companion object {
        fun fromRaw(raw: String?): NotificationWeekday? = values().firstOrNull { it.rawValue.equals(raw, ignoreCase = true) }
    }
}

data class NotificationRegistration(
    val id: Int?,
    val playerId: String?,
    val locationId: Int,
    val locationName: String?,
    val alarmType: NotificationAlarmType,
    val active: Boolean,
    val unit: String?,
    val thresholdValueUg: Double?,
    val thresholdCycle: NotificationRepeatCycle?,
    val scheduledDays: Set<NotificationWeekday>?,
    val scheduledTime: String?,
    val scheduledTimezone: String?,
    val createdAtIso: String?,
    val updatedAtIso: String?,
    val wasExceeded: Boolean?,
    val lastNotifiedAtIso: String?
)

data class ThresholdAlertSettings(
    val registrationId: Int?,
    val isEnabled: Boolean,
    val thresholdValueUg: Double,
    val frequency: ThresholdAlertFrequency,
    val unit: String
)

data class ScheduledNotification(
    val registrationId: Int?,
    val time: String,
    val selectedDays: Set<NotificationWeekday>,
    val timezone: String,
    val isActive: Boolean,
    val unit: String
)

data class LocationNotificationSettings(
    val locationId: Int,
    val locationName: String,
    val schedules: List<ScheduledNotification>,
    val threshold: ThresholdAlertSettings?
)

data class NotificationUpsertRequest(
    val userId: String,
    val locationId: Int,
    val alarmType: NotificationAlarmType,
    val isActive: Boolean,
    val unit: AQIDisplayUnit,
    val thresholdValueUg: Double? = null,
    val thresholdFrequency: ThresholdAlertFrequency? = null,
    val scheduledTime: String? = null,
    val scheduledTimezone: String? = null,
    val scheduledDays: Set<NotificationWeekday>? = null,
    val registrationId: Int? = null
)
