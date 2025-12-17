package com.airgradient.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.airgradient.android.domain.models.Coordinates
import com.airgradient.android.domain.repositories.BookmarkedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * DataStore for bookmarks with proper serialization and versioning
 */
class BookmarksDataStore(private val context: Context) {

    companion object {
        private val Context.bookmarksDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "bookmarks"
        )

        private val BOOKMARKS_DATA = stringPreferencesKey("bookmarks_data_v1")
        private const val SCHEMA_VERSION = 1
    }

    private val dataStore = context.bookmarksDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Get all bookmarks as Flow
     */
    val bookmarks: Flow<List<BookmarkedLocation>> = dataStore.data.map { preferences ->
        val bookmarksJson = preferences[BOOKMARKS_DATA]
        if (bookmarksJson != null) {
            try {
                val serializable = json.decodeFromString<BookmarksData>(bookmarksJson)
                serializable.bookmarks.map { it.toDomain() }
            } catch (e: Exception) {
                // If deserialization fails, return empty list
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Save all bookmarks
     */
    suspend fun saveBookmarks(bookmarks: List<BookmarkedLocation>) {
        val bookmarksData = BookmarksData(
            version = SCHEMA_VERSION,
            bookmarks = bookmarks.map { it.toSerializable() }
        )
        val bookmarksJson = json.encodeToString(bookmarksData)

        dataStore.edit { preferences ->
            preferences[BOOKMARKS_DATA] = bookmarksJson
        }
    }

    /**
     * Add a bookmark
     */
    suspend fun addBookmark(bookmark: BookmarkedLocation) {
        val current = bookmarks.map { it.toMutableList() }
        current.collect { list ->
            if (list.none { it.locationId == bookmark.locationId }) {
                val updated = list + bookmark
                saveBookmarks(updated)
            }
        }
    }

    /**
     * Remove a bookmark by location ID
     */
    suspend fun removeBookmark(locationId: Int) {
        val current = bookmarks.map { it.toMutableList() }
        current.collect { list ->
            val updated = list.filter { it.locationId != locationId }
            saveBookmarks(updated)
        }
    }

    /**
     * Check if a location is bookmarked
     */
    suspend fun isBookmarked(locationId: Int): Boolean {
        var result = false
        bookmarks.collect { list ->
            result = list.any { it.locationId == locationId }
        }
        return result
    }

    /**
     * Clear all bookmarks
     */
    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.remove(BOOKMARKS_DATA)
        }
    }
}

/**
 * Serializable wrapper for bookmarks with versioning
 */
@Serializable
private data class BookmarksData(
    val version: Int,
    val bookmarks: List<SerializableBookmark>
)

/**
 * Serializable bookmark model
 */
@Serializable
private data class SerializableBookmark(
    val locationId: Int,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val addedAt: Long
)

/**
 * Extension to convert domain model to serializable
 */
private fun BookmarkedLocation.toSerializable() = SerializableBookmark(
    locationId = locationId,
    locationName = locationName,
    latitude = coordinates.latitude,
    longitude = coordinates.longitude,
    addedAt = addedAt
)

/**
 * Extension to convert serializable to domain model
 */
private fun SerializableBookmark.toDomain() = BookmarkedLocation(
    locationId = locationId,
    locationName = locationName,
    coordinates = Coordinates(latitude, longitude),
    addedAt = addedAt
)
