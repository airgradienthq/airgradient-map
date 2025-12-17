package com.airgradient.android.domain.services

import androidx.compose.ui.graphics.Color
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.models.AQIStandard
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Canonical AQI calculation service
 * Single source of truth for all AQI-related calculations across the app
 * Supports US EPA, WHO, Thai, and Lao AQI standards
 */
@Singleton
class AQIService @Inject constructor() {

    companion object {
        private data class UsepaComputationBreakpoint(
            val pm25Low: Double,
            val pm25High: Double,
            val aqiLow: Int,
            val aqiHigh: Int,
            val category: AQICategory
        )

        /**
         * Detailed EPA computation breakpoints (2024 revision)
         * The ranges below are used for AQI calculation and include the split hazardous band.
         */
        private val US_EPA_COMPUTATION_BREAKPOINTS: List<UsepaComputationBreakpoint> = listOf(
            UsepaComputationBreakpoint(0.0, 9.0, 0, 50, AQICategory.GOOD),
            UsepaComputationBreakpoint(9.1, 35.4, 51, 100, AQICategory.MODERATE),
            UsepaComputationBreakpoint(35.5, 55.4, 101, 150, AQICategory.UNHEALTHY_FOR_SENSITIVE),
            UsepaComputationBreakpoint(55.5, 125.4, 151, 200, AQICategory.UNHEALTHY),
            UsepaComputationBreakpoint(125.5, 225.4, 201, 300, AQICategory.VERY_UNHEALTHY),
            UsepaComputationBreakpoint(225.5, 325.4, 301, 500, AQICategory.HAZARDOUS)
        )

        /**
         * Publicly exposed EPA category bands aggregated from the computation breakpoints.
         * Intended for any classification or display logic that previously hardcoded thresholds.
         */
        val US_EPA_CATEGORY_BANDS: List<AQIBreakpoint> by lazy {
            val ordered = linkedMapOf<AQICategory, AQIBreakpoint>()
            US_EPA_COMPUTATION_BREAKPOINTS.forEach { bp ->
                val existing = ordered[bp.category]
                if (existing == null) {
                    val maxPmForCategory = if (bp.category == AQICategory.HAZARDOUS) Double.MAX_VALUE else bp.pm25High
                    val maxAqiForCategory = if (bp.category == AQICategory.HAZARDOUS) 500 else bp.aqiHigh
                    ordered[bp.category] = AQIBreakpoint(
                        category = bp.category.displayName,
                        minPM25 = bp.pm25Low,
                        maxPM25 = maxPmForCategory,
                        minAQI = bp.aqiLow,
                        maxAQI = maxAqiForCategory,
                        aqiCategory = bp.category
                    )
                } else {
                    val updatedMaxPm = if (bp.category == AQICategory.HAZARDOUS) Double.MAX_VALUE else bp.pm25High
                    val updatedMaxAqi = if (bp.category == AQICategory.HAZARDOUS) 500 else bp.aqiHigh
                    ordered[bp.category] = existing.copy(
                        maxPM25 = updatedMaxPm,
                        maxAQI = updatedMaxAqi
                    )
                }
            }
            ordered.values.toList()
        }

        /**
         * Determine the EPA category for a PM2.5 concentration (μg/m³).
         */
        fun categoryForUsepaPm25(pm25: Double): AQICategory {
            if (pm25.isNaN() || pm25 < 0.0) return AQICategory.GOOD
            val breakpoint = US_EPA_CATEGORY_BANDS.firstOrNull { pm25 >= it.minPM25 && pm25 <= it.maxPM25 }
            return breakpoint?.aqiCategory ?: AQICategory.HAZARDOUS
        }

        /**
         * Determine the EPA category for an AQI value.
         */
        fun categoryForUsepaAqi(aqi: Int): AQICategory {
            if (aqi < 0) return AQICategory.GOOD
            val breakpoint = US_EPA_CATEGORY_BANDS.firstOrNull { aqi in it.minAQI..it.maxAQI }
            return breakpoint?.aqiCategory ?: AQICategory.HAZARDOUS
        }
    }

