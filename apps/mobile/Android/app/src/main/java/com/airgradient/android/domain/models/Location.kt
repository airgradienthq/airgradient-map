package com.airgradient.android.domain.models

/**
 * Core domain model for geographic location with air quality data
 */
data class Location(
    val id: Int,
    val name: String,
    val coordinates: Coordinates,
    val currentMeasurement: AirQualityMeasurement?,
    val sensorInfo: SensorInfo?,
    val organizationInfo: OrganizationInfo?
) {
    val hasValidMeasurement: Boolean
        get() = currentMeasurement?.isValid == true

    val displayName: String
        get() = name.ifBlank { "Location #$id" }
}

/**
 * Geographic coordinates
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * Information about the air quality sensor
 */
data class SensorInfo(
    val type: SensorType,
    val dataSource: String,
    val lastUpdated: String
)

/**
 * Types of air quality sensors
 */
enum class SensorType(val displayName: String, val description: String) {
    REFERENCE("Reference", "High-precision research-grade sensor"),
    LOW_COST("Low-cost", "Community-grade sensor"),
    CLUSTER("Cluster", "Multiple sensors grouped together")
}

/**
 * Organization providing the sensor/data
 */
data class OrganizationInfo(
    val id: Int,
    val name: String,
    val displayName: String?,
    val type: OrganizationType,
    val description: String? = null,
    val websiteUrl: String? = null
) {
    val publicName: String
        get() = displayName ?: name
}

/**
 * Types of organizations operating sensors
 */
enum class OrganizationType(val displayName: String) {
    SCHOOL("Educational Institution"),
    GOVERNMENT("Government Agency"),
    NGO("Non-Governmental Organization"),
    COMMUNITY("Community Group"),
    COMMERCIAL("Commercial Entity"),
    RESEARCH("Research Institution"),
    UNICEF("UNICEF Partnership"),
    OTHER("Other")
}