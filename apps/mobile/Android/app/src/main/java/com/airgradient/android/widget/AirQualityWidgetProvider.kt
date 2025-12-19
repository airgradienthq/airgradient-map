package com.airgradient.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import com.airgradient.android.app.MainActivity
import com.airgradient.android.R
import androidx.core.content.ContextCompat
import com.airgradient.android.domain.models.AQICategory
import com.airgradient.android.ui.map.Utils.EPAColorCoding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AirQualityWidgetProvider : AppWidgetProvider() {

    private val widgetJob = SupervisorJob()
    private val widgetScope: CoroutineScope = CoroutineScope(widgetJob + Dispatchers.Main)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // goAsync() can return null when invoked outside of a broadcast lifecycle (e.g. manual refresh)
        val pendingResult: PendingResult? = try {
            goAsync()
        } catch (e: IllegalStateException) {
            null
        }

        val updateJob: Job = widgetScope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult?.finish()
            }
        }

        if (pendingResult != null) {
            widgetScope.launch {
                delay(9000)
                if (updateJob.isActive) {
                    updateJob.cancel()
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Widget is added to home screen for the first time
        updateAllWidgets(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // All widgets removed, clean up any resources
        widgetJob.cancelChildren()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        widgetScope.launch {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } catch (_: Exception) {
                // Ignore; regular update will refresh later
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                updateAllWidgets(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, AirQualityWidgetProvider::class.java)
        )
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        const val ACTION_REFRESH = "com.airgradient.android.widget.ACTION_REFRESH"
        const val EXTRA_SELECTED_LOCATION_ID = "extra_selected_location_id"

        suspend fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) = withContext(Dispatchers.IO) {
            val views = selectLayout(context, appWidgetManager, appWidgetId)

            // Fetch and update widget data
            try {
                val widgetData = AirQualityWidgetDataService.getWidgetData(context)

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            if (widgetData.locationId > 0) {
                                putExtra(EXTRA_SELECTED_LOCATION_ID, widgetData.locationId)
                            }
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                        val mascotResource = mascotForAqi(widgetData.themeAqiValue)
                        val backgroundResource = backgroundForAqi(widgetData.themeAqiValue)

                        views.setTextViewText(R.id.widget_location, widgetData.locationName)
                        views.setTextViewText(R.id.widget_aqi_value, widgetData.primaryValueText)
                        views.setTextViewText(R.id.widget_aqi_label, widgetData.primaryLabel)
                        views.setTextViewText(
                            R.id.widget_aqi_category,
                            widgetData.categoryLabel
                        )

                        views.setTextViewText(R.id.widget_pm_value, widgetData.pm25Display)
                        views.setImageViewResource(R.id.widget_mascot, mascotResource)
                        views.setImageViewResource(R.id.widget_background, backgroundResource)

                        val primaryTextColor = Color.parseColor("#1B1B1F")
                        val secondaryTextColor = Color.parseColor("#991B1B1F")
                        val categoryColor = textColorForAqi(context, widgetData.themeAqiValue)

                        views.setTextColor(R.id.widget_location, primaryTextColor)
                        views.setTextColor(R.id.widget_aqi_value, primaryTextColor)
                                                views.setTextColor(R.id.widget_aqi_label, primaryTextColor)
                        views.setTextColor(R.id.widget_aqi_category, categoryColor)
                        views.setTextColor(R.id.widget_last_updated, Color.WHITE)
                        views.setTextColor(R.id.widget_pm_value, Color.WHITE)
                        views.setInt(R.id.widget_footer, "setBackgroundColor", categoryColor)

                        // Show mascot, hide refresh icon on success
                        views.setViewVisibility(R.id.widget_mascot, android.view.View.VISIBLE)
                        views.setViewVisibility(R.id.widget_refresh_icon, android.view.View.GONE)

                        // Display last updated time
                        val lastUpdatedText = widgetData.lastUpdated?.let { formatLastUpdated(context, it) }
                            ?: context.getString(R.string.widget_last_updated_unknown)
                        views.setTextViewText(R.id.widget_last_updated, lastUpdatedText)

                    // Update the widget
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                // Handle error - show default values with refresh option
                withContext(Dispatchers.Main) {
                        val intent = Intent(context, MainActivity::class.java)
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                        // Set up refresh action for the refresh icon
                        val refreshIntent = Intent(context, AirQualityWidgetProvider::class.java).apply {
                            action = ACTION_REFRESH
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            context,
                            appWidgetId,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_refresh_icon, refreshPendingIntent)

                        views.setTextViewText(
                            R.id.widget_location,
                            context.getString(R.string.widget_error_loading)
                        )
                        views.setTextViewText(R.id.widget_last_updated, context.getString(R.string.widget_tap_to_retry))
                        views.setTextViewText(R.id.widget_aqi_value, context.getString(R.string.widget_value_placeholder_empty))
                        views.setTextViewText(R.id.widget_aqi_label, context.getString(R.string.widget_aqi_label))
                        views.setTextViewText(R.id.widget_aqi_category, "")
                        views.setTextViewText(R.id.widget_pm_value, context.getString(R.string.widget_pm_missing))

                        // Hide mascot, show refresh icon
                        views.setViewVisibility(R.id.widget_mascot, android.view.View.GONE)
                        views.setViewVisibility(R.id.widget_refresh_icon, android.view.View.VISIBLE)

                        views.setImageViewResource(R.id.widget_background, backgroundForAqi(50))
                        views.setTextColor(R.id.widget_location, Color.parseColor("#1B1B1F"))
                        views.setTextColor(R.id.widget_aqi_value, Color.parseColor("#1B1B1F"))
                                                views.setTextColor(R.id.widget_aqi_label, Color.parseColor("#1B1B1F"))
                        views.setTextColor(R.id.widget_aqi_category, Color.parseColor("#1B1B1F"))
                        views.setTextColor(R.id.widget_last_updated, Color.WHITE)
                        views.setTextColor(R.id.widget_pm_value, Color.WHITE)
                        views.setInt(R.id.widget_footer, "setBackgroundColor", textColorForAqi(context, 50))
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private fun selectLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): RemoteViews {
            // Switch to the large layout when there's enough width OR height to breathe
            val layoutId = R.layout.widget_air_quality

            return RemoteViews(context.packageName, layoutId)
        }

        private fun mascotForAqi(aqi: Int): Int {
            val category = EPAColorCoding.getCategoryForAQI(aqi)
            return EPAColorCoding.mascotForCategory(category)
        }

        private fun backgroundForAqi(aqi: Int): Int {
            val category = EPAColorCoding.getCategoryForAQI(aqi)
            return EPAColorCoding.backgroundForCategory(category)
        }

        private fun textColorForAqi(context: Context, aqi: Int): Int {
            val category = EPAColorCoding.getCategoryForAQI(aqi)
            val colorRes = when (category) {
                AQICategory.GOOD -> R.color.widget_good
                AQICategory.MODERATE -> R.color.widget_moderate
                AQICategory.UNHEALTHY_FOR_SENSITIVE -> R.color.widget_unhealthy_sensitive
                AQICategory.UNHEALTHY -> R.color.widget_unhealthy
                AQICategory.VERY_UNHEALTHY -> R.color.widget_very_unhealthy
                AQICategory.HAZARDOUS -> R.color.widget_hazardous
            }
            return ContextCompat.getColor(context, colorRes)
        }

        private fun formatLastUpdated(context: Context, timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diffMinutes = ((now - timestamp) / 60_000).toInt().coerceAtLeast(0)
            val resources = context.resources

            return when {
                diffMinutes < 1 -> context.getString(R.string.widget_updated_just_now)
                diffMinutes < 60 -> resources.getQuantityString(
                    R.plurals.widget_updated_minutes,
                    diffMinutes,
                    diffMinutes
                )
                diffMinutes < 1440 -> {
                    val hours = (diffMinutes / 60).coerceAtLeast(1)
                    resources.getQuantityString(
                        R.plurals.widget_updated_hours,
                        hours,
                        hours
                    )
                }
                else -> {
                    val days = (diffMinutes / 1440).coerceAtLeast(1)
                    resources.getQuantityString(
                        R.plurals.widget_updated_days,
                        days,
                        days
                    )
                }
            }
        }
    }
}
