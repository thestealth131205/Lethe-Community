package com.securechat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceHolder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow

// ── Shared config types ───────────────────────────────────────────────────────

enum class LensMode(val label: String) {
    FRONT("Front"),
    BACK_MAIN("Haupt"),
    BACK_WIDE("Weitwinkel")
}

enum class QualityPreset(val label: String, val videoBitrate: Int, val width: Int, val height: Int) {
    DATA_SAVING("Datensparend", 600_000, 854, 480),
    STANDARD("Standard", 1_200_000, 1280, 720)
}

enum class VoiceEffect(val label: String) {
    NONE("Kein"),
    ECHO("Echo"),
    ROBOT("Roboter")
}

data class LiveStreamConfig(
    val lensMode: LensMode = LensMode.FRONT,
    val qualityPreset: QualityPreset = QualityPreset.STANDARD,
    val micGain: Float = 1.0f,
    val stereoMode: Boolean = true,
    val audioMonitoring: Boolean = false,
    val voiceEffect: VoiceEffect = VoiceEffect.NONE,
    val mirrorMode: Boolean = true,
    val focusLocked: Boolean = false
)

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Vorbereitungs-Screen vor dem Stream: Linsenauswahl, Qualitätspresets,
 * Mikrofon-Gain, Stereo, Audio-Monitoring, Stimm-Effekte, Spiegeln, Fokussperre.
 * Navigiert via onStartStream(config) zum CreatorLiveScreen.
 */
