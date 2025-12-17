package com.airgradient.android.data.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class BookmarkedLocation(
    val locationId: Int,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val addedAt: Long = System.currentTimeMillis()
)

@Singleton
class BookmarkService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _bookmarks = MutableStateFlow<List<BookmarkedLocation>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkedLocation>> = _bookmarks.asStateFlow()

    init {
        loadBookmarks()
    }

    private fun loadBookmarks() {
        val json = prefs.getString(BOOKMARKS_KEY, null)
        if (json != null) {
            val type = object : TypeToken<List<BookmarkedLocation>>() {}.type
            _bookmarks.value = gson.fromJson(json, type)
        }
    }

    private fun saveBookmarks() {
        val json = gson.toJson(_bookmarks.value)
        prefs.edit().putString(BOOKMARKS_KEY, json).apply()
    }

    fun isBookmarked(locationId: Int): Boolean {
        return _bookmarks.value.any { it.locationId == locationId }
    }

    fun addBookmark(locationId: Int, locationName: String, latitude: Double, longitude: Double) {
        if (!isBookmarked(locationId)) {
            val bookmark = BookmarkedLocation(
                locationId = locationId,
                locationName = locationName,
                latitude = latitude,
                longitude = longitude
            )
            _bookmarks.value = _bookmarks.value + bookmark
            saveBookmarks()
        }
    }

    fun removeBookmark(locationId: Int) {
        _bookmarks.value = _bookmarks.value.filter { it.locationId != locationId }
        saveBookmarks()
    }

    fun getBookmark(locationId: Int): BookmarkedLocation? {
        return _bookmarks.value.find { it.locationId == locationId }
    }

    fun getAllBookmarks(): List<BookmarkedLocation> {
        return _bookmarks.value
    }

    companion object {
        private const val BOOKMARKS_KEY = "bookmarked_locations"
    }
}