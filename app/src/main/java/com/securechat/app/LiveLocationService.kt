package com.securechat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class LiveLocationService : Service() {

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OR_RESUME -> {
                val receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID) ?: ""
                val durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 30 * 60 * 1000L)
                _activeReceiverId = receiverId
                _isRunning.tryEmit(true)
                startForegroundNotification()
                startLocationUpdates()
                handler.removeCallbacks(stopRunnable)
                handler.postDelayed(stopRunnable, durationMs)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "live_location_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Live-Standort",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Live-Standort teilen" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0,
            Intent(this, LiveLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live-Standort wird geteilt")
            .setContentText("Tippe um das Teilen zu beenden")
            .setSmallIcon(R.drawable.ic_notification)
            .addAction(0, "Beenden", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: run { stopSelf(); return }
        locationManager = lm
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val rid = _activeReceiverId
                if (rid.isNotEmpty()) {
                    _locationUpdates.tryEmit(LocationUpdate(location.latitude, location.longitude, rid))
                }
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Erforderlich für ältere API-Level, keine Logik nötig")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        locationListener = listener
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> { stopSelf(); return }
        }
        try {
            // 5 s Mindestintervall, 0 m Mindestdistanz: periodischer Heartbeat auch im
            // Stillstand. Sonst feuert onLocationChanged bei stehendem Gerät NIE (Distanzfilter),
            // der Empfänger bekommt keine Updates und seine Anzeige fällt nach 60 s auf die
            // veraltete Position aus der ursprünglichen Chat-Nachricht zurück.
            lm.requestLocationUpdates(provider, 5000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
        _activeReceiverId = ""
        _isRunning.tryEmit(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_OR_RESUME = "com.securechat.app.LIVE_LOC_START"
        const val ACTION_STOP = "com.securechat.app.LIVE_LOC_STOP"
        const val EXTRA_RECEIVER_ID = "receiver_id"
        const val EXTRA_DURATION_MS = "duration_ms"
        private const val NOTIFICATION_ID = 8901

        private val _locationUpdates = MutableSharedFlow<LocationUpdate>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val locationUpdates = _locationUpdates.asSharedFlow()

        private val _isRunning = MutableSharedFlow<Boolean>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        ).also { it.tryEmit(false) }
        val isRunning = _isRunning.asSharedFlow()

        private var _activeReceiverId: String = ""
        val activeReceiverId: String get() = _activeReceiverId

        data class LocationUpdate(val lat: Double, val lng: Double, val receiverId: String)
    }
}
