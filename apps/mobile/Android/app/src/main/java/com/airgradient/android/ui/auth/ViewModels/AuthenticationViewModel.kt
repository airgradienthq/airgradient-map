package com.airgradient.android.ui.auth.ViewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airgradient.android.R
import com.airgradient.android.data.network.NetworkError
import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.domain.models.auth.AuthenticationError
import com.airgradient.android.domain.models.auth.AuthenticationResult
import com.airgradient.android.domain.repositories.AuthenticationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthenticationEvent {
    object SignedIn : AuthenticationEvent()
    object SignedOut : AuthenticationEvent()
}

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val authenticationRepository: AuthenticationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthenticationUiState())
    val uiState: StateFlow<AuthenticationUiState> = _uiState.asStateFlow()

    val authState: StateFlow<AuthState> = authenticationRepository.authState

    private val _events = MutableSharedFlow<AuthenticationEvent>()
    val events: SharedFlow<AuthenticationEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            authenticationRepository.lastUsedEmail.collectLatest { email ->
                val target = email.orEmpty()
                _uiState.update { state ->
                    if (state.email == target) state else state.copy(email = target)
                }
            }
        }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    fun signIn() {
        val state = _uiState.value
        if (!state.isSignInEnabled) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
            when (val result = authenticationRepository.signIn(state.email, state.password)) {
                is AuthenticationResult.Success -> {
                    _uiState.update {
                        it.copy(password = "", isLoading = false, errorMessageRes = null)
                    }
                    _events.emit(AuthenticationEvent.SignedIn)
                }
                is AuthenticationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageRes = mapErrorToMessageRes(result.error)
                        )
                    }
                }
            }
        }
    }

    fun beginGoogleSignIn() {
        _uiState.update {
            it.copy(isGoogleSignInInProgress = true, errorMessageRes = null)
        }
    }

    fun cancelGoogleSignIn() {
        _uiState.update { it.copy(isGoogleSignInInProgress = false) }
    }

    fun onGoogleSignInFailed(messageRes: Int) {
        _uiState.update {
            it.copy(isGoogleSignInInProgress = false, errorMessageRes = messageRes)
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            onGoogleSignInFailed(R.string.auth_google_sign_in_failed)
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isGoogleSignInInProgress = true, errorMessageRes = null)
            }

            when (val result = authenticationRepository.signInWithGoogle(idToken)) {
                is AuthenticationResult.Success -> {
                    _uiState.update {
                        it.copy(isGoogleSignInInProgress = false, errorMessageRes = null)
                    }
                    _events.emit(AuthenticationEvent.SignedIn)
                }
                is AuthenticationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isGoogleSignInInProgress = false,
                            errorMessageRes = mapErrorToMessageRes(result.error)
                        )
                    }
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authenticationRepository.signOut()
            _uiState.update { it.copy(password = "", isLoading = false, errorMessageRes = null) }
            _events.emit(AuthenticationEvent.SignedOut)
        }
    }

    fun onSheetDismissed() {
        _uiState.update { state ->
            state.copy(
                password = "",
                isLoading = false,
                errorMessageRes = null,
                isGoogleSignInInProgress = false
            )
        }
    }

    private fun mapErrorToMessageRes(error: AuthenticationError): Int {
        return when (error) {
            AuthenticationError.InvalidCredentials -> R.string.auth_error_invalid_credentials
            AuthenticationError.GoogleOnlyAccount -> R.string.auth_error_google_only
            AuthenticationError.Forbidden -> R.string.auth_error_forbidden
            AuthenticationError.InvalidEmail -> R.string.auth_error_invalid_email
            AuthenticationError.EmailInUse -> R.string.auth_error_email_in_use
            AuthenticationError.InvalidToken -> R.string.auth_error_invalid_token
            AuthenticationError.AccountMissing -> R.string.auth_error_account_missing
            AuthenticationError.SessionExpired -> R.string.auth_error_session_expired
            is AuthenticationError.Unknown -> R.string.auth_error_unknown
            is AuthenticationError.Network -> mapNetworkErrorToMessageRes(error.error)
        }
    }

    private fun mapNetworkErrorToMessageRes(error: NetworkError): Int {
        return when (error) {
            NetworkError.NoInternetConnection -> R.string.auth_error_no_internet
            NetworkError.Timeout -> R.string.auth_error_timeout
            is NetworkError.ServerError -> R.string.auth_error_unknown
            is NetworkError.UnknownError -> R.string.auth_error_unknown
        }
    }
}
