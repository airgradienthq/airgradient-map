package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.domain.models.auth.AuthenticationResult
import kotlinx.coroutines.flow.StateFlow

interface AuthenticationRepository {
    suspend fun signIn(email: String, password: String): AuthenticationResult
    suspend fun signInWithGoogle(idToken: String): AuthenticationResult
    suspend fun refreshAuthenticatedUser(): AuthenticationResult
    suspend fun signOut()
    fun handleSessionExpired()
    val authState: StateFlow<AuthState>
    val lastUsedEmail: StateFlow<String?>
}
