package com.airgradient.android.ui.map.Views.marker

import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.airgradient.android.data.models.AirQualityAnnotation
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.models.MeasurementType
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.abs

internal class MapMarkerCoordinator {

    private val renderedMarkers = mutableMapOf<String, RenderedMarker>()
    private var pendingSelectionKey: String? = null
    private var selectionRetryAttempts: Int = 0

    fun render(
        mapView: MapView,
        annotations: List<AirQualityAnnotation>,
        selectedKey: String?,
        measurementType: MeasurementType,
        displayUnit: AQIDisplayUnit,
        onSensorSelected: (AirQualityAnnotation) -> Unit,
        onClusterSelected: (AirQualityAnnotation) -> Unit
    ) {
        val newByKey = annotations.associateBy { it.key }

        val toRemove = renderedMarkers.keys - newByKey.keys
        val toAddList = (newByKey.keys - renderedMarkers.keys).toList()
        val toCheck = newByKey.keys.intersect(renderedMarkers.keys)

        toRemove.forEach { key ->
            renderedMarkers.remove(key)?.let { entry ->
                entry.drawable.animateRemoval {
                    mapView.overlays.remove(entry.marker)
                    entry.drawable.dispose()
                    mapView.postInvalidate()
                }
            }
        }

        toCheck.forEach { key ->
            val existing = renderedMarkers[key] ?: return@forEach
            val updated = newByKey[key] ?: return@forEach
            if (hasSignificantChange(existing.annotation, updated)) {
                val style = buildVisualStyle(mapView, updated, measurementType, displayUnit)
                existing.drawable.updateStyle(style)
                existing.annotation = updated
            } else {
                existing.annotation = updated
            }
        }

        toAddList.forEachIndexed { index, key ->
            val annotation = newByKey[key] ?: return@forEachIndexed
            val marker = Marker(mapView)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.position = GeoPoint(annotation.coordinate.first, annotation.coordinate.second)
            val style = buildVisualStyle(mapView, annotation, measurementType, displayUnit)
            val drawable = CustomMarkerDrawable(style) {
                // Ensure a frame is requested for each animation tick
                try {
                    mapView.postInvalidateOnAnimation()
                } catch (t: Throwable) {
                    mapView.postInvalidate()
                }
            }
            drawable.alpha = 0
            marker.icon = drawable
            // Connect drawable invalidation to MapView so ValueAnimator updates trigger redraws
            drawable.callback = mapView

            marker.setOnMarkerClickListener { _, _ ->
                performHaptic(mapView)
                if (annotation.isCluster) {
                    onClusterSelected(annotation)
                } else {
                    onSensorSelected(annotation)
                }
                true
            }

            renderedMarkers[key] = RenderedMarker(annotation, marker, drawable)
            mapView.overlays.add(marker)
            drawable.alpha = 255
            drawable.animateAddition(delay = index * 40L)
        }

        renderedMarkers.values.forEach { entry ->
            entry.drawable.setSelected(entry.annotation.key == selectedKey)
        }

        ensureSelectionApplied(mapView, selectedKey)

        Log.d(
            "MapMarkerCoordinator",
            "Markers rendered=${renderedMarkers.size}, added=${toAddList.size}, removed=${toRemove.size}, updated=${toCheck.size}"
        )

        mapView.postInvalidate()
    }

    fun clear(mapView: MapView) {
        renderedMarkers.values.forEach { entry ->
            mapView.overlays.remove(entry.marker)
            entry.drawable.dispose()
        }
        renderedMarkers.clear()
        pendingSelectionKey = null
        selectionRetryAttempts = 0
        mapView.postInvalidate()
    }

    private fun hasSignificantChange(old: AirQualityAnnotation, new: AirQualityAnnotation): Boolean {
        if (old.currentValue != new.currentValue) return true
        if (abs(old.coordinate.first - new.coordinate.first) > 1e-6) return true
        if (abs(old.coordinate.second - new.coordinate.second) > 1e-6) return true
        if ((old.sensorType ?: "") != (new.sensorType ?: "")) return true
        if ((old.clusterCount ?: 0) != (new.clusterCount ?: 0)) return true
        if (old.measurementType != new.measurementType) return true
        if ((old.subtitle ?: "") != (new.subtitle ?: "")) return true
        return false
    }

