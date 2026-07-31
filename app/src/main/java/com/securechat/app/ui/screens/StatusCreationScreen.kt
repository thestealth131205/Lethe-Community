@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.securechat.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.media.MediaMetadataRetriever
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import java.util.UUID
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.securechat.app.media.FfmpegResult
import com.securechat.app.media.VideoOverlayRequest
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import com.securechat.app.ui.viewmodel.MusicSearchViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusCreationScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    initialImageUri: Uri? = null,
    initialVideoUri: Uri? = null
) {
    // Lese ausstehende Kamera-URI aus dem ViewModel (gesetzt von StatusScreen nach Aufnahme)
    val pendingStatusUri by viewModel.pendingStatusUri.collectAsState()
    val resolvedImageUri = initialImageUri ?: pendingStatusUri?.let { if (it.first == "image") it.second else null }
    val resolvedVideoUri = initialVideoUri ?: pendingStatusUri?.let { if (it.first == "video") it.second else null }
    LaunchedEffect(Unit) { viewModel.clearPendingStatusUri() }

    var selectedTab by remember { mutableIntStateOf(if (resolvedVideoUri != null) 1 else 0) }

    val tabLabels = listOf(
        stringResource(R.string.status_creation_tab_image),
        stringResource(R.string.status_creation_tab_video),
        stringResource(R.string.status_creation_tab_audio)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.status_creation_back_cd))
                    }
                },
                title = { Text(stringResource(R.string.status_creation_title), color = topBarTitleColor(), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabLabels.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ImageStatusTab(viewModel = viewModel, onNavigateBack = onNavigateBack, initialUri = resolvedImageUri)
                1 -> VideoStatusTab(viewModel = viewModel, onNavigateBack = onNavigateBack, initialUri = resolvedVideoUri)
                2 -> AudioStatusTab(viewModel = viewModel, onNavigateBack = onNavigateBack)
            }
        }
    }
}

// ─── DAUER-AUSWAHL (geteilt) ──────────────────────────────────────────────────

