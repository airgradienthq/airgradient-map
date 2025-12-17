package com.airgradient.android.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.airgradient.android.domain.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class UserProfilePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "user_profile_preferences"
        private const val KEY_VULNERABLE_GROUPS = "profile_vulnerable_groups"
        private const val KEY_PREEXISTING_CONDITIONS = "profile_preexisting_conditions"
        private const val KEY_EXERCISES_OUTDOORS = "profile_exercises_outdoors"
        private const val KEY_OWNS_EQUIPMENT = "profile_owns_equipment"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val profileState = MutableStateFlow(readProfile())

    fun userProfile(): StateFlow<UserProfile> = profileState.asStateFlow()

    fun updateProfile(transform: (UserProfile) -> UserProfile) {
        val updated = transform(profileState.value)
        saveProfile(updated)
    }

    fun currentProfile(): UserProfile = profileState.value

    private fun readProfile(): UserProfile {
        return UserProfile(
            hasVulnerableGroups = prefs.getBoolean(KEY_VULNERABLE_GROUPS, false),
            hasPreexistingConditions = prefs.getBoolean(KEY_PREEXISTING_CONDITIONS, false),
            exercisesOutdoors = prefs.getBoolean(KEY_EXERCISES_OUTDOORS, false),
            ownsProtectiveEquipment = prefs.getBoolean(KEY_OWNS_EQUIPMENT, false)
        )
    }

    private fun saveProfile(profile: UserProfile) {
        prefs.edit().apply {
            putBoolean(KEY_VULNERABLE_GROUPS, profile.hasVulnerableGroups)
            putBoolean(KEY_PREEXISTING_CONDITIONS, profile.hasPreexistingConditions)
            putBoolean(KEY_EXERCISES_OUTDOORS, profile.exercisesOutdoors)
            putBoolean(KEY_OWNS_EQUIPMENT, profile.ownsProtectiveEquipment)
            apply()
        }
        profileState.value = profile
    }
}
