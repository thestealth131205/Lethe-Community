package com.securechat.app.ui.screens

import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.R
import com.securechat.app.data.network.PinballLeaderboardEntry
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ─── Virtuelles Spielfeld (Web-kompatible Koordinaten) ────────────────────────
private const val PB_W = 420f
private const val PB_H = 720f
private const val PB_WALL = 14f
private const val PB_PLUNGE_X = PB_W - 26f          // Mitte der Abschuss-Bahn
private const val PB_LANE_W = 34f
private const val PB_PF_R = PB_W - PB_LANE_W - PB_WALL  // rechte Spielfeldkante

// ─── Farben ──────────────────────────────────────────────────────────────────
private val PB_GOLD    = Color(0xFFCAA23F)
private val PB_GOLD_LT = Color(0xFFF0D68A)
private val PB_PLASMA   = Color(0xFF37C8FF)
private val PB_HEART_C  = Color(0xFFFF4D6D)

// ─── Datenklassen ─────────────────────────────────────────────────────────────
private class PbBall(
    var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f,
    val r: Float = 9f, var launched: Boolean = false
) {
    val trail = ArrayDeque<Offset>(); var guiding = false; var captured = false
    var stuckFrames = 0; var anchorX = x; var anchorY = y
}

private class PbBumper(val x: Float, val y: Float, val r: Float, val score: Int, val big: Boolean) {
    var flash = 0f; var hits = 0
}
private class PbOrb(val x: Float, val y: Float, val r: Float = 18f) { var charge = 0f }
private class PbHeart(val x: Float, val y: Float, val r: Float) { var alive = true; var hit = 0f }
private class PbSling(
    val ax: Float, val ay: Float,   // Aussenkante oben
    val bx: Float, val by: Float,   // Aussenkante unten (Richtung Flipper)
    val cx: Float, val cy: Float,   // Innenecke (kickt zur Mitte)
    val dir: Int
) {
    var flash = 0f
    val x: Float get() = (ax + bx + cx) / 3f
    val y: Float get() = (ay + by + cy) / 3f
}
private class PbPocket(val x: Float, val y: Float, val r: Float = 22f) {
    var capturedBall: PbBall? = null
    var captureTimer = 0
    var flash = 0f
    var cooldown = 0
}
private class PbParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color)

private class PbFlipper(
    val px: Float, val py: Float, val side: Int,
    val len: Float = 70f, restAng: Float = 0.42f, sweep: Float = 0.9f
) {
    val w = 13f
    val rest = if (side > 0) restAng else (Math.PI.toFloat() - restAng)
    val up   = if (side > 0) restAng - sweep else (Math.PI.toFloat() - restAng + sweep)
    var ang = rest; var prevAng = rest; var vel = 0f; var active = false
    fun tipX() = px + cos(ang) * len
    fun tipY() = py + sin(ang) * len
}

// ─── Spielzustand ─────────────────────────────────────────────────────────────
private class PbGs {
    var score = 0
    var highScore = 0
    var lives = 3
    var ball = 3
    var mult = 1
    var running = true
    var gameOver = false
    var relaunching = false
    var relaunchTimer = 0
    var surge = 0f
    var shake = 0f
    var nudgeCd = 0          // Cooldown für „Rütteln" (Doppel-Tipp gleiche Seite / beide Hebel)
    var heartRespawnTimer = 0
    var laserRing = 0f
    var laserCharge = 0f
    var message = ""
    var msgTimer = 0
    var plungerPower = 0f
    var frame = 0

    // Sound-Events (vom Loop gelesen + zurückgesetzt)
    var playHit = false
    var playNewBall = false
    var playGameOver = false

    val balls = mutableListOf<PbBall>()
    val particles = mutableListOf<PbParticle>()

    // enger zusammen + kürzer (Finger waren zu weit auseinander/zu lang)
    // grosse Flipper etwas tiefer (User-Markup: nach unten versetzt)
    // Flipper etwas aufeinander zu gerückt (waren zu weit auseinander), unabhaengig von den Dreiecken
    // Pivots leicht zueinander + Finger kürzer, sodass der Spitzen-Abstand ≈ 1,5× Kugeldurchmesser
    // (Kugel r=9 → Ø18 → Lücke ≈27px) beträgt. Kleiner Flipper (flipL2) bleibt unverändert.
    // Finger kürzer (User-Markup pink: beide unteren Flipperhebel zu lang) → len 86→72→66
    val flipL = PbFlipper(119f, 640f, 1, len = 66f, restAng = 0.45f, sweep = 0.95f)
    val flipR = PbFlipper(301f, 640f, -1, len = 66f, restAng = 0.45f, sweep = 0.95f)
    // kleiner oberer Flipper hoch in der linken Ausbuchtung (User-Markup: grosser Punkt), folgt dem linken Flipper
    val flipL2 = PbFlipper(42f, 305f, 1, len = 50f, restAng = 0.85f, sweep = 1.45f)

    val bumpers = listOf(
        // zentraler Mascot-Roboter (gross, leuchtend) + symmetrischer Bumper-Bogen oben
        PbBumper(PB_W * 0.50f, 150f, 32f, 5000, true),
        PbBumper(PB_W * 0.30f, 188f, 28f, 3000, true),
        PbBumper(PB_W * 0.70f, 188f, 28f, 3000, true),
        PbBumper(PB_W * 0.17f, 252f, 18f, 700, false),
        PbBumper(PB_W * 0.83f, 252f, 18f, 700, false),
    )
    val orbs = listOf(PbOrb(PB_W * 0.20f, 420f, 15f), PbOrb(PB_W * 0.80f, 420f, 15f))
    val hearts = listOf(
        PbHeart(PB_W * 0.26f, 372f, 16f),
        PbHeart(PB_W * 0.74f, 372f, 16f),
        PbHeart(PB_W * 0.50f, 322f, 18f),
    )
    val laserX = PB_W * 0.50f
    val laserY = 470f
    val laserR = 22f
    // perfekte Dreiecke (a=oben-aussen, b=unten-aussen Richtung Flipper, c=Innenspitze kickt zur Mitte)
    // niedrigere Höhe (≈50px statt 77px) + an die Screenshot-Dreiecke ausgerichtet, sitzen über den Flippern
    // an die pinken Dreiecke aus dem User-Markup (Bild) ausgerichtet: weiter zur Mitte gerückt,
    // höher (sitzen über den Flipper-Aussenenden), Form = schräges Dreieck (a=oben-aussen,
    // b=unten-aussen Richtung Flipper, c=Innenspitze kickt zur Mitte)
    // gedreht (Apex zeigt nach unten zum Flipper, wie rotes User-Markup) + beide etwas verkleinert
    val slings = listOf(
        PbSling(80f, 515f, 98f, 575f, 140f, 535f, 1),
        PbSling(328f, 515f, 310f, 575f, 268f, 535f, -1),
    )
    val pocket = PbPocket(318f, 428f, 22f)    // Einbuchtung rechts Mitte: fängt Ball, schießt nach oben-links
}

private fun initPinball(g: PbGs) {
    g.score = 0; g.lives = 3; g.ball = 3; g.mult = 1
    g.running = true; g.gameOver = false; g.relaunching = false; g.surge = 0f
    g.playHit = false; g.playGameOver = false
    g.balls.clear(); g.particles.clear()
    g.bumpers.forEach { it.flash = 0f; it.hits = 0 }
    g.orbs.forEach { it.charge = 0f }
    g.hearts.forEach { it.alive = true; it.hit = 0f }
    g.laserCharge = 0f
    g.pocket.capturedBall?.let { it.captured = false }
    g.pocket.capturedBall = null; g.pocket.captureTimer = 0; g.pocket.flash = 0f; g.pocket.cooldown = 0
    spawnWaitingBall(g)
    showPbMsg(g, "SPIELER 1 — BEREIT", 100)
}

private fun spawnWaitingBall(g: PbGs) {
    g.balls.add(PbBall(PB_PLUNGE_X, PB_H - 70f, 0f, 0f, launched = false))
    g.playNewBall = true
}

