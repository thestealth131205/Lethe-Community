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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lethe.mediaplayer.data.PlaylistDto

/**
 * Admin-Bereich: verwaltet die globalen Lethe-Playlists, die bei ALLEN Nutzern neben den
 * Lieblingssongs auf dem Home-Bildschirm erscheinen. Der Name wird hier beim Erstellen vergeben,
 * die Titel werden aus der Lethe-Bibliothek ausgewählt. Nur für Admins (Backend prüft is_admin).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPlaylistsScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val state by vm.browse.collectAsState()
    val saving by vm.lethePlaylistSaving.collectAsState()

    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaylistDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (creating) "Neue Lethe-Playlist" else "Lethe-Playlists") },
                navigationIcon = {
                    IconButton(onClick = { if (creating) creating = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        if (creating) {
            CreateLethePlaylistForm(
                modifier = Modifier.padding(padding),
                library = state.library,
                saving = saving,
                onCreate = { name, ids ->
                    vm.createLethePlaylist(name, ids) { creating = false }
                }
            )
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "create") {
                    Card(
                        Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Neue Playlist erstellen", fontWeight = FontWeight.Bold)
                                Text(
                                    "Erscheint bei allen Nutzern neben den Lieblingssongs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { creating = true }) { Text("Erstellen") }
                        }
                    }
                }
                if (state.lethePlaylists.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Es gibt noch keine Lethe-Playlists.",
                            Modifier.fillMaxWidth().padding(top = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(state.lethePlaylists, key = { it.playlistId }) { pl ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pl.playlistName,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (pl.trackCount == 1) "1 Titel" else "${pl.trackCount} Titel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { deleteTarget = pl }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Löschen",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Playlist löschen") },
            text = { Text("Soll die Lethe-Playlist \u201e${target.playlistName}\u201c für alle Nutzer gelöscht werden?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLethePlaylist(target.playlistId)
                    deleteTarget = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun CreateLethePlaylistForm(
    modifier: Modifier = Modifier,
    library: List<com.lethe.mediaplayer.data.Track>,
    saving: Boolean,
    onCreate: (name: String, trackIds: List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name der Playlist") },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        Text(
            "Titel aus der Lethe-Bibliothek auswählen (${selected.size} gewählt)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f)) {
            if (library.isEmpty()) {
                Text(
                    "Die Bibliothek ist leer.",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(library, key = { it.id }) { track ->
                        val checked = track.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selected.remove(track.id) else selected.add(track.id)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            TextButton(
                enabled = !saving && name.isNotBlank() && selected.isNotEmpty(),
                onClick = { onCreate(name.trim(), selected.toList()) }
            ) { Text("Playlist anlegen") }
        }
    }
}
