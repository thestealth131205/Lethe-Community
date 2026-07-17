package com.securechat.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val Context.speedTestDataStore: DataStore<Preferences> by preferencesDataStore(name = "upload_speed_test")

/**
 * Hintergrund-Worker der alle 5 Minuten einen kurzen Upload-Geschwindigkeitstest durchführt.
 * Nur aktiv wenn KEINE WLAN-Verbindung besteht (nur Mobile Data).
 * Das Ergebnis (Mbps) wird lokal im DataStore gespeichert.
 */
@HiltWorker
class SpeedTestWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        val UPLOAD_SPEED_MBPS = floatPreferencesKey("upload_speed_mbps")
        val SPEED_TEST_TIMESTAMP = longPreferencesKey("speed_test_timestamp")
        private const val TAG = "SpeedTestWorker"
        // 256 KB Testdaten – kleiner Footprint, trotzdem aussagekräftig
        private const val TEST_DATA_SIZE = 256 * 1024
        // Backend-Endpunkt für den Speed-Test (HEAD-Request oder kleiner Upload)
        private const val SPEED_TEST_URL = "https://letheapp.de/api/speed-test"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Nur auf Mobile Data testen, nicht auf WLAN
            if (isOnWifi()) {
                Log.d(TAG, "WLAN verbunden – Speed-Test übersprungen")
                return@withContext Result.success()
            }

            if (!hasMobileData()) {
                Log.d(TAG, "Kein Mobilnetz – Speed-Test übersprungen")
                return@withContext Result.success()
            }

            val speedMbps = measureUploadSpeed()
            if (speedMbps > 0f) {
                appContext.speedTestDataStore.edit { prefs ->
                    prefs[UPLOAD_SPEED_MBPS] = speedMbps
                    prefs[SPEED_TEST_TIMESTAMP] = System.currentTimeMillis()
                }
                Log.d(TAG, "Upload-Geschwindigkeit gemessen: %.2f Mbps".format(speedMbps))
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Speed-Test fehlgeschlagen", e)
            Result.success() // Kein Retry, nächster Zyklus in 5 Min
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun hasMobileData(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private suspend fun measureUploadSpeed(): Float {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        // Zufällige Testdaten generieren
        val testData = ByteArray(TEST_DATA_SIZE).also { java.util.Random().nextBytes(it) }
        val requestBody = testData.toRequestBody("application/octet-stream".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(SPEED_TEST_URL)
            .post(requestBody)
            .build()

        val startTime = System.nanoTime()

        val result = withTimeoutOrNull(30_000L) {
            try {
                val response = client.newCall(request).execute()
                response.close()
                val elapsedNanos = System.nanoTime() - startTime
                val elapsedSeconds = elapsedNanos / 1_000_000_000.0
                if (elapsedSeconds > 0) {
                    // Bytes zu Megabit umrechnen: (bytes * 8) / 1_000_000 / seconds
                    val mbps = (TEST_DATA_SIZE * 8.0 / 1_000_000.0 / elapsedSeconds).toFloat()
                    mbps
                } else 0f
            } catch (e: Exception) {
                Log.w(TAG, "Upload-Test-Request fehlgeschlagen: ${e.message}")
                0f
            }
        }

        return result ?: 0f
    }
}

/** Hilfsfunktionen für den Zugriff auf die gespeicherte Upload-Geschwindigkeit. */
object SpeedTestHelper {
    /** Liest die zuletzt gemessene Upload-Geschwindigkeit (Mbps) aus dem DataStore. */
    suspend fun getLastUploadSpeedMbps(context: Context): Float {
        return try {
            context.speedTestDataStore.data.map { it[SpeedTestWorker.UPLOAD_SPEED_MBPS] ?: -1f }.first()
        } catch (_: Exception) { -1f }
    }

    /** Liest den Zeitstempel der letzten Messung. */
    suspend fun getLastTestTimestamp(context: Context): Long {
        return try {
            context.speedTestDataStore.data.map { it[SpeedTestWorker.SPEED_TEST_TIMESTAMP] ?: 0L }.first()
        } catch (_: Exception) { 0L }
    }

    /**
     * Bestimmt die optimale JPEG-Qualität basierend auf der Upload-Geschwindigkeit.
     * Schnelles Netz → hohe Qualität, langsames Netz → stärkere Komprimierung.
     */
    fun getOptimalImageQuality(speedMbps: Float): Int {
        return when {
            speedMbps < 0f -> 82   // Kein Messwert → Standard
            speedMbps < 0.5f -> 45  // Sehr langsam: starke Komprimierung
            speedMbps < 1.0f -> 55  // Langsam
            speedMbps < 2.0f -> 65  // Mäßig
            speedMbps < 5.0f -> 75  // Gut
            else -> 82              // Schnell: Standard-Qualität
        }
    }

    /**
     * Bestimmt die maximale Bildbreite basierend auf der Upload-Geschwindigkeit.
     */
    fun getOptimalMaxWidth(speedMbps: Float): Int {
        return when {
            speedMbps < 0f -> 1920  // Kein Messwert → Standard
            speedMbps < 0.5f -> 800  // Sehr langsam
            speedMbps < 1.0f -> 1024 // Langsam
            speedMbps < 2.0f -> 1280 // Mäßig
            speedMbps < 5.0f -> 1600 // Gut
            else -> 1920             // Schnell
        }
    }

    /**
     * Bestimmt die optimale Video-Höhe für Transkodierung basierend auf der Geschwindigkeit.
     */
    fun getOptimalVideoHeight(speedMbps: Float): Int {
        return when {
            speedMbps < 0f -> 720   // Kein Messwert → Standard
            speedMbps < 1.0f -> 360  // Sehr langsam
            speedMbps < 2.0f -> 480  // Langsam
            speedMbps < 5.0f -> 720  // Gut
            else -> 720              // Schnell
        }
    }
}