private fun tryLaunchBall(g: PbGs) {
    val b = g.balls.firstOrNull { !it.launched } ?: return
    b.launched = true
    b.guiding = true                                       // Kurven-Führung oben rechts aktiv
    val powerBoost = 1.12f + Random.nextFloat() * 0.10f    // zufällig 12%–22% mehr Power pro Schuss
    b.vy = -(13f + Random.nextFloat() * 4f) * powerBoost
    b.vx = Random.nextFloat() * 0.8f - 0.4f
}

// „Rütteln" am Spielfeld: kleiner Stoss auf alle Kugeln (löst festsitzende Kugel) + visueller Shake.
// dir: -1 = Tap links (Stoss nach rechts-oben), +1 = Tap rechts (Stoss nach links-oben).
private fun nudgePinball(g: PbGs, dir: Int) {
    if (g.nudgeCd > 0) return
    g.nudgeCd = 30                                          // ~0,5 s Pause zwischen Rüttlern
    g.shake = max(g.shake, 0.7f)
    g.balls.forEach { b ->
        if (b.launched && !b.captured) {
            b.vx += dir * 2.2f
            b.vy -= 2.6f
        }
    }
}

// Vertikaler Schubs: beide Flipperhebel doppelt gleichzeitig betätigt → kräftiger Stoss nach oben.
private fun nudgePinballVertical(g: PbGs) {
    if (g.nudgeCd > 0) return
    g.nudgeCd = 30
    g.shake = max(g.shake, 0.85f)
    g.balls.forEach { b ->
        if (b.launched && !b.captured) {
            b.vy -= 6.5f
            b.vx += (Random.nextFloat() * 2f - 1f) * 0.8f
        }
    }
}

private fun showPbMsg(g: PbGs, t: String, frames: Int) { g.message = t; g.msgTimer = frames }

private fun pbBurst(g: PbGs, x: Float, y: Float, color: Color, n: Int = 14, spd: Float = 4f) {
    repeat(n) {
        val a = Random.nextFloat() * (Math.PI.toFloat() * 2f)
        val s = 1f + Random.nextFloat() * (spd - 1f)
        g.particles.add(PbParticle(x, y, cos(a) * s, sin(a) * s, 1f, color))
    }
}

private fun pbAddScore(g: PbGs, n: Int): Int { val v = n * g.mult; g.score += v; return v }

// ─── Update-Schritt (≈60 fps, dt = 1) ─────────────────────────────────────────
private fun stepPinball(g: PbGs) {
    g.frame++
    if (!g.running) return

    updatePbFlipper(g.flipL); updatePbFlipper(g.flipL2); updatePbFlipper(g.flipR)

    g.bumpers.forEach { it.flash = max(0f, it.flash - 0.08f) }
    g.slings.forEach { it.flash = max(0f, it.flash - 0.12f) }
    g.hearts.forEach { it.hit = max(0f, it.hit - 0.08f) }
    g.laserRing = (g.laserRing + 0.04f) % (Math.PI.toFloat() * 2f)
    if (g.shake > 0f) g.shake = max(0f, g.shake - 0.08f)
    if (g.nudgeCd > 0) g.nudgeCd--
    if (g.msgTimer > 0) g.msgTimer--

    if (g.heartRespawnTimer > 0) {
        g.heartRespawnTimer--
        if (g.heartRespawnTimer == 0) g.hearts.forEach { it.alive = true }
    }

    // Pocket-Falle: gehalten Ball nach Countdown nach oben-links abschießen
    g.pocket.flash = max(0f, g.pocket.flash - 0.04f)
    if (g.pocket.cooldown > 0) g.pocket.cooldown--
    val pocketCap = g.pocket.capturedBall
    if (pocketCap != null) {
        g.pocket.captureTimer--
        pocketCap.x = g.pocket.x; pocketCap.y = g.pocket.y
        pocketCap.vx = 0f; pocketCap.vy = 0f
        if (g.pocket.captureTimer <= 0) {
            pocketCap.captured = false
            pocketCap.vx = -(7.5f + Random.nextFloat() * 1.5f)
            pocketCap.vy = -(13f + Random.nextFloat() * 2f)
            g.pocket.capturedBall = null
            g.pocket.flash = 1f
            g.pocket.cooldown = 90
            pbBurst(g, g.pocket.x, g.pocket.y, PB_PLASMA, 18, 7f)
            showPbMsg(g, "KANONE! +5000", 84)
            pbAddScore(g, 5000)
            g.shake = 0.6f
        }
    }

    // Plasma-Surge / Multiball
    if (g.orbs.all { it.charge >= 1f } && g.balls.size < 3 && g.surge == 0f) {
        g.surge = 1f
        showPbMsg(g, "PLASMA SURGE!!", 150)
        g.orbs.forEach { it.charge = 0f; pbBurst(g, it.x, it.y, PB_PLASMA, 26, 7f) }
        g.balls.add(PbBall(g.laserX - 20f, g.laserY, -2.5f, -6f, launched = true))
        g.balls.add(PbBall(g.laserX + 20f, g.laserY, 2.5f, -6f, launched = true))
        g.shake = 1f
    }
    if (g.surge > 0f) g.surge = max(0f, g.surge - 0.01f)

    for (i in g.balls.indices.reversed()) {
        val b = g.balls[i]
        stepPbBall(g, b)
        if (b.y > PB_H + 30f) {
            g.balls.removeAt(i)
            pbBurst(g, b.x, min(b.y, PB_H - 4f), PB_PLASMA, 10, 3f)
        }
    }

    // Ball verloren?
    if (g.balls.isEmpty() && g.running && !g.relaunching) {
        g.lives--; g.mult = 1
        if (g.lives <= 0) {
            g.running = false
            g.gameOver = true
            g.playGameOver = true
            if (g.score > g.highScore) g.highScore = g.score
        } else {
            g.ball = g.lives
            showPbMsg(g, "BALL VERLOREN", 90)
            g.relaunching = true
            g.relaunchTimer = 36
        }
    }
    if (g.relaunching) {
        g.relaunchTimer--
        if (g.relaunchTimer <= 0) { spawnWaitingBall(g); g.relaunching = false }
    }

    for (i in g.particles.indices.reversed()) {
        val p = g.particles[i]
        p.x += p.vx; p.y += p.vy; p.vy += 0.06f; p.life -= 0.03f
        if (p.life <= 0f) g.particles.removeAt(i)
    }

    if (g.score > g.highScore) g.highScore = g.score
}

private fun updatePbFlipper(f: PbFlipper) {
    val target = if (f.active) f.up else f.rest
    f.prevAng = f.ang
    f.ang += (target - f.ang) * 0.5f
    f.vel = f.ang - f.prevAng
}

private fun stepPbBall(g: PbGs, b: PbBall) {
    if (!b.launched) {                       // wartet auf Abschuss in der Bahn
        b.x = PB_PLUNGE_X; b.y = PB_H - 70f; b.vx = 0f; b.vy = 0f
        return
    }
    if (b.captured) return                   // Pocket hält den Ball fest
    b.vy += 0.20f
    // Kurven-Führung oben rechts: lenkt die abgeschossene Kugel sanft nach links ins Spielfeld
    if (b.guiding) {
        if (b.vy >= 0f || b.x < 282f) {
            b.guiding = false
        } else if (b.y < 165f) {
            val phi = 0.22f
            val cphi = cos(phi); val sphi = sin(phi)
            val nvx = b.vx * cphi + b.vy * sphi
            val nvy = -b.vx * sphi + b.vy * cphi
            b.vx = nvx; b.vy = nvy
        }
    }
    val sp = hypot(b.vx, b.vy); val maxV = 20f
    if (sp > maxV) { b.vx *= maxV / sp; b.vy *= maxV / sp }
    val steps = max(1, ceil(sp / 6f).toInt())
    for (s in 0 until steps) {
        b.x += b.vx / steps; b.y += b.vy / steps
        collidePbBall(g, b)
    }
    b.trail.addLast(Offset(b.x, b.y))
    if (b.trail.size > 8) b.trail.removeFirst()

    // Anti-Klemm: Kugel, die sich länger als 3 Sekunden nicht weiter als das Doppelte ihres
    // Durchmessers von der Stelle entfernt, an der der Timer zu laufen begann (egal wo auf dem
    // Feld — auch auf dem Flipper liegend), verschwindet und fällt neu von oben (leicht links der
    // Mitte, versetzt zum oberen Bumper) herunter — sonst könnte sie zwischen Bumper und Decke
    // hin- und herprallen und Punkte "farmen".
    val drift = hypot(b.x - b.anchorX, b.y - b.anchorY)
    val tolerance = 4f * b.r   // zweimal der Durchmesser (2 * 2r)
    if (drift > tolerance) {
        b.anchorX = b.x; b.anchorY = b.y
        b.stuckFrames = 0
    } else {
        b.stuckFrames++
        if (b.stuckFrames > 188) {   // ~3 Sekunden bei 16 ms Frame-Delay
            b.x = PB_W * 0.5f - 70f; b.y = PB_WALL + b.r
            b.vx = 0f; b.vy = 0f
            b.anchorX = b.x; b.anchorY = b.y
            b.stuckFrames = 0
            b.trail.clear()
            g.shake = max(g.shake, 0.4f)
        }
    }
}

