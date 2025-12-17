package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.repositories.BookmarkRepository
import javax.inject.Inject

class GetBookmarkStatusUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    suspend operator fun invoke(locationId: Int): Boolean = bookmarkRepository.isBookmarked(locationId)
}

