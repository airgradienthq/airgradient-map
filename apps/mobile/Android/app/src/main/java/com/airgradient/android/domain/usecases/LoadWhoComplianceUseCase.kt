package com.airgradient.android.domain.usecases

import com.airgradient.android.data.models.WHOCompliance
import com.airgradient.android.data.repositories.LocationDetailRepository
import javax.inject.Inject

class LoadWhoComplianceUseCase @Inject constructor(
    private val repository: LocationDetailRepository
) {
    suspend operator fun invoke(locationId: Int): WHOCompliance? = repository.getWHOCompliance(locationId)
}

