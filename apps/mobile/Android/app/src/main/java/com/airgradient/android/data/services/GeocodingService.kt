package com.airgradient.android.data.services

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

// Nominatim API Models
data class NominatimPlace(
    @SerializedName("place_id") val placeId: Long,
    @SerializedName("licence") val licence: String?,
    @SerializedName("osm_type") val osmType: String?,
    @SerializedName("osm_id") val osmId: Long?,
    @SerializedName("lat") val latitude: String,
    @SerializedName("lon") val longitude: String,
    @SerializedName("class") val placeClass: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("place_rank") val placeRank: Int?,
    @SerializedName("importance") val importance: Double?,
    @SerializedName("addresstype") val addressType: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("address") val address: NominatimAddress?,
    @SerializedName("boundingbox") val boundingBox: List<String>?
) {
    fun toSearchResult(): SearchResult {
        // Better classification logic based on Nominatim's place types and ranks
        val locationType = when {
            // Countries
            placeClass == "place" && type == "country" -> LocationType.COUNTRY
            placeClass == "boundary" && type == "administrative" && placeRank in 3..4 -> LocationType.COUNTRY

            // Cities, towns, villages
            placeClass == "place" && type in listOf("city", "town", "village", "hamlet") -> LocationType.CITY
            placeClass == "boundary" && type == "administrative" && placeRank in 8..16 -> LocationType.CITY
            addressType == "city" -> LocationType.CITY
            addressType == "town" -> LocationType.CITY
            addressType == "village" -> LocationType.CITY
            addressType == "municipality" -> LocationType.CITY

            // Capitals and major cities
            placeClass == "place" && type == "capital" -> LocationType.CITY
            placeClass == "place" && type == "state" -> LocationType.CITY

            // Skip everything else (POIs, roads, etc.)
            else -> LocationType.POI
        }

        // Better name extraction
        val mainName = when {
            // Use the specific name if available
            !name.isNullOrBlank() -> name
            // For cities, use city/town/village from address
            !address?.city.isNullOrBlank() -> address?.city
            !address?.town.isNullOrBlank() -> address?.town
            !address?.village.isNullOrBlank() -> address?.village
            // Fallback to first part of display name
            else -> displayName.split(",").firstOrNull()?.trim()
        } ?: "Unknown"

        // Better subtitle extraction
        val subtitle = when (locationType) {
            LocationType.COUNTRY -> "Country"
            LocationType.CITY -> {
                // For cities, show the country (and state if available)
                val country = address?.country
                val state = address?.state
                when {
                    !country.isNullOrBlank() && !state.isNullOrBlank() && state != mainName ->
                        "$state, $country"
                    !country.isNullOrBlank() && country != mainName ->
                        country
                    else ->
                        displayName.split(",").drop(1).take(2).joinToString(", ").trim()
                }
            }
            else -> displayName.split(",").drop(1).take(2).joinToString(", ").trim()
        }

        return SearchResult(
            name = mainName,
            subtitle = subtitle.ifBlank { null },
            latitude = latitude.toDoubleOrNull() ?: 0.0,
            longitude = longitude.toDoubleOrNull() ?: 0.0,
            type = locationType,
            locationId = null,
            isCluster = false,
            pm25Value = null,
            importance = importance
        )
    }
}

data class NominatimAddress(
    @SerializedName("city") val city: String?,
    @SerializedName("town") val town: String?,
    @SerializedName("village") val village: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("country_code") val countryCode: String?,
    @SerializedName("postcode") val postcode: String?,
    @SerializedName("suburb") val suburb: String?,
    @SerializedName("road") val road: String?
)

// Nominatim API Interface
interface NominatimApiService {
    @GET("search")
    suspend fun searchLocations(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("accept-language") language: String = "en",
        // Filter for cities and countries primarily
        @Query("featuretype") featureType: String? = "settlement,country",
        // Additional filters to prioritize settlements and countries
        @Query("extratags") extraTags: Int = 0,
        @Query("namedetails") nameDetails: Int = 1,
        // Dedupe results
        @Query("dedupe") dedupe: Int = 1
    ): List<NominatimPlace>
}