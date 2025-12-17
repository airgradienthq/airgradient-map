package com.airgradient.android.ui.locationdetail.Views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airgradient.android.data.models.LocationDetail
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.ui.shared.Views.AQIBanner
import com.airgradient.android.ui.shared.Views.MascotBanner

@Composable
fun LocationMascotBanner(
    location: LocationDetail,
    modifier: Modifier = Modifier
) {
    MascotBanner(
        locationName = location.name,
        pm25 = location.currentPM25,
        modifier = modifier
    )
}

@Composable
fun LocationAQIBanner(
    location: LocationDetail,
    measurementType: MeasurementType = MeasurementType.PM25,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    val value = when (measurementType) {
        MeasurementType.PM25 -> location.currentPM25
        MeasurementType.CO2 -> location.currentCO2 ?: 0.0
    }

    AQIBanner(
        value = value,
        measurementType = measurementType,
        displayUnit = displayUnit,
        lastUpdate = location.lastUpdated,
        modifier = modifier
    )
}

@Composable
fun LocationHeaderCard(
    location: LocationDetail,
    measurementType: MeasurementType = MeasurementType.PM25,
    displayUnit: AQIDisplayUnit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (measurementType == MeasurementType.PM25) {
            LocationMascotBanner(
                location = location
            )
        }

        LocationAQIBanner(
            location = location,
            measurementType = measurementType,
            displayUnit = displayUnit
        )
    }
}
