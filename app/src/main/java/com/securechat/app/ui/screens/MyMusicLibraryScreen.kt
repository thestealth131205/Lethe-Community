package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.data.network.PlaylistResponse
import com.securechat.app.data.network.UserMusicResponse
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor

private enum class MyMusicTab(val label: String) {
    LIBRARY("Bibliothek"),
    FAVORITES("Favoriten"),
    PLAYLISTS("Playlists")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMusicLibraryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onPlayTrack: (url: String, title: String?, artist: String?) -> Unit
) {
    val library by viewModel.userMusicLibrary.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val playlistTracks by viewModel.playlistTracks.collectAsState()

    var selectedTab by remember { mutableStateOf(MyMusicTab.LIBRARY) }
    var openedPlaylist by remember { mutableStateOf<PlaylistResponse?>(null) }
    var addToPlaylistTarget by remember { mutableStateOf<UserMusicResponse?>(null) }
    var trackToDelete by remember { mutableStateOf<UserMusicResponse?>(null) }

    LaunchedEffect(Unit) { viewModel.loadUserMusicLibrary() }
    LaunchedEffect(openedPlaylist) {
        openedPlaylist?.let { viewModel.loadPlaylistTracks(it.playlistId) }
    }

    // Löschen-Bestätigung
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Entfernen?") },
            text = { Text("\"${track.musicTitle?.ifBlank { null } ?: "Unbekannt"}\" wird aus deiner Musik entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMusicEntry(track)
                    trackToDelete = null
                }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) { Text("Abbrechen") }
            }
        )
    }

    // Playlist-Auswahl / neu anlegen
    addToPlaylistTarget?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylistTarget = null },
            onSelect = { playlist, newName ->
                viewModel.addMusicToPlaylist(
                    url = track.musicUrl,
                    title = track.musicTitle,
                    artist = track.artist,
                    playTimeSec = track.playTime,
                    playlistId = playlist?.playlistId,
                    playlistName = playlist?.playlistName ?: newName
                )
                addToPlaylistTarget = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(openedPlaylist?.playlistName ?: "Meine Musik") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (openedPlaylist != null) openedPlaylist = null else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = topBarTitleColor(),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Playlist-Detailansicht
            if (openedPlaylist != null) {
                val tracks = playlistTracks[openedPlaylist!!.playlistId].orEmpty()
                TrackList(
                    tracks = tracks,
                    favoriteUrls = library.filter { it.favorit }.map { it.musicUrl }.toSet(),
                    emptyText = "Diese Playlist ist leer",
                    onPlay = onPlayTrack,
                    onToggleFavorite = { viewModel.toggleMusicFavorite(it.musicUrl, it.musicTitle, it.artist, it.playTime) },
                    onAddToPlaylist = { addToPlaylistTarget = it },
                    onDelete = { trackToDelete = it }
                )
                return@Column
            }

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MyMusicTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                MyMusicTab.LIBRARY -> TrackList(
                    tracks = library,
                    favoriteUrls = library.filter { it.favorit }.map { it.musicUrl }.toSet(),
                    emptyText = "Noch keine Musik gespeichert",
                    onPlay = onPlayTrack,
                    onToggleFavorite = { viewModel.toggleMusicFavorite(it.musicUrl, it.musicTitle, it.artist, it.playTime) },
                    onAddToPlaylist = { addToPlaylistTarget = it },
                    onDelete = { trackToDelete = it }
                )
                MyMusicTab.FAVORITES -> TrackList(
                    tracks = library.filter { it.favorit },
                    favoriteUrls = library.filter { it.favorit }.map { it.musicUrl }.toSet(),
                    emptyText = "Keine Favoriten",
                    onPlay = onPlayTrack,
                    onToggleFavorite = { viewModel.toggleMusicFavorite(it.musicUrl, it.musicTitle, it.artist, it.playTime) },
                    onAddToPlaylist = { addToPlaylistTarget = it },
                    onDelete = { trackToDelete = it }
                )
                MyMusicTab.PLAYLISTS -> PlaylistList(
                    playlists = playlists,
                    onOpen = { openedPlaylist = it }
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<UserMusicResponse>,
    favoriteUrls: Set<String>,
    emptyText: String,
    onPlay: (url: String, title: String?, artist: String?) -> Unit,
    onToggleFavorite: (UserMusicResponse) -> Unit,
    onAddToPlaylist: (UserMusicResponse) -> Unit,
    onDelete: (UserMusicResponse) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(8.dp))
                Text(emptyText, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isFavorite = track.musicUrl in favoriteUrls || track.favorit,
                onPlay = { onPlay(track.musicUrl, track.musicTitle, track.artist) },
                onToggleFavorite = { onToggleFavorite(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onDelete = { onDelete(track) }
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: UserMusicResponse,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.musicTitle?.ifBlank { null } ?: "Unbekannt",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist?.ifBlank { null } ?: "Unbekannt",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Mehr",
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Zu Playlist hinzufuegen") },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Entfernen") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlaylistResponse>,
    onOpen: (PlaylistResponse) -> Unit
) {
    if (playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Noch keine Playlists",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    "Fuege einem Song ueber das Menue eine Playlist hinzu",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(playlists, key = { it.playlistId }) { playlist ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            playlist.playlistName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${playlist.trackCount} Titel",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistResponse>,
    onDismiss: () -> Unit,
    onSelect: (existing: PlaylistResponse?, newName: String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu Playlist hinzufuegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlists.isNotEmpty()) {
                    Text(
                        "Bestehende Playlist",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(playlist, playlist.playlistName) }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(playlist.playlistName, modifier = Modifier.weight(1f))
                            Text(
                                "${playlist.trackCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
                Text(
                    "Neue Playlist",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Name der Playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelect(null, newName.trim()) },
                enabled = newName.isNotBlank()
            ) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
