package com.securechat.app.ui.screens

import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ─── Physik-Konstanten ────────────────────────────────────────────────────────
private const val JOD_GRAVITY_B        = 0.70f
private const val JOD_JUMP_GRASS_B     = -20f
private const val JOD_JUMP_STONE_B     = -23f
private const val JOD_JUMP_METAL_B     = -27f
private const val JOD_JUMP_WOOD_B      = -21f
private const val JOD_SPRING_MULT      = 2.0f
private const val JOD_JETPACK_VEL_B    = -18f
private const val JOD_JETPACK_MS       = 4500L
private const val JOD_BUBBLE_VEL_B     = -9f
private const val JOD_BUBBLE_MS        = 13000L
private const val JOD_MAX_VEL_X_B     = 7.0f
private const val JOD_TILT_SENS_B     = 2.4f
private const val JOD_PLAT_H_B        = 20f
private const val JOD_PLAYER_W_B      = 47.38f
private const val JOD_PLAYER_H_B      = 55.62f
private const val JOD_ITEM_SIZE_B     = 22f
private const val JOD_SCORE_PER_PLAT  = 5

// ─── Level-Schwellen ─────────────────────────────────────────────────────────
private const val JOD_LEVEL2_SCORE = 50
private const val JOD_LEVEL3_SCORE = 150
private const val JOD_LEVEL4_SCORE = 300
private const val JOD_LEVEL5_SCORE = 500
private const val JOD_LEVEL6_SCORE = 750
private const val JOD_LEVEL7_SCORE = 1100
private const val JOD_LEVEL8_SCORE = 1600
private const val JOD_LEVEL9_SCORE = 2200
private const val JOD_BUBBLE_L6_MS  = 7000L

private val JOD_LEVEL_NAMES = mapOf(
    1 to "Untergrund",
    2 to "Himmel",
    3 to "Jungel",
    4 to "Pilzwelt",
    5 to "Schnee",
    6 to "Atmosphäre",
    7 to "ALL",
    8 to "Way to Home",
    9 to "New Home"
)

// ─── Enums ───────────────────────────────────────────────────────────────────
private enum class JodPlatType  { GRASS, WOOD, STONE, METAL }
private enum class JodItemType  { SPRING, JETPACK, SHIELD, HEART, BUBBLE, WORMHOLE }

// ─── Datenklassen ────────────────────────────────────────────────────────────
private data class JodPlat(
    val id: Int,
    val worldX: Float,
    val worldY: Float,
    val width: Float,
    val type: JodPlatType,
    var broken: Boolean = false,
    var breakTimer: Float = 0f
)

private data class JodItem(
    val id: Int,
    val worldX: Float,
    val worldY: Float,
    val type: JodItemType,
    var collected: Boolean = false,
    val durationMs: Long = 0L
)

private data class JodEnemy(
    val id: Int,
    val platId: Int,
    var worldX: Float,
    val worldY: Float,
    var velX: Float,
    val platMinX: Float,
    val platMaxX: Float,
    val w: Float,
    val h: Float
)

private data class JodPopup(var worldX: Float, var worldY: Float, var alpha: Float = 1f, val text: String = "+$JOD_SCORE_PER_PLAT")

// ─── Sensor ───────────────────────────────────────────────────────────────────
private class JodSensor { @Volatile var ax: Float = 0f }

// ─── Spielzustand ────────────────────────────────────────────────────────────
private class JodGs(val canvasW: Float, val canvasH: Float) {
    val scale      = (canvasW / 420f).coerceAtLeast(1f)

    val playerW    = JOD_PLAYER_W_B  * scale
    val playerH    = JOD_PLAYER_H_B  * scale
    val platH      = JOD_PLAT_H_B    * scale
    val itemSz     = JOD_ITEM_SIZE_B  * scale

    val gravity    = JOD_GRAVITY_B    * scale
    val jumpGrass  = JOD_JUMP_GRASS_B * scale
    val jumpStone  = JOD_JUMP_STONE_B * scale
    val jumpMetal  = JOD_JUMP_METAL_B * scale
    val jumpWood   = JOD_JUMP_WOOD_B  * scale
    val jetpackVel = JOD_JETPACK_VEL_B * scale
    val maxVelX    = JOD_MAX_VEL_X_B  * scale
    val tiltSens   = JOD_TILT_SENS_B  * scale

    var playerWorldX  = canvasW / 2f - playerW / 2f
    var playerWorldY  = canvasH * 0.62f - playerH
    var velX          = 0f
    var velY          = 0f
    var cameraY       = 0f
    var score         = 0
    var highScore     = 0
    var gameOver      = false
    var facingRight   = true
    var isJetpack     = false
    var jetpackMs     = 0L
    var level         = 1
    var levelAnnounceMs = 0L
    var frameCount    = 0L
    val platforms     = mutableListOf<JodPlat>()
    val items         = mutableListOf<JodItem>()
    val enemies       = mutableListOf<JodEnemy>()
    val popups        = mutableListOf<JodPopup>()
    val passedIds     = mutableSetOf<Int>()
    var nextId        = 0
    // Sound-Flags (werden nach dem Auslösen vom Composable zurückgesetzt)
    var isFallingSound: Boolean = false
    var playJumpNormal: Boolean = false
    var playJumpMetal:  Boolean = false
    var playSpring:     Boolean = false
    // Falling-Sound Schwelle
    var missedPlatformCount: Int = 0
    var fallPeakWorldY: Float = 0f
    var prevVelY: Float = 0f
    // Aussichtslosigkeits-Erkennung
    var lastLandedPlatId: Int = -1
    var sameplatLandCount: Int = 0
    var rescueCooldownMs: Long = 0L
    // Schutzschild & Herz
    var shieldMs: Long = 0L
    var extraLives: Int = 0          // max 3
    var shieldSpawnedL3: Boolean = false
    var shieldSpawnedL4: Boolean = false
    var heartSpawnedL5: Boolean = false
    var shieldSpawnedL5a: Boolean = false
    var shieldSpawnedL5b: Boolean = false
    var shieldSpawnedL5c: Boolean = false
    var shieldSpawnedL7: Boolean = false
    var extraPlatL6a: Boolean = false
    var extraPlatL6b: Boolean = false
    var extraPlatL6c: Boolean = false
    var monsterSpawnedL2: Boolean = false
    var heartSpawnedL2: Boolean = false
    var heartSpawnedL2b: Boolean = false
    var isBubble: Boolean = false
    var bubbleMs: Long = 0L
    var bubbleSpawnedL4: Boolean = false
    var bubbleSpawnedL6: Boolean = false
    var playItemSkill: Boolean = false    // Herz eingesammelt
    var playItemShield: Boolean = false  // Schild eingesammelt
    var playItemBubble: Boolean = false  // Bubble eingesammelt
    var enemySpawnCount: Int = 0          // Zähler für Bewegungslogik je Level
    var forceNextEnemyOpposite: Boolean = false  // Nächstes Monster auf gegenüberliegende Seite spawnen
    var consecutiveWoodCount: Int = 0     // Anzahl aufeinanderfolgender WOOD-Plattformen (max 3)
    // Wurmloch
    var wormholeSpawned: Boolean = false
    var wormhole2Spawned: Boolean = false
    var playWormhole: Boolean = false
    var wormholeTriggered: Boolean = false
    // Werte aus Server-Status (werden vom Composable gesetzt)
    var continuousPlay: Int = 0
    var holeAvailable: Boolean = false
}

// ─── Hilfsfunktionen ─────────────────────────────────────────────────────────
private fun JodGs.screenY(worldY: Float) = worldY - cameraY
private fun JodGs.screenX(worldX: Float) = worldX

private fun JodGs.highestPlatWorldY(): Float =
    platforms.minOfOrNull { it.worldY } ?: cameraY

