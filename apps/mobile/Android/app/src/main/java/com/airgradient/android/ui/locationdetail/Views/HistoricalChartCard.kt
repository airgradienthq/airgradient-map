package com.airgradient.android.ui.locationdetail.Views

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airgradient.android.R
import com.airgradient.android.data.models.ChartTimeframe
import com.airgradient.android.data.models.HistoricalData
import com.airgradient.android.data.models.HistoricalDataPointDetail
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.ui.shared.Views.AirgradientOutlinedCard
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun HistoricalChartCard(
    historicalData: HistoricalData?,
    chartTimeframe: ChartTimeframe,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit,
    onTimeframeChange: (ChartTimeframe) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val points = remember(historicalData, chartTimeframe, measurementType, displayUnit) {
        val relevant = when (chartTimeframe) {
            ChartTimeframe.HOURLY -> historicalData?.hourlyData.orEmpty()
            ChartTimeframe.DAILY -> historicalData?.dailyData.orEmpty()
        }
        buildChartBars(relevant, measurementType, displayUnit)
    }

    val title = stringResource(
        id = R.string.historical_chart_title_format,
        stringResource(
            if (measurementType == MeasurementType.PM25) {
                R.string.historical_chart_measurement_pm25
            } else {
                R.string.historical_chart_measurement_co2
            }
        ),
        stringResource(
            if (chartTimeframe == ChartTimeframe.HOURLY) {
                R.string.chart_range_label_24h
            } else {
                R.string.chart_range_label_30d
            }
        )
    )

    HistoricalChartPanel(
        modifier = modifier,
        title = title,
        points = points,
        timeframe = chartTimeframe,
        measurementType = measurementType,
        displayUnit = displayUnit,
        onTimeframeChange = onTimeframeChange,
        isLoading = isLoading
    )
}

