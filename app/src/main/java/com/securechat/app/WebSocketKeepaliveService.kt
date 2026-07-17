package com.securechat.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import timber.log.Timber
import com.securechat.app.data.network.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dauerhafter ForegroundService für den WebSocket-Keepalive.
 *
 * Verbesserungen gegenüber der alten Version:
 * - WakeLock verhindert, dass der Service vom System eingefroren wird
 * - Überprüft alle 30s nicht nur Ping, sondern ob WS überhaupt verbunden ist
 * - START_STICKY sorgt dafür, dass Android den Service bei Beendigung neu startet
 * - Häufigere Überprüfung wenn WS tot (10s statt 30s)
 */
@AndroidEntryPoint
class WebSocketKeepaliveService : Service() {

    @Inject
    lateinit var webSocketManager: WebSocketManager

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var pingInterval = PING_INTERVAL_CONNECTED

    companion object {
        const val PING_INTERVAL_CONNECTED    = 30_000L  // 30s wenn verbunden
        const val PING_INTERVAL_DISCONNECTED = 10_000L  // 10s wenn getrennt
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            // WakeLock-Timeout bei JEDEM Durchlauf erneuern. Ohne diese Erneuerung lief der
            // ursprüngliche 60-Minuten-WakeLock aus und der Keepalive-Loop wurde nach langer
            // Laufzeit im Doze-Modus eingefroren → der WebSocket starb unbemerkt (keine
            // Nachrichten / kein Online-Status) bis zum App-Neustart.
            acquireWakeLock()
            val connected = webSocketManager.isConnected()
            if (connected) {
                webSocketManager.sendPing()
                pingInterval = PING_INTERVAL_CONNECTED
                Timber.tag("LETHE_BG").d("KeepaliveService: Ping gesendet – WS verbunden")
            } else {
                // WebSocket tot → sofortigen Reconnect über sendPing() auslösen
                webSocketManager.sendPing()  // sendPing() löst jetzt Reconnect aus wenn WS tot
                pingInterval = PING_INTERVAL_DISCONNECTED
                Timber.tag("LETHE_BG").w("KeepaliveService: WS nicht verbunden – Reconnect ausgelöst")
            }
            handler.postDelayed(this, pingInterval)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(42, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(42, buildNotification())
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_BG").e(e, "KeepaliveService: startForeground fehlgeschlagen")
        }
        acquireWakeLock()
        handler.post(pingRunnable)
        Timber.tag("LETHE_BG").i("KeepaliveService: Service gestartet")
        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val wl = wakeLock ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Lethe:WebSocketKeepalive"
            ).also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            // Vor dem erneuten acquire freigeben, damit der 60-Minuten-Timeout sauber neu
            // gesetzt wird (sonst stapeln sich Timeout-Releaser und der Lock fällt zu früh weg).
            if (wl.isHeld) {
                try { wl.release() } catch (_: Exception) {}
            }
            wl.acquire(60 * 60 * 1000L)  // max 60 Minuten; wird durch den Handler-Loop alle 30s erneuert
        } catch (e: Exception) {
            Timber.tag("LETHE_BG").e(e, "KeepaliveService: WakeLock-Erneuerung fehlgeschlagen")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App aus Recents entfernt → Service trotzdem weiterlaufen lassen
        val restartIntent = Intent(applicationContext, WebSocketKeepaliveService::class.java)
        startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_FOREGROUND)
        .setContentTitle("Lethe")
        .setContentText("Verbindung aktiv")
        .setSmallIcon(R.drawable.ic_notification)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(pingRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        Timber.tag("LETHE_BG").i("KeepaliveService: Service zerstört – wird durch START_STICKY neu gestartet")
        super.onDestroy()
    }
}
