package com.securechat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.YuvImage
import android.view.SurfaceHolder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import com.pedro.encoder.input.video.Camera2ApiManager
import com.securechat.app.data.local.LiveSilhouetteFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private val LIVE_STICKERS = listOf("🔥", "❤️", "😂", "👏", "🎉", "⭐", "💯", "🚀")

private data class PlacedSticker(val id: Int, val emoji: String)

// Auflösung der NV21-Frames für die Selfie-Segmentierung (niedrig für Performance)
private const val SEG_W = 320
private const val SEG_H = 240

private fun nv21ToBitmap(nv21: ByteArray, w: Int, h: Int): Bitmap? = runCatching {
    val yuv = YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, w, h), 75, out)
    BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}.getOrNull()

/**
 * Creator-Live-Screen: Kamera-Preview als Vollbild + RTMP-Push (RootEncoder).
 * - Nimmt optionale LiveStreamConfig entgegen (Linse, Qualität, Mikrofon, Effekte).
 * - Bottom-Controls erscheinen nur bei Tap und verschwinden nach 3 Sekunden.
 * - Pause-Button: Video-Blackout, Audio läuft weiter.
 * - Kommentar-Filter: Keywords blockieren Chat-Nachrichten im Overlay.
 * - Adaptive Bitrate: automatische Anpassung bei schwacher Verbindung.
 */
