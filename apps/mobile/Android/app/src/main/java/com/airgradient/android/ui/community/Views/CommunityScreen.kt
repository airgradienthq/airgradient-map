package com.airgradient.android.ui.community.Views

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import android.view.MotionEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.airgradient.android.R
import com.airgradient.android.domain.models.BlogPost
import com.airgradient.android.domain.models.FeaturedCommunityProject
import com.airgradient.android.ui.community.ViewModels.ArticleUiState
import com.airgradient.android.ui.community.ViewModels.CommunityHeroStats
import com.airgradient.android.ui.community.ViewModels.CommunityViewModel
import com.airgradient.android.ui.community.ViewModels.FeaturedProjectsUiState
import com.airgradient.android.ui.community.ViewModels.KnowledgeHubUiState
import com.airgradient.android.ui.community.ViewModels.ProjectDetailUiState
import com.airgradient.android.ui.shared.Utils.openExternalUrl
import com.airgradient.android.ui.shared.Views.AgBottomSheetDefaults
import com.airgradient.android.ui.shared.Views.AgTopBar
import com.airgradient.android.ui.shared.Views.SectionHeader
import com.airgradient.android.ui.shared.Views.SectionPanel
import com.airgradient.android.ui.shared.Views.SectionPanelConfig
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BECOME_CONTRIBUTOR_URL = "https://www.airgradient.com/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    modifier: Modifier = Modifier,
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AgTopBar(
                title = stringResource(id = R.string.nav_community),
                centerTitle = true,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CommunityHeroSection(stats = uiState.heroStats)
            FeaturedProjectsSection(
                state = uiState.featuredProjects,
                onRetry = { viewModel.retryFeaturedProjects() },
                onProjectSelected = { project -> viewModel.onProjectSelected(project) }
            )
            KnowledgeHubSection(
                state = uiState.knowledgeHub,
                onRetry = { viewModel.retryKnowledgeHub() },
                onLoadMore = { viewModel.loadMorePosts() },
                onPostSelected = { post -> viewModel.onBlogPostSelected(post) }
            )
            PartnerProjectsSection(
                state = uiState.partnerProjects,
                onRetry = { viewModel.retryPartnerProjects() },
                onProjectSelected = { project -> viewModel.onProjectSelected(project) }
            )
            ActionSection()
            AboutMeSection()
            AboutAirGradientSection()
        }
    }

    ProjectDetailSheet(
        state = uiState.projectDetail,
        onDismiss = { viewModel.dismissProjectDetail() },
        onRetry = { viewModel.retryProjectDetail() }
    )

    ArticleBottomSheet(
        state = uiState.knowledgeHub.articleState,
        onDismiss = { viewModel.dismissArticle() },
        onRetry = { viewModel.retryArticle() }
    )
}

@Composable
private fun CommunityHeroSection(stats: CommunityHeroStats) {
    SectionPanel(
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = R.string.community_hero_title),
                subtitle = stringResource(id = R.string.community_hero_subtitle),
                mascotAssetName = "mascot-mission"
            )
        )
    ) {
        Text(
            text = stringResource(id = R.string.community_hero_mission),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CommunityHeroStatItem(
                value = stats.activeMonitors,
                label = stringResource(id = R.string.community_hero_stat_monitors)
            )
            CommunityHeroStatItem(
                value = stats.countries,
                label = stringResource(id = R.string.community_hero_stat_countries)
            )
            CommunityHeroStatItem(
                value = stats.citizens,
                label = stringResource(id = R.string.community_hero_stat_citizens)
            )
        }
    }
}

