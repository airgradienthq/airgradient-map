package com.airgradient.android.ui.locationdetail.DI

import com.airgradient.android.data.repositories.LocationDetailRepository
import com.airgradient.android.data.repositories.LocationDetailRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationDetailModule {
    
    @Binds
    @Singleton
    abstract fun bindLocationDetailRepository(
        locationDetailRepositoryImpl: LocationDetailRepositoryImpl
    ): LocationDetailRepository
}