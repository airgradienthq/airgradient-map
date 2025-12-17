package com.airgradient.android.data.models

import com.airgradient.android.domain.models.AQICategory as DomainAQICategory
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.AirQualityInsightKey
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.ComplianceStatus as DomainComplianceStatus
import com.airgradient.android.domain.models.HealthRiskLevel as DomainHealthRiskLevel
import com.airgradient.android.ui.community.ViewModels.FeaturedProjectsUiState
import com.airgradient.android.ui.community.ViewModels.ProjectDetailUiState
import com.airgradient.android.domain.models.FeaturedCommunityInfo
import com.airgradient.android.domain.services.AQIService

/**
 * Main UI state for the location detail bottom sheet
 */
data class LocationDetailUiState(
    val isVisible: Boolean = false,
    val isExpanded: Boolean = false,
    val currentLocationId: Int? = null,  // Track the current location ID being shown
    val location: LocationDetail? = null,
    val historicalData: HistoricalData? = null,
    val chartTimeframe: ChartTimeframe = ChartTimeframe.HOURLY,
    val cigaretteEquivalence: CigaretteEquivalenceState = CigaretteEquivalenceState(),
    val whoCompliance: WHOCompliance? = null,
    val measurementType: MeasurementType = MeasurementType.PM25,
    val displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    val airQualityInsights: AirQualityInsightsState = AirQualityInsightsState(),
    val communityProjects: FeaturedProjectsUiState = FeaturedProjectsUiState(),
    val communityProjectDetail: ProjectDetailUiState = ProjectDetailUiState(),
    val heatMap: HeatMapUiState = HeatMapUiState(),
    val featuredCommunity: FeaturedCommunityUiState = FeaturedCommunityUiState(),
    val isLoading: Boolean = false,
    val isLoadingHistorical: Boolean = false,
    val hasActiveNotifications: Boolean = false,
    val isBookmarked: Boolean = false,
    val error: String? = null,
    val shareState: ShareUiState = ShareUiState()
)

data class FeaturedCommunityUiState(
    val isLoading: Boolean = false,
    val info: FeaturedCommunityInfo? = null,
    val error: Boolean = false
)

data class ShareUiState(
    val isGenerating: Boolean = false,
    val isReady: Boolean = false,
    val errorMessage: String? = null
)

data class HeatMapUiState(
    val cells: List<HeatMapDataPoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: Boolean = false,
    val measurementType: MeasurementType = MeasurementType.PM25,
    val displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    val baseEpochMillis: Long = System.currentTimeMillis()
)

data class HeatMapResponse(
    val points: List<HeatMapDataPoint>,
    val displayUnit: AQIDisplayUnit,
    val generatedAt: Long
)

/**
 * Detailed location information
 */
data class LocationDetail(
    val id: Int,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val currentPM25: Double,
    val currentCO2: Double? = null,
    val temperature: Double? = null,
    val humidity: Double? = null,
    val aqiValue: Int,
    val aqiCategory: AQICategory,
    val lastUpdated: String,
    val ownerId: Int? = null,
    val organization: OrganizationInfo? = null,
    val sensorType: String? = null,
    val dataSource: String? = null,
    val license: String? = null
)

/**
 * Organization/Partner information
 */
data class OrganizationInfo(
    val id: Int,
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
    val websiteUrl: String? = null,
    val type: OrganizationType
)

enum class OrganizationType {
    SCHOOL,
    GOVERNMENT,
    NGO,
    COMMUNITY,
    COMMERCIAL,
    RESEARCH,
    UNICEF,
    OTHER
}

/**
 * AQI Categories based on EPA standards
 */
enum class AQICategory(
    val displayName: String,
    val colorHex: String
) {
    GOOD("Good", "#33CC33"),
    MODERATE("Moderate", "#FFD933"),
    UNHEALTHY_FOR_SENSITIVE("Unhealthy for Sensitive Groups", "#FF9933"),
    UNHEALTHY("Unhealthy", "#E63333"),
    VERY_UNHEALTHY("Very Unhealthy", "#9933E6"),
    HAZARDOUS("Hazardous", "#8C3333");

    companion object {
        fun fromPM25(value: Double): AQICategory {
            val domainCategory = com.airgradient.android.domain.services.AQIService.categoryForUsepaPm25(value)
            return when (domainCategory) {
                DomainAQICategory.GOOD -> GOOD
                DomainAQICategory.MODERATE -> MODERATE
                DomainAQICategory.UNHEALTHY_FOR_SENSITIVE -> UNHEALTHY_FOR_SENSITIVE
                DomainAQICategory.UNHEALTHY -> UNHEALTHY
                DomainAQICategory.VERY_UNHEALTHY -> VERY_UNHEALTHY
                DomainAQICategory.HAZARDOUS -> HAZARDOUS
            }
        }
    }
}

