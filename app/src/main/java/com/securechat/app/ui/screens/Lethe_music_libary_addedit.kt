package com.securechat.app.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicAddEditScreen(
    trackId: String?,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditMode = trackId != null

    var artist by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var producer by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf(0) }
    var previewOffsetSec by remember { mutableStateOf("0") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(isEditMode) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val isError = statusMessage?.startsWith("Fehler") == true || statusMessage?.contains("fehlgeschlagen") == true

    LaunchedEffect(trackId) {
        if (trackId != null) {
            isLoading = true
            viewModel.loadMusicLibrary(null) { tracks, error ->
                val track = tracks?.find { it.id == trackId }
                if (track != null) {
                    artist = track.artist
                    title = track.title
                    year = track.year ?: ""
                    lyrics = track.lyrics ?: ""
                    producer = track.producer ?: ""
                    durationSeconds = track.durationSeconds
                    previewOffsetSec = track.previewOffsetSec.toString()
                } else {
                    statusMessage = "Fehler: ${error ?: "Track nicht gefunden"}"
                }
                isLoading = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }?.let { artist = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }?.let { title = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.takeIf { it.isNotBlank() }?.let { year = it }
                val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                durationSeconds = (durationMs / 1000).toInt()
                mmr.release()
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Track bearbeiten" else "Musik hinzufuegen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dateiauswahl (nur beim Hinzufuegen)
            if (!isEditMode) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { filePicker.launch("audio/*") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (selectedUri != null) "Datei ausgewaehlt" else "MP3-Datei auswaehlen",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (durationSeconds > 0) {
                                val min = durationSeconds / 60
                                val sec = durationSeconds % 60
                                Text(
                                    text = "Dauer: $min:${sec.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        if (selectedUri != null) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else if (durationSeconds > 0) {
                // Im Bearbeitungsmodus: Dauer als Readonly-Feld anzeigen
                val min = durationSeconds / 60
                val sec = durationSeconds % 60
                OutlinedTextField(
                    value = "$min:${sec.toString().padStart(2, '0')}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Upload-Dauer") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { Icon(Icons.Default.Timer, contentDescription = null) }
                )
            }

            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = year,
                onValueChange = { year = it },
                label = { Text("Jahr (ID3)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = lyrics,
                onValueChange = { lyrics = it },
                label = { Text("Lyrics (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                minLines = 4,
                maxLines = 12
            )

            OutlinedTextField(
                value = producer,
                onValueChange = { producer = it },
                label = { Text("Producer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = previewOffsetSec,
                onValueChange = { v -> previewOffsetSec = v.filter { it.isDigit() } },
                label = { Text("Vorschau-Zeitstempel (Sekunden)") },
                placeholder = { Text("z. B. 30") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Ab diesem Zeitpunkt beginnt die Vorschau in Status & Sparks") }
            )

            statusMessage?.let { msg ->
                Text(
                    text = msg,
                    color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val offset = previewOffsetSec.toIntOrNull() ?: 0
                    if (isEditMode && trackId != null) {
                        isSaving = true
                        viewModel.updateMusicTrack(
                            trackId = trackId,
                            artist = artist.takeIf { it.isNotBlank() },
                            songTitle = title.takeIf { it.isNotBlank() },
                            year = year.takeIf { it.isNotBlank() },
                            lyrics = lyrics.takeIf { it.isNotBlank() },
                            producer = producer.takeIf { it.isNotBlank() },
                            previewOffsetSec = offset
                        ) { _, error ->
                            isSaving = false
                            if (error == null) onNavigateBack()
                            else statusMessage = "Fehler: $error"
                        }
                    } else {
                        val uri = selectedUri
                        if (uri == null) {
                            statusMessage = "Fehler: Bitte eine Audiodatei auswaehlen"
                            return@Button
                        }
                        isSaving = true
                        viewModel.uploadMusicFile(
                            uri = uri,
                            artist = artist.takeIf { it.isNotBlank() },
                            songTitle = title.takeIf { it.isNotBlank() },
                            year = year.takeIf { it.isNotBlank() },
                            lyrics = lyrics.takeIf { it.isNotBlank() },
                            producer = producer.takeIf { it.isNotBlank() },
                            previewOffsetSec = offset
                        ) { _, error ->
                            isSaving = false
                            if (error == null) onNavigateBack()
                            else statusMessage = "Fehler: $error"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && (isEditMode || selectedUri != null)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isEditMode) "Speichern" else "Einreichen")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
