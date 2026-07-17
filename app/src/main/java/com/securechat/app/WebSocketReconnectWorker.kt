package com.securechat.app

import android.content.Context
import androidx.hilt.work.HiltWorker
import timber.log.Timber
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.local.UserDao
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.TokenManager
import com.securechat.app.data.network.WebSocketManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodischer Hintergrund-Worker (alle 15 Minuten via WorkManager).
 *
 * Zweck: Stellt sicher, dass der WebSocket auch nach stundenlanger Inaktivität noch verbunden ist.
 * Falls nicht, wird der ForegroundService neu gestartet UND ein Force-Reconnect ausgelöst.
 *
 * Zusätzlich: Wenn der WS tot war und neue Nachrichten vorliegen, zeigt er eine Benachrichtigung an.
 * Das dient als HTTP-Polling-Fallback für den Fall, dass der WS komplett ausgefallen ist.
 */
@HiltWorker
class WebSocketReconnectWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val webSocketManager: WebSocketManager,
    private val tokenManager: TokenManager,
    private val apiService: ApiService,
    private val notificationHelper: NotificationHelper,
    private val userDao: UserDao,
    private val messageDao: MessageDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!tokenManager.hasToken()) {
            Timber.tag("LETHE_BG").d("ReconnectWorker: Kein Token – übersprungen")
            return Result.success()
        }

        val isConnected = webSocketManager.isConnected()
        Timber.tag("LETHE_BG").d("ReconnectWorker läuft. WS verbunden: $isConnected")

        val wasDisconnected = !isConnected

        if (wasDisconnected) {
            Timber.tag("LETHE_BG").w("ReconnectWorker: WS nicht verbunden – starte Reconnect + Service-Restart")

            // 1. ForegroundService (neu)starten
            try {
                NotificationHandler.start(context)
            } catch (e: Exception) {
                Timber.tag("LETHE_BG").e(e, "ReconnectWorker: Service-Start fehlgeschlagen")
            }

            // 2. Direkt Force-Reconnect über WebSocketManager
            webSocketManager.sendPing()  // löst Reconnect aus wenn tot

            // 3. Als Fallback: HTTP-Poll für neue Nachrichten von allen Kontakten.
            //    Im Only-FCM-Modus ist das WS/HTTP-Benachrichtigungssystem komplett
            //    deaktiviert – Nachrichten-Benachrichtigungen laufen ausschließlich über FCM.
            if (NotificationHelper.isOnlyFcmMode(context)) {
                Timber.tag("LETHE_BG").d("ReconnectWorker: Only-FCM-Modus – HTTP-Fallback-Benachrichtigungen übersprungen")
            } else {
                fetchMissedMessages()
            }
        } else {
            // WS verbunden → nur Ping senden um sicherzustellen dass Verbindung aktiv ist
            webSocketManager.sendPing()
        }

        return Result.success()
    }

    /**
     * HTTP-Fallback: Holt neue Nachrichten vom Server und zeigt Benachrichtigungen an.
     * Wird nur aufgerufen wenn der WS nicht verbunden war.
     */
    private suspend fun fetchMissedMessages() = withContext(Dispatchers.IO) {
        try {
            val contacts = apiService.getContacts()
            if (!contacts.isSuccessful) return@withContext

            val contactList = contacts.body() ?: return@withContext
            val me = userDao.getCurrentUser() ?: return@withContext

            for (contact in contactList) {
                try {
                    // partnerId = userId des Kontakts
                    val msgResponse = apiService.getMessages(contact.partnerId)
                    if (!msgResponse.isSuccessful) continue

                    val messages = msgResponse.body() ?: continue
                    // Nur Nachrichten vom Partner (nicht von uns selbst), die NOCH NICHT in Room sind
                    // Nur Nachrichten für die noch keine Benachrichtigung gezeigt wurde
                    // (isDeliveredAsNotification=0 ODER Nachricht noch nicht in Room)
                    val unnotifiedMessages = messages
                        .filter { msg ->
                            msg.senderId != me.userId && msg.id.isNotBlank()
                        }
                        .filter { msg ->
                            // Prüfe ob die Nachricht bereits als notifiziert markiert ist
                            messageDao.isDeliveredAsNotification(msg.id) == 0
                        }

                    if (unnotifiedMessages.isNotEmpty()) {
                        val last = unnotifiedMessages.last()
                        val senderName = contact.partnerName.ifBlank { contact.partnerFakeNumber }
                        val preview = when (last.mediaType) {
                            "audio" -> "🎙️ Sprachnachricht"
                            "image" -> "📷 Foto"
                            "video" -> "🎬 Video"
                            else    -> last.contentBlob?.take(100) ?: ""
                        }
                        notificationHelper.showMessageNotification(
                            senderId = contact.partnerId,
                            senderName = senderName,
                            senderImageUrl = contact.partnerImage,
                            messagePreview = preview,
                            notificationId = (contact.partnerId.hashCode() and 0x7FFFFFFF) + NotificationHelper.NOTIFICATION_ID_MESSAGE_BASE
                        )
                        // Alle benachrichtigten Nachrichten markieren
                        unnotifiedMessages.forEach { msg ->
                            messageDao.markDeliveredAsNotification(msg.id)
                        }
                        Timber.tag("LETHE_BG").d("Benachrichtigung: $senderName (${unnotifiedMessages.size} neue Nachrichten)")
                    }
                } catch (e: Exception) {
                    Timber.tag("LETHE_BG").e(e, "ReconnectWorker: Fehler bei ${contact.partnerId}")
                }
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_BG").e(e, "ReconnectWorker: fetchMissedMessages fehlgeschlagen")
        }
    }
}
