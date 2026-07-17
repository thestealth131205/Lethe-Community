package com.securechat.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.securechat.app.data.network.ApiService
import com.securechat.app.data.network.AudiusApiService
import com.securechat.app.data.network.AudiusTrack
import com.securechat.app.data.network.LetheMusicTrack
import com.securechat.app.data.network.LetheMusicTrackUiState
import com.securechat.app.data.network.MusicTrackUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

// ─────────────────────────────────────────────────────────────────────────────
// MusicSearchViewModel
//
// Verwaltet die Musiksuche via Audius API sowie die Audio-Vorschau-Wiedergabe.
//
// Audius-Ablauf:
//   1. discoverHost()  → GET https://api.audius.co/ → erster verfügbarer Node
//   2. loadTrending()  → GET {host}/v1/tracks/trending
//   3. searchTracks()  → GET {host}/v1/tracks/search?query=...
//   4. streamUrl(id)   → {host}/v1/tracks/{id}/stream?app_name=LetheApp
//      (HTTP-Redirect auf CDN-MP3 – direkt von ExoPlayer + FFmpeg konsumierbar)
//
// Zuständigkeiten:
//   - Host-Discovery (lazy, gecacht in resolvedHostNode)
//   - Suchbegriff-State + Debounce-Suche (500 ms)
//   - Trending-Tracks für die Startansicht
//   - Suchergebnisse als UiState-Liste (isPlaying / isSelected)
//   - ExoPlayer-Instanz für direktes Audius-Stream-Vorschau
//   - selectedTrack + selectedTrackStreamUrl für den FFmpeg-Export
//   - Lokal gewählte MP3-Datei (Gerätedatei via content:// URI)
// ─────────────────────────────────────────────────────────────────────────────

private const val APP_NAME             = "LetheApp"
private const val AUDIUS_DISCOVERY_URL = "https://api.audius.co/"
private const val AUDIUS_FALLBACK_HOST = "https://discoveryprovider.audius.co"