/**
 * Historical data container
 */
data class HistoricalData(
    val hourlyData: List<HistoricalDataPointDetail>? = null,
    val dailyData: List<HistoricalDataPointDetail>? = null,
    val hourlyAverage: Double? = null,
    val dailyAverage: Double? = null,
    val last7DaysAverage: Double? = null,
    val last30DaysAverage: Double? = null
)

/**
 * Individual data point for historical charts
 */
data class HistoricalDataPointDetail(
    val timestamp: String,
    val value: Double,
    val aqiCategory: AQICategory,
    val hour: Int? = null, // For hourly data
    val date: String? = null // For daily data
)

/**
 * Chart display timeframes
 */
enum class ChartTimeframe(val displayName: String) {
    HOURLY("Hourly"),
    DAILY("Daily")
}

/**
 * Cigarette equivalent calculation timeframes
 */
enum class CigaretteTimeframe(
    val displayName: String,
    val hours: Int,
    val days: Int
) {
    DAY("24h", 24, 1),
    WEEK("7d", 168, 7),
    MONTH("30d", 720, 30)
}

/**
 * Cigarette equivalent calculation result
 */
data class CigaretteCalculation(
    val cigaretteCount: Double,
    val timeframe: CigaretteTimeframe,
    val locationName: String,
    val explanationText: String,
    val calculationBasis: String = "Based on Berkeley Earth research: 1 cigarette ≈ 22 μg/m³ PM2.5 for 1 hour exposure"
)

data class CigaretteEquivalenceState(
    val isLoading: Boolean = false,
    val value30Days: Double? = null,
    val error: Boolean = false
)

data class CigaretteData(
    val last24hours: Double,
    val last7days: Double,
    val last30days: Double,
    val last365days: Double
)

data class CigaretteResponse(
    val last24hours: Double?,
    val last7days: Double?,
    val last30days: Double?,
    val last365days: Double?
)

fun CigaretteResponse.toData(): CigaretteData? {
    val last30 = last30days ?: return null
    return CigaretteData(
        last24hours = last24hours ?: 0.0,
        last7days = last7days ?: 0.0,
        last30days = last30,
        last365days = last365days ?: 0.0
    )
}

data class AirQualityInsightsState(
    val actions: List<AirQualityInsightKey> = emptyList(),
    val accentColor: String? = null,
    val mascotAssetName: String = "mascot-idea",
    val hasValidData: Boolean = false
)

/**
 * WHO Guidelines compliance information
 */
data class WHOCompliance(
    val currentCompliance: ComplianceStatus,
    val annualGuideline: WHOGuideline,
    val dailyGuideline: WHOGuideline,
    val interimTargets: List<WHOInterimTarget>,
    val last30DaysCompliance: MonthlyCompliance,
    val healthRiskLevel: HealthRiskLevel
)

/**
 * WHO Guideline definition
 */
data class WHOGuideline(
    val name: String,
    val limit: Double,
    val unit: String = "μg/m³",
    val timeframe: String,
    val isExceeded: Boolean,
    val currentValue: Double
)

/**
 * WHO Interim targets for areas with high pollution
 */
data class WHOInterimTarget(
    val level: String, // IT-1, IT-2, IT-3, IT-4, AQG
    val value: Double,
    val description: String,
    val isMet: Boolean
)

/**
 * Monthly compliance tracking
 */
data class MonthlyCompliance(
    val totalDays: Int,
    val daysWithinLimit: Int,
    val daysExceeded: Int,
    val compliancePercentage: Double,
    val dailyCompliance: List<DailyComplianceStatus>
)

/**
 * Daily compliance status for calendar view
 */
data class DailyComplianceStatus(
    val date: String,
    val dayOfMonth: Int,
    val value: Double?,
    val status: ComplianceStatus
)

/**
 * Compliance status categories
 */
typealias ComplianceStatus = DomainComplianceStatus
typealias HealthRiskLevel = DomainHealthRiskLevel

val ComplianceStatus.colorHex: String
    get() = when (this) {
        ComplianceStatus.WITHIN_LIMIT -> "#33CC33"
        ComplianceStatus.MODERATE -> "#FFD933"
        ComplianceStatus.EXCEEDED -> "#E63333"
        ComplianceStatus.NO_DATA -> "#808080"
    }

val HealthRiskLevel.colorHex: String
    get() = when (this) {
        HealthRiskLevel.LOW -> "#33CC33"
        HealthRiskLevel.MODERATE -> "#FFD933"
        HealthRiskLevel.HIGH -> "#FF9933"
        HealthRiskLevel.VERY_HIGH -> "#E63333"
        HealthRiskLevel.SEVERE -> "#8C3333"
    }

/**
 * Share content formats
 */
