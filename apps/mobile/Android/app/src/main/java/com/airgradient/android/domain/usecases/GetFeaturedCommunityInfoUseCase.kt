package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.FeaturedCommunityInfo
import com.airgradient.android.domain.repositories.FeaturedCommunityRepository
import java.util.Locale
import javax.inject.Inject

class GetFeaturedCommunityInfoUseCase @Inject constructor(
    private val repository: FeaturedCommunityRepository
) {
    suspend operator fun invoke(
        ownerId: Int,
        locale: Locale
    ): Result<FeaturedCommunityInfo?> = repository.getFeaturedCommunityInfo(ownerId, locale)
}
