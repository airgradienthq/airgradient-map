package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.FeaturedCommunityProject
import com.airgradient.android.domain.repositories.CommunityProjectsRepository
import java.util.Locale
import javax.inject.Inject

class GetFeaturedCommunityProjectsUseCase @Inject constructor(
    private val repository: CommunityProjectsRepository
) {
    suspend operator fun invoke(
        locale: Locale,
        category: String?
    ): Result<List<FeaturedCommunityProject>> {
        return repository.getFeaturedProjects(locale, category)
    }
}
