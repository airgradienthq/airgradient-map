package com.airgradient.android.ui.mymonitors.Views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.airgradient.android.R
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.monitors.ChartTimeRange
import com.airgradient.android.domain.models.monitors.HistorySample
import com.airgradient.android.domain.models.monitors.MonitorMeasurementKind
import com.airgradient.android.domain.models.monitors.TemperatureUnit
import com.airgradient.android.domain.models.monitors.labelResId
import com.airgradient.android.domain.models.monitors.shortLabelResId
import com.airgradient.android.domain.models.monitors.unitResId
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.ui.mymonitors.Views.MonitorMetricColors
import com.airgradient.android.ui.shared.Views.AirgradientOutlinedCard
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

@Composable
fun MonitorMeasurementChart(
    samples: List<HistorySample>,
    measurementKind: MonitorMeasurementKind,
    temperatureUnit: TemperatureUnit,
    displayUnit: AQIDisplayUnit,
    selectedRange: ChartTimeRange,
    onRangeSelected: (ChartTimeRange) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    val unitLabel = when {
        measurementKind == MonitorMeasurementKind.PM25 && displayUnit == AQIDisplayUnit.USAQI ->
            stringResource(id = R.string.unit_us_aqi_short)
        else -> stringResource(id = measurementKind.unitResId(temperatureUnit))
    }
    val title = stringResource(measurementKind.labelResId()) + " ($unitLabel)"

    val chartBars = remember(samples, measurementKind, temperatureUnit, displayUnit, colorScheme) {
        buildChartBars(samples, measurementKind, temperatureUnit, displayUnit, colorScheme)
    }

    val valueFormatter = remember(measurementKind, temperatureUnit, displayUnit, locale) {
        createValueFormatter(measurementKind, temperatureUnit, displayUnit, locale)
    }

    AirgradientOutlinedCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                TimeRangeSelector(
                    selected = selectedRange,
                    onSelected = onRangeSelected,
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                )
            }

            ChartContainer(
                bars = chartBars,
                valueFormatter = valueFormatter,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onRetry = onRetry,
                locale = locale
            )
        }
    }
}

