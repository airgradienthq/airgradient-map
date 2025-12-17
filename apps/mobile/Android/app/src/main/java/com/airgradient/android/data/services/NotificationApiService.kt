package com.airgradient.android.data.services

import com.airgradient.android.data.models.NotificationRegistrationDTO
import com.airgradient.android.data.models.NotificationRegistrationPayload
import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApiService {

    @GET("map/api/v1/notifications/players/{playerId}/registrations")
    suspend fun getRegistrations(
        @Path("playerId") playerId: String,
        @Query("locationId") locationId: Int? = null,
        @Query("deviceId") deviceId: String? = null
    ): Response<JsonElement>

    @POST("map/api/v1/notifications/registrations")
    suspend fun createRegistration(
        @Body payload: NotificationRegistrationPayload
    ): Response<NotificationRegistrationDTO>

    @PATCH("map/api/v1/notifications/players/{playerId}/registrations/{registrationId}")
    suspend fun updateRegistration(
        @Path("playerId") playerId: String,
        @Path("registrationId") registrationId: Int,
        @Body payload: NotificationRegistrationPayload
    ): Response<NotificationRegistrationDTO>

    @DELETE("map/api/v1/notifications/players/{playerId}/registrations/{registrationId}")
    suspend fun deleteRegistration(
        @Path("playerId") playerId: String,
        @Path("registrationId") registrationId: Int
    ): Response<Unit>
}
