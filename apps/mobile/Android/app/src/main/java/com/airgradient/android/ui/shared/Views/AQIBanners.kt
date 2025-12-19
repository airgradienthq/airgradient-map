package com.airgradient.android.ui.shared.Views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airgradient.android.R
import com.airgradient.android.data.models.AQIColorPalette
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Reusable Mascot Banner component matching iOS design
 * Used in both map bottom dialog and my locations dialog
 */
@Composable
fun MascotBanner(
    locationName: String,
    pm25: Double,
    modifier: Modifier = Modifier
) {
    val aqiColor = Color(AQIColorPalette.getColorForValue(pm25, MeasurementType.PM25))
    val category = EPAColorCoding.getCategoryForPM25(pm25) ?: AQICategory.GOOD
    val categoryName = stringResource(id = category.stringRes())

    val mascotDrawable = EPAColorCoding.mascotForCategory(category)
    val backgroundDrawable = EPAColorCoding.backgroundForCategory(category)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 220.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = aqiColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background city silhouette
            Image(
                painter = painterResource(id = backgroundDrawable),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            
            // Content overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val locationStyle = when {
                    locationName.length > 28 -> MaterialTheme.typography.titleMedium
                    locationName.length > 20 -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.headlineSmall
                }.copy(fontWeight = FontWeight.Bold, lineHeight = 28.sp)

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    val density = LocalDensity.current
                    val textMeasurer = rememberTextMeasurer()
                    val maxWidthPx = with(density) { maxWidth.toPx() }
                    val measurementConstraints = remember(maxWidthPx) {
                        if (maxWidthPx.isFinite()) {
                            Constraints(maxWidth = maxWidthPx.roundToInt().coerceAtLeast(0))
                        } else {
                            Constraints()
                        }
                    }

                    val candidateSizes = remember {
                        listOf(40.sp, 36.sp, 32.sp, 28.sp, 24.sp, 20.sp, 18.sp, 16.sp)
                    }

                    val categoryFontSize = remember(categoryName, measurementConstraints) {
                        fun fitsSingleLine(size: androidx.compose.ui.unit.TextUnit): Boolean {
                            val result = textMeasurer.measure(
                                text = categoryName,
                                style = TextStyle(
                                    fontSize = size,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = size * 1.1f
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                constraints = measurementConstraints
                            )
                            return !result.didOverflowHeight && !result.didOverflowWidth
                        }

                        fun fitsWithinTwoLines(size: androidx.compose.ui.unit.TextUnit): Boolean {
                            val result = textMeasurer.measure(
                                text = categoryName,
                                style = TextStyle(
                                    fontSize = size,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = size * 1.1f
                                ),
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Clip,
                                constraints = measurementConstraints
                            )
                            return !result.didOverflowHeight && !result.didOverflowWidth
                        }

                        candidateSizes.firstOrNull { fitsSingleLine(it) }
                            ?: candidateSizes.firstOrNull { fitsWithinTwoLines(it) }
                            ?: candidateSizes.last()
                    }
                    val categoryLineHeight = categoryFontSize * 1.1f

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = locationName,
                            style = locationStyle,
                            color = Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = categoryName,
                            fontSize = categoryFontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = categoryLineHeight,
                            color = aqiColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Right side: Mascot character in circle
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(aqiColor.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = mascotDrawable),
                        contentDescription = "AQI Mascot",
                        modifier = Modifier.size(70.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

private fun AQICategory.stringRes(): Int = when (this) {
    AQICategory.GOOD -> R.string.aqi_good
    AQICategory.MODERATE -> R.string.aqi_moderate
    AQICategory.UNHEALTHY_FOR_SENSITIVE -> R.string.aqi_unhealthy_for_sensitive
    AQICategory.UNHEALTHY -> R.string.aqi_unhealthy
    AQICategory.VERY_UNHEALTHY -> R.string.aqi_very_unhealthy
    AQICategory.HAZARDOUS -> R.string.aqi_hazardous
}

/**
 * Reusable AQI Banner component showing PM2.5 values and update time
 * Used in both map bottom dialog and my locations dialog
 */
@Composable
fun AQIBanner(
    value: Double,
    measurementType: MeasurementType = MeasurementType.PM25,
    displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    lastUpdate: String? = null,
    modifier: Modifier = Modifier
) {
    val bannerColor = Color(AQIColorPalette.getColorForValue(value, measurementType))

    val primaryLine: String
    val secondaryLine: String?
    val rightLabelTop: String
    val rightLabelBottom: String

    when (measurementType) {
        MeasurementType.PM25 -> {
            val aqiValue = EPAColorCoding.getAQIFromPM25(value)
            val pmDisplay = stringResource(
                R.string.aqi_banner_secondary_pm25,
                value.coerceAtLeast(0.0)
            )

            if (displayUnit == AQIDisplayUnit.UGM3) {
                primaryLine = pmDisplay
                secondaryLine = stringResource(R.string.aqi_banner_secondary_pm25_us_aqi, aqiValue)
            } else {
                primaryLine = stringResource(R.string.aqi_banner_primary_pm25, aqiValue)
                secondaryLine = pmDisplay
            }
            rightLabelTop = stringResource(R.string.aqi_banner_label_air_quality)
            rightLabelBottom = stringResource(R.string.aqi_banner_label_index)
        }

        MeasurementType.CO2 -> {
            primaryLine = stringResource(R.string.aqi_banner_primary_co2, value)
            secondaryLine = AQIColorPalette.getCategoryForValue(value, MeasurementType.CO2)
            rightLabelTop = stringResource(R.string.aqi_banner_label_co2_top)
            rightLabelBottom = stringResource(R.string.aqi_banner_label_co2_bottom)
        }
    }

    val lastUpdatedText = lastUpdate?.takeIf { it.isNotBlank() }?.let {
        stringResource(R.string.aqi_banner_last_updated, it)
    } ?: stringResource(R.string.aqi_banner_last_updated_recent)

    AQIBannerCard(
        modifier = modifier,
        backgroundColor = bannerColor,
        primaryLine = primaryLine,
        secondaryLine = secondaryLine,
        rightLabelTop = rightLabelTop,
        rightLabelBottom = rightLabelBottom,
        lastUpdatedText = lastUpdatedText
    )
}

/**
 * Backwards compatibility overload for existing code using pm25 parameter
 */
@Composable
fun AQIBannerCompat(
    pm25: Double,
    displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    lastUpdate: String? = null,
    modifier: Modifier = Modifier
) = AQIBanner(pm25, MeasurementType.PM25, displayUnit, lastUpdate, modifier)

/**
 * Alternative AQI Banner showing AQI value instead of μg/m³
 */
@Composable
fun AQIBannerWithAQIValue(
    pm25: Double,
    lastUpdate: Long? = null,
    displayUnit: AQIDisplayUnit = AQIDisplayUnit.USAQI,
    modifier: Modifier = Modifier
) {
    val formattedTime = lastUpdate?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }

    AQIBanner(
        value = pm25,
        measurementType = MeasurementType.PM25,
        displayUnit = displayUnit,
        lastUpdate = formattedTime,
        modifier = modifier
    )
}

@Composable
private fun AQIBannerCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    primaryLine: String,
    secondaryLine: String?,
    rightLabelTop: String,
    rightLabelBottom: String,
    lastUpdatedText: String,
    icon: ImageVector = Icons.Default.AccessTime
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = primaryLine,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    secondaryLine?.let {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = rightLabelTop,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = rightLabelBottom,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = lastUpdatedText,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
