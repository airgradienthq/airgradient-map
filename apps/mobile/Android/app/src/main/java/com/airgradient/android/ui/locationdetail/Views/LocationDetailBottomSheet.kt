package com.airgradient.android.ui.locationdetail.Views

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airgradient.android.R
import com.airgradient.android.data.models.*
import com.airgradient.android.ui.locationdetail.ViewModels.LocationDetailViewModel
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.models.AirQualityInsightKey
import com.airgradient.android.domain.models.RecommendationAction
import com.airgradient.android.ui.community.ViewModels.FeaturedProjectsUiState
import com.airgradient.android.ui.community.Views.ActionSection
import com.airgradient.android.ui.community.Views.ProjectDetailSheet
import com.airgradient.android.ui.community.Views.ProjectsCarouselSection
import com.airgradient.android.ui.shared.Views.AgBottomSheetDefaults
import com.airgradient.android.ui.shared.Views.AirgradientOutlinedCard
import com.airgradient.android.ui.shared.Views.SectionHeader
import com.airgradient.android.ui.shared.Views.SectionPanel
import com.airgradient.android.ui.shared.Views.SectionPanelConfig
import com.airgradient.android.ui.shared.Utils.openExternalUrl
import com.airgradient.android.ui.shared.ShareEvent
import com.airgradient.android.ui.location.Views.NotificationsBottomSheet
import com.airgradient.android.domain.models.FeaturedCommunityProject
import com.airgradient.android.domain.models.MeasurementType
import java.util.Locale

/**
 * Main bottom sheet dialog for displaying location details
 * Matches iOS design with green header for good AQI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailBottomSheet(
    locationId: Int,
    onDismiss: () -> Unit,
    viewModel: LocationDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    rememberCoroutineScope()
    var showRemoveBookmarkDialog by remember { mutableStateOf(false) }

    // Load location data when sheet opens
    LaunchedEffect(locationId) {
        viewModel.showLocationDetail(locationId)
    }

    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { event ->
            android.util.Log.d("LocationDetailBottomSheet", "Share event received: $event")
            val shareIntent = when (event) {
                is ShareEvent.Image -> Intent(Intent.ACTION_SEND).apply {
                    type = event.mimeType
                    putExtra(Intent.EXTRA_SUBJECT, event.subject)
                    putExtra(Intent.EXTRA_TEXT, event.message)
                    putExtra(Intent.EXTRA_STREAM, event.uri)
                    clipData = android.content.ClipData.newUri(
                        context.contentResolver,
                        event.subject,
                        event.uri
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                is ShareEvent.Text -> Intent(Intent.ACTION_SEND).apply {
                    type = event.mimeType
                    putExtra(Intent.EXTRA_SUBJECT, event.subject)
                    putExtra(Intent.EXTRA_TEXT, event.message)
                }
            }

            runCatching {
                if (context !is Activity) {
                    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                android.util.Log.d("LocationDetailBottomSheet", "Starting share intent")
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
            }.onFailure { exception ->
                android.util.Log.e("LocationDetailBottomSheet", "Failed to start share intent", exception)
                Toast.makeText(context, R.string.error_share_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(uiState.shareState.errorMessage) {
        uiState.shareState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (uiState.isVisible) {
        // Get lighter background color for dialog based on current location's PM2.5 value
        val dialogBackgroundColor = uiState.location?.let { location ->
            getDialogBackgroundColor(location.currentPM25)
        } ?: 0xFFF1F8E9 // Default light green

        LaunchedEffect(sheetState.currentValue) {
            viewModel.setExpanded(sheetState.currentValue == SheetValue.Expanded)
        }

        ModalBottomSheet(
            onDismissRequest = {
                viewModel.dismissDialog()
                onDismiss()
            },
            sheetState = sheetState,
            containerColor = Color(dialogBackgroundColor.toLong()),
            contentColor = Color.Black,
            dragHandle = {
                // Custom white drag handle
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
            ) {
                if (uiState.measurementType == MeasurementType.CO2) {
                    val locationLabel = uiState.location?.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(id = R.string.unknown_location)
                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                // Header with title and action buttons
                DialogHeader(
                    shareState = uiState.shareState,
                    onShareClick = viewModel::shareLocation,
                    isBookmarked = uiState.isBookmarked,
                    hasNotifications = uiState.hasActiveNotifications,
                    onNotificationsUpdated = viewModel::refreshNotificationStatus,
                    onBookmarkToggle = {
                        if (uiState.isBookmarked) {
                            showRemoveBookmarkDialog = true
                        } else {
                            viewModel.toggleBookmark()
                        }
                    },
                    locationName = uiState.location?.name,
                    locationId = uiState.location?.id
                )
                
                // Content below header
                LocationDetailContent(
                    uiState = uiState,
                    onTimeframeChange = viewModel::updateChartTimeframe,
                    onWHOGuidelinesClick = { /* TODO: Navigate to WHO details */ },
                    onCommunityProjectsRetry = viewModel::retryCommunityProjects,
                    onCommunityProjectSelected = viewModel::onCommunityProjectSelected
                )

                ProjectDetailSheet(
                    state = uiState.communityProjectDetail,
                    onDismiss = viewModel::dismissCommunityProjectDetail,
                    onRetry = viewModel::retryCommunityProjectDetail
                )
            }
        }
    }

    if (showRemoveBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveBookmarkDialog = false },
            title = { Text(stringResource(R.string.button_remove_bookmark)) },
            text = { Text(stringResource(R.string.confirm_remove_bookmark_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveBookmarkDialog = false
                        viewModel.toggleBookmark()
                    }
                ) {
                    Text(stringResource(R.string.button_remove_bookmark))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveBookmarkDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AirQualityInsightsSection(state: AirQualityInsightsState) {
    val headerMascot = if (state.hasValidData) state.mascotAssetName else "mascot-idea"

    SectionPanel(
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = R.string.air_quality_insights_title),
                mascotAssetName = headerMascot,
                accentColor = state.accentColor
            )
        )
    ) {
        if (!state.hasValidData || state.actions.isEmpty()) {
            Text(
                text = stringResource(id = R.string.air_quality_insights_no_data),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                state.actions.forEach { key ->
                    AirQualityInsightItem(recommendation = rememberInsightRecommendation(key))
                }
            }
        }
    }
}

