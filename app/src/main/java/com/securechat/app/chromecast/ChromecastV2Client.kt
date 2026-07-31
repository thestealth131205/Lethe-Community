package com.securechat.app.chromecast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Google-freier Chromecast-Client (CASTV2-Protokoll) — Ersatz für das proprietäre
 * Google-Cast-SDK, damit die Cast-Funktion auch im FOSS/F-Droid-Build läuft.
 *
 * - Geräte-Suche über Androids eingebauten [NsdManager] (mDNS `_googlecast._tcp`).
 * - Steuerung per protobuf-Frames (hand-kodiert) über TLS auf Port 8009.
 * - Namespaces: connection / heartbeat / receiver / media + Custom-Channel.
 *
 * Die öffentliche API verwendet ausschließlich Kotlin-/Primitiv-Typen (keine
 * Google-Klassen), damit der F-Droid-Scanner keine Play-Services findet.
 *
 * ISOLIERT: Wird (Phase 1) noch von keiner Stelle referenziert; Verdrahtung folgt
 * in den nächsten Phasen (CastDiscoveryManager / CastManager / Geräte-Picker-UI).
 */
@Singleton
class ChromecastV2Client @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ---- Öffentliches Datenmodell ------------------------------------------------

    data class CastDevice(
        val id: String,
        val name: String,
        val host: String,
        val port: Int
    )

    /** Ein Titel für die Cast-Warteschlange (Google-frei). */
    data class QueueItem(
        val url: String,
        val contentType: String,
        val title: String? = null,
        val artist: String? = null,
        val coverUrl: String? = null,
        val live: Boolean = false
    )

    // ---- Zustands-Flows (spiegeln die bisherige Cast-API) ------------------------

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _currentContentId = MutableStateFlow<String?>(null)
    val currentContentId: StateFlow<String?> = _currentContentId

    // ---- Callbacks ---------------------------------------------------------------

    /** Wird aufgerufen sobald der Receiver gestartet und verbunden ist. */
    var onConnected: (() -> Unit)? = null

    /** Wird aufgerufen wenn der Verbindungs-/Launch-Versuch fehlschlägt. */
    var onConnectFailed: (() -> Unit)? = null

    /** Wird aufgerufen wenn die Session endet (durch Receiver oder [disconnect]). */
    var onDisconnected: (() -> Unit)? = null

    /** Eingehende Nachricht auf einem Custom-Namespace (namespace, payload-JSON). */
    var onCustomMessage: ((namespace: String, message: String) -> Unit)? = null

    // ---- Interner Zustand --------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestId = AtomicInteger(1)
    private val running = AtomicBoolean(false)

    private var socket: SSLSocket? = null
    private var outputStream: OutputStream? = null
    private val writeLock = Any()

    private var readJob: Job? = null
    private var heartbeatJob: Job? = null
    private var pollJob: Job? = null

    private var receiverAppId: String = ""
    private var launchSent = false
    private var mediaTransportId: String? = null
    private var sessionId: String? = null
    private var mediaSessionId: Int = 0

    /** True sobald der Receiver den Media-Namespace registriert hat (LOAD darf raus). */
    private var appReady = false
    private var readyFallbackJob: Job? = null

    /** Media, das nach dem Verbindungsaufbau geladen werden soll. */
    private var pendingLoad: (() -> Unit)? = null

    // ---- mDNS-Geräte-Suche -------------------------------------------------------

    private val nsdManager: NsdManager? by lazy {
        runCatching { context.getSystemService(Context.NSD_SERVICE) as NsdManager }.getOrNull()
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolving = AtomicBoolean(false)
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private val discovered = ConcurrentHashMap<String, CastDevice>()

    companion object {
        private const val SERVICE_TYPE = "_googlecast._tcp."
        private const val SENDER_ID = "sender-0"
        private const val RECEIVER_ID = "receiver-0"
        private const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        private const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        private const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
        private const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
        private const val CAST_PORT = 8009
        private const val TAG = "LETHE_CASTV2"
    }

    /** Startet die Geräte-Suche (mDNS). Idempotent. */
    fun startDiscovery() {
        val mgr = nsdManager ?: return
        if (discoveryListener != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("googlecast")) {
                    enqueueResolve(serviceInfo)
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val id = serviceInfo.serviceName ?: return
                discovered.remove(id)
                _devices.value = discovered.values.sortedBy { it.name }
            }
        }
        discoveryListener = listener
        runCatching { mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    /** Stoppt die Geräte-Suche. */
    fun stopDiscovery() {
        val mgr = nsdManager
        discoveryListener?.let { l -> runCatching { mgr?.stopServiceDiscovery(l) } }
        discoveryListener = null
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) { resolveQueue.addLast(info) }
        processResolveQueue()
    }

    private fun processResolveQueue() {
        val mgr = nsdManager ?: return
        if (!resolving.compareAndSet(false, true)) return
        val next = synchronized(resolveQueue) { resolveQueue.removeFirstOrNull() }
        if (next == null) { resolving.set(false); return }
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolving.set(false); processResolveQueue()
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host: InetAddress? = serviceInfo.host
                if (host != null) {
                    val attrs = runCatching { serviceInfo.attributes }.getOrNull()
                    val friendly = attrs?.get("fn")?.let { String(it) }
                    val devId = attrs?.get("id")?.let { String(it) } ?: (serviceInfo.serviceName ?: host.hostAddress ?: "")
                    val name = friendly ?: serviceInfo.serviceName ?: (host.hostAddress ?: "Cast-Gerät")
                    val port = serviceInfo.port.takeIf { it > 0 } ?: CAST_PORT
                    val dev = CastDevice(devId, name, host.hostAddress ?: "", port)
                    if (dev.host.isNotBlank()) {
                        discovered[devId] = dev
                        _devices.value = discovered.values.sortedBy { it.name }
                    }
                }
                resolving.set(false); processResolveQueue()
            }
        }
        runCatching { mgr.resolveService(next, resolveListener) }
            .onFailure { resolving.set(false); processResolveQueue() }
    }

    // ---- Verbindung --------------------------------------------------------------

    /** Verbindet zum Gerät und startet den Custom-Receiver [appId]. */
    fun connect(device: CastDevice, appId: String) {
        disconnect()
        receiverAppId = appId
        launchSent = false
        appReady = false
        running.set(true)
        readJob = scope.launch {
            try {
                val sslCtx = SSLContext.getInstance("TLS")
                sslCtx.init(null, arrayOf<TrustManager>(TrustAll), SecureRandom())
                val s = sslCtx.socketFactory.createSocket(device.host, device.port) as SSLSocket
                s.soTimeout = 0
                s.startHandshake()
                socket = s
                outputStream = s.outputStream
                val ins = s.inputStream

                // Virtuelle Verbindung + Heartbeat + Launch
                sendMessage(RECEIVER_ID, NS_CONNECTION, JSONObject().put("type", "CONNECT").toString())
                startHeartbeat()
                launchSent = true
                sendMessage(RECEIVER_ID, NS_RECEIVER,
                    JSONObject().put("type", "LAUNCH").put("appId", appId)
                        .put("requestId", requestId.getAndIncrement()).toString())

                readLoop(ins)
            } catch (e: Exception) {
                if (running.get()) scope.launch { onConnectFailed?.invoke() }
                teardown()
            }
        }
    }

    /** Beendet die Session sauber (STOP) und schließt die Verbindung. */
    fun stopReceiver() {
        val sid = sessionId
        if (sid != null) {
            runCatching {
                sendMessage(RECEIVER_ID, NS_RECEIVER,
                    JSONObject().put("type", "STOP").put("sessionId", sid)
                        .put("requestId", requestId.getAndIncrement()).toString())
            }
        }
        disconnect()
    }

    /** Trennt die Verbindung ohne den Receiver zu stoppen. */
    fun disconnect() {
        val wasRunning = running.getAndSet(false)
        teardown()
        if (wasRunning) scope.launch { onDisconnected?.invoke() }
    }

    private fun teardown() {
        heartbeatJob?.cancel(); heartbeatJob = null
        pollJob?.cancel(); pollJob = null
        readJob?.cancel(); readJob = null
        readyFallbackJob?.cancel(); readyFallbackJob = null
        runCatching { socket?.close() }
        socket = null
        outputStream = null
        mediaTransportId = null
        sessionId = null
        mediaSessionId = 0
        appReady = false
        pendingLoad = null
        _connected.value = false
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _currentContentId.value = null
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (running.get()) {
                delay(5000)
                runCatching {
                    sendMessage(RECEIVER_ID, NS_HEARTBEAT, JSONObject().put("type", "PING").toString())
                }
            }
        }
    }

    private fun startStatusPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (running.get()) {
                delay(1000)
                val transport = mediaTransportId ?: continue
                if (mediaSessionId == 0) continue
                runCatching {
                    sendMessage(transport, NS_MEDIA,
                        JSONObject().put("type", "GET_STATUS")
                            .put("mediaSessionId", mediaSessionId)
                            .put("requestId", requestId.getAndIncrement()).toString())
                }
            }
        }
    }

    // ---- Empfang & Verarbeitung --------------------------------------------------

    private fun readLoop(ins: InputStream) {
        val lenBuf = ByteArray(4)
        while (running.get()) {
            readFully(ins, lenBuf)
            val len = ((lenBuf[0].toInt() and 0xFF) shl 24) or
                ((lenBuf[1].toInt() and 0xFF) shl 16) or
                ((lenBuf[2].toInt() and 0xFF) shl 8) or
                (lenBuf[3].toInt() and 0xFF)
            if (len <= 0 || len > 4_000_000) throw IOException("ungültige Framelänge: $len")
            val msgBuf = ByteArray(len)
            readFully(ins, msgBuf)
            val (namespace, payload) = decodeCastMessage(msgBuf)
            if (namespace.isNotEmpty()) handleMessage(namespace, payload)
        }
    }

    private fun handleMessage(namespace: String, payload: String) {
        when (namespace) {
            NS_HEARTBEAT -> {
                // PING vom Receiver mit PONG beantworten
                if (payload.contains("\"PING\"")) {
                    runCatching {
                        sendMessage(RECEIVER_ID, NS_HEARTBEAT, JSONObject().put("type", "PONG").toString())
                    }
                }
            }
            NS_RECEIVER -> handleReceiverStatus(payload)
            NS_MEDIA -> handleMediaStatus(payload)
            NS_CONNECTION -> { /* CLOSE etc. – ignoriert */ }
            else -> scope.launch { onCustomMessage?.invoke(namespace, payload) }
        }
    }

    private fun handleReceiverStatus(payload: String) {
        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val type = obj.optString("type")
        if (type == "LAUNCH_ERROR" || type == "INVALID_REQUEST") {
            if (running.get()) scope.launch { onConnectFailed?.invoke() }
            return
        }
        val status = obj.optJSONObject("status") ?: return
        val apps = status.optJSONArray("applications") ?: return
        for (i in 0 until apps.length()) {
            val app = apps.optJSONObject(i) ?: continue
            val transportId = app.optString("transportId").takeIf { it.isNotBlank() } ?: continue
            sessionId = app.optString("sessionId").takeIf { it.isNotBlank() } ?: sessionId
            if (mediaTransportId != transportId) {
                mediaTransportId = transportId
                // Virtuelle Verbindung zur App-Instanz öffnen
                runCatching {
                    sendMessage(transportId, NS_CONNECTION, JSONObject().put("type", "CONNECT").toString())
                }
                _connected.value = true
                scheduleReadyFallback()
            }
            // WICHTIG: LOAD erst senden, wenn der (Custom-)Receiver den Media-
            // Namespace registriert hat. Ein zu früh gesendetes LOAD wird sonst
            // verworfen (Receiver-JS noch nicht geladen) → kein Ton, bleibt "Pause".
            if (!appReady && appSupportsMedia(app)) markAppReady()
            return
        }
    }

    /** Prüft, ob die App den Media-Namespace in den Status-Namespaces meldet. */
    private fun appSupportsMedia(app: JSONObject): Boolean {
        val ns = app.optJSONArray("namespaces") ?: return false
        for (j in 0 until ns.length()) {
            val name = ns.optJSONObject(j)?.optString("name")
            if (name == NS_MEDIA) return true
        }
        return false
    }

    /**
     * Fallback: Manche Receiver melden den Media-Namespace nicht sauber im
     * RECEIVER_STATUS. Nach kurzer Wartezeit trotzdem als bereit behandeln, damit
     * die Wiedergabe nicht ganz ausbleibt.
     */
    private fun scheduleReadyFallback() {
        readyFallbackJob?.cancel()
        readyFallbackJob = scope.launch {
            delay(2500)
            if (running.get() && !appReady) markAppReady()
        }
    }

    private fun markAppReady() {
        if (appReady) return
        appReady = true
        readyFallbackJob?.cancel(); readyFallbackJob = null
        startStatusPolling()
        scope.launch { onConnected?.invoke() }
        // ausstehendes Media laden
        pendingLoad?.let { it() }
        pendingLoad = null
    }

    private fun handleMediaStatus(payload: String) {
        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val arr = obj.optJSONArray("status") ?: return
        val st = arr.optJSONObject(0) ?: return
        mediaSessionId = st.optInt("mediaSessionId", mediaSessionId)
        val playerState = st.optString("playerState")
        _isPlaying.value = playerState == "PLAYING" || playerState == "BUFFERING"
        val currentTime = st.optDouble("currentTime", -1.0)
        if (currentTime >= 0) _positionMs.value = (currentTime * 1000).toLong()
        val media = st.optJSONObject("media")
        if (media != null) {
            val dur = media.optDouble("duration", -1.0)
            if (dur > 0) _durationMs.value = (dur * 1000).toLong()
            media.optString("contentId").takeIf { it.isNotBlank() }?.let { _currentContentId.value = it }
        }
    }

    // ---- Media-Steuerung ---------------------------------------------------------

    /** Lädt einen einzelnen Titel/Video auf den Receiver. */
    fun loadMedia(item: QueueItem) {
        val load = {
            val transport = mediaTransportId
            if (transport != null) {
                val media = buildMediaJson(item)
                val msg = JSONObject()
                    .put("type", "LOAD")
                    .put("media", media)
                    .put("autoplay", true)
                    .put("requestId", requestId.getAndIncrement())
                _currentContentId.value = item.url
                runCatching { sendMessage(transport, NS_MEDIA, msg.toString()) }
            }
        }
        if (mediaTransportId != null) load() else pendingLoad = load
    }

    /** Lädt eine Warteschlange (Skip per Fernbedienung möglich). */
    fun loadQueue(items: List<QueueItem>, startIndex: Int) {
        if (items.isEmpty()) return
        val load = {
            val transport = mediaTransportId
            if (transport != null) {
                val jsonItems = JSONArray()
                for (it in items) {
                    jsonItems.put(JSONObject().put("media", buildMediaJson(it)).put("autoplay", true))
                }
                val queueData = JSONObject()
                    .put("items", jsonItems)
                    .put("startIndex", startIndex.coerceIn(0, items.size - 1))
                val msg = JSONObject()
                    .put("type", "LOAD")
                    .put("queueData", queueData)
                    .put("autoplay", true)
                    .put("requestId", requestId.getAndIncrement())
                _currentContentId.value = items.getOrNull(startIndex)?.url
                runCatching { sendMessage(transport, NS_MEDIA, msg.toString()) }
            }
        }
        if (mediaTransportId != null) load() else pendingLoad = load
    }

    private fun buildMediaJson(item: QueueItem): JSONObject {
        val metadata = JSONObject()
        // MUSIC_TRACK=3, GENERIC=0
        val isAudio = item.contentType.startsWith("audio/")
        metadata.put("metadataType", if (isAudio) 3 else 0)
        item.title?.takeIf { it.isNotBlank() }?.let { metadata.put("title", it) }
        item.artist?.takeIf { it.isNotBlank() }?.let { metadata.put("artist", it) }
        item.coverUrl?.takeIf { it.startsWith("http", true) }?.let {
            metadata.put("images", JSONArray().put(JSONObject().put("url", it)))
        }
        val media = JSONObject()
            .put("contentId", item.url)
            .put("streamType", if (item.live) "LIVE" else "BUFFERED")
            .put("contentType", item.contentType)
            .put("metadata", metadata)
        if (item.contentType.contains("mpegurl", true)) {
            media.put("hlsSegmentFormat", "ts")
        }
        return media
    }

    fun play() = mediaCommand("PLAY")
    fun pause() = mediaCommand("PAUSE")
    fun queueNext() = mediaCommand("QUEUE_NEXT")
    fun queuePrev() = mediaCommand("QUEUE_PREV")

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun seekTo(ms: Long) {
        val transport = mediaTransportId ?: return
        if (mediaSessionId == 0) return
        val msg = JSONObject()
            .put("type", "SEEK")
            .put("mediaSessionId", mediaSessionId)
            .put("currentTime", ms / 1000.0)
            .put("requestId", requestId.getAndIncrement())
        _positionMs.value = ms
        runCatching { sendMessage(transport, NS_MEDIA, msg.toString()) }
    }

    private fun mediaCommand(type: String) {
        val transport = mediaTransportId ?: return
        if (mediaSessionId == 0) return
        val msg = JSONObject()
            .put("type", type)
            .put("mediaSessionId", mediaSessionId)
            .put("requestId", requestId.getAndIncrement())
        runCatching { sendMessage(transport, NS_MEDIA, msg.toString()) }
    }

    /** Sendet eine Nachricht über einen Custom-Namespace (z.B. Spark-Channel). */
    fun sendCustomMessage(namespace: String, message: String): Boolean {
        val transport = mediaTransportId ?: return false
        return runCatching { sendMessage(transport, namespace, message); true }.getOrDefault(false)
    }

    // ---- protobuf / socket -------------------------------------------------------

    private fun sendMessage(destId: String, namespace: String, payload: String) {
        val os = outputStream ?: return
        val msg = encodeCastMessage(SENDER_ID, destId, namespace, payload)
        synchronized(writeLock) {
            val len = msg.size
            os.write(
                byteArrayOf(
                    (len ushr 24 and 0xFF).toByte(),
                    (len ushr 16 and 0xFF).toByte(),
                    (len ushr 8 and 0xFF).toByte(),
                    (len and 0xFF).toByte()
                )
            )
            os.write(msg)
            os.flush()
        }
    }

    /** Kodiert eine CastMessage (protobuf). Felder: 1=version,2=src,3=dst,4=ns,5=payloadType,6=payloadUtf8. */
    private fun encodeCastMessage(src: String, dst: String, namespace: String, payload: String): ByteArray {
        val b = ByteArrayOutputStream()
        writeVarintField(b, 1, 0)          // protocol_version = CASTV2_1_0
        writeStringField(b, 2, src)
        writeStringField(b, 3, dst)
        writeStringField(b, 4, namespace)
        writeVarintField(b, 5, 0)          // payload_type = STRING
        writeStringField(b, 6, payload)
        return b.toByteArray()
    }

    private fun writeVarintField(out: ByteArrayOutputStream, field: Int, value: Long) {
        writeVarint(out, ((field shl 3) or 0).toLong())
        writeVarint(out, value)
    }

    private fun writeStringField(out: ByteArrayOutputStream, field: Int, s: String) {
        writeVarint(out, ((field shl 3) or 2).toLong())
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) out.write(b or 0x80) else { out.write(b); break }
        }
    }

    /** Dekodiert eine CastMessage → (namespace, payloadUtf8). */
    private fun decodeCastMessage(data: ByteArray): Pair<String, String> {
        var i = 0
        var namespace = ""
        var payload = ""
        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (i < data.size) {
                val b = data[i].toInt() and 0xFF
                i++
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result
        }
        while (i < data.size) {
            val tag = readVarint()
            val field = (tag ushr 3).toInt()
            val wtype = (tag and 7).toInt()
            when (wtype) {
                0 -> readVarint()                       // varint
                1 -> i += 8                              // 64-bit
                5 -> i += 4                              // 32-bit
                2 -> {                                   // length-delimited
                    val len = readVarint().toInt()
                    if (len < 0 || i + len > data.size) return namespace to payload
                    val str = String(data, i, len, Charsets.UTF_8)
                    i += len
                    when (field) {
                        4 -> namespace = str
                        6 -> payload = str
                    }
                }
                else -> return namespace to payload
            }
        }
        return namespace to payload
    }

    private fun readFully(ins: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val r = ins.read(buf, off, buf.size - off)
            if (r < 0) throw IOException("Stream geschlossen")
            off += r
        }
    }

    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
