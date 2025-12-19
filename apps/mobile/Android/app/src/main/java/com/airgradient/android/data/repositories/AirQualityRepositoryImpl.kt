package com.airgradient.android.data.repositories

import android.util.Log
import com.airgradient.android.data.errors.DataIntegrityException
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.data.network.NetworkError
import com.airgradient.android.data.models.AirQualityLocation
import com.airgradient.android.data.models.CigaretteData
import com.airgradient.android.data.models.ClusteredMeasurement
import com.airgradient.android.data.models.HistoricalDataPoint
import com.airgradient.android.data.models.LocationInfo
import com.airgradient.android.data.models.toData
import com.airgradient.android.data.services.AirQualityApiService
import com.airgradient.android.data.utils.sanitizeCoordinates
import com.airgradient.android.domain.models.AirQualityMeasurement
import com.airgradient.android.domain.models.Coordinates
import com.airgradient.android.domain.models.Location
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.OrganizationInfo
import com.airgradient.android.domain.models.OrganizationType
import com.airgradient.android.domain.models.SensorInfo
import com.airgradient.android.domain.models.SensorType
import com.airgradient.android.domain.repositories.GeographicBounds
import com.airgradient.android.domain.repositories.TimeRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Singleton
class AirQualityRepositoryImpl @Inject constructor(
    private val apiService: AirQualityApiService
) : com.airgradient.android.domain.repositories.AirQualityRepository {

    private companion object {
        private const val MIN_CLUSTER_POINTS = 2
        private const val MAX_CLUSTER_ZOOM = 8
        private const val MAX_CLUSTER_RADIUS = 50
        private const val MIN_CLUSTER_RADIUS = 3
    }

    override suspend fun getClusteredMeasurements(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        measurementType: MeasurementType,
        zoomLevel: Int?
    ): ApiResult<List<ClusteredMeasurement>> {

        return try {
            val zoom = zoomLevel ?: calculateZoomLevel(north, south, east, west)
            val radius = radiusForZoom(zoom)

            Log.d(
                "AirQualityRepo",
                "Requesting clustered measurements: bounds($north,$south,$east,$west), type=${measurementType.displayName}, zoom=$zoom, radius=$radius"
            )

            val response = executeWithRetry {
                apiService.getClusteredMeasurements(
                    west = west,
                    south = south,
                    east = east,
                    north = north,
                    zoom = zoom,
                    measure = measurementType.apiValue,
                    minPoints = MIN_CLUSTER_POINTS,
                    radius = radius,
                    maxZoom = MAX_CLUSTER_ZOOM
                )
            }

            Log.d("AirQualityRepo", "API response received")

            // Simple response handling
            if (response.isSuccessful) {
                val body = response.body()
                val measurements = body?.data ?: emptyList()

                ApiResult.Success(measurements)
            } else {
                Log.e("AirQualityRepo", "API error: ${response.code()} ${response.message()}")
                ApiResult.Error(NetworkError.ServerError(response.code()))
            }

        } catch (e: Exception) {
            Log.e("AirQualityRepo", "Exception in clustered measurements request", e)
            ApiResult.Error(NetworkError.from(e))
        }
    }

    suspend fun getLocationInfo(locationId: Int): ApiResult<LocationInfo> {
        return try {
            val response = executeWithRetry {
                apiService.getLocationInfo(locationId)
            }

            if (response.isSuccessful) {
                response.body()?.let { locationInfo ->
                    ApiResult.Success(locationInfo)
                } ?: ApiResult.Error(NetworkError.UnknownError(Exception("No location info found")))
            } else {
                ApiResult.Error(NetworkError.ServerError(response.code()))
            }

        } catch (e: Exception) {
            ApiResult.Error(NetworkError.from(e))
        }
    }

    suspend fun getHistoricalData(
        locationId: Int,
        startTime: String,
        endTime: String,
        bucketSize: String = "1h",
        measure: String = "pm25"
    ): ApiResult<List<HistoricalDataPoint>> {
        Log.d("AirQualityRepository", "getHistoricalData called: locationId=$locationId, start=$startTime, end=$endTime, bucketSize=$bucketSize")
        return try {
            val response = executeWithRetry {
                apiService.getHistoricalData(locationId, startTime, endTime, bucketSize, measure)
            }

            Log.d("AirQualityRepository", "API response: isSuccessful=${response.isSuccessful}, code=${response.code()}, message=${response.message()}")

            if (response.isSuccessful) {
                response.body()?.let { responseData ->
                    Log.d("AirQualityRepository", "Historical data received: ${responseData.data.size} points (total: ${responseData.total})")
                    val historicalPoints = responseData.data.map { it.toHistoricalDataPoint(measure) }
                    ApiResult.Success(historicalPoints)
                } ?: run {
                    Log.e("AirQualityRepository", "No historical data in response body")
                    ApiResult.Error(NetworkError.UnknownError(Exception("No measurement data found")))
                }
            } else {
                Log.e("AirQualityRepository", "API call failed: ${response.code()} - ${response.message()}")
                Log.e("AirQualityRepository", "Error body: ${response.errorBody()?.string()}")
                ApiResult.Error(NetworkError.ServerError(response.code()))
            }

        } catch (e: Exception) {
            Log.e("AirQualityRepository", "Exception in getHistoricalData", e)
            ApiResult.Error(NetworkError.from(e))
        }
    }

    suspend fun getCurrentMeasurements(locationId: Int): ApiResult<AirQualityLocation> {
        return try {
            val response = executeWithRetry {
                apiService.getCurrentMeasurements(locationId)
            }

            if (response.isSuccessful) {
                response.body()?.let { measurements ->
                    ApiResult.Success(measurements)
                } ?: ApiResult.Error(NetworkError.ServerError(response.code()))
            } else {
                ApiResult.Error(NetworkError.ServerError(response.code()))
            }

        } catch (e: Exception) {
            ApiResult.Error(NetworkError.from(e))
        }
    }

    suspend fun getCigaretteEquivalence(locationId: Int): ApiResult<CigaretteData> {
        return try {
            val response = executeWithRetry {
                apiService.getCigaretteEquivalence(locationId)
            }

            if (response.isSuccessful) {
                val data = response.body()?.toData()
                if (data != null) {
                    ApiResult.Success(data)
                } else {
                    ApiResult.Error(NetworkError.UnknownError(Exception("Missing cigarette equivalence data")))
                }
            } else {
                ApiResult.Error(NetworkError.ServerError(response.code()))
            }
        } catch (e: Exception) {
            ApiResult.Error(NetworkError.from(e))
        }
    }

    // Helper functions
    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 3,
        baseDelayMs: Long = 1000,
        maxDelayMs: Long = 30000,
        block: suspend () -> Response<T>
    ): Response<T> {
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt == maxRetries - 1) {
                    throw e
                }

                // Exponential backoff with jitter
                val delayMs = minOf(
                    baseDelayMs * (1L shl attempt) + (0..1000).random(),
                    maxDelayMs
                )

                Log.d("AirQualityRepo", "Request failed (attempt ${attempt + 1}/$maxRetries), retrying in ${delayMs}ms: ${e.message}")
                delay(delayMs)
            }
        }

        throw IllegalStateException("Retry loop should not reach this point")
    }

    private fun calculateZoomLevel(north: Double, south: Double, east: Double, west: Double): Int {
        val latSpan = abs(north - south).coerceAtLeast(1e-6)
        val lonSpan = abs(east - west).coerceAtLeast(1e-6)
        val maxSpan = max(latSpan, lonSpan).coerceAtMost(360.0)

        val zoom = (ln(360.0 / maxSpan) / ln(2.0)).roundToInt()
        return zoom.coerceIn(1, 13)
    }

    private fun radiusForZoom(zoom: Int): Int {
        if (zoom <= 1) return MAX_CLUSTER_RADIUS
        if (zoom >= MAX_CLUSTER_ZOOM) return MIN_CLUSTER_RADIUS

        val normalized = (zoom - 1).toDouble() / (MAX_CLUSTER_ZOOM - 1)
        val interpolated = MAX_CLUSTER_RADIUS - normalized * (MAX_CLUSTER_RADIUS - MIN_CLUSTER_RADIUS)
        return interpolated.roundToInt().coerceAtLeast(MIN_CLUSTER_RADIUS)
    }

    // Interface methods from AirQualityRepository

    override suspend fun getHistoricalData(
        locationId: Int,
        timeRange: TimeRange
    ): Result<List<AirQualityMeasurement>> {
        return try {
            val formatter = DateTimeFormatter.ISO_DATE_TIME
            val startTime = LocalDateTime.ofEpochSecond(timeRange.startTime / 1000, 0, ZoneOffset.UTC)
                .format(formatter)
            val endTime = LocalDateTime.ofEpochSecond(timeRange.endTime / 1000, 0, ZoneOffset.UTC)
                .format(formatter)

            val bucketSize = when (timeRange.interval) {
                com.airgradient.android.domain.repositories.TimeInterval.HOURLY -> "1h"
                com.airgradient.android.domain.repositories.TimeInterval.DAILY -> "1d"
                com.airgradient.android.domain.repositories.TimeInterval.WEEKLY -> "1d" // API doesn't support weekly, aggregate daily
            }

            val apiResult = getHistoricalData(
                locationId = locationId,
                startTime = startTime,
                endTime = endTime,
                bucketSize = bucketSize,
                measure = "pm25"
            )

            when (apiResult) {
                is ApiResult.Success -> {
                    val measurements = apiResult.data.map { dataPoint ->
                        val parsedTimestamp = parseTimestamp(dataPoint.timestamp)
                            .getOrElse { throw it }
                        AirQualityMeasurement(
                            pm25 = dataPoint.pm25,
                            pm10 = null,
                            co2 = dataPoint.co2,
                            temperature = null,
                            humidity = null,
                            timestamp = parsedTimestamp,
                            measurementType = MeasurementType.PM25
                        )
                    }
                    Result.success(measurements)
                }
                is ApiResult.Error -> Result.failure(Exception(apiResult.error.toString()))
                is ApiResult.Loading -> Result.failure(Exception("Loading"))
            }
        } catch (e: Exception) {
            Log.e("AirQualityRepo", "Error getting historical data", e)
            Result.failure(e)
        }
    }

    override suspend fun getLocationsInBounds(
        bounds: GeographicBounds,
        measurementType: MeasurementType
    ): Result<List<Location>> {
        return try {
            val apiResult = getClusteredMeasurements(
                north = bounds.north,
                south = bounds.south,
                east = bounds.east,
                west = bounds.west,
                measurementType = measurementType
            )

            when (apiResult) {
                is ApiResult.Success -> {
                    val locations = apiResult.data.mapNotNull { measurement ->
                        if (measurement.type == "sensor" && measurement.locationId != null) {
                            val coordinates = sanitizeCoordinates(measurement.latitude, measurement.longitude)
                                ?: throw DataIntegrityException("Invalid coordinates for location ${measurement.locationId}")

                            Location(
                                id = measurement.locationId,
                                name = measurement.locationName ?: "Location #${measurement.locationId}",
                                coordinates = Coordinates(
                                    latitude = coordinates.first,
                                    longitude = coordinates.second
                                ),
                                currentMeasurement = measurement.value?.let { value ->
                                    AirQualityMeasurement(
                                        pm25 = if (measurementType == MeasurementType.PM25) value else null,
                                        pm10 = null,
                                        co2 = if (measurementType == MeasurementType.CO2) value else null,
                                        temperature = null,
                                        humidity = null,
                                        timestamp = LocalDateTime.now(),
                                        measurementType = measurementType
                                    )
                                },
                                sensorInfo = SensorInfo(
                                    type = when (measurement.sensorType) {
                                        "Reference" -> SensorType.REFERENCE
                                        else -> SensorType.LOW_COST
                                    },
                                    dataSource = measurement.dataSource ?: "Unknown",
                                    lastUpdated = LocalDateTime.now().toString()
                                ),
                                organizationInfo = measurement.ownerName?.let { name ->
                                    OrganizationInfo(
                                        id = 0,
                                        name = name,
                                        displayName = name,
                                        type = OrganizationType.OTHER,
                                        description = null,
                                        websiteUrl = null
                                    )
                                }
                            )
                        } else null
                    }
                    Result.success(locations)
                }
                is ApiResult.Error -> Result.failure(Exception(apiResult.error.toString()))
                is ApiResult.Loading -> Result.failure(Exception("Loading"))
            }
        } catch (e: Exception) {
            Log.e("AirQualityRepo", "Error getting locations in bounds", e)
            Result.failure(e)
        }
    }

    override suspend fun getLocationDetails(locationId: Int): Result<Location> {
        return try {
            val locationInfoResult = getLocationInfo(locationId)
            val measurementResult = getCurrentMeasurements(locationId)

            when {
                locationInfoResult is ApiResult.Success && measurementResult is ApiResult.Success -> {
                    val info = locationInfoResult.data
                    val measurement = measurementResult.data

                    val coordinates = sanitizeCoordinates(info.latitude, info.longitude)
                        ?: throw DataIntegrityException("Invalid coordinates for location $locationId")

                    val rawTimestamp = measurement.timestamp ?: measurement.measuredAt
                    val parsedTimestamp = rawTimestamp?.let { raw ->
                        parseTimestamp(raw).getOrElse { throw it }
                    } ?: throw DataIntegrityException("Missing timestamp for location $locationId")

                    val location = Location(
                        id = info.locationId,
                        name = info.locationName ?: "Location #${info.locationId}",
                        coordinates = Coordinates(
                            latitude = coordinates.first,
                            longitude = coordinates.second
                        ),
                        currentMeasurement = AirQualityMeasurement(
                            pm25 = measurement.pm25Value(),
                            pm10 = measurement.pm10,
                            co2 = measurement.co2Value(),
                            temperature = measurement.atmp,
                            humidity = measurement.rhum,
                            timestamp = parsedTimestamp,
                            measurementType = MeasurementType.PM25
                        ),
                        sensorInfo = SensorInfo(
                            type = when (info.sensorType) {
                                "Reference" -> SensorType.REFERENCE
                                else -> SensorType.LOW_COST
                            },
                            dataSource = info.dataSource ?: "Unknown",
                            lastUpdated = LocalDateTime.now().toString()
                        ),
                        organizationInfo = OrganizationInfo(
                            id = info.ownerId ?: 0,
                            name = info.ownerName ?: "Unknown",
                            displayName = info.ownerNameDisplay,
                            type = OrganizationType.OTHER,
                            description = info.description,
                            websiteUrl = info.url
                        )
                    )
                    Result.success(location)
                }
                locationInfoResult is ApiResult.Error -> Result.failure(Exception(locationInfoResult.error.toString()))
                measurementResult is ApiResult.Error -> Result.failure(Exception(measurementResult.error.toString()))
                else -> Result.failure(Exception("Failed to get location details"))
            }
        } catch (e: Exception) {
            Log.e("AirQualityRepo", "Error getting location details", e)
            Result.failure(e)
        }
    }

    override suspend fun getCurrentMeasurement(locationId: Int): Result<AirQualityMeasurement> {
        return try {
            val apiResult = getCurrentMeasurements(locationId)

            when (apiResult) {
                is ApiResult.Success -> {
                    val data = apiResult.data
                    val rawTimestamp = data.timestamp ?: data.measuredAt
                    val parsedTimestamp = rawTimestamp?.let { raw ->
                        parseTimestamp(raw).getOrElse { throw it }
                    } ?: throw DataIntegrityException("Missing timestamp for location $locationId")
                    val measurement = AirQualityMeasurement(
                        pm25 = data.pm25Value(),
                        pm10 = data.pm10,
                        co2 = data.co2Value(),
                        temperature = data.atmp,
                        humidity = data.rhum,
                        timestamp = parsedTimestamp,
                        measurementType = MeasurementType.PM25
                    )
                    Result.success(measurement)
                }
                is ApiResult.Error -> Result.failure(Exception(apiResult.error.toString()))
                is ApiResult.Loading -> Result.failure(Exception("Loading"))
            }
        } catch (e: Exception) {
            Log.e("AirQualityRepo", "Error getting current measurement", e)
            Result.failure(e)
        }
    }

    override suspend fun searchLocations(query: String): Result<List<Location>> {
        // Search is not directly supported by the API
        // This could be implemented by:
        // 1. Getting all locations and filtering client-side
        // 2. Using a geocoding service to convert query to coordinates
        // For now, return empty list
        return Result.success(emptyList())
    }

    override fun observeLocationUpdates(locationId: Int): Flow<AirQualityMeasurement> {
        return flow {
            while (true) {
                try {
                    val result = getCurrentMeasurement(locationId)
                    if (result.isSuccess) {
                        result.getOrNull()?.let { emit(it) }
                    }
                } catch (e: Exception) {
                    Log.e("AirQualityRepo", "Error in location updates flow", e)
                }
                // Emit updates every 5 minutes
                delay(5 * 60 * 1000L)
            }
        }
    }
}

private val TIMESTAMP_PARSERS = listOf<(String) -> LocalDateTime>(
    { OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime() },
    { Instant.parse(it).atOffset(ZoneOffset.UTC).toLocalDateTime() },
    { OffsetDateTime.parse(it, DateTimeFormatter.ISO_ZONED_DATE_TIME).toLocalDateTime() },
    { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }
)

private fun parseTimestamp(raw: String?): Result<LocalDateTime> {
    if (raw.isNullOrBlank()) {
        return Result.failure(DataIntegrityException("Missing timestamp value"))
    }

    TIMESTAMP_PARSERS.forEach { parser ->
        try {
            return Result.success(parser(raw))
        } catch (_: Exception) {
            // try next parser
        }
    }

    return Result.failure(DataIntegrityException("Unable to parse timestamp: $raw"))
}
