package com.airgradient.android.domain.models

import java.time.LocalDate

/**
 * Domain model for health impact analysis of air quality measurements
 */
data class HealthImpact(
    val whoCompliance: WHOCompliance,
    val cigaretteEquivalent: CigaretteEquivalent,
    val healthRiskLevel: HealthRiskLevel,
    val recommendations: List<String>
)

/**
 * WHO Air Quality Guidelines compliance analysis
 */
data class WHOCompliance(
    val currentCompliance: ComplianceStatus,
    val annualGuideline: WHOGuideline,
    val dailyGuideline: WHOGuideline,
    val interimTargets: List<WHOInterimTarget>,
    val monthlyCompliance: MonthlyCompliance
)

/**
 * WHO air quality guideline definition
 */
data class WHOGuideline(
    val name: String,
    val limit: Double,
    val unit: String,
    val timeframe: String,
    val isExceeded: Boolean,
    val currentValue: Double,
    val description: String
)

/**
 * WHO interim targets for areas with high pollution
 */
data class WHOInterimTarget(
    val level: String, // IT-1, IT-2, IT-3, IT-4, AQG
    val value: Double,
    val description: String,
    val isMet: Boolean,
    val healthBenefit: String
)

/**
 * Monthly compliance tracking
 */
data class MonthlyCompliance(
    val month: LocalDate,
    val totalDays: Int,
    val daysWithinLimit: Int,
    val daysExceeded: Int,
    val compliancePercentage: Double,
    val dailyCompliance: List<DailyComplianceStatus>
) {
    val isCompliant: Boolean
        get() = compliancePercentage >= 85.0 // Allow up to 4 exceedances per month
}

/**
 * Daily compliance status
 */
data class DailyComplianceStatus(
    val date: LocalDate,
    val value: Double?,
    val status: ComplianceStatus
)

/**
 * Compliance status categories
 */
enum class ComplianceStatus(val displayName: String) {
    WITHIN_LIMIT("Within WHO limit"),
    MODERATE("Moderate"),
    EXCEEDED("Exceeded"),
    NO_DATA("No data")
}

/**
 * Health risk assessment levels
 */
enum class HealthRiskLevel(
    val displayName: String,
    val description: String,
    val actionRequired: String
) {
    LOW("Low Risk", "Air quality is satisfactory", "None"),
    MODERATE("Moderate Risk", "Acceptable for most, sensitive groups may experience minor effects", "Limit outdoor activities if sensitive"),
    HIGH("High Risk", "Everyone may experience health effects", "Reduce outdoor activities"),
    VERY_HIGH("Very High Risk", "Health warnings of emergency conditions", "Avoid outdoor activities"),
    SEVERE("Severe Risk", "Everyone is at risk of serious health effects", "Stay indoors")
}

/**
 * Cigarette equivalent calculation for PM2.5 exposure
 */
data class CigaretteEquivalent(
    val cigaretteCount: Double,
    val timeframe: CigaretteTimeframe,
    val explanation: String,
    val calculationBasis: String = "Based on Berkeley Earth research: 1 cigarette ≈ 22 μg/m³ PM2.5 for 1 hour exposure"
)

/**
 * Timeframes for cigarette equivalent calculation
 */
enum class CigaretteTimeframe(
    val displayName: String,
    val hours: Int,
    val days: Int
) {
    HOUR("1 hour", 1, 0),
    DAY("24 hours", 24, 1),
    WEEK("7 days", 168, 7),
    MONTH("30 days", 720, 30)
}