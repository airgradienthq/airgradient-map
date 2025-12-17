package com.airgradient.android.data.services

import com.airgradient.android.data.models.auth.AuthenticatedUserDto
import com.airgradient.android.data.models.auth.GoogleSignInRequest
import com.airgradient.android.data.models.auth.SignInRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApiService {
    @POST("user/authenticate")
    suspend fun authenticate(@Body request: SignInRequest): Response<Unit>

    @POST("auth/google/signin")
    suspend fun authenticateWithGoogle(@Body request: GoogleSignInRequest): Response<Unit>

    @GET("auth/user")
    suspend fun fetchAuthenticatedUser(): Response<AuthenticatedUserDto>
}