private fun pbRefl(b: PbBall, nx: Float, ny: Float, restitution: Float, friction: Float = 1f): Boolean {
    val vn = b.vx * nx + b.vy * ny
    if (vn > 0) return false
    b.vx -= (1f + restitution) * vn * nx
    b.vy -= (1f + restitution) * vn * ny
    b.vx *= friction; b.vy *= friction
    return true
}

private fun pbSegHit(
    b: PbBall, x1: Float, y1: Float, x2: Float, y2: Float, r: Float,
    rest: Float, onHit: (() -> Unit)?
): Boolean {
    val ex = x2 - x1; val ey = y2 - y1; val len2 = ex * ex + ey * ey
    var t = if (len2 == 0f) 0f else ((b.x - x1) * ex + (b.y - y1) * ey) / len2
    t = t.coerceIn(0f, 1f)
    val cx = x1 + ex * t; val cy = y1 + ey * t
    val dx = b.x - cx; val dy = b.y - cy; val d = hypot(dx, dy)
    if (d < r) {
        val nx = dx / (if (d == 0f) 1f else d); val ny = dy / (if (d == 0f) 1f else d)
        b.x = cx + nx * r; b.y = cy + ny * r
        pbRefl(b, nx, ny, rest)
        onHit?.invoke()
        return true
    }
    return false
}

private fun collidePbBall(g: PbGs, b: PbBall) {
    val r = b.r
    val inLane = b.x > PB_PF_R - 2f && b.y > 110f
    if (b.y < PB_WALL + r) { b.y = PB_WALL + r; pbRefl(b, 0f, 1f, 0.5f) }
    if (b.x < PB_WALL + r) { b.x = PB_WALL + r; pbRefl(b, 1f, 0f, 0.6f) }
    if (inLane) {
        if (b.x > PB_W - PB_WALL - r) { b.x = PB_W - PB_WALL - r; pbRefl(b, -1f, 0f, 0.6f) }
        if (b.x < PB_PF_R + r && b.y > 150f) { b.x = PB_PF_R + r; pbRefl(b, 1f, 0f, 0.4f) }
    } else {
        if (b.x > PB_PF_R - r) { b.x = PB_PF_R - r; pbRefl(b, -1f, 0f, 0.55f) }
    }

    // Deflektor oben links (die im Hintergrund eingezeichnete Plattform/Schild nachgebildet) — Zelt-Form
    pbSegHit(b, 82f, 118f, 100f, 100f, r, 0.6f, null)
    pbSegHit(b, 100f, 100f, 118f, 116f, r, 0.6f, null)

    // Linke Blade/Roboter-Struktur exakt entlang der vom User rot eingezeichneten Außenkontur
    // nachgezeichnet (alte, völlig falsch sitzende Bande 40,212→70,300→26,404 entfernt). Zackenform
    // (Zähne zeigen ins Spielfeld, Täler bis an die linke Wand) verhindert, dass die Kugel links
    // durch die Struktur hindurch- bzw. dahinterläuft. Der kleine Flipper (flipL2, 42/305) sitzt in
    // der unteren Einbuchtung und schwingt nach rechts frei aus.
    pbSegHit(b, 16f, 156f, 58f, 172f, r, 0.4f, null)
    pbSegHit(b, 58f, 172f, 15f, 206f, r, 0.4f, null)
    pbSegHit(b, 15f, 206f, 46f, 258f, r, 0.4f, null)
    pbSegHit(b, 46f, 258f, 16f, 296f, r, 0.4f, null)
    pbSegHit(b, 16f, 296f, 40f, 330f, r, 0.4f, null)
    pbSegHit(b, 40f, 330f, 18f, 372f, r, 0.4f, null)
    pbSegHit(b, 18f, 372f, 22f, 418f, r, 0.4f, null)
    // Rechte Führungsbande am Spielfeldrand: schließt die Lücke oberhalb des rechten Orbs,
    // damit sich die Kugel dort nicht mehr einklemmt (User-Markup gelb).
    pbSegHit(b, 376f, 348f, 360f, 440f, r, 0.42f, null)

    // Flipper-Banden-Grafik (pinball_flipper_rail.png) angrenzend an die Flipperfinger:
    // undurchdringliche Innenkante entlang der diagonalen Schiene, leitet die Kugel auf den Finger.
    // Deutlich kleiner + leicht angehoben (User: Rails zu groß, Kugel klemmte am Übergang zum Hebel
    // bzw. wurde links eingesperrt). Innenkante ist eine saubere, leicht konvexe Diagonale ohne Tasche,
    // endet oberhalb des Pivots → Kugel rollt frei auf den Finger statt sich am Übergang zu verkeilen.
    // Links: diagonale Kante vom oberen Haken bis zum Band-Ende oberhalb des linken Pivots.
    // Synchron zum Render (ox=30, oy=533): +5px rechts / -4px hoch ggü. v10.4.99-Baseline.
    pbSegHit(b, 62f, 568f, 77f, 596f, r, 0.4f, null)
    pbSegHit(b, 77f, 596f, 96f, 625f, r, 0.4f, null)
    // Rechts: an der X-Achse (Feldmitte 210) gespiegelt + Korrektur-Offset,
    // synchron zum rOffX/rOffY der gespiegelten Render-Grafik (-5px links / -2px hoch ggü. Baseline).
    pbSegHit(b, 346f, 566f, 331f, 594f, r, 0.4f, null)
    pbSegHit(b, 331f, 594f, 312f, 623f, r, 0.4f, null)

    // Äußere Rinnen-Wände (Outlane Guides): Kugel kann links in die Rinne fallen,
    // eine kurze Bodenführung lenkt sie dann zum Flipper-Pivot hin.
    // Links: kurze Führung am Boden der linken Außenrinne → Kugel rollt zum Flipper-Hebel.
    pbSegHit(b, 16f, 638f, 100f, 655f, r, 0.4f, null)
    // Rechts: rechter Pivot diagonal zur rechten Spielfeldkante → kein Durchfall rechts neben dem Flipper.
    pbSegHit(b, 304f, 638f, 372f, 598f, r, 0.45f, null)

    // Bumper
    for (bp in g.bumpers) {
        val dx = b.x - bp.x; val dy = b.y - bp.y; val d = hypot(dx, dy); val minD = r + bp.r
        if (d < minD) {
            val nx = if (d == 0f) 0f else dx / d; val ny = if (d == 0f) -1f else dy / d
            b.x = bp.x + nx * minD; b.y = bp.y + ny * minD
            pbRefl(b, nx, ny, 0.6f)
            val k = if (bp.big) 5.5f else 4.0f
            b.vx += nx * k; b.vy += ny * k
            bp.flash = 1f; bp.hits++
            g.playHit = true
            pbAddScore(g, bp.score)
            pbBurst(g, bp.x, bp.y, if (bp.big) PB_PLASMA else PB_GOLD, 12, 5f)
            if (bp.hits % 10 == 0) {
                g.mult = min(8, g.mult + 1)
                showPbMsg(g, "MULTIPLIER x${g.mult}", 84)
            }
        }
    }

    // Slingshots — kickende Innenkante (a→c) + solide Unterkante (c→b)
    for (sl in g.slings) {
        val kick: () -> Unit = {
            val nx = sl.dir.toFloat(); val ny = -0.6f; val nl = hypot(nx, ny)
            b.vx += nx / nl * 5.5f; b.vy += ny / nl * 5.5f; sl.flash = 1f
            g.playHit = true
            pbAddScore(g, 300)
            pbBurst(g, sl.x, sl.y - 10f, PB_PLASMA, 8, 4f)
        }
        pbSegHit(b, sl.ax, sl.ay, sl.cx, sl.cy, r, 0.5f, kick)
        pbSegHit(b, sl.cx, sl.cy, sl.bx, sl.by, r, 0.5f, null)
        // Außenkante a→b ebenfalls solide → Dreieck ist ein voller Körper, die Kugel kann nicht
        // mehr in das Innere eindringen und sich in der V-Tasche verkeilen (Fang-Glitch behoben)
        pbSegHit(b, sl.ax, sl.ay, sl.bx, sl.by, r, 0.4f, null)
    }

    // Plasma-Orbs (Aufladung)
    for (o in g.orbs) {
        val dx = b.x - o.x; val dy = b.y - o.y; val d = hypot(dx, dy); val minD = r + o.r
        if (d < minD) {
            val nx = if (d == 0f) 0f else dx / d; val ny = if (d == 0f) -1f else dy / d
            b.x = o.x + nx * minD; b.y = o.y + ny * minD; pbRefl(b, nx, ny, 0.5f)
            b.vx += nx * 3f; b.vy += ny * 3f
            if (o.charge < 1f) {
                o.charge = min(1f, o.charge + 0.34f)
                g.playHit = true
                pbAddScore(g, 1500)
                pbBurst(g, o.x, o.y, PB_PLASMA, 10, 5f)
                if (o.charge >= 1f) showPbMsg(g, "ORB GELADEN", 72)
            }
        }
    }

    // Herz-Targets
    for (h in g.hearts) {
        if (!h.alive) continue
        val dx = b.x - h.x; val dy = b.y - h.y; val d = hypot(dx, dy); val minD = r + h.r
        if (d < minD) {
            val nx = if (d == 0f) 0f else dx / d; val ny = if (d == 0f) -1f else dy / d
            b.x = h.x + nx * minD; b.y = h.y + ny * minD; pbRefl(b, nx, ny, 0.55f)
            h.alive = false; h.hit = 1f
            g.playHit = true
            pbAddScore(g, 4000)
            pbBurst(g, h.x, h.y, PB_HEART_C, 18, 6f); g.shake = 0.6f
            if (g.hearts.all { !it.alive }) {
                showPbMsg(g, "MEMORY WIPED +20000", 120)
                pbAddScore(g, 20000)
                g.heartRespawnTimer = 90
            }
        }
    }

    // zentrales Laser-Target
    run {
        val dx = b.x - g.laserX; val dy = b.y - g.laserY; val d = hypot(dx, dy); val minD = r + g.laserR
        if (d < minD) {
            val nx = if (d == 0f) 0f else dx / d; val ny = if (d == 0f) -1f else dy / d
            b.x = g.laserX + nx * minD; b.y = g.laserY + ny * minD; pbRefl(b, nx, ny, 0.7f)
            b.vx += nx * 4f; b.vy += ny * 4f
            g.laserCharge = min(1f, g.laserCharge + 0.2f)
            g.playHit = true
            pbAddScore(g, 2500)
            pbBurst(g, g.laserX, g.laserY, PB_GOLD, 12, 6f)
            if (g.laserCharge >= 1f) {
                g.laserCharge = 0f; pbAddScore(g, 10000)
                showPbMsg(g, "LASER LOCK +10000", 96); g.shake = 0.8f
            }
        }
    }

    // Pocket-Falle (rechts Mitte): Kugel fällt von oben rein → wird gehalten → Abschuss oben-links
    val p = g.pocket
    if (!b.captured && p.capturedBall == null && p.cooldown == 0) {
        val pdx = b.x - p.x; val pdy = b.y - p.y; val pd = hypot(pdx, pdy)
        val pMinD = r + p.r
        if (pd < pMinD) {
            if (b.vy > 0.5f) {   // fällt von oben rein → einfangen
                b.captured = true; b.x = p.x; b.y = p.y; b.vx = 0f; b.vy = 0f
                p.capturedBall = b; p.captureTimer = 65; p.flash = 1f
                pbAddScore(g, 3000)
                pbBurst(g, p.x, p.y, PB_GOLD, 10, 4f)
                showPbMsg(g, "POCKET! +3000", 72)
            } else {              // von unten/der Seite → abprallen
                val pnx = if (pd == 0f) 0f else pdx / pd
                val pny = if (pd == 0f) -1f else pdy / pd
                b.x = p.x + pnx * pMinD; b.y = p.y + pny * pMinD
                pbRefl(b, pnx, pny, 0.5f)
            }
        }
    }

    flipperCollide(b, g.flipL)
    flipperCollide(b, g.flipL2)
    flipperCollide(b, g.flipR)
}

