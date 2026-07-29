@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.securechat.app.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.securechat.app.data.local.AudioOverlay
import com.securechat.app.data.local.ColorAdjustments
import com.securechat.app.data.local.VideoTranscoder
import com.securechat.app.data.network.LetheMusicTrack
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.viewmodel.MusicSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// ChatVideoEditorScreen
//
// Video-Editor für den Chat-Versand.
// Features: Zuschneiden (Trim) + Seitenverhältnis-Crop (Crop).
// ─────────────────────────────────────────────────────────────────────────────

private enum class ChatCropAspect(val label: String, val ratio: Float?) {
    FREE("Frei", null),
    PORTRAIT_916("9:16", 9f / 16f),
    LANDSCAPE_169("16:9", 16f / 9f),
    SQUARE_11("1:1", 1f),
    LANDSCAPE_43("4:3", 4f / 3f),
    PORTRAIT_34("3:4", 3f / 4f),
}

/**
 * Ausgabe-Auflösungsstufe (bezogen auf die kurze Kante des Videos).
 * Es wird NIE über die Quellauflösung hochskaliert – daher gilt die Stufe als
 * Obergrenze; die Standardstufe richtet sich nach dem Videomaterial.
 */
private enum class ExportResolution(val label: String, val shortSide: Int) {
    HD("HD", 1080),
    UHD("4K", 2160)
}

private enum class VideoTransitionType(val label: String) {
    FADE("Fading"),
    DITHER("Dither"),
    SLIDE_RIGHT("Von rechts rein")
}

private data class VideoMediaClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val durationMs: Long = 0L,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    // true = Standbild (wird als Video-Clip fester Länge gerendert)
    val isImage: Boolean = false
)

/** Standard-Anzeigedauer eines Bildes, das als Video-Clip verwendet wird. */
private const val IMAGE_CLIP_DEFAULT_MS = 4000L

/** Maximal einstellbare Anzeigedauer eines Bild-Clips (Obergrenze der Trim-Griffe). */
private const val IMAGE_CLIP_MAX_MS = 30000L

/** Repräsentiert einen Audio-Clip der auf die Video-Spur gelegt wird. */
private data class VideoAudioClip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String?,
    val streamUrl: String,
    val totalDurationMs: Long,
    val startOffsetMs: Long = 0L,
    val clipDurationMs: Long,
    // Position auf der Video-Timeline (ab wann der Song im Video zu hören ist)
    val timelineStartMs: Long = 0L
)

private fun formatVideoMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val decimals = (ms % 1000) / 100
    return "%d:%02d.%d".format(min, sec, decimals)
}