@Composable
fun CreatorLiveScreen(
    creatorId: String,
    viewModel: MainViewModel,
    config: LiveStreamConfig = LiveStreamConfig(),
    onNavigateBack: () -> Unit
) {
    val context        = LocalContext.current
    val streamKeyInfo  by viewModel.liveStreamKeyInfo.collectAsState()
    val chatMessages   by viewModel.liveChatMessages.collectAsState()
    val viewerCount    by viewModel.liveViewerCount.collectAsState()

    // Bildschirm wach halten solange der Stream-Screen offen ist
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val isStreamingState    = remember { mutableStateOf(false) }
    var isStreaming          by isStreamingState
    val statusMessageState  = remember { mutableStateOf("Bereit") }
    var statusMessage        by statusMessageState
    var isMuted              by remember { mutableStateOf(false) }
    var useFrontCamera       by remember { mutableStateOf(config.lensMode == LensMode.FRONT) }
    val cameraRef            = remember { mutableStateOf<RtmpCamera2?>(null) }
    val chatListState        = rememberLazyListState()
    val coroutineScope       = rememberCoroutineScope()

    // Pause (blackout video, audio continues)
    var isVideoPaused        by remember { mutableStateOf(false) }

    // Auto-hide bottom controls after 3 s of inactivity
    var showControls         by remember { mutableStateOf(true) }
    val hideControlsJob      = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun resetHideTimer() {
        hideControlsJob.value?.cancel()
        showControls = true
        hideControlsJob.value = coroutineScope.launch {
            delay(3000)
            showControls = false
        }
    }

    // Comment filter
    var filterKeywords       by remember { mutableStateOf(setOf<String>()) }
    var showFilterDialog     by remember { mutableStateOf(false) }
    var filterInput          by remember { mutableStateOf("") }

    // Chat overlay transparency
    var chatAlpha            by remember { mutableFloatStateOf(0.5f) }
    var showChatSettings     by remember { mutableStateOf(false) }

    // Goal display
    var goalEnabled          by remember { mutableStateOf(false) }
    var goalLabel            by remember { mutableStateOf("Follower-Ziel") }
    var goalTarget           by remember { mutableIntStateOf(1000) }
    var goalCurrent          by remember { mutableIntStateOf(0) }
    var showGoalDialog       by remember { mutableStateOf(false) }
    var goalLabelInput       by remember { mutableStateOf("") }
    var goalTargetInput      by remember { mutableStateOf("") }
    var goalCurrentInput     by remember { mutableStateOf("") }

    // Silhouette / virtueller Greenscreen
    val silhouetteFilter     = remember { LiveSilhouetteFilter() }
    var silhouetteEnabled    by remember { mutableStateOf(false) }
    var silhouetteBgSet      by remember { mutableStateOf(false) }
    val silhouetteProc       = remember { AtomicBoolean(false) }
    val silhouetteExecutor   = remember { Executors.newSingleThreadExecutor { r -> Thread(r, "silhouette-ml").also { it.isDaemon = true } } }
    val segmentationProvider = viewModel.segmentationProvider
    val livePrefs            = remember { context.getSharedPreferences("live_prefs", android.content.Context.MODE_PRIVATE) }

    val bgPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@launch
                    val bgFile = File(context.filesDir, "live_silhouette_bg.jpg")
                    bgFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    livePrefs.edit().putString("silhouette_bg_path", bgFile.absolutePath).apply()
                    silhouetteFilter.setBackground(bmp)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { silhouetteBgSet = true }
                }.onFailure { e -> Timber.tag("CreatorLive").e(e, "Hintergrundbild konnte nicht geladen werden") }
            }
        }
    }

    // Gespeicherten Hintergrund beim Start laden
    LaunchedEffect(Unit) {
        val savedPath = livePrefs.getString("silhouette_bg_path", null)
        if (savedPath != null) {
            val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(savedPath) }.getOrNull()
            }
            if (bmp != null) {
                silhouetteFilter.setBackground(bmp)
                silhouetteBgSet = true
            }
        }
    }

    // Sticker placement
    val placedStickers       = remember { mutableStateListOf<PlacedSticker>() }
    val stickerOffsets       = remember { mutableStateMapOf<Int, Offset>() }
    var nextStickerId        by remember { mutableIntStateOf(0) }
    var showStickerPicker    by remember { mutableStateOf(false) }

    LaunchedEffect(showGoalDialog) {
        if (showGoalDialog) {
            goalLabelInput  = goalLabel
            goalTargetInput = goalTarget.toString()
            goalCurrentInput = goalCurrent.toString()
        }
    }

    val filteredMessages = remember(chatMessages, filterKeywords) {
        if (filterKeywords.isEmpty()) chatMessages
        else chatMessages.filter { msg ->
            filterKeywords.none { kw -> msg.text.contains(kw, ignoreCase = true) }
        }
    }

    // Adaptive bitrate state (for fallback when bandwidth drops)
    val isAdaptiveFallbackState = remember { mutableStateOf(false) }

    // Zoom
    var zoomLevel            by remember { mutableFloatStateOf(0.7f) }
    var maxZoom              by remember { mutableFloatStateOf(5f) }
    var showZoomControls     by remember { mutableStateOf(false) }
    val hideZoomJob          = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun applyZoom(newZoom: Float) {
        val cam = cameraRef.value ?: return
        zoomLevel = newZoom.coerceIn(0.5f, maxZoom)
        cam.setZoom(zoomLevel)
        showZoomControls = true
        hideZoomJob.value?.cancel()
        hideZoomJob.value = coroutineScope.launch {
            delay(2500)
            showZoomControls = false
        }
    }

    // Permissions
    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)       == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> hasPermissions = perms.values.all { it } }

    LaunchedEffect(Unit) {
        viewModel.loadStreamKey {}
        viewModel.connectLiveChat(creatorId)
        if (!hasPermissions) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
        // Start auto-hide timer immediately
        resetHideTimer()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraRef.value?.let { cam ->
                cam.removeImageListener()
                if (cam.isStreaming) { cam.stopStream(); viewModel.endStream {} }
                if (cam.isOnPreview) cam.stopPreview()
            }
            silhouetteExecutor.shutdownNow()
            viewModel.disconnectLiveChat()
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            coroutineScope.launch { chatListState.animateScrollToItem(chatMessages.size - 1) }
        }
    }

    // Silhouetten-Filter-Flag mit Compose-State synchron halten
    LaunchedEffect(silhouetteEnabled) {
        silhouetteFilter.isEnabled = silhouetteEnabled
    }

    // ConnectChecker with adaptive bitrate fallback
    val connectChecker = remember {
        object : ConnectChecker {
            override fun onConnectionStarted(url: String) { statusMessageState.value = context.getString(R.string.creator_live_status_connecting) }
            override fun onConnectionSuccess() {
                isStreamingState.value = true
                statusMessageState.value = context.getString(R.string.creator_live_status_live)
                isAdaptiveFallbackState.value = false
                Timber.tag("CreatorLive").i("RTMP-Verbindung erfolgreich")
            }
            override fun onConnectionFailed(reason: String) {
                isStreamingState.value = false
                statusMessageState.value = "Fehler: $reason"
                Timber.tag("CreatorLive").e("RTMP-Fehler: $reason")
            }
            override fun onNewBitrate(bitrate: Long) {
                val cam = cameraRef.value ?: return
                // Adaptive fallback: drop bitrate when bandwidth is tight
                if (bitrate < 300_000L && !isAdaptiveFallbackState.value) {
                    isAdaptiveFallbackState.value = true
                    cam.setVideoBitrateOnFly(400_000)
                    Timber.tag("CreatorLive").w("Adaptive fallback → 400 kbps")
                } else if (bitrate > 900_000L && isAdaptiveFallbackState.value) {
                    isAdaptiveFallbackState.value = false
                    cam.setVideoBitrateOnFly(config.qualityPreset.videoBitrate)
                    Timber.tag("CreatorLive").i("Adaptive restore → ${config.qualityPreset.videoBitrate} bps")
                }
            }
            override fun onDisconnect() { isStreamingState.value = false; statusMessageState.value = context.getString(R.string.creator_live_status_disconnected) }
            override fun onAuthError()   { statusMessageState.value = context.getString(R.string.creator_live_status_auth_error) }
            override fun onAuthSuccess() {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { resetHideTimer() }
            }
    ) {

        // ── Kamera-Preview ────────────────────────────────────────────────────
        if (hasPermissions) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val availWpx = constraints.maxWidth
                val availHpx = constraints.maxHeight
                val viewWpx: Int
                val viewHpx: Int
                if (availWpx.toLong() * 16 <= availHpx.toLong() * 9) {
                    viewWpx = availWpx
                    viewHpx = availWpx * 16 / 9
                } else {
                    viewHpx = availHpx
                    viewWpx = availHpx * 9 / 16
                }
                val viewWdp = with(density) { viewWpx.toDp() }
                val viewHdp = with(density) { viewHpx.toDp() }

                AndroidView(
                    factory = { ctx ->
                        OpenGlView(ctx).also { glView ->
                            glView.layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val cam = RtmpCamera2(glView, connectChecker)
                            cameraRef.value = cam
                            // Silhouetten-Filter auf die GL-Pipeline setzen
                            cam.getGlInterface().addFilter(silhouetteFilter)

                            // Bildlistener registrieren (320×240 für Segmentierung)
                            cam.addImageListener(SEG_W, SEG_H, android.graphics.ImageFormat.YUV_420_888, 1, true, object : Camera2ApiManager.ImageCallback {
                                override fun onImageAvailable(image: android.media.Image) {
                                    if (!silhouetteFilter.isEnabled) return
                                    if (!silhouetteProc.compareAndSet(false, true)) return
                                    val imgW = image.width
                                    val imgH = image.height
                                    val yPlane  = image.planes[0]
                                    val uPlane  = image.planes[1]
                                    val vPlane  = image.planes[2]
                                    val yRowStride  = yPlane.rowStride
                                    val uvRowStride = vPlane.rowStride
                                    val uvPixStride = vPlane.pixelStride
                                    // Build a correct NV21 buffer handling row/pixel strides
                                    val nv21 = ByteArray(imgW * imgH * 3 / 2)
                                    val yBuf = yPlane.buffer
                                    for (row in 0 until imgH) {
                                        yBuf.position(row * yRowStride)
                                        yBuf.get(nv21, row * imgW, imgW)
                                    }
                                    val vBuf = vPlane.buffer
                                    val uBuf = uPlane.buffer
                                    val uvBase = imgW * imgH
                                    for (row in 0 until imgH / 2) {
                                        for (col in 0 until imgW / 2) {
                                            val src = row * uvRowStride + col * uvPixStride
                                            val dst = uvBase + row * imgW + col * 2
                                            vBuf.position(src); nv21[dst]     = vBuf.get()
                                            uBuf.position(src); nv21[dst + 1] = uBuf.get()
                                        }
                                    }
                                    silhouetteExecutor.execute {
                                        try {
                                            val raw = nv21ToBitmap(nv21, imgW, imgH) ?: run { silhouetteProc.set(false); return@execute }
                                            // Kamera-Sensor ist um 90° gedreht – der Provider kompensiert intern
                                            val mask = segmentationProvider.segment(raw, 90)
                                            if (mask != null) {
                                                silhouetteFilter.updateMask(mask.buffer, mask.width, mask.height)
                                            }
                                            raw.recycle()
                                        } catch (e: Exception) {
                                            Timber.tag("CreatorLive").w("Segmentierung fehlgeschlagen: ${e.message}")
                                        } finally {
                                            silhouetteProc.set(false)
                                        }
                                    }
                                }
                            })

                            glView.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    if (!cam.isOnPreview) {
                                        val initialFacing = if (config.lensMode == LensMode.FRONT)
                                            CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK
                                        cam.startPreview(initialFacing, 1920, 1080, 30, 90)
                                        maxZoom = cam.getZoomRange().upper.coerceAtLeast(1f)
                                        cam.setZoom(0.7f)
                                    }
                                }
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    if (cam.isOnPreview) cam.stopPreview()
                                }
                            })
                        }
                    },
                    modifier = Modifier
                        .size(viewWdp, viewHdp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                applyZoom(zoomLevel * zoomChange)
                            }
                        }
                )
            }

            // Video-pause blackout overlay
            if (isVideoPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Video pausiert",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.creator_live_camera_mic_required), color = Color.White, fontSize = 15.sp)
                    Button(onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    }) { Text(stringResource(R.string.creator_live_grant_permission)) }
                }
            }
        }

        // ── Top-Bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (!isStreaming) onNavigateBack() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back), tint = Color.White)
            }

            Spacer(Modifier.width(8.dp))

            AnimatedVisibility(visible = isStreaming, enter = fadeIn(), exit = fadeOut()) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val scale by pulse.animateFloat(
                    initialValue = 1f, targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "pulse_scale"
                )
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("● LIVE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            if (isStreaming) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.People, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(viewerCount.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
            }

            // Comment filter button
            IconButton(
                onClick = { showFilterDialog = true; resetHideTimer() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (filterKeywords.isNotEmpty()) Color(0xFF7B1FA2).copy(alpha = 0.7f)
                        else Color.Black.copy(alpha = 0.45f)
                    )
            ) {
                Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.creator_live_filter_comments_cd), tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Chat transparency toggle
            IconButton(
                onClick = { showChatSettings = !showChatSettings; resetHideTimer() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (showChatSettings) Color(0xFF0288D1).copy(alpha = 0.7f)
                        else Color.Black.copy(alpha = 0.45f)
                    )
            ) {
                Icon(Icons.Default.People, contentDescription = stringResource(R.string.creator_live_chat_transparency_cd), tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Goal display toggle
            IconButton(
                onClick = { showGoalDialog = true; resetHideTimer() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (goalEnabled) Color(0xFF388E3C).copy(alpha = 0.7f)
                        else Color.Black.copy(alpha = 0.45f)
                    )
            ) {
                Icon(Icons.Default.Flag, contentDescription = stringResource(R.string.creator_live_goal_cd), tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Sticker picker toggle
            IconButton(
                onClick = { showStickerPicker = !showStickerPicker; showChatSettings = false; resetHideTimer() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (showStickerPicker) Color(0xFFF57C00).copy(alpha = 0.7f)
                        else Color.Black.copy(alpha = 0.45f)
                    )
            ) {
                Icon(Icons.Default.EmojiEmotions, contentDescription = stringResource(R.string.creator_live_sticker_cd), tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Silhouetten-Toggle (virtueller Greenscreen)
            IconButton(
                onClick = {
                    silhouetteEnabled = !silhouetteEnabled
                    silhouetteFilter.isEnabled = silhouetteEnabled
                    showStickerPicker = false
                    showChatSettings = false
                    resetHideTimer()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (silhouetteEnabled) Color(0xFF00897B).copy(alpha = 0.8f)
                        else Color.Black.copy(alpha = 0.45f)
                    )
            ) {
                Icon(Icons.Default.Person, contentDescription = "Silhouette", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            // Hintergrundbild wählen (nur wenn Silhouette aktiv)
            if (silhouetteEnabled) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { bgPickerLauncher.launch("image/*"); resetHideTimer() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (silhouetteBgSet) Color(0xFF00897B).copy(alpha = 0.55f)
                            else Color.Black.copy(alpha = 0.45f)
                        )
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Hintergrund wählen", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(6.dp))

            Text(
                statusMessage,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        // ── Goal display ──────────────────────────────────────────────────────
        if (goalEnabled) {
            val progress = if (goalTarget > 0) (goalCurrent.toFloat() / goalTarget).coerceIn(0f, 1f) else 0f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 58.dp, start = 12.dp, end = 12.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(goalLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("$goalCurrent / $goalTarget", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFE53935),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }

        // ── Live-Chat Overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isStreaming && filteredMessages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(start = 12.dp, bottom = 120.dp)
        ) {
            LazyColumn(
                state = chatListState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(filteredMessages.takeLast(30)) { msg ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = chatAlpha))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = msg.userName,
                            color = Color(0xFFFDD835),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = msg.text,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── Zoom-Controls ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showZoomControls && hasPermissions,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Text("${maxZoom.toInt()}×", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                Box(
                    modifier = Modifier.height(160.dp).width(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Slider(
                        value = zoomLevel,
                        onValueChange = { applyZoom(it) },
                        valueRange = 0.5f..maxZoom,
                        modifier = Modifier
                            .width(160.dp)
                            .graphicsLayer { rotationZ = -90f },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
                Text("1×", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                Text("${"%.1f".format(zoomLevel)}×", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── Chat transparency panel ───────────────────────────────────────────
        AnimatedVisibility(
            visible = showChatSettings,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 130.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.creator_live_chat_label), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                Slider(
                    value = chatAlpha,
                    onValueChange = { chatAlpha = it },
                    valueRange = 0.1f..0.9f,
                    modifier = Modifier.width(110.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    "${(chatAlpha * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.width(32.dp)
                )
            }
        }

        // ── Sticker picker ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showStickerPicker,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LIVE_STICKERS.forEach { emoji ->
                    Text(
                        emoji,
                        fontSize = 26.sp,
                        modifier = Modifier.pointerInput(emoji) {
                            detectTapGestures {
                                val id = nextStickerId++
                                placedStickers.add(PlacedSticker(id, emoji))
                                stickerOffsets[id] = Offset(300f, 500f)
                                showStickerPicker = false
                                resetHideTimer()
                            }
                        }
                    )
                }
            }
        }

        // ── Placed stickers ───────────────────────────────────────────────────
        placedStickers.forEach { sticker ->
            val offset = stickerOffsets[sticker.id] ?: Offset(300f, 500f)
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .pointerInput(sticker.id) {
                        detectDragGestures { _, dragAmount ->
                            stickerOffsets[sticker.id] =
                                (stickerOffsets[sticker.id] ?: Offset(300f, 500f)) + dragAmount
                        }
                    }
            ) {
                Text(sticker.emoji, fontSize = 38.sp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                placedStickers.removeIf { it.id == sticker.id }
                                stickerOffsets.remove(sticker.id)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Bottom-Controls (auto-hide) ───────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mikrofon-Toggle
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            if (isMuted) cameraRef.value?.disableAudio()
                            else cameraRef.value?.enableAudio()
                            resetHideTimer()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) stringResource(R.string.creator_live_mic_on_cd) else stringResource(R.string.creator_live_mic_off_cd),
                            tint = if (isMuted) Color(0xFFEF5350) else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // ── Haupt-Stream-Button ──────────────────────────────────
                    if (!isStreaming) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    val cam  = cameraRef.value ?: return@IconButton
                                    val info = streamKeyInfo ?: return@IconButton
                                    statusMessage = context.getString(R.string.creator_live_status_connecting)
                                    viewModel.startStream { success ->
                                        if (success) {
                                            val rtmpUrl = "${info.rtmpUrl}/${info.streamKey}"
                                            val audioOk = cam.prepareAudio(128 * 1024, 44100, config.stereoMode)
                                            val videoOk = cam.prepareVideo(
                                                config.qualityPreset.width,
                                                config.qualityPreset.height,
                                                30,
                                                config.qualityPreset.videoBitrate,
                                                2,
                                                90
                                            )
                                            if (audioOk && videoOk) {
                                                cam.startStream(rtmpUrl)
                                            } else {
                                                statusMessage = context.getString(R.string.creator_live_status_prep_failed)
                                            }
                                        } else {
                                            statusMessage = context.getString(R.string.creator_live_status_server_error)
                                        }
                                    }
                                    resetHideTimer()
                                },
                                enabled = hasPermissions && streamKeyInfo != null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasPermissions && streamKeyInfo != null)
                                            Color(0xFFE53935)
                                        else
                                            Color(0xFF555555)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFE53935)))
                                }
                            }
                        }
                    } else {
                        // STOP – pulsierend
                        val pulse = rememberInfiniteTransition(label = "stop_pulse")
                        val scale by pulse.animateFloat(
                            initialValue = 1f, targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                            label = "stop_scale"
                        )
                        IconButton(
                            onClick = {
                                cameraRef.value?.stopStream()
                                viewModel.endStream {}
                                isStreaming = false
                                isVideoPaused = false
                                statusMessage = context.getString(R.string.creator_live_status_ended)
                                resetHideTimer()
                            },
                            modifier = Modifier
                                .scale(scale)
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.creator_live_stream_end_cd), tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }

                    // Kamera wechseln / Pause (gestapelt rechts)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pause-Button (nur während Stream)
                        if (isStreaming) {
                            IconButton(
                                onClick = {
                                    isVideoPaused = !isVideoPaused
                                    resetHideTimer()
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isVideoPaused) Color(0xFFFFA726).copy(alpha = 0.8f)
                                        else Color.Black.copy(alpha = 0.6f)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isVideoPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isVideoPaused) stringResource(R.string.creator_live_video_resume_cd) else stringResource(R.string.creator_live_video_pause_cd),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Kamera wechseln
                        IconButton(
                            onClick = {
                                useFrontCamera = !useFrontCamera
                                cameraRef.value?.switchCamera()
                                zoomLevel = 0.7f
                                cameraRef.value?.setZoom(0.7f)
                                cameraRef.value?.let { maxZoom = it.getZoomRange().upper.coerceAtLeast(1f) }
                                showZoomControls = false
                                resetHideTimer()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = stringResource(R.string.creator_live_switch_camera_cd), tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                }

                if (!isStreaming && streamKeyInfo == null) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.creator_live_stream_key_loading), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }

    // ── Ziel-Dialog ───────────────────────────────────────────────────────────
    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(stringResource(R.string.creator_live_goal_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Zeige einen Fortschrittsbalken im Stream (z. B. Follower oder Sparks).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = goalLabelInput,
                        onValueChange = { goalLabelInput = it },
                        label = { Text(stringResource(R.string.creator_live_goal_name_label)) },
                        placeholder = { Text(stringResource(R.string.creator_live_goal_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = goalTargetInput,
                        onValueChange = { goalTargetInput = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.creator_live_goal_target_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = goalCurrentInput,
                        onValueChange = { goalCurrentInput = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.creator_live_goal_current_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    if (goalEnabled) {
                        TextButton(
                            onClick = { goalEnabled = false; showGoalDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text(stringResource(R.string.creator_live_goal_hide)) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    goalLabel   = goalLabelInput.ifBlank { "Ziel" }
                    goalTarget  = goalTargetInput.toIntOrNull()?.coerceAtLeast(1) ?: 1000
                    goalCurrent = goalCurrentInput.toIntOrNull() ?: 0
                    goalEnabled = true
                    showGoalDialog = false
                }) { Text(stringResource(R.string.creator_live_goal_activate)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text(stringResource(R.string.general_cancel)) }
            }
        )
    }

    // ── Kommentar-Filter Dialog ───────────────────────────────────────────────
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false; filterInput = "" },
            title = { Text(stringResource(R.string.creator_live_filter_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Nachrichten mit diesen Wörtern werden im Overlay ausgeblendet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = filterInput,
                            onValueChange = { filterInput = it },
                            placeholder = { Text(stringResource(R.string.creator_live_filter_keyword_placeholder)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val kw = filterInput.trim()
                                if (kw.isNotEmpty()) {
                                    filterKeywords = filterKeywords + kw
                                    filterInput = ""
                                }
                            },
                            enabled = filterInput.isNotBlank()
                        ) { Text(stringResource(R.string.creator_live_filter_add)) }
                    }
                    if (filterKeywords.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            filterKeywords.forEach { kw ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(kw, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { filterKeywords = filterKeywords - kw }) {
                                        Text(stringResource(R.string.creator_live_filter_remove), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false; filterInput = "" }) { Text(stringResource(R.string.creator_live_filter_done)) }
            }
        )
    }
}
