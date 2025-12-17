package com.airgradient.android.ui.community.ViewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.data.services.LocationService
import com.airgradient.android.data.services.LocationServiceResult
import com.airgradient.android.data.services.UserLocation
import com.airgradient.android.domain.models.FeaturedCommunityProject
import com.airgradient.android.domain.models.FeaturedCommunityProjectDetail
import com.airgradient.android.domain.models.BlogPost
import com.airgradient.android.domain.models.Article
import com.airgradient.android.domain.usecases.GetFeaturedCommunityProjectDetailUseCase
import com.airgradient.android.domain.usecases.GetFeaturedCommunityProjectsUseCase
import com.airgradient.android.domain.usecases.GetLatestBlogPostsUseCase
import com.airgradient.android.domain.usecases.GetBlogArticleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class CommunityHeroStats(
    val activeMonitors: String,
    val countries: String,
    val citizens: String
)

data class FeaturedProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<FeaturedCommunityProject> = emptyList(),
    val hasError: Boolean = false,
    val isEmpty: Boolean = false,
    val projectDistances: Map<String, Double> = emptyMap()
)

data class CommunityUiState(
    val heroStats: CommunityHeroStats = DefaultHeroStats,
    val featuredProjects: FeaturedProjectsUiState = FeaturedProjectsUiState(),
    val partnerProjects: FeaturedProjectsUiState = FeaturedProjectsUiState(),
    val projectDetail: ProjectDetailUiState = ProjectDetailUiState(),
    val knowledgeHub: KnowledgeHubUiState = KnowledgeHubUiState()
)

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val getFeaturedCommunityProjectsUseCase: GetFeaturedCommunityProjectsUseCase,
    private val getFeaturedCommunityProjectDetailUseCase: GetFeaturedCommunityProjectDetailUseCase,
    private val getLatestBlogPostsUseCase: GetLatestBlogPostsUseCase,
    private val getBlogArticleUseCase: GetBlogArticleUseCase,
    private val locationService: LocationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private var lastSelectedProject: FeaturedCommunityProject? = null
    private var userLocation: UserLocation? = null
    private var featuredProjectsRaw: List<FeaturedCommunityProject> = emptyList()
    private var partnerProjectsRaw: List<FeaturedCommunityProject> = emptyList()

    init {
        loadFeaturedProjects()
        loadPartnerProjects()
        loadKnowledgeHubPosts()
        fetchUserLocation()
    }

    fun retryFeaturedProjects() {
        loadFeaturedProjects()
    }

    fun retryPartnerProjects() {
        loadPartnerProjects()
    }

    fun onProjectSelected(project: FeaturedCommunityProject) {
        lastSelectedProject = project
        _uiState.value = _uiState.value.copy(
            projectDetail = ProjectDetailUiState(
                isVisible = true,
                isLoading = true,
                projectTitle = project.title
            )
        )
        loadProjectDetail(project)
    }

    fun dismissProjectDetail() {
        _uiState.value = _uiState.value.copy(projectDetail = ProjectDetailUiState())
        lastSelectedProject = null
    }

    fun retryProjectDetail() {
        lastSelectedProject?.let { project ->
            _uiState.value = _uiState.value.copy(
                projectDetail = _uiState.value.projectDetail.copy(
                    isLoading = true,
                    error = false
                )
            )
            loadProjectDetail(project)
        }
    }

    fun loadMorePosts() {
        _uiState.value = _uiState.value.copy(
            knowledgeHub = _uiState.value.knowledgeHub.copy(
                expandedCount = (_uiState.value.knowledgeHub.expandedCount + INITIAL_VISIBLE_POSTS)
                    .coerceAtMost(_uiState.value.knowledgeHub.posts.size)
            )
        )
    }

    fun retryKnowledgeHub() {
        loadKnowledgeHubPosts()
    }

    fun onBlogPostSelected(post: BlogPost) {
        _uiState.value = _uiState.value.copy(
            knowledgeHub = _uiState.value.knowledgeHub.copy(
                articleState = ArticleUiState(
                    isVisible = true,
                    isLoading = true,
                    fallbackUrl = post.url
                )
            )
        )

        viewModelScope.launch {
            val result = getBlogArticleUseCase(post)
            val articleState = if (result.isSuccess) {
                ArticleUiState(
                    isVisible = true,
                    isLoading = false,
                    article = result.getOrNull(),
                    fallbackUrl = post.url
                )
            } else {
                ArticleUiState(
                    isVisible = true,
                    isLoading = false,
                    error = true,
                    fallbackUrl = post.url
                )
            }

            _uiState.value = _uiState.value.copy(
                knowledgeHub = _uiState.value.knowledgeHub.copy(articleState = articleState)
            )
        }
    }

    fun dismissArticle() {
        _uiState.value = _uiState.value.copy(
            knowledgeHub = _uiState.value.knowledgeHub.copy(articleState = ArticleUiState())
        )
    }

    fun retryArticle() {
        val posts = _uiState.value.knowledgeHub.posts
        val fallbackUrl = _uiState.value.knowledgeHub.articleState.fallbackUrl
        val post = posts.firstOrNull { it.url == fallbackUrl }
        if (post != null) {
            onBlogPostSelected(post)
        }
    }

    private fun loadFeaturedProjects(category: String? = "Community") {
        _uiState.value = _uiState.value.copy(
            featuredProjects = FeaturedProjectsUiState(isLoading = true)
        )

        viewModelScope.launch {
            val result = getFeaturedCommunityProjectsUseCase(Locale.getDefault(), category)
            val featuredState = if (result.isSuccess) {
                val projects = result.getOrNull() ?: emptyList()
                featuredProjectsRaw = projects
                val (sortedProjects, distanceMap) = buildDisplayProjects(projects)
                FeaturedProjectsUiState(
                    isLoading = false,
                    projects = sortedProjects,
                    isEmpty = projects.isEmpty(),
                    hasError = false,
                    projectDistances = distanceMap
                )
            } else {
                featuredProjectsRaw = emptyList()
                FeaturedProjectsUiState(
                    isLoading = false,
                    hasError = true
                )
            }

            _uiState.value = _uiState.value.copy(featuredProjects = featuredState)
        }
    }

    private fun loadPartnerProjects() {
        _uiState.value = _uiState.value.copy(
            partnerProjects = FeaturedProjectsUiState(isLoading = true)
        )

        viewModelScope.launch {
            val result = getFeaturedCommunityProjectsUseCase(Locale.getDefault(), "Partner")
            val partnerState = if (result.isSuccess) {
                val projects = result.getOrNull() ?: emptyList()
                partnerProjectsRaw = projects
                val (sortedProjects, distanceMap) = buildDisplayProjects(projects)
                FeaturedProjectsUiState(
                    isLoading = false,
                    projects = sortedProjects,
                    isEmpty = projects.isEmpty(),
                    hasError = false,
                    projectDistances = distanceMap
                )
            } else {
                partnerProjectsRaw = emptyList()
                FeaturedProjectsUiState(
                    isLoading = false,
                    hasError = true
                )
            }

            _uiState.value = _uiState.value.copy(partnerProjects = partnerState)
        }
    }

    private fun loadKnowledgeHubPosts() {
        _uiState.value = _uiState.value.copy(
            knowledgeHub = _uiState.value.knowledgeHub.copy(
                isLoading = true,
                error = false
            )
        )

        viewModelScope.launch {
            val result = getLatestBlogPostsUseCase()
            val knowledgeState = if (result.isSuccess) {
                val posts = result.getOrNull().orEmpty()
                KnowledgeHubUiState(
                    isLoading = false,
                    posts = posts,
                    error = false,
                    expandedCount = minOf(INITIAL_VISIBLE_POSTS, posts.size)
                )
            } else {
                KnowledgeHubUiState(
                    isLoading = false,
                    error = true,
                    expandedCount = INITIAL_VISIBLE_POSTS
                )
            }

            _uiState.value = _uiState.value.copy(knowledgeHub = knowledgeState)
        }
    }

    private fun loadProjectDetail(project: FeaturedCommunityProject) {
        viewModelScope.launch {
            val result = getFeaturedCommunityProjectDetailUseCase(
                Locale.getDefault(),
                project.id,
                project.projectUrl
            )

            val detailState = if (result.isSuccess) {
                val detail = result.getOrNull()
                _uiState.value.projectDetail.copy(
                    isLoading = false,
                    detail = detail,
                    error = false,
                    projectTitle = detail?.title ?: _uiState.value.projectDetail.projectTitle
                )
            } else {
                _uiState.value.projectDetail.copy(
                    isLoading = false,
                    error = true
                )
            }

            _uiState.value = _uiState.value.copy(projectDetail = detailState)
        }
    }

    private fun fetchUserLocation() {
        viewModelScope.launch {
            when (val result = locationService.getLastKnownLocation()) {
                is LocationServiceResult.Success -> {
                    userLocation = result.location
                    recalculateProjectDistances()
                }
                else -> Unit
            }
        }
    }

    private fun recalculateProjectDistances() {
        if (featuredProjectsRaw.isNotEmpty() && !_uiState.value.featuredProjects.hasError) {
            val (projects, distanceMap) = buildDisplayProjects(featuredProjectsRaw)
            _uiState.value = _uiState.value.copy(
                featuredProjects = _uiState.value.featuredProjects.copy(
                    projects = projects,
                    projectDistances = distanceMap,
                    isEmpty = projects.isEmpty()
                )
            )
        }

        if (partnerProjectsRaw.isNotEmpty() && !_uiState.value.partnerProjects.hasError) {
            val (projects, distanceMap) = buildDisplayProjects(partnerProjectsRaw)
            _uiState.value = _uiState.value.copy(
                partnerProjects = _uiState.value.partnerProjects.copy(
                    projects = projects,
                    projectDistances = distanceMap,
                    isEmpty = projects.isEmpty()
                )
            )
        }
    }

    private fun buildDisplayProjects(
        projects: List<FeaturedCommunityProject>
    ): Pair<List<FeaturedCommunityProject>, Map<String, Double>> {
        val userLoc = userLocation
        if (userLoc == null) {
            val sorted = projects.sortedBy { it.title.lowercase(Locale.getDefault()) }
            return sorted to emptyMap()
        }

        val distances = mutableMapOf<String, Double>()
        val comparator = compareBy<FeaturedCommunityProject> { project ->
            val distance = project.location?.let { loc ->
                calculateDistanceMeters(
                    userLoc.latitude,
                    userLoc.longitude,
                    loc.latitude,
                    loc.longitude
                )
            }
            if (distance != null) {
                distances[project.id] = distance
            }
            distance ?: Double.POSITIVE_INFINITY
        }.thenBy { it.title.lowercase(Locale.getDefault()) }

        val sortedProjects = projects.sortedWith(comparator)
        return sortedProjects to distances
    }

    private fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2.0) + cos(radLat1) * cos(radLat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}

private val DefaultHeroStats = CommunityHeroStats(
    activeMonitors = "45,000+",
    countries = "80+",
    citizens = "30K+"
)

data class ProjectDetailUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val detail: FeaturedCommunityProjectDetail? = null,
    val error: Boolean = false,
    val projectTitle: String? = null
)

data class KnowledgeHubUiState(
    val isLoading: Boolean = true,
    val posts: List<BlogPost> = emptyList(),
    val error: Boolean = false,
    val expandedCount: Int = INITIAL_VISIBLE_POSTS,
    val articleState: ArticleUiState = ArticleUiState()
)

data class ArticleUiState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val article: Article? = null,
    val error: Boolean = false,
    val fallbackUrl: String? = null
)

private const val INITIAL_VISIBLE_POSTS = 3
