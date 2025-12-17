package com.airgradient.android.data.repositories

import android.util.Xml
import androidx.core.text.HtmlCompat
import com.airgradient.android.domain.models.FeaturedCommunityInfo
import com.airgradient.android.domain.repositories.FeaturedCommunityRepository
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

@Singleton
class FeaturedCommunityRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : FeaturedCommunityRepository {

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()

    override suspend fun getFeaturedCommunityInfo(
        ownerId: Int,
        locale: Locale
    ): Result<FeaturedCommunityInfo?> = withContext(Dispatchers.IO) {
        if (ownerId <= 0) {
            return@withContext Result.success(null)
        }

        val languageAttempts = buildLanguageAttempts(locale)
        var lastError: Throwable? = null

        for (language in languageAttempts) {
            val cacheKey = CacheKey(ownerId, language ?: "en")
            when (val cached = cache[cacheKey]) {
                is CacheEntry.Data -> return@withContext Result.success(cached.info)
                CacheEntry.Empty -> {
                    if (language == null || language.equals("en", ignoreCase = true)) {
                        return@withContext Result.success(null)
                    } else {
                        continue
                    }
                }
                null -> { /* continue to fetch */ }
            }

            when (val result = fetchFeaturedCommunity(ownerId, language)) {
                is FetchResult.Success -> {
                    cache[cacheKey] = if (result.info != null) {
                        CacheEntry.Data(result.info)
                    } else {
                        CacheEntry.Empty
                    }
                    return@withContext Result.success(result.info)
                }
                FetchResult.NotFound -> {
                    cache[cacheKey] = CacheEntry.Empty
                    if (language == null || language.equals("en", ignoreCase = true)) {
                        return@withContext Result.success(null)
                    }
                }
                is FetchResult.Error -> {
                    lastError = result.throwable
                }
            }
        }

        lastError?.let { return@withContext Result.failure(it) }
        Result.success(null)
    }

    private fun buildLanguageAttempts(locale: Locale): List<String?> {
        val attempts = linkedSetOf<String?>()
        val languageTag = locale.toLanguageTag()
        if (languageTag.isNotBlank() && !languageTag.equals("und", ignoreCase = true) &&
            !languageTag.equals("en", ignoreCase = true)
        ) {
            attempts.add(languageTag)
        }
        val languageCode = locale.language
        if (languageCode.isNotBlank() && !languageCode.equals("en", ignoreCase = true)) {
            attempts.add(languageCode.lowercase(Locale.ROOT))
        }
        attempts.add(null) // English fallback without language prefix
        return attempts.toList()
    }

