package com.airgradient.android.domain.models

import java.time.LocalDateTime

/**
 * Core domain model for air quality measurements
 * Contains pure business data without any presentation or data layer concerns
 */
data class AirQualityMeasurement(
    val pm25: Double?,
    val pm10: Double?,
    val co2: Double?,
    val temperature: Double?,
    val humidity: Double?,
    val timestamp: LocalDateTime,
    val measurementType: MeasurementType
) {
    val isValid: Boolean
        get() = pm25?.let { it >= 0 && it.isFinite() } ?: false

    val primaryValue: Double?
        get() = when (measurementType) {
            MeasurementType.PM25 -> pm25
            MeasurementType.CO2 -> co2
        }
}

/**
 * Types of air quality measurements supported by the system
 */
enum class MeasurementType(
    val rawValue: String,
    val apiValue: String,
    val displayName: String,
    val unit: String
) {
    PM25("pm25", "pm25", "PM2.5", "μg/m³"),
    CO2("co2", "rco2", "CO2", "ppm")
}

enum class AQIDisplayUnit(val rawValue: String) {
    UGM3("ug_m3"),
    USAQI("us_aqi")
}

/**
 * Air Quality Index categories based on international standards
 */
enum class AQICategory(
    val displayName: String,
    val healthMessage: String
) {
    GOOD("Good", "Air quality is satisfactory"),
    MODERATE("Moderate", "Air quality is acceptable for most people"),
    UNHEALTHY_FOR_SENSITIVE("Unhealthy for Sensitive Groups", "Sensitive groups may experience health effects"),
    UNHEALTHY("Unhealthy", "Everyone may experience health effects"),
    VERY_UNHEALTHY("Very Unhealthy", "Health warnings of emergency conditions"),
    HAZARDOUS("Hazardous", "Everyone is at risk of serious health effects")
}

/**
 * International air quality standards
 */
enum class AQIStandard(val displayName: String) {
    US_EPA("US EPA"),
    WHO("WHO"),
    THAI("Thai Standard"),
    LAO("Lao Standard")
}