private fun addJodPlatform(gs: JodGs, canvasW: Float, worldY: Float, easy: Boolean) {
    val minW  = canvasW * 0.1512f
    val maxW  = if (easy) canvasW * 0.2376f else canvasW * 0.1944f
    var width = minW + Random.nextFloat() * (maxW - minW)
    // Level 6: 3 garantierte GRASS-Plattformen ohne Monster, verteilt übers Level
    val forceGrassNoEnemy = !easy && gs.level == 6 && when {
        !gs.extraPlatL6a && gs.score >= 820  -> { gs.extraPlatL6a = true; true }
        !gs.extraPlatL6b && gs.score >= 940  -> { gs.extraPlatL6b = true; true }
        !gs.extraPlatL6c && gs.score >= 1060 -> { gs.extraPlatL6c = true; true }
        else -> false
    }
    val type  = if (easy || forceGrassNoEnemy) JodPlatType.GRASS else when (gs.level) {
        1 -> when (Random.nextInt(10)) {
            in 0..4 -> JodPlatType.GRASS
            in 5..7 -> JodPlatType.WOOD
            8       -> JodPlatType.STONE
            else    -> JodPlatType.METAL
        }
        2 -> when (Random.nextInt(10)) {
            in 0..3 -> JodPlatType.GRASS
            in 4..6 -> JodPlatType.WOOD
            in 7..8 -> JodPlatType.STONE
            else    -> JodPlatType.METAL
        }
        else -> when (Random.nextInt(10)) {
            in 0..2 -> JodPlatType.GRASS
            in 3..5 -> JodPlatType.WOOD
            in 6..7 -> JodPlatType.STONE
            else    -> JodPlatType.METAL
        }
    }

    // Gegner-Spawn-Entscheidung vorab: In Level 4 am Anfang wenig, zum Ende hin mehr Monster
    // Mindestabstand zwischen Gegnern: 3 Plattformhöhen (damit man zwischen ihnen hindurchspringen kann)
    val minEnemyGap = gs.platH * 3f
    val enemyTooClose = gs.enemies.any { abs((it.worldY + it.h) - worldY) < minEnemyGap }
    val spawnEnemy = !easy && !forceGrassNoEnemy && (gs.level >= 4 || (gs.level == 2 && !gs.monsterSpawnedL2)) && width > canvasW * 0.15f && !enemyTooClose && run {
        val spawnChance = when (gs.level) {
            2 -> 0.4f   // Level 2: einmaliges zufälliges Monster
            4 -> {
                val progress = ((gs.score - JOD_LEVEL4_SCORE).toFloat() /
                    (JOD_LEVEL5_SCORE - JOD_LEVEL4_SCORE)).coerceIn(0f, 1f)
                0.10f + progress * 0.45f   // 10 % → 55 % über Level 4
            }
            5 -> 0.12f  // Schnee: weniger Monster
            7 -> 0.38f  // Etwas weniger Monster als Standard
            8 -> 0.60f
            9 -> 0.65f
            else -> 0.55f
        }
        Random.nextFloat() < spawnChance
    }

    // Plattform mit Monster: 50 % breiter damit man neben dem Monster hüpfen kann
    if (spawnEnemy) {
        width = (width * 1.5f).coerceAtMost(canvasW * 0.3024f)
    }

    // Während Jetpack-Flug (bis Level 6): Holz-Plattformen zu normalen Plattformen umwandeln
    val jetpackType = if (gs.isJetpack && gs.level <= 6 && type == JodPlatType.WOOD) JodPlatType.GRASS else type

    // Max 3 aufeinanderfolgende WOOD-Plattformen: danach erzwinge eine normale Plattform
    val effectiveType = if (jetpackType == JodPlatType.WOOD && gs.consecutiveWoodCount >= 3) JodPlatType.GRASS else jetpackType
    gs.consecutiveWoodCount = if (effectiveType == JodPlatType.WOOD) gs.consecutiveWoodCount + 1 else 0

    // Kollisionsgefahr: nächste Monster-Plattform auf gegenüberliegende Seite des Spielfeldes
    val x = if (spawnEnemy && gs.forceNextEnemyOpposite) {
        gs.forceNextEnemyOpposite = false
        val playerCX = gs.playerWorldX + gs.playerW / 2f
        if (playerCX > canvasW / 2f) {
            // Spieler rechts → Monster-Plattform auf linke Seite
            10f + Random.nextFloat() * (canvasW / 2f - width - 20f).coerceAtLeast(10f)
        } else {
            // Spieler links → Monster-Plattform auf rechte Seite
            canvasW / 2f + Random.nextFloat() * (canvasW / 2f - width - 10f).coerceAtLeast(10f)
        }
    } else {
        Random.nextFloat() * (canvasW - width - 20f) + 10f
    }
    val pid = gs.nextId++
    gs.platforms += JodPlat(id = pid, worldX = x, worldY = worldY, width = width, type = effectiveType)

    // Item-Position: bei Monster-Plattform seitlich verschieben um Überlappung zu vermeiden
    val iX = if (spawnEnemy) {
        val eW      = gs.playerW * 0.75f
        val eLeft   = x + (width - eW) / 2f
        val eRight  = eLeft + eW
        val leftPos = x + 2f * gs.scale
        val rightPos = x + width - gs.itemSz - 2f * gs.scale
        when {
            leftPos + gs.itemSz + 2f * gs.scale <= eLeft -> leftPos
            rightPos >= eRight + 2f * gs.scale            -> rightPos
            else -> x + width / 2f - gs.itemSz / 2f       // Fallback (sollte nicht eintreten)
        }
    } else {
        x + width / 2f - gs.itemSz / 2f
    }
    val iY   = worldY - gs.itemSz - 4f * gs.scale

    // Einmalige Spezial-Items haben Vorrang (kein reguläres Item auf derselben Plattform)
    val specialPlaced = !easy && when {
        gs.level == 2 && gs.score >= 80 && !gs.heartSpawnedL2 -> {
            gs.heartSpawnedL2 = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.HEART)
            true
        }
        gs.level == 2 && gs.score >= 120 && !gs.heartSpawnedL2b -> {
            gs.heartSpawnedL2b = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.HEART)
            true
        }
        gs.level == 3 && gs.score >= 225 && !gs.shieldSpawnedL3 -> {
            gs.shieldSpawnedL3 = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.SHIELD)
            true
        }
        gs.level == 4 && gs.score >= 400 && !gs.shieldSpawnedL4 -> {
            gs.shieldSpawnedL4 = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.SHIELD)
            true
        }
        gs.level == 5 && gs.score >= 520 && !gs.heartSpawnedL5 -> {
            gs.heartSpawnedL5 = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.HEART)
            true
        }
        gs.level == 5 && gs.score >= 550 && !gs.shieldSpawnedL5a -> {
            gs.shieldSpawnedL5a = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.SHIELD)
            true
        }
        gs.level == 5 && gs.score >= 650 && !gs.shieldSpawnedL5b -> {
            gs.shieldSpawnedL5b = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.SHIELD)
            true
        }
        gs.level == 5 && gs.score >= 710 && !gs.shieldSpawnedL5c -> {
            gs.shieldSpawnedL5c = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.SHIELD)
            true
        }
        gs.level == 7 && gs.score >= 1200 && !gs.shieldSpawnedL7 -> {
            gs.shieldSpawnedL7 = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.SHIELD)
            true
        }
        gs.level == 4 && !gs.bubbleSpawnedL4 && Random.nextFloat() < 0.30f -> {
            gs.bubbleSpawnedL4 = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.BUBBLE)
            true
        }
        // Erstes Wurmloch: in Level 4 spät (Score > 420), nur einmal. Erscheint ab 1000 erreichten
        // Session-Punkten IMMER (bei jedem Neustart bis zum Verlassen des Spiels), sonst wie bisher
        // nach 10 Fehlversuchen (continuous_play >= 10) mit min. 500 Punkten und wenn heute verfügbar.
        gs.level == 4 && gs.score > 420 && !gs.wormholeSpawned
            && (gs.highScore >= 1000 || (gs.continuousPlay >= 10 && gs.holeAvailable && gs.highScore >= 500)) -> {
            gs.wormholeSpawned = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.WORMHOLE)
            true
        }
        // Zweites Wurmloch: nur wenn man einmal über 1000 Punkte hatte – immer 10 Stufen
        // (10 Plattformen = 50 Punkte) vor dem ersten Loch.
        gs.level == 4 && gs.score > 370 && !gs.wormhole2Spawned && gs.highScore >= 1000 -> {
            gs.wormhole2Spawned = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.WORMHOLE)
            true
        }
        gs.level == 6 && !gs.bubbleSpawnedL6 && Random.nextFloat() < 0.28f -> {
            gs.bubbleSpawnedL6 = true
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.BUBBLE, durationMs = JOD_BUBBLE_L6_MS)
            true
        }
        gs.level in 5..8 && gs.extraLives < 3 && run {
            val chance = when (gs.level) {
                5, 6 -> 0.15f
                7    -> 0.10f
                8    -> 0.06f
                else -> 0f
            }
            Random.nextFloat() < chance
        } -> {
            gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.HEART)
            true
        }
        else -> false
    }

    if (!specialPlaced) {
        val roll = Random.nextFloat()
        when {
            roll < 0.10f                  -> gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.SPRING)
            roll < 0.13f && gs.level >= 2 -> {
                gs.items += JodItem(gs.nextId++, iX, iY, JodItemType.JETPACK)
                // Level 4: direkt zwei Plattformen über dem Jetpack ein Schild platzieren
                if (gs.level == 4) {
                    val shieldY = worldY - gs.canvasH * 0.28f - gs.itemSz - 4f * gs.scale
                    gs.items += JodItem(gs.nextId++, iX, shieldY, JodItemType.SHIELD)
                }
            }
        }
    }

    // Gegner platzieren
    if (spawnEnemy) {
        val eH    = gs.playerH * 0.75f
        val eW    = gs.playerW * 0.75f
        val eX    = x + (width - eW) / 2f
        val eY    = worldY - eH
        val speedBoost = when (gs.level) {
            8    -> 1.15f
            9    -> 1.30f
            else -> 1.0f
        }
        val speed = (0.9f + (gs.level - 4) * 0.4f) * gs.scale * speedBoost
        // Level 4: kein Monster bewegt sich
        // Level 5 (Schnee): nur jedes dritte bewegt sich
        // Level 6: nur jedes zweite bewegt sich
        // Level 7+: alle bewegen sich
        val moves = when (gs.level) {
            2    -> false
            4    -> false
            5    -> gs.enemySpawnCount % 3 == 2
            6    -> gs.enemySpawnCount % 2 == 1
            else -> true
        }
        val eVX = if (moves) (if (Random.nextBoolean()) speed else -speed) else 0f
        gs.enemySpawnCount++
        if (gs.level == 2) gs.monsterSpawnedL2 = true
        gs.enemies += JodEnemy(
            id = gs.nextId++, platId = pid,
            worldX = eX, worldY = eY, velX = eVX,
            platMinX = x + 2f, platMaxX = x + width - eW - 2f,
            w = eW, h = eH
        )
    }
}