private fun flipperCollide(b: PbBall, f: PbFlipper) {
    if (resolveFlipperLine(b, f, f.ang)) return
    // Anti-Tunnel: beim schnellen Hochschlagen wandert die Spitze pro Frame weiter als der
    // Kollisionsradius → zusätzlich die vorherige Lage prüfen, damit die Kugel nicht durchfliegt.
    if (f.active && kotlin.math.abs(f.ang - f.prevAng) > 0.02f) {
        resolveFlipperLine(b, f, f.prevAng)
    }
}

/** Kollidiert die Kugel mit dem Flipper als Kapsel (Linie px,py→Spitze bei [ang], Radius w). */
private fun resolveFlipperLine(b: PbBall, f: PbFlipper, ang: Float): Boolean {
    val tipX = f.px + cos(ang) * f.len
    val tipY = f.py + sin(ang) * f.len
    val ex = tipX - f.px; val ey = tipY - f.py; val len2 = ex * ex + ey * ey
    var t = if (len2 == 0f) 0f else ((b.x - f.px) * ex + (b.y - f.py) * ey) / len2
    t = t.coerceIn(0f, 1f)
    val cx = f.px + ex * t; val cy = f.py + ey * t
    val dx = b.x - cx; val dy = b.y - cy; val d = hypot(dx, dy); val minD = b.r + f.w
    if (d >= minD) return false
    var nx = if (d == 0f) 0f else dx / d
    var ny = if (d == 0f) -1f else dy / d
    // Beim aktiven Hochschlagen die Kugel immer auf die Oberseite (Spielfeld) drücken,
    // nie nach unten durch den Flipper hindurch.
    if (f.active && ny > 0f) { nx = -nx; ny = -ny }
    b.x = cx + nx * minD; b.y = cy + ny * minD
    pbRefl(b, nx, ny, 0.35f)
    // Geschwindigkeit des rotierenden Flipper-Punktes auf die Kugel übertragen (korrekter Stoß).
    if (kotlin.math.abs(f.vel) > 0.0005f) {
        val dist = t * f.len
        val ptVx = -sin(ang) * f.vel * dist
        val ptVy =  cos(ang) * f.vel * dist
        val gain = 1.3f
        b.vx += ptVx * gain
        b.vy += ptVy * gain
    }
    return true
}

// ─── Grafik-Assets ────────────────────────────────────────────────────────────
private class PbSprites(
    val bg: ImageBitmap?,
    val bumper: ImageBitmap?,
    val bumperLit: ImageBitmap?,
    val bumperSmall: ImageBitmap?,
    val orbOff: ImageBitmap?,
    val orbOn: ImageBitmap?,
    val heart: ImageBitmap?,
    val ball: ImageBitmap?,
    val laser: ImageBitmap?,
    val flipperRail: ImageBitmap?
)

/** Zeichnet ein Sprite zentriert auf (cx,cy) mit Zielhöhe [h]px (Seitenverhältnis erhalten). */
private fun DrawScope.drawSprite(img: ImageBitmap, cx: Float, cy: Float, h: Float, alpha: Float = 1f) {
    val ar = img.width.toFloat() / img.height.toFloat()
    val w = h * ar
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha
    )
}