@Composable
private fun CommunityProjectsCarousel(
    state: FeaturedProjectsUiState,
    onRetry: () -> Unit,
    onProjectSelected: (FeaturedCommunityProject) -> Unit
) {
    ProjectsCarouselSection(
        titleRes = R.string.featured_community_projects_title,
        subtitleRes = R.string.featured_community_projects_description,
        state = state,
        onRetry = onRetry,
        onProjectSelected = onProjectSelected
    )
}

@Composable
private fun HealthImpactSummarySection(
    cigaretteState: CigaretteEquivalenceState,
    whoCompliance: WHOCompliance?,
    onCigaretteInfoClick: () -> Unit,
    onWhoDetailsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CigaretteSummaryCard(
            state = cigaretteState,
            onInfoClick = onCigaretteInfoClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        WhoGuidelineSummaryCard(
            whoCompliance = whoCompliance,
            onViewDetailsClick = onWhoDetailsClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun CigaretteSummaryCard(
    state: CigaretteEquivalenceState,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AirgradientOutlinedCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.cigarettes_smoked_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.cigarettes_smoked_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                state.value30Days != null -> {
                    val valueFormatted = String.format(Locale.getDefault(), "%.1f", state.value30Days)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = valueFormatted,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.cigarettes_smoked_summary_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.cigarettes_smoked_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorAttributionSection(
    contributorName: String?,
    dataProvider: String?,
    license: String?
) {
    val resolvedName = contributorName?.takeIf { it.isNotBlank() }
        ?: stringResource(id = R.string.location_contributor_unknown_name)
    val resolvedProvider = dataProvider?.takeIf { it.isNotBlank() }
        ?: stringResource(id = R.string.location_contributor_unknown_provider)
    val resolvedLicense = license?.takeIf { it.isNotBlank() }
        ?: stringResource(id = R.string.location_contributor_unknown_license)

    AirgradientOutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.location_contributor_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    id = R.string.location_contributor_description,
                    resolvedName,
                    resolvedProvider,
                    resolvedLicense
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WhoGuidelineSummaryCard(
    whoCompliance: WHOCompliance?,
    onViewDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasData = whoCompliance != null
    val interactionSource = remember { MutableInteractionSource() }
    val ratioText = if (hasData) {
        val guideline = whoCompliance!!.annualGuideline
        val limit = guideline.limit
        val current = guideline.currentValue
        if (limit > 0) {
            val ratio = current / limit
            when {
                ratio > 1.05 -> stringResource(id = R.string.who_guideline_ratio_above, ratio)
                ratio < 0.95 -> stringResource(id = R.string.who_guideline_ratio_below, ratio)
                else -> stringResource(id = R.string.who_guideline_ratio_within)
            }
        } else {
            stringResource(id = R.string.who_guideline_ratio_within)
        }
    } else {
        stringResource(id = R.string.who_guideline_value_unavailable)
    }

    val currentGuideline = whoCompliance?.annualGuideline
    val currentValueText = currentGuideline?.let {
        String.format(Locale.getDefault(), "%.1f %s", it.currentValue, it.unit)
    }
    val limitValueText = currentGuideline?.let {
        String.format(Locale.getDefault(), "%.1f %s", it.limit, it.unit)
    }

    val cardModifier = if (hasData) {
        modifier.clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onViewDetailsClick() }
    } else {
        modifier
    }

    AirgradientOutlinedCard(
        modifier = cardModifier,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = ratioText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (hasData && currentValueText != null && limitValueText != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    GuidelineValueBlock(
                        label = stringResource(id = R.string.who_guideline_current_label),
                        value = currentValueText
                    )

                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    GuidelineValueBlock(
                        label = stringResource(id = R.string.who_guideline_limit_label),
                        value = limitValueText
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.who_guideline_value_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidelineValueBlock(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HealthSummaryPill(text = value)
    }
}

@Composable
private fun HealthSummaryPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private const val CIGARETTE_BLOG_URL = "https://www.airgradient.com/blog/how-many-cigarettes-do-you-smoke/"

@Composable
private fun rememberInsightRecommendation(key: AirQualityInsightKey): RecommendationAction {
    val (titleRes, messageRes) = when (key) {
        AirQualityInsightKey.ENJOY_OUTDOORS -> R.string.insights_action_enjoy_outdoors_title to R.string.insights_action_enjoy_outdoors_text
        AirQualityInsightKey.VENTILATE_HOME -> R.string.insights_action_ventilate_home_title to R.string.insights_action_ventilate_home_text
        AirQualityInsightKey.REDUCE_EXERTION -> R.string.insights_action_reduce_exertion_title to R.string.insights_action_reduce_exertion_text
        AirQualityInsightKey.MOVE_WORKOUT_INDOORS -> R.string.insights_action_move_workout_indoors_title to R.string.insights_action_move_workout_indoors_text
        AirQualityInsightKey.STAY_INDOORS -> R.string.insights_action_stay_indoors_title to R.string.insights_action_stay_indoors_text
        AirQualityInsightKey.CLOSE_WINDOWS -> R.string.insights_action_close_windows_title to R.string.insights_action_close_windows_text
        AirQualityInsightKey.RUN_PURIFIER -> R.string.insights_action_run_purifier_title to R.string.insights_action_run_purifier_text
        AirQualityInsightKey.WEAR_N95 -> R.string.insights_action_wear_n95_title to R.string.insights_action_wear_n95_text
        AirQualityInsightKey.MONITOR_SYMPTOMS -> R.string.insights_action_monitor_symptoms_title to R.string.insights_action_monitor_symptoms_text
        AirQualityInsightKey.PROTECT_VULNERABLE -> R.string.insights_action_protect_vulnerable_title to R.string.insights_action_protect_vulnerable_text
    }

    return RecommendationAction(
        key = key,
        title = stringResource(id = titleRes),
        message = stringResource(id = messageRes)
    )
}

@Composable
private fun AirQualityInsightItem(recommendation: RecommendationAction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = recommendation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Content of the location detail bottom sheet
 */
@Composable
fun LocationDetailContent(
    uiState: LocationDetailUiState,
    onTimeframeChange: (ChartTimeframe) -> Unit,
    onWHOGuidelinesClick: () -> Unit,
    onCommunityProjectsRetry: () -> Unit,
    onCommunityProjectSelected: (FeaturedCommunityProject) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(1f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Loading state
        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        
        // Error state
        uiState.error?.let { error ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        
        // Location header with AQI color
        uiState.location?.let { location ->
            item {
                LocationHeaderCard(
                    location = location,
                    measurementType = uiState.measurementType,
                    displayUnit = uiState.displayUnit
                )
            }
            
            // Air quality insights section
            item {
                AirQualityInsightsSection(state = uiState.airQualityInsights)
            }

            // Historical chart section
            item {
                HistoricalChartCard(
                    historicalData = uiState.historicalData,
                    chartTimeframe = uiState.chartTimeframe,
                    measurementType = uiState.measurementType,
                    displayUnit = uiState.displayUnit,
                    onTimeframeChange = onTimeframeChange,
                    isLoading = uiState.isLoadingHistorical
                )
            }

            // Combined health impact section
            item {
                val context = LocalContext.current
                HealthImpactSummarySection(
                    cigaretteState = uiState.cigaretteEquivalence,
                    whoCompliance = uiState.whoCompliance,
                    onCigaretteInfoClick = {
                        openExternalUrl(
                            context = context,
                            url = CIGARETTE_BLOG_URL,
                            errorMessage = context.getString(R.string.cigarettes_smoked_open_error)
                        )
                    },
                    onWhoDetailsClick = onWHOGuidelinesClick
                )
            }

            // Heat map section
            item {
                HeatMapSection(state = uiState.heatMap)
            }

            // Contributor attribution
            item {
                ContributorAttributionSection(
                    contributorName = location.organization?.displayName ?: location.organization?.name ?: location.name,
                    dataProvider = location.dataSource,
                    license = location.license
                )
            }

            // Organization attribution (Data Partner)
            val featuredCommunityInfo = uiState.featuredCommunity.info
            if (featuredCommunityInfo != null) {
                location.organization?.let { org ->
                    item {
                        OrganizationAttributionCard(
                            organization = org,
                            featuredCommunity = featuredCommunityInfo
                        )
                    }
                }
            }

            // Community projects carousel
            item {
                CommunityProjectsCarousel(
                    state = uiState.communityProjects,
                    onRetry = onCommunityProjectsRetry,
                    onProjectSelected = onCommunityProjectSelected
                )
            }

            // Call to action section reused from Community tab
            item {
                ActionSection()
            }
        }
    }
}

/**
 * Header for the dialog with location name and action buttons
 */
@Composable
private fun DialogHeader(
    shareState: ShareUiState,
    onShareClick: () -> Unit,
    isBookmarked: Boolean,
    hasNotifications: Boolean,
    onNotificationsUpdated: () -> Unit,
    onBookmarkToggle: () -> Unit,
    locationName: String?,
    locationId: Int?,
    modifier: Modifier = Modifier
) {
    var showNotifications by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Share button on the left
        val shareEnabled = shareState.isReady
        val shareIconTint = MaterialTheme.colorScheme.primary
        val shareIconColor = if (shareEnabled) {
            shareIconTint
        } else {
            shareIconTint.copy(alpha = 0.5f)
        }
        IconButton(
            onClick = {
                android.util.Log.d("LocationDetailBottomSheet", "Share button clicked!")
                onShareClick()
            },
            enabled = shareEnabled,
            modifier = Modifier.size(24.dp),
        ) {
            if (shareState.isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = shareIconTint
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(id = R.string.button_share_location),
                    tint = shareIconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Notification and Bookmark buttons on the right
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showNotifications = true },
                modifier = Modifier.size(24.dp),
            ) {
                val notificationsIcon = if (hasNotifications) {
                    Icons.Filled.Notifications
                } else {
                    Icons.Outlined.Notifications
                }
                Icon(
                    imageVector = notificationsIcon,
                    contentDescription = stringResource(R.string.settings_section_notifications),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onBookmarkToggle,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isBookmarked) {
                        stringResource(R.string.button_remove_bookmark)
                    } else {
                        stringResource(R.string.button_bookmark_location)
                    },
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // Show notifications bottom sheet
    if (showNotifications) {
        val notificationTitle = locationName ?: stringResource(R.string.unknown_location)
        if (locationId != null) {
            NotificationsBottomSheet(
                onDismiss = { shouldRefresh ->
                    showNotifications = false
                    if (shouldRefresh) {
                        onNotificationsUpdated()
                    }
                },
                locationId = locationId,
                locationName = notificationTitle
            )
        } else {
            showNotifications = false
        }
    }
}

/**
 * Get lighter background color for dialog based on PM2.5 value
 * These are lighter tints of the banner colors for better readability
 */
private fun getDialogBackgroundColor(pm25: Double): Long {
    val category = EPAColorCoding.getCategoryForPM25(pm25) ?: AQICategory.GOOD
    return EPAColorCoding.lightBackgroundColorForCategory(category)
}
