package com.airgradient.android.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationMetadataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun playerId(): String? = prefs.getString(KEY_PLAYER_ID, null)

    fun updatePlayerId(playerId: String) {
        prefs.edit().putString(KEY_PLAYER_ID, playerId).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PLAYER_ID).apply()
    }

    private companion object {
        private const val PREFS_NAME = "push_notification_metadata"
        private const val KEY_PLAYER_ID = "player_id"
    }
}
