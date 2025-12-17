package com.airgradient.android.data.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResult(
    val name: String,
    val subtitle: String?,
    val latitude: Double,
    val longitude: Double,
    val type: LocationType,
    val locationId: Int? = null,
    val isCluster: Boolean = false,
    val pm25Value: Double? = null,
    val importance: Double? = null
)

data class SensorLocation(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val pm25: Double?,
    val co2: Double?,
    val isCluster: Boolean,
    val sensorsCount: Int?,
    val ownerName: String?
)

enum class LocationType {
    CITY, COUNTRY, POI, SENSOR
}

@Singleton
class LocationSearchService @Inject constructor(
    private val nominatimApiService: NominatimApiService
) {
    companion object {
        private const val TAG = "LocationSearchService"
    }

    suspend fun search(query: String): List<SearchResult> {
        if (query.length < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching for: $query")

                // Search using Nominatim API - search for cities and countries
                val places = nominatimApiService.searchLocations(
                    query = query,
                    limit = 30  // Get more results to filter from
                )

                Log.d(TAG, "Found ${places.size} results from Nominatim for query: $query")

                // Convert and filter results
                val results = places
                    .mapNotNull { place ->
                        try {
                            val result = place.toSearchResult()
                            // Only include cities and countries, filter out POIs
                            if (result.type == LocationType.CITY || result.type == LocationType.COUNTRY) {
                                Log.d(TAG, "Including: ${result.name} (${result.type})")
                                result
                            } else {
                                Log.d(TAG, "Filtering out POI: ${result.name}")
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting place: ${e.message}")
                            null
                        }
                    }
                    .distinctBy { "${it.name}_${it.subtitle}" } // Remove duplicates based on name and subtitle
                    .take(15) // Limit to 15 results after filtering

                // Sort results by relevance, type, and importance (popularity)
                results.sortedWith(
                    compareBy<SearchResult> {
                        // Exact matches first
                        when {
                            it.name.equals(query, ignoreCase = true) -> 0
                            it.name.startsWith(query, ignoreCase = true) -> 1
                            else -> 2
                        }
                    }.thenBy {
                        // Then prioritize by type (countries before cities for broad searches)
                        if (query.length <= 3 && it.type == LocationType.COUNTRY) 0 else 1
                    }.thenByDescending {
                        // Then by importance (higher is more popular) - higher importance first
                        it.importance ?: 0.0
                    }.thenBy {
                        // Finally sort by name length (shorter names usually more relevant)
                        it.name.length
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error searching locations: ${e.message}")
                // Return empty list on error - UI will show "No results found"
                emptyList()
            }
        }
    }
}