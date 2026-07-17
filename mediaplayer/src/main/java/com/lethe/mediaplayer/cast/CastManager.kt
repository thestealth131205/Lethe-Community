package com.lethe.mediaplayer.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.lethe.mediaplayer.data.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Steuert Cast-Wiedergabe für den Lethe Medie Player. Lädt die Titel-Warteschlange auf den
 * verbundenen Cast-Empfänger (dieselbe Lethe-Cast-Receiver-App wie die Haupt-App) und spiegelt
 * Play/Pause/Position als [StateFlow]s – analog zu CastDiscoveryManager der Haupt-App.
 */
@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localServer: LocalCastServer
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting

    private val _castTrack = MutableStateFlow<Track?>(null)
    val castTrack: StateFlow<Track?> = _castTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    /** Wird aufgerufen sobald eine Cast-Session startet – z.B. um die lokale Wiedergabe zu pausieren. */
    var onCastSessionStarted: (() -> Unit)? = null

    private var activeSession: CastSession? = null
    private var tracks: List<Track> = emptyList()
    /** Auf den Cast-Empfänger geladene URL (contentId) → zugehöriger Track (für Status-Rückmeldungen). */
    private var contentIdToTrack: Map<String, Track> = emptyMap()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null
    private var started = false

    private val remoteCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = activeSession?.remoteMediaClient ?: return
            _isPlaying.value = client.isPlaying
            val dur = client.streamDuration
            if (dur > 0L) _durationMs.value = dur
            val contentId = client.mediaInfo?.contentId
            if (contentId != null) {
                (contentIdToTrack[contentId] ?: tracks.firstOrNull { it.audioUrl == contentId })
                    ?.let { _castTrack.value = it }
            }
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            activeSession = session
            _isCasting.value = true
            session.remoteMediaClient?.registerCallback(remoteCallback)
            onCastSessionStarted?.invoke()
            startPositionPolling()
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            activeSession = session
            _isCasting.value = true
            session.remoteMediaClient?.registerCallback(remoteCallback)
            startPositionPolling()
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            session.remoteMediaClient?.unregisterCallback(remoteCallback)
            resetState()
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) { resetState() }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) { _isCasting.value = false }
    }

    private fun resetState() {
        activeSession = null
        _isCasting.value = false
        _castTrack.value = null
        _isPlaying.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
        _queue.value = emptyList()
        contentIdToTrack = emptyMap()
        positionJob?.cancel()
        localServer.stop()
    }

    /** Registriert den Session-Listener. Idempotent, mehrfach aufrufbar (z.B. bei jedem Screen-Start). */
    fun start() {
        if (started) return
        started = true
        runCatching {
            val sessionManager = CastContext.getSharedInstance(context).sessionManager
            sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
            // Bereits laufende Session übernehmen: onSessionStarted/onSessionResumed feuern nicht
            // rückwirkend, wenn die Verbindung schon vor start() bestand (z.B. Player wird erneut
            // betreten oder Prozess kam aus dem Hintergrund). Ohne das bliebe _isCasting=false und
            // die UI zeigte fälschlich "Mein Smartphone" trotz aktiver Cast-Session.
            val existing = sessionManager.currentCastSession
            if (existing != null && existing.isConnected) {
                activeSession = existing
                _isCasting.value = true
                existing.remoteMediaClient?.registerCallback(remoteCallback)
                onCastSessionStarted?.invoke()
                startPositionPolling()
            }
        }
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (true) {
                delay(500)
                val client = activeSession?.remoteMediaClient ?: break
                _isPlaying.value = client.isPlaying
                _positionMs.value = client.approximateStreamPosition.coerceAtLeast(0L)
                val dur = client.streamDuration
                if (dur > 0L) _durationMs.value = dur
            }
        }
    }

    private fun contentTypeFor(track: Track, uri: android.net.Uri): String {
        // Bei lokalen Quellen liefert der ContentResolver den echten MIME-Typ (aus dem MediaStore).
        context.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") }?.let { return it }
        return when {
            track.audioUrl.contains(".m4a", true) -> "audio/mp4"
            track.audioUrl.contains(".aac", true) -> "audio/aac"
            track.audioUrl.contains(".ogg", true) -> "audio/ogg"
            track.audioUrl.contains(".wav", true) -> "audio/wav"
            track.audioUrl.contains(".flac", true) -> "audio/flac"
            else -> "audio/mpeg"
        }
    }

    /**
     * Bestimmt die für den Cast-Empfänger erreichbare URL je nach Herkunft des Titels:
     * - Lethe-Bibliothek / eigene Musik / Audius → bereits HTTP(S), direkt castbar.
     * - Lokal (Gerätespeicher `content://`, importiert/heruntergeladen `file://`) → über den
     *   lokalen HTTP-Server ([LocalCastServer]) im LAN ausgeliefert. Chromecast kann `content://`/
     *   `file://` nicht selbst erreichen.
     *
     * Liefert null, wenn eine lokale Datei mangels LAN-IP nicht ausgeliefert werden kann.
     */
    private fun resolveCastUrl(track: Track): String? {
        val url = track.audioUrl
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) return url
        val uri = android.net.Uri.parse(url)
        return localServer.serve(track.id, uri, contentTypeFor(track, uri))
    }

    private fun buildQueueItem(track: Track, contentUrl: String): MediaQueueItem {
        val contentType = contentTypeFor(track, android.net.Uri.parse(track.audioUrl))
        val meta = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        meta.putString(CastMediaMetadata.KEY_TITLE, track.title)
        if (track.artist.isNotBlank()) meta.putString(CastMediaMetadata.KEY_ARTIST, track.artist)
        // Cover nur bei über das Netz erreichbaren Bildern anhängen (lokale content://-Cover kann
        // der Empfänger nicht laden).
        track.coverUrl?.takeIf { it.startsWith("http", true) }?.let { meta.addImage(WebImage(android.net.Uri.parse(it))) }
        val mediaInfo = MediaInfo.Builder(contentUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(meta)
            .build()
        return MediaQueueItem.Builder(mediaInfo).setAutoplay(true).build()
    }

    /** Lädt eine Titelliste als Warteschlange auf den Cast-Empfänger (ermöglicht Skip per TV-Fernbedienung). */
    fun loadQueue(list: List<Track>, startIndex: Int) {
        val session = activeSession ?: return
        val client = session.remoteMediaClient ?: return
        if (list.isEmpty()) return
        // Herkunft je Titel auflösen; nicht castbare (lokal ohne LAN-IP) werden übersprungen.
        val startTrackId = list.getOrNull(startIndex)?.id
        val resolved = list.mapNotNull { track ->
            resolveCastUrl(track)?.let { url -> track to url }
        }
        if (resolved.isEmpty()) return
        tracks = resolved.map { it.first }
        contentIdToTrack = resolved.associate { (t, url) -> url to t }
        val items = resolved.map { (track, url) -> buildQueueItem(track, url) }
        val newStartIndex = resolved.indexOfFirst { it.first.id == startTrackId }.coerceAtLeast(0)
        val queueData = MediaQueueData.Builder()
            .setItems(items)
            .setStartIndex(newStartIndex.coerceIn(0, items.size - 1))
            .build()
        val loadRequest = MediaLoadRequestData.Builder()
            .setQueueData(queueData)
            .setAutoplay(true)
            .build()
        _castTrack.value = tracks.getOrNull(newStartIndex)
        _queue.value = tracks
        client.load(loadRequest)
    }

    /** Springt innerhalb der laufenden Cast-Warteschlange zu einem bestimmten Titel. */
    fun playAtIndex(index: Int) {
        loadQueue(tracks, index)
    }

    fun togglePlayPause() {
        val client = activeSession?.remoteMediaClient ?: return
        if (client.isPlaying) client.pause(null) else client.play(null)
    }

    fun next() { activeSession?.remoteMediaClient?.queueNext(null) }
    fun previous() { activeSession?.remoteMediaClient?.queuePrev(null) }

    fun seekTo(ms: Long) {
        activeSession?.remoteMediaClient?.seek(ms)
        _positionMs.value = ms
    }

    /** Beendet die aktive Cast-Session (Wechsel zurück auf "Mein Smartphone"). */
    fun stopCasting() {
        runCatching {
            CastContext.getSharedInstance(context)
                .sessionManager
                .endCurrentSession(true)
        }
    }
}