@Composable
private fun DurationSelector(
    selectedHours: Int,
    onSelect: (Int) -> Unit
) {
    val options = listOf(6, 12, 24, 48)
    val labels  = listOf("6 Std.", "12 Std.", "24 Std.", "48 Std.")

    Column {
        Text(
            stringResource(R.string.status_creation_visible_for),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { i, hours ->
                val selected = hours == selectedHours
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(hours) },
                    label = { Text(labels[i], fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

// ─── BILD-TAB ────────────────────────────────────────────────────────────────

private data class DrawingPathData(
    val points: List<Offset>,
    val color: Color,
    val strokeWidthPx: Float
)

/** Gespeicherter Bearbeitungszustand pro Bild im Multi-Status-Editor. */
private data class PerImageStatusState(
    val textOverlays: List<StickerTextOverlay> = emptyList(),
    val emojiOverlays: List<StickerEmojiOverlay> = emptyList(),
    val stickerOverlays: List<StickerImageOverlay> = emptyList(),
    val backgroundColor: Color? = null,
    val drawingPaths: List<DrawingPathData> = emptyList()
)

private enum class StatusCropHandle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT }

// Vordefinierte Hintergrundfarben für Text-/Farb-Status
private val statusBackgroundColors = listOf(
    Color(0xFFE53935), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5),
    Color(0xFF2196F3), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFF795548), Color(0xFF607D8B), Color(0xFF1A237E), Color(0xFF000000)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageStatusTab(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    initialUri: Uri? = null,
    musicViewModel: MusicSearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.isAdmin == true

    // Mehrfachauswahl: bis zu 5 Bilder
    val selectedImages = remember {
        mutableStateListOf<Uri>().also { if (initialUri != null) it.add(initialUri) }
    }
    var currentImageIndex by remember { mutableIntStateOf(0) }
    val savedPerState = remember { mutableMapOf<Int, PerImageStatusState>() }

    // Aktueller Bearbeitungszustand (für selectedImages[currentImageIndex])
    var textOverlays by remember { mutableStateOf<List<StickerTextOverlay>>(emptyList()) }
    var emojiOverlays by remember { mutableStateOf<List<StickerEmojiOverlay>>(emptyList()) }
    var stickerOverlays by remember { mutableStateOf<List<StickerImageOverlay>>(emptyList()) }
    var selectedBackgroundColor by remember { mutableStateOf<Color?>(null) }
    val completedDrawPaths = remember { mutableStateListOf<DrawingPathData>() }

    // Overlay-Interaktions-State
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var overlayDragMode by remember { mutableStateOf(OverlayDragMode.NONE) }
    var dragCenterX by remember { mutableFloatStateOf(0f) }
    var dragCenterY by remember { mutableFloatStateOf(0f) }
    var dragStartAngle by remember { mutableFloatStateOf(0f) }
    var dragStartRotation by remember { mutableFloatStateOf(0f) }
    var dragStartDist by remember { mutableFloatStateOf(1f) }
    var dragStartScale by remember { mutableFloatStateOf(1f) }
    var overlayActiveTool by remember { mutableStateOf(StickerEditorTool.NONE) }
    var showOverlayTextDialog by remember { mutableStateOf(false) }
    var overlayTextInput by remember { mutableStateOf(TextFieldValue("")) }
    var overlayTextColor by remember { mutableStateOf(Color.White) }
    var overlayTextFontIndex by remember { mutableIntStateOf(0) }
    var showOverlayEmojiPicker by remember { mutableStateOf(false) }
    var emojiPickerTab by remember { mutableIntStateOf(0) }
    var pendingOverlayOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    val myStickers by viewModel.myStickers.collectAsState()

    var durationHours by remember { mutableIntStateOf(24) }
    var showColorPicker by remember { mutableStateOf(false) }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    val isLoading by viewModel.isLoading.collectAsState()

    // Musik-State
    var showMusicSheet by remember { mutableStateOf(false) }
    val selectedTrack by musicViewModel.selectedTrack.collectAsState()
    val selectedLetheTrack by musicViewModel.selectedLetheTrack.collectAsState()
    val localMusicUri by musicViewModel.localMusicUri.collectAsState()
    val localMusicTitle by musicViewModel.localMusicTitle.collectAsState()
    val localMusicArtist by musicViewModel.localMusicArtist.collectAsState()
    val localMusicName by musicViewModel.localMusicName.collectAsState()
    val selectedTrackStreamUrl by musicViewModel.selectedTrackStreamUrl.collectAsState()
    var musicStartSec by remember { mutableIntStateOf(0) }
    var musicEndSec by remember { mutableIntStateOf(0) }

    // Link-State (externer Link / APK-Download, beim Tippen im Viewer extern geöffnet)
    var linkUrl by remember { mutableStateOf("") }
    var linkLabel by remember { mutableStateOf("") }
    var showLinkDialog by remember { mutableStateOf(false) }

    // Start/End zurücksetzen wenn Musik geändert wird
    val selectedTrackId = selectedTrack?.id
    val selectedLetheTrackId = selectedLetheTrack?.id
    LaunchedEffect(selectedTrackId, selectedLetheTrackId) {
        val dur = selectedTrack?.duration ?: selectedLetheTrack?.durationSeconds ?: 0
        if (dur > 0) {
            musicStartSec = 0
            musicEndSec = dur
        }
    }

    // Musik-Vorschau-Player (für Start/End-Handle-Feedback)
    val musicPreviewUrl: String? = when {
        selectedTrack != null -> selectedTrackStreamUrl
        selectedLetheTrack != null -> selectedLetheTrack?.audioUrl
        localMusicUri != null -> localMusicUri.toString()
        else -> null
    }
    val musicPreviewPlayerRef = remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(musicPreviewUrl) {
        val p = if (!musicPreviewUrl.isNullOrBlank()) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.parse(musicPreviewUrl)))
                prepare()
            }
        } else null
        musicPreviewPlayerRef.value?.release()
        musicPreviewPlayerRef.value = p
        onDispose {
            musicPreviewPlayerRef.value?.release()
            musicPreviewPlayerRef.value = null
        }
    }
    // Vorschau starten wenn Start/End geändert wird
    LaunchedEffect(musicStartSec, musicEndSec) {
        val p = musicPreviewPlayerRef.value ?: return@LaunchedEffect
        val clipMs = ((musicEndSec - musicStartSec).coerceAtLeast(1)) * 1000L
        p.seekTo(musicStartSec * 1000L)
        p.play()
        delay(clipMs)
        p.pause()
    }

    var drawingModeEnabled by remember { mutableStateOf(false) }
    var currentBrushColor by remember { mutableStateOf(Color.White) }
    var currentBrushSize by remember { mutableStateOf(12f) }
    var currentDrawPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showBrushColorPicker by remember { mutableStateOf(false) }

    // Crop state
    var cropModeEnabled by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(0f) }
    var cropBottom by remember { mutableStateOf(0f) }
    var cropInitialized by remember { mutableStateOf(false) }
    var draggingCropHandle by remember { mutableStateOf(StatusCropHandle.NONE) }
    var isApplyingCrop by remember { mutableStateOf(false) }

    // Zoom state
    var imageScale by remember { mutableFloatStateOf(1f) }
    var imageOffsetX by remember { mutableFloatStateOf(0f) }
    var imageOffsetY by remember { mutableFloatStateOf(0f) }

    // Position des verschiebbaren Zuschnitt-Panels (in Pixel relativ zur Vorschau-Box)
    var cropControlsPanelOffset by remember { mutableStateOf(Offset.Zero) }
    var cropControlsPanelInited by remember { mutableStateOf(false) }

    val imageUri = selectedImages.getOrNull(currentImageIndex)

    // Zwischen Bildern wechseln: aktuellen Zustand speichern, neuen laden
    fun switchToImage(newIdx: Int) {
        if (newIdx == currentImageIndex || newIdx !in selectedImages.indices) return
        savedPerState[currentImageIndex] = PerImageStatusState(
            textOverlays = textOverlays,
            emojiOverlays = emojiOverlays,
            stickerOverlays = stickerOverlays,
            backgroundColor = selectedBackgroundColor,
            drawingPaths = completedDrawPaths.toList()
        )
        val target = savedPerState[newIdx] ?: PerImageStatusState()
        textOverlays = target.textOverlays
        emojiOverlays = target.emojiOverlays
        stickerOverlays = target.stickerOverlays
        selectedBackgroundColor = target.backgroundColor
        completedDrawPaths.clear(); completedDrawPaths.addAll(target.drawingPaths)
        drawingModeEnabled = false
        selectedOverlayId = null
        cropModeEnabled = false
        cropInitialized = false
        imageScale = 1f
        imageOffsetX = 0f
        imageOffsetY = 0f
        currentImageIndex = newIdx
    }

    LaunchedEffect(cropModeEnabled, boxSize) {
        if (cropModeEnabled && boxSize.width > 1 && !cropInitialized) {
            cropLeft = 0f
            cropTop = 0f
            cropRight = boxSize.width.toFloat()
            cropBottom = boxSize.height.toFloat()
            cropInitialized = true
        }
    }

    var imageAspectRatio by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(imageUri) {
        if (imageUri == null) {
            imageAspectRatio = null
            imageScale = 1f
            imageOffsetX = 0f
            imageOffsetY = 0f
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                // EXIF-Orientierung prüfen: 90°/270° → Breite und Höhe tauschen
                val exifRotated = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    when (exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90,
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> true
                        else -> false
                    }
                } ?: false
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    imageAspectRatio = if (exifRotated) {
                        opts.outHeight.toFloat() / opts.outWidth.toFloat()
                    } else {
                        opts.outWidth.toFloat() / opts.outHeight.toFloat()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(cropModeEnabled, boxSize) {
        if (cropModeEnabled && boxSize.height > 1 && !cropControlsPanelInited) {
            cropControlsPanelOffset = Offset(8f, (boxSize.height - 80f).coerceAtLeast(0f))
            cropControlsPanelInited = true
        }
        if (!cropModeEnabled) cropControlsPanelInited = false
    }

    val hasContent = selectedImages.isNotEmpty() || selectedBackgroundColor != null

    // Mehrfachauswahl bis 5 Bilder
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val limited = uris.take(5)
        selectedImages.clear()
        savedPerState.clear()
        selectedImages.addAll(limited)
        currentImageIndex = 0
        textOverlays = emptyList()
        emojiOverlays = emptyList()
        stickerOverlays = emptyList()
        selectedBackgroundColor = null
        completedDrawPaths.clear()
        drawingModeEnabled = false
        selectedOverlayId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ─── Vorschau-Box ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(selectedBackgroundColor ?: MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { boxSize = it }
                .pointerInput(imageUri, boxSize, drawingModeEnabled) {
                    if (imageUri == null || drawingModeEnabled) return@pointerInput
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(false)
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }

                            when {
                                pointers.size >= 2 -> {
                                    // Pinch to zoom
                                    val (p1, p2) = pointers.take(2)
                                    val prevDist = ((p1.previousPosition - p2.previousPosition).getDistance())
                                    val curDist = ((p1.position - p2.position).getDistance())
                                    if (prevDist > 0f) {
                                        val zoom = curDist / prevDist
                                        imageScale = (imageScale * zoom).coerceIn(1f, 4f)
                                        // Reset offsets when approaching non-zoomed state
                                        if (imageScale <= 1.1f) {
                                            imageOffsetX = 0f
                                            imageOffsetY = 0f
                                        }
                                    }
                                    pointers.forEach { it.consume() }
                                }
                                pointers.size == 1 && imageScale > 1.05f -> {
                                    // Pan when zoomed
                                    val change = pointers[0]
                                    val maxOffsetX = (boxSize.width * (imageScale - 1) / 2)
                                    val maxOffsetY = (boxSize.height * (imageScale - 1) / 2)
                                    val delta = change.position - change.previousPosition
                                    imageOffsetX = (imageOffsetX + delta.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    imageOffsetY = (imageOffsetY + delta.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Bild (wenn ausgewählt)
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = imageScale,
                            scaleY = imageScale,
                            translationX = imageOffsetX,
                            translationY = imageOffsetY
                        ),
                    contentScale = ContentScale.Fit
                )
            } else if (selectedBackgroundColor == null) {
                // Platzhalter: kein Bild, keine Farbe
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        stringResource(R.string.status_creation_image_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Text/Emoji-Overlays mit Handles (Canvas)
            val handleRadiusPx = with(LocalDensity.current) { 11.dp.toPx() }
            val handleTouchPx  = with(LocalDensity.current) { 32.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(overlayActiveTool) {
                        if (overlayActiveTool != StickerEditorTool.BRUSH) detectTapGestures { pos ->
                            when (overlayActiveTool) {
                                StickerEditorTool.TEXT -> {
                                    pendingOverlayOffset = pos
                                    showOverlayTextDialog = true
                                    overlayTextInput = TextFieldValue("")
                                }
                                StickerEditorTool.EMOJI -> {
                                    pendingOverlayOffset = pos
                                    showOverlayEmojiPicker = true
                                }
                                else -> {
                                    val hit = findOverlayAtPos(pos, textOverlays, emojiOverlays, density)
                                    selectedOverlayId = if (hit == selectedOverlayId) null else hit
                                }
                            }
                        }
                    }
                    .pointerInput(overlayActiveTool) {
                        if (overlayActiveTool != StickerEditorTool.BRUSH) detectDragGestures(
                            onDragStart = { pos ->
                                val selId = selectedOverlayId
                                val info = if (selId != null) getOverlayInfo(selId, textOverlays, emojiOverlays, density) else null
                                if (info != null) {
                                    val handles = handlePositions(info)
                                    val brHandle = handles[3]
                                    val edgeSnap = snapHandleOnEdge(pos, handles, edgePx = 22f, cornerProxPx = handleTouchPx * 1.8f)
                                    when {
                                        (pos - brHandle).getDistance() < handleTouchPx || edgeSnap == 3 -> {
                                            overlayDragMode = OverlayDragMode.ROTATE
                                            dragCenterX = info.cx; dragCenterY = info.cy
                                            dragStartAngle = atan2((pos.y - info.cy).toDouble(), (pos.x - info.cx).toDouble()).toFloat()
                                            dragStartRotation = info.rotation
                                        }
                                        handles.take(3).any { (pos - it).getDistance() < handleTouchPx } || (edgeSnap in 0..2) -> {
                                            overlayDragMode = OverlayDragMode.SCALE
                                            dragCenterX = info.cx; dragCenterY = info.cy
                                            dragStartDist = (pos - Offset(info.cx, info.cy)).getDistance().coerceAtLeast(1f)
                                            dragStartScale = info.scale
                                        }
                                        isInsideOverlay(pos, info) -> overlayDragMode = OverlayDragMode.MOVE
                                        else -> { selectedOverlayId = null; overlayDragMode = OverlayDragMode.NONE }
                                    }
                                } else {
                                    val hit = findOverlayAtPos(pos, textOverlays, emojiOverlays, density)
                                    if (hit != null) { selectedOverlayId = hit; overlayDragMode = OverlayDragMode.MOVE }
                                    else overlayDragMode = OverlayDragMode.NONE
                                }
                            },
                            onDrag = { change, delta ->
                                val selId = selectedOverlayId ?: return@detectDragGestures
                                when (overlayDragMode) {
                                    OverlayDragMode.MOVE -> {
                                        textOverlays  = textOverlays.map  { if (it.id == selId) it.copy(offset = Offset(it.offset.x + delta.x, it.offset.y + delta.y)) else it }
                                        emojiOverlays = emojiOverlays.map { if (it.id == selId) it.copy(offset = Offset(it.offset.x + delta.x, it.offset.y + delta.y)) else it }
                                    }
                                    OverlayDragMode.ROTATE -> {
                                        val cur = atan2((change.position.y - dragCenterY).toDouble(), (change.position.x - dragCenterX).toDouble()).toFloat()
                                        val newRot = dragStartRotation + Math.toDegrees((cur - dragStartAngle).toDouble()).toFloat()
                                        textOverlays  = textOverlays.map  { if (it.id == selId) it.copy(rotation = newRot) else it }
                                        emojiOverlays = emojiOverlays.map { if (it.id == selId) it.copy(rotation = newRot) else it }
                                    }
                                    OverlayDragMode.SCALE -> {
                                        val curDist = (change.position - Offset(dragCenterX, dragCenterY)).getDistance().coerceAtLeast(1f)
                                        val newScale = (dragStartScale * (curDist / dragStartDist)).coerceIn(0.3f, 6f)
                                        textOverlays  = textOverlays.map  { if (it.id == selId) it.copy(scale = newScale) else it }
                                        emojiOverlays = emojiOverlays.map { if (it.id == selId) it.copy(scale = newScale) else it }
                                    }
                                    OverlayDragMode.NONE -> {}
                                }
                            },
                            onDragEnd = { overlayDragMode = OverlayDragMode.NONE }
                        )
                    }
            ) {
                val nc = drawContext.canvas.nativeCanvas
                // Text-Overlays
                textOverlays.forEach { t ->
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb((t.color.alpha*255).toInt(),(t.color.red*255).toInt(),(t.color.green*255).toInt(),(t.color.blue*255).toInt())
                        textSize = t.sizeSp * density; typeface = typefaceForIndex(t.fontIndex); isAntiAlias = true
                        setShadowLayer(3f, 1.5f, 1.5f, android.graphics.Color.BLACK)
                    }
                    val bounds = android.graphics.Rect()
                    paint.getTextBounds(t.text, 0, t.text.length, bounds)
                    nc.save(); nc.translate(t.offset.x, t.offset.y); nc.rotate(t.rotation); nc.scale(t.scale, t.scale)
                    // Rahmen hinter dem Text
                    val bw = paint.measureText(t.text); val bh = paint.descent() - paint.ascent()
                    val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.argb(140,0,0,0); isAntiAlias = true }
                    nc.drawRoundRect(android.graphics.RectF(-bw/2-8f, bounds.top.toFloat()-4f, bw/2+8f, bounds.bottom.toFloat()+4f), 8f, 8f, bgPaint)
                    nc.drawText(t.text, -bounds.exactCenterX(), -bounds.exactCenterY(), paint)
                    nc.restore()
                }
                // Emoji-Overlays
                emojiOverlays.forEach { e ->
                    val paint = android.graphics.Paint().apply { textSize = e.sizeSp * density; isAntiAlias = true }
                    val tw = paint.measureText(e.emoji) / 2f
                    val th = (-paint.ascent() + paint.descent()) / 2f - paint.descent()
                    nc.save(); nc.translate(e.offset.x, e.offset.y); nc.rotate(e.rotation); nc.scale(e.scale, e.scale)
                    nc.drawText(e.emoji, -tw, th, paint)
                    nc.restore()
                }
                // Auswahl-Handles
                val selId = selectedOverlayId
                val selInfo = if (selId != null) getOverlayInfo(selId, textOverlays, emojiOverlays, density) else null
                if (selInfo != null) {
                    val hw = selInfo.halfW * selInfo.scale; val hh = selInfo.halfH * selInfo.scale
                    val borderPaint = android.graphics.Paint().apply {
                        color = 0xCCFFFFFF.toInt(); style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f,6f),0f); isAntiAlias = true
                    }
                    nc.save(); nc.translate(selInfo.cx, selInfo.cy); nc.rotate(selInfo.rotation)
                    nc.drawRoundRect(android.graphics.RectF(-hw,-hh,hw,hh),6f,6f,borderPaint)
                    nc.restore()
                    val handles = handlePositions(selInfo)
                    handles.forEachIndexed { i, p ->
                        val fill = if (i==3) android.graphics.Color.rgb(255,87,34) else android.graphics.Color.rgb(72,199,142)
                        nc.drawCircle(p.x,p.y,handleRadiusPx+3f, android.graphics.Paint().apply{color=0x99000000.toInt();isAntiAlias=true})
                        nc.drawCircle(p.x,p.y,handleRadiusPx, android.graphics.Paint().apply{color=android.graphics.Color.WHITE;isAntiAlias=true})
                        nc.drawCircle(p.x,p.y,handleRadiusPx*0.5f, android.graphics.Paint().apply{color=fill;isAntiAlias=true})
                    }
                }
            }

            // Sticker-Bild-Overlays (über dem Zeichencanvas)
            val localDensityForStickers = LocalDensity.current
            stickerOverlays.forEach { sticker ->
                val sizePx = sticker.sizeDp * localDensityForStickers.density * sticker.scale
                Box(
                    modifier = Modifier
                        .absoluteOffset { IntOffset((sticker.offset.x - sizePx / 2).toInt(), (sticker.offset.y - sizePx / 2).toInt()) }
                        .size((sticker.sizeDp * sticker.scale).dp)
                        .rotate(sticker.rotation)
                        .pointerInput(sticker.id) {
                            detectDragGestures { _, delta ->
                                stickerOverlays = stickerOverlays.map {
                                    if (it.id == sticker.id) it.copy(offset = Offset(it.offset.x + delta.x, it.offset.y + delta.y)) else it
                                }
                            }
                        }
                ) {
                    coil.compose.AsyncImage(
                        model = if (sticker.url.startsWith("http")) sticker.url else "https://letheapp.de${sticker.url}",
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                            .clickable { stickerOverlays = stickerOverlays.filter { it.id != sticker.id } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }

            // Drawing canvas overlay (when background color or image is selected)
            if (selectedBackgroundColor != null || imageUri != null) {
                // Koordinaten-Transformation: Bildschirm → Bild-Raum (inverse graphicsLayer-Transform)
                fun Offset.toImageSpace(): Offset {
                    val cx = boxSize.width / 2f
                    val cy = boxSize.height / 2f
                    return Offset(
                        (x - cx - imageOffsetX) / imageScale + cx,
                        (y - cy - imageOffsetY) / imageScale + cy
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = imageScale,
                            scaleY = imageScale,
                            translationX = imageOffsetX,
                            translationY = imageOffsetY
                        )
                        .let { mod ->
                            if (drawingModeEnabled) {
                                mod.pointerInput(completedDrawPaths) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        currentDrawPoints = listOf(down.position.toImageSpace())
                                        drag(down.id) { change ->
                                            change.consume()
                                            currentDrawPoints = currentDrawPoints + change.position.toImageSpace()
                                        }
                                        if (currentDrawPoints.size > 1) {
                                            completedDrawPaths.add(
                                                DrawingPathData(currentDrawPoints, currentBrushColor, currentBrushSize)
                                            )
                                        }
                                        currentDrawPoints = emptyList()
                                    }
                                }
                            } else mod
                        }
                ) {
                    val allPaths = completedDrawPaths.toList() +
                        if (currentDrawPoints.size > 1)
                            listOf(DrawingPathData(currentDrawPoints, currentBrushColor, currentBrushSize))
                        else emptyList()
                    allPaths.forEach { pathData ->
                        if (pathData.points.size > 1) {
                            val path = androidx.compose.ui.graphics.Path()
                            path.moveTo(pathData.points[0].x, pathData.points[0].y)
                            pathData.points.drop(1).forEach { pt -> path.lineTo(pt.x, pt.y) }
                            drawPath(
                                path = path,
                                color = pathData.color,
                                style = Stroke(
                                    width = pathData.strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }

            // Crop-Canvas-Overlay (wenn Zuschnitt-Modus aktiv und Bild gewählt)
            if (cropModeEnabled && imageUri != null) {
                val cropHandleTouchPx = with(LocalDensity.current) { 44.dp.toPx() }
                val cropHandleVisualRadius = with(LocalDensity.current) { 20.dp.toPx() }
                val cropPrimaryColor = MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val edgePx = cropHandleTouchPx * 0.6f
                                    val cornerProxPx = cropHandleTouchPx * 1.8f
                                    val handles = listOf(
                                        Offset(cropLeft, cropTop),
                                        Offset(cropRight, cropTop),
                                        Offset(cropLeft, cropBottom),
                                        Offset(cropRight, cropBottom)
                                    )
                                    draggingCropHandle = when {
                                        (offset - handles[0]).getDistance() < cropHandleTouchPx -> StatusCropHandle.TOP_LEFT
                                        (offset - handles[1]).getDistance() < cropHandleTouchPx -> StatusCropHandle.TOP_RIGHT
                                        (offset - handles[2]).getDistance() < cropHandleTouchPx -> StatusCropHandle.BOTTOM_LEFT
                                        (offset - handles[3]).getDistance() < cropHandleTouchPx -> StatusCropHandle.BOTTOM_RIGHT
                                        kotlin.math.abs(offset.y - cropTop) < edgePx && offset.x in cropLeft..cropRight -> {
                                            val snap = snapHandleOnEdge(offset, handles, edgePx, cornerProxPx)
                                            when (snap) { 0 -> StatusCropHandle.TOP_LEFT; 1 -> StatusCropHandle.TOP_RIGHT; else -> StatusCropHandle.TOP }
                                        }
                                        kotlin.math.abs(offset.y - cropBottom) < edgePx && offset.x in cropLeft..cropRight -> {
                                            val snap = snapHandleOnEdge(offset, handles, edgePx, cornerProxPx)
                                            when (snap) { 2 -> StatusCropHandle.BOTTOM_LEFT; 3 -> StatusCropHandle.BOTTOM_RIGHT; else -> StatusCropHandle.BOTTOM }
                                        }
                                        kotlin.math.abs(offset.x - cropLeft) < edgePx && offset.y in cropTop..cropBottom -> {
                                            val snap = snapHandleOnEdge(offset, handles, edgePx, cornerProxPx)
                                            when (snap) { 0 -> StatusCropHandle.TOP_LEFT; 2 -> StatusCropHandle.BOTTOM_LEFT; else -> StatusCropHandle.LEFT }
                                        }
                                        kotlin.math.abs(offset.x - cropRight) < edgePx && offset.y in cropTop..cropBottom -> {
                                            val snap = snapHandleOnEdge(offset, handles, edgePx, cornerProxPx)
                                            when (snap) { 1 -> StatusCropHandle.TOP_RIGHT; 3 -> StatusCropHandle.BOTTOM_RIGHT; else -> StatusCropHandle.RIGHT }
                                        }
                                        else -> StatusCropHandle.NONE
                                    }
                                },
                                onDrag = { _, delta ->
                                    val minSize = 60f
                                    val maxW = boxSize.width.toFloat()
                                    val maxH = boxSize.height.toFloat()
                                    when (draggingCropHandle) {
                                        StatusCropHandle.TOP_LEFT -> {
                                            cropLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                            cropTop = (cropTop + delta.y).coerceIn(0f, cropBottom - minSize)
                                        }
                                        StatusCropHandle.TOP_RIGHT -> {
                                            cropRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                            cropTop = (cropTop + delta.y).coerceIn(0f, cropBottom - minSize)
                                        }
                                        StatusCropHandle.BOTTOM_LEFT -> {
                                            cropLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                            cropBottom = (cropBottom + delta.y).coerceIn(cropTop + minSize, maxH)
                                        }
                                        StatusCropHandle.BOTTOM_RIGHT -> {
                                            cropRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                            cropBottom = (cropBottom + delta.y).coerceIn(cropTop + minSize, maxH)
                                        }
                                        StatusCropHandle.TOP -> cropTop = (cropTop + delta.y).coerceIn(0f, cropBottom - minSize)
                                        StatusCropHandle.BOTTOM -> cropBottom = (cropBottom + delta.y).coerceIn(cropTop + minSize, maxH)
                                        StatusCropHandle.LEFT -> cropLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                        StatusCropHandle.RIGHT -> cropRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                        StatusCropHandle.NONE -> Unit
                                    }
                                },
                                onDragEnd = { draggingCropHandle = StatusCropHandle.NONE }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val dim = Color.Black.copy(alpha = 0.55f)
                    drawRect(dim, topLeft = Offset(0f, 0f), size = Size(w, cropTop))
                    drawRect(dim, topLeft = Offset(0f, cropBottom), size = Size(w, h - cropBottom))
                    drawRect(dim, topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropBottom - cropTop))
                    drawRect(dim, topLeft = Offset(cropRight, cropTop), size = Size(w - cropRight, cropBottom - cropTop))
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(cropLeft, cropTop),
                        size = Size(cropRight - cropLeft, cropBottom - cropTop),
                        style = Stroke(width = 2f)
                    )
                    val thirdW = (cropRight - cropLeft) / 3f
                    val thirdH = (cropBottom - cropTop) / 3f
                    for (i in 1..2) {
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft + thirdW * i, cropTop), Offset(cropLeft + thirdW * i, cropBottom), strokeWidth = 1f)
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft, cropTop + thirdH * i), Offset(cropRight, cropTop + thirdH * i), strokeWidth = 1f)
                    }
                    listOf(
                        Offset(cropLeft, cropTop),
                        Offset(cropRight, cropTop),
                        Offset(cropLeft, cropBottom),
                        Offset(cropRight, cropBottom)
                    ).forEach { corner ->
                        drawCircle(Color.Black.copy(alpha = 0.35f), radius = cropHandleVisualRadius + 5f, center = corner)
                        drawCircle(Color.White, radius = cropHandleVisualRadius, center = corner)
                        drawCircle(Color.Black.copy(alpha = 0.5f), radius = cropHandleVisualRadius, center = corner, style = Stroke(2.5f))
                        drawCircle(cropPrimaryColor, radius = cropHandleVisualRadius * 0.35f, center = corner)
                    }
                }
            }

            // ─── Zuschnitt-Steuerung-Panel (verschiebbar) ─────────────────────
            if (cropModeEnabled && imageUri != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(cropControlsPanelOffset.x.toInt(), cropControlsPanelOffset.y.toInt()) }
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.80f))
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { _, dragAmount ->
                                        cropControlsPanelOffset = Offset(
                                            cropControlsPanelOffset.x,
                                            (cropControlsPanelOffset.y + dragAmount.y).coerceIn(0f, (boxSize.height - 60).toFloat())
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.45f)))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                cropLeft = 0f; cropTop = 0f
                                cropRight = boxSize.width.toFloat()
                                cropBottom = boxSize.height.toFloat()
                            }) {
                                Text(stringResource(R.string.status_creation_reset_crop), color = Color.White)
                            }
                            Button(
                                onClick = {
                                    if (!cropInitialized || isApplyingCrop) return@Button
                                    isApplyingCrop = true
                                    val snapLeft = cropLeft
                                    val snapTop = cropTop
                                    val snapRight = cropRight
                                    val snapBottom = cropBottom
                                    val snapBoxSize = boxSize
                                    val snapUri = selectedImages.getOrNull(currentImageIndex) ?: run {
                                        isApplyingCrop = false; return@Button
                                    }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val newUri = try {
                                            val stream = context.contentResolver.openInputStream(snapUri)
                                            val orig = if (stream != null) android.graphics.BitmapFactory.decodeStream(stream).also { stream.close() } else null
                                            if (orig != null && snapBoxSize.width > 1) {
                                                val sx = orig.width.toFloat() / snapBoxSize.width.toFloat()
                                                val sy = orig.height.toFloat() / snapBoxSize.height.toFloat()
                                                val bx = (snapLeft * sx).toInt().coerceIn(0, orig.width - 1)
                                                val by_ = (snapTop * sy).toInt().coerceIn(0, orig.height - 1)
                                                val bw = ((snapRight - snapLeft) * sx).toInt().coerceIn(1, orig.width - bx)
                                                val bh = ((snapBottom - snapTop) * sy).toInt().coerceIn(1, orig.height - by_)
                                                val cropped = android.graphics.Bitmap.createBitmap(orig, bx, by_, bw, bh)
                                                val dir = java.io.File(context.cacheDir, "status_crop").also { it.mkdirs() }
                                                val file = java.io.File(dir, "crop_${System.currentTimeMillis()}.jpg")
                                                file.outputStream().use { cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 98, it) }
                                                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            } else null
                                        } catch (_: Exception) { null }
                                        withContext(Dispatchers.Main) {
                                            if (newUri != null) {
                                                selectedImages[currentImageIndex] = newUri
                                                cropInitialized = false
                                                imageAspectRatio = null
                                                cropModeEnabled = false
                                            }
                                            isApplyingCrop = false
                                        }
                                    }
                                },
                                enabled = !isApplyingCrop
                            ) {
                                if (isApplyingCrop) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.status_creation_apply_crop))
                                }
                            }
                        }
                    }
                }
            }

            // Button: Hintergrundfarbe auswählen (links unten)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showColorPicker = !showColorPicker },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Palette, contentDescription = "Hintergrundfarbe", tint = Color.White)
            }

            // Button: Zeichenmodus ein-/ausschalten (erscheint wenn Hintergrundfarbe oder Bild gewählt)
            if (selectedBackgroundColor != null || imageUri != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 64.dp, bottom = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (drawingModeEnabled) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.45f)
                        )
                        .clickable { drawingModeEnabled = !drawingModeEnabled },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Zeichnen", tint = Color.White)
                }
            }

            // Button: Zuschneiden (erscheint wenn Bild ausgewählt)
            if (imageUri != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 116.dp, bottom = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (cropModeEnabled) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.45f)
                        )
                        .clickable {
                            cropModeEnabled = !cropModeEnabled
                            if (cropModeEnabled) {
                                drawingModeEnabled = false
                                overlayActiveTool = StickerEditorTool.NONE
                                selectedOverlayId = null
                                if (boxSize.width > 1 && !cropInitialized) {
                                    cropLeft = 0f; cropTop = 0f
                                    cropRight = boxSize.width.toFloat()
                                    cropBottom = boxSize.height.toFloat()
                                    cropInitialized = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Crop, contentDescription = "Zuschneiden", tint = Color.White)
                }
            }

            // Button: Link hinzufügen (rechts unten, links neben Musik-Button)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 116.dp, bottom = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (linkUrl.isNotBlank()) Color(0xFF2196F3)
                        else Color.Black.copy(alpha = 0.45f)
                    )
                    .clickable { showLinkDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Link, contentDescription = "Link", tint = Color.White)
            }

            // Button: Musik hinzufügen (rechts unten, neben Bild-FAB)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 64.dp, bottom = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (selectedTrack != null || selectedLetheTrack != null || localMusicUri != null) Color(0xFFA8A800)
                        else Color.Black.copy(alpha = 0.45f)
                    )
                    .clickable { showMusicSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = "Musik", tint = Color.White)
            }

            // Button: Bild auswählen (rechts unten)
            FloatingActionButton(
                onClick = { imageLauncher.launch("image/*") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Image, contentDescription = "Bild wählen", modifier = Modifier.size(22.dp))
            }
        }

        // ─── Thumbnail-Strip (bei Mehrfachauswahl) ─────────────────────
        if (selectedImages.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(selectedImages.toList()) { idx, uri ->
                    val isActive = idx == currentImageIndex
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isActive) 2.5.dp else 1.dp,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { switchToImage(idx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary
                                    else Color.Black.copy(alpha = 0.55f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ─── Musik-Chip (wenn Musik ausgewählt) ────────────────────────
        val musicLabel = when {
            selectedTrack != null -> buildString {
                append(selectedTrack!!.title)
                if (!selectedTrack!!.artistName.isNullOrBlank()) append(" · ${selectedTrack!!.artistName}")
            }
            selectedLetheTrack != null -> buildString {
                append(selectedLetheTrack!!.title)
                if (selectedLetheTrack!!.artist.isNotBlank()) append(" · ${selectedLetheTrack!!.artist}")
            }
            localMusicUri != null -> localMusicTitle?.let { t ->
                buildString {
                    append(t)
                    if (!localMusicArtist.isNullOrBlank()) append(" · $localMusicArtist")
                }
            } ?: localMusicName ?: "Lokale Datei"
            else -> null
        }
        if (musicLabel != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFA8A800).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFFA8A800).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { showMusicSheet = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFA8A800), modifier = Modifier.size(16.dp))
                Text(musicLabel, color = Color(0xFFA8A800), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Icon(Icons.Default.Close, contentDescription = "Musik entfernen", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp).clickable { musicViewModel.clearSelection(); musicViewModel.clearLocalMusic() })
            }

            // Musik-Trim (Start- und Endpunkt im Track wählen)
            val trackDurSec = selectedTrack?.duration ?: selectedLetheTrack?.durationSeconds ?: 0
            if (trackDurSec > 0) {
                StatusMusicTrimStrip(
                    musicDurationSec = trackDurSec,
                    startSec = musicStartSec,
                    endSec = musicEndSec,
                    onTrimChange = { start, end ->
                        musicStartSec = start
                        musicEndSec = end
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }
        }

        // ─── Link-Chip (wenn Link gesetzt) ─────────────────────────────
        if (linkUrl.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2196F3).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF2196F3).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .clickable { showLinkDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                Text(
                    text = linkLabel.ifBlank { linkUrl },
                    color = Color(0xFF2196F3),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Link entfernen",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { linkUrl = ""; linkLabel = "" }
                )
            }
        }

        // ─── Text + Emoji Overlay-Werkzeuge ───────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text-Tool
            val textActive = overlayActiveTool == StickerEditorTool.TEXT
            Box(
                modifier = Modifier
                    .weight(1f).height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (textActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        overlayActiveTool = if (textActive) StickerEditorTool.NONE else StickerEditorTool.TEXT
                        selectedOverlayId = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.TextFields, null, modifier = Modifier.size(18.dp), tint = if (textActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.spark_editor_text_label), fontSize = 13.sp, color = if (textActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Emoji-Tool
            val emojiActive = overlayActiveTool == StickerEditorTool.EMOJI
            Box(
                modifier = Modifier
                    .weight(1f).height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (emojiActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        overlayActiveTool = if (emojiActive) StickerEditorTool.NONE else StickerEditorTool.EMOJI
                        selectedOverlayId = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.EmojiEmotions, null, modifier = Modifier.size(18.dp), tint = if (emojiActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.status_creation_emoji_tab), fontSize = 13.sp, color = if (emojiActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Undo
            if (textOverlays.isNotEmpty() || emojiOverlays.isNotEmpty()) {
                IconButton(onClick = {
                    selectedOverlayId = null
                    if (emojiOverlays.isNotEmpty()) emojiOverlays = emojiOverlays.dropLast(1)
                    else if (textOverlays.isNotEmpty()) textOverlays = textOverlays.dropLast(1)
                }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (overlayActiveTool == StickerEditorTool.TEXT || overlayActiveTool == StickerEditorTool.EMOJI) {
            Text(
                if (overlayActiveTool == StickerEditorTool.TEXT) stringResource(R.string.status_creation_text_add_hint)
                else stringResource(R.string.status_creation_emoji_add_hint),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selectedOverlayId != null && overlayActiveTool == StickerEditorTool.NONE) {
            Text(
                stringResource(R.string.status_creation_overlay_drag_hint),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ─── Farbpalette-Leiste ────────────────────────────────────────
        if (showColorPicker) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (selectedBackgroundColor == null) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .clickable { selectedBackgroundColor = null },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Kein Hintergrund", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                statusBackgroundColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selectedBackgroundColor == color) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .clickable { selectedBackgroundColor = color; showColorPicker = false }
                    )
                }
            }
        }

        // ─── Zeichen-Tools-Leiste ──────────────────────────────────────
        if ((selectedBackgroundColor != null || imageUri != null) && drawingModeEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { if (completedDrawPaths.isNotEmpty()) completedDrawPaths.removeLast() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Rückgängig", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { completedDrawPaths.clear() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Alles löschen", tint = MaterialTheme.colorScheme.error)
                    }
                    Slider(
                        value = currentBrushSize,
                        onValueChange = { currentBrushSize = it },
                        valueRange = 4f..40f,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentBrushColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showBrushColorPicker = !showBrushColorPicker }
                    )
                }
                if (showBrushColorPicker) {
                    val brushColors = listOf(
                        Color.White, Color.Black,
                        Color(0xFFFFEB3B), Color(0xFFFF5722), Color(0xFF4CAF50),
                        Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF9C27B0),
                        Color(0xFF00BCD4), Color(0xFFFF9800)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        brushColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (currentBrushColor == color) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .clickable { currentBrushColor = color; showBrushColorPicker = false }
                            )
                        }
                    }
                }
            }
        }

        // ─── Dauer-Auswahl ─────────────────────────────────────────────
        DurationSelector(selectedHours = durationHours, onSelect = { durationHours = it })

        // ─── Veröffentlichen ───────────────────────────────────────────
        val publishAction: (Boolean) -> Unit = { asLetheTeam ->
            if (hasContent) {
                savedPerState[currentImageIndex] = PerImageStatusState(
                    textOverlays = textOverlays,
                    emojiOverlays = emojiOverlays,
                    backgroundColor = selectedBackgroundColor,
                    drawingPaths = completedDrawPaths.toList()
                )
                val snapBoxSize = boxSize
                val snapDensity = density
                val snapDuration = durationHours
                // Musik-Metadaten für Upload zusammenstellen
                val snapMusicUrl: String? = when {
                    selectedTrack != null -> selectedTrackStreamUrl
                    selectedLetheTrack != null -> selectedLetheTrack!!.audioUrl
                    localMusicUri != null -> localMusicUri
                    else -> null
                }
                val snapMusicTitle: String? = when {
                    selectedTrack != null -> selectedTrack!!.title
                    selectedLetheTrack != null -> selectedLetheTrack!!.title
                    localMusicUri != null -> localMusicTitle ?: localMusicName
                    else -> null
                }
                val snapMusicArtist: String? = when {
                    selectedTrack != null -> selectedTrack!!.artistName
                    selectedLetheTrack != null -> selectedLetheTrack!!.artist
                    localMusicUri != null -> localMusicArtist
                    else -> null
                }
                val trackTotalDur = selectedTrack?.duration ?: selectedLetheTrack?.durationSeconds
                val snapMusicDurSec: Int? = trackTotalDur?.takeIf { it > 0 }?.let {
                    if (musicEndSec > musicStartSec) musicEndSec - musicStartSec else it
                }
                val snapMusicOffSec: Float? = if (snapMusicDurSec != null) musicStartSec.toFloat() else null
                val snapLinkUrl: String? = linkUrl.trim().takeIf { it.isNotBlank() }
                val snapLinkLabel: String? = linkLabel.trim().takeIf { it.isNotBlank() }

                coroutineScope.launch {
                    if (selectedImages.isEmpty()) {
                        val state = savedPerState[0] ?: PerImageStatusState(
                            textOverlays = textOverlays,
                            emojiOverlays = emojiOverlays,
                            backgroundColor = selectedBackgroundColor
                        )
                        val renderedFile = renderStatusImageToFile(
                            context = context,
                            imageUri = null,
                            backgroundColor = state.backgroundColor,
                            textOverlays = state.textOverlays,
                            emojiOverlays = state.emojiOverlays,
                            boxSize = snapBoxSize,
                            density = snapDensity,
                            drawingPaths = state.drawingPaths
                        )
                        saveStatusToGallery(context, Uri.fromFile(renderedFile), isVideo = false)
                        viewModel.createStatus(Uri.fromFile(renderedFile), "image", snapDuration, asLetheTeam, snapMusicUrl, snapMusicTitle, snapMusicArtist, snapMusicDurSec, snapMusicOffSec, snapLinkUrl, snapLinkLabel) { success ->
                            if (success) onNavigateBack()
                        }
                    } else {
                        val renderedUris = mutableListOf<Uri>()
                        for (idx in selectedImages.indices) {
                            val state = savedPerState[idx] ?: PerImageStatusState()
                            val renderedFile = renderStatusImageToFile(
                                context = context,
                                imageUri = selectedImages[idx],
                                backgroundColor = state.backgroundColor,
                                textOverlays = state.textOverlays,
                                emojiOverlays = state.emojiOverlays,
                                boxSize = snapBoxSize,
                                density = snapDensity,
                                drawingPaths = state.drawingPaths
                            )
                            renderedUris.add(Uri.fromFile(renderedFile))
                        }
                        renderedUris.forEach { saveStatusToGallery(context, it, isVideo = false) }
                        viewModel.createMultipleStatuses(renderedUris, "image", snapDuration, asLetheTeam, snapMusicUrl, snapMusicTitle, snapMusicArtist, snapMusicDurSec, snapMusicOffSec, snapLinkUrl, snapLinkLabel) { success ->
                            if (success) onNavigateBack()
                        }
                    }
                }
            }
        }

        if (isAdmin) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { publishAction(false) },
                    modifier = Modifier.weight(1f),
                    enabled = hasContent && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(4.dp))
                        val btnText = if (selectedImages.size > 1) stringResource(R.string.status_creation_publish_multiple, selectedImages.size) else stringResource(R.string.status_creation_publish)
                        Text(btnText, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                    }
                }
                Button(
                    onClick = { publishAction(true) },
                    modifier = Modifier.weight(1f),
                    enabled = hasContent && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondary)
                    } else {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.status_creation_publish_as_team), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                    }
                }
            }
        } else {
            Button(
                onClick = { publishAction(false) },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasContent && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(8.dp))
                    val btnText = if (selectedImages.size > 1) stringResource(R.string.status_creation_publish_multiple, selectedImages.size) else stringResource(R.string.status_creation_publish)
                    Text(btnText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── Text-Eingabe-Dialog ───────────────────────────────────────────────────
    if (showOverlayTextDialog) {
        val textPaletteColors = listOf(
            Color.White, Color.Black, Color.Red, Color(0xFFFF6600), Color.Yellow,
            Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color(0xFFFF69B4)
        )
        AlertDialog(
            onDismissRequest = { showOverlayTextDialog = false },
            title = { Text(stringResource(R.string.status_creation_add_text_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = overlayTextInput, onValueChange = { overlayTextInput = it },
                        singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.status_creation_add_text_label)) }
                    )
                    Text(stringResource(R.string.status_creation_color_label), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(textPaletteColors) { color ->
                            Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(color)
                                .then(if (color == overlayTextColor) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .pointerInput(Unit) { detectTapGestures { overlayTextColor = color } })
                        }
                    }
                    Text(stringResource(R.string.status_creation_font_label), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FONT_NAMES.size) { i ->
                            val sel = i == overlayTextFontIndex
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (sel) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .pointerInput(Unit) { detectTapGestures { overlayTextFontIndex = i } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(FONT_NAMES[i], fontFamily = FONT_FAMILIES[i], fontWeight = FONT_WEIGHTS[i],
                                    fontStyle = FONT_STYLES[i], fontSize = 14.sp,
                                    color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (overlayTextInput.text.isNotBlank()) {
                        textOverlays = textOverlays + StickerTextOverlay(
                            text = overlayTextInput.text, offset = pendingOverlayOffset,
                            color = overlayTextColor, sizeSp = 28f, fontIndex = overlayTextFontIndex
                        )
                        selectedOverlayId = textOverlays.last().id
                        overlayActiveTool = StickerEditorTool.NONE
                    }
                    showOverlayTextDialog = false
                }) { Text(stringResource(R.string.status_creation_add_text_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showOverlayTextDialog = false }) { Text(stringResource(R.string.status_creation_cancel)) } }
        )
    }

    // ── Emoji/Sticker-Picker (Bild-Tab) ──────────────────────────────────────
    if (showOverlayEmojiPicker) {
        LaunchedEffect(Unit) { viewModel.loadMyStickers() }
        AlertDialog(
            onDismissRequest = { showOverlayEmojiPicker = false },
            title = {
                TabRow(selectedTabIndex = emojiPickerTab, modifier = Modifier.fillMaxWidth()) {
                    Tab(selected = emojiPickerTab == 0, onClick = { emojiPickerTab = 0 }, text = { Text(stringResource(R.string.status_creation_emoji_tab)) })
                    Tab(selected = emojiPickerTab == 1, onClick = { emojiPickerTab = 1 }, text = { Text(stringResource(R.string.status_creation_sticker_tab)) })
                }
            },
            text = {
                when (emojiPickerTab) {
                    0 -> LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.heightIn(max = 360.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        gridItems(OVERLAY_EMOJIS) { emoji ->
                            TextButton(onClick = {
                                emojiOverlays = emojiOverlays + StickerEmojiOverlay(emoji = emoji, offset = pendingOverlayOffset, sizeSp = 36f)
                                selectedOverlayId = emojiOverlays.last().id
                                overlayActiveTool = StickerEditorTool.NONE
                                showOverlayEmojiPicker = false
                            }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                    else -> {
                        if (myStickers.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.status_creation_no_stickers), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.heightIn(max = 360.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gridItems(myStickers) { sticker ->
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                stickerOverlays = stickerOverlays + StickerImageOverlay(
                                                    url = sticker.url,
                                                    offset = pendingOverlayOffset
                                                )
                                                overlayActiveTool = StickerEditorTool.NONE
                                                showOverlayEmojiPicker = false
                                            }
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = if (sticker.url.startsWith("http")) sticker.url else "https://letheapp.de${sticker.url}",
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showOverlayEmojiPicker = false }) { Text(stringResource(R.string.status_creation_cancel)) } }
        )
    }

    // ── Musik-Picker-Sheet ────────────────────────────────────────────────────
    if (showMusicSheet) {
        MusicPickerSheet(
            musicViewModel = musicViewModel,
            onDismiss = {
                showMusicSheet = false
                musicViewModel.stopPreview()
            }
        )
    }

    // ── Link-Dialog (externer Link / APK-Download) ────────────────────────────
    if (showLinkDialog) {
        StatusLinkDialog(
            initialUrl = linkUrl,
            initialLabel = linkLabel,
            onDismiss = { showLinkDialog = false },
            onConfirm = { url, label ->
                linkUrl = url
                linkLabel = label
                showLinkDialog = false
            }
        )
    }
}

// ─── LINK-DIALOG ─────────────────────────────────────────────────────────────

@Composable
private fun StatusLinkDialog(
    initialUrl: String,
    initialLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (url: String, label: String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var label by remember { mutableStateOf(initialLabel) }
    // Normalisiert die Eingabe: fügt https:// voran wenn kein Schema vorhanden ist
    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }
    val isValid = url.isBlank() || normalizeUrl(url).let { it.startsWith("http://", true) || it.startsWith("https://", true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF2196F3))
                Text("Link hinzufügen", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Der Link wird über dem Status angezeigt und beim Antippen extern geöffnet (z.B. für einen APK-Download).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    isError = !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Anzeigetext (optional)") },
                    placeholder = { Text("z.B. Jetzt herunterladen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalizeUrl(url), label.trim()) },
                enabled = url.isBlank() || isValid
            ) { Text(if (url.isBlank()) "Entfernen" else "Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// ─── MUSIK-TRIM TIMELINE ─────────────────────────────────────────────────────

private enum class StatusMusicHandle { NONE, START, END }

@Composable
private fun StatusMusicTrimStrip(
    musicDurationSec: Int,
    startSec: Int,
    endSec: Int,
    onTrimChange: (startSec: Int, endSec: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(StatusMusicHandle.NONE) }
    val primaryColor = Color(0xFFA8A800)
    val latestDur = rememberUpdatedState(musicDurationSec)
    val latestStart = rememberUpdatedState(startSec)
    val latestEnd = rememberUpdatedState(endSec)
    val latestOnChange = rememberUpdatedState(onTrimChange)
    val handleTouchPx = with(LocalDensity.current) { 28.dp.toPx() }

    fun formatSec(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

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
                    tint = primaryColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${formatSec(startSec)} – ${formatSec(endSec)}",
                    color = primaryColor,
                    fontSize = 10.sp
                )
            }
            Text(
                "/ ${formatSec(musicDurationSec)}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val bw = barWidthPx
                        if (bw <= 0f) return@awaitEachGesture
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x = down.position.x
                        val dur = latestDur.value.toFloat()
                        val sx = if (dur > 0f) (latestStart.value / dur) * bw else 0f
                        val ex = if (dur > 0f) (latestEnd.value / dur) * bw else bw
                        dragging = when {
                            kotlin.math.abs(x - sx) <= handleTouchPx -> StatusMusicHandle.START
                            kotlin.math.abs(x - ex) <= handleTouchPx -> StatusMusicHandle.END
                            else -> StatusMusicHandle.NONE
                        }
                        if (dragging != StatusMusicHandle.NONE) {
                            drag(down.id) { change ->
                                change.consume()
                                val nx = change.position.x.coerceIn(0f, bw)
                                val newSec = if (dur > 0f) (nx / bw * dur).toInt() else 0
                                when (dragging) {
                                    StatusMusicHandle.START ->
                                        latestOnChange.value(
                                            newSec.coerceIn(0, latestEnd.value - 1),
                                            latestEnd.value
                                        )
                                    StatusMusicHandle.END ->
                                        latestOnChange.value(
                                            latestStart.value,
                                            newSec.coerceIn(latestStart.value + 1, latestDur.value)
                                        )
                                    StatusMusicHandle.NONE -> {}
                                }
                            }
                        }
                        dragging = StatusMusicHandle.NONE
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val trackH = 8.dp.toPx()
                val r = CornerRadius(4.dp.toPx())
                val dur = musicDurationSec.toFloat()

                // Hintergrund-Track (volle Musiklänge)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(0f, cy - trackH / 2),
                    size = Size(size.width, trackH),
                    cornerRadius = r
                )

                if (dur > 0f) {
                    val sx = (startSec / dur) * size.width
                    val ex = (endSec / dur) * size.width
                    // Ausgewählter Bereich (Start bis End)
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.8f),
                        topLeft = Offset(sx, cy - trackH / 2),
                        size = Size((ex - sx).coerceAtLeast(0f), trackH),
                        cornerRadius = r
                    )
                    // Start-Handle
                    val lineH = 22.dp.toPx()
                    drawLine(
                        color = primaryColor,
                        start = Offset(sx, cy - lineH / 2),
                        end = Offset(sx, cy + lineH / 2),
                        strokeWidth = 3.dp.toPx()
                    )
                    // End-Handle
                    drawLine(
                        color = primaryColor,
                        start = Offset(ex, cy - lineH / 2),
                        end = Offset(ex, cy + lineH / 2),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
        }
    }
}

// ─── BITMAP-RENDERING ────────────────────────────────────────────────────────

/**
 * Rendert Hintergrundfarbe + Bild (optional) + Text-Overlay auf ein 1080×1920 Bitmap
 * und speichert das Ergebnis als JPEG-Datei im Cache-Verzeichnis.
 */
private suspend fun renderStatusImageToFile(
    context: Context,
    imageUri: Uri?,
    backgroundColor: Color?,
    textOverlays: List<StickerTextOverlay> = emptyList(),
    emojiOverlays: List<StickerEmojiOverlay> = emptyList(),
    boxSize: IntSize,
    density: Float,
    drawingPaths: List<DrawingPathData> = emptyList()
): File = withContext(Dispatchers.IO) {
    val width = 1080
    // Bitmap-Höhe an das Seitenverhältnis des Bildes anpassen, sonst 9:16
    val height: Int = if (imageUri != null) {
        try {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, opts)
            }
            if (opts.outWidth > 0 && opts.outHeight > 0)
                (width.toFloat() * opts.outHeight / opts.outWidth).toInt()
            else 1920
        } catch (_: Exception) { 1920 }
    } else 1920
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Hintergrundfarbe (Fallback: Schwarz)
    canvas.drawColor(backgroundColor?.toArgb() ?: android.graphics.Color.BLACK)

    // Quell-Bild (füllt das Bitmap vollständig, da Seitenverhältnis übereinstimmt)
    if (imageUri != null) {
        try {
            val src = android.graphics.BitmapFactory.decodeStream(
                context.contentResolver.openInputStream(imageUri)
            )
            if (src != null) {
                val scale = minOf(width.toFloat() / src.width, height.toFloat() / src.height)
                val dstW = src.width * scale
                val dstH = src.height * scale
                val left = (width - dstW) / 2f
                val top  = (height - dstH) / 2f
                canvas.drawBitmap(
                    src,
                    null,
                    android.graphics.RectF(left, top, left + dstW, top + dstH),
                    null
                )
                src.recycle()
            }
        } catch (_: Exception) {}
    }

    // Text/Emoji-Overlays (via buildOverlayBitmap skaliert auf Bitmap-Koordinaten)
    if ((textOverlays.isNotEmpty() || emojiOverlays.isNotEmpty()) && boxSize.width > 1) {
        val overlayBmp = buildOverlayBitmap(
            width = width, height = height,
            drawPaths = emptyList(),
            textOverlays = textOverlays, emojiOverlays = emojiOverlays,
            canvasSize = boxSize, density = density
        )
        canvas.drawBitmap(overlayBmp, 0f, 0f, null)
        overlayBmp.recycle()
    }

    // Zeichenpfade (skaliert vom Vorschau-Box-Koordinatensystem auf Bitmap-Koordinatensystem)
    if (drawingPaths.isNotEmpty() && boxSize.width > 1 && boxSize.height > 1) {
        val scaleX = width.toFloat() / boxSize.width
        val scaleY = height.toFloat() / boxSize.height
        drawingPaths.forEach { pathData ->
            if (pathData.points.size > 1) {
                val strokePaint = android.graphics.Paint().apply {
                    color = pathData.color.toArgb()
                    strokeWidth = pathData.strokeWidthPx * ((scaleX + scaleY) / 2f)
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val gPath = android.graphics.Path()
                gPath.moveTo(pathData.points[0].x * scaleX, pathData.points[0].y * scaleY)
                for (i in 1 until pathData.points.size) {
                    gPath.lineTo(pathData.points[i].x * scaleX, pathData.points[i].y * scaleY)
                }
                canvas.drawPath(gPath, strokePaint)
            }
        }
    }

    // Als JPEG speichern
    val file = File(context.cacheDir, "status_img_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
    }
    bitmap.recycle()
    file
}

/**
 * Speichert den erstellten Status zusätzlich in der Galerie:
 * Bilder unter Pictures/Lethe, Videos unter Movies/Lethe.
 */
suspend fun saveStatusToGallery(context: Context, source: Uri, isVideo: Boolean) {
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val ts = System.currentTimeMillis()
            val values = android.content.ContentValues().apply {
                if (isVideo) {
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, "Lethe_Status_$ts.mp4")
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_MOVIES}/Lethe")
                } else {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Lethe_Status_$ts.jpg")
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/Lethe")
                }
            }
            val collection = if (isVideo) {
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val target = resolver.insert(collection, values) ?: return@withContext
            resolver.openOutputStream(target)?.use { out ->
                resolver.openInputStream(source)?.use { input ->
                    input.copyTo(out)
                }
            }
        } catch (_: Exception) {
            // Galerie-Speicherung ist optional – Fehler nicht an den Nutzer weiterreichen
        }
    }
}

// ─── VIDEO-TAB ───────────────────────────────────────────────────────────────

private const val MAX_STATUS_TRIM_MS = 60_000L

@Composable
private fun VideoStatusTab(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    initialUri: Uri? = null
) {
    var videoUri by remember { mutableStateOf<Uri?>(initialUri) }
    var durationHours by remember { mutableIntStateOf(24) }
    val density = LocalDensity.current.density
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.isAdmin == true

    // Overlay State (Text/Emoji/Sticker)
    var vTextOverlays by remember { mutableStateOf<List<StickerTextOverlay>>(emptyList()) }
    var vEmojiOverlays by remember { mutableStateOf<List<StickerEmojiOverlay>>(emptyList()) }
    var vStickerOverlays by remember { mutableStateOf<List<StickerImageOverlay>>(emptyList()) }
    var vSelectedOverlayId by remember { mutableStateOf<String?>(null) }
    var vOverlayDragMode by remember { mutableStateOf(OverlayDragMode.NONE) }
    var vDragCenterX by remember { mutableFloatStateOf(0f) }
    var vDragCenterY by remember { mutableFloatStateOf(0f) }
    var vDragStartAngle by remember { mutableFloatStateOf(0f) }
    var vDragStartRotation by remember { mutableFloatStateOf(0f) }
    var vDragStartDist by remember { mutableFloatStateOf(1f) }
    var vDragStartScale by remember { mutableFloatStateOf(1f) }
    var vActiveTool by remember { mutableStateOf(StickerEditorTool.NONE) }
    var vShowTextDialog by remember { mutableStateOf(false) }
    var vTextInput by remember { mutableStateOf(TextFieldValue("")) }
    var vTextColor by remember { mutableStateOf(Color.White) }
    var vTextFontIndex by remember { mutableIntStateOf(0) }
    var vShowEmojiPicker by remember { mutableStateOf(false) }
    var vEmojiPickerTab by remember { mutableIntStateOf(0) }
    var vPendingOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    val vMyStickers by viewModel.myStickers.collectAsState()
    var vBoxSize by remember { mutableStateOf(IntSize(1, 1)) }
    val isLoading by viewModel.isLoading.collectAsState()
    var isTranscoding by remember { mutableStateOf(false) }
    var transcodingError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Trim / Playback state
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    // isDragging: verhindert Player-Reload während des Drag-Vorgangs (flüssigeres Verschieben)
    var isDragging by remember { mutableStateOf(false) }
    // trimStartMs zum Zeitpunkt des Drag-Starts – zum relativen Seek im Player während des Drags
    var dragStartTrimStartMs by remember { mutableLongStateOf(0L) }

    // ExoPlayer für Loop-Vorschau
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().also { it.repeatMode = ExoPlayer.REPEAT_MODE_ONE }
    }
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Videodauer via MediaMetadataRetriever + Trim initialisieren
    LaunchedEffect(videoUri) {
        val uri = videoUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                withContext(Dispatchers.Main) {
                    videoDurationMs = dur
                    trimStartMs = 0L
                    trimEndMs = minOf(dur, MAX_STATUS_TRIM_MS)
                    currentPositionMs = 0L
                    isPlaying = false
                    exoPlayer.pause()
                }
            } finally { r.release() }
        }
    }

    // ExoPlayer-Clip aktualisieren wenn Trim oder URI ändert – aber NICHT während des Drags
    // (isDragging verhindert wiederholtes prepare() bei jedem Drag-Schritt → flüssiges Verschieben)
    LaunchedEffect(videoUri, trimStartMs, trimEndMs, isDragging) {
        if (isDragging) return@LaunchedEffect
        val uri = videoUri ?: return@LaunchedEffect
        if (videoDurationMs <= 0L) return@LaunchedEffect
        val wasPlaying = exoPlayer.isPlaying
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs)
                    .setEndPositionMs(trimEndMs)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        if (wasPlaying || isPlaying) exoPlayer.play()
    }

    // Positions-Polling (100ms Intervall)
    // isDragging als Key: Loop stoppt sofort sobald der Nutzer den Slider anfasst.
    LaunchedEffect(isPlaying, isDragging) {
        while (isPlaying && !isDragging) {
            if (!isDragging) {
                currentPositionMs = (trimStartMs + exoPlayer.currentPosition)
                    .coerceIn(trimStartMs, trimEndMs)
                // Loop-Back: Wenn Player am rechten Trimm-Rand angekommen ist, zurück zum linken
                val clipDuration = (trimEndMs - trimStartMs).coerceAtLeast(1L)
                if (exoPlayer.currentPosition >= clipDuration - 150L && exoPlayer.isPlaying) {
                    exoPlayer.seekTo(0)
                    currentPositionMs = trimStartMs
                }
            }
            delay(100)
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        videoUri = uri
        transcodingError = null
        isPlaying = false
        exoPlayer.pause()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Video-Vorschau ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .onSizeChanged { vBoxSize = it },
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null && !isTranscoding) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Text/Emoji Overlay Canvas
                val vHandleR = with(LocalDensity.current) { 11.dp.toPx() }
                val vHandleT = with(LocalDensity.current) { 32.dp.toPx() }
                Canvas(
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(vActiveTool) {
                            if (vActiveTool != StickerEditorTool.BRUSH) detectTapGestures { pos ->
                                when (vActiveTool) {
                                    StickerEditorTool.TEXT -> { vPendingOffset = pos; vShowTextDialog = true; vTextInput = TextFieldValue("") }
                                    StickerEditorTool.EMOJI -> { vPendingOffset = pos; vShowEmojiPicker = true }
                                    else -> { val h = findOverlayAtPos(pos, vTextOverlays, vEmojiOverlays, density); vSelectedOverlayId = if (h == vSelectedOverlayId) null else h }
                                }
                            }
                        }
                        .pointerInput(vActiveTool) {
                            if (vActiveTool != StickerEditorTool.BRUSH) detectDragGestures(
                                onDragStart = { pos ->
                                    val si = vSelectedOverlayId
                                    val info = if (si != null) getOverlayInfo(si, vTextOverlays, vEmojiOverlays, density) else null
                                    if (info != null) {
                                        val hs = handlePositions(info); val br = hs[3]
                                        val vEdgeSnap = snapHandleOnEdge(pos, hs, edgePx = 22f, cornerProxPx = vHandleT * 1.8f)
                                        when {
                                            (pos - br).getDistance() < vHandleT || vEdgeSnap == 3 -> { vOverlayDragMode = OverlayDragMode.ROTATE; vDragCenterX = info.cx; vDragCenterY = info.cy; vDragStartAngle = atan2((pos.y - info.cy).toDouble(), (pos.x - info.cx).toDouble()).toFloat(); vDragStartRotation = info.rotation }
                                            hs.take(3).any { (pos - it).getDistance() < vHandleT } || (vEdgeSnap in 0..2) -> { vOverlayDragMode = OverlayDragMode.SCALE; vDragCenterX = info.cx; vDragCenterY = info.cy; vDragStartDist = (pos - Offset(info.cx, info.cy)).getDistance().coerceAtLeast(1f); vDragStartScale = info.scale }
                                            isInsideOverlay(pos, info) -> vOverlayDragMode = OverlayDragMode.MOVE
                                            else -> { vSelectedOverlayId = null; vOverlayDragMode = OverlayDragMode.NONE }
                                        }
                                    } else {
                                        val h = findOverlayAtPos(pos, vTextOverlays, vEmojiOverlays, density)
                                        if (h != null) { vSelectedOverlayId = h; vOverlayDragMode = OverlayDragMode.MOVE } else vOverlayDragMode = OverlayDragMode.NONE
                                    }
                                },
                                onDrag = { change, delta ->
                                    val si = vSelectedOverlayId ?: return@detectDragGestures
                                    when (vOverlayDragMode) {
                                        OverlayDragMode.MOVE -> { vTextOverlays = vTextOverlays.map { if (it.id==si) it.copy(offset=Offset(it.offset.x+delta.x,it.offset.y+delta.y)) else it }; vEmojiOverlays = vEmojiOverlays.map { if (it.id==si) it.copy(offset=Offset(it.offset.x+delta.x,it.offset.y+delta.y)) else it } }
                                        OverlayDragMode.ROTATE -> { val cur = atan2((change.position.y-vDragCenterY).toDouble(),(change.position.x-vDragCenterX).toDouble()).toFloat(); val nr = vDragStartRotation + Math.toDegrees((cur-vDragStartAngle).toDouble()).toFloat(); vTextOverlays = vTextOverlays.map { if (it.id==si) it.copy(rotation=nr) else it }; vEmojiOverlays = vEmojiOverlays.map { if (it.id==si) it.copy(rotation=nr) else it } }
                                        OverlayDragMode.SCALE -> { val cd = (change.position-Offset(vDragCenterX,vDragCenterY)).getDistance().coerceAtLeast(1f); val ns = (vDragStartScale*(cd/vDragStartDist)).coerceIn(0.3f,6f); vTextOverlays = vTextOverlays.map { if (it.id==si) it.copy(scale=ns) else it }; vEmojiOverlays = vEmojiOverlays.map { if (it.id==si) it.copy(scale=ns) else it } }
                                        OverlayDragMode.NONE -> {}
                                    }
                                },
                                onDragEnd = { vOverlayDragMode = OverlayDragMode.NONE }
                            )
                        }
                ) {
                    val nc = drawContext.canvas.nativeCanvas
                    vTextOverlays.forEach { t ->
                        val p = android.graphics.Paint().apply { color = android.graphics.Color.argb((t.color.alpha*255).toInt(),(t.color.red*255).toInt(),(t.color.green*255).toInt(),(t.color.blue*255).toInt()); textSize = t.sizeSp*density; typeface = typefaceForIndex(t.fontIndex); isAntiAlias = true; setShadowLayer(3f,1.5f,1.5f,android.graphics.Color.BLACK) }
                        val b = android.graphics.Rect(); p.getTextBounds(t.text,0,t.text.length,b)
                        val bw = p.measureText(t.text)
                        val bp = android.graphics.Paint().apply { color = android.graphics.Color.argb(140,0,0,0); isAntiAlias = true }
                        nc.save(); nc.translate(t.offset.x,t.offset.y); nc.rotate(t.rotation); nc.scale(t.scale,t.scale)
                        nc.drawRoundRect(android.graphics.RectF(-bw/2-8f,b.top.toFloat()-4f,bw/2+8f,b.bottom.toFloat()+4f),8f,8f,bp)
                        nc.drawText(t.text,-b.exactCenterX(),-b.exactCenterY(),p); nc.restore()
                    }
                    vEmojiOverlays.forEach { e ->
                        val p = android.graphics.Paint().apply { textSize = e.sizeSp*density; isAntiAlias = true }
                        val tw = p.measureText(e.emoji)/2f; val th = (-p.ascent()+p.descent())/2f-p.descent()
                        nc.save(); nc.translate(e.offset.x,e.offset.y); nc.rotate(e.rotation); nc.scale(e.scale,e.scale); nc.drawText(e.emoji,-tw,th,p); nc.restore()
                    }
                    val si = vSelectedOverlayId; val info = if (si!=null) getOverlayInfo(si,vTextOverlays,vEmojiOverlays,density) else null
                    if (info != null) {
                        val hw = info.halfW*info.scale; val hh = info.halfH*info.scale
                        val bp2 = android.graphics.Paint().apply { color=0xCCFFFFFF.toInt(); style=android.graphics.Paint.Style.STROKE; strokeWidth=2f; pathEffect=android.graphics.DashPathEffect(floatArrayOf(10f,6f),0f); isAntiAlias=true }
                        nc.save(); nc.translate(info.cx,info.cy); nc.rotate(info.rotation); nc.drawRoundRect(android.graphics.RectF(-hw,-hh,hw,hh),6f,6f,bp2); nc.restore()
                        handlePositions(info).forEachIndexed { i, p2 ->
                            val fc = if (i==3) android.graphics.Color.rgb(255,87,34) else android.graphics.Color.rgb(72,199,142)
                            nc.drawCircle(p2.x,p2.y,vHandleR+3f,android.graphics.Paint().apply{color=0x99000000.toInt();isAntiAlias=true})
                            nc.drawCircle(p2.x,p2.y,vHandleR,android.graphics.Paint().apply{color=android.graphics.Color.WHITE;isAntiAlias=true})
                            nc.drawCircle(p2.x,p2.y,vHandleR*0.5f,android.graphics.Paint().apply{color=fc;isAntiAlias=true})
                        }
                    }
                }
                // Sticker-Bild-Overlays über dem Video
                val vDensityLocal = LocalDensity.current
                vStickerOverlays.forEach { sticker ->
                    val sizePx2 = sticker.sizeDp * vDensityLocal.density * sticker.scale
                    Box(
                        modifier = Modifier
                            .absoluteOffset { IntOffset((sticker.offset.x - sizePx2 / 2).toInt(), (sticker.offset.y - sizePx2 / 2).toInt()) }
                            .size((sticker.sizeDp * sticker.scale).dp)
                            .rotate(sticker.rotation)
                            .pointerInput(sticker.id) {
                                detectDragGestures { _, delta ->
                                    vStickerOverlays = vStickerOverlays.map {
                                        if (it.id == sticker.id) it.copy(offset = Offset(it.offset.x + delta.x, it.offset.y + delta.y)) else it
                                    }
                                }
                            }
                    ) {
                        coil.compose.AsyncImage(
                            model = if (sticker.url.startsWith("http")) sticker.url else "https://letheapp.de${sticker.url}",
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                                .clickable { vStickerOverlays = vStickerOverlays.filter { it.id != sticker.id } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                // Play/Pause Button
                IconButton(
                    onClick = {
                        isPlaying = !isPlaying
                        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                // Video wechseln
                FloatingActionButton(
                    onClick = { videoLauncher.launch("video/*") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                ) {
                    Icon(Icons.Default.Videocam, null, modifier = Modifier.size(20.dp))
                }
            } else if (isTranscoding) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.status_creation_transcoding), color = Color.White.copy(alpha = 0.8f))
                }
            } else {
                // Noch kein Video – Auswahl-Hinweis
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Videocam, null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                    Text(stringResource(R.string.status_creation_no_video_hint), color = Color.White.copy(alpha = 0.6f))
                }
                FloatingActionButton(
                    onClick = { videoLauncher.launch("video/*") },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(44.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Videocam, "Video wählen", modifier = Modifier.size(22.dp))
                }
            }
            // Fehlertext
            if (transcodingError != null) {
                Text(
                    "Fehler: $transcodingError",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // ── Trim-Timeline (nur wenn Video geladen) ────────────────────────
        if (videoUri != null && videoDurationMs > 0L && !isTranscoding) {
            VideoTrimTimeline(
                durationMs = videoDurationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                currentPositionMs = currentPositionMs,
                primaryColor = MaterialTheme.colorScheme.primary,
                onTrimChange = { newStart, newEnd ->
                    trimStartMs = newStart
                    trimEndMs = newEnd
                    currentPositionMs = newStart
                },
                onDragStart = {
                    dragStartTrimStartMs = trimStartMs  // Ursprung für relativen Seek merken
                    isDragging = true
                    isPlaying = false
                    exoPlayer.pause()
                },
                onSeekTo = { absoluteMs ->
                    // Relativ zum gerade geladenen Clip suchen (Clip startet bei dragStartTrimStartMs)
                    val relativeMs = (absoluteMs - dragStartTrimStartMs).coerceAtLeast(0L)
                    exoPlayer.seekTo(relativeMs)
                },
                onDragEnd = {
                    isDragging = false
                    isPlaying = true  // Wiedergabe nach dem Trimmen automatisch fortsetzen
                    // LaunchedEffect(videoUri, trimStartMs, trimEndMs, isDragging) bereitet
                    // den Clip neu vor und startet die Wiedergabe (isPlaying = true)
                }
            )
            val trimSec = (trimEndMs - trimStartMs) / 1000f
            Text(
                text = "${formatStatusMs(trimStartMs)}  –  ${formatStatusMs(trimEndMs)}" +
                       "   |   Ausschnitt: ${"%.1f".format(trimSec).trimEnd('0').trimEnd('.').plus(" Sek.")}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Text + Emoji Overlay-Werkzeuge ──────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val vtA = vActiveTool == StickerEditorTool.TEXT
            Box(modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (vtA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { vActiveTool = if (vtA) StickerEditorTool.NONE else StickerEditorTool.TEXT; vSelectedOverlayId = null },
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.TextFields, null, modifier = Modifier.size(18.dp), tint = if (vtA) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.spark_editor_text_label), fontSize = 13.sp, color = if (vtA) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val veA = vActiveTool == StickerEditorTool.EMOJI
            Box(modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (veA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { vActiveTool = if (veA) StickerEditorTool.NONE else StickerEditorTool.EMOJI; vSelectedOverlayId = null },
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.EmojiEmotions, null, modifier = Modifier.size(18.dp), tint = if (veA) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.status_creation_emoji_tab), fontSize = 13.sp, color = if (veA) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (vTextOverlays.isNotEmpty() || vEmojiOverlays.isNotEmpty()) {
                IconButton(onClick = { vSelectedOverlayId = null; if (vEmojiOverlays.isNotEmpty()) vEmojiOverlays = vEmojiOverlays.dropLast(1) else if (vTextOverlays.isNotEmpty()) vTextOverlays = vTextOverlays.dropLast(1) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        DurationSelector(selectedHours = durationHours, onSelect = { durationHours = it })

        val videoPublishAction: (Boolean) -> Unit = { asLetheTeam ->
            val uri = videoUri
            if (uri != null) {
                val snapTxtOverlays = vTextOverlays
                val snapEmojiOverlays = vEmojiOverlays
                val snapBoxSize = vBoxSize
                val snapDensity = density
                scope.launch {
                    isPlaying = false
                    exoPlayer.pause()
                    isTranscoding = true
                    transcodingError = null
                    try {
                        val hasOverlays = snapTxtOverlays.isNotEmpty() || snapEmojiOverlays.isNotEmpty()
                        val transcodedUri = if (hasOverlays) {
                            transcodeVideoWithOverlays(
                                context, viewModel.ffmpegProvider, uri, trimStartMs, trimEndMs,
                                snapTxtOverlays, snapEmojiOverlays, snapBoxSize, snapDensity
                            )
                        } else {
                            transcodeVideoForStatus(context, uri, trimStartMs, trimEndMs)
                        }
                        isTranscoding = false
                        saveStatusToGallery(context, transcodedUri, isVideo = true)
                        viewModel.createStatus(transcodedUri, "video", durationHours, asLetheTeam) { success ->
                            if (success) onNavigateBack()
                        }
                    } catch (e: Exception) {
                        isTranscoding = false
                        transcodingError = e.message ?: "Unbekannter Fehler"
                    }
                }
            }
        }

        if (isAdmin) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { videoPublishAction(false) },
                    modifier = Modifier.weight(1f),
                    enabled = videoUri != null && !isLoading && !isTranscoding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading || isTranscoding) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isTranscoding) "Konvertiere..." else "Hochladen...")
                    } else {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.status_creation_publish), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                    }
                }
                Button(
                    onClick = { videoPublishAction(true) },
                    modifier = Modifier.weight(1f),
                    enabled = videoUri != null && !isLoading && !isTranscoding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading || isTranscoding) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isTranscoding) "Konvertiere..." else "Hochladen...")
                    } else {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.status_creation_publish_as_team), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                    }
                }
            }
        } else {
            Button(
                onClick = { videoPublishAction(false) },
                modifier = Modifier.fillMaxWidth(),
                enabled = videoUri != null && !isLoading && !isTranscoding,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading || isTranscoding) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTranscoding) "Konvertiere..." else "Hochladen...")
                } else {
                    Icon(Icons.Default.Upload, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.status_creation_publish), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ── Text-Dialog (Video) ───────────────────────────────────────────────────
    if (vShowTextDialog) {
        val textPaletteColors = listOf(Color.White, Color.Black, Color.Red, Color(0xFFFF6600), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color(0xFFFF69B4))
        AlertDialog(
            onDismissRequest = { vShowTextDialog = false },
            title = { Text(stringResource(R.string.status_creation_add_text_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = vTextInput, onValueChange = { vTextInput = it }, singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.status_creation_add_text_label)) })
                    Text(stringResource(R.string.status_creation_color_label), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(textPaletteColors) { color -> Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(color).then(if (color==vTextColor) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier).pointerInput(Unit) { detectTapGestures { vTextColor = color } }) }
                    }
                    Text(stringResource(R.string.status_creation_font_label), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FONT_NAMES.size) { i ->
                            val sel = i == vTextFontIndex
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).border(if (sel) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp).pointerInput(Unit) { detectTapGestures { vTextFontIndex = i } }, contentAlignment = Alignment.Center) {
                                Text(FONT_NAMES[i], fontFamily = FONT_FAMILIES[i], fontWeight = FONT_WEIGHTS[i], fontStyle = FONT_STYLES[i], fontSize = 14.sp, color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (vTextInput.text.isNotBlank()) { vTextOverlays = vTextOverlays + StickerTextOverlay(text = vTextInput.text, offset = vPendingOffset, color = vTextColor, sizeSp = 28f, fontIndex = vTextFontIndex); vSelectedOverlayId = vTextOverlays.last().id; vActiveTool = StickerEditorTool.NONE }; vShowTextDialog = false }) { Text(stringResource(R.string.status_creation_add_text_confirm)) } },
            dismissButton = { TextButton(onClick = { vShowTextDialog = false }) { Text(stringResource(R.string.status_creation_cancel)) } }
        )
    }

    // ── Emoji/Sticker-Picker (Video) ──────────────────────────────────────────
    if (vShowEmojiPicker) {
        LaunchedEffect(Unit) { viewModel.loadMyStickers() }
        AlertDialog(
            onDismissRequest = { vShowEmojiPicker = false },
            title = {
                TabRow(selectedTabIndex = vEmojiPickerTab, modifier = Modifier.fillMaxWidth()) {
                    Tab(selected = vEmojiPickerTab == 0, onClick = { vEmojiPickerTab = 0 }, text = { Text(stringResource(R.string.status_creation_emoji_tab)) })
                    Tab(selected = vEmojiPickerTab == 1, onClick = { vEmojiPickerTab = 1 }, text = { Text(stringResource(R.string.status_creation_sticker_tab)) })
                }
            },
            text = {
                when (vEmojiPickerTab) {
                    0 -> LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.heightIn(max = 360.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        gridItems(OVERLAY_EMOJIS) { emoji ->
                            TextButton(onClick = {
                                vEmojiOverlays = vEmojiOverlays + StickerEmojiOverlay(emoji = emoji, offset = vPendingOffset, sizeSp = 36f)
                                vSelectedOverlayId = vEmojiOverlays.last().id
                                vActiveTool = StickerEditorTool.NONE
                                vShowEmojiPicker = false
                            }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) {
                                Text(emoji, fontSize = 22.sp)
                            }
                        }
                    }
                    else -> {
                        if (vMyStickers.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.status_creation_no_stickers), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.heightIn(max = 360.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gridItems(vMyStickers) { sticker ->
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                vStickerOverlays = vStickerOverlays + StickerImageOverlay(
                                                    url = sticker.url,
                                                    offset = vPendingOffset
                                                )
                                                vActiveTool = StickerEditorTool.NONE
                                                vShowEmojiPicker = false
                                            }
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = if (sticker.url.startsWith("http")) sticker.url else "https://letheapp.de${sticker.url}",
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vShowEmojiPicker = false }) { Text(stringResource(R.string.status_creation_cancel)) } }
        )
    }
}