    private fun buildVisualStyle(
        mapView: MapView,
        annotation: AirQualityAnnotation,
        measurementType: MeasurementType,
        displayUnit: AQIDisplayUnit
    ): MarkerVisualStyle {
        val metrics = mapView.resources.displayMetrics
        val isCluster = annotation.isCluster
        val baseSizeDp = if (isCluster) 40f else 28f
        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, baseSizeDp, metrics).toInt()
        val heightPx = widthPx
        val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, if (isCluster) 11f else 10f, metrics)
        val cornerRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, metrics)
        val strokeWidthPx = if (annotation.sensorType.equals("Reference", true)) {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, metrics)
        } else {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, if (isCluster) 2f else 1.2f, metrics)
        }
        val inset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.8f, metrics)
        val pulseStroke = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.2f, metrics)
        val glowExpansion = if (isCluster) 0f else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, metrics)
        val pulseBaseRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35f, metrics)
        val selectionShadowRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, metrics)
        val selectionShadowColor = Color.argb((SELECTION_SHADOW_ALPHA * 255).toInt(), 0, 0, 0)

        val value = annotation.currentValue
        val colorInt = EPAColorCoding.colorForMeasurement(value, measurementType, displayUnit).toArgb()
        // Force all marker text to be white for improved readability
        val textColorInt = Color.WHITE
        val displayValue = value?.let {
            EPAColorCoding.getDisplayValueForMeasurement(it, measurementType, displayUnit)
        } ?: annotation.clusterCount?.takeIf { it > 0 }?.toString() ?: "--"

        val hasGlow = true
        val isReference = annotation.sensorType.equals("Reference", true)
        val glowColor = if (isCluster) {
            Color.argb(150, Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
        } else {
            Color.argb(140, Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
        }
        val glowRadiusMultiplier = if (isCluster) 0.6f else 0.5f

        return MarkerVisualStyle(
            widthPx = widthPx,
            heightPx = heightPx,
            fillColor = colorInt,
            textColor = textColorInt,
            borderColor = Color.WHITE,
            glowColor = glowColor,
            borderWidthPx = if (isReference) strokeWidthPx else strokeWidthPx * 0.65f,
            borderAlpha = if (isReference) 1f else 0.45f,
            displayText = displayValue,
            textSizePx = textSizePx,
            isCluster = annotation.isCluster,
            clusterCount = annotation.clusterCount,
            isReference = isReference,
            hasGlow = hasGlow,
            cornerRadiusPx = cornerRadiusPx,
            pulseStrokeWidthPx = pulseStroke,
            measurementType = measurementType,
            strokeInsetPx = inset,
            glowRadiusMultiplier = glowRadiusMultiplier,
            glowExpansionPx = glowExpansion,
            pulseBaseRadiusPx = pulseBaseRadius,
            selectionShadowRadiusPx = selectionShadowRadius,
            selectionShadowColor = selectionShadowColor
        )
    }

    private fun performHaptic(mapView: MapView) {
        val view: View = mapView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun ensureSelectionApplied(mapView: MapView, selectedKey: String?) {
        if (selectedKey == null) {
            pendingSelectionKey = null
            selectionRetryAttempts = 0
            return
        }

        if (renderedMarkers.containsKey(selectedKey)) {
            pendingSelectionKey = null
            selectionRetryAttempts = 0
            return
        }

        if (pendingSelectionKey != selectedKey) {
            pendingSelectionKey = selectedKey
            selectionRetryAttempts = 0
        }

        if (selectionRetryAttempts == 0) {
            scheduleSelectionRetry(mapView)
        }
    }

    private fun scheduleSelectionRetry(mapView: MapView) {
        pendingSelectionKey ?: return
        if (selectionRetryAttempts >= MAX_SELECTION_RETRIES) {
            return
        }
        selectionRetryAttempts += 1
        mapView.postDelayed({
            val currentKey = pendingSelectionKey ?: return@postDelayed
            val entry = renderedMarkers[currentKey]
            if (entry != null) {
                entry.drawable.setSelected(true)
                mapView.postInvalidate()
                pendingSelectionKey = null
                selectionRetryAttempts = 0
            } else {
                scheduleSelectionRetry(mapView)
            }
        }, SELECTION_RETRY_DELAY_MS)
    }

    private data class RenderedMarker(
        var annotation: AirQualityAnnotation,
        val marker: Marker,
        val drawable: CustomMarkerDrawable
    )

    private companion object {
        private const val SELECTION_RETRY_DELAY_MS = 100L
        private const val MAX_SELECTION_RETRIES = 3
        private const val SELECTION_SHADOW_ALPHA = 0.8f
    }
}
