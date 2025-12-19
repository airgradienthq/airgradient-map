package com.airgradient.android.data.repositories

import com.airgradient.android.data.local.auth.PlaceSelectionStore
import com.airgradient.android.data.models.monitors.CurrentLocationReadingDto
import com.airgradient.android.data.models.monitors.PlaceDto
import com.airgradient.android.data.models.monitors.PlaceLocationDto
import com.airgradient.android.data.models.monitors.asDoubleOrNull
import com.airgradient.android.data.models.monitors.asIntOrNull
import com.airgradient.android.data.models.monitors.asLongOrNull
import com.airgradient.android.data.models.monitors.firstAvailable
import com.airgradient.android.data.services.MyMonitorsApiService
import com.airgradient.android.data.services.RegisterMonitorRequest
import com.airgradient.android.domain.models.monitors.CurrentLocationReading
import com.airgradient.android.domain.models.monitors.HistoryRequest
import com.airgradient.android.domain.models.monitors.HistorySample
import com.airgradient.android.domain.models.monitors.MonitorMeasurementKind
import com.airgradient.android.domain.models.monitors.MonitorMetrics
import com.airgradient.android.domain.models.monitors.MonitorsPlace
import com.airgradient.android.domain.models.monitors.PlaceLocation
import com.airgradient.android.domain.models.monitors.PlacePermissions
import com.airgradient.android.domain.models.monitors.TemperatureUnit
import com.airgradient.android.domain.repositories.AuthenticationRepository
import com.airgradient.android.domain.repositories.MyMonitorsRepository
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import android.net.Uri
import okhttp3.ResponseBody
import retrofit2.HttpException

