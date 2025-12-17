package com.airgradient.android.domain.models

data class ProjectLocation(
    val latitude: Double,
    val longitude: Double
)

data class FeaturedCommunityProject(
    val id: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val imageUrl: String,
    val projectUrl: String,
    val publishedDate: Long?,
    val category: String?,
    val location: ProjectLocation?
)

data class FeaturedCommunityProjectDetail(
    val id: String,
    val title: String,
    val subtitle: String?,
    val content: String,
    val summary: String?,
    val featuredImageUrl: String?,
    val featuredImageAlt: String?,
    val projectUrl: String,
    val externalUrl: String?,
    val permalink: String?,
    val videoUrl: String?,
    val publishedDate: Long?,
    val category: String?,
    val location: ProjectLocation?
)
