package com.airgradient.android.data.services

import com.airgradient.android.data.models.AirQualityLocation
import com.airgradient.android.data.models.ClusteredMapResponse
import com.airgradient.android.data.models.LocationInfo
import com.airgradient.android.data.models.HistoricalDataResponse
import com.airgradient.android.data.models.CigaretteResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AirQualityApiService {

    @GET("map/api/v1/measurements/current/cluster")
    suspend fun getClusteredMeasurements(
        @Query("xmin") west: Double,
        @Query("ymin") south: Double,
        @Query("xmax") east: Double,
        @Query("ymax") north: Double,
        @Query("zoom") zoom: Int = 10,
        @Query("measure") measure: String = "pm25",
        @Query("minPoints") minPoints: Int = 2,
        @Query("radius") radius: Int = 18,
        @Query("maxZoom") maxZoom: Int = 8
    ): Response<ClusteredMapResponse>

    @GET("map/api/v1/locations/{id}")
    suspend fun getLocationInfo(
        @Path("id") locationId: Int
    ): Response<LocationInfo>

    @GET("map/api/v1/locations/{id}/measures/current")
    suspend fun getCurrentMeasurements(
        @Path("id") locationId: Int
    ): Response<AirQualityLocation>

    @GET("map/api/v1/locations/{locationId}/measures/history")
    suspend fun getHistoricalData(
        @Path("locationId") locationId: Int,
        @Query("start") startTime: String,
        @Query("end") endTime: String,
        @Query("bucketSize") bucketSize: String = "1h",
        @Query("measure") measure: String = "pm25"
    ): Response<HistoricalDataResponse>

    @GET("map/api/v1/locations/{locationId}/cigarettes/smoked")
    suspend fun getCigaretteEquivalence(
        @Path("locationId") locationId: Int
    ): Response<CigaretteResponse>

    companion object {
        const val BASE_URL = "https://map-data.airgradient.com/"
        const val TIMEOUT_SECONDS = 30L
        const val MAX_RETRY_ATTEMPTS = 3
        const val REFRESH_INTERVAL_SECONDS = 300L // 5 minutes
    }
}
