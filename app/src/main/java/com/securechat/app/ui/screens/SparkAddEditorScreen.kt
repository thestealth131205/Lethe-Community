package com.securechat.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as sparkGridItems
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.atan2
import java.util.UUID
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.ui.MainViewModel
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.securechat.app.data.local.SparkFilterEngine
import com.securechat.app.data.local.SparkFilterId
import com.securechat.app.data.local.SparkProcessingTask
import com.securechat.app.data.network.AudiusTrack
import com.securechat.app.data.network.LetheMusicTrackUiState
import com.securechat.app.data.network.MusicTrackUiState
import com.securechat.app.ui.viewmodel.MusicSearchViewModel
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// SparkAddEditorScreen
//
// TikTok-Style Editor für Sparks (Videos + Bilder).
//
// Zwei Seiten:
//   Seite 0 (Editor): Vorschau, Filter, Musik, Trim-Zeitleiste, Musik-Sync
//   Seite 1 (Metadaten): Titel, Beschreibung, #Tags, @Mentions → Spark erstellen
// ─────────────────────────────────────────────────────────────────────────────

private const val SPARK_MAX_TRIM_MS = 60_000L   // max 60 Sekunden

private val SPARK_CATEGORIES = listOf(
    "Handwerklich", "Hobby", "Fotografie", "Beauty", "Musik",
    "Gaming", "Lifestyle", "Fitness", "Kochen", "Movie", "Comedy", "Songs", "18+"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SparkAddEditorScreen(
    viewModel: MainViewModel,
    mediaUri: Uri,
    extraImageUris: List<Uri> = emptyList(),
    preSelectedSoundOriginId: String? = null,
    preSelectedMusicTitle: String? = null,
    preSelectedMusicArtist: String? = null,
    preSelectedMusicCoverUrl: String? = null,
    onFinish: (processedUri: Uri, title: String, description: String, category: String?) -> Unit,
    onUploadImageSpark: (uris: List<Uri>, title: String, description: String, category: String?, musicTitle: String?, musicArtist: String?, musicCoverUrl: String?, soundOriginSparkId: String?, localMusicUri: Uri?, audiusTrackId: String?, audiusStreamUrl: String?, audiusDuration: Int, onProgress: (Float) -> Unit) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onCancel: () -> Unit,
    musicViewModel: MusicSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ─── Medientyp erkennen ───────────────────────────────────────────────────
    val isImage = remember(mediaUri) {
        val mime = context.contentResolver.getType(mediaUri) ?: ""
        mime.startsWith("image/")
    }

    // ─── Multi-Image Slideshow-Modus (mehr als ein Bild ausgewählt) ───────────
    val isMultiImage = extraImageUris.isNotEmpty()
    var imageOrder by remember { mutableStateOf(listOf(mediaUri) + extraImageUris) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var isUploadingImages by remember { mutableStateOf(false) }
    var imageUploadProgress by remember { mutableFloatStateOf(0f) }

    // ─── Bild-Zoom/Pan/Crop ───────────────────────────────────────────────────
    var imgScale by remember { mutableFloatStateOf(1f) }
    var imgOffset by remember { mutableStateOf(Offset.Zero) }
    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }
    var isCroppingBusy by remember { mutableStateOf(false) }
    LaunchedEffect(selectedImageIndex) { imgScale = 1f; imgOffset = Offset.Zero }
    val imgTransformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        imgScale = (imgScale * zoomChange).coerceIn(1f, 5f)
        if (imgScale > 1f) imgOffset += offsetChange else imgOffset = Offset.Zero
    }

    // ─── Orientation Lock (Portrait) ─────────────────────────────────────────
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ─── Editor State ─────────────────────────────────────────────────────────
    var selectedFilter by remember { mutableStateOf(SparkFilterId.NONE) }
    var isOriginalMuted by remember { mutableStateOf(false) }
    var showMusicSheet by remember { mutableStateOf(false) }
    var showFilterStrip by remember { mutableStateOf(false) }
    var musicVolume by remember { mutableFloatStateOf(0.60f) }
    var originalVolume by remember { mutableFloatStateOf(1.00f) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingError by remember { mutableStateOf<String?>(null) }

    // ─── Trim State ───────────────────────────────────────────────────────────
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var imageDurationSec by remember { mutableIntStateOf(5) }
    var isTrimDragging by remember { mutableStateOf(false) }
    var playerPositionMs by remember { mutableLongStateOf(0L) }

    // ─── Musik-Sync State ─────────────────────────────────────────────────────
    var musicOffsetFrac by remember { mutableFloatStateOf(0f) }

    // ─── Text/Emoji-Overlay State ─────────────────────────────────────────────
    val sparkDensity = androidx.compose.ui.platform.LocalDensity.current.density
    var spTextOverlays by remember { mutableStateOf<List<StickerTextOverlay>>(emptyList()) }
    var spEmojiOverlays by remember { mutableStateOf<List<StickerEmojiOverlay>>(emptyList()) }
    var spSelectedId by remember { mutableStateOf<String?>(null) }
    var spDragMode by remember { mutableStateOf(OverlayDragMode.NONE) }
    var spDragCX by remember { mutableFloatStateOf(0f) }
    var spDragCY by remember { mutableFloatStateOf(0f) }
    var spDragAngle by remember { mutableFloatStateOf(0f) }
    var spDragRot by remember { mutableFloatStateOf(0f) }
    var spDragDist by remember { mutableFloatStateOf(1f) }
    var spDragScale by remember { mutableFloatStateOf(1f) }
    var spActiveTool by remember { mutableStateOf(StickerEditorTool.NONE) }
    var spShowTextDialog by remember { mutableStateOf(false) }
    var spTextInput by remember { mutableStateOf(TextFieldValue("")) }
    var spTextColor by remember { mutableStateOf(Color.White) }
    var spTextFontIndex by remember { mutableIntStateOf(0) }
    var spShowEmojiPicker by remember { mutableStateOf(false) }
    var spPendingOffset by remember { mutableStateOf(Offset(0f, 0f)) }

    // ─── Zweiseitiger Flow ────────────────────────────────────────────────────
    var currentPage by remember { mutableIntStateOf(0) }
    var sparkTitle by remember { mutableStateOf("") }
    var sparkDescription by remember { mutableStateOf("") }
    var sparkCategory by remember { mutableStateOf("") }
    var sparkCategoryExpanded by remember { mutableStateOf(false) }

    // ─── MusicViewModel State ─────────────────────────────────────────────────
    val selectedTrack by musicViewModel.selectedTrack.collectAsState()
    val selectedLetheTrack by musicViewModel.selectedLetheTrack.collectAsState()
    val localMusicUri by musicViewModel.localMusicUri.collectAsState()
    val localMusicName by musicViewModel.localMusicName.collectAsState()
    val localMusicTitle by musicViewModel.localMusicTitle.collectAsState()
    val localMusicArtist by musicViewModel.localMusicArtist.collectAsState()
    val hasMusicSelected = selectedTrack != null || selectedLetheTrack != null || localMusicUri != null || preSelectedSoundOriginId != null

    // Vorschau-Player: startet automatisch wenn Musik gewählt wird
    val sparkMusicPreviewUrl: String? = when {
        selectedTrack != null -> musicViewModel.selectedTrackStreamUrl.value
        selectedLetheTrack != null -> selectedLetheTrack?.audioUrl
        else -> null
    }
    val sparkMusicPreviewPlayerRef = remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(sparkMusicPreviewUrl) {
        val p = if (!sparkMusicPreviewUrl.isNullOrBlank()) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.parse(sparkMusicPreviewUrl)))
                prepare()
            }
        } else null
        sparkMusicPreviewPlayerRef.value?.release()
        sparkMusicPreviewPlayerRef.value = p
        onDispose {
            sparkMusicPreviewPlayerRef.value?.release()
            sparkMusicPreviewPlayerRef.value = null
        }
    }
    // Auto-Start: Musik sofort abspielen sobald gewählt
    LaunchedEffect(selectedTrack?.id, selectedLetheTrack?.id) {
        if (selectedTrack == null && selectedLetheTrack == null) return@LaunchedEffect
        delay(400) // kurz warten bis Player vorbereitet ist
        val p = sparkMusicPreviewPlayerRef.value ?: return@LaunchedEffect
        val musicDurSec = selectedTrack?.duration ?: selectedLetheTrack?.durationSeconds ?: 0
        val offsetMs = if (musicDurSec > 0) (musicOffsetFrac * musicDurSec * 1000L).toLong() else 0L
        p.seekTo(offsetMs)
        p.play()
    }
    // Bei Offset-Änderung: Vorschau von neuer Startposition starten
    LaunchedEffect(musicOffsetFrac) {
        val p = sparkMusicPreviewPlayerRef.value ?: return@LaunchedEffect
        val musicDurSec = selectedTrack?.duration ?: selectedLetheTrack?.durationSeconds ?: 0
        if (musicDurSec <= 0) return@LaunchedEffect
        val offsetMs = (musicOffsetFrac * musicDurSec * 1000L).toLong()
        p.seekTo(offsetMs)
        p.play()
    }

    // ─── ExoPlayer für Video-Vorschau ─────────────────────────────────────────
    val videoPlayer = remember {
        if (isImage) null
        else {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 15_000, 2_000, 5_000)
                .build()
            ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 1f
                }
        }
    }

    LaunchedEffect(mediaUri) {
        if (!isImage && videoPlayer != null) {
            videoPlayer.setMediaItem(MediaItem.fromUri(mediaUri))
            videoPlayer.prepare()
            videoPlayer.play()
        }
    }

    // Video-Dauer abfragen und Trim initialisieren
    LaunchedEffect(isImage) {
        if (!isImage && videoPlayer != null) {
            while (true) {
                val dur = videoPlayer.duration.takeIf { it > 0 } ?: -1L
                if (dur > 0) {
                    videoDurationMs = dur
                    if (trimEndMs == 0L) trimEndMs = minOf(dur, SPARK_MAX_TRIM_MS)
                    break
                }
                delay(100)
            }
        }
    }

    // Player-Position laufend abfragen (für Timeline-Indikator)
    LaunchedEffect(isImage, isTrimDragging) {
        if (!isImage && !isTrimDragging && videoPlayer != null) {
            while (true) {
                playerPositionMs = videoPlayer.currentPosition.coerceAtLeast(0L)
                delay(250)
            }
        }
    }

    // Lautstärke synchronisieren
    LaunchedEffect(isOriginalMuted, originalVolume) {
        videoPlayer?.volume = if (isOriginalMuted) 0f else originalVolume
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer?.release()
            sparkMusicPreviewPlayerRef.value?.release()
            sparkMusicPreviewPlayerRef.value = null
            musicViewModel.stopPreview()
        }
    }

    val filterConfig = remember(selectedFilter) {
        SparkFilterEngine.forId(selectedFilter)
    }

    // ─── Processing-Logik ─────────────────────────────────────────────────────
    fun doProcess() {
        if (isProcessing) return
        processingError = null
        isProcessing = true
        processingProgress = 0f
        videoPlayer?.pause()

        scope.launch {
            val inputPath = withContext(Dispatchers.IO) {
                resolveMediaPath(context, mediaUri, isImage)
            }
            if (inputPath == null) {
                processingError = "Mediendatei konnte nicht gelesen werden."
                isProcessing = false
                videoPlayer?.play()
                return@launch
            }

            val durationMs = if (isImage) {
                imageDurationSec * 1000L
            } else {
                videoPlayer?.duration?.takeIf { it > 0L } ?: videoDurationMs
            }

            val musicSourceUrl: String? = when {
                localMusicUri != null -> withContext(Dispatchers.IO) {
                    val parsedUri = Uri.parse(localMusicUri)
                    resolveAudioPath(context, parsedUri)
                }
                selectedTrack != null -> musicViewModel.selectedTrackStreamUrl.value
                selectedLetheTrack != null -> selectedLetheTrack!!.audioUrl
                else -> null
            }

            val musicOffsetSec = when {
                selectedTrack != null && selectedTrack!!.duration > 0 ->
                    musicOffsetFrac * selectedTrack!!.duration.toFloat()
                selectedLetheTrack != null && selectedLetheTrack!!.durationSeconds > 0 ->
                    musicOffsetFrac * selectedLetheTrack!!.durationSeconds.toFloat()
                else -> 0f
            }

            val result = SparkProcessingTask.process(
                context = context,
                ffmpegProvider = viewModel.ffmpegProvider,
                inputVideoPath = inputPath,
                musicDownloadUrl = musicSourceUrl,
                isMuted = isOriginalMuted,
                filterId = selectedFilter,
                musicVolume = musicVolume,
                originalVolume = originalVolume,
                videoDurationMs = durationMs,
                isImage = isImage,
                imageDurationSec = imageDurationSec,
                trimStartMs = if (!isImage) trimStartMs else 0L,
                trimEndMs = if (!isImage && trimEndMs > 0L && trimEndMs < videoDurationMs) trimEndMs else 0L,
                musicOffsetSec = musicOffsetSec,
                onProgress = { p -> processingProgress = p }
            )

            isProcessing = false

            when (result) {
                is SparkProcessingTask.ProcessResult.Success -> {
                    onFinish(result.outputUri, sparkTitle.trim(), sparkDescription.trim(), sparkCategory.ifEmpty { null })
                }
                is SparkProcessingTask.ProcessResult.Error -> {
                    processingError = result.message
                    videoPlayer?.play()
                }
            }
        }
    }

    // ─── UI ───────────────────────────────────────────────────────────────────
    if (currentPage == 0) {
        // ── SEITE 0: EDITOR ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { containerWidth = it.width; containerHeight = it.height }
        ) {
            // 1. Medien-Vorschau (Bild oder Video) ─────────────────────────────
            if (isImage) {
                AsyncImage(
                    model = imageOrder.getOrNull(selectedImageIndex) ?: mediaUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = imgScale; scaleY = imgScale
                            translationX = imgOffset.x; translationY = imgOffset.y
                        }
                        .transformable(state = imgTransformState, canPan = { imgScale > 1f })
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            player = videoPlayer
                        }
                    },
                    update = { view -> view.player = videoPlayer },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. Filter-Overlay ──────────────────────────────────────────────────
            if (filterConfig.overlayAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(filterConfig.overlayColor.copy(alpha = filterConfig.overlayAlpha))
                )
            }
            if (selectedFilter == SparkFilterId.NOIR) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.18f)))
            }
            if (selectedFilter == SparkFilterId.CINEMATIC) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.50f)),
                                radius = 1200f
                            )
                        )
                )
            }

            // 2b. Text/Emoji-Overlay Canvas ──────────────────────────────────────
            if (isImage) {
                val spHandleR = with(androidx.compose.ui.platform.LocalDensity.current) { 11.dp.toPx() }
                val spHandleT = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }
                Canvas(
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(spActiveTool) {
                            if (spActiveTool != StickerEditorTool.BRUSH) detectTapGestures { pos ->
                                when (spActiveTool) {
                                    StickerEditorTool.TEXT -> { spPendingOffset = pos; spShowTextDialog = true; spTextInput = TextFieldValue("") }
                                    StickerEditorTool.EMOJI -> { spPendingOffset = pos; spShowEmojiPicker = true }
                                    else -> { val h = findOverlayAtPos(pos, spTextOverlays, spEmojiOverlays, sparkDensity); spSelectedId = if (h == spSelectedId) null else h }
                                }
                            }
                        }
                        .pointerInput(spActiveTool) {
                            if (spActiveTool != StickerEditorTool.BRUSH) detectDragGestures(
                                onDragStart = { pos ->
                                    val si = spSelectedId
                                    val info = if (si != null) getOverlayInfo(si, spTextOverlays, spEmojiOverlays, sparkDensity) else null
                                    if (info != null) {
                                        val hs = handlePositions(info); val br = hs[3]
                                        val spEdgeSnap = snapHandleOnEdge(pos, hs, edgePx = 22f, cornerProxPx = spHandleT * 1.8f)
                                        when {
                                            (pos - br).getDistance() < spHandleT || spEdgeSnap == 3 -> { spDragMode = OverlayDragMode.ROTATE; spDragCX = info.cx; spDragCY = info.cy; spDragAngle = atan2((pos.y-info.cy).toDouble(),(pos.x-info.cx).toDouble()).toFloat(); spDragRot = info.rotation }
                                            hs.take(3).any { (pos - it).getDistance() < spHandleT } || (spEdgeSnap in 0..2) -> { spDragMode = OverlayDragMode.SCALE; spDragCX = info.cx; spDragCY = info.cy; spDragDist = (pos-Offset(info.cx,info.cy)).getDistance().coerceAtLeast(1f); spDragScale = info.scale }
                                            isInsideOverlay(pos, info) -> spDragMode = OverlayDragMode.MOVE
                                            else -> { spSelectedId = null; spDragMode = OverlayDragMode.NONE }
                                        }
                                    } else {
                                        val h = findOverlayAtPos(pos, spTextOverlays, spEmojiOverlays, sparkDensity)
                                        if (h != null) { spSelectedId = h; spDragMode = OverlayDragMode.MOVE } else spDragMode = OverlayDragMode.NONE
                                    }
                                },
                                onDrag = { change, delta ->
                                    val si = spSelectedId ?: return@detectDragGestures
                                    when (spDragMode) {
                                        OverlayDragMode.MOVE -> { spTextOverlays = spTextOverlays.map { if (it.id==si) it.copy(offset=Offset(it.offset.x+delta.x,it.offset.y+delta.y)) else it }; spEmojiOverlays = spEmojiOverlays.map { if (it.id==si) it.copy(offset=Offset(it.offset.x+delta.x,it.offset.y+delta.y)) else it } }
                                        OverlayDragMode.ROTATE -> { val cur = atan2((change.position.y-spDragCY).toDouble(),(change.position.x-spDragCX).toDouble()).toFloat(); val nr = spDragRot+Math.toDegrees((cur-spDragAngle).toDouble()).toFloat(); spTextOverlays = spTextOverlays.map { if (it.id==si) it.copy(rotation=nr) else it }; spEmojiOverlays = spEmojiOverlays.map { if (it.id==si) it.copy(rotation=nr) else it } }
                                        OverlayDragMode.SCALE -> { val cd = (change.position-Offset(spDragCX,spDragCY)).getDistance().coerceAtLeast(1f); val ns = (spDragScale*(cd/spDragDist)).coerceIn(0.3f,6f); spTextOverlays = spTextOverlays.map { if (it.id==si) it.copy(scale=ns) else it }; spEmojiOverlays = spEmojiOverlays.map { if (it.id==si) it.copy(scale=ns) else it } }
                                        OverlayDragMode.NONE -> {}
                                    }
                                },
                                onDragEnd = { spDragMode = OverlayDragMode.NONE }
                            )
                        }
                ) {
                    val nc = drawContext.canvas.nativeCanvas
                    spTextOverlays.forEach { t ->
                        val p = android.graphics.Paint().apply { color=android.graphics.Color.argb((t.color.alpha*255).toInt(),(t.color.red*255).toInt(),(t.color.green*255).toInt(),(t.color.blue*255).toInt()); textSize=t.sizeSp*sparkDensity; typeface=typefaceForIndex(t.fontIndex); isAntiAlias=true; setShadowLayer(3f,1.5f,1.5f,android.graphics.Color.BLACK) }
                        val b = android.graphics.Rect(); p.getTextBounds(t.text,0,t.text.length,b); val bw=p.measureText(t.text)
                        val bp = android.graphics.Paint().apply { color=android.graphics.Color.argb(140,0,0,0); isAntiAlias=true }
                        nc.save(); nc.translate(t.offset.x,t.offset.y); nc.rotate(t.rotation); nc.scale(t.scale,t.scale)
                        nc.drawRoundRect(android.graphics.RectF(-bw/2-8f,b.top.toFloat()-4f,bw/2+8f,b.bottom.toFloat()+4f),8f,8f,bp)
                        nc.drawText(t.text,-b.exactCenterX(),-b.exactCenterY(),p); nc.restore()
                    }
                    spEmojiOverlays.forEach { e ->
                        val p = android.graphics.Paint().apply { textSize=e.sizeSp*sparkDensity; isAntiAlias=true }
                        val tw=p.measureText(e.emoji)/2f; val th=(-p.ascent()+p.descent())/2f-p.descent()
                        nc.save(); nc.translate(e.offset.x,e.offset.y); nc.rotate(e.rotation); nc.scale(e.scale,e.scale); nc.drawText(e.emoji,-tw,th,p); nc.restore()
                    }
                    val si = spSelectedId; val info = if (si!=null) getOverlayInfo(si,spTextOverlays,spEmojiOverlays,sparkDensity) else null
                    if (info != null) {
                        val hw=info.halfW*info.scale; val hh=info.halfH*info.scale
                        val bp2 = android.graphics.Paint().apply { color=0xCCFFFFFF.toInt(); style=android.graphics.Paint.Style.STROKE; strokeWidth=2f; pathEffect=android.graphics.DashPathEffect(floatArrayOf(10f,6f),0f); isAntiAlias=true }
                        nc.save(); nc.translate(info.cx,info.cy); nc.rotate(info.rotation); nc.drawRoundRect(android.graphics.RectF(-hw,-hh,hw,hh),6f,6f,bp2); nc.restore()
                        handlePositions(info).forEachIndexed { i, p2 ->
                            val fc = if (i==3) android.graphics.Color.rgb(255,87,34) else android.graphics.Color.rgb(72,199,142)
                            nc.drawCircle(p2.x,p2.y,spHandleR+3f,android.graphics.Paint().apply{color=0x99000000.toInt();isAntiAlias=true})
                            nc.drawCircle(p2.x,p2.y,spHandleR,android.graphics.Paint().apply{color=android.graphics.Color.WHITE;isAntiAlias=true})
                            nc.drawCircle(p2.x,p2.y,spHandleR*0.5f,android.graphics.Paint().apply{color=fc;isAntiAlias=true})
                        }
                    }
                }
            }

            // 3. Oberer Verlauf ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .align(Alignment.TopCenter)
            )

            // 4. Unterer Verlauf ─────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))))
                    .align(Alignment.BottomCenter)
            )

            // 5. Musik-Laufschrift ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = hasMusicSelected,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
            ) {
                val tickerTitle = when {
                    localMusicUri != null -> "♪ ${localMusicName ?: "Lokale Datei"}"
                    selectedTrack != null -> "${selectedTrack!!.title} · ${selectedTrack!!.artistName}"
                    selectedLetheTrack != null -> "${selectedLetheTrack!!.title} · ${selectedLetheTrack!!.artist}"
                    preSelectedSoundOriginId != null -> "♪ ${preSelectedMusicTitle ?: "Sound"}" + if (!preSelectedMusicArtist.isNullOrBlank()) " · $preSelectedMusicArtist" else ""
                    else -> null
                }
                tickerTitle?.let { title ->
                    MusicTickerBanner(
                        title = title,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
                    )
                }
            }

            // 6. Zurück-Button ───────────────────────────────────────────────────
            IconButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.spark_editor_cancel),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(6.dp)
                )
            }

            // 7. Rechte Seitenleiste ─────────────────────────────────────────────
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SparkEditorSidebarButton(
                    icon = Icons.Default.MusicNote,
                    label = when {
                        localMusicUri != null -> (localMusicName ?: "Lokal").take(8) + "…"
                        selectedTrack != null -> selectedTrack!!.title.take(8) + "…"
                        selectedLetheTrack != null -> selectedLetheTrack!!.title.take(8) + "…"
                        preSelectedSoundOriginId != null -> (preSelectedMusicTitle ?: "Sound").take(8) + "…"
                        else -> stringResource(R.string.spark_editor_music_label)
                    },
                    isActive = hasMusicSelected,
                    activeColor = Color(0xFFA8A800),
                    onClick = { showMusicSheet = true }
                )
                if (!isImage) {
                    SparkEditorSidebarButton(
                        icon = if (isOriginalMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        label = if (isOriginalMuted) stringResource(R.string.spark_editor_mute_label) else stringResource(R.string.spark_editor_unmute_label),
                        isActive = isOriginalMuted,
                        activeColor = Color(0xFFE53935),
                        onClick = { isOriginalMuted = !isOriginalMuted }
                    )
                }
                SparkEditorSidebarButton(
                    icon = Icons.Default.AutoFixHigh,
                    label = if (selectedFilter != SparkFilterId.NONE)
                        SparkFilterEngine.forId(selectedFilter).displayName
                    else stringResource(R.string.spark_editor_filter_label),
                    isActive = selectedFilter != SparkFilterId.NONE,
                    activeColor = Color(0xFF7B1FA2),
                    onClick = { showFilterStrip = !showFilterStrip }
                )
                if (isImage) {
                    SparkEditorSidebarButton(
                        icon = Icons.Default.TextFields,
                        label = stringResource(R.string.spark_editor_text_label),
                        isActive = spActiveTool == StickerEditorTool.TEXT,
                        activeColor = Color(0xFF2196F3),
                        onClick = { spActiveTool = if (spActiveTool == StickerEditorTool.TEXT) StickerEditorTool.NONE else StickerEditorTool.TEXT; spSelectedId = null }
                    )
                    SparkEditorSidebarButton(
                        icon = Icons.Default.EmojiEmotions,
                        label = stringResource(R.string.spark_editor_emoji_label),
                        isActive = spActiveTool == StickerEditorTool.EMOJI,
                        activeColor = Color(0xFFFF9800),
                        onClick = { spActiveTool = if (spActiveTool == StickerEditorTool.EMOJI) StickerEditorTool.NONE else StickerEditorTool.EMOJI; spSelectedId = null }
                    )
                }
            }

            // 7b. Zoom/Zuschneiden bestätigen ────────────────────────────────────
            if (isImage && (imgScale != 1f || imgOffset != Offset.Zero)) {
                Button(
                    onClick = {
                        scope.launch {
                            isCroppingBusy = true
                            val uri = imageOrder.getOrNull(selectedImageIndex) ?: mediaUri
                            val croppedUri = cropImageWithTransform(
                                context, uri, containerWidth, containerHeight, imgScale, imgOffset.x, imgOffset.y
                            )
                            if (croppedUri != null) {
                                imageOrder = imageOrder.toMutableList().also { it[selectedImageIndex] = croppedUri }
                            }
                            imgScale = 1f
                            imgOffset = Offset.Zero
                            isCroppingBusy = false
                        }
                    },
                    enabled = !isCroppingBusy,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 72.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8A800))
                ) {
                    if (isCroppingBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.spark_editor_confirm_crop), fontSize = 12.sp)
                    }
                }
            }

            // 8. Lautstärke-Slider (wenn Musik + Video) ──────────────────────────
            AnimatedVisibility(
                visible = hasMusicSelected && !isImage,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 320.dp)
                    .width(160.dp)
            ) {
                Column {
                    if (!isOriginalMuted) {
                        VolumeSliderRow(
                            label = stringResource(R.string.spark_editor_original_label),
                            value = originalVolume,
                            onValueChange = { originalVolume = it }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    VolumeSliderRow(
                        label = stringResource(R.string.spark_editor_music_label),
                        value = musicVolume,
                        onValueChange = { musicVolume = it }
                    )
                }
            }

            // 9. Unterer Bereich: Filter + Zeitleiste + Weiter-Button ─────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Filter-Leiste (ausklappbar)
                AnimatedVisibility(
                    visible = showFilterStrip,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    SparkFilterStrip(
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }

                // Trim-Zeitleiste (Video) oder Dauer-Regler (Einzelbild) oder Thumbnail-Strip (Multi-Image)
                if (isMultiImage) {
                    // Multi-Image: Thumbnail-Leiste zum Umsortieren der Bilder
                    SparkImageOrderStrip(
                        imageUris = imageOrder,
                        selectedIndex = selectedImageIndex,
                        onTap = { selectedImageIndex = it },
                        onMoveLeft = { idx ->
                            if (idx > 0) {
                                val m = imageOrder.toMutableList()
                                val tmp = m[idx - 1]; m[idx - 1] = m[idx]; m[idx] = tmp
                                imageOrder = m
                                selectedImageIndex = idx - 1
                            }
                        },
                        onMoveRight = { idx ->
                            if (idx < imageOrder.size - 1) {
                                val m = imageOrder.toMutableList()
                                val tmp = m[idx + 1]; m[idx + 1] = m[idx]; m[idx] = tmp
                                imageOrder = m
                                selectedImageIndex = idx + 1
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                } else if (isImage) {
                    // Einzelbild-Dauer-Regler: 1–60 Sek.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.spark_editor_display_duration), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text("${imageDurationSec}s", color = Color(0xFFA8A800), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(2.dp))
                        Slider(
                            value = imageDurationSec.toFloat(),
                            onValueChange = { imageDurationSec = it.roundToInt().coerceIn(1, 60) },
                            valueRange = 1f..60f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFA8A800),
                                activeTrackColor = Color(0xFFA8A800),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }
                } else if (videoDurationMs > 0L) {
                    SparkTrimTimeline(
                        durationMs = videoDurationMs,
                        trimStartMs = trimStartMs,
                        trimEndMs = trimEndMs,
                        currentPositionMs = playerPositionMs,
                        onTrimChange = { start, end ->
                            trimStartMs = start
                            trimEndMs = end
                        },
                        onSeekTo = { ms -> videoPlayer?.seekTo(ms) },
                        onDragStart = { isTrimDragging = true; videoPlayer?.pause() },
                        onDragEnd = { isTrimDragging = false; videoPlayer?.play() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 4.dp)
                    )
                } else {
                    // Lade-Platzhalter bis Dauer bekannt
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFFA8A800),
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Musik-Sync-Leiste (wenn Musik gewählt, nur bei Einzel-Bild oder Video)
                if (hasMusicSelected && !isMultiImage) {
                    val clipDurationMs = if (isImage) {
                        imageDurationSec * 1000L
                    } else {
                        if (trimEndMs > trimStartMs) trimEndMs - trimStartMs
                        else videoDurationMs
                    }
                    SparkMusicSyncStrip(
                        clipDurationMs = clipDurationMs,
                        musicDurationSec = selectedTrack?.duration ?: selectedLetheTrack?.durationSeconds ?: 0,
                        musicOffsetFrac = musicOffsetFrac,
                        onOffsetChange = { musicOffsetFrac = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )
                }

                // Sound-Label: Musik oder Original Sound
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (hasMusicSelected) Color(0xFFA8A800) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    val soundLabel = when {
                        selectedTrack != null -> buildString {
                            append(selectedTrack!!.artistName)
                            append(" - ")
                            append(selectedTrack!!.title)
                        }
                        selectedLetheTrack != null -> buildString {
                            append(selectedLetheTrack!!.artist)
                            append(" - ")
                            append(selectedLetheTrack!!.title)
                        }
                        localMusicUri != null -> buildString {
                            if (!localMusicArtist.isNullOrBlank()) {
                                append(localMusicArtist)
                                append(" - ")
                            }
                            append(localMusicTitle ?: localMusicName ?: "Lokale Datei")
                        }
                        preSelectedSoundOriginId != null -> buildString {
                            if (!preSelectedMusicArtist.isNullOrBlank()) {
                                append(preSelectedMusicArtist)
                                append(" - ")
                            }
                            append(preSelectedMusicTitle ?: "Sound")
                        }
                        else -> buildString {
                            if (sparkTitle.isNotBlank()) append(sparkTitle)
                            append(" (${stringResource(R.string.spark_editor_original_sound)})")
                        }
                    }
                    Text(
                        text = soundLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                // "Weiter" Button → Seite 1
                Button(
                    onClick = {
                        if (isImage && (spTextOverlays.isNotEmpty() || spEmojiOverlays.isNotEmpty())) {
                            // Overlays in alle Bilder einbrennen bevor wir zur Metadaten-Seite gehen
                            scope.launch {
                                val previewSize = IntSize(containerWidth, containerHeight)
                                val newOrder = imageOrder.map { uri ->
                                    burnOverlaysIntoImage(context, uri, previewSize, sparkDensity, spTextOverlays, spEmojiOverlays)
                                        ?: uri
                                }
                                imageOrder = newOrder
                                spTextOverlays = emptyList(); spEmojiOverlays = emptyList(); spSelectedId = null
                                currentPage = 1
                            }
                        } else {
                            currentPage = 1
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8A800))
                ) {
                    Text(
                        stringResource(R.string.spark_editor_continue_btn),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }

            // 10a. Text/Emoji-Dialoge ────────────────────────────────────────────
            if (spShowTextDialog) {
                val tColors = listOf(Color.White, Color.Black, Color.Red, Color(0xFFFF6600), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color(0xFFFF69B4))
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { spShowTextDialog = false },
                    title = { androidx.compose.material3.Text(stringResource(R.string.spark_editor_add_text_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            androidx.compose.material3.OutlinedTextField(value = spTextInput, onValueChange = { spTextInput = it }, singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth(), label = { androidx.compose.material3.Text(stringResource(R.string.spark_editor_add_text_field_label)) })
                            androidx.compose.material3.Text(stringResource(R.string.status_creation_color_label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(tColors) { color -> Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(color).then(if (color==spTextColor) Modifier.border(3.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary, CircleShape) else Modifier).pointerInput(Unit) { detectTapGestures { spTextColor = color } }) }
                            }
                            androidx.compose.material3.Text(stringResource(R.string.status_creation_font_label), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(FONT_NAMES.size) { i ->
                                    val sel = i == spTextFontIndex
                                    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant).border(if (sel) 2.dp else 0.dp, androidx.compose.material3.MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp).pointerInput(Unit) { detectTapGestures { spTextFontIndex = i } }, contentAlignment = Alignment.Center) {
                                        androidx.compose.material3.Text(FONT_NAMES[i], fontFamily = FONT_FAMILIES[i], fontWeight = FONT_WEIGHTS[i], fontStyle = FONT_STYLES[i], fontSize = 14.sp, color = if (sel) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { if (spTextInput.text.isNotBlank()) { spTextOverlays = spTextOverlays + StickerTextOverlay(text = spTextInput.text, offset = spPendingOffset, color = spTextColor, sizeSp = 28f, fontIndex = spTextFontIndex); spSelectedId = spTextOverlays.last().id; spActiveTool = StickerEditorTool.NONE }; spShowTextDialog = false }) { androidx.compose.material3.Text(stringResource(R.string.spark_editor_add_text_confirm)) } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { spShowTextDialog = false }) { androidx.compose.material3.Text(stringResource(R.string.spark_editor_add_text_cancel)) } }
                )
            }
            if (spShowEmojiPicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { spShowEmojiPicker = false },
                    title = { androidx.compose.material3.Text(stringResource(R.string.spark_editor_emoji_title)) },
                    text = { LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.heightIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        sparkGridItems(OVERLAY_EMOJIS) { emoji -> androidx.compose.material3.TextButton(onClick = { spEmojiOverlays = spEmojiOverlays + StickerEmojiOverlay(emoji=emoji,offset=spPendingOffset,sizeSp=36f); spSelectedId = spEmojiOverlays.last().id; spActiveTool = StickerEditorTool.NONE; spShowEmojiPicker = false }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { androidx.compose.material3.Text(emoji, fontSize = 22.sp) } }
                    } },
                    confirmButton = {},
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { spShowEmojiPicker = false }) { androidx.compose.material3.Text(stringResource(R.string.spark_editor_abbrechen)) } }
                )
            }

            // 10. Musik-Sheet ──────────────────────────────────────────────────
            if (showMusicSheet) {
                MusicPickerSheet(
                    musicViewModel = musicViewModel,
                    onDismiss = {
                        showMusicSheet = false
                        musicViewModel.stopPreview()
                    }
                )
            }
        }
    } else {
        // ── SEITE 1: METADATEN ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07131F))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header mit Zurück-Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentPage = 0; processingError = null }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.spark_editor_back_to_editor),
                            tint = Color.White
                        )
                    }
                    Text(
                        stringResource(R.string.spark_editor_add_details),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.spark_editor_title_label),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = sparkTitle,
                            onValueChange = { if (it.length <= 80) sparkTitle = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.spark_editor_title_placeholder),
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 15.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFA8A800),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                                cursorColor = Color(0xFFA8A800)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        Text(
                            "${sparkTitle.length}/80",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, end = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }

                    item {
                        Text(
                            stringResource(R.string.spark_editor_description_label),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = sparkDescription,
                            onValueChange = { if (it.length <= 300) sparkDescription = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.spark_editor_description_placeholder),
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            maxLines = 6,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFA8A800),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                                cursorColor = Color(0xFFA8A800)
                            )
                        )
                        Text(
                            "${sparkDescription.length}/300",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, end = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }

                    item {
                        Text(
                            stringResource(R.string.spark_editor_category_label),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = sparkCategoryExpanded,
                            onExpandedChange = { sparkCategoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = sparkCategory.ifEmpty { stringResource(R.string.spark_editor_no_category) },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sparkCategoryExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFA8A800),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                                    focusedTrailingIconColor = Color(0xFFA8A800),
                                    unfocusedTrailingIconColor = Color.White.copy(alpha = 0.5f)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = sparkCategoryExpanded,
                                onDismissRequest = { sparkCategoryExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.spark_editor_no_category)) },
                                    onClick = { sparkCategory = ""; sparkCategoryExpanded = false }
                                )
                                SPARK_CATEGORIES.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = { sparkCategory = cat; sparkCategoryExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        // Fehleranzeige
                        processingError?.let { err ->
                            Text(
                                text = "⚠ $err",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isMultiImage) {
                                    isUploadingImages = true
                                    imageUploadProgress = 0f
                                    val hasMusicFromUser = selectedTrack != null || selectedLetheTrack != null || localMusicUri != null
                                    val mTitle = selectedTrack?.title ?: selectedLetheTrack?.title ?: (if (localMusicUri != null) localMusicTitle ?: localMusicName else null) ?: if (!hasMusicFromUser) preSelectedMusicTitle else null
                                    val mArtist = selectedTrack?.artistName ?: selectedLetheTrack?.artist ?: (if (localMusicUri != null) localMusicArtist else null) ?: if (!hasMusicFromUser) preSelectedMusicArtist else null
                                    val mCover = selectedTrack?.coverUrl ?: selectedLetheTrack?.coverUrl ?: if (!hasMusicFromUser) preSelectedMusicCoverUrl else null
                                    val mUri = if (selectedLetheTrack != null) null else localMusicUri?.let { android.net.Uri.parse(it) }
                                    val mAudiusId = selectedTrack?.id
                                    val mAudiusUrl = when {
                                        selectedTrack != null -> musicViewModel.selectedTrackStreamUrl.value
                                        selectedLetheTrack != null -> selectedLetheTrack!!.audioUrl
                                        else -> null
                                    }
                                    val mAudiusDur = selectedTrack?.duration ?: selectedLetheTrack?.durationSeconds ?: 0
                                    val mSoundOriginId = if (!hasMusicFromUser) preSelectedSoundOriginId else null
                                    onUploadImageSpark(
                                        imageOrder,
                                        sparkTitle.trim(),
                                        sparkDescription.trim(),
                                        sparkCategory.ifEmpty { null },
                                        mTitle,
                                        mArtist,
                                        mCover,
                                        mSoundOriginId,
                                        mUri,
                                        mAudiusId,
                                        mAudiusUrl,
                                        mAudiusDur
                                    ) { progress ->
                                        imageUploadProgress = progress
                                        if (progress >= 1f) isUploadingImages = false
                                    }
                                } else {
                                    doProcess()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !isProcessing && !isUploadingImages,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA8A800),
                                disabledContainerColor = Color(0xFFA8A800).copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.spark_editor_create_button),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            stringResource(R.string.spark_editor_create_hint),
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Export-Overlay (Fullscreen) beim Verarbeiten / Hochladen
            AnimatedVisibility(
                visible = isProcessing || isUploadingImages,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(400)),
                modifier = Modifier.fillMaxSize()
            ) {
                SparkExportOverlay(progress = if (isMultiImage) imageUploadProgress else processingProgress)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SparkTrimTimeline – Trim-Handles für Video
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkTrimTimeline(
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    onTrimChange: (startMs: Long, endMs: Long) -> Unit,
    onSeekTo: (Long) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val primaryColor = Color(0xFFA8A800)
    val trackColor = Color.White.copy(alpha = 0.25f)
    val posColor = Color.White.copy(alpha = 0.9f)

    val latestTrimStart = rememberUpdatedState(trimStartMs)
    val latestTrimEnd = rememberUpdatedState(trimEndMs)
    val latestDuration = rememberUpdatedState(durationMs)
    val latestOnTrimChange = rememberUpdatedState(onTrimChange)
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val latestOnSeekTo = rememberUpdatedState(onSeekTo)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatSparkMs(trimStartMs), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            Text(
                "${formatSparkMs(trimEndMs - trimStartMs)} ${stringResource(R.string.spark_editor_clip_suffix)}",
                color = Color(0xFFA8A800).copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(formatSparkMs(durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val bw = barWidthPx
                        val dur = latestDuration.value
                        if (bw <= 0f || dur <= 0L) return@awaitEachGesture

                        fun msToX(ms: Long) = ms.toFloat() / dur * bw

                        val hitPx = 80f
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val sx = down.position.x

                        val ts0 = latestTrimStart.value
                        val te0 = latestTrimEnd.value
                        val lx = msToX(ts0)
                        val rx = msToX(te0)

                        val hitLeft = abs(sx - lx) < hitPx
                        val hitRight = !hitLeft && abs(sx - rx) < hitPx
                        val hitMid = !hitLeft && !hitRight && sx in (lx..rx)

                        if (hitLeft || hitRight || hitMid) {
                            latestOnDragStart.value()
                            var currentTs = ts0
                            var currentTe = te0

                            drag(down.id) { change ->
                                val dx = change.position.x - change.previousPosition.x
                                val dMs = (dx / bw * dur).toLong()
                                when {
                                    hitLeft -> {
                                        val ns = (currentTs + dMs).coerceIn(
                                            (currentTe - SPARK_MAX_TRIM_MS).coerceAtLeast(0L),
                                            currentTe - 1_000L
                                        )
                                        latestOnTrimChange.value(ns, currentTe)
                                        latestOnSeekTo.value(ns)
                                        currentTs = ns
                                    }
                                    hitRight -> {
                                        val ne = (currentTe + dMs).coerceIn(
                                            currentTs + 1_000L,
                                            (currentTs + SPARK_MAX_TRIM_MS).coerceAtMost(dur)
                                        )
                                        latestOnTrimChange.value(currentTs, ne)
                                        latestOnSeekTo.value(ne)
                                        currentTe = ne
                                    }
                                    hitMid -> {
                                        val wMs = currentTe - currentTs
                                        val ns = (currentTs + dMs).coerceIn(0L, dur - wMs)
                                        latestOnTrimChange.value(ns, ns + wMs)
                                        latestOnSeekTo.value(ns)
                                        currentTs = ns
                                        currentTe = ns + wMs
                                    }
                                }
                            }
                            latestOnDragEnd.value()
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.height / 2f
                val trackH = 6.dp.toPx()
                val handleW = 6.dp.toPx()
                val handleH = 44.dp.toPx()
                val r = CornerRadius(3.dp.toPx())
                val rHandle = CornerRadius(handleW / 2)

                fun ms2x(ms: Long) = if (durationMs > 0) ms.toFloat() / durationMs * size.width else 0f

                val lx = ms2x(trimStartMs)
                val rx = ms2x(trimEndMs)
                val px = ms2x(currentPositionMs)

                // Track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, cx - trackH / 2),
                    size = Size(size.width, trackH),
                    cornerRadius = r
                )
                // Ausschnitt-Highlight
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.4f),
                    topLeft = Offset(lx, cx - trackH / 2),
                    size = Size((rx - lx).coerceAtLeast(0f), trackH),
                    cornerRadius = r
                )
                // Linkes Handle
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(lx - handleW / 2, cx - handleH / 2),
                    size = Size(handleW, handleH),
                    cornerRadius = rHandle
                )
                // Rechtes Handle
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(rx - handleW / 2, cx - handleH / 2),
                    size = Size(handleW, handleH),
                    cornerRadius = rHandle
                )
                // Positions-Indikator
                if (currentPositionMs in trimStartMs..trimEndMs) {
                    drawLine(
                        color = posColor,
                        start = Offset(px, cx - 18.dp.toPx()),
                        end = Offset(px, cx + 18.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SparkMusicSyncStrip – Musik-Startposition auswählen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SparkMusicSyncStrip(
    clipDurationMs: Long,
    musicDurationSec: Int,
    musicOffsetFrac: Float,
    onOffsetChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val primaryColor = Color(0xFFA8A800)

    // Fensterbreite im Track (Anteil des Clips an der Musik-Gesamtdauer)
    val windowFrac = if (musicDurationSec > 0) {
        (clipDurationMs / 1000f / musicDurationSec.toFloat()).coerceIn(0.05f, 0.95f)
    } else 0.3f

    val latestOffset = rememberUpdatedState(musicOffsetFrac)
    val latestOnChange = rememberUpdatedState(onOffsetChange)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFA8A800),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.spark_editor_music_start), color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
            if (musicDurationSec > 0) {
                Text(
                    "⏱ ${formatSparkMs((musicOffsetFrac * musicDurationSec * 1000).toLong())}",
                    color = Color(0xFFA8A800),
                    fontSize = 10.sp
                )
            }
        }
        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val bw = barWidthPx
                        if (bw <= 0f) return@awaitEachGesture
                        val down = awaitFirstDown(requireUnconsumed = false)
                        drag(down.id) { change ->
                            val dx = change.position.x - change.previousPosition.x
                            val dFrac = dx / bw
                            val maxOffset = 1f - windowFrac
                            val newOffset = (latestOffset.value + dFrac).coerceIn(0f, maxOffset)
                            latestOnChange.value(newOffset)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val trackH = 8.dp.toPx()
                val r = CornerRadius(4.dp.toPx())

                // Hintergrund-Track
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(0f, cy - trackH / 2),
                    size = Size(size.width, trackH),
                    cornerRadius = r
                )

                // Clip-Fenster (gelb)
                val winX = musicOffsetFrac * size.width
                val winW = windowFrac * size.width
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.8f),
                    topLeft = Offset(winX, cy - trackH / 2),
                    size = Size(winW, trackH),
                    cornerRadius = r
                )

                // Linke Griff-Linie des Fensters
                val lineH = 20.dp.toPx()
                drawLine(
                    color = primaryColor,
                    start = Offset(winX, cy - lineH / 2),
                    end = Offset(winX, cy + lineH / 2),
                    strokeWidth = 3.dp.toPx()
                )
                drawLine(
                    color = primaryColor,
                    start = Offset(winX + winW, cy - lineH / 2),
                    end = Offset(winX + winW, cy + lineH / 2),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spark Export-Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkExportOverlay(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "exportPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val percent = (progress * 100).toInt()
    val statusText = when {
        progress < 0.25f -> "Musik wird geladen…"
        progress < 0.90f -> "Dein Spark wird erstellt…"
        else -> "Fast fertig…"
    }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(136.dp).graphicsLayer { alpha = 0.18f },
                    color = Color(0xFFA8A800), strokeWidth = 10.dp
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(128.dp).graphicsLayer { alpha = glowAlpha },
                    color = Color(0xFFA8A800),
                    trackColor = Color.White.copy(alpha = 0.10f),
                    strokeWidth = 8.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.spark_editor_uploading), color = Color(0xFFA8A800), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(statusText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFA8A800),
                trackColor = Color.White.copy(alpha = 0.15f)
            )
            Text(stringResource(R.string.spark_editor_do_not_close), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Musik-Laufschrift-Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MusicTickerBanner(
    title: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "musicTicker")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "tickerOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFFA8A800),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.offset(x = offsetX.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sidebar-Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkEditorSidebarButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.50f))
                .border(
                    width = if (isActive) 2.dp else 0.dp,
                    color = if (isActive) activeColor else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lautstärke-Slider
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VolumeSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text("$label: ${(value * 100).toInt()}%", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFA8A800),
                activeTrackColor = Color(0xFFA8A800),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter-Leiste
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkFilterStrip(
    selectedFilter: SparkFilterId,
    onFilterSelected: (SparkFilterId) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(SparkFilterEngine.allFilters) { config ->
            val isSelected = config.id == selectedFilter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onFilterSelected(config.id) }
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (config.overlayColor == Color.Transparent) Color(0xFF333333)
                            else config.overlayColor.copy(alpha = 0.85f)
                        )
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color(0xFFA8A800) else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFA8A800), modifier = Modifier.size(24.dp))
                    } else {
                        when (config.id) {
                            SparkFilterId.NONE -> Icon(
                                Icons.Default.Lightbulb, contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)
                            )
                            SparkFilterId.NOIR -> Box(
                                modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(Color.White, Color.Black)))
                            )
                            SparkFilterId.CINEMATIC -> Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.radialGradient(
                                        listOf(Color.Transparent, Color.Black.copy(0.7f)), radius = 120f
                                    )
                                )
                            )
                            else -> Box(
                                modifier = Modifier.size(30.dp).clip(CircleShape)
                                    .background(config.overlayColor.copy(alpha = 0.9f))
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = config.displayName,
                    color = if (isSelected) Color(0xFFA8A800) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bild-Reihenfolge-Leiste (Multi-Image Slideshow)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkImageOrderStrip(
    imageUris: List<Uri>,
    selectedIndex: Int,
    onTap: (Int) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(0xFFA8A800)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Reihenfolge (${imageUris.size} Bilder)",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
            Text(
                "Auswählen, dann ◀ ▶ zum Verschieben",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(imageUris.size) { idx ->
                val isSelected = idx == selectedIndex
                Box(contentAlignment = Alignment.BottomCenter) {
                    AsyncImage(
                        model = imageUris[idx],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) primaryColor else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onTap(idx) }
                    )
                    // Positionsnummer
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${idx + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    // Pfeil-Buttons für ausgewähltes Element
                    if (isSelected) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (idx > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor.copy(alpha = 0.85f))
                                        .clickable { onMoveLeft(idx) },
                                    contentAlignment = Alignment.Center
                                ) { Text("◀", color = Color.White, fontSize = 10.sp) }
                            } else {
                                Spacer(Modifier.size(22.dp))
                            }
                            if (idx < imageUris.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor.copy(alpha = 0.85f))
                                        .clickable { onMoveRight(idx) },
                                    contentAlignment = Alignment.Center
                                ) { Text("▶", color = Color.White, fontSize = 10.sp) }
                            } else {
                                Spacer(Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Musik-Auswahl BottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicPickerSheet(
    musicViewModel: MusicSearchViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tracks by musicViewModel.tracks.collectAsState()
    val isLoading by musicViewModel.isLoading.collectAsState()
    val error by musicViewModel.error.collectAsState()
    val searchQuery by musicViewModel.searchQuery.collectAsState()
    val selectedTrack by musicViewModel.selectedTrack.collectAsState()
    val playingTrackId by musicViewModel.playingTrackId.collectAsState()
    val localMusicUri by musicViewModel.localMusicUri.collectAsState()
    val localMusicName by musicViewModel.localMusicName.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val letheTracks by musicViewModel.letheTracks.collectAsState()
    val letheSearchQuery by musicViewModel.letheSearchQuery.collectAsState()
    val isLetheLoading by musicViewModel.isLetheLoading.collectAsState()
    val letheError by musicViewModel.letheError.collectAsState()
    val selectedLetheTrack by musicViewModel.selectedLetheTrack.collectAsState()

    val mp3Launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val name = context.contentResolver.query(
                    it, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "audio.mp3"
                // ID3-Tags via MediaMetadataRetriever auslesen
                var id3Title: String? = null
                var id3Artist: String? = null
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    mmr.setDataSource(context, it)
                    id3Title = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { t -> t.isNotBlank() }
                    id3Artist = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { a -> a.isNotBlank() }
                    mmr.release()
                } catch (_: Exception) {}
                musicViewModel.selectLocalMusic(it.toString(), name, id3Title, id3Artist)
                onDismiss()
            } catch (e: Exception) {
                android.util.Log.e("MusicPicker", "Fehler beim Verarbeiten der Musikdatei: ${e.message}")
                // Fallback: URI ohne Anzeigename verwenden
                try {
                    musicViewModel.selectLocalMusic(it.toString(), "audio.mp3")
                    onDismiss()
                } catch (_: Exception) {}
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.88f),
        containerColor = Color(0xFF0F1E35)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA8A800), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.spark_editor_music_choose), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, modifier = Modifier.weight(1f))
                if (selectedTrack != null || localMusicUri != null) {
                    Text(
                        "✕ Entfernen",
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { musicViewModel.clearSelection(); onDismiss() }.padding(8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (localMusicUri != null) Color(0xFFA8A800).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.07f))
                    .border(
                        1.dp,
                        if (localMusicUri != null) Color(0xFFA8A800).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { mp3Launcher.launch("audio/*") }
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (localMusicUri != null) Color(0xFFA8A800) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (localMusicUri != null) "Lokal: ${localMusicName ?: "Datei gewählt"}" else "Vom Gerät wählen",
                        color = if (localMusicUri != null) Color(0xFFA8A800) else Color.White,
                        fontWeight = if (localMusicUri != null) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (localMusicUri != null) "Tippe um eine andere Datei zu wählen" else "MP3 aus dem Datei-Manager öffnen",
                        color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp
                    )
                }
                if (localMusicUri != null) {
                    Text(
                        "✕", color = Color(0xFFFF5252), fontSize = 16.sp,
                        modifier = Modifier.clickable { musicViewModel.clearLocalMusic() }.padding(8.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White.copy(alpha = 0.08f))

            // ── Lethe Bibliothek ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA8A800), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.lethe_library_label), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = letheSearchQuery,
                onValueChange = { musicViewModel.updateLetheQuery(it) },
                placeholder = { Text(stringResource(R.string.lethe_library_search_placeholder), color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFA8A800),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    cursorColor = Color(0xFFA8A800)
                ),
                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide(); musicViewModel.updateLetheQuery(letheSearchQuery) })
            )
            Spacer(Modifier.height(4.dp))

            selectedLetheTrack?.let { track ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFA8A800).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFA8A800).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFA8A800), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${track.title}", color = Color(0xFFA8A800), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artist} · ${track.durationFormatted}", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                        Text(stringResource(R.string.spark_editor_ok), color = Color(0xFFA8A800), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
                    }
                }
            }

            if (isLetheLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFA8A800), modifier = Modifier.size(24.dp))
                }
            }
            letheError?.let { err ->
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                    Text(err, color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
            if (!isLetheLoading && letheError == null) {
                if (letheTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.lethe_library_empty), color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                } else {
                    // Max 3 Titel sichtbar, scrollbar wenn mehr vorhanden
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp)
                            .padding(bottom = 4.dp)
                    ) {
                        items(letheTracks, key = { it.track.id }) { uiState ->
                            LetheTrackListItem(
                                uiState = uiState,
                                isPlayingPreview = musicViewModel.playingTrackId.collectAsState().value == uiState.track.id,
                                onPlayToggle = { try { musicViewModel.toggleLethePreview(uiState.track) } catch (_: Exception) {} },
                                onSelect = {
                                    try {
                                        val wasSelected = musicViewModel.selectedLetheTrack.value?.id == uiState.track.id
                                        musicViewModel.selectLetheTrack(uiState.track)
                                        if (!wasSelected) {
                                            musicViewModel.stopPreview()
                                            onDismiss()
                                        }
                                    } catch (_: Exception) { onDismiss() }
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA8A800).copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.spark_editor_audius_library), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { musicViewModel.updateQuery(it) },
                placeholder = { Text(stringResource(R.string.spark_editor_music_search_placeholder), color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFA8A800),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    cursorColor = Color(0xFFA8A800)
                ),
                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide(); musicViewModel.updateQuery(searchQuery) })
            )

            Spacer(Modifier.height(8.dp))

            selectedTrack?.let { track ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFA8A800).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFA8A800).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFA8A800), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.spark_editor_track_selected, track.title), color = Color(0xFFA8A800), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${track.artistName} · ${track.durationFormatted}", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                        }
                        Text(stringResource(R.string.spark_editor_ok), color = Color(0xFFA8A800), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFA8A800))
                }
            }

            error?.let { err ->
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(err, color = Color(0xFFFF5252), fontSize = 13.sp)
                }
            }

            if (!isLoading && error == null) {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (tracks.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.spark_editor_no_tracks, searchQuery), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                            }
                        }
                    }
                    items(tracks, key = { it.track.id }) { uiState ->
                        MusicTrackListItem(
                            uiState = uiState,
                            isPlayingPreview = playingTrackId == uiState.track.id,
                            onPlayToggle = {
                                try { musicViewModel.togglePreviewPlayback(uiState.track) } catch (_: Exception) {}
                            },
                            onSelect = {
                                try {
                                    val wasSelected = musicViewModel.selectedTrack.value?.id == uiState.track.id
                                    musicViewModel.selectTrack(uiState.track)
                                    if (!wasSelected) {
                                        // Track wurde neu ausgewählt → Sheet schließen
                                        musicViewModel.stopPreview()
                                        onDismiss()
                                    }
                                } catch (_: Exception) {
                                    onDismiss()
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track-Listeneintrag (Audius)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MusicTrackListItem(
    uiState: MusicTrackUiState,
    isPlayingPreview: Boolean,
    onPlayToggle: () -> Unit,
    onSelect: () -> Unit
) {
    val track = uiState.track
    val isSelected = uiState.isSelected

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(if (isSelected) Color(0xFFA8A800).copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Cover Art ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) Color(0xFFA8A800).copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = track.coverUrl
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "${track.title} Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
                // Selected-Overlay über das Cover
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFA8A800).copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                // Fallback: Icon wenn kein Cover vorhanden
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFA8A800) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // ── Titel + Künstler + Genre ──────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isSelected) Color(0xFFA8A800) else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artistName,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val genre = track.genre
                if (!genre.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(genre, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // ── Dauer + Play-Button ───────────────────────────────────────────────
        Text(
            text = track.durationFormatted,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onPlayToggle, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlayingPreview) "Pause" else "Vorschau",
                tint = if (isPlayingPreview) Color(0xFFA8A800) else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track-Listeneintrag (Lethe Bibliothek)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LetheTrackListItem(
    uiState: LetheMusicTrackUiState,
    isPlayingPreview: Boolean,
    onPlayToggle: () -> Unit,
    onSelect: () -> Unit
) {
    val track = uiState.track
    val isSelected = uiState.isSelected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(if (isSelected) Color(0xFFA8A800).copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) Color(0xFFA8A800).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (!track.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = "${track.title} Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0xFFA8A800).copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Check else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFA8A800) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = if (isSelected) Color(0xFFA8A800) else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.artist, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!track.genre.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFA8A800).copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(track.genre, color = Color(0xFFA8A800).copy(alpha = 0.9f), fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(track.durationFormatted, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onPlayToggle, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlayingPreview) "Pause" else "Vorschau",
                tint = if (isPlayingPreview) Color(0xFFA8A800) else Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hilfsfunktionen
// ─────────────────────────────────────────────────────────────────────────────

internal fun formatSparkMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

/**
 * Löst eine Medien-URI (content:// oder file://) in einen absoluten Dateipfad auf.
 * Für Bilder wird eine .jpg-Erweiterung verwendet, für Videos .mp4.
 */
private fun resolveMediaPath(context: android.content.Context, uri: Uri, isImage: Boolean): String? {
    return try {
        if (uri.scheme == "file") return uri.path
        val ext = if (isImage) "jpg" else "mp4"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val cacheFile = java.io.File(context.cacheDir, "spark_input_${System.currentTimeMillis()}.$ext")
        inputStream.use { input ->
            java.io.FileOutputStream(cacheFile).use { out -> input.copyTo(out) }
        }
        cacheFile.absolutePath
    } catch (e: Exception) {
        timber.log.Timber.tag("LETHE_SPARK").e("resolveMediaPath Fehler: ${e.message}")
        null
    }
}

/**
 * Schneidet ein Bild entsprechend des aktuellen Zoom/Pan-Zustands zu.
 * Berechnet den sichtbaren Bildausschnitt aus den graphicsLayer-Transformationen
 * (ContentScale.Crop als Basis, dann userScale + userOffset) und speichert
 * das Ergebnis als JPEG im Cache.
 */
private suspend fun cropImageWithTransform(
    context: android.content.Context,
    uri: Uri,
    containerW: Int,
    containerH: Int,
    userScale: Float,
    userOffsetX: Float,
    userOffsetY: Float
): Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@withContext null

        val iW = bitmap.width.toFloat()
        val iH = bitmap.height.toFloat()
        val cW = containerW.toFloat()
        val cH = containerH.toFloat()
        if (cW <= 0f || cH <= 0f) return@withContext null

        // ContentScale.Crop: scale so the image fills the container
        val baseScale = maxOf(cW / iW, cH / iH)

        // Visible image region in image-pixel coordinates:
        // px_left  = iW/2 + (-cW/2 - userOffsetX) / (userScale * baseScale)
        // px_right = iW/2 + ( cW/2 - userOffsetX) / (userScale * baseScale)
        val halfCW = cW / 2f
        val halfCH = cH / 2f
        val totalScale = userScale * baseScale

        val pxLeft   = (iW / 2f + (-halfCW - userOffsetX) / totalScale).coerceIn(0f, iW)
        val pxRight  = (iW / 2f + ( halfCW - userOffsetX) / totalScale).coerceIn(0f, iW)
        val pyTop    = (iH / 2f + (-halfCH - userOffsetY) / totalScale).coerceIn(0f, iH)
        val pyBottom = (iH / 2f + ( halfCH - userOffsetY) / totalScale).coerceIn(0f, iH)

        val srcLeft   = pxLeft.toInt()
        val srcTop    = pyTop.toInt()
        val srcWidth  = (pxRight - pxLeft).toInt().coerceAtLeast(1)
        val srcHeight = (pyBottom - pyTop).toInt().coerceAtLeast(1)

        val cropped = Bitmap.createBitmap(bitmap, srcLeft, srcTop, srcWidth, srcHeight)
        bitmap.recycle()

        val file = java.io.File(context.cacheDir, "spark_crop_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out -> cropped.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        cropped.recycle()

        Uri.fromFile(file)
    } catch (e: Exception) {
        timber.log.Timber.tag("LETHE_SPARK").e("cropImageWithTransform Fehler: ${e.message}")
        null
    }
}

/**
 * Löst eine Audio-URI (content:// oder file://) in einen absoluten Dateipfad auf.
 * Kopiert content://-Dateien in den Cache (MP3-Endung).
 */
/**
 * Brennt Text/Emoji-Overlays in ein Bild und gibt die neue URI zurück.
 * Gibt null zurück wenn ein Fehler auftritt.
 */
private suspend fun burnOverlaysIntoImage(
    context: android.content.Context,
    imageUri: Uri,
    previewBoxSize: IntSize,
    density: Float,
    textOverlays: List<StickerTextOverlay>,
    emojiOverlays: List<StickerEmojiOverlay>
): Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val w = opts.outWidth.takeIf { it > 0 } ?: return@withContext null
        val h = opts.outHeight.takeIf { it > 0 } ?: return@withContext null

        val src = context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
        val bmp = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        src.recycle()
        val canvas = android.graphics.Canvas(bmp)

        val overlayBmp = buildOverlayBitmap(w, h, emptyList(), textOverlays, emojiOverlays, previewBoxSize, density)
        canvas.drawBitmap(overlayBmp, 0f, 0f, null)
        overlayBmp.recycle()

        val outFile = java.io.File(context.cacheDir, "spark_ov_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(outFile).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out) }
        bmp.recycle()
        Uri.fromFile(outFile)
    } catch (e: Exception) { null }
}

private fun resolveAudioPath(context: android.content.Context, uri: Uri): String? {
    return try {
        if (uri.scheme == "file") return uri.path
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val cacheFile = java.io.File(context.cacheDir, "spark_music_${System.currentTimeMillis()}.mp3")
        inputStream.use { input ->
            java.io.FileOutputStream(cacheFile).use { out -> input.copyTo(out) }
        }
        cacheFile.absolutePath
    } catch (e: Exception) {
        timber.log.Timber.tag("LETHE_SPARK").e("resolveAudioPath Fehler: ${e.message}")
        null
    }
}
