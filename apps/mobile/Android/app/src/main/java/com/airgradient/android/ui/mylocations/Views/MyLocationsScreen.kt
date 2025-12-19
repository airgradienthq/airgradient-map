package com.airgradient.android.ui.mylocations.Views

import android.content.res.Resources
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.airgradient.android.domain.models.AQICategory
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.domain.models.monitors.MonitorsPlace
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.ui.shared.Utils.toDisplayNameRes
import com.airgradient.android.ui.mylocations.ViewModels.BookmarkedLocationWithData
import com.airgradient.android.ui.mylocations.ViewModels.MyLocationsSortOption
import com.airgradient.android.ui.mylocations.ViewModels.MyLocationsViewModel
import com.airgradient.android.ui.mymonitors.ViewModels.MonitorSummaryUi
import com.airgradient.android.ui.mymonitors.ViewModels.PlaceSelectorViewModel
import com.airgradient.android.ui.mymonitors.Views.MonitorCard
import com.airgradient.android.R
import com.airgradient.android.ui.community.Views.ActionSection
import com.airgradient.android.ui.shared.Views.AgTopBar
import java.util.Locale

@Composable
private fun displayUnitShortName(unit: AQIDisplayUnit): String {
    return when (unit) {
        AQIDisplayUnit.UGM3 -> stringResource(R.string.unit_ugm3)
        AQIDisplayUnit.USAQI -> stringResource(R.string.unit_us_aqi_short)
    }
}

private sealed interface MyLocationsListItem {
    val key: String
    val normalizedName: String
    val pm25: Double?
    val isOutdoor: Boolean
    val isOffline: Boolean
    val source: MyLocationsItemSource
}

private enum class MyLocationsItemSource(val sortRank: Int) {
    OWNED(0),
    PUBLIC(1)
}

private data class MonitorListItem(val summary: MonitorSummaryUi) : MyLocationsListItem {
    override val key: String = "monitor_${summary.placeId}_${summary.locationId}"
    override val normalizedName: String = summary.name.lowercase(Locale.getDefault())
    override val pm25: Double? = summary.metrics.pm25?.takeIf { it.isFinite() && it >= 0 }
    override val isOutdoor: Boolean = summary.indoor == false
    override val isOffline: Boolean = summary.offline
    override val source: MyLocationsItemSource = MyLocationsItemSource.OWNED
}

private data class BookmarkedLocationListItem(val location: BookmarkedLocationWithData) : MyLocationsListItem {
    private val displayName: String = location.resolvedDisplayName()
    override val key: String = "location_${location.bookmark.locationId}"
    override val normalizedName: String = displayName.lowercase(Locale.getDefault())
    override val pm25: Double? = location.currentMeasurement?.pm25?.takeIf { it.isFinite() && it >= 0 }
    override val isOutdoor: Boolean = false
    override val isOffline: Boolean = location.currentMeasurement == null
    override val source: MyLocationsItemSource = MyLocationsItemSource.PUBLIC
}

private fun BookmarkedLocationWithData.resolvedDisplayName(): String {
    return location?.displayName?.takeIf { it.isNotBlank() } ?: bookmark.locationName
}

private fun sortMyLocationsItems(
    items: List<MyLocationsListItem>,
    sortOption: MyLocationsSortOption
): List<MyLocationsListItem> {
    val comparator = when (sortOption) {
        MyLocationsSortOption.ALPHABETICAL -> compareBy<MyLocationsListItem> { it.normalizedName }
            .thenBy { it.key }
        MyLocationsSortOption.AIR_QUALITY -> compareByDescending<MyLocationsListItem> { it.pm25SortValue() }
            .thenBy { it.normalizedName }
            .thenBy { it.key }
        MyLocationsSortOption.OUTDOOR_FIRST -> compareBy<MyLocationsListItem> { if (it.isOutdoor) 0 else 1 }
            .thenByDescending { it.pm25SortValue() }
            .thenBy { it.normalizedName }
            .thenBy { it.key }
        MyLocationsSortOption.GROUPED -> compareBy<MyLocationsListItem> { it.source.sortRank }
            .thenBy { it.normalizedName }
            .thenBy { it.key }
    }

    val (online, offline) = items.partition { !it.isOffline }
    return online.sortedWith(comparator) + offline.sortedWith(comparator)
}

