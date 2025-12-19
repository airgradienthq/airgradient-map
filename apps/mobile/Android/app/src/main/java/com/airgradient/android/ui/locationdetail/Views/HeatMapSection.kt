package com.airgradient.android.ui.locationdetail.Views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airgradient.android.R
import com.airgradient.android.data.models.HeatMapDataPoint
import com.airgradient.android.data.models.HeatMapUiState
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.ui.shared.Views.SectionHeader
import com.airgradient.android.ui.shared.Views.SectionPanel
import com.airgradient.android.ui.shared.Views.SectionPanelConfig
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HeatMapSection(state: HeatMapUiState) {
    val zone = remember { ZoneId.systemDefault() }
    val baseEpoch = state.baseEpochMillis
    val baseDate = remember(baseEpoch) {
        Instant.ofEpochMilli(baseEpoch).atZone(zone).toLocalDate()
    }

    val todayLabel = stringResource(id = R.string.heat_map_today)
    val yesterdayLabel = stringResource(id = R.string.heat_map_yesterday)

    val baselineCells = remember(baseEpoch) {
        buildEmptyHeatMapCells(baseEpoch, zone)
    }

    val displayCells = remember(state.cells, baselineCells, state.isLoading) {
        if (state.cells.isEmpty()) {
            baselineCells
        } else {
            val actualBySlot = state.cells.associateBy { it.day to it.hour }
            baselineCells.map { baseline ->
                val actual = actualBySlot[baseline.day to baseline.hour]
                if (actual != null) {
                    baseline.copy(
                        value = actual.value,
                        isFuture = actual.isFuture
                    )
                } else baseline
            }
        }
    }

    var selection by remember { mutableStateOf<HeatMapSelection?>(null) }
    val cellLayouts = remember { mutableStateMapOf<Pair<Int, Int>, HeatMapCellLayout>() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerSize by remember { mutableStateOf<IntSize?>(null) }
    val density = LocalDensity.current
    val currentLocale = Locale.getDefault()
    val tooltipDateFormatter = remember(currentLocale) {
        DateTimeFormatter.ofPattern("MMM d", currentLocale)
    }
    val tooltipTimeFormatter = remember(currentLocale) {
        DateTimeFormatter.ofPattern("HH:mm", currentLocale)
    }

    LaunchedEffect(state.cells, state.measurementType, state.displayUnit, state.isLoading) {
        if (state.isLoading || state.cells.isEmpty()) {
            selection = null
        } else {
            selection?.let { current ->
                val matchingCell = state.cells.firstOrNull {
                    it.day == current.cell.day &&
                        it.hour == current.cell.hour &&
                        it.date == current.cell.date
                }
                selection = if (matchingCell?.value != null) {
                    current.copy(cell = matchingCell)
                } else {
                    null
                }
            }
        }
    }

    val dayLabels = remember(baseDate, todayLabel, yesterdayLabel, currentLocale) {
        (0..6).map { day ->
            when (day) {
                0 -> todayLabel
                1 -> yesterdayLabel
                else -> baseDate.minusDays(day.toLong())
                    .dayOfWeek
                    .getDisplayName(TextStyle.FULL, currentLocale)
            }
        }
    }

    val titleRes = if (state.measurementType == MeasurementType.PM25) {
        R.string.heat_map_pm25_title
    } else {
        R.string.heat_map_co2_title
    }

    val cellSpacing = 1.dp
    val hourLabelHeight = 24.dp
    val dayLabelWidth = 95.dp
    val dayColumnSpacing = 8.dp

    SectionPanel(
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = titleRes)
            )
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            val availableForCells = (maxWidth - dayLabelWidth - dayColumnSpacing).coerceAtLeast(0.dp)
            val totalSpacing = cellSpacing * (24 - 1)
            val computedCellSize = if (availableForCells <= totalSpacing) {
                8.dp
            } else {
                (availableForCells - totalSpacing) / 24
            }
            val scaledCellSize = computedCellSize * 1.2f
            val cellSize = scaledCellSize.coerceIn(8.dp, 28.dp)
            val labelHeight = cellSize.coerceAtLeast(16.dp)
            val hourLabelFontSize = (cellSize.value * 0.85f).coerceIn(9f, 14f).sp

            var tooltipSize by remember { mutableStateOf(IntSize.Zero) }

            val handleCellTap: (HeatMapDataPoint) -> Unit = { tappedCell ->
                if (!state.isLoading && !tappedCell.isFuture && tappedCell.value != null) {
                    val layout = cellLayouts[tappedCell.cellKey()]
                    if (layout != null) {
                        val isSameCell = selection?.cell?.cellKey() == tappedCell.cellKey()
                        selection = if (isSameCell) {
                            null
                        } else {
                            HeatMapSelection(tappedCell, layout.topLeft, layout.size)
                        }
                    }
                }
            }

            val updateCellLayout: (HeatMapDataPoint, LayoutCoordinates) -> Unit = { cell, coordinates ->
                containerCoordinates?.let { container ->
                    val offset = container.localPositionOf(coordinates, Offset.Zero)
                    val layout = HeatMapCellLayout(offset, coordinates.size)
                    cellLayouts[cell.cellKey()] = layout
                    if (selection?.cell?.cellKey() == cell.cellKey()) {
                        selection = HeatMapSelection(cell, layout.topLeft, layout.size)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        containerCoordinates = it
                        containerSize = it.size
                    }
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dayColumnSpacing)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(cellSpacing)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                            ) {
                                val groupWidth = cellSize * 6 + cellSpacing * 5
                                for (hour in 0 until 24 step 6) {
                                    Box(
                                        modifier = Modifier
                                            .width(groupWidth)
                                            .height(hourLabelHeight),
                                        contentAlignment = Alignment.BottomStart
                                    ) {
                                        Text(
                                            text = String.format(currentLocale, "%02d", hour),
                                            modifier = Modifier.fillMaxWidth(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = hourLabelFontSize),
                                            color = Color(0xFF424242),
                                            maxLines = 1,
                                            softWrap = false,
                                            textAlign = TextAlign.Start
                                        )
                                    }
                                }
                            }

                            for (day in 0..6) {
                                val rowCells = displayCells.filter { it.day == day }.sortedBy { it.hour }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                                ) {
                                    rowCells.forEach { cell ->
                                        Box(
                                            modifier = Modifier
                                                .width(cellSize)
                                                .height(labelHeight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            HeatMapCell(
                                                cell = cell,
                                                state = state,
                                                size = cellSize,
                                                isSelected = selection?.cell?.cellKey() == cell.cellKey(),
                                                onPositioned = updateCellLayout,
                                                onCellTapped = handleCellTap
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(cellSpacing)
                        ) {
                            Spacer(modifier = Modifier.height(hourLabelHeight))
                            dayLabels.forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .width(dayLabelWidth)
                                        .height(labelHeight),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.75f
                                        ),
                                        color = Color(0xFF424242),
                                        textAlign = TextAlign.Start,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    if (state.error && !state.isLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.heat_map_error_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                selection?.let { selected ->
                    val container = containerSize
                    if (container != null) {
                        val tooltipWidthPx = if (tooltipSize.width > 0) {
                            tooltipSize.width
                        } else {
                            with(density) { 180.dp.roundToPx() }
                        }
                        val tooltipHeightPx = if (tooltipSize.height > 0) {
                            tooltipSize.height
                        } else {
                            with(density) { 56.dp.roundToPx() }
                        }
                        val gapPx = with(density) { 12.dp.roundToPx() }
                        val minTopPx = with(density) { 8.dp.roundToPx() }

                        val maxX = (container.width - tooltipWidthPx).coerceAtLeast(0)
                        val centerX = selected.topLeft.x + selected.size.width / 2f
                        val tooltipX = centerX - tooltipWidthPx / 2f
                        val tooltipXInt = tooltipX.roundToInt().coerceIn(0, maxX)

                        val rawY = selected.topLeft.y - tooltipHeightPx - gapPx
                        val tooltipYFloat = if (rawY < minTopPx) minTopPx.toFloat() else rawY
                        val tooltipYInt = tooltipYFloat.roundToInt()

                        val valueText = when (state.measurementType) {
                            MeasurementType.PM25 -> {
                                selected.cell.value?.let { value ->
                                    when (state.displayUnit) {
                                        AQIDisplayUnit.USAQI -> stringResource(
                                            id = R.string.aqi_banner_secondary_pm25_us_aqi,
                                            EPAColorCoding.getAQIFromPM25(value)
                                        )
                                        AQIDisplayUnit.UGM3 -> stringResource(
                                            id = R.string.aqi_banner_secondary_pm25,
                                            value
                                        )
                                    }
                                }
                            }
                            MeasurementType.CO2 -> {
                                selected.cell.value?.let { value ->
                                    stringResource(id = R.string.aqi_banner_primary_co2, value)
                                }
                            }
                        } ?: stringResource(id = R.string.heat_map_value_unavailable)

                        val instant = Instant.ofEpochMilli(selected.cell.date)
                        val zonedTimestamp = instant.atZone(zone)
                        val tooltipDate = tooltipDateFormatter.format(zonedTimestamp)
                        val tooltipTime = tooltipTimeFormatter.format(zonedTimestamp)
                        val dateTimeText = stringResource(
                            id = R.string.historical_chart_tooltip_datetime,
                            tooltipDate,
                            tooltipTime
                        )

                        Card(
                            modifier = Modifier
                                .offset { IntOffset(tooltipXInt, tooltipYInt) }
                                .onGloballyPositioned { tooltipSize = it.size },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.95f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = valueText,
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
    }
}

@Composable
private fun HeatMapCell(
    cell: HeatMapDataPoint,
    state: HeatMapUiState,
    size: Dp,
    isSelected: Boolean,
    onPositioned: (HeatMapDataPoint, LayoutCoordinates) -> Unit,
    onCellTapped: (HeatMapDataPoint) -> Unit
) {
    val skeletonColor = Color(0xFFBDBDBD)
    val futureColor = Color(0xFFF5F5F5)
    val backgroundColor = when {
        state.isLoading -> skeletonColor
        cell.value != null -> EPAColorCoding.colorForMeasurement(
            value = cell.value,
            measurementType = state.measurementType,
            displayUnit = state.displayUnit
        )
        cell.isFuture -> futureColor
        else -> Color.Transparent
    }

    val shape = RoundedCornerShape(3.dp)
    val borderColor = Color.White.copy(alpha = 0.9f)

    var modifier = Modifier
        .size(size)
        .onGloballyPositioned { coordinates -> onPositioned(cell, coordinates) }

    if (isSelected) {
        modifier = modifier.border(width = 2.dp, color = borderColor, shape = shape)
    }

    modifier = modifier
        .clip(shape)
        .background(backgroundColor)

    val isInteractive = !state.isLoading && !cell.isFuture && cell.value != null
    if (isInteractive) {
        modifier = modifier.pointerInput(cell.day, cell.hour, cell.value) {
            detectTapGestures {
                onCellTapped(cell)
            }
        }
    }

    Box(modifier = modifier)
}

private data class HeatMapSelection(
    val cell: HeatMapDataPoint,
    val topLeft: Offset,
    val size: IntSize
)

private data class HeatMapCellLayout(
    val topLeft: Offset,
    val size: IntSize
)

private fun HeatMapDataPoint.cellKey(): Pair<Int, Int> = day to hour

private fun buildEmptyHeatMapCells(baseEpochMillis: Long, zone: ZoneId): List<HeatMapDataPoint> {
    val anchor = Instant.ofEpochMilli(baseEpochMillis)
        .atZone(zone)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)

    val cells = mutableListOf<HeatMapDataPoint>()

    for (day in 0..6) {
        val dayTime: ZonedDateTime = anchor.minusDays(day.toLong())
        for (hour in 0..23) {
            val cellTime = dayTime.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            val isFuture = day == 0 && hour > anchor.hour
            cells.add(
                HeatMapDataPoint(
                    day = day,
                    hour = hour,
                    value = null,
                    date = cellTime.toInstant().toEpochMilli(),
                    isFuture = isFuture
                )
            )
        }
    }

    return cells
}
