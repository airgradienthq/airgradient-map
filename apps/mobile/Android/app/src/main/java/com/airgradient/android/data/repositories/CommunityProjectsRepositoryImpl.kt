package com.airgradient.android.data.repositories

import android.util.Xml
import androidx.core.text.HtmlCompat
import com.airgradient.android.domain.models.FeaturedCommunityProject
import com.airgradient.android.domain.models.FeaturedCommunityProjectDetail
import com.airgradient.android.domain.models.ProjectLocation
import com.airgradient.android.domain.repositories.CommunityProjectsRepository
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

@Singleton
class CommunityProjectsRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : CommunityProjectsRepository {

    override suspend fun getFeaturedProjects(
        locale: Locale,
        category: String?
    ): Result<List<FeaturedCommunityProject>> = withContext(Dispatchers.IO) {
        val language = resolveLanguage(locale)

        when (val localizedResult = fetchFeed(language)) {
            is FeedFetchResult.Error -> Result.failure(localizedResult.throwable)
            FeedFetchResult.NotFound -> fetchEnglishAsFallback(category)
            is FeedFetchResult.Success -> {
                val filteredLocalized = filterByCategory(localizedResult.projects, category)
                if (language != "en" && shouldFallbackToEnglish(category, filteredLocalized.size)) {
                    fetchEnglishAsFallback(category)
                } else {
                    Result.success(filteredLocalized)
                }
            }
        }
    }

    override suspend fun getProjectDetail(
        locale: Locale,
        projectId: String,
        projectUrl: String
    ): Result<FeaturedCommunityProjectDetail> = withContext(Dispatchers.IO) {
        val language = resolveLanguage(locale)
        when (val localized = fetchDetail(language, projectId, projectUrl)) {
            is DetailFetchResult.Error -> Result.failure(localized.throwable)
            DetailFetchResult.NotFound -> fetchDetailEnglishFallback(projectId, projectUrl)
            is DetailFetchResult.Success -> Result.success(localized.detail)
        }
    }

    private suspend fun fetchEnglishAsFallback(category: String?): Result<List<FeaturedCommunityProject>> {
        return when (val englishResult = fetchFeed("en")) {
            is FeedFetchResult.Error -> Result.failure(englishResult.throwable)
            FeedFetchResult.NotFound -> Result.success(emptyList())
            is FeedFetchResult.Success -> Result.success(filterByCategory(englishResult.projects, category))
        }
    }

    private suspend fun fetchDetailEnglishFallback(
        projectId: String,
        projectUrl: String
    ): Result<FeaturedCommunityProjectDetail> {
        return when (val english = fetchDetail("en", projectId, projectUrl)) {
            is DetailFetchResult.Error -> Result.failure(english.throwable)
            DetailFetchResult.NotFound -> Result.failure(IllegalStateException("Project not found"))
            is DetailFetchResult.Success -> Result.success(english.detail)
        }
    }

