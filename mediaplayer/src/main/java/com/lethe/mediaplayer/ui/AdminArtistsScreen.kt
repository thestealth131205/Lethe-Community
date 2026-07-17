package com.lethe.mediaplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lethe.mediaplayer.data.ArtistDto
import com.lethe.mediaplayer.data.BpmStatusDto

/**
 * Admin-Bereich (nur für Lethe-Admins): listet alle Künstler-Accounts und erlaubt es, sie
 * freizugeben, zu sperren/entsperren oder zu löschen. Das Backend prüft zusätzlich is_admin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminArtistsScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val artists by vm.adminArtists.collectAsState()
    val loading by vm.adminArtistsLoading.collectAsState()
    val bpmStatus by vm.bpmStatus.collectAsState()

    LaunchedEffect(Unit) { vm.loadAdminArtists() }
    // BPM-Status alle 5s aktualisieren, solange dieser Screen sichtbar ist.
    DisposableEffect(Unit) {
        vm.startBpmStatusPolling()
        onDispose { vm.stopBpmStatusPolling() }
    }

    var deleteTarget by remember { mutableStateOf<ArtistDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin: Künstler") },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "bpm-status") {
                BpmStatusCard(status = bpmStatus)
            }
            when {
                loading && artists.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp)) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
                artists.isEmpty() -> item(key = "empty") {
                    Text(
                        "Es gibt noch keine Künstler-Accounts.",
                        Modifier.fillMaxWidth().padding(top = 32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> items(artists, key = { it.id }) { artist ->
                    AdminArtistCard(
                        artist = artist,
                        onApprove = { vm.adminApproveArtist(artist.id, !artist.isApproved) },
                        onBlock = { vm.adminBlockArtist(artist.id, !artist.isBlocked) },
                        onDelete = { deleteTarget = artist }
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Künstler löschen") },
            text = { Text("Soll der Künstler-Account \u201e${target.artistName}\u201c wirklich gelöscht werden?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.adminDeleteArtist(target.id)
                    deleteTarget = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            }
        )
    }
}

/**
 * Zeigt den Live-Fortschritt der serverseitigen FriendsMix-BPM-Berechnung. Der Server aktualisiert
 * die Zahlen, der Screen pollt alle 5 Sekunden: "berechnet / noch offen".
 */
@Composable
private fun BpmStatusCard(status: BpmStatusDto?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("FriendsMix – BPM-Berechnung", fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            if (status == null) {
                Text(
                    "Status wird geladen …",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${status.calculated}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    " / ${status.remaining} offen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 4.dp)
                )
            }
            Text(
                "berechnet / noch zu berechnen (gesamt ${status.total})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(10.dp))
            LinearProgressIndicator(
                progress = { if (status.total > 0) status.calculated.toFloat() / status.total else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(10.dp))
            if (status.running && !status.currentTitle.isNullOrBlank()) {
                Text(
                    "Berechnet gerade: ${status.currentTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (status.remaining == 0) {
                Text(
                    "Alle Titel berechnet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Wartet auf nächsten Titel …",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AdminArtistCard(
    artist: ArtistDto,
    onApprove: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtistAvatar(name = artist.artistName, imageUrl = artist.artistPicture, size = 48)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.artistName.ifBlank { "Unbenannt" },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artist.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (artist.isBlocked) {
                    StatusChip("Gesperrt", MaterialTheme.colorScheme.error)
                } else if (artist.isApproved) {
                    StatusChip("Freigegeben", MaterialTheme.colorScheme.primary)
                } else {
                    StatusChip("Wartet", MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    if (artist.songIds.size == 1) "1 Titel" else "${artist.songIds.size} Titel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onApprove) {
                    Text(if (artist.isApproved) "Freigabe zurückziehen" else "Freigeben")
                }
                OutlinedButton(onClick = onBlock) {
                    Text(if (artist.isBlocked) "Entsperren" else "Sperren")
                }
            }
            Spacer(Modifier.size(4.dp))
            TextButton(onClick = onDelete) {
                Text("Löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledContainerColor = color.copy(alpha = 0.12f)
        )
    )
}
