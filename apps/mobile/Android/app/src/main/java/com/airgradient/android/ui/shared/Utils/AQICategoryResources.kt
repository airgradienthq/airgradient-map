package com.airgradient.android.ui.shared.Utils

import androidx.annotation.StringRes
import com.airgradient.android.R
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.data.models.AQICategory as LocationAQICategory

@StringRes
fun AQICategory.toDisplayNameRes(): Int = when (this) {
    AQICategory.GOOD -> R.string.aqi_good
    AQICategory.MODERATE -> R.string.aqi_moderate
    AQICategory.UNHEALTHY_FOR_SENSITIVE -> R.string.aqi_unhealthy_for_sensitive
    AQICategory.UNHEALTHY -> R.string.aqi_unhealthy
    AQICategory.VERY_UNHEALTHY -> R.string.aqi_very_unhealthy
    AQICategory.HAZARDOUS -> R.string.aqi_hazardous
}

@StringRes
fun LocationAQICategory.toDisplayNameRes(): Int = when (this) {
    LocationAQICategory.GOOD -> R.string.aqi_good
    LocationAQICategory.MODERATE -> R.string.aqi_moderate
    LocationAQICategory.UNHEALTHY_FOR_SENSITIVE -> R.string.aqi_unhealthy_for_sensitive
    LocationAQICategory.UNHEALTHY -> R.string.aqi_unhealthy
    LocationAQICategory.VERY_UNHEALTHY -> R.string.aqi_very_unhealthy
    LocationAQICategory.HAZARDOUS -> R.string.aqi_hazardous
}

@StringRes
fun aqiCategoryLabelRes(category: String): Int = when (category) {
    "Good" -> R.string.aqi_good
    "Moderate" -> R.string.aqi_moderate
    "Unhealthy for Sensitive Groups" -> R.string.aqi_unhealthy_for_sensitive
    "Unhealthy" -> R.string.aqi_unhealthy
    "Very Unhealthy" -> R.string.aqi_very_unhealthy
    "Hazardous" -> R.string.aqi_hazardous
    else -> R.string.aqi_good
}
