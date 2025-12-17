package com.airgradient.android.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "agmap_preferences"
        private const val KEY_LAST_MAP_LATITUDE = "last_map_latitude"
        private const val KEY_LAST_MAP_LONGITUDE = "last_map_longitude"
        private const val KEY_LAST_MAP_ZOOM = "last_map_zoom"
        private const val KEY_HAS_SAVED_POSITION = "has_saved_position"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchComplete() {
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    fun saveMapPosition(latitude: Double, longitude: Double, zoom: Double) {
        prefs.edit().apply {
            putFloat(KEY_LAST_MAP_LATITUDE, latitude.toFloat())
            putFloat(KEY_LAST_MAP_LONGITUDE, longitude.toFloat())
            putFloat(KEY_LAST_MAP_ZOOM, zoom.toFloat())
            putBoolean(KEY_HAS_SAVED_POSITION, true)
            apply()
        }
    }

    fun getLastMapPosition(): MapPosition? {
        if (!prefs.getBoolean(KEY_HAS_SAVED_POSITION, false)) {
            return null
        }

        return MapPosition(
            latitude = prefs.getFloat(KEY_LAST_MAP_LATITUDE, 0f).toDouble(),
            longitude = prefs.getFloat(KEY_LAST_MAP_LONGITUDE, 0f).toDouble(),
            zoom = prefs.getFloat(KEY_LAST_MAP_ZOOM, 10f).toDouble()
        )
    }

    fun clearMapPosition() {
        prefs.edit().apply {
            remove(KEY_LAST_MAP_LATITUDE)
            remove(KEY_LAST_MAP_LONGITUDE)
            remove(KEY_LAST_MAP_ZOOM)
            remove(KEY_HAS_SAVED_POSITION)
            apply()
        }
    }

    fun saveRecentSearch(name: String, subtitle: String?, latitude: Double, longitude: Double) {
        val recentSearches = getRecentSearches().toMutableList()

        // Remove if already exists to avoid duplicates
        recentSearches.removeAll { it.name == name }

        // Add to beginning
        recentSearches.add(0, RecentSearch(name, subtitle, latitude, longitude))

        // Convert to JSON string (no limit)
        val json = recentSearches.joinToString("|") { search ->
            "${search.name}::${search.subtitle ?: ""}::${search.latitude}::${search.longitude}"
        }

        prefs.edit().putString(KEY_RECENT_SEARCHES, json).apply()
    }

    fun getRecentSearches(): List<RecentSearch> {
        val json = prefs.getString(KEY_RECENT_SEARCHES, "") ?: ""
        if (json.isEmpty()) return emptyList()

        return json.split("|").mapNotNull { item ->
            val parts = item.split("::")
            if (parts.size >= 4) {
                RecentSearch(
                    name = parts[0],
                    subtitle = parts[1].takeIf { it.isNotEmpty() },
                    latitude = parts[2].toDoubleOrNull() ?: 0.0,
                    longitude = parts[3].toDoubleOrNull() ?: 0.0
                )
            } else null
        }
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }
}

data class MapPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

data class RecentSearch(
    val name: String,
    val subtitle: String?,
    val latitude: Double,
    val longitude: Double
)