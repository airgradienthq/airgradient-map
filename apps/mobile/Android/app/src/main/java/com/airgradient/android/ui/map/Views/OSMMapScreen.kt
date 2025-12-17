package com.airgradient.android.ui.map.Views

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
 
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.airgradient.android.data.models.AQIColorPalette
import com.airgradient.android.ui.shared.Utils.aqiCategoryLabelRes
import com.airgradient.android.data.models.MapCameraFocus
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.ui.locationdetail.ViewModels.LocationDetailViewModel
import com.airgradient.android.ui.locationdetail.Views.LocationDetailBottomSheet
import com.airgradient.android.ui.map.ViewModels.MapViewModel
import com.airgradient.android.ui.search.Views.AirQualitySearchBar
import com.airgradient.android.ui.map.Views.marker.MapMarkerCoordinator
import com.airgradient.android.ui.theme.md_theme_light_surfaceVariant
import com.airgradient.android.R
import com.airgradient.android.ui.shared.Views.AgBottomSheetDefaults
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import androidx.hilt.navigation.compose.hiltViewModel as hiltLocationDetailViewModel

private const val OSM_ATTRIBUTION_URL = "https://www.openstreetmap.org/copyright"
private const val OSM_ATTRIBUTION_TAG = "osm_link"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OSMMapScreen(
    selectedLocationId: Int?,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
    locationDetailViewModel: LocationDetailViewModel = hiltLocationDetailViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val locationDetailUiState by locationDetailViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            viewModel.onCurrentLocationRequested()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }
    var showLegend by remember { mutableStateOf(false) }
    var pendingExternalLocationId by remember { mutableStateOf<Int?>(null) }

    // Track render cycles
    android.util.Log.d("OSMMapScreen", "🔄 OSMMapScreen recomposed - locations: ${uiState.locations.size}, isLoading: ${uiState.isLoading}")
    
    // Initialize OSMDroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            // Reduce tile cache to prevent memory issues
            tileFileSystemCacheMaxBytes = 50L * 1024 * 1024 // 50MB cache
            tileFileSystemCacheTrimBytes = 40L * 1024 * 1024 // Trim to 40MB
            // Set tile download threads
            tileDownloadThreads = 2
            tileDownloadMaxQueueSize = 8
        }
    }
    
    var mapView: MapView? by remember { mutableStateOf(null) }
    var currentZoomLevel: Double by remember { mutableStateOf(8.0) }
    var markerCoordinator by remember { mutableStateOf<MapMarkerCoordinator?>(null) }
    // Keep the map rendering even when frozen so marker animations continue
    
    // Handle map center and zoom changes from search results and saved position
    LaunchedEffect(mapView, uiState.mapCenter, uiState.targetZoomLevel) {
        uiState.mapCenter?.let { (lat, lng) ->
            mapView?.controller?.apply {
                // Apply saved position with proper zoom
                if (uiState.targetZoomLevel != null) {
                    android.util.Log.d("OSMMapScreen", "Applying saved/target position: ($lat, $lng) with zoom ${uiState.targetZoomLevel}")
                    setCenter(GeoPoint(lat, lng))
                    setZoom(uiState.targetZoomLevel!!)

                    // Update the tracking variable
                    currentZoomLevel = uiState.targetZoomLevel!!

                    // Clear the target zoom level after applying it to avoid interfering with normal map interactions
                    kotlinx.coroutines.delay(500) // Small delay to ensure zoom is applied
                    viewModel.clearTargetZoomLevel()
                } else {
                    // Just center without changing zoom (for search results without specific zoom)
                    android.util.Log.d("OSMMapScreen", "Centering map at ($lat, $lng) without changing zoom")
                    setCenter(GeoPoint(lat, lng))
                }
            }
        }
    }
    
    // Simplified map listener setup - ViewportManager handles debouncing
    LaunchedEffect(mapView) {
        mapView?.let { map ->
            val mapListener = object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                    // ViewportManager will handle debouncing and data fetching
                    viewModel.onMapViewportChange(map)
                    return false
                }

                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                    // ViewportManager will handle debouncing and data fetching
                    viewModel.onMapViewportChange(map)
                    return false
                }
            }

            map.addMapListener(mapListener)

            // Initial data load - trigger viewport change to load data
            // ViewportManager will handle the actual data fetching
            viewModel.onMapViewportChange(map)
        }
    }
    
    // Track the last update to prevent duplicate calls

    // Render markers using coordinator
    LaunchedEffect(mapView, markerCoordinator, uiState.annotations, uiState.selectedMarkerKey, uiState.measurementType, uiState.aqiDisplayUnit, uiState.isMapFrozen) {
        val view = mapView ?: return@LaunchedEffect
        val coordinator = markerCoordinator ?: return@LaunchedEffect
        if (uiState.isMapFrozen) return@LaunchedEffect
        coordinator.render(
            mapView = view,
            annotations = uiState.annotations,
            selectedKey = uiState.selectedMarkerKey,
            measurementType = uiState.measurementType,
            displayUnit = uiState.aqiDisplayUnit,
            onSensorSelected = { annotation ->
                viewModel.onMarkerSelected(annotation)
                locationDetailViewModel.showLocationDetail(annotation.locationId, uiState.measurementType)
            },
            onClusterSelected = { annotation ->
                view.controller.animateTo(
                    GeoPoint(annotation.coordinate.first, annotation.coordinate.second),
                    view.zoomLevelDouble + 1.0,
                    800L
                )
            }
        )
    }

    LaunchedEffect(uiState.pendingCameraFocus, mapView) {
        val focus = uiState.pendingCameraFocus ?: return@LaunchedEffect
        val view = mapView ?: return@LaunchedEffect
        applyCameraFocus(view, focus)
        viewModel.consumeCameraFocus()
    }

    LaunchedEffect(locationDetailUiState.isVisible) {
        if (!locationDetailUiState.isVisible) {
            viewModel.clearMarkerSelection()
            viewModel.setMapFrozen(false)
        }
    }

    // Freeze map interactions whenever the location detail sheet is open
    LaunchedEffect(locationDetailUiState.isVisible) {
        viewModel.setMapFrozen(locationDetailUiState.isVisible)
    }

    LaunchedEffect(selectedLocationId) {
        if (selectedLocationId != null && selectedLocationId > 0) {
            pendingExternalLocationId = selectedLocationId
            locationDetailViewModel.showLocationDetail(selectedLocationId, uiState.measurementType)
        }
    }

    LaunchedEffect(pendingExternalLocationId, locationDetailUiState.location, mapView) {
        val targetId = pendingExternalLocationId
        val locationDetail = locationDetailUiState.location
        val view = mapView

        if (targetId != null && locationDetail != null && locationDetail.id == targetId && view != null) {
            val latitude = locationDetail.latitude
            val longitude = locationDetail.longitude
            val targetPoint = GeoPoint(latitude, longitude)
            val targetZoom = view.zoomLevelDouble.coerceAtLeast(13.0)
            view.controller.animateTo(targetPoint, targetZoom, 800L)
            pendingExternalLocationId = null
        }
    }

    LaunchedEffect(uiState.isMapFrozen, mapView) {
        val view = mapView ?: return@LaunchedEffect
        val frozen = uiState.isMapFrozen
        // Disable interactions but keep the map view rendering so marker animations (pulses)
        // continue to play even when the location detail bottom sheet is open/expanded.
        view.isEnabled = !frozen
        view.isClickable = !frozen
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView?.let { view ->
                markerCoordinator?.clear(view)
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // OpenStreetMap
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)

                    // Set initial Europe focus with regional zoom
                    controller.setZoom(6.0) // Continental overview zoom level
                    controller.setCenter(GeoPoint(50.0, 10.0)) // Center roughly on Central Europe

                    // Store reference for camera control
                    mapView = this
                    markerCoordinator = MapMarkerCoordinator()
                }
            },
            update = { osmMapView ->
                val newZoomLevel = osmMapView.zoomLevelDouble

                // Only update for significant zoom changes (for smooth zooming)
                if (kotlin.math.abs(newZoomLevel - currentZoomLevel) > 0.3) {
                    currentZoomLevel = newZoomLevel
                }
            }
        )

        // Do not overlay a static snapshot while frozen; this preserves live marker animations.

        // Top controls overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Search Bar
            AirQualitySearchBar(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = viewModel::updateSearchQuery,
                searchResults = uiState.searchResults,
                recentSearches = uiState.recentSearches,
                isSearching = uiState.isSearching,
                onLocationClick = viewModel::onLocationSelected,
                onRecentSearchClick = viewModel::onRecentSearchSelected
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TopMapControls(
                measurementType = uiState.measurementType,
                displayUnit = uiState.aqiDisplayUnit,
                onMeasurementTypeChange = viewModel::setMeasurementType,
                onLegendClick = { showLegend = true }
            )
            
            // Error message
            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.map_connection_error_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        
        // Current Location button (bottom-right)
        FloatingActionButton(
            onClick = {
                val fineGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fineGranted || coarseGranted) {
                    viewModel.onCurrentLocationRequested()
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 8.dp)
                .size(48.dp),
            containerColor = Color.White,
            contentColor = Color.Black
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = stringResource(R.string.widget_placeholder_location)
            )
        }

        val osmPrefix = stringResource(R.string.map_osm_attribution_prefix)
        val osmLabel = stringResource(R.string.map_osm_attribution_label)
        val osmSuffix = stringResource(R.string.map_osm_attribution_suffix)
        val osmLinkColor = MaterialTheme.colorScheme.primary

        val attributionText = remember(osmPrefix, osmLabel, osmSuffix, osmLinkColor) {
            buildAnnotatedString {
                append(osmPrefix)
                pushStringAnnotation(tag = OSM_ATTRIBUTION_TAG, annotation = OSM_ATTRIBUTION_URL)
                withStyle(SpanStyle(color = osmLinkColor)) {
                    append(osmLabel)
                }
                pop()
                append(" ")
                append(osmSuffix)
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.9f),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            ClickableText(
                text = attributionText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.Black,
                    fontSize = 6.sp
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                onClick = { offset ->
                    attributionText
                        .getStringAnnotations(OSM_ATTRIBUTION_TAG, offset, offset)
                        .firstOrNull()
                        ?.let { uriHandler.openUri(it.item) }
                }
            )
        }
        
    }

    // Location Settings Dialog
    if (uiState.showLocationSettingsDialog) {
        LocationSettingsDialog(
            onOpenSettings = {
                val intent = viewModel.getLocationSettingsIntent()
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Handle error if settings can't be opened
                }
                viewModel.dismissLocationSettingsDialog()
            },
            onDismiss = {
                viewModel.dismissLocationSettingsDialog()
            }
        )
    }

    // Location Detail Bottom Sheet - Show when visible, regardless of location data
    if (locationDetailUiState.isVisible) {
        // Use the currentLocationId which is set when showing location details
        val locationId = locationDetailUiState.currentLocationId ?: 1 // Fallback ID
        LocationDetailBottomSheet(
            locationId = locationId,
            onDismiss = { locationDetailViewModel.dismissDialog() }
        )
    }
    
    // Legend dialog
    if (showLegend) {
        MapLegendDialog(
            measurementType = uiState.measurementType,
            onDismiss = { showLegend = false }
        )
    }
}

