package com.airgradient.android.data.local.migrations

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.airgradient.android.data.local.datastore.AppSettingsDataStore
import com.airgradient.android.data.local.datastore.BookmarksDataStore
import com.airgradient.android.domain.models.Coordinates
import com.airgradient.android.domain.repositories.BookmarkedLocation
import com.airgradient.android.domain.repositories.WidgetLocationSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

/**
 * Handles migration from old SharedPreferences to new DataStore
 */
class SharedPreferencesToDataStore(
    private val context: Context,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val bookmarksDataStore: BookmarksDataStore
) {
    companion object {
        private const val TAG = "DataStoreMigration"
        private const val MIGRATION_PREFS = "migration_status"
        private const val KEY_SETTINGS_MIGRATED = "settings_migrated"
        private const val KEY_BOOKMARKS_MIGRATED = "bookmarks_migrated"
    }

    private val migrationPrefs: SharedPreferences =
        context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)

    /**
     * Migrate app settings from SharedPreferences to DataStore
     */
    suspend fun migrateAppSettings() {
        if (migrationPrefs.getBoolean(KEY_SETTINGS_MIGRATED, false)) {
            Log.d(TAG, "Settings already migrated, skipping")
            return
        }

        try {
            val oldPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

            // Check if DataStore already has data (fresh install)
            val currentDisplayUnit = appSettingsDataStore.displayUnit.first()
            val currentWidgetLocation = appSettingsDataStore.widgetLocation.first()

            val hasDataStoreData = currentDisplayUnit != AppSettingsDataStore.DEFAULT_DISPLAY_UNIT ||
                    currentWidgetLocation.locationName != AppSettingsDataStore.DEFAULT_WIDGET_LOCATION_NAME

            if (hasDataStoreData) {
                Log.d(TAG, "DataStore already has data, marking as migrated")
                markSettingsAsMigrated()
                return
            }

            // Migrate display unit
            val displayUnit = oldPrefs.getString("display_unit", null)
            if (displayUnit != null) {
                val parsedUnit = AppSettingsDataStore.parseDisplayUnit(displayUnit)
                appSettingsDataStore.updateDisplayUnit(parsedUnit)
                Log.d(TAG, "Migrated display unit: $displayUnit -> ${parsedUnit.rawValue}")
            }

            // Migrate widget location
            val widgetLocationName = oldPrefs.getString("widget_location", null)
            if (widgetLocationName != null && widgetLocationName != "None") {
                val widgetLocationId = oldPrefs.getInt("widget_location_id", -1)
                val widgetLocationLat = oldPrefs.getFloat("widget_location_lat", 0f).toDouble()
                val widgetLocationLng = oldPrefs.getFloat("widget_location_lng", 0f).toDouble()

                val widgetSettings = WidgetLocationSettings(
                    locationName = widgetLocationName,
                    locationId = widgetLocationId,
                    latitude = widgetLocationLat,
                    longitude = widgetLocationLng
                )

                appSettingsDataStore.updateWidgetLocation(widgetSettings)
                Log.d(TAG, "Migrated widget location: $widgetLocationName")
            }

            markSettingsAsMigrated()
            Log.d(TAG, "Settings migration completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating settings", e)
        }
    }

    /**
     * Migrate bookmarks from SharedPreferences to DataStore
     */
    suspend fun migrateBookmarks() {
        if (migrationPrefs.getBoolean(KEY_BOOKMARKS_MIGRATED, false)) {
            Log.d(TAG, "Bookmarks already migrated, skipping")
            return
        }

        try {
            val oldPrefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
            val gson = Gson()

            // Check if DataStore already has data
            val currentBookmarks = bookmarksDataStore.bookmarks.first()
            if (currentBookmarks.isNotEmpty()) {
                Log.d(TAG, "DataStore already has bookmarks, marking as migrated")
                markBookmarksAsMigrated()
                return
            }

            // Migrate bookmarks from old JSON blob
            val bookmarksJson = oldPrefs.getString("bookmarked_locations", null)
            if (bookmarksJson != null) {
                try {
                    val type = object : TypeToken<List<OldBookmarkedLocation>>() {}.type
                    val oldBookmarks: List<OldBookmarkedLocation> = gson.fromJson(bookmarksJson, type)

                    val newBookmarks = oldBookmarks.map { old ->
                        BookmarkedLocation(
                            locationId = old.locationId,
                            locationName = old.locationName,
                            coordinates = Coordinates(old.latitude, old.longitude),
                            addedAt = old.addedAt
                        )
                    }

                    bookmarksDataStore.saveBookmarks(newBookmarks)
                    Log.d(TAG, "Migrated ${newBookmarks.size} bookmarks")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing old bookmarks JSON", e)
                }
            }

            markBookmarksAsMigrated()
            Log.d(TAG, "Bookmarks migration completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating bookmarks", e)
        }
    }

    /**
     * Run all migrations
     */
    suspend fun migrateAll() {
        migrateAppSettings()
        migrateBookmarks()
    }

    private fun markSettingsAsMigrated() {
        migrationPrefs.edit().putBoolean(KEY_SETTINGS_MIGRATED, true).apply()
    }

    private fun markBookmarksAsMigrated() {
        migrationPrefs.edit().putBoolean(KEY_BOOKMARKS_MIGRATED, true).apply()
    }

    /**
     * Old bookmark model for migration compatibility
     */
    private data class OldBookmarkedLocation(
        val locationId: Int,
        val locationName: String,
        val latitude: Double,
        val longitude: Double,
        val addedAt: Long = System.currentTimeMillis()
    )
}