// ─── Render-Helfer ────────────────────────────────────────────────────────────
private fun DrawScope.pbGlow(cx: Float, cy: Float, r: Float, color: Color, intensity: Float = 0.55f) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = intensity),
            0.45f to color.copy(alpha = intensity * 0.4f),
            1f to Color.Transparent,
            center = Offset(cx, cy), radius = r
        ),
        radius = r, center = Offset(cx, cy)
    )
}

private fun DrawScope.pbLightning(
    x1: Float, y1: Float, x2: Float, y2: Float,
    seed: Int, color: Color, width: Float = 2f, displace: Float = 12f, segs: Int = 7
) {
    val rnd = Random(seed)
    val dx = x2 - x1; val dy = y2 - y1; val len = hypot(dx, dy)
    if (len < 1f) return
    val nx = -dy / len; val ny = dx / len
    val path = Path().apply {
        moveTo(x1, y1)
        for (i in 1 until segs) {
            val t = i.toFloat() / segs
            val off = (rnd.nextFloat() * 2f - 1f) * displace * (1f - kotlin.math.abs(t - 0.5f))
            lineTo(x1 + dx * t + nx * off, y1 + dy * t + ny * off)
        }
        lineTo(x2, y2)
    }
    drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = width * 4f, cap = StrokeCap.Round))
    drawPath(path, color.copy(alpha = 0.85f), style = Stroke(width = width, cap = StrokeCap.Round))
    drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = width * 0.4f, cap = StrokeCap.Round))
}

