package com.airgradient.android.data.di

import com.airgradient.android.data.repositories.AirQualityRepositoryImpl
import com.airgradient.android.data.repositories.AuthenticationRepositoryImpl
import com.airgradient.android.data.repositories.BookmarkRepositoryImpl
import com.airgradient.android.data.repositories.CommunityProjectsRepositoryImpl
import com.airgradient.android.data.repositories.FeaturedCommunityRepositoryImpl
import com.airgradient.android.data.repositories.KnowledgeHubRepositoryImpl
import com.airgradient.android.data.repositories.MyMonitorsRepositoryImpl
import com.airgradient.android.data.repositories.NotificationsRepositoryImpl
import com.airgradient.android.data.repositories.SettingsRepositoryImpl
import com.airgradient.android.domain.repositories.AirQualityRepository
import com.airgradient.android.domain.repositories.AuthenticationRepository
import com.airgradient.android.domain.repositories.BookmarkRepository
import com.airgradient.android.domain.repositories.CommunityProjectsRepository
import com.airgradient.android.domain.repositories.FeaturedCommunityRepository
import com.airgradient.android.domain.repositories.KnowledgeHubRepository
import com.airgradient.android.domain.repositories.MyMonitorsRepository
import com.airgradient.android.domain.repositories.NotificationsRepository
import com.airgradient.android.domain.repositories.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAirQualityRepository(
        airQualityRepositoryImpl: AirQualityRepositoryImpl
    ): AirQualityRepository

    @Binds
    @Singleton
    abstract fun bindCommunityProjectsRepository(
        impl: CommunityProjectsRepositoryImpl
    ): CommunityProjectsRepository

    @Binds
    @Singleton
    abstract fun bindFeaturedCommunityRepository(
        impl: FeaturedCommunityRepositoryImpl
    ): FeaturedCommunityRepository

    @Binds
    @Singleton
    abstract fun bindKnowledgeHubRepository(
        impl: KnowledgeHubRepositoryImpl
    ): KnowledgeHubRepository

    @Binds
    @Singleton
    abstract fun bindNotificationsRepository(
        impl: NotificationsRepositoryImpl
    ): NotificationsRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        impl: BookmarkRepositoryImpl
    ): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindMyMonitorsRepository(
        impl: MyMonitorsRepositoryImpl
    ): MyMonitorsRepository

    @Binds
    @Singleton
    abstract fun bindAuthenticationRepository(
        impl: AuthenticationRepositoryImpl
    ): AuthenticationRepository
}
