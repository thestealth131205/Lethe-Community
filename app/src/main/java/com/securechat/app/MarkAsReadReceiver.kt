package com.securechat.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.local.UserDao
import com.securechat.app.data.network.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Empfängt die "Als gelesen markieren"-Aktion sowie den Wisch-weg (Dismiss) einer
 * Nachrichten-Benachrichtigung. Markiert alle ausstehenden Nachrichten dieses Chats
 * lokal + serverseitig als zugestellt/gelesen (gleiche Logik wie beim Öffnen des Chats
 * in MainViewModel.onChatOpened) und blendet die Benachrichtigung aus.
 */
@AndroidEntryPoint
class MarkAsReadReceiver : BroadcastReceiver() {

    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var userDao: UserDao

    override fun onReceive(context: Context, intent: Intent) {
        val senderId = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val isGroup = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_GROUP, false)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val myId = userDao.getCurrentUser()?.userId

                if (myId != null) {
                    // Lokal sofort als gelesen markieren (Unread-Indikator verschwindet)
                    if (isGroup) {
                        messageDao.markGroupMessagesRead(senderId, myId)
                    } else {
                        messageDao.markAllRead(senderId, myId)
                    }

                    // Alle noch ausstehenden Nachrichten dieses Chats einzeln als
                    // delivered/gelesen an den Server melden (gleiches Vorgehen wie onChatOpened)
                    val pendingMessages = if (isGroup) {
                        messageDao.getUnreadGroupMessages(senderId, myId)
                    } else {
                        messageDao.getUnreadMessagesFrom(senderId, myId)
                    }
                    pendingMessages.filter { it.deliveryStatus < 2 && !it.messageId.isNullOrBlank() }.forEach { msg ->
                        webSocketManager.sendMessage(
                            "delivered", senderId, mapOf("message_id" to msg.messageId!!)
                        )
                    }
                    pendingMessages.forEach { msg ->
                        messageDao.updateDeliveryStatus(msg.localId, 3)
                        if (!msg.messageId.isNullOrBlank()) {
                            webSocketManager.sendMessage(
                                "read_receipt", senderId, mapOf("message_id" to msg.messageId)
                            )
                        }
                    }
                }

                // Sammel-Lesebestätigung an den Absender (Legacy-Kompatibilität, nur 1:1)
                if (!isGroup) {
                    webSocketManager.sendMessage(
                        "read_receipt", senderId,
                        mapOf("sender_id" to senderId)
                    )
                }

                // Benachrichtigung ausblenden
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
