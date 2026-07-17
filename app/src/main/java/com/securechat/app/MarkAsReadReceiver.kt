package com.securechat.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securechat.app.data.network.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Empfängt die "Als gelesen markieren"-Aktion aus einer Benachrichtigung.
 * Schickt eine read_receipt via WebSocket und blendet die Benachrichtigung aus.
 * Wird automatisch von Wear OS / Android Auto beim Wischen ausgelöst.
 */
@AndroidEntryPoint
class MarkAsReadReceiver : BroadcastReceiver() {

    @Inject lateinit var webSocketManager: WebSocketManager

    override fun onReceive(context: Context, intent: Intent) {
        val senderId = intent.getStringExtra(NotificationHelper.EXTRA_SENDER_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Lesebestätigung an den Absender schicken
                webSocketManager.sendMessage(
                    "read_receipt", senderId,
                    mapOf("sender_id" to senderId)
                )

                // Benachrichtigung ausblenden
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
