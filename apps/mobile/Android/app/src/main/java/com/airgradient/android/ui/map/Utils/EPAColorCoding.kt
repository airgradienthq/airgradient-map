package com.airgradient.android.ui.map.Utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.airgradient.android.R
import com.airgradient.android.data.models.AQIColorPalette
import com.airgradient.android.data.models.AQIStandard as PaletteStandard
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.AQIStandard
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.services.AQIService
import java.util.Locale

/**
 * EPA Color Coding for Air Quality Index (AQI)
 * Provides colors, categories, AQI calculations, and asset references for PM2.5 values
 * Delegates to AQIService for canonical calculations
 *
 * Based on EPA AQI standards:
 * https://www.airnow.gov/aqi/aqi-basics/
 */
object EPAColorCoding {

    // Lazy-initialized singleton - not ideal for DI but maintains backward compatibility
    private val aqiService by lazy { AQIService() }
    
    /**
     * Get color for PM2.5 value based on EPA AQI standards
     * Delegates to AQIService for consistency
     */
    fun getColorForPM25(pm25: Double): Color {
        if (!aqiService.isValidPM25(pm25)) return Color.Gray
        val category = aqiService.getCategory(pm25, AQIStandard.US_EPA)
        return aqiService.getCategoryComposeColor(category)
    }

    /**
     * Get AQI category name for PM2.5 value
     */
    fun getCategoryForPM25(pm25: Double): AQICategory? {
        if (!aqiService.isValidPM25(pm25)) return null
        return aqiService.getCategory(pm25, AQIStandard.US_EPA)
    }

    /**
     * Calculate AQI value from PM2.5 concentration
     * Delegates to AQIService for canonical calculation
     */
    fun getAQIFromPM25(pm25: Double): Int {
        return aqiService.calculateAQI(pm25, AQIStandard.US_EPA)
    }

    /**
     * Get health advice for PM2.5 value
     */
    fun getHealthAdvice(pm25: Double): String {
        if (!aqiService.isValidPM25(pm25)) return "No data available"
        val category = aqiService.getCategory(pm25, AQIStandard.US_EPA)
        return aqiService.getCategoryDescription(category)
    }

    /**
     * Resolve AQI category from a numeric AQI value.
     */
    fun getCategoryForAQI(aqi: Int): AQICategory = aqiService.getCategoryFromAQI(aqi)

    /**
     * Asset helpers shared across the UI — keeps mascot/background selection in one place.
     */
    fun mascotForCategory(category: AQICategory): Int = when (category) {
        AQICategory.GOOD -> R.drawable.aqi_mascot_good
        AQICategory.MODERATE -> R.drawable.aqi_mascot_moderate
        AQICategory.UNHEALTHY_FOR_SENSITIVE -> R.drawable.aqi_mascot_unhealthy_sensitive
        AQICategory.UNHEALTHY -> R.drawable.aqi_mascot_unhealthy
        AQICategory.VERY_UNHEALTHY -> R.drawable.aqi_mascot_very_unhealthy
        AQICategory.HAZARDOUS -> R.drawable.aqi_mascot_hazardous
    }

    fun backgroundForCategory(category: AQICategory): Int = when (category) {
        AQICategory.GOOD -> R.drawable.aqi_bg_good_mobile
        AQICategory.MODERATE -> R.drawable.aqi_bg_moderate_mobile
        AQICategory.UNHEALTHY_FOR_SENSITIVE -> R.drawable.aqi_bg_unhealthy_sensitive_groups_mobile
        AQICategory.UNHEALTHY -> R.drawable.aqi_bg_unhealthy_mobile
        AQICategory.VERY_UNHEALTHY -> R.drawable.aqi_bg_very_unhealthy_mobile
        AQICategory.HAZARDOUS -> R.drawable.aqi_bg_hazardous_mobile
    }

    fun lightBackgroundColorForCategory(category: AQICategory): Long = when (category) {
        AQICategory.GOOD -> 0xFFF1F8E9
        AQICategory.MODERATE -> 0xFFFFFDE7
        AQICategory.UNHEALTHY_FOR_SENSITIVE -> 0xFFFFF3E0
        AQICategory.UNHEALTHY -> 0xFFFFEBEE
        AQICategory.VERY_UNHEALTHY -> 0xFFF3E5F5
        AQICategory.HAZARDOUS -> 0xFFEFEBE9
    }

