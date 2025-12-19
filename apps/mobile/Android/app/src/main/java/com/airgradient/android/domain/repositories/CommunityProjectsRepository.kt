package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.FeaturedCommunityProject
import com.airgradient.android.domain.models.FeaturedCommunityProjectDetail
import java.util.Locale

interface CommunityProjectsRepository {
    suspend fun getFeaturedProjects(
        locale: Locale,
        category: String? = null
    ): Result<List<FeaturedCommunityProject>>

    suspend fun getProjectDetail(
        locale: Locale,
        projectId: String,
        projectUrl: String
    ): Result<FeaturedCommunityProjectDetail>
}
