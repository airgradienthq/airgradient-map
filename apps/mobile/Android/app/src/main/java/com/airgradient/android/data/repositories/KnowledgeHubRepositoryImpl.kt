package com.airgradient.android.data.repositories

import android.util.Xml
import androidx.core.text.HtmlCompat
import com.airgradient.android.domain.models.Article
import com.airgradient.android.domain.models.BlogPost
import com.airgradient.android.domain.repositories.KnowledgeHubRepository
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
class KnowledgeHubRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : KnowledgeHubRepository {

    override suspend fun getLatestPosts(): Result<List<BlogPost>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RSS_URL)
            .header("Accept", "application/xml")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val stream = response.body?.byteStream()
                    ?: return@withContext Result.failure(IllegalStateException("Empty feed body"))

                val posts = stream.use { parseRssFeed(it) }
                    .sortedByDescending { it.publishDate }
                Result.success(posts)
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    override suspend fun getArticle(post: BlogPost): Result<Article> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(deriveArticleUrl(post.url))
            .header("Accept", "application/xml")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val stream = response.body?.byteStream()
                    ?: return@withContext Result.failure(IllegalStateException("Empty article body"))

                val article = stream.use { parseArticle(it, post) }
                Result.success(article)
            }
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private fun parseRssFeed(stream: InputStream): List<BlogPost> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(Xml.FEATURE_RELAXED, true)
        parser.setInput(stream, null)

        val posts = mutableListOf<BlogPost>()
        var eventType = parser.eventType

        var insideItem = false
        var currentTag: String? = null

        var title: String? = null
        var summary: String? = null
        var link: String? = null
        var pubDate: Long = 0L
        var author: String? = null
        var imageUrl: String? = null
        var content: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    if (name == "item") {
                        insideItem = true
                        title = null
                        summary = null
                        link = null
                        pubDate = 0L
                        author = null
                        imageUrl = null
                        content = null
                    } else if (insideItem) {
                        currentTag = name
                        if (name == "media:content" || name == "enclosure") {
                            val urlAttr = parser.getAttributeValue(null, "url")
                            if (!urlAttr.isNullOrBlank()) imageUrl = urlAttr
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideItem && currentTag != null) {
                        val text = parser.text ?: ""
                        when (currentTag) {
                            "title" -> title = text.trim()
                            "description" -> summary = sanitizeHtml(text)
                            "link", "guid" -> if (link.isNullOrBlank()) link = text.trim()
                            "pubdate" -> pubDate = parseRfcDate(text)
                            "author" -> author = text.trim()
                            "content:encoded" -> content = text
                            "image" -> if (imageUrl.isNullOrBlank()) imageUrl = text.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    if (name == "item" && insideItem) {
                        insideItem = false
                        if (!link.isNullOrBlank() && !title.isNullOrBlank()) {
                            val post = BlogPost(
                                id = deriveIdFromUrl(link!!),
                                title = title!!.trim(),
                                summary = summary ?: "",
                                url = link!!.trim(),
                                publishDate = pubDate,
                                author = author,
                                imageUrl = imageUrl,
                                content = content
                            )
                            posts.add(post)
                        }
                        currentTag = null
                    } else if (insideItem) {
                        currentTag = null
                    }
                }
            }
            eventType = parser.next()
        }

        return posts
    }

    private fun parseArticle(stream: InputStream, post: BlogPost): Article {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(Xml.FEATURE_RELAXED, true)
        parser.setInput(stream, null)

        var eventType = parser.eventType
        var currentTag: String? = null

        var title: String? = null
        var author: String? = null
        var published: Long = post.publishDate
        var readingTime: String? = null
        var wordCount: Int? = null
        val categories = mutableListOf<String>()
        var heroImageUrl: String? = post.imageUrl
        var heroImageAlt: String? = null
        var contentHtml: String? = post.content
        var seoDescription: String? = null

        var insideMetadata = false
        var insideCategories = false
        var insideImages = false
        var insideContent = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    currentTag = name
                    when (name) {
                        "metadata" -> insideMetadata = true
                        "categories" -> insideCategories = true
                        "images" -> insideImages = true
                        "content" -> insideContent = true
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (insideMetadata && currentTag != null) {
                        val trimmed = text.trim()
                        when (currentTag) {
                            "title" -> title = trimmed
                            "author" -> author = trimmed
                            "published" -> parseIsoDate(trimmed)?.let { published = it }
                            "readingtime" -> readingTime = trimmed
                            "wordcount" -> wordCount = trimmed.toIntOrNull()
                        }
                    } else if (insideCategories && currentTag == "category") {
                        categories.add(text.trim())
                    } else if (insideImages && currentTag != null) {
                        val trimmed = text.trim()
                        when (currentTag) {
                            "url" -> heroImageUrl = trimmed
                            "alt" -> heroImageAlt = trimmed
                        }
                    } else if (insideContent && currentTag == "html") {
                        contentHtml = text
                    } else if (currentTag == "description") {
                        seoDescription = text.trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.lowercase(Locale.ROOT)
                    when (name) {
                        "metadata" -> insideMetadata = false
                        "categories" -> insideCategories = false
                        "images" -> insideImages = false
                        "content" -> insideContent = false
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        val content = contentHtml?.takeIf { it.isNotBlank() } ?: post.content ?: post.summary

        return Article(
            id = post.id,
            title = title ?: post.title,
            author = author ?: post.author,
            publishedDate = published,
            readingTime = readingTime,
            wordCount = wordCount,
            categories = categories.toList(),
            heroImageUrl = heroImageUrl,
            heroImageAlt = heroImageAlt,
            content = content,
            seoDescription = seoDescription,
            canonicalUrl = post.url
        )
    }

    private fun deriveArticleUrl(postUrl: String): String {
        val trimmed = postUrl.trimEnd('/')
        return "$trimmed/index.xml"
    }

    private fun deriveIdFromUrl(url: String): String {
        return url.trimEnd('/').substringAfterLast('/')
    }

    private fun parseRfcDate(value: String): Long {
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }
    }

    private fun parseIsoDate(value: String): Long? {
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun sanitizeHtml(raw: String?): String {
        return HtmlCompat.fromHtml(raw ?: "", HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    companion object {
        private const val RSS_URL = "https://www.airgradient.com/blog/index.xml"
    }
}
