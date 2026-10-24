package com.securechat.app.push

import com.securechat.app.PushPayloadHandler
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält die persistente Iris-Verbindung ([/iris/connect/{token}]) des Geräts offen - der
 * FCM-unabhängige Zustellweg für Lethe Messenger (beide Flavors, siehe IrisPushProvider).
 * Wird vom NotificationHandler-Foreground-Service verwaltet (ensureConnected()/ping() im
 * selben Ping-Tick wie die normale Chat-WebSocket-Keepalive, disconnect() in onDestroy()).
 */
@Singleton
class IrisConnection @Inject constructor(
    private val client: OkHttpClient,
    private val pushPayloadHandler: PushPayloadHandler,
) {
    private var socket: WebSocket? = null
    private var currentToken: String? = null
    @Volatile private var connected = false

    fun isConnected(): Boolean = connected

    /** Stellt die Verbindung her, falls noch nicht mit genau diesem Token verbunden. */
    fun ensureConnected(token: String?) {
        if (token.isNullOrBlank()) {
            disconnect()
            return
        }
        if (token == currentToken && connected) return
        disconnect()
        currentToken = token
        val request = Request.Builder()
            .url("wss://letheapp.de/iris/connect/$token")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                Timber.tag("LETHE_IRIS").d("Iris-Verbindung aufgebaut.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "pong") return
                try {
                    val json = JsonParser.parseString(text).asJsonObject
                    if (json.get("type")?.asString != "push") return
                    val data = linkedMapOf<String, String>()
                    json.getAsJsonObject("data")?.entrySet()?.forEach { (key, value) ->
                        if (!value.isJsonNull) data[key] = value.asString
                    }
                    json.get("title")?.asString?.let { data["title"] = it }
                    json.get("body")?.asString?.let { data["body"] = it }
                    pushPayloadHandler.handle(data)
                } catch (e: Exception) {
                    Timber.tag("LETHE_IRIS").w(e, "Iris-Payload konnte nicht verarbeitet werden")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                Timber.tag("LETHE_IRIS").w(t, "Iris-Verbindung getrennt")
            }
        })
    }

    /** Hält die Verbindung am Leben; sendet nur, wenn tatsächlich verbunden. */
    fun ping() {
        if (connected) socket?.send("ping")
    }

    fun disconnect() {
        socket?.close(1000, null)
        socket = null
        connected = false
    }
}
