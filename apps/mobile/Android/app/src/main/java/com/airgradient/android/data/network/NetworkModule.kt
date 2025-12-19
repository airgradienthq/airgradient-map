package com.airgradient.android.data.network

import com.airgradient.android.BuildConfig
import com.airgradient.android.data.local.auth.AuthCookieJar
import com.airgradient.android.data.services.AirQualityApiService
import com.airgradient.android.data.services.AuthApiService
import com.airgradient.android.data.services.MyMonitorsApiService
import com.airgradient.android.data.services.NominatimApiService
import com.airgradient.android.data.services.NotificationApiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private fun createLoggingInterceptor(): HttpLoggingInterceptor? {
        if (!BuildConfig.DEBUG) return null
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        createLoggingInterceptor()?.let { builder.addInterceptor(it) }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://map-data-int.airgradient.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthOkHttpClient(
        authCookieJar: AuthCookieJar
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(authCookieJar)

        createLoggingInterceptor()?.let { builder.addInterceptor(it) }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthRetrofit(
        @Named("auth") okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.AUTH_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(@Named("auth") retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMyMonitorsApiService(@Named("auth") retrofit: Retrofit): MyMonitorsApiService {
        return retrofit.create(MyMonitorsApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideAirQualityApiService(retrofit: Retrofit): AirQualityApiService {
        return retrofit.create(AirQualityApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNotificationApiService(retrofit: Retrofit): NotificationApiService {
        return retrofit.create(NotificationApiService::class.java)
    }

    @Provides
    @Singleton
    @Named("nominatim")
    fun provideNominatimOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "AGMap-Android/1.0") // Required by Nominatim
                    .build()
                chain.proceed(request)
            }

        createLoggingInterceptor()?.let { builder.addInterceptor(it) }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("nominatim")
    fun provideNominatimRetrofit(@Named("nominatim") okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideNominatimApiService(@Named("nominatim") retrofit: Retrofit): NominatimApiService {
        return retrofit.create(NominatimApiService::class.java)
    }
}
