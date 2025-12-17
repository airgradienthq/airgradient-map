package com.airgradient.android.data.local.auth

import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.domain.models.auth.AuthenticatedUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.HttpUrl

@Singleton
class AuthenticationStateStore @Inject constructor(
    private val preferences: AuthenticationPreferences,
    private val authCookieJar: AuthCookieJar
) {
    private val _authState = MutableStateFlow(initialState())
    private val emailState = preferences.email()

    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun lastUsedEmail(): StateFlow<String?> = emailState

    fun storeAuthenticatedUser(user: AuthenticatedUser, email: String) {
        preferences.saveEmail(email)
        preferences.saveUser(user)
        _authState.value = AuthState.Authenticated(user)
    }

    fun updateAuthenticatedUser(user: AuthenticatedUser) {
        preferences.saveUser(user)
        _authState.value = AuthState.Authenticated(user)
    }

    fun updateEmail(email: String) {
        preferences.saveEmail(email)
    }

    fun clearSession(keepEmail: Boolean = true) {
        authCookieJar.clear()
        if (keepEmail) {
            preferences.saveUser(null)
        } else {
            preferences.clear()
        }
        _authState.value = AuthState.SignedOut
    }

    fun hasValidSession(): Boolean = authCookieJar.hasValidCookies()

    fun importCookies(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        authCookieJar.saveFromResponse(url, cookies)
    }

    private fun initialState(): AuthState {
        val user = preferences.currentUser()
        val hasCookies = authCookieJar.hasValidCookies()
        return if (user != null && hasCookies) {
            AuthState.Authenticated(user)
        } else {
            AuthState.SignedOut
        }
    }
}
