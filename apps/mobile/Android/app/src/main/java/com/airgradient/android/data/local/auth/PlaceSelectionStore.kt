package com.airgradient.android.data.local.auth

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class PlaceSelectionStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val selectedPlaceIdState = MutableStateFlow(readPersistedPlaceId())

    fun selectedPlaceId(): StateFlow<Int?> = selectedPlaceIdState.asStateFlow()

    fun updateSelectedPlaceId(placeId: Int?) {
        persistPlaceId(placeId)
        selectedPlaceIdState.value = placeId
    }

    fun clear() {
        persistPlaceId(null)
        selectedPlaceIdState.value = null
    }

    private fun readPersistedPlaceId(): Int? {
        val stored = prefs.getInt(KEY_PLACE_ID, INVALID_PLACE_ID)
        return stored.takeIf { it != INVALID_PLACE_ID }
    }

    private fun persistPlaceId(placeId: Int?) {
        prefs.edit().apply {
            if (placeId == null) {
                remove(KEY_PLACE_ID)
            } else {
                putInt(KEY_PLACE_ID, placeId)
            }
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "place_selection_store"
        const val KEY_PLACE_ID = "selected_place_id"
        const val INVALID_PLACE_ID = -1
    }
}
