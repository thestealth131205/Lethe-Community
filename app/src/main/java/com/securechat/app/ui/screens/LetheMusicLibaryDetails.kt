@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.securechat.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.securechat.app.MediaPlayerInstallDialog
import com.securechat.app.MediaPlayerLauncher
import com.securechat.app.data.local.AudioWaveformAnalyzer
import com.securechat.app.data.local.WaveformData
import com.securechat.app.data.network.MusicResponse
import com.securechat.app.data.network.UserMusicResponse
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetheMusicLibaryDetails(
    musicUrl: String,
    musicTitle: String?,
    musicArtist: String?,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // ── Aktiver Track (kann per Warteschlange wechseln) ───────────────────────
    var activeUrl    by remember { mutableStateOf(musicUrl) }
    var activeTitle  by remember { mutableStateOf(musicTitle) }
    var activeArtist by remember { mutableStateOf(musicArtist) }

    var trackDetails by remember { mutableStateOf<MusicResponse?>(null) }
    LaunchedEffect(activeUrl) {
        trackDetails = null
        if (activeUrl.isNotBlank()) {
            viewModel.getMusicByUrl(activeUrl) { response, _ -> trackDetails = response }
        }
    }

    // ── ExoPlayer ─────────────────────────────────────────────────────────────
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(activeUrl) {
        player.stop()
        if (activeUrl.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(activeUrl)))
            player.prepare()
            player.playWhenReady = isPlaying
        }
    }
    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    // ── Position + Dauer ──────────────────────────────────────────────────────
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs        by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            currentPositionMs = player.currentPosition
            if (durationMs == 0L) {
                val pd = player.duration.takeIf { it > 0 }
                if (pd != null) durationMs = pd
            }
            delay(100)
        }
    }
    LaunchedEffect(trackDetails) {
        val td = (trackDetails?.length ?: 0).toLong() * 1000L
        if (td > 0 && durationMs == 0L) durationMs = td
    }
    // Reset bei Track-Wechsel
    LaunchedEffect(activeUrl) {
        currentPositionMs = 0L
        durationMs = 0L
    }

    // ── Waveform-Analyse (läuft im Hintergrund) ───────────────────────────────
    var waveformData by remember { mutableStateOf<WaveformData?>(null) }
    LaunchedEffect(activeUrl) {
        waveformData = null
        if (activeUrl.isNotBlank()) {
            waveformData = runCatching { AudioWaveformAnalyzer.analyze(activeUrl) }.getOrNull()
        }
    }

    // ── Bibliothek / Favoriten ────────────────────────────────────────────────
    val library         by viewModel.userMusicLibrary.collectAsState()
    val favoriteMusicUrls by viewModel.favoriteMusicUrls.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadUserMusicLibrary() }

    var showQueue            by remember { mutableStateOf(false) }
    var showFavoritesInQueue by remember { mutableStateOf(false) }
    var showSaveDialog       by remember { mutableStateOf(false) }
    var savePlaylistName     by remember { mutableStateOf("") }

    // ── Angezeigte Track-Infos ────────────────────────────────────────────────
    val displayTitle  = trackDetails?.songTitle  ?: activeTitle  ?: "Unbekannter Titel"
    val displayArtist = trackDetails?.artist      ?: activeArtist ?: "Unbekannter K\u00fcnstler"
    val rawCoverUrl   = trackDetails?.coverUrl
    val coverUrl      = rawCoverUrl?.let { if (it.startsWith("/")) "https://letheapp.de$it" else it }
    val durationSec   = trackDetails?.length ?: 0
    val source        = trackDetails?.source ?: ""
    val year          = trackDetails?.year
    val lyrics        = trackDetails?.lyrics
    val producer      = trackDetails?.producer
    val sourceLabel   = when (source) {
        "lethe_library" -> "Lethe Bibliothek"
        "audius"        -> "Audius"
        else            -> "Audio Bibliothek"
    }

    // ── Dialog: Warteschlange als Playlist speichern ──────��───────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false; savePlaylistName = "" },
            title = { Text("Warteschlange als Playlist") },
            text = {
                OutlinedTextField(
                    value = savePlaylistName,
                    onValueChange = { savePlaylistName = it },
                    label = { Text("Name der Playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = savePlaylistName.isNotBlank(),
                    onClick = {
                        val name = savePlaylistName.trim()
                        val queueTracks = if (showFavoritesInQueue)
                            library.filter { it.musicUrl in favoriteMusicUrls || it.favorit }
                        else library
                        queueTracks.forEach { track ->
                            viewModel.addMusicToPlaylist(
                                url = track.musicUrl,
                                title = track.musicTitle,
                                artist = track.artist,
                                playTimeSec = track.playTime,
                                playlistId = null,
                                playlistName = name
                            )
                        }
                        showSaveDialog = false
                        savePlaylistName = ""
                    }
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false; savePlaylistName = "" }) { Text("Abbrechen") }
            }
        )
    }

    // ── Lethe Media Player (Companion-App) ────────────────────────────────────
    var showMediaPlayerInstallDialog by remember { mutableStateOf(false) }
    if (showMediaPlayerInstallDialog) {
        MediaPlayerInstallDialog(onDismiss = { showMediaPlayerInstallDialog = false })
    }

    // ── Haupt-Layout ──────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Musik Details", color = topBarTitleColor()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zur\u00fcck")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scrollbarer Haupt-Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))

                // Cover-Art
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Quelle-Badge
                if (source.isNotBlank()) {
                    Surface(
                        color = Color(0xFFA8A800).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            sourceLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color(0xFFA8A800),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Titel
                Text(
                    text = displayTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(Modifier.height(6.dp))

                // K\u00fcnstler
                Text(
                    text = displayArtist,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                // Dauer-Text
                if (durationSec > 0) {
                    Spacer(Modifier.height(4.dp))
                    val min = durationSec / 60
                    val sec = durationSec % 60
                    Text(
                        text = "$min:${sec.toString().padStart(2, '0')}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Jahr & Produzent
                if (!year.isNullOrBlank() || !producer.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    if (!year.isNullOrBlank()) Text("Jahr: $year", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    if (!producer.isNullOrBlank()) Text("Produzent: $producer", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }

                // Lyrics
                if (!lyrics.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Lyrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    Spacer(Modifier.height(6.dp))
                    Text(lyrics, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 16.dp))
                }

                Spacer(Modifier.height(24.dp))

                // ── Waveform ──────────────────────────────────────────────────
                val effectiveDurationMs = if (durationMs > 0) durationMs
                    else player.duration.takeIf { it > 0 } ?: 0L
                WaveformDisplay(
                    waveformData = waveformData,
                    currentPositionMs = currentPositionMs,
                    durationMs = effectiveDurationMs,
                    onSeek = { fraction ->
                        val total = if (durationMs > 0) durationMs else player.duration.takeIf { it > 0 } ?: 0L
                        val seekMs = (fraction * total).toLong()
                        player.seekTo(seekMs)
                        currentPositionMs = seekMs
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                // Positions-Anzeige
                if (effectiveDurationMs > 0) {
                    Spacer(Modifier.height(4.dp))
                    val posSec = (currentPositionMs / 1000).toInt()
                    val totSec = (effectiveDurationMs / 1000).toInt()
                    Text(
                        text = "${posSec / 60}:${(posSec % 60).toString().padStart(2,'0')} / ${totSec / 60}:${(totSec % 60).toString().padStart(2,'0')}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Play/Pause
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Abspielen",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Pfeil-nach-unten: Warteschlange \u00f6ffnen/schlie\u00dfen
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        imageVector = if (showQueue) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showQueue) "Warteschlange schlie\u00dfen" else "Warteschlange \u00f6ffnen",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // In Lethe Media Player öffnen
                OutlinedButton(
                    onClick = {
                        if (MediaPlayerLauncher.open(context)) {
                            player.pause()
                        } else {
                            showMediaPlayerInstallDialog = true
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("In Lethe Media Player \u00f6ffnen")
                }

                Spacer(Modifier.height(32.dp))
            }

            // ── Warteschlangen-Panel (dreiteilig + scrollbar) ─────────────────
            AnimatedVisibility(
                visible = showQueue,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit  = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                QueuePanel(
                    library            = library,
                    favoriteMusicUrls  = favoriteMusicUrls,
                    activeUrl          = activeUrl,
                    showFavorites      = showFavoritesInQueue,
                    onToggleFavorites  = { showFavoritesInQueue = !showFavoritesInQueue },
                    onTrackSelected    = { track ->
                        activeUrl    = track.musicUrl
                        activeTitle  = track.musicTitle
                        activeArtist = track.artist
                        isPlaying    = true
                    },
                    onSaveAsPlaylist   = { showSaveDialog = true },
                    onToggleFavorite   = { track ->
                        viewModel.toggleMusicFavorite(track.musicUrl, track.musicTitle, track.artist, track.playTime)
                    }
                )
            }
        }
    }
}

// ── Waveform-Komponente ────────────────────────────────────────────────────────

@Composable
private fun WaveformDisplay(
    waveformData: WaveformData?,
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor  = MaterialTheme.colorScheme.primary
    val surfaceColor  = MaterialTheme.colorScheme.onSurface

    if (waveformData == null) {
        Box(modifier = modifier.height(56.dp), contentAlignment = Alignment.Center) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp))
        }
        return
    }

    val progressFraction = if (durationMs > 0)
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    else 0f

    Canvas(
        modifier = modifier
            .height(56.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val count      = waveformData.segmentCount
        val barW       = size.width / (count * 1.5f)
        val gap        = barW * 0.5f
        val maxH       = size.height * 0.85f
        val centerY    = size.height / 2f

        for (i in 0 until count) {
            val x    = i * (barW + gap)
            val amp  = waveformData.amplitudes[i].coerceAtLeast(0.04f)
            val freq = waveformData.frequencies[i]
            val barH = (maxH * amp).coerceAtLeast(4.dp.toPx())
            val top  = centerY - barH / 2f
            val frac = i.toFloat() / count.toFloat()

            val color = if (frac <= progressFraction)
                primaryColor.copy(alpha = (0.65f + freq * 0.35f).coerceIn(0.5f, 1f))
            else
                surfaceColor.copy(alpha = 0.22f)

            drawRect(color = color, topLeft = Offset(x, top), size = Size(barW, barH))
        }
    }
}

// ── Warteschlangen-Panel (dreiteilig) ─────────────────────────────────────────

@Composable
private fun QueuePanel(
    library: List<UserMusicResponse>,
    favoriteMusicUrls: Set<String>,
    activeUrl: String,
    showFavorites: Boolean,
    onToggleFavorites: () -> Unit,
    onTrackSelected: (UserMusicResponse) -> Unit,
    onSaveAsPlaylist: () -> Unit,
    onToggleFavorite: (UserMusicResponse) -> Unit
) {
    val displayTracks = if (showFavorites)
        library.filter { it.musicUrl in favoriteMusicUrls || it.favorit }
    else library

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // Teil 1: Kopfzeile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showFavorites) "Favoriten" else "Wiedergabeliste",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Herz: wechselt zwischen Warteschlange und Favoriten
                IconButton(onClick = onToggleFavorites, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (showFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (showFavorites) "Zur\u00fcck zur Wiedergabeliste" else "Favoriten anzeigen",
                        tint = if (showFavorites) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Plus: aktuelle Liste als Playlist speichern (nur im Warteschlangen-Modus)
                if (!showFavorites) {
                    IconButton(onClick = onSaveAsPlaylist, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Als Playlist speichern",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Teil 2: Scrollbare Track-Liste (~3 Einträge sichtbar)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 204.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (displayTracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showFavorites) "Keine Favoriten" else "Bibliothek leer",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(displayTracks, key = { it.id }) { track ->
                        QueueTrackRow(
                            track           = track,
                            isActive        = track.musicUrl == activeUrl,
                            isFavorite      = track.musicUrl in favoriteMusicUrls || track.favorit,
                            onClick         = { onTrackSelected(track) },
                            onToggleFavorite = { onToggleFavorite(track) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Teil 3: Fu\u00dfzeile
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${displayTracks.size} Titel",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: UserMusicResponse,
    isActive: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isActive) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.musicTitle?.ifBlank { null } ?: "Unbekannt",
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                track.artist?.ifBlank { null } ?: "Unbekannt",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Favorit entfernen" else "Favorisieren",
                tint = if (isFavorite) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
