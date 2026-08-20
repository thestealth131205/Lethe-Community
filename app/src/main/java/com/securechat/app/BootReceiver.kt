package com.securechat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Startet die Hintergrund-Arbeit nach einem Geräte-Neustart oder App-Update erneut.
 *
 * Ohne diesen Receiver läuft nach einem Reboot nichts wieder an, bis der Nutzer die App
 * manuell öffnet – der WebSocket bleibt tot und Nachrichten kommen erst beim nächsten Öffnen.
 *
 * Aus einem BroadcastReceiver darf im Hintergrund kein ForegroundService direkt gestartet
 * werden (Android 12+), daher wird ein WorkManager-Job eingereiht. Der bereits vorhandene
 * WebSocketReconnectWorker startet dann den NotificationHandler-Service und den Reconnect.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",   // HTC/einige OEMs
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.tag("LETHE_BG").i("BootReceiver: $action – Hintergrund-Arbeit wird neu eingereiht")
                val request = OneTimeWorkRequestBuilder<WebSocketReconnectWorker>()
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueue(request)
            }
        }
    }
}
