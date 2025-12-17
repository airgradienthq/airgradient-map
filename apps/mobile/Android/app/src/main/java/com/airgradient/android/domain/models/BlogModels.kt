package com.airgradient.android.domain.models

data class BlogPost(
    val id: String,
    val title: String,
    val summary: String,
    val url: String,
    val publishDate: Long,
    val author: String?,
    val imageUrl: String?,
    val content: String?
)

data class Article(
    val id: String,
    val title: String,
    val author: String?,
    val publishedDate: Long,
    val readingTime: String?,
    val wordCount: Int?,
    val categories: List<String>,
    val heroImageUrl: String?,
    val heroImageAlt: String?,
    val content: String,
    val seoDescription: String?,
    val canonicalUrl: String
)
