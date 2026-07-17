package com.securechat.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.securechat.app.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import org.webrtc.PeerConnectionFactory
import timber.log.Timber
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

    override fun onCreate() {
        super.onCreate()

        // Dateibasierter Logger – muss vor allem anderen initialisiert werden
        LetheLogger.init(this)
        LetheLogger.i("APP", "Lethe gestartet (debug=${BuildConfig.DEBUG}, version=${BuildConfig.VERSION_NAME})")

        // Globaler Crash-Handler: fängt unbehandelte Exceptions ab und schreibt sie in die Log-Datei
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )

        if (BuildConfig.DEBUG) {
            // Debug: vollständiges Logging mit Klasse/Zeile
            Timber.plant(Timber.DebugTree())
        } else {
            // Release: kein Logging außer Fehler (Crash-Reporting hier einfügbar)
            Timber.plant(ReleaseTree())
        }
        Timber.tag("LETHE_INIT").i("Lethe gestartet (debug=${BuildConfig.DEBUG})")

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
