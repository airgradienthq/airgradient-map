package com.airgradient.android.ui.shared.Views.Charts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airgradient.android.R

/**
 * Loading state for charts with spinner and message
 */
@Composable
fun ChartLoadingState(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    val loadingText = message ?: stringResource(R.string.chart_loading_message)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF2196F3)
            )
            Text(
                text = loadingText,
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Error state for charts with retry functionality
 */
@Composable
fun ChartErrorState(
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val resolvedError = errorMessage ?: stringResource(R.string.chart_error_message)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📊",
                fontSize = 32.sp
            )
            
            Text(
                text = resolvedError,
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

/**
 * Empty state for charts when no data is available
 */
@Composable
fun ChartEmptyState(
    message: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val resolvedMessage = message ?: stringResource(R.string.chart_empty_message)
    val resolvedSubtitle = subtitle ?: stringResource(R.string.chart_empty_subtitle)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "📈",
                fontSize = 32.sp
            )
            
            Text(
                text = resolvedMessage,
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            if (subtitle != null || resolvedSubtitle.isNotEmpty()) {
                Text(
                    text = resolvedSubtitle,
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * Chart overlay loading state for when data is being refreshed
 * Shows a subtle loading indicator without replacing the entire chart
 */
@Composable
fun ChartOverlayLoading(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(8.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF2196F3)
                )
            }
        }
    }
}

/**
 * Combined chart state handler
 * Manages loading, error, empty, and success states in one component
 */
@Composable
fun ChartStateHandler(
    isLoading: Boolean,
    error: String? = null,
    isEmpty: Boolean,
    onRetry: (() -> Unit)? = null,
    loadingMessage: String? = null,
    emptyMessage: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val resolvedLoadingMessage = loadingMessage ?: stringResource(R.string.chart_loading_message)
    val resolvedEmptyMessage = emptyMessage ?: stringResource(R.string.chart_empty_message)
    when {
        isLoading && isEmpty -> {
            ChartLoadingState(
                message = resolvedLoadingMessage,
                modifier = modifier
            )
        }
        
        error != null && isEmpty -> {
            ChartErrorState(
                errorMessage = error,
                onRetry = onRetry,
                modifier = modifier
            )
        }
        
        isEmpty -> {
            ChartEmptyState(
                message = resolvedEmptyMessage,
                modifier = modifier
            )
        }
        
        else -> {
            Box(modifier = modifier) {
                content()
                ChartOverlayLoading(isLoading = isLoading)
            }
        }
    }
}

/**
 * Inline loading state for switching timeframes
 * Shows a subtle loading indicator within the chart area
 */
@Composable
fun ChartInlineLoading(
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFF2196F3)
                )
                Text(
                    text = stringResource(R.string.chart_updating_message),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
