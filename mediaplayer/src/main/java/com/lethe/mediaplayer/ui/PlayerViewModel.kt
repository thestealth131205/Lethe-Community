package com.lethe.mediaplayer.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lethe.mediaplayer.auth.SessionBridge
import com.lethe.mediaplayer.auth.SessionResult
import com.lethe.mediaplayer.cast.CastManager
import com.lethe.mediaplayer.data.ArtistDto
import com.lethe.mediaplayer.data.ArtistProfile
import com.lethe.mediaplayer.data.BpmStatusDto
import com.lethe.mediaplayer.data.ExternalAudioImportBridge
import com.lethe.mediaplayer.data.FriendsMixContactDto
import com.lethe.mediaplayer.data.LocalAudio
import com.lethe.mediaplayer.data.LocalMediaScanner
import com.lethe.mediaplayer.data.MediaRepository
import com.lethe.mediaplayer.data.PlaylistDto
import com.lethe.mediaplayer.data.Track
import com.lethe.mediaplayer.data.UserMeDto
import com.lethe.mediaplayer.player.AutoDownloadManager
import com.lethe.mediaplayer.player.MediaCache
import com.lethe.mediaplayer.player.PlaybackSettings
import com.lethe.mediaplayer.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class BrowseTab { LIBRARY, LOCAL, AUDIUS, FAVORITES, PLAYLISTS }

/** Status des Uploads eines lokalen Songs in eine Playlist. */
sealed interface UploadState {
    data object Idle : UploadState
    data object Uploading : UploadState
    data class Done(val playlistName: String) : UploadState
    data class Error(val message: String) : UploadState
}

/** Ein Künstler, abgeleitet aus den Favoriten – sortiert nach Anzahl der Herzen des Nutzers. */
data class ArtistItem(
    val name: String,
    val favoriteCount: Int,
    val tracks: List<Track>
)