private fun initJodPlatforms(gs: JodGs, canvasW: Float, canvasH: Float) {
    val startPlatW = canvasW * 0.3456f
    gs.platforms += JodPlat(
        id = gs.nextId++, worldX = canvasW / 2f - startPlatW / 2f,
        worldY = canvasH * 0.68f, width = startPlatW, type = JodPlatType.GRASS
    )
    var topY = canvasH * 0.68f
    repeat(28) { i ->
        val gapMin = if (i < 5) canvasH * 0.08f else canvasH * 0.11f
        val gapMax = if (i < 5) canvasH * 0.13f else canvasH * 0.18f
        topY -= gapMin + Random.nextFloat() * (gapMax - gapMin)
        addJodPlatform(gs, canvasW, topY, easy = i < 8)
    }
}

// ─── Physik-Update ───────────────────────────────────────────────────────────
private fun stepJod(gs: JodGs, canvasW: Float, canvasH: Float, sensor: JodSensor) {
    if (gs.gameOver) return

    gs.frameCount++
    if (gs.rescueCooldownMs > 0L) gs.rescueCooldownMs -= 16L
    if (gs.shieldMs > 0L) gs.shieldMs -= 16L

    // Level berechnen
    val newLevel = when {
        gs.score >= JOD_LEVEL9_SCORE -> 9
        gs.score >= JOD_LEVEL8_SCORE -> 8
        gs.score >= JOD_LEVEL7_SCORE -> 7
        gs.score >= JOD_LEVEL6_SCORE -> 6
        gs.score >= JOD_LEVEL5_SCORE -> 5
        gs.score >= JOD_LEVEL4_SCORE -> 4
        gs.score >= JOD_LEVEL3_SCORE -> 3
        gs.score >= JOD_LEVEL2_SCORE -> 2
        else -> 1
    }
    if (newLevel != gs.level) {
        gs.level = newLevel
        gs.levelAnnounceMs = 1800L
    }
    if (gs.levelAnnounceMs > 0L) gs.levelAnnounceMs -= 16L

    // Jetpack / Bubble
    val velYBefore = gs.velY
    if (gs.isJetpack) {
        gs.jetpackMs -= 16L
        if (gs.jetpackMs <= 0L) { gs.isJetpack = false; gs.velY = 0f }
        else {
            // Letzte 1,5 Sekunden: Geschwindigkeit linear von 85 % auf 75 % drosseln
            gs.velY = if (gs.jetpackMs <= 1500L) {
                val t = gs.jetpackMs.toFloat() / 1500f  // 1.0 → 0.0
                gs.jetpackVel * (0.75f + 0.10f * t)     // 85 % → 75 %
            } else {
                gs.jetpackVel
            }
        }
    } else if (gs.isBubble) {
        gs.bubbleMs -= 16L
        if (gs.bubbleMs <= 0L) { gs.isBubble = false; gs.velY = 0f }
        else gs.velY = JOD_BUBBLE_VEL_B * gs.scale
    } else {
        gs.velY += gs.gravity
    }

    // Scheitelpunkt erkennen: Übergang von Aufwärts- zu Abwärtsbewegung
    if (!gs.isJetpack && velYBefore <= 0f && gs.velY > 0f) {
        gs.fallPeakWorldY = gs.playerWorldY
        gs.missedPlatformCount = 0
    }
    gs.prevVelY = gs.velY

    // Horizontale Steuerung
    val targetVX = (-sensor.ax * gs.tiltSens).coerceIn(-gs.maxVelX, gs.maxVelX)
    gs.velX = gs.velX * 0.82f + targetVX * 0.18f
    if (gs.velX > 0.4f) gs.facingRight = true
    else if (gs.velX < -0.4f) gs.facingRight = false

    gs.playerWorldX += gs.velX
    gs.playerWorldY += gs.velY

    // X-Wraparound
    if (gs.playerWorldX + gs.playerW < 0f) gs.playerWorldX = canvasW
    else if (gs.playerWorldX > canvasW) gs.playerWorldX = -gs.playerW

    // Kamera: nur nach oben folgen
    val desiredCamY = gs.playerWorldY - canvasH * 0.38f
    if (desiredCamY < gs.cameraY) gs.cameraY = desiredCamY

    // Plattform-Kollision (nur beim Fallen, nicht Jetpack)
    val colTol = 4f * gs.scale
    if (gs.velY > 0f && !gs.isJetpack) {
        val feetY     = gs.playerWorldY + gs.playerH
        val prevFeetY = feetY - gs.velY
        var landedThisFrame  = false
        var missedPlatform   = false
        for (plat in gs.platforms.toList()) {
            if (plat.broken) continue
            val platTop = plat.worldY
            if (prevFeetY <= platTop + colTol && feetY >= platTop - colTol) {
                val playerRight = gs.playerWorldX + gs.playerW
                val playerLeft  = gs.playerWorldX
                if (playerRight > plat.worldX + colTol && playerLeft < plat.worldX + plat.width - colTol) {
                    gs.playerWorldY = platTop - gs.playerH
                    val jumpVel = when (plat.type) {
                        JodPlatType.GRASS  -> gs.jumpGrass
                        JodPlatType.WOOD   -> gs.jumpWood
                        JodPlatType.STONE  -> gs.jumpStone
                        JodPlatType.METAL  -> gs.jumpMetal
                    }
                    gs.velY = jumpVel
                    if (plat.type == JodPlatType.WOOD) {
                        val idx = gs.platforms.indexOf(plat)
                        if (idx >= 0) gs.platforms[idx] = plat.copy(broken = true, breakTimer = 0f)
                    }
                    landedThisFrame = true
                    gs.missedPlatformCount = 0
                    // Sound-Flags setzen
                    gs.isFallingSound = false
                    if (plat.type == JodPlatType.METAL) gs.playJumpMetal  = true
                    else                                 gs.playJumpNormal = true
                    // Aussichtslosigkeits-Erkennung: 3x auf derselben Plattform gelandet
                    if (plat.id == gs.lastLandedPlatId) {
                        gs.sameplatLandCount++
                    } else {
                        gs.sameplatLandCount = 1
                        gs.lastLandedPlatId  = plat.id
                    }
                    if (gs.sameplatLandCount >= 2 && gs.rescueCooldownMs <= 0L) {
                        // Kreis-Erreichbarkeit prüfen: obere Hälfte des Kreises um Spieler-Mittelpunkt
                        val rescueCircleR = run {
                            val mjh = (gs.jumpGrass * gs.jumpGrass) / (2f * gs.gravity)
                            mjh * 1.2f
                        }
                        val rCX = gs.playerWorldX + gs.playerW / 2f
                        val rCY = gs.playerWorldY + gs.playerH / 2f
                        val canProgress = gs.platforms.any { p ->
                            !p.broken && p.id != plat.id && p.worldY < rCY &&
                            run {
                                val nx = rCX.coerceIn(p.worldX, p.worldX + p.width)
                                val dx = nx - rCX; val dy = p.worldY - rCY
                                dx * dx + dy * dy <= rescueCircleR * rescueCircleR
                            }
                        }
                        if (canProgress) {
                            // Spieler kann selbst weiterkommen → kein Rescue, nur Zähler zurücksetzen
                            gs.sameplatLandCount = 0
                        } else {
                            // Keine erreichbare Plattform im Kreis → Rettungsplattform spawnen
                            val abovePlat = gs.platforms
                                .filter { !it.broken && it.worldY < plat.worldY }
                                .minByOrNull { plat.worldY - it.worldY }
                            val rescueY = if (abovePlat != null)
                                plat.worldY - (plat.worldY - abovePlat.worldY) * 0.48f
                            else
                                plat.worldY - canvasH * 0.14f
                            val rescueW = canvasW * 0.22f
                            val rescueX = (gs.playerWorldX + gs.playerW / 2f - rescueW / 2f)
                                .coerceIn(10f, canvasW - rescueW - 10f)
                            gs.platforms += JodPlat(
                                id = gs.nextId++,
                                worldX = rescueX,
                                worldY = rescueY,
                                width  = rescueW,
                                type   = JodPlatType.GRASS
                            )
                            gs.score = (gs.score - 40).coerceAtLeast(0)
                            gs.popups += JodPopup(
                                worldX = rescueX + rescueW / 2f,
                                worldY = rescueY - 20f,
                                text   = "-40 Pkt."
                            )
                            gs.rescueCooldownMs  = 3000L
                            gs.sameplatLandCount = 0
                        }
                    }
                    break
                } else {
                    // Plattform seitlich verfehlt → Zähler erhöhen
                    missedPlatform = true
                    gs.missedPlatformCount++
                }
            }
        }
        if (!landedThisFrame && missedPlatform && !gs.isFallingSound) {
            val fallDist = gs.playerWorldY - gs.fallPeakWorldY
            if (fallDist >= 125f * gs.scale) {
                gs.isFallingSound = true
            }
        }
    } else if (gs.isJetpack || gs.isBubble) {
        // Beim Jetpack/Bubble kein Falling-Sound
        gs.isFallingSound = false
    }

    // Item-Kollision
    val pCenterX = gs.playerWorldX + gs.playerW / 2f
    val pCenterY = gs.playerWorldY + gs.playerH / 2f
    for (item in gs.items.toList()) {
        if (item.collected) continue
        val iCX = item.worldX + gs.itemSz / 2f
        val iCY = item.worldY + gs.itemSz / 2f
        // Spring: Spielerfüße müssen zur Spring-Oberkante passen – vertikale Toleranz entsprechend größer
        val vTol = if (item.type == JodItemType.SPRING) (gs.playerH + gs.itemSz) * 0.6f else gs.playerH * 0.6f
        if (abs(pCenterX - iCX) < gs.playerW * 0.6f && abs(pCenterY - iCY) < vTol) {
            when (item.type) {
                JodItemType.SPRING  -> {
                    // Swept-Collision wie bei Plattformen: prevFeetY prüfen damit schneller Fall nicht überspringt
                    val feetY     = gs.playerWorldY + gs.playerH
                    val prevFeetY = feetY - gs.velY
                    val springTop = item.worldY
                    if (gs.velY > 0f && prevFeetY <= springTop + colTol && feetY >= springTop) {
                        item.collected = true
                        gs.velY       = gs.jumpGrass * JOD_SPRING_MULT
                        gs.playSpring = true
                        gs.isFallingSound = false
                    }
                }
                JodItemType.JETPACK -> {
                    item.collected = true
                    gs.isJetpack = true
                    gs.jetpackMs = if (gs.level >= 5) 3500L else JOD_JETPACK_MS
                    gs.velY      = gs.jetpackVel
                    // Bis Level 6: alle vorhandenen Holz-Plattformen zu normalen machen,
                    // damit man nach dem Jetpack nicht desorientiert auf brechenden Plattformen landet
                    if (gs.level <= 6) {
                        val idxList = gs.platforms.indices.filter {
                            gs.platforms[it].type == JodPlatType.WOOD && !gs.platforms[it].broken
                        }
                        for (i in idxList) gs.platforms[i] = gs.platforms[i].copy(type = JodPlatType.GRASS)
                    }
                }
                JodItemType.SHIELD -> {
                    item.collected = true
                    gs.shieldMs = 6000L
                    gs.playItemShield = true
                }
                JodItemType.HEART -> {
                    item.collected = true
                    gs.extraLives = (gs.extraLives + 1).coerceAtMost(3)
                    gs.playItemSkill = true
                }
                JodItemType.BUBBLE -> {
                    item.collected = true
                    gs.isBubble = true
                    gs.bubbleMs = if (item.durationMs > 0L) item.durationMs else JOD_BUBBLE_MS
                    gs.isJetpack = false   // Jetpack beenden falls aktiv
                    gs.playItemBubble = true
                }
                JodItemType.WORMHOLE -> {
                    item.collected = true
                    gs.playWormhole = true
                    gs.wormholeTriggered = true
                    // Teleport zu Level 6: Score auf Level-6-Schwelle setzen
                    gs.score = JOD_LEVEL6_SCORE
                    gs.level = 6
                    gs.levelAnnounceMs = 1800L
                    gs.isJetpack = false
                    gs.isBubble = false
                    gs.shieldMs = 1000L  // 1 Sek. Schild nach Wormhole-Austritt
                    // Alle Plattformen/Items/Gegner löschen – leere Platform unter Spieler spawnen
                    gs.platforms.clear()
                    gs.items.clear()
                    gs.enemies.clear()
                    gs.passedIds.clear()
                    val platW = canvasW * 0.35f
                    val platX = (gs.playerWorldX + gs.playerW / 2f - platW / 2f)
                        .coerceIn(10f, canvasW - platW - 10f)
                    val platY = gs.playerWorldY + canvasH * 0.28f
                    gs.platforms += JodPlat(
                        id = gs.nextId++,
                        worldX = platX, worldY = platY,
                        width = platW, type = JodPlatType.GRASS
                    )
                    gs.cameraY = gs.playerWorldY - canvasH * 0.38f
                }
            }
        }
    }

    // Gegner-Update
    val validPlatIds = gs.platforms.filter { !it.broken }.map { it.id }.toSet()
    gs.enemies.removeAll { it.platId !in validPlatIds }
    for (enemy in gs.enemies) {
        enemy.worldX += enemy.velX
        if (enemy.worldX <= enemy.platMinX) {
            enemy.worldX = enemy.platMinX
            enemy.velX   = abs(enemy.velX)
        }
        if (enemy.worldX >= enemy.platMaxX) {
            enemy.worldX = enemy.platMaxX
            enemy.velX   = -abs(enemy.velX)
        }
    }

    // Gegner-Kollision → Schild/Extra-Leben prüfen oder Game Over
    val pLeft   = gs.playerWorldX + gs.playerW * 0.15f
    val pRight  = gs.playerWorldX + gs.playerW * 0.85f
    val pTop    = gs.playerWorldY + gs.playerH * 0.1f
    val pBottom = gs.playerWorldY + gs.playerH * 0.9f
    val hitEnemy = gs.enemies.firstOrNull { enemy ->
        pRight  > enemy.worldX + enemy.w * 0.1f &&
        pLeft   < enemy.worldX + enemy.w * 0.9f &&
        pBottom > enemy.worldY + enemy.h * 0.1f &&
        pTop    < enemy.worldY + enemy.h * 0.9f
    }
    if (hitEnemy != null) {
        when {
            gs.shieldMs > 0L -> { /* Schutzschild aktiv – kein Schaden */ }
            gs.extraLives > 0 -> {
                gs.extraLives--
                gs.enemies.remove(hitEnemy)          // Monster entfernen
                gs.shieldMs = 1500L                  // Kurze Unverwundbarkeit nach Herzverbrauch
            }
            else -> {
                gs.gameOver = true
                if (gs.score > gs.highScore) gs.highScore = gs.score
            }
        }
    }

    // Break-Animation vorwärts
    val iter = gs.platforms.iterator()
    while (iter.hasNext()) {
        val p = iter.next()
        if (p.broken) {
            val newTimer = p.breakTimer + 0.06f
            val idx = gs.platforms.indexOf(p)
            if (newTimer >= 1f) {
                iter.remove()
                gs.items.removeAll { it.worldX >= p.worldX && it.worldX <= p.worldX + p.width && !it.collected }
                gs.enemies.removeAll { it.platId == p.id }
            } else {
                if (idx >= 0) gs.platforms[idx] = p.copy(breakTimer = newTimer)
            }
        }
    }

    // Score: Plattformen unter Kamera
    for (plat in gs.platforms) {
        if (plat.id !in gs.passedIds) {
            val pScreenY = gs.screenY(plat.worldY)
            if (pScreenY > canvasH * 0.9f) {
                gs.passedIds.add(plat.id)
                gs.score += JOD_SCORE_PER_PLAT
                gs.popups += JodPopup(plat.worldX + plat.width / 2f, plat.worldY - 20f)
            }
        }
    }

    // Popups animieren
    val popupIter = gs.popups.iterator()
    while (popupIter.hasNext()) {
        val pop = popupIter.next()
        pop.alpha  -= 0.025f
        pop.worldY -= 1.5f
        if (pop.alpha <= 0f) popupIter.remove()
    }

    // Culling
    gs.platforms.removeAll { gs.screenY(it.worldY) > canvasH + canvasH * 0.15f }
    gs.items.removeAll    { it.collected || gs.screenY(it.worldY) > canvasH + canvasH * 0.15f }
    gs.enemies.removeAll  { gs.screenY(it.worldY) > canvasH + canvasH * 0.15f }

    // Kollisionsgefahr-Erkennung: Monster in wahrscheinlicher Sprungbahn
    // Wenn der Spieler aufwärts springt und ein Monster genau im Weg liegt,
    // wird das nächste Monster auf die gegenüberliegende Seite des Spielfeldes gespannt.
    if (!gs.forceNextEnemyOpposite && gs.velY < 0f && !gs.isJetpack) {
        val pCX   = gs.playerWorldX + gs.playerW / 2f
        val pCY   = gs.playerWorldY + gs.playerH / 2f
        val maxJH = if (gs.gravity > 0f) (gs.jumpGrass * gs.jumpGrass) / (2f * gs.gravity) else 0f
        for (enemy in gs.enemies) {
            val eCX = enemy.worldX + enemy.w / 2f
            val eCY = enemy.worldY + enemy.h / 2f
            val dy  = pCY - eCY                         // positiv = Monster ist über dem Spieler
            if (dy <= 0f || dy > maxJH * 1.15f) continue  // nicht über Spieler oder zu weit weg
            val framesEst    = dy / (-gs.velY)          // geschätzte Frames bis zur Monster-Höhe
            val predictedCX  = pCX + gs.velX * framesEst
            val wrappedCX    = ((predictedCX % canvasW) + canvasW) % canvasW
            if (abs(wrappedCX - eCX) < (enemy.w + gs.playerW) * 0.5f) {
                gs.forceNextEnemyOpposite = true
                break
            }
        }
    }

    // Neue Plattformen generieren (oben, Lücken steigen mit Level)
    val gapMin = when (gs.level) {
        1    -> canvasH * 0.11f
        2    -> canvasH * 0.12f
        3    -> canvasH * 0.13f
        4    -> canvasH * 0.13f
        5    -> canvasH * 0.14f
        6    -> canvasH * 0.12f
        7    -> canvasH * 0.16f
        8    -> canvasH * 0.17f
        else -> canvasH * 0.18f
    }
    val gapMax = when (gs.level) {
        1    -> canvasH * 0.18f
        2    -> canvasH * 0.19f
        3    -> canvasH * 0.20f
        4    -> canvasH * 0.20f
        5    -> canvasH * 0.21f
        6    -> canvasH * 0.19f
        7    -> canvasH * 0.23f
        8    -> canvasH * 0.24f
        else -> canvasH * 0.25f
    }
    // Erreichbarkeit prüfen: Gibt es eine Plattform direkt über dem Spieler die in einem Sprung
    // erreichbar ist? Wenn ja → kein neuer Spawn (Spieler kann dort hüpfen und denken).
    // Kreis-Erreichbarkeit: unsichtbarer Kreis um Spieler-Mittelpunkt, nur obere Hälfte zählt
    val maxJumpH = (gs.jumpGrass * gs.jumpGrass) / (2f * gs.gravity)
    val circleRadius = maxJumpH * 1.164f
    val spawnCX = gs.playerWorldX + gs.playerW / 2f
    val spawnCY = gs.playerWorldY + gs.playerH / 2f
    val hasReachablePlatform = gs.platforms.any { plat ->
        !plat.broken && plat.worldY < spawnCY &&
        run {
            val nx = spawnCX.coerceIn(plat.worldX, plat.worldX + plat.width)
            val dx = nx - spawnCX; val dy = plat.worldY - spawnCY
            dx * dx + dy * dy <= circleRadius * circleRadius
        }
    }

    val lookAhead = canvasH * 1.2f
    while (gs.highestPlatWorldY() > gs.cameraY - lookAhead) {
        if (hasReachablePlatform) break  // Erreichbare Plattform im Kreis → kein weiterer Spawn
        val newY = gs.highestPlatWorldY() - (gapMin + Random.nextFloat() * (gapMax - gapMin))
        addJodPlatform(gs, canvasW, newY, easy = false)
    }

    // Unter den Bildschirm gefallen
    if (gs.screenY(gs.playerWorldY) > canvasH + canvasH * 0.08f) {
        if (gs.extraLives > 0) {
            // Noch ein Herz übrig → von oben erneut hereinfallen, 1 s Schild, steuerbar.
            gs.extraLives--
            gs.playerWorldX = canvasW / 2f - gs.playerW / 2f
            gs.playerWorldY = gs.cameraY - gs.playerH   // knapp über dem sichtbaren Rand
            gs.velY = 0f                                // Schwerkraft beschleunigt den Fall
            gs.velX = 0f
            gs.shieldMs = maxOf(gs.shieldMs, 1000L)     // 1 Sekunde Unverwundbarkeit
            gs.isJetpack = false
            gs.isBubble = false
            gs.isFallingSound = false
            gs.missedPlatformCount = 0
            gs.fallPeakWorldY = gs.playerWorldY
            gs.playItemSkill = true                     // kurzes Feedback wie beim Herz
        } else {
            gs.gameOver = true
            if (gs.score > gs.highScore) gs.highScore = gs.score
        }
    }

    // Sounds bei Game Over stoppen
    if (gs.gameOver) { gs.isFallingSound = false; gs.isJetpack = false; gs.isBubble = false }
}

