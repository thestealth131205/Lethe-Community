package com.securechat.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Öffentliches Enum — welcher Lumis-Effekt läuft
// ─────────────────────────────────────────────────────────────────────────────

enum class LumisType { LOVE, SNOW, KISS, RAIN, SUMMER, CHRISTMAS, STRANGER_THINGS, LAST_OF_US, MARIO, NONE }

// ─────────────────────────────────────────────────────────────────────────────
// Interne Partikel-Datenklassen
// ─────────────────────────────────────────────────────────────────────────────

private data class FallingParticle(
    val startX: Float,           // X-Startposition (Pixel)
    val fallDuration: Float,     // Wie viel Anteil von 6 s es dauert, von oben nach unten zu fallen
    val startDelay: Float,       // Anteil von 6 s bevor das Partikel erscheint (Phase-Offset)
    val wobbleAmplitude: Float,  // Amplitude der horizontalen Schwingung (Pixel)
    val wobblePhase: Float,      // Phasenversatz der horizontalen Schwingung (Radiant)
    val size: Float              // Halb-Größe/Radius des Partikels (Pixel)
)

private data class KissParticle(
    val xFraction: Float,        // Zufällige X-Position als Anteil der Bildschirmbreite (0..1)
    val yFraction: Float,        // Zufällige Y-Position als Anteil der Bildschirmhöhe (0..1)
    val startProgress: Float,    // Wann der Kuss auftaucht (0..0.72 des 6-s-Fortschritts)
    val maxScale: Float,         // Maximaler Scale-Faktor dieses Kusses (0.8..1.3)
    val rotationDeg: Float,      // Drehung in Grad (-25..+25)
    val size: Float              // Basis-Größe (Pixel)
)

private data class RainDrop(
    val startX: Float,           // X-Startposition (Pixel)
    val cycleDuration: Float,    // Dauer eines Falldurchgangs als Anteil von 6 s (0.05..0.12)
    val phase: Float,            // Phasenversatz im Zyklus (0..1), damit Tropfen versetzt fallen
    val lineLength: Float,       // Länge der Regenlinie (Pixel)
    val opacity: Float           // Deckkraft des Tropfens (0.25..0.70)
)

private data class Snowflake(
    val startX: Float,           // X-Startposition (Pixel)
    val fallDuration: Float,     // Dauer eines Falldurchgangs als Anteil von 6 s (0.35..0.70)
    val phaseOffset: Float,      // Phasenversatz im Zyklus (0..1)
    val wobbleAmplitude: Float,  // Amplitude der horizontalen Schwingung (Pixel)
    val wobblePhase: Float,      // Phasenversatz der Schwingung (Radiant)
    val size: Float,             // Flocken-Radius (Pixel)
    val opacity: Float           // Max-Deckkraft (0.55..1.0)
)

