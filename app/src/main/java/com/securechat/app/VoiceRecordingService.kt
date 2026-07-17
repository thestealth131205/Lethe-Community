package com.securechat.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

/**
 * Foreground Service für Sprachnachrichten-Aufnahme.
 *
 * Auf Android 14+ (Xiaomi/MIUI HyperOS) kann der Mikrofon-Datenstrom
 * bei reinem WakeLock in seltenen Fällen trotzdem unterbrochen werden,
 * wenn das System die App in einen eingeschränkten Hintergrundmodus
 * versetzt. Dieser Service hält den Mikrofon-Foreground-Service-Typ
 * als Absicherung; er wird gestartet wenn startRecording() aufgerufen
 * wird und gestoppt wenn die Aufnahme endet.
 *
 * Manifest-Deklaration: android:foregroundServiceType="microphone"
 */
class VoiceRecordingService : Service() {

    companion object {
        const val ACTION_START = "START_RECORDING"
        const val ACTION_STOP  = "STOP_RECORDING"

        /** Notification-Channel-ID — muss mit dem in NotificationHandler erzeugten Kanal übereinstimmen. */
        private const val CHANNEL_ID = "lethe_foreground"
        private const val NOTIF_ID   = 9002
    }

    override fun onCreate() {
        super.onCreate()
        // startForeground() so früh wie möglich aufrufen, da der 5s-Timer schon
        // bei startForegroundService() im Aufrufer beginnt – nicht erst bei onStartCommand().
        startForegroundRecording()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    private fun startForegroundRecording() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aufnahme läuft")
            .setContentText("Sprachnachricht wird aufgezeichnet…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            Timber.tag("LETHE_AUDIO").i("VoiceRecordingService Foreground gestartet")
        } catch (e: Exception) {
            Timber.tag("LETHE_AUDIO").e(e, "VoiceRecordingService: startForeground fehlgeschlagen")
            // Ohne startForeground() würde Android den Service nach 5s mit ANR beenden.
            // stopSelf() signalisiert dem System, dass wir uns selbst beenden.
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("LETHE_AUDIO").i("VoiceRecordingService gestoppt")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
