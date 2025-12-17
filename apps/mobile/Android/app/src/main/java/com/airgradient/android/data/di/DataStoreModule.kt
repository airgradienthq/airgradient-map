package com.airgradient.android.data.di

import android.content.Context
import com.airgradient.android.data.local.datastore.AppSettingsDataStore
import com.airgradient.android.data.local.datastore.BookmarksDataStore
import com.airgradient.android.data.local.migrations.SharedPreferencesToDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for DataStore and migration dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context
    ): AppSettingsDataStore {
        return AppSettingsDataStore(context)
    }

    @Provides
    @Singleton
    fun provideBookmarksDataStore(
        @ApplicationContext context: Context
    ): BookmarksDataStore {
        return BookmarksDataStore(context)
    }

    @Provides
    @Singleton
    fun provideSharedPreferencesToDataStoreMigration(
        @ApplicationContext context: Context,
        appSettingsDataStore: AppSettingsDataStore,
        bookmarksDataStore: BookmarksDataStore
    ): SharedPreferencesToDataStore {
        return SharedPreferencesToDataStore(
            context = context,
            appSettingsDataStore = appSettingsDataStore,
            bookmarksDataStore = bookmarksDataStore
        )
    }
}