@Composable
fun LiveStreamConfigScreen(
    viewModel: MainViewModel,
    onStartStream: (LiveStreamConfig) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var lensMode by remember { mutableStateOf(LensMode.FRONT) }
    var qualityPreset by remember { mutableStateOf(QualityPreset.STANDARD) }
    var micGain by remember { mutableFloatStateOf(1.0f) }
    var stereoMode by remember { mutableStateOf(true) }
    var audioMonitoring by remember { mutableStateOf(false) }
    var voiceEffect by remember { mutableStateOf(VoiceEffect.NONE) }
    var mirrorMode by remember { mutableStateOf(true) }
    var focusLocked by remember { mutableStateOf(false) }
    var focusIndicatorVisible by remember { mutableStateOf(false) }

    val streamKeyInfo by viewModel.liveStreamKeyInfo.collectAsState()
    var showStreamKey by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Tracks current preview facing so we only switchCamera() when it actually changes
    val currentFacingRef = remember { mutableStateOf(CameraHelper.Facing.FRONT) }
    val cameraRef = remember { mutableStateOf<RtmpCamera2?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> hasPermissions = perms.values.all { it } }

    LaunchedEffect(Unit) {
        viewModel.loadStreamKey {}
        if (!hasPermissions) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraRef.value?.let { cam -> if (cam.isOnPreview) cam.stopPreview() }
        }
    }

    fun switchLens(newMode: LensMode) {
        val cam = cameraRef.value ?: run { lensMode = newMode; return }
        val newFacing = if (newMode == LensMode.FRONT) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK
        if (newFacing != currentFacingRef.value && cam.isOnPreview) {
            cam.switchCamera()
            currentFacingRef.value = newFacing
        }
        lensMode = newMode
    }

    // No-op ConnectChecker for preview-only use
    val dummyChecker = remember {
        object : ConnectChecker {
            override fun onConnectionStarted(url: String) {}
            override fun onConnectionSuccess() {}
            override fun onConnectionFailed(reason: String) {}
            override fun onNewBitrate(bitrate: Long) {}
            override fun onDisconnect() {}
            override fun onAuthError() {}
            override fun onAuthSuccess() {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ── Camera preview ────────────────────────────────────────────────────
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
                            val cam = RtmpCamera2(glView, dummyChecker)
                            cameraRef.value = cam
                            glView.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    if (!cam.isOnPreview) {
                                        cam.startPreview(CameraHelper.Facing.FRONT, 1920, 1080, 30, 90)
                                    }
                                }
                                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    if (cam.isOnPreview) cam.stopPreview()
                                }
                            })
                        }
                    },
                    modifier = Modifier
                        .size(viewWdp, viewHdp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    focusLocked = !focusLocked
                                    focusIndicatorVisible = true
                                    coroutineScope.launch {
                                        delay(1800)
                                        focusIndicatorVisible = false
                                    }
                                }
                            )
                        }
                )
            }

            // Focus-lock indicator (center)
            AnimatedVisibility(
                visible = focusIndicatorVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(2.dp, if (focusLocked) Color.Yellow else Color.White, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        if (focusLocked) "AF ●" else "AF ○",
                        color = if (focusLocked) Color.Yellow else Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.live_config_camera_mic_required), color = Color.White, fontSize = 15.sp)
                    Button(onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    }) { Text(stringResource(R.string.live_config_grant_permission)) }
                }
            }
        }

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.BottomCenter)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
        )

        // ── Top bar: back + lens selector + mirror ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back), tint = Color.White)
            }

            Spacer(Modifier.weight(1f))

            // Lens selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LensMode.entries.forEach { mode ->
                    val selected = lensMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { switchLens(mode) },
                        label = { Text(mode.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.Black.copy(alpha = 0.55f),
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Mirror toggle
            IconButton(
                onClick = { mirrorMode = !mirrorMode },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (mirrorMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = stringResource(R.string.live_config_mirror_cd), tint = Color.White)
            }
        }

        // ── Bottom settings ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Quality preset
            ConfigRow(label = stringResource(R.string.live_config_quality_label)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = qualityPreset == preset,
                            onClick = { qualityPreset = preset },
                            label = { Text(preset.label, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE53935),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Microphone gain
            ConfigRow(label = stringResource(R.string.live_config_mic_label)) {
                Slider(
                    value = micGain,
                    onValueChange = { micGain = it },
                    valueRange = 0.5f..3.0f,
                    modifier = Modifier.width(140.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(
                    "${"%.1f".format(micGain)}×",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(32.dp)
                )
            }

            // Stereo + monitoring toggles
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConfigToggleChip(stringResource(R.string.live_config_stereo), stereoMode) { stereoMode = it }
                ConfigToggleChip(stringResource(R.string.live_config_monitoring), audioMonitoring) { audioMonitoring = it }
            }

            // Voice effects
            ConfigRow(label = stringResource(R.string.live_config_voice_label)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VoiceEffect.entries.forEach { effect ->
                        FilterChip(
                            selected = voiceEffect == effect,
                            onClick = { voiceEffect = effect },
                            label = { Text(effect.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF7B1FA2),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Streaming software (OBS etc.)
            streamKeyInfo?.let { info ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.live_config_streaming_software),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    // RTMP server URL
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.live_config_server_url_label), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                            Text(
                                info.rtmpUrl,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(info.rtmpUrl)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.live_config_copy_url_cd),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    // Stream key
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.live_config_stream_key_label), color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                            Text(
                                if (showStreamKey) info.streamKey
                                else "•".repeat(minOf(info.streamKey.length, 28)),
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row {
                            IconButton(
                                onClick = { showStreamKey = !showStreamKey },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (showStreamKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(info.streamKey)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.live_config_copy_key_cd),
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Start stream button
            Button(
                onClick = {
                    onStartStream(
                        LiveStreamConfig(
                            lensMode = lensMode,
                            qualityPreset = qualityPreset,
                            micGain = micGain,
                            stereoMode = stereoMode,
                            audioMonitoring = audioMonitoring,
                            voiceEffect = voiceEffect,
                            mirrorMode = mirrorMode,
                            focusLocked = focusLocked
                        )
                    )
                },
                enabled = hasPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.live_config_start_button), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

@Composable
private fun ConfigRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(72.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ConfigToggleChip(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = checked,
        onClick = { onToggle(!checked) },
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = if (checked) {
            { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF388E3C),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = Color.White.copy(alpha = 0.15f),
            labelColor = Color.White
        )
    )
}
