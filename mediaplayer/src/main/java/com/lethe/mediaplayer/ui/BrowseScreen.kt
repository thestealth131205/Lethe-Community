package com.lethe.mediaplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.lethe.mediaplayer.data.PlaylistDto
import com.lethe.mediaplayer.data.Track
import com.lethe.mediaplayer.player.AutoDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Von der Audius-API unterstützte Genres für die Kategorie-Filterung im Audius-Tab. */
private val AudiusGenres: List<String> = listOf(
    "Electronic", "Hip-Hop/Rap", "Pop", "Rock", "House", "Techno",
    "Dubstep", "Trap", "Ambient", "Deep House", "Drum & Bass",
    "Jazz", "R&B/Soul", "Classical", "Reggae", "Metal", "World"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    vm: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onBack: () -> Unit = {},
    openPlaylistId: String? = null,
    onPlaylistOpened: () -> Unit = {}
) {
    val state by vm.browse.collectAsState()
    val tab by vm.tab.collectAsState()
    val current by vm.current.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val audiusTracks by vm.audiusTracks.collectAsState()
    val audiusQuery by vm.audiusQuery.collectAsState()
    val audiusLoading by vm.audiusLoading.collectAsState()
    val audiusError by vm.audiusError.collectAsState()
    val audiusGenre by vm.audiusGenre.collectAsState()
    val localTracks by vm.localTracks.collectAsState()
    val localLoading by vm.localLoading.collectAsState()
    val localScanned by vm.localScanned.collectAsState()

    var selectedPlaylist by remember { mutableStateOf<PlaylistDto?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlistMenu by remember { mutableStateOf<PlaylistDto?>(null) }
    var playlistToEdit by remember { mutableStateOf<PlaylistDto?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistDto?>(null) }
    var trackMenu by remember { mutableStateOf<Track?>(null) }
    var trackInfo by remember { mutableStateOf<Track?>(null) }
    val autoDownloadEnabled by vm.autoDownloadManager.enabledKeys.collectAsState()
    val autoDownloadBusy by vm.autoDownloadManager.downloadingKeys.collectAsState()

    // Von außen (z.B. Playlist-Kachel auf dem Home-Bildschirm) angeforderte Playlist öffnen:
    // in den Playlists-Tab wechseln und die Detailansicht dieser Playlist anzeigen.
    LaunchedEffect(openPlaylistId, state.playlists) {
        val id = openPlaylistId ?: return@LaunchedEffect
        val pl = state.playlists.firstOrNull { it.playlistId == id } ?: return@LaunchedEffect
        vm.setTab(BrowseTab.PLAYLISTS)
        selectedPlaylist = pl
        vm.loadPlaylist(id)
        onPlaylistOpened()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bibliothek") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (tab == BrowseTab.PLAYLISTS && selectedPlaylist == null) {
                        IconButton(onClick = { showCreatePlaylist = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Neue Playlist")
                        }
                    }
                    CastButton(modifier = Modifier.size(48.dp))
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == BrowseTab.LIBRARY, onClick = { vm.setTab(BrowseTab.LIBRARY) }, text = { Text("Bibliothek") })
                Tab(selected = tab == BrowseTab.LOCAL, onClick = { vm.setTab(BrowseTab.LOCAL) }, text = { Text("Lokal") })
                Tab(selected = tab == BrowseTab.AUDIUS, onClick = { vm.setTab(BrowseTab.AUDIUS) }, text = { Text("Audius") })
                Tab(selected = tab == BrowseTab.FAVORITES, onClick = { vm.setTab(BrowseTab.FAVORITES) }, text = { Text("Favoriten") })
                Tab(selected = tab == BrowseTab.PLAYLISTS, onClick = { vm.setTab(BrowseTab.PLAYLISTS); selectedPlaylist = null }, text = { Text("Playlists") })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    tab != BrowseTab.AUDIUS && tab != BrowseTab.LOCAL && state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    tab != BrowseTab.AUDIUS && tab != BrowseTab.LOCAL && state.error != null -> Text(
                        state.error!!,
                        Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> when (tab) {
                        BrowseTab.LIBRARY -> {
                            val genres = remember(state.library) {
                                state.library.mapNotNull { it.genre }.distinct().sorted()
                            }
                            val filtered = remember(state.library, searchQuery, selectedGenre) {
                                state.library.filter { t ->
                                    (selectedGenre == null || t.genre == selectedGenre) &&
                                        (searchQuery.isBlank() ||
                                            t.title.contains(searchQuery, ignoreCase = true) ||
                                            t.artist.contains(searchQuery, ignoreCase = true))
                                }
                            }
                            Column(Modifier.fillMaxSize()) {
                                LibrarySearchAndCategories(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    genres = genres,
                                    selectedGenre = selectedGenre,
                                    onSelectGenre = { selectedGenre = if (selectedGenre == it) null else it }
                                )
                                TrackList(filtered, current?.id, onLongPress = { trackMenu = it }) { list, i ->
                                    vm.playFrom(list, i); onOpenNowPlaying()
                                }
                            }
                        }
                        BrowseTab.LOCAL -> LocalMediaTab(
                            tracks = localTracks,
                            loading = localLoading,
                            scanned = localScanned,
                            currentId = current?.id,
                            onScan = { vm.scanLocalMedia() },
                            onLongPress = { trackMenu = it },
                            onPlay = { list, i -> vm.playFrom(list, i); onOpenNowPlaying() }
                        )
                        BrowseTab.AUDIUS -> {
                            Column(Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = audiusQuery,
                                    onValueChange = { vm.updateAudiusQuery(it) },
                                    singleLine = true,
                                    placeholder = { Text("Audius durchsuchen") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (audiusQuery.isNotEmpty()) {
                                            IconButton(onClick = { vm.updateAudiusQuery("") }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Löschen")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                                if (audiusQuery.isBlank()) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    ) {
                                        item {
                                            FilterChip(
                                                selected = audiusGenre == null,
                                                onClick = { vm.setAudiusGenre(null) },
                                                label = { Text("Alle") }
                                            )
                                        }
                                        items(AudiusGenres) { genre ->
                                            FilterChip(
                                                selected = audiusGenre == genre,
                                                onClick = { vm.setAudiusGenre(genre) },
                                                label = { Text(genre) }
                                            )
                                        }
                                    }
                                }
                                when {
                                    audiusLoading -> Box(Modifier.fillMaxSize()) {
                                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                                    }
                                    audiusError != null -> Box(Modifier.fillMaxSize()) {
                                        Text(
                                            audiusError!!,
                                            Modifier.align(Alignment.Center).padding(24.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    else -> TrackList(audiusTracks, current?.id, onLongPress = { trackMenu = it }) { list, i ->
                                        vm.playFrom(list, i); onOpenNowPlaying()
                                    }
                                }
                            }
                        }
                        BrowseTab.FAVORITES -> Column(Modifier.fillMaxSize()) {
                            AutoDownloadRow(
                                enabled = autoDownloadEnabled.contains(AutoDownloadManager.FAVORITES_KEY),
                                busy = autoDownloadBusy.contains(AutoDownloadManager.FAVORITES_KEY),
                                onToggle = { checked ->
                                    vm.toggleAutoDownload(AutoDownloadManager.FAVORITES_KEY, state.favorites, checked)
                                }
                            )
                            TrackList(state.favorites, current?.id, onLongPress = { trackMenu = it }) { list, i ->
                                vm.playFrom(list, i); onOpenNowPlaying()
                            }
                        }
                        BrowseTab.PLAYLISTS -> {
                            val pl = selectedPlaylist
                            if (pl == null) {
                                PlaylistList(
                                    playlists = state.playlists,
                                    onOpen = { chosen ->
                                        selectedPlaylist = chosen
                                        vm.loadPlaylist(chosen.playlistId)
                                    },
                                    onLongPress = { chosen -> playlistMenu = chosen }
                                )
                            } else {
                                val tracks = state.playlistTracks[pl.playlistId]
                                Column(Modifier.fillMaxSize()) {
                                    PlaylistDetailHeader(
                                        playlist = pl,
                                        trackCount = tracks?.size ?: pl.trackCount,
                                        onPlay = {
                                            if (!tracks.isNullOrEmpty()) {
                                                if (pl.playAsMix) vm.playPlaylistAsMix(tracks) else vm.playInOrder(tracks)
                                                onOpenNowPlaying()
                                            }
                                        },
                                        onShuffle = {
                                            if (!tracks.isNullOrEmpty()) { vm.playShuffled(tracks); onOpenNowPlaying() }
                                        }
                                    )
                                    AutoDownloadRow(
                                        enabled = autoDownloadEnabled.contains(pl.playlistId),
                                        busy = autoDownloadBusy.contains(pl.playlistId),
                                        onToggle = { checked ->
                                            vm.toggleAutoDownload(pl.playlistId, tracks.orEmpty(), checked)
                                        }
                                    )
                                    if (tracks == null) {
                                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                                    } else {
                                        TrackList(tracks, current?.id, onLongPress = { trackMenu = it }) { list, i ->
                                            vm.playFrom(list, i); onOpenNowPlaying()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mini-Player-Leiste
            if (current != null) {
                HorizontalDivider()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenNowPlaying() }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverThumb(current?.coverUrl, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(current?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(current?.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.togglePlayPause() }) {
                            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    playlistMenu?.let { pl ->
        PlaylistContextMenu(
            playlist = pl,
            onEdit = { playlistToEdit = pl; playlistMenu = null },
            onDelete = { playlistToDelete = pl; playlistMenu = null },
            onDismiss = { playlistMenu = null }
        )
    }

    playlistToEdit?.let { pl ->
        EditPlaylistDialog(
            playlist = pl,
            onDismiss = { playlistToEdit = null },
            onSave = { name, coverBytes, coverMime, playAsMix ->
                vm.updatePlaylist(pl.playlistId, name, coverBytes, coverMime, playAsMix)
                if (selectedPlaylist?.playlistId == pl.playlistId) {
                    selectedPlaylist = pl.copy(playlistName = name ?: pl.playlistName, playAsMix = playAsMix)
                }
                playlistToEdit = null
            }
        )
    }

    playlistToDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Playlist löschen") },
            text = { Text("Möchtest du die Playlist \u201E${pl.playlistName}\u201C wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(pl.playlistId)
                    if (selectedPlaylist?.playlistId == pl.playlistId) selectedPlaylist = null
                    playlistToDelete = null
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Abbrechen") }
            }
        )
    }

    trackMenu?.let { t ->
        TrackContextMenu(
            track = t,
            onInfo = { trackInfo = t; trackMenu = null },
            onDismiss = { trackMenu = null }
        )
    }

    trackInfo?.let { t ->
        TrackInfoDialog(track = t, onDismiss = { trackInfo = null })
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onCreate = { name, coverBytes, coverMime, playAsMix ->
                vm.createPlaylist(name, coverBytes, coverMime, playAsMix)
                showCreatePlaylist = false
            }
        )
    }
}

/** Kontextmenü beim langen Drücken auf ein Lied: Infos anzeigen (i-Symbol). */
@Composable
private fun TrackContextMenu(track: Track, onInfo: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column {
                Text(
                    track.artist.ifBlank { "Unbekannt" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onInfo)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text("Informationen")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

/** Zeigt Speicherort (voller Pfad + Ordner), Spieldauer, Interpret, Quelle usw. eines Liedes. */
@Composable
private fun TrackInfoDialog(track: Track, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val storage by produceState<TrackStorageInfo?>(initialValue = null, track.id) {
        value = withContext(Dispatchers.IO) { resolveTrackStorage(context, track) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Informationen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TrackInfoRow("Titel", track.title)
                TrackInfoRow("Interpret", track.artist.ifBlank { "Unbekannt" })
                if (!track.genre.isNullOrBlank()) TrackInfoRow("Genre", track.genre)
                TrackInfoRow("Spieldauer", formatTrackDuration(track.durationSec))
                TrackInfoRow("Quelle", trackSourceLabel(track.source))
                if (track.source == "lethe") {
                    TrackInfoRow("Streams", track.playCount.toString())
                    val bpm = track.bpm
                    TrackInfoRow(
                        "BPM",
                        when {
                            bpm == null -> "Wird ermittelt …"
                            bpm <= 0f -> "Nicht ermittelbar"
                            else -> Math.round(bpm).toString()
                        }
                    )
                }
                val s = storage
                when {
                    s == null -> TrackInfoRow("Speicherort", "Wird ermittelt …")
                    s.isOnline -> {
                        TrackInfoRow("Speicherort", "Online-Stream")
                        TrackInfoRow("Adresse", s.url)
                    }
                    else -> {
                        s.fileName?.let { TrackInfoRow("Dateiname", it) }
                        s.folder?.let { TrackInfoRow("Ordner", it) }
                        s.fullPath?.let { TrackInfoRow("Vollständiger Pfad", it) }
                        if (s.fullPath == null && s.fileName == null && s.folder == null) {
                            TrackInfoRow("Speicherort", "Nicht ermittelbar")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

@Composable
private fun TrackInfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Aufgelöster Speicherort eines Liedes (lokal mit Pfad/Ordner oder Online-Stream). */
private data class TrackStorageInfo(
    val fullPath: String?,
    val folder: String?,
    val fileName: String?,
    val isOnline: Boolean,
    val url: String
)

/**
 * Ermittelt den Speicherort eines Liedes: bei lokaler Gerätemusik (MediaStore-content-URI) den
 * echten Dateipfad + Ordner, bei importierten Dateien den file-Pfad, bei Online-Titeln die
 * Stream-Adresse. Blocking-IO (ContentResolver-Abfrage) – vom Aufrufer auf IO-Thread ausführen.
 */
private fun resolveTrackStorage(context: android.content.Context, track: Track): TrackStorageInfo {
    val url = track.audioUrl
    if (url.startsWith("http")) {
        return TrackStorageInfo(null, null, null, isOnline = true, url = url)
    }
    val uri = runCatching { Uri.parse(url) }.getOrNull()
        ?: return TrackStorageInfo(null, null, null, isOnline = true, url = url)
    if (uri.scheme == "file") {
        val path = uri.path
        return TrackStorageInfo(
            fullPath = path,
            folder = path?.substringBeforeLast('/'),
            fileName = path?.substringAfterLast('/'),
            isOnline = false,
            url = url
        )
    }
    // MediaStore-content-URI (lokale Gerätemusik): echten Dateipfad auslesen.
    var fullPath: String? = null
    var fileName: String? = null
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (dataIdx >= 0) fullPath = c.getString(dataIdx)
                if (nameIdx >= 0) fileName = c.getString(nameIdx)
            }
        }
    }
    return TrackStorageInfo(
        fullPath = fullPath,
        folder = fullPath?.substringBeforeLast('/'),
        fileName = fileName ?: fullPath?.substringAfterLast('/'),
        isOnline = false,
        url = url
    )
}

private fun trackSourceLabel(source: String): String = when (source) {
    "lethe" -> "Lethe-Bibliothek"
    "audius" -> "Audius"
    "local" -> "Lokale Gerätemusik"
    "imported" -> "Importiert"
    "user" -> "Persönliche Musik"
    else -> source
}

private fun formatTrackDuration(seconds: Int): String {
    if (seconds <= 0) return "Unbekannt"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d min".format(m, s)
}

/** Ein-/Ausschalter für den automatischen Download aller Titel dieser Ansicht auf das Gerät. */
@Composable
private fun AutoDownloadRow(enabled: Boolean, busy: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Automatischer Download", fontWeight = FontWeight.SemiBold)
            Text(
                "Speichert alle Titel lokal in Musik/LetheMediaPlayer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
    HorizontalDivider()
}

/** Dialog zum Anlegen einer neuen Playlist mit optionalem, manuell gewähltem Cover-Bild. */
@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, coverBytes: ByteArray?, coverMime: String?, playAsMix: Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var coverUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var playAsMix by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        coverUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Name der Playlist") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { pickImage.launch("image/*") }
                    ) {
                        if (coverUri != null) {
                            AsyncImage(model = coverUri, contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Cover wählen (optional – ohne Bild wird automatisch eine Collage aus den ersten 4 Songs erstellt)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                PlayAsMixToggle(checked = playAsMix, onCheckedChange = { playAsMix = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val bytes = coverUri?.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    val mime = coverUri?.let { context.contentResolver.getType(it) }
                    onCreate(name.trim(), bytes, mime, playAsMix)
                }
            ) { Text("Erstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/** Kopfbereich der Playlist-Detailansicht: Cover/Name + Buttons zum Abspielen und zur Shuffle-Wiedergabe. */
@Composable
private fun PlaylistDetailHeader(
    playlist: PlaylistDto,
    trackCount: Int,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (playlist.coverUrl != null) {
                CoverThumb(playlist.coverUrl, 64.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.playlistName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$trackCount Titel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Abspielen")
            }
            OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Zufällig")
            }
        }
    }
    HorizontalDivider()
}

/** Menü beim langen Drücken auf eine Playlist: Bearbeiten (Name/Cover) oder Löschen. */
@Composable
private fun PlaylistContextMenu(
    playlist: PlaylistDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlist.playlistName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text("Bearbeiten")
                }
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

/**
 * Dialog zum Bearbeiten einer Playlist: Namen anpassen und optional ein neues Cover-Bild wählen.
 * [onSave] erhält den neuen Namen (null = unverändert) und optional die Cover-Bytes.
 */
@Composable
private fun EditPlaylistDialog(
    playlist: PlaylistDto,
    onDismiss: () -> Unit,
    onSave: (name: String?, coverBytes: ByteArray?, coverMime: String?, playAsMix: Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(playlist.playlistName) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var playAsMix by remember { mutableStateOf(playlist.playAsMix) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        coverUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist bearbeiten") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name der Playlist") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { pickImage.launch("image/*") }
                    ) {
                        when {
                            coverUri != null -> AsyncImage(model = coverUri, contentDescription = null, modifier = Modifier.fillMaxSize())
                            playlist.coverUrl != null -> AsyncImage(model = playlist.coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                            else -> Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Cover ändern (optional – zum Auswählen eines Bildes antippen)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                PlayAsMixToggle(checked = playAsMix, onCheckedChange = { playAsMix = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val bytes = coverUri?.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                    val mime = coverUri?.let { context.contentResolver.getType(it) }
                    val newName = name.trim().takeIf { it != playlist.playlistName }
                    onSave(newName, bytes, mime, playAsMix)
                }
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

/**
 * Umschalter, ob eine Playlist als DJ-Mix (wie der Family/FriendsMix, mit fester 15s-Überblendung
 * und Einstieg ab 30% des Titels) abgespielt wird.
 */
@Composable
private fun PlayAsMixToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Als Mix abspielen", fontWeight = FontWeight.SemiBold)
            Text(
                "Nahtlose Übergänge wie beim Family Mix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Suchfeld + horizontale Kategorien-Chips (Genres) über der Bibliotheksliste. */
@Composable
private fun LibrarySearchAndCategories(
    query: String,
    onQueryChange: (String) -> Unit,
    genres: List<String>,
    selectedGenre: String?,
    onSelectGenre: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Titel oder Interpret suchen") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Löschen")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genres) { genre ->
                    FilterChip(
                        selected = selectedGenre == genre,
                        onClick = { onSelectGenre(genre) },
                        label = { Text(genre) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }
    }
}

/**
 * Tab "Lokal": zeigt die auf dem Gerät gefundenen Musiktitel (min. 1 Minute, mit Interpret + Titel).
 * Fordert bei Bedarf die Audio-Leseberechtigung an und durchsucht danach den MediaStore.
 */
@Composable
private fun LocalMediaTab(
    tracks: List<Track>,
    loading: Boolean,
    scanned: Boolean,
    currentId: String?,
    onScan: () -> Unit,
    onLongPress: (Track) -> Unit,
    onPlay: (List<Track>, Int) -> Unit
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) onScan()
    }

    // Beim ersten Öffnen mit Berechtigung automatisch scannen.
    LaunchedEffect(hasPermission) {
        if (hasPermission && !scanned && !loading) onScan()
    }

    var searchQuery by remember { mutableStateOf("") }

    when {
        !hasPermission -> Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Lokale Medien",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Erlaube den Zugriff auf deine Musik, um alle lokal gespeicherten Titel (mind. 1 Minute, mit Interpret & Titel) zu finden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(permission) }) {
                    Text("Zugriff erlauben")
                }
            }
        }
        loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        else -> {
            val filtered = remember(tracks, searchQuery) {
                if (searchQuery.isBlank()) tracks
                else tracks.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.artist.contains(searchQuery, ignoreCase = true)
                }
            }
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = { Text("Titel oder Interpret suchen") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Löschen")
                            }
                        } else {
                            IconButton(onClick = onScan) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Neu durchsuchen")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                )
                TrackList(filtered, currentId, onLongPress = onLongPress, onPlay = onPlay)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackList(
    tracks: List<Track>,
    currentId: String?,
    onLongPress: (Track) -> Unit = {},
    onPlay: (List<Track>, Int) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text("Keine Titel", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks) { index, t ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPlay(tracks, index) },
                        onLongClick = { onLongPress(t) }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverThumb(t.coverUrl, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = if (t.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (t.source == "lethe" && t.favoriteCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        t.favoriteCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (t.source == "lethe" || t.source == "audius" || t.source == "local") {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (t.source) {
                            "lethe" -> "Lethe"
                            "audius" -> "Audius"
                            else -> "Lokal"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (t.source) {
                            "lethe" -> MaterialTheme.colorScheme.primary
                            "audius" -> Color(0xFF7E1BCC)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistList(
    playlists: List<PlaylistDto>,
    onOpen: (PlaylistDto) -> Unit,
    onLongPress: (PlaylistDto) -> Unit
) {
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text("Keine Playlists", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists) { pl ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { onOpen(pl) }, onLongClick = { onLongPress(pl) })
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pl.coverUrl != null) {
                    CoverThumb(pl.coverUrl, 48.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pl.playlistName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pl.trackCount} Titel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CoverThumb(url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
