package com.airgradient.android.ui.locationdetail.Utils

import com.airgradient.android.data.models.AQICategory
import com.airgradient.android.domain.models.AirQualityInsightKey
import com.airgradient.android.domain.models.UserProfile
import java.util.LinkedHashSet

object AirQualityInsightsCalculator {

    fun calculate(pm25: Double?, profile: UserProfile): AirQualityInsightResult {
        if (!isValidPmValue(pm25)) {
            return AirQualityInsightResult(
                actions = emptyList(),
                accentColor = null,
                mascotAssetName = DEFAULT_MASCOT,
                hasValidData = false
            )
        }

        val category = AQICategory.fromPM25(pm25!!)
        val combinedActions = LinkedHashSet<AirQualityInsightKey>().apply {
            baseActions[category].orEmpty().forEach { add(it) }
            profileActions(category, profile).forEach { add(it) }
        }.toList()

        val mascotResolution = resolveMascotResolution(category, combinedActions)
        val orderedActions = reorderActions(combinedActions, mascotResolution.highlightedKeys)

        return AirQualityInsightResult(
            actions = orderedActions,
            accentColor = category.colorHex,
            mascotAssetName = mascotResolution.assetName,
            hasValidData = orderedActions.isNotEmpty()
        )
    }

    private fun profileActions(category: AQICategory, profile: UserProfile): List<AirQualityInsightKey> {
        val rules = profileRules[category] ?: emptyMap()
        val actions = mutableListOf<AirQualityInsightKey>()

        rules.forEach { (flag, keys) ->
            if (profile.matches(flag)) {
                actions.addAll(keys)
            }
        }

        return actions
    }

    private fun UserProfile.matches(flag: ProfileFlag): Boolean {
        return when (flag) {
            ProfileFlag.HAS_VULNERABLE_GROUPS -> hasVulnerableGroups
            ProfileFlag.HAS_PREEXISTING_CONDITIONS -> hasPreexistingConditions
            ProfileFlag.EXERCISES_OUTDOORS -> exercisesOutdoors
            ProfileFlag.OWNS_PROTECTIVE_EQUIPMENT -> ownsProtectiveEquipment
        }
    }

    private fun resolveMascotResolution(
        category: AQICategory,
        actions: List<AirQualityInsightKey>
    ): MascotResolution {
        if (actions.isEmpty()) {
            return MascotResolution(categoryFallbackMascot(category), emptySet())
        }

        val keySet = actions.toSet()

        categorySpecificResolution(category, keySet)?.let { return it }

        val enjoyMatches = keySet.intersect(enjoyWeatherActions)
        if (enjoyMatches.isNotEmpty()) {
            return MascotResolution("mascot-enjoy-weather", enjoyMatches)
        }

        val maskMatches = keySet.intersect(maskActions)
        if (maskMatches.isNotEmpty()) {
            return MascotResolution("mascot-wear-mask", maskMatches)
        }

        val babyMatches = keySet.intersect(babyActions)
        if (babyMatches.isNotEmpty()) {
            return MascotResolution("mascot-baby", babyMatches)
        }

        val doctorMatches = keySet.intersect(doctorActions)
        if (doctorMatches.isNotEmpty()) {
            return MascotResolution("mascot-doctor", doctorMatches)
        }

        val stayIndoorsMatches = keySet.intersect(stayIndoorsActions)
        if (stayIndoorsMatches.isNotEmpty()) {
            return MascotResolution("mascot-stay-indoors", stayIndoorsMatches)
        }

        if (keySet.contains(AirQualityInsightKey.REDUCE_EXERTION) &&
            keySet.contains(AirQualityInsightKey.MOVE_WORKOUT_INDOORS)
        ) {
            return MascotResolution(
                assetName = "mascot-sleep",
                highlightedKeys = setOf(
                    AirQualityInsightKey.REDUCE_EXERTION,
                    AirQualityInsightKey.MOVE_WORKOUT_INDOORS
                )
            )
        }

        val exertionMatches = keySet.intersect(exertionActions)
        if (exertionMatches.isNotEmpty()) {
            return MascotResolution("mascot-coffee", exertionMatches)
        }

        return MascotResolution(categoryFallbackMascot(category), emptySet())
    }

    private fun reorderActions(
        actions: List<AirQualityInsightKey>,
        highlighted: Set<AirQualityInsightKey>
    ): List<AirQualityInsightKey> {
        if (highlighted.isEmpty()) return actions

        val highlightedActions = actions.filter { highlighted.contains(it) }
        val remainingActions = actions.filterNot { highlighted.contains(it) }
        return highlightedActions + remainingActions
    }

    private fun isValidPmValue(pm25: Double?): Boolean {
        return pm25 != null && pm25.isFinite() && pm25 >= 0.0
    }

    private const val DEFAULT_MASCOT = "mascot-idea"

    private fun categoryFallbackMascot(category: AQICategory): String {
        return when (category) {
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> "mascot-baby"
            AQICategory.UNHEALTHY -> "mascot-doctor"
            AQICategory.VERY_UNHEALTHY -> "mascot-stay-indoors"
            AQICategory.HAZARDOUS -> "mascot-wear-mask"
            else -> DEFAULT_MASCOT
        }
    }

