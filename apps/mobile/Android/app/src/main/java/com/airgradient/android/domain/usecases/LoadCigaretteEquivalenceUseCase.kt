package com.airgradient.android.domain.usecases

import com.airgradient.android.data.models.CigaretteData
import com.airgradient.android.data.repositories.LocationDetailRepository
import javax.inject.Inject

class LoadCigaretteEquivalenceUseCase @Inject constructor(
    private val repository: LocationDetailRepository
) {
    suspend operator fun invoke(locationId: Int): CigaretteData? = repository.getCigaretteEquivalence(locationId)
}

