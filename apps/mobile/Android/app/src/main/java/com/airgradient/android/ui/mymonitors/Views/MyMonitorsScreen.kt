package com.airgradient.android.ui.mymonitors.Views

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.annotation.StringRes
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.airgradient.android.R
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.monitors.ChartTimeRange
import com.airgradient.android.domain.models.monitors.MonitorsPlace
import com.airgradient.android.domain.models.monitors.MonitorMetrics
import com.airgradient.android.domain.models.monitors.MonitorMeasurementKind
import com.airgradient.android.domain.models.monitors.TemperatureUnit
import com.airgradient.android.domain.models.monitors.labelResId
import com.airgradient.android.domain.models.monitors.temperature
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.ui.shared.Views.AgTopBar
import com.airgradient.android.ui.mymonitors.ViewModels.MonitorDetailUiState
import com.airgradient.android.ui.mymonitors.ViewModels.MonitorDetailViewModel
import com.airgradient.android.ui.mymonitors.ViewModels.MonitorSummaryUi
import com.airgradient.android.ui.mymonitors.ViewModels.PlaceSelectorUiState
import com.airgradient.android.ui.mymonitors.ViewModels.PlaceSelectorViewModel
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay

private object MyMonitorsDestinations {
    const val ROOT = "monitors_root"
    const val DETAIL = "monitor_detail/{placeId}/{locationId}"

    fun detailRoute(placeId: Int, locationId: Int): String = "monitor_detail/$placeId/$locationId"
}

