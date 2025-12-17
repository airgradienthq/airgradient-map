package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.FeaturedCommunityInfo
import java.util.Locale

interface FeaturedCommunityRepository {
    suspend fun getFeaturedCommunityInfo(
        ownerId: Int,
        locale: Locale
    ): Result<FeaturedCommunityInfo?>
}
