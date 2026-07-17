@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lethe.mediaplayer.player

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lethe.mediaplayer.auth.SessionBridge
import com.lethe.mediaplayer.data.LocalMediaScanner
import com.lethe.mediaplayer.data.MediaRepository
import com.lethe.mediaplayer.data.PlaylistDto
import com.lethe.mediaplayer.data.Track
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Hintergrund-Wiedergabe für den Lethe Medie Player.
 *
 * Media3 [MediaLibraryService] (Unterklasse von [androidx.media3.session.MediaSessionService]).
 * Liefert Media-Notification, Lockscreen-Steuerung und einen Android-Auto-Browse-Baum
 * (Playlist-Übersicht, siehe [LibrarySessionCallback]). Loop-Modus und Shuffle werden direkt
 * über den [Player] gesteuert (repeatMode / shuffleModeEnabled).
 *
 * Echter Crossfade: für einen tatsächlichen akustischen Überlapp beider Titel (statt reinem
 * Aus-/Einblenden auf einem einzigen Player) laufen zwei [ExoPlayer]-Instanzen parallel.
 * [activePlayer] ist gerade an die [MediaSession] gebunden, [standbyPlayer] spiegelt dieselbe
 * Titel-Queue. Kurz vor Titelende ([beginCrossfade]) startet der Standby-Player bereits leise
 * den nächsten Titel, während der aktive Player über [PlaybackSettings.crossfadeSeconds]
 * gegenläufig ausblendet – beide sind während der Rampe gleichzeitig hörbar. Am Ende der Rampe
 * tauschen die Rollen inkl. [MediaSession.setPlayer]. Da Audiofokus pro Prozess nur einmal
 * gehalten werden darf, übernehmen beide ExoPlayer-Instanzen selbst KEIN Audiofokus-Handling
 * (`handleAudioFocus = false`) – das übernimmt [focusListener] zentral für den Service.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var settings: PlaybackSettings
    @Inject lateinit var mediaCache: MediaCache
    @Inject lateinit var audioRoutingManager: AudioRoutingManager
    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var localMediaScanner: LocalMediaScanner
    @Inject lateinit var sessionBridge: SessionBridge

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private lateinit var activePlayer: ExoPlayer
    private lateinit var standbyPlayer: ExoPlayer
    private val scope = CoroutineScope(Dispatchers.Main)
    private var fadeJob: Job? = null
    private var fadeInJob: Job? = null
    private var crossfadeJob: Job? = null
    private var fadingIn = false
    private var crossfading = false
    /** Ob der 30%-Startoffset für den aktuell aktiven FriendsMix-Titel bereits gesetzt wurde. */
    private var friendsMixOffsetApplied = false
    /** Ob der aktuell laufende Titel als Favorit markiert ist (steuert das Herz-Symbol in Android Auto). */
    private var currentFavorite = false
    /** Zeitpunkt der letzten Warteschlangen-Persistenz (throttled, siehe [persistQueue]). */
    private var lastPersistAtMs = 0L

    /** true, wenn der gerade aktive Titel als FriendsMix markiert ist (siehe [EXTRA_FRIENDS_MIX]). */
    private fun isFriendsMixActive(): Boolean =
        activePlayer.currentMediaItem?.mediaMetadata?.extras?.getBoolean(EXTRA_FRIENDS_MIX, false) == true

    /** Startposition (ms) für einen FriendsMix-Titel: serverseitig optimierter Wert aus den
     * Metadaten-Extras, sonst der Standard-Einstieg bei 30% der Gesamtdauer. */
    private fun friendsMixStartMs(item: MediaItem?, durationMs: Long): Long {
        val sec = item?.mediaMetadata?.extras?.getFloat(EXTRA_MIX_START_SEC, -1f) ?: -1f
        return if (sec >= 0f) (sec * 1000f).toLong()
        else (durationMs * FRIENDS_MIX_START_FRACTION).toLong()
    }

    /** Position (ms), ab der die Überblendung beginnt: serverseitig optimierter Wert aus den
     * Metadaten-Extras, sonst [fadeMs] vor dem Titelende. */
    private fun friendsMixCrossfadeStartMs(item: MediaItem?, durationMs: Long, fadeMs: Long): Long {
        val sec = item?.mediaMetadata?.extras?.getFloat(EXTRA_CROSSFADE_START_SEC, -1f) ?: -1f
        return if (sec >= 0f) (sec * 1000f).toLong().coerceIn(0L, durationMs)
        else (durationMs - fadeMs).coerceAtLeast(0L)
    }

    private val audioManager by lazy {
        getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    /** Zentrale Audiofokus-Verwaltung für beide Player (siehe Klassenkommentar). Bildet in etwa
     * das Default-Verhalten von ExoPlayers eigenem AudioFocusManager nach (Ducken bei
     * TRANSIENT_CAN_DUCK, Pause+Auto-Resume bei TRANSIENT, Pause+Abgabe bei dauerhaftem Verlust). */
    private val focusListener = android.media.AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                activePlayer.pause()
                standbyPlayer.pause()
                abandonAudioFocus()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = activePlayer.playWhenReady
                activePlayer.pause()
                standbyPlayer.pause()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!crossfading) activePlayer.volume = 0.2f
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                if (!crossfading) activePlayer.volume = 1f
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    activePlayer.play()
                }
            }
        }
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        if (audioManager.requestAudioFocus(request) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    override fun onCreate() {
        super.onCreate()

        playerA = buildPlayer()
        playerB = buildPlayer()
        activePlayer = playerA
        standbyPlayer = playerB

        // Priorität Android Auto > Bluetooth (aptX-Erkennung) > Geräte-Lautsprecher,
        // live nachgeführt während der Wiedergabe (nicht nur beim Start).
        audioRoutingManager.attach(activePlayer)

        val openAppIntent = android.content.Intent(this, com.lethe.mediaplayer.MainActivity::class.java)
            .setFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val sessionActivityPendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaLibrarySession.Builder(this, activePlayer, LibrarySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // Ein gemeinsamer Listener auf beiden Player-Instanzen – onEvents liefert den auslösenden
        // Player mit, sodass Ereignisse des gerade nicht aktiven (Standby-)Players ignoriert werden.
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (player !== activePlayer) return
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED
                    )
                ) {
                    mirrorQueueToStandby()
                }
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    // Neuer aktiver Titel: 30%-Startoffset (FriendsMix) neu anwenden lassen,
                    // Herz-Symbol (Android Auto) auf den neuen Titel setzen und die zuletzt
                    // gehörte Warteschlange zum Fortsetzen (Auto-Wiedereinstieg) sichern.
                    friendsMixOffsetApplied = false
                    refreshCurrentFavorite()
                    updateCustomLayout()
                    persistQueue(force = true)
                    if (!crossfading && (settings.crossfadeEnabled.value || isFriendsMixActive())) {
                        // Manueller Titelwechsel (Skip) oder Wiederholung: sanft einblenden.
                        // Der automatische Übergang am Titelende läuft bereits überlappend über
                        // beginCrossfade()/crossfadeJob und wird hier über !crossfading ausgeschlossen.
                        fadeIn()
                    }
                }
                if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && activePlayer.playWhenReady) {
                    requestAudioFocus()
                }
            }
        }
        playerA.addListener(listener)
        playerB.addListener(listener)

        startFadeWatcher()
    }

    private fun buildPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // SmartCache: HTTP-Datenquelle wird transparent auf die Festplatte gespiegelt,
        // sodass bereits gehörte Titel (Lethe-Bibliothek + Audius) beim erneuten Abspielen
        // nicht erneut heruntergeladen werden. Beide Player-Instanzen teilen sich denselben
        // SimpleCache (dafür ausgelegt, gleichzeitig von mehreren Lesern genutzt zu werden).
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true) // Audius-Stream-Endpunkt leitet per 303 auf CDN weiter
        // DefaultDataSource statt reinem Http-Datenquelle: unterstützt zusätzlich file://
        // (importierte Audiodateien aus "Öffnen mit", siehe ExternalAudioImportBridge) neben http(s).
        val baseDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(mediaCache.cache)
            .setUpstreamDataSourceFactory(baseDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            // Audiofokus wird zentral für beide Player gemeinsam verwaltet (siehe focusListener) –
            // zwei gleichzeitige handleAudioFocus=true-Player würden sich während der Überblendung
            // beim Fokus-Handshake gegenseitig pausieren.
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /** Überwacht die Wiedergabeposition des aktiven Players und startet am Titelende die Überblendung. */
    private fun startFadeWatcher() {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            while (true) {
                val friendsMix = isFriendsMixActive()
                // FriendsMix erzwingt die Überblendung mit fester Dauer, unabhängig von den
                // Nutzereinstellungen; sonst gelten die konfigurierten Werte.
                val crossfadeOn = friendsMix || settings.crossfadeEnabled.value
                val fadeMs = if (friendsMix) FRIENDS_MIX_CROSSFADE_MS else settings.crossfadeSeconds.value * 1000L
                // FriendsMix: aktuellen Titel einmalig ab dem (optimierten) Einstiegspunkt starten.
                if (friendsMix && !friendsMixOffsetApplied && !crossfading && activePlayer.isPlaying) {
                    val d = activePlayer.duration
                    if (d != C.TIME_UNSET && d > 0) {
                        val startMs = friendsMixStartMs(activePlayer.currentMediaItem, d)
                        if (activePlayer.currentPosition < startMs - 500L) {
                            activePlayer.seekTo(startMs)
                        }
                        friendsMixOffsetApplied = true
                    }
                }
                if (crossfadeOn && activePlayer.isPlaying && !crossfading) {
                    val dur = activePlayer.duration
                    val pos = activePlayer.currentPosition
                    if (dur != C.TIME_UNSET && dur > 0 && activePlayer.hasNextMediaItem()) {
                        val remaining = dur - pos
                        val crossStartMs = if (friendsMix)
                            friendsMixCrossfadeStartMs(activePlayer.currentMediaItem, dur, fadeMs)
                        else (dur - fadeMs).coerceAtLeast(0L)
                        if (pos >= crossStartMs && remaining > 250) {
                            beginCrossfade(fadeMs, remaining, friendsMix)
                        } else if (!fadingIn && activePlayer.volume < 1f) {
                            // Direkt nach einem Titelwechsel ist "remaining" riesig (neuer Titel) ->
                            // ohne !fadingIn-Schutz würde dieser Zweig die Fade-In-Rampe aus fadeIn()
                            // binnen 200ms wieder auf volle Lautstärke zurücksetzen.
                            activePlayer.volume = 1f
                        }
                    }
                } else if (!crossfading && !fadingIn && activePlayer.volume < 1f) {
                    // Während eines laufenden Fade-Ins/Crossfades NICHT hart auf 1f zurückspringen,
                    // sonst überschreibt dieser Watcher (alle 200ms) die sanfte Rampe.
                    activePlayer.volume = 1f
                }
                // Warteschlange + Position throttled sichern (für Android-Auto-Wiedereinstieg).
                if (activePlayer.isPlaying) persistQueue()
                delay(200)
            }
        }
    }

    /**
     * Startet den nächsten Titel bereits auf dem Standby-Player (leise, volume=0) parallel zum
     * noch laufenden aktiven Titel und blendet beide über [fadeMs] gegenläufig – echter
     * akustischer Überlapp statt reinem Aus-/Einblenden auf einem einzigen Player. Die Rampe ist
     * exakt auf [remainingMs] (verbleibende Spielzeit des aktiven Titels) getaktet, damit der
     * Wechsel am eigentlichen Titelende abgeschlossen ist. Am Ende übernimmt der Standby-Player
     * die Rolle des aktiven Players (inkl. [MediaSession.setPlayer]).
     */
    private fun beginCrossfade(fadeMs: Long, remainingMs: Long, friendsMix: Boolean) {
        if (crossfading) return
        val nextIndex = activePlayer.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        crossfading = true
        crossfadeJob?.cancel()

        standbyPlayer.seekTo(nextIndex, 0L)
        standbyPlayer.prepare()
        standbyPlayer.volume = 0f
        audioRoutingManager.applyRoutingTo(standbyPlayer)
        standbyPlayer.playWhenReady = true

        crossfadeJob = scope.launch {
            if (friendsMix) {
                // Kurz auf die (nach dem prepare) bekannte Dauer des nächsten Titels warten und
                // ihn dann ab 30% starten. Danach das Fade-Timing an die REAL verbleibende Spielzeit
                // des aktiven Titels koppeln (die Warteschleife hat sie verkürzt), damit die Rampe
                // vor dem natürlichen Titelende abgeschlossen ist (kein doppelter Auto-Übergang).
                var waited = 0
                while (standbyPlayer.duration <= 0 && waited < 12) { delay(80); waited++ }
                val nextDur = standbyPlayer.duration
                if (nextDur > 0) {
                    standbyPlayer.seekTo(friendsMixStartMs(standbyPlayer.currentMediaItem, nextDur))
                }
            }
            val steps = 20
            val liveRemaining = (activePlayer.duration - activePlayer.currentPosition)
                .let { if (it > 0) it else remainingMs }
            val stepDelay = (liveRemaining / steps).coerceAtLeast(1L)
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                activePlayer.volume = (1f - t).coerceIn(0f, 1f)
                standbyPlayer.volume = t.coerceIn(0f, 1f)
                delay(stepDelay)
            }
            activePlayer.volume = 0f
            activePlayer.pause()

            // Rollentausch: der bisherige Standby-Player (jetzt voll eingeblendet) wird zum
            // aktiven Player der MediaSession, der bisherige aktive Player geht in Standby.
            val finished = activePlayer
            activePlayer = standbyPlayer
            standbyPlayer = finished

            activePlayer.volume = 1f
            mediaSession?.setPlayer(activePlayer)
            audioRoutingManager.attach(activePlayer)

            standbyPlayer.volume = 1f
            standbyPlayer.pause()

            // Der neue aktive Titel läuft im FriendsMix bereits ab 30% (oben geseekt) – kein
            // erneuter Offset-Seek durch den Fade-Watcher.
            friendsMixOffsetApplied = friendsMix

            crossfading = false
        }
    }

    /** Spiegelt Titel-Queue + Wiederholung/Shuffle des aktiven auf den Standby-Player, damit
     * dieser bei der nächsten Überblendung bereits denselben nächsten Titel kennt. */
    private fun mirrorQueueToStandby() {
        if (crossfading) return
        val items = (0 until activePlayer.mediaItemCount).map { activePlayer.getMediaItemAt(it) }
        standbyPlayer.setMediaItems(items, /* resetPosition = */ false)
        standbyPlayer.repeatMode = activePlayer.repeatMode
        standbyPlayer.shuffleModeEnabled = activePlayer.shuffleModeEnabled
    }

    private fun fadeIn() {
        fadeInJob?.cancel()
        val target = activePlayer
        fadeInJob = scope.launch {
            val fadeMs = settings.crossfadeSeconds.value * 1000L
            if (fadeMs <= 0) { target.volume = 1f; return@launch }
            fadingIn = true
            target.volume = 0f
            val steps = 20
            for (i in 1..steps) {
                target.volume = (i.toFloat() / steps)
                delay(fadeMs / steps)
            }
            target.volume = 1f
            fadingIn = false
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Wiedergabe stoppen, wenn nichts läuft und die App aus Recents entfernt wird
        if (!activePlayer.playWhenReady || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        fadeJob?.cancel()
        fadeInJob?.cancel()
        crossfadeJob?.cancel()
        abandonAudioFocus()
        audioRoutingManager.detach()
        serviceScope.cancel()
        mediaSession?.run {
            playerA.release()
            playerB.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Android-Auto-Browse-Baum. Der Wurzelknoten listet feste Kategorien auf ([onGetChildren] mit
     * [ROOT_ID]): Bibliothek, Favoriten, Playlists, FriendsMix und lokale Titel. Jede Kategorie ist
     * ein browsebarer Ordner, dessen Kinder entweder abspielbare Titel (`trk|<parent>|<id>`) oder –
     * bei "Playlists" – die einzelnen Playlist-Ordner (`playlist:<id>`) sind.
     *
     * Ein Tipp auf einen Titel im Fahrzeug löst [onSetMediaItems] aus: dort wird die KOMPLETTE
     * Titelliste des umschließenden Ordners geladen und ab dem angetippten Titel abgespielt (statt
     * nur den einen Titel), damit im Auto eine echte Warteschlange entsteht. [onAddMediaItems] löst
     * einzelne mediaIds (Titel/Playlist) zu abspielbaren [MediaItem]s mit echter Stream-URL auf.
     *
     * Vor jedem Netzwerkzugriff wird [SessionBridge.load] aufgerufen, weil Android Auto den Service
     * per Cold-Start hochziehen kann, ohne dass die UI zuvor lief (und damit ohne geladenes JWT).
     */
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        /** Meldet zusätzlich zur Standardsteuerung die eigene Herz-Aktion an und legt das
         * Herz-Symbol (Like) neben die Transport-Buttons – auch in der Android-Auto-Wiedergabe. */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            refreshCurrentFavorite()
            val available = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(ACTION_LIKE, android.os.Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available)
                .setCustomLayout(listOf(likeCommandButton()))
                .build()
        }

        /** Herz gedrückt (App-UI oder Android Auto): den laufenden Titel (an)liken bzw. entliken. */
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_LIKE) {
                val track = activePlayer.currentMediaItem?.toTrackOrNull()
                if (track != null) {
                    val newFav = !currentFavorite
                    currentFavorite = newFav
                    serviceScope.launch { runCatching { mediaRepository.setFavorite(track, newFav) } }
                    updateCustomLayout()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /**
         * Setzt die zuletzt gehörte Warteschlange fort, wenn das System/Android Auto die Wiedergabe
         * fortsetzen will (z.B. beim Einsteigen ins Auto), ohne dass die App-UI zuvor lief. Ohne diese
         * Implementierung zeigt Android Auto nur "Tippen zum Öffnen" statt automatisch weiterzuspielen.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val (tracks, index, position) = settings.getLastQueue()
                ?: return@future MediaSession.MediaItemsWithStartPosition(emptyList<MediaItem>(), 0, 0L)
            val items = tracks.map { it.toResumableMediaItem() }
            MediaSession.MediaItemsWithStartPosition(items, index.coerceIn(0, items.size - 1), position)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            withContext(Dispatchers.IO) { sessionBridge.load() }
            val items: List<MediaItem> = when (parentId) {
                ROOT_ID -> rootCategories()
                CAT_PLAYLISTS -> runCatching { mediaRepository.getPlaylists() }.getOrDefault(emptyList())
                    .map { playlistFolderItem(it) }
                else -> loadTracksForParent(parentId).map { it.toTrackItem(parentId) }
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            when {
                mediaId == ROOT_ID -> LibraryResult.ofItem(rootItem(), null)
                mediaId in CATEGORY_IDS -> LibraryResult.ofItem(categoryItem(mediaId), null)
                mediaId.startsWith(PLAYLIST_ID_PREFIX) -> {
                    withContext(Dispatchers.IO) { sessionBridge.load() }
                    val playlistId = mediaId.removePrefix(PLAYLIST_ID_PREFIX)
                    val playlist = runCatching { mediaRepository.getPlaylists() }.getOrDefault(emptyList())
                        .firstOrNull { it.playlistId == playlistId }
                    if (playlist != null) LibraryResult.ofItem(playlistFolderItem(playlist), null)
                    else LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
                }
                mediaId.startsWith(TRACK_ID_PREFIX) -> {
                    withContext(Dispatchers.IO) { sessionBridge.load() }
                    val (parentId, trackId) = parseTrackId(mediaId)
                    val track = loadTracksForParent(parentId).firstOrNull { it.id == trackId }
                    if (track != null) LibraryResult.ofItem(track.toTrackItem(parentId), null)
                    else LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
                }
                else -> LibraryResult.ofError<MediaItem>(SessionResult.RESULT_ERROR_BAD_VALUE)
            }
        }

        /**
         * Titel-Tipp im Auto: statt nur des einen angetippten Titels wird die vollständige
         * Titelliste des umschließenden Ordners als Warteschlange gesetzt und ab dem angetippten
         * Titel gestartet. Playlist-/Ordner-mediaIds werden ebenfalls zur vollen Liste expandiert.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            withContext(Dispatchers.IO) { sessionBridge.load() }
            if (mediaItems.size == 1) {
                val id = mediaItems[0].mediaId
                when {
                    id.startsWith(TRACK_ID_PREFIX) -> {
                        val (parentId, trackId) = parseTrackId(id)
                        val tracks = loadTracksForParent(parentId)
                        if (tracks.isNotEmpty()) {
                            val items = tracks.map { it.toPlayableMediaItem(parentId) }
                            val idx = tracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                            return@future MediaSession.MediaItemsWithStartPosition(items, idx, startPositionMs)
                        }
                    }
                    id.startsWith(PLAYLIST_ID_PREFIX) -> {
                        val items = resolvePlaylistTracks(id.removePrefix(PLAYLIST_ID_PREFIX))
                        if (items.isNotEmpty()) {
                            return@future MediaSession.MediaItemsWithStartPosition(items, 0, startPositionMs)
                        }
                    }
                }
            }
            val resolved = resolveItems(mediaItems)
            val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex
            MediaSession.MediaItemsWithStartPosition(resolved, start, startPositionMs)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> = serviceScope.future {
            withContext(Dispatchers.IO) { sessionBridge.load() }
            resolveItems(mediaItems)
        }

        /** Löst beliebige (browse-)mediaIds zu abspielbaren [MediaItem]s mit echter Stream-URL auf. */
        private suspend fun resolveItems(mediaItems: List<MediaItem>): List<MediaItem> =
            mediaItems.flatMap { item ->
                val id = item.mediaId
                when {
                    item.localConfiguration != null -> listOf(item)
                    id.startsWith(TRACK_ID_PREFIX) -> {
                        val (parentId, trackId) = parseTrackId(id)
                        loadTracksForParent(parentId).firstOrNull { it.id == trackId }
                            ?.let { listOf(it.toPlayableMediaItem(parentId)) } ?: emptyList()
                    }
                    id.startsWith(PLAYLIST_ID_PREFIX) -> resolvePlaylistTracks(id.removePrefix(PLAYLIST_ID_PREFIX))
                    else -> emptyList()
                }
            }

        private suspend fun resolvePlaylistTracks(playlistId: String): List<MediaItem> =
            loadTracksForParent(PLAYLIST_ID_PREFIX + playlistId)
                .map { it.toPlayableMediaItem(PLAYLIST_ID_PREFIX + playlistId) }

        private fun playlistFolderItem(dto: PlaylistDto): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(dto.playlistName)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .apply { dto.coverUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build()
            return MediaItem.Builder()
                .setMediaId(PLAYLIST_ID_PREFIX + dto.playlistId)
                .setMediaMetadata(metadata)
                .build()
        }
    }

    /** Lädt die Titelliste eines Browse-Ordners (Kategorie oder konkrete Playlist). */
    private suspend fun loadTracksForParent(parentId: String): List<Track> = runCatching {
        when {
            parentId == CAT_LIBRARY -> mediaRepository.getLibrary()
            parentId == CAT_FAVORITES -> mediaRepository.getUserMusic(favoritesOnly = true)
            parentId == CAT_FRIENDSMIX -> mediaRepository.getFriendsMix()
            parentId == CAT_LOCAL -> withContext(Dispatchers.IO) { localMediaScanner.scan() }
            parentId.startsWith(PLAYLIST_ID_PREFIX) ->
                mediaRepository.getUserMusic(playlistId = parentId.removePrefix(PLAYLIST_ID_PREFIX))
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /** Zerlegt eine Titel-mediaId `trk|<parentId>|<trackId>` in (parentId, trackId). */
    private fun parseTrackId(mediaId: String): Pair<String, String> {
        val rest = mediaId.removePrefix(TRACK_ID_PREFIX)
        val sep = rest.indexOf('|')
        return if (sep < 0) "" to rest else rest.substring(0, sep) to rest.substring(sep + 1)
    }

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setTitle(getString(com.lethe.mediaplayer.R.string.app_name))
                .build()
        )
        .build()

    private fun rootCategories(): List<MediaItem> = CATEGORY_IDS.map { categoryItem(it) }

    private fun categoryItem(id: String): MediaItem {
        val titleRes: Int
        val type: Int
        when (id) {
            CAT_LIBRARY -> { titleRes = com.lethe.mediaplayer.R.string.auto_cat_library; type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED }
            CAT_FAVORITES -> { titleRes = com.lethe.mediaplayer.R.string.auto_cat_favorites; type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED }
            CAT_PLAYLISTS -> { titleRes = com.lethe.mediaplayer.R.string.auto_cat_playlists; type = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS }
            CAT_FRIENDSMIX -> { titleRes = com.lethe.mediaplayer.R.string.auto_cat_friendsmix; type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED }
            CAT_LOCAL -> { titleRes = com.lethe.mediaplayer.R.string.auto_cat_local; type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED }
            else -> { titleRes = com.lethe.mediaplayer.R.string.app_name; type = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(titleRes))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .build()
            )
            .build()
    }

    /** Browse-Repräsentation eines Titels (mediaId trägt den Ordner für spätere Auflösung). */
    private fun Track.toTrackItem(parentId: String): MediaItem = MediaItem.Builder()
        .setMediaId(TRACK_ID_PREFIX + parentId + "|" + id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { coverUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build()
        )
        .build()

    /** Abspielbares [MediaItem] mit echter Stream-URL (mediaId trägt den Ordner für die Queue).
     * Aus dem Android-Auto-FriendsMix-Ordner gestartete Titel werden – wie in der App – mit dem
     * FriendsMix-Flag versehen (fester 15s-Crossfade + Titelstart ab 30%). */
    private fun Track.toPlayableMediaItem(parentId: String): MediaItem = MediaItem.Builder()
        .setMediaId(TRACK_ID_PREFIX + parentId + "|" + id)
        .setUri(Uri.parse(audioUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { coverUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .setExtras(android.os.Bundle().apply {
                    // Standard-Extras für Warteschlangen-Persistenz + Herz-Aktion (Rekonstruktion
                    // eines Track aus dem MediaItem, siehe toTrackOrNull).
                    putString(EXTRA_TRACK_ID, id)
                    putInt(EXTRA_DURATION_SEC, durationSec)
                    putBoolean(EXTRA_IS_LIBRARY, isLibraryTrack)
                    putString(EXTRA_SOURCE, source)
                    putBoolean(EXTRA_IS_FAVORITE, isFavorite)
                    if (parentId == CAT_FRIENDSMIX) {
                        putBoolean(EXTRA_FRIENDS_MIX, true)
                        mixStartSec?.let { putFloat(EXTRA_MIX_START_SEC, it) }
                        crossfadeStartSec?.let { putFloat(EXTRA_CROSSFADE_START_SEC, it) }
                    }
                })
                .build()
        )
        .build()

    /** Abspielbares [MediaItem] für die Wiedergabe-Fortsetzung (onPlaybackResumption): baut den
     * persistierten [Track] mit Stream-URL + Standard-Extras wieder auf, damit auch nach dem
     * Fortsetzen Persistenz und Herz-Aktion weiter funktionieren. */
    private fun Track.toResumableMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(audioUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .apply { coverUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .setExtras(android.os.Bundle().apply {
                    putString(EXTRA_TRACK_ID, id)
                    putInt(EXTRA_DURATION_SEC, durationSec)
                    putBoolean(EXTRA_IS_LIBRARY, isLibraryTrack)
                    putString(EXTRA_SOURCE, source)
                    putBoolean(EXTRA_IS_FAVORITE, isFavorite)
                })
                .build()
        )
        .build()

    /** Rekonstruiert einen [Track] aus einem laufenden [MediaItem] anhand der Standard-Extras
     * (für die Warteschlangen-Persistenz und die Herz-Aktion). null, wenn keine Extras vorliegen. */
    private fun MediaItem.toTrackOrNull(): Track? {
        val extras = mediaMetadata.extras ?: return null
        val trackId = extras.getString(EXTRA_TRACK_ID) ?: return null
        val uri = localConfiguration?.uri?.toString() ?: return null
        return Track(
            id = trackId,
            title = mediaMetadata.title?.toString() ?: "Unbekannt",
            artist = mediaMetadata.artist?.toString().orEmpty(),
            coverUrl = mediaMetadata.artworkUri?.toString(),
            audioUrl = uri,
            durationSec = extras.getInt(EXTRA_DURATION_SEC, 0),
            isFavorite = extras.getBoolean(EXTRA_IS_FAVORITE, false),
            isLibraryTrack = extras.getBoolean(EXTRA_IS_LIBRARY, false),
            source = extras.getString(EXTRA_SOURCE) ?: "user"
        )
    }

    /** Aktualisiert [currentFavorite] anhand des gerade aktiven Titels (Herz-Symbol Android Auto). */
    private fun refreshCurrentFavorite() {
        currentFavorite = activePlayer.currentMediaItem?.mediaMetadata?.extras
            ?.getBoolean(EXTRA_IS_FAVORITE, false) == true
    }

    /** Der Herz-Button (Like) für das Session-CustomLayout – gefüllt, wenn der Titel favorisiert ist. */
    private fun likeCommandButton(): CommandButton = CommandButton.Builder()
        .setDisplayName(
            getString(
                if (currentFavorite) com.lethe.mediaplayer.R.string.auto_unlike
                else com.lethe.mediaplayer.R.string.auto_like
            )
        )
        .setIconResId(
            if (currentFavorite) com.lethe.mediaplayer.R.drawable.ic_favorite
            else com.lethe.mediaplayer.R.drawable.ic_favorite_border
        )
        .setSessionCommand(SessionCommand(ACTION_LIKE, android.os.Bundle.EMPTY))
        .build()

    /** Schreibt das aktuelle CustomLayout (Herz-Symbol) an alle Controller (App + Android Auto). */
    private fun updateCustomLayout() {
        mediaSession?.setCustomLayout(listOf(likeCommandButton()))
    }

    /** Sichert die laufende Warteschlange + Index + Position (für den Android-Auto-Wiedereinstieg).
     * Throttled auf 5s, außer bei [force] (z.B. Titelwechsel). */
    private fun persistQueue(force: Boolean = false) {
        val count = activePlayer.mediaItemCount
        if (count == 0) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAtMs < 5000) return
        val tracks = (0 until count).mapNotNull { activePlayer.getMediaItemAt(it).toTrackOrNull() }
        if (tracks.isEmpty()) return
        lastPersistAtMs = now
        val currentId = activePlayer.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_TRACK_ID)
        val index = tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val position = activePlayer.currentPosition.coerceAtLeast(0)
        // Serialisierung (Gson) + Prefs-Schreiben abseits des Player-/Main-Threads.
        serviceScope.launch { settings.saveLastQueue(tracks, index, position) }
    }

    companion object {
        private const val ROOT_ID = "root"
        private const val PLAYLIST_ID_PREFIX = "playlist:"
        private const val TRACK_ID_PREFIX = "trk|"
        private const val CAT_LIBRARY = "cat:library"
        private const val CAT_FAVORITES = "cat:favorites"
        private const val CAT_PLAYLISTS = "cat:playlists"
        private const val CAT_FRIENDSMIX = "cat:friendsmix"
        private const val CAT_LOCAL = "cat:local"
        private val CATEGORY_IDS = listOf(CAT_LIBRARY, CAT_FAVORITES, CAT_PLAYLISTS, CAT_FRIENDSMIX, CAT_LOCAL)

        /** Session-Command: den laufenden Titel liken/entliken (Herz-Button, auch in Android Auto). */
        private const val ACTION_LIKE = "com.lethe.mediaplayer.LIKE"

        /** MediaMetadata-Extra-Schlüssel für die Rekonstruktion eines Track aus einem MediaItem
         * (Warteschlangen-Persistenz + Herz-Aktion, siehe toTrackOrNull). */
        const val EXTRA_TRACK_ID = "lethe_track_id"
        const val EXTRA_DURATION_SEC = "lethe_duration_sec"
        const val EXTRA_IS_LIBRARY = "lethe_is_library"
        const val EXTRA_SOURCE = "lethe_source"
        const val EXTRA_IS_FAVORITE = "lethe_is_favorite"

        /** MediaMetadata-Extra-Schlüssel: markiert einen Titel als Teil des FriendsMix. */
        const val EXTRA_FRIENDS_MIX = "lethe_friends_mix"
        /** MediaMetadata-Extra (Float, Sekunden): serverseitig optimierter Einstiegspunkt (Ziel 28-30%). */
        const val EXTRA_MIX_START_SEC = "lethe_mix_start_sec"
        /** MediaMetadata-Extra (Float, Sekunden): serverseitig optimierter Überblend-Start (min. 10s vor Ende). */
        const val EXTRA_CROSSFADE_START_SEC = "lethe_crossfade_start_sec"
        /** Feste Überblendungsdauer im FriendsMix, unabhängig von den Nutzereinstellungen. */
        private const val FRIENDS_MIX_CROSSFADE_MS = 15_000L
        /** Anteil der Gesamtdauer, ab dem FriendsMix-Titel starten (mix-typischer Einstieg). */
        private const val FRIENDS_MIX_START_FRACTION = 0.30f
    }
}
