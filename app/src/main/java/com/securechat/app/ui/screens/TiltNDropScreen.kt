package com.securechat.app.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlinx.coroutines.isActive
import kotlin.math.*
import kotlin.random.Random

// ─── Physik-Konstanten ───────────────────────────────────────────────────────
private const val TND_RESTITUTION       = 0.62f
private const val TND_FRICTION          = 0.996f
private const val TND_MAX_ANGLE_DEG     = 60f
private const val TND_DEADZONE_DEG      = 2.0f
private const val TND_CALIB_DURATION_MS = 2000L
private const val TND_GAME_DURATION_S   = 180

// ─── Sensor-Halter (thread-sicher via @Volatile, KEIN Compose-State) ─────────
private class TndSensorHolder {
    @Volatile var gx: Float = 0f
    @Volatile var gy: Float = -9.8f
    @Volatile var gz: Float = 0f
}

// ─── Multiplayer-Phasen ──────────────────────────────────────────────────────
private enum class TndMultiPhase {
    WAITING, COUNTDOWN, PLAYING, PARTNER_DECLINED, GAME_OVER
}

// ─── Spielende-Grund ─────────────────────────────────────────────────────────
private enum class TndEndReason { TIMER, PARTNER_LEFT, SOLO }

// ─── Kugel-Physik-Zustand ────────────────────────────────────────────────────
private enum class TndBallState { PLAYING, SCORED, MISSED }

private data class TndPlatform(
    val cx: Float,
    val cy: Float,
    val width: Float,
    val angleDeg: Float = 0f,
    val isP2: Boolean = false
)

private data class TndPhysics(
    val bx: Float               = 0f,
    val by: Float               = 0f,
    val bvx: Float              = 0f,
    val bvy: Float              = 0f,
    val glassCx: Float          = 0f,
    val glassCy: Float          = 0f,
    val glassDirRight: Boolean  = true,
    val ballState: TndBallState = TndBallState.PLAYING,
    val initialized: Boolean    = false,
    val score: Int              = 0,
    val justBounced: Boolean    = false,
    val timeInGlassMs: Long     = 0L
)

// ─── Sprite-Datenstrukturen ───────────────────────────────────────────────────
private data class TndPlatformTiles(
    val left:   ImageBitmap,
    val center: ImageBitmap,
    val longer: ImageBitmap,
    val right:  ImageBitmap
)

private data class TndGlassTiles(
    val leftUp:       ImageBitmap,
    val leftBottom:   ImageBitmap,
    val rightUp:      ImageBitmap,
    val rightBottom:  ImageBitmap,
    val longerBottom: ImageBitmap
)

// ─── Platform-Layouts ────────────────────────────────────────────────────────
private fun tndGetPlatforms(
    w: Float, h: Float,
    p1Angle: Float, p2Angle: Float,
    layout: Int
): List<TndPlatform> = if (layout == 1) listOf(
    TndPlatform(w * 0.15f, h * 0.35f, w * 0.18f, p2Angle, isP2 = true),
    TndPlatform(w * 0.50f, h * 0.35f, w * 0.18f, p2Angle, isP2 = true),
    TndPlatform(w * 0.85f, h * 0.35f, w * 0.18f, p2Angle, isP2 = true),
    TndPlatform(w * 0.50f, h * 0.53f, w * 0.50f, p1Angle, isP2 = false),
    TndPlatform(w * 0.22f, h * 0.75f, w * 0.27f, p2Angle, isP2 = true),
    TndPlatform(w * 0.78f, h * 0.75f, w * 0.27f, p1Angle, isP2 = false)
) else listOf(
    TndPlatform(w * 0.22f, h * 0.42f, w * 0.27f, p2Angle, isP2 = true),
    TndPlatform(w * 0.78f, h * 0.42f, w * 0.27f, p1Angle, isP2 = false),
    TndPlatform(w * 0.50f, h * 0.60f, w * 0.27f, p1Angle, isP2 = false),
    TndPlatform(w * 0.22f, h * 0.78f, w * 0.27f, p1Angle, isP2 = false),
    TndPlatform(w * 0.78f, h * 0.78f, w * 0.27f, p2Angle, isP2 = true)
)

// ─── Physik-Schritt ──────────────────────────────────────────────────────────
private fun tndStep(
    s: TndPhysics,
    w: Float,
    h: Float,
    platforms: List<TndPlatform>,
    glassSpeed: Float,
    frameDeltaMs: Long = 16L
): TndPhysics {
    if (!s.initialized || s.ballState != TndBallState.PLAYING) return s

    val radius  = w * 0.028f
    val gravity = h * 0.00026f

    var vx = s.bvx * TND_FRICTION
    var vy = s.bvy + gravity
    var bx = s.bx + vx
    var by = s.by + vy

    var collisionHappened = false
    if (bx - radius < 0f)  { bx = radius;     vx =  abs(vx) * TND_RESTITUTION; collisionHappened = true }
    if (bx + radius > w)   { bx = w - radius; vx = -abs(vx) * TND_RESTITUTION; collisionHappened = true }

    for (plat in platforms) {
        val res = tndPlatCollision(bx, by, vx, vy, radius, plat) ?: continue
        bx = res[0]; by = res[1]; vx = res[2]; vy = res[3]
        collisionHappened = true
    }

    val glassHalfW = w * 0.075f
    val wallT      = maxOf(7f, w * 0.009f)
    val glassH     = h * 0.075f
    val glassLeft  = s.glassCx - glassHalfW
    val glassRight = s.glassCx + glassHalfW
    val glassTop   = s.glassCy - glassH / 2f
    val glassBottom= s.glassCy + glassH / 2f

    if (by + radius > glassTop && by - radius < glassBottom + radius) {
        if (bx - radius < glassLeft + wallT && bx > glassLeft - radius && bx < s.glassCx) {
            bx = glassLeft + wallT + radius; vx = abs(vx) * TND_RESTITUTION; collisionHappened = true
        }
        if (bx + radius > glassRight - wallT && bx < glassRight + radius && bx > s.glassCx) {
            bx = glassRight - wallT - radius; vx = -abs(vx) * TND_RESTITUTION; collisionHappened = true
        }
        if (by + radius > glassBottom - wallT && by - radius < glassBottom + wallT * 2f
            && bx > glassLeft + wallT && bx < glassRight - wallT) {
            by = glassBottom - wallT - radius; vy = -abs(vy) * TND_RESTITUTION * 0.5f; collisionHappened = true
        }
    }

    val innerLeft   = glassLeft  + wallT
    val innerRight  = glassRight - wallT
    val innerBottom = glassBottom - wallT
    val ballInGlass = bx > innerLeft && bx < innerRight && by > glassTop && by < innerBottom
    val newTimeInGlass = if (ballInGlass) s.timeInGlassMs + frameDeltaMs else 0L

    var newBallState = s.ballState
    when {
        newTimeInGlass >= 500L -> newBallState = TndBallState.SCORED
        by > h + radius * 2f  -> newBallState = TndBallState.MISSED
    }

    val margin      = glassHalfW + w * 0.05f
    var newGlassCx  = if (s.glassDirRight) s.glassCx + glassSpeed else s.glassCx - glassSpeed
    var newGlassDir = s.glassDirRight
    if (newGlassCx > w - margin) { newGlassCx = w - margin; newGlassDir = false }
    if (newGlassCx < margin)     { newGlassCx = margin;     newGlassDir = true  }

    return s.copy(
        bx = bx, by = by, bvx = vx, bvy = vy,
        glassCx = newGlassCx, glassDirRight = newGlassDir,
        ballState = newBallState,
        justBounced = collisionHappened,
        timeInGlassMs = newTimeInGlass
    )
}