@HiltViewModel
class MusicSearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val audiusApiService: AudiusApiService,
    private val apiService: ApiService,
    @Named("audius") private val audiusHttpClient: OkHttpClient
) : ViewModel() {

    // ─── State-Flows ─────────────────────────────────────────────────────────

    /** Aktueller Suchbegriff (bidirektional via [updateQuery]). */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Liste der Suchergebnisse / Trending-Tracks inkl. Play/Select-State. */
    private val _tracks = MutableStateFlow<List<MusicTrackUiState>>(emptyList())
    val tracks: StateFlow<List<MusicTrackUiState>> = _tracks.asStateFlow()

    /** True, solange ein Netzwerkrequest läuft. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Fehlermeldung (null = kein Fehler). */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Aktuell für den Spark-Export ausgewählter Track (null = keiner gewählt).
     * Wird für die MusicTickerBanner-Anzeige im Editor und für SparkMusicSyncStrip
     * (track.duration) genutzt.
     */
    private val _selectedTrack = MutableStateFlow<AudiusTrack?>(null)
    val selectedTrack: StateFlow<AudiusTrack?> = _selectedTrack.asStateFlow()

    /**
     * Fertig berechnete Stream-URL des ausgewählten Tracks für FFmpeg.
     * Null, wenn kein Audius-Track gewählt ist.
     * Format: "{resolvedHostNode}/v1/tracks/{trackId}/stream?app_name=LetheApp"
     * FFmpeg und ExoPlayer folgen dem HTTP-Redirect auf die CDN-MP3 automatisch.
     */
    private val _selectedTrackStreamUrl = MutableStateFlow<String?>(null)
    val selectedTrackStreamUrl: StateFlow<String?> = _selectedTrackStreamUrl.asStateFlow()

    /** ID des aktuell im Vorschau-Player abspielenden Tracks (null = keiner spielt). */
    private val _playingTrackId = MutableStateFlow<String?>(null)
    val playingTrackId: StateFlow<String?> = _playingTrackId.asStateFlow()

    /** Lokal gewählte MP3-Datei (content:// URI als String, null = keine). */
    private val _localMusicUri = MutableStateFlow<String?>(null)
    val localMusicUri: StateFlow<String?> = _localMusicUri.asStateFlow()

    /** Anzeigename der lokal gewählten MP3. */
    private val _localMusicName = MutableStateFlow<String?>(null)
    val localMusicName: StateFlow<String?> = _localMusicName.asStateFlow()

    // ─── Lethe Bibliothek State ───────────────────────────────────────────────

    /** Tracks aus der Lethe-eigenen Musikbibliothek. */
    private val _letheTracks = MutableStateFlow<List<LetheMusicTrackUiState>>(emptyList())
    val letheTracks: StateFlow<List<LetheMusicTrackUiState>> = _letheTracks.asStateFlow()

    /** Suchbegriff für die Lethe Bibliothek. */
    private val _letheSearchQuery = MutableStateFlow("")
    val letheSearchQuery: StateFlow<String> = _letheSearchQuery.asStateFlow()

    /** Ladezustand der Lethe Bibliothek. */
    private val _isLetheLoading = MutableStateFlow(false)
    val isLetheLoading: StateFlow<Boolean> = _isLetheLoading.asStateFlow()

    /** Fehlermeldung der Lethe Bibliothek (null = kein Fehler). */
    private val _letheError = MutableStateFlow<String?>(null)
    val letheError: StateFlow<String?> = _letheError.asStateFlow()

    /** Aktuell ausgewählter Lethe-Library-Track (null = keiner). */
    private val _selectedLetheTrack = MutableStateFlow<LetheMusicTrack?>(null)
    val selectedLetheTrack: StateFlow<LetheMusicTrack?> = _selectedLetheTrack.asStateFlow()

    /** ID3-Titel der lokal gewählten MP3 (null = nicht auslesbar). */
    private val _localMusicTitle = MutableStateFlow<String?>(null)
    val localMusicTitle: StateFlow<String?> = _localMusicTitle.asStateFlow()

    /** ID3-Artist der lokal gewählten MP3 (null = nicht auslesbar). */
    private val _localMusicArtist = MutableStateFlow<String?>(null)
    val localMusicArtist: StateFlow<String?> = _localMusicArtist.asStateFlow()

    // ─── Interner State ───────────────────────────────────────────────────────

    /**
     * Gecachter Audius-Host-Node nach erfolgter Discovery.
     * Vorbelegt mit dem Fallback, damit das ViewModel auch ohne Netz nutzbar ist.
     */
    @Volatile
    private var resolvedHostNode: String = AUDIUS_FALLBACK_HOST

    // ─── ExoPlayer für Vorschau-Wiedergabe ───────────────────────────────────

    /**
     * ExoPlayer-Instanz für das direkte Streaming der Audius-Stream-URLs.
     * Lazy initialisiert; wird zwischen Recompositionen im ViewModel gehalten.
     * MUSS in [onCleared] freigegeben werden.
     */
    val previewPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _playingTrackId.value = null
                        updateTrackPlayingState(null)
                    }
                }
            })
        }
    }

    // ─── Initialisierung ──────────────────────────────────────────────────────

    init {
        // Host-Discovery + Trending-Tracks beim ersten Öffnen laden
        viewModelScope.launch {
            discoverHost()
            loadTrending()
        }
        // Lethe Bibliothek initial laden
        viewModelScope.launch { loadLetheLibrary() }

        // Suchbegriff mit 500 ms Debounce beobachten → Suche auslösen
        @OptIn(FlowPreview::class)
        _searchQuery
            .debounce(500L)
            .distinctUntilChanged()
            .filter { it.isNotBlank() }
            .onEach { query -> searchTracks(query) }
            .launchIn(viewModelScope)
    }

    // ─── Öffentliche API ─────────────────────────────────────────────────────

    /**
     * Aktualisiert den Suchbegriff.
     * Bei leerem String: Trending-Tracks laden (Startansicht wiederherstellen).
     */
    fun updateQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            viewModelScope.launch { loadTrending() }
        }
    }

    /**
     * Wählt einen Audius-Track für den Spark-Export aus.
     * Zweimaliges Auswählen des gleichen Tracks hebt die Auswahl auf (Toggle).
     * Löscht eine vorhandene lokale Datei-Auswahl.
     */
    fun selectTrack(track: AudiusTrack) {
        val currentSelected = _selectedTrack.value
        if (currentSelected?.id == track.id) {
            // Toggle: Auswahl aufheben
            _selectedTrack.value = null
            _selectedTrackStreamUrl.value = null
        } else {
            _selectedTrack.value = track
            _selectedTrackStreamUrl.value = buildStreamUrl(track.id)
            // Lokale Datei-Auswahl aufheben
            _localMusicUri.value = null
            _localMusicName.value = null
        }
        updateTrackSelectedState(_selectedTrack.value?.id)
    }

    /**
     * Wählt eine lokale MP3-Datei vom Gerät.
     * Hebt eine vorhandene Audius-Track-Auswahl auf.
     *
     * @param uriString  content:// URI der MP3 als String.
     * @param name       Anzeigename der Datei (aus OpenableColumns).
     */
    fun selectLocalMusic(uriString: String, name: String, id3Title: String? = null, id3Artist: String? = null) {
        _localMusicUri.value = uriString
        _localMusicName.value = name
        _localMusicTitle.value = id3Title
        _localMusicArtist.value = id3Artist
        // Audius-Auswahl aufheben
        _selectedTrack.value = null
        _selectedTrackStreamUrl.value = null
        updateTrackSelectedState(null)
        stopPreview()
    }

    /** Hebt die lokale Musikauswahl auf (ohne Audius-State zu berühren). */
    fun clearLocalMusic() {
        _localMusicUri.value = null
        _localMusicName.value = null
        _localMusicTitle.value = null
        _localMusicArtist.value = null
    }

    /**
     * Startet oder stoppt die Vorschau-Wiedergabe eines Tracks.
     * Zweimaliges Aufrufen des gleichen Tracks stoppt ihn.
     * ExoPlayer streamt direkt von der Audius-Stream-URL (HTTP-Redirect auf CDN-MP3).
     */
    fun togglePreviewPlayback(track: AudiusTrack) {
        if (_playingTrackId.value == track.id) {
            // Selben Track stoppen
            previewPlayer.pause()
            _playingTrackId.value = null
            updateTrackPlayingState(null)
        } else {
            // Neuen Track starten (Stream-URL direkt an ExoPlayer übergeben)
            val streamUrl = buildStreamUrl(track.id)
            previewPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
            previewPlayer.prepare()
            previewPlayer.play()
            _playingTrackId.value = track.id
            updateTrackPlayingState(track.id)
        }
    }

    /** Stoppt die Vorschau-Wiedergabe (z.B. beim Schließen des Sheets). */
    fun stopPreview() {
        previewPlayer.pause()
        _playingTrackId.value = null
        updateTrackPlayingState(null)
    }

    /** Hebt Audius-Track- und lokale Datei-Auswahl auf. */
    fun clearSelection() {
        _selectedTrack.value = null
        _selectedTrackStreamUrl.value = null
        _selectedLetheTrack.value = null
        _localMusicUri.value = null
        _localMusicName.value = null
        _localMusicTitle.value = null
        _localMusicArtist.value = null
        updateTrackSelectedState(null)
    }

    // ─── Lethe Bibliothek: öffentliche API ───────────────────────────────────

    /** Aktualisiert den Suchbegriff der Lethe Bibliothek und löst eine neue Suche aus. */
    fun updateLetheQuery(query: String) {
        _letheSearchQuery.value = query
        viewModelScope.launch { loadLetheLibrary(query.takeIf { it.isNotBlank() }) }
    }

    /** Wählt einen Lethe-Library-Track aus (Toggle). Hebt Audius- und lokale Auswahl auf. */
    fun selectLetheTrack(track: LetheMusicTrack) {
        val current = _selectedLetheTrack.value
        if (current?.id == track.id) {
            _selectedLetheTrack.value = null
        } else {
            _selectedLetheTrack.value = track
            // Audius- und lokale Auswahl aufheben
            _selectedTrack.value = null
            _selectedTrackStreamUrl.value = null
            _localMusicUri.value = null
            _localMusicName.value = null
            updateTrackSelectedState(null)
        }
        updateLetheSelectedState(_selectedLetheTrack.value?.id)
    }

    /** Hebt die Lethe-Library-Auswahl auf. */
    fun clearLetheSelection() {
        _selectedLetheTrack.value = null
        updateLetheSelectedState(null)
    }

    /** Startet/Stoppt Vorschau-Wiedergabe eines Lethe-Library-Tracks. */
    fun toggleLethePreview(track: LetheMusicTrack) {
        if (_playingTrackId.value == track.id) {
            previewPlayer.pause()
            _playingTrackId.value = null
            updateTrackPlayingState(null)
            updateLethePlayingState(null)
        } else {
            previewPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(track.audioUrl))
            previewPlayer.prepare()
            previewPlayer.play()
            _playingTrackId.value = track.id
            updateTrackPlayingState(null)
            updateLethePlayingState(track.id)
        }
    }

    // ─── Privat: Lethe Bibliothek laden ──────────────────────────────────────

    private suspend fun loadLetheLibrary(query: String? = null) {
        _isLetheLoading.value = true
        _letheError.value = null
        try {
            val response = apiService.getLetheLibrary(query)
            if (response.isSuccessful) {
                val hits = response.body() ?: emptyList()
                _letheTracks.value = hits.map { t ->
                    LetheMusicTrackUiState(
                        track = t,
                        isPlaying = _playingTrackId.value == t.id,
                        isSelected = _selectedLetheTrack.value?.id == t.id
                    )
                }
            } else {
                _letheError.value = "Lethe Bibliothek konnte nicht geladen werden (${response.code()})."
            }
        } catch (e: Exception) {
            _letheError.value = "Keine Verbindung zur Lethe Bibliothek."
            Timber.tag("LETHE_MUSIC").e("loadLetheLibrary Fehler: ${e.message}")
        } finally {
            _isLetheLoading.value = false
        }
    }

    private fun updateLetheSelectedState(selectedId: String?) {
        _letheTracks.value = _letheTracks.value.map { it.copy(isSelected = it.track.id == selectedId) }
    }

    private fun updateLethePlayingState(playingId: String?) {
        _letheTracks.value = _letheTracks.value.map { it.copy(isPlaying = it.track.id == playingId) }
    }

    // ─── Privat: Discovery + API-Calls ───────────────────────────────────────

    /**
     * Ruft https://api.audius.co/ via OkHttp auf und extrahiert den ersten
     * verfügbaren Host-Node aus dem "data"-Array.
     * Fehler werden still ignoriert – [resolvedHostNode] bleibt auf dem Fallback.
     */
    private suspend fun discoverHost() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(AUDIUS_DISCOVERY_URL)
                .header("Accept", "application/json")
                .build()
            val body = audiusHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                resp.body?.string()
            } ?: return@withContext

            val nodes = JSONObject(body).optJSONArray("data") ?: return@withContext
            if (nodes.length() > 0) {
                val host = nodes.getString(0).trimEnd('/')
                if (host.startsWith("http")) {
                    resolvedHostNode = host
                    Timber.tag("LETHE_MUSIC").d("Audius Host resolved: $resolvedHostNode")
                }
            }
        } catch (e: Exception) {
            Timber.tag("LETHE_MUSIC").w(
                "Audius Discovery fehlgeschlagen – nutze Fallback '$AUDIUS_FALLBACK_HOST': ${e.message}"
            )
        }
    }

    /**
     * Sucht Tracks anhand eines Suchbegriffs via GET {host}/v1/tracks/search.
     * Wird über den Debounce-Flow automatisch ausgelöst.
     */
    private fun searchTracks(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val url = "$resolvedHostNode/v1/tracks/search"
                val response = audiusApiService.searchTracks(
                    url = url,
                    query = query,
                    appName = APP_NAME
                )
                if (response.isSuccessful) {
                    val hits = response.body()?.data ?: emptyList()
                    _tracks.value = hits.map { track ->
                        MusicTrackUiState(
                            track = track,
                            isPlaying = _playingTrackId.value == track.id,
                            isSelected = _selectedTrack.value?.id == track.id
                        )
                    }
                    if (hits.isEmpty()) {
                        _error.value = "Keine Tracks für \"$query\" gefunden."
                    }
                } else {
                    _error.value = "Suche fehlgeschlagen (${response.code()})"
                    Timber.tag("LETHE_MUSIC").e(
                        "Audius Search ${response.code()}: ${response.errorBody()?.string()?.take(200)}"
                    )
                }
            } catch (e: Exception) {
                _error.value = "Keine Verbindung. Bitte Internetverbindung prüfen."
                Timber.tag("LETHE_MUSIC").e("searchTracks Fehler: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Lädt Trending-Tracks ohne Suchbegriff via GET {host}/v1/tracks/trending.
     * Wird beim Initialisieren und bei leerem Suchfeld aufgerufen.
     */
    private fun loadTrending() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val url = "$resolvedHostNode/v1/tracks/trending"
                val response = audiusApiService.getTrendingTracks(
                    url = url,
                    appName = APP_NAME
                )
                if (response.isSuccessful) {
                    val hits = response.body()?.data ?: emptyList()
                    _tracks.value = hits.map { track ->
                        MusicTrackUiState(
                            track = track,
                            isPlaying = _playingTrackId.value == track.id,
                            isSelected = _selectedTrack.value?.id == track.id
                        )
                    }
                } else {
                    Timber.tag("LETHE_MUSIC").e(
                        "Audius Trending ${response.code()}: ${response.errorBody()?.string()?.take(200)}"
                    )
                    _error.value = "Trending-Tracks konnten nicht geladen werden (${response.code()})."
                }
            } catch (e: Exception) {
                _error.value = "Keine Verbindung."
                Timber.tag("LETHE_MUSIC").e("loadTrending Fehler: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Baut die Audius-Stream-URL für einen Track.
     * FFmpeg und ExoPlayer folgen dem HTTP-303-Redirect auf die CDN-MP3 automatisch.
     */
    private fun buildStreamUrl(trackId: String): String =
        "$resolvedHostNode/v1/tracks/$trackId/stream?app_name=$APP_NAME"

    /** Öffentlich: Stream-URL für einen Audius-Track (z.B. für den Video-Editor). */
    fun getStreamUrlForEditor(trackId: String): String = buildStreamUrl(trackId)

    /** Aktualisiert den isPlaying-State aller Tracks in der Liste. */
    private fun updateTrackPlayingState(playingId: String?) {
        _tracks.value = _tracks.value.map { uiState ->
            uiState.copy(isPlaying = uiState.track.id == playingId)
        }
    }

    /** Aktualisiert den isSelected-State aller Tracks in der Liste. */
    private fun updateTrackSelectedState(selectedId: String?) {
        _tracks.value = _tracks.value.map { uiState ->
            uiState.copy(isSelected = uiState.track.id == selectedId)
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        previewPlayer.release()
        Timber.tag("LETHE_MUSIC").d("MusicSearchViewModel onCleared – ExoPlayer freigegeben.")
    }
}