// ─── VIDEO-TRIM TIMELINE ──────────────────────────────────────────────────────

/**
 * Interaktive Trim-Timeline für den Video-Status-Creator.
 *
 * Zeigt die volle Videolänge als Track; ein farbiger Ausschnitt markiert den gewählten
 * Bereich (max 60 Sek.). Beide Handles (links / rechts) sowie der Ausschnitt selbst
 * sind per Drag verschiebbar.
 */
@Composable
private fun VideoTrimTimeline(
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    primaryColor: Color,
    onTrimChange: (startMs: Long, endMs: Long) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val trackColor = Color.Gray.copy(alpha = 0.35f)
    val posColor = Color.White.copy(alpha = 0.9f)

    // rememberUpdatedState: frische Werte im pointerInput-Block ohne Gesture-Neustart beim Recompose
    val latestTrimStart    = rememberUpdatedState(trimStartMs)
    val latestTrimEnd      = rememberUpdatedState(trimEndMs)
    val latestDuration     = rememberUpdatedState(durationMs)
    val latestOnTrimChange = rememberUpdatedState(onTrimChange)
    val latestOnDragStart  = rememberUpdatedState(onDragStart)
    val latestOnDragEnd    = rememberUpdatedState(onDragEnd)
    val latestOnSeekTo     = rememberUpdatedState(onSeekTo)

    Column(modifier = modifier.fillMaxWidth()) {
        // Zeitstempel-Leiste
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatStatusMs(0), fontSize = 10.sp, color = Color.Gray)
            if (durationMs > 30_000L) {
                Text(formatStatusMs(durationMs / 2), fontSize = 10.sp, color = Color.Gray)
            }
            Text(formatStatusMs(durationMs), fontSize = 10.sp, color = Color.Gray)
        }
        Spacer(Modifier.height(2.dp))

        // Canvas + Drag-Interaktion
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat() }
                // WICHTIG: pointerInput(Unit) — der Gesture-Block wird NIE neu gestartet.
                // Stattdessen lesen wir alle sich ändernden Werte über latestXxx.value,
                // die via rememberUpdatedState immer aktuell sind, ohne die Geste zu unterbrechen.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val bw  = barWidthPx
                        val dur = latestDuration.value
                        if (bw <= 0f || dur <= 0L) return@awaitEachGesture

                        fun msToX(ms: Long) = ms.toFloat() / dur * bw

                        val hitPx = 80f
                        val down  = awaitFirstDown(requireUnconsumed = false)
                        val sx    = down.position.x

                        // Trim-Werte zum Zeitpunkt des Finger-Aufsetzens einfrieren
                        val ts0 = latestTrimStart.value
                        val te0 = latestTrimEnd.value
                        val lx  = msToX(ts0)
                        val rx  = msToX(te0)

                        val hitLeft  = abs(sx - lx) < hitPx
                        val hitRight = !hitLeft && abs(sx - rx) < hitPx
                        val hitMid   = !hitLeft && !hitRight && sx in (lx..rx)

                        if (hitLeft || hitRight || hitMid) {
                            latestOnDragStart.value()

                            // Laufende Trim-Werte als lokale Variablen – werden synchron
                            // mit jedem Drag-Schritt aktualisiert (kein State-Lag)
                            var currentTs = ts0
                            var currentTe = te0

                            drag(down.id) { change ->
                                val dx  = change.position.x - change.previousPosition.x
                                val dMs = (dx / bw * dur).toLong()
                                when {
                                    hitLeft -> {
                                        val ns = (currentTs + dMs).coerceIn(
                                            (currentTe - MAX_STATUS_TRIM_MS).coerceAtLeast(0L),
                                            currentTe - 1_000L
                                        )
                                        latestOnTrimChange.value(ns, currentTe)
                                        latestOnSeekTo.value(ns)   // Frame-Vorschau im Player
                                        currentTs = ns
                                    }
                                    hitRight -> {
                                        val ne = (currentTe + dMs).coerceIn(
                                            currentTs + 1_000L,
                                            (currentTs + MAX_STATUS_TRIM_MS).coerceAtMost(dur)
                                        )
                                        latestOnTrimChange.value(currentTs, ne)
                                        latestOnSeekTo.value(ne)   // Frame-Vorschau im Player
                                        currentTe = ne
                                    }
                                    hitMid -> {
                                        val wMs = currentTe - currentTs
                                        val ns  = (currentTs + dMs).coerceIn(0L, dur - wMs)
                                        latestOnTrimChange.value(ns, ns + wMs)
                                        latestOnSeekTo.value(ns)   // Frame-Vorschau im Player
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

                // Hintergrund-Track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, cx - trackH / 2),
                    size = Size(size.width, trackH),
                    cornerRadius = r
                )
                // Ausschnitt-Hervorhebung
                drawRoundRect(
                    color = primaryColor.copy(alpha = 0.4f),
                    topLeft = Offset(lx, cx - trackH / 2),
                    size = Size((rx - lx).coerceAtLeast(0f), trackH),
                    cornerRadius = r
                )
                // Linkes Handle (Pill)
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(lx - handleW / 2, cx - handleH / 2),
                    size = Size(handleW, handleH),
                    cornerRadius = rHandle
                )
                // Rechtes Handle (Pill)
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(rx - handleW / 2, cx - handleH / 2),
                    size = Size(handleW, handleH),
                    cornerRadius = rHandle
                )
                // Positions-Indikator (weißer dünner Strich)
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

private fun formatStatusMs(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Transcodiert den gewählten Ausschnitt (trimStart..trimEnd) eines Videos
 * mit Media3 Transformer zu H.264/AAC MP4.
 */
private suspend fun transcodeVideoForStatus(
    context: Context,
    inputUri: Uri,
    trimStartMs: Long = 0L,
    trimEndMs: Long = Long.MAX_VALUE
): Uri = suspendCancellableCoroutine { cont ->
    val outputFile = File(context.cacheDir, "status_tc_${System.currentTimeMillis()}.mp4")

    val mediaItem = MediaItem.Builder()
        .setUri(inputUri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs)
                .apply { if (trimEndMs != Long.MAX_VALUE) setEndPositionMs(trimEndMs) }
                .build()
        )
        .build()

    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (cont.isActive) cont.resume(Uri.fromFile(outputFile))
            }
            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (cont.isActive) cont.resumeWithException(exportException)
            }
        })
        .build()

    val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
    transformer.start(editedMediaItem, outputFile.absolutePath)

    cont.invokeOnCancellation {
        transformer.cancel()
        outputFile.delete()
    }
}