// ─── Rendering ────────────────────────────────────────────────────────────────
private fun DrawScope.drawPinball(g: PbGs, spr: PbSprites) {
    // Spielfeld-Hintergrund: Schaltkreis-Grafik (Fallback: Marineblau-Gradient + Raster)
    if (spr.bg != null) {
        drawImage(
            image = spr.bg,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(spr.bg.width, spr.bg.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(PB_W.toInt(), PB_H.toInt())
        )
        // leichte Abdunklung für Kontrast der Spielobjekte
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0x330A1A34), 0.5f to Color(0x110A1A34), 1f to Color(0x66060E1E)
            ),
            size = Size(PB_W, PB_H)
        )
    } else {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF0E2240), 0.45f to Color(0xFF0A1A34), 1f to Color(0xFF060E1E)
            ),
            size = Size(PB_W, PB_H)
        )
        var gx = 30f
        while (gx < PB_W) { drawLine(Color(0x14385C88), Offset(gx, 0f), Offset(gx, PB_H), 1f); gx += 42f }
        var gy = 30f
        while (gy < PB_H) { drawLine(Color(0x10385C88), Offset(0f, gy), Offset(PB_W, gy), 1f); gy += 42f }
    }
    // Eck-Glanz + zentrale Plasma-Atmosphäre (wie im Mockup)
    pbGlow(PB_W * 0.5f, 220f, 260f, Color(0xFF12365F), 0.5f)
    pbGlow(PB_W * 0.5f, 165f, 150f, PB_PLASMA, 0.10f + (sin(g.frame * 0.08f) * 0.5f + 0.5f) * 0.06f)

    // Spielfeld-Titel
    drawPbTitle()

    // Abschuss-Bahn
    drawRect(Color(0xB30A1428), topLeft = Offset(PB_PF_R, 150f),
        size = Size(PB_W - PB_WALL - PB_PF_R, PB_H - 150f))
    drawLine(PB_GOLD.copy(alpha = 0.5f), Offset(PB_PF_R, 150f), Offset(PB_PF_R, PB_H), 2f)

    // Deflektor oben links (im Hintergrund eingezeichnetes Schild nachgezeichnet) — Zelt-Form
    val deflPath = Path().apply {
        moveTo(82f, 118f); lineTo(100f, 100f); lineTo(118f, 116f)
    }
    drawPath(deflPath, Color(0xFF3D2800).copy(alpha = 0.6f), style = Stroke(width = 7f, cap = StrokeCap.Round))
    drawPath(deflPath, PB_GOLD.copy(alpha = 0.9f), style = Stroke(width = 4f, cap = StrokeCap.Round))
    drawPath(deflPath, PB_GOLD_LT.copy(alpha = 0.6f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))

    // Chrome Kugelführungs-Schiene oben rechts (U-Form, passend zum Hintergrunddesign)
    val guidePath = Path().apply {
        moveTo(PB_PF_R - 2f, 155f)
        cubicTo(PB_PF_R - 2f, 82f, 300f, 28f, 350f, 18f)
        cubicTo(380f, 12f, 404f, 26f, PB_W - PB_WALL + 1f, 56f)
        lineTo(PB_W - PB_WALL + 1f, 124f)
    }
    // Chrome-Rohr: Schatten → Körper → Glanz → Highlight
    drawPath(guidePath, Color(0x99060E1E), style = Stroke(width = 20f, cap = StrokeCap.Round))
    drawPath(guidePath, Color(0xCC6E8494), style = Stroke(width = 14f, cap = StrokeCap.Round))
    drawPath(guidePath, Color(0xCCA8BDC8), style = Stroke(width = 10f, cap = StrokeCap.Round))
    drawPath(guidePath, Color(0xAAD0DDE5), style = Stroke(width = 6f, cap = StrokeCap.Round))
    drawPath(guidePath, Color(0x88F2F7FA), style = Stroke(width = 2.2f, cap = StrokeCap.Round))

    // Linke Blade/Roboter-Struktur exakt entlang der rot eingezeichneten Außenkontur nachgezeichnet
    // (Zickzack, Zähne zeigen ins Spielfeld). Synchron zur Physik-Bande in collidePbBall.
    val leftBandePath = Path().apply {
        moveTo(16f, 156f)
        lineTo(58f, 172f)
        lineTo(15f, 206f)
        lineTo(46f, 258f)
        lineTo(16f, 296f)
        lineTo(40f, 330f)
        lineTo(18f, 372f)
        lineTo(22f, 418f)
    }
    drawPath(leftBandePath, PB_GOLD.copy(alpha = 0.55f), style = Stroke(width = 6.5f, cap = StrokeCap.Round))
    drawPath(leftBandePath, PB_PLASMA.copy(alpha = 0.2f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

    // Rechte Führungsbande am Spielfeldrand (verhindert Einklemmen oberhalb des rechten Orbs)
    drawLine(PB_GOLD.copy(alpha = 0.55f), Offset(376f, 348f), Offset(360f, 440f), 6.5f, cap = StrokeCap.Round)
    drawLine(PB_PLASMA.copy(alpha = 0.2f), Offset(376f, 348f), Offset(360f, 440f), 2.5f, cap = StrokeCap.Round)

    // Äußere Rinnen-Wände (Outlane Guides) — synchron zur Physik in collidePbBall.
    // Links: kurze Bodenführung am Ende der linken Außenrinne → leitet Kugel zum Flipper-Hebel.
    drawLine(PB_GOLD.copy(alpha = 0.55f), Offset(16f, 638f), Offset(100f, 655f), 6.5f, cap = StrokeCap.Round)
    drawLine(PB_PLASMA.copy(alpha = 0.2f), Offset(16f, 638f), Offset(100f, 655f), 2.5f, cap = StrokeCap.Round)
    // Rechts: rechter Flipper-Pivot → rechte Spielfeldkante (schließt rechte Außenrinne).
    drawLine(PB_GOLD.copy(alpha = 0.55f), Offset(304f, 638f), Offset(372f, 598f), 6.5f, cap = StrokeCap.Round)
    drawLine(PB_PLASMA.copy(alpha = 0.2f), Offset(304f, 638f), Offset(372f, 598f), 2.5f, cap = StrokeCap.Round)

    // Flipper-Banden-Grafik (pinball_flipper_rail.png) angrenzend an die Flipperfinger.
    // Links normal, rechts an der X-Achse (Feldmitte 210) gespiegelt. Physik dazu in collidePbBall.
    spr.flipperRail?.let { rail ->
        // näher an die Finger (nach innen) + etwas tiefer (User-Markup) — Seitenverhältnis ~0.74 beibehalten
        // Linke Rail minimal nach oben (oy 545→537). rh unverändert.
        val ox = 30f; val oy = 533f; val rw = 84f; val rh = 115f
        // Rechte Rail etwas schmaler (User: Breite um 10f verringern).
        val rwR = rw - 10f
        // Korrektur-Offset NUR für das rechte (gespiegelte) Band (Screen-Koordinaten):
        // rOffX > 0 = nach LINKS, rOffY > 0 = nach OBEN.
        // rOffY von 12→4 angepasst, da oy um 8 nach oben rückte → rechte Rail bleibt bei y=533.
        val rOffX = 12f; val rOffY = 2f
        drawImage(
            image = rail,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(rail.width, rail.height),
            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
            dstSize = IntSize(rw.roundToInt(), rh.roundToInt())
        )
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(PB_W / 2f, 0f)) {
            // Im gespiegelten Canvas: prescale-x vergrößern → Screen nach LINKS; y abziehen → nach OBEN.
            drawImage(
                image = rail,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(rail.width, rail.height),
                dstOffset = IntOffset((ox + rOffX).roundToInt(), (oy - rOffY).roundToInt()),
                dstSize = IntSize(rwR.roundToInt(), rh.roundToInt())
            )
        }
    }

    // dekorative Plasma-Lichtbögen zwischen den grossen Bumpern
    val big = g.bumpers.filter { it.big }
    if (big.size >= 3) {
        if (g.frame % 6 < 4) {
            pbLightning(big[0].x, big[0].y, big[1].x, big[1].y, g.frame / 2, PB_PLASMA, 2f, 14f)
            pbLightning(big[0].x, big[0].y, big[2].x, big[2].y, g.frame / 2 + 7, PB_PLASMA, 1.6f, 12f)
            pbLightning(big[1].x, big[1].y, big[2].x, big[2].y, g.frame / 2 + 13, PB_PLASMA, 1.6f, 12f)
        }
    }
    // zentraler Plasma-Strudel um das Laser-Target (wie im Mockup)
    pbGlow(g.laserX, g.laserY - 4f, 70f, PB_PLASMA, 0.28f + (sin(g.frame * 0.15f) * 0.5f + 0.5f) * 0.12f)
    if (g.frame % 4 < 2) {
        repeat(3) { k ->
            val a = g.frame * 0.2f + k * 2.1f
            pbLightning(
                g.laserX, g.laserY,
                g.laserX + cos(a) * 56f, g.laserY + sin(a) * 56f,
                g.frame + k * 31, PB_PLASMA, 1.4f, 8f, 5
            )
        }
    }

    // Slingshots
    for (sl in g.slings) {
        val path = Path().apply {
            moveTo(sl.ax, sl.ay); lineTo(sl.cx, sl.cy); lineTo(sl.bx, sl.by); close()
        }
        if (sl.flash > 0f) pbGlow(sl.x, sl.y - 8f, 50f, PB_PLASMA, sl.flash * 0.6f)
        drawPath(path, if (sl.flash > 0f) PB_PLASMA.copy(alpha = 0.4f + sl.flash * 0.5f) else Color(0xB31E3C64))
        drawPath(path, if (sl.flash > 0f) Color(0xFF9FE6FF) else PB_GOLD, style = Stroke(width = 3f))
    }

    // Pocket-Falle (rechts Mitte) — Schüssel-Form, öffnet nach oben-links
    run {
        val p = g.pocket
        val pFlash = p.flash
        pbGlow(p.x, p.y, p.r * 2.8f, PB_GOLD, 0.22f + pFlash * 0.55f)
        // 270°-Bogen: startAngle=-135° (oben-links), sweepAngle=270° → öffnet nach oben-links
        drawArc(
            color = if (pFlash > 0.2f) PB_GOLD_LT.copy(alpha = 0.65f + pFlash * 0.3f) else PB_GOLD.copy(alpha = 0.65f),
            startAngle = -135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(p.x - p.r, p.y - p.r),
            size = Size(p.r * 2f, p.r * 2f),
            style = Stroke(width = 4.5f)
        )
        // Plasma-Lade-Ring wenn Ball eingeschlossen
        if (p.capturedBall != null && p.captureTimer > 0) {
            val prog = 1f - p.captureTimer / 65f
            drawArc(
                color = PB_PLASMA.copy(alpha = 0.7f * prog),
                startAngle = -90f,
                sweepAngle = 360f * prog,
                useCenter = false,
                topLeft = Offset(p.x - p.r * 1.5f, p.y - p.r * 1.5f),
                size = Size(p.r * 3f, p.r * 3f),
                style = Stroke(width = 3f)
            )
        }
        // Pfeil nach oben-links als Hinweis auf Abschussrichtung
        drawLine(PB_GOLD.copy(alpha = 0.45f + pFlash * 0.3f),
            Offset(p.x, p.y - p.r - 4f), Offset(p.x - p.r * 1.6f, p.y - p.r * 2.2f), 2f)
        drawLine(PB_GOLD.copy(alpha = 0.35f + pFlash * 0.3f),
            Offset(p.x - p.r * 1.6f, p.y - p.r * 2.2f), Offset(p.x - p.r * 0.9f, p.y - p.r * 2.2f), 2f)
        drawLine(PB_GOLD.copy(alpha = 0.35f + pFlash * 0.3f),
            Offset(p.x - p.r * 1.6f, p.y - p.r * 2.2f), Offset(p.x - p.r * 1.6f, p.y - p.r * 1.4f), 2f)
    }

    // Orbs (Plasma-Containment) — Grafik-Sprite, Glow/Blitze prozedural
    for (o in g.orbs) {
        val pulse = sin(g.frame * 0.12f + o.x) * 0.5f + 0.5f
        if (o.charge > 0f) pbGlow(o.x, o.y, o.r * (2.4f + pulse), PB_PLASMA, 0.35f + o.charge * 0.25f)
        val orbImg = if (o.charge > 0f) spr.orbOn else spr.orbOff
        if (orbImg != null) {
            drawSprite(orbImg, o.x, o.y, o.r * 3.4f)
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFF1B4A63), 1f to Color(0xFF0A1E2C),
                    center = Offset(o.x - o.r * 0.3f, o.y - o.r * 0.3f), radius = o.r
                ),
                radius = o.r, center = Offset(o.x, o.y)
            )
            drawCircle(PB_GOLD, o.r + 6f, Offset(o.x, o.y), style = Stroke(width = 3f))
        }
        if (o.charge >= 1f && g.frame % 5 < 2) {
            repeat(2) { k ->
                val a = g.frame * 0.3f + k * 3.1f
                pbLightning(o.x, o.y, o.x + cos(a) * o.r, o.y + sin(a) * o.r,
                    g.frame + o.x.toInt() + k, Color(0xFFEAFFFF), 1f, 5f, 4)
            }
        }
    }

    // Herzen (Memory-Targets) — Grafik-Sprite
    for (h in g.hearts) {
        if (!h.alive) {
            if (spr.heart != null) drawSprite(spr.heart, h.x, h.y, h.r * 3.0f, alpha = 0.2f)
            else drawCircle(Color(0xFF5A2030).copy(alpha = 0.25f), h.r, Offset(h.x, h.y), style = Stroke(width = 2f))
            continue
        }
        val beat = sin(g.frame * 0.18f + h.x) * 0.5f + 0.5f
        pbGlow(h.x, h.y, h.r * (1.8f + beat * 0.5f + h.hit), PB_HEART_C, 0.45f + h.hit * 0.4f)
        if (spr.heart != null) drawSprite(spr.heart, h.x, h.y, h.r * 3.0f + h.hit * 6f + beat * 2f)
        else drawHeart(h.x, h.y, h.r + h.hit * 4f + beat * 1.5f, PB_HEART_C)
    }

    // Laser-Target — Grafik-Sprite
    pbGlow(g.laserX, g.laserY, g.laserR * 1.8f, PB_GOLD, 0.4f + g.laserCharge * 0.4f)
    if (spr.laser != null) {
        drawSprite(spr.laser, g.laserX, g.laserY, g.laserR * 2.6f * (0.98f + (sin(g.frame * 0.2f) * 0.5f + 0.5f) * 0.05f))
    } else {
        drawCircle(PB_GOLD, g.laserR, Offset(g.laserX, g.laserY), style = Stroke(width = 3f))
        drawCircle(PB_GOLD.copy(alpha = 0.6f), g.laserR * 0.72f, Offset(g.laserX, g.laserY), style = Stroke(width = 2f))
    }

    // Bumper (Roboter-Köpfe) — Grafik-Sprite
    for (bp in g.bumpers) {
        // Glow-Halo
        pbGlow(bp.x, bp.y, bp.r * (1.7f + bp.flash * 1.2f),
            if (bp.big) PB_PLASMA else PB_GOLD, 0.3f + bp.flash * 0.5f)
        val img = if (bp.big) {
            if (bp.flash > 0f) spr.bumperLit ?: spr.bumper else spr.bumper
        } else spr.bumperSmall
        if (img != null) {
            // Roboter zappelt leicht beim Treffer
            val bounce = bp.flash * 3f
            drawSprite(img, bp.x, bp.y - bounce, bp.r * (if (bp.big) 3.0f else 2.1f) + bp.flash * 4f)
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to (if (bp.flash > 0f) Color(0xFFD6F4FF) else Color(0xFF2E7494)),
                    0.7f to (if (bp.flash > 0f) Color(0xFF1FA0D6) else Color(0xFF143A4E)),
                    1f to Color(0xFF0A1C28),
                    center = Offset(bp.x - bp.r * 0.3f, bp.y - bp.r * 0.35f), radius = bp.r
                ),
                radius = bp.r, center = Offset(bp.x, bp.y)
            )
            drawCircle(PB_GOLD, bp.r, Offset(bp.x, bp.y), style = Stroke(width = 3.5f))
        }
    }

    // Flipper
    drawFlipper(g.flipL); drawFlipper(g.flipL2); drawFlipper(g.flipR)

    // Bälle
    for (b in g.balls) {
        for (i in b.trail.indices) {
            val tpt = b.trail[i]; val a = i.toFloat() / b.trail.size * 0.4f
            drawCircle(Color(0xFFB4DCFF).copy(alpha = a), b.r * (0.4f + a), tpt)
        }
        pbGlow(b.x, b.y, b.r * 2.2f, Color(0xFFCFE6FF), 0.4f)
        if (spr.ball != null) {
            drawSprite(spr.ball, b.x, b.y, b.r * 2.6f)
        } else {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White, 0.4f to Color(0xFFD0DEEC), 1f to Color(0xFF44566C),
                    center = Offset(b.x - 3f, b.y - 3f), radius = b.r
                ),
                radius = b.r, center = Offset(b.x, b.y)
            )
            drawCircle(Color.White.copy(alpha = 0.9f), 2f, Offset(b.x - 3f, b.y - 3f))
        }
    }

    // Partikel
    for (p in g.particles) {
        drawCircle(p.color.copy(alpha = (p.life * 0.4f).coerceIn(0f, 0.5f)), 4f * p.life + 1f, Offset(p.x, p.y))
        drawCircle(p.color.copy(alpha = p.life.coerceIn(0f, 1f)), 2.5f * p.life + 0.5f, Offset(p.x, p.y))
    }

    // goldener Doppel-Rahmen
    drawRect(PB_GOLD, topLeft = Offset(4f, 4f), size = Size(PB_W - 8f, PB_H - 8f), style = Stroke(width = 6f))
    drawRect(PB_GOLD_LT.copy(alpha = 0.5f), topLeft = Offset(9f, 9f),
        size = Size(PB_W - 18f, PB_H - 18f), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawPbTitle() {
    val cx = PB_W / 2f
    drawContext.canvas.nativeCanvas.apply {
        val title = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(235, 240, 214, 138)   // gold
            textSize = 36f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD
            )
            letterSpacing = 0.22f
            setShadowLayer(9f, 0f, 0f, android.graphics.Color.argb(170, 55, 200, 255))
        }
        drawText("LETHE", cx, 50f, title)
        val sub = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(200, 159, 230, 255)   // plasma
            textSize = 12f
            textAlign = android.graphics.Paint.Align.CENTER
            letterSpacing = 0.45f
        }
        drawText("IN VERGESSEN", cx, 67f, sub)
    }
}