// ─── Plattform-Kollisionserkennung ───────────────────────────────────────────
private fun tndPlatCollision(
    bx: Float, by: Float, vx: Float, vy: Float,
    radius: Float, plat: TndPlatform
): FloatArray? {
    val angleRad = (plat.angleDeg * PI / 180.0).toFloat()
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val hw   = plat.width / 2f

    val p1x = plat.cx - hw * cosA;  val p1y = plat.cy - hw * sinA
    val p2x = plat.cx + hw * cosA;  val p2y = plat.cy + hw * sinA

    val abx = p2x - p1x;  val aby = p2y - p1y
    val lenSq = abx * abx + aby * aby
    if (lenSq < 1e-6f) return null

    var t = ((bx - p1x) * abx + (by - p1y) * aby) / lenSq
    t = t.coerceIn(0f, 1f)

    val cx   = p1x + t * abx
    val cy   = p1y + t * aby
    val dx   = bx - cx
    val dy   = by - cy
    val dist = sqrt(dx * dx + dy * dy)

    if (dist >= radius + 1f || dist < 1e-6f) return null

    val nx = dx / dist
    val ny = dy / dist
    val vDotN = vx * nx + vy * ny
    if (vDotN > 0f) return null

    val newBx   = cx + nx * (radius + 0.5f)
    val newBy   = cy + ny * (radius + 0.5f)
    val impulse = (1f + TND_RESTITUTION) * vDotN
    val newVx   = vx - impulse * nx
    val newVy   = vy - impulse * ny

    return floatArrayOf(newBx, newBy, newVx, newVy)
}

// ─── Plattform zeichnen ───────────────────────────────────────────────────────
private fun DrawScope.tndDrawTiledPlatform(
    plat: TndPlatform,
    platH: Float,
    tiles: TndPlatformTiles,
    useLonger: Boolean
) {
    val alpha     = 1.0f
    val pw        = plat.width
    val px        = plat.cx - pw / 2f
    val py        = plat.cy - platH / 2f
    val centerImg = if (useLonger) tiles.longer else tiles.center

    val leftW  = (platH * tiles.left.width.toFloat()  / tiles.left.height.toFloat()).coerceAtLeast(1f)
    val rightW = (platH * tiles.right.width.toFloat() / tiles.right.height.toFloat()).coerceAtLeast(1f)
    val centerW = (pw - leftW - rightW).coerceAtLeast(0f)

    withTransform({ rotate(plat.angleDeg, pivot = Offset(plat.cx, plat.cy)) }) {
        drawImage(
            image     = tiles.left,
            dstOffset = IntOffset(px.toInt(), py.toInt()),
            dstSize   = IntSize(leftW.toInt().coerceAtLeast(1), platH.toInt().coerceAtLeast(1)),
            alpha     = alpha
        )
        if (centerW > 0f) {
            drawImage(
                image     = centerImg,
                dstOffset = IntOffset((px + leftW).toInt(), py.toInt()),
                dstSize   = IntSize(centerW.toInt().coerceAtLeast(1), platH.toInt().coerceAtLeast(1)),
                alpha     = alpha
            )
        }
        drawImage(
            image     = tiles.right,
            dstOffset = IntOffset((px + leftW + centerW).toInt(), py.toInt()),
            dstSize   = IntSize(rightW.toInt().coerceAtLeast(1), platH.toInt().coerceAtLeast(1)),
            alpha     = alpha
        )
    }
}

