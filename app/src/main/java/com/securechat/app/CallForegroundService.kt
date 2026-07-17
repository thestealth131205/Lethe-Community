package com.securechat.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground Service für laufende Videoanrufe.
 * Hält Mikrofon- und Kamera-Zugriff im Hintergrund aufrecht (Android 14+ / targetSdk 36).
 * Ohne diesen Service blockiert Android 14+ den Mikrofon-Zugriff sobald die App in den Hintergrund geht.
 */
class CallForegroundService : Service() {

    companion object {
        const val ACTION_START        = "START_CALL"
        const val ACTION_STOP         = "STOP_CALL"
        /** Service neu starten und mediaProjection-Typ hinzufügen (vor Bildschirmfreigabe). */
        const val ACTION_SCREEN_SHARE = "START_SCREEN_SHARE"
        private const val NOTIF_ID = 9002
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_FOREGROUND)
            .setContentTitle("Lethe")
            .setContentText(
                if (intent?.action == ACTION_SCREEN_SHARE)
                    "Bildschirmfreigabe läuft …"
                else
                    "Anruf läuft im Hintergrund …"
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        if (intent?.action == ACTION_SCREEN_SHARE)
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        else 0
            startForeground(NOTIF_ID, notif, types)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
