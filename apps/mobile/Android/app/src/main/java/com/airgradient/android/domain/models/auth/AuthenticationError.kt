package com.airgradient.android.domain.models.auth

import com.airgradient.android.data.network.NetworkError

sealed interface AuthenticationError {
    object InvalidCredentials : AuthenticationError
    object GoogleOnlyAccount : AuthenticationError
    object Forbidden : AuthenticationError
    object InvalidEmail : AuthenticationError
    object EmailInUse : AuthenticationError
    object InvalidToken : AuthenticationError
    object AccountMissing : AuthenticationError
    object SessionExpired : AuthenticationError
    data class Network(val error: NetworkError) : AuthenticationError
    data class Unknown(val statusCode: Int) : AuthenticationError
}