// ─── Glas zeichnen ────────────────────────────────────────────────────────────
private fun DrawScope.tndDrawGlass(
    cx: Float, cy: Float, halfW: Float, glassH: Float, wallT: Float,
    tiles: TndGlassTiles,
    alpha: Float
) {
    val left   = cx - halfW
    val right  = cx + halfW
    val top    = cy - glassH / 2f
    val bottom = cy + glassH / 2f

    val leftBotH  = (wallT * tiles.leftBottom.height.toFloat()  / tiles.leftBottom.width.toFloat()).coerceAtLeast(1f)
    val rightBotH = (wallT * tiles.rightBottom.height.toFloat() / tiles.rightBottom.width.toFloat()).coerceAtLeast(1f)
    val leftTopH  = (bottom - top - leftBotH).coerceAtLeast(0f)
    val rightTopH = (bottom - top - rightBotH).coerceAtLeast(0f)
    val wT = wallT.toInt().coerceAtLeast(1)

    if (leftTopH > 0f) drawImage(tiles.leftUp,
        dstOffset = IntOffset(left.toInt(), top.toInt()),
        dstSize   = IntSize(wT, leftTopH.toInt().coerceAtLeast(1)), alpha = alpha)
    drawImage(tiles.leftBottom,
        dstOffset = IntOffset(left.toInt(), (top + leftTopH).toInt()),
        dstSize   = IntSize(wT, leftBotH.toInt().coerceAtLeast(1)), alpha = alpha)

    if (rightTopH > 0f) drawImage(tiles.rightUp,
        dstOffset = IntOffset((right - wallT).toInt(), top.toInt()),
        dstSize   = IntSize(wT, rightTopH.toInt().coerceAtLeast(1)), alpha = alpha)
    drawImage(tiles.rightBottom,
        dstOffset = IntOffset((right - wallT).toInt(), (top + rightTopH).toInt()),
        dstSize   = IntSize(wT, rightBotH.toInt().coerceAtLeast(1)), alpha = alpha)

    val floorX = (left + wallT).toInt()
    val floorW = ((right - left - 2f * wallT).toInt()).coerceAtLeast(1)
    val floorY = (bottom - wallT).toInt()
    drawImage(tiles.longerBottom,
        dstOffset = IntOffset(floorX, floorY),
        dstSize   = IntSize(floorW, wT), alpha = alpha)
}

// ─── Status-Punkt ─────────────────────────────────────────────────────────────
@Composable
private fun TndStatusDot(color: Color, size: Dp = 10.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
    )
}

