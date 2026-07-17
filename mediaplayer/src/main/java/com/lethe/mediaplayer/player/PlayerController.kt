@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lethe.mediaplayer.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lethe.mediaplayer.data.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verbindet die Compose-UI mit dem [PlaybackService] über einen [MediaController]
 * und stellt den Zustand als [StateFlow]s bereit.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: PlaybackSettings
) {
    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val trackById = mutableMapOf<String, Track>()
    private var currentQueueList: List<Track> = emptyList()

    private val _current = MutableStateFlow<Track?>(null)
    val current: StateFlow<Track?> = _current

    /**
     * Live-Artwork des aktuellen Titels (Uri oder eingebettete ID3-Bilddaten als ByteArray).
     * ExoPlayer extrahiert eingebettete Cover aus dem Audio-Stream selbst und merged sie in
     * [Player.getMediaMetadata], auch wenn das MediaItem selbst keine artworkUri gesetzt hat
     * (z.B. persönliche Musik ohne Cover-URL vom Server). Dieselbe Quelle nutzt bereits die
     * System-Medienbenachrichtigung, weshalb dort das Cover korrekt erscheint.
     */
    private val _artwork = MutableStateFlow<Any?>(null)
    val artwork: StateFlow<Any?> = _artwork

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    /** 0 = aus, 1 = ganze Liste, 2 = einzelner Titel (spiegelt Player.REPEAT_MODE_*) */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get()
            attachListener()
            syncState()
            startPositionLoop()
            restoreLastPlaybackIfIdle()
        }, MoreExecutors.directExecutor())
    }

    private fun attachListener() {
        controller?.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncState()
            }
        })
    }

    private fun startPositionLoop() {
        scope.launch {
            while (true) {
                controller?.let {
                    _positionMs.value = it.currentPosition.coerceAtLeast(0)
                    val d = it.duration
                    _durationMs.value = if (d > 0) d else 0
                }
                delay(500)
            }
        }
    }

    private fun syncState() {
        val c = controller ?: return
        _isPlaying.value = c.isPlaying
        _repeatMode.value = c.repeatMode
        _shuffle.value = c.shuffleModeEnabled
        val id = c.currentMediaItem?.mediaId
        _current.value = id?.let { trackById[it] }
        val meta = c.mediaMetadata
        _artwork.value = meta.artworkData ?: meta.artworkUri
        val d = c.duration
        _durationMs.value = if (d > 0) d else 0
    }

    /** Stellt beim ersten Verbinden (z.B. frischer App-Start) die zuletzt gehörte Warteschlange
     * pausiert an der zuletzt gehörten Position wieder her, falls gerade keine Wiedergabe läuft.
     * Die Persistenz erfolgt zentral im [PlaybackService] (auch bei reiner Android-Auto-Wiedergabe). */
    private fun restoreLastPlaybackIfIdle() {
        val c = controller ?: return
        if (c.mediaItemCount > 0) return
        val (tracks, index, position) = settings.getLastQueue() ?: return
        trackById.clear()
        currentQueueList = tracks
        val items = tracks.map { t -> trackById[t.id] = t; buildMediaItem(t) }
        c.setMediaItems(items, index, position)
        c.prepare()
        c.playWhenReady = false
        _queue.value = tracks
    }

    /** Baut ein abspielbares [MediaItem] samt Metadaten und Extras (Track-ID, Dauer, Favorit,
     * Bibliotheks-/Quell-Kennzeichnung), die der [PlaybackService] zum Persistieren der
     * Warteschlange und für die Android-Auto-Herz-Aktion wieder in einen [Track] zurückliest. */
    private fun buildMediaItem(t: Track, friendsMix: Boolean = false): MediaItem {
        val extras = Bundle().apply {
            putString(PlaybackService.EXTRA_TRACK_ID, t.id)
            putInt(PlaybackService.EXTRA_DURATION_SEC, t.durationSec)
            putBoolean(PlaybackService.EXTRA_IS_LIBRARY, t.isLibraryTrack)
            putString(PlaybackService.EXTRA_SOURCE, t.source)
            putBoolean(PlaybackService.EXTRA_IS_FAVORITE, t.isFavorite)
            // FriendsMix-Kennzeichnung für den PlaybackService: fester 15s-Crossfade + Titelstart
            // ab 30% der Gesamtdauer, unabhängig von den Nutzereinstellungen. Sofern der Server
            // optimierte Positionen liefert, ersetzen sie die Standardwerte (Einstieg / Überblend-Start).
            if (friendsMix) {
                putBoolean(PlaybackService.EXTRA_FRIENDS_MIX, true)
                t.mixStartSec?.let { putFloat(PlaybackService.EXTRA_MIX_START_SEC, it) }
                t.crossfadeStartSec?.let { putFloat(PlaybackService.EXTRA_CROSSFADE_START_SEC, it) }
            }
        }
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(Uri.parse(t.audioUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setArtworkUri(t.coverUrl?.let { Uri.parse(it) })
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    fun play(tracks: List<Track>, startIndex: Int, friendsMix: Boolean = false) {
        val c = controller ?: return
        trackById.clear()
        currentQueueList = tracks
        val items = tracks.map { t -> trackById[t.id] = t; buildMediaItem(t, friendsMix) }
        c.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
        c.prepare()
        c.playWhenReady = true
        _queue.value = tracks
    }

    /** Springt innerhalb der laufenden Warteschlange zu einem bestimmten Titel (z.B. aus der Warteschlangenansicht). */
    fun playAtIndex(index: Int) {
        val c = controller ?: return
        if (index < 0 || index >= c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.playWhenReady = true
    }

    /** Aktuelle Warteschlange + Index des laufenden Titels (z.B. für die Übergabe an Cast). */
    fun currentQueue(): Pair<List<Track>, Int>? {
        val track = _current.value ?: return null
        val index = currentQueueList.indexOfFirst { it.id == track.id }
        if (index < 0) return null
        return currentQueueList to index
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() { controller?.pause() }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** Zyklus: aus → ganze Liste → einzelner Titel → aus */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
