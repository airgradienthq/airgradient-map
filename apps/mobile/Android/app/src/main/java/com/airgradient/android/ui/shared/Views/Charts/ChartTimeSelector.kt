package com.airgradient.android.ui.shared.Views.Charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airgradient.android.data.models.ChartTimeframe

/**
 * Reusable time selector for charts
 * Matches iOS design with blue selected state and white unselected
 */
@Composable
fun ChartTimeSelector(
    selectedTimeframe: ChartTimeframe,
    onTimeframeChange: (ChartTimeframe) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Gray.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ChartTimeframe.values().forEach { timeframe ->
                TimeframeButton(
                    timeframe = timeframe,
                    isSelected = timeframe == selectedTimeframe,
                    onClick = { onTimeframeChange(timeframe) },
                    enabled = enabled
                )
            }
        }
    }
}

/**
 * Individual timeframe button component
 */
@Composable
private fun TimeframeButton(
    timeframe: ChartTimeframe,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val backgroundColor = when {
        !enabled -> Color.Gray.copy(alpha = 0.3f)
        isSelected -> Color(0xFF2196F3) // iOS-style blue
        else -> Color.Transparent
    }
    
    val textColor = when {
        !enabled -> Color.Gray
        isSelected -> Color.White
        else -> Color.Black
    }
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timeframe.displayName,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Alternative implementation with Material 3 buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialChartTimeSelector(
    selectedTimeframe: ChartTimeframe,
    onTimeframeChange: (ChartTimeframe) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChartTimeframe.values().forEach { timeframe ->
            FilterChip(
                selected = timeframe == selectedTimeframe,
                onClick = { onTimeframeChange(timeframe) },
                label = {
                    Text(
                        text = timeframe.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (timeframe == selectedTimeframe) FontWeight.Bold else FontWeight.Normal
                    )
                },
                enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                    containerColor = Color.Gray.copy(alpha = 0.2f),
                    labelColor = Color.Gray
                )
            )
        }
    }
}

/**
 * Extended time selector for more timeframe options
 * Can be used for future enhancements (weekly, monthly views)
 */
@Composable
fun ExtendedChartTimeSelector(
    selectedTimeframe: String,
    timeframes: List<Pair<String, String>>, // Pair of (key, displayName)
    onTimeframeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Gray.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            timeframes.forEach { (key, displayName) ->
                val isSelected = key == selectedTimeframe
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected && enabled) Color(0xFF2196F3) 
                            else Color.Transparent
                        )
                        .clickable(enabled = enabled) { onTimeframeChange(key) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName,
                        color = when {
                            !enabled -> Color.Gray
                            isSelected -> Color.White
                            else -> Color.Black
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}