package com.airgradient.android.data.models

import com.airgradient.android.data.utils.sanitizeCoordinates
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.services.AQIService
import com.google.gson.annotations.SerializedName

// API response wrapper mirroring iOS naming while remaining lenient with alt payload keys
data class ClusteredMapResponse(
    @SerializedName(value = "data", alternate = ["measurements", "results"])
    val data: List<ClusteredMeasurement> = emptyList(),
    val total: Int? = null,
    val page: Int? = null,
    @SerializedName("pagesize") val pageSize: Int? = null
)

data class ClusteredMeasurement(
    val type: String, // "sensor" or "cluster"
    val latitude: Double,
    val longitude: Double,
    @SerializedName("locationId") val locationId: Int?,
    @SerializedName("locationName") val locationName: String?,
    @SerializedName("sensorsCount") val sensorsCount: Int?,
    @SerializedName("sensorType") val sensorType: String?,
    @SerializedName("ownerName") val ownerName: String?,
    @SerializedName("dataSource") val dataSource: String?,
    val value: Double?
) {
    private val sanitizedCoordinate: Pair<Double, Double>
        get() = sanitizeCoordinates(latitude, longitude) ?: (latitude to longitude)

    val coordinate: Pair<Double, Double> get() = sanitizedCoordinate
    val isCluster: Boolean get() = type.contains("cluster", ignoreCase = true)

    val validLatitude: Double get() = sanitizedCoordinate.first
    val validLongitude: Double get() = sanitizedCoordinate.second

    fun valueForMeasurementType(): Double? = value
}

data class AirQualityLocation(
    @SerializedName("locationId") val locationId: Int,
    @SerializedName("locationName") val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pm01: Double? = null,
    @SerializedName("pm02") val pm02: Double? = null,
    @SerializedName("pm25") val pm25: Double? = null,
    val pm10: Double? = null,
    @SerializedName("co2") val co2: Double? = null,
    @SerializedName("rco2") val rco2: Double? = null,
    val atmp: Double? = null,
    val rhum: Double? = null,
    val timestamp: String? = null,
    @SerializedName("measuredAt") val measuredAt: String? = null,
    @SerializedName("sensorType") val sensorType: String? = null,
    @SerializedName("dataSource") val dataSource: String? = null,
    @SerializedName("ownerName") val ownerName: String? = null
) {
    fun coordinate(): Pair<Double, Double>? =
        if (latitude != null && longitude != null) latitude to longitude else null

    fun pm25Value(): Double? = pm25?.takeIf { it.isFinite() && it >= 0 } ?: pm02?.takeIf { it.isFinite() && it >= 0 }

    fun co2Value(): Double? = rco2?.takeIf { it.isFinite() && it >= 0 } ?: co2?.takeIf { it.isFinite() && it >= 0 }

    fun valueForMeasurementType(type: MeasurementType): Double? =
        if (type == MeasurementType.PM25) pm25Value() else co2Value()

    val pm25Value: Double?
        get() = pm25Value()

    val co2Value: Double?
        get() = co2Value()

    val temperature: Double?
        get() = atmp?.takeIf { it.isFinite() }

    val humidity: Double?
        get() = rhum?.takeIf { it.isFinite() && it >= 0 }

    val validLatitude: Double?
        get() = latitude?.takeIf { it in -90.0..90.0 }

    val validLongitude: Double?
        get() = longitude?.takeIf { it in -180.0..180.0 }

    val hasValidCoordinates: Boolean
        get() = validLatitude != null && validLongitude != null
}

data class AirQualityAnnotation(
    val key: String,
    val locationId: Int,
    val coordinate: Pair<Double, Double>,
    val title: String?,
    val subtitle: String?,
    val pm25: Double?,
    val co2: Double?,
    val isCluster: Boolean,
    val clusterCount: Int? = null,
    val sensorType: String?,
    val measurementType: MeasurementType,
    val airQualityLocation: AirQualityLocation
) {
    val currentValue: Double?
        get() = if (measurementType == MeasurementType.PM25) pm25 else co2
}

data class MapCameraFocus(
    val center: Pair<Double, Double>,
    val spanLatitudeDelta: Double,
    val spanLongitudeDelta: Double,
    val zoomMultiplier: Double,
    val verticalOvershootFraction: Double
)

data class HeatMapDataPoint(
    val day: Int,
    val hour: Int,
    val value: Double?,
    val date: Long,
    val isFuture: Boolean = false
)

data class LocationInfo(
    @SerializedName("locationId") val locationId: Int,
    @SerializedName("locationName") val locationName: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("ownerId") val ownerId: Int?,
    @SerializedName("ownerName") val ownerName: String?,
    @SerializedName("ownerNameDisplay") val ownerNameDisplay: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("sensorType") val sensorType: String?,
    @SerializedName("licenses") val licenses: List<String>?,
    @SerializedName("provider") val provider: String?,
    @SerializedName("dataSource") val dataSource: String?,
    @SerializedName("timezone") val timezone: String?
) {
    val displayOwnerName: String
        get() = ownerNameDisplay ?: ownerName ?: "Unknown Organization"

    val license: String?
        get() = licenses?.firstOrNull()
}

