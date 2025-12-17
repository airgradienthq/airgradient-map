package com.airgradient.android.data.repositories

import com.airgradient.android.data.local.datastore.BookmarksDataStore
import com.airgradient.android.data.local.migrations.SharedPreferencesToDataStore
import com.airgradient.android.data.services.AirQualityApiService
import com.airgradient.android.domain.models.AirQualityMeasurement
import com.airgradient.android.domain.models.Coordinates
import com.airgradient.android.domain.models.Location
import com.airgradient.android.domain.repositories.BookmarkRepository
import com.airgradient.android.domain.repositories.BookmarkedLocation
import com.airgradient.android.domain.repositories.BookmarkedLocationWithData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of BookmarkRepository using DataStore
 * All operations run on IO dispatcher
 */
@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarksDataStore: BookmarksDataStore,
    private val airQualityApiService: AirQualityApiService,
    private val migration: SharedPreferencesToDataStore
) : BookmarkRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Migrate bookmarks on initialization
        scope.launch {
            migration.migrateBookmarks()
        }
    }

    override fun getAllBookmarks(): Flow<List<BookmarkedLocationWithData>> {
        return bookmarksDataStore.bookmarks.map { bookmarks ->
            bookmarks.map { bookmark ->
                // For now, return without current data
                // Fetching current data for all bookmarks would be expensive
                BookmarkedLocationWithData(
                    bookmark = bookmark,
                    currentMeasurement = null,
                    lastUpdate = null
                )
            }
        }
    }

    override suspend fun isBookmarked(locationId: Int): Boolean {
        return bookmarksDataStore.bookmarks.first().any { it.locationId == locationId }
    }

    override suspend fun addBookmark(location: Location): Result<Unit> {
        return try {
            val resolvedName = resolveLocationName(location)
            val bookmark = BookmarkedLocation(
                locationId = location.id,
                locationName = resolvedName,
                coordinates = location.coordinates,
                addedAt = System.currentTimeMillis()
            )

            val currentBookmarks = bookmarksDataStore.bookmarks.first().toMutableList()
            if (currentBookmarks.none { it.locationId == location.id }) {
                currentBookmarks.add(bookmark)
                bookmarksDataStore.saveBookmarks(currentBookmarks)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun resolveLocationName(location: Location): String {
        val initialName = location.name.trim()
        if (initialName.isNotEmpty()) return initialName

        val apiName = try {
            val response = airQualityApiService.getLocationInfo(location.id)
            if (response.isSuccessful) {
                response.body()?.locationName?.orEmpty()?.trim()
            } else null
        } catch (_: Exception) {
            null
        }

        return apiName?.takeIf { it.isNotEmpty() } ?: location.displayName
    }

    override suspend fun removeBookmark(locationId: Int): Result<Unit> {
        return try {
            val currentBookmarks = bookmarksDataStore.bookmarks.first()
            val updatedBookmarks = currentBookmarks.filter { it.locationId != locationId }
            bookmarksDataStore.saveBookmarks(updatedBookmarks)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getBookmarkWithCurrentData(locationId: Int): Result<BookmarkedLocationWithData> {
        return try {
            val bookmark = bookmarksDataStore.bookmarks.first()
                .find { it.locationId == locationId }
                ?: return Result.failure(Exception("Bookmark not found"))

            // Fetch current measurements
            val response = airQualityApiService.getCurrentMeasurements(locationId)

            val measurement = if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                val parsedTimestamp = parseTimestamp(data.measuredAt ?: data.timestamp)

                // Only create measurement if we have a valid timestamp
                if (parsedTimestamp != null) {
                    AirQualityMeasurement(
                        pm25 = data.pm25,
                        pm10 = data.pm10,
                        co2 = data.rco2,
                        temperature = data.atmp,
                        humidity = data.rhum,
                        timestamp = parsedTimestamp,
                        measurementType = com.airgradient.android.domain.models.MeasurementType.PM25
                    )
                } else {
                    null
                }
            } else {
                null
            }

            // Use measurement timestamp if available, otherwise null
            val lastUpdate = measurement?.let {
                java.time.ZoneOffset.UTC.let { offset ->
                    it.timestamp.toInstant(offset).toEpochMilli()
                }
            }

            Result.success(
                BookmarkedLocationWithData(
                    bookmark = bookmark,
                    currentMeasurement = measurement,
                    lastUpdate = lastUpdate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTimestamp(timestampStr: String?): java.time.LocalDateTime? {
        if (timestampStr.isNullOrBlank()) return null

        // Try multiple timestamp formats (same as AirQualityRepositoryImpl)
        val parsers = listOf<(String) -> java.time.LocalDateTime>(
            { java.time.OffsetDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime() },
            { java.time.Instant.parse(it).atOffset(java.time.ZoneOffset.UTC).toLocalDateTime() },
            { java.time.OffsetDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME).toLocalDateTime() },
            { java.time.LocalDateTime.parse(it, java.time.format.DateTimeFormatter.ISO_DATE_TIME) }
        )

        parsers.forEach { parser ->
            try {
                return parser(timestampStr)
            } catch (_: Exception) {
                // Try next parser
            }
        }

        return null
    }
}
