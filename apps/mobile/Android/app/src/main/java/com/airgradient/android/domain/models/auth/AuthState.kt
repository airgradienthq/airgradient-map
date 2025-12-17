package com.airgradient.android.domain.models.auth

sealed class AuthState {
    object SignedOut : AuthState()
    data class Authenticated(val user: AuthenticatedUser) : AuthState()
}