@Composable
private fun ChartContainer(
    bars: List<ChartBar>,
    valueFormatter: (Double) -> String,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    locale: Locale
) {
    when {
        isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.my_monitors_chart_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        errorMessage != null -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (errorMessage.isBlank()) stringResource(R.string.my_monitors_chart_error) else errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = stringResource(R.string.my_monitors_retry),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { onRetry() },
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        bars.isEmpty() -> {
            Text(
                text = stringResource(R.string.my_monitors_chart_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> {
            MonitorChartCanvas(
                bars = bars,
                valueFormatter = valueFormatter,
                locale = locale
            )
        }
    }
}

@Composable
private fun MonitorChartCanvas(
    bars: List<ChartBar>,
    valueFormatter: (Double) -> String,
    locale: Locale
) {
    val maxValue = bars.maxOfOrNull { it.displayValue } ?: 0.0
    val safeMax = if (maxValue <= 0.0) 1.0 else maxValue * 1.1
    val yLabels = remember(safeMax, valueFormatter) { buildYAxisLabels(safeMax, valueFormatter) }
    val xLabels = remember(bars, locale) { buildXAxisLabels(bars, locale) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val outlineColor = MaterialTheme.colorScheme.outlineVariant
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val barSpacing = size.width / max(1, bars.size)
                val barWidth = barSpacing * 0.6f
                val maxHeight = size.height

                yLabels.sortedBy { it.value }.forEach { axisLabel ->
                    val ratio = (axisLabel.value / safeMax).coerceIn(0.0, 1.0)
                    val y = maxHeight * (1f - ratio.toFloat())
                    drawLine(
                        color = outlineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                bars.forEachIndexed { index, bar ->
                    val heightRatio = (bar.displayValue / safeMax).toFloat().coerceIn(0f, 1f)
                    val barHeight = maxHeight * heightRatio
                    val xCenter = index * barSpacing + barSpacing / 2f
                    val left = xCenter - barWidth / 2f
                    drawRect(
                        color = bar.color,
                        topLeft = androidx.compose.ui.geometry.Offset(left, maxHeight - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                xLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            yLabels.sortedByDescending { it.value }.forEach { label ->
                Text(
                    text = label.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selected: ChartTimeRange,
    onSelected: (ChartTimeRange) -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChartTimeRange.all.forEach { range ->
            val isSelected = range == selected
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onSelected(range) }
            ) {
                Text(
                    text = stringResource(range.shortLabelResId()),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private data class ChartBar(
    val timestamp: Instant,
    val rawValue: Double,
    val displayValue: Double,
    val color: Color
)

private fun buildChartBars(
    samples: List<HistorySample>,
    kind: MonitorMeasurementKind,
    temperatureUnit: TemperatureUnit,
    displayUnit: AQIDisplayUnit,
    colors: ColorScheme
): List<ChartBar> {
    if (samples.isEmpty()) return emptyList()
    return samples.sortedBy { it.timestamp }.mapNotNull { sample ->
        val rawValue = sample.value
        if (!rawValue.isFinite()) {
            return@mapNotNull null
        }
        val displayValue = when (kind) {
            MonitorMeasurementKind.TEMPERATURE -> convertTemperature(rawValue, temperatureUnit)
            MonitorMeasurementKind.PM25 -> convertPmToDisplay(rawValue, displayUnit)
            else -> rawValue
        }
        val barColor = when (kind) {
            MonitorMeasurementKind.PM25 -> EPAColorCoding.getColorForPM25(rawValue)
            MonitorMeasurementKind.CO2 -> MonitorMetricColors.colorForCo2(rawValue)
            MonitorMeasurementKind.TVOC_INDEX -> MonitorMetricColors.colorForTvoc(rawValue)
            MonitorMeasurementKind.NOX_INDEX -> MonitorMetricColors.colorForNox(rawValue)
            MonitorMeasurementKind.TEMPERATURE -> MonitorMetricColors.colorForTemperature(rawValue, temperatureUnit)
            MonitorMeasurementKind.HUMIDITY -> MonitorMetricColors.colorForHumidity(rawValue)
        }
        ChartBar(
            timestamp = sample.timestamp,
            rawValue = rawValue,
            displayValue = displayValue,
            color = barColor
        )
    }
}

private fun convertTemperature(value: Double, unit: TemperatureUnit): Double {
    return if (unit == TemperatureUnit.FAHRENHEIT) {
        value * 9.0 / 5.0 + 32.0
    } else value
}

private fun convertPmToDisplay(value: Double, displayUnit: AQIDisplayUnit): Double {
    return when (displayUnit) {
        AQIDisplayUnit.USAQI -> EPAColorCoding.getAQIFromPM25(value).toDouble()
        AQIDisplayUnit.UGM3 -> value
    }
}

private fun createValueFormatter(
    kind: MonitorMeasurementKind,
    temperatureUnit: TemperatureUnit,
    displayUnit: AQIDisplayUnit,
    locale: Locale
): (Double) -> String {
    val formatter = NumberFormat.getNumberInstance(locale)
    when (kind) {
        MonitorMeasurementKind.PM25 -> {
            if (displayUnit == AQIDisplayUnit.USAQI) {
                formatter.maximumFractionDigits = 0
                formatter.minimumFractionDigits = 0
            } else {
                formatter.maximumFractionDigits = 1
                formatter.minimumFractionDigits = 0
            }
        }
        MonitorMeasurementKind.CO2 -> {
            formatter.maximumFractionDigits = 0
            formatter.minimumFractionDigits = 0
        }
        MonitorMeasurementKind.TVOC_INDEX,
        MonitorMeasurementKind.NOX_INDEX -> {
            formatter.maximumFractionDigits = 1
            formatter.minimumFractionDigits = 0
        }
        MonitorMeasurementKind.TEMPERATURE -> {
            formatter.maximumFractionDigits = 1
            formatter.minimumFractionDigits = 0
        }
        MonitorMeasurementKind.HUMIDITY -> {
            formatter.maximumFractionDigits = 0
            formatter.minimumFractionDigits = 0
        }
    }
    return { value -> formatter.format(value) }
}

private fun buildYAxisLabels(maxValue: Double, formatter: (Double) -> String): List<YAxisLabel> {
    if (maxValue <= 0.0) {
        val label = formatter(0.0)
        return listOf(YAxisLabel(0.0, label))
    }
    val rawStep = maxValue / 4
    val stepMagnitude = 10.0.pow(floor(log10(rawStep)))
    val leadingDigit = (rawStep / stepMagnitude).toInt()
    val step = when {
        leadingDigit <= 1 -> 1 * stepMagnitude
        leadingDigit <= 2 -> 2 * stepMagnitude
        leadingDigit <= 5 -> 5 * stepMagnitude
        else -> 10 * stepMagnitude
    }
    val values = mutableListOf<Double>()
    var current = 0.0
    while (current <= maxValue) {
        values += current
        current += step
    }
    if (values.lastOrNull()?.let { kotlin.math.abs(it - maxValue) > 0.0001 } != false) {
        values += maxValue
    }
    return values.distinct().map { value ->
        YAxisLabel(value = value, label = formatter(value))
    }
}

private data class YAxisLabel(val value: Double, val label: String)

private fun buildXAxisLabels(bars: List<ChartBar>, locale: Locale): List<String> {
    if (bars.isEmpty()) return emptyList()
    val formatter = DateTimeFormatter.ofPattern("MMM d", locale)
    val zone = ZoneId.systemDefault()
    val first = bars.first().timestamp.atZone(zone)
    val last = bars.last().timestamp.atZone(zone)
    val mid = bars[(bars.size - 1).coerceAtLeast(0) / 2].timestamp.atZone(zone)
    return listOf(formatter.format(first), formatter.format(mid), formatter.format(last))
}
