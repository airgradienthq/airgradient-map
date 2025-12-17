package com.airgradient.android.widget

import android.content.Context
import com.airgradient.android.R
import com.airgradient.android.data.di.WidgetEntryPoint
import com.airgradient.android.domain.models.AQIStandard
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale
import com.airgradient.android.ui.shared.Utils.toDisplayNameRes

data class WidgetData(
    val locationName: String,
    val primaryValueText: String,
    val primaryLabel: String,
    val categoryLabel: String,
    val themeAqiValue: Int,
    val locationId: Int,
    val lastUpdated: Long? = null,
    val pm25Display: String
)

/**
 * Widget data service that uses Hilt-injected dependencies
 * Reads widget location from DataStore for consistency
 */
object AirQualityWidgetDataService {

    suspend fun getWidgetData(context: Context): WidgetData = withContext(Dispatchers.IO) {
        try {
            // Get Hilt-injected dependencies
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
            val apiService = entryPoint.airQualityApiService()
            val settingsDataStore = entryPoint.appSettingsDataStore()
            val aqiService = entryPoint.aqiService()

            // Read widget location from DataStore
            val widgetLocation = settingsDataStore.widgetLocation.first()

            if (widgetLocation.locationName == "None" || widgetLocation.locationId == -1) {
                return@withContext WidgetData(
                    locationName = context.getString(R.string.settings_notifications_location_none),
                    primaryValueText = context.getString(R.string.widget_value_placeholder_empty),
                    primaryLabel = context.getString(R.string.unit_us_aqi_short),
                    categoryLabel = context.getString(R.string.widget_value_placeholder_empty),
                    themeAqiValue = 0,
                    locationId = -1,
                    pm25Display = context.getString(R.string.widget_pm_missing)
                )
            }

            // Fetch current measurements for the location
            val response = apiService.getCurrentMeasurements(widgetLocation.locationId)

            if (!response.isSuccessful) {
                throw Exception("API call failed")
            }

            // Calculate AQI from PM2.5 using canonical AQIService
            val currentMeasure = response.body() ?: throw Exception("No data")
            val pm25Value = currentMeasure.pm25 ?: currentMeasure.pm02
            val aqiValue = aqiService.calculateAQI(pm25Value ?: 0.0, AQIStandard.US_EPA)
            val category = aqiService.getCategoryFromAQI(aqiValue)
            val locale = Locale.getDefault()

            // Parse timestamp from API response
            val lastUpdated = parseTimestamp(currentMeasure.measuredAt ?: currentMeasure.timestamp)
            val pmDisplay = pm25Value?.let { String.format(locale, "%.1f \u03BCg/m\u00B3", it) }
            val pmText = pmDisplay?.let {
                context.getString(R.string.widget_pm_value_template, it)
            } ?: context.getString(R.string.widget_pm_missing)
            val formattedCategory = context.getString(category.toDisplayNameRes())

            WidgetData(
                locationName = widgetLocation.locationName,
                primaryValueText = aqiValue.toString(),
                primaryLabel = context.getString(R.string.unit_us_aqi_short),
                categoryLabel = formattedCategory,
                themeAqiValue = aqiValue,
                locationId = widgetLocation.locationId,
                lastUpdated = lastUpdated,
                pm25Display = pmText
            )

        } catch (e: Exception) {
            e.printStackTrace()
            // Return default data on error
            WidgetData(
                locationName = context.getString(R.string.widget_error_loading),
                primaryValueText = context.getString(R.string.widget_value_placeholder_empty),
                primaryLabel = context.getString(R.string.unit_us_aqi_short),
                categoryLabel = context.getString(R.string.widget_value_placeholder_empty),
                themeAqiValue = 0,
                locationId = -1,
                pm25Display = context.getString(R.string.widget_pm_missing)
            )
        }
    }

    private fun parseTimestamp(timestampStr: String?): Long? {
        if (timestampStr.isNullOrBlank()) return null

        // Try multiple timestamp formats (same as AirQualityRepositoryImpl)
        val parsers = listOf<(String) -> Long>(
            { java.time.OffsetDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant().toEpochMilli() },
            { java.time.Instant.parse(it).toEpochMilli() },
            { java.time.OffsetDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME)
                .toInstant().toEpochMilli() },
            { java.time.LocalDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_DATE_TIME)
                .atOffset(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
        )

        parsers.forEach { parser ->
            try {
                return parser(timestampStr)
            } catch (_: Exception) {
                // Try next parser
            }
        }

        return null
    }
}