/**
 * Exportiert ein Video mit Trim + Text/Emoji-Overlays via FfmpegProvider.
 * Erstellt zuerst ein Overlay-Bitmap, Rest (PNG-Zwischenschritt, Filtergraph) kapselt der Provider.
 */
private suspend fun transcodeVideoWithOverlays(
    context: Context,
    ffmpegProvider: com.securechat.app.media.FfmpegProvider,
    inputUri: Uri,
    trimStartMs: Long,
    trimEndMs: Long,
    textOverlays: List<StickerTextOverlay>,
    emojiOverlays: List<StickerEmojiOverlay>,
    previewBoxSize: IntSize,
    density: Float
): Uri = withContext(Dispatchers.IO) {
    val id = System.currentTimeMillis()
    val inputFile = File(context.cacheDir, "vstatus_in_$id")
    context.contentResolver.openInputStream(inputUri)?.use { i -> java.io.FileOutputStream(inputFile).use { o -> i.copyTo(o) } }

    // Video-Dimensionen ermitteln
    val retriever = android.media.MediaMetadataRetriever()
    val vidW: Int; val vidH: Int
    try {
        retriever.setDataSource(inputFile.absolutePath)
        vidW = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
        vidH = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
    } finally { retriever.release() }

    // Overlay-Bitmap bauen (gleiche Dimensionen wie Video)
    val overlayBmp = buildOverlayBitmap(vidW, vidH, emptyList(), textOverlays, emojiOverlays, previewBoxSize, density)

    val outputFile = File(context.cacheDir, "vstatus_out_$id.mp4")
    val result = ffmpegProvider.overlayOnVideo(
        VideoOverlayRequest(
            inputFile = inputFile,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            overlayBitmap = overlayBmp,
            outputFile = outputFile
        )
    )
    overlayBmp.recycle()
    inputFile.delete()

    if (result is FfmpegResult.Error || !outputFile.exists()) {
        throw Exception("FFmpeg overlay-Fehler: ${(result as? FfmpegResult.Error)?.message}")
    }
    Uri.fromFile(outputFile)
}

