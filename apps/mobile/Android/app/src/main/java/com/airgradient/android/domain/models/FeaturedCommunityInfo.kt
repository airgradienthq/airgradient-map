package com.airgradient.android.domain.models

data class FeaturedCommunityInfo(
    val title: String,
    val subtitle: String?,
    val content: String,
    val externalUrl: String?,
    val featuredImageUrl: String?,
    val featuredImageAlt: String?,
    val videoUrl: String?
)
