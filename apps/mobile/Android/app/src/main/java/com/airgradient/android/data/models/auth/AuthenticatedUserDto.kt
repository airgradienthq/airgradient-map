package com.airgradient.android.data.models.auth

import com.airgradient.android.domain.models.auth.AuthenticatedGroup
import com.airgradient.android.domain.models.auth.AuthenticatedUser

data class AuthenticatedUserDto(
    val id: Int,
    val name: String? = null,
    val email: String,
    val authenticated: Boolean? = null,
    val admin: Boolean = false,
    val support_staff: Boolean = false,
    val default_place_uid: String? = null,
    val groups: List<AuthenticatedGroupDto> = emptyList()
)

fun AuthenticatedUserDto.toDomain(): AuthenticatedUser {
    return AuthenticatedUser(
        id = id,
        name = name.orEmpty(),
        email = email,
        authenticated = authenticated ?: true,
        admin = admin,
        supportStaff = support_staff,
        defaultPlaceUid = default_place_uid,
        groups = groups.map { dto ->
            AuthenticatedGroup(
                id = dto.id,
                name = dto.name,
                placeId = dto.place_id
            )
        }
    )
}