    /**
     * Calculate numerical AQI value from PM2.5 concentration
     * @param pm25 PM2.5 concentration in μg/m³
     * @param standard AQI standard to use for calculation
     * @return Calculated AQI value
     */
    fun calculateAQI(pm25: Double, standard: AQIStandard = AQIStandard.US_EPA): Int {
        if (!isValidPM25(pm25)) return 0

        return when (standard) {
            AQIStandard.US_EPA -> calculateUSEPAAQI(pm25)
            AQIStandard.WHO -> calculateWHOAQI(pm25)
            AQIStandard.THAI -> calculateThaiAQI(pm25)
            AQIStandard.LAO -> calculateLaoAQI(pm25)
        }
    }

    /**
     * Get AQI category from PM2.5 measurement
     * @param pm25 PM2.5 concentration in μg/m³
     * @param standard AQI standard to use
     * @return AQI category
     */
    fun getCategory(pm25: Double, standard: AQIStandard = AQIStandard.US_EPA): AQICategory {
        if (!isValidPM25(pm25)) return AQICategory.GOOD

        return when (standard) {
            AQIStandard.US_EPA -> getCategoryUSEPA(pm25)
            AQIStandard.WHO -> getCategoryWHO(pm25)
            AQIStandard.THAI -> getCategoryThai(pm25)
            AQIStandard.LAO -> getCategoryThai(pm25) // Lao uses same as Thai
        }
    }

    /**
     * Get AQI category from numerical AQI value
     */
    fun getCategoryFromAQI(aqi: Int): AQICategory = categoryForUsepaAqi(aqi)

    /**
     * Get category name string
     */
    fun getCategoryName(category: AQICategory): String {
        return when (category) {
            AQICategory.GOOD -> "Good"
            AQICategory.MODERATE -> "Moderate"
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> "Unhealthy for Sensitive Groups"
            AQICategory.UNHEALTHY -> "Unhealthy"
            AQICategory.VERY_UNHEALTHY -> "Very Unhealthy"
            AQICategory.HAZARDOUS -> "Hazardous"
        }
    }

    /**
     * Get category description
     */
    fun getCategoryDescription(category: AQICategory): String {
        return when (category) {
            AQICategory.GOOD -> "Air quality is satisfactory, and air pollution poses little or no risk."
            AQICategory.MODERATE -> "Air quality is acceptable. However, there may be a risk for some people, particularly those who are unusually sensitive to air pollution."
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> "Members of sensitive groups may experience health effects. The general public is less likely to be affected."
            AQICategory.UNHEALTHY -> "Some members of the general public may experience health effects; members of sensitive groups may experience more serious health effects."
            AQICategory.VERY_UNHEALTHY -> "Health alert: The risk of health effects is increased for everyone."
            AQICategory.HAZARDOUS -> "Health warning of emergency conditions: everyone is more likely to be affected."
        }
    }

    /**
     * Get color for AQI category as ARGB Long (for Compose Color conversion)
     */
    fun getCategoryColor(category: AQICategory): Long {
        return when (category) {
            AQICategory.GOOD -> 0xFF33CC33L                    // Green
            AQICategory.MODERATE -> 0xFFFFD933L                // Yellow
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> 0xFFFF9933L // Orange
            AQICategory.UNHEALTHY -> 0xFFE63333L               // Red
            AQICategory.VERY_UNHEALTHY -> 0xFF9933E6L          // Purple
            AQICategory.HAZARDOUS -> 0xFF8C3333L               // Maroon
        }
    }

    /**
     * Get Compose Color for AQI category
     */
    fun getCategoryComposeColor(category: AQICategory): Color {
        return when (category) {
            AQICategory.GOOD -> Color(red = 0.2f, green = 0.8f, blue = 0.2f)
            AQICategory.MODERATE -> Color(red = 1.0f, green = 0.85f, blue = 0.2f)
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> Color(red = 1.0f, green = 0.6f, blue = 0.2f)
            AQICategory.UNHEALTHY -> Color(red = 0.9f, green = 0.2f, blue = 0.2f)
            AQICategory.VERY_UNHEALTHY -> Color(red = 0.6f, green = 0.3f, blue = 0.9f)
            AQICategory.HAZARDOUS -> Color(red = 0.55f, green = 0.2f, blue = 0.2f)
        }
    }

