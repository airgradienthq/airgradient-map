package com.airgradient.android.domain.models.auth

data class AuthenticatedUser(
    val id: Int,
    val name: String,
    val email: String,
    val authenticated: Boolean,
    val admin: Boolean,
    val supportStaff: Boolean,
    val defaultPlaceUid: String?,
    val groups: List<AuthenticatedGroup>
)
