package com.airgradient.android.data.services

import com.airgradient.android.data.models.monitors.CurrentLocationReadingDto
import com.airgradient.android.data.models.monitors.PlaceDto
import com.airgradient.android.data.models.monitors.PlaceLocationDto
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MyMonitorsApiService {
    @GET("places")
    suspend fun getPlaces(): Response<List<PlaceDto>>

    @GET("places/{placeId}/locations")
    suspend fun getPlaceLocations(@Path("placeId") placeId: Int): Response<List<PlaceLocationDto>>

    @GET("places/{placeId}/locations/current")
    suspend fun getCurrentLocationReadings(
        @Path("placeId") placeId: Int,
        @Query("active") active: Boolean = true
    ): Response<List<CurrentLocationReadingDto>>

    @GET("places/{placeId}/locations/{locationId}/history")
    suspend fun getHistory(
        @Path("placeId") placeId: Int,
        @Path("locationId") locationId: Int,
        @Query("bucket") bucket: String,
        @Query("since") since: String,
        @Query("measure") measure: String,
        @Query("duringPlaceOpenOnly") duringPlaceOpenOnly: Boolean = false
    ): Response<JsonElement>

    @POST("places/{placeId}/sensors/{serial}/locations")
    suspend fun registerMonitor(
        @Path("placeId") placeId: Int,
        @Path(value = "serial", encoded = true) serial: String,
        @Body body: RegisterMonitorRequest
    ): Response<ResponseBody>
}

data class RegisterMonitorRequest(
    val model: String,
    val location_name: String
)
