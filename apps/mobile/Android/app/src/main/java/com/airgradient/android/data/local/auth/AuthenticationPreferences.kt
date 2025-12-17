package com.airgradient.android.data.local.auth

import android.content.Context
import android.content.SharedPreferences
import com.airgradient.android.domain.models.auth.AuthenticatedUser
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthenticationPreferences @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val emailState = MutableStateFlow(prefs.getString(KEY_EMAIL, null))
    private val userState = MutableStateFlow(loadUserFromPrefs())

    fun email(): StateFlow<String?> = emailState.asStateFlow()
    fun user(): StateFlow<AuthenticatedUser?> = userState.asStateFlow()

    fun saveEmail(email: String?) {
        prefs.edit().apply {
            if (email.isNullOrEmpty()) {
                remove(KEY_EMAIL)
            } else {
                putString(KEY_EMAIL, email)
            }
        }.apply()
        emailState.value = email
    }

    fun saveUser(user: AuthenticatedUser?) {
        prefs.edit().apply {
            if (user == null) {
                remove(KEY_USER)
            } else {
                putString(KEY_USER, gson.toJson(user))
            }
        }.apply()
        userState.value = user
    }

    fun currentEmail(): String? = emailState.value
    fun currentUser(): AuthenticatedUser? = userState.value

    fun clear() {
        prefs.edit().clear().apply()
        emailState.value = null
        userState.value = null
    }

    private fun loadUserFromPrefs(): AuthenticatedUser? {
        val raw = prefs.getString(KEY_USER, null) ?: return null
        return try {
            gson.fromJson(raw, AuthenticatedUser::class.java)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "authentication_preferences"
        private const val KEY_EMAIL = "email"
        private const val KEY_USER = "user"
    }
}
