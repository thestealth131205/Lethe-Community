package com.securechat.app.data.network

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.securechat.app.TorServiceWrapper
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tor-Routing-Modi der App.
 *
 * OFF      – Kein Tor; nur Clearnet (Standard).
 * TOR_ONLY – Sämtlicher Netzwerkverkehr läuft über den eingebetteten Tor-SOCKS5-Proxy.
 *            Requests werden automatisch auf die .onion-Adresse umgeleitet.
 */
enum class TorMode { OFF, TOR_ONLY }

/**
 * Singleton-Manager für den eingebetteten Tor-Client (Guardian Project tor-android).
 *
 * Der eingebettete [TorService] stellt unter 127.0.0.1:9050 einen SOCKS5-Proxy bereit.
 * Kein externes Orbot erforderlich – Tor läuft direkt in der App.
 *
 * Der [proxySelector] wird in OkHttpClient eingebunden und wechselt
 * dynamisch zwischen Tor-Proxy und direkter Verbindung je nach [currentMode].
 */
@Singleton
class TorProxyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TorProxyManager"

        /** SOCKS5-Port des eingebetteten Tor-Clients */
        const val TOR_SOCKS_PORT = 9050
        const val TOR_SOCKS_HOST = "127.0.0.1"
    }

    /** Tor SOCKS5-Proxy-Objekt (eingebetteter Tor-Client, lokal) */
    val torProxy: Proxy = Proxy(
        Proxy.Type.SOCKS,
        InetSocketAddress(TOR_SOCKS_HOST, TOR_SOCKS_PORT)
    )

    /** Aktueller Betriebsmodus; Thread-safe über @Volatile */
    @Volatile
    var currentMode: TorMode = TorMode.OFF
        private set

    /** .onion-Adresse des Backends (ohne "http://"-Präfix, nur Hostname) */
    @Volatile
    var onionAddress: String = ""

    /**
     * ProxySelector, der in OkHttpClient eingebunden wird.
     * Gibt im TOR_ONLY-Modus den Tor-SOCKS5-Proxy zurück,
     * sonst eine direkte Verbindung.
     */
    val proxySelector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            return if (currentMode == TorMode.TOR_ONLY) {
                listOf(torProxy)
            } else {
                listOf(Proxy.NO_PROXY)
            }
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            Log.w(TAG, "Verbindungsfehler via Tor-Proxy ($uri): ${ioe?.message}")
        }
    }

    /** Startet den eingebetteten Tor-Service. */
    fun startEmbeddedTor() {
        try {
            val intent = Intent(context, TorServiceWrapper::class.java)
            context.startForegroundService(intent)
            Log.i(TAG, "Eingebetteter Tor-Service gestartet")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Starten des Tor-Service: ${e.message}")
        }
    }

    /** Stoppt den eingebetteten Tor-Service. */
    fun stopEmbeddedTor() {
        try {
            context.stopService(Intent(context, TorServiceWrapper::class.java))
            Log.i(TAG, "Eingebetteter Tor-Service gestoppt")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Stoppen des Tor-Service: ${e.message}")
        }
    }

    /** Ändert den Tor-Modus und startet/stoppt den Tor-Service entsprechend. */
    fun setMode(mode: TorMode) {
        currentMode = mode
        Log.i(TAG, "Tor-Modus gewechselt: $mode" +
            if (mode == TorMode.TOR_ONLY && onionAddress.isNotBlank())
                " (.onion: $onionAddress)" else "")
        when (mode) {
            TorMode.TOR_ONLY -> startEmbeddedTor()
            TorMode.OFF -> stopEmbeddedTor()
        }
    }

    /** Setzt die .onion-Adresse und den Modus (startet/stoppt Tor entsprechend). */
    fun configure(onionAddr: String, mode: TorMode) {
        onionAddress = onionAddr.trim()
        setMode(mode)
    }

    /** Gibt zurück ob Tor aktuell aktiv ist. */
    val isTorActive: Boolean get() = currentMode != TorMode.OFF
}