// ─── AUDIO-TAB ───────────────────────────────────────────────────────────────

@Composable
private fun AudioStatusTab(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingFinished by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var durationHours by remember { mutableIntStateOf(24) }
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.isAdmin == true

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordSeconds++
            }
        }
    }

    fun startRecording() {
        val f = File(context.cacheDir, "status_audio_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val mr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(f.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        recordingFile = f
        isRecording = true
        recordingFinished = false
    }

    fun stopRecording() {
        try { recorder?.apply { stop(); release() } } catch (_: Exception) {}
        recorder = null
        isRecording = false
        recordingFinished = true
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        // Aufnahme-Visualisierung
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    if (isRecording) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    3.dp,
                    if (isRecording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(56.dp),
                    tint = if (isRecording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                if (isRecording) {
                    val mins = recordSeconds / 60
                    val secs = recordSeconds % 60
                    Text("%02d:%02d".format(mins, secs), fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Text(
            text = when {
                isRecording -> stringResource(R.string.status_creation_recording_running)
                recordingFinished -> stringResource(R.string.status_creation_recording_done)
                else -> stringResource(R.string.status_creation_recording_hint)
            },
            fontSize = 16.sp,
            color = when {
                isRecording -> MaterialTheme.colorScheme.error
                recordingFinished -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
        )

        // Dauer-Auswahl (immer sichtbar)
        DurationSelector(selectedHours = durationHours, onSelect = { durationHours = it })

        // Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!recordingFinished) {
                Button(
                    onClick = {
                        if (isRecording) {
                            stopRecording()
                        } else {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                startRecording()
                            } else {
                                audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRecording) stringResource(R.string.status_creation_stop) else stringResource(R.string.status_creation_record))
                }
            }

            if (recordingFinished) {
                OutlinedButton(
                    onClick = { recordingFinished = false; recordingFile = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.status_creation_re_record))
                }

                if (isAdmin) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val f = recordingFile ?: return@Button
                                viewModel.createStatus(Uri.fromFile(f), "audio", durationHours, false) { success ->
                                    if (success) onNavigateBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.Upload, null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.status_creation_publish), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                            }
                        }
                        Button(
                            onClick = {
                                val f = recordingFile ?: return@Button
                                viewModel.createStatus(Uri.fromFile(f), "audio", durationHours, true) { success ->
                                    if (success) onNavigateBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSecondary)
                            } else {
                                Icon(Icons.Default.Upload, null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.status_creation_publish_as_team), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val f = recordingFile ?: return@Button
                            viewModel.createStatus(Uri.fromFile(f), "audio", durationHours, false) { success ->
                                if (success) onNavigateBack()
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Upload, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.status_creation_publish))
                        }
                    }
                }
            }
        }
    }
}
