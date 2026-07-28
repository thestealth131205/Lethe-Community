package com.securechat.app.push

import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.FcmTokenRequest
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS/F-Droid-Push-Registrierung – ohne Firebase.
 *
 * Der `foss`-Build empfängt Pushes über den persistenten WebSocket
 * ([com.securechat.app.NotificationHandler]-Foreground-Service), der die Payloads
 * an [com.securechat.app.PushPayloadHandler] weiterreicht. Es gibt daher keinen
 * FCM-Push-Token. Es wird lediglich einmalig `push_kind='ws'` am Server registriert,
 * damit send_push_notification() keinen FCM-Versand versucht (das Gerät hat keinen Token).
 */
class FossPushProvider @Inject constructor(
    private val apiService: ApiService
) : PushProvider {

    override fun registerToken() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                apiService.updateFcmToken(FcmTokenRequest(fcmToken = null, pushKind = "ws"))
                Timber.tag("LETHE_FCM").d("FOSS-Build: push_kind='ws' am Server registriert (WS-Foreground-Push).")
            } catch (e: Exception) {
                Timber.tag("LETHE_FCM").w(e, "FOSS push_kind-Registrierung fehlgeschlagen")
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PushProviderModule {
    @Binds
    @Singleton
    abstract fun bindPushProvider(impl: FossPushProvider): PushProvider
}
