package com.airgradient.android.utils

import android.content.Context
import com.airgradient.android.R
import com.airgradient.android.domain.models.AQIDisplayUnit

object DisplayUnitResolver {
    fun shortName(context: Context, unit: AQIDisplayUnit): String {
        return when (unit) {
            AQIDisplayUnit.UGM3 -> context.getString(R.string.unit_ugm3)
            AQIDisplayUnit.USAQI -> context.getString(R.string.unit_us_aqi_short)
        }
    }
}
