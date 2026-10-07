package com.securechat.app

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.securechat.app.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import org.webrtc.PeerConnectionFactory
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class SecureChatApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Coil-ImageLoader aus dem Hilt-Graph. Hilt injiziert dieses Feld
     * während super.onCreate(), sodass es in newImageLoader() verfügbar ist.
     */
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var castDiscoveryManager: com.securechat.app.cast.CastDiscoveryManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Wird von Coil aufgerufen, bevor das erste Bild geladen wird.
     * Gibt den vorkonfigurierten Singleton-ImageLoader zurück.
     */
    override fun newImageLoader(): ImageLoader = imageLoader

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // Logger + Crash-Handler MÜSSEN vor super.onCreate() stehen: bei @HiltAndroidApp
        // löst super.onCreate() die Injection der @Inject-Felder (workerFactory, imageLoader,
        // castDiscoveryManager) aus – stürzt dabei etwas ab, wäre das sonst ein nicht
        // erfasster Absturz VOR der bisherigen Registrierung in onCreate().
        LetheLogger.init(this)
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )
    }

    override fun onCreate() {
        super.onCreate()

        LetheLogger.i("APP", "Lethe gestartet (debug=${BuildConfig.DEBUG}, version=${BuildConfig.VERSION_NAME})")

        // Defensive: Beim App-Start läuft NIE ein Anruf. Blieb der AudioManager nach einem
        // abgestürzten Videoanruf im MODE_IN_COMMUNICATION hängen (die Cleanup-Routine in
        // WebRtcClient.dispose() lief wegen des Crashs nie), denkt das System dauerhaft, es
        // führe ein Telefonat → gesamter Ton kommt aus der Hörmuschel. Zurücksetzen auf Normal.
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    if (audioManager.isBluetoothScoOn) {
                        audioManager.stopBluetoothSco()
                        @Suppress("DEPRECATION")
                        audioManager.isBluetoothScoOn = false
                    }
                }
                audioManager.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                LetheLogger.i("APP", "AudioManager-Modus nach Absturz zurückgesetzt (war nicht NORMAL)")
            }
        } catch (t: Throwable) {
            LetheLogger.e("APP", "AudioManager-Reset beim Start fehlgeschlagen", t)
        }

        if (BuildConfig.DEBUG) {
            // Debug: vollständiges Logging mit Klasse/Zeile
            Timber.plant(Timber.DebugTree())
        } else {
            // Release: kein Logging außer Fehler (Crash-Reporting hier einfügbar)
            Timber.plant(ReleaseTree())
        }
        Timber.tag("LETHE_INIT").i("Lethe gestartet (debug=${BuildConfig.DEBUG})")

        clearCacheOnceForNewVersion()

        // Cast-Discovery sofort starten, damit Geräte bereits gefunden sind
        castDiscoveryManager.startDiscovery()

        // WebRTC-Factory einmalig beim App-Start initialisieren (lädt native Bibliotheken, ~200–500 ms).
        // Wird bei Anruf-Start nicht nochmals ausgeführt (PeerConnectionFactory.initialize ist idempotent).
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
    }

    /**
     * Löscht beim allerersten Start einer neuen App-Version einmalig den kompletten
     * Medien- und Bild-Cache (media_cache, osmdroid-Kartenkacheln, Coil-Bildcache).
     * Grund: angesammelter Cache aus Vorversionen soll nicht unbegrenzt weiterwachsen,
     * ohne dass Bestandsnutzer dafür manuell etwas tun müssen. Läuft im Hintergrund,
     * damit der App-Start nicht durch potenziell großen Cache (bis 300 MB) blockiert.
     */
    private fun clearCacheOnceForNewVersion() {
        val prefs = getSharedPreferences("lethe_app_prefs", Context.MODE_PRIVATE)
        val lastClearedVersionCode = prefs.getInt("cache_cleared_version_code", -1)
        if (lastClearedVersionCode == BuildConfig.VERSION_CODE) return

        Thread {
            try {
                File(filesDir, "media_cache").deleteRecursively()
                File(filesDir, "osmdroid/tiles").deleteRecursively()
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
                Timber.tag("LETHE_CACHE").i(
                    "Einmaliger Cache-Reset für Version ${BuildConfig.VERSION_CODE} abgeschlossen"
                )
            } catch (e: Exception) {
                Timber.tag("LETHE_CACHE").e(e, "Einmaliger Cache-Reset fehlgeschlagen")
            } finally {
                prefs.edit().putInt("cache_cleared_version_code", BuildConfig.VERSION_CODE).apply()
            }
        }.start()
    }

    /** Release-Tree: unterdrückt alle Logs außer Fehler/Warnungen. */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= Log.WARN) {
                // Hier könnte ein Crash-Reporter (z.B. Firebase Crashlytics) eingebunden werden
                // Crashlytics.logException(t)
            }
            // Kein Output in Release
        }
    }
}