    /**
     * Get hex color string for widgets
     */
    fun getCategoryColorHex(category: AQICategory): String {
        return when (category) {
            AQICategory.GOOD -> "#4CAF50"
            AQICategory.MODERATE -> "#FFEB3B"
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> "#FF9800"
            AQICategory.UNHEALTHY -> "#F44336"
            AQICategory.VERY_UNHEALTHY -> "#9C27B0"
            AQICategory.HAZARDOUS -> "#880E4F"
        }
    }

    /**
     * Get appropriate text color for background
     */
    fun getTextColor(category: AQICategory): Color {
        return when (category) {
            AQICategory.GOOD, AQICategory.MODERATE -> Color.Black
            else -> Color.White
        }
    }

    /**
     * Validate PM2.5 value is within reasonable range
     */
    fun isValidPM25(pm25: Double?): Boolean {
        return pm25 != null && pm25 >= 0.0 && pm25 <= 1000.0 && pm25.isFinite()
    }

    /**
     * Get all AQI breakpoints for a given standard
     */
    fun getBreakpoints(standard: AQIStandard = AQIStandard.US_EPA): List<AQIBreakpoint> {
        return when (standard) {
            AQIStandard.US_EPA -> getUSEPABreakpoints()
            AQIStandard.WHO -> getWHOBreakpoints()
            AQIStandard.THAI -> getThaiBreakpoints()
            AQIStandard.LAO -> getThaiBreakpoints() // Lao uses same as Thai
        }
    }

    /**
     * Get display value as formatted string
     * Handles conversion between µg/m³ and AQI based on display unit
     */
    fun getDisplayValue(pm25: Double, displayUnit: com.airgradient.android.domain.models.AQIDisplayUnit): String {
        if (!isValidPM25(pm25)) return "N/A"

        return when (displayUnit) {
            com.airgradient.android.domain.models.AQIDisplayUnit.UGM3 -> {
                String.format(java.util.Locale.US, "%.0f", pm25)
            }
            com.airgradient.android.domain.models.AQIDisplayUnit.USAQI -> {
                calculateAQI(pm25, AQIStandard.US_EPA).toString()
            }
        }
    }

    /**
     * Get category and description as a pair (for widget compatibility)
     */
    fun getCategoryWithDescription(pm25: Double, standard: AQIStandard = AQIStandard.US_EPA): Pair<String, String> {
        if (!isValidPM25(pm25)) return "Unknown" to "No data available"
        val category = getCategory(pm25, standard)
        return getCategoryName(category) to getCategoryDescription(category)
    }

    /**
     * Get category and description from AQI value
     */
    fun getCategoryWithDescriptionFromAQI(aqi: Int): Pair<String, String> {
        val category = getCategoryFromAQI(aqi)
        return getCategoryName(category) to getCategoryDescription(category)
    }

    // ===== US EPA Implementation =====

    private fun calculateUSEPAAQI(pm25: Double): Int {
        // Truncate to 1 decimal place for EPA standard
        val truncatedPM25 = floor(pm25 * 10) / 10
        val breakpoint = US_EPA_COMPUTATION_BREAKPOINTS.firstOrNull { truncatedPM25 <= it.pm25High }
            ?: US_EPA_COMPUTATION_BREAKPOINTS.last()

        val aqi = ((breakpoint.aqiHigh - breakpoint.aqiLow) / (breakpoint.pm25High - breakpoint.pm25Low)) *
            (truncatedPM25 - breakpoint.pm25Low) + breakpoint.aqiLow
        return round(aqi).toInt()
    }

    private fun getCategoryUSEPA(pm25: Double): AQICategory {
        return categoryForUsepaPm25(pm25)
    }

    private fun getUSEPABreakpoints(): List<AQIBreakpoint> {
        return US_EPA_CATEGORY_BANDS
    }

    // ===== WHO Implementation =====

