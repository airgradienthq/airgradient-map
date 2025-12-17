package com.airgradient.android.data.di

import com.airgradient.android.data.local.datastore.AppSettingsDataStore
import com.airgradient.android.data.services.AirQualityApiService
import com.airgradient.android.domain.services.AQIService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing Hilt dependencies from widgets
 * Widgets can't use @Inject directly, so we use EntryPoint instead
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun airQualityApiService(): AirQualityApiService
    fun appSettingsDataStore(): AppSettingsDataStore
    fun aqiService(): AQIService
}
