package com.securechat.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import org.torproject.jni.TorService

/**
 * Kompatibilitäts-Wrapper für den eingebetteten Guardian-Project TorService.
 *
 * Problem auf Android 14+ (API 34):
 *   Die ältere tor-android-Bibliothek ruft startForeground(id, notification) (2-Arg-Version)
 *   auf, was auf API 34+ eine SecurityException auslöst, wenn im Manifest ein
 *   foregroundServiceType deklariert ist (hier: dataSync).
 *
 * Fix:
 *   Vor dem super.onStartCommand()-Aufruf wird startForeground() mit dem korrekten
 *   ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC aufgerufen, sodass der Service
 *   bereits als Foreground-Service registriert ist.
 *   Etwaige Exceptions aus TorService.onStartCommand() werden abgefangen,
 *   damit die App nicht abstürzt.
 */
class TorServiceWrapper : TorService() {

    companion object {
        private const val TAG = "TorServiceWrapper"
        private const val COMPAT_CHANNEL_ID = "tor_compat_channel"
        private const val COMPAT_NOTIFICATION_ID = 9051
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Auf API 34+: Foreground-Service-Typ vorab korrekt setzen,
            // bevor TorService intern die veraltete 2-Arg-Variante aufruft.
            promoteToForeground()
        }
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: SecurityException) {
            // SecurityException von TorService.startForeground() auf API 34+
            // (alte 2-Arg-API ohne Typ-Angabe). Service läuft bereits als Foreground.
            Log.w(TAG, "SecurityException in TorService.onStartCommand() abgefangen (API 34+): ${e.message}")
            Service.START_STICKY
        } catch (e: Exception) {
            Log.w(TAG, "Exception in TorService.onStartCommand() abgefangen: ${e.message}")
            Service.START_STICKY
        }
    }

    private fun promoteToForeground() {
        try {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(COMPAT_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        COMPAT_CHANNEL_ID,
                        "Tor Netzwerk",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                )
            }
            val notification = Notification.Builder(this, COMPAT_CHANNEL_ID)
                .setContentTitle("Tor")
                .setContentText("Tor-Netzwerk wird gestartet…")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build()
            startForeground(
                COMPAT_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            Log.w(TAG, "promoteToForeground fehlgeschlagen: ${e.message}")
        }
    }
}