@Composable
private fun TimeRangeSelector(
    selected: ChartTimeframe,
    onSelected: (ChartTimeframe) -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChartTimeframe.values().forEach { timeframe ->
            val isSelected = timeframe == selected
            val label = stringResource(
                if (timeframe == ChartTimeframe.HOURLY) {
                    R.string.chart_range_toggle_24h
                } else {
                    R.string.chart_range_toggle_30d
                }
            )
            val interactionSource = remember(timeframe) { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isSelected) 0.dp else 0.dp,
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onSelected(timeframe) }
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoricalChartPanel(
    modifier: Modifier = Modifier,
    title: String,
    points: List<ChartBar>,
    timeframe: ChartTimeframe,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit,
    onTimeframeChange: (ChartTimeframe) -> Unit,
    isLoading: Boolean
) {
    stringResource(
        if (measurementType == MeasurementType.PM25) {
            R.string.historical_chart_axis_pm25
        } else {
            R.string.historical_chart_axis_co2
        }
    )

    val axisConfig = remember(points, measurementType, displayUnit) {
        createYAxisConfig(points, measurementType, displayUnit)
    }
    val timeBounds = remember(points) { computeTimeBounds(points) }
    val chartHeight = 220.dp
    val hasData = points.isNotEmpty()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val xLabels = remember(points, timeframe) {
        buildXAxisLabels(points, timeframe, timeBounds)
    }

    AirgradientOutlinedCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                TimeRangeSelector(
                    selected = timeframe,
                    onSelected = onTimeframeChange,
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                if (hasData) {
                    ChartWithAxis(
                        points = points,
                        axisConfig = axisConfig,
                        xLabels = xLabels,
                        timeframe = timeframe,
                        timeBounds = timeBounds,
                        chartHeight = chartHeight,
                        gridColor = gridColor,
                        measurementType = measurementType,
                        displayUnit = displayUnit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                text = stringResource(id = R.string.historical_chart_no_data),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (isLoading && hasData) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            if (hasData && measurementType == MeasurementType.PM25) {
                Spacer(modifier = Modifier.height(24.dp))
                HistoricalSummaryRow(
                    points = points,
                    timeframe = timeframe,
                    measurementType = measurementType,
                    displayUnit = displayUnit
                )
            }
        }
    }
}

@Composable
private fun ChartWithAxis(
    points: List<ChartBar>,
    axisConfig: YAxisConfig,
    xLabels: List<AxisLabel>,
    timeframe: ChartTimeframe,
    timeBounds: TimeBounds?,
    chartHeight: Dp,
    gridColor: Color,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit
) {
    if (timeBounds == null) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val separators = remember(points, timeframe, timeBounds) {
                buildSeparatorPositions(points, timeframe, timeBounds)
            }

            // Chart takes most of the width
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight)
            ) {
                ChartCanvas(
                    points = points,
                    axisConfig = axisConfig,
                    timeBounds = timeBounds,
                    separators = separators,
                    gridColor = gridColor,
                    measurementType = measurementType,
                    displayUnit = displayUnit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Y-axis line and labels (compact)
            Canvas(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
            ) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val yAxisLabel = when (measurementType) {
                MeasurementType.PM25 -> if (displayUnit == AQIDisplayUnit.USAQI) {
                    stringResource(R.string.unit_us_aqi_short)
                } else {
                    val pmLabel = stringResource(R.string.historical_chart_measurement_pm25)
                    val unitLabel = stringResource(R.string.unit_ugm3)
                    "$pmLabel $unitLabel"
                }
                MeasurementType.CO2 -> stringResource(R.string.historical_chart_axis_co2)
            }

            Box(
                modifier = Modifier
                    .height(chartHeight)
                    .padding(end = 2.dp)
                    .widthIn(min = 40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 2.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        axisConfig.ticks.sortedDescending().forEach { tick ->
                            val tickText = if (axisConfig.step >= 1.0) {
                                tick.roundToInt().toString()
                            } else {
                                String.format(Locale.getDefault(), "%.1f", tick)
                            }
                            Text(
                                text = tickText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    Text(
                        text = yAxisLabel,
                        modifier = Modifier
                            .rotate(-90f),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                XAxisLabelsRow(labels = xLabels)
            }

            Spacer(modifier = Modifier.width(1.dp))

            Spacer(modifier = Modifier.width(28.dp))
        }
    }
}

@Composable
private fun ChartCanvas(
    points: List<ChartBar>,
    axisConfig: YAxisConfig,
    timeBounds: TimeBounds,
    separators: List<Float>,
    gridColor: Color,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }
    var touchPosition by remember { mutableStateOf<Offset?>(null) }
    val currentLocale = Locale.getDefault()
    val zoneId = remember { ZoneId.systemDefault() }
    val tooltipDateFormatter = remember(currentLocale) {
        DateTimeFormatter.ofPattern("MMM d", currentLocale)
    }
    val tooltipTimeFormatter = remember(currentLocale) {
        DateTimeFormatter.ofPattern("HH:mm", currentLocale)
    }

    BoxWithConstraints(modifier = modifier) {
        val boxWidth = constraints.maxWidth.toFloat()
        val boxHeight = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points, boxWidth) {
                detectTapGestures { offset ->
                    // Find which bar was tapped
                    val barIndex = findBarAtPosition(offset, points, boxWidth, timeBounds)
                    selectedBarIndex = barIndex  // Will be null if tapping empty space
                    touchPosition = if (barIndex != null) offset else null
                }
            }
            .pointerInput(points, boxWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val barIndex = findBarAtPosition(offset, points, boxWidth, timeBounds)
                        selectedBarIndex = barIndex
                        touchPosition = if (barIndex != null) offset else null
                    },
                    onDrag = { _, dragAmount ->
                        touchPosition?.let { currentPos ->
                            val newPos = currentPos + dragAmount
                            val barIndex = findBarAtPosition(newPos, points, boxWidth, timeBounds)
                            selectedBarIndex = barIndex
                            touchPosition = if (barIndex != null) newPos else null
                        }
                    },
                    onDragEnd = {
                        selectedBarIndex = null
                        touchPosition = null
                    }
                )
            }
    ) {
        if (points.isEmpty()) return@Canvas

        val maxValue = axisConfig.max
        val range = timeBounds.range
        val minTime = timeBounds.min
        val safeWidth = size.width.coerceAtLeast(1f)
        val maxBarWidth = safeWidth * 0.07f
        val baseWidth = safeWidth / (points.size * 1.6f)
        val barWidth = baseWidth.coerceAtMost(maxBarWidth).coerceAtLeast(4.dp.toPx())

        axisConfig.ticks.forEach { tick ->
            if (tick == 0.0) return@forEach
            val ratio = (tick / maxValue).toFloat().coerceIn(0f, 1f)
            val y = size.height - ratio * size.height
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        drawLine(
            color = gridColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )

        points.forEachIndexed { index, bar ->
            val valueRatio = (bar.displayValue / maxValue).toFloat().coerceIn(0f, 1f)
            val barHeight = size.height * valueRatio
            val timeRatio = ((bar.timestamp.toEpochMilli() - minTime) / range).toFloat().coerceIn(0f, 1f)
            val centerX = timeRatio * safeWidth
            val left = (centerX - barWidth / 2f).coerceIn(0f, safeWidth - barWidth)
            val topLeft = Offset(left, size.height - barHeight)

            // Draw bar with highlight if selected
            val isSelected = selectedBarIndex == index
            val barColor = if (isSelected) bar.color.copy(alpha = 1f) else bar.color.copy(alpha = 0.8f)

            drawRoundRect(
                color = barColor,
                topLeft = topLeft,
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(x = 14f, y = 14f)
            )

            // Draw selection indicator
            if (isSelected) {
                // Draw a white border around selected bar
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = topLeft - Offset(2f, 2f),
                    size = Size(barWidth + 4f, barHeight + 4f),
                    cornerRadius = CornerRadius(x = 14f, y = 14f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Draw vertical indicator line for selected bar
        selectedBarIndex?.let { index ->
            if (index in points.indices) {
                val bar = points[index]
                val timeRatio = ((bar.timestamp.toEpochMilli() - minTime) / range).toFloat().coerceIn(0f, 1f)
                val centerX = timeRatio * safeWidth

                // Draw vertical dotted line
                val dashHeight = 4.dp.toPx()
                val gapHeight = 4.dp.toPx()
                var currentY = 0f
                while (currentY < size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(centerX, currentY),
                        end = Offset(centerX, (currentY + dashHeight).coerceAtMost(size.height)),
                        strokeWidth = 2.dp.toPx()
                    )
                    currentY += dashHeight + gapHeight
                }
            }
        }

        separators.forEach { ratio ->
            val x = (ratio * size.width).coerceIn(0f, size.width)
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }

        // Tooltip overlay - without blocking touch events
        selectedBarIndex?.let { index ->
            if (index in points.indices) {
                val bar = points[index]
                val maxValue = axisConfig.max
                val valueRatio = (bar.displayValue / maxValue).toFloat().coerceIn(0f, 1f)
                val timeRatio = ((bar.timestamp.toEpochMilli() - timeBounds.min) / timeBounds.range).toFloat().coerceIn(0f, 1f)

                Card(
                    modifier = Modifier
                        .offset {
                            val centerX = (timeRatio * boxWidth).toInt()
                            val tooltipHeight = 56.dp.roundToPx()
                            val barHeight = ((valueRatio * boxHeight).toInt())
                            val tooltipY = ((boxHeight - barHeight - tooltipHeight - 12.dp.roundToPx()).toFloat().coerceAtLeast(8.dp.roundToPx().toFloat())).toInt()
                            val tooltipX = (centerX - 45.dp.roundToPx()).coerceIn(0, boxWidth.toInt() - 90.dp.roundToPx())
                            androidx.compose.ui.unit.IntOffset(tooltipX, tooltipY)
                        }
                        .pointerInput(Unit) {
                            // Allow pass-through of touch events
                            detectTapGestures { /* Don't consume */ }
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    val tooltipText = when (measurementType) {
                        MeasurementType.PM25 -> when (displayUnit) {
                            AQIDisplayUnit.UGM3 -> stringResource(
                                R.string.aqi_banner_secondary_pm25,
                                bar.displayValue
                            )
                            AQIDisplayUnit.USAQI -> stringResource(
                                R.string.aqi_banner_secondary_pm25_us_aqi,
                                bar.displayValue.roundToInt()
                            )
                            else -> stringResource(
                                R.string.aqi_banner_secondary_pm25,
                                bar.displayValue
                            )
                        }
                        MeasurementType.CO2 -> stringResource(
                            R.string.aqi_banner_primary_co2,
                            bar.displayValue
                        )
                    }
                    val zonedTimestamp = bar.timestamp.atZone(zoneId)
                    val tooltipDate = tooltipDateFormatter.format(zonedTimestamp)
                    val tooltipTime = tooltipTimeFormatter.format(zonedTimestamp)
                    val dateTimeText = stringResource(
                        R.string.historical_chart_tooltip_datetime,
                        tooltipDate,
                        tooltipTime
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tooltipText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = dateTimeText,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun XAxisLabelsRow(labels: List<AxisLabel>) {
    if (labels.isEmpty()) return

    Layout(
        content = {
            labels.forEach { label ->
                Text(
                    text = label.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val minSpacingPx = 8.dp.roundToPx()
        val placements = mutableListOf<Pair<Int, Int>>()

        labels.indices.forEach { index ->
            val placeable = placeables[index]
            val ratio = labels[index].positionRatio.coerceIn(0f, 1f)
            val maxStart = (width - placeable.width).coerceAtLeast(0)
            var start = (ratio * width).roundToInt().coerceIn(0, maxStart)

            while (true) {
                val lastPlacement = placements.lastOrNull()
                if (lastPlacement == null) {
                    placements.add(index to start)
                    break
                }

                val lastIndex = lastPlacement.first
                val lastStart = lastPlacement.second
                val lastEnd = lastStart + placeables[lastIndex].width

                if (start >= lastEnd + minSpacingPx) {
                    placements.add(index to start)
                    break
                } else {
                    if (index == labels.lastIndex) {
                        placements.removeAt(placements.size - 1)
                        continue
                    } else {
                        // Skip this label if there's no room without overlapping
                        start = -1
                        break
                    }
                }
            }

            if (start == -1) {
                // Ensure the skipped label isn't considered in later iterations
                return@forEach
            }
        }

        layout(width, height) {
            placements.forEach { (index, start) ->
                val placeable = placeables[index]
                placeable.place(start, 0)
            }
        }
    }
}

@Composable
private fun HistoricalSummaryRow(
    points: List<ChartBar>,
    timeframe: ChartTimeframe,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit
) {
    val summaries = remember(points, timeframe) {
        buildSummaryItems(points, timeframe)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        summaries.forEach { item ->
            SummaryCard(item, measurementType, displayUnit)
        }
    }
}

private fun createYAxisConfig(
    points: List<ChartBar>,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit
): YAxisConfig {
    val targetTickCount = 6
    val defaultMax = when (measurementType) {
        MeasurementType.PM25 -> if (displayUnit == AQIDisplayUnit.USAQI) 500.0 else 200.0
        MeasurementType.CO2 -> 1000.0
    }

    val maxValue = points.maxOfOrNull { it.displayValue }?.takeIf { it > 0.0 } ?: defaultMax
    val niceStep = niceStep(maxValue, targetTickCount)
    val maxTick = ceil(maxValue / niceStep) * niceStep
    val tickCount = (maxTick / niceStep).roundToInt()
    val ticks = (0..tickCount).map { it * niceStep }

    return YAxisConfig(max = maxTick, step = niceStep, ticks = ticks)
}

private data class YAxisConfig(
    val max: Double,
    val step: Double,
    val ticks: List<Double>
)

private data class TimeBounds(
    val min: Double,
    val max: Double,
    val range: Double
)

private data class AxisLabel(
    val text: String,
    val positionRatio: Float
)

private fun computeTimeBounds(points: List<ChartBar>): TimeBounds? {
    if (points.isEmpty()) return null
    val first = points.first().timestamp.toEpochMilli().toDouble()
    val last = points.last().timestamp.toEpochMilli().toDouble()
    val averageSpacing = if (points.size > 1) {
        (last - first) / (points.size - 1)
    } else {
        60_000.0 // default to one minute when only a single point exists
    }

    val firstSpacing = points.getOrNull(1)?.let { second ->
        (second.timestamp.toEpochMilli().toDouble() - first).coerceAtLeast(1.0)
    } ?: averageSpacing

    val lastSpacing = points.getOrNull(points.lastIndex - 1)?.let { penultimate ->
        (last - penultimate.timestamp.toEpochMilli().toDouble()).coerceAtLeast(1.0)
    } ?: averageSpacing

    val paddedMin = first - firstSpacing / 2.0
    val paddedMax = last + lastSpacing / 2.0
    val range = (paddedMax - paddedMin).coerceAtLeast(1.0)
    return TimeBounds(min = paddedMin, max = paddedMax, range = range)
}

private fun findBarAtPosition(
    offset: Offset,
    points: List<ChartBar>,
    canvasWidth: Float,
    timeBounds: TimeBounds?
): Int? {
    if (points.isEmpty() || timeBounds == null) return null

    val maxBarWidth = canvasWidth * 0.07f
    val baseWidth = canvasWidth / (points.size * 1.6f)
    val barWidth = baseWidth.coerceAtMost(maxBarWidth).coerceAtLeast(12f)

    points.forEachIndexed { index, bar ->
        val timeRatio = ((bar.timestamp.toEpochMilli() - timeBounds.min) / timeBounds.range).toFloat().coerceIn(0f, 1f)
        val centerX = timeRatio * canvasWidth
        val left = centerX - barWidth / 2f
        val right = centerX + barWidth / 2f

        if (offset.x in left..right) {
            return index
        }
    }

    return null
}

private fun buildSeparatorPositions(
    points: List<ChartBar>,
    timeframe: ChartTimeframe,
    timeBounds: TimeBounds
): List<Float> {
    if (points.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val minInstant = points.first().timestamp.atZone(zone)
    val maxInstant = points.last().timestamp.atZone(zone)
    val separators = mutableListOf<Long>()

    when (timeframe) {
        ChartTimeframe.HOURLY -> {
            var current = minInstant.toLocalDate().atStartOfDay(zone)
            while (current.toInstant() <= points.first().timestamp) {
                current = current.plusDays(1)
            }
            while (current.toInstant() < maxInstant.toInstant()) {
                separators.add(current.toInstant().toEpochMilli())
                current = current.plusDays(1)
            }

            val middayStart = minInstant.toLocalDate().atStartOfDay(zone).plusHours(12)
            val middayMillis = middayStart.toInstant().toEpochMilli()
            if (middayMillis > timeBounds.min && middayMillis < timeBounds.max) {
                separators.add(middayMillis)
            }
        }
        ChartTimeframe.DAILY -> {
            var current = minInstant.toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone)
            while (current.toInstant() <= points.first().timestamp) {
                current = current.plusWeeks(1)
            }
            while (current.toInstant() < maxInstant.toInstant()) {
                separators.add(current.toInstant().toEpochMilli())
                current = current.plusWeeks(1)
            }
        }
    }

    val range = timeBounds.range
    return separators.distinct().sorted().map { separator ->
        ((separator - timeBounds.min) / range).toFloat().coerceIn(0f, 1f)
    }
}

private fun niceStep(maxValue: Double, targetTickCount: Int): Double {
    val range = maxValue
    val roughStep = range / (targetTickCount - 1)
    if (roughStep <= 0.0 || roughStep.isNaN()) return 1.0

    val exponent = floor(log10(roughStep))
    val fraction = roughStep / 10.0.pow(exponent)

    val niceFraction = when {
        fraction < 1.5 -> 1.0
        fraction < 3.0 -> 2.0
        fraction < 7.0 -> 5.0
        else -> 10.0
    }

    return niceFraction * 10.0.pow(exponent)
}

@Composable
private fun RowScope.SummaryCard(
    item: SummaryItem,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    val valueText = item.displayAverage?.let { avg ->
        when (measurementType) {
            MeasurementType.PM25 -> when (displayUnit) {
                AQIDisplayUnit.UGM3 -> stringResource(id = R.string.historical_chart_summary_pm25_ugm3, avg)
                AQIDisplayUnit.USAQI -> stringResource(id = R.string.historical_chart_summary_us_aqi, avg)
            }
            MeasurementType.CO2 -> stringResource(id = R.string.historical_chart_summary_co2, avg)
        }
    }
    val backgroundColor = item.rawAverage?.let { avg ->
        val baseColor = when (measurementType) {
            MeasurementType.PM25 -> EPAColorCoding.getColorForPM25(avg)
            MeasurementType.CO2 -> EPAColorCoding.colorForMeasurement(avg, measurementType, displayUnit)
        }
        lightenColor(baseColor, 0.25f)
    } ?: MaterialTheme.colorScheme.surfaceVariant
    val textColor = item.rawAverage?.let { avg ->
        EPAColorCoding.textColorForMeasurement(avg, measurementType, displayUnit)
    } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = valueText ?: stringResource(id = R.string.historical_chart_summary_placeholder),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondary
            )
            Text(
                text = stringResource(id = item.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---- Helpers ----

private data class ChartBar(
    val timestamp: Instant,
    val rawValue: Double,
    val displayValue: Double,
    val color: Color
)

private data class SummaryItem(
    @StringRes val labelRes: Int,
    val displayAverage: Double?,
    val rawAverage: Double?
)

private fun buildChartBars(
    data: List<HistoricalDataPointDetail>,
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit
): List<ChartBar> {
    if (data.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()

    return data.mapNotNull { point ->
        val instant = runCatching { Instant.parse(point.timestamp) }.getOrNull() ?: return@mapNotNull null
        val rawValue = point.value
        val displayValue = when (measurementType) {
            MeasurementType.PM25 -> when (displayUnit) {
                AQIDisplayUnit.USAQI -> EPAColorCoding.getAQIFromPM25(rawValue).toDouble()
                AQIDisplayUnit.UGM3 -> rawValue
            }
            MeasurementType.CO2 -> rawValue
        }
        val barColor = when (measurementType) {
            MeasurementType.PM25 -> EPAColorCoding.getColorForPM25(rawValue)
            MeasurementType.CO2 -> EPAColorCoding.colorForMeasurement(rawValue, measurementType, displayUnit)
        }
        ChartBar(
            timestamp = instant.atZone(zone).toInstant(),
            rawValue = rawValue,
            displayValue = displayValue,
            color = barColor
        )
    }.sortedBy { it.timestamp }
}

private fun buildXAxisLabels(
    points: List<ChartBar>,
    timeframe: ChartTimeframe,
    timeBounds: TimeBounds?
): List<AxisLabel> {
    val bounds = timeBounds ?: return emptyList()
    if (points.isEmpty()) return emptyList()

    val zoneId = ZoneId.systemDefault()
    val segments = 4

    val formatter = when (timeframe) {
        ChartTimeframe.HOURLY -> DateTimeFormatter.ofPattern("HH", Locale.getDefault())
        ChartTimeframe.DAILY -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    }

    // Only create labels that fall within the actual data range
    return (0..segments).mapNotNull { index ->
        val ratio = index.toFloat() / segments
        val instantMillis = (bounds.min + bounds.range * ratio).toLong()

        // Skip labels that go beyond the last data point
        if (instantMillis > points.last().timestamp.toEpochMilli()) {
            return@mapNotNull null
        }

        val zoned = Instant.ofEpochMilli(instantMillis).atZone(zoneId)
        val labelText = formatter.format(zoned)
        AxisLabel(labelText, ratio)
    }
}

private fun buildSummaryItems(
    points: List<ChartBar>,
    timeframe: ChartTimeframe
): List<SummaryItem> {
    val periods = when (timeframe) {
        ChartTimeframe.HOURLY -> listOf(
            SummaryPeriod(R.string.stats_last_24h, 24),
            SummaryPeriod(R.string.stats_last_12h, 12)
        )
        ChartTimeframe.DAILY -> listOf(
            SummaryPeriod(R.string.stats_last_30_days, 720),
            SummaryPeriod(R.string.stats_last_7_days, 168)
        )
    }

    return periods.map { period ->
        val averages = calculateAverages(points, period.hours)
        SummaryItem(
            labelRes = period.labelRes,
            displayAverage = averages?.displayAverage,
            rawAverage = averages?.rawAverage
        )
    }
}

private data class SummaryPeriod(@StringRes val labelRes: Int, val hours: Int)

private data class Averages(val displayAverage: Double, val rawAverage: Double)

private fun calculateAverages(points: List<ChartBar>, periodHours: Int): Averages? {
    if (points.isEmpty()) return null
    val latest = points.maxByOrNull { it.timestamp }?.timestamp ?: return null
    val windowStart = latest.minus(Duration.ofHours(periodHours.toLong()))
    val windowPoints = points.filter { !it.timestamp.isBefore(windowStart) }
    if (windowPoints.size < 3) return null

    val actualSpanHours = Duration.between(windowPoints.first().timestamp, latest).toHours().toDouble()
    if (actualSpanHours <= 0 || actualSpanHours / periodHours < 0.25) return null

    val displayAvg = windowPoints.map { it.displayValue }.average()
    val rawAvg = windowPoints.map { it.rawValue }.average()
    return Averages(displayAvg, rawAvg)
}

private fun lightenColor(color: Color, blendFactor: Float): Color {
    val factor = blendFactor.coerceIn(0f, 1f)
    val red = color.red + (1f - color.red) * factor
    val green = color.green + (1f - color.green) * factor
    val blue = color.blue + (1f - color.blue) * factor
    return Color(red, green, blue, alpha = 1f)
}
