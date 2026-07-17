package com.securechat.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.securechat.app.data.local.MessageDao
import com.securechat.app.data.local.MessageEntity
import com.securechat.app.data.local.UserDao
import com.securechat.app.data.network.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Empfängt den Antwort-Intent aus einer Nachrichten-Benachrichtigung (Inline Reply).
 * Speichert die Nachricht lokal, sendet sie via WebSocket und
 * aktualisiert die Benachrichtigung mit der gesendeten Nachricht (für Wear OS Feedback).
 */
@AndroidEntryPoint
class MessageReplyReceiver : BroadcastReceiver() {

    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var messageDao: MessageDao
    @Inject lateinit var userDao: UserDao

    override fun onReceive(context: Context, intent: Intent) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()?.trim()
            ?: return

        val receiverId = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_ID) ?: return
        val senderName = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_NAME) ?: receiverId
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val isGroup = intent.getBooleanExtra(NotificationHelper.EXTRA_IS_GROUP, false)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val me = userDao.getCurrentUser() ?: return@launch
                val clientId = UUID.randomUUID().toString()

                // Nachricht lokal speichern (mit clientMessageId für DEDUP-2)
                messageDao.insertMessage(
                    MessageEntity(
                        chatId = receiverId,
                        senderId = me.userId,
                        receiverId = receiverId,
                        content = replyText,
                        timestamp = System.currentTimeMillis(),
                        isSent = false,
                        clientMessageId = clientId,
                        deliveryStatus = 0
                    )
                )

                // Via WebSocket senden (clientMessageId im Payload → Server-ACK + DEDUP-2)
                if (isGroup) {
                    webSocketManager.sendMessage(
                        "group_message", receiverId,
                        mapOf(
                            "content_blob" to replyText,
                            "media_type" to "text",
                            "group_id" to receiverId,
                            "client_message_id" to clientId
                        )
                    )
                } else {
                    webSocketManager.sendMessage(
                        "message", receiverId,
                        mapOf(
                            "content_blob" to replyText,
                            "media_type" to "text",
                            "client_message_id" to clientId
                        )
                    )
                }

                // Benachrichtigung mit gesendeter Nachricht aktualisieren (Wear OS Feedback)
                val mePerson = Person.Builder().setName("Du").setKey("me").build()
                val senderPerson = Person.Builder().setName(senderName).setKey(receiverId).build()

                val channelId = if (isGroup) NotificationHelper.CHANNEL_ID_GROUPS else NotificationHelper.CHANNEL_ID_MESSAGES
                val updatedStyle = NotificationCompat.MessagingStyle(mePerson)
                    .setConversationTitle(senderName)
                    .setGroupConversation(isGroup)
                    .addMessage(replyText, System.currentTimeMillis(), null as Person?) // null = "ich"

                val updatedNotification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(senderName)
                    .setContentText("Du: $replyText")
                    .setStyle(updatedStyle)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setAutoCancel(true)
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notificationId, updatedNotification)

            } finally {
                pendingResult.finish()
            }
        }
    }
}
