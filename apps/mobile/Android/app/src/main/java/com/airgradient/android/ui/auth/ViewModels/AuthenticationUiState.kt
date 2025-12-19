package com.airgradient.android.ui.auth.ViewModels

data class AuthenticationUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessageRes: Int? = null,
    val passwordVisible: Boolean = false,
    val isGoogleSignInInProgress: Boolean = false
) {
    val isSignInEnabled: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isLoading && !isGoogleSignInInProgress
}
