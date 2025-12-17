package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.Location
import com.airgradient.android.domain.repositories.AirQualityRepository
import com.airgradient.android.domain.repositories.BookmarkRepository
import com.airgradient.android.domain.repositories.BookmarkedLocationWithData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Use case for managing bookmarked locations
 * Handles business logic for adding, removing, and retrieving bookmarks with current data
 */
class ManageBookmarksUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val airQualityRepository: AirQualityRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Get all bookmarks with current air quality data
     */
    fun getAllBookmarksWithData(): Flow<List<BookmarkedLocationWithData>> {
        return bookmarkRepository.getAllBookmarks()
    }

    /**
     * Add a location to bookmarks
     */
    suspend fun addBookmark(location: Location): Result<Unit> = withContext(dispatcher) {
        try {
            // Validate location before bookmarking
            if (!location.coordinates.isValid) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot bookmark location with invalid coordinates")
                )
            }

            if (location.name.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot bookmark location without a name")
                )
            }

            bookmarkRepository.addBookmark(location)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a location from bookmarks
     */
    suspend fun removeBookmark(locationId: Int): Result<Unit> = withContext(dispatcher) {
        try {
            if (locationId <= 0) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid location ID")
                )
            }

            bookmarkRepository.removeBookmark(locationId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if a location is bookmarked
     */
    suspend fun isBookmarked(locationId: Int): Boolean = withContext(dispatcher) {
        try {
            bookmarkRepository.isBookmarked(locationId)
        } catch (e: Exception) {
            false // Default to not bookmarked if there's an error
        }
    }

    /**
     * Toggle bookmark status for a location
     */
    suspend fun toggleBookmark(location: Location): Result<Boolean> = withContext(dispatcher) {
        try {
            val isCurrentlyBookmarked = bookmarkRepository.isBookmarked(location.id)

            if (isCurrentlyBookmarked) {
                bookmarkRepository.removeBookmark(location.id).fold(
                    onSuccess = { Result.success(false) },
                    onFailure = { Result.failure(it) }
                )
            } else {
                bookmarkRepository.addBookmark(location).fold(
                    onSuccess = { Result.success(true) },
                    onFailure = { Result.failure(it) }
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get bookmark with current air quality data
     */
    suspend fun getBookmarkWithCurrentData(locationId: Int): Result<BookmarkedLocationWithData> =
        withContext(dispatcher) {
            try {
                bookmarkRepository.getBookmarkWithCurrentData(locationId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

}

/**
 * Criteria for sorting bookmarks
 */
