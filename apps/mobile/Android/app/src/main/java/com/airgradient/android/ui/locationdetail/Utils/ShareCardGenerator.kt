package com.airgradient.android.ui.locationdetail.Utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.airgradient.android.R
import com.airgradient.android.data.models.LocationDetail
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import com.airgradient.android.ui.shared.Utils.toDisplayNameRes
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShareCardGenerator {
    private const val CARD_WIDTH_DP = 400
    private const val CARD_HEIGHT_DP = 300

    suspend fun renderShareCard(
        context: Context,
        location: LocationDetail,
        displayUnit: AQIDisplayUnit,
        mapTitle: String,
        dateLabel: String
    ): Bitmap = withContext(Dispatchers.Main.immediate) {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_AGMap_ShareCard)
        val inflater = LayoutInflater.from(themedContext)
        val root = inflater.inflate(R.layout.share_card_view, null, false) as MaterialCardView

        val density = themedContext.resources.displayMetrics.density
        val widthPx = (CARD_WIDTH_DP * density).roundToInt()
        val heightPx = (CARD_HEIGHT_DP * density).roundToInt()

        val accentColorCompose = EPAColorCoding.colorForMeasurement(
            value = location.currentPM25,
            measurementType = MeasurementType.PM25,
            displayUnit = displayUnit
        )
        val accentColor = accentColorCompose.toArgb()
        val contentColor = EPAColorCoding
            .textColorForMeasurement(location.currentPM25, MeasurementType.PM25, displayUnit)
            .toArgb()

        val overlay = root.findViewById<View>(R.id.accentOverlay)
        overlay.setBackgroundColor(accentColor)

        val borderColor = accentColorCompose.copy(alpha = 0.24f).toArgb()
        root.strokeWidth = density.toInt()
        root.strokeColor = borderColor
        root.cardElevation = 0f
        root.setCardBackgroundColor(AndroidColor.WHITE)

        val locationName = root.findViewById<TextView>(R.id.locationName)
        locationName.text = location.name
        locationName.setTextColor(contentColor)

        val categoryLabel = root.findViewById<TextView>(R.id.categoryLabel)
        categoryLabel.text = themedContext.getString(location.aqiCategory.toDisplayNameRes())
        categoryLabel.setTextColor(contentColor)
        tintBackground(categoryLabel.background, accentColorCompose.copy(alpha = 0.25f).toArgb())

        val valueLabel = root.findViewById<TextView>(R.id.valueLabel)
        val unitLabel = displayUnitLabel(themedContext, displayUnit)
        val displayValue = EPAColorCoding.getDisplayValueForMeasurement(
            value = location.currentPM25,
            measurementType = MeasurementType.PM25,
            displayUnit = displayUnit
        )
        valueLabel.text = "$displayValue $unitLabel"
        valueLabel.setTextColor(contentColor)

        val microgramsText = root.findViewById<TextView>(R.id.microgramsLabel)
        if (displayUnit == AQIDisplayUnit.USAQI) {
            microgramsText.isVisible = true
            microgramsText.text = String.format(
                Locale.US,
                "%.1f %s",
                location.currentPM25,
                themedContext.getString(R.string.unit_ugm3)
            )
            microgramsText.setTextColor(applyAlpha(contentColor, 0.85f))
        } else {
            microgramsText.isGone = true
        }

        val backgroundImage = root.findViewById<ImageView>(R.id.backgroundImage)
        val backgroundResId = backgroundForPm25(location.currentPM25)
        if (backgroundResId != null) {
            backgroundImage.isVisible = true
            backgroundImage.setImageResource(backgroundResId)
        } else {
            backgroundImage.isGone = true
        }

        val mascotImage = root.findViewById<ImageView>(R.id.mascotImage)
        val mascotResId = mascotForPm25(location.currentPM25)
        if (mascotResId != null) {
            mascotImage.setImageResource(mascotResId)
        }

        val mascotCircle = root.findViewById<FrameLayout>(R.id.mascotContainer)
        tintBackground(mascotCircle.background, accentColorCompose.copy(alpha = 0.2f).toArgb())

        val footerContainer = root.findViewById<LinearLayout>(R.id.footerContainer)
        tintBackground(footerContainer.background, accentColor)

        val mapTitleText = root.findViewById<TextView>(R.id.mapTitle)
        mapTitleText.text = mapTitle
        mapTitleText.setTextColor(AndroidColor.WHITE)

        val dateLabelText = root.findViewById<TextView>(R.id.dateLabel)
        dateLabelText.text = dateLabel
        dateLabelText.setTextColor(applyAlpha(AndroidColor.WHITE, 0.82f))
        dateLabelText.alpha = 0.8f

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, widthPx, heightPx)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        bitmap
    }

    fun saveShareCard(context: Context, bitmap: Bitmap): File {
        val directory = File(context.cacheDir, "share_cards").apply { if (!exists()) mkdirs() }
        val filename = "air_quality_share_" + System.currentTimeMillis() + ".png"
        val file = File(directory, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private fun tintBackground(drawable: Drawable?, color: Int) {
        drawable ?: return
        val wrapped = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(wrapped, color)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val clamped = alpha.coerceIn(0f, 1f)
        val a = (255 * clamped).toInt().coerceIn(0, 255)
        val r = AndroidColor.red(color)
        val g = AndroidColor.green(color)
        val b = AndroidColor.blue(color)
        return AndroidColor.argb(a, r, g, b)
    }

    private fun displayUnitLabel(context: Context, displayUnit: AQIDisplayUnit): String {
        val resId = when (displayUnit) {
            AQIDisplayUnit.UGM3 -> R.string.unit_ugm3
            AQIDisplayUnit.USAQI -> R.string.unit_us_aqi_short
        }
        return context.getString(resId)
    }

    @DrawableRes
    private fun backgroundForPm25(pm25: Double): Int? {
        if (!pm25.isFinite()) return null
        val category = EPAColorCoding.getCategoryForPM25(pm25) ?: AQICategory.GOOD
        return EPAColorCoding.backgroundForCategory(category)
    }

    @DrawableRes
    private fun mascotForPm25(pm25: Double): Int? {
        if (!pm25.isFinite()) return null
        val category = EPAColorCoding.getCategoryForPM25(pm25) ?: AQICategory.GOOD
        return EPAColorCoding.mascotForCategory(category)
    }
}
