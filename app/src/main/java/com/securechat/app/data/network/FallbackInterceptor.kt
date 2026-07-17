package com.securechat.app.data.network

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp-Interceptor für automatischen Fallback und Tor/Onion-Routing.
 *
 * Priorität der Verbindungsversuche:
 *
 * TOR_ONLY-Modus:
 *   1. .onion-Adresse (via Tor-SOCKS5-Proxy, der im OkHttpClient via ProxySelector aktiv ist)
 *   → kein weiterer Fallback (Privatsphäre hat Vorrang)
 *
 * Normalmodus (OFF):
 *   1. Primär-Server (letheapp.de)
 *   2. Home-Server (falls via [setHomeServerUrl] konfiguriert)
 *
 * Bei HTTP-Fehlern (4xx/5xx) wird KEIN Fallback ausgelöst – nur bei
 * Verbindungsfehlern (IOException: Timeout, DNS-Fehler, Server nicht erreichbar).
 */
@Singleton
class FallbackInterceptor @Inject constructor(
    private val torProxyManager: TorProxyManager
) : Interceptor {

    @Volatile private var homeHost: String? = null
    @Volatile private var homeScheme: String? = null

    /** Nutzer-konfigurierter Home-/Zweit-Server. */
    fun setHomeServerUrl(url: String?) = applyUrl(url) { h, s -> homeHost = h; homeScheme = s }

    private fun applyUrl(url: String?, apply: (String?, String?) -> Unit) {
        if (url.isNullOrBlank()) { apply(null, null); return }
        val parsed = url.toHttpUrlOrNull()
        if (parsed != null) apply(parsed.host, parsed.scheme) else apply(null, null)
    }

    private val effectiveBackupHost: String? get() = homeHost
    private val effectiveBackupScheme: String? get() = homeScheme

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        return when (torProxyManager.currentMode) {
            TorMode.TOR_ONLY -> interceptTorOnly(chain, request)
            TorMode.OFF      -> interceptClearnet(chain, request)
        }
    }

    /**
     * TOR_ONLY-Modus:
     * Schreibt den Request-Host auf die .onion-Adresse um.
     * Der OkHttpClient leitet den Traffic bereits via ProxySelector durch Orbot.
     */
    private fun interceptTorOnly(chain: Interceptor.Chain, request: okhttp3.Request): Response {
        val onionHost = torProxyManager.onionAddress.trim()

        if (onionHost.isBlank()) {
            // Keine .onion-Adresse konfiguriert → normaler Request (Tor-Proxy aber aktiv)
            Log.w("FallbackInterceptor", "TOR_ONLY aktiv, aber keine .onion-Adresse konfiguriert.")
            return chain.proceed(request)
        }

        val onionUrl = request.url.newBuilder()
            .scheme("http")   // .onion über Tor = HTTP (Tor selbst verschlüsselt die Verbindung)
            .host(onionHost)
            .port(80)
            .build()

        Log.d("FallbackInterceptor", "Tor-Request → http://$onionHost${request.url.encodedPath}")
        return chain.proceed(request.newBuilder().url(onionUrl).build())
    }

    /**
     * Normalmodus (OFF):
     * Primär-Server versuchen → bei IOException → Backup-Server versuchen.
     */
    private fun interceptClearnet(chain: Interceptor.Chain, request: okhttp3.Request): Response {
        return try {
            chain.proceed(request)
        } catch (primaryException: IOException) {
            val host = effectiveBackupHost
            val scheme = effectiveBackupScheme
            if (host == null || scheme == null) {
                throw primaryException
            }

            val backupUrl = request.url.newBuilder()
                .scheme(scheme)
                .host(host)
                .build()

            Log.w("FallbackInterceptor",
                "Primär-Server nicht erreichbar (${primaryException.message}) – " +
                "Fallback auf $scheme://$host")

            val backupRequest = request.newBuilder().url(backupUrl).build()
            try {
                chain.proceed(backupRequest)
            } catch (backupException: IOException) {
                Log.e("FallbackInterceptor",
                    "Backup-Server ebenfalls nicht erreichbar: ${backupException.message}")
                throw primaryException
            }
        }
    }
}