@Singleton
class MyMonitorsRepositoryImpl @Inject constructor(
    private val apiService: MyMonitorsApiService,
    private val placeSelectionStore: PlaceSelectionStore,
    private val authenticationRepository: AuthenticationRepository
) : MyMonitorsRepository {

    override fun selectedPlaceId() = placeSelectionStore.selectedPlaceId()

    override fun updateSelectedPlaceId(placeId: Int?) {
        placeSelectionStore.updateSelectedPlaceId(placeId)
    }

    override fun clearSelection() {
        placeSelectionStore.clear()
    }

    override suspend fun fetchPlaces(): Result<List<MonitorsPlace>> = runCatching {
        val response = apiService.getPlaces()
        if (!response.isSuccessful) throw response.toHttpExceptionWithAuth()
        response.body().orEmpty().map { it.toDomain() }
    }

    override suspend fun fetchPlaceLocations(placeId: Int): Result<List<PlaceLocation>> = runCatching {
        val response = apiService.getPlaceLocations(placeId)
        if (!response.isSuccessful) throw response.toHttpExceptionWithAuth()
        response.body().orEmpty().map { it.toDomain() }
    }

    override suspend fun fetchCurrentReadings(placeId: Int): Result<List<CurrentLocationReading>> = runCatching {
        val response = apiService.getCurrentLocationReadings(placeId)
        if (!response.isSuccessful) throw response.toHttpExceptionWithAuth()
        response.body().orEmpty().mapNotNull { it.toDomain() }
    }

    override suspend fun fetchHistory(request: HistoryRequest): Result<List<HistorySample>> = runCatching {
        val response = apiService.getHistory(
            placeId = request.placeId,
            locationId = request.locationId,
            bucket = request.timeRange.apiBucket,
            since = request.timeRange.apiSince,
            measure = request.measurement.apiValue
        )
        if (!response.isSuccessful) throw response.toHttpExceptionWithAuth()
        val element = response.body() ?: return@runCatching emptyList()
        parseHistory(element, request.measurement)
    }

    override suspend fun registerMonitor(
        placeId: Int,
        serial: String,
        model: String,
        locationName: String
    ): Result<Unit> = runCatching {
        val encodedSerial = Uri.encode(serial)
        val response = apiService.registerMonitor(
            placeId = placeId,
            serial = encodedSerial,
            body = RegisterMonitorRequest(model = model, location_name = locationName)
        )
        if (!response.isSuccessful) {
            val httpException = response.toHttpExceptionWithAuth()
            val errorMessage = parseRegisterError(response.errorBody())
                ?: "Registration failed (status ${response.code()})"
            throw RuntimeException(errorMessage, httpException)
        }
    }

    private fun <T> retrofit2.Response<T>.toHttpExceptionWithAuth(): HttpException {
        val exception = HttpException(this)
        if (exception.code() == 401) {
            authenticationRepository.handleSessionExpired()
        }
        return exception
    }

    private fun PlaceDto.toDomain(): MonitorsPlace {
        return MonitorsPlace(
            id = id,
            name = name,
            temperatureUnit = TemperatureUnit.fromString(temperatureUnit),
            plantowerPm2CorrectionAlgo = plantowerPm2CorrectionAlgo,
            permissions = permissions?.let { PlacePermissions(it.settings == true) }
        )
    }

    private fun PlaceLocationDto.toDomain(): PlaceLocation {
        val mergedPm = pm02.asDoubleOrNull()
            ?: current.firstAvailable("pm02", "pm2", "pm")?.asDoubleOrNull()
        val mergedCo2 = rco2.asDoubleOrNull()
            ?: current.firstAvailable("rco2", "co2")?.asDoubleOrNull()
        val mergedTvoc = tvocIndex.asDoubleOrNull()
            ?: tvocIndexCamel.asDoubleOrNull()
            ?: tvoc.asDoubleOrNull()
            ?: current.firstAvailable("tvoc_index", "tvocIndex", "tvoc")?.asDoubleOrNull()
        val mergedNox = noxIndex.asDoubleOrNull()
            ?: noxIndexCamel.asDoubleOrNull()
            ?: nox.asDoubleOrNull()
            ?: current.firstAvailable("nox_index", "noxIndex", "nox")?.asDoubleOrNull()
        val mergedTemperature = temperature.asDoubleOrNull()
            ?: current.firstAvailable("atmp", "temperature")?.asDoubleOrNull()
        val mergedHumidity = humidity.asDoubleOrNull()
            ?: current.firstAvailable("rhum", "humidity")?.asDoubleOrNull()

        val metrics = MonitorMetrics(
            pm25 = mergedPm,
            co2 = mergedCo2,
            tvocIndex = mergedTvoc,
            noxIndex = mergedNox,
            temperatureCelsius = mergedTemperature,
            humidity = mergedHumidity
        )

        return PlaceLocation(
            id = id,
            placeId = placeId,
            name = name,
            locationType = locationType,
            indoor = indoor,
            active = active ?: true,
            offline = offline ?: false,
            metrics = metrics
        )
    }

    private fun CurrentLocationReadingDto.toDomain(): CurrentLocationReading? {
        val locationIdValue = id.asIntOrNull()
            ?: locationId.asIntOrNull()
            ?: locationIdCamel.asIntOrNull()
            ?: return null

        val pm = pm02.asDoubleOrNull()
            ?: current.firstAvailable("pm02", "pm2", "pm")?.asDoubleOrNull()
        val co2 = rco2.asDoubleOrNull()
            ?: current.firstAvailable("rco2", "co2")?.asDoubleOrNull()
        val tvoc = tvocIndex.asDoubleOrNull()
            ?: tvocIndexCamel.asDoubleOrNull()
            ?: tvoc.asDoubleOrNull()
            ?: current.firstAvailable("tvoc_index", "tvocIndex", "tvoc")?.asDoubleOrNull()
        val nox = noxIndex.asDoubleOrNull()
            ?: noxIndexCamel.asDoubleOrNull()
            ?: nox.asDoubleOrNull()
            ?: current.firstAvailable("nox_index", "noxIndex", "nox")?.asDoubleOrNull()
        val temperatureValue = temperature.asDoubleOrNull()
            ?: current.firstAvailable("atmp", "temperature")?.asDoubleOrNull()
        val humidityValue = humidity.asDoubleOrNull()
            ?: current.firstAvailable("rhum", "humidity")?.asDoubleOrNull()

        val metrics = MonitorMetrics(
            pm25 = pm,
            co2 = co2,
            tvocIndex = tvoc,
            noxIndex = nox,
            temperatureCelsius = temperatureValue,
            humidity = humidityValue
        )

        val timestampInstant = parseTimestamp(
            timestamp = timestamp.asDoubleOrNull(),
            updatedAt = updatedAt.asDoubleOrNull(),
            dateString = date,
            current = current
        )

        return CurrentLocationReading(
            locationId = locationIdValue,
            placeId = placeId,
            indoor = indoor,
            active = active,
            offline = offline,
            metrics = metrics,
            timestamp = timestampInstant
        )
    }

    private fun parseTimestamp(
        timestamp: Double?,
        updatedAt: Double?,
        dateString: String?,
        current: JsonObject?
    ): Instant? {
        val dateCandidate = sequenceOf(
            current.firstAvailable("date")?.let {
                if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else null
            },
            dateString
        ).firstOrNull { !it.isNullOrBlank() }

        dateCandidate?.let { value ->
            parseLocalDate(value)?.let { return it }
        }

        val numericCandidate = listOfNotNull(
            timestamp,
            updatedAt,
            current.firstAvailable("timestamp", "updated_at")?.asDoubleOrNull()
        ).firstOrNull()
        if (numericCandidate != null) {
            val seconds = if (numericCandidate > 1_000_000_000_000) numericCandidate / 1000.0 else numericCandidate
            val wholeSeconds = seconds.toLong()
            val nanos = ((seconds - wholeSeconds) * 1_000_000_000).roundToLong()
            return Instant.ofEpochSecond(wholeSeconds, nanos)
        }

        val stringCandidate = sequenceOf(
            current.firstAvailable("timestamp", "updated_at")?.let {
                if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else null
            }
        ).firstOrNull { !it.isNullOrBlank() }

        return stringCandidate?.let { parseFlexibleInstant(it) }
    }

    private fun parseLocalDate(value: String): Instant? {
        val trimmed = value.trim()
        val localFormats = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        )
        localFormats.forEach { formatter ->
            try {
                val localDateTime = LocalDateTime.parse(trimmed, formatter)
                return localDateTime.atZone(ZoneId.systemDefault()).toInstant()
            } catch (_: DateTimeParseException) {
                // ignore
            }
        }
        return null
    }

    private fun parseFlexibleInstant(value: String): Instant? {
        val trimmed = value.trim()
        val knownFormats = listOf(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        )

        knownFormats.forEach { formatter ->
            try {
                return Instant.from(formatter.parse(trimmed))
            } catch (_: DateTimeParseException) {
                // try next format
            }
        }

        return runCatching { Instant.parse(trimmed) }.getOrNull()
    }

    private fun parseHistory(element: JsonElement, measurement: MonitorMeasurementKind): List<HistorySample> {
        return when {
            element.isJsonArray -> parseHistoryArray(element.asJsonArray, measurement)
            element.isJsonObject -> parseHistoryObject(element.asJsonObject, measurement)
            else -> emptyList()
        }
    }

    private fun parseHistoryObject(obj: JsonObject, measurement: MonitorMeasurementKind): List<HistorySample> {
        val primaryArray = obj.getAsJsonArray("data")
            ?: obj.getAsJsonArray("history")
            ?: obj.getAsJsonArray("values")
        if (primaryArray != null) {
            return parseHistoryArray(primaryArray, measurement)
        }
        // Some payloads may nest under arbitrary keys
        val firstArray = obj.entrySet()
            .mapNotNull { (_, value) -> if (value is JsonArray) value else null }
            .firstOrNull()
        return firstArray?.let { parseHistoryArray(it, measurement) } ?: emptyList()
    }

    private fun parseHistoryArray(array: JsonArray, measurement: MonitorMeasurementKind): List<HistorySample> {
        return array.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject
            val timestamp = extractTimestampForHistory(obj) ?: return@mapNotNull null
            val value = extractMeasurementValue(obj, measurement)
                ?: measurementFallback(measurement, obj)
            value?.let { HistorySample(timestamp = timestamp, value = it) }
        }.sortedBy { it.timestamp }
    }

    private fun extractTimestampForHistory(obj: JsonObject): Instant? {
        val numberCandidate = obj.firstAvailable("timestamp", "ts", "time", "unix")?.asDoubleOrNull()
        if (numberCandidate != null) {
            val seconds = if (numberCandidate > 1_000_000_000_000) numberCandidate / 1000.0 else numberCandidate
            val whole = seconds.toLong()
            val nanos = ((seconds - whole) * 1_000_000_000).roundToLong()
            return Instant.ofEpochSecond(whole, nanos)
        }

        val stringCandidate = obj.firstAvailable("date", "timebucket", "time_bucket", "timestamp", "updated_at")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        if (stringCandidate != null) {
            val parsedInstant = parseFlexibleInstant(stringCandidate)
            if (parsedInstant != null) {
                return parsedInstant
            }
            return parseLocalDate(stringCandidate)
        }
        return null
    }

    private fun extractMeasurementValue(obj: JsonObject, measurement: MonitorMeasurementKind): Double? {
        val primary = obj.firstAvailable(measurement.apiValue, "value", "reading", "avg", "mean")?.asDoubleOrNull()
        if (primary != null) return primary
        val nested = obj.firstAvailable("current", "data")
        if (nested is JsonObject) {
            return nested.firstAvailable(measurement.apiValue, "value", "reading", "avg", "mean")?.asDoubleOrNull()
        }
        return null
    }

    private fun measurementFallback(measurement: MonitorMeasurementKind, obj: JsonObject): Double? {
        return when (measurement) {
            MonitorMeasurementKind.TVOC_INDEX -> obj.firstAvailable("tvoc", "tvoc_index", "tvocIndex")?.asDoubleOrNull()
            MonitorMeasurementKind.NOX_INDEX -> obj.firstAvailable("nox", "nox_index", "noxIndex")?.asDoubleOrNull()
            MonitorMeasurementKind.TEMPERATURE -> obj.firstAvailable("atmp", "temperature")?.asDoubleOrNull()
            MonitorMeasurementKind.HUMIDITY -> obj.firstAvailable("rhum", "humidity")?.asDoubleOrNull()
            MonitorMeasurementKind.PM25 -> obj.firstAvailable("pm02", "pm2", "pm")?.asDoubleOrNull()
            MonitorMeasurementKind.CO2 -> obj.firstAvailable("rco2", "co2")?.asDoubleOrNull()
        }
    }

    private fun parseRegisterError(errorBody: ResponseBody?): String? {
        if (errorBody == null) return null
        return runCatching {
            val raw = errorBody.string()
            if (raw.isBlank()) return null
            val json = runCatching { com.google.gson.JsonParser.parseString(raw).asJsonObject }.getOrNull()
            json?.get("message")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: raw
        }.getOrNull()
    }
}