@Composable
private fun TopMapControls(
    measurementType: MeasurementType,
    displayUnit: AQIDisplayUnit,
    onMeasurementTypeChange: (MeasurementType) -> Unit,
    onLegendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Material 3 Segmented Button with integrated info icon
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(100.dp)
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Measurement type segments
                MeasurementType.values().forEach { type ->
                    val isSelected = type == measurementType
                    val segmentLabel = when (type) {
                        MeasurementType.PM25 -> when (displayUnit) {
                            AQIDisplayUnit.UGM3 -> stringResource(R.string.settings_display_unit_ugm3)
                            AQIDisplayUnit.USAQI -> stringResource(R.string.settings_display_unit_us_aqi)
                        }
                        MeasurementType.CO2 -> stringResource(R.string.measurement_co2)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (isSelected) md_theme_light_surfaceVariant
                                else Color.Transparent
                            )
                            .clickable { onMeasurementTypeChange(type) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = segmentLabel,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                   else Color.Black.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color.Black.copy(alpha = 0.12f))
                )

                // Info icon button inside the segmented button
                IconButton(
                    onClick = onLegendClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.map_show_legend_content_description),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapLegendDialog(
    measurementType: MeasurementType,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White.copy(alpha = 0.8f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
                // AQI Categories in 2-column grid
                val legendEntries = if (measurementType == MeasurementType.CO2) {
                    AQIColorPalette.CO2_RANGES.map { range ->
                        LegendEntry(
                            labelRes = co2CategoryLabelRes(range.category),
                            minValue = range.min.toInt(),
                            maxValue = range.max.takeIf { it != Double.MAX_VALUE }?.toInt(),
                            color = Color(range.color)
                        )
                    }
                } else {
                    AQIColorPalette.US_EPA_RANGES.take(6).map { range ->
                        LegendEntry(
                            labelRes = aqiCategoryLabelRes(range.category),
                            minValue = range.min.toInt(),
                            maxValue = range.max.takeIf { it != Double.MAX_VALUE }?.toInt(),
                            color = Color(range.color)
                        )
                    }
                }
                val unitRes = if (measurementType == MeasurementType.CO2) {
                    R.string.map_legend_unit_ppm
                } else {
                    R.string.map_legend_unit_aqi
                }

                // Create rows of 2 items each
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    legendEntries.chunked(2).forEach { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowEntries.forEach { entry ->
                                AQICard(
                                    labelRes = entry.labelRes,
                                    minValue = entry.minValue,
                                    maxValue = entry.maxValue,
                                    unitRes = unitRes,
                                    color = entry.color,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Add empty space if odd number of items
                            if (rowEntries.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Marker legend at bottom - with horizontal padding to match cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MarkerLegendItem(
                        icon = { ClusterMarkerIcon() },
                        title = stringResource(R.string.cluster_marker_title),
                        subtitle = stringResource(R.string.cluster_marker_subtitle)
                    )
                    MarkerLegendItem(
                        icon = { ReferenceMarkerIcon() },
                        title = stringResource(R.string.reference_marker_title),
                        subtitle = stringResource(R.string.reference_marker_subtitle)
                    )
                    MarkerLegendItem(
                        icon = { CommunityMarkerIcon() },
                        title = stringResource(R.string.small_sensor_marker_subtitle),
                        subtitle = stringResource(R.string.community_marker_subtitle)
                    )
                }
        }
    }
}

@Composable
private fun AQICard(
    @StringRes labelRes: Int,
    minValue: Int,
    maxValue: Int?,
    @StringRes unitRes: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(70.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Use smaller font for long category names
            val categoryText = stringResource(labelRes)
            val categoryFontSize = if (categoryText.length > 15) 11.sp else 14.sp
            Text(
                text = categoryText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = categoryFontSize,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
            val unitLabel = stringResource(unitRes)
            val rangeText = maxValue?.let {
                stringResource(R.string.map_legend_range, minValue, it, unitLabel)
            } ?: stringResource(R.string.map_legend_range_open, minValue, unitLabel)
            Text(
                text = rangeText,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@StringRes
private fun co2CategoryLabelRes(category: String): Int = when (category) {
    "Excellent" -> R.string.co2_quality_excellent
    "Good" -> R.string.co2_quality_good
    "Moderate" -> R.string.co2_quality_moderate
    "Poor" -> R.string.co2_quality_poor
    else -> R.string.co2_quality_moderate
}

private data class LegendEntry(
    @StringRes val labelRes: Int,
    val minValue: Int,
    val maxValue: Int?,
    val color: Color
)

@Composable
private fun MarkerLegendItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            icon()
        }
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ClusterMarkerIcon() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF4CAF50), CircleShape)
        ) {
            Text(
                text = "25",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ReferenceMarkerIcon() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF4CAF50), RoundedCornerShape(2.dp))
            ) {
                Text(
                    text = "18",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun CommunityMarkerIcon() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
        ) {
            Text(
                text = "8",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun LocationSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    // Overlay background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        // Dialog card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.Center)
                .clickable { }, // Prevent dismissing when clicking card
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                // Title
                Text(
                    text = stringResource(R.string.map_location_services_disabled_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Description
                Text(
                    text = stringResource(R.string.map_location_services_disabled_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Gray
                        )
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }

                    // Open Settings button
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.map_open_settings_button))
                    }
                }
            }
        }
    }
}

private fun applyCameraFocus(mapView: MapView, focus: MapCameraFocus) {
    val target = GeoPoint(focus.center.first, focus.center.second)
    val controller = mapView.controller
    val desiredZoom = (mapView.zoomLevelDouble * focus.zoomMultiplier).coerceAtLeast(1.0)
    controller.animateTo(target, desiredZoom, 700L)
}
