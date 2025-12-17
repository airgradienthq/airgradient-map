package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.FeaturedCommunityProjectDetail
import com.airgradient.android.domain.repositories.CommunityProjectsRepository
import java.util.Locale
import javax.inject.Inject

class GetFeaturedCommunityProjectDetailUseCase @Inject constructor(
    private val repository: CommunityProjectsRepository
) {
    suspend operator fun invoke(
        locale: Locale,
        projectId: String,
        projectUrl: String
    ): Result<FeaturedCommunityProjectDetail> {
        return repository.getProjectDetail(locale, projectId, projectUrl)
    }
}
