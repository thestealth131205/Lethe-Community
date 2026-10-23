package com.securechat.app.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.securechat.app.NotificationHelper
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.FcmTokenRequest
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FCM-basierte Push-Registrierung – nur im `playstore`-Flavor.
 * Holt den aktuellen Firebase-Token und registriert ihn am Server.
 */
class FcmPushProvider @Inject constructor(
    private val apiService: ApiService,
    @ApplicationContext private val context: Context
) : PushProvider {

    override fun registerToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    apiService.updateFcmToken(FcmTokenRequest(fcmToken = token, pushKind = "fcm"))
                    NotificationHelper.setFcmTokenRegistered(context, true)
                    Timber.tag("LETHE_FCM").d("FCM-Token am Server registriert.")
                } catch (e: Exception) {
                    NotificationHelper.setFcmTokenRegistered(context, false)
                    Timber.tag("LETHE_FCM").w(e, "FCM-Token-Registrierung fehlgeschlagen")
                }
            }
        }.addOnFailureListener {
            NotificationHelper.setFcmTokenRegistered(context, false)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushProviderModule {
    @Binds
    @Singleton
    abstract fun bindPushProvider(impl: FcmPushProvider): PushProvider
}
