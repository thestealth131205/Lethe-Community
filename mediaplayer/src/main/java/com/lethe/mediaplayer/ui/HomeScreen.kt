package com.lethe.mediaplayer.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lethe.mediaplayer.data.PlaylistDto

/** Farbverläufe für die Kacheln – deterministisch aus dem Namen abgeleitet. */
private val TileGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFF7C4DFF) to Color(0xFFE91E63),
    Color(0xFF2196F3) to Color(0xFF21D4FD),
    Color(0xFF11998E) to Color(0xFF38EF7D),
    Color(0xFFF7971E) to Color(0xFFFFD200),
    Color(0xFFEE0979) to Color(0xFFFF6A00),
    Color(0xFF8E2DE2) to Color(0xFF4A00E0),
    Color(0xFF00B4DB) to Color(0xFF0083B0),
    Color(0xFFFC466B) to Color(0xFF3F5EFB)
)

private fun brushFor(key: String): Brush {
    val idx = (Math.floorMod(key.hashCode(), TileGradients.size))
    val (a, b) = TileGradients[idx]
    return Brush.linearGradient(listOf(a, b))
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onOpenNowPlaying: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenFriendsGroup: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenArtistArea: () -> Unit,
    onOpenAdminArea: () -> Unit,
    onOpenAdminPlaylists: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    val state by vm.browse.collectAsState()
    val current by vm.current.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val uploadState by vm.uploadState.collectAsState()
    val artwork by vm.playerController.artwork.collectAsState()
    val isCasting by vm.castManager.isCasting.collectAsState()
    val accountInfo by vm.accountInfo.collectAsState()
    val context = LocalContext.current

    // Titel jeder Playlist laden, damit die Kacheln eine Cover-Collage der Songs zeigen können.
    LaunchedEffect(state.playlists, state.lethePlaylists) {
        state.playlists.forEach { vm.loadPlaylist(it.playlistId) }
        state.lethePlaylists.forEach { vm.loadLethePlaylist(it.playlistId) }
    }

    // Ausgewählte lokale Datei, für die noch eine Ziel-Playlist gewählt wird.
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingUri = uri }

    // Rückmeldung nach dem Upload.
    LaunchedEffect(uploadState) {
        when (val s = uploadState) {
            is UploadState.Done -> {
                Toast.makeText(context, "Zu \u201e${s.playlistName}\u201c hinzugefügt", Toast.LENGTH_SHORT).show()
                vm.clearUploadState()
            }
            is UploadState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                vm.clearUploadState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Home",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Künstler") },
                            onClick = { menuOpen = false; onOpenArtists() }
                        )
                        DropdownMenuItem(
                            text = { Text("Künstler-Bereich") },
                            onClick = { menuOpen = false; onOpenArtistArea() }
                        )
                        if (accountInfo?.isAdmin == true) {
                            DropdownMenuItem(
                                text = { Text("Admin-Bereich") },
                                onClick = { menuOpen = false; onOpenAdminArea() }
                            )
                            DropdownMenuItem(
                                text = { Text("Lethe-Playlists") },
                                onClick = { menuOpen = false; onOpenAdminPlaylists() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("App Infos") },
                            onClick = { menuOpen = false; onOpenAppInfo() }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column {
                if (current != null) {
                    NowPlayingBar(
                        title = current?.title ?: "",
                        artist = current?.artist ?: "",
                        coverUrl = current?.coverUrl,
                        artwork = artwork,
                        isCasting = isCasting,
                        isPlaying = isPlaying,
                        isFavorite = vm.isFavoriteTrack(current),
                        onPlayPause = { vm.togglePlayPause() },
                        onToggleFavorite = { current?.let { vm.toggleFavorite(it) } },
                        onSkip = { vm.next() },
                        onOpen = onOpenNowPlaying
                    )
                }
                HomeBottomNav(selected = 0, onLibrary = onOpenLibrary)
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            // ── Kacheln: Lieblingssongs + je Playlist eine Kachel + lokale Songs hinzufügen ──
            // Die "Favoriten"-Playlist wird ausgeblendet: sie dupliziert die Lieblingssongs-Kachel,
            // die bereits alle mit Herz markierten Songs (Favoriten) enthält.
            val hiddenNames = setOf("favoriten", "favorites", "lieblingssongs")
            val tiles = buildList<HomeTile> {
                add(HomeTile.Favorites)
                add(HomeTile.FriendsMix)
                // Global vom Admin kuratierte Lethe-Playlists direkt neben den Lieblingssongs.
                state.lethePlaylists.forEach { add(HomeTile.LethePlaylist(it)) }
                state.playlists
                    .filter { it.playlistName.trim().lowercase() !in hiddenNames }
                    .forEach { add(HomeTile.Playlist(it)) }
                add(HomeTile.AddLocal)
            }
            tiles.chunked(3).forEach { rowTiles ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowTiles.forEach { tile ->
                        Box(Modifier.weight(1f)) {
                            when (tile) {
                                is HomeTile.Favorites -> CollageTile(
                                    key = "lieblingssongs",
                                    caption = "Lieblingssongs",
                                    subtitle = "${state.favorites.size} Titel",
                                    covers = state.favorites.mapNotNull { it.coverUrl },
                                    icon = Icons.Filled.Favorite,
                                    onClick = { vm.playFavorites(); onOpenNowPlaying() }
                                )
                                is HomeTile.FriendsMix -> {
                                    var contactMenuOpen by remember { mutableStateOf(false) }
                                    Box {
                                        CollageTile(
                                            key = "friendsmix",
                                            caption = "FriendsMix",
                                            subtitle = if (state.friendsMix.isEmpty()) "Gruppe einrichten"
                                            else "${state.friendsMix.size} Titel",
                                            covers = state.friendsMix.mapNotNull { it.coverUrl },
                                            icon = Icons.Filled.Group,
                                            onClick = {
                                                if (state.friendsMix.isEmpty()) onOpenFriendsGroup()
                                                else { vm.playFriendsMix(); onOpenNowPlaying() }
                                            },
                                            onLongClick = {
                                                vm.loadFriendsContacts()
                                                contactMenuOpen = true
                                            },
                                            onEdit = onOpenFriendsGroup
                                        )
                                        FriendsMixContactMenu(
                                            vm = vm,
                                            expanded = contactMenuOpen,
                                            onDismiss = { contactMenuOpen = false }
                                        )
                                    }
                                }
                                is HomeTile.Playlist -> CollageTile(
                                    key = tile.dto.playlistId,
                                    caption = tile.dto.playlistName,
                                    subtitle = "${tile.dto.trackCount} Titel",
                                    covers = state.playlistTracks[tile.dto.playlistId]
                                        ?.mapNotNull { it.coverUrl }.orEmpty(),
                                    bigText = tile.dto.playlistName,
                                    onClick = { onOpenPlaylist(tile.dto.playlistId) }
                                )
                                is HomeTile.LethePlaylist -> CollageTile(
                                    key = tile.dto.playlistId,
                                    caption = tile.dto.playlistName,
                                    subtitle = "${tile.dto.trackCount} Titel",
                                    covers = state.playlistTracks[tile.dto.playlistId]
                                        ?.mapNotNull { it.coverUrl }.orEmpty(),
                                    bigText = tile.dto.playlistName,
                                    onClick = { vm.playLethePlaylist(tile.dto.playlistId); onOpenNowPlaying() }
                                )
                                is HomeTile.AddLocal -> AddLocalTile(
                                    onClick = { filePicker.launch(arrayOf("audio/*")) }
                                )
                            }
                        }
                    }
                    repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ── Künstler (nach Herzen des Nutzers) ──
            if (state.artists.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Künstler",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.artists) { artist ->
                        ArtistTile(
                            name = artist.name,
                            count = artist.favoriteCount,
                            onClick = { vm.playArtist(artist); onOpenNowPlaying() }
                        )
                    }
                }
            }
        }
    }

    pendingUri?.let { uri ->
        AddToPlaylistDialog(
            playlists = state.playlists,
            onDismiss = { pendingUri = null },
            onConfirm = { playlistId, playlistName ->
                vm.addLocalSongToPlaylist(uri, playlistId, playlistName)
                pendingUri = null
            }
        )
    }

    if (uploadState is UploadState.Uploading) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("Song wird hinzugefügt") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Lädt hoch…")
                }
            }
        )
    }
}