    private fun categorySpecificResolution(
        category: AQICategory,
        keySet: Set<AirQualityInsightKey>
    ): MascotResolution? {
        return when (category) {
            AQICategory.UNHEALTHY_FOR_SENSITIVE -> {
                val matches = keySet.intersect(babyActions)
                MascotResolution("mascot-baby", matches)
            }
            AQICategory.UNHEALTHY -> {
                val matches = keySet.intersect(doctorActions)
                MascotResolution("mascot-doctor", matches)
            }
            AQICategory.VERY_UNHEALTHY -> {
                val matches = keySet.intersect(stayIndoorsActions)
                MascotResolution("mascot-stay-indoors", matches)
            }
            AQICategory.HAZARDOUS -> {
                val matches = keySet.intersect(maskActions)
                MascotResolution("mascot-wear-mask", matches)
            }
            else -> null
        }
    }

    private val baseActions = mapOf(
        AQICategory.GOOD to listOf(
            AirQualityInsightKey.ENJOY_OUTDOORS,
            AirQualityInsightKey.VENTILATE_HOME
        ),
        AQICategory.MODERATE to listOf(AirQualityInsightKey.REDUCE_EXERTION),
        AQICategory.UNHEALTHY_FOR_SENSITIVE to listOf(AirQualityInsightKey.REDUCE_EXERTION),
        AQICategory.UNHEALTHY to listOf(
            AirQualityInsightKey.CLOSE_WINDOWS,
            AirQualityInsightKey.REDUCE_EXERTION
        ),
        AQICategory.VERY_UNHEALTHY to listOf(
            AirQualityInsightKey.STAY_INDOORS,
            AirQualityInsightKey.CLOSE_WINDOWS
        ),
        AQICategory.HAZARDOUS to listOf(
            AirQualityInsightKey.STAY_INDOORS,
            AirQualityInsightKey.CLOSE_WINDOWS
        )
    )

    private val profileRules = mapOf(
        AQICategory.GOOD to emptyMap<ProfileFlag, List<AirQualityInsightKey>>(),
        AQICategory.MODERATE to mapOf(
            ProfileFlag.HAS_PREEXISTING_CONDITIONS to listOf(AirQualityInsightKey.MONITOR_SYMPTOMS)
        ),
        AQICategory.UNHEALTHY_FOR_SENSITIVE to mapOf(
            ProfileFlag.HAS_PREEXISTING_CONDITIONS to listOf(AirQualityInsightKey.MONITOR_SYMPTOMS),
            ProfileFlag.HAS_VULNERABLE_GROUPS to listOf(AirQualityInsightKey.PROTECT_VULNERABLE),
            ProfileFlag.EXERCISES_OUTDOORS to listOf(AirQualityInsightKey.MOVE_WORKOUT_INDOORS),
            ProfileFlag.OWNS_PROTECTIVE_EQUIPMENT to listOf(AirQualityInsightKey.RUN_PURIFIER)
        ),
        AQICategory.UNHEALTHY to mapOf(
            ProfileFlag.HAS_PREEXISTING_CONDITIONS to listOf(AirQualityInsightKey.STAY_INDOORS),
            ProfileFlag.EXERCISES_OUTDOORS to listOf(AirQualityInsightKey.MOVE_WORKOUT_INDOORS),
            ProfileFlag.OWNS_PROTECTIVE_EQUIPMENT to listOf(
                AirQualityInsightKey.RUN_PURIFIER,
                AirQualityInsightKey.WEAR_N95
            ),
            ProfileFlag.HAS_VULNERABLE_GROUPS to listOf(AirQualityInsightKey.PROTECT_VULNERABLE)
        ),
        AQICategory.VERY_UNHEALTHY to mapOf(
            ProfileFlag.OWNS_PROTECTIVE_EQUIPMENT to listOf(
                AirQualityInsightKey.RUN_PURIFIER,
                AirQualityInsightKey.WEAR_N95
            )
        ),
        AQICategory.HAZARDOUS to mapOf(
            ProfileFlag.OWNS_PROTECTIVE_EQUIPMENT to listOf(
                AirQualityInsightKey.RUN_PURIFIER,
                AirQualityInsightKey.WEAR_N95
            )
        )
    )

    private val enjoyWeatherActions = setOf(
        AirQualityInsightKey.ENJOY_OUTDOORS,
        AirQualityInsightKey.VENTILATE_HOME
    )

    private val maskActions = setOf(
        AirQualityInsightKey.WEAR_N95,
        AirQualityInsightKey.RUN_PURIFIER
    )

    private val babyActions = setOf(AirQualityInsightKey.PROTECT_VULNERABLE)
    private val doctorActions = setOf(AirQualityInsightKey.MONITOR_SYMPTOMS)
    private val stayIndoorsActions = setOf(
        AirQualityInsightKey.STAY_INDOORS,
        AirQualityInsightKey.CLOSE_WINDOWS
    )

    private val exertionActions = setOf(
        AirQualityInsightKey.REDUCE_EXERTION,
        AirQualityInsightKey.MOVE_WORKOUT_INDOORS
    )

    private enum class ProfileFlag {
        HAS_VULNERABLE_GROUPS,
        HAS_PREEXISTING_CONDITIONS,
        EXERCISES_OUTDOORS,
        OWNS_PROTECTIVE_EQUIPMENT
    }

    private data class MascotResolution(
        val assetName: String,
        val highlightedKeys: Set<AirQualityInsightKey>
    )
}

data class AirQualityInsightResult(
    val actions: List<AirQualityInsightKey>,
    val accentColor: String?,
    val mascotAssetName: String,
    val hasValidData: Boolean
)
