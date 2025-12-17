package com.airgradient.android.domain.usecases

import com.airgradient.android.data.models.LocationDetail
import com.airgradient.android.domain.models.Coordinates
import com.airgradient.android.domain.models.Location
import com.airgradient.android.domain.repositories.BookmarkRepository
import javax.inject.Inject

class ToggleBookmarkUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) {
    suspend operator fun invoke(location: LocationDetail, isCurrentlyBookmarked: Boolean): Boolean {
        if (isCurrentlyBookmarked) {
            bookmarkRepository.removeBookmark(location.id)
            return false
        }

        // Convert LocationDetail to Location (only id, name, and coordinates are needed for bookmarks)
        val domainLocation = Location(
            id = location.id,
            name = location.name,
            coordinates = Coordinates(location.latitude, location.longitude),
            currentMeasurement = null,
            sensorInfo = null,
            organizationInfo = null
        )
        bookmarkRepository.addBookmark(domainLocation)
        return true
    }
}