/** Kleiner Chip zur Auswahl der Ausgabe-Auflösung (HD / 4K) in der Editor-Kopfzeile. */
@Composable
private fun ResolutionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        !enabled -> Color.White.copy(0.08f)
        selected -> Color(0xFFFFD700)
        else -> Color.White.copy(0.15f)
    }
    val fg = when {
        !enabled -> Color.White.copy(0.3f)
        selected -> Color.Black
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/**
 * Eine Zeile im „Anpassen"-Popup: Beschriftung + Wert (in %), darunter ein Schieber,
 * dessen Mitte 0 (neutral) ist und der nach + und − verstellt werden kann (−1f..+1f).
 */
@Composable
private fun AdjustSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${(value * 100).roundToInt()}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatVideoEditorScreen(
    videoUri: Uri?,
    chatId: String,
    isGroup: Boolean,
    viewModel: MainViewModel,
    onCancel: () -> Unit,
    musicViewModel: MusicSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Portrait-Lock während Editor offen
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ─── Primär-Clip ──────────────────────────────────────────────────────────
    // Erster Clip der Videospur. Kann ein Video ODER (beim leeren Start aus dem
    // Anhang-Menü) ein Bild sein. Ist beim Start noch keiner gesetzt (videoUri == null),
    // wird das ERSTE über das „+" hinzugefügte Medium zum Primär-Clip.
    var primaryUri by remember { mutableStateOf(videoUri) }
    var primaryIsImage by remember { mutableStateOf(false) }

    // ─── Video-Metadaten ─────────────────────────────────────────────────────
    var videoWidthPx by remember { mutableIntStateOf(0) }
    var videoHeightPx by remember { mutableIntStateOf(0) }

    // ─── Trim-State (früh deklariert, damit Bild-Primär die Dauer setzen kann) ──
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(primaryUri, primaryIsImage) {
        val uri = primaryUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            if (primaryIsImage) {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    }
                    videoWidthPx = opts.outWidth.coerceAtLeast(0)
                    videoHeightPx = opts.outHeight.coerceAtLeast(0)
                } catch (_: Exception) { }
                if (videoDurationMs == 0L) {
                    // Bild-Clip: videoDurationMs ist die MAX einstellbare Länge (Obergrenze der
                    // Trim-Griffe). Der ausgewählte Ausschnitt (trimStart..trimEnd) bestimmt die
                    // tatsächliche Anzeigedauer; Start bei 4s Standard, frei per Griffe änderbar.
                    videoDurationMs = IMAGE_CLIP_MAX_MS
                    trimStartMs = 0L
                    trimEndMs = IMAGE_CLIP_DEFAULT_MS
                }
            } else {
                val ret = MediaMetadataRetriever()
                try {
                    ret.setDataSource(context, uri)
                    videoWidthPx = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    videoHeightPx = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                } catch (_: Exception) {
                } finally {
                    ret.release()
                }
            }
        }
    }

    // ─── Auflösung-State ──────────────────────────────────────────────────────
    // Standard richtet sich nach dem Videomaterial. Die eigentliche Fähigkeits-
    // Logik (Auto-Wahl, 4K-Verfügbarkeit, Zielhöhe) wird weiter unten definiert,
    // sobald auch die Extra-Clips der Videospur bekannt sind – sie bemisst sich
    // am BESTEN Clip der Spur, nicht nur am ersten Video.
    var selectedResolution by remember { mutableStateOf(ExportResolution.HD) }
    var resolutionUserPicked by remember { mutableStateOf(false) }

    // ─── Trim-State (videoDurationMs/trimStartMs/trimEndMs siehe oben) ─────────
    var playerPositionMs by remember { mutableLongStateOf(0L) }
    var isTrimDragging by remember { mutableStateOf(false) }

    // ─── Crop-State ───────────────────────────────────────────────────────────
    var selectedCropAspect by remember { mutableStateOf(ChatCropAspect.FREE) }
    var cropScale by remember { mutableFloatStateOf(1f) }
    var cropOffsetX by remember { mutableFloatStateOf(0f) }
    var cropOffsetY by remember { mutableFloatStateOf(0f) }
    var showCropControls by remember { mutableStateOf(false) }
    LaunchedEffect(selectedCropAspect) {
        cropScale = 1f; cropOffsetX = 0f; cropOffsetY = 0f
    }

    // ─── Audio / Mute State ───────────────────────────────────────────────────
    var isMuted by remember { mutableStateOf(false) }
    var videoTrackVolume by remember { mutableIntStateOf(100) }
    var musicTrackVolume by remember { mutableIntStateOf(100) }
    var showVolumePopup by remember { mutableStateOf(false) }
    var audioClips by remember { mutableStateOf<List<VideoAudioClip>>(emptyList()) }
    var showMusicPicker by remember { mutableStateOf(false) }

    // ─── Farb-/Ton-Anpassung ──────────────────────────────────────────────────
    var colorAdjustments by remember { mutableStateOf(ColorAdjustments()) }
    var showAdjustPopup by remember { mutableStateOf(false) }

    // ─── Vorschau-Alle-Spuren ─────────────────────────────────────────────────
    var previewAllTracks by remember { mutableStateOf(false) }

    // ─── Multi-Clip Video-Spur ─────────────────────────────────────────────────
    var extraVideoClips by remember { mutableStateOf<List<VideoMediaClip>>(emptyList()) }
    var videoTransitions by remember { mutableStateOf<List<VideoTransitionType?>>(emptyList()) }
    var showTransitionPickerForIndex by remember { mutableStateOf<Int?>(null) }

    var showAddMediaChooser by remember { mutableStateOf(false) }

    // Fügt der Videospur ein Medium hinzu. Ist noch kein Primär-Clip vorhanden
    // (leerer Start), wird es zum Primär-Clip; sonst als Extra-Clip angehängt.
    fun addMediaClip(uri: Uri, isImage: Boolean) {
        scope.launch(Dispatchers.IO) {
            var dur = IMAGE_CLIP_DEFAULT_MS
            var w = 0
            var h = 0
            if (isImage) {
                try {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opts)
                    }
                    w = opts.outWidth.coerceAtLeast(0); h = opts.outHeight.coerceAtLeast(0)
                } catch (_: Exception) { }
            } else {
                try {
                    val ret = MediaMetadataRetriever()
                    ret.setDataSource(context, uri)
                    dur = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    w = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = ret.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    ret.release()
                } catch (_: Exception) { }
            }
            withContext(Dispatchers.Main) {
                if (primaryUri == null) {
                    primaryIsImage = isImage
                    primaryUri = uri
                } else {
                    extraVideoClips = extraVideoClips + VideoMediaClip(
                        uri = uri, durationMs = dur, widthPx = w, heightPx = h, isImage = isImage
                    )
                    videoTransitions = videoTransitions + null
                }
            }
        }
    }

    val addVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) addMediaClip(uri, isImage = false)
    }
    val addImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) addMediaClip(uri, isImage = true)
    }

    // ─── Auflösungs-Fähigkeit (bezogen auf die GESAMTE Videospur) ──────────────
    // Alle Clips (erstes Video + Extra-Clips) werden per Presentation.createForHeight
    // auf eine gemeinsame Höhe skaliert. Ein einzelner niedrig aufgelöster Clip wird
    // dabei hochskaliert – er darf die erreichbare Ausgabe-Auflösung NICHT deckeln.
    // Deshalb bemisst sich die Fähigkeit am BESTEN Clip (größte kurze Kante) und HD
    // ist immer garantiert möglich.
    val bestShortSide = remember(videoWidthPx, videoHeightPx, extraVideoClips) {
        var best = 0
        if (videoWidthPx > 0 && videoHeightPx > 0) best = minOf(videoWidthPx, videoHeightPx)
        extraVideoClips.forEach { c ->
            if (c.widthPx > 0 && c.heightPx > 0) best = maxOf(best, minOf(c.widthPx, c.heightPx))
        }
        best
    }
    // 4K nur anbieten, wenn mindestens EIN Clip der Spur eine kurze Kante > 1080 px hat
    val uhdAvailable = bestShortSide > ExportResolution.HD.shortSide
    LaunchedEffect(bestShortSide) {
        if (!resolutionUserPicked && bestShortSide > 0) {
            selectedResolution = if (bestShortSide > ExportResolution.HD.shortSide)
                ExportResolution.UHD else ExportResolution.HD
        }
    }
    // Ausgabehöhe für die gewählte Stufe. Die kurze Kante der Ausgabe wird auf die
    // Stufe gesetzt, aber nie über das hinaus, was der beste Clip liefert – mit HD
    // als garantierter Untergrenze (ein einzelner schwächerer Clip wird hochskaliert
    // statt die ganze Spur zu deckeln). Höhe proportional zum ersten Video (bestimmt
    // Orientierung/Seitenverhältnis der Ausgabe).
    fun computeTargetHeight(): Int {
        if (videoWidthPx <= 0 || videoHeightPx <= 0) return selectedResolution.shortSide
        val firstShort = minOf(videoWidthPx, videoHeightPx)
        // Obergrenze: bester verfügbarer Clip, aber mindestens HD zulassen
        val cap = maxOf(bestShortSide, ExportResolution.HD.shortSide)
        val targetShort = minOf(selectedResolution.shortSide, cap)
        val h = (videoHeightPx.toLong() * targetShort / firstShort).toInt().coerceAtLeast(2)
        return if (h % 2 != 0) h + 1 else h  // Encoder verlangen gerade Abmessungen
    }

    // ─── Processing / Save ────────────────────────────────────────────────────
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingLabel by remember { mutableStateOf("") }
    var processingError by remember { mutableStateOf<String?>(null) }
    var saveSuccess by remember { mutableStateOf(false) }

    // ─── Video-Container-Größe für Crop-Overlay ───────────────────────────────
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }

    // ─── ExoPlayer ───────────────────────────────────────────────────────────
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            // Effekt-Pipeline MUSS mindestens einmal vor dem ersten prepare()
            // aktiviert werden (Media3-Anforderung), sonst greifen spätere
            // setVideoEffects-Aufrufe (Live-Farbanpassung) nicht.
            setVideoEffects(emptyList())
            videoUri?.let { setMediaItem(MediaItem.fromUri(it)); prepare() }
        }
    }

    // ─── Musik-Vorschau-Player ────────────────────────────────────────────────
    val musicPreviewPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && videoDurationMs == 0L) {
                    val dur = player.duration.takeIf { it > 0 } ?: 0L
                    videoDurationMs = dur
                    trimStartMs = 0L
                    trimEndMs = dur
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Musik-Vorschau wird im Poll-Loop je Timeline-Position gesteuert;
                // beim Anhalten des Videos direkt mit pausieren.
                if (!isPlaying) musicPreviewPlayer.pause()
            }
        })
        while (true) {
            if (!isTrimDragging) {
                playerPositionMs = if (previewAllTracks) {
                    val idx = player.currentMediaItemIndex
                    val completedMs = if (idx == 0) 0L else {
                        (trimEndMs - trimStartMs) + extraVideoClips.take(idx - 1).sumOf { it.durationMs }
                    }
                    completedMs + player.currentPosition
                } else {
                    player.currentPosition
                }
            }
            if (trimEndMs > 0L && !previewAllTracks && player.currentPosition > trimEndMs + 500L) {
                player.seekTo(trimStartMs)
            }
            // Musik-Vorschau an die Timeline-Position des Clips koppeln
            val syncClip = audioClips.firstOrNull()
            if (syncClip != null) {
                // playerPositionMs ist bei !previewAllTracks relativ zum getrimmten Video-Start
                val timelinePos = if (previewAllTracks) playerPositionMs else (player.currentPosition - trimStartMs)
                val songElapsed = timelinePos - syncClip.timelineStartMs
                val inWindow = songElapsed in 0L until syncClip.clipDurationMs
                if (inWindow && player.isPlaying) {
                    if (abs(musicPreviewPlayer.currentPosition - songElapsed) > 300L) {
                        musicPreviewPlayer.seekTo(songElapsed)
                    }
                    if (!musicPreviewPlayer.isPlaying) musicPreviewPlayer.play()
                } else {
                    if (musicPreviewPlayer.isPlaying) musicPreviewPlayer.pause()
                    if (songElapsed < 0L && musicPreviewPlayer.currentPosition > 50L) musicPreviewPlayer.seekTo(0L)
                }
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        player.play()
        onDispose { player.release() }
    }

    DisposableEffect(musicPreviewPlayer) {
        onDispose { musicPreviewPlayer.release() }
    }

    // Vorschau-Lautstärke (Video) mit Mute-Button und Lautstärke-Schieber synchronisieren
    LaunchedEffect(isMuted, videoTrackVolume) {
        player.volume = if (isMuted) 0f else videoTrackVolume / 100f
    }

    // Musik-Vorschau-Lautstärke aktualisieren
    LaunchedEffect(musicTrackVolume) {
        musicPreviewPlayer.volume = musicTrackVolume / 100f
    }

    // Farb-/Ton-Anpassung live in der Vorschau anwenden (leer = neutral).
    LaunchedEffect(colorAdjustments) {
        try {
            val wasPlaying = player.playWhenReady
            player.setVideoEffects(
                listOfNotNull(VideoTranscoder.colorAdjustEffect(colorAdjustments))
            )
            // Media3: setVideoEffects hält die Effekt-Pipeline an und rendert danach
            // keine neuen Frames mehr. Ein Seek auf die aktuelle Position stößt das
            // Rendering wieder an; playWhenReady stellt die laufende Wiedergabe wieder her.
            player.seekTo(player.currentPosition)
            player.playWhenReady = wasPlaying
        } catch (_: Exception) { }
    }

    // Musik-Vorschau-Player nur neu aufsetzen, wenn sich der Song oder sein
    // Ausschnitt (Start/Dauer) ändert – NICHT beim Verschieben auf der Timeline.
    val previewClip = audioClips.firstOrNull()
    LaunchedEffect(previewClip?.streamUrl, previewClip?.startOffsetMs, previewClip?.clipDurationMs) {
        if (previewClip != null) {
            val item = MediaItem.Builder()
                .setUri(previewClip.streamUrl)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().apply {
                        if (previewClip.startOffsetMs > 0L) setStartPositionMs(previewClip.startOffsetMs)
                        if (previewClip.clipDurationMs > 0L) setEndPositionMs(previewClip.startOffsetMs + previewClip.clipDurationMs)
                    }.build()
                ).build()
            musicPreviewPlayer.setMediaItem(item)
            musicPreviewPlayer.volume = musicTrackVolume / 100f
            musicPreviewPlayer.prepare()
            // Wiedergabe/Anhalten übernimmt der Poll-Loop je nach Timeline-Position.
        } else {
            musicPreviewPlayer.stop()
            musicPreviewPlayer.clearMediaItems()
        }
    }

    // Baut ein MediaItem für die Vorschau (Bilder erhalten eine feste Anzeigedauer).
    fun previewMediaItem(uri: Uri, isImage: Boolean, durMs: Long): MediaItem =
        if (isImage) MediaItem.Builder()
            .setUri(uri)
            .setImageDurationMs(if (durMs > 0L) durMs else IMAGE_CLIP_DEFAULT_MS)
            .build()
        else MediaItem.fromUri(uri)

    // Vorschau-Alle-Spuren: Player auf alle Clips umschalten / zurücksetzen.
    // Reagiert auch auf einen neu gesetzten Primär-Clip (leerer Start).
    LaunchedEffect(previewAllTracks, primaryUri, primaryIsImage) {
        val pUri = primaryUri ?: return@LaunchedEffect
        if (previewAllTracks) {
            val allItems = buildList {
                val needsTrim = !primaryIsImage && (trimStartMs > 0L || (trimEndMs > 0L && trimEndMs < videoDurationMs))
                val primaryItem = if (needsTrim) {
                    MediaItem.Builder()
                        .setUri(pUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(trimStartMs)
                                .apply { if (trimEndMs > 0L) setEndPositionMs(trimEndMs) }
                                .build()
                        ).build()
                } else {
                    // Bild-Primär: Vorschaudauer = getrimmter Ausschnitt (frei per Griffe wählbar)
                    val imgDur = (trimEndMs - trimStartMs).coerceAtLeast(1_000L)
                    previewMediaItem(pUri, primaryIsImage, if (primaryIsImage) imgDur else videoDurationMs)
                }
                add(primaryItem)
                extraVideoClips.forEach { clip -> add(previewMediaItem(clip.uri, clip.isImage, clip.durationMs)) }
            }
            player.setMediaItems(allItems)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.seekTo(0, 0)
            player.prepare()
            player.play()
        } else {
            val imgDur = (trimEndMs - trimStartMs).coerceAtLeast(1_000L)
            player.setMediaItem(previewMediaItem(pUri, primaryIsImage, if (primaryIsImage) imgDur else videoDurationMs))
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.seekTo(trimStartMs)
            player.prepare()
            player.play()
        }
    }

    // ─── Helper: Video verarbeiten ────────────────────────────────────────────
    suspend fun processVideo(outFile: File, onProgress: (Float) -> Unit): Boolean {
        val pUri = primaryUri ?: return false
        // Bilder haben keinen Trim.
        val needsTrim = !primaryIsImage && (trimStartMs > 0L || (trimEndMs > 0L && trimEndMs < videoDurationMs))
        val cropRatio = selectedCropAspect.ratio
        val cropRect: FloatArray? = if (cropRatio != null && videoWidthPx > 0 && videoHeightPx > 0
            && containerWidthPx > 0 && containerHeightPx > 0
        ) {
            val g = computeCropGeom(
                containerWidthPx.toFloat(), containerHeightPx.toFloat(),
                videoWidthPx.toFloat(), videoHeightPx.toFloat(), cropRatio
            )
            deriveCropRect(g, cropScale, cropOffsetX, cropOffsetY)
        } else null

        val overlayUrls = audioClips.map {
            AudioOverlay(it.streamUrl, it.startOffsetMs, it.clipDurationMs, it.timelineStartMs)
        }
        val effectiveMute = isMuted || videoTrackVolume == 0
        val videoVol = if (effectiveMute) 0f else videoTrackVolume / 100f
        val musicVol = musicTrackVolume / 100f
        val colorAdj = if (colorAdjustments.isIdentity) null else colorAdjustments

        // Bild-Primär oder Extra-Clips → Verkettungs-Pfad (unterstützt Standbilder).
        return if (extraVideoClips.isNotEmpty() || primaryIsImage) {
            // Effektive Dauer des Primär-Clips (Bild: feste Anzeigedauer; Video: getrimmt)
            val primaryEffectiveDur = if (primaryIsImage) {
                // Bild: Anzeigedauer = per Trim-Griffe gewählter Ausschnitt (Standard 4s)
                (trimEndMs - trimStartMs).takeIf { it > 0L } ?: IMAGE_CLIP_DEFAULT_MS
            } else run {
                val end = if (needsTrim && trimEndMs > 0L && trimEndMs < videoDurationMs) trimEndMs else videoDurationMs
                val start = if (needsTrim) trimStartMs else 0L
                (end - start).coerceAtLeast(0L)
            }
            // Clip-Dauern in Reihenfolge [primär, extra0, extra1, …]
            val clipDurations = listOf(primaryEffectiveDur) + extraVideoClips.map {
                if (it.isImage && it.durationMs <= 0L) IMAGE_CLIP_DEFAULT_MS else it.durationMs
            }
            // Übergangsdauer je Grenze (Grenze b = zwischen Clip b und b+1). videoTransitions[i]
            // liegt vor extraVideoClips[i] = Grenze i. Fade aktiv = 1200ms (0 = kein Übergang).
            val transitionFades = extraVideoClips.indices.map { i ->
                if (videoTransitions.getOrNull(i) != null) 1200L else 0L
            }
            VideoTranscoder.transcodeConcat(
                context = context,
                primaryUri = pUri,
                primaryIsImage = primaryIsImage,
                primaryTrimStartMs = if (needsTrim) trimStartMs else 0L,
                primaryTrimEndMs = if (needsTrim && trimEndMs > 0L && trimEndMs < videoDurationMs) trimEndMs else 0L,
                cropRect = cropRect,
                videoWidthPx = videoWidthPx,
                videoHeightPx = videoHeightPx,
                muteAudio = effectiveMute,
                videoAudioVolume = videoVol,
                audioOverlayUrls = overlayUrls,
                musicVolume = musicVol,
                extraClipUris = extraVideoClips.map { it.uri },
                extraClipIsImage = extraVideoClips.map { it.isImage },
                clipDurationsMs = clipDurations,
                transitionFadeMs = transitionFades,
                targetHeight = computeTargetHeight(),
                colorAdjustments = colorAdj,
                outputFile = outFile,
                onProgress = onProgress
            )
        } else if (needsTrim || cropRect != null || effectiveMute || videoTrackVolume != 100 || overlayUrls.isNotEmpty() || musicTrackVolume != 100 || colorAdj != null) {
            VideoTranscoder.transcodeWithEdit(
                context = context,
                inputUri = pUri,
                outputFile = outFile,
                trimStartMs = if (needsTrim) trimStartMs else 0L,
                trimEndMs = if (needsTrim && trimEndMs > 0L && trimEndMs < videoDurationMs) trimEndMs else 0L,
                cropRect = cropRect,
                videoWidthPx = videoWidthPx,
                videoHeightPx = videoHeightPx,
                muteAudio = effectiveMute,
                videoAudioVolume = videoVol,
                audioOverlayUrls = overlayUrls,
                musicVolume = musicVol,
                targetHeight = computeTargetHeight(),
                colorAdjustments = colorAdj,
                onProgress = onProgress
            )
        } else {
            VideoTranscoder.transcode(context, pUri, outFile, computeTargetHeight(), colorAdj, onProgress)
        }
    }

    // ─── Senden-Handler ───────────────────────────────────────────────────────
    fun onSend() {
        if (isProcessing) return
        scope.launch {
            isProcessing = true
            processingLabel = "Senden"
            processingError = null
            val outFile = File(context.cacheDir, "chat_video_edit_${System.currentTimeMillis()}.mp4")
            val success = processVideo(outFile) { processingProgress = it }
            if (success && outFile.exists()) {
                val outUri = Uri.fromFile(outFile)
                if (isGroup) viewModel.sendGroupMediaMessage(chatId, outUri, "video")
                else viewModel.sendMediaMessage(chatId, outUri, "video")
                onCancel()
            } else {
                processingError = "Video konnte nicht verarbeitet werden."
                isProcessing = false
            }
        }
    }

    // ─── Speichern-Handler (in Galerie) ──────────────────────────────────────
    fun onSave() {
        if (isProcessing) return
        scope.launch {
            isProcessing = true
            processingLabel = "Speichern"
            processingError = null
            val tmpFile = File(context.cacheDir, "chat_video_save_${System.currentTimeMillis()}.mp4")
            val success = processVideo(tmpFile) { processingProgress = it }
            if (success && tmpFile.exists()) {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, "Lethe_${System.currentTimeMillis()}.mp4")
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Lethe")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                tmpFile.inputStream().use { it.copyTo(out) }
                            }
                            values.clear()
                            values.put(MediaStore.Video.Media.IS_PENDING, 0)
                            context.contentResolver.update(uri, values, null, null)
                            true
                        } else false
                    } catch (_: Exception) { false }
                }
                tmpFile.delete()
                isProcessing = false
                if (saved) saveSuccess = true
                else processingError = "Speichern fehlgeschlagen."
            } else {
                processingError = "Video konnte nicht verarbeitet werden."
                isProcessing = false
            }
        }
    }

    // ─── UI ───────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        Column(modifier = Modifier.fillMaxSize()) {

            // Top-Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!isProcessing) onCancel() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                }
                // Auflösungs-Wahl (oben links): HD / 4K. Höchstmögliche Stufe je nach
                // bestem Clip der Videospur vorgewählt; HD immer möglich, 4K nur wenn
                // mindestens ein Clip es hergibt.
                ResolutionChip(
                    label = ExportResolution.HD.label,
                    selected = selectedResolution == ExportResolution.HD,
                    enabled = true,
                    onClick = {
                        if (!isProcessing) { selectedResolution = ExportResolution.HD; resolutionUserPicked = true }
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                ResolutionChip(
                    label = ExportResolution.UHD.label,
                    selected = selectedResolution == ExportResolution.UHD,
                    enabled = uhdAvailable,
                    onClick = {
                        if (!isProcessing && uhdAvailable) { selectedResolution = ExportResolution.UHD; resolutionUserPicked = true }
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                // Anpassen (Farbe/Ton)
                IconButton(onClick = { if (!isProcessing) showAdjustPopup = true }) {
                    Icon(
                        Icons.Default.Tune,
                        "Anpassen",
                        tint = if (!colorAdjustments.isIdentity) Color(0xFFFFD700) else Color.White.copy(0.7f)
                    )
                }
                // Zuschneiden-Toggle
                IconButton(onClick = { showCropControls = !showCropControls }) {
                    Icon(
                        Icons.Default.ContentCut,
                        "Zuschneiden",
                        tint = if (showCropControls) Color(0xFFFFD700) else Color.White.copy(0.7f)
                    )
                }
                // Speichern
                val hasClip = primaryUri != null
                IconButton(onClick = { if (!isProcessing && hasClip) onSave() }) {
                    Icon(Icons.Default.Save, "Speichern", tint = Color.White.copy(if (hasClip) 0.8f else 0.3f))
                }
                // Senden
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isProcessing && hasClip) Color(0xFFFFD700) else Color.Gray)
                        .clickable(enabled = !isProcessing && hasClip) { onSend() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Senden", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Senden", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Video-Preview mit Crop-Overlay
            val cropActive = (selectedCropAspect.ratio != null &&
                videoWidthPx > 0 && videoHeightPx > 0 &&
                containerWidthPx > 0 && containerHeightPx > 0)
            val cropGeom = remember(
                selectedCropAspect, videoWidthPx, videoHeightPx, containerWidthPx, containerHeightPx
            ) {
                if (!cropActive) null else computeCropGeom(
                    containerWidthPx.toFloat(), containerHeightPx.toFloat(),
                    videoWidthPx.toFloat(), videoHeightPx.toFloat(), selectedCropAspect.ratio!!
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .onSizeChanged {
                        containerWidthPx = it.width
                        containerHeightPx = it.height
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(cropActive) {
                            // Tippen auf das laufende Vorschau-Video hält es an / startet es
                            // erneut. Im Zuschneide-Modus übernimmt das Overlay die Gesten.
                            if (!cropActive) {
                                detectTapGestures {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        }
                        .graphicsLayer {
                        if (cropActive) {
                            scaleX = cropScale; scaleY = cropScale
                            translationX = cropOffsetX; translationY = cropOffsetY
                        }
                    }
                )
                if (cropActive && cropGeom != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) { drawCropFrame(cropGeom) }
                    Box(
                        modifier = Modifier.fillMaxSize().pointerInput(cropGeom) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (cropScale * zoom).coerceIn(1f, 6f)
                                cropScale = newScale
                                val maxOffX = (newScale * cropGeom.displayW / 2f - cropGeom.frameHalfW).coerceAtLeast(0f)
                                val maxOffY = (newScale * cropGeom.displayH / 2f - cropGeom.frameHalfH).coerceAtLeast(0f)
                                cropOffsetX = (cropOffsetX + pan.x).coerceIn(-maxOffX, maxOffX)
                                cropOffsetY = (cropOffsetY + pan.y).coerceIn(-maxOffY, maxOffY)
                            }
                        }
                    )
                }
            }

            // Zuschneiden-Bereich (kollapsibel)
            if (showCropControls) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (selectedCropAspect == ChatCropAspect.FREE) "Seitenverhältnis"
                        else "Seitenverhältnis · Bild im Rahmen ziehen & zoomen",
                        color = Color.White.copy(0.7f), fontSize = 11.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChatCropAspect.entries.forEach { aspect ->
                            val sel = selectedCropAspect == aspect
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) Color(0xFFFFD700) else Color(0xFF2A2A2A))
                                    .clickable { selectedCropAspect = aspect }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(aspect.label, color = if (sel) Color.Black else Color.White, fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(0.05f))
            }

            // ─── Vorschau-Toggle ──────────────────────────────────────────────
            val previewAccent = if (previewAllTracks) MaterialTheme.colorScheme.primary else Color.White.copy(0.4f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Vorschau",
                    color = previewAccent,
                    fontSize = 12.sp,
                    fontWeight = if (previewAllTracks) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { previewAllTracks = !previewAllTracks }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // ─── Zwei-Spur-Ansicht ────────────────────────────────────────────
            VideoEditorTracksSection(
                videoDurationMs = videoDurationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                isMuted = isMuted,
                onMuteToggle = { isMuted = !isMuted },
                videoTrackVolume = videoTrackVolume,
                musicTrackVolume = musicTrackVolume,
                onVolumeIconClick = { showVolumePopup = true },
                currentPositionMs = playerPositionMs,
                audioClips = audioClips,
                extraVideoClips = extraVideoClips,
                videoTransitions = videoTransitions,
                onTransitionClick = { idx -> showTransitionPickerForIndex = idx },
                onVideoTrimChange = { s, e ->
                    trimStartMs = s; trimEndMs = e; player.seekTo(s)
                },
                onVideoTrimDragStart = { isTrimDragging = true; player.pause() },
                onVideoTrimDragEnd = { isTrimDragging = false; player.play() },
                onAudioClipUpdate = { idx, timelineStart, startOff, clipDur ->
                    audioClips = audioClips.toMutableList().also { list ->
                        list[idx] = list[idx].copy(
                            timelineStartMs = timelineStart,
                            startOffsetMs = startOff,
                            clipDurationMs = clipDur
                        )
                    }
                },
                hasPrimary = primaryUri != null,
                onAddMusicClick = { showMusicPicker = true },
                onAddVideoClick = { showAddMediaChooser = true },
                onSeekTo = { ms ->
                    if (previewAllTracks) {
                        val trimmedPrimaryDur = trimEndMs - trimStartMs
                        if (ms <= trimmedPrimaryDur) {
                            player.seekTo(0, ms)
                        } else {
                            var remaining = ms - trimmedPrimaryDur
                            var itemIdx = 1
                            for (clip in extraVideoClips) {
                                if (remaining <= clip.durationMs) {
                                    player.seekTo(itemIdx, remaining)
                                    break
                                }
                                remaining -= clip.durationMs
                                itemIdx++
                            }
                        }
                    } else {
                        player.seekTo(ms.coerceIn(0L, videoDurationMs))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Processing-Overlay
        if (isProcessing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Video wird ${processingLabel.lowercase()}… ${(processingProgress * 100).roundToInt()}%",
                        color = Color.White, fontSize = 14.sp
                    )
                }
            }
        }

        // Erfolgs-Toast: Gespeichert
        if (saveSuccess) {
            LaunchedEffect(saveSuccess) { delay(2500); saveSuccess = false }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF388E3C))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("In Galerie gespeichert (Videos/Lethe)", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // Fehler-Toast
        processingError?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFB71C1C))
                    .padding(12.dp)
            ) {
                Text(err, color = Color.White, fontSize = 13.sp)
            }
        }
    }

    // ─── Medium-hinzufügen-Auswahl (Video oder Bild) ──────────────────────────
    if (showAddMediaChooser) {
        AlertDialog(
            onDismissRequest = { showAddMediaChooser = false },
            confirmButton = {
                TextButton(onClick = { showAddMediaChooser = false; addVideoLauncher.launch("video/*") }) {
                    Text("Video")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMediaChooser = false; addImageLauncher.launch("image/*") }) {
                    Text("Bild")
                }
            },
            title = { Text("Medium hinzufügen") },
            text = { Text("Video oder Bild zur Videospur hinzufügen. Aus Bildern lässt sich ein Video erstellen.") }
        )
    }

    // ─── Übergangs-Picker ────────────────────────────────────────────────────
    showTransitionPickerForIndex?.let { gapIdx ->
        TransitionPickerDialog(
            currentTransition = videoTransitions.getOrNull(gapIdx),
            onSelect = { type ->
                videoTransitions = videoTransitions.toMutableList().also { list ->
                    if (gapIdx < list.size) list[gapIdx] = type
                }
                showTransitionPickerForIndex = null
            },
            onRemove = {
                videoTransitions = videoTransitions.toMutableList().also { list ->
                    if (gapIdx < list.size) list[gapIdx] = null
                }
                showTransitionPickerForIndex = null
            },
            onDismiss = { showTransitionPickerForIndex = null }
        )
    }

    // ─── Musik-Picker ─────────────────────────────────────────────────────────
    if (showMusicPicker) {
        VideoEditorMusicPickerSheet(
            musicViewModel = musicViewModel,
            videoDurationMs = videoDurationMs,
            onSelect = { clip ->
                audioClips = audioClips + clip
                showMusicPicker = false
            },
            onDismiss = { showMusicPicker = false }
        )
    }

    // ─── Lautstärke-Popup ─────────────────────────────────────────────────────
    if (showVolumePopup) {
        VideoEditorVolumeDialog(
            videoVolume = videoTrackVolume,
            musicVolume = musicTrackVolume,
            onVideoVolumeChange = { videoTrackVolume = it },
            onMusicVolumeChange = { musicTrackVolume = it },
            onDismiss = { showVolumePopup = false }
        )
    }

    // ─── Anpassen-Popup (Farbe/Ton) ───────────────────────────────────────────
    if (showAdjustPopup) {
        AlertDialog(
            onDismissRequest = { showAdjustPopup = false },
            confirmButton = {
                TextButton(onClick = { showAdjustPopup = false }) { Text("Fertig") }
            },
            dismissButton = {
                TextButton(onClick = { colorAdjustments = ColorAdjustments() }) { Text("Zurücksetzen") }
            },
            title = { Text("Anpassen") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AdjustSliderRow("Helligkeit", colorAdjustments.brightness) {
                        colorAdjustments = colorAdjustments.copy(brightness = it)
                    }
                    AdjustSliderRow("Kontrast", colorAdjustments.contrast) {
                        colorAdjustments = colorAdjustments.copy(contrast = it)
                    }
                    AdjustSliderRow("Sättigung", colorAdjustments.saturation) {
                        colorAdjustments = colorAdjustments.copy(saturation = it)
                    }
                    AdjustSliderRow("Farbtemperatur", colorAdjustments.temperature) {
                        colorAdjustments = colorAdjustments.copy(temperature = it)
                    }
                    AdjustSliderRow("Schwarzpunkt", colorAdjustments.blackPoint) {
                        colorAdjustments = colorAdjustments.copy(blackPoint = it)
                    }
                    AdjustSliderRow("Weißpunkt", colorAdjustments.whitePoint) {
                        colorAdjustments = colorAdjustments.copy(whitePoint = it)
                    }
                    AdjustSliderRow("Schatten", colorAdjustments.shadows) {
                        colorAdjustments = colorAdjustments.copy(shadows = it)
                    }
                    AdjustSliderRow("Spitzlichter", colorAdjustments.highlights) {
                        colorAdjustments = colorAdjustments.copy(highlights = it)
                    }
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Crop-Geometrie + Overlay
//
// Modell: der Formatrahmen ist fix in der Container-Mitte (Größe = zentrierter
// Crop des FIT-angezeigten Videos bei Skalierung 1). Der Nutzer bewegt/zoomt das
// Bild (PlayerView via graphicsLayer, Pivot = Mitte). Der tatsächliche Ausschnitt
// wird über die inverse Transformation aus dem fixen Rahmen abgeleitet.
// ─────────────────────────────────────────────────────────────────────────────

private class CropGeom(
    val displayX: Float, val displayY: Float, val displayW: Float, val displayH: Float,
    val frameHalfW: Float, val frameHalfH: Float,
    val centerX: Float, val centerY: Float
)

/** Berechnet FIT-Anzeigerechteck und den zentrierten Formatrahmen (Skalierung 1). */
private fun computeCropGeom(
    containerW: Float, containerH: Float,
    videoW: Float, videoH: Float,
    targetAspect: Float
): CropGeom {
    val inputAspect = videoW / videoH
    val containerAspect = containerW / containerH

    val displayW: Float; val displayH: Float; val displayX: Float; val displayY: Float
    if (inputAspect > containerAspect) {
        displayW = containerW
        displayH = containerW / inputAspect
        displayX = 0f
        displayY = (containerH - displayH) / 2f
    } else {
        displayH = containerH
        displayW = containerH * inputAspect
        displayX = (containerW - displayW) / 2f
        displayY = 0f
    }

    val frameW: Float; val frameH: Float
    if (targetAspect < inputAspect) {
        frameH = displayH
        frameW = displayH * targetAspect
    } else {
        frameW = displayW
        frameH = displayW / targetAspect
    }
    return CropGeom(
        displayX, displayY, displayW, displayH,
        frameW / 2f, frameH / 2f,
        containerW / 2f, containerH / 2f
    )
}

/**
 * Leitet aus Pan/Zoom den Crop-Bereich in NDC ab: [left, right, bottom, top] (-1..1).
 * Inverse der graphicsLayer-Transformation (Pivot = Container-Mitte).
 */
private fun deriveCropRect(g: CropGeom, scale: Float, offX: Float, offY: Float): FloatArray {
    // Rahmenecken in untransformierte Bild-Pixel zurückrechnen
    val otlx = g.centerX + (-g.frameHalfW - offX) / scale
    val obrx = g.centerX + (g.frameHalfW - offX) / scale
    val otly = g.centerY + (-g.frameHalfH - offY) / scale
    val obry = g.centerY + (g.frameHalfH - offY) / scale

    fun nx(x: Float) = (((x - g.displayX) / g.displayW) * 2f - 1f).coerceIn(-1f, 1f)
    fun ny(y: Float) = (1f - ((y - g.displayY) / g.displayH) * 2f).coerceIn(-1f, 1f)

    val left = nx(otlx)
    val right = nx(obrx)
    val top = ny(otly)      // kleineres y → größeres NDC-y = top
    val bottom = ny(obry)
    return floatArrayOf(left, right, bottom, top)
}

/** Zeichnet die Maske außerhalb des fixen, zentrierten Formatrahmens + Rahmenkante. */
private fun DrawScope.drawCropFrame(g: CropGeom) {
    val frameX = g.centerX - g.frameHalfW
    val frameY = g.centerY - g.frameHalfH
    val frameW = g.frameHalfW * 2f
    val frameH = g.frameHalfH * 2f
    val frameRight = frameX + frameW
    val frameBottom = frameY + frameH

    val overlayColor = Color.Black.copy(alpha = 0.55f)
    val borderColor = Color(0xFFFFD700)
    val containerW = g.centerX * 2f
    val containerH = g.centerY * 2f

    // Obere Maske
    if (frameY > 0f) drawRect(overlayColor, topLeft = Offset(0f, 0f), size = Size(containerW, frameY))
    // Untere Maske
    if (frameBottom < containerH) {
        drawRect(overlayColor, topLeft = Offset(0f, frameBottom), size = Size(containerW, containerH - frameBottom))
    }
    // Linke Maske
    if (frameX > 0f) drawRect(overlayColor, topLeft = Offset(0f, frameY), size = Size(frameX, frameH))
    // Rechte Maske
    if (frameRight < containerW) {
        drawRect(overlayColor, topLeft = Offset(frameRight, frameY), size = Size(containerW - frameRight, frameH))
    }
    // Rahmenkante
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(frameX, frameY),
        size = Size(frameW, frameH),
        cornerRadius = CornerRadius(4.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Zwei-Spur-Ansicht (Video + Audio)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoEditorTracksSection(
    videoDurationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    videoTrackVolume: Int = 100,
    musicTrackVolume: Int = 100,
    onVolumeIconClick: () -> Unit = {},
    currentPositionMs: Long,
    audioClips: List<VideoAudioClip>,
    extraVideoClips: List<VideoMediaClip> = emptyList(),
    videoTransitions: List<VideoTransitionType?> = emptyList(),
    onTransitionClick: (Int) -> Unit = {},
    onVideoTrimChange: (Long, Long) -> Unit,
    onVideoTrimDragStart: () -> Unit,
    onVideoTrimDragEnd: () -> Unit,
    onAudioClipUpdate: (Int, Long, Long, Long) -> Unit,
    hasPrimary: Boolean = true,
    onAddMusicClick: () -> Unit,
    onAddVideoClick: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFFFD700)
    val trackBg = Color(0xFF1C1C1C)
    val labelW = 52.dp
    val ctrlW = 44.dp
    val addBtnW = 36.dp

    // Zeige farbigen Punkt wenn Lautstärke angepasst wurde
    val volumeModified = videoTrackVolume != 100 || musicTrackVolume != 100

    // Gesamtlänge der Timeline: max(Video-Dauer, Musik-Titel-Dauer)
    val totalVideoDurMs = remember(videoDurationMs, extraVideoClips) {
        videoDurationMs + extraVideoClips.sumOf { it.durationMs }
    }
    val totalMusicDurMs = remember(audioClips) {
        audioClips.maxOfOrNull { it.timelineStartMs + it.clipDurationMs } ?: 0L
    }
    val totalTimelineMs = remember(totalVideoDurMs, totalMusicDurMs) {
        maxOf(totalVideoDurMs, totalMusicDurMs).coerceAtLeast(1L)
    }
    // Timeline-Zoom: 1 = 1:1 (20 dp/s), 2 = 1:2 (halbe Breite), 4 = 1:4 (Viertel-Breite)
    var timelineScaleDivisor by remember { mutableIntStateOf(1) }
    var showScaleMenu by remember { mutableStateOf(false) }
    // 20 dp pro Sekunde (bei 1:1), mind. 280 dp
    val timelineWidthDp = ((totalTimelineMs / 1000f) * (20f / timelineScaleDivisor)).coerceAtLeast(280f).dp

    // Gemeinsamer Scroll-State für alle Spuren
    val trackScrollState = rememberScrollState()

    Column(modifier = modifier.background(Color(0xFF111111))) {
        // Zeitstempel-Header mit Musik-Lautstärke-Symbol links
        if (videoDurationMs > 0L) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = addBtnW + 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom-Chip (1:1 / 1:2 / 1:4) + Musik-Lautstärke-Button (belegt labelW + ctrlW Bereich)
                Row(
                    modifier = Modifier.width(labelW + ctrlW),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timeline-Skalierung
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(0.08f))
                                .clickable { showScaleMenu = true }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "1:$timelineScaleDivisor",
                                color = if (timelineScaleDivisor != 1) accent else Color.White.copy(0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = showScaleMenu,
                            onDismissRequest = { showScaleMenu = false }
                        ) {
                            listOf(1, 2, 4).forEach { divisor ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "1:$divisor",
                                            color = if (divisor == timelineScaleDivisor) accent else Color.White,
                                            fontWeight = if (divisor == timelineScaleDivisor) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { timelineScaleDivisor = divisor; showScaleMenu = false }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = onVolumeIconClick, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = "Lautstärke",
                                tint = if (volumeModified) accent else Color.White.copy(0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Kleiner Punkt wenn Lautstärke angepasst
                        if (volumeModified) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(accent, RoundedCornerShape(50))
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 2.dp)
                            )
                        }
                    }
                }
                // Zeitstempel
                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatVideoMs(trimStartMs), color = Color.White.copy(0.45f), fontSize = 9.sp)
                    Text(
                        "Ausschnitt: ${formatVideoMs(trimEndMs - trimStartMs)}",
                        color = accent.copy(0.8f), fontSize = 9.sp, fontWeight = FontWeight.Medium
                    )
                    Text(formatVideoMs(videoDurationMs), color = Color.White.copy(0.45f), fontSize = 9.sp)
                }
            }
        }

        // ── Video-Spur ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(labelW), contentAlignment = Alignment.Center) {
                Text("Video", color = Color.White.copy(0.65f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            // Stummschalten-Button (Musiknoten mit Strich)
            IconButton(onClick = onMuteToggle, modifier = Modifier.size(ctrlW)) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MusicOff else Icons.Default.MusicNote,
                    contentDescription = if (isMuted) "Ton ein" else "Ton aus",
                    tint = if (isMuted) Color(0xFFFF5252) else Color.White.copy(0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            // Video-Clip-Timeline (scrollbar)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(trackBg)
                    .horizontalScroll(trackScrollState)
            ) {
                if (videoDurationMs > 0L) {
                    Box(modifier = Modifier.width(timelineWidthDp).fillMaxHeight()) {
                        VideoClipTrackTimeline(
                            durationMs = videoDurationMs,
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            currentPositionMs = currentPositionMs,
                            totalTimelineMs = totalTimelineMs,
                            extraClips = extraVideoClips,
                            videoTransitions = videoTransitions,
                            onTransitionClick = onTransitionClick,
                            onTrimChange = onVideoTrimChange,
                            onDragStart = onVideoTrimDragStart,
                            onDragEnd = onVideoTrimDragEnd,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (!hasPrimary) {
                    // Leerer Start: noch kein Medium in der Spur → zum Hinzufügen auffordern
                    Box(
                        modifier = Modifier
                            .width(timelineWidthDp)
                            .fillMaxHeight()
                            .clickable { onAddVideoClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(14.dp))
                            Text("Video oder Bild hinzufügen", color = accent, fontSize = 11.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.width(timelineWidthDp).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
            }
            // + Weitere Videos/Bilder
            IconButton(onClick = onAddVideoClick, modifier = Modifier.size(addBtnW)) {
                Icon(Icons.Default.Add, "Weitere Medien", tint = Color.White.copy(0.35f), modifier = Modifier.size(17.dp))
            }
        }

        HorizontalDivider(color = Color.White.copy(0.05f))

        // ── Audio-Spur ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(labelW), contentAlignment = Alignment.Center) {
                Text("Audio", color = Color.White.copy(0.65f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(ctrlW))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(trackBg)
                    .horizontalScroll(trackScrollState)
            ) {
                if (audioClips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .width(timelineWidthDp)
                            .fillMaxHeight()
                            .clickable { onAddMusicClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Add, null, tint = accent, modifier = Modifier.size(14.dp))
                            Text("Musik hinzufügen", color = accent, fontSize = 11.sp)
                        }
                    }
                } else {
                    Box(modifier = Modifier.width(timelineWidthDp).fillMaxHeight()) {
                        AudioClipsTrackTimeline(
                            totalTimelineMs = totalTimelineMs,
                            audioClips = audioClips,
                            currentPositionMs = currentPositionMs,
                            onClipUpdate = onAudioClipUpdate,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            if (audioClips.isNotEmpty()) {
                IconButton(onClick = onAddMusicClick, modifier = Modifier.size(addBtnW)) {
                    Icon(Icons.Default.Add, "Musik hinzufügen", tint = accent, modifier = Modifier.size(17.dp))
                }
            } else {
                Spacer(Modifier.width(addBtnW))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ─── Video-Clip-Timeline ──────────────────────────────────────────────────────

@Composable
private fun VideoClipTrackTimeline(
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    totalTimelineMs: Long,
    extraClips: List<VideoMediaClip> = emptyList(),
    videoTransitions: List<VideoTransitionType?> = emptyList(),
    onTransitionClick: (Int) -> Unit = {},
    onTrimChange: (Long, Long) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val accent = Color(0xFFFFD700)
    val latestStart = rememberUpdatedState(trimStartMs)
    val latestEnd = rememberUpdatedState(trimEndMs)
    val latestDur = rememberUpdatedState(durationMs)
    val latestTotalMs = rememberUpdatedState(totalTimelineMs)
    val latestChange = rememberUpdatedState(onTrimChange)
    val latestDragStart = rememberUpdatedState(onDragStart)
    val latestDragEnd = rememberUpdatedState(onDragEnd)
    val latestSeekTo = rememberUpdatedState(onSeekTo)
    val latestPos = rememberUpdatedState(currentPositionMs)

    // Breite des primären Clips in der gemeinsamen Timeline
    val primaryFraction = if (totalTimelineMs > 0L) durationMs.toFloat() / totalTimelineMs else 1f

    Box(
        modifier = modifier
            .onSizeChanged { barWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val tMs = latestTotalMs.value
                    val dur = latestDur.value
                    val bw = barWidthPx
                    val primaryBw = bw * (if (tMs > 0L) dur.toFloat() / tMs else 1f)
                    if (primaryBw <= 0f || dur <= 0L) return@awaitEachGesture
                    fun msToX(ms: Long) = ms.toFloat() / dur * primaryBw
                    val hitPx = 70f
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val sx = down.position.x
                    // Playhead-Griff: Berührung in der Nähe des Playheads → Seek-Drag
                    val playheadX = if (tMs > 0L) latestPos.value.toFloat() / tMs * bw else 0f
                    if (abs(sx - playheadX) < 36f) {
                        drag(down.id) { change ->
                            val newMs = (change.position.x / bw * tMs).toLong().coerceIn(0L, tMs)
                            latestSeekTo.value(newMs)
                        }
                        return@awaitEachGesture
                    }
                    // Nur Gesten im Primär-Clip-Bereich verarbeiten
                    if (sx > primaryBw + 20f) return@awaitEachGesture
                    val ts0 = latestStart.value; val te0 = latestEnd.value
                    val lx = msToX(ts0); val rx = msToX(te0)
                    val hitLeft = abs(sx - lx) < hitPx
                    val hitRight = !hitLeft && abs(sx - rx) < hitPx
                    val hitMid = !hitLeft && !hitRight && sx in (lx..rx)
                    if (hitLeft || hitRight || hitMid) {
                        latestDragStart.value()
                        var curTs = ts0; var curTe = te0
                        drag(down.id) { change ->
                            val dMs = ((change.position.x - change.previousPosition.x) / primaryBw * dur).toLong()
                            when {
                                hitLeft -> { curTs = (curTs + dMs).coerceIn(0L, curTe - 1_000L); latestChange.value(curTs, curTe) }
                                hitRight -> { curTe = (curTe + dMs).coerceIn(curTs + 1_000L, dur); latestChange.value(curTs, curTe) }
                                hitMid -> {
                                    val w = curTe - curTs
                                    curTs = (curTs + dMs).coerceIn(0L, dur - w)
                                    curTe = curTs + w
                                    latestChange.value(curTs, curTe)
                                }
                            }
                        }
                        latestDragEnd.value()
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height; val w = size.width
            val blockH = h * 0.65f; val blockY = (h - blockH) / 2f
            val handleW = 5.dp.toPx(); val r = CornerRadius(3.dp.toPx())

            // Primärclip-Breite in der gemeinsamen Timeline
            val p1W = w * primaryFraction
            fun ms2x(ms: Long) = if (durationMs > 0L) ms.toFloat() / durationMs * p1W else 0f
            val lx = ms2x(trimStartMs); val rx = ms2x(trimEndMs)

            // Primär-Clip Hintergrund
            drawRoundRect(Color.White.copy(0.1f), topLeft = Offset(0f, blockY), size = Size(p1W, blockH), cornerRadius = r)
            // Ausgewählter Ausschnitt (Trim)
            drawRoundRect(accent.copy(0.38f), topLeft = Offset(lx, blockY), size = Size((rx - lx).coerceAtLeast(0f), blockH), cornerRadius = r)
            // Linkes Trim-Handle
            drawRoundRect(accent, topLeft = Offset(lx - handleW / 2, blockY), size = Size(handleW, blockH), cornerRadius = CornerRadius(handleW / 2))
            drawLine(Color.Black.copy(0.4f), Offset(lx, blockY + blockH * 0.28f), Offset(lx, blockY + blockH * 0.72f), strokeWidth = 1.5f.dp.toPx())
            // Rechtes Trim-Handle
            drawRoundRect(accent, topLeft = Offset(rx - handleW / 2, blockY), size = Size(handleW, blockH), cornerRadius = CornerRadius(handleW / 2))
            drawLine(Color.Black.copy(0.4f), Offset(rx, blockY + blockH * 0.28f), Offset(rx, blockY + blockH * 0.72f), strokeWidth = 1.5f.dp.toPx())
            // Playhead — globale Timeline-Position (auch über Extra-Clips)
            val px = if (totalTimelineMs > 0L) currentPositionMs.toFloat() / totalTimelineMs * w else ms2x(currentPositionMs)
            // Linie über volle Spurhöhe + kleines Dreieck oben als Griff
            drawLine(Color.White.copy(0.92f), Offset(px, 0f), Offset(px, h), strokeWidth = 2.5f.dp.toPx())
            val triangleH = 5.dp.toPx()
            val triangleW = 4.dp.toPx()
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(px, 0f)
                    lineTo(px - triangleW, -triangleH)
                    lineTo(px + triangleW, -triangleH)
                    close()
                },
                color = Color.White.copy(0.92f)
            )

            // Zusätzliche Clips (grün) — Breite relativ zur totalTimelineMs
            var clipX = p1W
            extraClips.forEach { clip ->
                val cW = if (totalTimelineMs > 0L) clip.durationMs.toFloat() / totalTimelineMs * w else 0f
                drawRoundRect(
                    Color(0xFF2E7D32).copy(0.75f),
                    topLeft = Offset(clipX, blockY),
                    size = Size(cW.coerceAtLeast(4.dp.toPx()), blockH),
                    cornerRadius = r
                )
                clipX += cW
            }
        }

        // Übergangs-Buttons an den Clip-Grenzen (als Composable-Overlay)
        if (extraClips.isNotEmpty() && barWidthPx > 0f) {
            var cumulativeX = barWidthPx * primaryFraction
            extraClips.forEachIndexed { idx, clip ->
                val thisBtnCenterX = cumulativeX
                val hasTransition = videoTransitions.getOrNull(idx) != null
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .offset { IntOffset((thisBtnCenterX - 11.dp.roundToPx()).roundToInt(), 0) }
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(50))
                        .background(if (hasTransition) accent else Color(0xFF383838))
                        .border(1.dp, if (hasTransition) accent else Color.White.copy(0.5f), RoundedCornerShape(50))
                        .clickable { onTransitionClick(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Übergang wählen",
                        tint = if (hasTransition) Color.Black else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
                cumulativeX += if (totalTimelineMs > 0L) clip.durationMs.toFloat() / totalTimelineMs * barWidthPx else 0f
            }
        }
    }
}

// ─── Audio-Clips-Timeline ─────────────────────────────────────────────────────

@Composable
private fun AudioClipsTrackTimeline(
    totalTimelineMs: Long,
    audioClips: List<VideoAudioClip>,
    currentPositionMs: Long = 0L,
    onClipUpdate: (Int, Long, Long, Long) -> Unit,
    onSeekTo: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val latestClips = rememberUpdatedState(audioClips)
    val latestTotalMs = rememberUpdatedState(totalTimelineMs)
    val latestUpdate = rememberUpdatedState(onClipUpdate)
    val latestSeekTo = rememberUpdatedState(onSeekTo)
    val latestPos = rememberUpdatedState(currentPositionMs)
    val clipHighlightColor = Color(0xFF42A5F5)
    val handleColor = Color(0xFF90CAF9)

    Box(
        modifier = modifier
            .onSizeChanged { barWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val bw = barWidthPx
                    val tMs = latestTotalMs.value
                    if (bw <= 0f || tMs <= 0L) return@awaitEachGesture
                    val clips = latestClips.value
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val sx = down.position.x
                    // Playhead-Griff in der Audiospur → Seek-Drag
                    val playheadX = if (tMs > 0L) latestPos.value.toFloat() / tMs * bw else 0f
                    if (abs(sx - playheadX) < 36f) {
                        drag(down.id) { change ->
                            val newMs = (change.position.x / bw * tMs).toLong().coerceIn(0L, tMs)
                            latestSeekTo.value(newMs)
                        }
                        return@awaitEachGesture
                    }
                    val handleHitPx = 60f
                    var hitIdx = -1; var hitLeft = false; var hitBody = false
                    clips.forEachIndexed { idx, clip ->
                        val clipStartX = clip.timelineStartMs.toFloat() / tMs * bw
                        val clipEndX = (clip.timelineStartMs + clip.clipDurationMs).toFloat() / tMs * bw
                        when {
                            abs(sx - clipStartX) < handleHitPx -> { hitIdx = idx; hitLeft = true; hitBody = false }
                            abs(sx - clipEndX) < handleHitPx -> { hitIdx = idx; hitLeft = false; hitBody = false }
                            sx in clipStartX..clipEndX -> if (hitIdx < 0) { hitIdx = idx; hitBody = true }
                        }
                    }
                    if (hitIdx < 0) return@awaitEachGesture
                    val clip = clips[hitIdx]
                    var curStartOff = clip.startOffsetMs
                    var curClipDur = clip.clipDurationMs
                    var curTimeline = clip.timelineStartMs
                    if (hitBody) {
                        // Ganzen Clip per Gedrückt-halten auf der Timeline verschieben
                        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        drag(longPress.id) { change ->
                            val dMs = ((change.position.x - change.previousPosition.x) / bw * tMs).toLong()
                            curTimeline = (curTimeline + dMs).coerceIn(0L, (tMs - curClipDur).coerceAtLeast(0L))
                            latestUpdate.value(hitIdx, curTimeline, curStartOff, curClipDur)
                        }
                    } else {
                        drag(down.id) { change ->
                            val dMs = ((change.position.x - change.previousPosition.x) / bw * tMs).toLong()
                            if (hitLeft) {
                                // Linkes Handle: Song-Anfang wegschneiden, rechte Timeline-Kante bleibt fix
                                val minDelta = maxOf(-curStartOff, -curTimeline)
                                val maxDelta = curClipDur - 1_000L
                                // Bei sehr kurzen Clips kann maxDelta < minDelta sein → leere Range vermeiden
                                val delta = if (maxDelta <= minDelta) 0L else dMs.coerceIn(minDelta, maxDelta)
                                curStartOff += delta; curClipDur -= delta; curTimeline += delta
                            } else {
                                // Rechtes Handle: Song-Ende wegschneiden/verlängern
                                val maxDur = maxOf(1_000L, clip.totalDurationMs - curStartOff)
                                curClipDur = (curClipDur + dMs).coerceIn(1_000L, maxDur)
                            }
                            latestUpdate.value(hitIdx, curTimeline, curStartOff, curClipDur)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height; val w = size.width
            val blockH = h * 0.65f; val blockY = (h - blockH) / 2f
            val handleW = 5.dp.toPx()
            val r = CornerRadius(3.dp.toPx())
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 9.sp.toPx()
                isAntiAlias = true
            }
            audioClips.forEach { clip ->
                if (totalTimelineMs <= 0L) return@forEach
                // Ausschnitt-Block an seiner Timeline-Position
                val clipStartX = clip.timelineStartMs.toFloat() / totalTimelineMs * w
                val clipEndX = (clip.timelineStartMs + clip.clipDurationMs).toFloat() / totalTimelineMs * w
                val clipW = (clipEndX - clipStartX).coerceAtLeast(8.dp.toPx())
                drawRoundRect(clipHighlightColor.copy(0.78f), topLeft = Offset(clipStartX, blockY), size = Size(clipW, blockH), cornerRadius = r)

                // Linkes Handle (Song-Anfang wegschneiden)
                drawRoundRect(handleColor, topLeft = Offset(clipStartX - handleW / 2, blockY), size = Size(handleW, blockH), cornerRadius = CornerRadius(handleW / 2))
                drawLine(Color.Black.copy(0.4f), Offset(clipStartX, blockY + blockH * 0.28f), Offset(clipStartX, blockY + blockH * 0.72f), strokeWidth = 1.5f.dp.toPx())
                // Rechtes Handle (Song-Ende wegschneiden)
                drawRoundRect(handleColor, topLeft = Offset(clipEndX - handleW / 2, blockY), size = Size(handleW, blockH), cornerRadius = CornerRadius(handleW / 2))
                drawLine(Color.Black.copy(0.4f), Offset(clipEndX, blockY + blockH * 0.28f), Offset(clipEndX, blockY + blockH * 0.72f), strokeWidth = 1.5f.dp.toPx())

                // Titeltext im Ausschnitt
                val title = clip.title.take(28)
                drawContext.canvas.nativeCanvas.drawText(
                    title,
                    clipStartX + handleW + 3.dp.toPx(),
                    blockY + blockH / 2f + textPaint.textSize / 3f,
                    textPaint
                )
            }
            // Playhead — gleiche Position wie in der Videospur
            if (totalTimelineMs > 0L) {
                val px = currentPositionMs.toFloat() / totalTimelineMs * w
                drawLine(Color.White.copy(0.92f), Offset(px, 0f), Offset(px, h), strokeWidth = 2.5f.dp.toPx())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lautstärke-Dialog (Video-Spur + Musik-Spur)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoEditorVolumeDialog(
    videoVolume: Int,
    musicVolume: Int,
    onVideoVolumeChange: (Int) -> Unit,
    onMusicVolumeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = Color(0xFFFFD700)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(18.dp))
                Text("Lautstärke", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Video-Spur
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Videospur", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("$videoVolume%", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = videoVolume.toFloat(),
                        onValueChange = { onVideoVolumeChange(it.roundToInt()) },
                        valueRange = 0f..100f,
                        steps = 0,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(0.2f)
                        )
                    )
                }
                // Musik-Spur
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Musikspur", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("$musicVolume%", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = musicVolume.toFloat(),
                        onValueChange = { onMusicVolumeChange(it.roundToInt()) },
                        valueRange = 0f..100f,
                        steps = 0,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(0.2f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fertig", color = accent, fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = Color(0xFF121212),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Übergangs-Picker Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TransitionPickerDialog(
    currentTransition: VideoTransitionType?,
    onSelect: (VideoTransitionType) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = Color(0xFFFFD700)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Übergang wählen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoTransitionType.entries.forEach { type ->
                    val isSelected = currentTransition == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accent.copy(0.18f) else Color(0xFF1E1E1E))
                            .border(
                                1.dp,
                                if (isSelected) accent else Color.White.copy(0.12f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(type) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val icon = when (type) {
                            VideoTransitionType.FADE -> "≈"
                            VideoTransitionType.DITHER -> "⋯"
                            VideoTransitionType.SLIDE_RIGHT -> "→"
                        }
                        Text(icon, color = if (isSelected) accent else Color.White.copy(0.6f), fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.label, color = Color.White, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (currentTransition != null) {
                TextButton(onClick = onRemove) {
                    Text("Entfernen", color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }
        },
        containerColor = Color(0xFF121212),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Musik-Picker für den Video-Editor
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoEditorMusicPickerSheet(
    musicViewModel: MusicSearchViewModel,
    videoDurationMs: Long,
    onSelect: (VideoAudioClip) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val letheTracks by musicViewModel.letheTracks.collectAsState()
    val audiusTracks by musicViewModel.tracks.collectAsState()
    val isLetheLoading by musicViewModel.isLetheLoading.collectAsState()
    val isAudiusLoading by musicViewModel.isLoading.collectAsState()
    val searchQuery by musicViewModel.searchQuery.collectAsState()
    val letheSearchQuery by musicViewModel.letheSearchQuery.collectAsState()

    val accent = Color(0xFFFFD700)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.88f),
        containerColor = Color(0xFF0D1A2A)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Musik wählen", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, modifier = Modifier.weight(1f))
                Text("✕", color = Color.White.copy(0.5f), fontSize = 18.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            }

            HorizontalDivider(color = Color.White.copy(0.07f))

            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── Lethe Bibliothek ──────────────────────────────────────
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MusicNote, null, tint = accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Lethe Bibliothek", color = Color.White.copy(0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = letheSearchQuery,
                            onValueChange = { musicViewModel.updateLetheQuery(it) },
                            placeholder = { Text("Suchen…", color = Color.White.copy(0.35f), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color.White.copy(0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = accent
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                if (isLetheLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                } else {
                    items(letheTracks, key = { it.track.id }) { uiState ->
                        val track = uiState.track
                        // Manche Library-Tracks (z.B. aus Sparks extrahiert) haben duration_seconds=0.
                        // Dann Video-Länge als Fallback nehmen, sonst wäre der Clip 0ms lang.
                        val trackTotalMs = (track.durationSeconds * 1000L)
                            .takeIf { it > 0L }
                            ?: videoDurationMs.takeIf { it > 0L }
                            ?: 60_000L
                        val clipDurMs = if (videoDurationMs > 0L)
                            minOf(trackTotalMs, videoDurationMs)
                        else trackTotalMs

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(VideoAudioClip(
                                        title = track.title,
                                        artist = track.artist.takeIf { it.isNotBlank() },
                                        streamUrl = track.audioUrl,
                                        totalDurationMs = trackTotalMs,
                                        clipDurationMs = clipDurMs
                                    ))
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (track.artist.isNotBlank()) {
                                    Text(track.artist, color = Color.White.copy(0.55f), fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(track.durationFormatted, color = Color.White.copy(0.4f), fontSize = 11.sp)
                        }
                    }
                }

                // ── Audius ────────────────────────────────────────────────
                item {
                    HorizontalDivider(color = Color.White.copy(0.07f), modifier = Modifier.padding(vertical = 4.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF7B68EE), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Audius", color = Color.White.copy(0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { musicViewModel.updateQuery(it) },
                            placeholder = { Text("Audius durchsuchen…", color = Color.White.copy(0.35f), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B68EE),
                                unfocusedBorderColor = Color.White.copy(0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF7B68EE)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                if (isAudiusLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF7B68EE), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                } else {
                    items(audiusTracks, key = { it.track.id }) { uiState ->
                        val track = uiState.track
                        val durationMs = (track.duration * 1000L)
                            .takeIf { it > 0L }
                            ?: videoDurationMs.takeIf { it > 0L }
                            ?: 60_000L
                        val clipDurMs = if (videoDurationMs > 0L) minOf(durationMs, videoDurationMs) else durationMs
                        val streamUrl = musicViewModel.getStreamUrlForEditor(track.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(VideoAudioClip(
                                        title = track.title,
                                        artist = track.user?.name,
                                        streamUrl = streamUrl,
                                        totalDurationMs = maxOf(durationMs, clipDurMs),
                                        clipDurationMs = clipDurMs
                                    ))
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                track.user?.name?.let { artist ->
                                    Text(artist, color = Color.White.copy(0.55f), fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            val min = track.duration / 60; val sec = track.duration % 60
                            Text("$min:${sec.toString().padStart(2,'0')}", color = Color.White.copy(0.4f), fontSize = 11.sp)
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
