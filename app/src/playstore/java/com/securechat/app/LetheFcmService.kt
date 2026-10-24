package com.securechat.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Firebase Cloud Messaging Service (nur Play-Store-Build).
 *
 * FCM wird seit der Umstellung auf Iris (siehe [com.securechat.app.push.IrisPushProvider])
 * nicht mehr als Push-Transport genutzt – der Server erhält keinen fcm_token mehr, Google
 * liefert dadurch keine Pushes an dieses Gerät aus. Die Klasse bleibt als no-op bestehen,
 * damit die Firebase-Messaging-SDK-Initialisierung (Google Play Services) nicht gegen einen
 * fehlenden FirebaseMessagingService-Eintrag im Manifest läuft; [PushPayloadHandler] bliebe
 * für onMessageReceived weiterhin transport-unabhängig nutzbar, falls FCM je reaktiviert wird.
 */
@AndroidEntryPoint
class LetheFcmService : FirebaseMessagingService() {

    @Inject lateinit var pushPayloadHandler: PushPayloadHandler

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag("LETHE_FCM").d("FCM-Token erneuert, aber nicht registriert (Iris ersetzt FCM).")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        pushPayloadHandler.handle(message.data)
    }
}
