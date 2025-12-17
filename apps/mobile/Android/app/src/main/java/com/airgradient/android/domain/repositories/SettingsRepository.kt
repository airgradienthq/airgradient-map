package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.AQIDisplayUnit
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for application settings
 */
interface SettingsRepository {

    /**
     * Get the current display unit setting
     * @return Flow of display unit selection
     */
    fun getDisplayUnit(): Flow<AQIDisplayUnit>

    /**
     * Update the display unit setting
     */
    suspend fun setDisplayUnit(unit: AQIDisplayUnit)

    /**
     * Get the widget location settings
     */
    fun getWidgetLocation(): Flow<WidgetLocationSettings>

    /**
     * Update the widget location settings
     */
    suspend fun setWidgetLocation(settings: WidgetLocationSettings)

    /**
     * Clear all widget location settings
     */
    suspend fun clearWidgetLocation()
}

/**
 * Widget location settings data
 */
data class WidgetLocationSettings(
    val locationName: String = "None",
    val locationId: Int = -1,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
