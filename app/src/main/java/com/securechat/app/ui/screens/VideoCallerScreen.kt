package com.securechat.app.ui.screens

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.BlurOff
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.network.GroupMemberInfo
import com.securechat.app.ui.CallParticipant
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ══════════════════════════════════════════════════════════════════════════════
// FLASH STATE – Zustände für Taschenlampe / Softlicht im Videoanruf
// ══════════════════════════════════════════════════════════════════════════════

enum class FlashState {
    OFF, WARM_WHITE, PINK, RED, BLUE, COLD_WHITE;

    fun next(): FlashState = when (this) {
        OFF        -> WARM_WHITE
        WARM_WHITE -> PINK
        PINK       -> RED
        RED        -> BLUE
        BLUE       -> COLD_WHITE
        COLD_WHITE -> OFF
    }

    val isOn: Boolean get() = this != OFF

    val borderColor: Color get() = when (this) {
        OFF        -> Color.Transparent
        WARM_WHITE -> Color(0xFFFFF5E0).copy(alpha = 0.55f)
        PINK       -> Color(0xFFFF69B4).copy(alpha = 0.70f)
        RED        -> Color(0xFFFF3B30).copy(alpha = 0.70f)
        BLUE       -> Color(0xFF007AFF).copy(alpha = 0.70f)
        COLD_WHITE -> Color(0xFFE8F4FF).copy(alpha = 0.70f)
    }

    val buttonColor: Color get() = when (this) {
        OFF        -> Color.Black.copy(alpha = 0.5f)
        WARM_WHITE -> Color(0xFFFFF5E0).copy(alpha = 0.85f)
        PINK       -> Color(0xFFFF69B4).copy(alpha = 0.85f)
        RED        -> Color(0xFFFF3B30).copy(alpha = 0.85f)
        BLUE       -> Color(0xFF007AFF).copy(alpha = 0.85f)
        COLD_WHITE -> Color(0xFFE8F4FF).copy(alpha = 0.85f)
    }

    val iconTint: Color get() = if (this == OFF) Color.White else Color.Black
}

// ══════════════════════════════════════════════════════════════════════════════
// 1. NETWORK CAPABILITY MONITOR – checks for active internet connection
// ══════════════════════════════════════════════════════════════════════════════

class VideoCapabilityMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isVideoCapable = MutableStateFlow(false)
    val isVideoCapable: StateFlow<Boolean> = _isVideoCapable.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            caps: NetworkCapabilities
        ) = evaluate(caps)

        override fun onLost(network: Network) {
            _isVideoCapable.value = false
        }
    }

    private var callbackRegistered = false

    init {
        try {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
            callbackRegistered = true
        } catch (e: Exception) {
            android.util.Log.w("LETHE_VIDEO", "VideoCapabilityMonitor: registerNetworkCallback fehlgeschlagen: ${e.message}")
        }
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.let { evaluate(it) }
    }

    private fun evaluate(caps: NetworkCapabilities) {
        _isVideoCapable.value = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> true
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true // 4G/5G always capable
            else -> false
        }
    }

    fun cleanup() {
        if (callbackRegistered) {
            connectivityManager.unregisterNetworkCallback(callback)
            callbackRegistered = false
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. WebRTC VIDEO RENDERER  –  wraps SurfaceViewRenderer safely
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun WebRtcVideoRenderer(
    videoTrack: VideoTrack?,
    eglBaseContext: org.webrtc.EglBase.Context?,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false
) {
    // Don't render at all until the EGL context is ready (avoids uninitialized renderer)
    if (eglBaseContext == null) return

    // key() forces full recreation of the AndroidView whenever the EGL context changes
    key(eglBaseContext) {
        // Track which VideoTrack is currently attached to avoid duplicate addSink calls
        val attachedTrack = remember { mutableStateOf<VideoTrack?>(null) }

        AndroidView(
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    init(eglBaseContext, null)
                    // Hardware-Scaler AUS: sonst dimensioniert der Renderer die SurfaceView
                    // auf das Seitenverhältnis des Frames → im Querformat (gedrehtes Gerät,
                    // 9:16-Frame in 16:9-Container) entstehen schwarze Balken oben/unten.
                    // Mit FILL füllt der Renderer den gesamten Container und beschneidet
                    // stattdessen (kein Letterboxing).
                    setEnableHardwareScaler(false)
                    setScalingType(
                        RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                        RendererCommon.ScalingType.SCALE_ASPECT_FILL
                    )
                    setMirror(isLocal)
                    // Lokales PiP muss über dem Remote-SurfaceViewRenderer liegen (SurfaceView Z-Order)
                    if (isLocal) setZOrderMediaOverlay(true)
                }
            },
            update = { renderer ->
                val prev = attachedTrack.value
                if (prev != videoTrack) {
                    prev?.removeSink(renderer)
                    videoTrack?.addSink(renderer)
                    attachedTrack.value = videoTrack
                }
            },
            onRelease = { renderer ->
                attachedTrack.value?.removeSink(renderer)
                renderer.release()
            },
            modifier = modifier
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2b. CALL EMOJI REACTIONS  –  Animated emoji overlays with sound
// ══════════════════════════════════════════════════════════════════════════════

enum class CallEmoji(
    val glyph: String,
    val label: String,
    val soundRes: Int?,          // res/raw/emoji_xxx.mp3 — null = kein Ton
    val bgTint: Color
) {
    POOP(     "💩", "Pfui",     R.raw.emoji_monkey,  Color(0xFF8B5E3C)),
    MONKEY(   "🙈", "Ups",      R.raw.emoji_poop,    Color(0xFF6D4C2A)),
    HEART_EYES("😍","Verliebt", R.raw.emoji_heart,   Color(0xFFE91E8C)),
    LAUGH(    "😂", "Lachen",   R.raw.emoji_laugh,   Color(0xFFFFC107)),
    PARTY(    "🎉", "Party!",   R.raw.emoji_party,   Color(0xFFFF6B35)),
}

/** Partikel-Datenpunkt für Floating-Effekte. */
private data class EmojiParticle(val x: Float, val delay: Float, val glyph: String)

/**
 * Vollbild-Overlay, das 4 Sekunden lang eine animierte Emoji-Reaktion zeigt.
 * Fades automatisch aus und ruft [onDismiss] auf.
 */
@Composable
fun EmojiReactionAnimation(
    emoji: CallEmoji,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── Sound ──────────────────────────────────────────────────────────────
    DisposableEffect(emoji) {
        val player = emoji.soundRes?.let { resId ->
            try {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    val afd = context.resources.openRawResourceFd(resId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    prepare()
                    start()
                }
            } catch (_: Exception) { null }
        }
        onDispose { player?.release() }
    }

    // ── Auto-Dismiss nach 4 s ──────────────────────────────────────────────
    LaunchedEffect(emoji) {
        delay(4_000)
        onDismiss()
    }

    // ── Fade-in/out Gesamtoverlay ──────────────────────────────────────────
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(emoji) {
        alphaAnim.animateTo(1f, tween(300))
        delay(3_200)
        alphaAnim.animateTo(0f, tween(500))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "emojiAnim")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = alphaAnim.value },
        contentAlignment = Alignment.Center
    ) {
        when (emoji) {
            // ── 💩 Kackhaufen: hüpft + Gestankspartikel steigen auf ─────────
            CallEmoji.POOP -> {
                val bounceY by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = -30f,
                    animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "bounceY"
                )
                val stinkParticles = remember {
                    listOf("💨","💨","🤢","💨","💨").mapIndexed { i, g ->
                        EmojiParticle(x = (-80f + i * 40f), delay = i * 0.15f, glyph = g)
                    }
                }
                stinkParticles.forEach { p ->
                    val floatY by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = -160f,
                        animationSpec = infiniteRepeatable(
                            tween((1500 + (p.delay * 400).toInt()), easing = FastOutSlowInEasing),
                            RepeatMode.Restart
                        ),
                        label = "stink${p.x}"
                    )
                    val stinkAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.9f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            tween((1500 + (p.delay * 400).toInt())),
                            RepeatMode.Restart
                        ),
                        label = "stinkA${p.x}"
                    )
                    Text(
                        text = p.glyph,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .offset(x = p.x.dp, y = (-80f + floatY).dp)
                            .graphicsLayer { alpha = stinkAlpha }
                    )
                }
                Text(
                    text = emoji.glyph,
                    fontSize = 120.sp,
                    modifier = Modifier.offset(y = bounceY.dp)
                )
            }

            // ── 🙈 Affe: schaukelt links-rechts, kurzes Peeken ──────────────
            CallEmoji.MONKEY -> {
                val rotDeg by infiniteTransition.animateFloat(
                    initialValue = -18f, targetValue = 18f,
                    animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "monkeyRot"
                )
                Text(
                    text = emoji.glyph,
                    fontSize = 120.sp,
                    modifier = Modifier.graphicsLayer { rotationZ = rotDeg }
                )
            }

            // ── 😍 Herz-Augen: pulsiert + schwebende Herzen ─────────────────
            CallEmoji.HEART_EYES -> {
                val pulse by infiniteTransition.animateFloat(
                    initialValue = 0.85f, targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "heartPulse"
                )
                val heartParticles = remember {
                    listOf("❤️","💕","💖","❤️","💗").mapIndexed { i, g ->
                        EmojiParticle(x = (-100f + i * 50f), delay = i * 0.2f, glyph = g)
                    }
                }
                heartParticles.forEach { p ->
                    val hy by infiniteTransition.animateFloat(
                        initialValue = 60f, targetValue = -200f,
                        animationSpec = infiniteRepeatable(
                            tween((1800 + (p.delay * 300).toInt()), easing = FastOutSlowInEasing),
                            RepeatMode.Restart
                        ),
                        label = "heartY${p.x}"
                    )
                    val ha by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            tween((1800 + (p.delay * 300).toInt())),
                            RepeatMode.Restart
                        ),
                        label = "heartA${p.x}"
                    )
                    Text(
                        text = p.glyph,
                        fontSize = 30.sp,
                        modifier = Modifier
                            .offset(x = p.x.dp, y = hy.dp)
                            .graphicsLayer { alpha = ha }
                    )
                }
                Text(
                    text = emoji.glyph,
                    fontSize = 120.sp,
                    modifier = Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
                )
            }

            // ── 😂 Lachen: vibriert + Tränen fliegen ────────────────────────
            CallEmoji.LAUGH -> {
                val shakeX by infiniteTransition.animateFloat(
                    initialValue = -12f, targetValue = 12f,
                    animationSpec = infiniteRepeatable(tween(120, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "laughShake"
                )
                val scaleL by infiniteTransition.animateFloat(
                    initialValue = 0.92f, targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(tween(240, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "laughScale"
                )
                val tearParticles = remember {
                    listOf(-60f, 60f).map { x ->
                        EmojiParticle(x = x, delay = 0f, glyph = "💧")
                    }
                }
                tearParticles.forEach { p ->
                    val ty by infiniteTransition.animateFloat(
                        initialValue = 30f, targetValue = 120f,
                        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Restart),
                        label = "tearY${p.x}"
                    )
                    val ta by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Restart),
                        label = "tearA${p.x}"
                    )
                    Text(
                        text = p.glyph,
                        fontSize = 26.sp,
                        modifier = Modifier
                            .offset(x = p.x.dp, y = ty.dp)
                            .graphicsLayer { alpha = ta }
                    )
                }
                Text(
                    text = emoji.glyph,
                    fontSize = 120.sp,
                    modifier = Modifier.graphicsLayer {
                        translationX = shakeX
                        scaleX = scaleL; scaleY = scaleL
                    }
                )
            }

            // ── 🎉 Party: hüpft + Konfetti-Partikel regnen herunter ──────────
            CallEmoji.PARTY -> {
                val bounceP by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = -25f,
                    animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "partyBounce"
                )
                val rotP by infiniteTransition.animateFloat(
                    initialValue = -15f, targetValue = 15f,
                    animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "partyRot"
                )
                val confettiParticles = remember {
                    listOf(
                        EmojiParticle(x = -80f, delay = 0f,    glyph = "🟥"),
                        EmojiParticle(x = -40f, delay = 150f,  glyph = "🟨"),
                        EmojiParticle(x =   0f, delay = 50f,   glyph = "🟦"),
                        EmojiParticle(x =  40f, delay = 200f,  glyph = "🟩"),
                        EmojiParticle(x =  80f, delay = 100f,  glyph = "🟪"),
                    )
                }
                confettiParticles.forEach { p ->
                    val cy by infiniteTransition.animateFloat(
                        initialValue = -120f, targetValue = 80f,
                        animationSpec = infiniteRepeatable(
                            tween(900, easing = FastOutSlowInEasing, delayMillis = p.delay.toInt()),
                            RepeatMode.Restart
                        ),
                        label = "confY${p.x}"
                    )
                    val ca by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            tween(900, delayMillis = p.delay.toInt()),
                            RepeatMode.Restart
                        ),
                        label = "confA${p.x}"
                    )
                    Text(
                        text = p.glyph,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .offset(x = p.x.dp, y = cy.dp)
                            .graphicsLayer { alpha = ca }
                    )
                }
                Text(
                    text = emoji.glyph,
                    fontSize = 120.sp,
                    modifier = Modifier.graphicsLayer {
                        translationY = bounceP
                        rotationZ = rotP
                    }
                )
            }
        }

        // Emoji-Label unten
        Text(
            text = emoji.label,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

/**
 * Ausklappbares Emoji-Picker-Panel (4 Emojis in Zeile 1, Party-Emoji in Zeile 2).
 */
@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (CallEmoji) -> Unit,
    onDismiss: () -> Unit
) {
    val allEmojis = CallEmoji.entries
    val firstRow = allEmojis.dropLast(1)
    val secondRow = allEmojis.takeLast(1)

    @Composable
    fun EmojiButton(emoji: CallEmoji) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(emoji.bgTint.copy(alpha = 0.3f))
                .clickable {
                    onEmojiSelected(emoji)
                    onDismiss()
                }
                .padding(12.dp)
        ) {
            Text(emoji.glyph, fontSize = 40.sp)
            Spacer(Modifier.height(4.dp))
            Text(emoji.label, color = Color.White, fontSize = 11.sp)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 180.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                firstRow.forEach { emoji -> EmojiButton(emoji) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                secondRow.forEach { emoji -> EmojiButton(emoji) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. INCOMING CALL SCREEN  –  FaceTime-style ringing UI
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun IncomingCallScreen(
    callerName: String,
    callerImageUrl: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    callType: String = "VIDEO",
    isGroupCall: Boolean = false,
    groupParticipants: List<Pair<String, String?>> = emptyList(),
    groupName: String? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // ── Blurred background ────────────────────────────────────────────────
        if (callerImageUrl != null) {
            AsyncImage(
                model = callerImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 40.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                        )
                    )
            )
        }

        // ── Dark overlay ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 64.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Caller info ───────────────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isGroupCall && groupParticipants.isNotEmpty()) {
                    // Gruppenanruf: gestapelte Avatare (Einladender + bestehende Teilnehmer)
                    val allParticipants = listOf(callerName to callerImageUrl) +
                        groupParticipants.take(3)
                    Box(
                        modifier = Modifier
                            .height(120.dp)
                            .width((100 + (allParticipants.size - 1) * 50).dp.coerceAtMost(220.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        allParticipants.forEachIndexed { index, (_, imgUrl) ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (index * 50).dp)
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                if (imgUrl != null) {
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = callerName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light
                    )
                    if (groupParticipants.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "mit " + groupParticipants.take(2).joinToString(", ") { it.first } +
                                if (groupParticipants.size > 2) " +${groupParticipants.size - 2}" else "",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (callType == "VOICE") "Eingehender Gruppensprachanruf" else "Eingehender Gruppenvideoanruf",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                    if (!groupName.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = groupName,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Normaler 1-zu-1-Anruf
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (callerImageUrl != null) {
                            AsyncImage(
                                model = callerImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = callerName,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (callType == "VOICE") stringResource(R.string.video_call_voice_incoming) else stringResource(R.string.video_call_video_incoming),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }

            // ── Accept / Decline ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 52.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30)),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.video_call_decline),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.video_call_decline), color = Color.White, fontSize = 13.sp)
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34C759)),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            if (callType == "VOICE") Icons.Default.Call else Icons.Default.Videocam,
                            contentDescription = stringResource(R.string.video_call_accept),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.video_call_accept), color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 4. ACTIVE VIDEO CALL SCREEN  –  Edge-to-edge, PiP, Glassmorphism controls
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ActiveVideoCallScreen(
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    eglBaseContext: org.webrtc.EglBase.Context?,
    isMuted: Boolean,
    isScreenSharing: Boolean,
    isHdVideo: Boolean,
    isFrontCamera: Boolean,
    flashState: FlashState,
    partnerName: String,
    partnerImageUrl: String?,
    onToggleMute: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onToggleVideoQuality: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onTurnOffFlashlight: () -> Unit,
    onHangUp: () -> Unit,
    isBackgroundBlurEnabled: Boolean = false,
    onToggleBackgroundBlur: () -> Unit = {},
    contacts: List<ContactEntity> = emptyList(),
    additionalParticipants: Map<String, CallParticipant> = emptyMap(),
    activePartnerId: String? = null,
    onInviteContact: (String) -> Unit = {},
    onCancelInvite: (String) -> Unit = {},
    callDurationText: String? = null,
    onSendEmoji: (CallEmoji) -> Unit = {},
    remoteEmoji: CallEmoji? = null,
    isRecording: Boolean = false,
    partnerRecording: Boolean = false,
    onToggleRecording: () -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Keep screen on during call
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-hide controls
    var controlsVisible by remember { mutableStateOf(true) }
    // Wird bei JEDER Bildschirmberührung erhöht → setzt den Auto-Hide-Timer zurück
    var interactionTick by remember { mutableStateOf(0) }
    LaunchedEffect(controlsVisible, interactionTick) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(5_000)
            controlsVisible = false
        }
    }
    // Pill expand/collapse state
    var isPillExpanded by remember { mutableStateOf(false) }
    // Emoji-Picker anzeigen
    var showEmojiPicker by remember { mutableStateOf(false) }
    // Aktive Emoji-Animation
    var activeEmoji by remember { mutableStateOf<CallEmoji?>(null) }
    // Welcher Teilnehmer groß angezeigt wird: null = original remote, "local" = eigene Kamera, sonst userId
    var mainParticipantId by remember { mutableStateOf<String?>(null) }
    // Bildschirm-Bounds der eingeblendeten Steuer-Pille — Taps in diesem Bereich
    // dürfen NICHT das Haupt-/Kleinbild umschalten.
    var pillBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (controlsVisible) {
                        // Pille bereits eingeblendet → Tippen schaltet Haupt-/Kleinbild um,
                        // ABER nicht wenn der Tap im Bereich der Pille liegt.
                        val onPill = pillBounds?.contains(offset) == true
                        if (!onPill && additionalParticipants.isEmpty()) {
                            mainParticipantId = if (mainParticipantId == "local") null else "local"
                        }
                    } else {
                        // Pille war ausgeblendet → NUR einblenden, NICHT umschalten
                        controlsVisible = true
                    }
                    interactionTick++
                }
            }
            // Jede Berührung des Bildschirms (auch Scrollen/Drag der Pille) setzt
            // den Auto-Hide-Timer zurück, damit die Pille länger sichtbar bleibt.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any {
                                it.changedToDownIgnoreConsumed() || it.changedToUpIgnoreConsumed()
                            }) {
                            interactionTick++
                        }
                    }
                }
            }
    ) {

        // ── 4.1 Remote video (full-screen background) ─────────────────────────
        // Bestimme den anzuzeigenden Haupt-Track
        val mainTrack = when {
            mainParticipantId == "local" -> localVideoTrack
            mainParticipantId != null    -> additionalParticipants[mainParticipantId]?.videoTrack ?: remoteVideoTrack
            else                         -> remoteVideoTrack
        }
        val mainName = when {
            mainParticipantId == "local" -> stringResource(R.string.video_call_you)
            mainParticipantId != null    -> additionalParticipants[mainParticipantId]?.username ?: partnerName
            else                         -> partnerName
        }
        val mainImageUrl = when {
            mainParticipantId == "local" -> null
            mainParticipantId != null    -> additionalParticipants[mainParticipantId]?.imageUrl ?: partnerImageUrl
            else                         -> partnerImageUrl
        }
        if (mainTrack != null) {
            WebRtcVideoRenderer(
                videoTrack = mainTrack,
                eglBaseContext = eglBaseContext,
                isLocal = (mainParticipantId == "local"),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Connecting placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF1C2340), Color(0xFF0A0E1A))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mainImageUrl != null) {
                            AsyncImage(
                                model = mainImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(52.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(mainName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.video_call_connecting), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        }

        // ── 4.1a Blinkender REC-Hinweis (Partner zeichnet auf) oben links ──
        if (partnerRecording) {
            val recTransition = rememberInfiniteTransition(label = "recBlink")
            val recAlpha by recTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.15f,
                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "recAlpha"
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(recAlpha)
                        .background(Color(0xFFFF3B30), shape = CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "REC",
                    color = Color(0xFFFF3B30).copy(alpha = recAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── 4.1b Frontkamera-Softlicht: farbiger Rahmen je nach FlashState ──
        if (flashState.isOn && isFrontCamera) {
            val borderThickness = 28.dp
            val borderColor = flashState.borderColor
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(borderThickness).align(Alignment.TopCenter).background(borderColor))
                Box(modifier = Modifier.fillMaxWidth().height(borderThickness).align(Alignment.BottomCenter).background(borderColor))
                Box(modifier = Modifier.fillMaxHeight().width(borderThickness).align(Alignment.CenterStart).background(borderColor))
                Box(modifier = Modifier.fillMaxHeight().width(borderThickness).align(Alignment.CenterEnd).background(borderColor))
            }
        }

        // ── 4.2 Small PiP overlays (lokal + zusätzliche Teilnehmer, draggable) ──
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val pipW   = if (isLandscape) 160.dp else 110.dp
            val pipH   = if (isLandscape) 90.dp  else 160.dp
            val margin = 16.dp
            val safeTop = 32.dp

            val maxXPx = with(density) { (maxWidth  - pipW - margin).toPx() }
            val maxYPx = with(density) { (maxHeight - pipH - 180.dp).toPx() }
            val minXPx = with(density) { margin.toPx() }
            val minYPx = with(density) { safeTop.toPx() }

            // Baue Liste der kleinen Overlays: lokal + alle Zusatz-Teilnehmer die NICHT groß angezeigt werden
            data class PipEntry(val id: String, val track: VideoTrack?, val isLocal: Boolean, val imageUrl: String?, val name: String)
            val pipEntries = buildList<PipEntry> {
                // Lokal: nur einblenden wenn nicht als Haupt angezeigt
                if (mainParticipantId != "local") {
                    add(PipEntry("local", localVideoTrack, true, null, stringResource(R.string.video_call_you)))
                }
                // Originaler Remote-Partner: als PiP anzeigen wenn er nicht der Haupt-Stream ist
                // (mainParticipantId == null → remote ist Haupt, also kein PiP nötig)
                if (mainParticipantId != null) {
                    add(PipEntry("remote", remoteVideoTrack, false, partnerImageUrl, partnerName))
                }
                // Zusätzliche Teilnehmer als PiP (außer dem groß angezeigten)
                additionalParticipants.entries
                    .filter { it.key != mainParticipantId }
                    .forEach { (uid, p) -> add(PipEntry(uid, p.videoTrack, false, p.imageUrl, p.username)) }
            }

            pipEntries.forEachIndexed { idx, entry ->
                // Startposition: staffeln vertikal vom oberen Rand
                val startY = with(density) { (safeTop + (pipH + 12.dp) * idx).toPx() }
                val offsetX = remember(entry.id) { Animatable(maxXPx) }
                val offsetY = remember(entry.id) { Animatable(startY.coerceIn(minYPx, maxYPx)) }
                // Beim Drehen des Bildschirms PiP an den rechten Rand snappen
                LaunchedEffect(maxXPx) {
                    offsetX.animateTo(maxXPx, spring(stiffness = Spring.StiffnessMediumLow))
                }
                var pipEnlarged by remember(entry.id) { mutableStateOf(false) }
                val curPipW = if (pipEnlarged) pipW * 1.5f else pipW
                val curPipH = if (pipEnlarged) pipH * 1.5f else pipH
                // Extra-Breite beim Vergrößern nach links verschieben, damit PiP nicht aus dem Bild ragt
                val enlargeExtraWPx = with(density) { if (pipEnlarged) (pipW * 0.5f).toPx() else 0f }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(
                            (offsetX.value - enlargeExtraWPx).coerceAtLeast(minXPx).roundToInt(),
                            offsetY.value.roundToInt()
                        ) }
                        .size(width = curPipW, height = curPipH)
                        .animateContentSize()
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF222222))
                        .pointerInput(entry.id) {
                            detectTapGestures {
                                // Tippen → diese Ansicht wird groß, bisherige große wird PiP
                                // "remote" = Original-Partner → null bedeutet remote ist Haupt
                                mainParticipantId = if (entry.id == "remote") null else entry.id
                                controlsVisible = true
                            }
                        }
                        .pointerInput(entry.id + "_drag") {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        val targetX = if (offsetX.value > (minXPx + maxXPx) / 2f) maxXPx else minXPx
                                        val targetY = if (offsetY.value > (minYPx + maxYPx) / 2f) maxYPx else minYPx
                                        launch { offsetX.animateTo(targetX, spring(stiffness = Spring.StiffnessMediumLow)) }
                                        launch { offsetY.animateTo(targetY, spring(stiffness = Spring.StiffnessMediumLow)) }
                                    }
                                    controlsVisible = true
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    scope.launch {
                                        offsetX.snapTo((offsetX.value + delta.x).coerceIn(minXPx, maxXPx))
                                        offsetY.snapTo((offsetY.value + delta.y).coerceIn(minYPx, maxYPx))
                                    }
                                }
                            )
                        }
                ) {
                    if (entry.track != null) {
                        WebRtcVideoRenderer(
                            videoTrack = entry.track,
                            eglBaseContext = eglBaseContext,
                            isLocal = entry.isLocal,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (entry.imageUrl != null) {
                        AsyncImage(
                            model = entry.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }
                    // Name-Badge unten
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entry.name, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                    // Vergrößern-Button oben rechts
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                            .clickable { pipEnlarged = !pipEnlarged },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (pipEnlarged) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (pipEnlarged) "Verkleinern" else "Vergrößern",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    // Anruf-Abbrechen-Button oben links – nur für wartende Einladungen (noch kein Video)
                    if (!entry.isLocal && entry.id != "remote" && entry.track == null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(22.dp)
                                .background(Color(0xFFCC0000), shape = CircleShape)
                                .clickable { onCancelInvite(entry.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Einladung abbrechen",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── 4.3 Glassmorphism control pill (bottom center, auto-hides) ────────
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp)
                .navigationBarsPadding()
        ) {
            // Glass pill: blurred background layer + non-blurred buttons on top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .animateContentSize()
                    .onGloballyPositioned { pillBounds = it.boundsInRoot() }
            ) {
                // Frosted-glass background
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(9.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── Buttons row ──────────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Expand / collapse icon (ganz links)
                        IconButton(
                            onClick = { isPillExpanded = !isPillExpanded },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        ) {
                            Icon(
                                imageVector = if (isPillExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = if (isPillExpanded)
                                    stringResource(R.string.video_call_collapse_cd)
                                else
                                    stringResource(R.string.video_call_expand_cd),
                                tint = Color.White
                            )
                        }

                        // Mute
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMuted) Color.White else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isMuted) stringResource(R.string.video_call_unmute_cd) else stringResource(R.string.video_call_mute_cd),
                                tint = if (isMuted) Color.Black else Color.White
                            )
                        }

                        // Emoji-Reaktionen
                        IconButton(
                            onClick = {
                                showEmojiPicker = !showEmojiPicker
                                isPillExpanded = false
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showEmojiPicker) Color(0xFFFF9500)
                                    else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "Emoji-Reaktion",
                                tint = Color.White
                            )
                        }

                        // Hang up (kleinerer Touch-Bereich gegen versehentliches Auflegen)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3B30)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onHangUp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = stringResource(R.string.video_call_hangup_cd),
                                    tint = Color.White
                                )
                            }
                        }

                        // Switch camera
                        IconButton(
                            onClick = onSwitchCamera,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Default.Cameraswitch,
                                contentDescription = stringResource(R.string.video_call_switch_camera_cd),
                                tint = Color.White
                            )
                        }

                        // Screen share
                        IconButton(
                            onClick = onToggleScreenShare,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isScreenSharing) Color(0xFF007AFF) else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ScreenShare,
                                contentDescription = stringResource(R.string.video_call_share_screen_cd),
                                tint = Color.White
                            )
                        }

                        // Anruf aufzeichnen
                        IconButton(
                            onClick = onToggleRecording,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRecording) Color(0xFFFF3B30) else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.StopCircle else Icons.Default.FiberManualRecord,
                                contentDescription = if (isRecording) "Aufzeichnung stoppen" else "Anruf aufzeichnen",
                                tint = Color.White
                            )
                        }

                        // HD / SD quality toggle
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHdVideo) Color(0xFFA8A800) else Color.Black.copy(alpha = 0.5f)
                                )
                                .pointerInput(Unit) { detectTapGestures { onToggleVideoQuality() } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isHdVideo) "HD" else "SD",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // Taschenlampe / Softlicht – Tippen: Farbe wechseln, 2s halten: sofort ausschalten
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(flashState.buttonColor)
                                .pointerInput(onToggleFlashlight, onTurnOffFlashlight) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val up = withTimeoutOrNull(2000L) { waitForUpOrCancellation() }
                                        if (up != null) {
                                            onToggleFlashlight()
                                        } else {
                                            onTurnOffFlashlight()
                                            waitForUpOrCancellation()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (flashState == FlashState.COLD_WHITE || flashState == FlashState.OFF)
                                    Icons.Default.FlashOff else Icons.Default.FlashOn,
                                contentDescription = if (flashState.isOn) stringResource(R.string.video_call_torch_off_cd) else stringResource(R.string.video_call_torch_on_cd),
                                tint = flashState.iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Hintergrundunschärfe
                        IconButton(
                            onClick = onToggleBackgroundBlur,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isBackgroundBlurEnabled) Color(0xFF007AFF)
                                    else Color.Black.copy(alpha = 0.5f)
                                )
                        ) {
                            Icon(
                                imageVector = if (isBackgroundBlurEnabled) Icons.Default.BlurOn else Icons.Default.BlurOff,
                                contentDescription = if (isBackgroundBlurEnabled)
                                    stringResource(R.string.video_call_blur_bg_off_cd)
                                else
                                    stringResource(R.string.video_call_blur_bg_on_cd),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } // end Row
                    // Scroll-Pfeil: zeigt an, dass die Buttonleiste horizontal scrollbar ist
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 6.dp)
                    )
                    } // end Box

                    // ── Erweiterter Bereich: Kontaktliste ────────────────────
                    if (isPillExpanded) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.video_call_add_participant),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        val invitableContacts = contacts.filter { c ->
                            c.userId != activePartnerId && !additionalParticipants.containsKey(c.userId)
                        }
                        if (invitableContacts.isEmpty()) {
                            Text(
                                text = stringResource(R.string.video_call_no_contacts),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(invitableContacts) { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val imgUrl = contact.profileImageUrl?.let {
                                                if (it.startsWith("http")) it else "https://letheapp.de$it"
                                            }
                                            if (imgUrl != null) {
                                                AsyncImage(
                                                    model = imgUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        // Username
                                        Text(
                                            text = contact.username ?: contact.fakeNumber,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1
                                        )
                                        // Einladen Button
                                        val isAlreadyInvited = additionalParticipants.containsKey(contact.userId)
                                        IconButton(
                                            onClick = { if (!isAlreadyInvited) onInviteContact(contact.userId) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isAlreadyInvited) Color.Gray else Color(0xFF34C759)
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.PersonAdd,
                                                contentDescription = stringResource(R.string.video_call_invite_participant_cd),
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        // ── 4.5 Call duration – always visible at very bottom ─────────────
        if (callDurationText != null) {
            Text(
                text = callDurationText,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // ── 4.4 Top overlay: Partner-Name ──────────────────────────────────────
        if (controlsVisible && mainTrack != null) {
            Text(
                text = mainName,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // ── 4.6 Emoji-Picker Panel ──────────────────────────────────────────
        if (showEmojiPicker) {
            EmojiPickerPanel(
                onEmojiSelected = { emoji ->
                    activeEmoji = emoji
                    showEmojiPicker = false
                    onSendEmoji(emoji)
                },
                onDismiss = { showEmojiPicker = false }
            )
        }

        // ── 4.7 Aktive Emoji-Animation (vollbild, 4 s) ──────────────────────
        activeEmoji?.let { emoji ->
            EmojiReactionAnimation(
                emoji = emoji,
                onDismiss = { activeEmoji = null }
            )
        }

        // ── 4.8 Remote Emoji-Animation vom Anruf-Partner ────────────────────
        remoteEmoji?.let { emoji ->
            EmojiReactionAnimation(
                emoji = emoji,
                onDismiss = {}
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 5. FULL CALL SCREEN  –  Entry composable used by navigation
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Top-level composable for "video_call/{partnerId}" route.
 * Observes ViewModel state and delegates to [IncomingCallScreen] or [ActiveVideoCallScreen].
 */
@Composable
fun VideoCallScreen(
    partnerId: String,
    viewModel: MainViewModel,
    onCallEnded: () -> Unit,
    onToggleScreenShare: () -> Unit = {}
) {
    val localTrack        by viewModel.localVideoTrack.collectAsState()
    val remoteTrack       by viewModel.remoteVideoTrack.collectAsState()
    val isMuted           by viewModel.callIsMuted.collectAsState()
    val isScreenSharing   by viewModel.isScreenSharing.collectAsState()
    val isCallRecording   by viewModel.isCallRecording.collectAsState()
    val partnerRecording  by viewModel.partnerCallRecording.collectAsState()
    val recordingConsentFrom by viewModel.incomingRecordingConsentFrom.collectAsState()
    val callState         by viewModel.callState.collectAsState()
    val eglCtx            by viewModel.webRtcEglBaseContext.collectAsState()
    val callStatusMessage by viewModel.callStatusMessage.collectAsState()
    val isFrontCamera          by viewModel.isUsingFrontCamera.collectAsState()
    val isBackgroundBlurEnabled by viewModel.isBackgroundBlurEnabled.collectAsState()

    val contacts              by viewModel.contacts.collectAsState(initial = emptyList())
    val additionalParticipants by viewModel.additionalParticipants.collectAsState()
    val activePartnerId       by viewModel.activeCallPartnerId.collectAsState()
    val incomingCallEmojiName by viewModel.incomingCallEmoji.collectAsState()
    val remoteEmoji = incomingCallEmojiName?.let { name ->
        CallEmoji.entries.firstOrNull { it.name == name }
    }
    val partner     = contacts.find { it.userId == partnerId }
    val partnerName = partner?.username ?: partner?.fakeNumber ?: partnerId
    val partnerImageUrl = partner?.profileImageUrl?.let {
        if (it.startsWith("http")) it else "https://letheapp.de$it"
    }

    // Navigate back when the call ends; delay 2.5s if there's a status message to show
    LaunchedEffect(callState) {
        if (callState == MainViewModel.CallState.ENDED) {
            if (callStatusMessage != null) kotlinx.coroutines.delay(2_500L)
            onCallEnded()
        }
    }

    // Pause video on background
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                viewModel.onCallPause()
            }
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                viewModel.onCallResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var isHdVideo by remember { mutableStateOf(true) }
    var flashState by remember { mutableStateOf(FlashState.OFF) }
    val context = LocalContext.current

    // Call-Dauer: startet beim ersten CONNECTED-State
    var callDurationSeconds by remember { mutableStateOf(0) }
    var callTimerRunning by remember { mutableStateOf(false) }
    LaunchedEffect(callState) {
        if (callState == MainViewModel.CallState.CONNECTED && !callTimerRunning) {
            callTimerRunning = true
        }
    }
    LaunchedEffect(callTimerRunning) {
        if (callTimerRunning) {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                callDurationSeconds++
            }
        }
    }
    val callDurationText = if (callTimerRunning) {
        val h = callDurationSeconds / 3600
        val m = (callDurationSeconds % 3600) / 60
        val s = callDurationSeconds % 60
        if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    } else null

    // Cleanup: LED-Taschenlampe beim Verlassen des Screens immer ausschalten
    DisposableEffect(Unit) {
        onDispose {
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val backId = cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                }
                backId?.let { cm.setTorchMode(it, false) }
            } catch (_: Exception) {}
        }
    }

    // Turn off torch when switching to front camera
    LaunchedEffect(isFrontCamera) {
        if (isFrontCamera && flashState.isOn) {
            try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val backId = cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                }
                backId?.let { cm.setTorchMode(it, false) }
            } catch (_: Exception) {}
        }
    }

    // Bildschirmhelligkeit bei Frontkamera-Softlicht maximieren
    val activity = context as? android.app.Activity
    DisposableEffect(flashState.isOn && isFrontCamera) {
        val softLightActive = flashState.isOn && isFrontCamera
        if (softLightActive) {
            val lp = activity?.window?.attributes
            lp?.let {
                it.screenBrightness = 1.0f
                activity.window.attributes = it
            }
        }
        onDispose {
            val lp = activity?.window?.attributes
            lp?.let {
                it.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = it
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ActiveVideoCallScreen(
            localVideoTrack        = localTrack,
            remoteVideoTrack       = remoteTrack,
            eglBaseContext         = eglCtx,
            isMuted                = isMuted,
            isScreenSharing        = isScreenSharing,
            isHdVideo              = isHdVideo,
            isFrontCamera          = isFrontCamera,
            flashState             = flashState,
            partnerName            = partnerName,
            partnerImageUrl        = partnerImageUrl,
            contacts               = contacts,
            additionalParticipants = additionalParticipants,
            activePartnerId        = activePartnerId,
            isBackgroundBlurEnabled = isBackgroundBlurEnabled,
            onToggleBackgroundBlur  = { viewModel.toggleBackgroundBlur() },
            onInviteContact        = { uid -> viewModel.inviteToCall(uid) },
            onCancelInvite         = { uid -> viewModel.cancelInvite(uid) },
            callDurationText       = callDurationText,
            onSendEmoji            = { emoji -> viewModel.sendCallEmoji(emoji.name) },
            remoteEmoji            = remoteEmoji,
            isRecording            = isCallRecording,
            partnerRecording       = partnerRecording,
            onToggleRecording      = { viewModel.toggleCallRecording() },
            onToggleMute           = { viewModel.toggleCallMute() },
            onSwitchCamera         = { viewModel.switchCallCamera() },
            onToggleScreenShare    = onToggleScreenShare,
            onToggleVideoQuality   = {
                isHdVideo = !isHdVideo
                viewModel.setCallVideoQuality(isHdVideo)
            },
            onToggleFlashlight = {
                val next = flashState.next()
                if (!isFrontCamera) {
                    // Rückkamera: LED-Taschenlampe (nur ON/OFF, kein Farbwechsel)
                    try {
                        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val backId = cm.cameraIdList.firstOrNull { id ->
                            cm.getCameraCharacteristics(id)
                                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                        }
                        backId?.let { cm.setTorchMode(it, next.isOn) }
                        flashState = next
                    } catch (_: Exception) {}
                } else {
                    // Frontkamera: Display-Softlicht mit Farbwechsel
                    flashState = next
                }
            },
            onTurnOffFlashlight = {
                if (!isFrontCamera && flashState.isOn) {
                    try {
                        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val backId = cm.cameraIdList.firstOrNull { id ->
                            cm.getCameraCharacteristics(id)
                                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                        }
                        backId?.let { cm.setTorchMode(it, false) }
                    } catch (_: Exception) {}
                }
                flashState = FlashState.OFF
            },
            onHangUp          = { viewModel.endCall() }
        )

        // Status-Overlay: Besetzt-Meldung oder Verbindungsabbruch
        if (callStatusMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A2E),
                    tonalElevation = 8.dp
                ) {
                    Text(
                        text = callStatusMessage!!,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)
                    )
                }
            }
        }

        // Einwilligungs-Dialog: Partner möchte den Anruf aufzeichnen
        if (recordingConsentFrom != null) {
            val requesterName = contacts.find { it.userId == recordingConsentFrom }
                ?.let { it.username ?: it.fakeNumber } ?: recordingConsentFrom!!
            var allowDownload by remember(recordingConsentFrom) { mutableStateOf(false) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.respondCallRecordingConsent(false, false) },
                title = { Text("Aufzeichnung des Anrufs") },
                text = {
                    Column {
                        Text("$requesterName möchte diesen Anruf aufzeichnen. Stimmst du zu?")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = allowDownload,
                                onCheckedChange = { allowDownload = it }
                            )
                            Text(
                                "$requesterName darf meine Aufnahme herunterladen",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.respondCallRecordingConsent(true, allowDownload) }
                    ) { Text("Zustimmen") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.respondCallRecordingConsent(false, false) }
                    ) { Text("Ablehnen") }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 6. CHAT TOPBAR ACTIONS  –  Combined call dropdown button
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Zeigt ein kombiniertes Anruf-Dropdown in der ChatScreen TopBar.
 * Ist ein Call mit diesem Partner aktiv, pulst der Button rot und navigiert
 * bei Klick direkt in den laufenden Videocall.
 */
@Composable
fun CallDropdownTopBarAction(
    partnerId: String,
    viewModel: MainViewModel,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onNavigateToActiveCall: (() -> Unit)? = null
) {
    val localCapable         by viewModel.isLocalVideoCapable.collectAsState()
    val partnerCapable       by viewModel.partnerVideoCapable.collectAsState()
    val videoCapable         = localCapable && partnerCapable[partnerId] != false
    val activeCallPartnerId  by viewModel.activeCallPartnerId.collectAsState()
    val callState            by viewModel.callState.collectAsState()

    val isActiveCallWithPartner = activeCallPartnerId == partnerId &&
            callState != MainViewModel.CallState.IDLE &&
            callState != MainViewModel.CallState.ENDED

    var expanded by remember { mutableStateOf(false) }

    if (isActiveCallWithPartner) {
        // Pulsierender roter Button – navigiert zurück in den laufenden Call
        val pulseTransition = rememberInfiniteTransition(label = "callPulse")
        val pulseAlpha by pulseTransition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700), repeatMode = RepeatMode.Reverse
            ),
            label = "callPulseAlpha"
        )
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = pulseAlpha))
                .pointerInput(Unit) { detectTapGestures { onNavigateToActiveCall?.invoke() } },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Aktiver Anruf – tippen zum Zurückkehren",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    } else {
        Box {
            IconButton(onClick = { expanded = true }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Videocam,
                        contentDescription = stringResource(R.string.video_call_options_cd),
                        tint = if (videoCapable)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                    Icon(
                        imageVector        = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            DropdownMenu(
                expanded        = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text         = { Text(stringResource(R.string.video_call_voice)) },
                    leadingIcon  = {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            tint = if (localCapable)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    },
                    onClick      = {
                        expanded = false
                        onStartVoiceCall()
                    },
                    enabled      = localCapable
                )
                DropdownMenuItem(
                    text         = { Text(stringResource(R.string.video_call_video)) },
                    leadingIcon  = {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (videoCapable)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    },
                    onClick      = {
                        expanded = false
                        onStartVideoCall()
                    },
                    enabled      = videoCapable
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 7. GROUP CALL PICKER – Teilnehmerauswahl vor dem Anrufstart
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Bottom Sheet zur Auswahl von Anruf-Teilnehmern aus den Gruppenmitgliedern.
 * Zeigt Profilbild + Benutzernamen mit Checkboxen; Bestätigung per "Anruf starten".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCallPickerDialog(
    members: List<GroupMemberInfo>,
    myUserId: String,
    onStartCall: (participantIds: List<String>, callType: String) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var callType by remember { mutableStateOf("VIDEO") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Teilnehmer auswählen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val others = members.filter { it.userId != myUserId }
            if (others.isEmpty()) {
                Text(
                    "Keine weiteren Mitglieder in dieser Gruppe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(others) { member ->
                        val isChecked = selected[member.userId] == true
                        val displayName = member.name?.takeIf { it.isNotBlank() }
                            ?: member.fakeNumber ?: member.userId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected[member.userId] = !isChecked
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Profilbild
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val imgUrl = member.profileImageUrl?.let {
                                    if (it.startsWith("http")) it else "https://letheapp.de$it"
                                }
                                if (imgUrl != null) {
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { selected[member.userId] = it }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Anruf-Typ Auswahl
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = callType == "VOICE",
                    onClick = { callType = "VOICE" },
                    label = { Text("Sprachanruf") },
                    leadingIcon = {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = callType == "VIDEO",
                    onClick = { callType = "VIDEO" },
                    label = { Text("Videoanruf") },
                    leadingIcon = {
                        Icon(Icons.Default.Videocam, null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            val selectedIds = selected.filterValues { it }.keys.toList()
            Button(
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        onStartCall(selectedIds, callType)
                        onDismiss()
                    }
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectedIds.isEmpty()) "Anruf starten"
                    else "Anruf starten (${selectedIds.size})"
                )
            }
        }
    }
}

/**
 * Anruf-Dropdown-Button für den Gruppen-Chat (TopBar).
 * Öffnet den [GroupCallPickerDialog] zur Teilnehmerauswahl.
 */
@Composable
fun GroupCallDropdownTopBarAction(
    groupId: String,
    groupName: String? = null,
    viewModel: MainViewModel,
    members: List<GroupMemberInfo>,
    onNavigateToActiveCall: (() -> Unit)? = null
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val myUserId = currentUser?.userId
    val activeCallPartnerId by viewModel.activeCallPartnerId.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val localCapable by viewModel.isLocalVideoCapable.collectAsState()

    val isCallActive = callState != MainViewModel.CallState.IDLE &&
            callState != MainViewModel.CallState.ENDED

    var showPicker by remember { mutableStateOf(false) }

    if (isCallActive && activeCallPartnerId != null) {
        // Pulsierender roter Button → zurück in laufenden Call
        val pulseTransition = rememberInfiniteTransition(label = "groupCallPulse")
        val pulseAlpha by pulseTransition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "groupCallPulseAlpha"
        )
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Red.copy(alpha = pulseAlpha))
                .clickable { onNavigateToActiveCall?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    } else {
        IconButton(
            onClick = {
                if (localCapable) showPicker = true
            },
            enabled = localCapable
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = "Gruppenanruf starten",
                    tint = if (localCapable)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showPicker) {
        GroupCallPickerDialog(
            members = members,
            myUserId = myUserId ?: "",
            onStartCall = { ids, type ->
                // userId → (Anzeigename, Bild-URL) auflösen, damit alle Angerufenen die
                // vollständige Teilnehmerliste sehen.
                val targets = ids.map { id ->
                    val m = members.firstOrNull { it.userId == id }
                    val name = m?.name?.takeIf { it.isNotBlank() } ?: m?.fakeNumber ?: id
                    val img = m?.profileImageUrl?.let {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }
                    Triple(id, name, img)
                }
                viewModel.startGroupCallWithParticipants(targets, type, groupId, groupName)
                onNavigateToActiveCall?.invoke()
            },
            onDismiss = { showPicker = false }
        )
    }
}
