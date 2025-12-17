package com.airgradient.android.ui.mymonitors.Views

import androidx.compose.ui.graphics.Color
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.monitors.TemperatureUnit
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.domain.models.AQIDisplayUnit

internal object MonitorMetricColors {
    private val Green = Color(0xFF33CC33)
    private val Yellow = Color(0xFFF0B900)
    private val Orange = Color(0xFFFF9933)
    private val Red = Color(0xFFE63333)

    fun colorForTvoc(value: Double): Color = when {
        value <= 150.0 -> Green
        value <= 250.0 -> Yellow
        value <= 400.0 -> Orange
        else -> Red
    }

    fun colorForNox(value: Double): Color = when {
        value <= 20.0 -> Green
        value <= 150.0 -> Yellow
        value <= 300.0 -> Orange
        else -> Red
    }

    fun colorForTemperature(value: Double, unit: TemperatureUnit): Color {
        val celsius = if (unit == TemperatureUnit.CELSIUS) value else (value - 32.0) * 5.0 / 9.0
        return when {
            celsius <= 32.0 -> Green
            celsius <= 40.0 -> Yellow
            celsius <= 53.0 -> Orange
            else -> Red
        }
    }

    fun colorForHumidity(value: Double): Color = when {
        value <= 30.0 -> Yellow
        value <= 60.0 -> Green
        value <= 80.0 -> Orange
        else -> Red
    }

    fun colorForCo2(value: Double): Color = EPAColorCoding.colorForMeasurement(
        value,
        MeasurementType.CO2,
        AQIDisplayUnit.USAQI
    )
}