data class HistoricalDataPoint(
    val timestamp: String,
    val pm25: Double?,
    val co2: Double?
)

data class HistoricalApiDataPoint(
    val timebucket: String,
    val value: String // API returns value as string
) {
    fun toHistoricalDataPoint(measure: String): HistoricalDataPoint {
        val numericValue = value.toDoubleOrNull()
        return HistoricalDataPoint(
            timestamp = timebucket,
            pm25 = if (measure == "pm25") numericValue else null,
            co2 = if (measure == "rco2") numericValue else null
        )
    }
}

data class HistoricalDataResponse(
    val data: List<HistoricalApiDataPoint>,
    val total: Int,
    val page: Int?,
    val pagesize: Int?
)

enum class AQIStandard(val value: String, val displayName: String) {
    US_EPA("us_epa", "US EPA"),
    THAI("thai", "Thai"),
    LAO("lao", "Lao")
}

// AQI color coding based on Swift implementation
data class AQIRange(
    val min: Double,
    val max: Double,
    val color: Long, // Android color as Long (0xFFRRGGBB)
    val category: String
)

object AQIColorPalette {
    // EPA Standard colors - updated to match iOS AQIColorPalette.swift
    private const val GOOD = 0xFF33CC33L            // Green: Color(red: 0.2, green: 0.8, blue: 0.2)
    private const val MODERATE = 0xFFF0B900L         // Golden Yellow with greenish tint: Better contrast
    private const val UNHEALTHY_SENSITIVE = 0xFFFF9933L // Orange: Color(red: 1.0, green: 0.6, blue: 0.2)
    private const val UNHEALTHY = 0xFFE63333L        // Red: Color(red: 0.9, green: 0.2, blue: 0.2)
    private const val VERY_UNHEALTHY = 0xFF9933E6L   // Purple: Color(red: 0.6, green: 0.3, blue: 0.9)
    private const val HAZARDOUS = 0xFF8C3333L        // Maroon: Color(red: 0.55, green: 0.2, blue: 0.2)
    private const val CO2_ALERT = 0xFF808080L        // Gray for excessive CO2

    val US_EPA_RANGES: List<AQIRange> by lazy {
        AQIService.US_EPA_CATEGORY_BANDS.map { breakpoint ->
            AQIRange(
                min = breakpoint.minPM25,
                max = breakpoint.maxPM25,
                color = when (breakpoint.aqiCategory) {
                    AQICategory.GOOD -> GOOD
                    AQICategory.MODERATE -> MODERATE
                    AQICategory.UNHEALTHY_FOR_SENSITIVE -> UNHEALTHY_SENSITIVE
                    AQICategory.UNHEALTHY -> UNHEALTHY
                    AQICategory.VERY_UNHEALTHY -> VERY_UNHEALTHY
                    AQICategory.HAZARDOUS -> HAZARDOUS
                },
                category = breakpoint.aqiCategory.displayName
            )
        }
    }

    val THAI_RANGES = listOf(
        AQIRange(0.0, 15.0, GOOD, "Good"),
        AQIRange(15.1, 25.0, MODERATE, "Moderate"),
        AQIRange(25.1, 37.5, UNHEALTHY_SENSITIVE, "Unhealthy for Sensitive Groups"),
        AQIRange(37.6, 75.0, UNHEALTHY, "Unhealthy"),
        AQIRange(75.1, Double.MAX_VALUE, VERY_UNHEALTHY, "Very Unhealthy")
    )

    val CO2_RANGES = listOf(
        AQIRange(0.0, 450.0, GOOD, "Excellent"),
        AQIRange(450.0, 500.0, MODERATE, "Good"),
        AQIRange(500.0, 800.0, UNHEALTHY_SENSITIVE, "Moderate"),
        AQIRange(800.0, Double.MAX_VALUE, CO2_ALERT, "Poor")
    )

    fun getColorForValue(value: Double, measurementType: MeasurementType, standard: AQIStandard = AQIStandard.US_EPA): Long {
        val ranges = when {
            measurementType == MeasurementType.CO2 -> CO2_RANGES
            standard == AQIStandard.THAI -> THAI_RANGES
            else -> US_EPA_RANGES
        }

        return ranges.firstOrNull { value >= it.min && value <= it.max }?.color ?: 0xFF808080L // Gray for unknown
    }

    fun getCategoryForValue(value: Double, measurementType: MeasurementType, standard: AQIStandard = AQIStandard.US_EPA): String {
        val ranges = when {
            measurementType == MeasurementType.CO2 -> CO2_RANGES
            standard == AQIStandard.THAI -> THAI_RANGES
            else -> US_EPA_RANGES
        }

        return ranges.firstOrNull { value >= it.min && value <= it.max }?.category ?: "Unknown"
    }
}
