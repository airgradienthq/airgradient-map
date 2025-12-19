package com.airgradient.android.data.repositories

import com.airgradient.android.data.local.auth.AuthenticationStateStore
import com.airgradient.android.data.local.auth.PlaceSelectionStore
import com.airgradient.android.data.models.auth.GoogleSignInRequest
import com.airgradient.android.data.models.auth.SignInRequest
import com.airgradient.android.data.models.auth.toDomain
import com.airgradient.android.data.network.NetworkError
import com.airgradient.android.data.services.AuthApiService
import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.domain.models.auth.AuthenticationError
import com.airgradient.android.domain.models.auth.AuthenticationResult
import com.airgradient.android.domain.repositories.AuthenticationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

@Singleton
class AuthenticationRepositoryImpl @Inject constructor(
    private val authApiService: AuthApiService,
    private val stateStore: AuthenticationStateStore,
    private val placeSelectionStore: PlaceSelectionStore
) : AuthenticationRepository {

    override val authState: StateFlow<AuthState> = stateStore.authState
    override val lastUsedEmail: StateFlow<String?> = stateStore.lastUsedEmail()

    override suspend fun signIn(email: String, password: String): AuthenticationResult {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isNotEmpty()) {
            stateStore.updateEmail(normalizedEmail)
        }

        return withContext(Dispatchers.IO) {
            try {
                val authResponse = authApiService.authenticate(
                    SignInRequest(email = normalizedEmail, password = password)
                )
                if (!authResponse.isSuccessful) {
                    return@withContext AuthenticationResult.Error(
                        mapAuthenticateError(authResponse.code())
                    )
                }

                val userResponse = authApiService.fetchAuthenticatedUser()
                if (!userResponse.isSuccessful) {
                    return@withContext handleFetchUserFailure(userResponse.code())
                }

                val dto = userResponse.body()
                if (dto == null) {
                    return@withContext AuthenticationResult.Error(
                        AuthenticationError.Unknown(userResponse.code())
                    )
                }

                val user = dto.toDomain()
                stateStore.storeAuthenticatedUser(user, normalizedEmail)
                AuthenticationResult.Success(user)
            } catch (throwable: Exception) {
                val networkError = when (throwable) {
                    is HttpException -> NetworkError.ServerError(throwable.code())
                    else -> NetworkError.from(throwable)
                }
                AuthenticationResult.Error(AuthenticationError.Network(networkError))
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AuthenticationResult {
        return withContext(Dispatchers.IO) {
            try {
                val authResponse = authApiService.authenticateWithGoogle(
                    GoogleSignInRequest(token = idToken)
                )
                if (!authResponse.isSuccessful) {
                    return@withContext AuthenticationResult.Error(
                        mapAuthenticateError(authResponse.code())
                    )
                }

                val userResponse = authApiService.fetchAuthenticatedUser()
                if (!userResponse.isSuccessful) {
                    return@withContext handleFetchUserFailure(userResponse.code())
                }

                val dto = userResponse.body()
                if (dto == null) {
                    return@withContext AuthenticationResult.Error(
                        AuthenticationError.Unknown(userResponse.code())
                    )
                }

                val user = dto.toDomain()
                stateStore.storeAuthenticatedUser(user, user.email)
                AuthenticationResult.Success(user)
            } catch (throwable: Exception) {
                val networkError = when (throwable) {
                    is HttpException -> NetworkError.ServerError(throwable.code())
                    else -> NetworkError.from(throwable)
                }
                AuthenticationResult.Error(AuthenticationError.Network(networkError))
            }
        }
    }

    override suspend fun refreshAuthenticatedUser(): AuthenticationResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = authApiService.fetchAuthenticatedUser()
                if (!response.isSuccessful) {
                    return@withContext handleFetchUserFailure(response.code())
                }

                val dto = response.body()
                if (dto == null) {
                    return@withContext AuthenticationResult.Error(
                        AuthenticationError.Unknown(response.code())
                    )
                }

                val user = dto.toDomain()
                stateStore.updateAuthenticatedUser(user)
                AuthenticationResult.Success(user)
            } catch (throwable: Exception) {
                val networkError = when (throwable) {
                    is HttpException -> NetworkError.ServerError(throwable.code())
                    else -> NetworkError.from(throwable)
                }
                AuthenticationResult.Error(AuthenticationError.Network(networkError))
            }
        }
    }

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            stateStore.clearSession(keepEmail = true)
            placeSelectionStore.clear()
        }
    }

    override fun handleSessionExpired() {
        stateStore.clearSession(keepEmail = true)
        placeSelectionStore.clear()
    }

    private fun handleFetchUserFailure(code: Int): AuthenticationResult {
        return if (code == 401) {
            handleSessionExpired()
            AuthenticationResult.Error(AuthenticationError.SessionExpired)
        } else {
            AuthenticationResult.Error(AuthenticationError.Unknown(code))
        }
    }

    private fun mapAuthenticateError(code: Int): AuthenticationError {
        return when (code) {
            401 -> AuthenticationError.InvalidCredentials
            418 -> AuthenticationError.GoogleOnlyAccount
            403 -> AuthenticationError.Forbidden
            else -> AuthenticationError.Unknown(code)
        }
    }

}