private fun DrawScope.drawHeart(x: Float, y: Float, r: Float, color: Color) {
    val s = r / 16f
    val path = Path().apply {
        moveTo(x, y + 6 * s)
        cubicTo(x, y + 2 * s, x - 3 * s, y - 8 * s, x - 10 * s, y - 8 * s)
        cubicTo(x - 20 * s, y - 8 * s, x - 20 * s, y + 4 * s, x - 10 * s, y + 10 * s)
        cubicTo(x - 5 * s, y + 14 * s, x, y + 16 * s, x, y + 18 * s)
        cubicTo(x, y + 16 * s, x + 5 * s, y + 14 * s, x + 10 * s, y + 10 * s)
        cubicTo(x + 20 * s, y + 4 * s, x + 20 * s, y - 8 * s, x + 10 * s, y - 8 * s)
        cubicTo(x + 3 * s, y - 8 * s, x, y + 2 * s, x, y + 6 * s)
        close()
    }
    drawPath(path, color)
    // innerer Glanz
    drawPath(path, Brush.radialGradient(
        0f to Color.White.copy(alpha = 0.6f), 0.6f to Color.Transparent,
        center = Offset(x - r * 0.25f, y - r * 0.2f), radius = r
    ))
    drawPath(path, Color(0xFFFFD0DA), style = Stroke(width = 1.5f))
}

private fun DrawScope.drawFlipper(f: PbFlipper) {
    pbGlow(f.tipX(), f.tipY(), f.w * 3f, PB_GOLD, 0.35f)
    drawLine(Color(0xFF8A6A22), Offset(f.px, f.py), Offset(f.tipX(), f.tipY()), f.w * 2f, cap = StrokeCap.Round)
    drawLine(PB_GOLD, Offset(f.px, f.py), Offset(f.tipX(), f.tipY()), f.w * 1.5f, cap = StrokeCap.Round)
    drawLine(PB_GOLD_LT, Offset(f.px, f.py), Offset(f.tipX(), f.tipY()), f.w * 0.6f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF10303F), 7f, Offset(f.px, f.py))
    drawCircle(PB_GOLD, 7f, Offset(f.px, f.py), style = Stroke(width = 2f))
}

