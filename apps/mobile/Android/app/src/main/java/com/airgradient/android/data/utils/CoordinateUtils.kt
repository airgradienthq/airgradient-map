package com.airgradient.android.data.utils

import kotlin.math.abs

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

fun sanitizeCoordinates(latitude: Double?, longitude: Double?): Pair<Double, Double>? {
    if (latitude == null || longitude == null) return null

    val latValid = latitude in -MAX_LATITUDE..MAX_LATITUDE
    val lonValid = longitude in -MAX_LONGITUDE..MAX_LONGITUDE

    val swappedLatValid = longitude in -MAX_LATITUDE..MAX_LATITUDE
    val swappedLonValid = latitude in -MAX_LONGITUDE..MAX_LONGITUDE

    return when {
        latValid && lonValid -> latitude to longitude
        swappedLatValid && swappedLonValid -> longitude to latitude
        else -> null
    }
}

