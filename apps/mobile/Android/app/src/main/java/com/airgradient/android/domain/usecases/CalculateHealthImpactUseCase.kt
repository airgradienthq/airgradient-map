package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.*
import com.airgradient.android.domain.services.AQIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Use case for calculating health impact from air quality measurements
 * Implements WHO compliance checking and cigarette equivalent calculations
 */
class CalculateHealthImpactUseCase @Inject constructor(
    private val aqiService: AQIService
) {

    private val dispatcher = Dispatchers.IO

    /**
     * Calculate comprehensive health impact analysis
     */
    suspend operator fun invoke(
        currentMeasurement: AirQualityMeasurement,
        historicalData: List<AirQualityMeasurement> = emptyList()
    ): Result<HealthImpact> = withContext(dispatcher) {

        try {
            val pm25 = currentMeasurement.pm25
                ?: return@withContext Result.failure(
                    IllegalArgumentException("PM2.5 measurement required for health impact analysis")
                )

            val whoCompliance = calculateWHOCompliance(currentMeasurement, historicalData)
            val cigaretteEquivalent = calculateCigaretteEquivalent(pm25, CigaretteTimeframe.DAY)
        val healthRiskLevel = determineHealthRiskLevel(pm25)
            val recommendations = generateHealthRecommendations(healthRiskLevel)

            val healthImpact = HealthImpact(
                whoCompliance = whoCompliance,
                cigaretteEquivalent = cigaretteEquivalent,
                healthRiskLevel = healthRiskLevel,
                recommendations = recommendations
            )

            Result.success(healthImpact)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate WHO air quality guidelines compliance
     */
    suspend fun calculateWHOCompliance(
        currentMeasurement: AirQualityMeasurement,
        historicalData: List<AirQualityMeasurement>
    ): WHOCompliance = withContext(dispatcher) {

        val currentPM25 = currentMeasurement.pm25 ?: 0.0

        // Annual guideline (based on historical average)
        val annualAverage = if (historicalData.isNotEmpty()) {
            historicalData.mapNotNull { it.pm25 }.average()
        } else {
            currentPM25
        }

        val annualGuideline = WHOGuideline(
            name = "Annual Guideline",
            limit = 5.0,
            unit = "μg/m³",
            timeframe = "Annual average",
            isExceeded = annualAverage > 5.0,
            currentValue = annualAverage,
            description = "Long-term exposure limit to protect public health"
        )

        // 24-hour guideline
        val dailyGuideline = WHOGuideline(
            name = "24-hour Guideline",
            limit = 15.0,
            unit = "μg/m³",
            timeframe = "Daily average",
            isExceeded = currentPM25 > 15.0,
            currentValue = currentPM25,
            description = "Short-term exposure limit (3-4 exceedances/year allowed)"
        )

        // Interim targets
        val interimTargets = listOf(
            WHOInterimTarget("IT-4", 10.0, "Near-optimal level", currentPM25 <= 10.0, "Significant health benefits"),
            WHOInterimTarget("IT-3", 15.0, "Significant improvement level", currentPM25 <= 15.0, "Lower mortality risk"),
            WHOInterimTarget("IT-2", 25.0, "Moderate improvement level", currentPM25 <= 25.0, "Reduced health risks"),
            WHOInterimTarget("IT-1", 35.0, "First step for highly polluted areas", currentPM25 <= 35.0, "Initial health improvements"),
            WHOInterimTarget("AQG", 5.0, "Ultimate health protection", currentPM25 <= 5.0, "Maximum health protection")
        )

        // Monthly compliance (simplified)
        val monthlyCompliance = calculateMonthlyCompliance(historicalData)

        // Current compliance status
        val currentCompliance = when {
            currentPM25 <= 15.0 -> ComplianceStatus.WITHIN_LIMIT
            currentPM25 <= 25.0 -> ComplianceStatus.MODERATE
            else -> ComplianceStatus.EXCEEDED
        }

        WHOCompliance(
            currentCompliance = currentCompliance,
            annualGuideline = annualGuideline,
            dailyGuideline = dailyGuideline,
            interimTargets = interimTargets,
            monthlyCompliance = monthlyCompliance
        )
    }

    /**
     * Calculate cigarette equivalent for PM2.5 exposure
     */
    suspend fun calculateCigaretteEquivalent(
        pm25Value: Double,
        timeframe: CigaretteTimeframe
    ): CigaretteEquivalent = withContext(dispatcher) {

        // Berkeley Earth research: 1 cigarette ≈ 22 μg/m³ PM2.5 for 1 hour exposure
        val PM25_PER_CIGARETTE_HOUR = 22.0

        val cigarettesPerHour = pm25Value / PM25_PER_CIGARETTE_HOUR
        val totalCigarettes = cigarettesPerHour * timeframe.hours
        val roundedCigarettes = (totalCigarettes * 10).roundToInt() / 10.0

        val explanation = when {
            roundedCigarettes < 0.1 -> "Breathing this air for ${timeframe.displayName} is equivalent to less than 0.1 cigarettes"
            roundedCigarettes < 1.0 -> "Breathing this air for ${timeframe.displayName} is equivalent to ${String.format("%.1f", roundedCigarettes)} cigarettes"
            roundedCigarettes < 10.0 -> "Breathing this air for ${timeframe.displayName} is equivalent to ${String.format("%.1f", roundedCigarettes)} cigarettes"
            else -> "Breathing this air for ${timeframe.displayName} is equivalent to ${roundedCigarettes.roundToInt()} cigarettes"
        }

        CigaretteEquivalent(
            cigaretteCount = roundedCigarettes,
            timeframe = timeframe,
            explanation = explanation
        )
    }

    /**
     * Determine health risk level based on PM2.5 value
     */
    private fun determineHealthRiskLevel(pm25: Double): HealthRiskLevel {
        val category = aqiService.getCategory(pm25, AQIStandard.US_EPA)
        return when (category) {
            AQICategory.GOOD -> HealthRiskLevel.LOW
            AQICategory.MODERATE -> HealthRiskLevel.MODERATE
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> HealthRiskLevel.HIGH
            AQICategory.UNHEALTHY -> HealthRiskLevel.VERY_HIGH
            AQICategory.VERY_UNHEALTHY, AQICategory.HAZARDOUS -> HealthRiskLevel.SEVERE
        }
    }

    /**
     * Generate health recommendations based on risk level
     */
    private fun generateHealthRecommendations(
        riskLevel: HealthRiskLevel
    ): List<String> {
        return when (riskLevel) {
            HealthRiskLevel.LOW -> listOf(
                "Air quality is good for outdoor activities",
                "No special precautions needed"
            )
            HealthRiskLevel.MODERATE -> listOf(
                "Air quality is acceptable for most people",
                "Sensitive individuals should consider limiting prolonged outdoor exertion"
            )
            HealthRiskLevel.HIGH -> listOf(
                "Sensitive groups should avoid outdoor activities",
                "Everyone else should limit prolonged outdoor exertion",
                "Consider wearing a mask when outdoors"
            )
            HealthRiskLevel.VERY_HIGH -> listOf(
                "Everyone should avoid outdoor activities",
                "Wear a mask if you must go outside",
                "Keep windows closed and use air purifiers indoors"
            )
            HealthRiskLevel.SEVERE -> listOf(
                "Stay indoors and keep activity levels low",
                "Avoid all outdoor activities",
                "Use air purifiers and avoid opening windows",
                "Seek medical attention if experiencing breathing difficulties"
            )
        }
    }

    /**
     * Calculate monthly compliance (simplified implementation)
     */
    private fun calculateMonthlyCompliance(
        historicalData: List<AirQualityMeasurement>
    ): MonthlyCompliance {
        val currentMonth = LocalDate.now()
        if (historicalData.isEmpty()) {
            return MonthlyCompliance(
                month = currentMonth,
                totalDays = 0,
                daysWithinLimit = 0,
                daysExceeded = 0,
                compliancePercentage = 0.0,
                dailyCompliance = emptyList()
            )
        }

        val complianceByDay = historicalData
            .groupBy { it.timestamp.toLocalDate() }
            .map { (date, measurements) ->
                val values = measurements.mapNotNull { it.pm25 }
                val average = if (values.isNotEmpty()) values.average() else null
                val status = when {
                    average == null -> ComplianceStatus.NO_DATA
                    average <= 15.0 -> ComplianceStatus.WITHIN_LIMIT
                    average <= 25.0 -> ComplianceStatus.MODERATE
                    else -> ComplianceStatus.EXCEEDED
                }

                DailyComplianceStatus(
                    date = date,
                    value = average,
                    status = status
                )
            }
            .sortedBy { it.date }

        val daysWithinLimit = complianceByDay.count { it.status == ComplianceStatus.WITHIN_LIMIT }
        val daysExceeded = complianceByDay.count { it.status == ComplianceStatus.EXCEEDED }
        val totalDays = complianceByDay.size

        val referenceMonth = complianceByDay.firstOrNull()?.date?.withDayOfMonth(1) ?: currentMonth

        return MonthlyCompliance(
            month = referenceMonth,
            totalDays = totalDays,
            daysWithinLimit = daysWithinLimit,
            daysExceeded = daysExceeded,
            compliancePercentage = if (totalDays == 0) 0.0 else (daysWithinLimit.toDouble() / totalDays) * 100,
            dailyCompliance = complianceByDay
        )
    }
}
