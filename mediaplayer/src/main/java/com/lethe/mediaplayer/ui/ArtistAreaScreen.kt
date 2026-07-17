package com.lethe.mediaplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Künstler-Bereich: Login/Registrierung per E-Mail + Passwort. Nach dem Login zeigt sich das
 * Dashboard, in dem der Künstler Biografie, Bild und die zugeordneten Titel bearbeiten kann.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAreaScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val session by vm.artistSession.collectAsState()

    if (session != null) {
        ArtistDashboard(vm = vm, onBack = onBack)
    } else {
        ArtistLogin(vm = vm, onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistLogin(vm: PlayerViewModel, onBack: () -> Unit) {
    val loading by vm.artistAuthLoading.collectAsState()
    val error by vm.artistAuthError.collectAsState()
    val registerInfo by vm.artistRegisterInfo.collectAsState()

    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.clearArtistAuthError(); vm.clearArtistRegisterInfo() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (registerMode) "Künstler-Registrierung" else "Künstler-Login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (registerMode)
                    "Erstelle einen Künstler-Account. Nach der Registrierung muss ein Admin ihn freigeben."
                else
                    "Melde dich mit deinem Künstler-Account an.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (registerMode) {
                OutlinedTextField(
                    value = artistName,
                    onValueChange = { artistName = it },
                    label = { Text("Künstlername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-Mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            registerInfo?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    if (registerMode) vm.artistRegister(email, password, artistName)
                    else vm.artistLogin(email, password)
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank() &&
                    (!registerMode || artistName.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (registerMode) "Registrieren" else "Anmelden")
                }
            }
            TextButton(
                onClick = {
                    registerMode = !registerMode
                    vm.clearArtistAuthError()
                    vm.clearArtistRegisterInfo()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (registerMode) "Ich habe schon einen Account – anmelden" else "Neuen Künstler-Account erstellen")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistDashboard(vm: PlayerViewModel, onBack: () -> Unit) {
    val session by vm.artistSession.collectAsState()
    val browse by vm.browse.collectAsState()
    val artist = session ?: return

    var bio by remember(artist.id) { mutableStateOf(artist.artistBio ?: "") }
    var name by remember(artist.id) { mutableStateOf(artist.artistName) }
    // Ausgewählte Song-IDs (aus den Lethe-Library-Titeln).
    val selectedIds = remember(artist.id) { mutableStateListOf<String>().apply { addAll(artist.songIds) } }

    val picturePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.uploadArtistPicture(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mein Künstler-Profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.artistLogout() }) { Text("Abmelden") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtistAvatar(name = name, imageUrl = artist.artistPicture, size = 120)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { picturePicker.launch(arrayOf("image/*")) }) {
                        Text("Bild ändern")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Künstlername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Biografie") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    "Meine Titel (aus der Lethe-Bibliothek)",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (browse.library.isEmpty()) {
                item {
                    Text(
                        "Es sind noch keine Bibliothekstitel verfügbar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(browse.library, key = { it.id }) { track ->
                    val checked = selectedIds.contains(track.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selectedIds.remove(track.id) else selectedIds.add(track.id)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Icon(
                            if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { vm.saveArtistProfile(name, bio, selectedIds.toList()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}
