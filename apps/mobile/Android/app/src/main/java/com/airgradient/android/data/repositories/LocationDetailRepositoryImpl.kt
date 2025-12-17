package com.airgradient.android.data.repositories

import android.content.Context
import com.airgradient.android.data.models.*
import com.airgradient.android.data.local.datastore.AppSettingsDataStore
import com.airgradient.android.data.network.ApiResult
import com.airgradient.android.data.network.NetworkError
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.HealthRiskLevel
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.WHOGuideline as DomainWHOGuideline
import com.airgradient.android.domain.models.WHOCompliance as DomainWHOCompliance
import com.airgradient.android.domain.models.WHOInterimTarget as DomainWHOInterimTarget
import com.airgradient.android.domain.models.MonthlyCompliance as DomainMonthlyCompliance
import com.airgradient.android.domain.models.DailyComplianceStatus as DomainDailyComplianceStatus
import com.airgradient.android.domain.repositories.TimeInterval
import com.airgradient.android.domain.repositories.TimeRange
import com.airgradient.android.domain.usecases.CalculateHealthImpactUseCase
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LocationDetailRepository
 * Currently provides mock data for development - to be replaced with real API calls
 */
@Singleton
class LocationDetailRepositoryImpl @Inject constructor(
    private val airQualityRepository: AirQualityRepositoryImpl,
    private val calculateHealthImpactUseCase: CalculateHealthImpactUseCase,
    @ApplicationContext context: Context
) : LocationDetailRepository {

    private val appSettingsDataStore = AppSettingsDataStore(context)

    override suspend fun getLocationDetail(locationId: Int): LocationDetail {
        // Log for debugging
        android.util.Log.d("LocationDetailRepo", "getLocationDetail called with locationId: $locationId")

        // Get real location info from AirGradient API
        val locationInfoResult = airQualityRepository.getLocationInfo(locationId)
        val currentDataResult = airQualityRepository.getCurrentMeasurements(locationId)

        // Extract location name from API or use fallback
        val locationInfo = (locationInfoResult as? ApiResult.Success)?.data

        val locationName = locationInfo?.locationName ?: "Unknown Location #$locationId"

        // Extract current measurements for realistic data
        val currentData = when (currentDataResult) {
            is ApiResult.Success -> currentDataResult.data
            else -> null
        }

        val pm25Value = currentData?.pm25Value ?: 0.0
        val co2Value = currentData?.co2Value
        val temperature = currentData?.temperature
        val humidity = currentData?.humidity

        val lastUpdatedRaw = currentData?.timestamp
        val lastUpdatedDisplay = lastUpdatedRaw?.takeUnless { it.equals("Recently", ignoreCase = true) } ?: ""

        return LocationDetail(
            id = locationId,
            name = locationName,
            address = (locationInfoResult as? ApiResult.Success)?.data?.description ?: "Southeast Asia",
            latitude = (locationInfoResult as? ApiResult.Success)?.data?.latitude ?: 17.9667,
            longitude = (locationInfoResult as? ApiResult.Success)?.data?.longitude ?: 102.6000,
            currentPM25 = pm25Value,
            currentCO2 = co2Value,
            temperature = temperature,
            humidity = humidity,
            aqiValue = EPAColorCoding.getAQIFromPM25(pm25Value),
            aqiCategory = AQICategory.fromPM25(pm25Value),
            lastUpdated = lastUpdatedDisplay,
            ownerId = locationInfo?.ownerId,
            organization = locationInfo?.ownerName?.let { ownerName ->
                OrganizationInfo(
                    id = locationInfo.ownerId ?: 0,
                    name = ownerName,
                    displayName = locationInfo.displayOwnerName,
                    description = null,
                    logoUrl = null,
                    websiteUrl = locationInfo.url,
                    type = OrganizationType.OTHER
                )
            },
            sensorType = (locationInfoResult as? ApiResult.Success)?.data?.sensorType ?: "AirGradient",
            dataSource = locationInfo?.dataSource ?: "AirGradient Network",
            license = locationInfo?.license
        )
    }

    override suspend fun getHistoricalData(
        locationId: Int,
        timeframe: ChartTimeframe,
        measurementType: MeasurementType
    ): HistoricalData {
        // Use real API call instead of mock data
        val historicalResult = try {
            // Calculate proper historical time ranges (no future data)
            val endTime = System.currentTimeMillis() - (5 * 60 * 1000L) // 5 minutes ago to ensure no future data
            val (startTime, bucketSize) = when (timeframe) {
                ChartTimeframe.HOURLY -> Pair(
                    endTime - (24 * 60 * 60 * 1000L), // Last 24 hours
                    "1h"
                )
                ChartTimeframe.DAILY -> Pair(
                    endTime - (30 * 24 * 60 * 60 * 1000L), // Last 30 days
                    "1d"
                )
            }

            // Format timestamps in ISO8601 format like iOS
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val startTimeStr = formatter.format(java.util.Date(startTime))
            val endTimeStr = formatter.format(java.util.Date(endTime))

            android.util.Log.d("LocationDetailRepo", "Fetching historical data: start=$startTimeStr, end=$endTimeStr, bucket=$bucketSize")

            airQualityRepository.getHistoricalData(
                locationId = locationId,
                startTime = startTimeStr,
                endTime = endTimeStr,
                bucketSize = bucketSize,
                measure = measurementType.apiValue
            )
        } catch (e: Exception) {
            android.util.Log.e("LocationDetailRepo", "Failed to fetch historical data", e)
            ApiResult.Error(NetworkError.from(e))
        }

        // Convert API result to HistoricalData format
        return when (historicalResult) {
            is ApiResult.Success -> {
                val apiPoints = historicalResult.data
                android.util.Log.d("LocationDetailRepo", "Received ${apiPoints.size} historical data points")

                // Convert to LocationDetail HistoricalDataPoint format
                val convertedPoints = apiPoints.mapNotNull { mapApiPoint ->
                    val measurementValue = when (measurementType) {
                        MeasurementType.PM25 -> mapApiPoint.pm25
                        MeasurementType.CO2 -> mapApiPoint.co2
                    }?.takeIf { it.isFinite() && it >= 0 }

                    val value = measurementValue ?: return@mapNotNull null
                    val category = when (measurementType) {
                        MeasurementType.PM25 -> AQICategory.fromPM25(value)
                        MeasurementType.CO2 -> AQICategory.GOOD
                    }

                    HistoricalDataPointDetail(
                        timestamp = mapApiPoint.timestamp,
                        value = value,
                        aqiCategory = category,
                        hour = null,
                        date = null
                    )
                }

                val average = convertedPoints.takeIf { it.isNotEmpty() }
                    ?.map { it.value }
                    ?.average()
                    ?: 0.0

                when (timeframe) {
                    ChartTimeframe.HOURLY -> HistoricalData(
                        hourlyData = convertedPoints,
                        hourlyAverage = average,
                        last7DaysAverage = average, // Placeholder
                        last30DaysAverage = average // Placeholder
                    )
                    ChartTimeframe.DAILY -> HistoricalData(
                        dailyData = convertedPoints,
                        dailyAverage = average,
                        last7DaysAverage = average,
                        last30DaysAverage = average
                    )
                }
            }
            is ApiResult.Error -> {
                android.util.Log.e("LocationDetailRepo", "API error: ${historicalResult.error}")
                // Fallback to empty data instead of mock data
                when (timeframe) {
                    ChartTimeframe.HOURLY -> HistoricalData(
                        hourlyData = emptyList(),
                        hourlyAverage = 0.0,
                        last7DaysAverage = 0.0,
                        last30DaysAverage = 0.0
                    )
                    ChartTimeframe.DAILY -> HistoricalData(
                        dailyData = emptyList(),
                        dailyAverage = 0.0,
                        last7DaysAverage = 0.0,
                        last30DaysAverage = 0.0
                    )
                }
            }
            is ApiResult.Loading -> {
                // Handle loading state - return empty data for now
                when (timeframe) {
                    ChartTimeframe.HOURLY -> HistoricalData(
                        hourlyData = emptyList(),
                        hourlyAverage = 0.0,
                        last7DaysAverage = 0.0,
                        last30DaysAverage = 0.0
                    )
                    ChartTimeframe.DAILY -> HistoricalData(
                        dailyData = emptyList(),
                        dailyAverage = 0.0,
                        last7DaysAverage = 0.0,
                        last30DaysAverage = 0.0
                    )
                }
            }
        }
    }

    override suspend fun getHeatMapData(
        locationId: Int,
        measurementType: MeasurementType
    ): ApiResult<HeatMapResponse> {
        return try {
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone).withMinute(0).withSecond(0).withNano(0)
            val endInstant = now.toInstant()
            val startInstant = now.minusDays(6).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant()

            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
            val apiResult = airQualityRepository.getHistoricalData(
                locationId = locationId,
                startTime = formatter.format(startInstant),
                endTime = formatter.format(endInstant),
                bucketSize = "1h",
                measure = measurementType.apiValue
            )

            when (apiResult) {
                is ApiResult.Success -> {
                    val valuesBySlot = mutableMapOf<Pair<Int, Int>, Double?>()
                    val today = now.toLocalDate()

                    apiResult.data.forEach { point ->
                        val timestamp = point.timestamp ?: return@forEach
                        val instant = runCatching { Instant.parse(timestamp) }.getOrNull() ?: return@forEach
                        val localTime = instant.atZone(zone)
                        val dayOffset = ChronoUnit.DAYS.between(localTime.toLocalDate(), today).toInt()

                        if (dayOffset !in 0..6) return@forEach

                        val hour = localTime.hour
                        val value = when (measurementType) {
                            MeasurementType.PM25 -> point.pm25
                            MeasurementType.CO2 -> point.co2
                        }?.takeIf { it.isFinite() && it >= 0 }

                        valuesBySlot[dayOffset to hour] = value
                    }

                    val grid = mutableListOf<HeatMapDataPoint>()

                    for (day in 0..6) {
                        val dayTime = now.minusDays(day.toLong())
                        for (hour in 0..23) {
                            val cellDateTime = dayTime.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                            val isFutureHour = day == 0 && hour > now.hour
                            val slotValue = if (isFutureHour) null else valuesBySlot[day to hour]
                            grid.add(
                                HeatMapDataPoint(
                                    day = day,
                                    hour = hour,
                                    value = slotValue,
                                    date = cellDateTime.toInstant().toEpochMilli(),
                                    isFuture = isFutureHour
                                )
                            )
                        }
                    }

                    ApiResult.Success(
                        HeatMapResponse(
                            points = grid,
                            displayUnit = resolveDisplayUnit(measurementType),
                            generatedAt = now.toInstant().toEpochMilli()
                        )
                    )
                }

                is ApiResult.Error -> apiResult
                is ApiResult.Loading -> ApiResult.Loading
            }
        } catch (e: Exception) {
            ApiResult.Error(NetworkError.from(e))
        }
    }

    private fun resolveDisplayUnit(measurementType: MeasurementType): AQIDisplayUnit {
        if (measurementType == MeasurementType.CO2) {
            return AQIDisplayUnit.UGM3
        }

        return runBlocking(Dispatchers.IO) {
            appSettingsDataStore.displayUnit.first()
        }
    }

    override suspend fun getOrganizationInfo(organizationId: Int): OrganizationInfo? {
        delay(200)

        return when (organizationId) {
            1 -> OrganizationInfo(
                id = 1,
                name = "UNICEF Laos",
                displayName = "UNICEF Partnership",
                description = "Supporting children's health through clean air monitoring",
                logoUrl = null,
                websiteUrl = "https://www.unicef.org/laos",
                type = OrganizationType.UNICEF
            )
            else -> null
        }
    }

    override suspend fun getWHOCompliance(locationId: Int): WHOCompliance? {
        val currentMeasurement = airQualityRepository.getCurrentMeasurement(locationId)
            .getOrElse {
                android.util.Log.e("LocationDetailRepo", "Failed to load current measurement", it)
                return null
            }

        val endTime = System.currentTimeMillis()
        val startTime = endTime - Duration.ofDays(30).toMillis()
        val timeRange = TimeRange(startTime = startTime, endTime = endTime, interval = TimeInterval.DAILY)

        val historicalMeasurements = airQualityRepository.getHistoricalData(locationId, timeRange)
            .getOrElse {
                android.util.Log.w("LocationDetailRepo", "Unable to load historical measurements: ${it.message}")
                emptyList()
            }

        val healthImpact = calculateHealthImpactUseCase(
            currentMeasurement = currentMeasurement,
            historicalData = historicalMeasurements
        ).getOrElse {
            android.util.Log.e("LocationDetailRepo", "Health impact calculation failed", it)
            return null
        }

        return healthImpact.whoCompliance.toDataModel(healthImpact.healthRiskLevel)
    }

    override suspend fun getCigaretteEquivalence(locationId: Int): CigaretteData? {
        return when (val result = airQualityRepository.getCigaretteEquivalence(locationId)) {
            is ApiResult.Success -> result.data
            else -> null
        }
    }

    override suspend fun getCurrentMeasurement(locationId: Int): ApiResult<AirQualityLocation> {
        android.util.Log.d("LocationDetailRepo", "getCurrentMeasurement called with locationId: $locationId")

        return airQualityRepository.getCurrentMeasurements(locationId)
    }
}

