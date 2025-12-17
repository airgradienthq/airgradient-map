package com.airgradient.android.domain.models.auth

sealed class AuthenticationResult {
    data class Success(val user: AuthenticatedUser) : AuthenticationResult()
    data class Error(val error: AuthenticationError) : AuthenticationResult()
}