private sealed interface HomeTile {
    data object Favorites : HomeTile
    data object FriendsMix : HomeTile
    data class Playlist(val dto: PlaylistDto) : HomeTile
    data class LethePlaylist(val dto: PlaylistDto) : HomeTile
    data object AddLocal : HomeTile
}

/**
 * Kachel mit einer 2x2-Collage aus bis zu 4 (zufälligen) Song-Covers der Playlist/Favoriten.
 * Ohne Cover fällt sie auf den Farbverlauf + [icon]/[bigText] zurück; bei genau einem Cover
 * wird dieses formatfüllend gezeigt, ab zwei Covern das 2x2-Raster (leere Zellen = Farbverlauf).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CollageTile(
    key: String,
    caption: String,
    subtitle: String,
    covers: List<String>,
    onClick: () -> Unit,
    bigText: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onEdit: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    // Bis zu 4 verschiedene Cover zufällig auswählen (stabil, solange sich die Cover-Liste nicht ändert).
    val shown = remember(covers) { covers.distinct().shuffled().take(4) }
    Column(
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(brushFor(key))
        ) {
            when {
                shown.size >= 2 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        CollageCell(shown.getOrNull(0), brushFor("$key-0"), Modifier.weight(1f).fillMaxHeight())
                        CollageCell(shown.getOrNull(1), brushFor("$key-1"), Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        CollageCell(shown.getOrNull(2), brushFor("$key-2"), Modifier.weight(1f).fillMaxHeight())
                        CollageCell(shown.getOrNull(3), brushFor("$key-3"), Modifier.weight(1f).fillMaxHeight())
                    }
                }
                shown.size == 1 -> AsyncImage(
                    model = shown[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                icon != null -> Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(56.dp)
                )
                bigText != null -> Text(
                    bigText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                )
            }
            // Bearbeiten-Symbol (nur FriendsMix): Gruppe auch dann verwalten, wenn schon Titel da sind.
            if (onEdit != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onEdit() }
                        .padding(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = "FriendsMix-Gruppe bearbeiten",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/**
 * Inline-Menü (per Long-Press auf die FriendsMix-Kachel): listet alle Kontakte und erlaubt das
 * An-/Abwählen, wessen Lieblingssongs in den FriendsMix einfließen. Nutzt dieselben Kontaktdaten,
 * die Lethe über GET /friendsmix/contacts bereitstellt.
 */
