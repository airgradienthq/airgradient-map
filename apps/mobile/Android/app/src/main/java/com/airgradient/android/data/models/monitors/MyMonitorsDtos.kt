package com.airgradient.android.data.models.monitors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class PlaceDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("temperature_unit") val temperatureUnit: String?,
    @SerializedName("plantower_pm2_correction_algo") val plantowerPm2CorrectionAlgo: String?,
    @SerializedName("permissions") val permissions: PlacePermissionsDto?
)

data class PlacePermissionsDto(
    @SerializedName("settings") val settings: Boolean?
)

data class PlaceLocationDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("place_id") val placeId: Int?,
    @SerializedName("location_type") val locationType: String?,
    @SerializedName("indoor") val indoor: Boolean?,
    @SerializedName("active") val active: Boolean?,
    @SerializedName("offline") val offline: Boolean?,
    @SerializedName("pm02") val pm02: JsonElement?,
    @SerializedName("rco2") val rco2: JsonElement?,
    @SerializedName("tvoc_index") val tvocIndex: JsonElement?,
    @SerializedName("tvocIndex") val tvocIndexCamel: JsonElement?,
    @SerializedName("tvoc") val tvoc: JsonElement?,
    @SerializedName("nox_index") val noxIndex: JsonElement?,
    @SerializedName("noxIndex") val noxIndexCamel: JsonElement?,
    @SerializedName("nox") val nox: JsonElement?,
    @SerializedName("atmp") val temperature: JsonElement?,
    @SerializedName("rhum") val humidity: JsonElement?,
    @SerializedName("current") val current: JsonObject?
)

data class CurrentLocationReadingDto(
    @SerializedName("id") val id: JsonElement?,
    @SerializedName("location_id") val locationId: JsonElement?,
    @SerializedName("locationId") val locationIdCamel: JsonElement?,
    @SerializedName("place_id") val placeId: Int?,
    @SerializedName("pm02") val pm02: JsonElement?,
    @SerializedName("rco2") val rco2: JsonElement?,
    @SerializedName("indoor") val indoor: Boolean?,
    @SerializedName("active") val active: Boolean?,
    @SerializedName("offline") val offline: Boolean?,
    @SerializedName("tvoc_index") val tvocIndex: JsonElement?,
    @SerializedName("tvocIndex") val tvocIndexCamel: JsonElement?,
    @SerializedName("tvoc") val tvoc: JsonElement?,
    @SerializedName("nox_index") val noxIndex: JsonElement?,
    @SerializedName("noxIndex") val noxIndexCamel: JsonElement?,
    @SerializedName("nox") val nox: JsonElement?,
    @SerializedName("atmp") val temperature: JsonElement?,
    @SerializedName("rhum") val humidity: JsonElement?,
    @SerializedName("timestamp") val timestamp: JsonElement?,
    @SerializedName("updated_at") val updatedAt: JsonElement?,
    @SerializedName("date") val date: String?,
    @SerializedName("current") val current: JsonObject?
)
