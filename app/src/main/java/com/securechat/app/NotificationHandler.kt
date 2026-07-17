package com.securechat.app

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.securechat.app.data.network.WebSocketManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Zentraler ForegroundService für WebSocket-Keepalive.
 *
 * - Ping-Loop: alle 30s wenn verbunden, alle 10s wenn getrennt (löst Reconnect aus)
 * - Fallback-Benachrichtigung "Du könntest neue Nachrichten haben" NUR wenn das System
 *   den Dienst beendet (onTaskRemoved) – NICHT aufgrund von Verbindungsabbrüchen
 * - Startet WorkManager-Jobs für belt-and-suspenders-Hintergrundprüfung
 */
@AndroidEntryPoint
class NotificationHandler : Service() {

    @Inject
    lateinit var webSocketManager: WebSocketManager

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val PING_INTERVAL_CONNECTED    = 30_000L  // 30s wenn verbunden
        const val PING_INTERVAL_DISCONNECTED = 10_000L  // 10s wenn getrennt
        const val NOTIFICATION_ID_FOREGROUND = 42
        const val NOTIFICATION_ID_FALLBACK   = 43

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NotificationHandler::class.java))
        }

        fun requestBatteryOptimization(context: Context) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) { /* Einige Geräte unterstützen diesen Intent nicht */ }
            }
        }
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            val connected = webSocketManager.isConnected()
            webSocketManager.sendPing()  // ping wenn verbunden, reconnect-trigger wenn tot
            if (connected) {
                Timber.tag("LETHE_BG").d("NotificationHandler: Ping – WS verbunden")
                handler.postDelayed(this, PING_INTERVAL_CONNECTED)
            } else {
                Timber.tag("LETHE_BG").w("NotificationHandler: WS nicht verbunden – Reconnect-Versuch")
                handler.postDelayed(this, PING_INTERVAL_DISCONNECTED)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID_FOREGROUND, buildForegroundNotification())
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_BG").w("NotificationHandler: startForeground fehlgeschlagen – Service wird beendet: ${e.message}")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        acquireWakeLock()
        handler.post(pingRunnable)
        scheduleWorkManagerJobs()
        Timber.tag("LETHE_BG").i("NotificationHandler: Service gestartet")
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App vom System beendet → Fallback-Benachrichtigung + WorkManager-Neustart
        // startForegroundService() hier wäre auf Android 12+ eine ForegroundServiceStartNotAllowedException
        Timber.tag("LETHE_BG").w("NotificationHandler: App vom System beendet – Fallback-Notification")
        showFallbackNotification()
        // Neustart über WorkManager statt startForegroundService() aus Hintergrund
        val restartRequest = androidx.work.OneTimeWorkRequestBuilder<WebSocketReconnectWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        androidx.work.WorkManager.getInstance(applicationContext)
            .enqueue(restartRequest)
        super.onTaskRemoved(rootIntent)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Lethe:NotificationHandler"
        ).apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L)  // max 60 Minuten; Handler-Loop hält Service aktiv
        }
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_FOREGROUND)
            .setContentTitle("Lethe")
            .setContentText("Verbindung aktiv")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()

    private fun showFallbackNotification() {
        val tapIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID_FALLBACK,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_MESSAGES)
            .setContentTitle("Lethe")
            .setContentText("Du könntest neue Nachrichten haben. Öffne Lethe.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID_FALLBACK, notification)
        Timber.tag("LETHE_BG").i("NotificationHandler: Fallback-Benachrichtigung angezeigt")
    }

    private fun scheduleWorkManagerJobs() {
        val wm = WorkManager.getInstance(this)

        val contactRequest = PeriodicWorkRequestBuilder<ContactRequestWorker>(15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            "contact_request_check",
            ExistingPeriodicWorkPolicy.KEEP,
            contactRequest
        )

        val reconnectRequest = PeriodicWorkRequestBuilder<WebSocketReconnectWorker>(15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            "websocket_reconnect",
            ExistingPeriodicWorkPolicy.KEEP,
            reconnectRequest
        )

        Timber.tag("LETHE_BG").d("NotificationHandler: WorkManager-Jobs geplant")
    }

    // Android 14+: dataSync-Services erhalten onTimeout() wenn das System-Timeout abläuft.
    // Ohne Override wird ForegroundServiceDidNotStopInTimeException geworfen.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        Timber.tag("LETHE_BG").w("NotificationHandler: System-Timeout – Service wird sauber beendet")
        handler.removeCallbacks(pingRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSelf(startId)
    }

    // Android 15+ (API 35): neue Signatur mit fgsType – ohne Override wird nur diese aufgerufen
    @RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.tag("LETHE_BG").w("NotificationHandler: System-Timeout (API 35+) – Service wird sauber beendet")
        handler.removeCallbacks(pingRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(pingRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        Timber.tag("LETHE_BG").i("NotificationHandler: Service zerstört – wird durch START_STICKY neu gestartet")
        super.onDestroy()
    }
}