private fun MyLocationsListItem.pm25SortValue(): Double {
    return pm25 ?: Double.NEGATIVE_INFINITY
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MyLocationsScreen(
    onNavigateToLocationDetail: (Int) -> Unit,
    onNavigateToMonitorDetail: (placeId: Int, locationId: Int) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onExploreMap: () -> Unit = {},
    isAuthenticated: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: MyLocationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val allowedSortOptions = if (isAuthenticated) {
        MyLocationsSortOption.signedInOptions
    } else {
        MyLocationsSortOption.signedOutOptions
    }
    val effectiveSortOption = uiState.sortOption.takeIf { it in allowedSortOptions }
        ?: MyLocationsSortOption.ALPHABETICAL

    LaunchedEffect(isAuthenticated, uiState.sortOption) {
        if (uiState.sortOption !in allowedSortOptions) {
            viewModel.updateSortOption(MyLocationsSortOption.ALPHABETICAL)
        }
    }

    if (isAuthenticated) {
        val monitorsViewModel: PlaceSelectorViewModel = hiltViewModel()
        val monitorsState by monitorsViewModel.uiState.collectAsStateWithLifecycle()

        val isRefreshing = uiState.isLoading ||
            monitorsState.isLoadingPlaces ||
            monitorsState.isLoadingLocations ||
            monitorsState.isLoadingReadings

        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = {
                viewModel.refresh()
                monitorsViewModel.refreshCurrentSelection()
            }
        )

        val combinedItems = remember(uiState.bookmarkedLocations, monitorsState.monitors, effectiveSortOption) {
            val monitorItems = monitorsState.monitors.map { MonitorListItem(it) }
            val bookmarkedItems = uiState.bookmarkedLocations.map { BookmarkedLocationListItem(it) }
            sortMyLocationsItems(monitorItems + bookmarkedItems, effectiveSortOption)
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MyLocationsTopAppBar(
                    onNavigateToSettings = onNavigateToSettings,
                    sortOption = effectiveSortOption,
                    sortOptions = allowedSortOptions,
                    onSortOptionSelected = viewModel::updateSortOption,
                    places = monitorsState.places,
                    selectedPlaceId = monitorsState.selectedPlaceId,
                    onPlaceSelected = monitorsViewModel::onPlaceSelected
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 0.dp
                    )
                ) {
                    uiState.error?.let { errorMessage ->
                        item {
                            AssistiveMessage(text = errorMessage)
                        }
                    }

                    monitorsState.placesError?.let { message ->
                        item { AssistiveMessage(text = message) }
                    }

                    monitorsState.locationsError?.let { message ->
                        item { AssistiveMessage(text = message) }
                    }

                    monitorsState.readingsError?.let { message ->
                        item { AssistiveMessage(text = message) }
                    }

                    if (monitorsState.isLoadingReadings && monitorsState.monitors.isNotEmpty()) {
                        item {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    when {
                        combinedItems.isEmpty() && isRefreshing -> {
                            item {
                                MyLocationsLoadingState(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp)
                                )
                            }
                        }
                        combinedItems.isEmpty() -> {
                            item {
                                MyLocationsEmptyState(
                                    modifier = Modifier.fillMaxWidth(),
                                    onExploreMap = onExploreMap
                                )
                            }
                        }
                        else -> {
                            items(
                                items = combinedItems,
                                key = { it.key }
                            ) { listItem ->
                                when (listItem) {
                                    is MonitorListItem -> MonitorCard(
                                        summary = listItem.summary,
                                        displayUnit = monitorsState.aqiDisplayUnit,
                                        onClick = {
                                            onNavigateToMonitorDetail(
                                                listItem.summary.placeId,
                                                listItem.summary.locationId
                                            )
                                        }
                                    )
                                    is BookmarkedLocationListItem -> MyLocationCard(
                                        bookmarkedLocation = listItem.location,
                                        displayUnit = uiState.displayUnit,
                                        onLocationClick = {
                                            onNavigateToLocationDetail(listItem.location.bookmark.locationId)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.isLoading && uiState.bookmarkedLocations.isNotEmpty()) {
                        items(count = 2, key = { index -> "my_locations_loading_" + index }) {
                            MyLocationSkeletonCard(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        ActionSection(
                            modifier = Modifier
                                .fillMaxWidth()
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
    } else {
        val isRefreshing = uiState.isLoading
        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = { viewModel.refresh() }
        )

        val bookmarkedItems = remember(uiState.bookmarkedLocations, effectiveSortOption) {
            sortMyLocationsItems(
                items = uiState.bookmarkedLocations.map { BookmarkedLocationListItem(it) },
                sortOption = effectiveSortOption
            )
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MyLocationsTopAppBar(
                    onNavigateToSettings = onNavigateToSettings,
                    sortOption = effectiveSortOption,
                    sortOptions = allowedSortOptions,
                    onSortOptionSelected = viewModel::updateSortOption
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 0.dp
                    )
                ) {
                    uiState.error?.let { errorMessage ->
                        item {
                            AssistiveMessage(text = errorMessage)
                        }
                    }

                    when {
                        uiState.isLoading && uiState.bookmarkedLocations.isEmpty() -> {
                            item {
                                MyLocationsLoadingState(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp)
                                )
                            }
                        }
                        uiState.bookmarkedLocations.isEmpty() -> {
                            item {
                                MyLocationsEmptyState(
                                    modifier = Modifier.fillMaxWidth(),
                                    onExploreMap = onExploreMap
                                )
                            }
                        }
                        else -> {
                            items(
                                items = bookmarkedItems,
                                key = { it.key }
                            ) { listItem ->
                                when (listItem) {
                                    is BookmarkedLocationListItem -> MyLocationCard(
                                        bookmarkedLocation = listItem.location,
                                        displayUnit = uiState.displayUnit,
                                        onLocationClick = {
                                            onNavigateToLocationDetail(listItem.location.bookmark.locationId)
                                        }
                                    )
                                    else -> Unit
                                }
                            }
                        }
                    }

                    if (uiState.isLoading && uiState.bookmarkedLocations.isNotEmpty()) {
                        items(count = 2, key = { index -> "my_locations_loading_" + index }) {
                            MyLocationSkeletonCard(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        ActionSection(
                            modifier = Modifier
                                .fillMaxWidth()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyLocationsTopAppBar(
    onNavigateToSettings: () -> Unit,
    sortOption: MyLocationsSortOption,
    sortOptions: List<MyLocationsSortOption>,
    onSortOptionSelected: (MyLocationsSortOption) -> Unit,
    places: List<MonitorsPlace> = emptyList(),
    selectedPlaceId: Int? = null,
    onPlaceSelected: (Int) -> Unit = {}
) {
    val showPlaceSelector = places.size > 1

    AgTopBar(
        title = {
            if (showPlaceSelector) {
                PlaceSelectorTopBarTitle(
                    places = places,
                    selectedPlaceId = selectedPlaceId,
                    onPlaceSelected = onPlaceSelected
                )
            } else {
                Text(
                    text = stringResource(id = R.string.nav_my_locations),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        centerTitle = true,
        actions = {
            MyLocationsSortMenu(
                currentOption = sortOption,
                options = sortOptions,
                onOptionSelected = onSortOptionSelected
            )
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(id = R.string.nav_settings),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun PlaceSelectorTopBarTitle(
    places: List<MonitorsPlace>,
    selectedPlaceId: Int?,
    onPlaceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedPlace = places.firstOrNull { it.id == selectedPlaceId }
    val selectedLabel = selectedPlace?.name ?: stringResource(id = R.string.my_monitors_places_label)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null
            )
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
                    },
                    leadingIcon = {
                        if (place.id == selectedPlaceId) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyLocationsSortMenu(
    currentOption: MyLocationsSortOption,
    options: List<MyLocationsSortOption>,
    onOptionSelected: (MyLocationsSortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(
                    id = R.string.my_locations_sort_label,
                    stringResource(id = currentOption.labelRes)
                ),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(id = option.labelRes),
                            fontWeight = if (option == currentOption) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                    leadingIcon = {
                        if (option == currentOption) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
private fun AssistiveMessage(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun MyLocationsLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(3) {
            MyLocationSkeletonCard(
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MyLocationSkeletonCard(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                SkeletonBlock(
                    modifier = Modifier.matchParentSize(),
                    brush = shimmerBrush,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(20.dp),
                                brush = shimmerBrush
                            )
                            SkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(18.dp),
                                brush = shimmerBrush
                            )
                            SkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(16.dp),
                                brush = shimmerBrush
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        SkeletonBlock(
                            modifier = Modifier.size(68.dp),
                            brush = shimmerBrush,
                            shape = CircleShape
                        )
                    }

                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(16.dp),
                        brush = shimmerBrush
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                SkeletonBlock(
                    modifier = Modifier.matchParentSize(),
                    brush = shimmerBrush,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(110.dp)
                            .height(18.dp),
                        brush = shimmerBrush,
                        shape = RoundedCornerShape(12.dp)
                    )

                    SkeletonBlock(
                        modifier = Modifier
                            .width(72.dp)
                            .height(18.dp),
                        brush = shimmerBrush,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    brush: Brush,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "myLocationsShimmer")
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
private fun MyLocationsEmptyState(
    modifier: Modifier = Modifier,
    onExploreMap: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.aqi_mascot_good),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Text(
                text = stringResource(R.string.my_locations_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(R.string.my_locations_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onExploreMap,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.my_locations_explore_map))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyLocationCard(
    bookmarkedLocation: BookmarkedLocationWithData,
    displayUnit: AQIDisplayUnit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unitLabel = displayUnitShortName(displayUnit)

    val rawPm25 = bookmarkedLocation.currentMeasurement?.pm25
    val pm25 = rawPm25?.takeIf { it.isFinite() && it >= 0 }

    val accentColor = pm25?.let { EPAColorCoding.colorForMeasurement(it, MeasurementType.PM25, displayUnit) }
        ?: MaterialTheme.colorScheme.primary
    val locationTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val categoryColor = accentColor

    val category = pm25?.let { EPAColorCoding.getCategoryForPM25(it) }
    val displayValue = pm25?.let { EPAColorCoding.getDisplayValueForMeasurement(it, MeasurementType.PM25, displayUnit) }
    val microgramsLabel = pm25?.takeIf { displayUnit != AQIDisplayUnit.UGM3 }?.let {
        String.format(Locale.US, "%.1f %s", it, context.getString(R.string.unit_ugm3))
    }
    val backgroundRes = pm25?.let { backgroundForPm25(it) }
    val mascotRes = pm25?.let { mascotForPm25(it) } ?: R.drawable.aqi_mascot_good

    val displayName = bookmarkedLocation.location?.displayName
        ?: bookmarkedLocation.bookmark.locationName
    val ownerName = bookmarkedLocation.location?.organizationInfo?.publicName?.takeIf { it.isNotBlank() }
        ?: bookmarkedLocation.location?.sensorInfo?.dataSource?.takeIf { it.isNotBlank() }
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onLocationClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                backgroundRes?.let { res ->
                    Image(
                        painter = painterResource(id = res),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                color = locationTextColor,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            category?.let {
                                Text(
                                    text = stringResource(id = it.toDisplayNameRes()),
                                    color = categoryColor,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val recentValueText = displayValue?.let { "$it $unitLabel" }
                                ?: stringResource(R.string.my_locations_no_recent_readings)
                            Text(
                                text = recentValueText,
                                color = locationTextColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = mascotRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            microgramsLabel?.let {
                                Text(
                                    text = it,
                                    color = secondaryTextColor,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = accentColor,
                contentColor = contentColorFor(accentColor),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        val resources = LocalContext.current.resources
                        Text(
                            text = bookmarkedLocation.lastUpdate?.let { formatRelativeTime(resources, it) }
                                ?: stringResource(R.string.my_locations_no_recent_data),
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Text(
                            text = ownerName ?: stringResource(id = R.string.app_name),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            color = Color.White,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@DrawableRes
private fun backgroundForPm25(pm25: Double): Int {
    val category = EPAColorCoding.getCategoryForPM25(pm25) ?: AQICategory.GOOD
    return EPAColorCoding.backgroundForCategory(category)
}

@DrawableRes
private fun mascotForPm25(pm25: Double): Int {
    val category = EPAColorCoding.getCategoryForPM25(pm25) ?: AQICategory.GOOD
    return EPAColorCoding.mascotForCategory(category)
}

private fun formatRelativeTime(resources: Resources, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> resources.getString(R.string.my_locations_relative_just_now)
        diff < 3_600_000 -> {
            val minutes = (diff / 60_000).toInt().coerceAtLeast(1)
            resources.getQuantityString(R.plurals.my_locations_relative_minutes, minutes, minutes)
        }
        diff < 86_400_000 -> {
            val hours = (diff / 3_600_000).toInt().coerceAtLeast(1)
            resources.getQuantityString(R.plurals.my_locations_relative_hours, hours, hours)
        }
        else -> {
            val days = (diff / 86_400_000).toInt().coerceAtLeast(1)
            resources.getQuantityString(R.plurals.my_locations_relative_days, days, days)
        }
    }
}
