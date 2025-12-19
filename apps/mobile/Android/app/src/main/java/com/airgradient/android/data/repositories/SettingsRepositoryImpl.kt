package com.airgradient.android.data.repositories

import com.airgradient.android.data.local.datastore.AppSettingsDataStore
import com.airgradient.android.data.local.migrations.SharedPreferencesToDataStore
import com.airgradient.android.domain.models.AQIDisplayUnit
import com.airgradient.android.domain.repositories.SettingsRepository
import com.airgradient.android.domain.repositories.WidgetLocationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SettingsRepository using DataStore
 * All operations run on IO dispatcher
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val appSettingsDataStore: AppSettingsDataStore,
    private val migration: SharedPreferencesToDataStore
) : SettingsRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Migrate settings on initialization
        scope.launch {
            migration.migrateAppSettings()
        }
    }

    override fun getDisplayUnit(): Flow<AQIDisplayUnit> {
        return appSettingsDataStore.displayUnit
    }

    override suspend fun setDisplayUnit(unit: AQIDisplayUnit) {
        appSettingsDataStore.updateDisplayUnit(unit)
    }

    override fun getWidgetLocation(): Flow<WidgetLocationSettings> {
        return appSettingsDataStore.widgetLocation
    }

    override suspend fun setWidgetLocation(settings: WidgetLocationSettings) {
        appSettingsDataStore.updateWidgetLocation(settings)
    }

    override suspend fun clearWidgetLocation() {
        appSettingsDataStore.clearWidgetLocation()
    }
}