// ─── Zeichnen ─────────────────────────────────────────────────────────────────
private fun DrawScope.drawJodGame(
    gs: JodGs,
    canvasW: Float,
    canvasH: Float,
    bgBitmap: ImageBitmap?,
    charLeftBitmap: ImageBitmap?,
    charRightBitmap: ImageBitmap?,
    charUpBitmap: ImageBitmap?,
    charLeftJetpackBitmap: ImageBitmap?,
    charRightJetpackBitmap: ImageBitmap?,
    monsterLvl6Bitmap: ImageBitmap?,
    charShieldLeftBitmap: ImageBitmap?,
    charShieldRightBitmap: ImageBitmap?,
    shieldItemBitmap: ImageBitmap?,
    heartItemBitmap: ImageBitmap?,
    jetpackItemBitmap: ImageBitmap?,
    bubbleBitmap: ImageBitmap?,
    wormholeBitmap: ImageBitmap?
) {
    // Hintergrund
    if (bgBitmap != null) {
        drawImage(
            image = bgBitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(canvasW.toInt(), canvasH.toInt())
        )
    } else {
        // Level 1: Untergrund-Gradient + Sterne (Fallback falls kein Bild)
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFF1A0A3A), Color(0xFF0D1B4B), Color(0xFF112244)),
                startY = 0f, endY = canvasH
            ),
            size = Size(canvasW, canvasH)
        )
        val starPositions = listOf(
            0.05f to 0.08f, 0.18f to 0.03f, 0.35f to 0.12f, 0.52f to 0.05f,
            0.70f to 0.09f, 0.85f to 0.04f, 0.92f to 0.15f, 0.10f to 0.20f,
            0.28f to 0.17f, 0.44f to 0.22f, 0.62f to 0.18f, 0.78f to 0.25f,
            0.07f to 0.30f, 0.22f to 0.35f, 0.40f to 0.28f, 0.58f to 0.32f,
            0.76f to 0.29f, 0.90f to 0.36f
        )
        for ((rx, ry) in starPositions) {
            drawCircle(Color.White.copy(alpha = 0.6f), radius = 1.5f,
                center = Offset(rx * canvasW, ry * canvasH))
        }
    }

    // Plattformen
    for (plat in gs.platforms) {
        val sy = gs.screenY(plat.worldY)
        if (sy < -gs.platH - 10f || sy > canvasH + 10f) continue
        val alpha = if (plat.broken) (1f - plat.breakTimer).coerceIn(0f, 1f) else 1f
        val ox    = if (plat.broken) sin(plat.breakTimer * PI.toFloat() * 4f) * 6f * gs.scale else 0f
        drawJodPlatform(plat.worldX + ox, sy, plat.width, plat.type, alpha, gs.platH, gs.scale)
    }

    // Items
    val specialItemSz = gs.playerH * 0.75f  // 50 % größer als zuvor (war playerH / 2f)
    for (item in gs.items) {
        if (item.collected) continue
        val sy = gs.screenY(item.worldY)
        if (sy < -gs.itemSz * 2f || sy > canvasH + gs.itemSz * 2f) continue
        when (item.type) {
            JodItemType.SPRING  -> drawSpring(item.worldX, sy, gs.itemSz)
            JodItemType.JETPACK -> if (jetpackItemBitmap != null) {
                val jH = specialItemSz
                val jW = jH * (712f / 992f)
                drawImage(jetpackItemBitmap,
                    dstOffset = IntOffset(item.worldX.toInt(), sy.toInt()),
                    dstSize = IntSize(jW.toInt(), jH.toInt()))
            } else drawJetpack(item.worldX, sy, gs.itemSz)
            JodItemType.SHIELD  -> if (shieldItemBitmap != null) {
                drawImage(shieldItemBitmap,
                    dstOffset = IntOffset(item.worldX.toInt(), sy.toInt()),
                    dstSize = IntSize(specialItemSz.toInt(), specialItemSz.toInt()))
            }
            JodItemType.HEART   -> if (heartItemBitmap != null) {
                drawImage(heartItemBitmap,
                    dstOffset = IntOffset(item.worldX.toInt(), sy.toInt()),
                    dstSize = IntSize(specialItemSz.toInt(), specialItemSz.toInt()))
            }
            JodItemType.BUBBLE  -> if (bubbleBitmap != null) {
                val bH = specialItemSz
                val bW = bH * (196f / 256f)
                drawImage(bubbleBitmap,
                    dstOffset = IntOffset(item.worldX.toInt(), sy.toInt()),
                    dstSize = IntSize(bW.toInt(), bH.toInt()))
            }
            JodItemType.WORMHOLE -> {
                val wSz = specialItemSz * 1.4f
                val pulse = (sin(gs.frameCount * 0.08f) * 0.12f + 1f).toFloat()
                val wSzP = wSz * pulse
                val wX = item.worldX - (wSzP - wSz) / 2f
                val wY = sy - (wSzP - wSz) / 2f
                if (wormholeBitmap != null) {
                    drawImage(wormholeBitmap,
                        dstOffset = IntOffset(wX.toInt(), wY.toInt()),
                        dstSize = IntSize(wSzP.toInt(), wSzP.toInt()))
                } else {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFAA00FF), Color(0xFF6600CC), Color.Transparent),
                            center = Offset(item.worldX + wSz / 2f, sy + wSz / 2f),
                            radius = wSzP / 2f
                        ),
                        radius = wSzP / 2f,
                        center = Offset(item.worldX + wSz / 2f, sy + wSz / 2f)
                    )
                }
            }
        }
    }

    // Gegner
    for (enemy in gs.enemies) {
        val sy = gs.screenY(enemy.worldY)
        if (sy < -enemy.h * 2f || sy > canvasH + enemy.h * 2f) continue
        drawJodEnemy(enemy.worldX, sy, enemy.w, enemy.h, gs.scale, gs.frameCount, gs.level, monsterLvl6Bitmap)
    }

    // Monster-Warn-Pfeile (nur Level 1–5): roter ↑-Pfeil am oberen Rand für Monster außerhalb des Bildschirms
    if (gs.level <= 5) {
        val pulse = (sin(gs.frameCount * 0.12f) * 0.35f + 0.65f).toFloat()
        val arrowColor = Color(1f, 0.42f, 0f, pulse)
        val arrowSz = 13f * gs.scale
        val arrowTopY = 10f * gs.scale
        for (enemy in gs.enemies) {
            if (gs.screenY(enemy.worldY) >= canvasH * 0.3f) continue  // Warnung: Monster im oberen Drittel oder darüber
            val cx = (enemy.worldX + enemy.w / 2f).coerceIn(arrowSz + 4f, canvasW - arrowSz - 4f)
            val path = Path().apply {
                moveTo(cx, arrowTopY)                                        // Spitze (oben)
                lineTo(cx - arrowSz * 0.65f, arrowTopY + arrowSz)           // unten links
                lineTo(cx - arrowSz * 0.25f, arrowTopY + arrowSz)           // Einzug links
                lineTo(cx - arrowSz * 0.25f, arrowTopY + arrowSz * 1.85f)   // Schaft unten links
                lineTo(cx + arrowSz * 0.25f, arrowTopY + arrowSz * 1.85f)   // Schaft unten rechts
                lineTo(cx + arrowSz * 0.25f, arrowTopY + arrowSz)           // Einzug rechts
                lineTo(cx + arrowSz * 0.65f, arrowTopY + arrowSz)           // unten rechts
                close()
            }
            drawPath(path, arrowColor)
        }
    }

    // Spieler
    val psx = gs.screenX(gs.playerWorldX)
    val psy = gs.screenY(gs.playerWorldY)

    val selectedBitmap: ImageBitmap? = when {
        gs.shieldMs > 0L -> if (gs.facingRight) charShieldRightBitmap else charShieldLeftBitmap
        gs.isJetpack -> if (gs.facingRight) charRightJetpackBitmap else charLeftJetpackBitmap
        gs.velY < -2f * gs.scale -> charUpBitmap
        else -> if (gs.facingRight) charRightBitmap else charLeftBitmap
    }

    if (selectedBitmap != null) {
        drawImage(
            image = selectedBitmap,
            dstOffset = IntOffset(psx.toInt(), psy.toInt()),
            dstSize = IntSize(gs.playerW.toInt(), gs.playerH.toInt())
        )
    } else {
        drawDoodlestein(psx, psy, gs.facingRight, gs.isJetpack, gs.playerW, gs.playerH)
    }

    // Bubble-Overlay über Spielerfigur
    if (gs.isBubble && bubbleBitmap != null) {
        val bW = gs.playerW * 1.3f
        val bH = bW * (256f / 196f)
        val bX = psx + gs.playerW / 2f - bW / 2f
        val bY = psy + gs.playerH / 2f - bH / 2f
        drawImage(bubbleBitmap,
            dstOffset = IntOffset(bX.toInt(), bY.toInt()),
            dstSize = IntSize(bW.toInt(), bH.toInt()))
    }

    // Jetpack-Flammen
    if (gs.isJetpack && gs.jetpackMs > 0L) {
        val cx   = psx + gs.playerW / 2f
        val flamY = psy + gs.playerH + 4f * gs.scale
        drawJetpackFlame(cx, flamY, gs.jetpackMs.toFloat() / JOD_JETPACK_MS.toFloat(), gs.scale)
    }

    // Score-Popups
    for (pop in gs.popups) {
        val sy = gs.screenY(pop.worldY)
        drawContext.canvas.nativeCanvas.apply {
            val isNeg = pop.text.startsWith("-")
            val paint = android.graphics.Paint().apply {
                color = if (isNeg)
                    android.graphics.Color.argb((pop.alpha * 255).toInt(), 255, 80, 50)
                else
                    android.graphics.Color.argb((pop.alpha * 255).toInt(), 255, 220, 50)
                textSize = if (isNeg) 30f else 26f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(pop.text, pop.worldX, sy, paint)
        }
    }
}

