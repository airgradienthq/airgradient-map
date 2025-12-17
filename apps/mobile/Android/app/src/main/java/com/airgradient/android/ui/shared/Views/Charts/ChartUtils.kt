package com.airgradient.android.ui.shared.Views.Charts

import androidx.compose.ui.graphics.Color
import com.airgradient.android.data.models.AQICategory
import com.airgradient.android.data.models.HistoricalDataPoint
import com.airgradient.android.data.models.HistoricalDataPointDetail
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Utility functions for chart data processing and rendering
 */
object ChartUtils {
    
    /**
     * Sample data points to a maximum number for performance
     * Matches iOS implementation with consistent sampling
     */
    fun sampleDataPoints(dataPoints: List<HistoricalDataPoint>, maxPoints: Int): List<HistoricalDataPoint> {
        if (dataPoints.size <= maxPoints) return dataPoints

        val step = dataPoints.size.toDouble() / maxPoints.toDouble()
        val sampled = mutableListOf<HistoricalDataPoint>()

        for (i in 0 until maxPoints) {
            val index = (i * step).toInt()
            if (index < dataPoints.size) {
                sampled.add(dataPoints[index])
            }
        }

        return sampled
    }

    /**
     * Sample data points to a maximum number for performance (HistoricalDataPointDetail version)
     * Matches iOS implementation with consistent sampling
     */
    fun sampleDataPointsDetail(dataPoints: List<HistoricalDataPointDetail>, maxPoints: Int): List<HistoricalDataPointDetail> {
        if (dataPoints.size <= maxPoints) return dataPoints

        val step = dataPoints.size.toDouble() / maxPoints.toDouble()
        val sampled = mutableListOf<HistoricalDataPointDetail>()

        for (i in 0 until maxPoints) {
            val index = (i * step).toInt()
            if (index < dataPoints.size) {
                sampled.add(dataPoints[index])
            }
        }

        return sampled
    }
    
    /**
     * Get AQI color for PM2.5 value following EPA standards
     * Matches the color scheme from iOS implementation
     */
    fun getAQIColorForValue(pm25: Double): Color {
        return EPAColorCoding.getColorForPM25(pm25)
    }
    
    /**
     * Get AQI color from AQI category enum
     */
    fun getColorFromAQICategory(category: AQICategory): Color {
        return when (category) {
            AQICategory.GOOD -> Color(0xFF4CAF50)
            AQICategory.MODERATE -> Color(0xFFFFEB3B)
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> Color(0xFFFF9800)
            AQICategory.UNHEALTHY -> Color(0xFFE53935)
            AQICategory.VERY_UNHEALTHY -> Color(0xFF9C27B0)
            AQICategory.HAZARDOUS -> Color(0xFF795548)
        }
    }
    
    /**
     * Calculate proper Y-axis scale for chart
     * Returns min and max values with appropriate padding
     */
    fun calculateYAxisScale(dataPoints: List<HistoricalDataPointDetail>): Pair<Double, Double> {
        if (dataPoints.isEmpty()) return Pair(0.0, 300.0)

        val maxValue = dataPoints.maxOfOrNull { it.value } ?: 300.0
        val minValue = 0.0 // Always start from 0 for AQI charts
        
        // Add padding and round to nice numbers
        val paddedMax = when {
            maxValue <= 50 -> 100.0
            maxValue <= 100 -> 150.0
            maxValue <= 200 -> 250.0
            maxValue <= 300 -> 350.0
            else -> (maxValue * 1.2).let { 
                // Round to nearest 50
                ((it / 50).toInt() + 1) * 50.0
            }
        }
        
        return Pair(minValue, paddedMax)
    }
    
    /**
     * Generate Y-axis gridline positions
     * Returns evenly spaced values for gridlines
     */
    fun generateYAxisGridlines(minValue: Double, maxValue: Double, targetCount: Int = 6): List<Double> {
        val range = maxValue - minValue
        val step = range / (targetCount - 1)
        
        return (0 until targetCount).map { i ->
            minValue + (i * step)
        }
    }
    
    /**
     * Format timestamp for chart X-axis labels
     * Handles both hourly and daily formats
     */
    fun formatTimeLabel(timestamp: String, isHourly: Boolean): String {
        return try {
            // Try parsing different timestamp formats
            val parsedTime = parseTimestamp(timestamp)
            
            if (isHourly) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsedTime)
            } else {
                // For daily view, show day of month only to avoid repetition
                SimpleDateFormat("dd", Locale.getDefault()).format(parsedTime)
            }
        } catch (e: Exception) {
            // Fallback to showing first 5 characters
            timestamp.take(5)
        }
    }
    
    /**
     * Parse timestamp from various formats
     * Made public for use in other components
     */
    fun parseTimestamp(timestamp: String): Date {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "HH:mm"
        )
        
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                return sdf.parse(timestamp) ?: throw Exception("Parse returned null")
            } catch (e: Exception) {
                continue
            }
        }
        
        // Fallback to current time
        return Date()
    }
    
    /**
     * Calculate bar height as fraction of available space
     */
    fun calculateBarHeight(value: Double, minValue: Double, maxValue: Double): Float {
        if (maxValue <= minValue) return 0f
        
        val normalizedValue = (value - minValue) / (maxValue - minValue)
        return max(0.02f, min(1f, normalizedValue.toFloat())) // Minimum 2% height for visibility
    }
    
    /**
     * Generate X-axis label positions for better readability
     * Returns indices of data points that should show labels
     */
    fun generateXAxisLabelPositions(dataPointCount: Int, targetLabelCount: Int = 6): List<Int> {
        if (dataPointCount <= targetLabelCount) {
            return (0 until dataPointCount).toList()
        }
        
        val step = (dataPointCount - 1).toDouble() / (targetLabelCount - 1).toDouble()
        val positions = mutableListOf<Int>()
        
        for (i in 0 until targetLabelCount) {
            val position = (i * step).toInt()
            if (position < dataPointCount && !positions.contains(position)) {
                positions.add(position)
            }
        }
        
        return positions
    }
    
    /**
     * WHO guideline values for reference lines
     */
    object WHOGuidelines {
        const val DAILY_PM25_GUIDELINE = 15.0  // μg/m³
        const val ANNUAL_PM25_GUIDELINE = 5.0  // μg/m³
        
        fun getGuidelineForTimeframe(isHourly: Boolean): Double? {
            return if (isHourly) DAILY_PM25_GUIDELINE else null
        }
    }
}