@Composable
private fun FriendsMixContactMenu(
    vm: PlayerViewModel,
    expanded: Boolean,
    onDismiss: () -> Unit
) {
    val contacts by vm.friendsContacts.collectAsState()
    val loading by vm.friendsContactsLoading.collectAsState()

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            "Kontakte für FriendsMix",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Divider()
        when {
            loading && contacts.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(Modifier.size(24.dp)) }
            contacts.isEmpty() -> Text(
                "Keine Kontakte gefunden.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            else -> contacts.forEach { contact ->
                DropdownMenuItem(
                    text = {
                        Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    onClick = { vm.setFriendInGroup(contact.userId, !contact.inGroup) },
                    leadingIcon = {
                        Icon(
                            if (contact.inGroup) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (contact.inGroup) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}

/** Eine Zelle der Cover-Collage: zeigt das Cover formatfüllend oder – falls keins – den Farbverlauf. */
@Composable
private fun CollageCell(cover: String?, fallback: Brush, modifier: Modifier) {
    Box(modifier.background(fallback)) {
        if (cover != null) AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ArtistTile(name: String, count: Int, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(brushFor(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            if (count == 1) "1 Herz" else "$count Herzen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddLocalTile(onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Lokale Lieder hinzufügen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Lieder hinzufügen",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            "Vom Gerät",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistDto>,
    onDismiss: () -> Unit,
    onConfirm: (playlistId: String?, playlistName: String) -> Unit
) {
    // null = "Neue Playlist" ausgewählt; sonst die playlistId einer bestehenden.
    var selectedId by remember { mutableStateOf(playlists.firstOrNull()?.playlistId) }
    var newName by remember { mutableStateOf("") }
    val creatingNew = selectedId == null

    val chosenName = if (creatingNew) newName.trim()
    else playlists.firstOrNull { it.playlistId == selectedId }?.playlistName.orEmpty()
    val canConfirm = chosenName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zu Playlist hinzufügen") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                playlists.forEach { pl ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedId == pl.playlistId,
                                onClick = { selectedId = pl.playlistId }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == pl.playlistId,
                            onClick = { selectedId = pl.playlistId }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(pl.playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (playlists.isNotEmpty()) Divider(Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = creatingNew, onClick = { selectedId = null })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = creatingNew, onClick = { selectedId = null })
                    Spacer(Modifier.width(4.dp))
                    Text("Neue Playlist")
                }
                if (creatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("Name der Playlist") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(if (creatingNew) null else selectedId, chosenName) }
            ) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun NowPlayingBar(
    title: String,
    artist: String,
    coverUrl: String?,
    artwork: Any?,
    isCasting: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSkip: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onOpen() }
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
            ) {
                // Cover-Quelle wie in der Vollansicht: eingebettetes ID3-Artwork (ByteArray/Uri
                // aus der MediaSession) hat Vorrang, sonst die Server-Cover-URL des Titels.
                // Beim Casten das Cover des Cast-Titels nutzen – das lokale Artwork bleibt beim
                // Skippen im Cast-Controller unverändert (stale).
                val artworkBytes = if (isCasting) null else artwork as? ByteArray
                val artworkUri = if (isCasting) null else artwork as? Uri
                val coverModel = artworkUri?.toString() ?: coverUrl
                when {
                    artworkBytes != null -> {
                        val bitmap = remember(artworkBytes) {
                            BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                    coverModel != null -> AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Wiedergabe",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    artist,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(onClick = onSkip) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Weiter",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun HomeBottomNav(selected: Int, onLibrary: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = onLibrary,
            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
            label = { Text("Bibliothek") }
        )
    }
}