private fun DrawScope.drawJodEnemy(x: Float, y: Float, w: Float, h: Float, scale: Float, frame: Long, level: Int = 1, monsterBitmap: ImageBitmap? = null) {
    if (level >= 6 && monsterBitmap != null) {
        drawImage(
            image = monsterBitmap,
            dstOffset = IntOffset(x.toInt(), y.toInt()),
            dstSize = IntSize(w.toInt(), h.toInt())
        )
        return
    }
    val bounce = sin(frame * 0.15f) * 2.5f * scale
    // Körper
    drawRoundRect(
        Brush.radialGradient(
            listOf(Color(0xFFFF6D00), Color(0xFFE65100)),
            center = Offset(x + w * 0.5f, y + h * 0.4f + bounce), radius = w * 0.7f
        ),
        topLeft = Offset(x, y + bounce),
        size = Size(w, h * 0.78f), cornerRadius = CornerRadius(8f * scale)
    )
    // Augen
    drawCircle(Color.White, radius = w * 0.13f, center = Offset(x + w * 0.28f, y + h * 0.27f + bounce))
    drawCircle(Color.White, radius = w * 0.13f, center = Offset(x + w * 0.72f, y + h * 0.27f + bounce))
    drawCircle(Color(0xFF212121), radius = w * 0.07f, center = Offset(x + w * 0.31f, y + h * 0.28f + bounce))
    drawCircle(Color(0xFF212121), radius = w * 0.07f, center = Offset(x + w * 0.69f, y + h * 0.28f + bounce))
    // Böser Mund
    drawArc(Color(0xFF212121).copy(alpha = 0.85f),
        startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(x + w * 0.25f, y + h * 0.47f + bounce),
        size = Size(w * 0.50f, h * 0.18f), style = Stroke(2f * scale))
    // Hörner
    drawLine(Color(0xFFFF6F00),
        start = Offset(x + w * 0.22f, y + bounce),
        end   = Offset(x + w * 0.10f, y - h * 0.18f + bounce),
        strokeWidth = 3f * scale)
    drawLine(Color(0xFFFF6F00),
        start = Offset(x + w * 0.78f, y + bounce),
        end   = Offset(x + w * 0.90f, y - h * 0.18f + bounce),
        strokeWidth = 3f * scale)
    // Beine (wackeln alternierend)
    val legOff = sin(frame * 0.22f) * 3.5f * scale
    drawRoundRect(Color(0xFFB71C1C),
        topLeft = Offset(x + w * 0.12f, y + h * 0.76f + bounce - legOff),
        size = Size(w * 0.28f, h * 0.26f), cornerRadius = CornerRadius(4f * scale))
    drawRoundRect(Color(0xFFB71C1C),
        topLeft = Offset(x + w * 0.60f, y + h * 0.76f + bounce + legOff),
        size = Size(w * 0.28f, h * 0.26f), cornerRadius = CornerRadius(4f * scale))
}