    private fun calculateWHOAQI(pm25: Double): Int {
        return when {
            pm25 <= 5.0 -> (pm25 / 5.0 * 50).roundToInt()
            pm25 <= 15.0 -> (50 + (pm25 - 5.0) / 10.0 * 50).roundToInt()
            pm25 <= 25.0 -> (100 + (pm25 - 15.0) / 10.0 * 50).roundToInt()
            pm25 <= 50.0 -> (150 + (pm25 - 25.0) / 25.0 * 50).roundToInt()
            pm25 <= 75.0 -> (200 + (pm25 - 50.0) / 25.0 * 100).roundToInt()
            else -> (300 + (pm25 - 75.0) / 75.0 * 200).roundToInt().coerceAtMost(500)
        }
    }

    private fun getCategoryWHO(pm25: Double): AQICategory {
        return when {
            pm25 <= 5.0 -> AQICategory.GOOD
            pm25 <= 15.0 -> AQICategory.MODERATE
            pm25 <= 25.0 -> AQICategory.UNHEALTHY_FOR_SENSITIVE
            pm25 <= 50.0 -> AQICategory.UNHEALTHY
            pm25 <= 75.0 -> AQICategory.VERY_UNHEALTHY
            else -> AQICategory.HAZARDOUS
        }
    }

    private fun getWHOBreakpoints(): List<AQIBreakpoint> {
        return listOf(
            AQIBreakpoint("Good", 0.0, 5.0, 0, 50, AQICategory.GOOD),
            AQIBreakpoint("Moderate", 5.1, 15.0, 51, 100, AQICategory.MODERATE),
            AQIBreakpoint("Unhealthy for Sensitive Groups", 15.1, 25.0, 101, 150, AQICategory.UNHEALTHY_FOR_SENSITIVE),
            AQIBreakpoint("Unhealthy", 25.1, 50.0, 151, 200, AQICategory.UNHEALTHY),
            AQIBreakpoint("Very Unhealthy", 50.1, 75.0, 201, 300, AQICategory.VERY_UNHEALTHY),
            AQIBreakpoint("Hazardous", 75.1, Double.MAX_VALUE, 301, 500, AQICategory.HAZARDOUS)
        )
    }

    // ===== Thai/Lao Implementation =====

    private fun calculateThaiAQI(pm25: Double): Int {
        return when {
            pm25 <= 15.0 -> (pm25 / 15.0 * 50).roundToInt()
            pm25 <= 25.0 -> (50 + (pm25 - 15.0) / 10.0 * 50).roundToInt()
            pm25 <= 37.5 -> (100 + (pm25 - 25.0) / 12.5 * 50).roundToInt()
            pm25 <= 75.0 -> (150 + (pm25 - 37.5) / 37.5 * 50).roundToInt()
            else -> (200 + (pm25 - 75.0) / 75.0 * 100).roundToInt().coerceAtMost(300)
        }
    }

    private fun calculateLaoAQI(pm25: Double): Int = calculateThaiAQI(pm25)

    private fun getCategoryThai(pm25: Double): AQICategory {
        return when {
            pm25 <= 15.0 -> AQICategory.GOOD
            pm25 <= 25.0 -> AQICategory.MODERATE
            pm25 <= 37.5 -> AQICategory.UNHEALTHY_FOR_SENSITIVE
            pm25 <= 75.0 -> AQICategory.UNHEALTHY
            else -> AQICategory.VERY_UNHEALTHY
        }
    }

    private fun getThaiBreakpoints(): List<AQIBreakpoint> {
        return listOf(
            AQIBreakpoint("Good", 0.0, 15.0, 0, 50, AQICategory.GOOD),
            AQIBreakpoint("Moderate", 15.1, 25.0, 51, 100, AQICategory.MODERATE),
            AQIBreakpoint("Unhealthy for Sensitive Groups", 25.1, 37.5, 101, 150, AQICategory.UNHEALTHY_FOR_SENSITIVE),
            AQIBreakpoint("Unhealthy", 37.6, 75.0, 151, 200, AQICategory.UNHEALTHY),
            AQIBreakpoint("Very Unhealthy", 75.1, Double.MAX_VALUE, 201, 300, AQICategory.VERY_UNHEALTHY)
        )
    }
}

/**
 * AQI breakpoint data class
 */
data class AQIBreakpoint(
    val category: String,
    val minPM25: Double,
    val maxPM25: Double,
    val minAQI: Int,
    val maxAQI: Int,
    val aqiCategory: AQICategory
)
