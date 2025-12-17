package com.airgradient.android.ui.locationdetail.Utils

import com.airgradient.android.data.models.*
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.services.AQIService

/**
 * Utility for checking WHO air quality guidelines compliance
 */
object WHOComplianceChecker {
    
    // WHO Air Quality Guidelines (2021 update)
    private const val WHO_ANNUAL_LIMIT = 5.0  // μg/m³
    private const val WHO_24HOUR_LIMIT = 15.0 // μg/m³
    
    // WHO Interim Targets
    private val INTERIM_TARGETS = listOf(
        WHOInterimTarget("IT-4", 10.0, "Near-optimal level", false),
        WHOInterimTarget("IT-3", 15.0, "Significant improvement level", false),
        WHOInterimTarget("IT-2", 25.0, "Moderate improvement level", false),
        WHOInterimTarget("IT-1", 35.0, "First step for highly polluted areas", false),
        WHOInterimTarget("AQG", 5.0, "Ultimate health protection", false)
    )
    
    /**
     * Check WHO compliance for current and historical data
     */
    fun checkCompliance(
        currentPM25: Double,
        historicalData: HistoricalData? = null
    ): WHOCompliance {
        
        // Check annual guideline compliance
        val annualAverage = historicalData?.last30DaysAverage ?: currentPM25
        val annualGuideline = WHOGuideline(
            name = "Annual Guideline",
            limit = WHO_ANNUAL_LIMIT,
            timeframe = "Annual average limit",
            isExceeded = annualAverage > WHO_ANNUAL_LIMIT,
            currentValue = annualAverage
        )
        
        // Check 24-hour guideline compliance
        val dailyAverage = historicalData?.dailyAverage ?: currentPM25
        val dailyGuideline = WHOGuideline(
            name = "24-hour Guideline",
            limit = WHO_24HOUR_LIMIT,
            timeframe = "Daily exposure limit (3-4 exceedances/year allowed)",
            isExceeded = dailyAverage > WHO_24HOUR_LIMIT,
            currentValue = dailyAverage
        )
        
        // Check interim targets
        val interimTargets = INTERIM_TARGETS.map { target ->
            target.copy(isMet = currentPM25 <= target.value)
        }
        
        // Calculate monthly compliance
        val monthlyCompliance = calculateMonthlyCompliance(historicalData)
        
        // Determine current compliance status
        val currentCompliance = when {
            currentPM25 <= WHO_24HOUR_LIMIT -> ComplianceStatus.WITHIN_LIMIT
            currentPM25 <= 25.0 -> ComplianceStatus.MODERATE
            else -> ComplianceStatus.EXCEEDED
        }
        
        // Assess health risk level
        val healthRiskLevel = assessHealthRisk(currentPM25)
        
        return WHOCompliance(
            currentCompliance = currentCompliance,
            annualGuideline = annualGuideline,
            dailyGuideline = dailyGuideline,
            interimTargets = interimTargets,
            last30DaysCompliance = monthlyCompliance,
            healthRiskLevel = healthRiskLevel
        )
    }
    
    /**
     * Calculate monthly compliance statistics
     */
    private fun calculateMonthlyCompliance(historicalData: HistoricalData?): MonthlyCompliance {
        val dailyData = historicalData?.dailyData ?: emptyList()
        
        if (dailyData.isEmpty()) {
            return MonthlyCompliance(
                totalDays = 30,
                daysWithinLimit = 0,
                daysExceeded = 0,
                compliancePercentage = 0.0,
                dailyCompliance = emptyList()
            )
        }
        
        val dailyCompliance = dailyData.mapIndexed { index, dataPoint ->
            val status = when {
                dataPoint.value <= WHO_24HOUR_LIMIT -> ComplianceStatus.WITHIN_LIMIT
                dataPoint.value <= 25.0 -> ComplianceStatus.MODERATE
                else -> ComplianceStatus.EXCEEDED
            }
            
            DailyComplianceStatus(
                date = dataPoint.date ?: "",
                dayOfMonth = index + 1,
                value = dataPoint.value,
                status = status
            )
        }
        
        val daysWithinLimit = dailyCompliance.count { it.status == ComplianceStatus.WITHIN_LIMIT }
        val daysExceeded = dailyCompliance.count { it.status == ComplianceStatus.EXCEEDED }
        
        return MonthlyCompliance(
            totalDays = dailyCompliance.size,
            daysWithinLimit = daysWithinLimit,
            daysExceeded = daysExceeded,
            compliancePercentage = (daysWithinLimit.toDouble() / dailyCompliance.size) * 100,
            dailyCompliance = dailyCompliance
        )
    }
    
    /**
     * Assess health risk level based on PM2.5 value
     */
    private fun assessHealthRisk(pm25Value: Double): HealthRiskLevel {
        if (pm25Value <= WHO_24HOUR_LIMIT) return HealthRiskLevel.LOW
        return when (AQIService.categoryForUsepaPm25(pm25Value)) {
            AQICategory.MODERATE -> HealthRiskLevel.MODERATE
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> HealthRiskLevel.HIGH
            AQICategory.UNHEALTHY -> HealthRiskLevel.VERY_HIGH
            AQICategory.VERY_UNHEALTHY, AQICategory.HAZARDOUS -> HealthRiskLevel.SEVERE
            else -> HealthRiskLevel.LOW
        }
    }
    
    /**
     * Get health recommendations based on PM2.5 level
     */
    fun getHealthRecommendations(pm25Value: Double): List<String> {
        if (pm25Value <= WHO_24HOUR_LIMIT) {
            return listOf(
                "Air quality is good. Enjoy outdoor activities!",
                "No special precautions needed."
            )
        }

        return when (AQIService.categoryForUsepaPm25(pm25Value)) {
            AQICategory.MODERATE -> listOf(
                "Sensitive individuals should consider limiting prolonged outdoor exertion.",
                "Monitor for symptoms like coughing or shortness of breath."
            )
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> listOf(
                "Children, elderly, and people with heart/lung conditions should reduce outdoor activities.",
                "Everyone else should limit prolonged outdoor exertion.",
                "Keep windows closed when indoors."
            )
            AQICategory.UNHEALTHY -> listOf(
                "Everyone should avoid prolonged outdoor exertion.",
                "Move activities indoors or reschedule.",
                "Use air purifiers if available.",
                "Wear N95/KN95 masks if going outside."
            )
            AQICategory.VERY_UNHEALTHY, AQICategory.HAZARDOUS -> listOf(
                "Health warning of emergency conditions!",
                "Everyone should avoid all outdoor exertion.",
                "Remain indoors with windows and doors closed.",
                "Use air purifiers on highest setting.",
                "Seek medical attention if experiencing symptoms."
            )
            else -> listOf(
                "Monitor air quality updates for changes.",
                "Adjust outdoor plans based on conditions."
            )
        }
    }
    
    /**
     * Calculate WHO compliance percentage for a given period
     */
    fun calculateCompliancePercentage(dataPoints: List<Double>): Double {
        if (dataPoints.isEmpty()) return 0.0
        
        val compliantDays = dataPoints.count { it <= WHO_24HOUR_LIMIT }
        return (compliantDays.toDouble() / dataPoints.size) * 100
    }
}
