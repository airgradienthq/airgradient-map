package com.airgradient.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.repositories.WidgetLocationSettings
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore for application settings with type-safe preferences
 */
class AppSettingsDataStore(private val context: Context) {

    companion object {
        private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "app_settings"
        )

        // Preference keys
        val DISPLAY_UNIT = stringPreferencesKey("display_unit")
        val WIDGET_LOCATION_NAME = stringPreferencesKey("widget_location_name")
        val WIDGET_LOCATION_ID = intPreferencesKey("widget_location_id")
        val WIDGET_LOCATION_LAT = doublePreferencesKey("widget_location_lat")
        val WIDGET_LOCATION_LNG = doublePreferencesKey("widget_location_lng")

        // Default values
        val DEFAULT_DISPLAY_UNIT: AQIDisplayUnit = AQIDisplayUnit.USAQI
        const val DEFAULT_WIDGET_LOCATION_NAME = "None"
        const val DEFAULT_WIDGET_LOCATION_ID = -1
        const val DEFAULT_WIDGET_LOCATION_LAT = 0.0
        const val DEFAULT_WIDGET_LOCATION_LNG = 0.0

        internal fun parseDisplayUnit(raw: String?): AQIDisplayUnit = raw.toDisplayUnit()
    }

    private val dataStore = context.settingsDataStore

    /**
     * Get display unit as Flow
     */
    val displayUnit: Flow<AQIDisplayUnit> = dataStore.data.map { preferences ->
        val stored = preferences[DISPLAY_UNIT]
        stored.toDisplayUnit()
    }

    /**
     * Update display unit
     */
    suspend fun updateDisplayUnit(unit: AQIDisplayUnit) {
        dataStore.edit { preferences ->
            preferences[DISPLAY_UNIT] = unit.rawValue
        }
    }

    /**
     * Get widget location settings as Flow
     */
    val widgetLocation: Flow<WidgetLocationSettings> = dataStore.data.map { preferences ->
        WidgetLocationSettings(
            locationName = preferences[WIDGET_LOCATION_NAME] ?: DEFAULT_WIDGET_LOCATION_NAME,
            locationId = preferences[WIDGET_LOCATION_ID] ?: DEFAULT_WIDGET_LOCATION_ID,
            latitude = preferences[WIDGET_LOCATION_LAT] ?: DEFAULT_WIDGET_LOCATION_LAT,
            longitude = preferences[WIDGET_LOCATION_LNG] ?: DEFAULT_WIDGET_LOCATION_LNG
        )
    }

    /**
     * Update widget location settings
     */
    suspend fun updateWidgetLocation(settings: WidgetLocationSettings) {
        dataStore.edit { preferences ->
            preferences[WIDGET_LOCATION_NAME] = settings.locationName
            preferences[WIDGET_LOCATION_ID] = settings.locationId
            preferences[WIDGET_LOCATION_LAT] = settings.latitude
            preferences[WIDGET_LOCATION_LNG] = settings.longitude
        }
    }

    /**
     * Clear widget location settings
     */
    suspend fun clearWidgetLocation() {
        dataStore.edit { preferences ->
            preferences[WIDGET_LOCATION_NAME] = DEFAULT_WIDGET_LOCATION_NAME
            preferences[WIDGET_LOCATION_ID] = DEFAULT_WIDGET_LOCATION_ID
            preferences[WIDGET_LOCATION_LAT] = DEFAULT_WIDGET_LOCATION_LAT
            preferences[WIDGET_LOCATION_LNG] = DEFAULT_WIDGET_LOCATION_LNG
        }
    }
}

private fun String?.toDisplayUnit(): AQIDisplayUnit {
    val normalized = this?.lowercase(Locale.US) ?: return AppSettingsDataStore.DEFAULT_DISPLAY_UNIT
    return when {
        normalized == AQIDisplayUnit.UGM3.rawValue -> AQIDisplayUnit.UGM3
        normalized == AQIDisplayUnit.USAQI.rawValue -> AQIDisplayUnit.USAQI
        normalized.contains("µg") || normalized.contains("ug") -> AQIDisplayUnit.UGM3
        normalized.contains("thai") || normalized.contains("lao") -> AQIDisplayUnit.USAQI
        else -> AppSettingsDataStore.DEFAULT_DISPLAY_UNIT
    }
}