    /**
     * Get all AQI breakpoints for legend/reference
     */
    data class AQIBreakpoint(
        val category: String,
        val minPM25: Double,
        val maxPM25: Double,
        val minAQI: Int,
        val maxAQI: Int,
        val color: Color,
        @androidx.annotation.DrawableRes val mascotRes: Int,
        @androidx.annotation.DrawableRes val backgroundRes: Int
    )

    fun getAllBreakpoints(): List<AQIBreakpoint> {
        val serviceBreakpoints = aqiService.getBreakpoints(AQIStandard.US_EPA)
        return serviceBreakpoints.map { bp ->
            AQIBreakpoint(
                bp.category,
                bp.minPM25,
                bp.maxPM25,
                bp.minAQI,
                bp.maxAQI,
                aqiService.getCategoryComposeColor(bp.aqiCategory),
                mascotForCategory(bp.aqiCategory),
                backgroundForCategory(bp.aqiCategory)
            )
        }
    }

    /**
     * Validate PM2.5 value is within reasonable range
     */
    fun isValidPM25(pm25: Double?): Boolean {
        return aqiService.isValidPM25(pm25)
    }

    /**
     * Get appropriate text color for background
     */
    fun getTextColorForBackground(pm25: Double): Color {
        if (!aqiService.isValidPM25(pm25)) return Color.White
        val category = aqiService.getCategory(pm25, AQIStandard.US_EPA)
        return aqiService.getTextColor(category)
    }

    fun colorForMeasurement(
        value: Double?,
        measurementType: MeasurementType,
        displayUnit: AQIDisplayUnit
    ): Color {
        val safeValue = value?.takeIf { it.isFinite() && it >= 0 } ?: return Color.Gray
        val standard = PaletteStandard.US_EPA

        val colorLong = AQIColorPalette.getColorForValue(safeValue, measurementType, standard)
        return Color(colorLong.toInt())
    }

    fun textColorForMeasurement(
        value: Double?,
        measurementType: MeasurementType,
        displayUnit: AQIDisplayUnit
    ): Color {
        return when (measurementType) {
            MeasurementType.PM25 -> {
                val pmValue = value?.takeIf { it.isFinite() && it >= 0 } ?: return Color.White
                getTextColorForBackground(pmValue)
            }
            MeasurementType.CO2 -> {
                val background = colorForMeasurement(value, measurementType, displayUnit)
                if (background.luminance() > 0.5f) Color.Black else Color.White
            }
        }
    }

    fun getDisplayValueForMeasurement(
        value: Double?,
        measurementType: MeasurementType,
        displayUnit: AQIDisplayUnit
    ): String {
        val safeValue = value?.takeIf { it.isFinite() && it >= 0 } ?: return "N/A"

        return when (measurementType) {
            MeasurementType.PM25 -> aqiService.getDisplayValue(safeValue, displayUnit)
            MeasurementType.CO2 -> String.format(Locale.US, "%.0f", safeValue)
        }
    }

    /**
     * Get partner organization logo based on data source/owner
     */
    fun getPartnerLogo(ownerName: String?, dataSource: String?): String? {
        val name = (ownerName ?: dataSource)?.lowercase() ?: return null
        
        return when {
            name.contains("unicef") -> "logo_unicef_lao"
            name.contains("sustenta") || name.contains("honduras") -> "logo_sustenta_honduras"
            name.contains("advocates") || name.contains("airgradient") -> "logo_advocates"
            else -> null
        }
    }

}

/**
 * Extension functions for easier usage
 */
fun Double.toAQI(): Int = EPAColorCoding.getAQIFromPM25(this)
fun Double.toAQICategory(): AQICategory? = EPAColorCoding.getCategoryForPM25(this)
fun Double.toAQIColor(): Color = EPAColorCoding.getColorForPM25(this)
fun Double.isValidPM25(): Boolean = EPAColorCoding.isValidPM25(this)
