package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.Location
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for bookmark management
 */
interface BookmarkRepository {

    /**
     * Get all bookmarked locations
     */
    fun getAllBookmarks(): Flow<List<BookmarkedLocationWithData>>

    /**
     * Check if a location is bookmarked
     */
    suspend fun isBookmarked(locationId: Int): Boolean

    /**
     * Add a location to bookmarks
     */
    suspend fun addBookmark(location: Location): Result<Unit>

    /**
     * Remove a location from bookmarks
     */
    suspend fun removeBookmark(locationId: Int): Result<Unit>

    /**
     * Get bookmark details with current air quality data
     */
    suspend fun getBookmarkWithCurrentData(locationId: Int): Result<BookmarkedLocationWithData>
}

/**
 * Domain model for bookmarked location
 */
data class BookmarkedLocation(
    val locationId: Int,
    val locationName: String,
    val coordinates: com.airgradient.android.domain.models.Coordinates,
    val addedAt: Long
)

/**
 * Bookmarked location enriched with current data
 */
data class BookmarkedLocationWithData(
    val bookmark: BookmarkedLocation,
    val currentMeasurement: com.airgradient.android.domain.models.AirQualityMeasurement?,
    val lastUpdate: Long?
)