// ─────────────────────────────────────────────────────────────────────────────
// LumisPickerDialog — Auswahl-Dialog für den Nutzer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LumisPickerDialog(
    onLumisSelected: (LumisType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Lumis senden",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                LumisType.values()
                    .filter { it != LumisType.NONE }
                    .forEach { lumisType ->
                        val emoji: String
                        val label: String
                        val description: String
                        when (lumisType) {
                            LumisType.LOVE            -> { emoji = "❤️";  label = "Love";           description = "Großes Herz, Federn und rosa Schleier" }
                            LumisType.SNOW            -> { emoji = "❄️";  label = "Snow";           description = "Schneeflocken sammeln sich als Schneeberg" }
                            LumisType.KISS            -> { emoji = "💋";  label = "Kiss";           description = "Küsse tauchen überall auf dem Bildschirm auf" }
                            LumisType.RAIN            -> { emoji = "🌧️"; label = "Rain";           description = "Leichter Regen mit wachsender Wasserpfütze" }
                            LumisType.SUMMER          -> { emoji = "🌴";  label = "Summer";         description = "Palmen, Sandhügel und Sonnenuntergang" }
                            LumisType.CHRISTMAS       -> { emoji = "🎅";  label = "Weihnachten";    description = "Schlitten mit Weihnachtsmann und Rentieren im Schnee" }
                            LumisType.STRANGER_THINGS -> { emoji = "🔴";  label = "Upside Down";    description = "Stranger Things: Ascheflocken, roter Himmel, Shadow Monster" }
                            LumisType.LAST_OF_US      -> { emoji = "🍄";  label = "The Last of Us"; description = "Ein Clicker steht düster in der Mitte" }
                            LumisType.MARIO           -> { emoji = "🍄";  label = "Mario & Peach";  description = "Mario-Landschaft mit Mario und Peach" }
                            LumisType.NONE            -> return@forEach
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLumisSelected(lumisType) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 30.sp,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LumisPlayer — Haupt-Entry-Point-Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fullscreen-Overlay, das den aktiven Lumis-Effekt für exakt 6 Sekunden abspielt.
 * Nach 6 Sekunden wird [onAnimationEnd] aufgerufen.
 * Der Modifier sollte fillMaxSize() enthalten, damit der Effekt den gesamten Bereich abdeckt.
 */
@Composable
fun LumisPlayer(
    lumisType: LumisType,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lumisType == LumisType.NONE) return

    // Linearer Fortschritt 0f → 1f über exakt 6 000 ms
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(lumisType) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 6000, easing = LinearEasing)
        )
        onAnimationEnd()
    }

    val progress = progressAnim.value

    BoxWithConstraints(modifier = modifier) {
        // constraints.maxWidth/maxHeight sind in Pixel (raw pixels, konsistent mit Canvas DrawScope)
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        when (lumisType) {
            LumisType.LOVE            -> LumisLove(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.SNOW            -> LumisSnow(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.KISS            -> LumisKiss(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.RAIN            -> LumisRain(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.SUMMER          -> LumisSummer(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.CHRISTMAS       -> LumisChristmas(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.STRANGER_THINGS -> LumisStrangerThings(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.LAST_OF_US      -> LumisLastOfUs(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.MARIO           -> LumisMario(progress = progress, screenW = screenW, screenH = screenH)
            LumisType.NONE            -> { /* Nichts zeichnen */ }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOVE-Effekt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * LOVE: Großes 3D-Herz in der Mitte pulsiert. Im Hintergrund fallen Federn und kleine Herzen.
 * Ab Sekunde 3 (progress > 0.5) verfärbt sich der Hintergrund fließend in transparentes Pink.
 */
@Composable
private fun LumisLove(progress: Float, screenW: Float, screenH: Float) {
    // 12 Federn mit zufälligen Eigenschaften – einmalig berechnet
    val feathers = remember(screenW, screenH) {
        val rng = Random(seed = 1234L)
        List(12) {
            FallingParticle(
                startX          = rng.nextFloat() * screenW,
                fallDuration    = 0.55f + rng.nextFloat() * 0.30f,
                startDelay      = rng.nextFloat() * 0.45f,
                wobbleAmplitude = 16f + rng.nextFloat() * 22f,
                wobblePhase     = rng.nextFloat() * (PI * 2.0).toFloat(),
                size            = 10f + rng.nextFloat() * 14f
            )
        }
    }

    // 20 kleine Herzen, die von oben herunterfallen
    val smallHearts = remember(screenW, screenH) {
        val rng = Random(seed = 5678L)
        List(20) {
            FallingParticle(
                startX          = rng.nextFloat() * screenW,
                fallDuration    = 0.42f + rng.nextFloat() * 0.33f,
                startDelay      = rng.nextFloat() * 0.55f,
                wobbleAmplitude = 10f + rng.nextFloat() * 18f,
                wobblePhase     = rng.nextFloat() * (PI * 2.0).toFloat(),
                size            = 7f  + rng.nextFloat() * 11f
            )
        }
    }

    // Hintergrund-Tint: transparent → Pink nach Sekunde 3 (progress 0.5 → 1.0)
    val bgAlpha = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f) * 0.38f

    // Puls: das zentrale Herz schwingt 6-mal in 6 Sekunden leicht in der Größe
    val heartPulse   = 1f + 0.18f * sinf(progress * PI.toFloat() * 6f)
    val heartSizePx  = minOf(screenW, screenH) * 0.38f * heartPulse

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Hintergrund-Tint ──────────────────────────────────────────────
        if (bgAlpha > 0f) {
            drawRect(
                color = Color(0xFFFF69B4).copy(alpha = bgAlpha),
                size  = size
            )
        }

        // ── Fallende Federn ───────────────────────────────────────────────
        feathers.forEach { p ->
            val fp = ((progress - p.startDelay) / p.fallDuration).coerceIn(0f, 1f)
            if (fp <= 0f) return@forEach

            val rawY = -p.size * 2f + fp * (screenH + p.size * 4f)
            if (rawY > screenH + p.size * 2f) return@forEach

            val x          = p.startX + p.wobbleAmplitude * sinf(fp * PI.toFloat() * 4f + p.wobblePhase)
            val alpha      = (1f - fp * 0.50f).coerceIn(0f, 1f)
            val rotDeg     = sinf(fp * PI.toFloat() * 3f + p.wobblePhase) * 28f

            drawFeather(x = x, y = rawY, size = p.size, rotationDeg = rotDeg, alpha = alpha)
        }

        // ── Fallende kleine Herzen ────────────────────────────────────────
        smallHearts.forEach { p ->
            val fp = ((progress - p.startDelay) / p.fallDuration).coerceIn(0f, 1f)
            if (fp <= 0f) return@forEach

            val rawY = -p.size * 2f + fp * (screenH + p.size * 4f)
            if (rawY > screenH + p.size * 2f) return@forEach

            val x     = p.startX + p.wobbleAmplitude * sinf(fp * PI.toFloat() * 3f + p.wobblePhase)
            val alpha = (1f - fp * 0.60f).coerceIn(0f, 1f)

            val heartPath = buildHeartPath(cx = x, cy = rawY, size = p.size * 2f)
            drawPath(heartPath, Color(0xFFFF1493).copy(alpha = alpha))
        }

        // ── Großes 3D-Herz in der Mitte ──────────────────────────────────
        drawBigHeart(
            cx       = screenW / 2f,
            cy       = screenH / 2f - screenH * 0.03f,
            size     = heartSizePx,
            progress = progress
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SNOW-Effekt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * SNOW: Schneeflocken rieseln aus der TopBar nach unten und sammeln sich als
 * wachsender Schneeberg am unteren Bildschirmrand an.
 */
@Composable
private fun LumisSnow(progress: Float, screenW: Float, screenH: Float) {
    val density          = LocalDensity.current
    val maxPileHeightPx  = with(density) { 80.dp.toPx() }

    // Aktuelle Bergeshöhe wächst linear mit progress
    val pileHeight = progress * maxPileHeightPx

    // 55 Schneeflocken – jede hat einen Phase-Offset damit sie über den Bildschirm verteilt sind
    val snowflakes = remember(screenW, screenH) {
        val rng = Random(seed = 9999L)
        List(55) {
            Snowflake(
                startX          = rng.nextFloat() * screenW,
                fallDuration    = 0.38f + rng.nextFloat() * 0.42f,
                phaseOffset     = rng.nextFloat(),
                wobbleAmplitude = 6f + rng.nextFloat() * 16f,
                wobblePhase     = rng.nextFloat() * (PI * 2.0).toFloat(),
                size            = 3f + rng.nextFloat() * 7f,
                opacity         = 0.55f + rng.nextFloat() * 0.45f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Schneeflocken (auf die Fläche über dem Schneeberg begrenzt) ──
        clipRect(
            left   = 0f,
            top    = 0f,
            right  = screenW,
            bottom = screenH - pileHeight
        ) {
            snowflakes.forEach { flake ->
                // Zyklische Bewegung: der Phase-Offset verschiebt die Startposition im Zyklus
                val rawCyclePos  = (progress / flake.fallDuration + flake.phaseOffset) % 1f
                val cyclePos     = if (rawCyclePos < 0f) rawCyclePos + 1f else rawCyclePos

                val rawY = -flake.size * 2f + cyclePos * (screenH + flake.size * 4f)
                val x    = flake.startX + flake.wobbleAmplitude * sinf(
                    cyclePos * PI.toFloat() * 5f + flake.wobblePhase
                )

                // Deckkraft: voll wenn weit oben, etwas schwächer kurz vor der Schneefläche
                val distanceToPile = (screenH - pileHeight) - rawY
                val fadeNearPile   = (distanceToPile / (flake.size * 5f)).coerceIn(0f, 1f)
                val alpha          = flake.opacity * fadeNearPile

                if (alpha > 0.01f) {
                    drawSnowflake(cx = x, cy = rawY, radius = flake.size, alpha = alpha)
                }
            }
        }

        // ── Wachsender Schneeberg am unteren Rand ──
        if (pileHeight > 0.5f) {
            drawSnowPile(
                screenW    = screenW,
                screenH    = screenH,
                pileHeight = pileHeight,
                progress   = progress
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KISS-Effekt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * KISS: Auf dem gesamten Bildschirm tauchen zufällig Kussmünder auf —
 * kurzes Einblenden mit leichtem Scale, halten, Ausblenden.
 */
@Composable
private fun LumisKiss(progress: Float, screenW: Float, screenH: Float) {
    // 22 Küsse mit zufälliger Position, Timing, Größe und Rotation
    val kissMarks = remember(screenW, screenH) {
        val rng = Random(seed = 3333L)
        List(22) {
            KissParticle(
                xFraction     = 0.05f + rng.nextFloat() * 0.90f,
                yFraction     = 0.05f + rng.nextFloat() * 0.90f,
                startProgress = rng.nextFloat() * 0.70f,
                maxScale      = 0.80f + rng.nextFloat() * 0.50f,
                rotationDeg   = -25f  + rng.nextFloat() * 50f,
                size          = screenW * (0.038f + rng.nextFloat() * 0.042f)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        kissMarks.forEach { kiss ->
            val localProgress = progress - kiss.startProgress
            if (localProgress <= 0f) return@forEach

            // Lebenszyklus: Einblenden → Peak → Halten → Ausblenden
            // Gesamtdauer: 0.32 × 6 s ≈ 1.92 s pro Kuss
            val totalLifetime = 0.32f
            val appearDur     = 0.06f   // Einblenden
            val peakDur       = 0.12f   // Peak (voll sichtbar)
            val holdDur       = 0.04f   // Halten
            // Restliche Zeit = Ausblenden

            val alpha: Float
            val scaleMultiplier: Float

            when {
                localProgress < appearDur -> {
                    val t          = localProgress / appearDur
                    alpha          = t
                    scaleMultiplier = 0.35f + t * 0.85f   // 0.35 → 1.20
                }
                localProgress < appearDur + peakDur -> {
                    val t          = (localProgress - appearDur) / peakDur
                    alpha          = 1f
                    scaleMultiplier = 1.20f - t * 0.20f   // 1.20 → 1.00
                }
                localProgress < appearDur + peakDur + holdDur -> {
                    alpha          = 1f
                    scaleMultiplier = 1.00f
                }
                localProgress < totalLifetime -> {
                    val fadeDur    = totalLifetime - appearDur - peakDur - holdDur
                    val t          = (localProgress - appearDur - peakDur - holdDur) / fadeDur
                    alpha          = 1f - t
                    scaleMultiplier = 1.00f - t * 0.15f
                }
                else -> return@forEach
            }

            val cx = kiss.xFraction * screenW
            val cy = kiss.yFraction * screenH
            val s  = kiss.size * scaleMultiplier * kiss.maxScale

            withTransform(
                transformBlock = {
                    rotate(degrees = kiss.rotationDeg, pivot = Offset(cx, cy))
                }
            ) {
                drawKissMark(cx = cx, cy = cy, size = s, alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RAIN-Effekt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * RAIN: Viele kleine, schnelle, halbtransparente Regenlinien fallen von oben nach unten.
 * Am unteren Rand bildet sich eine stetig größer werdende Wasserpfütze.
 */
@Composable
private fun LumisRain(progress: Float, screenW: Float, screenH: Float) {
    val density           = LocalDensity.current
    val maxPuddleWidthPx  = with(density) { 230.dp.toPx() }
    val maxPuddleHeightPx = with(density) { 13.dp.toPx() }

    // 85 Regentropfen
    val rainDrops = remember(screenW, screenH) {
        val rng = Random(seed = 7777L)
        List(85) {
            RainDrop(
                startX       = rng.nextFloat() * screenW,
                cycleDuration = 0.048f + rng.nextFloat() * 0.068f,   // 0.29 s..0.70 s pro Zyklus
                phase         = rng.nextFloat(),
                lineLength    = 12f + rng.nextFloat() * 18f,
                opacity       = 0.22f + rng.nextFloat() * 0.42f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Regenlinien (zyklisch fallend) ───────────────────────────────
        rainDrops.forEach { drop ->
            // Zyklische Position: Tropfen fällt wiederholt von oben nach unten
            val rawCyclePos = (progress / drop.cycleDuration + drop.phase) % 1f
            val cyclePos    = if (rawCyclePos < 0f) rawCyclePos + 1f else rawCyclePos

            val topY = cyclePos * (screenH + drop.lineLength * 2f) - drop.lineLength
            val botY = topY + drop.lineLength

            // Leichter Schrägfall: 8° von der Vertikalen (dx ≈ 0.141 × Länge)
            val dx = drop.lineLength * 0.141f

            drawLine(
                color       = Color(0xFF87CEEB).copy(alpha = drop.opacity),
                start       = Offset(drop.startX, topY),
                end         = Offset(drop.startX + dx, botY),
                strokeWidth = 1.5f,
                cap         = StrokeCap.Round
            )
        }

        // ── Wachsende Wasserpfütze am unteren Rand ──────────────────────
        drawRainPuddle(
            screenW   = screenW,
            screenH   = screenH,
            progress  = progress,
            maxWidth  = maxPuddleWidthPx,
            maxHeight = maxPuddleHeightPx
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SUMMER-Effekt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * SUMMER:
 * - Links und rechts unten wachsen zwei Palmen.
 * - In der Mitte unten bildet sich ein Sandhügel.
 * - Rechts oben ist eine Sonne, die langsam untergeht (nach unten wandert).
 * - Der Hintergrund verfärbt sich fließend von transparent zu warmem Orange-Rot.
 */
@Composable
private fun LumisSummer(progress: Float, screenW: Float, screenH: Float) {
    val density          = LocalDensity.current
    val maxPalmHeightPx  = with(density) { 200.dp.toPx() }
    val maxSandHeightPx  = with(density) { 28.dp.toPx() }
    val sunRadiusPx      = with(density) { 34.dp.toPx() }

    // Palmen wachsen in den ersten 40 % der Animation
    val leftPalmScale  = ((progress - 0.00f) / 0.40f).coerceIn(0f, 1f)
    val rightPalmScale = ((progress - 0.08f) / 0.40f).coerceIn(0f, 1f)  // 0.5 s Versatz

    // Sonne: startet oben rechts, bewegt sich nach unten
    val sunStartY = screenH * 0.08f
    val sunEndY   = screenH * 0.72f
    val sunX      = screenW * 0.84f
    val sunY      = sunStartY + progress * (sunEndY - sunStartY)

    // Hintergrund-Farbe: transparent (t=0) → tiefes Orange-Rot (t=1)
    val bgAlpha = (progress * 0.52f).coerceIn(0f, 0.52f)

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Sonnenuntergangs-Hintergrundtönung ──────────────────────────
        val bgColor = lerp(
            start    = Color(0xFFFF8C00),
            stop     = Color(0xFFCC2000),
            fraction = (progress * 0.80f).coerceIn(0f, 1f)
        ).copy(alpha = bgAlpha)
        drawRect(bgColor, size = size)

        // ── Sonne (rechts oben, wandert nach unten) ──────────────────────
        drawSummerSun(
            cx       = sunX,
            cy       = sunY,
            radius   = sunRadiusPx,
            progress = progress
        )

        // ── Linke Palme (Ecke unten links, lehnt leicht nach rechts) ─────
        if (leftPalmScale > 0.01f) {
            drawPalmTree(
                baseX     = screenW * 0.02f,
                baseY     = screenH,
                maxHeight = maxPalmHeightPx,
                scale     = leftPalmScale,
                leanRight = true
            )
        }

        // ── Rechte Palme (Ecke unten rechts, lehnt leicht nach links) ────
        if (rightPalmScale > 0.01f) {
            drawPalmTree(
                baseX     = screenW * 0.98f,
                baseY     = screenH,
                maxHeight = maxPalmHeightPx,
                scale     = rightPalmScale,
                leanRight = false
            )
        }

        // ── Wachsender Sandhügel in der Mitte unten ──────────────────────
        drawSandPile(
            screenW   = screenW,
            screenH   = screenH,
            progress  = progress,
            maxWidth  = screenW * 0.55f,
            maxHeight = maxSandHeightPx
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Herzform
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Erstellt einen Herz-Pfad zentriert bei (cx, cy) in einer Box der Größe [size] × [size].
 * Das Herz hat:
 *  - Eine Spitze unten bei (cx, cy + size*0.45)
 *  - Zwei Rundungen oben bei (cx ± size*0.25, cy - size*0.42)
 *  - Eine Einbuchtung in der Mitte oben bei (cx, cy - size*0.15)
 */
private fun buildHeartPath(cx: Float, cy: Float, size: Float): Path {
    val path = Path()
    val s    = size / 2f          // Halb-Größen-Einheit für die Steuerberechnung

    // Alle Steuerpunkte für ein proportionales Herz:
    val tipX    = cx
    val tipY    = cy + s * 0.90f  // untere Spitze

    val dipX    = cx
    val dipY    = cy - s * 0.28f  // obere Mittelmulde

    val lBumpX  = cx - s * 0.50f
    val lBumpY  = cy - s * 0.82f  // Scheitel der linken Rundung

    val rBumpX  = cx + s * 0.50f
    val rBumpY  = cy - s * 0.82f  // Scheitel der rechten Rundung

    path.moveTo(tipX, tipY)

    // Untere Spitze → rechte Rundung
    path.cubicTo(
        cx + s * 1.20f, cy + s * 0.30f,   // ctrl 1 (rechts unten)
        cx + s * 1.10f, cy - s * 0.58f,   // ctrl 2 (rechts der Rundung)
        rBumpX, rBumpY                     // Scheitel rechte Rundung
    )

    // Rechte Rundung → Mittelmulde
    path.cubicTo(
        cx + s * 0.22f, cy - s * 1.10f,   // ctrl 1
        cx + s * 0.10f, cy - s * 0.60f,   // ctrl 2
        dipX, dipY                         // Mittelmulde
    )

    // Mittelmulde → linke Rundung
    path.cubicTo(
        cx - s * 0.10f, cy - s * 0.60f,   // ctrl 1
        cx - s * 0.22f, cy - s * 1.10f,   // ctrl 2
        lBumpX, lBumpY                     // Scheitel linke Rundung
    )

    // Linke Rundung → untere Spitze
    path.cubicTo(
        cx - s * 1.10f, cy - s * 0.58f,   // ctrl 1
        cx - s * 1.20f, cy + s * 0.30f,   // ctrl 2
        tipX, tipY                         // zurück zur Spitze
    )

    path.close()
    return path
}

/**
 * Zeichnet das große 3D-Herz in der Mitte des LOVE-Effekts.
 * Der 3D-Effekt entsteht durch: Schatten-Offset + Radialverlauf + Highlight.
 */
private fun DrawScope.drawBigHeart(cx: Float, cy: Float, size: Float, progress: Float) {
    val heartPath     = buildHeartPath(cx, cy, size)
    val shadowPath    = buildHeartPath(cx + 7f, cy + 7f, size)
    val highlightPath = buildHeartPath(cx - size * 0.08f, cy - size * 0.12f, size * 0.42f)

    // Schatten (dunkelrosa, leicht versetzt)
    drawPath(shadowPath, Color(0x55BB0030))

    // Hauptherz: radialer Verlauf von hellem Pink (oben-links) zu tiefem Rot (außen)
    val heartBrush = Brush.radialGradient(
        colors = listOf(
            Color(0xFFFF80B3),   // helles Pink (Zentrum)
            Color(0xFFE91E63),   // mittleres Pink
            Color(0xFFAD1457)    // tiefes Himbeerrot (Rand)
        ),
        center = Offset(cx - size * 0.12f, cy - size * 0.15f),
        radius = size * 0.92f
    )
    drawPath(heartPath, heartBrush)

    // Weißes Highlight oben-links (simuliert runde 3D-Oberfläche)
    drawPath(highlightPath, Color(0x50FFFFFF))

    // Pulsierender äußerer Leuchtkranz (synchon mit der Pulsation des Herzens)
    val glowAlpha = (0.08f + 0.07f * sinf(progress * PI.toFloat() * 8f)).coerceIn(0f, 0.22f)
    val glowPath  = buildHeartPath(cx, cy, size * 1.20f)
    drawPath(glowPath, Color(0xFFFF69B4).copy(alpha = glowAlpha))
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Feder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zeichnet eine stilisierte Feder: spitz zulaufender Ovalrumpf + Mittelschaft + Bartlinien.
 * Die Feder wird um [rotationDeg] Grad um ihren Schwerpunkt gedreht.
 */
private fun DrawScope.drawFeather(
    x: Float, y: Float,
    size: Float,
    rotationDeg: Float,
    alpha: Float
) {
    withTransform(
        transformBlock = {
            rotate(degrees = rotationDeg, pivot = Offset(x, y))
        }
    ) {
        // Federfahne: spitz zulaufendes Oval (oben schmal, in der Mitte breit, unten Stiel)
        val featherPath = Path()
        featherPath.moveTo(x, y - size * 1.80f)            // obere Spitze
        featherPath.cubicTo(
            x + size * 0.55f, y - size * 1.20f,            // ctrl 1 (rechte Seite weitet)
            x + size * 0.55f, y - size * 0.20f,            // ctrl 2
            x, y + size * 0.55f                            // untere Stielspitze
        )
        featherPath.cubicTo(
            x - size * 0.55f, y - size * 0.20f,            // ctrl 1
            x - size * 0.55f, y - size * 1.20f,            // ctrl 2
            x, y - size * 1.80f                            // zurück zur oberen Spitze
        )
        featherPath.close()
        drawPath(featherPath, Color(0xFFFFF0F5).copy(alpha = alpha * 0.72f))

        // Mittelschaft
        drawLine(
            color       = Color(0xFFFFCCD5).copy(alpha = alpha * 0.88f),
            start       = Offset(x, y - size * 1.80f),
            end         = Offset(x, y + size * 0.55f),
            strokeWidth = 1.8f,
            cap         = StrokeCap.Round
        )

        // Seitliche Bartlinien (6 Paare)
        val numBarbs = 6
        for (i in 1..numBarbs) {
            val t      = i.toFloat() / (numBarbs + 1)
            val shaftY = y - size * 1.80f + t * (size * 2.35f)
            val vaneX  = size * 0.52f * (1f - t * 0.28f)

            drawLine(
                color       = Color(0xFFFFDDE6).copy(alpha = alpha * 0.30f),
                start       = Offset(x, shaftY),
                end         = Offset(x + vaneX, shaftY + size * 0.14f),
                strokeWidth = 0.9f
            )
            drawLine(
                color       = Color(0xFFFFDDE6).copy(alpha = alpha * 0.30f),
                start       = Offset(x, shaftY),
                end         = Offset(x - vaneX, shaftY + size * 0.14f),
                strokeWidth = 0.9f
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Schneeflocke und Schneeberg
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zeichnet eine 6-armige Schneeflocke mit je zwei Ästen pro Arm.
 */
private fun DrawScope.drawSnowflake(cx: Float, cy: Float, radius: Float, alpha: Float) {
    val snowColor = Color.White.copy(alpha = alpha)
    val numArms   = 6

    for (armIndex in 0 until numArms) {
        val angle = armIndex.toFloat() * (PI.toFloat() / numArms.toFloat())

        // Hauptarm (in beide Richtungen verlängert)
        val arm1EndX = cx + cosf(angle) * radius
        val arm1EndY = cy + sinf(angle) * radius
        val arm2EndX = cx - cosf(angle) * radius
        val arm2EndY = cy - sinf(angle) * radius

        drawLine(snowColor, Offset(cx, cy), Offset(arm1EndX, arm1EndY), strokeWidth = 1.6f, cap = StrokeCap.Round)
        drawLine(snowColor, Offset(cx, cy), Offset(arm2EndX, arm2EndY), strokeWidth = 1.6f, cap = StrokeCap.Round)

        // Ast auf der Mitte des Hauptarms (rechts vom Arm)
        val branchAngleRight = angle + PI.toFloat() / 4f
        val branchLen        = radius * 0.38f
        val midX1 = cx + cosf(angle) * radius * 0.50f
        val midY1 = cy + sinf(angle) * radius * 0.50f

        drawLine(
            snowColor,
            Offset(midX1, midY1),
            Offset(midX1 + cosf(branchAngleRight) * branchLen, midY1 + sinf(branchAngleRight) * branchLen),
            strokeWidth = 1.0f,
            cap = StrokeCap.Round
        )

        // Ast auf der Mitte des Hauptarms (links vom Arm)
        val branchAngleLeft = angle - PI.toFloat() / 4f
        drawLine(
            snowColor,
            Offset(midX1, midY1),
            Offset(midX1 + cosf(branchAngleLeft) * branchLen, midY1 + sinf(branchAngleLeft) * branchLen),
            strokeWidth = 1.0f,
            cap = StrokeCap.Round
        )

        // Zweiter Ast auf der anderen Hälfte des Hauptarms
        val midX2 = cx - cosf(angle) * radius * 0.50f
        val midY2 = cy - sinf(angle) * radius * 0.50f
        val branchAngleRight2 = (angle + PI.toFloat()) + PI.toFloat() / 4f
        val branchAngleLeft2  = (angle + PI.toFloat()) - PI.toFloat() / 4f

        drawLine(
            snowColor,
            Offset(midX2, midY2),
            Offset(midX2 + cosf(branchAngleRight2) * branchLen, midY2 + sinf(branchAngleRight2) * branchLen),
            strokeWidth = 1.0f,
            cap = StrokeCap.Round
        )
        drawLine(
            snowColor,
            Offset(midX2, midY2),
            Offset(midX2 + cosf(branchAngleLeft2) * branchLen, midY2 + sinf(branchAngleLeft2) * branchLen),
            strokeWidth = 1.0f,
            cap = StrokeCap.Round
        )
    }

    // Zentraler Punkt
    drawCircle(snowColor, radius = radius * 0.18f, center = Offset(cx, cy))
}

/**
 * Zeichnet den wachsenden Schneeberg als Polygon am unteren Bildschirmrand.
 * Die Oberfläche ist eine Kombination mehrerer Sinuswellen — dadurch entstehen
 * natürlich wirkende Schneebuckel.
 */
private fun DrawScope.drawSnowPile(
    screenW: Float,
    screenH: Float,
    pileHeight: Float,
    progress: Float
) {
    val numSegments = 26

    // Schneeberg-Polygon aufbauen
    val pilePath = Path()
    pilePath.moveTo(0f, screenH)
    pilePath.lineTo(0f, screenH - pileHeight)

    for (i in 0..numSegments) {
        val x     = screenW * i.toFloat() / numSegments.toFloat()
        val normX = x / screenW

        // Drei überlagerte Sinuswellen erzeugen unregelmäßige Schneebuckel
        val wave1   = sinf(normX * PI.toFloat() * 5.2f + 0.30f) * pileHeight * 0.22f
        val wave2   = sinf(normX * PI.toFloat() * 9.8f + 1.70f) * pileHeight * 0.11f
        val wave3   = sinf(normX * PI.toFloat() * 3.1f + 2.50f + progress * 0.4f) * pileHeight * 0.13f

        val surfaceY = screenH - pileHeight + wave1 + wave2 + wave3
        pilePath.lineTo(x, surfaceY)
    }

    pilePath.lineTo(screenW, screenH)
    pilePath.close()

    // Hauptkörper: reines Schnee-Weiß mit leichtem Blaustich
    drawPath(pilePath, Color(0xFFF0F8FF))

    // Oberkante: sanftes Highlight/Schatten für Tiefenwirkung
    val surfacePath = Path()
    surfacePath.moveTo(0f, screenH - pileHeight)
    for (i in 0..numSegments) {
        val x     = screenW * i.toFloat() / numSegments.toFloat()
        val normX = x / screenW
        val wave1   = sinf(normX * PI.toFloat() * 5.2f + 0.30f) * pileHeight * 0.22f
        val wave2   = sinf(normX * PI.toFloat() * 9.8f + 1.70f) * pileHeight * 0.11f
        val wave3   = sinf(normX * PI.toFloat() * 3.1f + 2.50f + progress * 0.4f) * pileHeight * 0.13f
        val surfaceY = screenH - pileHeight + wave1 + wave2 + wave3
        surfacePath.lineTo(x, surfaceY)
    }
    drawPath(
        path  = surfacePath,
        color = Color(0xFFD8ECFF).copy(alpha = 0.55f),
        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Kussmund
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zeichnet einen stilisierten Kussmund:
 * - Untere Lippe: größeres Oval
 * - Obere Lippe: Cupid's-Bow-Form (zwei Rundungen mit Mitteleinzug)
 * - Highlight auf Unterlippe und linker Rundung der Oberlippe
 */
private fun DrawScope.drawKissMark(cx: Float, cy: Float, size: Float, alpha: Float) {
    val lipColor  = Color(0xFFD81B60).copy(alpha = alpha)        // tiefes Rose
    val lipLight  = Color(0xFFE91E63).copy(alpha = alpha * 0.90f) // helles Pink

    // ── Untere Lippe ─────────────────────────────────────────────────────
    val lowerPath = Path()
    lowerPath.moveTo(cx - size, cy + size * 0.05f)
    lowerPath.cubicTo(
        cx - size * 0.60f, cy + size * 1.10f,   // ctrl 1: links-unten
        cx + size * 0.60f, cy + size * 1.10f,   // ctrl 2: rechts-unten
        cx + size, cy + size * 0.05f             // Ende: rechts
    )
    lowerPath.cubicTo(
        cx + size * 0.38f, cy - size * 0.15f,   // ctrl 1: rechts-innen
        cx - size * 0.38f, cy - size * 0.15f,   // ctrl 2: links-innen
        cx - size, cy + size * 0.05f             // zurück zum Start
    )
    lowerPath.close()
    drawPath(lowerPath, lipColor)

    // ── Obere Lippe (Cupid's Bow) ─────────────────────────────────────────
    val upperPath = Path()
    upperPath.moveTo(cx - size, cy + size * 0.05f)   // linkes Ende

    // Linke Rundung
    upperPath.cubicTo(
        cx - size * 0.86f, cy - size * 0.68f,   // ctrl 1
        cx - size * 0.50f, cy - size * 0.65f,   // ctrl 2
        cx - size * 0.14f, cy - size * 0.20f    // Einzug links (Cupid's dip)
    )
    // Mittlerer Einzug
    upperPath.cubicTo(
        cx - size * 0.05f, cy - size * 0.38f,   // ctrl 1
        cx + size * 0.05f, cy - size * 0.38f,   // ctrl 2
        cx + size * 0.14f, cy - size * 0.20f    // Einzug rechts
    )
    // Rechte Rundung
    upperPath.cubicTo(
        cx + size * 0.50f, cy - size * 0.65f,   // ctrl 1
        cx + size * 0.86f, cy - size * 0.68f,   // ctrl 2
        cx + size, cy + size * 0.05f             // rechtes Ende
    )
    // Unterkante der Oberlippe (schließt das Polygon)
    upperPath.cubicTo(
        cx + size * 0.38f, cy - size * 0.15f,
        cx - size * 0.38f, cy - size * 0.15f,
        cx - size, cy + size * 0.05f
    )
    upperPath.close()
    drawPath(upperPath, lipLight)

    // ── Highlights ───────────────────────────────────────────────────────
    // Auf der Unterlippe (mittig)
    drawOval(
        color   = Color.White.copy(alpha = alpha * 0.27f),
        topLeft = Offset(cx - size * 0.38f, cy + size * 0.20f),
        size    = Size(size * 0.76f, size * 0.28f)
    )
    // Auf der linken Rundung der Oberlippe
    drawOval(
        color   = Color.White.copy(alpha = alpha * 0.22f),
        topLeft = Offset(cx - size * 0.72f, cy - size * 0.54f),
        size    = Size(size * 0.36f, size * 0.16f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Regenpfütze
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zeichnet eine wachsende Wasserpfütze mit sich ausbreitenden Ripple-Ringen.
 */
private fun DrawScope.drawRainPuddle(
    screenW: Float,
    screenH: Float,
    progress: Float,
    maxWidth: Float,
    maxHeight: Float
) {
    val puddleWidth  = progress * maxWidth
    val puddleHeight = (progress * maxHeight).coerceAtLeast(2f)
    val cx           = screenW / 2f
    // Die Pfütze liegt direkt auf der BottomBar (leicht über dem Bildschirmrand)
    val puddleBaseY  = screenH - maxHeight * 0.5f - 3f

    if (puddleWidth < 2f) return

    // ── Hauptpfütze (flaches Oval) ──────────────────────────────────────
    drawOval(
        color   = Color(0x9900AADD),
        topLeft = Offset(cx - puddleWidth / 2f, puddleBaseY - puddleHeight / 2f),
        size    = Size(puddleWidth, puddleHeight)
    )

    // Licht-Reflex auf der Pfütze
    drawOval(
        color   = Color.White.copy(alpha = 0.22f),
        topLeft = Offset(cx - puddleWidth * 0.28f, puddleBaseY - puddleHeight * 0.38f),
        size    = Size(puddleWidth * 0.56f, puddleHeight * 0.32f)
    )

    // ── Ripple-Ringe (4 Ringe, zeitversetzt sich ausbreitend) ──────────
    val ripplePeriod = 0.22f   // Ein Ausbreitungs-Zyklus dauert 0.22 × 6 s = 1.32 s
    for (ringIndex in 0 until 4) {
        val ringDelay    = ringIndex * 0.055f
        val ringProgress = progress - ringDelay
        if (ringProgress <= 0f) continue

        // Anteil innerhalb des aktuellen Ausbreitungszyklus (0..1)
        val ringPhase  = (ringProgress / ripplePeriod) % 1f
        val ringScale  = ringPhase.coerceIn(0f, 1f)
        val ringAlpha  = ((1f - ringScale) * 0.52f).coerceIn(0f, 0.52f)
        if (ringAlpha < 0.01f) continue

        val ringWidth  = puddleWidth  * (0.28f + ringScale * 0.92f)
        val ringHeight = puddleHeight * (0.45f + ringScale * 2.10f)

        drawOval(
            color   = Color(0xFF00AADD).copy(alpha = ringAlpha),
            topLeft = Offset(cx - ringWidth / 2f, puddleBaseY - ringHeight / 2f),
            size    = Size(ringWidth, ringHeight),
            style   = Stroke(width = 1.8f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Sommer: Sonne
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zeichnet die Sonne mit Glüh-Halos, 8 Strahlen und einem Farbübergang
 * von Gold (progress=0) zu Orange-Rot (progress=1).
 */
private fun DrawScope.drawSummerSun(
    cx: Float, cy: Float,
    radius: Float,
    progress: Float
) {
    // Farbe wechselt von Gold zu tiefem Orange-Rot beim Untergang
    val sunColor  = lerp(Color(0xFFFFD700), Color(0xFFFF4500), (progress * 0.88f).coerceIn(0f, 1f))
    val glowColor = sunColor.copy(alpha = 0.22f)

    // ── Weiche Glow-Halos ────────────────────────────────────────────────
    drawCircle(color = glowColor,                      radius = radius * 2.8f, center = Offset(cx, cy))
    drawCircle(color = glowColor.copy(alpha = 0.12f),  radius = radius * 4.5f, center = Offset(cx, cy))
    drawCircle(color = glowColor.copy(alpha = 0.06f),  radius = radius * 6.5f, center = Offset(cx, cy))

    // ── 8 Sonnenstrahlen (langsam rotierend) ─────────────────────────────
    val numRays   = 8
    val rotOffset = progress * (PI.toFloat() / 5.5f)   // sanfte Rotation

    for (i in 0 until numRays) {
        val angle    = rotOffset + i.toFloat() * (PI.toFloat() * 2f / numRays.toFloat())
        // Strahlenlänge pulsiert leicht
        val innerR   = radius * 1.28f
        val outerR   = radius * (1.80f + 0.12f * sinf(progress * PI.toFloat() * 5f + i.toFloat()))
        val rayStart = Offset(cx + cosf(angle) * innerR, cy + sinf(angle) * innerR)
        val rayEnd   = Offset(cx + cosf(angle) * outerR, cy + sinf(angle) * outerR)

        drawLine(
            color       = sunColor.copy(alpha = 0.58f),
            start       = rayStart,
            end         = rayEnd,
            strokeWidth = 3.5f,
            cap         = StrokeCap.Round
        )
    }

    // ── Haupt-Scheibe ────────────────────────────────────────────────────
    drawCircle(color = sunColor, radius = radius, center = Offset(cx, cy))

    // Weißes Highlight (oben-links — simuliert runde 3D-Form)
    drawCircle(
        color  = Color.White.copy(alpha = 0.28f),
        radius = radius * 0.40f,
        center = Offset(cx - radius * 0.22f, cy - radius * 0.22f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Sommer: Palme
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zeichnet eine Palme, die von (baseX, baseY) nach oben wächst.
 * [leanRight] = true:  Krone lehnt nach rechts (linke Palme)
 * [leanRight] = false: Krone lehnt nach links  (rechte Palme)
 * [scale] animiert das Wachstum von 0 auf 1.
 */
private fun DrawScope.drawPalmTree(
    baseX: Float, baseY: Float,
    maxHeight: Float, scale: Float,
    leanRight: Boolean
) {
    val h = maxHeight * scale
    if (h < 2f) return

    val leanDir     = if (leanRight) 1f else -1f
    val leanOffsetX = leanDir * h * 0.27f        // wie weit sich die Krone horizontal verschiebt
    val crownX      = baseX + leanOffsetX
    val crownY      = baseY - h
    val baseHalfW   = (16f * scale).coerceAtLeast(1f)
    val topHalfW    = (8f  * scale).coerceAtLeast(0.5f)

    // ── Stamm (leicht gebogen, nach oben verjüngend) ─────────────────────
    val trunkPath = Path()
    trunkPath.moveTo(baseX - baseHalfW, baseY)

    // Linke Stammkante
    trunkPath.cubicTo(
        baseX - baseHalfW + leanOffsetX * 0.18f, baseY - h * 0.35f,
        crownX - topHalfW + leanOffsetX * 0.08f, baseY - h * 0.68f,
        crownX - topHalfW, crownY
    )
    trunkPath.lineTo(crownX + topHalfW, crownY)

    // Rechte Stammkante
    trunkPath.cubicTo(
        crownX + topHalfW + leanOffsetX * 0.08f, baseY - h * 0.68f,
        baseX + baseHalfW + leanOffsetX * 0.18f, baseY - h * 0.35f,
        baseX + baseHalfW, baseY
    )
    trunkPath.close()
    drawPath(trunkPath, Color(0xFF7B5230))

    // Stammstruktur: horizontale Segmentlinien (Stammringe)
    val numSegments = (7 * scale).toInt().coerceAtLeast(2)
    for (seg in 1 until numSegments) {
        val t      = seg.toFloat() / numSegments.toFloat()
        val segY   = baseY - h * t
        val segX   = baseX + leanOffsetX * t
        val halfW  = (baseHalfW * (1f - t * 0.35f)).coerceAtLeast(0.5f)

        drawLine(
            color       = Color(0xFF5C3D1E).copy(alpha = 0.38f),
            start       = Offset(segX - halfW, segY),
            end         = Offset(segX + halfW, segY),
            strokeWidth = 1.5f
        )
    }

    // ── Blätter (7 Wedel, fächerartig von der Krone ausgehend) ──────────
    // Winkel: um –90° (gerade nach oben) verteilt in einem 130°-Fächer
    val frondColors  = listOf(
        Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF43A047),
        Color(0xFF4CAF50), Color(0xFF66BB6A), Color(0xFF2E7D32), Color(0xFF388E3C)
    )
    val frondAngles  = listOf(-90f, -58f, -122f, -26f, -154f, -42f, -138f)
    val frondLen     = h * 0.52f
    val strokeWidth  = (6f * scale).coerceAtLeast(1f)

    frondAngles.forEachIndexed { idx, angleDeg ->
        val angleRad = (angleDeg.toDouble() * PI / 180.0).toFloat()
        val endX     = crownX + cosf(angleRad) * frondLen
        val endY     = crownY + sinf(angleRad) * frondLen

        // Steuerpunkt: nach unten gezogen (Schwerkraft → hängende Wedel)
        val ctrlX    = crownX + cosf(angleRad) * frondLen * 0.54f
        val ctrlY    = crownY + sinf(angleRad) * frondLen * 0.54f + frondLen * 0.28f

        val frondPath = Path()
        frondPath.moveTo(crownX, crownY)
        frondPath.quadraticTo(ctrlX, ctrlY, endX, endY)

        drawPath(
            path  = frondPath,
            color = frondColors[idx % frondColors.size],
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Kleines Blattoval an der Spitze des Wedels
        val leafW = (14f * scale).coerceAtLeast(1f)
        val leafH = (6f  * scale).coerceAtLeast(0.5f)
        if (leafW > 1f && leafH > 0.5f) {
            drawOval(
                color   = frondColors[idx % frondColors.size].copy(alpha = 0.82f),
                topLeft = Offset(endX - leafW / 2f, endY - leafH / 2f),
                size    = Size(leafW, leafH)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope-Hilfsfunktionen — Sommer: Sandhügel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zeichnet einen wachsenden halbelliptischen Sandhügel in der Mitte des Bildschirm-Unterrands.
 */
private fun DrawScope.drawSandPile(
    screenW: Float,
    screenH: Float,
    progress: Float,
    maxWidth: Float,
    maxHeight: Float
) {
    val sandWidth  = progress * maxWidth
    val sandHeight = progress * maxHeight
    if (sandWidth < 2f) return

    val cx = screenW / 2f

    // Sandhügel als Halb-Ellipse (Cubic-Bezier-Bogen)
    val sandPath = Path()
    sandPath.moveTo(cx - sandWidth / 2f, screenH)
    sandPath.cubicTo(
        cx - sandWidth / 2f, screenH - sandHeight * 1.60f,   // ctrl 1 (links)
        cx + sandWidth / 2f, screenH - sandHeight * 1.60f,   // ctrl 2 (rechts)
        cx + sandWidth / 2f, screenH                         // Ende (rechter Fuß)
    )
    sandPath.close()

    // Grundfarbe: warmes Sandelb
    drawPath(sandPath, Color(0xFFE0B84A))

    // Highlight-Kammlinie (helleres Gelb auf dem höchsten Punkt)
    val ridgePath = Path()
    ridgePath.moveTo(cx - sandWidth * 0.30f, screenH - sandHeight * 0.80f)
    ridgePath.cubicTo(
        cx - sandWidth * 0.10f, screenH - sandHeight * 1.05f,
        cx + sandWidth * 0.10f, screenH - sandHeight * 1.05f,
        cx + sandWidth * 0.30f, screenH - sandHeight * 0.80f
    )
    drawPath(
        path  = ridgePath,
        color = Color(0xFFF5CC56).copy(alpha = 0.68f),
        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CHRISTMAS-Effekt
// ─────────────────────────────────────────────────────────────────────────────

private data class ChristmasSnowflake(
    val startX: Float,
    val fallDuration: Float,
    val phaseOffset: Float,
    val wobbleAmplitude: Float,
    val wobblePhase: Float,
    val size: Float,
    val opacity: Float
)

private data class StarDot(
    val x: Float,
    val y: Float,
    val size: Float,
    val twinklePhase: Float
)

/**
 * CHRISTMAS: Weihnachtsnacht-Szene mit Sternenhimmel, fallenden Schneeflocken
 * und einem Schlitten mit Weihnachtsmann und zwei Rentieren, der auf einer
 * Sinusbahn von rechts-oben nach links-unten über den Bildschirm fliegt.
 */
@Composable
private fun LumisChristmas(progress: Float, screenW: Float, screenH: Float) {
    val density = LocalDensity.current

    val snowflakes = remember(screenW, screenH) {
        val rng = Random(seed = 4242L)
        List(48) {
            ChristmasSnowflake(
                startX          = rng.nextFloat() * screenW,
                fallDuration    = 0.40f + rng.nextFloat() * 0.45f,
                phaseOffset     = rng.nextFloat(),
                wobbleAmplitude = 5f + rng.nextFloat() * 14f,
                wobblePhase     = rng.nextFloat() * (PI * 2.0).toFloat(),
                size            = 2.5f + rng.nextFloat() * 6f,
                opacity         = 0.50f + rng.nextFloat() * 0.50f
            )
        }
    }

    val starDots = remember(screenW, screenH) {
        val rng = Random(seed = 8181L)
        List(60) {
            StarDot(
                x            = rng.nextFloat() * screenW,
                y            = rng.nextFloat() * screenH * 0.70f,
                size         = 1f + rng.nextFloat() * 3f,
                twinklePhase = rng.nextFloat() * (PI * 2.0).toFloat()
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Nacht-Himmels-Hintergrund (dunkelblau-schwarz Gradient) ──────
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF010814),
                    Color(0xFF011530),
                    Color(0xFF02204A)
                ),
                startY = 0f,
                endY   = screenH
            ),
            size = size
        )

        // ── Sternenhimmel ─────────────────────────────────────────────────
        starDots.forEach { star ->
            val twinkle = 0.55f + 0.45f * sinf(progress * PI.toFloat() * 8f + star.twinklePhase)
            drawCircle(
                color  = Color.White.copy(alpha = twinkle * 0.90f),
                radius = star.size * 0.5f,
                center = Offset(star.x, star.y)
            )
        }

        // ── Schneeflocken (zyklisch fallend) ─────────────────────────────
        snowflakes.forEach { flake ->
            val rawCyclePos = (progress / flake.fallDuration + flake.phaseOffset) % 1f
            val cyclePos    = if (rawCyclePos < 0f) rawCyclePos + 1f else rawCyclePos

            val rawY = -flake.size * 2f + cyclePos * (screenH + flake.size * 4f)
            val x    = flake.startX + flake.wobbleAmplitude * sinf(
                cyclePos * PI.toFloat() * 5f + flake.wobblePhase
            )

            val alpha = flake.opacity
            if (alpha > 0.01f) {
                drawCircle(
                    color  = Color.White.copy(alpha = alpha),
                    radius = flake.size,
                    center = Offset(x, rawY)
                )
            }
        }

        // ── Schlitten mit Weihnachtsmann (sinusförmige Bahn von rechts→links) ──
        // Erscheint bei progress=0.05, verschwindet aus dem Screen am Ende
        if (progress >= 0.05f) {
            val t = ((progress - 0.05f) / 0.95f).coerceIn(0f, 1f)

            // X: von rechts außen (screenW+150) nach links außen (-250)
            val sleighX = screenW + 150f - t * (screenW + 400f)

            // Y: sinusförmige Bahn im oberen Drittel des Bildschirms
            val baseY      = screenH * 0.28f
            val amplitude  = screenH * 0.12f
            val sleighY    = baseY + amplitude * sinf(t * PI.toFloat() * 2.5f)

            // Schlitten fliegt von rechts nach links → horizontale Spiegelung
            // Schlitten-Silhouette zeichnen
            drawChristmasSleigh(
                cx       = sleighX,
                cy       = sleighY,
                screenW  = screenW,
                screenH  = screenH,
                progress = progress
            )
        }

        // ── Schneehügel am unteren Rand ──────────────────────────────────
        val pileH = screenH * 0.06f * progress
        if (pileH > 1f) {
            val numSeg = 30
            val pilePath = Path()
            pilePath.moveTo(0f, screenH)
            pilePath.lineTo(0f, screenH - pileH)
            for (i in 0..numSeg) {
                val x     = screenW * i.toFloat() / numSeg.toFloat()
                val normX = x / screenW
                val wave1 = sinf(normX * PI.toFloat() * 4.8f + 0.5f) * pileH * 0.25f
                val wave2 = sinf(normX * PI.toFloat() * 9.2f + 1.3f) * pileH * 0.13f
                pilePath.lineTo(x, screenH - pileH + wave1 + wave2)
            }
            pilePath.lineTo(screenW, screenH)
            pilePath.close()
            drawPath(pilePath, Color(0xFFE8F4FF).copy(alpha = 0.92f))
        }
    }
}

/**
 * Zeichnet Schlitten + Weihnachtsmann-Silhouette + 2 Rentiere.
 */
private fun DrawScope.drawChristmasSleigh(
    cx: Float, cy: Float,
    screenW: Float, screenH: Float,
    progress: Float
) {
    val scale = (minOf(screenW, screenH) * 0.0009f).coerceIn(0.5f, 1.2f)
    val silhouetteColor = Color(0xFF0A0A15)
    val darkAccent      = Color(0xFF1A1030)

    // ── Rentier 1 (weiter vorn, links vom Schlitten) ──────────────────────
    val r1x = cx - 130f * scale
    val r1y = cy - 10f * scale
    drawReindeer(r1x, r1y, scale * 0.9f, silhouetteColor)

    // ── Rentier 2 (dahinter, etwas weiter vorn) ───────────────────────────
    val r2x = cx - 80f * scale
    val r2y = cy - 5f * scale
    drawReindeer(r2x, r2y, scale * 0.85f, silhouetteColor)

    // Verbindungsleinen (Zügel) zu den Rentieren
    drawLine(
        color = silhouetteColor.copy(alpha = 0.80f),
        start = Offset(cx - 40f * scale, cy),
        end   = Offset(r1x + 20f * scale, r1y),
        strokeWidth = 1.5f * scale, cap = StrokeCap.Round
    )
    drawLine(
        color = silhouetteColor.copy(alpha = 0.80f),
        start = Offset(cx - 40f * scale, cy),
        end   = Offset(r2x + 20f * scale, r2y),
        strokeWidth = 1.5f * scale, cap = StrokeCap.Round
    )

    // ── Schlitten-Korb ────────────────────────────────────────────────────
    val sledPath = Path()
    // Hauptkorb (gebogen)
    sledPath.moveTo(cx - 35f * scale, cy - 5f * scale)
    sledPath.cubicTo(
        cx - 30f * scale, cy - 30f * scale,
        cx + 20f * scale, cy - 30f * scale,
        cx + 35f * scale, cy - 5f * scale
    )
    sledPath.lineTo(cx + 35f * scale, cy + 5f * scale)
    sledPath.cubicTo(
        cx + 20f * scale, cy + 5f * scale,
        cx - 30f * scale, cy + 5f * scale,
        cx - 35f * scale, cy + 5f * scale
    )
    sledPath.close()
    drawPath(sledPath, darkAccent)

    // Schlitten-Kufen (2 gebogene Linien unten)
    val runner1 = Path()
    runner1.moveTo(cx - 40f * scale, cy + 5f * scale)
    runner1.cubicTo(
        cx - 38f * scale, cy + 14f * scale,
        cx + 32f * scale, cy + 14f * scale,
        cx + 42f * scale, cy + 5f * scale
    )
    drawPath(runner1, silhouetteColor, style = Stroke(width = 3f * scale, cap = StrokeCap.Round))

    val runner2 = Path()
    runner2.moveTo(cx - 36f * scale, cy + 8f * scale)
    runner2.cubicTo(
        cx - 34f * scale, cy + 18f * scale,
        cx + 28f * scale, cy + 18f * scale,
        cx + 38f * scale, cy + 8f * scale
    )
    drawPath(runner2, silhouetteColor.copy(alpha = 0.55f), style = Stroke(width = 2f * scale))

    // ── Weihnachtsmann-Silhouette (auf dem Schlitten) ─────────────────────
    val santaX = cx + 8f * scale
    val santaY = cy - 5f * scale

    // Körper
    drawOval(
        color   = silhouetteColor,
        topLeft = Offset(santaX - 14f * scale, santaY - 28f * scale),
        size    = Size(28f * scale, 24f * scale)
    )
    // Kopf
    drawCircle(
        color  = silhouetteColor,
        radius = 11f * scale,
        center = Offset(santaX, santaY - 38f * scale)
    )
    // Mütze (Dreieck-artig nach oben)
    val hatPath = Path()
    hatPath.moveTo(santaX - 10f * scale, santaY - 46f * scale)
    hatPath.lineTo(santaX + 2f * scale, santaY - 70f * scale)
    hatPath.lineTo(santaX + 12f * scale, santaY - 48f * scale)
    hatPath.close()
    drawPath(hatPath, silhouetteColor)
    // Mützen-Bommel
    drawCircle(
        color  = Color(0xFFEEEEEE).copy(alpha = 0.85f),
        radius = 4f * scale,
        center = Offset(santaX + 2f * scale, santaY - 70f * scale)
    )
    // Sack auf dem Schlitten
    drawOval(
        color   = silhouetteColor,
        topLeft = Offset(cx - 30f * scale, cy - 30f * scale),
        size    = Size(22f * scale, 26f * scale)
    )
}

/**
 * Zeichnet eine einfache Rentier-Silhouette (Kopf, Geweih, Körper, Beine).
 */
private fun DrawScope.drawReindeer(cx: Float, cy: Float, scale: Float, color: Color) {
    // Körper (Oval)
    drawOval(
        color   = color,
        topLeft = Offset(cx - 22f * scale, cy - 10f * scale),
        size    = Size(44f * scale, 18f * scale)
    )
    // Hals
    drawLine(color, Offset(cx + 14f * scale, cy - 8f * scale), Offset(cx + 22f * scale, cy - 22f * scale),
        strokeWidth = 7f * scale, cap = StrokeCap.Round)
    // Kopf
    drawCircle(color, radius = 7f * scale, center = Offset(cx + 26f * scale, cy - 26f * scale))
    // Geweih links
    val antlerLx = cx + 22f * scale
    val antlerLy = cy - 32f * scale
    drawLine(color, Offset(antlerLx, antlerLy), Offset(antlerLx - 8f * scale, antlerLy - 12f * scale),
        strokeWidth = 2.5f * scale, cap = StrokeCap.Round)
    drawLine(color, Offset(antlerLx - 8f * scale, antlerLy - 12f * scale),
        Offset(antlerLx - 14f * scale, antlerLy - 8f * scale),
        strokeWidth = 2f * scale, cap = StrokeCap.Round)
    drawLine(color, Offset(antlerLx - 8f * scale, antlerLy - 12f * scale),
        Offset(antlerLx - 5f * scale, antlerLy - 18f * scale),
        strokeWidth = 2f * scale, cap = StrokeCap.Round)
    // Geweih rechts
    drawLine(color, Offset(antlerLx + 4f * scale, antlerLy), Offset(antlerLx + 12f * scale, antlerLy - 12f * scale),
        strokeWidth = 2.5f * scale, cap = StrokeCap.Round)
    drawLine(color, Offset(antlerLx + 12f * scale, antlerLy - 12f * scale),
        Offset(antlerLx + 18f * scale, antlerLy - 8f * scale),
        strokeWidth = 2f * scale, cap = StrokeCap.Round)
    drawLine(color, Offset(antlerLx + 12f * scale, antlerLy - 12f * scale),
        Offset(antlerLx + 9f * scale, antlerLy - 18f * scale),
        strokeWidth = 2f * scale, cap = StrokeCap.Round)
    // 4 Beine
    val legY = cy + 8f * scale
    listOf(-14f, -5f, 8f, 17f).forEachIndexed { i, lx ->
        val swing = sinf(lx * 0.8f) * 4f * scale
        drawLine(color, Offset(cx + lx * scale, legY), Offset(cx + lx * scale + swing, legY + 14f * scale),
            strokeWidth = 4f * scale, cap = StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STRANGER THINGS – Upside Down-Effekt
// ─────────────────────────────────────────────────────────────────────────────

private data class AshFlake(
    val startX: Float,
    val fallDuration: Float,
    val phaseOffset: Float,
    val wobbleAmplitude: Float,
    val wobblePhase: Float,
    val size: Float,
    val opacity: Float,
    val irregularity: Float   // leichte Verformung des Ovals
)

private data class RedParticle(
    val angleOffset: Float,   // Startwinkel um den Monster-Mittelpunkt
    val speed: Float,         // Ausbreitungsgeschwindigkeit
    val size: Float,
    val phaseOffset: Float
)

/**
 * STRANGER THINGS – UPSIDE DOWN: Blutroter Himmel, Ascheflocken, Dunst-Wirbel oben
 * und die gigantische Silhouette des Shadow Monsters mit pulsierenden Tentakeln.
 */
@Composable
private fun LumisStrangerThings(progress: Float, screenW: Float, screenH: Float) {
    val density = LocalDensity.current

    val ashFlakes = remember(screenW, screenH) {
        val rng = Random(seed = 6060L)
        List(60) {
            AshFlake(
                startX          = rng.nextFloat() * screenW,
                fallDuration    = 0.50f + rng.nextFloat() * 0.55f,
                phaseOffset     = rng.nextFloat(),
                wobbleAmplitude = 8f + rng.nextFloat() * 20f,
                wobblePhase     = rng.nextFloat() * (PI * 2.0).toFloat(),
                size            = 2f + rng.nextFloat() * 7f,
                opacity         = 0.30f + rng.nextFloat() * 0.55f,
                irregularity    = 0.4f + rng.nextFloat() * 0.6f
            )
        }
    }

    val redParticles = remember(screenW, screenH) {
        val rng = Random(seed = 3131L)
        List(25) {
            RedParticle(
                angleOffset = rng.nextFloat() * (PI * 2.0).toFloat(),
                speed       = 0.04f + rng.nextFloat() * 0.08f,
                size        = 3f + rng.nextFloat() * 8f,
                phaseOffset = rng.nextFloat()
            )
        }
    }

    // Puls des Monsters (0.9 ↔ 1.1)
    val monsterPulse  = 1.0f + 0.10f * sinf(progress * PI.toFloat() * 6f)
    val monsterCx     = screenW * 0.50f
    val monsterCy     = screenH * 0.32f

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Hintergrund: tiefes Schwarz mit Blutrot-Schimmer von oben ────
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A0000),
                    Color(0xFF0D0000),
                    Color(0xFF050005)
                ),
                startY = 0f, endY = screenH
            ),
            size = size
        )

        // ── Roter Nebel-Dunst oben 30 % ───────────────────────────────────
        val numWisps = 8
        for (i in 0 until numWisps) {
            val wispPhase  = i.toFloat() / numWisps.toFloat()
            val wispX      = screenW * (0.05f + wispPhase * 0.90f)
            val wispY      = screenH * (0.05f + sinf(wispPhase * PI.toFloat() * 3f + progress * 2f) * 0.08f)
            val wispRadius = screenW * (0.22f + sinf(wispPhase * PI.toFloat() * 2.5f) * 0.08f)
            val wispAlpha  = 0.12f + 0.08f * sinf(progress * PI.toFloat() * 4f + wispPhase * 5f)

            drawCircle(
                color  = Color(0xFFCC1100).copy(alpha = wispAlpha),
                radius = wispRadius,
                center = Offset(wispX, wispY)
            )
        }

        // Zweite Dunst-Schicht (dunkler, breiter)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x55880000),
                    Color(0x00000000)
                ),
                startY = 0f, endY = screenH * 0.38f
            ),
            size = size
        )

        // ── Shadow-Monster-Silhouette ─────────────────────────────────────
        drawShadowMonster(
            cx      = monsterCx,
            cy      = monsterCy,
            scale   = monsterPulse,
            screenW = screenW,
            screenH = screenH,
            progress = progress
        )

        // ── Rote Partikel, die vom Monster-Zentrum ausstrahlen ────────────
        redParticles.forEach { p ->
            val cyclePos   = ((progress + p.phaseOffset) / (p.speed * 4f)) % 1f
            val dist       = cyclePos * screenH * 0.22f
            val angle      = p.angleOffset + progress * PI.toFloat() * 0.8f
            val px         = monsterCx + cosf(angle) * dist
            val py         = monsterCy + sinf(angle) * dist
            val particleA  = (1f - cyclePos) * 0.65f

            drawCircle(
                color  = Color(0xFFFF1100).copy(alpha = particleA),
                radius = p.size * (1f - cyclePos * 0.5f),
                center = Offset(px, py)
            )
        }

        // ── Ascheflocken (zyklisch fallend, dunkelgrau, leicht unregelmäßig) ──
        ashFlakes.forEach { flake ->
            val rawCyclePos = (progress / flake.fallDuration + flake.phaseOffset) % 1f
            val cyclePos    = if (rawCyclePos < 0f) rawCyclePos + 1f else rawCyclePos

            val rawY = -flake.size * 2f + cyclePos * (screenH + flake.size * 4f)
            val x    = flake.startX + flake.wobbleAmplitude *
                       sinf(cyclePos * PI.toFloat() * 4f + flake.wobblePhase)

            drawOval(
                color   = Color(0xFF3A3A3A).copy(alpha = flake.opacity),
                topLeft = Offset(x - flake.size * flake.irregularity,
                                 rawY - flake.size),
                size    = Size(flake.size * 2f * flake.irregularity, flake.size * 2f)
            )
        }
    }
}

/**
 * Zeichnet die Shadow-Monster-Silhouette mit Tentakeln.
 */
private fun DrawScope.drawShadowMonster(
    cx: Float, cy: Float,
    scale: Float,
    screenW: Float, screenH: Float,
    progress: Float
) {
    val monsterColor = Color(0xFF080808)
    val bodyH        = screenH * 0.38f * scale
    val bodyW        = screenW * 0.28f * scale

    // ── Hauptkörper (mehrere überlagerte dunkle Ellipsen) ─────────────────
    // Rumpf
    drawOval(
        color   = monsterColor,
        topLeft = Offset(cx - bodyW * 0.55f, cy - bodyH * 0.30f),
        size    = Size(bodyW * 1.10f, bodyH * 0.70f)
    )

    // Kopf-"Krone" oben (unregelmäßige, ausgebuchtete Form)
    val crownPath = Path()
    val crownCy   = cy - bodyH * 0.30f
    crownPath.moveTo(cx, crownCy - bodyH * 0.38f)
    // Strahlenförmige "Krone"-Tentakel — 8 Stück
    val numSpikes = 8
    for (i in 0 until numSpikes) {
        val frac       = i.toFloat() / numSpikes.toFloat()
        val spikeAngle = -PI.toFloat() + frac * PI.toFloat()   // Halbbogen oben
        val pulsate    = 1f + 0.15f * sinf(progress * PI.toFloat() * 5f + frac * 7f)
        val outerR     = bodyH * (0.45f + 0.10f * sinf(frac * PI.toFloat() * 4f)) * pulsate
        val innerR     = bodyH * 0.22f

        val innerX = cx + cosf(spikeAngle) * innerR
        val innerY = crownCy + sinf(spikeAngle) * innerR * 0.5f
        val outerX = cx + cosf(spikeAngle) * outerR
        val outerY = crownCy + sinf(spikeAngle) * outerR * 0.6f

        drawLine(
            color       = monsterColor,
            start       = Offset(innerX, innerY),
            end         = Offset(outerX, outerY),
            strokeWidth = bodyW * 0.14f * (1f - frac * 0.3f),
            cap         = StrokeCap.Round
        )

        // Seitenäste an jedem Tentakel
        val branchAngle = spikeAngle + 0.5f
        val branchR     = outerR * 0.42f
        drawLine(
            color       = monsterColor,
            start       = Offset(outerX, outerY),
            end         = Offset(outerX + cosf(branchAngle) * branchR,
                                 outerY + sinf(branchAngle) * branchR * 0.6f),
            strokeWidth = bodyW * 0.07f,
            cap         = StrokeCap.Round
        )
    }

    // Seitententakel (links und rechts vom Körper)
    for (side in listOf(-1f, 1f)) {
        val numSide = 5
        for (j in 0 until numSide) {
            val frac        = j.toFloat() / (numSide - 1).toFloat()
            val tentY       = cy - bodyH * 0.20f + frac * bodyH * 0.50f
            val tentBaseX   = cx + side * bodyW * 0.50f
            val tentAngle   = (if (side > 0) 0f else PI.toFloat()) +
                              sinf(frac * PI.toFloat() + progress * PI.toFloat() * 3f) * 0.4f
            val tentLen     = bodyW * (0.6f + 0.3f * sinf(frac * PI.toFloat() * 3f))
            val endX        = tentBaseX + cosf(tentAngle) * tentLen * side
            val endY        = tentY + sinf(tentAngle) * tentLen * 0.4f

            drawLine(
                color       = monsterColor,
                start       = Offset(tentBaseX, tentY),
                end         = Offset(endX, endY),
                strokeWidth = bodyW * 0.11f * (1f - frac * 0.4f),
                cap         = StrokeCap.Round
            )
        }
    }

    // Dunkel-roter Glow hinter dem Monster
    drawCircle(
        color  = Color(0xFFAA0000).copy(alpha = 0.12f + 0.06f * sinf(progress * PI.toFloat() * 4f)),
        radius = bodyH * 0.85f * scale,
        center = Offset(cx, cy)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LAST OF US – Clicker-Effekt
// ─────────────────────────────────────────────────────────────────────────────

private data class SporeParticle(
    val startX: Float,
    val startY: Float,
    val driftX: Float,
    val riseSpeed: Float,
    val size: Float,
    val opacity: Float,
    val phaseOffset: Float
)

private data class DustParticle(
    val x: Float,
    val y: Float,
    val driftX: Float,
    val driftY: Float,
    val size: Float,
    val opacity: Float,
    val phase: Float
)

/**
 * LAST OF US: Düstere, grünlich-gelbe Post-Apokalypse-Atmosphäre.
 * Ein Clicker steht ominös in der Mitte. Sporen steigen vom Boden auf,
 * Staubpartikel driften umher. Der Clicker-Kopf pulsiert biolumineszent.
 */
@Composable
private fun LumisLastOfUs(progress: Float, screenW: Float, screenH: Float) {
    val density = LocalDensity.current

    val spores = remember(screenW, screenH) {
        val rng = Random(seed = 5050L)
        List(45) {
            SporeParticle(
                startX      = screenW * (0.05f + rng.nextFloat() * 0.90f),
                startY      = screenH * (0.60f + rng.nextFloat() * 0.40f),
                driftX      = (rng.nextFloat() - 0.5f) * 30f,
                riseSpeed   = 0.15f + rng.nextFloat() * 0.25f,
                size        = 2f + rng.nextFloat() * 5f,
                opacity     = 0.25f + rng.nextFloat() * 0.50f,
                phaseOffset = rng.nextFloat()
            )
        }
    }

    val dustParticles = remember(screenW, screenH) {
        val rng = Random(seed = 7070L)
        List(30) {
            DustParticle(
                x       = rng.nextFloat() * screenW,
                y       = rng.nextFloat() * screenH,
                driftX  = (rng.nextFloat() - 0.5f) * 60f,
                driftY  = (rng.nextFloat() - 0.5f) * 20f,
                size    = 1.5f + rng.nextFloat() * 4f,
                opacity = 0.10f + rng.nextFloat() * 0.25f,
                phase   = rng.nextFloat()
            )
        }
    }

    // Clicker-Position: Mitte des Screens
    val clickerX = screenW * 0.50f
    val clickerBaseY = screenH * 0.90f
    val clickerH  = screenH * 0.58f

    // Biolumineszenz-Puls
    val bioPulse = 0.5f + 0.5f * sinf(progress * PI.toFloat() * 7f)

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Hintergrund: dunkel grünlich-braun ────────────────────────────
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0C1208),
                    Color(0xFF111A08),
                    Color(0xFF1A2410)
                ),
                startY = 0f, endY = screenH
            ),
            size = size
        )

        // ── Atmosphärischer Dunst (grünlich-gelb, oben leichter) ──────────
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x00000000),
                    Color(0x18446600),
                    Color(0x00000000)
                ),
                startY = screenH * 0.3f, endY = screenH * 0.75f
            ),
            size = size
        )

        // ── Staubpartikel (langsam driftend) ──────────────────────────────
        dustParticles.forEach { p ->
            val px = p.x + p.driftX * progress + 15f * sinf(progress * PI.toFloat() * 3f + p.phase)
            val py = p.y + p.driftY * progress
            drawCircle(
                color  = Color(0xFF8A9060).copy(alpha = p.opacity),
                radius = p.size,
                center = Offset(px % screenW, py % screenH)
            )
        }

        // ── Clicker-Silhouette ─────────────────────────────────────────────
        drawClickerSilhouette(
            cx       = clickerX,
            baseY    = clickerBaseY,
            height   = clickerH,
            bioPulse = bioPulse,
            progress = progress
        )

        // ── Sporen steigen vom Boden auf ───────────────────────────────────
        spores.forEach { spore ->
            val riseAmt = spore.riseSpeed * progress
            val cy      = spore.startY - riseAmt * screenH
            if (cy < -spore.size * 2f) return@forEach
            val cx2     = spore.startX + spore.driftX * progress +
                          8f * sinf(progress * PI.toFloat() * 5f + spore.phaseOffset)
            val fadeTop = (cy / (screenH * 0.15f)).coerceIn(0f, 1f)
            val alpha   = spore.opacity * fadeTop

            drawCircle(
                color  = Color(0xFFCCDD44).copy(alpha = alpha * 0.75f),
                radius = spore.size,
                center = Offset(cx2, cy)
            )
            // Kleiner Halo um Spore
            drawCircle(
                color  = Color(0xFF99BB00).copy(alpha = alpha * 0.25f),
                radius = spore.size * 2.2f,
                center = Offset(cx2, cy)
            )
        }
    }
}

/**
 * Zeichnet die Clicker-Silhouette: gebeugter Körper + bizarrer Pilzkopf.
 */
private fun DrawScope.drawClickerSilhouette(
    cx: Float, baseY: Float,
    height: Float,
    bioPulse: Float,
    progress: Float
) {
    val color     = Color(0xFF0C120A)
    val bioColor  = Color(0xFF88CC00)
    val bodyW     = height * 0.18f

    // ── Beine ──────────────────────────────────────────────────────────────
    // Linkes Bein (leicht vorgestreckt)
    val legAngleL = -0.15f + sinf(progress * PI.toFloat() * 4f) * 0.08f
    drawLine(color,
        start = Offset(cx - bodyW * 0.30f, baseY - height * 0.28f),
        end   = Offset(cx - bodyW * 0.50f + sinf(legAngleL) * height * 0.28f,
                       baseY - height * 0.28f + height * 0.28f),
        strokeWidth = bodyW * 0.55f, cap = StrokeCap.Round)
    // Rechtes Bein
    val legAngleR = 0.15f + sinf(progress * PI.toFloat() * 4f + 1.0f) * 0.08f
    drawLine(color,
        start = Offset(cx + bodyW * 0.30f, baseY - height * 0.28f),
        end   = Offset(cx + bodyW * 0.50f + sinf(legAngleR) * height * 0.28f,
                       baseY - height * 0.28f + height * 0.28f),
        strokeWidth = bodyW * 0.55f, cap = StrokeCap.Round)

    // ── Rumpf (leicht gebeugt) ──────────────────────────────────────────────
    val bodyPath = Path()
    bodyPath.moveTo(cx - bodyW * 0.55f, baseY)
    bodyPath.cubicTo(
        cx - bodyW * 0.60f, baseY - height * 0.30f,
        cx - bodyW * 0.50f, baseY - height * 0.50f,
        cx - bodyW * 0.30f, baseY - height * 0.55f
    )
    bodyPath.lineTo(cx + bodyW * 0.30f, baseY - height * 0.55f)
    bodyPath.cubicTo(
        cx + bodyW * 0.50f, baseY - height * 0.50f,
        cx + bodyW * 0.60f, baseY - height * 0.30f,
        cx + bodyW * 0.55f, baseY
    )
    bodyPath.close()
    drawPath(bodyPath, color)

    // ── Arme (nach vorn/unten hängend) ───────────────────────────────────
    val armSwing = sinf(progress * PI.toFloat() * 3f) * 0.12f
    // Linker Arm
    drawLine(color,
        start = Offset(cx - bodyW * 0.45f, baseY - height * 0.47f),
        end   = Offset(cx - bodyW * 1.10f + armSwing * height * 0.15f,
                       baseY - height * 0.18f),
        strokeWidth = bodyW * 0.42f, cap = StrokeCap.Round)
    // Rechter Arm
    drawLine(color,
        start = Offset(cx + bodyW * 0.45f, baseY - height * 0.47f),
        end   = Offset(cx + bodyW * 1.10f - armSwing * height * 0.15f,
                       baseY - height * 0.18f),
        strokeWidth = bodyW * 0.42f, cap = StrokeCap.Round)

    // ── Hals ──────────────────────────────────────────────────────────────
    drawLine(color,
        start = Offset(cx, baseY - height * 0.55f),
        end   = Offset(cx, baseY - height * 0.63f),
        strokeWidth = bodyW * 0.60f, cap = StrokeCap.Round)

    // ── Pilzkopf: bizarres Blob oben mit Tentakel-Verzweigungen ──────────
    val headCx = cx
    val headCy = baseY - height * 0.78f
    val headR  = height * 0.22f * (1f + 0.04f * bioPulse)

    // Biolumineszenz-Glow hinter Kopf
    val glowAlpha = 0.08f + 0.12f * bioPulse
    drawCircle(
        color  = bioColor.copy(alpha = glowAlpha),
        radius = headR * 2.2f,
        center = Offset(headCx, headCy)
    )
    drawCircle(
        color  = bioColor.copy(alpha = glowAlpha * 0.5f),
        radius = headR * 3.5f,
        center = Offset(headCx, headCy)
    )

    // Haupt-Pilzkopf-Blob (unregelmäßige Form via Bezier)
    val headPath = Path()
    headPath.moveTo(headCx, headCy - headR * 1.10f)
    headPath.cubicTo(
        headCx + headR * 0.80f, headCy - headR * 1.10f,
        headCx + headR * 1.30f, headCy - headR * 0.20f,
        headCx + headR * 0.90f, headCy + headR * 0.60f
    )
    headPath.cubicTo(
        headCx + headR * 0.40f, headCy + headR * 1.00f,
        headCx - headR * 0.40f, headCy + headR * 1.00f,
        headCx - headR * 0.90f, headCy + headR * 0.60f
    )
    headPath.cubicTo(
        headCx - headR * 1.30f, headCy - headR * 0.20f,
        headCx - headR * 0.80f, headCy - headR * 1.10f,
        headCx, headCy - headR * 1.10f
    )
    headPath.close()
    drawPath(headPath, color)

    // Pilz-"Tentakel"-Zweige vom Kopf
    val numBranches = 7
    for (i in 0 until numBranches) {
        val frac        = i.toFloat() / (numBranches - 1).toFloat()
        val branchAngle = -PI.toFloat() * 0.85f + frac * PI.toFloat() * 0.70f
        val pulseBranch = 1f + 0.12f * sinf(progress * PI.toFloat() * 6f + frac * 4f)
        val branchLen   = headR * (0.7f + 0.5f * sinf(frac * PI.toFloat() * 3f)) * pulseBranch

        val endX = headCx + cosf(branchAngle) * branchLen
        val endY = headCy + sinf(branchAngle) * branchLen * 0.75f

        drawLine(
            color       = color,
            start       = Offset(headCx + cosf(branchAngle) * headR * 0.6f,
                                 headCy + sinf(branchAngle) * headR * 0.6f),
            end         = Offset(endX, endY),
            strokeWidth = headR * 0.18f * (1f - frac * 0.35f),
            cap         = StrokeCap.Round
        )

        // Kleine Bio-Leuchtpunkte an Zweigspitzen
        drawCircle(
            color  = bioColor.copy(alpha = 0.35f + 0.30f * bioPulse),
            radius = headR * 0.10f,
            center = Offset(endX, endY)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MARIO – Mario & Peach-Effekt
// ─────────────────────────────────────────────────────────────────────────────

private data class MarioCoin(
    val x: Float,
    val startProgress: Float,
    val duration: Float
)

private data class MarioCloud(
    val x: Float,
    val y: Float,
    val scale: Float
)

/**
 * MARIO & PEACH: Helle Mario-Landschaft mit blauem Himmel, grünem Boden,
 * Wolken, Frage-Blöcken und einer Rohr-Dekoration. Mario (links) läuft hopfend
 * auf Peach (rechts) zu. Münzen erscheinen und fliegen nach oben.
 */
@Composable
private fun LumisMario(progress: Float, screenW: Float, screenH: Float) {
    val density = LocalDensity.current

    val coins = remember(screenW, screenH) {
        val rng = Random(seed = 2222L)
        List(8) {
            MarioCoin(
                x             = screenW * (0.15f + rng.nextFloat() * 0.70f),
                startProgress = rng.nextFloat() * 0.70f,
                duration      = 0.15f + rng.nextFloat() * 0.15f
            )
        }
    }

    val clouds = remember(screenW, screenH) {
        val rng = Random(seed = 9090L)
        List(4) {
            MarioCloud(
                x     = screenW * (0.05f + rng.nextFloat() * 0.85f),
                y     = screenH * (0.05f + rng.nextFloat() * 0.22f),
                scale = 0.6f + rng.nextFloat() * 0.7f
            )
        }
    }

    // Mario bewegt sich von links nach rechts (und hopft)
    val marioStartX = screenW * 0.08f
    val marioEndX   = screenW * 0.42f
    val marioX      = marioStartX + (marioEndX - marioStartX) * progress
    val groundY     = screenH * 0.80f
    val marioSize   = minOf(screenW, screenH) * 0.09f

    // Hopf-Animation: sinusförmige Y-Verschiebung nach oben
    val hopAmp  = marioSize * 0.90f
    val hopFreq = 5f   // Hopf-Frequenz (Mal in 6 s)
    val marioHop = hopAmp * maxOf(0f, sinf(progress * PI.toFloat() * hopFreq * 2f))

    Canvas(modifier = Modifier.fillMaxSize()) {

        // ── Himmel (helles Blau) ──────────────────────────────────────────
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF5C94FC), Color(0xFF92C8FC)),
                startY = 0f, endY = groundY
            ),
            topLeft = Offset(0f, 0f),
            size    = Size(screenW, groundY)
        )

        // ── Hintergrund-Boden ─────────────────────────────────────────────
        drawRect(
            color   = Color(0xFF5C8A14),
            topLeft = Offset(0f, groundY),
            size    = Size(screenW, screenH - groundY)
        )
        // Erde darunter
        drawRect(
            color   = Color(0xFF9B6930),
            topLeft = Offset(0f, groundY + (screenH - groundY) * 0.28f),
            size    = Size(screenW, (screenH - groundY) * 0.72f)
        )

        // ── Boden-Blöcke (Mario-Stil: grüne Quadrate mit Linien) ──────────
        val blockSize = marioSize * 0.80f
        val numBlocks = (screenW / blockSize).toInt() + 1
        for (i in 0..numBlocks) {
            val bx = i * blockSize
            // Gras-Block oben
            drawRect(
                color   = Color(0xFF5C8A14),
                topLeft = Offset(bx, groundY),
                size    = Size(blockSize - 1f, blockSize * 0.35f)
            )
            // Trennlinie
            drawLine(
                color       = Color(0xFF3D6B00).copy(alpha = 0.55f),
                start       = Offset(bx, groundY),
                end         = Offset(bx, groundY + blockSize * 0.35f),
                strokeWidth = 1.5f
            )
        }

        // ── Wolken ───────────────────────────────────────────────────────
        clouds.forEach { cloud ->
            // Wolke driftet leicht nach rechts
            val cloudX = cloud.x + progress * screenW * 0.04f
            drawMarioCloud(cloudX, cloud.y, cloud.scale * marioSize * 1.4f)
        }

        // ── Frage-Blöcke ─────────────────────────────────────────────────
        val qbY    = groundY - marioSize * 2.8f
        val qbSize = marioSize * 0.90f
        listOf(screenW * 0.32f, screenW * 0.48f, screenW * 0.64f).forEach { qbX ->
            drawMarioQuestionBlock(qbX, qbY, qbSize, progress)
        }

        // ── Grünes Pipe-Rohr (rechts, neben Peach) ───────────────────────
        val pipeX     = screenW * 0.80f
        val pipeW     = marioSize * 0.80f
        val pipeH     = marioSize * 1.50f
        val pipeCapH  = marioSize * 0.30f
        // Rohr-Körper
        drawRect(
            color   = Color(0xFF3AA015),
            topLeft = Offset(pipeX - pipeW * 0.45f, groundY - pipeH),
            size    = Size(pipeW * 0.90f, pipeH)
        )
        // Rohr-Kappe (breiter)
        drawRect(
            color   = Color(0xFF4DBB1C),
            topLeft = Offset(pipeX - pipeW * 0.55f, groundY - pipeH - pipeCapH),
            size    = Size(pipeW * 1.10f, pipeCapH)
        )
        // Highlight auf Rohr
        drawRect(
            color   = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(pipeX - pipeW * 0.40f, groundY - pipeH),
            size    = Size(pipeW * 0.20f, pipeH)
        )

        // ── Münzen (fliegen nach oben) ────────────────────────────────────
        coins.forEach { coin ->
            val lp = progress - coin.startProgress
            if (lp <= 0f || lp > coin.duration) return@forEach
            val t       = lp / coin.duration
            val coinY   = groundY - marioSize * 1.5f - t * marioSize * 3.5f
            val coinA   = (1f - t).coerceIn(0f, 1f)
            val coinR   = marioSize * 0.22f

            // Münze (gelbes Oval, leicht gedrückt)
            drawOval(
                color   = Color(0xFFFFD700).copy(alpha = coinA),
                topLeft = Offset(coin.x - coinR, coinY - coinR * 1.4f),
                size    = Size(coinR * 2f, coinR * 2.8f)
            )
            drawOval(
                color   = Color(0xFFFFA500).copy(alpha = coinA * 0.60f),
                topLeft = Offset(coin.x - coinR * 0.60f, coinY - coinR * 1.0f),
                size    = Size(coinR * 1.20f, coinR * 2.0f),
                style   = Stroke(width = 2f)
            )
        }

        // ── Peach (rechts, wartet) ────────────────────────────────────────
        val peachX = screenW * 0.70f
        val peachY = groundY
        drawMarioPeach(peachX, peachY, marioSize, progress)

        // ── Mario (läuft / hopft von links) ───────────────────────────────
        drawMarioFigure(marioX, groundY - marioHop, marioSize, progress)
    }
}

/**
 * Zeichnet eine Mario-Style-Wolke (3 überlagerte Kreise).
 */
private fun DrawScope.drawMarioCloud(cx: Float, cy: Float, radius: Float) {
    val cloudColor = Color.White.copy(alpha = 0.92f)
    drawCircle(cloudColor, radius = radius * 0.55f, center = Offset(cx - radius * 0.55f, cy))
    drawCircle(cloudColor, radius = radius * 0.75f, center = Offset(cx, cy - radius * 0.22f))
    drawCircle(cloudColor, radius = radius * 0.55f, center = Offset(cx + radius * 0.55f, cy))
    // Unterseite auffüllen
    drawRect(
        color   = cloudColor,
        topLeft = Offset(cx - radius * 1.10f, cy),
        size    = Size(radius * 2.20f, radius * 0.55f)
    )
    // Blaue Outline-Linie unten (Mario-Stil)
    drawLine(
        color = Color(0xFF92C8FC),
        start = Offset(cx - radius * 1.10f, cy + radius * 0.55f),
        end   = Offset(cx + radius * 1.10f, cy + radius * 0.55f),
        strokeWidth = 2f
    )
}

/**
 * Zeichnet einen Mario-Frageblock mit ? (pulsiert leicht).
 */
private fun DrawScope.drawMarioQuestionBlock(cx: Float, cy: Float, size: Float, progress: Float) {
    val pulse = 1f + 0.04f * sinf(progress * PI.toFloat() * 8f + cx)

    // Block-Körper
    drawRect(
        color   = Color(0xFFE8A000),
        topLeft = Offset(cx - size * 0.5f * pulse, cy - size * 0.5f * pulse),
        size    = Size(size * pulse, size * pulse)
    )
    // Dunkle Border
    drawRect(
        color   = Color(0xFF7A4500),
        topLeft = Offset(cx - size * 0.5f * pulse, cy - size * 0.5f * pulse),
        size    = Size(size * pulse, size * pulse),
        style   = Stroke(width = size * 0.08f)
    )
    // Highlight oben-links
    drawRect(
        color   = Color.White.copy(alpha = 0.30f),
        topLeft = Offset(cx - size * 0.5f * pulse + size * 0.07f * pulse,
                         cy - size * 0.5f * pulse + size * 0.07f * pulse),
        size    = Size(size * 0.28f * pulse, size * 0.12f * pulse)
    )
    // Fragezeichen-Balken (vereinfacht: 2 Rechtecke)
    drawRect(
        color   = Color.White,
        topLeft = Offset(cx - size * 0.06f * pulse, cy - size * 0.30f * pulse),
        size    = Size(size * 0.12f * pulse, size * 0.30f * pulse)
    )
    drawCircle(
        color  = Color.White,
        radius = size * 0.10f * pulse,
        center = Offset(cx, cy + size * 0.18f * pulse)
    )
}

/**
 * Zeichnet Mario: rote Mütze, blaue Latzhose, Gesicht, Schnurrbart.
 */
private fun DrawScope.drawMarioFigure(cx: Float, baseY: Float, size: Float, progress: Float) {
    val red   = Color(0xFFCC2200)
    val blue  = Color(0xFF3333CC)
    val skin  = Color(0xFFFFCC99)
    val brown = Color(0xFF7B3F00)
    val white = Color.White

    // Beine / Schuhe (abwechselnd für Lauf-Animation)
    val legSwing = sinf(progress * PI.toFloat() * 10f) * size * 0.18f
    // Linkes Bein
    drawRect(blue,
        topLeft = Offset(cx - size * 0.28f, baseY - size * 0.40f),
        size    = Size(size * 0.22f, size * 0.38f + legSwing))
    // Rechtes Bein
    drawRect(blue,
        topLeft = Offset(cx + size * 0.06f, baseY - size * 0.40f),
        size    = Size(size * 0.22f, size * 0.38f - legSwing))
    // Schuhe (dunkelbraun)
    drawRect(brown,
        topLeft = Offset(cx - size * 0.32f, baseY - size * 0.06f + legSwing),
        size    = Size(size * 0.30f, size * 0.12f))
    drawRect(brown,
        topLeft = Offset(cx + size * 0.02f, baseY - size * 0.06f - legSwing),
        size    = Size(size * 0.30f, size * 0.12f))

    // Körper / Latzhose
    drawRect(blue,
        topLeft = Offset(cx - size * 0.30f, baseY - size * 0.72f),
        size    = Size(size * 0.60f, size * 0.32f))
    // Latzhosen-Streifen (hellblau)
    drawRect(Color(0xFF5555EE).copy(alpha = 0.60f),
        topLeft = Offset(cx - size * 0.08f, baseY - size * 0.72f),
        size    = Size(size * 0.16f, size * 0.28f))

    // Arme
    drawRect(red,
        topLeft = Offset(cx - size * 0.48f, baseY - size * 0.80f),
        size    = Size(size * 0.18f, size * 0.26f))
    drawRect(red,
        topLeft = Offset(cx + size * 0.30f, baseY - size * 0.80f),
        size    = Size(size * 0.18f, size * 0.26f))
    // Hände
    drawCircle(skin, radius = size * 0.09f, center = Offset(cx - size * 0.39f, baseY - size * 0.56f))
    drawCircle(skin, radius = size * 0.09f, center = Offset(cx + size * 0.39f, baseY - size * 0.56f))

    // Kopf (Fleisch-farben)
    drawOval(skin,
        topLeft = Offset(cx - size * 0.28f, baseY - size * 1.10f),
        size    = Size(size * 0.56f, size * 0.48f))

    // Mütze (rot)
    val hatPath = Path()
    hatPath.moveTo(cx - size * 0.32f, baseY - size * 0.88f)
    hatPath.lineTo(cx - size * 0.26f, baseY - size * 1.14f)
    hatPath.lineTo(cx + size * 0.30f, baseY - size * 1.14f)
    hatPath.lineTo(cx + size * 0.36f, baseY - size * 0.88f)
    hatPath.close()
    drawPath(hatPath, red)
    // Mützen-Krempe
    drawRect(red,
        topLeft = Offset(cx - size * 0.36f, baseY - size * 0.92f),
        size    = Size(size * 0.72f, size * 0.08f))

    // Augen
    drawCircle(white, radius = size * 0.06f, center = Offset(cx - size * 0.08f, baseY - size * 0.96f))
    drawCircle(white, radius = size * 0.06f, center = Offset(cx + size * 0.12f, baseY - size * 0.96f))
    drawCircle(Color.Black, radius = size * 0.03f, center = Offset(cx - size * 0.07f, baseY - size * 0.96f))
    drawCircle(Color.Black, radius = size * 0.03f, center = Offset(cx + size * 0.13f, baseY - size * 0.96f))

    // Schnurrbart (3 braune Linien)
    for (i in -1..1) {
        drawLine(brown,
            start = Offset(cx + size * 0.00f, baseY - size * 0.85f + i * size * 0.025f),
            end   = Offset(cx + size * 0.28f, baseY - size * 0.85f + i * size * 0.025f),
            strokeWidth = size * 0.028f, cap = StrokeCap.Round)
    }
    // Nase
    drawCircle(skin.copy(alpha = 0.80f),
        radius = size * 0.07f,
        center = Offset(cx + size * 0.08f, baseY - size * 0.88f))
}

/**
 * Zeichnet Peach: pinkes Kleid, Krone, helles Haar.
 */
private fun DrawScope.drawMarioPeach(cx: Float, baseY: Float, size: Float, progress: Float) {
    val pink   = Color(0xFFFF99BB)
    val pink2  = Color(0xFFFFCCDD)
    val yellow = Color(0xFFFFDD44)
    val skin   = Color(0xFFFFCC99)
    val gold   = Color(0xFFFFAA00)
    val white  = Color.White

    // Kleid (breites A-Line, unten breiter)
    val dressPath = Path()
    dressPath.moveTo(cx - size * 0.22f, baseY - size * 0.65f)
    dressPath.cubicTo(
        cx - size * 0.55f, baseY - size * 0.50f,
        cx - size * 0.65f, baseY - size * 0.10f,
        cx - size * 0.50f, baseY
    )
    dressPath.lineTo(cx + size * 0.50f, baseY)
    dressPath.cubicTo(
        cx + size * 0.65f, baseY - size * 0.10f,
        cx + size * 0.55f, baseY - size * 0.50f,
        cx + size * 0.22f, baseY - size * 0.65f
    )
    dressPath.close()
    drawPath(dressPath, pink)

    // Kleid-Highlight
    val dressLight = Path()
    dressLight.moveTo(cx - size * 0.10f, baseY - size * 0.60f)
    dressLight.cubicTo(
        cx - size * 0.30f, baseY - size * 0.40f,
        cx - size * 0.35f, baseY - size * 0.10f,
        cx - size * 0.20f, baseY
    )
    dressLight.lineTo(cx + size * 0.20f, baseY)
    dressLight.cubicTo(
        cx + size * 0.35f, baseY - size * 0.10f,
        cx + size * 0.30f, baseY - size * 0.40f,
        cx + size * 0.10f, baseY - size * 0.60f
    )
    dressLight.close()
    drawPath(dressLight, pink2.copy(alpha = 0.45f))

    // Oberkörper
    drawOval(pink,
        topLeft = Offset(cx - size * 0.22f, baseY - size * 0.82f),
        size    = Size(size * 0.44f, size * 0.24f))

    // Arme
    drawLine(skin,
        start = Offset(cx - size * 0.22f, baseY - size * 0.72f),
        end   = Offset(cx - size * 0.42f, baseY - size * 0.58f),
        strokeWidth = size * 0.12f, cap = StrokeCap.Round)
    drawLine(skin,
        start = Offset(cx + size * 0.22f, baseY - size * 0.72f),
        end   = Offset(cx + size * 0.42f, baseY - size * 0.58f),
        strokeWidth = size * 0.12f, cap = StrokeCap.Round)

    // Kopf
    drawOval(skin,
        topLeft = Offset(cx - size * 0.24f, baseY - size * 1.16f),
        size    = Size(size * 0.48f, size * 0.42f))

    // Haar (hellgelb/blond, hinter Kopf)
    val hairPath = Path()
    hairPath.moveTo(cx - size * 0.28f, baseY - size * 1.10f)
    hairPath.cubicTo(
        cx - size * 0.50f, baseY - size * 1.20f,
        cx - size * 0.45f, baseY - size * 0.82f,
        cx - size * 0.28f, baseY - size * 0.80f
    )
    drawPath(hairPath, yellow.copy(alpha = 0.85f))
    val hairPath2 = Path()
    hairPath2.moveTo(cx + size * 0.28f, baseY - size * 1.10f)
    hairPath2.cubicTo(
        cx + size * 0.50f, baseY - size * 1.20f,
        cx + size * 0.45f, baseY - size * 0.82f,
        cx + size * 0.28f, baseY - size * 0.80f
    )
    drawPath(hairPath2, yellow.copy(alpha = 0.85f))
    // Stirn-Haar
    drawOval(yellow,
        topLeft = Offset(cx - size * 0.24f, baseY - size * 1.20f),
        size    = Size(size * 0.48f, size * 0.18f))

    // Krone (gold, 3 Zacken)
    val crownPath = Path()
    crownPath.moveTo(cx - size * 0.22f, baseY - size * 1.18f)
    crownPath.lineTo(cx - size * 0.22f, baseY - size * 1.30f)
    crownPath.lineTo(cx - size * 0.12f, baseY - size * 1.22f)
    crownPath.lineTo(cx,               baseY - size * 1.36f)
    crownPath.lineTo(cx + size * 0.12f, baseY - size * 1.22f)
    crownPath.lineTo(cx + size * 0.22f, baseY - size * 1.30f)
    crownPath.lineTo(cx + size * 0.22f, baseY - size * 1.18f)
    crownPath.close()
    drawPath(crownPath, gold)
    // Krone-Outline
    drawPath(crownPath, Color(0xFFCC8800), style = Stroke(width = 1.5f))
    // Krone-Edelstein
    drawCircle(Color(0xFFFF3366), radius = size * 0.05f, center = Offset(cx, baseY - size * 1.30f))

    // Augen
    drawOval(white,
        topLeft = Offset(cx - size * 0.18f, baseY - size * 1.05f),
        size    = Size(size * 0.10f, size * 0.09f))
    drawOval(white,
        topLeft = Offset(cx + size * 0.08f, baseY - size * 1.05f),
        size    = Size(size * 0.10f, size * 0.09f))
    drawCircle(Color(0xFF3333AA), radius = size * 0.035f, center = Offset(cx - size * 0.13f, baseY - size * 1.005f))
    drawCircle(Color(0xFF3333AA), radius = size * 0.035f, center = Offset(cx + size * 0.13f, baseY - size * 1.005f))

    // Wangen-Rouge
    drawCircle(Color(0xFFFF8888).copy(alpha = 0.35f), radius = size * 0.07f,
        center = Offset(cx - size * 0.18f, baseY - size * 0.97f))
    drawCircle(Color(0xFFFF8888).copy(alpha = 0.35f), radius = size * 0.07f,
        center = Offset(cx + size * 0.18f, baseY - size * 0.97f))

    // Herz über Peach (winkt Mario zu) – erscheint wenn Mario nahe genug
    if (progress > 0.60f) {
        val heartAlpha = ((progress - 0.60f) / 0.40f).coerceIn(0f, 1f)
        val heartSize  = size * 0.32f
        val heartCx    = cx + size * 0.50f
        val heartCy    = baseY - size * 1.50f
        val heartPath  = buildHeartPath(heartCx, heartCy, heartSize)
        drawPath(heartPath, Color(0xFFFF3366).copy(alpha = heartAlpha))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kotlin-Float-Mathematik-Hilfsfunktionen
// ─────────────────────────────────────────────────────────────────────────────

/** Berechnet den Sinus eines Float-Werts in Radiant und gibt ein Float zurück. */
private fun sinf(x: Float): Float = sin(x.toDouble()).toFloat()

/** Berechnet den Kosinus eines Float-Werts in Radiant und gibt ein Float zurück. */
private fun cosf(x: Float): Float = cos(x.toDouble()).toFloat()
