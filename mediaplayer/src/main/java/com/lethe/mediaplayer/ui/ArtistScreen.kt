package com.lethe.mediaplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lethe.mediaplayer.data.ArtistProfile
import com.lethe.mediaplayer.data.Track

/**
 * Künstler-Screen: zeigt alle freigegebenen Künstler. Beim Antippen öffnet sich das Detail mit
 * Künstlerbild, Biografie und den zugeordneten Songs (abspielbar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDiscoveryScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val artists by vm.publicArtists.collectAsState()
    val loading by vm.publicArtistsLoading.collectAsState()
    val selected by vm.selectedArtist.collectAsState()

    LaunchedEffect(Unit) { vm.loadPublicArtists() }

    // Detailansicht (Künstler-Screen) hat Vorrang, solange ein Künstler gewählt ist.
    selected?.let { profile ->
        ArtistDetailContent(
            profile = profile,
            onBack = { vm.clearSelectedArtist() },
            onPlayAll = { vm.playArtistProfile(profile); onOpenNowPlaying() },
            onPlaySong = { index -> vm.playFrom(profile.songs, index); onOpenNowPlaying() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Künstler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading && artists.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            artists.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    "Noch keine Künstler verfügbar.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onClick = { vm.openArtist(artist.id) })
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistProfile, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtistAvatar(name = artist.name, imageUrl = artist.picture, size = 56)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name.ifBlank { "Unbekannt" },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (artist.songs.size == 1) "1 Titel" else "${artist.songs.size} Titel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistDetailContent(
    profile: ArtistProfile,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlaySong: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name.ifBlank { "Künstler" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ArtistAvatar(name = profile.name, imageUrl = profile.picture, size = 140)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        profile.name.ifBlank { "Unbekannt" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!profile.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (profile.songs.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onPlayAll) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Alle abspielen")
                        }
                    }
                }
            }
            if (profile.songs.isEmpty()) {
                item {
                    Text(
                        "Dieser Künstler hat noch keine Titel hinterlegt.",
                        Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(profile.songs, key = { _, t -> t.id }) { index, track ->
                    SongRow(track = track, onClick = { onPlaySong(index) })
                }
            }
        }
    }
}

@Composable
private fun SongRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(artistBrush(track.title)),
            contentAlignment = Alignment.Center
        ) {
            if (!track.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (track.artist.isNotBlank()) {
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ArtistAvatar(name: String, imageUrl: String?, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(artistBrush(name)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                artistInitials(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 3).sp
            )
        }
    }
}

private fun artistInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

private val ArtistGradients = listOf(
    Color(0xFF7C4DFF) to Color(0xFFE91E63),
    Color(0xFF2196F3) to Color(0xFF21D4FD),
    Color(0xFF11998E) to Color(0xFF38EF7D),
    Color(0xFFF7971E) to Color(0xFFFFD200),
    Color(0xFFEE0979) to Color(0xFFFF6A00),
    Color(0xFF8E2DE2) to Color(0xFF4A00E0)
)

private fun artistBrush(key: String): Brush {
    val idx = Math.floorMod(key.hashCode(), ArtistGradients.size)
    val (a, b) = ArtistGradients[idx]
    return Brush.linearGradient(listOf(a, b))
}
