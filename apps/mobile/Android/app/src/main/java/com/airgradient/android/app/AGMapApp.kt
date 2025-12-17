package com.airgradient.android.app

import android.app.Application
import android.util.Log
import com.airgradient.android.BuildConfig
import com.airgradient.android.data.local.PushNotificationMetadataStore
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class AGMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable verbose logging for debugging (remove in production)
        // OneSignal.Debug.logLevel = LogLevel.VERBOSE
        val oneSignalAppId = BuildConfig.ONE_SIGNAL_APP_ID
        Log.d(TAG, "Initialising OneSignal with appId=$oneSignalAppId")
        if (oneSignalAppId.isBlank()) {
            Log.w(TAG, "Skipping OneSignal initialisation; supply ONESIGNAL_APP_ID via Gradle properties for push support.")
            return
        }

        // Initialize with the OneSignal App ID provided at build time
        try {
            OneSignal.initWithContext(this, oneSignalAppId)
            Log.d(TAG, "OneSignal initialised successfully")
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to initialise OneSignal", exception)
            return
        }

        val metadataStore = EntryPointAccessors.fromApplication(
            this,
            PushNotificationMetadataStoreEntryPoint::class.java
        ).pushNotificationMetadataStore()

        // Capture the current OneSignal player ID if it already exists
        OneSignal.User.pushSubscription.id?.let { existingId ->
            metadataStore.updatePlayerId(existingId)
        }

        // Keep the stored player ID up to date whenever the subscription changes
        OneSignal.User.pushSubscription.addObserver(
            object : IPushSubscriptionObserver {
                override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
                    val playerId = state.current?.id ?: return
                    metadataStore.updatePlayerId(playerId)
                }
            }
        )
    }

    companion object {
        private const val TAG = "AGMapApplication"
    }

    // Application class should only contain application-level initialization
    // No UI components or navigation should be defined here
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushNotificationMetadataStoreEntryPoint {
    fun pushNotificationMetadataStore(): PushNotificationMetadataStore
}