    private suspend fun fetchFeed(language: String): FeedFetchResult = withContext(Dispatchers.IO) {
        val url = buildFeedUrl(language)
        val request = Request.Builder().url(url).header("Accept", "application/xml").build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> FeedFetchResult.NotFound
                    !response.isSuccessful -> FeedFetchResult.Error(
                        IllegalStateException("HTTP ${response.code}")
                    )
                    else -> {
                        val stream = response.body?.byteStream()
                            ?: return@use FeedFetchResult.Error(IllegalStateException("Empty body"))
                        val projects = stream.use { parseFeed(it) }
                        FeedFetchResult.Success(projects)
                    }
                }
            }
        } catch (ex: Exception) {
            FeedFetchResult.Error(ex)
        }
    }

    private fun buildFeedUrl(language: String): String {
        return if (language == "en") {
            "https://www.airgradient.com/app-featured-communities/index.xml"
        } else {
            "https://www.airgradient.com/$language/app-featured-communities/index.xml"
        }
    }

    private fun buildDetailUrl(language: String, projectId: String, fallbackProjectUrl: String? = null): String {
        val fallbackId = fallbackProjectUrl
            ?.trimEnd('/')
            ?.substringAfterLast('/')
            ?.lowercase(Locale.ROOT)
        val sanitizedId = projectId.trim('/').lowercase(Locale.ROOT).ifEmpty { fallbackId ?: projectId }
        return if (language == "en") {
            "https://www.airgradient.com/app-featured-communities/$sanitizedId/index.xml"
        } else {
            "https://www.airgradient.com/$language/app-featured-communities/$sanitizedId/index.xml"
        }
    }

    private fun parseFeed(stream: InputStream): List<FeaturedCommunityProject> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(Xml.FEATURE_RELAXED, true)
        parser.setInput(stream, null)

        val projects = mutableListOf<FeaturedCommunityProject>()
        var eventType = parser.eventType
        var insideItem = false

        var title: String? = null
        var subtitle: String? = null
        var category: String? = null
        var description: String? = null
        var imageUrl: String? = null
        var link: String? = null
        var publishedDate: Long? = null
        var location: ProjectLocation? = null
        var currentTag: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    if (name == "item") {
                        insideItem = true
                        title = null
                        subtitle = null
                        category = null
                        description = null
                        imageUrl = null
                        link = null
                        publishedDate = null
                        location = null
                    } else if (insideItem) {
                        currentTag = name
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideItem && currentTag != null) {
                        val text = parser.text?.trim() ?: ""
                        when (currentTag) {
                            "title" -> title = text
                            "subtitle", "sub-title", "summary" -> subtitle = text
                            "category" -> category = text
                            "description" -> description = text
                            "image" -> imageUrl = text
                            "link" -> link = text
                            "pubdate" -> publishedDate = parseRfcDate(text)
                            "location" -> location = parseLocation(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    if (name == "item" && insideItem) {
                        insideItem = false
                        val project = FeaturedCommunityProject(
                            id = buildId(link, title),
                            title = title ?: "",
                            subtitle = subtitle,
                            description = description,
                            imageUrl = imageUrl ?: "",
                            projectUrl = link ?: "",
                            publishedDate = publishedDate,
                            category = category,
                            location = location
                        )
                        projects.add(project)
                        currentTag = null
                    } else if (insideItem) {
                        currentTag = null
                    }
                }
            }
            eventType = parser.next()
        }

        return projects
    }

    private suspend fun fetchDetail(
        language: String,
        projectId: String,
        fallbackProjectUrl: String
    ): DetailFetchResult = withContext(Dispatchers.IO) {
        val url = buildDetailUrl(language, projectId, fallbackProjectUrl)
        val request = Request.Builder().url(url).header("Accept", "application/xml").build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> DetailFetchResult.NotFound
                    !response.isSuccessful -> DetailFetchResult.Error(IllegalStateException("HTTP ${response.code}"))
                    else -> {
                        val stream = response.body?.byteStream()
                            ?: return@use DetailFetchResult.Error(IllegalStateException("Empty body"))
                        val detail = stream.use { parseDetail(it, projectId, fallbackProjectUrl) }
                        DetailFetchResult.Success(detail)
                    }
                }
            }
        } catch (ex: Exception) {
            DetailFetchResult.Error(ex)
        }
    }

    private fun parseDetail(
        stream: InputStream,
        projectId: String,
        fallbackProjectUrl: String
    ): FeaturedCommunityProjectDetail {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(Xml.FEATURE_RELAXED, true)
        parser.setInput(stream, null)

        var eventType = parser.eventType

        var title: String? = null
        var subtitle: String? = null
        var summary: String? = null
        var featuredImageUrl: String? = null
        var featuredImageAlt: String? = null
        var projectUrl = fallbackProjectUrl
        var externalUrl: String? = null
        var permalink: String? = null
        var videoUrl: String? = null
        var publishedDate: Long? = null
        var category: String? = null
        var location: ProjectLocation? = null
        val contentBuilder = StringBuilder()

        var currentTag: String? = null
        var insideMetadata = false
        var insideFeaturedImage = false
        var insideContent = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    currentTag = name
                    when (name) {
                        "metadata" -> insideMetadata = true
                        "featuredimage" -> insideFeaturedImage = true
                        "content" -> insideContent = true
                        "p", "paragraph" -> if (insideContent && contentBuilder.isNotEmpty()) {
                            contentBuilder.append("\n\n")
                        }
                        "li" -> if (insideContent) {
                            if (contentBuilder.isNotEmpty()) contentBuilder.append('\n')
                            contentBuilder.append("• ")
                        }
                        "br" -> if (insideContent) contentBuilder.append('\n')
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (insideMetadata && currentTag != null) {
                        val trimmed = text.trim()
                        when (currentTag) {
                            "title" -> title = trimmed
                            "subtitle" -> subtitle = trimmed
                            "summary" -> summary = sanitizeHtml(trimmed)
                            "externalurl" -> externalUrl = trimmed
                            "permalink" -> permalink = trimmed
                            "projecturl" -> if (trimmed.isNotBlank()) projectUrl = trimmed
                            "category" -> category = trimmed
                            "location" -> location = parseLocation(trimmed)
                            "pubdate", "publisheddate" -> publishedDate = parseRfcDate(trimmed)
                        }
                    } else if (insideFeaturedImage && currentTag != null) {
                        val trimmed = text.trim()
                        when (currentTag) {
                            "url" -> featuredImageUrl = trimmed
                            "alt" -> featuredImageAlt = trimmed
                        }
                    } else if (insideContent && currentTag != null) {
                        val sanitized = sanitizeHtml(text)
                        if (sanitized.isNotBlank()) {
                            contentBuilder.append(sanitized)
                        }
                    } else if (!insideContent && currentTag == "videourl") {
                        videoUrl = text.trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    when (name) {
                        "metadata" -> insideMetadata = false
                        "featuredimage" -> insideFeaturedImage = false
                        "content" -> insideContent = false
                        "li" -> if (insideContent) contentBuilder.append('\n')
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        val content = contentBuilder.toString().trim()
        val computedSummary = summary?.takeIf { it.isNotBlank() }

        return FeaturedCommunityProjectDetail(
            id = projectId,
            title = title ?: "",
            subtitle = subtitle,
            content = content,
            summary = computedSummary,
            featuredImageUrl = featuredImageUrl,
            featuredImageAlt = featuredImageAlt,
            projectUrl = projectUrl,
            externalUrl = externalUrl,
            permalink = permalink,
            videoUrl = videoUrl,
            publishedDate = publishedDate,
            category = category,
            location = location
        )
    }

    private fun parseRfcDate(value: String): Long? {
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseLocation(value: String): ProjectLocation? {
        val parts = value.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull()
        val lon = parts[1].trim().toDoubleOrNull()
        return if (lat != null && lon != null) ProjectLocation(lat, lon) else null
    }

    private fun filterByCategory(
        projects: List<FeaturedCommunityProject>,
        category: String?
    ): List<FeaturedCommunityProject> {
        if (category.isNullOrBlank()) return projects
        return projects.filter { project ->
            project.category?.equals(category, ignoreCase = true) == true
        }
    }

    private fun shouldFallbackToEnglish(category: String?, resultCount: Int): Boolean {
        val normalized = category?.lowercase(Locale.ROOT)
        return when {
            normalized == null || normalized == "community" -> resultCount < 4
            normalized == "partner" -> resultCount == 0
            else -> false
        }
    }

    private fun resolveLanguage(locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        return when (language) {
            "de", "fr", "es", "th", "lo" -> language
            "la" -> "lo"
            else -> "en"
        }
    }

    private fun buildId(link: String?, title: String?): String {
        if (!link.isNullOrBlank()) {
            val slug = link.trimEnd('/').substringAfterLast('/')
            if (slug.isNotBlank()) return slug
            return link.hashCode().toString()
        }
        if (!title.isNullOrBlank()) {
            return title.lowercase(Locale.ROOT).replace("[^a-z0-9]+".toRegex(), "-")
        }
        return System.currentTimeMillis().toString()
    }

    private sealed class FeedFetchResult {
        data class Success(val projects: List<FeaturedCommunityProject>) : FeedFetchResult()
        data class Error(val throwable: Throwable) : FeedFetchResult()
        object NotFound : FeedFetchResult()
    }

    private sealed class DetailFetchResult {
        data class Success(val detail: FeaturedCommunityProjectDetail) : DetailFetchResult()
        data class Error(val throwable: Throwable) : DetailFetchResult()
        object NotFound : DetailFetchResult()
    }

    private fun sanitizeHtml(raw: String?): String {
        return HtmlCompat.fromHtml(raw ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    }
}