private const val AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMonitorsScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = MyMonitorsDestinations.ROOT,
        modifier = modifier.fillMaxSize()
    ) {
        composable(MyMonitorsDestinations.ROOT) {
            val viewModel: PlaceSelectorViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PlaceSelectorRoute(
                uiState = uiState,
                onPlaceSelected = viewModel::onPlaceSelected,
                onMonitorSelected = { summary ->
                    navController.navigate(
                        MyMonitorsDestinations.detailRoute(summary.placeId, summary.locationId)
                    )
                },
                onRetryPlaces = viewModel::refreshPlaces,
                onRetryLocations = viewModel::retryLocations,
                onRetryReadings = viewModel::retryReadings,
                onRefreshContent = viewModel::refreshCurrentSelection
            )
        }

        composable(
            route = MyMonitorsDestinations.DETAIL,
            arguments = listOf(
                navArgument("placeId") { type = NavType.IntType },
                navArgument("locationId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val placeId = backStackEntry.arguments?.getInt("placeId") ?: return@composable
            val locationId = backStackEntry.arguments?.getInt("locationId") ?: return@composable

            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(MyMonitorsDestinations.ROOT)
            }
            val parentViewModel: PlaceSelectorViewModel = hiltViewModel(parentEntry)
            val parentState by parentViewModel.uiState.collectAsStateWithLifecycle()
            val summary = parentState.monitors.firstOrNull { it.locationId == locationId }

            val detailViewModel: MonitorDetailViewModel = hiltViewModel()
            LaunchedEffect(summary, placeId) {
                summary?.let { detailViewModel.setInitialData(placeId, it) }
            }
            val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()

            MonitorDetailRoute(
                uiState = detailUiState,
                fallbackSummary = summary,
                displayUnit = parentState.aqiDisplayUnit,
                onBack = { navController.popBackStack() },
                onSelectMetric = detailViewModel::selectMetric,
                onSelectRange = detailViewModel::selectTimeRange,
                onRetry = detailViewModel::retry
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
private fun PlaceSelectorRoute(
    uiState: PlaceSelectorUiState,
    onPlaceSelected: (Int) -> Unit,
    onMonitorSelected: (MonitorSummaryUi) -> Unit,
    onRetryPlaces: () -> Unit,
    onRetryLocations: () -> Unit,
    onRetryReadings: () -> Unit,
    onRefreshContent: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val refreshCallback by rememberUpdatedState(onRefreshContent)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            refreshCallback()
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                refreshCallback()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AgTopBar(
                title = stringResource(id = R.string.tab_my_monitors),
                centerTitle = true,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        when {
            uiState.isLoadingPlaces && uiState.places.isEmpty() -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(3) { index ->
                        MonitorsSkeletonCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    testTag = "monitors_loading_card_$index"
                                }
                        )
                    }
                }
            }
            uiState.placesError != null && uiState.places.isEmpty() -> {
                ErrorCard(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    message = uiState.placesError,
                    onRetry = onRetryPlaces
                )
            }
            else -> {
                val isRefreshing = uiState.isLoadingPlaces || uiState.isLoadingLocations || uiState.isLoadingReadings
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = isRefreshing,
                    onRefresh = onRefreshContent
                )

                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            PlaceSelectorCard(
                                places = uiState.places,
                                selectedPlaceId = uiState.selectedPlaceId,
                                onPlaceSelected = onPlaceSelected
                            )
                        }

                        if (uiState.locationsError != null) {
                            item {
                                InlineErrorMessage(
                                    message = uiState.locationsError,
                                    fallbackResId = R.string.my_monitors_locations_error,
                                    onRetry = onRetryLocations
                                )
                            }
                        }

                        if (uiState.readingsError != null) {
                            item {
                                InlineErrorMessage(
                                    message = uiState.readingsError,
                                    fallbackResId = R.string.my_monitors_readings_error,
                                    onRetry = onRetryReadings
                                )
                            }
                        }

                        if (uiState.isLoadingReadings && uiState.monitors.isNotEmpty()) {
                            item {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (uiState.monitors.isEmpty() && !uiState.isLoadingLocations && !uiState.isLoadingReadings) {
                            item {
                                EmptyStateCard()
                            }
                        }

                        items(uiState.monitors, key = { it.locationId }) { summary ->
                            MonitorCard(
                                summary = summary,
                                displayUnit = uiState.aqiDisplayUnit,
                                onClick = { onMonitorSelected(summary) }
                            )
                        }
                    }

                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaceSelectorCard(
    places: List<MonitorsPlace>,
    selectedPlaceId: Int?,
    onPlaceSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlace = places.firstOrNull { it.id == selectedPlaceId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (places.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.my_monitors_places_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val selectedName = selectedPlace?.name ?: stringResource(id = R.string.my_monitors_places_empty)
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        places.forEach { place ->
                            DropdownMenuItem(
                                text = { Text(text = place.name) },
                                onClick = {
                                    expanded = false
                                    onPlaceSelected(place.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MonitorCard(
    summary: MonitorSummaryUi,
    displayUnit: AQIDisplayUnit,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val pmValue = summary.metrics.pm25
    val baseColor = pmValue?.let { EPAColorCoding.getColorForPM25(it) } ?: MaterialTheme.colorScheme.primaryContainer
    val bannerTextColor = pmValue?.let { EPAColorCoding.getTextColorForBackground(it) } ?: MaterialTheme.colorScheme.onPrimaryContainer
    val categoryLabelRes = pmValue?.let { EPAColorCoding.getCategoryForPM25(it)?.labelRes() }

    val cardModifier = when {
        !enabled || summary.offline -> Modifier
            .fillMaxWidth()
            .let { base -> if (summary.offline) base.alpha(0.6f) else base }
        else -> Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    }

    val cardBackground = MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = 0.18f)
        .compositeOver(MaterialTheme.colorScheme.surface)

    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = cardBackground,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        val horizontalPadding = 20.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = horizontalPadding)
            )

            if (pmValue != null) {
                MonitorBanner(
                    pm25 = pmValue,
                    displayUnit = displayUnit,
                    baseColor = baseColor,
                    textColor = bannerTextColor,
                    categoryLabelRes = categoryLabelRes,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (summary.offline) {
                StatusChip(
                    icon = Icons.Default.SignalWifiOff,
                    label = stringResource(id = R.string.my_monitors_status_offline),
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                )
            }

            MetricsSection(
                summary = summary,
                modifier = Modifier.padding(horizontal = horizontalPadding)
            )

            summary.lastUpdatedLabel?.let { updatedLabel ->
                LastUpdatedRow(
                    label = updatedLabel,
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                )
            }
        }
    }
}

@Composable
private fun MonitorsSkeletonCard(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberMonitorShimmerBrush()
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = 0.18f)
        .compositeOver(MaterialTheme.colorScheme.surface)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        val horizontalPadding = 20.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MonitorSkeletonBlock(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(0.6f)
                    .height(20.dp),
                brush = shimmerBrush
            )

            MonitorSkeletonBlock(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(0.35f)
                    .height(16.dp),
                brush = shimmerBrush
            )

            MonitorSkeletonBlock(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth()
                    .height(96.dp),
                brush = shimmerBrush,
                shape = MaterialTheme.shapes.medium
            )

            Column(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MonitorSkeletonBlock(
                            modifier = Modifier.size(12.dp),
                            brush = shimmerBrush,
                            shape = CircleShape
                        )
                        MonitorSkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(16.dp),
                            brush = shimmerBrush
                        )
                    }
                }
            }

            MonitorSkeletonBlock(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .fillMaxWidth(0.4f)
                    .height(14.dp),
                brush = shimmerBrush,
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
private fun MonitorSkeletonBlock(
    modifier: Modifier,
    brush: Brush,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier.background(brush, shape)
    )
}

@Composable
private fun rememberMonitorShimmerBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "myMonitorsShimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val colors = listOf(
        colorScheme.surfaceVariant.copy(alpha = 0.6f),
        colorScheme.surfaceVariant.copy(alpha = 0.3f),
        colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(translate, 0f),
        end = Offset(translate + 400f, 0f)
    )
}

@Composable
private fun MonitorBanner(
    pm25: Double,
    displayUnit: AQIDisplayUnit,
    baseColor: Color,
    textColor: Color,
    categoryLabelRes: Int?,
    modifier: Modifier = Modifier
) {
    val backgroundBrush = Brush.horizontalGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.40f),
            baseColor.copy(alpha = 0.70f)
        )
    )

    val pmSuffix = stringResource(id = R.string.monitor_metric_pm25_suffix)
    val unitLabel = when (displayUnit) {
        AQIDisplayUnit.USAQI -> stringResource(id = R.string.unit_us_aqi_short)
        AQIDisplayUnit.UGM3 -> stringResource(id = R.string.unit_micrograms_per_cubic_meter)
    }
    val primaryValue = when (displayUnit) {
        AQIDisplayUnit.USAQI -> {
            val aqi = EPAColorCoding.getDisplayValueForMeasurement(pm25, MeasurementType.PM25, AQIDisplayUnit.USAQI)
            "$aqi $unitLabel $pmSuffix"
        }
        AQIDisplayUnit.UGM3 -> {
            val formatted = String.format(Locale.getDefault(), "%.1f", pm25)
            "$formatted $unitLabel $pmSuffix"
        }
    }
    val secondaryValue = if (displayUnit == AQIDisplayUnit.USAQI) {
        String.format(
            Locale.getDefault(),
            "%.1f %s",
            pm25,
            stringResource(id = R.string.unit_micrograms_per_cubic_meter)
        )
    } else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(brush = backgroundBrush, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categoryLabelRes?.let { labelRes ->
            Text(
                text = stringResource(id = labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
        }
        Text(
            text = primaryValue,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        secondaryValue?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MetricsSection(summary: MonitorSummaryUi, modifier: Modifier = Modifier) {
    val metrics = buildMetricDisplays(summary)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.forEach { metric ->
            MetricRow(metric)
        }
    }
}

@Composable
private fun MetricRow(display: MetricDisplay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = display.color)
            }
            Text(
                text = display.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = display.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LastUpdatedRow(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.my_monitors_last_updated, label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InlineErrorMessage(message: String, @StringRes fallbackResId: Int, onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        val displayMessage = if (message.isBlank()) {
            stringResource(id = fallbackResId)
        } else {
            message
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onRetry) {
                Text(text = stringResource(id = R.string.my_monitors_retry))
            }
        }
    }
}

@Composable
private fun ErrorCard(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text(text = stringResource(id = R.string.my_monitors_retry))
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.my_monitors_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(id = R.string.my_monitors_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
internal fun MonitorDetailRoute(
    uiState: MonitorDetailUiState,
    fallbackSummary: MonitorSummaryUi?,
    displayUnit: AQIDisplayUnit,
    onBack: () -> Unit,
    onSelectMetric: (MonitorMeasurementKind) -> Unit,
    onSelectRange: (ChartTimeRange) -> Unit,
    onRetry: () -> Unit
) {
    val summary = uiState.summary ?: fallbackSummary

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AgTopBar(
                title = summary?.name ?: stringResource(id = R.string.tab_my_monitors),
                centerTitle = true,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back_button)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        if (summary == null) {
            ErrorCard(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                message = stringResource(id = R.string.my_monitors_locations_error),
                onRetry = onBack
            )
            return@Scaffold
        }

        val history = uiState.history
        val pullRefreshState = rememberPullRefreshState(
            refreshing = uiState.isLoading,
            onRefresh = onRetry
        )

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MonitorCard(summary = summary, displayUnit = displayUnit, onClick = {}, enabled = false)

                MetricsTabs(
                    availableMetrics = uiState.availableMetrics,
                    selectedMetric = uiState.selectedMetric,
                    onSelect = onSelectMetric
                )

                MonitorMeasurementChart(
                    samples = history,
                    measurementKind = uiState.selectedMetric,
                    temperatureUnit = summary.temperatureUnit,
                    displayUnit = displayUnit,
                    selectedRange = uiState.selectedTimeRange,
                    onRangeSelected = onSelectRange,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onRetry = onRetry
                )
            }

            PullRefreshIndicator(
                refreshing = uiState.isLoading,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun MetricsTabs(
    availableMetrics: List<MonitorMeasurementKind>,
    selectedMetric: MonitorMeasurementKind,
    onSelect: (MonitorMeasurementKind) -> Unit
) {
    if (availableMetrics.isEmpty()) return

    ScrollableTabRow(
        selectedTabIndex = availableMetrics.indexOf(selectedMetric).coerceAtLeast(0),
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[availableMetrics.indexOf(selectedMetric).coerceAtLeast(0)]),
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        availableMetrics.forEach { metric ->
            Tab(
                selected = metric == selectedMetric,
                onClick = { onSelect(metric) }
            ) {
                Text(
                    text = stringResource(id = metric.labelResId()),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

private data class MetricDisplay(
    val label: String,
    val value: String,
    val color: Color
)

@Composable
private fun buildMetricDisplays(summary: MonitorSummaryUi): List<MetricDisplay> {
    val metrics = summary.metrics
    val displays = mutableListOf<MetricDisplay>()
    metrics.co2?.let { value ->
        displays += MetricDisplay(
            label = stringResource(id = R.string.monitor_metric_co2),
            value = formatNumber(value, 0) + " " + stringResource(id = R.string.unit_ppm),
            color = MonitorMetricColors.colorForCo2(value)
        )
    }
    metrics.tvocIndex?.let { value ->
        displays += MetricDisplay(
            label = stringResource(id = R.string.monitor_metric_tvoc),
            value = formatNumber(value, 1) + " " + stringResource(id = R.string.unit_index),
            color = MonitorMetricColors.colorForTvoc(value)
        )
    }
    metrics.noxIndex?.let { value ->
        displays += MetricDisplay(
            label = stringResource(id = R.string.monitor_metric_nox),
            value = formatNumber(value, 1) + " " + stringResource(id = R.string.unit_index),
            color = MonitorMetricColors.colorForNox(value)
        )
    }
    metrics.temperature(summary.temperatureUnit)?.let { value ->
        val unit = if (summary.temperatureUnit == TemperatureUnit.FAHRENHEIT) {
            stringResource(id = R.string.unit_temperature_fahrenheit)
        } else {
            stringResource(id = R.string.unit_temperature_celsius)
        }
        displays += MetricDisplay(
            label = stringResource(id = R.string.monitor_metric_temperature),
            value = formatNumber(value, 1) + " $unit",
            color = MonitorMetricColors.colorForTemperature(value, summary.temperatureUnit)
        )
    }
    metrics.humidity?.let { value ->
        displays += MetricDisplay(
            label = stringResource(id = R.string.monitor_metric_humidity),
            value = formatNumber(value, 0) + stringResource(id = R.string.unit_percent),
            color = MonitorMetricColors.colorForHumidity(value)
        )
    }
    return displays
}

private fun formatNumber(value: Double, decimals: Int): String {
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
    formatter.maximumFractionDigits = decimals
    formatter.minimumFractionDigits = decimals
    return formatter.format(value)
}

private fun AQICategory.labelRes(): Int = when (this) {
    AQICategory.GOOD -> R.string.aqi_good
    AQICategory.MODERATE -> R.string.aqi_moderate
    AQICategory.UNHEALTHY_FOR_SENSITIVE -> R.string.aqi_unhealthy_for_sensitive
    AQICategory.UNHEALTHY -> R.string.aqi_unhealthy
    AQICategory.VERY_UNHEALTHY -> R.string.aqi_very_unhealthy
    AQICategory.HAZARDOUS -> R.string.aqi_hazardous
}

// Preview placeholders for tooling
@Composable
private fun PreviewMonitorCard() {
    val metrics = MonitorSummaryUi(
        locationId = 1,
        placeId = 1,
        name = "Downtown School",
        metrics = MonitorMetrics(
            pm25 = 12.4,
            co2 = 720.0,
            tvocIndex = 0.5,
            noxIndex = 0.2,
            temperatureCelsius = 22.5,
            humidity = 45.0
        ),
        offline = false,
        indoor = true,
        lastUpdatedInstant = Instant.now(),
        lastUpdatedLabel = "5 minutes ago",
        temperatureUnit = TemperatureUnit.CELSIUS
    )
    MonitorCard(summary = metrics, displayUnit = AQIDisplayUnit.USAQI, onClick = {})
}
