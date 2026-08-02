package com.securechat.app.data.network

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val client: OkHttpClient,
    private val tokenManager: TokenManager
) {
    private val primaryBaseUrl: String = "wss://letheapp.de/ws/"
    @Volatile private var backupBaseUrl: String? = null
    @Volatile private var usingBackup: Boolean = false

    private val currentBaseUrl: String
        get() = if (usingBackup) backupBaseUrl ?: primaryBaseUrl else primaryBaseUrl

    /** Nutzer-konfigurierter Home-Server als Fallback (nur nach 3 Fehlversuchen auf Primary). */
    fun setHomeServerUrl(httpUrl: String?) {
        val converted = httpUrl?.toWsUrl()
        if (converted != null) backupBaseUrl = converted else if (backupBaseUrl == null) backupBaseUrl = null
    }

    private fun String.toWsUrl(): String? {
        if (isBlank()) return null
        val normalized = when {
            startsWith("wss://") || startsWith("ws://") -> this
            startsWith("https://") -> replace("https://", "wss://")
            startsWith("http://") -> replace("http://", "ws://")
            else -> "wss://$this"
        }
        return normalized.trimEnd('/') + "/ws/"
    }

    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Für Auto-Reconnect: aktive User-ID, Versuchszähler und Ausfallzeit-Tracking
    @Volatile private var activeUserId: String? = null
    @Volatile private var reconnectAttempts = 0
    @Volatile private var firstFailureTime: Long = 0L  // Zeitstempel des ersten Fehlers (0 = kein aktiver Ausfall)

    // Präsenz-Status der aktuellen Verbindung: true = aktive Vordergrund-Session (Bildschirm an),
    // false = Hintergrund/Keepalive-Verbindung. Wird als ?active=-Query-Param mitgeschickt, damit der
    // Server is_online NICHT fälschlich auf "online" setzt nur weil der Keepalive-WS im Hintergrund
    // (Display aus / App im Hintergrund) reconnectet. Default true (Login passiert im Vordergrund).
    @Volatile private var presenceActive: Boolean = true

    /** Setzt den Präsenz-Status für künftige (Re-)Connects. */
    fun setPresenceActive(active: Boolean) {
        presenceActive = active
    }

    // Puffer für Nachrichten die offline entstanden sind (werden bei Reconnect gesendet)
    private val pendingQueue = ConcurrentLinkedQueue<String>()

    // Tracking von clientMessageIds im PendingQueue (verhindert Doppel-Enqueue)
    private val pendingClientIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // In-flight: gesendete Nachrichten die noch kein message_ack erhalten haben
    // Bei Disconnect → werden in pendingQueue zurückverschoben (server dedup verhindert Duplikate)
    private val inFlightMessages = ConcurrentHashMap<String, String>() // clientMessageId → json
    // Schützt Snapshot+Clear von inFlightMessages: requeueInFlight() kann von mehreren Threads
    // gleichzeitig aufgerufen werden (onClosed/onFailure via OkHttp-Dispatcher, startWriter() via
    // Coroutine), und onOpen() ruft parallel clear() auf. Ein clear() während entries.toList()
    // iteriert kann bei ConcurrentHashMap zu NoSuchElementException führen → alle Zugriffe sperren.
    private val inFlightLock = Any()

    // ── Prioritäts-Sende-Queue (Online-Draht-Puffer) ───────────────────────────────
    // HIGH überholt NORMAL. Innerhalb einer Klasse gilt FIFO über die Sequenz-Nummer.
    // NORMAL = message/group_message (In-Flight-getrackt, Reihenfolge MUSS erhalten bleiben).
    // HIGH   = alle Echtzeit-/Steuer-Frames (typing, receipts, delivered, call/webrtc-Signaling,
    //          Key-Sync, ping, Spiel-/Sync-Events). Dürfen NORMAL-Bursts überholen.
    private enum class SendPriority { HIGH, NORMAL }

    private data class OutFrame(
        val json: String,
        val priority: SendPriority,
        val clientId: String?,   // nur für NORMAL (message/group_message) → In-Flight-Tracking
        val bufferable: Boolean, // true: bei Sende-Fehler in pendingQueue puffern (sendMessage-Verhalten)
                                 // false: bei Fehler verwerfen (sendRaw/ping – wie bisher)
        val seq: Long
    )

    private val normalTypes = setOf("message", "group_message")
    private fun priorityForType(type: String): SendPriority =
        if (type in normalTypes) SendPriority.NORMAL else SendPriority.HIGH

    // Monotone Sequenz für FIFO-Ordering innerhalb einer Prioritätsklasse
    private val frameSeq = AtomicLong(0L)

    // Der eigentliche Draht-Puffer. Comparator: HIGH (ordinal 0) vor NORMAL (ordinal 1),
    // dann aufsteigende Sequenz → FIFO innerhalb der Klasse.
    private val sendQueue = PriorityBlockingQueue<OutFrame>(
        64,
        compareBy<OutFrame>({ it.priority.ordinal }, { it.seq })
    )

    // Einzelner Writer-Consumer → serialisiert alle ws.send()-Aufrufe, keine Races.
    @Volatile private var writerJob: Job? = null

    // Stream für eingehende Events (Chat, Calls, System-Nachrichten)
    private val _incomingMessages = MutableSharedFlow<WebSocketMessage>(replay = 0)
    val incomingMessages: SharedFlow<WebSocketMessage> = _incomingMessages.asSharedFlow()

    // Status der Verbindung für die UI
    private val _connectionStatus = MutableSharedFlow<Boolean>(replay = 1)
    val connectionStatus: SharedFlow<Boolean> = _connectionStatus.asSharedFlow()

    fun connect(userId: String) {
        activeUserId = userId
        reconnectAttempts = 0
        firstFailureTime = 0L
        usingBackup = false
        startWriter()
        doConnect(userId)
    }

    private fun doConnect(userId: String) {
        if (webSocket != null) {
            Log.d("WebSocket", "Bereits verbunden.")
            return
        }

        val token = tokenManager.getToken()
        val base = currentBaseUrl
        val activeFlag = if (presenceActive) "1" else "0"
        val url = if (token != null) "$base$userId?token=$token&active=$activeFlag" else "$base$userId?active=$activeFlag"
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            Log.e("WebSocket", "Ungültige WebSocket-URL '$url', falle auf Primary zurück: ${e.message}")
            backupBaseUrl = null
            val fallbackUrl = if (token != null) "$primaryBaseUrl$userId?token=$token&active=$activeFlag" else "$primaryBaseUrl$userId?active=$activeFlag"
            Request.Builder().url(fallbackUrl).build()
        }
        Log.d("WebSocket", "Connecting to: $url (Versuch ${reconnectAttempts + 1})")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected – flushe ${pendingQueue.size} gepufferte Nachrichten")
                reconnectAttempts = 0
                firstFailureTime = 0L
                synchronized(inFlightLock) { inFlightMessages.clear() } // Safety: in-flight wurde bereits bei Disconnect in pending verschoben
                // Offline-Backlog in die Sende-Queue einspeisen (als NORMAL, FIFO über seq beibehalten)
                // statt direkt zu senden → einheitlicher Draht über den einzelnen Writer-Consumer.
                while (pendingQueue.isNotEmpty()) {
                    val queued = pendingQueue.poll() ?: break
                    sendQueue.add(OutFrame(queued, SendPriority.NORMAL, null, true, frameSeq.incrementAndGet()))
                    Log.d("WebSocket", "Backlog eingereiht: $queued")
                }
                // IDs nach dem Flushen freigeben — neue Nachrichten können wieder getracked werden
                pendingClientIds.clear()
                scope.launch { _connectionStatus.emit(true) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
                try {
                    val message = gson.fromJson(text, WebSocketMessage::class.java)
                    // message_ack: Nachricht erfolgreich vom Server gespeichert → aus in-flight entfernen
                    if (message.type == "message_ack") {
                        val payload = message.payload
                        if (payload is Map<*, *>) {
                            val clientId = payload["client_message_id"] as? String
                            if (clientId != null) {
                                inFlightMessages.remove(clientId)
                                Log.d("WebSocket", "message_ack: in-flight entfernt für $clientId")
                            }
                        }
                    }
                    scope.launch { _incomingMessages.emit(message) }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Parsing Error: ${e.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closed: code=$code reason=$reason")
                this@WebSocketManager.webSocket = null
                requeueInFlight()
                scope.launch { _connectionStatus.emit(false) }
                // Code 1013: Backup-Server signalisiert dass Primary wieder online ist
                if (code == 1013 && usingBackup) {
                    Log.d("WebSocket", "Code 1013: Primary wieder online – wechsle zurück")
                    usingBackup = false
                    reconnectAttempts = 0
                    firstFailureTime = 0L
                }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Failure: ${t.message}")
                this@WebSocketManager.webSocket = null
                requeueInFlight()
                scope.launch { _connectionStatus.emit(false) }
                scheduleReconnect()
            }
        })
    }

    /** Verschiebt alle noch nicht bestätigten in-flight Nachrichten zurück in die pending queue.
     *  Wird bei onClosed/onFailure aufgerufen. Server-seitiges client_message_id Dedup verhindert
     *  doppelte Speicherung falls der Server die Nachricht doch noch erhalten hat. */
    private fun requeueInFlight() {
        val entries = synchronized(inFlightLock) {
            val snapshot = inFlightMessages.entries.map { it.key to it.value }
            inFlightMessages.clear()
            snapshot
        }
        if (entries.isEmpty()) return
        Log.w("WebSocket", "requeueInFlight: ${entries.size} in-flight Nachrichten zurück in pending queue")
        for ((clientId, json) in entries) {
            if (pendingClientIds.add(clientId)) {
                pendingQueue.add(json)
            }
        }
    }

    /** Puffert einen nicht gesendeten Frame zurück in die (Offline-)pendingQueue.
     *  Nur für bufferable-Frames (message/group_message). NORMAL nutzt clientId-Dedup,
     *  nicht-bufferable Frames (sendRaw/ping) werden wie bisher verworfen. */
    private fun bufferUnsent(frame: OutFrame) {
        if (!frame.bufferable) return
        if (frame.clientId == null || pendingClientIds.add(frame.clientId)) {
            pendingQueue.add(frame.json)
        }
    }

    /** Startet den einzelnen Writer-Consumer (idempotent). Läuft über den gesamten
     *  Verbindungs-Lebenszyklus (überdauert Reconnects), wird nur in disconnect() beendet. */
    private fun startWriter() {
        if (writerJob?.isActive == true) return
        writerJob = scope.launch {
            while (isActive) {
                val frame = sendQueue.take()   // blockiert bis ein Frame verfügbar ist
                val ws = webSocket
                if (ws == null) {
                    // Verbindung fiel zwischen Enqueue und Take weg → Frame nicht verwerfen.
                    bufferUnsent(frame)
                    continue
                }
                // In-Flight erst beim TATSÄCHLICHEN Senden markieren (nicht beim Enqueue),
                // damit ACK/Requeue konsistent bleibt.
                if (frame.clientId != null) inFlightMessages[frame.clientId] = frame.json
                val sent = try { ws.send(frame.json) } catch (_: Exception) { false }
                if (sent) {
                    Log.d("WebSocket", "Sent[${frame.priority}]: ${frame.json}")
                } else {
                    // WS-Verbindung ist tot (send returned false) → puffern + Reconnect anstoßen
                    Log.w("WebSocket", "send() returned false – WS tot, requeue + reconnect")
                    if (frame.clientId != null) inFlightMessages.remove(frame.clientId)
                    bufferUnsent(frame)
                    this@WebSocketManager.webSocket = null
                    requeueInFlight()
                    _connectionStatus.emit(false)
                    val userId = activeUserId
                    if (userId != null) doConnect(userId)
                }
            }
        }
    }

    private fun scheduleReconnect() {
        val userId = activeUserId ?: return   // disconnect() wurde explizit aufgerufen → kein Reconnect
        reconnectAttempts++
        // Ersten Fehler-Zeitstempel setzen
        if (firstFailureTime == 0L) {
            firstFailureTime = System.currentTimeMillis()
        }
        // Nach 2 Minuten kontinuierlicher Ausfallzeit auf Backup-Server wechseln (falls konfiguriert)
        val failingForMs = System.currentTimeMillis() - firstFailureTime
        if (!usingBackup && backupBaseUrl != null && failingForMs >= 2 * 60 * 1000L) {
            usingBackup = true
            Log.d("WebSocket", "Wechsle zu Backup-Server nach ${failingForMs / 1000}s Ausfallzeit")
        }
        // Exponentieller Backoff: 2s, 4s, 8s, 16s, max 30s
        val delayMs = minOf(2000L * reconnectAttempts, 30_000L)
        Log.d("WebSocket", "Reconnect in ${delayMs}ms (Versuch $reconnectAttempts, backup=$usingBackup, ausfallzeit=${failingForMs / 1000}s)")
        scope.launch {
            delay(delayMs)
            if (activeUserId == userId) doConnect(userId)
        }
    }

    fun disconnect() {
        activeUserId = null   // verhindert Auto-Reconnect
        reconnectAttempts = 0
        writerJob?.cancel()
        writerJob = null
        // Noch nicht gesendete Frames sichern (bufferable → pendingQueue), Rest verwerfen.
        while (true) { val f = sendQueue.poll() ?: break; bufferUnsent(f) }
        webSocket?.close(1000, "App closed")
        webSocket = null
        scope.launch { _connectionStatus.emit(false) }
    }

    fun sendMessage(type: String, receiverId: String, payload: Any) {
        val msgMap = mapOf(
            "type" to type,
            "receiver_id" to receiverId,
            "payload" to payload
        )
        val json = gson.toJson(msgMap)

        // Dedup: clientMessageId tracken um Doppel-Enqueue im Offline-Fall zu verhindern.
        // Auch group_message tracken – sonst würde eine bei einem Verbindungsabriss noch nicht
        // bestätigte Gruppen-Nachricht NICHT via requeueInFlight nachgereicht und ginge verloren
        // (sie käme erst nach App-Neustart – und via retryPendingMessages fälschlich als 1:1 – an).
        val clientId = if (type == "message" || type == "group_message") {
            (payload as? Map<*, *>)?.get("client_message_id") as? String
        } else null

        val ws = webSocket
        if (ws != null) {
            // Online: in die Prioritäts-Queue einreihen. Der einzelne Writer-Consumer sendet
            // tatsächlich, markiert In-Flight und behandelt send()==false + Reconnect.
            sendQueue.add(OutFrame(json, priorityForType(type), clientId, true, frameSeq.incrementAndGet()))
        } else {
            // Offline: prüfen ob diese clientMessageId schon in der Queue ist
            if (clientId != null && pendingClientIds.contains(clientId)) {
                Log.w("WebSocket", "Doppel-Enqueue verhindert für clientId: $clientId")
                return
            }
            if (clientId != null) pendingClientIds.add(clientId)
            pendingQueue.add(json)
            Log.w("WebSocket", "Offline – gepuffert (${pendingQueue.size} ausstehend): $json")
        }
    }

    /**
     * Sendet ein rohes JSON-Objekt über den WebSocket (ohne receiver_id Wrapper).
     * Wird für Key-Sync Responses und andere direkte Events verwendet.
     */
    fun sendRaw(data: Map<String, Any?>) {
        val json = gson.toJson(data)
        val ws = webSocket
        if (ws != null) {
            // HIGH-Priorität, non-bufferable (bei Sende-Fehler verworfen – wie bisher)
            sendQueue.add(OutFrame(json, SendPriority.HIGH, null, false, frameSeq.incrementAndGet()))
        } else {
            Log.w("WebSocket", "SendRaw: WS nicht verbunden – verworfen")
        }
    }

    /** Liefert true wenn gerade eine aktive WebSocket-Verbindung besteht. */
    fun isConnected(): Boolean = webSocket != null

    /** Gibt die aktive User-ID zurück (null wenn nicht eingeloggt oder explizit getrennt). */
    fun getActiveUserId(): String? = activeUserId

    /**
     * Sendet einen Ping-Frame. Wenn der WebSocket nicht verbunden ist aber
     * eine User-ID aktiv ist, wird sofort ein Reconnect-Versuch gestartet.
     */
    fun sendPing() {
        val ws = webSocket
        if (ws != null) {
            // HIGH-Priorität, non-bufferable (ein veralteter Ping ist wertlos)
            sendQueue.add(OutFrame("{\"type\":\"ping\"}", SendPriority.HIGH, null, false, frameSeq.incrementAndGet()))
        } else {
            // WS ist tot, aber wir sollten verbunden sein → sofort reconnecten
            val userId = activeUserId
            if (userId != null) {
                Log.w("WebSocket", "Ping: WS nicht verbunden – starte sofortigen Reconnect")
                scope.launch { doConnect(userId) }
            }
        }
    }

    /**
     * Erzwingt einen Reconnect (für WorkManager-Worker und NetworkCallback).
     * Trennt die bestehende Verbindung sicher und baut neu auf.
     */
    fun forceReconnect() {
        val userId = activeUserId ?: return
        Log.d("WebSocket", "forceReconnect() aufgerufen für userId=$userId")
        webSocket?.close(1000, "force_reconnect")
        webSocket = null
        scope.launch {
            delay(500)
            doConnect(userId)
        }
    }
}