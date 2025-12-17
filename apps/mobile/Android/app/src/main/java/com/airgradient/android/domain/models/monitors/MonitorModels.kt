package com.airgradient.android.domain.models.monitors

import java.time.Instant
import java.util.Locale

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT;

    companion object {
        fun fromString(raw: String?): TemperatureUnit? {
            val normalised = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
            return when (normalised) {
                "f", "fahrenheit", "imperial" -> FAHRENHEIT
                "c", "celsius", "metric" -> CELSIUS
                else -> null
            }
        }
    }
}

data class PlacePermissions(
    val canManageSettings: Boolean
)

data class MonitorsPlace(
    val id: Int,
    val name: String,
    val temperatureUnit: TemperatureUnit?,
    val plantowerPm2CorrectionAlgo: String?,
    val permissions: PlacePermissions?
)

data class MonitorMetrics(
    val pm25: Double?,
    val co2: Double?,
    val tvocIndex: Double?,
    val noxIndex: Double?,
    val temperatureCelsius: Double?,
    val humidity: Double?
)

fun MonitorMetrics.temperature(unit: TemperatureUnit): Double? {
    val celsius = temperatureCelsius ?: return null
    return when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }
}

data class PlaceLocation(
    val id: Int,
    val placeId: Int?,
    val name: String,
    val locationType: String?,
    val indoor: Boolean?,
    val active: Boolean,
    val offline: Boolean,
    val metrics: MonitorMetrics
)

data class CurrentLocationReading(
    val locationId: Int,
    val placeId: Int?,
    val indoor: Boolean?,
    val active: Boolean?,
    val offline: Boolean?,
    val metrics: MonitorMetrics,
    val timestamp: Instant?
)

data class HistorySample(
    val timestamp: Instant,
    val value: Double
)

enum class MonitorMeasurementKind(
    val apiValue: String
) {
    PM25("pm02"),
    CO2("rco2"),
    TVOC_INDEX("tvoc_index"),
    NOX_INDEX("nox_index"),
    TEMPERATURE("atmp"),
    HUMIDITY("rhum")
}

enum class ChartTimeRange(
    val apiSince: String,
    val apiBucket: String
) {
    LAST_24_HOURS("24h", "1h"),
    LAST_30_DAYS("30d", "1d");

    companion object {
        val default: ChartTimeRange = LAST_24_HOURS
        val all: List<ChartTimeRange> = values().toList()
    }
}

data class HistoryRequest(
    val placeId: Int,
    val locationId: Int,
    val measurement: MonitorMeasurementKind,
    val timeRange: ChartTimeRange
)

fun MonitorMeasurementKind.labelResId(): Int = when (this) {
    MonitorMeasurementKind.PM25 -> com.airgradient.android.R.string.monitor_metric_pm25
    MonitorMeasurementKind.CO2 -> com.airgradient.android.R.string.monitor_metric_co2
    MonitorMeasurementKind.TVOC_INDEX -> com.airgradient.android.R.string.monitor_metric_tvoc
    MonitorMeasurementKind.NOX_INDEX -> com.airgradient.android.R.string.monitor_metric_nox
    MonitorMeasurementKind.TEMPERATURE -> com.airgradient.android.R.string.monitor_metric_temperature
    MonitorMeasurementKind.HUMIDITY -> com.airgradient.android.R.string.monitor_metric_humidity
}

fun MonitorMeasurementKind.unitResId(temperatureUnit: TemperatureUnit): Int = when (this) {
    MonitorMeasurementKind.PM25 -> com.airgradient.android.R.string.unit_micrograms_per_cubic_meter
    MonitorMeasurementKind.CO2 -> com.airgradient.android.R.string.unit_ppm
    MonitorMeasurementKind.TVOC_INDEX, MonitorMeasurementKind.NOX_INDEX -> com.airgradient.android.R.string.unit_index
    MonitorMeasurementKind.TEMPERATURE -> if (temperatureUnit == TemperatureUnit.FAHRENHEIT) {
        com.airgradient.android.R.string.unit_temperature_fahrenheit
    } else {
        com.airgradient.android.R.string.unit_temperature_celsius
    }
    MonitorMeasurementKind.HUMIDITY -> com.airgradient.android.R.string.unit_percent
}

fun ChartTimeRange.labelResId(): Int = when (this) {
    ChartTimeRange.LAST_24_HOURS -> com.airgradient.android.R.string.chart_time_24h
    ChartTimeRange.LAST_30_DAYS -> com.airgradient.android.R.string.chart_time_30d
}

fun ChartTimeRange.shortLabelResId(): Int = when (this) {
    ChartTimeRange.LAST_24_HOURS -> com.airgradient.android.R.string.chart_time_24h_short
    ChartTimeRange.LAST_30_DAYS -> com.airgradient.android.R.string.chart_time_30d_short
}