private fun DrawScope.drawJodPlatform(x: Float, y: Float, w: Float, type: JodPlatType, alpha: Float, platH: Float, scale: Float) {
    val h  = platH
    val cr = CornerRadius(4f * scale)
    when (type) {
        JodPlatType.GRASS -> {
            val grassTopH = h * 0.45f
            drawRoundRect(
                Color(0xFF5D4037).copy(alpha = alpha),
                topLeft = Offset(x, y + grassTopH), size = Size(w, h - grassTopH + 2f),
                cornerRadius = cr
            )
            drawRoundRect(
                Color(0xFF4CAF50).copy(alpha = alpha),
                topLeft = Offset(x, y), size = Size(w, grassTopH + 2f),
                cornerRadius = CornerRadius(4f * scale)
            )
            for (i in 0..3) {
                val gx = x + 8f * scale + i * (w / 4.5f)
                drawLine(Color(0xFF81C784).copy(alpha = alpha * 0.8f),
                    start = Offset(gx, y + 2f * scale), end = Offset(gx - 2f * scale, y - 4f * scale),
                    strokeWidth = 2f * scale)
            }
        }
        JodPlatType.WOOD -> {
            drawRoundRect(
                Color(0xFF8D6E63).copy(alpha = alpha),
                topLeft = Offset(x, y), size = Size(w, h), cornerRadius = cr
            )
            val lineStep = w / 4f
            for (i in 1..3) {
                drawLine(Color(0xFF6D4C41).copy(alpha = alpha * 0.5f),
                    start = Offset(x + i * lineStep, y + 1f * scale),
                    end   = Offset(x + i * lineStep, y + h - 1f * scale), strokeWidth = 1.5f * scale)
            }
            drawLine(Color(0xFFA1887F).copy(alpha = alpha),
                start = Offset(x + 2f * scale, y + 1f * scale),
                end   = Offset(x + w - 2f * scale, y + 1f * scale), strokeWidth = 2f * scale)
        }
        JodPlatType.STONE -> {
            drawRoundRect(
                Color(0xFF78909C).copy(alpha = alpha),
                topLeft = Offset(x, y), size = Size(w, h), cornerRadius = cr
            )
            drawLine(Color(0xFF546E7A).copy(alpha = alpha * 0.6f),
                start = Offset(x + w * 0.3f, y + 2f * scale),
                end   = Offset(x + w * 0.7f, y + 2f * scale), strokeWidth = 1.5f * scale)
            drawRoundRect(
                Color.White.copy(alpha = alpha * 0.18f),
                topLeft = Offset(x + 2f * scale, y + 1f * scale),
                size = Size(w - 4f * scale, h * 0.35f), cornerRadius = CornerRadius(2f * scale)
            )
        }
        JodPlatType.METAL -> {
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color(0xFFB0BEC5).copy(alpha = alpha), Color(0xFF607D8B).copy(alpha = alpha),
                           Color(0xFFCFD8DC).copy(alpha = alpha)),
                    start = Offset(x, y), end = Offset(x + w, y + h)
                ),
                topLeft = Offset(x, y), size = Size(w, h), cornerRadius = cr
            )
            for (i in 0..1) {
                val nx = x + 6f * scale + i * (w - 12f * scale)
                drawCircle(Color(0xFF455A64).copy(alpha = alpha), radius = 3f * scale,
                    center = Offset(nx, y + h / 2f))
            }
        }
    }
}

