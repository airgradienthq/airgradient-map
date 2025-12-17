package com.airgradient.android.domain.usecases

import com.airgradient.android.data.models.LocationDetail
import com.airgradient.android.data.repositories.LocationDetailRepository
import javax.inject.Inject

class LoadLocationDetailUseCase @Inject constructor(
    private val repository: LocationDetailRepository
) {
    suspend operator fun invoke(locationId: Int): LocationDetail = repository.getLocationDetail(locationId)
}