@Composable
private fun AboutAirGradientSection() {
    val context = LocalContext.current
    val aboutPoints = listOf(
        R.string.about_airgradient_open_source_title to R.string.about_airgradient_open_source_description,
        R.string.about_airgradient_global_community_title to R.string.about_airgradient_global_community_description,
        R.string.about_airgradient_real_impact_title to R.string.about_airgradient_real_impact_description,
        R.string.about_airgradient_environmental_justice_title to R.string.about_airgradient_environmental_justice_description
    )

    SectionPanel(
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = R.string.about_airgradient_title),
                subtitle = stringResource(id = R.string.about_airgradient_subtitle),
                mascotAssetName = "logo_white"
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            aboutPoints.forEach { (titleRes, descriptionRes) ->
                AboutAirGradientPoint(
                    title = stringResource(id = titleRes),
                    description = stringResource(id = descriptionRes)
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        openExternalUrl(
                            context = context,
                            url = "https://www.airgradient.com/join-us/",
                            errorMessage = context.getString(R.string.about_airgradient_website_error)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.about_airgradient_website_button),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutAirGradientPoint(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AboutMeSection() {
    val bulletPoints = listOf(
        R.string.about_me_design_point,
        R.string.about_me_team_point,
        R.string.about_me_community_point,
        R.string.about_me_job_point,
        R.string.about_me_powered_point
    )

    SectionPanel(
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = R.string.about_me_title),
                subtitle = stringResource(id = R.string.about_me_subtitle),
                mascotAssetName = "mascot_hello"
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(id = R.string.about_me_intro),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface,
                fontStyle = FontStyle.Italic
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bulletPoints.forEach { pointRes ->
                    AboutMeBullet(point = stringResource(id = pointRes))
                }
            }

            Text(
                text = stringResource(id = R.string.about_me_conclusion),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun AboutMeBullet(point: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = point,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showBrowser by remember { mutableStateOf(false) }

    if (showBrowser) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val scope = rememberCoroutineScope()
        val dismissSheet: () -> Unit = {
            scope.launch {
                sheetState.hide()
            }.invokeOnCompletion {
                showBrowser = false
            }
        }

        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = dismissSheet,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 21.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                    .heightIn(min = 320.dp)
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .heightIn(min = 260.dp)
                ) {
                    ContributorWebContent(
                        url = BECOME_CONTRIBUTOR_URL,
                        onClose = dismissSheet,
                        onError = {
                            Toast.makeText(
                                context,
                                context.getString(R.string.become_contributor_open_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    SectionPanel(
        modifier = modifier,
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = R.string.become_contributor_title)
            )
        )
    ) {
        Text(
            text = stringResource(id = R.string.become_contributor_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        Image(
            painter = painterResource(id = R.drawable.open_air_outdoor),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Button(
            onClick = {
                showBrowser = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(text = stringResource(id = R.string.become_contributor_learn_more))
        }
    }
}



@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ContributorWebContent(
    url: String,
    onClose: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasErrorHandled by remember { mutableStateOf(false) }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    DisposableEffect(webView) {
        val client = object : WebViewClient() {
            private fun triggerError() {
                if (!hasErrorHandled) {
                    hasErrorHandled = true
                    onError()
                    onClose()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame != false) {
                    triggerError()
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    triggerError()
                }
            }
        }
        webView.webViewClient = client

        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    LaunchedEffect(url) {
        hasErrorHandled = false
        webView.loadUrl(url)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webView }
    )
}


@Composable
private fun FeaturedProjectsSection(
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
private fun PartnerProjectsSection(
    state: FeaturedProjectsUiState,
    onRetry: () -> Unit,
    onProjectSelected: (FeaturedCommunityProject) -> Unit
) {
    ProjectsCarouselSection(
        titleRes = R.string.featured_partners_section_title,
        subtitleRes = R.string.featured_partners_section_description,
        state = state,
        onRetry = onRetry,
        onProjectSelected = onProjectSelected,
        emptyTitleRes = R.string.featured_partners_empty_title,
        emptyDescriptionRes = R.string.featured_partners_empty_description
    )
}

@Composable
fun ProjectsCarouselSection(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int,
    state: FeaturedProjectsUiState,
    onRetry: () -> Unit,
    onProjectSelected: (FeaturedCommunityProject) -> Unit,
    @StringRes loadingRes: Int = R.string.featured_projects_loading,
    @StringRes errorTitleRes: Int = R.string.featured_projects_error_title,
    @StringRes retryRes: Int = R.string.featured_projects_try_again,
    @StringRes emptyTitleRes: Int = R.string.featured_projects_empty_title,
    @StringRes emptyDescriptionRes: Int = R.string.featured_projects_empty_description,
    @StringRes learnMoreRes: Int = R.string.featured_projects_learn_more
) {
    SectionPanel(
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = titleRes),
                subtitle = stringResource(id = subtitleRes)
            )
        )
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(id = loadingRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            state.hasError -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(id = errorTitleRes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(onClick = onRetry) {
                        Text(text = stringResource(id = retryRes))
                    }
                }
            }
            state.isEmpty -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(id = emptyTitleRes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = emptyDescriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                BoxWithConstraints {
                    val cardWidth = maxWidth * 0.75f

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.projects, key = { it.id }) { project ->
                            val distanceMeters = state.projectDistances[project.id]
                            FeaturedProjectCard(
                                project = project,
                                distanceMeters = distanceMeters,
                                onLearnMore = { onProjectSelected(project) },
                                learnMoreRes = learnMoreRes,
                                modifier = Modifier.width(cardWidth)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeaturedProjectCard(
    project: FeaturedCommunityProject,
    distanceMeters: Double?,
    onLearnMore: () -> Unit,
    @StringRes learnMoreRes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val imageModel: Any = project.imageUrl.takeIf { it.isNotBlank() }
                ?: R.drawable.mascot_mission

            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val density = LocalDensity.current
                val titleLineHeight = MaterialTheme.typography.titleMedium.lineHeight
                val titleMinHeight = if (titleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) {
                    with(density) { titleLineHeight.toDp() * 2 }
                } else 52.dp
                val subtitleLineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                val subtitleMinHeight = if (subtitleLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) {
                    with(density) { subtitleLineHeight.toDp() * 2 }
                } else 48.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = titleMinHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                project.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = subtitleMinHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                distanceMeters?.let { distance ->
                    DistanceLabel(distance)
                }

                Button(
                    onClick = onLearnMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Text(text = stringResource(id = learnMoreRes))
                }
            }
        }
    }
}

@Composable
private fun CommunityHeroStatItem(
    modifier: Modifier = Modifier,
    value: String,
    label: String
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DistanceLabel(distanceMeters: Double) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val usesImperial = locale.usesImperialDistance()
    val unitRes = if (usesImperial) {
        R.string.featured_project_distance_mi
    } else {
        R.string.featured_project_distance_km
    }
    val rawValue = if (usesImperial) {
        distanceMeters / 1609.344
    } else {
        distanceMeters / 1000.0
    }
    val formattedValue = formatDistanceValue(rawValue, locale)

    Text(
        text = stringResource(id = unitRes, formattedValue),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatDistanceValue(value: Double, locale: Locale): String {
    val displayValue = if (value >= 100) {
        value.roundToInt().toDouble()
    } else {
        (value * 10).roundToInt() / 10.0
    }
    val isWholeNumber = kotlin.math.abs(displayValue - displayValue.roundToInt()) < 1e-6
    return if (isWholeNumber) {
        String.format(locale, "%,.0f", displayValue)
    } else {
        String.format(locale, "%,.1f", displayValue)
    }
}

private fun Locale.usesImperialDistance(): Boolean {
    return country.uppercase(Locale.ROOT) in setOf("US", "LR", "MM", "GB")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailSheet(
    state: ProjectDetailUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (!state.isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxSheetHeight = remember(configuration) {
        configuration.screenHeightDp.dp * AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                        .padding(24.dp)
                        .heightIn(max = maxSheetHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(id = R.string.featured_project_detail_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            state.error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                        .padding(24.dp)
                        .heightIn(max = maxSheetHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.featured_project_detail_error_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(onClick = onRetry) {
                        Text(text = stringResource(id = R.string.featured_project_detail_retry_button))
                    }
                }
            }
            else -> {
                val detail = state.detail
                if (detail == null) {
                    onDismiss()
                    return@ModalBottomSheet
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .heightIn(max = maxSheetHeight)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    detail.featuredImageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = detail.featuredImageAlt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .widthIn(max = 520.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        text = detail.title.ifBlank { state.projectTitle.orEmpty() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    detail.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    detail.publishedDate?.let { publishedMillis ->
                        formatPublishedDate(publishedMillis)?.let { formatted ->
                            Text(
                                text = stringResource(id = R.string.featured_project_detail_published, formatted),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (detail.content.isNotBlank()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(id = R.string.featured_project_detail_about_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = detail.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    detail.externalUrl?.takeIf { it.isNotBlank() }?.let { partnerUrl ->
                        OutlinedButton(
                            onClick = {
                                openExternalUrl(
                                    context = context,
                                    url = partnerUrl,
                                    errorMessage = context.getString(R.string.featured_project_detail_partner_error)
                                )
                            },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(text = stringResource(id = R.string.featured_project_detail_partner_button))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun formatPublishedDate(epochMillis: Long): String? {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return androidx.compose.runtime.remember(locale, epochMillis) {
        try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", locale)
            val zonedDateTime = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault())
            formatter.format(zonedDateTime)
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun KnowledgeHubSection(
    state: KnowledgeHubUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPostSelected: (BlogPost) -> Unit
) {
    SectionPanel(
        config = SectionPanelConfig(
            header = SectionHeader(
                title = stringResource(id = R.string.knowledge_hub_title),
                subtitle = stringResource(id = R.string.knowledge_hub_subtitle),
                mascotAssetName = "mascot-book"
            )
        )
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(id = R.string.knowledge_hub_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.knowledge_hub_loading_error),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(onClick = onRetry) {
                        Text(text = stringResource(id = R.string.knowledge_hub_retry))
                    }
                }
            }
            state.posts.isEmpty() -> {
                Text(
                    text = stringResource(id = R.string.knowledge_hub_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            else -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.posts.take(state.expandedCount).forEach { post ->
                        BlogPostCard(post = post, onClick = { onPostSelected(post) })
                    }

                    if (state.expandedCount < state.posts.size) {
                        OutlinedButton(onClick = onLoadMore) {
                            Text(text = stringResource(id = R.string.knowledge_hub_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlogPostCard(post: BlogPost, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            post.summary.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val metaParts = listOfNotNull(
                formatBlogDate(post.publishDate),
                post.author?.takeIf { it.isNotBlank() }
            )
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(separator = " • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleBottomSheet(
    state: ArticleUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    if (!state.isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(id = R.string.knowledge_hub_article_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.knowledge_hub_article_error),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(onClick = onRetry) {
                        Text(text = stringResource(id = R.string.knowledge_hub_article_retry))
                    }
                    state.fallbackUrl?.let { fallback ->
                        OutlinedButton(onClick = {
                            openExternalUrl(
                                context = context,
                                url = fallback,
                                errorMessage = context.getString(R.string.knowledge_hub_article_open_browser)
                            )
                        }) {
                            Text(text = stringResource(id = R.string.knowledge_hub_article_open_browser))
                        }
                    }
                }
            }
            else -> {
                val article = state.article
                if (article == null) {
                    onDismiss()
                    return@ModalBottomSheet
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    article.heroImageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = article.heroImageAlt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .widthIn(max = 520.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val metaItems = buildList {
                        formatBlogDate(article.publishedDate)?.let { add(it) }
                        article.author?.takeIf { it.isNotBlank() }?.let { add(it) }
                        article.readingTime?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                    if (metaItems.isNotEmpty()) {
                        Text(
                            text = metaItems.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (article.categories.isNotEmpty()) {
                        Text(
                            text = article.categories.joinToString(separator = " • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    ArticleContent(article.content)

                    OutlinedButton(onClick = {
                        openExternalUrl(
                            context = context,
                            url = article.canonicalUrl,
                            errorMessage = context.getString(R.string.knowledge_hub_article_open_browser)
                        )
                    }) {
                        Text(text = stringResource(id = R.string.knowledge_hub_article_open_browser))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ArticleContent(html: String) {
    val color = MaterialTheme.colorScheme.onSurface
    val bodyFont = MaterialTheme.typography.bodyMedium.fontSize
    val fontSizeSp = if (bodyFont.isUnspecified) 16f else bodyFont.value
    val sanitizedHtml = remember(html) { sanitizeArticleHtml(html) }

    AndroidView(
        factory = { ctx ->
            HtmlTextView(ctx).apply {
                setHtml(sanitizedHtml, color.toArgb(), fontSizeSp)
            }
        },
        update = { view ->
            view.setHtml(sanitizedHtml, color.toArgb(), fontSizeSp)
        }
    )
}

private val SCRIPT_TAG_REGEX = Regex("(?is)<script[^>]*>.*?</script>")

private fun sanitizeArticleHtml(rawHtml: String): String {
    return SCRIPT_TAG_REGEX.replace(rawHtml, "")
}

@Composable
private fun formatBlogDate(epochMillis: Long): String? {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return remember(locale, epochMillis) {
        try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
            val zonedDateTime = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault())
            formatter.format(zonedDateTime)
        } catch (_: Exception) {
            null
        }
    }
}
