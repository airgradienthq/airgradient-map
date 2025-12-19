package com.airgradient.android.domain.models

enum class AirQualityInsightKey {
    ENJOY_OUTDOORS,
    VENTILATE_HOME,
    REDUCE_EXERTION,
    MOVE_WORKOUT_INDOORS,
    STAY_INDOORS,
    CLOSE_WINDOWS,
    RUN_PURIFIER,
    WEAR_N95,
    MONITOR_SYMPTOMS,
    PROTECT_VULNERABLE
}

data class RecommendationAction(
    val key: AirQualityInsightKey,
    val title: String,
    val message: String
)

data class UserProfile(
    val hasVulnerableGroups: Boolean = false,
    val hasPreexistingConditions: Boolean = false,
    val exercisesOutdoors: Boolean = false,
    val ownsProtectiveEquipment: Boolean = false
)