private fun DrawScope.drawSpring(x: Float, y: Float, itemSz: Float) {
    val cx      = x + itemSz / 2f
    val springH = itemSz
    val springW = itemSz * 0.7f
    val lx      = cx - springW / 2f
    val rx      = cx + springW / 2f
    val scale   = itemSz / 22f
    val segH    = springH / 5
    for (i in 0 until 5) {
        val ty    = y + i * segH
        val by    = ty + segH
        val fromX = if (i % 2 == 0) lx else rx
        val toX   = if (i % 2 == 0) rx else lx
        drawLine(Color(0xFFFFEB3B), start = Offset(fromX, ty), end = Offset(toX, by),
            strokeWidth = 3f * scale)
    }
    drawRoundRect(Color(0xFFFFC107),
        topLeft = Offset(cx - itemSz * 0.4f, y + springH - 3f * scale),
        size = Size(itemSz * 0.8f, 6f * scale), cornerRadius = CornerRadius(2f * scale))
}

private fun DrawScope.drawJetpack(x: Float, y: Float, itemSz: Float) {
    val w     = itemSz
    val h     = itemSz
    val scale = itemSz / 22f
    drawRoundRect(Color(0xFFE53935),
        topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(4f * scale))
    for (i in 0..1) {
        val dx = x + 3f * scale + i * (w / 2f)
        drawRoundRect(Color(0xFFFF7043),
            topLeft = Offset(dx, y + h - 5f * scale), size = Size(w / 2.5f, 8f * scale),
            cornerRadius = CornerRadius(2f * scale))
    }
    drawLine(Color(0xFFFFD600),
        start = Offset(x + w * 0.25f, y + h + 2f * scale),
        end   = Offset(x + w * 0.25f, y + h + 8f * scale), strokeWidth = 3f * scale)
    drawLine(Color(0xFFFFD600),
        start = Offset(x + w * 0.75f, y + h + 2f * scale),
        end   = Offset(x + w * 0.75f, y + h + 8f * scale), strokeWidth = 3f * scale)
}

private fun DrawScope.drawDoodlestein(x: Float, y: Float, facingRight: Boolean, hasJetpack: Boolean, w: Float, h: Float) {
    withTransform({
        if (!facingRight) scale(-1f, 1f, pivot = Offset(x + w / 2f, y + h / 2f))
    }) {
        drawRoundRect(
            Brush.radialGradient(
                listOf(Color(0xFF9E9E9E), Color(0xFF616161)),
                center = Offset(x + w * 0.4f, y + h * 0.35f), radius = w * 0.8f
            ),
            topLeft = Offset(x, y + h * 0.15f),
            size = Size(w, h * 0.85f), cornerRadius = CornerRadius(10f)
        )
        drawRoundRect(
            Brush.radialGradient(
                listOf(Color(0xFFAAAAAA), Color(0xFF757575)),
                center = Offset(x + w * 0.4f, y + h * 0.08f), radius = w * 0.6f
            ),
            topLeft = Offset(x + w * 0.05f, y),
            size = Size(w * 0.9f, h * 0.48f), cornerRadius = CornerRadius(12f)
        )
        val sw = 1.5f * (w / JOD_PLAYER_W_B)
        drawLine(Color(0xFF424242).copy(alpha = 0.5f),
            start = Offset(x + w * 0.6f, y + h * 0.18f),
            end   = Offset(x + w * 0.75f, y + h * 0.40f), strokeWidth = sw)
        drawLine(Color(0xFF424242).copy(alpha = 0.4f),
            start = Offset(x + w * 0.25f, y + h * 0.55f),
            end   = Offset(x + w * 0.15f, y + h * 0.72f), strokeWidth = sw)
        drawCircle(Color.White, radius = w * 0.12f, center = Offset(x + w * 0.30f, y + h * 0.22f))
        drawCircle(Color.White, radius = w * 0.12f, center = Offset(x + w * 0.65f, y + h * 0.22f))
        drawCircle(Color(0xFF212121), radius = w * 0.07f, center = Offset(x + w * 0.33f, y + h * 0.23f))
        drawCircle(Color(0xFF212121), radius = w * 0.07f, center = Offset(x + w * 0.68f, y + h * 0.23f))
        drawCircle(Color.White, radius = w * 0.03f, center = Offset(x + w * 0.29f, y + h * 0.19f))
        drawCircle(Color.White, radius = w * 0.03f, center = Offset(x + w * 0.64f, y + h * 0.19f))
        drawArc(Color(0xFF424242), startAngle = 10f, sweepAngle = 160f, useCenter = false,
            topLeft = Offset(x + w * 0.28f, y + h * 0.32f),
            size = Size(w * 0.44f, h * 0.14f), style = Stroke(2.5f * (w / JOD_PLAYER_W_B)))
        drawRoundRect(Color(0xFF757575),
            topLeft = Offset(x + w * 0.12f, y + h * 0.88f),
            size = Size(w * 0.28f, h * 0.14f), cornerRadius = CornerRadius(4f))
        drawRoundRect(Color(0xFF757575),
            topLeft = Offset(x + w * 0.60f, y + h * 0.88f),
            size = Size(w * 0.28f, h * 0.14f), cornerRadius = CornerRadius(4f))
        if (hasJetpack) {
            drawRoundRect(Color(0xFFE53935),
                topLeft = Offset(x - w * 0.15f, y + h * 0.18f),
                size = Size(w * 0.22f, h * 0.50f), cornerRadius = CornerRadius(4f))
        }
    }
}

private fun DrawScope.drawJetpackFlame(cx: Float, y: Float, progress: Float, scale: Float) {
    val intensity = (0.5f + sin(progress * 20f) * 0.3f).coerceIn(0f, 1f)
    val r         = (16f * intensity + 6f) * scale
    drawOval(
        Brush.radialGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFFFD600), Color.Transparent),
            center = Offset(cx, y + 8f * scale), radius = r
        ),
        topLeft = Offset(cx - 12f * scale, y),
        size = Size(24f * scale, (24f * intensity + 8f) * scale)
    )
}

