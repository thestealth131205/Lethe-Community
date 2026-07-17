package com.securechat.app.cast

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentraler Cast-Discovery-Manager. Wird beim App-Start initialisiert und
 * scannt nach verfügbaren Cast-Geräten. Beim Wechsel in den Hintergrund
 * wird der aktive Scan pausiert und beim Vordergrundholen neu gestartet.
 * Screens beobachten [castAvailable] statt eigene MediaRouter-Callbacks
 * zu registrieren.
 */
@Singleton
class CastDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    val selector: MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
        .build()

    private val _castAvailable = MutableStateFlow(false)
    val castAvailable: StateFlow<Boolean> = _castAvailable

    /** True solange eine aktive Cast-Session besteht. */
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting

    /** URL des Titels, der gerade per Cast gestreamt wird. */
    private val _castCurrentUrl = MutableStateFlow<String?>(null)
    val castCurrentUrl: StateFlow<String?> = _castCurrentUrl

    /** True wenn der Cast-Receiver gerade abspielt (nicht pausiert). */
    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying

    /** Aktuelle Position des Cast-Streams in Millisekunden. */
    private val _castCurrentMs = MutableStateFlow(0L)
    val castCurrentMs: StateFlow<Long> = _castCurrentMs

    /** Gesamtdauer des Cast-Streams in Millisekunden. */
    private val _castTotalMs = MutableStateFlow(0L)
    val castTotalMs: StateFlow<Long> = _castTotalMs

    private var activeSession: CastSession? = null
    private val managerScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
    )
    private var positionPollingJob: kotlinx.coroutines.Job? = null

    /** Wird vor dem Öffnen des Chooser-Dialogs gesetzt; nach dem Session-Start geladen. */
    var pendingCastUrl: String? = null

    /** Spark-ID des Videos das per Cast übertragen wird; nach dem Session-Start als Nachricht gesendet. */
    var pendingCastSparkId: String? = null

    /** Bild-URLs für Image-Sparks (wird im Receiver angezeigt). */
    var pendingCastImageUrls: List<String>? = null

    /** Musik-URL für Sparks mit Hintergrundaudio. */
    var pendingCastMusicUrl: String? = null

    /** MediaMetadata für pending Cast-Load (Musik mit Cover/Artist/Titel). */
    var pendingCastMediaMetadata: MediaMetadata? = null

    /** Pending Musik-Queue: alle Track-URLs für Skip per Fernbedienung. */
    var pendingMusicQueue: List<String>? = null
    var pendingMusicQueueStartIndex: Int = 0
    var pendingMusicQueueMetadataBuilder: ((String) -> MediaMetadata)? = null

    /** Wird aufgerufen wenn eine Cast-Session startet – z.B. um lokale Wiedergabe zu stoppen. */
    var onCastSessionStarted: (() -> Unit)? = null

    /** Wird aufgerufen wenn der Session-Start fehlschlägt (z.B. Audio-Gerät unterstützt keinen Custom Receiver). */
    var onCastSessionStartFailed: (() -> Unit)? = null

    /** Callback für Nav-Nachrichten vom Receiver (TV-Fernbedienung Hoch/Runter → Spark-Navigation). */
    var onSparkNavMessage: ((direction: String) -> Unit)? = null

    private var scanning = false

    private val router by lazy { MediaRouter.getInstance(context) }

    private val remoteMediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = activeSession?.remoteMediaClient ?: return
            _castIsPlaying.value = client.isPlaying
            // URL aus dem aktuell geladenen MediaInfo auslesen
            val url = client.mediaInfo?.contentId
            if (!url.isNullOrBlank()) _castCurrentUrl.value = url
            val dur = client.streamDuration
            if (dur > 0L) _castTotalMs.value = dur
            _castCurrentMs.value = client.approximateStreamPosition.coerceAtLeast(0L)
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            activeSession = session
            _isCasting.value = true
            session.remoteMediaClient?.registerCallback(remoteMediaCallback)
            // Eingehende Nachrichten vom Receiver empfangen (z.B. TV-Fernbedienung Nav)
            try {
                session.setMessageReceivedCallbacks(SparkCastChannel.NAMESPACE,
                    com.google.android.gms.cast.Cast.MessageReceivedCallback { _, _, message ->
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
                )
            } catch (_: Exception) {}
            // Lokale Wiedergabe stoppen (ViewModel-Callback)
            onCastSessionStarted?.invoke()
            loadPendingMedia(session)
            startPositionPolling()
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            activeSession = session
            _isCasting.value = true
            session.remoteMediaClient?.registerCallback(remoteMediaCallback)
            startPositionPolling()
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            pendingCastUrl = null
            pendingCastSparkId = null
            pendingCastImageUrls = null
            pendingCastMusicUrl = null
            pendingCastMediaMetadata = null
            pendingMusicQueue = null
            pendingMusicQueueStartIndex = 0
            pendingMusicQueueMetadataBuilder = null
            onCastSessionStartFailed?.invoke()
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteMediaCallback)
            activeSession = null
            _isCasting.value = false
            _castCurrentUrl.value = null
            _castIsPlaying.value = false
            stopPositionPolling()
            _castCurrentMs.value = 0L
            _castTotalMs.value = 0L
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
        }
    }

    private fun loadPendingMedia(session: CastSession) {
        val url = pendingCastUrl
        val sparkId = pendingCastSparkId
        val imageUrls = pendingCastImageUrls
        val musicUrl = pendingCastMusicUrl
        val mediaMetadata = pendingCastMediaMetadata
        val queue = pendingMusicQueue
        val queueStart = pendingMusicQueueStartIndex
        val queueMetaBuilder = pendingMusicQueueMetadataBuilder
        pendingCastUrl = null
        pendingCastSparkId = null
        pendingCastImageUrls = null
        pendingCastMusicUrl = null
        pendingCastMediaMetadata = null
        pendingMusicQueue = null
        pendingMusicQueueStartIndex = 0
        pendingMusicQueueMetadataBuilder = null

        if (queue != null && queue.size > 1 && queueMetaBuilder != null) {
            // Musik-Queue laden (für Skip per Fernbedienung)
            loadMusicQueue(queue, queueStart, queueMetaBuilder)
        } else if (url != null) {
            loadUrlOnSession(url, session, mediaMetadata)
        }

        if (sparkId != null) {
            managerScope.launch {
                // Kurz warten bis der Receiver die Media-Load-Anfrage verarbeitet hat
                kotlinx.coroutines.delay(1500)
                session.sendSparkMessage(
                    SparkCastChannel.buildSparkMessage(sparkId, url, null, null, imageUrls, musicUrl)
                )
            }
        }
    }

    /** Lädt eine URL auf die aktive Cast-Session und aktualisiert [castCurrentUrl]. */
    fun loadUrlOnCast(url: String, metadata: MediaMetadata? = null, isLive: Boolean = false) {
        val session = activeSession ?: return
        loadUrlOnSession(url, session, metadata, isLive)
    }

    /**
     * Lädt eine Musik-Queue auf die aktive Cast-Session.
     * Ermöglicht Skip per TV-Fernbedienung.
     */
    fun loadMusicQueue(
        urls: List<String>,
        startIndex: Int,
        metadataBuilder: (String) -> MediaMetadata
    ) {
        val session = activeSession ?: return
        val client = session.remoteMediaClient ?: return
        if (urls.isEmpty()) return

        val queueItems = urls.map { url ->
            val contentType = when {
                url.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
                url.contains(".m4a", ignoreCase = true) -> "audio/mp4"
                url.contains(".aac", ignoreCase = true) -> "audio/aac"
                url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
                url.contains(".wav", ignoreCase = true) -> "audio/wav"
                url.contains(".flac", ignoreCase = true) -> "audio/flac"
                else -> "audio/mpeg"
            }
            val mediaInfo = MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(metadataBuilder(url))
                .build()
            MediaQueueItem.Builder(mediaInfo)
                .setAutoplay(true)
                .build()
        }

        val queueData = MediaQueueData.Builder()
            .setItems(queueItems)
            .setStartIndex(startIndex.coerceIn(0, queueItems.size - 1))
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setQueueData(queueData)
            .setAutoplay(true)
            .build()

        _castCurrentUrl.value = urls.getOrNull(startIndex) ?: urls.first()
        timber.log.Timber.tag("LETHE_CAST").d("Cast-Queue geladen: ${urls.size} Tracks, Start=$startIndex")
        client.load(loadRequest)
    }

    /** Erstellt MediaMetadata für Musik-Cast mit Cover, Artist und Titel. */
    fun buildMusicMetadata(title: String?, artist: String?, coverUrl: String?): MediaMetadata {
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        if (!title.isNullOrBlank()) meta.putString(MediaMetadata.KEY_TITLE, title)
        if (!artist.isNullOrBlank()) meta.putString(MediaMetadata.KEY_ARTIST, artist)
        if (!coverUrl.isNullOrBlank()) {
            meta.addImage(WebImage(android.net.Uri.parse(coverUrl)))
        }
        return meta
    }

    /** Springt zum nächsten Track in der Cast-Queue. */
    fun castQueueNext() {
        activeSession?.remoteMediaClient?.queueNext(null)
    }

    /** Springt zum vorherigen Track in der Cast-Queue. */
    fun castQueuePrev() {
        activeSession?.remoteMediaClient?.queuePrev(null)
    }

    /** Schaltet Play/Pause auf dem Cast-Receiver um. */
    fun castPlayPause() {
        val client = activeSession?.remoteMediaClient ?: return
        if (client.isPlaying) client.pause(null) else client.play(null)
    }

    /** Springt zu einer bestimmten Position im Cast-Stream. */
    fun castSeekTo(ms: Long) {
        val client = activeSession?.remoteMediaClient ?: return
        client.seek(ms)
        _castCurrentMs.value = ms
    }

    /** Sendet eine Spark-Nachricht über den Custom Channel an den Receiver. */
    fun sendSparkMessage(message: String): Boolean {
        return activeSession?.sendSparkMessage(message) ?: false
    }

    /** Beendet die aktive Cast-Session. */
    fun stopCasting() {
        try {
            CastContext.getSharedInstance(context)
                .sessionManager
                .endCurrentSession(true)
        } catch (_: Exception) {}
    }

    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = managerScope.launch {
            while (true) {
                kotlinx.coroutines.delay(200)
                val client = activeSession?.remoteMediaClient ?: break
                _castIsPlaying.value = client.isPlaying
                _castCurrentMs.value = client.approximateStreamPosition.coerceAtLeast(0L)
                val dur = client.streamDuration
                if (dur > 0L) _castTotalMs.value = dur
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    /**
     * Lädt eine URL auf die Cast-Session. Erkennt automatisch Audio, Video,
     * HLS und Livestreams anhand der URL-Endung.
     */
    fun loadUrlOnSession(
        url: String,
        session: CastSession,
        metadata: MediaMetadata? = null,
        isLive: Boolean = false
    ) {
        _castCurrentUrl.value = url
        val isHls = url.contains(".m3u8", ignoreCase = true)
        val contentType = when {
            isHls -> "application/vnd.apple.mpegurl"
            url.contains(".mp4", ignoreCase = true) -> "video/mp4"
            url.contains(".webm", ignoreCase = true) -> "video/webm"
            url.contains(".mp3", ignoreCase = true) -> "audio/mpeg"
            url.contains(".m4a", ignoreCase = true) -> "audio/mp4"
            url.contains(".aac", ignoreCase = true) -> "audio/aac"
            url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
            url.contains(".wav", ignoreCase = true) -> "audio/wav"
            else -> "video/mp4"
        }
        val streamType = if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
        val builder = MediaInfo.Builder(url)
            .setStreamType(streamType)
            .setContentType(contentType)
        // HLS-Segmentformat explizit setzen damit der CAF-Receiver
        // MPEG-TS Segmente korrekt erkennt (Auto-Detection kann fehlschlagen).
        if (isHls) {
            builder.setHlsSegmentFormat("ts")
        }
        if (metadata != null) builder.setMetadata(metadata)
        val mediaInfo = builder.build()
        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()
        val client = session.remoteMediaClient
        if (client == null) {
            timber.log.Timber.tag("LETHE_CAST").w("remoteMediaClient ist null – Cast-Load abgebrochen für: $url")
            return
        }
        timber.log.Timber.tag("LETHE_CAST").d("Cast-Load: $contentType | live=$isLive | $url")
        client.load(loadRequest)
    }

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailability(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailability(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailability(router)
        }
    }

    private fun updateAvailability(router: MediaRouter) {
        _castAvailable.value = router.routes.any {
            it != router.defaultRoute && it.matchesSelector(selector)
        }
    }

    /**
     * Registriert den Lifecycle-Observer und startet den ersten Scan.
     * Muss auf dem Main-Thread aufgerufen werden.
     */
    fun startDiscovery() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        try {
            CastContext.getSharedInstance(context)
                .sessionManager
                .addSessionManagerListener(sessionListener, CastSession::class.java)
        } catch (_: Exception) {}
        startScan()
    }

    override fun onStart(owner: LifecycleOwner) {
        // App kommt in den Vordergrund
        startScan()
    }

    override fun onStop(owner: LifecycleOwner) {
        // App geht in den Hintergrund – Scan pausieren (spart Akku)
        stopScan()
    }

    private fun startScan() {
        if (scanning) return
        scanning = true
        updateAvailability(router)
        router.addCallback(
            selector,
            callback,
            MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
        )
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        router.removeCallback(callback)
    }
}