// ─── Composable ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinballScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val sprites = remember {
        fun load(id: Int): ImageBitmap? =
            try { BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap() } catch (_: Exception) { null }
        PbSprites(
            bg = load(R.drawable.pinball_bg),
            bumper = load(R.drawable.pinball_bumper),
            bumperLit = load(R.drawable.pinball_bumper_lit),
            bumperSmall = load(R.drawable.pinball_bumper_small),
            orbOff = load(R.drawable.pinball_orb_off),
            orbOn = load(R.drawable.pinball_orb_on),
            heart = load(R.drawable.pinball_heart),
            ball = load(R.drawable.pinball_ball),
            laser = load(R.drawable.pinball_laser),
            flipperRail = load(R.drawable.pinball_flipper_rail)
        )
    }

    // ─── SoundPool ───────────────────────────────────────────────────────────
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
    }
    val sndHitA      = remember { soundPool.load(context, R.raw.pinball_hit_a,    1) }
    val sndHitB      = remember { soundPool.load(context, R.raw.pinball_hit_b,    1) }
    val sndNewBall   = remember { soundPool.load(context, R.raw.pinball_newball,  1) }
    val sndGameOver  = remember { soundPool.load(context, R.raw.pinball_gameover, 1) }
    DisposableEffect(soundPool) { onDispose { soundPool.release() } }

    var sessionHigh by remember { mutableIntStateOf(0) }
    var restartTrigger by remember { mutableIntStateOf(0) }
    val gs = remember(restartTrigger) { PbGs().also { initPinball(it); it.highScore = sessionHigh } }
    val gsRef = rememberUpdatedState(gs)

    var renderTick by remember { mutableIntStateOf(0) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var lbCurrent by remember { mutableStateOf<List<PinballLeaderboardEntry>>(emptyList()) }
    var lbPrev by remember { mutableStateOf<List<PinballLeaderboardEntry>>(emptyList()) }

    BackHandler { onNavigateBack() }

    // Spiel-Loop
    LaunchedEffect(gs) {
        var prevGameOver = false
        var scoreSubmitted = false
        while (isActive) {
            stepPinball(gs)

            // Sound-Events abspielen + zurücksetzen
            if (gs.playHit) {
                gs.playHit = false
                soundPool.play(sndHitA, 1f, 1f, 0, 0, 1f)   // beide Kollisions-Sounds gemischt
                soundPool.play(sndHitB, 1f, 1f, 0, 0, 1f)
            }
            if (gs.playNewBall) {
                gs.playNewBall = false
                soundPool.play(sndNewBall, 1f, 1f, 1, 0, 1f)
            }
            if (gs.playGameOver) {
                gs.playGameOver = false
                soundPool.play(sndGameOver, 1f, 1f, 1, 0, 1f)
            }

            if (gs.gameOver && !prevGameOver) {
                // Jeden Score übermitteln: Der Server entscheidet, ob es ein neuer
                // Monats-Bestwert ist. (Nicht an den lokalen Allzeit-Highscore koppeln,
                // sonst taucht man in einem neuen Monat nie auf, wenn der Vormonat höher war.)
                if (gs.score > 0 && !scoreSubmitted) {
                    scoreSubmitted = true
                    val s = gs.score
                    launch { try { viewModel.submitPinballScore(s) } catch (_: Exception) {} }
                }
            }
            prevGameOver = gs.gameOver
            renderTick++
            delay(16)
        }
    }

    suspend fun loadLeaderboard() {
        try {
            val resp = viewModel.getPinballLeaderboard()
            if (resp.isSuccessful) {
                lbCurrent = resp.body()?.currentMonth ?: emptyList()
                lbPrev = resp.body()?.prevMonth ?: emptyList()
            }
        } catch (_: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF05070D))) {
        TopAppBar(
            title = { Text("Flipper", fontWeight = FontWeight.ExtraBold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
            },
            actions = {
                @Suppress("UNUSED_EXPRESSION") renderTick
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                    Text("x${gsRef.value.mult}", color = PB_PLASMA, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp))
                    Text("${gsRef.value.score}", color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp))
                    IconButton(onClick = {
                        showLeaderboard = true
                        coroutineScope.launch { loadLeaderboard() }
                    }) {
                        Icon(Icons.Default.Leaderboard, contentDescription = "Bestenliste", tint = PB_GOLD)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0C1220))
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var lastLeftTap = 0L    // Zeitpunkt des letzten Tipps auf die linke Hälfte
                        var lastRightTap = 0L   // Zeitpunkt des letzten Tipps auf die rechte Hälfte
                        var lastBothTap = 0L    // Zeitpunkt des letzten gleichzeitigen Betätigens beider Hebel
                        var bothPrev = false    // waren im vorigen Event beide Hebel gedrückt?
                        while (true) {
                            val event = awaitPointerEvent()
                            val w = size.width
                            var leftPressed = false
                            var rightPressed = false
                            event.changes.forEach { c ->
                                if (c.pressed) {
                                    if (c.position.x < w / 2f) leftPressed = true else rightPressed = true
                                }
                                if (c.changedToDown()) {
                                    tryLaunchBall(gsRef.value)
                                    // Doppel-Tipp auf DIESELBE Seite innerhalb 500 ms → am Spielfeld rütteln
                                    val now = System.currentTimeMillis()
                                    if (c.position.x < w / 2f) {
                                        if (lastLeftTap != 0L && now - lastLeftTap <= 500L) {
                                            nudgePinball(gsRef.value, 1)    // links → Stoss nach rechts-oben
                                            lastLeftTap = 0L
                                        } else lastLeftTap = now
                                    } else {
                                        if (lastRightTap != 0L && now - lastRightTap <= 500L) {
                                            nudgePinball(gsRef.value, -1)   // rechts → Stoss nach links-oben
                                            lastRightTap = 0L
                                        } else lastRightTap = now
                                    }
                                }
                            }
                            val g = gsRef.value
                            g.flipL.active = leftPressed
                            g.flipL2.active = leftPressed   // kleiner Flipper folgt dem linken
                            g.flipR.active = rightPressed
                            // Beide Hebel gleichzeitig + DOPPELT innerhalb 600 ms → vertikaler Schubs
                            val bothNow = leftPressed && rightPressed
                            if (bothNow && !bothPrev) {
                                val now = System.currentTimeMillis()
                                if (lastBothTap != 0L && now - lastBothTap <= 600L) {
                                    nudgePinballVertical(g)
                                    lastBothTap = 0L
                                } else lastBothTap = now
                            }
                            bothPrev = bothNow
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") renderTick
                val g = gsRef.value
                val scaleF = min(size.width / PB_W, size.height / PB_H)
                val offX = (size.width - PB_W * scaleF) / 2f
                val offY = (size.height - PB_H * scaleF) / 2f
                val shakeX = if (g.shake > 0f) (Random.nextFloat() * 2f - 1f) * 6f * g.shake * scaleF else 0f
                val shakeY = if (g.shake > 0f) (Random.nextFloat() * 2f - 1f) * 6f * g.shake * scaleF else 0f
                translate(offX + shakeX, offY + shakeY) {
                    scale(scaleF, scaleF, pivot = Offset.Zero) {
                        drawPinball(g, sprites)
                    }
                }
            }

            // HUD: Best + Leben
            Column(
                modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                @Suppress("UNUSED_EXPRESSION") renderTick
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.45f)) {
                    Text("Best: ${gsRef.value.highScore}", color = PB_GOLD, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.45f)) {
                    Text("Bälle: ${gsRef.value.lives}", color = PB_PLASMA, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            // Status-Meldung
            run {
                @Suppress("UNUSED_EXPRESSION") renderTick
                val g = gsRef.value
                if (g.msgTimer > 0 && g.message.isNotEmpty() && !g.gameOver) {
                    Box(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.6f)) {
                            Text(g.message, color = PB_PLASMA, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                }
            }

            // Abschuss-Hinweis
            run {
                @Suppress("UNUSED_EXPRESSION") renderTick
                val g = gsRef.value
                if (!g.gameOver && g.balls.any { !it.launched }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(shape = RoundedCornerShape(20.dp), color = PB_GOLD.copy(alpha = 0.9f)) {
                            Text("Tippen zum Abschießen", color = Color(0xFF06101F),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
            }

            // Game-Over-Overlay
            run {
                @Suppress("UNUSED_EXPRESSION") renderTick
                val g = gsRef.value
                if (g.gameOver) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1220)),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("GAME OVER", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFEF5350))
                                Text("Score: ${g.score}", fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Bestleistung: ${g.highScore}", fontSize = 16.sp, color = PB_GOLD)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = {
                                            sessionHigh = g.highScore
                                            restartTrigger++
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null,
                                            modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Nochmal", fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(onClick = {
                                        showLeaderboard = true
                                        coroutineScope.launch { loadLeaderboard() }
                                    }) {
                                        Icon(Icons.Default.Leaderboard, contentDescription = null,
                                            modifier = Modifier.size(20.dp), tint = PB_GOLD)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Bestenliste", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLeaderboard) {
        PinballLeaderboardDialog(
            current = lbCurrent,
            prev = lbPrev,
            onDismiss = { showLeaderboard = false }
        )
    }
}

@Composable
private fun PinballLeaderboardDialog(
    current: List<PinballLeaderboardEntry>,
    prev: List<PinballLeaderboardEntry>,
    onDismiss: () -> Unit
) {
    var showPrev by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        title = {
            Column {
                Text("Flipper – Bestenliste", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showPrev, onClick = { showPrev = false },
                        label = { Text("Dieser Monat") })
                    FilterChip(selected = showPrev, onClick = { showPrev = true },
                        label = { Text("Vormonat") })
                }
            }
        },
        text = {
            val entries = if (showPrev) prev else current
            if (entries.isEmpty()) {
                Text("Noch keine Einträge.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    entries.take(20).forEach { e ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${e.rank}.", fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp),
                                color = when (e.rank) {
                                    1 -> Color(0xFFFFD600); 2 -> Color(0xFFB0BEC5); 3 -> Color(0xFFCD7F32)
                                    else -> MaterialTheme.colorScheme.onSurface
                                })
                            Text(e.username, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${e.bestScore}", fontWeight = FontWeight.Bold, color = PB_GOLD)
                        }
                    }
                }
            }
        }
    )
}
