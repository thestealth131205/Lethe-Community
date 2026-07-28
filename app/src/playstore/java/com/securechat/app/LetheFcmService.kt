package com.securechat.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.FcmTokenRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Firebase Cloud Messaging Service (nur Play-Store-Build).
 * Empfängt Push-Notifications wenn die App im Hintergrund oder nicht aktiv ist.
 *
 * Die eigentliche Verarbeitung der Payload liegt in [PushPayloadHandler] – transport-
 * unabhängig, damit der FOSS/F-Droid-Build dieselbe Logik über den persistenten
 * WebSocket-Foreground-Service (statt FCM) nutzen kann.
 */
@AndroidEntryPoint
class LetheFcmService : FirebaseMessagingService() {

    @Inject lateinit var apiService: ApiService
    @Inject lateinit var pushPayloadHandler: PushPayloadHandler

    /**
     * Wird aufgerufen wenn sich der FCM-Token erneuert (z.B. nach Neuinstallation).
     * Registriert den neuen Token sofort am Server.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.tag("LETHE_FCM").d("FCM-Token erneuert: ${token.take(20)}…")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                apiService.updateFcmToken(FcmTokenRequest(fcmToken = token, pushKind = "fcm"))
                Timber.tag("LETHE_FCM").d("Neuer FCM-Token erfolgreich am Server registriert.")
            } catch (e: Exception) {
                Timber.tag("LETHE_FCM").e(e, "Token-Update fehlgeschlagen")
            }
        }
    }

    /**
     * Empfängt FCM-Datennachrichten und delegiert an den transport-unabhängigen Handler.
     * Wird immer aufgerufen – auch wenn die App im Hintergrund ist (data-only messages).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        pushPayloadHandler.handle(message.data)
    }
}
