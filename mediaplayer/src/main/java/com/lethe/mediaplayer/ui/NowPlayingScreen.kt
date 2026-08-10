package com.lethe.mediaplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.lethe.mediaplayer.player.PlayerController
import com.lethe.mediaplayer.share.ShareCardGenerator
import kotlinx.coroutines.launch

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenAudioSheet: () -> Unit,
    onOpenQueue: () -> Unit,
    onPlayMix: () -> Unit
) {
    val controller = vm.playerController
    val current by vm.current.collectAsState()
    val artwork by controller.artwork.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()
    val shuffle by controller.shuffle.collectAsState()
    val isCasting by vm.castManager.isCasting.collectAsState()

    val browseState by vm.browse.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // ── Kopfzeile ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Zurück")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Wiedergabe", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Lethe",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CastButton(modifier = Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        // ── Cover mit Songtext-Chip ──
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Beim Casten das Cover des Cast-Titels (current.coverUrl) nutzen – das lokale
            // MediaSession-Artwork bleibt beim Skippen im Cast-Controller unverändert (stale).
            val artworkBytes = if (isCasting) null else artwork as? ByteArray
            val artworkUri = if (isCasting) null else artwork as? Uri
            val coverModel = artworkUri?.toString() ?: current?.coverUrl
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
                            modifier = Modifier.align(Alignment.Center).size(96.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.align(Alignment.Center).size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!current?.lyrics.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clickable { showLyrics = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Lyrics, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Songtext", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Aktionen: Teilen / Hinzufügen / Favorit ──
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val track = current ?: return@IconButton
                    if (isSharing) return@IconButton
                    isSharing = true
                    coroutineScope.launch {
                        val imageUri = ShareCardGenerator.generate(context, track, artwork)
                        // Teilbaren Song-Link erzeugen: entfernte Titel direkt, lokale Gerätedateien
                        // werden dafür einmalig hochgeladen (sonst wären sie beim Empfänger nicht
                        // abrufbar). Schlägt das fehl → APK-Empfehlung als Rückfall.
                        val songLink = vm.buildShareSongLink(track)
                        isSharing = false
                        if (imageUri != null) {
                            val recommendationText = if (songLink != null) {
                                "Hör dir \"${track.title}\" von ${track.artist} im Lethe Media Player an: $songLink"
                            } else {
                                "Hör dir \"${track.title}\" von ${track.artist} im Lethe Media Player an: " +
                                    "https://letheapp.de/downloads/mediaplayer/lethe-mediaplayer-latest.apk"
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, imageUri)
                                putExtra(Intent.EXTRA_TEXT, recommendationText)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Song teilen").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    }
                },
                enabled = !isSharing
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Teilen")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showAddToPlaylist = true }, enabled = current != null) {
                Icon(Icons.Filled.Add, contentDescription = "Hinzufügen")
            }
            IconButton(onClick = { current?.let { vm.toggleFavorite(it) } }) {
                Icon(
                    if (vm.isFavoriteTrack(current)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorit"
                )
            }
        }

        // ── Fortschritt ──
        val dur = if (durationMs > 0) durationMs else (current?.durationSec?.times(1000L) ?: 0L)
        Slider(
            value = if (dur > 0) (positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f,
            onValueChange = { frac -> if (dur > 0) vm.seekTo((frac * dur).toLong()) }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(positionMs), style = MaterialTheme.typography.labelMedium)
            Text(fmt(dur), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(12.dp))

        // ── Titel / Interpret ──
        Text(
            current?.title ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                current?.artist.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isCasting) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "· Wird gecastet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Steuerung: Loop | Prev | Play/Pause | Next | Shuffle ──
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller.cycleRepeat() }, enabled = !isCasting) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Wiederholen",
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { vm.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Zurück", modifier = Modifier.size(38.dp))
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            ) {
                IconButton(onClick = { vm.togglePlayPause() }, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Wiedergabe",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(onClick = { vm.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Weiter", modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { controller.toggleShuffle() }, enabled = !isCasting) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Zufall",
                    tint = if (shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Fußzeile: Lautsprecher | Titel-Mix | Warteschlange ──
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenAudioSheet) {
                Icon(Icons.Filled.Speaker, contentDescription = "Audioausgabe")
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onPlayMix) {
                Text("Titel-Mix abspielen")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Warteschlange")
            }
        }
    }

    if (showLyrics) {
        AlertDialog(
            onDismissRequest = { showLyrics = false },
            confirmButton = { TextButton(onClick = { showLyrics = false }) { Text("Schließen") } },
            title = { Text(current?.title ?: "Songtext") },
            text = { Text(current?.lyrics.orEmpty()) }
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            playlists = browseState.playlists,
            onDismiss = { showAddToPlaylist = false },
            onConfirm = { playlistId, playlistName ->
                vm.addCurrentTrackToPlaylist(playlistId, playlistName)
                showAddToPlaylist = false
            }
        )
    }
}
