package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.LocationNotificationSettings
import com.airgradient.android.domain.models.NotificationRegistration
import com.airgradient.android.domain.models.NotificationUpsertRequest

interface NotificationsRepository {

    suspend fun fetchRegistrations(
        playerId: String,
        locationId: Int? = null
    ): Result<List<NotificationRegistration>>

    suspend fun fetchLocationSettings(
        playerId: String,
        locationId: Int
    ): Result<LocationNotificationSettings>

    suspend fun upsertRegistration(
        playerId: String,
        request: NotificationUpsertRequest
    ): Result<NotificationRegistration>

    suspend fun deleteRegistration(
        playerId: String,
        registrationId: Int
    ): Result<Unit>
}