// ─── Haupt-Composable ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpOrDieScreen(viewModel: MainViewModel, onNavigateBack: () -> Unit) {

    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        coroutineScope.launch {
            try { viewModel.updateJodContinuousPlay("reset") } catch (_: Exception) {}
        }
        onNavigateBack()
    }

    val view = LocalView.current
    val window = remember { (view.context as android.app.Activity).window }
    val insetsController = remember { androidx.core.view.WindowCompat.getInsetsController(window, view) }
    @Suppress("DEPRECATION")
    val prevStatusBarColor = remember { window.statusBarColor }
    val prevLightStatus = remember { insetsController.isAppearanceLightStatusBars }

    // Bei jeder Recomposition Statusbar-Farbe erzwingen (SecureChatTheme's SideEffect überschreiben)
    SideEffect {
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.parseColor("#1A0A3A")
        insetsController.isAppearanceLightStatusBars = false
    }

    DisposableEffect(Unit) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            @Suppress("DEPRECATION")
            window.statusBarColor = prevStatusBarColor
            insetsController.isAppearanceLightStatusBars = prevLightStatus
        }
    }

    val context = LocalContext.current

    // ─── SoundPool ───────────────────────────────────────────────────────────
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val sndJump        = remember { soundPool.load(context, R.raw.jod_jump,        1) }
    val sndJumpArcade  = remember { soundPool.load(context, R.raw.jod_jump_arcade, 1) }
    val sndFalling     = remember { soundPool.load(context, R.raw.jod_falling,     1) }
    val sndSpring      = remember { soundPool.load(context, R.raw.jod_spring,      1) }
    val sndJetpack     = remember { soundPool.load(context, R.raw.jod_jetpack,     1) }
    val sndSkill       = remember { soundPool.load(context, R.raw.jod_skill,       1) }
    val sndShield      = remember { soundPool.load(context, R.raw.jod_shield,      1) }
    val sndWormhole    = remember { soundPool.load(context, R.raw.jod_wormhole,    1) }
    var fallingStreamId  by remember { mutableIntStateOf(0) }
    var fallingActive    by remember { mutableStateOf(false) }
    var jetpackStreamId  by remember { mutableIntStateOf(0) }
    var jetpackActive    by remember { mutableStateOf(false) }

    DisposableEffect(soundPool) {
        onDispose { soundPool.release() }
    }

    // Hintergrundbilder laden (Index = Level - 1)
    val bgBitmaps = remember {
        listOf(
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_1)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_2)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_3)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_4)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_5)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_6)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_7)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_8)?.asImageBitmap(),
            BitmapFactory.decodeResource(context.resources, R.drawable.jod_bg_9)?.asImageBitmap()
        )
    }

    // Figuren-Sprites laden (Richtungs- und Jetpack-Sprites)
    val charLeftBmp        = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_char_left)?.asImageBitmap() }
    val charRightBmp       = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_char_right)?.asImageBitmap() }
    val charUpBmp          = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_char_1_jump)?.asImageBitmap() }
    val charLeftJetpackBmp  = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_char_left_jetpack)?.asImageBitmap() }
    val charRightJetpackBmp = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_char_right_jetpack)?.asImageBitmap() }
    val monsterLvl6Bmp      = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_monster_lvl6)?.asImageBitmap() }
    val charShieldLeftBmp   = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_char_shield_left)?.asImageBitmap() }
    val charShieldRightBmp  = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_char_shield_right)?.asImageBitmap() }
    val shieldItemBmp       = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_item_shield)?.asImageBitmap() }
    val heartItemBmp        = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_item_heart)?.asImageBitmap() }
    val jetpackItemBmp      = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_item_jetpack)?.asImageBitmap() }
    val bubbleItemBmp       = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_item_bubble)?.asImageBitmap() }
    val wormholeBmp         = remember { BitmapFactory.decodeResource(context.resources, R.drawable.jod_wormhole)?.asImageBitmap() }

    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }
    var sessionHighScore by remember { mutableIntStateOf(0) }
    var restartTrigger by remember { mutableIntStateOf(0) }
    var scoreSubmitted by remember { mutableStateOf(false) }
    var jodContinuousPlay by remember { mutableIntStateOf(0) }
    var jodHoleAvailable by remember { mutableStateOf(false) }

    // JOD-Status vom Server laden
    LaunchedEffect(Unit) {
        try {
            val resp = viewModel.getJodStatus()
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) {
                    jodContinuousPlay = body.continuousPlay
                    jodHoleAvailable = body.holeAvailable
                }
            }
        } catch (_: Exception) {}
    }
    val gsState = remember(restartTrigger, canvasW > 0f && canvasH > 0f) {
        if (canvasW > 0f && canvasH > 0f) {
            JodGs(canvasW, canvasH).also {
                initJodPlatforms(it, canvasW, canvasH)
                it.highScore = sessionHighScore
                it.continuousPlay = jodContinuousPlay
                it.holeAvailable = jodHoleAvailable
            }
        } else null
    }

    // gsState-Felder aktualisieren wenn Server-Status später eintrifft
    LaunchedEffect(jodContinuousPlay, jodHoleAvailable) {
        gsState?.let {
            it.continuousPlay = jodContinuousPlay
            it.holeAvailable = jodHoleAvailable
        }
    }

    val sensor = remember { JodSensor() }
    DisposableEffect(Unit) {
        val sm  = context.getSystemService(SensorManager::class.java) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { sensor.ax = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, acc, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    var renderTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(gsState) {
        var prevGameOver = false
        while (isActive) {
            gsState?.let { g ->
                stepJod(g, canvasW, canvasH, sensor)

                // Score sofort beim Game-Over-Übergang speichern (nur wenn neuer Bestwert)
                if (g.gameOver && !prevGameOver) {
                    if (g.score > 0 && g.score >= g.highScore && !scoreSubmitted) {
                        scoreSubmitted = true
                        launch {
                            try { viewModel.submitJodScore(g.score) } catch (_: Exception) {}
                        }
                    }
                }
                prevGameOver = g.gameOver

                // Falling-Sound verwalten
                if (g.isFallingSound && !fallingActive) {
                    fallingStreamId = soundPool.play(sndFalling, 1f, 1f, 0, -1, 1f)
                    fallingActive = true
                } else if (!g.isFallingSound && fallingActive) {
                    soundPool.stop(fallingStreamId)
                    fallingActive = false
                }

                // Jetpack-Sound verwalten
                if (g.isJetpack && !jetpackActive) {
                    jetpackStreamId = soundPool.play(sndJetpack, 1f, 1f, 0, -1, 1f)
                    jetpackActive = true
                } else if (!g.isJetpack && jetpackActive) {
                    soundPool.stop(jetpackStreamId)
                    jetpackActive = false
                }

                // Sprung-Sounds (einmalig)
                if (g.playJumpNormal) {
                    g.playJumpNormal = false
                    soundPool.play(sndJump, 1f, 1f, 0, 0, 1f)
                }
                if (g.playJumpMetal) {
                    g.playJumpMetal = false
                    soundPool.play(sndJumpArcade, 1f, 1f, 0, 0, 1f)
                }
                if (g.playSpring) {
                    g.playSpring = false
                    soundPool.play(sndSpring, 1f, 1f, 0, 0, 1f)
                }
                if (g.playItemSkill) {
                    g.playItemSkill = false
                    soundPool.play(sndSkill, 1f, 1f, 0, 0, 1f)
                }
                if (g.playItemShield) {
                    g.playItemShield = false
                    soundPool.play(sndShield, 1f, 1f, 0, 0, 1f)
                }
                if (g.playItemBubble) {
                    g.playItemBubble = false
                    soundPool.play(sndSkill, 1f, 1f, 0, 0, 1f)
                }
                if (g.playWormhole) {
                    g.playWormhole = false
                    soundPool.play(sndWormhole, 1f, 1f, 0, 0, 1f)
                }
                // Wurmloch benutzt → Server informieren
                if (g.wormholeTriggered) {
                    g.wormholeTriggered = false
                    jodContinuousPlay = 0
                    jodHoleAvailable = false
                    launch {
                        try { viewModel.jodWormholeUsed() } catch (_: Exception) {}
                    }
                }
            }
            renderTick++
            delay(16)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0820))) {
        TopAppBar(
            title = { Text("JUMP or Die", fontWeight = FontWeight.ExtraBold, color = Color.White) },
            navigationIcon = {
                IconButton(onClick = {
                    coroutineScope.launch {
                        try { viewModel.updateJodContinuousPlay("reset") } catch (_: Exception) {}
                    }
                    onNavigateBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
            },
            actions = {
                gsState?.let { g ->
                    @Suppress("UNUSED_EXPRESSION") renderTick
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)) {
                        Text("Lvl ${g.level}", color = Color(0xFFFFD600), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp))
                        Text("Score: ${g.score}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A0A3A))
        )

        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        val newW = size.width.toFloat()
                        val newH = size.height.toFloat()
                        if (canvasW != newW || canvasH != newH) {
                            canvasW = newW
                            canvasH = newH
                        }
                    }
            ) {
                @Suppress("UNUSED_EXPRESSION") renderTick
                val g = gsState ?: return@Canvas

                val levelIdx = (g.level - 1).coerceIn(0, bgBitmaps.size - 1)
                val bgBitmap = bgBitmaps[levelIdx]

                drawJodGame(g, size.width, size.height, bgBitmap,
                    charLeftBmp, charRightBmp, charUpBmp,
                    charLeftJetpackBmp, charRightJetpackBmp, monsterLvl6Bmp,
                    charShieldLeftBmp, charShieldRightBmp, shieldItemBmp, heartItemBmp, jetpackItemBmp, bubbleItemBmp,
                    wormholeBmp)
            }

            // Score + Best + Jetpack-Timer
            gsState?.let { g ->
                @Suppress("UNUSED_EXPRESSION") renderTick
                Column(
                    modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.45f)) {
                        Text("Best: ${g.highScore}", color = Color(0xFFFFD600), fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (g.isJetpack) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE53935).copy(alpha = 0.8f)) {
                            Text(
                                "🚀 ${"%.1f".format(g.jetpackMs / 1000f)}s",
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (g.isBubble) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF29B6F6).copy(alpha = 0.85f)) {
                            Text(
                                "🫧 ${"%.1f".format(g.bubbleMs / 1000f)}s",
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Level-Ansage beim Stufenwechsel
            gsState?.let { g ->
                @Suppress("UNUSED_EXPRESSION") renderTick
                if (g.levelAnnounceMs > 0L) {
                    val alpha = (g.levelAnnounceMs / 2500f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = alpha * 0.75f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("LEVEL ${g.level}", fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFD600).copy(alpha = alpha))
                                Text(JOD_LEVEL_NAMES[g.level] ?: "", fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = alpha))
                            }
                        }
                    }
                }
            }

            // Game-Over-Overlay
            gsState?.let { g ->
                @Suppress("UNUSED_EXPRESSION") renderTick
                if (g.gameOver) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0A3A)),
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
                                Text("Bestleistung: ${g.highScore}", fontSize = 16.sp,
                                    color = Color(0xFFFFD600))
                                Text("Erreicht: Level ${g.level} – ${JOD_LEVEL_NAMES[g.level] ?: ""}",
                                    fontSize = 14.sp, color = Color(0xFF90CAF9))
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        sessionHighScore = g.highScore
                                        scoreSubmitted = false
                                        jodContinuousPlay++
                                        restartTrigger++
                                        coroutineScope.launch {
                                            try { viewModel.updateJodContinuousPlay("increment") } catch (_: Exception) {}
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Nochmal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Bottom Bar (30 dp) ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color(0xFF1A0A3A)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            @Suppress("UNUSED_EXPRESSION") renderTick
            val g = gsState
            // Herz-Anzeige unten links wenn extra Leben vorhanden
            if (g != null && g.extraLives > 0 && heartItemBmp != null) {
                repeat(g.extraLives) {
                    Image(
                        bitmap = heartItemBmp,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(22.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // Schild-Timer rechts wenn aktiv
            if (g != null && g.shieldMs > 0L) {
                Text(
                    "🛡 ${"%.1f".format(g.shieldMs / 1000f)}s",
                    color = Color(0xFF80D8FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
        }
    }
}
