package com.securechat.app.cast

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.securechat.app.chromecast.ChromecastV2Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Google-freie Metadaten für Cast-Wiedergabe (Ersatz für com.google.android.gms.cast.MediaMetadata). */
data class CastMediaMetadata(
    val title: String? = null,
    val artist: String? = null,
    val coverUrl: String? = null
)

/**
 * Zentraler Cast-Discovery-Manager. Fassade über den Google-freien
 * [ChromecastV2Client] (CASTV2-Protokoll, mDNS via NsdManager). Behält die
 * bisherige öffentliche API bei, damit die UI-Screens unverändert weiter-
 * funktionieren – ohne jede Abhängigkeit vom proprietären Google-Cast-SDK.
 *
 * Statt eines MediaRouter-Chooser-Dialogs bietet der Manager jetzt einen
 * eigenen Geräte-Picker: [devices] listet gefundene Geräte, [showPicker]
 * steuert die Sichtbarkeit des (in der App gehosteten) Picker-Composables.
 */
@Singleton
class CastDiscoveryManager @Inject constructor(
    private val v2: ChromecastV2Client
) : DefaultLifecycleObserver {

    companion object {
        /** App-ID des Lethe-Custom-Cast-Receivers. */
        const val RECEIVER_APP_ID = "8622B21C"
    }

    // ---- Geräte-Liste & Picker ---------------------------------------------------

    /** Gefundene Cast-Geräte (mDNS). */
    val devices: StateFlow<List<ChromecastV2Client.CastDevice>> = v2.devices

    private val _showPicker = MutableStateFlow(false)
    val showPicker: StateFlow<Boolean> = _showPicker

    // ---- Zustands-Flows (unveränderte API) --------------------------------------

    private val _castAvailable = MutableStateFlow(false)
    val castAvailable: StateFlow<Boolean> = _castAvailable

    /** True solange eine aktive Cast-Session besteht. */
    val isCasting: StateFlow<Boolean> = v2.connected

    /** URL des Titels, der gerade per Cast gestreamt wird. */
    val castCurrentUrl: StateFlow<String?> = v2.currentContentId

    /** True wenn der Cast-Receiver gerade abspielt (nicht pausiert). */
    val castIsPlaying: StateFlow<Boolean> = v2.isPlaying

    /** Aktuelle Position des Cast-Streams in Millisekunden. */
    val castCurrentMs: StateFlow<Long> = v2.positionMs

    /** Gesamtdauer des Cast-Streams in Millisekunden. */
    val castTotalMs: StateFlow<Long> = v2.durationMs

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ---- Pending-Media (wird vor dem Verbindungsaufbau gesetzt) ------------------

    var pendingCastUrl: String? = null
    var pendingCastSparkId: String? = null
    var pendingCastImageUrls: List<String>? = null
    var pendingCastMusicUrl: String? = null
    var pendingCastMediaMetadata: CastMediaMetadata? = null
    var pendingCastIsLive: Boolean = false

    var pendingMusicQueue: List<String>? = null
    var pendingMusicQueueStartIndex: Int = 0
    var pendingMusicQueueMetadataBuilder: ((String) -> CastMediaMetadata)? = null

    // ---- Callbacks ---------------------------------------------------------------

    /** Wird aufgerufen wenn eine Cast-Session startet – z.B. um lokale Wiedergabe zu stoppen. */
    var onCastSessionStarted: (() -> Unit)? = null

    /** Wird aufgerufen wenn der Session-Start fehlschlägt. */
    var onCastSessionStartFailed: (() -> Unit)? = null

    /** Callback für Nav-Nachrichten vom Receiver (TV-Fernbedienung Hoch/Runter → Spark-Navigation). */
    var onSparkNavMessage: ((direction: String) -> Unit)? = null

    private var observersWired = false

    private fun wireObservers() {
        if (observersWired) return
        observersWired = true

        v2.onConnected = {
            managerScope.launch {
                onCastSessionStarted?.invoke()
                loadPendingMedia()
            }
        }
        v2.onConnectFailed = {
            clearPending()
            managerScope.launch { onCastSessionStartFailed?.invoke() }
        }
        v2.onCustomMessage = { namespace, message ->
            if (namespace == SparkCastChannel.NAMESPACE) {
                try {
                    val json = org.json.JSONObject(message)
                    if (json.optString("type") == "nav") {
                        val dir = json.optString("direction")
                        if (dir.isNotBlank()) {
                            managerScope.launch { onSparkNavMessage?.invoke(dir) }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // castAvailable aus der Geräte-Liste ableiten
        managerScope.launch {
            v2.devices.collect { list -> _castAvailable.value = list.isNotEmpty() }
        }
    }

    // ---- Picker-Steuerung --------------------------------------------------------

    /** Öffnet den Geräte-Picker (nachdem die Pending-Felder gesetzt wurden). */
    fun requestDevicePicker() {
        _showPicker.value = true
    }

    /** Schließt den Geräte-Picker ohne zu verbinden. */
    fun dismissPicker() {
        _showPicker.value = false
    }

    /** Verbindet zum gewählten Gerät und startet den Lethe-Receiver. */
    fun connectToDevice(device: ChromecastV2Client.CastDevice) {
        _showPicker.value = false
        v2.connect(device, RECEIVER_APP_ID)
    }

    // ---- Pending-Media laden -----------------------------------------------------

    private fun clearPending() {
        pendingCastUrl = null
        pendingCastSparkId = null
        pendingCastImageUrls = null
        pendingCastMusicUrl = null
        pendingCastMediaMetadata = null
        pendingCastIsLive = false
        pendingMusicQueue = null
        pendingMusicQueueStartIndex = 0
        pendingMusicQueueMetadataBuilder = null
    }

    private fun loadPendingMedia() {
        val url = pendingCastUrl
        val sparkId = pendingCastSparkId
        val imageUrls = pendingCastImageUrls
        val musicUrl = pendingCastMusicUrl
        val metadata = pendingCastMediaMetadata
        val isLive = pendingCastIsLive
        val queue = pendingMusicQueue
        val queueStart = pendingMusicQueueStartIndex
        val queueMetaBuilder = pendingMusicQueueMetadataBuilder
        clearPending()

        if (queue != null && queue.size > 1 && queueMetaBuilder != null) {
            loadMusicQueue(queue, queueStart, queueMetaBuilder)
        } else if (url != null) {
            loadUrlOnCast(url, metadata, isLive)
        }

        if (sparkId != null) {
            managerScope.launch {
                kotlinx.coroutines.delay(1500)
                v2.sendCustomMessage(
                    SparkCastChannel.NAMESPACE,
                    SparkCastChannel.buildSparkMessage(sparkId, url, null, null, imageUrls, musicUrl)
                )
            }
        }
    }

    // ---- Media-Steuerung ---------------------------------------------------------

    /** Lädt eine URL auf den verbundenen Cast-Receiver. */
    fun loadUrlOnCast(url: String, metadata: CastMediaMetadata? = null, isLive: Boolean = false) {
        v2.loadMedia(
            ChromecastV2Client.QueueItem(
                url = url,
                contentType = contentTypeForUrl(url),
                title = metadata?.title,
                artist = metadata?.artist,
                coverUrl = metadata?.coverUrl,
                live = isLive
            )
        )
    }

    /** Lädt eine Musik-Queue (Skip per Fernbedienung möglich). */
    fun loadMusicQueue(
        urls: List<String>,
        startIndex: Int,
        metadataBuilder: (String) -> CastMediaMetadata
    ) {
        if (urls.isEmpty()) return
        val items = urls.map { url ->
            val meta = metadataBuilder(url)
            ChromecastV2Client.QueueItem(
                url = url,
                contentType = contentTypeForUrl(url),
                title = meta.title,
                artist = meta.artist,
                coverUrl = meta.coverUrl,
                live = false
            )
        }
        v2.loadQueue(items, startIndex)
    }

    /** Erstellt Cast-Metadaten für Musik mit Cover, Artist und Titel. */
    fun buildMusicMetadata(title: String?, artist: String?, coverUrl: String?): CastMediaMetadata =
        CastMediaMetadata(title, artist, coverUrl)

    fun castQueueNext() = v2.queueNext()
    fun castQueuePrev() = v2.queuePrev()
    fun castPlayPause() = v2.togglePlayPause()
    fun castPlay() = v2.play()
    fun castPause() = v2.pause()
    fun castSeekTo(ms: Long) = v2.seekTo(ms)

    /** Sendet eine Spark-Nachricht über den Custom-Channel an den Receiver. */
    fun sendSparkMessage(message: String): Boolean =
        v2.sendCustomMessage(SparkCastChannel.NAMESPACE, message)

    /** Beendet die aktive Cast-Session (stoppt den Receiver). */
    fun stopCasting() {
        v2.stopReceiver()
    }

    private fun contentTypeForUrl(url: String): String = when {
        url.contains(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
        url.contains(".mp4", ignoreCase = true) -> "video/mp4"
        url.contains(".webm", ignoreCase = true) -> "video/webm"
        url.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
        url.contains(".m4a", ignoreCase = true) -> "audio/mp4"
        url.contains(".aac", ignoreCase = true) -> "audio/aac"
        url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
        url.contains(".wav", ignoreCase = true) -> "audio/wav"
        url.contains(".flac", ignoreCase = true) -> "audio/flac"
        else -> "video/mp4"
    }

    // ---- Lifecycle & Discovery ---------------------------------------------------

    /** Registriert den Lifecycle-Observer und startet die Geräte-Suche. */
    fun startDiscovery() {
        wireObservers()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        v2.startDiscovery()
    }

    override fun onStart(owner: LifecycleOwner) {
        // App kommt in den Vordergrund
        v2.startDiscovery()
    }

    override fun onStop(owner: LifecycleOwner) {
        // App geht in den Hintergrund – Scan pausieren (spart Akku)
        v2.stopDiscovery()
    }
}
