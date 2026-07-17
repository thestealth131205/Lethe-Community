package com.securechat.app.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay

// ══════════════════════════════════════════════════════════════════════════════
// 1. ACTIVE VOICE CALL SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun ActiveVoiceCallScreen(
    partnerName: String,
    partnerImageUrl: String?,
    isMuted: Boolean,
    isSpeakerphoneOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onHangUp: () -> Unit,
    onSendEmoji: (CallEmoji) -> Unit = {},
    remoteEmoji: CallEmoji? = null
) {
    val context = LocalContext.current

    var showEmojiPicker by remember { mutableStateOf(false) }
    var activeEmoji by remember { mutableStateOf<CallEmoji?>(null) }

    // ── Bildschirm während Anruf eingeschaltet lassen ────────────────────────
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ── Proximity Sensor: Bildschirm aus wenn Handy am Ohr ───────────────────
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val powerManager  = context.getSystemService(Context.POWER_SERVICE)  as PowerManager
        val proxSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "lethe:voicecall:proximity"
        )
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val isNear = event.values[0] < (proxSensor?.maximumRange ?: 5f)
                if (isNear) { if (!wakeLock.isHeld) wakeLock.acquire(10 * 60 * 1000L) }
                else         { if (wakeLock.isHeld)  wakeLock.release() }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (proxSensor != null) {
            sensorManager.registerListener(listener, proxSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            if (proxSensor != null) sensorManager.unregisterListener(listener)
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    // ── Anruf-Timer ──────────────────────────────────────────────────────────
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000); seconds++ }
    }
    val timerText = remember(seconds) {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else       String.format("%02d:%02d", m, s)
    }

    // ── Pulsierender Glow-Ring um Avatar ─────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.00f,
        targetValue  = 1.10f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1_200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue  = 0.65f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1_200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1B2A), Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            )
    ) {
        // Blurred partner image as subtle background
        if (partnerImageUrl != null) {
            AsyncImage(
                model = partnerImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
            )
            // Dark overlay so text is readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top spacer ───────────────────────────────────────────────────
            Spacer(Modifier.height(64.dp))

            // ── Avatar + Name + Timer ─────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Äußerer pulsierender Glow-Ring
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFFA8A800).copy(alpha = pulseAlpha))
                    )

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A3550)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (partnerImageUrl != null) {
                            AsyncImage(
                                model = partnerImageUrl,
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
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = partnerName,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = timerText,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // ── Glassmorphism Control Pill ────────────────────────────────────
            Box(modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(48.dp))) {
                // Frosted glass background
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(12.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute
                    VoiceControlButton(
                        onClick = onToggleMute,
                        active = isMuted,
                        activeColor = Color.White,
                        size = 60
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Stummschalten aufheben" else "Stummschalten",
                            tint = if (isMuted) Color.Black else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Hang up (prominenter roter Button)
                    IconButton(
                        onClick = onHangUp,
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30))
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Auflegen",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Speakerphone
                    VoiceControlButton(
                        onClick = onToggleSpeakerphone,
                        active = isSpeakerphoneOn,
                        activeColor = Color(0xFF34C759),
                        size = 60
                    ) {
                        Icon(
                            imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (isSpeakerphoneOn) "Freisprecher aus" else "Freisprecher ein",
                            tint = if (isSpeakerphoneOn) Color.Black else Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Emoji-Reaktionen
                    VoiceControlButton(
                        onClick = { showEmojiPicker = !showEmojiPicker },
                        active = showEmojiPicker,
                        activeColor = Color(0xFFFF9500),
                        size = 60
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "Emoji-Reaktion",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // ── Emoji-Picker Panel ────────────────────────────────────────────────
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

        // ── Eigene Emoji-Animation (4 s) ──────────────────────────────────────
        activeEmoji?.let { emoji ->
            EmojiReactionAnimation(
                emoji = emoji,
                onDismiss = { activeEmoji = null }
            )
        }

        // ── Remote Emoji-Animation vom Anruf-Partner ──────────────────────────
        remoteEmoji?.let { emoji ->
            EmojiReactionAnimation(
                emoji = emoji,
                onDismiss = {}
            )
        }
    }
}

@Composable
private fun VoiceControlButton(
    onClick: () -> Unit,
    active: Boolean,
    activeColor: Color,
    size: Int,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (active) activeColor else Color.Black.copy(alpha = 0.45f))
    ) { content() }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. VOICE CALL SCREEN  –  Navigation entry composable ("voice_call/{partnerId}")
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun VoiceCallScreen(
    partnerId: String,
    viewModel: MainViewModel,
    onCallEnded: () -> Unit
) {
    val callState             by viewModel.callState.collectAsState()
    val isMuted               by viewModel.callIsMuted.collectAsState()
    val isSpeakerphone        by viewModel.isSpeakerphoneOn.collectAsState()
    val callStatusMsg         by viewModel.callStatusMessage.collectAsState()
    val incomingCallEmojiName by viewModel.incomingCallEmoji.collectAsState()
    val remoteEmoji = incomingCallEmojiName?.let { name ->
        CallEmoji.entries.firstOrNull { it.name == name }
    }

    val contacts        by viewModel.contacts.collectAsState(initial = emptyList())
    val partner         = contacts.find { it.userId == partnerId }
    val partnerName     = partner?.username ?: partner?.fakeNumber ?: partnerId
    val partnerImageUrl = partner?.profileImageUrl?.let {
        if (it.startsWith("http")) it else "https://letheapp.de$it"
    }

    // Zurücknavigieren wenn Anruf endet
    LaunchedEffect(callState) {
        if (callState == MainViewModel.CallState.ENDED) {
            if (callStatusMsg != null) delay(2_500L)
            onCallEnded()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ActiveVoiceCallScreen(
            partnerName          = partnerName,
            partnerImageUrl      = partnerImageUrl,
            isMuted              = isMuted,
            isSpeakerphoneOn     = isSpeakerphone,
            onToggleMute         = { viewModel.toggleCallMute() },
            onToggleSpeakerphone = { viewModel.toggleSpeakerphone() },
            onHangUp             = { viewModel.endCall() },
            onSendEmoji          = { emoji -> viewModel.sendCallEmoji(emoji.name) },
            remoteEmoji          = remoteEmoji
        )

        // Status-Overlay (Besetzt, Verbindungsabbruch)
        if (callStatusMsg != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = Color(0xFF1A1A2E),
                    tonalElevation = 8.dp
                ) {
                    Text(
                        text = callStatusMsg!!,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)
                    )
                }
            }
        }
    }
}