// ─── Hauptscreen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiltNDropScreen(
    viewModel:      MainViewModel,
    partnerId:      String,
    partnerName:    String,
    isHost:         Boolean,
    onNavigateBack: () -> Unit
) {
    val context     = LocalContext.current
    val density     = LocalDensity.current
    val soloMode    = partnerId.isEmpty()
    val currentUser by viewModel.currentUser.collectAsState()
    val myName      = currentUser?.name ?: "Du"

    // ── Bildschirm während des Spiels wach halten ─────────────────────────────
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Plattform-Sprites ─────────────────────────────────────────────────────
    val platTuerkise: TndPlatformTiles = remember(context) {
        TndPlatformTiles(
            left   = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_tuerkise_left).asImageBitmap(),
            center = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_tuerkise_center).asImageBitmap(),
            longer = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_tuerkise_longer).asImageBitmap(),
            right  = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_tuerkise_right).asImageBitmap()
        )
    }
    val platPink: TndPlatformTiles = remember(context) {
        TndPlatformTiles(
            left   = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_pink_left).asImageBitmap(),
            center = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_pink_center).asImageBitmap(),
            longer = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_pink_longerr).asImageBitmap(),
            right  = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_platform_pink_right).asImageBitmap()
        )
    }

    // ── Glas-Sprites ──────────────────────────────────────────────────────────
    val glasTuerkise: TndGlassTiles = remember(context) {
        TndGlassTiles(
            leftUp       = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_glas_tuerkise_left_up).asImageBitmap(),
            leftBottom   = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_glas_tuerkise_left_bottom).asImageBitmap(),
            rightUp      = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_glas_tuerkise_right_up).asImageBitmap(),
            rightBottom  = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_glas_tuerkise_right_bottom).asImageBitmap(),
            longerBottom = BitmapFactory.decodeResource(context.resources, R.drawable.tiles_glas_tuerkise_longer_bottom).asImageBitmap()
        )
    }

    // ── SoundPool ─────────────────────────────────────────────────────────────
    var soundsLoadedCount by remember { mutableIntStateOf(0) }
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) soundsLoadedCount++
                }
            }
    }
    val soundBounceId = remember { soundPool.load(context, R.raw.ball_bounce,  1) }
    val soundScoredId = remember { soundPool.load(context, R.raw.ball_in_glas, 1) }
    DisposableEffect(soundPool) { onDispose { soundPool.release() } }

    // ── Multiplayer-Phase ─────────────────────────────────────────────────────
    var multiPhase by remember {
        mutableStateOf(
            when {
                soloMode -> TndMultiPhase.PLAYING
                else     -> TndMultiPhase.WAITING
            }
        )
    }
    var countdownSeconds  by remember { mutableIntStateOf(5) }
    var declineMessage    by remember { mutableStateOf("") }

    // ── Bereit-System ─────────────────────────────────────────────────────────
    // Beide Spieler müssen "Bereit" drücken, bevor das Spiel startet.
    // localReady: dieser Spieler hat Bereit gedrückt
    // partnerReady: Partner hat Bereit-Nachricht gesendet
    // partnerConnected: Partner hat tnd_player_practicing gesendet (Bildschirm geöffnet)
    var localReady        by remember { mutableStateOf(false) }
    var partnerReady      by remember { mutableStateOf(false) }
    var partnerConnected  by remember { mutableStateOf(false) }

    // ── Spieltimer und Ergebnis ───────────────────────────────────────────────
    var gameTimeRemaining by remember { mutableIntStateOf(TND_GAME_DURATION_S) }
    var partnerFinalScore by remember { mutableIntStateOf(-1) }
    var endReason         by remember { mutableStateOf(TndEndReason.SOLO) }
    var gameCardSent      by remember { mutableStateOf(false) }

    // ── Sensor-Halter ─────────────────────────────────────────────────────────
    val sensorHolder = remember { TndSensorHolder() }

    // ── Kalibrierungs-Zustand ─────────────────────────────────────────────────
    var calibPrepCountdown by remember { mutableIntStateOf(5) }
    var calibInMeasure     by remember { mutableStateOf(false) }
    var calibDone          by remember { mutableStateOf(false) }
    var calibProgress      by remember { mutableFloatStateOf(0f) }
    val zeroOffsetHolder   = remember { floatArrayOf(0f) }

    // ── Abgeleitete UI-Werte ──────────────────────────────────────────────────
    var tiltAngle        by remember { mutableFloatStateOf(0f) }
    var partnerTiltAngle by remember { mutableFloatStateOf(0f) }

    val p1Angle = if (soloMode || isHost)  tiltAngle else partnerTiltAngle
    val p2Angle = if (soloMode || !isHost) tiltAngle else partnerTiltAngle

    // ── Level-Layout ──────────────────────────────────────────────────────────
    var currentLayout by remember { mutableIntStateOf(0) }

    // ── Canvas-Größe ──────────────────────────────────────────────────────────
    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }

    // ── Physik-Zustand ────────────────────────────────────────────────────────
    var physics by remember { mutableStateOf(TndPhysics()) }
    var round   by remember { mutableIntStateOf(0) }

    // ── Mindestabstand für Bounce-Sound ───────────────────────────────────────
    val minSoundDistPx = remember(density) { with(density) { 20.dp.toPx() } }

    // ── Sensor-Registrierung ──────────────────────────────────────────────────
    DisposableEffect(context) {
        val sm     = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                sensorHolder.gx = ev.values[0]
                sensorHolder.gy = ev.values[1]
                sensorHolder.gz = ev.values[2]
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(listener) }
    }

    // ── Partner über eigenen Spielbeitritt informieren ────────────────────────
    LaunchedEffect(Unit) {
        if (!soloMode) {
            viewModel.sendGameWsMessage("tnd_player_practicing", partnerId, emptyMap())
        }
    }

    // ── Einmalige Kalibrierung beim Screen-Start ──────────────────────────────
    LaunchedEffect(Unit) {
        // PHASE 1 (PREP – 5 Sek.): Countdown zum Ausrichten – noch KEINE Messung
        for (i in 5 downTo 1) {
            if (!isActive) return@LaunchedEffect
            calibPrepCountdown = i
            delay(1000L)
        }
        calibPrepCountdown = 0
        calibInMeasure     = true

        // PHASE 2 (MEASURE – 2 Sek.): Jetzt Nullwert ermitteln
        val calibSamples = mutableListOf<Float>()
        val calibStart   = System.currentTimeMillis()
        while (isActive) {
            val elapsed = System.currentTimeMillis() - calibStart
            if (elapsed >= TND_CALIB_DURATION_MS) break

            val gx = sensorHolder.gx
            val gy = sensorHolder.gy
            val rawDeg = Math.toDegrees(atan2(gx.toDouble(), -gy.toDouble())).toFloat()
            calibSamples.add(rawDeg)

            calibProgress = (elapsed.toFloat() / TND_CALIB_DURATION_MS).coerceIn(0f, 1f)
            delay(33L)
        }

        zeroOffsetHolder[0] = if (calibSamples.isNotEmpty()) calibSamples.average().toFloat() else 0f
        calibProgress  = 1f
        calibInMeasure = false
        calibDone      = true
    }

    // ── Angle-Update & Übertragung (läuft immer nach Kalibrierung, unabhängig von Physik) ─────
    // Stellt sicher dass: (a) tiltAngle immer aktuell ist (auch in COUNTDOWN/WAITING+bereit),
    //                     (b) der Winkel auch dann an den Partner gesendet wird wenn die
    //                         Physik-Schleife pausiert ist.
    LaunchedEffect(calibDone) {
        if (!calibDone) return@LaunchedEffect
        var angleFrameCounter = 0
        while (isActive) {
            delay(16L)
            val gx = sensorHolder.gx
            val gy = sensorHolder.gy
            val rawDeg = Math.toDegrees(atan2(gx.toDouble(), -gy.toDouble())).toFloat()
            var d = rawDeg - zeroOffsetHolder[0]
            d = (d + 540f) % 360f - 180f
            if (abs(d) < TND_DEADZONE_DEG) d = 0f else d *= 2.8f
            tiltAngle = d.coerceIn(-TND_MAX_ANGLE_DEG, TND_MAX_ANGLE_DEG)
            if (!soloMode) {
                angleFrameCounter++
                if (angleFrameCounter >= 6) {
                    angleFrameCounter = 0
                    viewModel.sendGameWsMessage("tnd_angle", partnerId, mapOf("angle" to tiltAngle))
                }
            }
        }
    }

    // ── Host startet Spiel wenn beide bereit sind ─────────────────────────────
    // Nur der Host sendet tnd_start_game mit dem gewählten Spielfeld-Layout.
    // Der Gast wartet auf tnd_start_game und setzt dann sein Layout.
    LaunchedEffect(localReady, partnerReady) {
        if (!soloMode && isHost && localReady && partnerReady && multiPhase == TndMultiPhase.WAITING) {
            val chosenLayout = Random.nextInt(2)
            currentLayout = chosenLayout
            viewModel.sendGameWsMessage(
                "tnd_start_game", partnerId,
                mapOf("layout" to chosenLayout)
            )
            multiPhase = TndMultiPhase.COUNTDOWN
        }
    }

    // ── WebSocket-Events ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.incomingGameEvent.collect { msg ->
            if (!soloMode && msg.senderId != partnerId) return@collect
            when (msg.type) {
                // Partner hat den Spielbildschirm geöffnet (übt)
                "tnd_player_practicing" -> {
                    partnerConnected = true
                }
                // Partner hat "Bereit" gedrückt
                "tnd_player_ready" -> {
                    partnerConnected = true
                    partnerReady = true
                }
                // Host hat Spielstart ausgelöst → Gast übernimmt Layout und startet Countdown
                "tnd_start_game" -> {
                    if (!isHost && multiPhase == TndMultiPhase.WAITING) {
                        val layout = ((msg.payload as? Map<*, *>)?.get("layout") as? Double)?.toInt() ?: 0
                        currentLayout = layout
                        multiPhase = TndMultiPhase.COUNTDOWN
                    }
                }
                // Partner hat seinen Endscore gesendet
                "tnd_final_score" -> {
                    val score = ((msg.payload as? Map<*, *>)?.get("score") as? Double)?.toInt() ?: 0
                    partnerFinalScore = score
                }
                // Partner-Plattformwinkel empfangen
                "tnd_angle" -> {
                    val angle = ((msg.payload as? Map<*, *>)?.get("angle") as? Double)?.toFloat() ?: 0f
                    partnerTiltAngle = angle
                }
                // Autoritative Kugelposition vom Host empfangen (nur Gast)
                "tnd_ball" -> {
                    if (!isHost && multiPhase == TndMultiPhase.PLAYING) {
                        val p = msg.payload as? Map<*, *> ?: return@collect
                        val bx  = (p["bx"]  as? Double)?.toFloat() ?: return@collect
                        val by  = (p["by"]  as? Double)?.toFloat() ?: return@collect
                        val bvx = (p["bvx"] as? Double)?.toFloat() ?: 0f
                        val bvy = (p["bvy"] as? Double)?.toFloat() ?: 0f
                        val gcx = (p["gcx"] as? Double)?.toFloat() ?: physics.glassCx
                        val gdr = p["gdr"] as? Boolean ?: physics.glassDirRight
                        val bs  = (p["bs"]  as? Double)?.toInt() ?: 0
                        val ballState = TndBallState.values().getOrElse(bs) { TndBallState.PLAYING }
                        val score = (p["sc"] as? Double)?.toInt() ?: physics.score
                        val bounced = p["jb"] as? Boolean ?: false
                        physics = physics.copy(
                            bx = bx, by = by, bvx = bvx, bvy = bvy,
                            glassCx = gcx, glassDirRight = gdr,
                            ballState = ballState, score = score,
                            justBounced = bounced, initialized = true
                        )
                    }
                }
                "game_decline", "game_cancel" -> {
                    if (multiPhase == TndMultiPhase.WAITING || multiPhase == TndMultiPhase.COUNTDOWN) {
                        declineMessage = "$partnerName hat die Einladung abgelehnt."
                        multiPhase = TndMultiPhase.PARTNER_DECLINED
                    }
                }
                "game_end" -> {
                    if (multiPhase == TndMultiPhase.PLAYING) {
                        endReason = TndEndReason.PARTNER_LEFT
                        multiPhase = TndMultiPhase.GAME_OVER
                    }
                }
            }
        }
    }

    // ── Phasen-Automat ────────────────────────────────────────────────────────
    LaunchedEffect(multiPhase) {
        when (multiPhase) {
            TndMultiPhase.COUNTDOWN -> {
                // Kurzer 5-Sekunden-Countdown nachdem beide "Bereit" gedrückt haben
                countdownSeconds = 5
                repeat(5) {
                    delay(1000L)
                    if (isActive) countdownSeconds--
                }
                if (isActive) {
                    gameTimeRemaining = TND_GAME_DURATION_S
                    multiPhase = TndMultiPhase.PLAYING
                    round++
                }
            }
            TndMultiPhase.PLAYING -> {
                // 180-Sekunden-Spieltimer
                while (isActive && gameTimeRemaining > 0) {
                    delay(1000L)
                    if (isActive) gameTimeRemaining = (gameTimeRemaining - 1).coerceAtLeast(0)
                }
                if (isActive && multiPhase == TndMultiPhase.PLAYING) {
                    // Timer abgelaufen → Spiel beenden
                    endReason = if (soloMode) TndEndReason.SOLO else TndEndReason.TIMER
                    val finalScore = physics.score
                    if (!soloMode) {
                        viewModel.sendGameWsMessage(
                            "tnd_final_score", partnerId,
                            mapOf("score" to finalScore)
                        )
                        viewModel.sendGameWsMessage("game_end", partnerId, emptyMap())
                    }
                    multiPhase = TndMultiPhase.GAME_OVER
                }
            }
            TndMultiPhase.GAME_OVER -> {
                // Einmalig Münzen gutschreiben (Score × 20)
                if (!soloMode) {
                    viewModel.saveGameSession(
                        level           = 1,
                        coins           = physics.score * 20,
                        durationSeconds = TND_GAME_DURATION_S - gameTimeRemaining,
                        result          = "win",
                        gameType        = "tnd",
                        opponentId      = partnerId
                    )
                }
            }
            TndMultiPhase.PARTNER_DECLINED -> {
                delay(3000L)
                if (isActive) onNavigateBack()
            }
            else -> Unit
        }
    }

    // ── Physik-Schleife ───────────────────────────────────────────────────────
    // Läuft in WAITING (Üben), COUNTDOWN (Geist-Ball) und PLAYING.
    // Im Multiplayer bleibt das Layout zwischen Runden synchron durch
    // das geteilte currentLayout (Host hat es beim Spielstart gesetzt).
    // Physik läuft in WAITING (Üben) und PLAYING. In COUNTDOWN kein Ghost-Ball.
    val physicsActive = canvasW > 0f && canvasH > 0f &&
        multiPhase in setOf(TndMultiPhase.WAITING, TndMultiPhase.PLAYING) &&
        !(multiPhase == TndMultiPhase.WAITING && localReady && !soloMode)

    LaunchedEffect(physicsActive, round) {
        if (!physicsActive) return@LaunchedEffect

        while (isActive && !calibDone) {
            delay(50L)
        }

        // Layout: in WAITING immer wechseln (Üben), im Spiel vom Host vorgegeben
        if (multiPhase == TndMultiPhase.WAITING || soloMode) {
            currentLayout = round % 2
        }
        val layout   = currentLayout
        val glassSpd = if (layout == 1) canvasW * 0.007f else canvasW * 0.004f

        val prevScore = if (multiPhase == TndMultiPhase.PLAYING) physics.score else 0
        physics = TndPhysics(
            bx          = canvasW * (0.30f + Random.nextFloat() * 0.40f),
            by          = canvasH * 0.04f,
            bvx         = (Random.nextFloat() - 0.5f) * canvasW * 0.003f,
            glassCx     = canvasW * 0.50f,
            glassCy     = canvasH * 0.88f,
            initialized = true,
            score       = prevScore
        )

        val lastSoundPos  = floatArrayOf(-9999f, -9999f)
        var lastFrameTime = System.currentTimeMillis()
        var ballFrameCounter = 0

        while (isActive && physics.ballState == TndBallState.PLAYING) {
            delay(16L)

            if (soloMode || isHost) {
                // Host/Solo: lokale Physik berechnen
                val now        = System.currentTimeMillis()
                val frameDelta = (now - lastFrameTime).coerceIn(1L, 50L)
                lastFrameTime  = now

                // Winkelberechnung für die Physik (tiltAngle + Senden übernimmt die dedizierte Coroutine)
                val gx = sensorHolder.gx
                val gy = sensorHolder.gy
                val rawDeg = Math.toDegrees(atan2(gx.toDouble(), -gy.toDouble())).toFloat()
                var deltaAngle = rawDeg - zeroOffsetHolder[0]
                deltaAngle = (deltaAngle + 540f) % 360f - 180f
                if (abs(deltaAngle) < TND_DEADZONE_DEG) deltaAngle = 0f else deltaAngle *= 1.5f
                val currentTilt = deltaAngle.coerceIn(-TND_MAX_ANGLE_DEG, TND_MAX_ANGLE_DEG)

                val curP1     = if (soloMode || isHost)  currentTilt else partnerTiltAngle
                val curP2     = if (soloMode || !isHost) currentTilt else partnerTiltAngle
                val platforms = tndGetPlatforms(canvasW, canvasH, curP1, curP2, layout)
                physics       = tndStep(physics, canvasW, canvasH, platforms, glassSpd, frameDelta)

                // Host sendet autoritative Kugelposition an Gast (~50 ms)
                if (!soloMode && multiPhase == TndMultiPhase.PLAYING) {
                    ballFrameCounter++
                    if (ballFrameCounter >= 3) {
                        ballFrameCounter = 0
                        viewModel.sendGameWsMessage(
                            "tnd_ball", partnerId, mapOf(
                                "bx"  to physics.bx,
                                "by"  to physics.by,
                                "bvx" to physics.bvx,
                                "bvy" to physics.bvy,
                                "gcx" to physics.glassCx,
                                "gdr" to physics.glassDirRight,
                                "bs"  to physics.ballState.ordinal,
                                "sc"  to physics.score,
                                "jb"  to physics.justBounced
                            )
                        )
                    }
                }
            }
            // Gast: Physik kommt via WS (tnd_ball-Ereignisse), hier nur auf State-Änderungen warten

            if (physics.justBounced && soundBounceId > 0) {
                val dx = physics.bx - lastSoundPos[0]
                val dy = physics.by - lastSoundPos[1]
                if (sqrt(dx * dx + dy * dy) >= minSoundDistPx) {
                    soundPool.play(soundBounceId, 0.65f, 0.65f, 1, 0, 1.0f)
                    lastSoundPos[0] = physics.bx
                    lastSoundPos[1] = physics.by
                }
            }
        }

        // Ergebnis auswerten
        if (isActive && physics.ballState == TndBallState.SCORED) {
            if (soundScoredId > 0) {
                soundPool.play(soundScoredId, 1.0f, 1.0f, 2, 0, 1.0f)
            }
            if (multiPhase == TndMultiPhase.PLAYING && (soloMode || isHost)) {
                physics = physics.copy(score = physics.score + 1)
            }
        }

        if (isActive && multiPhase == TndMultiPhase.PLAYING) {
            delay(2000L)
            if (isActive && multiPhase == TndMultiPhase.PLAYING) round++
        } else if (isActive && multiPhase != TndMultiPhase.PLAYING) {
            delay(2000L + Random.nextLong(4001L))
            if (isActive) round++
        }
    }

    // ── Zurück-Handler ────────────────────────────────────────────────────────
    val handleBack: () -> Unit = {
        when {
            multiPhase == TndMultiPhase.WAITING && !soloMode -> {
                viewModel.sendGameWsMessage("game_cancel", partnerId, emptyMap())
                viewModel.insertGameCancelSystemMessage(partnerId, partnerName)
            }
            multiPhase == TndMultiPhase.PLAYING && !soloMode -> {
                viewModel.sendGameWsMessage("game_end", partnerId, emptyMap())
            }
            else -> Unit
        }
        // Spielergebnis-Karte in den Chat einfügen (nur Multiplayer, einmalig)
        if (!soloMode && !gameCardSent &&
            multiPhase in setOf(TndMultiPhase.PLAYING, TndMultiPhase.GAME_OVER)) {
            gameCardSent = true
            val myCoins      = physics.score * 20
            val partnerCoins = if (partnerFinalScore >= 0) partnerFinalScore * 20 else 0
            val content = JSONObject().apply {
                put("game",         "tnd")
                put("gameName",     "Neon Tilt 'n' Drop")
                put("won",          false)
                put("isDraw",       true)
                put("partnerName",  partnerName)
                put("partnerId",    partnerId)
                put("myName",       myName)
                put("rounds",       1)
                put("myScore",      "${physics.score} Treffer")
                put("myCoins",      myCoins)
                put("partnerScore", if (partnerFinalScore >= 0) "$partnerFinalScore Treffer" else "")
                put("partnerCoins", partnerCoins)
            }.toString()
            viewModel.insertGameResultMessage(partnerId, content)
        }
        onNavigateBack()
    }
    BackHandler { handleBack() }

    // ── Abgeleitete Rendering-Werte ───────────────────────────────────────────
    val isCalibrating = !calibDone

    // ── Spielzeit-Formatierung ────────────────────────────────────────────────
    val timeMinutes = gameTimeRemaining / 60
    val timeSeconds = gameTimeRemaining % 60
    val timeIsLow   = gameTimeRemaining <= 30 && multiPhase == TndMultiPhase.PLAYING

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Neon Tilt 'n' Drop",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (!soloMode) {
                            val subtitle = when (multiPhase) {
                                TndMultiPhase.WAITING -> {
                                    val myText      = if (localReady) "Du: Bereit" else "Du: Üben"
                                    val partnerText = if (partnerReady) "$partnerName: Bereit" else "$partnerName: Üben"
                                    "$myText | $partnerText"
                                }
                                TndMultiPhase.COUNTDOWN        -> "Startet in ${countdownSeconds}s"
                                TndMultiPhase.PLAYING          -> "vs. $partnerName"
                                TndMultiPhase.PARTNER_DECLINED -> "Abgelehnt"
                                TndMultiPhase.GAME_OVER        -> "Spiel beendet"
                            }
                            Text(
                                subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    when {
                        // WAITING: Spielernamen + Status-Punkte + "Bereit"-Button
                        !soloMode && multiPhase == TndMultiPhase.WAITING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                // Eigener Status (immer orange bis grün)
                                TndStatusDot(
                                    color = if (localReady) Color(0xFF22CC44) else Color(0xFFFF8800)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    myName,
                                    color    = Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.widthIn(max = 72.dp)
                                )
                                Spacer(Modifier.width(6.dp))

                                // Bereit-Button
                                Button(
                                    onClick = {
                                        if (!localReady) {
                                            localReady = true
                                            viewModel.sendGameWsMessage("tnd_player_ready", partnerId, emptyMap())
                                        }
                                    },
                                    enabled = !localReady,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor        = if (localReady) Color(0xFF228833) else Color(0xFF0077CC),
                                        disabledContainerColor = Color(0xFF228833)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        if (localReady) "Bereit!" else "Bereit",
                                        color      = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 13.sp
                                    )
                                }

                                Spacer(Modifier.width(6.dp))

                                // Partner-Name + Status-Punkt
                                Text(
                                    partnerName,
                                    color    = Color.White,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.widthIn(max = 72.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                val partnerDotColor = when {
                                    partnerReady     -> Color(0xFF22CC44)  // grün: bereit
                                    partnerConnected -> Color(0xFFFF8800)  // orange: übt
                                    else             -> Color(0xFFFF3333)  // rot: noch nicht da
                                }
                                TndStatusDot(color = partnerDotColor)
                            }
                        }
                        // PLAYING im Solo-Modus: Neu-starten-Button
                        soloMode && multiPhase == TndMultiPhase.PLAYING -> {
                            IconButton(onClick = { round++ }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Neu starten")
                            }
                        }
                        else -> Unit
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF050510))
                .onSizeChanged { sz ->
                    if (canvasW <= 0f) {
                        canvasW = sz.width.toFloat()
                        canvasH = sz.height.toFloat()
                    }
                }
        ) {
            val p = physics

            // ── Spielfeld-Canvas ──────────────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w      = size.width
                val h      = size.height
                val platH  = maxOf(20f, w * 0.032f)
                val radius = w * 0.028f
                val glassHalfW = w * 0.075f
                val glassH     = h * 0.075f
                val wallT      = maxOf(7f, w * 0.009f)

                val platforms = tndGetPlatforms(w, h, p1Angle, p2Angle, currentLayout)
                for (plat in platforms) {
                    val tiles = if (plat.isP2) platPink else platTuerkise
                    tndDrawTiledPlatform(plat, platH, tiles, useLonger = false)
                }

                if (p.initialized) {
                    tndDrawGlass(p.glassCx, p.glassCy, glassHalfW, glassH, wallT, glasTuerkise, 1.0f)

                    if (p.ballState == TndBallState.PLAYING || multiPhase == TndMultiPhase.PLAYING) {
                        drawCircle(color = Color(0xFFFFFFFF), radius = radius,        center = Offset(p.bx, p.by))
                        drawCircle(
                            color  = Color(0x66FFFFFF),
                            radius = radius * 0.45f,
                            center = Offset(p.bx - radius * 0.28f, p.by - radius * 0.28f)
                        )
                    }
                }
            }

            // ── Spiel-HUD: Restspielzeit (links) und Score (rechts) ───────────
            if (multiPhase == TndMultiPhase.PLAYING && p.initialized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restspielzeit links
                    Box(
                        modifier = Modifier
                            .background(
                                if (timeIsLow) Color(0xAA440000) else Color(0x88000000),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "%d:%02d".format(timeMinutes, timeSeconds),
                            color      = if (timeIsLow) Color(0xFFFF4444) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    }
                    // Score rechts
                    Box(
                        modifier = Modifier
                            .background(Color(0x88000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Score: ${p.score}",
                            color      = Color(0xFF80CFFF),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    }
                }
            }

            // ── Kalibrierungs- / Wartephasen-Overlay ─────────────────────────
            if (isCalibrating || multiPhase in setOf(TndMultiPhase.WAITING, TndMultiPhase.COUNTDOWN)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(
                            top   = if (multiPhase == TndMultiPhase.PLAYING) 56.dp else 12.dp,
                            start = 24.dp,
                            end   = 24.dp
                        )
                ) {
                    Surface(
                        shape          = RoundedCornerShape(14.dp),
                        color          = Color(0xCC001030),
                        tonalElevation = 4.dp,
                        modifier       = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier            = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            when {
                                isCalibrating -> {
                                    if (calibPrepCountdown > 0) {
                                        Text(
                                            "Handy in Position bringen…",
                                            color      = Color(0xFFFFAA00),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize   = 15.sp,
                                            textAlign  = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "$calibPrepCountdown",
                                            color      = Color(0xFFFFDD00),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize   = 52.sp,
                                            lineHeight = 56.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Halte das Handy aufrecht vor dich.\nOberer Teil leicht nach vorne geneigt.",
                                            color     = Color(0xFFFFCC88),
                                            fontSize  = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    } else {
                                        Text(
                                            "Bitte stillhalten…",
                                            color      = Color(0xFFFFFF00),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize   = 15.sp,
                                            textAlign  = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Kalibrierung läuft (2 Sek.)",
                                            color     = Color(0xFFFFFFAA),
                                            fontSize  = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        LinearProgressIndicator(
                                            progress   = { calibProgress },
                                            modifier   = Modifier.fillMaxWidth(),
                                            color      = Color(0xFFFFFF00),
                                            trackColor = Color(0x33FFFF00)
                                        )
                                    }
                                }
                                else -> {
                                    when (multiPhase) {
                                        TndMultiPhase.WAITING -> {
                                            if (!localReady) {
                                                Text(
                                                    "Übe solange du möchtest!",
                                                    color      = Color(0xFF80CFFF),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize   = 15.sp,
                                                    textAlign  = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "Drücke oben \"Bereit\" wenn du bereit bist.",
                                                    color     = Color(0xFFAADDFF),
                                                    fontSize  = 12.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            } else if (!partnerReady) {
                                                CircularProgressIndicator(
                                                    modifier    = Modifier.size(24.dp),
                                                    color       = Color(0xFF80CFFF),
                                                    strokeWidth = 3.dp
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    "Warte auf $partnerName…",
                                                    color      = Color.White,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize   = 15.sp
                                                )
                                            } else {
                                                Text(
                                                    "Beide bereit!",
                                                    color      = Color(0xFF66FF66),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize   = 16.sp
                                                )
                                            }
                                        }
                                        TndMultiPhase.COUNTDOWN -> {
                                            Text(
                                                "$countdownSeconds",
                                                color      = Color(0xFF80CFFF),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize   = 52.sp,
                                                lineHeight = 56.sp
                                            )
                                            Text(
                                                "Los geht's!",
                                                color    = Color.White,
                                                fontSize = 14.sp
                                            )
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Einladung abgelehnt-Overlay ───────────────────────────────────
            if (multiPhase == TndMultiPhase.PARTNER_DECLINED) {
                Box(
                    modifier           = Modifier.fillMaxSize().background(Color(0xAA000000)),
                    contentAlignment   = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌", fontSize = 52.sp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            declineMessage,
                            color      = Color(0xFFFF6666),
                            fontWeight = FontWeight.Bold,
                            fontSize   = 18.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Kehre zur Spielauswahl zurück…",
                            color    = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ── Spiel beendet-Overlay ─────────────────────────────────────────
            if (multiPhase == TndMultiPhase.GAME_OVER) {
                Box(
                    modifier           = Modifier.fillMaxSize().background(Color(0xCC000020)),
                    contentAlignment   = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier            = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text(
                            "Spiel beendet!",
                            color      = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 30.sp
                        )
                        Spacer(Modifier.height(16.dp))

                        if (endReason == TndEndReason.PARTNER_LEFT) {
                            // Partner hat das Spiel verlassen
                            Text(
                                "$partnerName hat das Spiel verlassen.",
                                color     = Color(0xFFCCCCCC),
                                fontSize  = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Dein Score: ${p.score}",
                                color      = Color(0xFF80CFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize   = 22.sp
                            )
                        } else if (!soloMode) {
                            // Timer abgelaufen — beide Scores vergleichen
                            Text(
                                "Dein Score",
                                color    = Color(0xFFCCCCCC),
                                fontSize = 13.sp
                            )
                            Text(
                                "${p.score}",
                                color      = Color(0xFF80CFFF),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 48.sp,
                                lineHeight = 52.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "$partnerName",
                                color    = Color(0xFFCCCCCC),
                                fontSize = 13.sp
                            )
                            // Eigene Münzen
                            Text(
                                "🪙 ${p.score * 20} Münzen",
                                color      = Color(0xFFFFDD00),
                                fontWeight = FontWeight.Bold,
                                fontSize   = 18.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "$partnerName",
                                color    = Color(0xFFCCCCCC),
                                fontSize = 13.sp
                            )
                            if (partnerFinalScore >= 0) {
                                Text(
                                    "$partnerFinalScore",
                                    color      = Color(0xFFFFAA88),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 48.sp,
                                    lineHeight = 52.sp
                                )
                                Text(
                                    "🪙 ${partnerFinalScore * 20} Münzen",
                                    color    = Color(0xFFFFDD00),
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 18.sp
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(20.dp),
                                    color       = Color(0xFFFFAA88),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Warte auf Score von $partnerName…",
                                    color    = Color(0xFFCCCCCC),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            // Solo-Modus
                            Text(
                                "Score: ${p.score}",
                                color      = Color(0xFF80CFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize   = 28.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "in 3 Minuten",
                                color    = Color(0xFFCCCCCC),
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        Button(onClick = handleBack) { Text("Zurück zur Übersicht") }
                    }
                }
            }

            // ── Treffer / Verfehlt-Overlay ────────────────────────────────────
            if (multiPhase == TndMultiPhase.PLAYING) {
                when (p.ballState) {
                    TndBallState.SCORED -> Box(
                        modifier           = Modifier.fillMaxSize().background(Color(0x44002200)),
                        contentAlignment   = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TREFFER!", color = Color(0xFF66FF66), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Score: ${p.score}", color = Color.White, fontSize = 24.sp)
                        }
                    }
                    TndBallState.MISSED -> Box(
                        modifier           = Modifier.fillMaxSize().background(Color(0x44220000)),
                        contentAlignment   = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("VERFEHLT!", color = Color(0xFFFF4444), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Neue Runde startet…", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    TndBallState.PLAYING -> Unit
                }
            }

            // ── Hinweis-Leiste ────────────────────────────────────────────────
            if (multiPhase != TndMultiPhase.GAME_OVER) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                ) {
                    val hint = when {
                        soloMode -> "Türkis = Spieler 1 • Pink = Spieler 2 • Gerät drehen = Plattform kippen"
                        isHost   -> "Du steuerst die türkisen Plattformen (Spieler 1) • vs. $partnerName"
                        else     -> "Du steuerst die pinken Plattformen (Spieler 2) • vs. $partnerName"
                    }
                    Text(
                        hint,
                        color    = Color(0x99FFFFFF),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