data class BrowseState(
    val loading: Boolean = false,
    val library: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    /** Global vom Admin kuratierte Lethe-Playlists (bei jedem Nutzer sichtbar, neben den Lieblingssongs). */
    val lethePlaylists: List<PlaylistDto> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val playlistTracks: Map<String, List<Track>> = emptyMap(),
    /** Der dynamisch vom Server zusammengestellte FriendsMix (Favoriten der Freunde). */
    val friendsMix: List<Track> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: MediaRepository,
    private val sessionBridge: SessionBridge,
    val playerController: PlayerController,
    val settings: PlaybackSettings,
    val castManager: CastManager,
    val mediaCache: MediaCache,
    val autoDownloadManager: AutoDownloadManager,
    private val importBridge: ExternalAudioImportBridge,
    private val localMediaScanner: LocalMediaScanner
) : ViewModel() {

    /** Feuert, sobald eine von außen ("Öffnen mit") importierte Datei zu spielen begonnen hat. */
    private val _externalPlaybackEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val externalPlaybackEvent: SharedFlow<Unit> = _externalPlaybackEvent

    /** Ob gerade eine von MainActivity übergebene Audio-URI auf Verarbeitung wartet (reaktiv für Compose). */
    val pendingImportUri: StateFlow<Uri?> = importBridge.pendingUri

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState

    private val _accountInfo = MutableStateFlow<UserMeDto?>(null)
    val accountInfo: StateFlow<UserMeDto?> = _accountInfo

    /** Lädt Name + fake_number des angemeldeten Lethe-Kontos (für die App-Infos-Ansicht). */
    fun loadAccountInfo() {
        viewModelScope.launch {
            _accountInfo.value = withContext(Dispatchers.IO) { repo.getMe() }
        }
    }

    private val _session = MutableStateFlow<SessionResult?>(null)
    val session: StateFlow<SessionResult?> = _session

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse

    /** Automatisch generierte 2x2-Collage der ersten 4 Lieblingssongs (Homescreen-Kachel). */
    private val _favoritesCoverUrl = MutableStateFlow<String?>(null)
    val favoritesCoverUrl: StateFlow<String?> = _favoritesCoverUrl

    private val _tab = MutableStateFlow(BrowseTab.LIBRARY)
    val tab: StateFlow<BrowseTab> = _tab

    private val _audiusTracks = MutableStateFlow<List<Track>>(emptyList())
    val audiusTracks: StateFlow<List<Track>> = _audiusTracks

    private val _audiusQuery = MutableStateFlow("")
    val audiusQuery: StateFlow<String> = _audiusQuery

    private val _audiusLoading = MutableStateFlow(false)
    val audiusLoading: StateFlow<Boolean> = _audiusLoading

    private val _audiusError = MutableStateFlow<String?>(null)
    val audiusError: StateFlow<String?> = _audiusError

    /** Aktuell gewähltes Audius-Genre für die Kategorie-Filterung (null = alle/Trending). */
    private val _audiusGenre = MutableStateFlow<String?>(null)
    val audiusGenre: StateFlow<String?> = _audiusGenre

    private var audiusLoadedOnce = false
    private var audiusSearchJob: Job? = null

    // ── Lokale Medien (geräteweiter MediaStore-Scan) ────────────────────────────
    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks

    private val _localLoading = MutableStateFlow(false)
    val localLoading: StateFlow<Boolean> = _localLoading

    /** true sobald mindestens einmal erfolgreich (mit Berechtigung) gescannt wurde. */
    private val _localScanned = MutableStateFlow(false)
    val localScanned: StateFlow<Boolean> = _localScanned

    fun setTab(t: BrowseTab) {
        _tab.value = t
        if (t == BrowseTab.AUDIUS) ensureAudiusLoaded()
    }

    /**
     * Durchsucht den geräteweiten Medienspeicher nach lokalen Musiktiteln (min. 1 Minute lang,
     * Interpret + Titel im Tag). Erfordert eine bereits erteilte Audio-Leseberechtigung.
     */
    fun scanLocalMedia() {
        viewModelScope.launch {
            _localLoading.value = true
            try {
                _localTracks.value = withContext(Dispatchers.IO) { localMediaScanner.scan() }
                _localScanned.value = true
            } catch (_: Exception) {
                // Scan fehlgeschlagen (z.B. Berechtigung entzogen) – Liste bleibt unverändert
            } finally {
                _localLoading.value = false
            }
        }
    }

    /** Lädt beim ersten Öffnen des Audius-Tabs die Trending-Tracks. */
    fun ensureAudiusLoaded() {
        if (audiusLoadedOnce) return
        audiusLoadedOnce = true
        loadAudius(null)
    }

    /** Suchbegriff für die Audius-Bibliothek, mit 500ms Debounce. */
    fun updateAudiusQuery(query: String) {
        _audiusQuery.value = query
        audiusSearchJob?.cancel()
        audiusSearchJob = viewModelScope.launch {
            delay(500)
            loadAudius(query.takeIf { it.isNotBlank() })
        }
    }

    /** Wählt eine Audius-Kategorie (Genre) für die Trending-Filterung; null = alle. */
    fun setAudiusGenre(genre: String?) {
        if (_audiusGenre.value == genre) return
        _audiusGenre.value = genre
        loadAudius(_audiusQuery.value.takeIf { it.isNotBlank() })
    }

    private fun loadAudius(query: String?) {
        viewModelScope.launch {
            _audiusLoading.value = true
            _audiusError.value = null
            try {
                val tracks = withContext(Dispatchers.IO) {
                    if (query.isNullOrBlank()) repo.getAudiusTrending(_audiusGenre.value) else repo.searchAudius(query)
                }
                _audiusTracks.value = tracks
                if (tracks.isEmpty() && !query.isNullOrBlank()) {
                    _audiusError.value = "Keine Treffer für \"$query\"."
                }
            } catch (e: Exception) {
                _audiusError.value = "Audius nicht erreichbar."
            } finally {
                _audiusLoading.value = false
            }
        }
    }

    /** Aktuell wiedergegebener Titel – während des Castens der Cast-Titel, sonst der lokale. */
    val current: StateFlow<Track?> = combine(
        playerController.current, castManager.isCasting, castManager.castTrack
    ) { local, casting, castTrack -> if (casting) castTrack else local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isPlaying: StateFlow<Boolean> = combine(
        playerController.isPlaying, castManager.isCasting, castManager.isPlaying
    ) { local, casting, castPlaying -> if (casting) castPlaying else local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val positionMs: StateFlow<Long> = combine(
        playerController.positionMs, castManager.isCasting, castManager.positionMs
    ) { local, casting, castPos -> if (casting) castPos else local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val durationMs: StateFlow<Long> = combine(
        playerController.durationMs, castManager.isCasting, castManager.durationMs
    ) { local, casting, castDur -> if (casting) castDur else local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** Aktuelle Wiedergabe-Warteschlange (lokal oder Cast, je nach Wiedergabeziel). */
    val queue: StateFlow<List<Track>> = combine(
        playerController.queue, castManager.isCasting, castManager.queue
    ) { local, casting, castQueue -> if (casting) castQueue else local }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Beim Verbinden mit einem Cast-Gerät die aktuelle Warteschlange dorthin übertragen
        // und die lokale Wiedergabe pausieren.
        castManager.onCastSessionStarted = {
            playerController.pause()
            playerController.currentQueue()?.let { (list, index) ->
                castManager.loadQueue(list, index)
            }
        }
        // Meldet jeden neu gestarteten Lethe-Library-Titel (auch bei Weiter/Zurück) für den Stream-Zähler.
        viewModelScope.launch {
            current.filterNotNull().distinctUntilChanged { old, new -> old.id == new.id }.collect { track ->
                withContext(Dispatchers.IO) { repo.registerPlay(track) }
            }
        }
    }

    fun togglePlayPause() {
        if (castManager.isCasting.value) castManager.togglePlayPause() else playerController.togglePlayPause()
    }

    fun next() {
        if (castManager.isCasting.value) castManager.next() else playerController.next()
    }

    fun previous() {
        if (castManager.isCasting.value) castManager.previous() else playerController.previous()
    }

    fun seekTo(ms: Long) {
        if (castManager.isCasting.value) castManager.seekTo(ms) else playerController.seekTo(ms)
    }

    /** Springt in der aktuellen Warteschlange (Warteschlangenansicht) zu einem bestimmten Titel. */
    fun playQueueIndex(index: Int) {
        if (castManager.isCasting.value) castManager.playAtIndex(index) else playerController.playAtIndex(index)
    }

    /** Prüft die Lethe-Session und lädt bei Erfolg die Inhalte. */
    fun loadSession() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { sessionBridge.load() }
            _session.value = result
            if (result is SessionResult.Available) {
                playerController.connect()
                castManager.start()
                refresh()
                loadAccountInfo()
                restoreArtistSession()
                importPendingExternalAudioIfAny()
                startFavoritesAutoRefresh()
            }
        }
    }

    private var favoritesAutoRefreshStarted = false

    /** Aktualisiert die Lieblingssongs-Kachel alle 60 Sekunden (z.B. neue Likes von anderen Geräten). */
    private fun startFavoritesAutoRefresh() {
        if (favoritesAutoRefreshStarted) return
        favoritesAutoRefreshStarted = true
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val favorites = withContext(Dispatchers.IO) { repo.getUserMusic(favoritesOnly = true) }
                    val friendsMix = withContext(Dispatchers.IO) { runCatching { repo.getFriendsMix() }.getOrDefault(_browse.value.friendsMix) }
                    _browse.value = _browse.value.copy(
                        favorites = favorites,
                        artists = deriveArtists(favorites),
                        friendsMix = friendsMix
                    )
                    _favoritesCoverUrl.value = withContext(Dispatchers.IO) { repo.getFavoritesCover() }
                } catch (_: Exception) {
                    // Naechster Versuch in 60s
                }
            }
        }
    }

    /**
     * Registrierung als Musik-Player: prüft, ob MainActivity per "Öffnen mit"/"Teilen" eine
     * Audio-URI über die Bridge übergeben hat, kopiert sie in den SmartCache-Importordner
     * und startet die Wiedergabe. Cover wird nicht manuell extrahiert – Media3 liest eingebettete
     * ID3-Artworks bereits automatisch aus lokalen Dateien in playerController.artwork.
     */
    fun importPendingExternalAudioIfAny() {
        val uri = importBridge.pendingUri.value ?: return
        importBridge.consume()
        viewModelScope.launch {
            try {
                val track = withContext(Dispatchers.IO) { importExternalAudioTrack(uri) }
                play(listOf(track), 0)
                _externalPlaybackEvent.tryEmit(Unit)
            } catch (_: Exception) {
                // Datei nicht lesbar/kein gültiges Audioformat – wird stillschweigend ignoriert
            }
        }
    }

    /** Ob eine aus der Lethe-Haupt-App (Chat-Cast-Symbol) übergebene Stream-URL wartet (reaktiv für Compose). */
    val pendingStreamUrl: StateFlow<String?> = importBridge.pendingStreamUrl

    /**
     * Streamt eine aus der Lethe-Haupt-App (Chat-Cast-Symbol) übergebene vollständige Musik-URL.
     * Liest vorab die ID3-Tags (Titel, Künstler, Dauer, eingebettetes Cover) über
     * MediaMetadataRetriever aus, baut daraus einen Track und startet die Wiedergabe. ExoPlayer
     * streamt die URL direkt (kein Download in den Cache); von dort kann der Nutzer casten.
     */
    fun playPendingExternalStreamIfAny() {
        val url = importBridge.pendingStreamUrl.value ?: return
        importBridge.consumeStream()
        viewModelScope.launch {
            try {
                val track = withContext(Dispatchers.IO) { buildStreamTrack(url) }
                play(listOf(track), 0)
                _externalPlaybackEvent.tryEmit(Unit)
            } catch (_: Exception) {
                // URL nicht abspielbar – wird stillschweigend ignoriert
            }
        }
    }

    /** Liest die ID3-Tags einer entfernten Musik-URL (Titel, Künstler, Dauer, Cover) und baut einen Stream-Track. */
    private fun buildStreamTrack(url: String): Track {
        var title: String? = null
        var artist: String? = null
        var durationSec = 0
        var coverUri: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(url, HashMap<String, String>())
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotBlank() }
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotBlank() }
            durationSec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { (it / 1000L).toInt() } ?: 0
            retriever.embeddedPicture?.let { pic ->
                val coverFile = File(appContext.cacheDir, "stream_cover_${UUID.randomUUID()}.jpg")
                coverFile.writeBytes(pic)
                coverUri = Uri.fromFile(coverFile).toString()
            }
        } catch (_: Exception) {
            // Metadaten nicht lesbar (z.B. kein ID3) – dann streamt der Player trotzdem;
            // ExoPlayer extrahiert eingebettete Cover zusätzlich automatisch in playerController.artwork.
        } finally {
            runCatching { retriever.release() }
        }
        val fallbackName = url.substringAfterLast('/').substringBefore('?')
            .substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Musik" }
        return Track(
            id = "stream_${url.hashCode()}",
            title = title ?: fallbackName,
            artist = artist ?: "",
            coverUrl = coverUri,
            audioUrl = url,
            durationSec = durationSec,
            source = "external_stream"
        )
    }

    /** Kopiert eine externe Audio-URI in den SmartCache-Importordner und liest Metadaten aus. */
    private fun importExternalAudioTrack(uri: Uri): Track {
        val local = readLocalAudio(uri)
        val extension = local.fileName.substringAfterLast('.', "mp3").ifBlank { "mp3" }
        val destFile = File(mediaCache.importedAudioDir, "${UUID.randomUUID()}.$extension")
        destFile.writeBytes(local.bytes)
        return Track(
            id = "imported_${destFile.name}",
            title = local.title ?: local.fileName,
            artist = local.artist ?: "",
            coverUrl = null,
            audioUrl = Uri.fromFile(destFile).toString(),
            durationSec = local.durationSec,
            source = "imported"
        )
    }

    fun refresh() {
        viewModelScope.launch {
            _browse.value = _browse.value.copy(loading = true, error = null)
            try {
                val library = withContext(Dispatchers.IO) { repo.getLibrary() }
                val favorites = withContext(Dispatchers.IO) { repo.getUserMusic(favoritesOnly = true) }
                val playlists = withContext(Dispatchers.IO) { repo.getPlaylists() }
                val lethePlaylists = withContext(Dispatchers.IO) { runCatching { repo.getLethePlaylists() }.getOrDefault(emptyList()) }
                val friendsMix = withContext(Dispatchers.IO) { runCatching { repo.getFriendsMix() }.getOrDefault(emptyList()) }
                _browse.value = BrowseState(
                    loading = false,
                    library = library,
                    favorites = favorites,
                    playlists = playlists,
                    lethePlaylists = lethePlaylists,
                    artists = deriveArtists(favorites),
                    friendsMix = friendsMix
                )
                autoDownloadManager.syncIfEnabled(AutoDownloadManager.FAVORITES_KEY, favorites)
                _favoritesCoverUrl.value = withContext(Dispatchers.IO) { repo.getFavoritesCover() }
            } catch (e: Exception) {
                _browse.value = _browse.value.copy(loading = false, error = e.message ?: "Fehler beim Laden")
            }
        }
    }

    fun loadPlaylist(playlistId: String) {
        if (_browse.value.playlistTracks.containsKey(playlistId)) return
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) { repo.getUserMusic(playlistId = playlistId) }
            _browse.value = _browse.value.copy(
                playlistTracks = _browse.value.playlistTracks + (playlistId to tracks)
            )
            autoDownloadManager.syncIfEnabled(playlistId, tracks)
        }
    }

    /** Lädt die Titel einer Lethe-Playlist (global vom Admin) in den playlistTracks-Cache (für die Collage). */
    fun loadLethePlaylist(playlistId: String) {
        if (_browse.value.playlistTracks.containsKey(playlistId)) return
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) { runCatching { repo.getLethePlaylistTracks(playlistId) }.getOrDefault(emptyList()) }
            _browse.value = _browse.value.copy(
                playlistTracks = _browse.value.playlistTracks + (playlistId to tracks)
            )
        }
    }

    /** Lädt die Titel einer Lethe-Playlist (falls nötig) und startet die Wiedergabe. */
    fun playLethePlaylist(playlistId: String) {
        viewModelScope.launch {
            val cached = _browse.value.playlistTracks[playlistId]
            val tracks = cached ?: withContext(Dispatchers.IO) { repo.getLethePlaylistTracks(playlistId) }
            if (cached == null) {
                _browse.value = _browse.value.copy(
                    playlistTracks = _browse.value.playlistTracks + (playlistId to tracks)
                )
            }
            if (tracks.isNotEmpty()) play(tracks, 0)
        }
    }

    /** Ersetzt bei automatisch heruntergeladenen Titeln die Stream-URL durch die lokale Datei. */
    private fun resolveLocal(list: List<Track>): List<Track> = list.map { t ->
        autoDownloadManager.localUriFor(t.id)?.let { local -> t.copy(audioUrl = local) } ?: t
    }

    private fun play(list: List<Track>, index: Int, friendsMix: Boolean = false) {
        val resolved = resolveLocal(list)
        if (castManager.isCasting.value) castManager.loadQueue(resolved, index) else playerController.play(resolved, index, friendsMix)
    }

    fun playFrom(list: List<Track>, index: Int) {
        play(list, index)
    }

    /** Startet die Favoriten (Lieblingssongs) des Nutzers. */
    fun playFavorites() {
        val f = _browse.value.favorites
        if (f.isNotEmpty()) play(f, 0)
    }

    // ── FriendsMix ───────────────────────────────────────────────────────────

    /** Kontakte für die FriendsMix-Gruppenauswahl (Name + Profilbild + Zugehörigkeit). */
    private val _friendsContacts = MutableStateFlow<List<FriendsMixContactDto>>(emptyList())
    val friendsContacts: StateFlow<List<FriendsMixContactDto>> = _friendsContacts

    private val _friendsContactsLoading = MutableStateFlow(false)
    val friendsContactsLoading: StateFlow<Boolean> = _friendsContactsLoading

    /** Lädt die akzeptierten Kontakte für die FriendsMix-Gruppen-Verwaltung. */
    fun loadFriendsContacts() {
        viewModelScope.launch {
            _friendsContactsLoading.value = true
            try {
                _friendsContacts.value = withContext(Dispatchers.IO) { repo.getFriendsMixContacts() }
            } catch (_: Exception) {
                // Kontakte konnten nicht geladen werden – Liste bleibt unverändert
            } finally {
                _friendsContactsLoading.value = false
            }
        }
    }

    /** Fügt einen Kontakt zur FriendsMix-Gruppe hinzu bzw. entfernt ihn und aktualisiert den Mix. */
    fun setFriendInGroup(friendId: String, inGroup: Boolean) {
        // Optimistisches UI-Update der Auswahl.
        _friendsContacts.value = _friendsContacts.value.map {
            if (it.userId == friendId) it.copy(inGroup = inGroup) else it
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setFriendInGroup(friendId, inGroup) }
            val mix = withContext(Dispatchers.IO) { runCatching { repo.getFriendsMix() }.getOrDefault(_browse.value.friendsMix) }
            _browse.value = _browse.value.copy(friendsMix = mix)
        }
    }

    // ── Jam (geteilte Live-Playlist, per QR-Code beitretbar) ──────────────────

    private val _jamState = MutableStateFlow<com.lethe.mediaplayer.data.JamStateDto?>(null)
    val jamState: StateFlow<com.lethe.mediaplayer.data.JamStateDto?> = _jamState

    private val _jamError = MutableStateFlow<String?>(null)
    val jamError: StateFlow<String?> = _jamError

    private var jamPollJob: Job? = null

    /** Eigene Nutzer-ID (für den Host-/Teilnehmer-Vergleich in der UI). */
    val ownUserId: String?
        get() = (_session.value as? SessionResult.Available)?.userId

    /** Startet einen neuen Jam mit der aktuellen Warteschlange als Startplaylist. */
    fun startJam() {
        viewModelScope.launch {
            _jamError.value = null
            val initial = playerController.queue.value
            val state = withContext(Dispatchers.IO) { repo.createJam(initial) }
            if (state == null) {
                _jamError.value = "Jam konnte nicht gestartet werden."
                return@launch
            }
            _jamState.value = state
            startJamPolling()
        }
    }

    /** Tritt einem Jam anhand des gescannten QR-Codeinhalts bei ("lethejam:<id>"). */
    fun joinJamFromQrContent(content: String) {
        val jamId = content.removePrefix(JAM_QR_PREFIX).trim()
        if (jamId.isBlank() || jamId == content) {
            _jamError.value = "Ungültiger Jam-QR-Code."
            return
        }
        viewModelScope.launch {
            _jamError.value = null
            val state = withContext(Dispatchers.IO) { repo.joinJam(jamId) }
            if (state == null) {
                _jamError.value = "Jam nicht gefunden oder bereits beendet."
                return@launch
            }
            _jamState.value = state
            val tracks = repo.jamTracksAsTracks(state)
            if (tracks.isNotEmpty()) play(tracks, 0)
            startJamPolling()
        }
    }

    /** Fügt den aktuell spielenden Titel der geteilten Jam-Playlist hinzu. */
    fun addCurrentTrackToJam() {
        playerController.current.value?.let { addTrackToJam(it) }
    }

    /** Fügt einen Titel der geteilten Jam-Playlist hinzu (für alle Teilnehmer sichtbar). */
    fun addTrackToJam(track: Track) {
        val jamId = _jamState.value?.id ?: return
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { repo.addTracksToJam(jamId, listOf(track)) }
            if (state != null) _jamState.value = state
        }
    }

    private fun startJamPolling() {
        jamPollJob?.cancel()
        var knownTrackIds = _jamState.value?.playlist?.map { it.id }?.toSet() ?: emptySet()
        jamPollJob = viewModelScope.launch {
            while (true) {
                delay(4000)
                val jamId = _jamState.value?.id ?: break
                val updated = withContext(Dispatchers.IO) { repo.getJam(jamId) }
                if (updated == null || !updated.active) {
                    _jamState.value = null
                    break
                }
                val newDtos = updated.playlist.filter { it.id !in knownTrackIds }
                knownTrackIds = updated.playlist.map { it.id }.toSet()
                if (newDtos.isNotEmpty()) {
                    playerController.appendToQueue(repo.jamTracksAsTracks(updated.copy(playlist = newDtos)))
                }
                _jamState.value = updated
            }
        }
    }

    /** Verlässt den aktuellen Jam (beendet ihn für alle, falls man der Host ist). */
    fun leaveJam() {
        val jamId = _jamState.value?.id ?: return
        jamPollJob?.cancel()
        _jamState.value = null
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.leaveJam(jamId) } }
    }

    /** Beendet den Jam vorzeitig (nur als Host aufrufbar). */
    fun endJam() {
        val jamId = _jamState.value?.id ?: return
        jamPollJob?.cancel()
        _jamState.value = null
        viewModelScope.launch { withContext(Dispatchers.IO) { repo.endJam(jamId) } }
    }

    companion object {
        const val JAM_QR_PREFIX = "lethejam:"
    }

    /** Startet den FriendsMix (Favoriten der Freunde) im Player mit fester Überblendung. */
    fun playFriendsMix() {
        val mix = _browse.value.friendsMix
        if (mix.isNotEmpty()) play(mix, 0, friendsMix = true)
    }

    /**
     * Startet eine Playlist im DJ-Mix-Modus (wie der FriendsMix: feste 15s-Überblendung,
     * Einstieg ab 30% des Titels). Wird für Playlists mit play_as_mix=true genutzt.
     */
    fun playPlaylistAsMix(list: List<Track>) {
        if (list.isEmpty()) return
        if (!castManager.isCasting.value && playerController.shuffle.value) {
            playerController.toggleShuffle()
        }
        play(list, 0, friendsMix = true)
    }

    /** Startet alle Favoriten eines Künstlers. */
    fun playArtist(artist: ArtistItem) {
        if (artist.tracks.isNotEmpty()) play(artist.tracks, 0)
    }

    /** Lädt die Titel einer Playlist (falls nötig) und startet die Wiedergabe. */
    fun playPlaylist(playlistId: String) {
        viewModelScope.launch {
            val cached = _browse.value.playlistTracks[playlistId]
            val tracks = cached ?: withContext(Dispatchers.IO) { repo.getUserMusic(playlistId = playlistId) }
            if (cached == null) {
                _browse.value = _browse.value.copy(
                    playlistTracks = _browse.value.playlistTracks + (playlistId to tracks)
                )
            }
            if (tracks.isNotEmpty()) play(tracks, 0)
        }
    }

    /**
     * Schaltet den automatischen Download für die Favoriten oder eine Playlist um (Toggle in der
     * Bibliotheks-Ansicht). Bei Aktivierung werden alle aktuell geladenen Titel heruntergeladen,
     * bei Deaktivierung werden die lokal gespeicherten Dateien wieder gelöscht.
     */
    fun toggleAutoDownload(key: String, tracks: List<Track>, enabled: Boolean) {
        autoDownloadManager.setEnabled(key, enabled, tracks)
    }

    /** Ob der aktuell spielende Titel als Favorit markiert ist (für die Now-Playing-Leiste). */
    fun isFavoriteTrack(track: Track?): Boolean {
        if (track == null) return false
        if (track.isFavorite) return true
        return _browse.value.favorites.any {
            it.id == track.id || (it.title == track.title && it.artist == track.artist)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val newFav = !isFavoriteTrack(track)
            withContext(Dispatchers.IO) { repo.setFavorite(track, newFav) }
            refresh()
        }
    }

    /**
     * Lädt einen lokal ausgewählten Song hoch und fügt ihn der Playlist hinzu.
     * [playlistId] null + [playlistName] = neue Playlist anlegen.
     */
    fun addLocalSongToPlaylist(uri: Uri, playlistId: String?, playlistName: String) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            try {
                val local = withContext(Dispatchers.IO) { readLocalAudio(uri) }
                withContext(Dispatchers.IO) {
                    repo.addLocalTrackToPlaylist(local, playlistId, playlistName)
                }
                _uploadState.value = UploadState.Done(playlistName)
                refresh()
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(e.message ?: "Fehler beim Hinzufügen")
            }
        }
    }

    fun clearUploadState() { _uploadState.value = UploadState.Idle }

    /**
     * Hängt den aktuell wiedergegebenen Titel an eine Playlist (bestehend über [playlistId]
     * oder neu über [playlistName]).
     */
    fun addCurrentTrackToPlaylist(playlistId: String?, playlistName: String) {
        val track = current.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.addTrackToPlaylist(track, playlistId, playlistName) }
            refresh()
        }
    }

    /**
     * Legt eine neue (ggf. leere) Playlist an. Ohne [coverBytes] generiert der Server automatisch
     * eine Collage aus den ersten 4 Titelcovern, sobald Titel hinzugefügt werden.
     */
    fun createPlaylist(name: String, coverBytes: ByteArray?, coverMimeType: String?, playAsMix: Boolean = false) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.createPlaylist(name, coverBytes, coverMimeType, playAsMix) }
                refresh()
            } catch (_: Exception) {
                // Playlist-Erstellung fehlgeschlagen – nächster manueller Refresh zeigt konsistenten Stand
            }
        }
    }

    /**
     * Aktualisiert Name und/oder Cover einer bestehenden Playlist. [name] null lässt den Namen
     * unverändert, [coverBytes] null lässt das Cover unverändert, [playAsMix] null den Mix-Modus.
     */
    fun updatePlaylist(
        playlistId: String,
        name: String?,
        coverBytes: ByteArray?,
        coverMimeType: String?,
        playAsMix: Boolean? = null
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.updatePlaylist(playlistId, name, coverBytes, coverMimeType, playAsMix) }
                refresh()
            } catch (_: Exception) {
                // Aktualisierung fehlgeschlagen – nächster manueller Refresh zeigt konsistenten Stand
            }
        }
    }

    /** Startet eine Titelliste in normaler Reihenfolge (Shuffle wird deaktiviert). */
    fun playInOrder(list: List<Track>) {
        if (list.isEmpty()) return
        if (!castManager.isCasting.value && playerController.shuffle.value) {
            playerController.toggleShuffle()
        }
        play(list, 0)
    }

    /** Startet eine Titelliste in zufälliger Reihenfolge (Shuffle-Wiedergabe). */
    fun playShuffled(list: List<Track>) {
        if (list.isEmpty()) return
        play(list, list.indices.random())
        if (!castManager.isCasting.value && !playerController.shuffle.value) {
            playerController.toggleShuffle()
        }
    }

    /** Löscht eine Playlist (samt ihrer Titel) und aktualisiert die Liste. */
    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.deletePlaylist(playlistId) }
                _browse.value = _browse.value.copy(
                    playlists = _browse.value.playlists.filterNot { it.playlistId == playlistId },
                    playlistTracks = _browse.value.playlistTracks - playlistId
                )
                refresh()
            } catch (_: Exception) {
                // Löschen fehlgeschlagen – nächster manueller Refresh zeigt konsistenten Stand
            }
        }
    }

    /** Liest Bytes + Metadaten eines lokalen Audio-Uri über den ContentResolver. */
    private fun readLocalAudio(uri: Uri): LocalAudio {
        val resolver = appContext.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Datei konnte nicht gelesen werden")

        var fileName = "audio"
        resolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.let { fileName = it }
        }
        val mime = resolver.getType(uri) ?: "audio/mpeg"

        var title: String? = fileName.substringBeforeLast('.').ifBlank { fileName }
        var artist: String? = null
        var durationSec = 0
        runCatching {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(appContext, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            durationSec = ((mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000L).toInt()
            mmr.release()
        }
        return LocalAudio(bytes, fileName, mime, title, artist, durationSec)
    }

    /**
     * Baut den teilbaren Song-Link, der beim Empfänger den Lethe Media Player mit genau diesem
     * Lied öffnet. Entfernte (http/https) Titel werden direkt verlinkt; lokale Gerätedateien
     * (content://) werden dafür einmalig hochgeladen, damit der Empfänger sie überhaupt abrufen
     * kann. Gibt null zurück, wenn kein teilbarer Link erzeugt werden konnte.
     */
    suspend fun buildShareSongLink(track: Track): String? {
        if (track.audioUrl.startsWith("http")) {
            return buildSongLink(
                track.audioUrl, track.title, track.artist,
                track.coverUrl?.takeIf { it.startsWith("http") }
            )
        }
        val uri = runCatching { Uri.parse(track.audioUrl) }.getOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val local = readLocalAudio(uri)
                val uploaded = repo.uploadForShare(local) ?: return@runCatching null
                buildSongLink(
                    uploaded.mediaUrl, track.title, track.artist,
                    uploaded.coverUrl?.takeIf { it.startsWith("http") }
                )
            }.getOrNull()
        }
    }

    private fun buildSongLink(audioUrl: String, title: String, artist: String, coverUrl: String?): String =
        buildString {
            append("https://letheapp.de/song.php?u=")
            append(Uri.encode(audioUrl))
            append("&t=").append(Uri.encode(title))
            append("&a=").append(Uri.encode(artist))
            coverUrl?.takeIf { it.isNotBlank() }?.let {
                append("&c=").append(Uri.encode(it))
            }
        }

    /** Gruppiert Favoriten nach Künstler und sortiert nach Anzahl der Herzen absteigend. */
    private fun deriveArtists(favorites: List<Track>): List<ArtistItem> =
        favorites
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist }
            .map { (name, tracks) -> ArtistItem(name = name, favoriteCount = tracks.size, tracks = tracks) }
            .sortedWith(compareByDescending<ArtistItem> { it.favoriteCount }.thenBy { it.name.lowercase() })

    // ── Künstler-Bereich (Media Player) ────────────────────────────────────────

    private val artistPrefs = appContext.getSharedPreferences("artist_session", Context.MODE_PRIVATE)

    /** Öffentliche Künstler-Liste für den Künstler-Screen. */
    private val _publicArtists = MutableStateFlow<List<ArtistProfile>>(emptyList())
    val publicArtists: StateFlow<List<ArtistProfile>> = _publicArtists

    private val _publicArtistsLoading = MutableStateFlow(false)
    val publicArtistsLoading: StateFlow<Boolean> = _publicArtistsLoading

    /** Aktuell geöffnetes Künstler-Detail (Künstler-Screen mit allen Infos). */
    private val _selectedArtist = MutableStateFlow<ArtistProfile?>(null)
    val selectedArtist: StateFlow<ArtistProfile?> = _selectedArtist

    /** Angemeldeter Künstler (Künstler-Bereich); null = nicht angemeldet. */
    private val _artistSession = MutableStateFlow<ArtistDto?>(null)
    val artistSession: StateFlow<ArtistDto?> = _artistSession
    @Volatile private var artistToken: String? = null

    private val _artistAuthLoading = MutableStateFlow(false)
    val artistAuthLoading: StateFlow<Boolean> = _artistAuthLoading

    private val _artistAuthError = MutableStateFlow<String?>(null)
    val artistAuthError: StateFlow<String?> = _artistAuthError

    /** Meldung nach erfolgreicher Registrierung (Freigabe durch Admin ausstehend). */
    private val _artistRegisterInfo = MutableStateFlow<String?>(null)
    val artistRegisterInfo: StateFlow<String?> = _artistRegisterInfo

    /** Künstler-Verwaltung im Admin-Bereich. */
    private val _adminArtists = MutableStateFlow<List<ArtistDto>>(emptyList())
    val adminArtists: StateFlow<List<ArtistDto>> = _adminArtists

    private val _adminArtistsLoading = MutableStateFlow(false)
    val adminArtistsLoading: StateFlow<Boolean> = _adminArtistsLoading

    /** Live-Fortschritt der serverseitigen FriendsMix-BPM-Berechnung (Admin-Bereich). */
    private val _bpmStatus = MutableStateFlow<BpmStatusDto?>(null)
    val bpmStatus: StateFlow<BpmStatusDto?> = _bpmStatus

    private var bpmStatusJob: Job? = null

    /** Startet das 5-Sekunden-Polling des BPM-Status (solange der Admin-Bereich sichtbar ist). */
    fun startBpmStatusPolling() {
        if (bpmStatusJob?.isActive == true) return
        bpmStatusJob = viewModelScope.launch {
            while (true) {
                val status = withContext(Dispatchers.IO) { repo.adminBpmStatus() }
                if (status != null) _bpmStatus.value = status
                delay(5000)
            }
        }
    }

    /** Stoppt das BPM-Status-Polling, wenn der Admin-Bereich verlassen wird. */
    fun stopBpmStatusPolling() {
        bpmStatusJob?.cancel()
        bpmStatusJob = null
    }

    fun clearArtistAuthError() { _artistAuthError.value = null }
    fun clearArtistRegisterInfo() { _artistRegisterInfo.value = null }

    /** Lädt die öffentliche Künstler-Liste. */
    fun loadPublicArtists() {
        viewModelScope.launch {
            _publicArtistsLoading.value = true
            try {
                _publicArtists.value = withContext(Dispatchers.IO) { repo.getArtists() }
            } catch (_: Exception) {
                // Liste bleibt unverändert
            } finally {
                _publicArtistsLoading.value = false
            }
        }
    }

    /** Öffnet das Detail eines Künstlers (Künstler-Screen). */
    fun openArtist(artistId: String) {
        viewModelScope.launch {
            _selectedArtist.value = _publicArtists.value.firstOrNull { it.id == artistId }
            val full = withContext(Dispatchers.IO) { repo.getArtist(artistId) }
            if (full != null) _selectedArtist.value = full
        }
    }

    fun clearSelectedArtist() { _selectedArtist.value = null }

    /** Spielt alle Songs eines Künstlers ab. */
    fun playArtistProfile(profile: ArtistProfile) {
        if (profile.songs.isNotEmpty()) play(profile.songs, 0)
    }

    /** Stellt eine ggf. gespeicherte Künstler-Sitzung beim App-Start wieder her. */
    private fun restoreArtistSession() {
        val token = artistPrefs.getString("token", null) ?: return
        artistToken = token
        viewModelScope.launch {
            val me = withContext(Dispatchers.IO) { repo.artistMe(token) }
            if (me != null) {
                _artistSession.value = me
            } else {
                // Token abgelaufen/ungültig -> verwerfen
                artistToken = null
                artistPrefs.edit().remove("token").apply()
            }
        }
    }

    /** Registriert einen neuen Künstler-Account. */
    fun artistRegister(email: String, password: String, artistName: String) {
        viewModelScope.launch {
            _artistAuthLoading.value = true
            _artistAuthError.value = null
            val error = withContext(Dispatchers.IO) { repo.artistRegister(email, password, artistName) }
            _artistAuthLoading.value = false
            if (error == null) {
                _artistRegisterInfo.value = "Account erstellt. Ein Admin muss ihn noch freigeben."
            } else {
                _artistAuthError.value = error
            }
        }
    }

    /** Meldet einen Künstler an und speichert die Sitzung. */
    fun artistLogin(email: String, password: String) {
        viewModelScope.launch {
            _artistAuthLoading.value = true
            _artistAuthError.value = null
            try {
                val resp = withContext(Dispatchers.IO) { repo.artistLogin(email, password) }
                artistToken = resp.accessToken
                artistPrefs.edit().putString("token", resp.accessToken).apply()
                _artistSession.value = resp.artist
            } catch (e: Exception) {
                _artistAuthError.value = e.message ?: "Anmeldung fehlgeschlagen."
            } finally {
                _artistAuthLoading.value = false
            }
        }
    }

    /** Meldet den Künstler ab. */
    fun artistLogout() {
        artistToken = null
        artistPrefs.edit().remove("token").apply()
        _artistSession.value = null
    }

    /** Speichert Name/Bio/zugeordnete Songs des angemeldeten Künstlers. */
    fun saveArtistProfile(artistName: String?, artistBio: String?, songIds: List<String>?) {
        val token = artistToken ?: return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                repo.artistUpdateMe(token, artistName, artistBio, songIds)
            }
            if (updated != null) _artistSession.value = updated
        }
    }

    /** Lädt ein neues Künstlerbild aus einer lokalen Bild-URI hoch. */
    fun uploadArtistPicture(uri: Uri) {
        val token = artistToken ?: return
        viewModelScope.launch {
            try {
                val resolver = appContext.contentResolver
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val mime = resolver.getType(uri) ?: "image/jpeg"
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val updated = withContext(Dispatchers.IO) {
                    repo.artistUploadPicture(token, bytes, mime, "picture.$ext")
                }
                if (updated != null) _artistSession.value = updated
            } catch (_: Exception) {
                // Upload fehlgeschlagen – Nutzer kann es erneut versuchen
            }
        }
    }

    // ── Admin-Verwaltung der Künstler ──

    fun loadAdminArtists() {
        viewModelScope.launch {
            _adminArtistsLoading.value = true
            try {
                _adminArtists.value = withContext(Dispatchers.IO) { repo.adminGetArtists() }
            } catch (_: Exception) {
                // Liste bleibt unverändert
            } finally {
                _adminArtistsLoading.value = false
            }
        }
    }

    fun adminApproveArtist(artistId: String, approved: Boolean) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repo.adminApproveArtist(artistId, approved) }
            if (updated != null) replaceAdminArtist(updated)
        }
    }

    fun adminBlockArtist(artistId: String, blocked: Boolean) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) { repo.adminBlockArtist(artistId, blocked) }
            if (updated != null) replaceAdminArtist(updated)
        }
    }

    fun adminDeleteArtist(artistId: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.adminDeleteArtist(artistId) }
            if (ok) _adminArtists.value = _adminArtists.value.filterNot { it.id == artistId }
        }
    }

    private fun replaceAdminArtist(updated: ArtistDto) {
        _adminArtists.value = _adminArtists.value.map { if (it.id == updated.id) updated else it }
    }

    // ── Admin-Verwaltung der Lethe-Playlists ──

    /** true während eine Lethe-Playlist angelegt wird (Admin-Erstellen-Screen). */
    private val _lethePlaylistSaving = MutableStateFlow(false)
    val lethePlaylistSaving: StateFlow<Boolean> = _lethePlaylistSaving

    /**
     * Legt eine globale Lethe-Playlist mit [name] und den ausgewählten [trackIds] an.
     * [onDone] wird bei Erfolg aufgerufen (z.B. um den Erstellen-Screen zu schließen).
     */
    fun createLethePlaylist(name: String, trackIds: List<String>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _lethePlaylistSaving.value = true
            try {
                withContext(Dispatchers.IO) { repo.createLethePlaylist(name, trackIds) }
                refresh()
                onDone()
            } catch (_: Exception) {
                // Anlegen fehlgeschlagen – Nutzer kann es erneut versuchen
            } finally {
                _lethePlaylistSaving.value = false
            }
        }
    }

    /** Löscht eine globale Lethe-Playlist und aktualisiert die Liste. */
    fun deleteLethePlaylist(playlistId: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.deleteLethePlaylist(playlistId) }
            if (ok) {
                _browse.value = _browse.value.copy(
                    lethePlaylists = _browse.value.lethePlaylists.filterNot { it.playlistId == playlistId }
                )
            }
        }
    }
}
