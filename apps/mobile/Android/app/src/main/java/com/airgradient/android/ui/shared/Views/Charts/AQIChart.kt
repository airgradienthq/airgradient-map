package com.airgradient.android.ui.shared.Views.Charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airgradient.android.data.models.ChartTimeframe
import com.airgradient.android.data.models.HistoricalDataPointDetail
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Main reusable AQI chart component
 * Displays historical air quality data as colored column bars
 * Matches iOS design with proper scaling and color coding
 */
@Composable
fun AQIChart(
    data: List<HistoricalDataPointDetail>,
    timeframe: ChartTimeframe,
    onTimeframeChange: (ChartTimeframe) -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showWHOGuideline: Boolean = true,
    displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with title and timeframe selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PM2.5 - ${timeframe.displayName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                
                ChartTimeSelector(
                    selectedTimeframe = timeframe,
                    onTimeframeChange = onTimeframeChange,
                    enabled = !isLoading
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Chart content with state handling
            ChartStateHandler(
                isLoading = isLoading,
                error = error,
                isEmpty = data.isEmpty(),
                onRetry = onRetry,
                modifier = Modifier.height(180.dp)
            ) {
                AQIBarChart(
                    data = data,
                    timeframe = timeframe,
                    showWHOGuideline = showWHOGuideline,
                    displayUnit = displayUnit
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Chart bottom summary cards (like iOS)
            if (data.isNotEmpty()) {
                ChartSummaryCards(
                    data = data,
                    displayUnit = displayUnit
                )
            }
        }
    }
}

/**
 * Bar chart implementation using Compose Canvas
 * Renders colored bars with proper scaling and labels
 */
@Composable
private fun AQIBarChart(
    data: List<HistoricalDataPointDetail>,
    timeframe: ChartTimeframe,
    showWHOGuideline: Boolean,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val sampledData = remember(data) {
        ChartUtils.sampleDataPointsDetail(data, maxPoints = 30)
    }

    val displayData = remember(sampledData, displayUnit) {
        sampledData.map { point -> point.copy(value = convertValueForDisplay(point.value, displayUnit)) }
    }

    val (minValue, maxValue) = remember(displayData) {
        ChartUtils.calculateYAxisScale(displayData)
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Main chart area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            // Y-axis labels
            YAxisLabels(
                minValue = minValue,
                maxValue = maxValue,
                displayUnit = displayUnit,
                modifier = Modifier
                    .width(35.dp)
                    .fillMaxHeight()
            )
            
            // Chart bars
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
            ) {
                ChartCanvas(
                    data = sampledData,
                    minValue = minValue,
                    maxValue = maxValue,
                    showWHOGuideline = showWHOGuideline,
                    timeframe = timeframe,
                    displayUnit = displayUnit
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // X-axis labels (scrollable)
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.width(35.dp)) // Align with Y-axis
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                XAxisLabels(
                    data = sampledData,
                    timeframe = timeframe
                )
            }
        }
        
        if (showWHOGuideline) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // WHO guideline indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "_____ WHO Daily Guideline",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * Y-axis labels component
 */
@Composable
private fun YAxisLabels(
    minValue: Double,
    maxValue: Double,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    val gridlines = remember(minValue, maxValue) { 
        ChartUtils.generateYAxisGridlines(minValue, maxValue) 
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        gridlines.reversed().forEach { value ->
            val label = when (displayUnit) {
                AQIDisplayUnit.USAQI -> value.roundToInt().toString()
                AQIDisplayUnit.UGM3 -> if (value >= 100) {
                    value.roundToInt().toString()
                } else {
                    String.format(Locale.getDefault(), "%.1f", value)
                }
            }
            Text(
                text = label,
                fontSize = 9.sp,
                color = Color.Gray,
                modifier = Modifier.padding(end = 4.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * X-axis labels component
 */
@Composable
private fun XAxisLabels(
    data: List<HistoricalDataPointDetail>,
    timeframe: ChartTimeframe,
    modifier: Modifier = Modifier
) {
    val labelPositions = remember(data.size) { 
        ChartUtils.generateXAxisLabelPositions(data.size, targetLabelCount = 6) 
    }
    
    val barWidth = 32.dp
    val barSpacing = 8.dp
    val totalWidth = (barWidth + barSpacing) * data.size - barSpacing
    
    Column(modifier = modifier.width(totalWidth)) {
        // Show month label for daily view
        if (timeframe != ChartTimeframe.HOURLY && data.isNotEmpty()) {
            val monthLabel = remember(data.first().timestamp) {
                try {
                    val date = ChartUtils.parseTimestamp(data.first().timestamp)
                    java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    ""
                }
            }
            if (monthLabel.isNotEmpty()) {
                Text(
                    text = monthLabel,
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(barSpacing)
        ) {
            data.forEachIndexed { index, point ->
                val showLabel = labelPositions.contains(index)
                
                Box(
                    modifier = Modifier.width(barWidth),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLabel) {
                        Text(
                            text = ChartUtils.formatTimeLabel(
                                point.timestamp, 
                                timeframe == ChartTimeframe.HOURLY
                            ),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Main canvas for drawing bars and guidelines
 */
@Composable
private fun ChartCanvas(
    data: List<HistoricalDataPointDetail>,
    minValue: Double,
    maxValue: Double,
    showWHOGuideline: Boolean,
    timeframe: ChartTimeframe,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    val barWidth = 32.dp
    val barSpacing = 8.dp
    val totalWidth = (barWidth + barSpacing) * data.size - barSpacing
    
    Canvas(
        modifier = modifier
            .width(totalWidth)
            .fillMaxHeight()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidthPx = barWidth.toPx()
        val barSpacingPx = barSpacing.toPx()
        
        // Draw WHO guideline if enabled
        if (showWHOGuideline && timeframe == ChartTimeframe.HOURLY) {
            val guideline = ChartUtils.WHOGuidelines.getGuidelineForTimeframe(true)
            if (guideline != null) {
                val displayGuideline = convertValueForDisplay(guideline, displayUnit)
                drawWHOGuideline(displayGuideline, minValue, maxValue, canvasWidth, canvasHeight)
            }
        }
        
        // Draw bars
        data.forEachIndexed { index, point ->
            val x = index * (barWidthPx + barSpacingPx)
            val displayValue = convertValueForDisplay(point.value, displayUnit)
            val barHeight = ChartUtils.calculateBarHeight(displayValue, minValue, maxValue)
            val barHeightPx = canvasHeight * barHeight
            val y = canvasHeight - barHeightPx
            
            val color = ChartUtils.getColorFromAQICategory(point.aqiCategory)
            
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeightPx)
            )
        }
    }
}

/**
 * Draw WHO guideline as a dashed line
 */
private fun DrawScope.drawWHOGuideline(
    guideline: Double,
    minValue: Double,
    maxValue: Double,
    canvasWidth: Float,
    canvasHeight: Float
) {
    val normalizedY = (guideline - minValue) / (maxValue - minValue)
    val y = canvasHeight - (normalizedY * canvasHeight).toFloat()
    
    if (y >= 0 && y <= canvasHeight) {
        // Draw dashed line effect with multiple short lines
        val dashLength = 10f
        val gapLength = 6f
        var x = 0f
        
        while (x < canvasWidth) {
            val endX = minOf(x + dashLength, canvasWidth)
            drawLine(
                color = Color(0xFF2196F3), // Blue for WHO guideline
                start = Offset(x, y),
                end = Offset(endX, y),
                strokeWidth = 1.5f
            )
            x += dashLength + gapLength
        }
    }
}

/**
 * Chart summary cards showing Last 7 days, 24h, and 6h averages
 * Matches the iOS design from screenshots
 */
@Composable
private fun ChartSummaryCards(
    data: List<HistoricalDataPointDetail>,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    remember { System.currentTimeMillis() }

    // Calculate averages for different periods
    val last6Hours = calculatePeriodAverage(data, 6 * 60 * 60 * 1000L, displayUnit)
    val last24Hours = calculatePeriodAverage(data, 24 * 60 * 60 * 1000L, displayUnit)
    val last7Days = calculatePeriodAverage(data, 7 * 24 * 60 * 60 * 1000L, displayUnit)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            SummaryCard(
            title = "Last 7 days",
            value = formatPeriodValue(last7Days.display, displayUnit),
            color = ChartUtils.getAQIColorForValue(last7Days.raw),
            modifier = Modifier.weight(1f)
        )
        
        SummaryCard(
            title = "Last 24h",
            value = formatPeriodValue(last24Hours.display, displayUnit),
            color = ChartUtils.getAQIColorForValue(last24Hours.raw),
            modifier = Modifier.weight(1f)
        )
        
        SummaryCard(
            title = "Last 6h",
            value = formatPeriodValue(last6Hours.display, displayUnit),
            color = ChartUtils.getAQIColorForValue(last6Hours.raw),
            modifier = Modifier.weight(1f)
        )
    }
}

private data class PeriodAverage(
    val raw: Double,
    val display: Double
)

private fun formatPeriodValue(value: Double, displayUnit: AQIDisplayUnit): String {
    return when (displayUnit) {
        AQIDisplayUnit.USAQI -> "${value.roundToInt()} AQI"
        AQIDisplayUnit.UGM3 -> String.format(Locale.getDefault(), "%.1f µg/m³", value)
    }
}

/**
 * Individual summary card component
 */
@Composable
private fun SummaryCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Calculate average value for a specific time period
 */
private fun calculatePeriodAverage(
    data: List<HistoricalDataPointDetail>,
    periodMillis: Long,
    displayUnit: AQIDisplayUnit
): PeriodAverage {
    if (data.isEmpty()) return PeriodAverage(raw = 0.0, display = 0.0)
    
    // Sort data by timestamp to get the most recent
    val sortedData = data.sortedByDescending { point ->
        try {
            ChartUtils.parseTimestamp(point.timestamp).time
        } catch (e: Exception) {
            0L
        }
    }
    
    // Calculate how many data points to include based on the period
    val hoursInPeriod = periodMillis / (60 * 60 * 1000L)
    val dataPointsToInclude = when {
        hoursInPeriod <= 6 -> minOf(6, sortedData.size)  // Last 6 data points for 6h
        hoursInPeriod <= 24 -> minOf(24, sortedData.size) // Last 24 data points for 24h
        else -> sortedData.size // All data for 7 days
    }
    
    val periodData = sortedData.take(dataPointsToInclude)
    
    return if (periodData.isNotEmpty()) {
        val rawAverage = periodData.map { point -> point.value }.average()
        PeriodAverage(
            raw = rawAverage,
            display = convertValueForDisplay(rawAverage, displayUnit)
        )
    } else {
        PeriodAverage(raw = 0.0, display = 0.0)
    }
}

/**
 * Convert PM2.5 value to AQI
 */
private fun convertPM25ToAQI(pm25: Double): Double {
    return EPAColorCoding.getAQIFromPM25(pm25).toDouble()
}

private fun convertValueForDisplay(pm25: Double, displayUnit: AQIDisplayUnit): Double {
    return when (displayUnit) {
        AQIDisplayUnit.USAQI -> convertPM25ToAQI(pm25)
        AQIDisplayUnit.UGM3 -> pm25
    }
}

/**
 * Simplified AQI Chart for basic use cases
 * Less configuration options but easier to use
 */
@Composable
fun SimpleAQIChart(
    data: List<HistoricalDataPointDetail>,
    modifier: Modifier = Modifier,
    title: String = "Air Quality Trend",
    displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI
) {
    var selectedTimeframe by remember { mutableStateOf(ChartTimeframe.HOURLY) }

    AQIChart(
        data = data,
        timeframe = selectedTimeframe,
        onTimeframeChange = { selectedTimeframe = it },
        modifier = modifier,
        showWHOGuideline = true,
        displayUnit = displayUnit
    )
}