private fun DomainWHOCompliance.toDataModel(healthRiskLevel: HealthRiskLevel): WHOCompliance {
    return WHOCompliance(
        currentCompliance = currentCompliance,
        annualGuideline = annualGuideline.toDataModel(),
        dailyGuideline = dailyGuideline.toDataModel(),
        interimTargets = interimTargets.map { it.toDataModel() },
        last30DaysCompliance = monthlyCompliance.toDataModel(),
        healthRiskLevel = healthRiskLevel
    )
}

private fun DomainWHOGuideline.toDataModel(): WHOGuideline = WHOGuideline(
    name = name,
    limit = limit,
    unit = unit,
    timeframe = timeframe,
    isExceeded = isExceeded,
    currentValue = currentValue
)

private fun DomainWHOInterimTarget.toDataModel(): WHOInterimTarget = WHOInterimTarget(
    level = level,
    value = value,
    description = "$description — $healthBenefit",
    isMet = isMet
)

private fun DomainMonthlyCompliance.toDataModel(): MonthlyCompliance = MonthlyCompliance(
    totalDays = totalDays,
    daysWithinLimit = daysWithinLimit,
    daysExceeded = daysExceeded,
    compliancePercentage = compliancePercentage,
    dailyCompliance = dailyCompliance.map { it.toDataModel() }
)

private fun DomainDailyComplianceStatus.toDataModel(): DailyComplianceStatus = DailyComplianceStatus(
    date = date.format(DateTimeFormatter.ISO_DATE),
    dayOfMonth = date.dayOfMonth,
    value = value,
    status = status
)