    private fun fetchFeaturedCommunity(ownerId: Int, language: String?): FetchResult {
        val url = buildFeaturedCommunityUrl(ownerId, language)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/xml")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> FetchResult.NotFound
                    !response.isSuccessful -> FetchResult.Error(
                        IllegalStateException("HTTP ${response.code}")
                    )
                    else -> {
                        val bodyStream = response.body?.byteStream()
                            ?: return@use FetchResult.Error(IllegalStateException("Empty response body"))
                        val info = bodyStream.use { parseFeaturedCommunity(it) }
                        FetchResult.Success(info)
                    }
                }
            }
        } catch (throwable: Throwable) {
            FetchResult.Error(throwable)
        }
    }

    private fun buildFeaturedCommunityUrl(ownerId: Int, language: String?): String {
        val sanitizedId = ownerId.toString()
        return if (language.isNullOrBlank() || language.equals("en", ignoreCase = true)) {
            "https://www.airgradient.com/app-featured-communities/$sanitizedId/index.xml"
        } else {
            val prefix = language.trim('/')
            "https://www.airgradient.com/$prefix/app-featured-communities/$sanitizedId/index.xml"
        }
    }

    private fun parseFeaturedCommunity(stream: InputStream): FeaturedCommunityInfo? {
        val parserFactory = XmlPullParserFactory.newInstance()
        val parser = parserFactory.newPullParser().apply {
            setFeature(Xml.FEATURE_RELAXED, true)
            setInput(stream, null)
        }

        val path = ArrayDeque<String>()
        var title: String? = null
        var subtitle: String? = null
        var externalUrl: String? = null
        var imageUrl: String? = null
        var imageAlt: String? = null
        var videoUrl: String? = null
        val paragraphs = mutableListOf<String>()
        val directContent = StringBuilder()
        var paragraphBuilder: StringBuilder? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.orEmpty()
                    path.addLast(name)
                    if (pathMatches(path, "community", "content", "p")) {
                        paragraphBuilder = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    val rawText = parser.text ?: ""
                    if (rawText.isBlank()) {
                        // Ignore pure whitespace nodes
                    } else if (pathMatches(path, "community", "metadata", "title")) {
                        title = appendText(title, rawText)
                    } else if (pathMatches(path, "community", "metadata", "subtitle")) {
                        subtitle = appendText(subtitle, rawText)
                    } else if (pathMatches(path, "community", "metadata", "externalUrl")) {
                        externalUrl = appendText(externalUrl, rawText)
                    } else if (pathMatches(path, "community", "featuredImage", "url")) {
                        imageUrl = appendText(imageUrl, rawText)
                    } else if (pathMatches(path, "community", "featuredImage", "alt")) {
                        imageAlt = appendText(imageAlt, rawText)
                    } else if (pathMatches(path, "community", "videoUrl")) {
                        videoUrl = appendText(videoUrl, rawText)
                    } else if (pathMatches(path, "community", "content", "p")) {
                        val trimmed = rawText.trim()
                        if (trimmed.isNotEmpty()) {
                            if (paragraphBuilder == null) {
                                paragraphBuilder = StringBuilder()
                            } else if (paragraphBuilder!!.isNotEmpty()) {
                                paragraphBuilder!!.append(' ')
                            }
                            paragraphBuilder!!.append(trimmed)
                        }
                    } else if (pathMatches(path, "community", "content")) {
                        val trimmed = rawText.trim()
                        if (trimmed.isNotEmpty()) {
                            if (directContent.isNotEmpty()) {
                                directContent.append('\n')
                            }
                            directContent.append(trimmed)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (pathMatches(path, "community", "content", "p")) {
                        val paragraph = paragraphBuilder?.toString()?.trim()
                        if (!paragraph.isNullOrEmpty()) {
                            paragraphs.add(paragraph)
                        }
                        paragraphBuilder = null
                    }
                    if (path.isNotEmpty()) {
                        path.removeLast()
                    }
                }
            }
            eventType = parser.next()
        }

        val normalizedTitle = normalize(title) ?: return null
        val normalizedContent = buildContent(paragraphs, directContent.toString()) ?: return null

        return FeaturedCommunityInfo(
            title = normalizedTitle,
            subtitle = normalize(subtitle),
            content = normalizedContent,
            externalUrl = normalize(externalUrl),
            featuredImageUrl = normalize(imageUrl),
            featuredImageAlt = normalize(imageAlt),
            videoUrl = normalize(videoUrl)
        )
    }

    private fun appendText(current: String?, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return current ?: ""
        return if (current.isNullOrEmpty()) trimmed else current + trimmed
    }

    private fun normalize(value: String?): String? {
        val trimmed = value?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun buildContent(paragraphs: List<String>, directContent: String): String? {
        val html = when {
            paragraphs.isNotEmpty() -> paragraphs.joinToString(separator = "") { paragraph ->
                "<p>$paragraph</p>"
            }
            directContent.isNotBlank() -> "<p>${directContent.trim()}</p>"
            else -> return null
        }

        val parsed = runCatching {
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        }.getOrElse {
            html.replace("<[^>]+>".toRegex(), "").trim()
        }

        return parsed.takeIf { it.isNotBlank() }
    }

    private fun pathMatches(path: ArrayDeque<String>, vararg expected: String): Boolean {
        if (path.size != expected.size) return false
        val iterator = path.iterator()
        expected.forEach { target ->
            if (!iterator.hasNext()) return false
            val segment = iterator.next()
            if (!segment.equals(target, ignoreCase = true)) return false
        }
        return true
    }

    private data class CacheKey(val ownerId: Int, val language: String)

    private sealed interface CacheEntry {
        data class Data(val info: FeaturedCommunityInfo) : CacheEntry
        data object Empty : CacheEntry
    }

    private sealed interface FetchResult {
        data class Success(val info: FeaturedCommunityInfo?) : FetchResult
        data class Error(val throwable: Throwable) : FetchResult
        data object NotFound : FetchResult
    }
}
