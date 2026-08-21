package com.securechat.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.network.GameMonthWinner
import com.securechat.app.data.network.GameRankingEntry
import com.securechat.app.data.network.JodLeaderboardEntry
import com.securechat.app.ui.ChildPermission
import com.securechat.app.ui.MainViewModel
import java.time.Instant
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import com.securechat.app.R

// ─────────────────────────────────────────────────────────────────
// Konstanten
// ─────────────────────────────────────────────────────────────────
private const val WORLD_WIDTH  = 5000f
private const val PLAYER_W     = 36f
private const val PLAYER_H     = 48f
private const val GRAVITY      = 0.7f
private const val JUMP_VEL     = -13.5f
private const val MAX_H_SPEED  = 5.0f
private const val FLAG_X       = 4650f
private const val SYNC_MS      = 60L   // ~16fps Sync
private const val HILL_X       = 4510f
private const val HILL_W       = 240f
private const val HILL_H       = 80f
// Warp-Röhre & Himmelsbereich
private const val ZOOM                   = 3.5f
private const val WARP_PIPE_X            = 1600f
private const val SKY_PLATFORM_X         = 200f
private const val SKY_PLATFORM_W         = 400f
private const val SKY_PLATFORM_Y_OFFSET  = 1200f  // sehr weit oben – nur per Röhre erreichbar
private const val SKY_RETURN_PIPE_REL_X  = 10f    // X-Offset auf der Himmelsplattform (links)
private const val SKY_COIN_BASE_ID       = 500    // IDs >= this = Himmelsmünzen
private const val BLOCK_COIN_BASE_ID     = 1000   // IDs >= this = Block-Münzen
private const val WALL_GRAB_DURATION_MS  = 200f   // wie lange der Charakter an der Wand hält (ms)
private const val WALL_SLIDE_SPEED       = 1.5f   // langsames Runterrutschen nach Ablauf

// ─────────────────────────────────────────────────────────────────
// Datenklassen
// ─────────────────────────────────────────────────────────────────
enum class GameCharacter(val label: String) {
    MARIO_ALT("Tommy"),
    LUIGI_ALT("Jimmy"),
    PEACH("Lucy")
}

enum class GamePhase {
    HUB, GAME_SELECT, INVITE_SENT, INVITE_RECEIVED, CHAR_SELECT, COUNTDOWN, PLAYING, RESULTS,
    ACTIVITY_PLAYING, ACTIVITY_RESULTS, TIC_TAC_TOE_PLAYING, TIC_TAC_TOE_RESULTS
}

enum class GameType { JUMP_AND_RUN, ACTIVITY, TIC_TAC_TOE, TILT_N_DROP, JUMP_OR_DIE, PINBALL }

// Normalisierter Zeichen-Strich (Punkte in [0,1] relativ zur Canvas-Größe)
data class DrawStroke(
    val points: List<Float>,   // [x0,y0, x1,y1, …] normalisiert
    val colorArgb: Long = 0xFF000000L,
    val width: Float = 10f
)

// ─────────────────────────────────────────────────────────────────
// Activity-Wortliste & Hilfsfunktionen
// ─────────────────────────────────────────────────────────────────
private val ACTIVITY_WORDS = listOf(
    "Hund", "Katze", "Haus", "Auto", "Baum", "Sonne", "Mond", "Stern", "Blume", "Berg",
    "Fluss", "Meer", "Boot", "Flugzeug", "Zug", "Fahrrad", "Brücke", "Schloss", "Turm", "Kirche",
    "Pizza", "Kuchen", "Apfel", "Banane", "Kaffee", "Telefon", "Computer", "Buch", "Gitarre", "Klavier",
    "Fußball", "Tennis", "Regenbogen", "Wolke", "Regen", "Schnee", "Feuer", "Elefant", "Löwe", "Affe",
    "Pinguin", "Delfin", "Krone", "Schlüssel", "Herz", "Blitz", "Igel", "Pilz", "Rakete", "Leuchtturm",
    "Schmetterling", "Einhorn", "Drache", "Roboter", "Pirat", "Brille", "Hut", "Schal", "Rucksack", "Uhr",
    "Hammer", "Schere", "Pinsel", "Lampe", "Stuhl", "Eiffelturm", "Pyramide", "Vulkan", "Insel", "Wasserfall",
    "Sandburg", "Anker", "Kompass", "Trommel", "Trompete", "Kaktus", "Ballon", "Würfel", "Spiegel", "Treppe",
    "Schiff", "Burg", "Drachen", "Käse", "Koffer", "Socke", "Tasche", "Fenster", "Tisch", "Lampe"
)

private val ACTIVITY_COLORS: List<Long> = listOf(
    0xFF000000L, 0xFFE53935L, 0xFF1E88E5L, 0xFF43A047L,
    0xFFFFD600L, 0xFFFF8F00L, 0xFF8E24AAL, 0xFFE91E63L,
    0xFF00ACC1L, 0xFFFFFFFFL   // Weiß = Radiergummi
)

private fun levenshteinDistance(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                   else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
    }
    return dp[a.length][b.length]
}

private fun isCorrectGuess(guess: String, word: String): Boolean {
    val g = guess.trim().lowercase()
    val w = word.trim().lowercase()
    if (g == w) return true
    val maxDist = when {
        w.length <= 4 -> 1
        w.length <= 7 -> 2
        else          -> 3
    }
    return levenshteinDistance(g, w) <= maxDist
}

data class GamePlatform(val x: Float, val y: Float, val w: Float, val h: Float = 26f)
data class GameCoin(val id: Int, val x: Float, val y: Float, var collected: Boolean = false)
data class GameBlock(
    val id: Int, val x: Float, val y: Float,
    val w: Float = 32f, val h: Float = 32f,
    val coinsLeft: Int = 1,
    val bumpOffset: Float = 0f
) {
    val coinCollected: Boolean get() = coinsLeft <= 0
}
data class CoinPopup(val x: Float, val y: Float, val alpha: Float = 1f)

data class GamePipe(val x: Float, val groundY: Float, val h: Float) {
    val topY: Float get() = groundY - h
    val w: Float = 55f
    val capW: Float = 63f
    val capH: Float = 12f
    val capY: Float get() = topY - capH
}

data class GameWall(val x: Float, val topY: Float, val w: Float, val h: Float)

// Tic-Tac-Toe Spielstein
data class TttCell(val row: Int, val col: Int, val player: Char, val placedAt: Long)

data class PlayerState(
    val x: Float = 200f,
    val y: Float = 0f,
    val velX: Float = 0f,
    val velY: Float = 0f,
    val onGround: Boolean = false,
    val facingRight: Boolean = true,
    val coinsCollected: Int = 0,
    val reachedGoal: Boolean = false,
    val collectedCoinIds: Set<Int> = emptySet(),
    val touchingWallSide: Int = 0,      // -1 = linke Wand, 1 = rechte Wand, 0 = keine
    val wallGrabTimer: Float = 0f       // verbleibende Haltezeit in ms
)

// ─────────────────────────────────────────────────────────────────
// Sprite-Daten
// ─────────────────────────────────────────────────────────────────
private data class CharSprites(
    val front: androidx.compose.ui.graphics.ImageBitmap,
    val idle:  androidx.compose.ui.graphics.ImageBitmap,
    val jump:  androidx.compose.ui.graphics.ImageBitmap,
    val walkA: androidx.compose.ui.graphics.ImageBitmap,
    val walkB: androidx.compose.ui.graphics.ImageBitmap,
)

private data class GameSprites(
    val mario:         CharSprites,
    val luigi:         CharSprites,
    val peach:         CharSprites,
    val blockActive:   androidx.compose.ui.graphics.ImageBitmap,
    val blockInactive: androidx.compose.ui.graphics.ImageBitmap,
    val groundTile:    androidx.compose.ui.graphics.ImageBitmap,
    val platformLeft:  androidx.compose.ui.graphics.ImageBitmap,
    val platformMid:   androidx.compose.ui.graphics.ImageBitmap,
    val platformRight: androidx.compose.ui.graphics.ImageBitmap,
    val cloudLeft:     androidx.compose.ui.graphics.ImageBitmap,
    val cloudMid:      androidx.compose.ui.graphics.ImageBitmap,
    val cloudRight:    androidx.compose.ui.graphics.ImageBitmap,
    val bgHills:       androidx.compose.ui.graphics.ImageBitmap,
)

@androidx.compose.runtime.Composable
private fun rememberGameSprites(): GameSprites {
    val ctx = LocalContext.current
    return androidx.compose.runtime.remember {
        fun img(id: Int) = BitmapFactory.decodeResource(ctx.resources, id).asImageBitmap()
        fun charSprites(colorName: String) = CharSprites(
            front = img(ctx.resources.getIdentifier("character_${colorName}_front",  "drawable", ctx.packageName)),
            idle  = img(ctx.resources.getIdentifier("character_${colorName}_idle",   "drawable", ctx.packageName)),
            jump  = img(ctx.resources.getIdentifier("character_${colorName}_jump",   "drawable", ctx.packageName)),
            walkA = img(ctx.resources.getIdentifier("character_${colorName}_walk_a", "drawable", ctx.packageName)),
            walkB = img(ctx.resources.getIdentifier("character_${colorName}_walk_b", "drawable", ctx.packageName)),
        )
        GameSprites(
            mario         = charSprites("yellow"),
            luigi         = charSprites("green"),
            peach         = charSprites("pink"),
            blockActive   = img(R.drawable.block_coin_active),
            blockInactive = img(R.drawable.block_coin),
            groundTile    = img(R.drawable.terrain_grass_block),
            platformLeft  = img(R.drawable.terrain_dirt_block_top_left),
            platformMid   = img(R.drawable.terrain_dirt_block_top),
            platformRight = img(R.drawable.terrain_dirt_block_top_right),
            cloudLeft     = img(R.drawable.terrain_dirt_cloud_left),
            cloudMid      = img(R.drawable.terrain_dirt_cloud_middle),
            cloudRight    = img(R.drawable.terrain_dirt_cloud_right),
            bgHills       = img(R.drawable.background_color_hills),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSprite(
    image: androidx.compose.ui.graphics.ImageBitmap,
    x: Float, y: Float, w: Float, h: Float,
    flipX: Boolean = false,
) {
    val iw = w.toInt().coerceAtLeast(1)
    val ih = h.toInt().coerceAtLeast(1)
    if (flipX) {
        scale(scaleX = -1f, scaleY = 1f, pivot = androidx.compose.ui.geometry.Offset(x + w / 2f, y + h / 2f)) {
            drawImage(image,
                dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()),
                dstSize   = androidx.compose.ui.unit.IntSize(iw, ih),
                filterQuality = FilterQuality.None)
        }
    } else {
        drawImage(image,
            dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()),
            dstSize   = androidx.compose.ui.unit.IntSize(iw, ih),
            filterQuality = FilterQuality.None)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlatformTiled(
    sprites: GameSprites,
    x: Float, y: Float, w: Float, h: Float,
    isSky: Boolean,
) {
    val tileW = h  // square tiles based on platform height
    val left  = if (isSky) sprites.cloudLeft  else sprites.platformLeft
    val mid   = if (isSky) sprites.cloudMid   else sprites.platformMid
    val right = if (isSky) sprites.cloudRight else sprites.platformRight
    if (w <= tileW * 2) {
        // Small platform: just stretch one tile
        drawSprite(mid, x, y, w, h)
        return
    }
    drawSprite(left, x, y, tileW, h)
    var mx = x + tileW
    while (mx + tileW < x + w - tileW) {
        drawSprite(mid, mx, y, tileW, h)
        mx += tileW
    }
    drawSprite(right, x + w - tileW, y, tileW, h)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGroundTiled(
    tile: androidx.compose.ui.graphics.ImageBitmap,
    startX: Float, y: Float, endX: Float, h: Float,
) {
    val tileW = h  // square tiles
    var tx = (startX / tileW).toInt() * tileW
    while (tx < endX) {
        val screenLeft = tx
        drawSprite(tile, screenLeft, y, tileW, h)
        tx += tileW
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCharSprite(
    sprites: CharSprites,
    x: Float, y: Float, w: Float, h: Float,
    facingRight: Boolean,
    walkFrame: Int,
    onGround: Boolean,
) {
    val sprite = when {
        !onGround          -> sprites.jump
        walkFrame == 0     -> sprites.walkA
        walkFrame == 2     -> sprites.walkB
        else               -> sprites.idle
    }
    drawSprite(sprite, x, y, w, h, flipX = !facingRight)
}

// ─────────────────────────────────────────────────────────────────
// Level-Daten
// ─────────────────────────────────────────────────────────────────
private fun buildPlatforms(groundY: Float): List<GamePlatform> = listOf(
    // Durchgehender Boden
    GamePlatform(0f, groundY, WORLD_WIDTH, 60f),
    // Reihe 1 – niedrig
    GamePlatform(280f,  groundY - 100f, 110f),
    GamePlatform(480f,  groundY - 170f, 90f),
    GamePlatform(680f,  groundY - 120f, 130f),
    GamePlatform(870f,  groundY - 200f, 100f),
    GamePlatform(1060f, groundY - 140f, 120f),
    GamePlatform(1250f, groundY - 220f, 100f),
    GamePlatform(1430f, groundY - 160f, 140f),
    GamePlatform(1640f, groundY - 240f, 90f),
    GamePlatform(1820f, groundY - 180f, 110f),
    GamePlatform(2010f, groundY - 260f, 100f),
    GamePlatform(2210f, groundY - 190f, 130f),
    GamePlatform(2410f, groundY - 130f, 110f),
    GamePlatform(2600f, groundY - 210f, 100f),
    GamePlatform(2790f, groundY - 280f, 120f),
    GamePlatform(2990f, groundY - 150f, 100f),
    GamePlatform(3190f, groundY - 230f, 130f),
    GamePlatform(3390f, groundY - 170f, 110f),
    GamePlatform(3580f, groundY - 250f, 90f),
    // Erweitertes Level (3750–4450)
    GamePlatform(3760f, groundY - 190f, 115f),
    GamePlatform(3950f, groundY - 260f, 100f),
    GamePlatform(4140f, groundY - 165f, 120f),
    GamePlatform(4320f, groundY - 240f, 95f),
    // Reihe 3 – vereinzelte Hochplattformen
    GamePlatform(550f,  groundY - 330f, 80f),
    GamePlatform(1350f, groundY - 350f, 85f),
    GamePlatform(2100f, groundY - 370f, 90f),
    GamePlatform(2900f, groundY - 360f, 80f),
    GamePlatform(3450f, groundY - 340f, 85f),
    GamePlatform(4090f, groundY - 380f, 90f),
    // Röhren-Landeplattformen (Oberkante der Kappen)
    GamePlatform(846f,  groundY - 102f, 48f),
    GamePlatform(2246f, groundY - 122f, 48f),
    // Anhöhe vor der Flagge
    GamePlatform(HILL_X, groundY - HILL_H, HILL_W, HILL_H),
    // Warp-Röhren-Kappe (Landeplattform für Röhre bei WARP_PIPE_X)
    GamePlatform(WARP_PIPE_X - 4f, groundY - 132f, 48f),
    // Geheime Himmelsplattform
    GamePlatform(SKY_PLATFORM_X, groundY - SKY_PLATFORM_Y_OFFSET, SKY_PLATFORM_W),
    // Rückkehr-Röhren-Kappe auf der Himmelsplattform
    GamePlatform(
        SKY_PLATFORM_X + SKY_RETURN_PIPE_REL_X - 4f,
        groundY - SKY_PLATFORM_Y_OFFSET - 92f,
        48f
    ),
)

private fun buildCoins(groundY: Float): List<GameCoin> {
    val coins = mutableListOf<GameCoin>()
    var id = 0
    // Münzen auf dem Boden: abwechselnd leicht schwebend oder nur per Sprung erreichbar
    val groundXList = listOf(150f, 350f, 550f, 750f, 950f, 1150f, 1350f, 1550f,
                             1750f, 1950f, 2150f, 2350f, 2550f, 2750f, 2950f, 3150f,
                             3350f, 3550f, 3700f, 3900f, 4100f, 4300f, 4480f)
    groundXList.forEachIndexed { index, gx ->
        val coinY = if (index % 2 == 0) groundY - 55f else groundY - 140f
        coins.add(GameCoin(id++, gx, coinY))
    }
    // Münzen auf Plattformen – leicht über Oberkante schwebend
    val plats = buildPlatforms(groundY).drop(1) // ohne Boden
    for (p in plats) {
        coins.add(GameCoin(id++, p.x + p.w / 2f - 12f, p.y - 45f))
        if (p.w > 100f) coins.add(GameCoin(id++, p.x + p.w / 2f + 12f, p.y - 45f))
    }
    // Geheime Himmelsmünzen – 3 Reihen, größere Abstände (40 px) zwischen den Münzen
    val skyPlatSurface = groundY - SKY_PLATFORM_Y_OFFSET
    for (row in 0 until 3) {
        val coinY = skyPlatSurface - 50f - row * 35f
        for (i in 0 until 9) {
            coins.add(GameCoin(SKY_COIN_BASE_ID + row * 9 + i,
                SKY_PLATFORM_X + 70f + i * 40f, coinY))
        }
    }
    return coins
}

private fun blockSurface(bx: Float, groundY: Float, platforms: List<GamePlatform>): Float {
    val cx = bx + 16f // block.w / 2
    return platforms
        .filter { p -> cx >= p.x && cx <= p.x + p.w && p.y >= groundY - 300f }
        .maxByOrNull { p -> p.y }
        ?.y ?: groundY
}

private fun buildBlocks(groundY: Float): List<GameBlock> {
    val rng = kotlin.random.Random.Default
    val plats = buildPlatforms(groundY).drop(1)
    val gaps = listOf(1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f)
    fun by(bx: Float, i: Int): Float {
        val surf = blockSurface(bx, groundY, plats)
        return surf - gaps[i] * PLAYER_H - 32f
    }
    return listOf(
        GameBlock(0,   340f, by(340f,  0), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(1,   620f, by(620f,  1), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(2,   860f, by(860f,  2), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(3,  1100f, by(1100f, 3), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(4,  1380f, by(1380f, 4), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(5,  1700f, by(1700f, 5), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(6,  1980f, by(1980f, 6), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(7,  2280f, by(2280f, 7), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(8,  2560f, by(2560f, 8), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(9,  2860f, by(2860f, 9), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(10, 3150f, by(3150f,10), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(11, 3460f, by(3460f,11), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(12, 3780f, by(3780f,12), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(13, 4060f, by(4060f,13), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(14, 4340f, by(4340f,14), coinsLeft = rng.nextInt(10) + 1),
    )
}

private fun buildPipes(groundY: Float): List<GamePipe> = listOf(
    GamePipe(870f,  groundY, 90f),
    GamePipe(2270f, groundY, 110f),
    GamePipe(WARP_PIPE_X, groundY, 120f),  // Warp-Röhre zum Himmel
)

// ─────────────────────────────────────────────────────────────────
// Level 2 – Höhlen-Daten
// ─────────────────────────────────────────────────────────────────
private fun buildPlatformsL2(groundY: Float): List<GamePlatform> = listOf(
    GamePlatform(0f, groundY, WORLD_WIDTH, 60f),
    // Kammer 1
    GamePlatform(250f,  groundY - 80f,  100f),
    GamePlatform(450f,  groundY - 150f, 80f),
    GamePlatform(640f,  groundY - 100f, 120f),
    GamePlatform(850f,  groundY - 170f, 90f),
    GamePlatform(1040f, groundY - 110f, 110f),
    // Kammer 2
    GamePlatform(1230f, groundY - 200f, 100f),
    GamePlatform(1420f, groundY - 130f, 130f),
    GamePlatform(1640f, groundY - 220f, 85f),
    GamePlatform(1840f, groundY - 160f, 100f),
    GamePlatform(2050f, groundY - 240f, 90f),
    GamePlatform(2260f, groundY - 170f, 120f),
    GamePlatform(2340f, groundY - 110f, 80f),   // Anlauf vor dem Schacht
    // Schacht-Plattformen (Mitte der Welt, x≈2500)
    GamePlatform(2490f, groundY - 130f, 70f),   // Etage 1
    GamePlatform(2490f, groundY - 260f, 70f),   // Etage 2
    GamePlatform(2490f, groundY - 390f, 70f),   // Etage 3
    GamePlatform(2490f, groundY - 510f, 300f),  // Austrittsbrücke nach rechts
    // Kammer 3
    GamePlatform(2680f, groundY - 200f, 95f),
    GamePlatform(2880f, groundY - 270f, 110f),
    GamePlatform(3090f, groundY - 140f, 100f),
    GamePlatform(3300f, groundY - 230f, 120f),
    GamePlatform(3510f, groundY - 160f, 90f),
    GamePlatform(3720f, groundY - 250f, 100f),
    // Kammer 4 – Ausgang
    GamePlatform(3940f, groundY - 180f, 110f),
    GamePlatform(4150f, groundY - 260f, 95f),
    GamePlatform(4360f, groundY - 140f, 120f),
    // Hochplattformen
    GamePlatform(500f,  groundY - 310f, 90f),
    GamePlatform(1300f, groundY - 340f, 80f),
    GamePlatform(2100f, groundY - 360f, 85f),
    GamePlatform(2900f, groundY - 330f, 90f),
    GamePlatform(3600f, groundY - 350f, 85f),
    GamePlatform(4200f, groundY - 370f, 95f),
    // Röhren-Landeplattformen
    GamePlatform(824f,  groundY - 92f,  48f),
    GamePlatform(2224f, groundY - 112f, 48f),
    // Stein-Anhöhe vor der Flagge
    GamePlatform(HILL_X, groundY - HILL_H, HILL_W, HILL_H),
)

private fun buildCoinsL2(groundY: Float): List<GameCoin> {
    val coins = mutableListOf<GameCoin>()
    var id = 0
    val groundXList = listOf(120f, 320f, 520f, 720f, 920f, 1120f, 1320f, 1520f,
                             1720f, 1920f, 2120f, 2320f, 2520f, 2720f, 2920f, 3120f,
                             3320f, 3520f, 3720f, 3920f, 4120f, 4320f, 4520f)
    groundXList.forEachIndexed { index, gx ->
        val coinY = if (index % 2 == 0) groundY - 55f else groundY - 130f
        coins.add(GameCoin(id++, gx, coinY))
    }
    val plats = buildPlatformsL2(groundY).drop(1)
    for (p in plats) {
        coins.add(GameCoin(id++, p.x + p.w / 2f - 12f, p.y - 45f))
        if (p.w > 100f) coins.add(GameCoin(id++, p.x + p.w / 2f + 12f, p.y - 45f))
    }
    return coins
}

private fun buildBlocksL2(groundY: Float): List<GameBlock> {
    val rng = kotlin.random.Random.Default
    val plats = buildPlatformsL2(groundY).drop(1)
    val gaps = listOf(1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f, 1.5f, 1.7f, 1.6f)
    fun by(bx: Float, i: Int): Float {
        val surf = blockSurface(bx, groundY, plats)
        return surf - gaps[i] * PLAYER_H - 32f
    }
    return listOf(
        GameBlock(0,   310f, by(310f,  0), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(1,   600f, by(600f,  1), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(2,   840f, by(840f,  2), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(3,  1080f, by(1080f, 3), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(4,  1360f, by(1360f, 4), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(5,  1680f, by(1680f, 5), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(6,  1960f, by(1960f, 6), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(7,  2260f, by(2260f, 7), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(8,  2540f, by(2540f, 8), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(9,  2840f, by(2840f, 9), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(10, 3130f, by(3130f,10), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(11, 3440f, by(3440f,11), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(12, 3760f, by(3760f,12), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(13, 4050f, by(4050f,13), coinsLeft = rng.nextInt(10) + 1),
        GameBlock(14, 4320f, by(4320f,14), coinsLeft = rng.nextInt(10) + 1),
    )
}

private fun buildPipesL2(groundY: Float): List<GamePipe> = listOf(
    GamePipe(844f,  groundY, 80f),
    GamePipe(2244f, groundY, 100f),
    GamePipe(3600f, groundY, 90f),
)

// Schacht-Wände (bei Weltmitte ~x=2500)
private fun buildWallsL2(groundY: Float): List<GameWall> = listOf(
    GameWall(2420f, groundY - 510f, 70f, 510f),  // linke Schachtwand
    GameWall(2560f, groundY - 510f, 70f, 510f),  // rechte Schachtwand
)

// ─────────────────────────────────────────────────────────────────
// Physik-Hilfsfunktionen
// ─────────────────────────────────────────────────────────────────
private fun applyPhysics(
    state: PlayerState,
    platforms: List<GamePlatform>,
    walls: List<GameWall>,
    groundY: Float,
    enableWallGrab: Boolean = false
): PlayerState {
    var x   = state.x + state.velX
    var y   = state.y + state.velY
    var vy  = state.velY + GRAVITY
    var vx  = state.velX
    var onG = false

    for (p in platforms) {
        val px  = x + PLAYER_W / 2f
        val bottom = y + PLAYER_H
        val prevBottom = state.y + PLAYER_H
        if (px >= p.x && px <= p.x + p.w) {
            if (prevBottom <= p.y && bottom >= p.y && vy > 0f) {
                y   = p.y - PLAYER_H
                vy  = 0f
                onG = true
                break
            }
        }
    }
    // Wand-Kollision (seitlich)
    var newWallSide = 0
    for (wall in walls) {
        val playerBottom = y + PLAYER_H
        val playerTop    = y
        if (playerBottom > wall.topY && playerTop < wall.topY + wall.h) {
            val prevRight = state.x + PLAYER_W
            val prevLeft  = state.x
            val newRight  = x + PLAYER_W
            val newLeft   = x
            if (prevRight <= wall.x && newRight > wall.x) {
                x  = wall.x - PLAYER_W
                vx = 0f
                newWallSide = 1
            } else if (prevLeft >= wall.x + wall.w && newLeft < wall.x + wall.w) {
                x  = wall.x + wall.w
                vx = 0f
                newWallSide = -1
            }
        }
    }
    // Wand-Greifen: 0,2 Sek. halten, dann langsam rutschen
    var newGrabTimer = state.wallGrabTimer
    if (enableWallGrab && newWallSide != 0 && !onG) {
        if (state.touchingWallSide == 0) {
            // gerade neu an Wand gekommen → Timer zurücksetzen
            newGrabTimer = WALL_GRAB_DURATION_MS
        } else {
            newGrabTimer = (newGrabTimer - 16f).coerceAtLeast(0f)
        }
        vy = if (newGrabTimer > 0f) 0f else vy.coerceAtMost(WALL_SLIDE_SPEED)
    } else {
        if (newWallSide == 0) newGrabTimer = 0f
    }
    // Kein Fall unter die Welt
    if (y + PLAYER_H > groundY + 60f) {
        y   = groundY - PLAYER_H
        vy  = 0f
        onG = true
    }
    // Welt-Rand
    x = x.coerceIn(0f, WORLD_WIDTH - PLAYER_W)
    return state.copy(
        x = x, y = y, velX = vx, velY = vy, onGround = onG,
        touchingWallSide = if (onG) 0 else newWallSide,
        wallGrabTimer = if (onG) 0f else newGrabTimer
    )
}

// ─────────────────────────────────────────────────────────────────
// Sound-Engine (PCM-Synthese, keine externen Dateien)
// ─────────────────────────────────────────────────────────────────
private object GameSoundPlayer {
    private val scope = CoroutineScope(Dispatchers.IO)
    private const val SAMPLE_RATE = 22050
    @Volatile private var lastJumpMs  = 0L
    @Volatile private var lastCoinMs  = 0L

    /** Generiert eine Schwingung als PCM-ShortArray.
     *  waveType: 0 = Rechteck (8-bit-Charakter), 1 = Sinus (weicher) */
    private fun generateTone(
        durationMs: Int,
        startFreq: Float,
        endFreq: Float,
        volume: Float = 0.55f,
        waveType: Int = 0
    ): ShortArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val out = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            val angle = 2.0 * PI * freq * i / SAMPLE_RATE
            val raw = if (waveType == 0) {
                if (sin(angle) >= 0.0) volume else -volume
            } else {
                (sin(angle) * volume).toFloat()
            }
            // Sanftes Ausblenden in den letzten 25%
            val fade = if (progress > 0.75f) (1f - (progress - 0.75f) / 0.25f) else 1f
            out[i] = (raw * fade * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun concat(a: ShortArray, b: ShortArray): ShortArray {
        val result = ShortArray(a.size + b.size)
        a.copyInto(result)
        b.copyInto(result, a.size)
        return result
    }

    /** Mario & Luigi: klassischer 8-Bit-Sprung (Rechteckwelle, 250→550 Hz) */
    fun playMarioJump() {
        val now = System.currentTimeMillis()
        if (now - lastJumpMs < 150L) return
        lastJumpMs = now
        scope.launch { playPcm(generateTone(130, 250f, 550f, 0.50f, 0)) }
    }

    /** Peach: höherer, weicherer Sprung (Sinus, 500→1050 Hz, etwas länger) */
    fun playPeachJump() {
        val now = System.currentTimeMillis()
        if (now - lastJumpMs < 150L) return
        lastJumpMs = now
        scope.launch { playPcm(generateTone(170, 500f, 1050f, 0.42f, 1)) }
    }

    /** Münze: kurzes zweiteiliges Ding (659 Hz → 988 Hz) */
    fun playCoin() {
        val now = System.currentTimeMillis()
        if (now - lastCoinMs < 80L) return
        lastCoinMs = now
        scope.launch {
            val part1 = generateTone(55,  659f,  659f, 0.52f, 1)
            val part2 = generateTone(85,  988f,  988f, 0.48f, 1)
            playPcm(concat(part1, part2))
        }
    }

    private fun playPcm(samples: ShortArray) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(samples.size.toLong() * 1000L / SAMPLE_RATE + 60L)
            track.stop()
            track.release()
        } catch (_: Exception) {}
    }
}

// ─────────────────────────────────────────────────────────────────
// Charakter-Zeichenfunktionen (Canvas)
// ─────────────────────────────────────────────────────────────────
private fun DrawScope.drawMarioAlt(cx: Float, cy: Float, facingRight: Boolean, walkFrame: Int = 0) {
    val dir = if (facingRight) 1f else -1f
    // Hut (dunkelblau)
    drawRect(Color(0xFF1A237E), Offset(cx - 10f, cy - 16f), Size(20f, 7f))
    drawRect(Color(0xFF1A237E), Offset(cx - 7f, cy - 22f), Size(14f, 6f))
    // Gesicht (Hautton)
    drawOval(Color(0xFFFDBCB4), Offset(cx - 8f, cy - 14f), Size(16f, 14f))
    // Schnurrbart (braun)
    drawRect(Color(0xFF5D4037), Offset(cx - 7f + 1f * dir, cy - 4f), Size(6f, 3f))
    // Shirt (orange)
    drawRect(Color(0xFFFF6D00), Offset(cx - 10f, cy), Size(20f, 10f))
    // Latzhose (rot)
    drawRect(Color(0xFFC62828), Offset(cx - 10f, cy + 8f), Size(20f, 16f))
    drawRect(Color(0xFFC62828), Offset(cx - 6f, cy - 1f), Size(12f, 9f))
    // Schuhe (braun) – Bein-Animation
    val (lx, ly, rx, ry) = when (walkFrame) {
        0    -> listOf(cx - 14f, cy + 22f, cx + 3f,  cy + 24f)
        2    -> listOf(cx - 10f, cy + 24f, cx - 1f,  cy + 22f)
        else -> listOf(cx - 12f, cy + 24f, cx + 1f,  cy + 24f)
    }
    drawRect(Color(0xFF5D4037), Offset(lx, ly), Size(11f, 8f))
    drawRect(Color(0xFF5D4037), Offset(rx, ry), Size(11f, 8f))
    // Augen (weiß + schwarz)
    val eyeX = if (facingRight) cx + 2f else cx - 6f
    drawCircle(Color.White, 3.5f, Offset(eyeX, cy - 8f))
    drawCircle(Color.Black, 1.8f, Offset(eyeX + 1f * dir, cy - 8f))
}

private fun DrawScope.drawLuigiAlt(cx: Float, cy: Float, facingRight: Boolean, walkFrame: Int = 0) {
    val dir = if (facingRight) 1f else -1f
    // Hut (lila)
    drawRect(Color(0xFF4A148C), Offset(cx - 10f, cy - 16f), Size(20f, 7f))
    drawRect(Color(0xFF4A148C), Offset(cx - 7f, cy - 22f), Size(14f, 6f))
    // Gesicht
    drawOval(Color(0xFFFDBCB4), Offset(cx - 8f, cy - 14f), Size(16f, 14f))
    // Schnurrbart (schwarzbraun)
    drawRect(Color(0xFF37474F), Offset(cx - 7f + 1f * dir, cy - 4f), Size(6f, 3f))
    // Shirt (hellgrün)
    drawRect(Color(0xFFAEEA00), Offset(cx - 10f, cy), Size(20f, 10f))
    // Latzhose (dunkelgrün)
    drawRect(Color(0xFF1B5E20), Offset(cx - 10f, cy + 8f), Size(20f, 16f))
    drawRect(Color(0xFF1B5E20), Offset(cx - 6f, cy - 1f), Size(12f, 9f))
    // Schuhe (braun) – Bein-Animation
    val (lx, ly, rx, ry) = when (walkFrame) {
        0    -> listOf(cx - 14f, cy + 22f, cx + 3f,  cy + 24f)
        2    -> listOf(cx - 10f, cy + 24f, cx - 1f,  cy + 22f)
        else -> listOf(cx - 12f, cy + 24f, cx + 1f,  cy + 24f)
    }
    drawRect(Color(0xFF5D4037), Offset(lx, ly), Size(11f, 8f))
    drawRect(Color(0xFF5D4037), Offset(rx, ry), Size(11f, 8f))
    // Augen
    val eyeX = if (facingRight) cx + 2f else cx - 6f
    drawCircle(Color.White, 3.5f, Offset(eyeX, cy - 8f))
    drawCircle(Color.Black, 1.8f, Offset(eyeX + 1f * dir, cy - 8f))
}

private fun DrawScope.drawPeach(cx: Float, cy: Float, facingRight: Boolean, walkFrame: Int = 0) {
    val dir = if (facingRight) 1f else -1f
    // Haare (gold)
    drawOval(Color(0xFFFFD600), Offset(cx - 10f, cy - 22f), Size(20f, 10f))
    // Krone (gelb)
    val crownPath = Path().apply {
        moveTo(cx - 7f, cy - 22f)
        lineTo(cx - 7f, cy - 28f)
        lineTo(cx - 3f, cy - 24f)
        lineTo(cx, cy - 30f)
        lineTo(cx + 3f, cy - 24f)
        lineTo(cx + 7f, cy - 28f)
        lineTo(cx + 7f, cy - 22f)
        close()
    }
    drawPath(crownPath, Color(0xFFFFD600))
    // Gesicht
    drawOval(Color(0xFFFDBCB4), Offset(cx - 8f, cy - 16f), Size(16f, 14f))
    // Augen
    val eyeX = if (facingRight) cx + 2f else cx - 6f
    drawCircle(Color(0xFF1565C0), 3.5f, Offset(eyeX, cy - 10f))
    drawCircle(Color.Black, 1.5f, Offset(eyeX + 0.5f * dir, cy - 10f))
    // Kleid (türkisblau) – Oberteil
    drawRoundRect(Color(0xFF00BCD4), Offset(cx - 11f, cy - 2f), Size(22f, 12f), CornerRadius(3f))
    // Kleid – Rock (breit)
    val dressPath = Path().apply {
        moveTo(cx - 11f, cy + 10f)
        lineTo(cx - 18f, cy + 32f)
        lineTo(cx + 18f, cy + 32f)
        lineTo(cx + 11f, cy + 10f)
        close()
    }
    drawPath(dressPath, Color(0xFF00838F))
    // Handschuhe (weiß)
    drawCircle(Color.White, 5f, Offset(cx - 14f, cy + 5f))
    drawCircle(Color.White, 5f, Offset(cx + 14f, cy + 5f))
    // Schuhe (rosa) – Bein-Animation
    val (lx, ly, rx, ry) = when (walkFrame) {
        0    -> listOf(cx - 12f, cy + 30f, cx + 3f, cy + 32f)
        2    -> listOf(cx - 8f,  cy + 32f, cx - 1f, cy + 30f)
        else -> listOf(cx - 10f, cy + 32f, cx + 1f, cy + 32f)
    }
    drawRoundRect(Color(0xFFFF80AB), Offset(lx, ly), Size(9f, 7f), CornerRadius(2f))
    drawRoundRect(Color(0xFFFF80AB), Offset(rx, ry), Size(9f, 7f), CornerRadius(2f))
}

private fun DrawScope.drawCharacter(
    character: GameCharacter,
    cx: Float, cy: Float,
    facingRight: Boolean,
    walkFrame: Int = 0
) = when (character) {
    GameCharacter.MARIO_ALT -> drawMarioAlt(cx, cy, facingRight, walkFrame)
    GameCharacter.LUIGI_ALT -> drawLuigiAlt(cx, cy, facingRight, walkFrame)
    GameCharacter.PEACH      -> drawPeach(cx, cy, facingRight, walkFrame)
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, scale: Float = 1f) {
    val r = 30f * scale
    val c = Color.White.copy(alpha = 0.88f)
    drawCircle(c, r * 0.90f, Offset(cx, cy))
    drawCircle(c, r * 0.70f, Offset(cx - r * 0.75f, cy + r * 0.12f))
    drawCircle(c, r * 0.70f, Offset(cx + r * 0.75f, cy + r * 0.12f))
    drawCircle(c, r * 0.75f, Offset(cx - r * 0.35f, cy - r * 0.28f))
    drawCircle(c, r * 0.75f, Offset(cx + r * 0.35f, cy - r * 0.28f))
}

// ─────────────────────────────────────────────────────────────────
// Charakter-Auswahlkarte
// ─────────────────────────────────────────────────────────────────
@Composable
private fun CharacterCard(
    character: GameCharacter,
    myUsername: String?,
    partnerUsername: String?,
    isSelectedByMe: Boolean,
    isSelectedByPartner: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelectedByMe && isSelectedByPartner -> Color(0xFFFF6B00)
        isSelectedByMe     -> MaterialTheme.colorScheme.primary
        isSelectedByPartner -> Color(0xFFE040FB)
        else               -> Color.Transparent
    }
    val borderWidth = if (isSelectedByMe || isSelectedByPartner) 3.dp else 1.dp
    val ctx = LocalContext.current
    val colorName = when (character) {
        GameCharacter.MARIO_ALT -> "yellow"
        GameCharacter.LUIGI_ALT -> "green"
        GameCharacter.PEACH     -> "pink"
    }
    val frontSprite = androidx.compose.runtime.remember(character) {
        BitmapFactory.decodeResource(ctx.resources,
            ctx.resources.getIdentifier("character_${colorName}_front", "drawable", ctx.packageName)
        ).asImageBitmap()
    }

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectedByMe)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)) {
                val spriteW = size.height * 0.72f
                val spriteH = size.height * 0.90f
                val sx = (size.width - spriteW) / 2f
                val sy = (size.height - spriteH) / 2f
                drawImage(frontSprite,
                    dstOffset = androidx.compose.ui.unit.IntOffset(sx.toInt(), sy.toInt()),
                    dstSize   = androidx.compose.ui.unit.IntSize(spriteW.toInt().coerceAtLeast(1), spriteH.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.None)
            }
            Text(
                text = character.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (myUsername != null && isSelectedByMe) {
                Text(
                    text = myUsername,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            if (partnerUsername != null && isSelectedByPartner) {
                Text(
                    text = partnerUsername,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE040FB),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Hauptscreen
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTiltNDrop: (() -> Unit)? = null,
    onNavigateToJumpOrDie: (() -> Unit)? = null,
    onNavigateToPinball: (() -> Unit)? = null,
    initialLeaderboardMode: Int = 0
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val contacts    by viewModel.contactsSortedByRecent.collectAsState(initial = emptyList())
    val preselectedGamePartner by viewModel.preselectedGamePartner.collectAsState()
    val gamesBlocked = remember { !viewModel.childCan(ChildPermission.PLAY_GAMES) }

    // ── Zustandsvariablen ──
    var phase              by remember { mutableStateOf(GamePhase.HUB) }
    var partnerId          by remember { mutableStateOf("") }
    var partnerName        by remember { mutableStateOf("") }
    var myCharacter        by remember { mutableStateOf<GameCharacter?>(null) }
    var partnerCharacter   by remember { mutableStateOf<GameCharacter?>(null) }
    var myReady            by remember { mutableStateOf(false) }
    var partnerReady       by remember { mutableStateOf(false) }
    var myWantsPlayAgain       by remember { mutableStateOf(false) }
    var partnerWantsPlayAgain  by remember { mutableStateOf(false) }
    var countdownValue     by remember { mutableIntStateOf(5) }
    var ranking            by remember { mutableStateOf<List<GameRankingEntry>>(emptyList()) }
    var rankingResetAt     by remember { mutableStateOf("") }
    var lastMonthTop3      by remember { mutableStateOf<List<GameMonthWinner>>(emptyList()) }
    var jodCurrentMonth    by remember { mutableStateOf<List<JodLeaderboardEntry>>(emptyList()) }
    var jodPrevMonth       by remember { mutableStateOf<List<JodLeaderboardEntry>>(emptyList()) }
    var showContactPicker  by remember { mutableStateOf(false) }
    var resultCoins        by remember { mutableIntStateOf(0) }
    var resultDuration     by remember { mutableIntStateOf(0) }
    var resultWon          by remember { mutableStateOf(false) }

    // Spieler-States
    var myState            by remember { mutableStateOf(PlayerState()) }
    var partnerState       by remember { mutableStateOf<PlayerState?>(null) }
    var gameStartTime      by remember { mutableLongStateOf(0L) }
    var partnerEliminated  by remember { mutableStateOf(false) }
    var selectedLevel      by remember { mutableIntStateOf(1) }
    var myTotalCoins       by remember { mutableIntStateOf(0) }

    // Verhindert doppeltes Akkumulieren eines Runden-Ergebnisses
    var jumpRunResultSent  by remember { mutableStateOf(false) }
    var tttResultSent      by remember { mutableStateOf(false) }
    var activityResultSent by remember { mutableStateOf(false) }

    // Session-Ergebnisse: kumuliert über alle Runden – Karte wird erst beim Verlassen gesendet
    var sessionPartnerId     by remember { mutableStateOf("") }
    var sessionPartnerName   by remember { mutableStateOf("") }
    var sessionGameType      by remember { mutableStateOf("") }
    var sessionGameName      by remember { mutableStateOf("") }
    var sessionRounds        by remember { mutableIntStateOf(0) }
    var sessionMyCoins       by remember { mutableIntStateOf(0) }
    var sessionMyWins        by remember { mutableIntStateOf(0) }
    var sessionMyLosses      by remember { mutableIntStateOf(0) }
    var sessionMyDraws       by remember { mutableIntStateOf(0) }
    var sessionTotalDuration by remember { mutableIntStateOf(0) }

    var showHistory        by remember { mutableStateOf(false) }

    // ── Activity-Spielzustand ──
    var selectedGameType        by remember { mutableStateOf(GameType.JUMP_AND_RUN) }
    var activityIsHost          by remember { mutableStateOf(false) }
    var activityIsDrawer        by remember { mutableStateOf(false) }
    var activityCurrentWord     by remember { mutableStateOf("") }
    var activityWordChoices     by remember { mutableStateOf<List<String>>(emptyList()) }
    var activityWordChosen      by remember { mutableStateOf(false) }
    var activityCoins           by remember { mutableIntStateOf(0) }
    var activityTimer           by remember { mutableIntStateOf(120) }
    var activityMyStrokes       by remember { mutableStateOf<List<DrawStroke>>(emptyList()) }
    var activityMyCurrentStroke by remember { mutableStateOf<DrawStroke?>(null) }
    var activityPartnerStrokes  by remember { mutableStateOf<List<DrawStroke>>(emptyList()) }
    var activityPartnerCurrent  by remember { mutableStateOf<DrawStroke?>(null) }
    var activityGuessText       by remember { mutableStateOf("") }
    var activityCorrectWord     by remember { mutableStateOf("") }
    var activitySelectedColor   by remember { mutableLongStateOf(0xFF000000L) }
    var activityStrokeWidth     by remember { mutableFloatStateOf(10f) }
    var activityLastPartialSent by remember { mutableLongStateOf(0L) }
    // Sketch-'n'-Check: GraphicsLayer zum Erfassen der Zeichnung + gesammelte Bild-URLs
    val activitySketchLayer = rememberGraphicsLayer()
    val activitySavedSketchUrls = remember { mutableStateListOf<String>() }

    val resetActivityRound = { isDrawerNow: Boolean ->
        activityIsDrawer        = isDrawerNow
        activityCurrentWord     = ""
        activityWordChosen      = false
        activityMyStrokes       = emptyList()
        activityMyCurrentStroke = null
        activityPartnerStrokes  = emptyList()
        activityPartnerCurrent  = null
        activityCorrectWord     = ""
        activityGuessText       = ""
        activityWordChoices     = if (isDrawerNow) ACTIVITY_WORDS.shuffled().take(3) else emptyList()
    }

    // ── Tic-Tac-Toe Zustand ──
    var tttBoard         by remember { mutableStateOf<List<TttCell>>(emptyList()) }
    var tttMySymbol      by remember { mutableStateOf('X') }  // Einladender = X, Annehmender = O
    var tttIsMyTurn      by remember { mutableStateOf(true) }  // X beginnt
    var tttWon           by remember { mutableStateOf<Char?>(null) } // 'X', 'O', 'D' (draw), null
    var tttRewardClaimed by remember { mutableStateOf(false) }
    var tttRoundNumber   by remember { mutableStateOf(0) }      // Anzahl gespielte Runden
    var tttResultsCountdown by remember { mutableStateOf(10) }  // 10s Countdown nach Spielende

    // Ausstehende Einladung (gesetzt bevor GamesScreen geöffnet wurde) aufnehmen
    val pendingInvite by viewModel.pendingGameInvite.collectAsState()
    LaunchedEffect(pendingInvite) {
        val invite = pendingInvite ?: return@LaunchedEffect
        if (phase == GamePhase.HUB) {
            partnerId        = invite.senderId
            partnerName      = invite.fromName
            selectedGameType = try { GameType.valueOf(invite.gameType) } catch (_: Exception) { GameType.JUMP_AND_RUN }
            viewModel.clearPendingGameInvite()
            if (invite.alreadyAccepted) {
                // Einladung wurde bereits im globalen Dialog angenommen → direkt in Spielphase
                when (selectedGameType) {
                    GameType.ACTIVITY -> {
                        activityIsHost  = false
                        activityCoins   = 0
                        activityTimer   = 120
                        resetActivityRound(false)
                        phase = GamePhase.ACTIVITY_PLAYING
                    }
                    GameType.TIC_TAC_TOE -> {
                        tttMySymbol  = 'O'
                        tttBoard     = emptyList()
                        tttIsMyTurn  = false
                        tttWon       = null
                        tttRewardClaimed = false
                        phase = GamePhase.TIC_TAC_TOE_PLAYING
                    }
                    else -> phase = GamePhase.CHAR_SELECT  // JUMP_AND_RUN
                }
            } else {
                phase = GamePhase.INVITE_RECEIVED
            }
        }
    }

    // Ranking und eigene Statistiken bei jedem Hub-Aufruf laden
    LaunchedEffect(phase) {
        if (phase != GamePhase.HUB) return@LaunchedEffect
        try {
            val resp = viewModel.getGamingRanking()
            if (resp.isSuccessful) {
                val body = resp.body()
                ranking       = body?.entries ?: emptyList()
                rankingResetAt = body?.resetAt ?: ""
                lastMonthTop3  = body?.lastMonthTop3 ?: emptyList()
            }
        } catch (_: Exception) {}
        try {
            val statsResp = viewModel.getMyGamingStats()
            if (statsResp.isSuccessful) myTotalCoins = statsResp.body()?.totalCoins ?: 0
        } catch (_: Exception) {}
        try {
            val jodResp = viewModel.getJodLeaderboard()
            if (jodResp.isSuccessful) {
                jodCurrentMonth = jodResp.body()?.currentMonth ?: emptyList()
                jodPrevMonth    = jodResp.body()?.prevMonth ?: emptyList()
            }
        } catch (_: Exception) {}
    }

    // JOD-Rangliste nach Score-Übermittlung aktualisieren
    LaunchedEffect(viewModel.jodScoreSubmitted) {
        viewModel.jodScoreSubmitted.collect {
            try {
                val jodResp = viewModel.getJodLeaderboard()
                if (jodResp.isSuccessful) {
                    jodCurrentMonth = jodResp.body()?.currentMonth ?: emptyList()
                    jodPrevMonth    = jodResp.body()?.prevMonth ?: emptyList()
                }
            } catch (_: Exception) {}
        }
    }

    // Eingehende WebSocket-Spiel-Events verarbeiten
    LaunchedEffect(Unit) {
        viewModel.incomingGameEvent.collect { msg ->
            val payload = msg.payload as? Map<*, *> ?: return@collect
            when (msg.type) {
                "game_invite" -> {
                    if (phase == GamePhase.HUB) {
                        partnerId   = msg.senderId ?: return@collect
                        partnerName = payload["from_name"] as? String ?: "Unbekannt"
                        val gtName  = payload["game_type"] as? String ?: "JUMP_AND_RUN"
                        selectedGameType = try { GameType.valueOf(gtName) } catch (_: Exception) { GameType.JUMP_AND_RUN }
                        phase = GamePhase.INVITE_RECEIVED
                        viewModel.clearPendingGameInvite()
                    }
                }
                "game_accept" -> {
                    if (phase == GamePhase.INVITE_SENT) {
                        when (selectedGameType) {
                            GameType.ACTIVITY -> {
                                activityIsHost  = true
                                activityCoins   = 0
                                activityTimer   = 120
                                resetActivityRound(true)
                                phase = GamePhase.ACTIVITY_PLAYING
                            }
                            GameType.TIC_TAC_TOE -> {
                                // Einladender = X, beginnt
                                tttMySymbol  = 'X'
                                tttBoard     = emptyList()
                                tttIsMyTurn  = true
                                tttWon       = null
                                tttRewardClaimed = false
                                phase = GamePhase.TIC_TAC_TOE_PLAYING
                            }
                            GameType.TILT_N_DROP -> {
                                // Params wurden beim Senden gesetzt (isHost = true)
                                onNavigateToTiltNDrop?.invoke()
                            }
                            else -> phase = GamePhase.CHAR_SELECT
                        }
                    }
                }
                "game_decline" -> {
                    if (phase == GamePhase.INVITE_SENT) {
                        phase = GamePhase.HUB
                        partnerId = ""
                    }
                }
                "game_cancel" -> {
                    if (phase == GamePhase.INVITE_RECEIVED) {
                        phase = GamePhase.HUB
                        partnerId = ""
                        partnerName = ""
                    }
                }
                "game_char_select" -> {
                    val charName = payload["character"] as? String ?: return@collect
                    try { partnerCharacter = GameCharacter.valueOf(charName) } catch (_: Exception) {}
                }
                "game_level_select" -> {
                    val lvl = (payload["level"] as? Number)?.toInt() ?: 1
                    if (lvl in 1..2) selectedLevel = lvl
                }
                "game_ready" -> {
                    partnerReady = true
                    if (myReady && partnerReady && phase == GamePhase.CHAR_SELECT) {
                        phase = GamePhase.COUNTDOWN
                    }
                }
                "game_state" -> {
                    val px   = (payload["x"] as? Number)?.toFloat() ?: return@collect
                    val py   = (payload["y"] as? Number)?.toFloat() ?: return@collect
                    val pvx  = (payload["vx"] as? Number)?.toFloat() ?: 0f
                    val fr   = payload["facing_right"] as? Boolean ?: true
                    val pc   = (payload["coins"] as? Number)?.toInt() ?: 0
                    val prg  = payload["reached_goal"] as? Boolean ?: false
                    val pSkyCoins = (payload["sky_coins"] as? List<*>)
                        ?.filterIsInstance<Number>()
                        ?.map { it.toInt() }
                        ?.toSet() ?: emptySet()
                    partnerState = PlayerState(
                        x = px, y = py, velX = pvx,
                        facingRight = fr, coinsCollected = pc, reachedGoal = prg,
                        collectedCoinIds = pSkyCoins
                    )
                    if (prg && phase == GamePhase.PLAYING) {
                        // Partner hat Ziel erreicht
                        resultCoins    = myState.coinsCollected
                        resultDuration = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                        resultWon      = false
                        phase = GamePhase.RESULTS
                        viewModel.saveGameSession(selectedLevel, resultCoins, resultDuration, "lose", "jump_run", partnerId, partnerName)
                    }
                }
                "game_end" -> {
                    if (phase == GamePhase.PLAYING) {
                        resultCoins    = myState.coinsCollected
                        resultDuration = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                        resultWon      = false
                        phase = GamePhase.RESULTS
                        viewModel.saveGameSession(selectedLevel, resultCoins, resultDuration, "lose", "jump_run", partnerId, partnerName)
                    }
                }
                "game_sky_fall" -> {
                    // Partner ist von der Himmelsplattform gefallen – wir spielen weiter
                    if (phase == GamePhase.PLAYING) {
                        partnerEliminated = true
                    }
                }
                // ── Activity-Events ────────────────────────────────
                "activity_word_chosen" -> {
                    if (phase == GamePhase.ACTIVITY_PLAYING && !activityIsDrawer) {
                        activityWordChosen = true
                    }
                }
                "activity_draw_partial" -> {
                    if (phase == GamePhase.ACTIVITY_PLAYING && !activityIsDrawer) {
                        val pts   = (payload["points"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toFloat() } ?: return@collect
                        val color = (payload["color"]  as? Number)?.toLong() ?: 0xFF000000L
                        val width = (payload["width"]  as? Number)?.toFloat() ?: 10f
                        activityPartnerCurrent = DrawStroke(pts, color, width)
                    }
                }
                "activity_draw_stroke" -> {
                    if (phase == GamePhase.ACTIVITY_PLAYING && !activityIsDrawer) {
                        val pts   = (payload["points"] as? List<*>)?.filterIsInstance<Number>()?.map { it.toFloat() } ?: return@collect
                        val color = (payload["color"]  as? Number)?.toLong() ?: 0xFF000000L
                        val width = (payload["width"]  as? Number)?.toFloat() ?: 10f
                        activityPartnerStrokes  = activityPartnerStrokes + DrawStroke(pts, color, width)
                        activityPartnerCurrent  = null
                    }
                }
                "activity_draw_clear" -> {
                    if (phase == GamePhase.ACTIVITY_PLAYING) {
                        activityPartnerStrokes = emptyList()
                        activityPartnerCurrent = null
                    }
                }
                "activity_guess" -> {
                    if (phase == GamePhase.ACTIVITY_PLAYING && activityIsDrawer && activityWordChosen) {
                        val guess = payload["text"] as? String ?: return@collect
                        if (isCorrectGuess(guess, activityCurrentWord)) {
                            activityCoins += 10
                            viewModel.sendGameWsMessage(
                                "activity_correct", partnerId,
                                mapOf("word" to activityCurrentWord, "coins" to activityCoins)
                            )
                            resetActivityRound(false)  // jetzt Rater
                        }
                    }
                }
                "activity_correct" -> {
                    if (phase == GamePhase.ACTIVITY_PLAYING && !activityIsDrawer) {
                        val word     = payload["word"]  as? String ?: ""
                        val newCoins = (payload["coins"] as? Number)?.toInt() ?: (activityCoins + 10)
                        activityCoins       = newCoins
                        activityCorrectWord = word
                        delay(1500)
                        resetActivityRound(true)  // jetzt Zeichner
                    }
                }
                "activity_end" -> {
                    if (phase == GamePhase.ACTIVITY_PLAYING) {
                        viewModel.saveGameSession(1, activityCoins, 120 - activityTimer, "lose", "activity", partnerId, partnerName)
                        phase = GamePhase.ACTIVITY_RESULTS
                    }
                }
                "play_again" -> {
                    if (phase == GamePhase.RESULTS) {
                        partnerWantsPlayAgain = true
                        if (myWantsPlayAgain) {
                            myCharacter           = null
                            partnerCharacter      = null
                            myReady               = false
                            partnerReady          = false
                            myWantsPlayAgain      = false
                            partnerWantsPlayAgain = false
                            myState               = PlayerState()
                            partnerState          = null
                            partnerEliminated     = false
                            selectedLevel         = 1
                            phase                 = GamePhase.CHAR_SELECT
                        }
                    }
                }
                "ttt_move" -> {
                    if (phase == GamePhase.TIC_TAC_TOE_PLAYING) {
                        val row    = (payload["row"] as? Number)?.toInt() ?: return@collect
                        val col    = (payload["col"] as? Number)?.toInt() ?: return@collect
                        val sym    = (payload["symbol"] as? String)?.firstOrNull() ?: return@collect
                        val now    = System.currentTimeMillis()
                        val theirPieces = tttBoard.filter { it.player == sym }
                        val baseBoard = if (theirPieces.size >= 3) {
                            val oldest = theirPieces.minByOrNull { it.placedAt }!!
                            tttBoard.filter { it != oldest }
                        } else tttBoard
                        if (baseBoard.any { it.row == row && it.col == col }) return@collect
                        val updatedBoard = baseBoard + TttCell(row, col, sym, now)
                        tttBoard    = updatedBoard
                        tttIsMyTurn = true
                        val winner = checkTttWinner(updatedBoard)
                        if (winner != null) {
                            tttWon = winner
                            if (!tttRewardClaimed) {
                                tttRewardClaimed = true
                                val isWin = winner == tttMySymbol
                                viewModel.claimTttReward(if (isWin) "win" else "lose", partnerId, partnerName)
                            }
                            phase = GamePhase.TIC_TAC_TOE_RESULTS
                        }
                    }
                }
                "ttt_end" -> {
                    if (phase == GamePhase.TIC_TAC_TOE_PLAYING) {
                        val winnerStr = payload["winner"] as? String ?: return@collect
                        val winner = winnerStr.firstOrNull() ?: return@collect
                        tttWon = winner
                        if (!tttRewardClaimed) {
                            tttRewardClaimed = true
                            val isWin = winner == tttMySymbol
                            viewModel.claimTttReward(if (isWin) "win" else "lose", partnerId, partnerName)
                        }
                        phase = GamePhase.TIC_TAC_TOE_RESULTS
                    }
                }
                "ttt_rematch" -> {
                    if (phase == GamePhase.TIC_TAC_TOE_RESULTS || phase == GamePhase.TIC_TAC_TOE_PLAYING) {
                        tttRoundNumber++
                        // Jede 2. Runde wechselt der Startspieler: Symbol tauschen
                        tttMySymbol  = if (tttMySymbol == 'X') 'O' else 'X'
                        tttBoard     = emptyList()
                        tttWon       = null
                        tttRewardClaimed = false
                        tttIsMyTurn  = tttMySymbol == 'X'
                        phase = GamePhase.TIC_TAC_TOE_PLAYING
                    }
                }
            }
        }
    }

    // TicTacToe Ergebnis-Countdown: 10s nach Spielende, dann zurück zum Hub
    LaunchedEffect(phase) {
        if (phase != GamePhase.TIC_TAC_TOE_RESULTS) return@LaunchedEffect
        tttResultsCountdown = 10
        while (tttResultsCountdown > 0) {
            delay(1000)
            tttResultsCountdown--
        }
        // Countdown abgelaufen → zurück zum Hub
        if (phase == GamePhase.TIC_TAC_TOE_RESULTS) {
            tttBoard    = emptyList()
            tttWon      = null
            partnerId   = ""
            partnerName = ""
            tttRoundNumber = 0
            phase       = GamePhase.HUB
        }
    }

    // Activity-Timer
    LaunchedEffect(phase) {
        if (phase != GamePhase.ACTIVITY_PLAYING) return@LaunchedEffect
        activityTimer = 120
        while (activityTimer > 0) {
            delay(1000)
            activityTimer--
            // 1 Sekunde vor Ablauf: aktuelle Zeichnung des Zeichners sichern
            if (activityTimer == 1 && activityIsDrawer && activityWordChosen &&
                activityCurrentWord.isNotBlank() && activityMyStrokes.isNotEmpty()) {
                val word = activityCurrentWord
                val bmp = runCatching { activitySketchLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
                if (bmp != null) {
                    viewModel.saveSketchAndGetUrl(bmp, word)?.let { activitySavedSketchUrls.add(it) }
                }
            }
        }
        viewModel.saveGameSession(1, activityCoins, 120, "win", "activity", partnerId, partnerName)
        phase = GamePhase.ACTIVITY_RESULTS
    }

    // Runden-Ergebnisse in Session-Statistiken akkumulieren (Karte wird erst beim Verlassen gesendet)
    LaunchedEffect(phase) {
        when (phase) {
            GamePhase.RESULTS -> if (!jumpRunResultSent && partnerId.isNotBlank()) {
                jumpRunResultSent = true
                sessionPartnerId     = partnerId
                sessionPartnerName   = partnerName
                sessionGameType      = "jump_run"
                sessionGameName      = "Jump & Run"
                sessionRounds        += 1
                sessionMyCoins       += resultCoins
                sessionTotalDuration += resultDuration
                if (resultWon) sessionMyWins++ else sessionMyLosses++
            }
            GamePhase.TIC_TAC_TOE_RESULTS -> if (!tttResultSent && partnerId.isNotBlank()) {
                tttResultSent = true
                val won    = tttWon == tttMySymbol
                val isDraw = tttWon == 'D'
                sessionPartnerId   = partnerId
                sessionPartnerName = partnerName
                sessionGameType    = "tictactoe"
                sessionGameName    = "Tic Tac Toe"
                sessionRounds      += 1
                when {
                    isDraw -> sessionMyDraws++
                    won    -> sessionMyWins++
                    else   -> sessionMyLosses++
                }
            }
            GamePhase.ACTIVITY_RESULTS -> if (!activityResultSent && partnerId.isNotBlank()) {
                activityResultSent = true
                sessionPartnerId   = partnerId
                sessionPartnerName = partnerName
                sessionGameType    = "sketch_n_check"
                sessionGameName    = "Sketch & Check"
                sessionRounds      += 1
                sessionMyCoins     += activityCoins
            }
            GamePhase.HUB -> {
                jumpRunResultSent = false
                tttResultSent     = false
                activityResultSent = false
                // Session zurücksetzen wenn zum Hub zurückgekehrt
                sessionRounds        = 0
                sessionMyCoins       = 0
                sessionMyWins        = 0
                sessionMyLosses      = 0
                sessionMyDraws       = 0
                sessionTotalDuration = 0
                sessionPartnerId     = ""
                sessionPartnerName   = ""
                sessionGameType      = ""
                sessionGameName      = ""
            }
            GamePhase.CHAR_SELECT -> jumpRunResultSent = false
            GamePhase.TIC_TAC_TOE_PLAYING -> tttResultSent = false
            GamePhase.ACTIVITY_PLAYING -> activityResultSent = false
            else -> {}
        }
    }

    // Countdown-Loop
    LaunchedEffect(phase) {
        if (phase == GamePhase.COUNTDOWN) {
            countdownValue = 5
            repeat(5) { i ->
                delay(1000)
                countdownValue = 4 - i
            }
            phase = GamePhase.PLAYING
            gameStartTime = System.currentTimeMillis()
        }
    }

    // ── Verlassen-Handler (TopAppBar-Button + Android-Zurück-Geste) ──
    val handleGameExit: () -> Unit = {
        // Partner benachrichtigen + Session für Mid-Game-Abbruch befüllen
        if (phase == GamePhase.PLAYING && partnerId.isNotBlank()) {
            viewModel.sendGameWsMessage("game_end", partnerId, emptyMap())
            if (sessionPartnerId.isBlank()) {
                sessionPartnerId   = partnerId
                sessionPartnerName = partnerName
                sessionGameType    = "jump_run"
                sessionGameName    = "Jump & Run"
                sessionRounds      = 1
                sessionMyLosses    = 1
            }
        }
        if (phase == GamePhase.ACTIVITY_PLAYING && partnerId.isNotBlank()) {
            viewModel.sendGameWsMessage("activity_end", partnerId, emptyMap())
            if (sessionPartnerId.isBlank()) {
                sessionPartnerId   = partnerId
                sessionPartnerName = partnerName
                sessionGameType    = "sketch_n_check"
                sessionGameName    = "Zeichnen & Raten"
                sessionRounds      = 1
                sessionMyLosses    = 1
            }
        }
        if (phase == GamePhase.TIC_TAC_TOE_PLAYING && partnerId.isNotBlank()) {
            // Partner als Gewinner senden (Verlassender verliert)
            viewModel.sendGameWsMessage(
                "ttt_end", partnerId,
                mapOf("winner" to if (tttMySymbol == 'X') "O" else "X")
            )
            if (sessionPartnerId.isBlank()) {
                sessionPartnerId   = partnerId
                sessionPartnerName = partnerName
                sessionGameType    = "tictactoe"
                sessionGameName    = "Tic Tac Toe"
                sessionRounds      = 1
                sessionMyLosses    = 1
            } else {
                // Laufende Runde als Niederlage hinzufügen
                sessionRounds   += 1
                sessionMyLosses += 1
            }
        }
        // Ergebniskarte in den Chat einfügen
        if (sessionPartnerId.isNotBlank() && sessionRounds > 0) {
            val myName = currentUser?.name?.takeIf { it.isNotBlank() }
                ?: currentUser?.fakeNumber ?: "Du"
            val overallWon  = sessionMyWins > sessionMyLosses
            val overallDraw = sessionGameType == "sketch_n_check" ||
                (sessionMyWins == sessionMyLosses && sessionMyDraws > 0)
            val myScore = when (sessionGameType) {
                "jump_run" -> {
                    val m = sessionTotalDuration / 60
                    val s = sessionTotalDuration % 60
                    "${sessionMyWins}S/${sessionMyLosses}N · ${m}m${s}s"
                }
                "tictactoe" ->
                    "${sessionMyWins}S/${sessionMyLosses}N/${sessionMyDraws}U"
                else -> "🪙 ${sessionMyCoins}"
            }
            val content = org.json.JSONObject().apply {
                put("game",         sessionGameType)
                put("gameName",     sessionGameName)
                put("won",          overallWon)
                put("isDraw",       overallDraw)
                put("partnerName",  sessionPartnerName)
                put("partnerId",    sessionPartnerId)
                put("myName",       myName)
                put("rounds",       sessionRounds)
                put("myScore",      myScore)
                put("myCoins",      sessionMyCoins)
                put("partnerScore", "")
                put("partnerCoins", partnerState?.coinsCollected ?: 0)
            }.toString()
            viewModel.insertGameResultMessage(sessionPartnerId, content)
        }
        // Sketch-'n'-Check: gesammelte Zeichnungen in den gemeinsamen Chat senden
        if (activitySavedSketchUrls.isNotEmpty()) {
            val sketchTarget = partnerId.ifBlank { sessionPartnerId }
            if (sketchTarget.isNotBlank()) {
                activitySavedSketchUrls.forEach { url ->
                    viewModel.sendSketchImageMessage(sketchTarget, url, isGroup = false)
                }
            }
            activitySavedSketchUrls.clear()
        }
        onNavigateBack()
    }

    // Android-Zurück-Geste abfangen (wenn Spiel aktiv)
    BackHandler(
        enabled = phase in setOf(
            GamePhase.PLAYING,
            GamePhase.ACTIVITY_PLAYING,
            GamePhase.TIC_TAC_TOE_PLAYING
        )
    ) { handleGameExit() }

    // Anfrage zurückziehen: INVITE_SENT (Warte-Screen) oder INVITE_RECEIVED mit Zurück-Taste
    val handleCancelInvite: () -> Unit = {
        if (partnerId.isNotBlank()) {
            viewModel.sendGameWsMessage("game_cancel", partnerId, emptyMap())
            viewModel.insertGameCancelSystemMessage(partnerId, partnerName)
        }
        viewModel.clearTiltNDropParams()
        phase = GamePhase.HUB
        partnerId = ""
        partnerName = ""
    }
    BackHandler(enabled = phase == GamePhase.INVITE_SENT) { handleCancelInvite() }
    BackHandler(enabled = phase == GamePhase.INVITE_RECEIVED) {
        viewModel.sendGameWsMessage("game_decline", partnerId, emptyMap())
        phase = GamePhase.HUB
        partnerId = ""
        partnerName = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.games_play_together)) },
                navigationIcon = {
                    IconButton(onClick = { handleGameExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.games_play_cd))
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "Spielhistorie")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (phase) {
                GamePhase.HUB -> HubScreen(
                    ranking = ranking,
                    resetAt = rankingResetAt,
                    lastMonthTop3 = lastMonthTop3,
                    jodCurrentMonth = jodCurrentMonth,
                    jodPrevMonth = jodPrevMonth,
                    initialLeaderboardMode = initialLeaderboardMode,
                    onStartNewGame = { if (!gamesBlocked) phase = GamePhase.GAME_SELECT }
                )
                GamePhase.GAME_SELECT -> GameSelectScreen(
                    onSelectGame = { gameType ->
                        selectedGameType = gameType
                        // Solo-Spiel: direkt navigieren ohne Partner
                        if (gameType == GameType.JUMP_OR_DIE) {
                            onNavigateToJumpOrDie?.invoke()
                            return@GameSelectScreen
                        }
                        if (gameType == GameType.PINBALL) {
                            onNavigateToPinball?.invoke()
                            return@GameSelectScreen
                        }
                        val prePartner = preselectedGamePartner
                        if (prePartner != null) {
                            // Direkt einladen – kein Contact-Picker nötig
                            partnerId   = prePartner.first
                            partnerName = prePartner.second
                            viewModel.clearPreselectedGamePartner()
                            viewModel.sendGameWsMessage(
                                "game_invite", prePartner.first,
                                mapOf(
                                    "from_name" to (currentUser?.name ?: ""),
                                    "game_type" to gameType.name
                                )
                            )
                            val gameName = when (gameType) {
                                GameType.JUMP_AND_RUN -> "JUMP and Run"
                                GameType.ACTIVITY     -> "Zeichnen & Raten"
                                GameType.TIC_TAC_TOE  -> "Tic Tac Toe"
                                GameType.TILT_N_DROP  -> "Neon Tilt 'n' Drop"
                                GameType.JUMP_OR_DIE  -> "JUMP or Die"
                                GameType.PINBALL      -> "Flipper"
                            }
                            viewModel.insertGameInviteSystemMessage(prePartner.first, gameName)
                            if (gameType == GameType.TILT_N_DROP) {
                                viewModel.setTiltNDropParams(prePartner.first, prePartner.second, isHost = true)
                            }
                            phase = GamePhase.INVITE_SENT
                        } else {
                            showContactPicker = true
                        }
                    },
                    onBack = { phase = GamePhase.HUB }
                )
                GamePhase.INVITE_SENT -> WaitingScreen(
                    partnerName = partnerName,
                    onCancel    = handleCancelInvite
                )
                GamePhase.INVITE_RECEIVED -> InviteReceivedScreen(
                    partnerName = partnerName,
                    onAccept = {
                        if (gamesBlocked) {
                            viewModel.sendGameWsMessage("game_decline", partnerId, emptyMap())
                            phase = GamePhase.HUB
                            return@InviteReceivedScreen
                        }
                        viewModel.sendGameWsMessage(
                            "game_accept", partnerId,
                            mapOf("from_name" to (currentUser?.name ?: ""))
                        )
                        when (selectedGameType) {
                            GameType.ACTIVITY -> {
                                activityIsHost  = false
                                activityCoins   = 0
                                activityTimer   = 120
                                resetActivityRound(false)
                                phase = GamePhase.ACTIVITY_PLAYING
                            }
                            GameType.TIC_TAC_TOE -> {
                                // Annehmender = O, X beginnt → noch nicht mein Zug
                                tttMySymbol  = 'O'
                                tttBoard     = emptyList()
                                tttIsMyTurn  = false
                                tttWon       = null
                                tttRewardClaimed = false
                                phase = GamePhase.TIC_TAC_TOE_PLAYING
                            }
                            GameType.TILT_N_DROP -> {
                                viewModel.setTiltNDropParams(partnerId, partnerName, isHost = false)
                                onNavigateToTiltNDrop?.invoke()
                            }
                            else -> phase = GamePhase.CHAR_SELECT
                        }
                    },
                    onDecline = {
                        viewModel.sendGameWsMessage("game_decline", partnerId, emptyMap())
                        phase = GamePhase.HUB
                        partnerId = ""
                    }
                )
                GamePhase.CHAR_SELECT -> CharSelectScreen(
                    myUsername      = currentUser?.name,
                    partnerName     = partnerName,
                    myCharacter     = myCharacter,
                    partnerCharacter = partnerCharacter,
                    myReady         = myReady,
                    partnerReady    = partnerReady,
                    selectedLevel   = selectedLevel,
                    myTotalCoins    = myTotalCoins,
                    onSelectCharacter = { char ->
                        myCharacter = char
                        viewModel.sendGameWsMessage(
                            "game_char_select", partnerId,
                            mapOf("character" to char.name)
                        )
                    },
                    onSelectLevel = { lvl ->
                        selectedLevel = lvl
                        viewModel.sendGameWsMessage(
                            "game_level_select", partnerId,
                            mapOf("level" to lvl)
                        )
                    },
                    onStartGame = {
                        if (myCharacter != null) {
                            myReady = true
                            viewModel.sendGameWsMessage("game_ready", partnerId, emptyMap())
                            if (partnerReady) phase = GamePhase.COUNTDOWN
                        }
                    }
                )
                GamePhase.COUNTDOWN -> CountdownScreen(value = countdownValue)
                GamePhase.PLAYING -> GameCanvas(
                    myCharacter      = myCharacter ?: GameCharacter.MARIO_ALT,
                    partnerCharacter = partnerCharacter ?: GameCharacter.LUIGI_ALT,
                    partnerState     = partnerState,
                    level            = selectedLevel,
                    onStateUpdate    = { newState, gY ->
                        myState = newState
                        // Sync an Partner – Y relativ zu lokalem groundY senden
                        viewModel.sendGameWsMessage(
                            "game_state", partnerId,
                            mapOf(
                                "x"            to newState.x,
                                "y"            to (newState.y - gY),
                                "vx"           to newState.velX,
                                "facing_right" to newState.facingRight,
                                "coins"        to newState.coinsCollected,
                                "reached_goal" to newState.reachedGoal,
                                "sky_coins"    to newState.collectedCoinIds.toList()
                            )
                        )
                        if (newState.reachedGoal) {
                            resultCoins    = newState.coinsCollected
                            resultDuration = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                            resultWon      = true
                            phase = GamePhase.RESULTS
                            viewModel.saveGameSession(selectedLevel, resultCoins, resultDuration, "win", "jump_run", partnerId, partnerName)
                        }
                    },
                    onTimeout = { coinsCollected ->
                        resultCoins    = coinsCollected
                        resultDuration = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                        resultWon      = partnerEliminated
                        phase = GamePhase.RESULTS
                        if (!partnerEliminated) viewModel.sendGameWsMessage("game_end", partnerId, emptyMap())
                        viewModel.saveGameSession(selectedLevel, resultCoins, resultDuration, if (partnerEliminated) "win" else "lose", "jump_run", partnerId, partnerName)
                    },
                    onFellOffSky = { coinsCollected ->
                        // Spieler ist von der Himmelsplattform gefallen → Spiel sofort beendet
                        resultCoins    = coinsCollected
                        resultDuration = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                        resultWon      = false
                        phase = GamePhase.RESULTS
                        viewModel.sendGameWsMessage("game_sky_fall", partnerId, emptyMap())
                        viewModel.saveGameSession(selectedLevel, resultCoins, resultDuration, "lose", "jump_run", partnerId, partnerName)
                    }
                )
                GamePhase.RESULTS -> ResultsScreen(
                    coins                = resultCoins,
                    duration             = resultDuration,
                    won                  = resultWon,
                    partnerName          = partnerName,
                    myWantsPlayAgain     = myWantsPlayAgain,
                    partnerWantsPlayAgain = partnerWantsPlayAgain,
                    onPlayAgain          = {
                        myWantsPlayAgain = true
                        viewModel.sendGameWsMessage("play_again", partnerId, emptyMap())
                        if (partnerWantsPlayAgain) {
                            myCharacter           = null
                            partnerCharacter      = null
                            myReady               = false
                            partnerReady          = false
                            myWantsPlayAgain      = false
                            partnerWantsPlayAgain = false
                            myState               = PlayerState()
                            partnerState          = null
                            partnerEliminated     = false
                            selectedLevel         = 1
                            phase                 = GamePhase.CHAR_SELECT
                        }
                    },
                    onBackToHub          = {
                        myCharacter           = null
                        partnerCharacter      = null
                        myReady               = false
                        partnerReady          = false
                        myWantsPlayAgain      = false
                        partnerWantsPlayAgain = false
                        myState               = PlayerState()
                        partnerState          = null
                        partnerEliminated     = false
                        partnerId             = ""
                        partnerName           = ""
                        selectedLevel         = 1
                        phase                 = GamePhase.HUB
                    }
                )
                GamePhase.ACTIVITY_PLAYING -> ActivityGameScreen(
                    sketchLayer          = activitySketchLayer,
                    isDrawer             = activityIsDrawer,
                    currentWord          = activityCurrentWord,
                    wordChoices          = activityWordChoices,
                    wordChosen           = activityWordChosen,
                    coins                = activityCoins,
                    timer                = activityTimer,
                    myStrokes            = activityMyStrokes,
                    myCurrentStroke      = activityMyCurrentStroke,
                    partnerStrokes       = activityPartnerStrokes,
                    partnerCurrentStroke = activityPartnerCurrent,
                    guessText            = activityGuessText,
                    correctWordToShow    = activityCorrectWord,
                    selectedColor        = activitySelectedColor,
                    strokeWidth          = activityStrokeWidth,
                    onGuessTextChange    = { activityGuessText = it },
                    onGuessSubmit        = {
                        if (activityGuessText.isNotBlank()) {
                            viewModel.sendGameWsMessage(
                                "activity_guess", partnerId,
                                mapOf("text" to activityGuessText)
                            )
                            activityGuessText = ""
                        }
                    },
                    onWordChosen         = { word ->
                        activityCurrentWord = word
                        activityWordChosen  = true
                        viewModel.sendGameWsMessage("activity_word_chosen", partnerId, emptyMap())
                    },
                    onStrokeEnd          = { stroke ->
                        activityMyStrokes       = activityMyStrokes + stroke
                        activityMyCurrentStroke = null
                        viewModel.sendGameWsMessage(
                            "activity_draw_stroke", partnerId,
                            mapOf("points" to stroke.points, "color" to stroke.colorArgb, "width" to stroke.width)
                        )
                    },
                    onStrokePartial      = { stroke ->
                        activityMyCurrentStroke = stroke
                        val now = System.currentTimeMillis()
                        if (now - activityLastPartialSent > 50) {
                            activityLastPartialSent = now
                            viewModel.sendGameWsMessage(
                                "activity_draw_partial", partnerId,
                                mapOf("points" to stroke.points, "color" to stroke.colorArgb, "width" to stroke.width)
                            )
                        }
                    },
                    onClearCanvas        = {
                        activityMyStrokes       = emptyList()
                        activityMyCurrentStroke = null
                        viewModel.sendGameWsMessage("activity_draw_clear", partnerId, emptyMap())
                    },
                    onColorChange        = { activitySelectedColor = it },
                    onStrokeWidthChange  = { activityStrokeWidth = it }
                )
                GamePhase.ACTIVITY_RESULTS -> ActivityResultsScreen(
                    coins      = activityCoins,
                    onBackToHub = {
                        activityIsDrawer        = false
                        activityCurrentWord     = ""
                        activityWordChoices     = emptyList()
                        activityWordChosen      = false
                        activityCoins           = 0
                        activityTimer           = 120
                        activityMyStrokes       = emptyList()
                        activityMyCurrentStroke = null
                        activityPartnerStrokes  = emptyList()
                        activityPartnerCurrent  = null
                        activityCorrectWord     = ""
                        activityGuessText       = ""
                        partnerId               = ""
                        partnerName             = ""
                        phase                   = GamePhase.HUB
                    }
                )
                GamePhase.TIC_TAC_TOE_PLAYING -> TicTacToeScreen(
                    mySymbol    = tttMySymbol,
                    board       = tttBoard,
                    isMyTurn    = tttIsMyTurn,
                    partnerName = partnerName,
                    onMove      = { row, col ->
                        if (!tttIsMyTurn || tttWon != null) return@TicTacToeScreen
                        val now = System.currentTimeMillis()
                        val myPieces = tttBoard.filter { it.player == tttMySymbol }
                        // Ältesten Stein entfernen wenn bereits 3 Steine
                        val newBoard = if (myPieces.size >= 3) {
                            val oldest = myPieces.minByOrNull { it.placedAt }!!
                            tttBoard.filter { it != oldest }
                        } else tttBoard
                        // Zug setzen
                        if (newBoard.any { it.row == row && it.col == col }) return@TicTacToeScreen
                        val updatedBoard = newBoard + TttCell(row, col, tttMySymbol, now)
                        tttBoard    = updatedBoard
                        tttIsMyTurn = false
                        viewModel.sendGameWsMessage(
                            "ttt_move", partnerId,
                            mapOf("row" to row, "col" to col, "symbol" to tttMySymbol.toString())
                        )
                        // Gewinner prüfen
                        val winner = checkTttWinner(updatedBoard)
                        if (winner != null) {
                            tttWon = winner
                            viewModel.sendGameWsMessage("ttt_end", partnerId, mapOf("winner" to winner.toString()))
                            if (winner == tttMySymbol && !tttRewardClaimed) {
                                tttRewardClaimed = true
                                viewModel.claimTttReward("win", partnerId, partnerName)
                            }
                            phase = GamePhase.TIC_TAC_TOE_RESULTS
                        }
                    },
                    onBackToHub = {
                        tttBoard    = emptyList()
                        tttWon      = null
                        partnerId   = ""
                        partnerName = ""
                        phase       = GamePhase.HUB
                    }
                )
                GamePhase.TIC_TAC_TOE_RESULTS -> TicTacToeResultsScreen(
                    won         = tttWon == tttMySymbol,
                    isDraw      = tttWon == 'D',
                    partnerName = partnerName,
                    countdown   = tttResultsCountdown,
                    onPlayAgain = {
                        tttRoundNumber++
                        // Nächste Runde: Symbol tauschen → der andere Spieler beginnt
                        tttMySymbol  = if (tttMySymbol == 'X') 'O' else 'X'
                        tttBoard     = emptyList()
                        tttWon       = null
                        tttRewardClaimed = false
                        tttIsMyTurn  = tttMySymbol == 'X'
                        phase = GamePhase.TIC_TAC_TOE_PLAYING
                        viewModel.sendGameWsMessage("ttt_rematch", partnerId, emptyMap())
                    },
                    onBackToHub = {
                        tttBoard    = emptyList()
                        tttWon      = null
                        partnerId   = ""
                        partnerName = ""
                        tttRoundNumber = 0
                        phase       = GamePhase.HUB
                    }
                )
            }

            // Kontakt-Auswahl Dialog
            if (showContactPicker) {
                ContactPickerDialog(
                    contacts = contacts,
                    onSelect = { contact ->
                        showContactPicker = false
                        partnerId   = contact.userId
                        partnerName = contact.username ?: contact.fakeNumber
                        viewModel.sendGameWsMessage(
                            "game_invite", contact.userId,
                            mapOf(
                                "from_name" to (currentUser?.name ?: ""),
                                "game_type" to selectedGameType.name
                            )
                        )
                        val gameName = when (selectedGameType) {
                            GameType.JUMP_AND_RUN -> "JUMP and Run"
                            GameType.ACTIVITY     -> "Zeichnen & Raten"
                            GameType.TIC_TAC_TOE  -> "Tic Tac Toe"
                            GameType.TILT_N_DROP  -> "Neon Tilt 'n' Drop"
                            GameType.JUMP_OR_DIE  -> "JUMP or Die"
                            GameType.PINBALL      -> "Flipper"
                        }
                        viewModel.insertGameInviteSystemMessage(contact.userId, gameName)
                        if (selectedGameType == GameType.TILT_N_DROP) {
                            viewModel.setTiltNDropParams(contact.userId, contact.username ?: contact.fakeNumber, isHost = true)
                        }
                        phase = GamePhase.INVITE_SENT
                    },
                    onDismiss = { showContactPicker = false }
                )
            }

            // ── Spielhistorie-Overlay ──────────────────────────────────
            if (showHistory) {
                GameHistoryScreen(
                    viewModel = viewModel,
                    onClose = { showHistory = false }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Spielhistorie
// ─────────────────────────────────────────────────────────────────
@Composable
fun GameHistoryScreen(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    var entries by remember { mutableStateOf<List<com.securechat.app.data.network.GamingHistoryEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val resp = viewModel.getGamingHistory()
                if (resp.isSuccessful) entries = resp.body() ?: emptyList()
            } catch (_: Exception) {}
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Titelleiste
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
                Text(
                    text = "Spielhistorie",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            HorizontalDivider()

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Spielhistorie vorhanden.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(entries) { _, entry ->
                        GameHistoryRow(entry)
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHistoryRow(entry: com.securechat.app.data.network.GamingHistoryEntry) {
    val gameName = when (entry.gameType) {
        "jump_run" -> "JUMP and Run"
        "tictactoe", "ttt" -> "Tic Tac Toe"
        "activity" -> "Zeichnen & Raten"
        "sknch" -> "Sketch & Check"
        else -> entry.gameType
    }
    val resultColor = when (entry.result) {
        "win"  -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        "lose" -> androidx.compose.ui.graphics.Color(0xFFF44336)
        else   -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val resultLabel = when (entry.result) {
        "win"  -> "Gewonnen"
        "lose" -> "Verloren"
        "draw" -> "Unentschieden"
        else   -> entry.result
    }
    val dateLabel = entry.createdAt?.let {
        try {
            val inst = Instant.parse(it)
            val zdt  = inst.atZone(java.time.ZoneId.systemDefault())
            "%02d.%02d.%04d %02d:%02d".format(zdt.dayOfMonth, zdt.monthValue, zdt.year, zdt.hour, zdt.minute)
        } catch (_: Exception) { it.take(16) }
    } ?: "${entry.periodMonth}/${entry.periodYear}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(gameName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            val opponent = entry.opponentUsername?.takeIf { it.isNotBlank() }
            if (opponent != null) {
                Text("vs. $opponent", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Zusatzinfo Jump & Run
            if (entry.gameType == "jump_run" && entry.durationSeconds != null && entry.durationSeconds > 0) {
                val m = entry.durationSeconds / 60
                val s = entry.durationSeconds % 60
                Text("Level ${entry.level ?: 1} · ${m}m ${s}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(dateLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(resultLabel, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold, color = resultColor)
            if (entry.coinsEarned > 0) {
                Text("🪙 ${entry.coinsEarned}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Hub Screen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun HubScreen(
    ranking: List<GameRankingEntry>,
    resetAt: String,
    lastMonthTop3: List<GameMonthWinner>,
    jodCurrentMonth: List<JodLeaderboardEntry>,
    jodPrevMonth: List<JodLeaderboardEntry>,
    initialLeaderboardMode: Int = 0,
    onStartNewGame: () -> Unit
) {
    // Countdown bis zum nächsten Reset
    var secondsUntilReset by remember { mutableLongStateOf(0L) }
    LaunchedEffect(resetAt) {
        if (resetAt.isEmpty()) return@LaunchedEffect
        while (true) {
            val target = try {
                Instant.parse(resetAt).epochSecond
            } catch (_: Exception) { return@LaunchedEffect }
            secondsUntilReset = maxOf(0L, target - Instant.now().epochSecond)
            delay(1_000L)
        }
    }
    val days    = secondsUntilReset / 86400
    val hours   = (secondsUntilReset % 86400) / 3600
    val minutes = (secondsUntilReset % 3600) / 60
    val secs    = secondsUntilReset % 60
    val countdownStr = when {
        days > 0  -> "${days}T ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m ${secs}s"
        else      -> "${minutes}m ${secs}s"
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = onStartNewGame,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.games_new_game), style = MaterialTheme.typography.titleMedium)
        }

        // Reset-Countdown
        if (resetAt.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.games_ranking_reset_countdown),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            countdownStr,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Vormonat Top-3
        if (lastMonthTop3.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null,
                            tint = Color(0xFFFFD600))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.games_top_last_month), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    lastMonthTop3.forEach { winner ->
                        val medalColor = when (winner.rank) {
                            1 -> Color(0xFFFFD600)
                            2 -> Color(0xFFB0BEC5)
                            else -> Color(0xFFFF8F00)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "#${winner.rank}",
                                style = MaterialTheme.typography.labelLarge,
                                color = medalColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                winner.username,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(13.dp)) {
                                    drawCircle(Color(0xFFFFD600))
                                    drawCircle(Color(0xFFFF8F00), radius = size.minDimension / 2f,
                                        style = Stroke(1.5f))
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "${winner.totalCoins}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Weltrangliste: Multiplayer / Singleplayer auswählen
        var leaderboardMode by remember { mutableStateOf(initialLeaderboardMode) } // 0 = Multiplayer, 1 = Singleplayer

        // Monatswischer innerhalb des gewählten Modus (Seite 0 = aktuell, 1 = Vormonat)
        val leaderboardPagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { 2 }
        )
        val currentMonthName = remember {
            java.time.LocalDate.now().month.getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.GERMAN
            ).replaceFirstChar { it.uppercase() }
        }
        val prevMonthName = remember {
            java.time.LocalDate.now().minusMonths(1).month.getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.GERMAN
            ).replaceFirstChar { it.uppercase() }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {

                // Modus-Buttons oben
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { leaderboardMode = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (leaderboardMode == 0) Color(0xFFFFD600) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = if (leaderboardMode == 0) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Multiplayer", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { leaderboardMode = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (leaderboardMode == 1) Color(0xFFFFD600) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor   = if (leaderboardMode == 1) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text("Singleplayer", fontWeight = FontWeight.Bold) }
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null,
                        tint = Color(0xFFFFD600))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (leaderboardPagerState.currentPage == 0)
                            "🏆 Weltrangliste – $currentMonthName"
                        else
                            "📅 Vormonat – $prevMonthName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // Teilen-Button: aktueller Monat, Multiplayer und Singleplayer
                    if (leaderboardPagerState.currentPage == 0) {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val bmp = if (leaderboardMode == 0)
                                        createLeaderboardBitmap(ranking, context.resources)
                                    else
                                        createJodLeaderboardBitmap(jodCurrentMonth, context.resources)
                                    val shareDir = File(context.cacheDir, "share").also { it.mkdirs() }
                                    val file = File(shareDir, "weltrangliste.png")
                                    withContext(Dispatchers.IO) {
                                        file.outputStream().use {
                                            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                                        }
                                    }
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Weltrangliste teilen"))
                                } catch (e: Exception) {
                                    android.util.Log.e("GamesScreen", "Share Fehler: ${e.message}", e)
                                    android.widget.Toast.makeText(context, "Teilen fehlgeschlagen", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Teilen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Seitenwischer für aktuellen und vorherigen Monat
                HorizontalPager(
                    state = leaderboardPagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    if (leaderboardMode == 0) {
                        // MULTIPLAYER
                        when (page) {
                            0 -> {
                                if (ranking.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(stringResource(R.string.games_leaderboard_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        itemsIndexed(ranking) { _, entry ->
                                            RankingRow(entry)
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (lastMonthTop3.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Keine Daten für den Vormonat.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        itemsIndexed(lastMonthTop3) { _, winner ->
                                            RankingRow(
                                                GameRankingEntry(
                                                    username = winner.username,
                                                    totalCoins = winner.totalCoins,
                                                    rank = winner.rank
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // SINGLEPLAYER – JOD
                        val entries = if (page == 0) jodCurrentMonth else jodPrevMonth
                        if (entries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (page == 0) "Noch keine Einträge diesen Monat."
                                    else "Keine Daten für den Vormonat.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(entries) { _, entry ->
                                    JodRankingRow(entry)
                                }
                            }
                        }
                    }
                }

                // Seiten-Indikatoren
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (leaderboardPagerState.currentPage == 1)
                                    Color(0xFFA8A800)
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (leaderboardPagerState.currentPage == 0)
                                    Color(0xFFA8A800)
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun JodRankingRow(entry: JodLeaderboardEntry) {
    val medalColor = when (entry.rank) {
        1 -> Color(0xFFFFD600)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFFF8F00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${entry.rank}",
            style = MaterialTheme.typography.labelLarge,
            color = medalColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )
        Text(
            text = entry.username,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${entry.bestScore} Pts",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD600)
        )
    }
}

@Composable
private fun RankingRow(entry: GameRankingEntry) {
    val medalColor = when (entry.rank) {
        1 -> Color(0xFFFFD600)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFFF8F00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val playMinutes = entry.totalPlayDuration / 60
    val playHours   = playMinutes / 60
    val playTimeStr = if (playHours > 0) "${playHours}h ${playMinutes % 60}m" else "${playMinutes}m"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${entry.rank}",
            style = MaterialTheme.typography.labelLarge,
            color = medalColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.username, style = MaterialTheme.typography.bodyMedium)
            if (entry.totalPlayDuration > 0) {
                Text(
                    text = stringResource(R.string.games_played_time, playTimeStr),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(14.dp)) {
                drawCircle(Color(0xFFFFD600))
                drawCircle(Color(0xFFFF8F00), radius = size.minDimension / 2f, style = Stroke(1.5f))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${entry.totalCoins}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun createJodLeaderboardBitmap(
    entries: List<JodLeaderboardEntry>,
    resources: android.content.res.Resources
): android.graphics.Bitmap {
    val d = resources.displayMetrics.density
    val width = (320 * d).toInt()
    val rowH = (48 * d).toInt()
    val headerH = (52 * d).toInt()
    val pad = (16 * d).toInt()
    val height = headerH + entries.size * rowH + pad

    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cornerR = 16 * d

    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E1B2E")
    }
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerR, cornerR, bgPaint)

    val goldenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFD600")
    }
    canvas.drawCircle(pad + 8 * d, headerH / 2f, 10 * d, goldenPaint)

    val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 18 * d
        isFakeBoldText = true
    }
    canvas.drawText("Jump or Die – Weltrangliste", pad + 24 * d, headerH / 2f + 7 * d, titlePaint)

    val divPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#333333")
    }
    canvas.drawRect(pad.toFloat(), (headerH - 1).toFloat(), (width - pad).toFloat(), headerH.toFloat(), divPaint)

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 14 * d
    }

    entries.forEach { entry ->
        val rowTop = headerH + (entry.rank - 1) * rowH
        val baseY = rowTop + rowH / 2f + 5 * d
        val medalColor = when (entry.rank) {
            1 -> android.graphics.Color.parseColor("#FFD600")
            2 -> android.graphics.Color.parseColor("#B0BEC5")
            3 -> android.graphics.Color.parseColor("#FF8F00")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
        val rankPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = medalColor
            textSize = 14 * d
            isFakeBoldText = true
        }
        canvas.drawText("#${entry.rank}", pad.toFloat(), baseY, rankPaint)
        canvas.drawText(entry.username, pad + 44 * d, baseY, textPaint)
        val scoreText = "${entry.bestScore} Pkt."
        val bounds = android.graphics.Rect()
        textPaint.getTextBounds(scoreText, 0, scoreText.length, bounds)
        canvas.drawText(scoreText, (width - pad - bounds.width()).toFloat(), baseY, textPaint)
    }
    return bmp
}

private fun createLeaderboardBitmap(
    ranking: List<GameRankingEntry>,
    resources: android.content.res.Resources
): android.graphics.Bitmap {
    val d = resources.displayMetrics.density
    val width = (320 * d).toInt()
    val rowH = (48 * d).toInt()
    val headerH = (52 * d).toInt()
    val pad = (16 * d).toInt()
    val height = headerH + ranking.size * rowH + pad

    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cornerR = 16 * d

    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E1B2E")
    }
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerR, cornerR, bgPaint)

    val goldenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FFD600")
    }
    canvas.drawCircle(pad + 8 * d, headerH / 2f, 10 * d, goldenPaint)

    val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 18 * d
        isFakeBoldText = true
    }
    canvas.drawText("Weltrangliste", pad + 24 * d, headerH / 2f + 7 * d, titlePaint)

    val divPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#333333")
    }
    canvas.drawRect(pad.toFloat(), (headerH - 1).toFloat(), (width - pad).toFloat(), headerH.toFloat(), divPaint)

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 14 * d
    }

    ranking.forEach { entry ->
        val rowTop = headerH + (entry.rank - 1) * rowH
        val baseY = rowTop + rowH / 2f + 5 * d
        val medalColor = when (entry.rank) {
            1 -> android.graphics.Color.parseColor("#FFD600")
            2 -> android.graphics.Color.parseColor("#B0BEC5")
            3 -> android.graphics.Color.parseColor("#FF8F00")
            else -> android.graphics.Color.parseColor("#9E9E9E")
        }
        val rankPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = medalColor
            textSize = 14 * d
            isFakeBoldText = true
        }
        canvas.drawText("#${entry.rank}", pad.toFloat(), baseY, rankPaint)
        canvas.drawText(entry.username, pad + 44 * d, baseY, textPaint)
        val coinsText = "${entry.totalCoins} Styx"
        val bounds = android.graphics.Rect()
        textPaint.getTextBounds(coinsText, 0, coinsText.length, bounds)
        canvas.drawText(coinsText, (width - pad - bounds.width()).toFloat(), baseY, textPaint)
    }
    return bmp
}

// ─────────────────────────────────────────────────────────────────
// Spiel-Auswahl Screen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun GameSelectScreen(
    onSelectGame: (GameType) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.games_play_cd))
            }
            Text(
                stringResource(R.string.games_select_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            stringResource(R.string.games_select_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        JumpAndRunCoverTile(onClick = { onSelectGame(GameType.JUMP_AND_RUN) })
        ActivityCoverTile(onClick = { onSelectGame(GameType.ACTIVITY) })
        TicTacToeCoverTile(onClick = { onSelectGame(GameType.TIC_TAC_TOE) })
        TiltNDropCoverTile(onClick = { onSelectGame(GameType.TILT_N_DROP) })
        JumpOrDieCoverTile(onClick = { onSelectGame(GameType.JUMP_OR_DIE) })
        PinballCoverTile(onClick = { onSelectGame(GameType.PINBALL) })

        // Abstand am Ende der Scroll-Liste
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun JumpAndRunCoverTile(onClick: () -> Unit) {
    val sprites = rememberGameSprites()
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cover-Grafik
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val groundY = h * 0.72f

                // Hintergrund-Bild (Hills)
                drawImage(sprites.bgHills,
                    dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                    dstSize   = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Low)

                // Boden – gekachelt
                drawGroundTiled(sprites.groundTile, 0f, groundY, w, h - groundY)

                // Münzblöcke (Sprite)
                val bw = w * 0.07f
                for (bx in listOf(w * 0.29f, w * 0.41f)) {
                    val by = h * 0.30f
                    drawSprite(sprites.blockActive, bx, by, bw, bw)
                }

                // ── Mario (links) ──
                val charH = h * 0.38f
                val charW = charH * 0.7f
                val marioX = w * 0.18f - charW / 2f
                val marioY = groundY - charH
                drawSprite(sprites.mario.front, marioX, marioY, charW, charH)

                // ── Peach (rechts) ──
                val peachX = w * 0.62f - charW / 2f
                val peachY = groundY - charH
                drawSprite(sprites.peach.front, peachX, peachY, charW, charH)

                // Münze zwischen den Charakteren
                val coinX = w * 0.44f
                val coinY = groundY - h * 0.22f
                drawCircle(Color(0xFFFFD600), h * 0.04f, Offset(coinX, coinY))
                drawCircle(Color(0xFFFF8F00), h * 0.04f, Offset(coinX, coinY), style = Stroke(2f))
                drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.4f), h * 0.015f, Offset(coinX - h * 0.012f, coinY - h * 0.012f))
            }

            // Titel-Overlay unten
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    stringResource(R.string.games_jump_run_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    stringResource(R.string.games_jump_run_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Play-Button oben rechts
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.games_play_cd),
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp).size(24.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Warte-Screen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun WaitingScreen(partnerName: String, onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dots by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200)),
        label = "dots"
    )
    val dotStr = ".".repeat(dots.toInt() + 1)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.games_invite_sent, partnerName) + dotStr,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center)
            Text(stringResource(R.string.games_waiting_acceptance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.games_decline))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Einladungs-Screen (empfangen)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun InviteReceivedScreen(
    partnerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.SportsEsports, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.games_invite_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.games_invite_wants_play_with_you, partnerName),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDecline) { Text(stringResource(R.string.games_decline)) }
                    Button(onClick = onAccept) { Text(stringResource(R.string.games_accept)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Charakter-Auswahl Screen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun CharSelectScreen(
    myUsername: String?,
    partnerName: String,
    myCharacter: GameCharacter?,
    partnerCharacter: GameCharacter?,
    myReady: Boolean,
    partnerReady: Boolean,
    selectedLevel: Int,
    myTotalCoins: Int,
    onSelectCharacter: (GameCharacter) -> Unit,
    onSelectLevel: (Int) -> Unit,
    onStartGame: () -> Unit
) {
    val bothSelected = myCharacter != null && partnerCharacter != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.games_choose_character),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.games_partner, partnerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GameCharacter.entries.forEach { char ->
                CharacterCard(
                    character        = char,
                    myUsername       = myUsername,
                    partnerUsername  = partnerName,
                    isSelectedByMe   = myCharacter == char,
                    isSelectedByPartner = partnerCharacter == char,
                    onClick = { onSelectCharacter(char) },
                    modifier         = Modifier.weight(1f)
                )
            }
        }

        // Legende
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.games_you), style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(0xFFE040FB), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(partnerName, style = MaterialTheme.typography.labelSmall)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(R.string.games_choose_level),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        val levelMeadow = stringResource(R.string.games_level_meadow)
        val levelCave = stringResource(R.string.games_level_cave)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(1 to levelMeadow, 2 to levelCave).forEach { (lvl, label) ->
                val isSelected = selectedLevel == lvl
                val isLocked = lvl == 2 && myTotalCoins < 400
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            when {
                                isLocked   -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                isSelected -> MaterialTheme.colorScheme.primary
                                else       -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = if (isLocked) 0.2f else 0.5f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .then(if (!isLocked) Modifier.clickable { onSelectLevel(lvl) } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.games_locked_cd),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "400 🪙",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            )
                        } else {
                            Text(
                                text = "$lvl",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Bereit-Status
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatusChip("Du", myReady)
            StatusChip(partnerName, partnerReady)
        }

        Button(
            onClick = onStartGame,
            enabled = myCharacter != null && !myReady,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            val startBtnText = when {
                myReady && partnerReady -> stringResource(R.string.games_starting)
                myReady -> stringResource(R.string.games_waiting_partner, partnerName)
                else -> stringResource(R.string.games_start_game)
            }
            Text(text = startBtnText)
        }
    }
}

@Composable
private fun StatusChip(name: String, ready: Boolean) {
    Row(
        modifier = Modifier
            .background(
                if (ready) Color(0xFF1B5E20).copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(50)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (ready) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (ready) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}

// ─────────────────────────────────────────────────────────────────
// Countdown Screen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun CountdownScreen(value: Int) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "countdown_scale"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.games_get_ready),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (value > 0) "$value" else stringResource(R.string.games_go),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
                fontWeight = FontWeight.ExtraBold,
                color = if (value > 0) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Spiel-Canvas
// ─────────────────────────────────────────────────────────────────
@Composable
private fun GameCanvas(
    myCharacter: GameCharacter,
    partnerCharacter: GameCharacter,
    partnerState: PlayerState?,
    level: Int,
    onStateUpdate: (PlayerState, Float) -> Unit,
    onTimeout: (coinsCollected: Int) -> Unit,
    onFellOffSky: (coinsCollected: Int) -> Unit
) {
    val sprites = rememberGameSprites()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val controlTop = screenH * 2f / 3f
        val groundY = controlTop - 60f

        // Plattformen, Coins und Röhren einmalig berechnen
        val platforms  = remember(groundY, level) { if (level == 2) buildPlatformsL2(groundY) else buildPlatforms(groundY) }
        val pipes      = remember(groundY, level) { if (level == 2) buildPipesL2(groundY) else buildPipes(groundY) }
        val walls      = remember(groundY, level) { if (level == 2) buildWallsL2(groundY) else emptyList() }
        var localCoins by remember(groundY, level) { mutableStateOf(if (level == 2) buildCoinsL2(groundY) else buildCoins(groundY)) }
        val clouds = remember(groundY, level) {
            if (level == 2) emptyList()
            else listOf(
                Triple(350f,  groundY - 195f, 1.0f),
                Triple(900f,  groundY - 220f, 1.25f),
                Triple(1500f, groundY - 200f, 0.9f),
                Triple(2050f, groundY - 215f, 1.1f),
                Triple(2650f, groundY - 200f, 1.3f),
                Triple(3200f, groundY - 210f, 0.85f),
                Triple(3750f, groundY - 205f, 1.0f),
                Triple(4300f, groundY - 215f, 1.15f),
                Triple(4750f, groundY - 200f, 0.95f),
            )
        }
        var localBlocks by remember(groundY, level) { mutableStateOf(if (level == 2) buildBlocksL2(groundY) else buildBlocks(groundY)) }
        var localBlockCoins by remember(groundY) { mutableIntStateOf(0) }
        var coinPopups by remember { mutableStateOf(emptyList<CoinPopup>()) }

        // Lauf-Animation
        var walkFrame        by remember { mutableIntStateOf(1) }
        var walkFrameTick    by remember { mutableIntStateOf(0) }

        // Touch-Eingabe-State
        var leftDragStart   by remember { mutableStateOf<Offset?>(null) }
        var leftDragCurrent by remember { mutableStateOf<Offset?>(null) }
        var jumpPressed     by remember { mutableStateOf(false) }
        var jumpConsumed    by remember { mutableStateOf(false) }

        // Spielzustand
        var localState  by remember(groundY) {
            mutableStateOf(PlayerState(x = 200f, y = groundY - PLAYER_H))
        }
        var syncCounter by remember { mutableLongStateOf(0L) }
        // Warp-State
        var warpCooldown       by remember { mutableLongStateOf(0L) }
        var showWarpEntryHint  by remember { mutableStateOf(false) }
        var showWarpReturnHint by remember { mutableStateOf(false) }
        // Röhren-Button
        var enterPipePressed  by remember { mutableStateOf(false) }
        var enterPipeConsumed by remember { mutableStateOf(false) }
        // Himmelsbereich-Tracking für Fall-Erkennung
        var inSkyZone         by remember { mutableStateOf(false) }
        var fellOffSkyFired   by remember { mutableStateOf(false) }

        // Countdown (Level 1: 50s, Level 2: 70s)
        val totalTime = if (level == 2) 70 else 50
        var gameTimeLeft by remember { mutableIntStateOf(totalTime) }
        LaunchedEffect(Unit) {
            repeat(totalTime) {
                delay(1000L)
                gameTimeLeft--
                if (gameTimeLeft <= 0) {
                    onTimeout(localState.coinsCollected)
                }
            }
        }

        // Game-Loop (~60fps)
        LaunchedEffect(groundY) {
            while (true) {
                delay(16L)

                // Horizontale Geschwindigkeit aus Drag-Delta
                val dragDelta = leftDragStart?.let { start ->
                    leftDragCurrent?.let { cur -> cur.x - start.x }
                } ?: 0f
                val newVX = (dragDelta / 60f * MAX_H_SPEED).coerceIn(-MAX_H_SPEED, MAX_H_SPEED)

                // Springen (inkl. Wand-Absprung in Level 2)
                val canJump = localState.onGround || (level == 2 && localState.touchingWallSide != 0)
                val shouldJump = jumpPressed && !jumpConsumed && canJump
                val newVY = if (shouldJump) { jumpConsumed = true; JUMP_VEL } else localState.velY
                if (!jumpPressed) jumpConsumed = false

                // Sprung-Sound (charakterabhängig)
                if (shouldJump) {
                    if (myCharacter == GameCharacter.PEACH) GameSoundPlayer.playPeachJump()
                    else GameSoundPlayer.playMarioJump()
                }

                val updatedState = applyPhysics(
                    localState.copy(
                        velX        = newVX,
                        velY        = newVY,
                        facingRight = if (newVX > 0.1f) true
                                      else if (newVX < -0.1f) false
                                      else localState.facingRight
                    ),
                    platforms, walls, groundY,
                    enableWallGrab = (level == 2)
                )

                // Warp-Cooldown
                if (warpCooldown > 0L) warpCooldown -= 16L

                // Warp-Erkennung
                val skyPlatY     = groundY - SKY_PLATFORM_Y_OFFSET
                val playerCX     = updatedState.x + PLAYER_W / 2f
                val onWarpEntry  = updatedState.onGround &&
                    playerCX >= WARP_PIPE_X - 4f && playerCX <= WARP_PIPE_X + 44f
                val retPipeAbsX  = SKY_PLATFORM_X + SKY_RETURN_PIPE_REL_X
                val onWarpReturn = updatedState.onGround &&
                    playerCX >= retPipeAbsX - 4f && playerCX <= retPipeAbsX + 44f &&
                    updatedState.y < skyPlatY - 50f

                showWarpEntryHint  = onWarpEntry  && warpCooldown <= 0L
                showWarpReturnHint = onWarpReturn && warpCooldown <= 0L
                if (!onWarpEntry && !onWarpReturn) enterPipeConsumed = false

                val shouldEnterPipe = enterPipePressed && !enterPipeConsumed

                var didWarp = false
                if (onWarpEntry && warpCooldown <= 0L && shouldEnterPipe) {
                    enterPipeConsumed = true
                    warpCooldown = 2000L
                    localState = updatedState.copy(
                        x = SKY_PLATFORM_X + 60f,
                        y = skyPlatY - PLAYER_H,
                        velX = 0f, velY = 0f, onGround = true,
                        collectedCoinIds = localState.collectedCoinIds
                    )
                    didWarp = true
                }

                if (!didWarp && onWarpReturn && warpCooldown <= 0L && shouldEnterPipe) {
                    enterPipeConsumed = true
                    warpCooldown = 2000L
                    inSkyZone = false
                    localState = updatedState.copy(
                        x = WARP_PIPE_X + 60f,
                        y = groundY - PLAYER_H,
                        velX = 0f, velY = 0f, onGround = true,
                        collectedCoinIds = localState.collectedCoinIds
                    )
                    didWarp = true
                }

                // Warp nach oben: inSkyZone aktivieren
                if (didWarp && localState.y < skyPlatY + 200f) {
                    inSkyZone = true
                }

                // Himmelsfall-Erkennung: war oben, jetzt weit unterhalb der Himmelsplattform
                // didWarp ausschließen: Warp-Frame nicht als Fall werten
                if (!fellOffSkyFired && inSkyZone && !didWarp && updatedState.y > skyPlatY + 400f) {
                    fellOffSkyFired = true
                    inSkyZone = false
                    onFellOffSky(localState.coinsCollected)
                }

                if (!didWarp) {
                    // Münzen-Kollision (Gegner-Münzen berücksichtigen)
                    val partnerCollectedIds = partnerState?.collectedCoinIds ?: emptySet()
                    val prevCoins = localCoins
                    val updatedCoins = prevCoins.map { coin ->
                        when {
                            coin.collected -> coin
                            partnerCollectedIds.contains(coin.id) -> coin.copy(collected = true)
                            else -> {
                                val coinR = if (coin.id >= SKY_COIN_BASE_ID) 13f else 10f
                                val touches =
                                    coin.x + coinR > updatedState.x &&
                                    coin.x - coinR < updatedState.x + PLAYER_W &&
                                    coin.y + coinR > updatedState.y &&
                                    coin.y - coinR < updatedState.y + PLAYER_H
                                if (touches) coin.copy(collected = true) else coin
                            }
                        }
                    }
                    localCoins = updatedCoins

                    // Nur vom Spieler selbst gesammelte Münzen zählen
                    val myNewIds = updatedCoins.zip(prevCoins)
                        .filter { (updated, prev) ->
                            updated.collected && !prev.collected &&
                            !partnerCollectedIds.contains(updated.id)
                        }
                        .map { (updated, _) -> updated.id }
                        .toSet()

                    // Münzen-Sound
                    if (myNewIds.isNotEmpty()) GameSoundPlayer.playCoin()

                    val updatedCollectedIds = localState.collectedCoinIds + myNewIds

                    // Block-Kopf-Kollision (Fragezeichen-Blöcke von unten treffen)
                    var blockBonusCoins = 0
                    val newPopups = mutableListOf<CoinPopup>()
                    var blockHitFromBelow = false
                    var blockHitBottomY = updatedState.y
                    localBlocks = localBlocks.map { blk ->
                        val decayed = if (blk.bumpOffset < 0f)
                            blk.copy(bumpOffset = (blk.bumpOffset + 2f).coerceAtMost(0f))
                        else blk
                        val blkBottom = decayed.y + decayed.h
                        val playerCX = updatedState.x + PLAYER_W / 2f
                        val headHit = playerCX >= decayed.x && playerCX <= decayed.x + decayed.w &&
                            localState.y >= blkBottom - 4f && updatedState.y < blkBottom &&
                            newVY < 0f
                        if (headHit) {
                            blockHitFromBelow = true
                            blockHitBottomY = blkBottom
                        }
                        if (decayed.coinsLeft > 0 && headHit) {
                            blockBonusCoins++
                            newPopups.add(CoinPopup(decayed.x + decayed.w / 2f - 8f, decayed.y - 20f))
                            decayed.copy(coinsLeft = decayed.coinsLeft - 1, bumpOffset = -10f)
                        } else decayed
                    }
                    if (newPopups.isNotEmpty()) {
                        coinPopups = coinPopups + newPopups
                    }
                    // Coin-Popups animieren (aufsteigen + ausblenden)
                    coinPopups = coinPopups.mapNotNull { p ->
                        val na = p.alpha - 0.04f
                        if (na > 0f) p.copy(y = p.y - 2f, alpha = na) else null
                    }
                    if (blockBonusCoins > 0) {
                        localBlockCoins += blockBonusCoins
                        GameSoundPlayer.playCoin()
                    }

                    val stateAfterBlock = if (blockHitFromBelow)
                        updatedState.copy(y = blockHitBottomY, velY = 0f)
                    else updatedState
                    val finalState = stateAfterBlock.copy(
                        coinsCollected   = updatedCollectedIds.size + localBlockCoins,
                        reachedGoal      = stateAfterBlock.x >= FLAG_X,
                        collectedCoinIds = updatedCollectedIds
                    )
                    localState = finalState

                    // Lauf-Animation: Frame wechseln wenn Spieler sich bewegt
                    if (finalState.onGround && kotlin.math.abs(finalState.velX) > 0.5f) {
                        walkFrameTick++
                        if (walkFrameTick >= 6) {
                            walkFrameTick = 0
                            walkFrame = (walkFrame + 1) % 3
                        }
                    } else {
                        walkFrame = 1
                        walkFrameTick = 0
                    }

                    syncCounter += 16L
                    if (syncCounter >= SYNC_MS || finalState.reachedGoal) {
                        syncCounter = 0L
                        onStateUpdate(finalState, groundY)
                    }
                } else {
                    // Nach Warp: Zustand sofort synchronisieren
                    syncCounter += 16L
                    if (syncCounter >= SYNC_MS || localState.reachedGoal) {
                        syncCounter = 0L
                        onStateUpdate(localState, groundY)
                    }
                }
            }
        }

        // Kamera (folgt Spieler X + Y) – mit Zoom
        val visibleW   = screenW / ZOOM
        val visibleH   = controlTop / ZOOM
        val cameraX = (localState.x - visibleW / 2f + PLAYER_W / 2f)
            .coerceIn(0f, (WORLD_WIDTH - visibleW).coerceAtLeast(0f))
        val cameraYMax = groundY - visibleH * 0.82f
        val cameraY = (localState.y - visibleH * 0.80f)
            .coerceIn(cameraYMax - (SKY_PLATFORM_Y_OFFSET + 400f), cameraYMax)

        // Rendering
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Alle Touch-Events verarbeiten
                        val pointers = mutableMapOf<PointerId, Offset>()
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes) {
                                val pos = change.position
                                if (change.pressed) {
                                    pointers[change.id] = pos
                                } else {
                                    pointers.remove(change.id)
                                }
                                change.consume()
                            }
                            // Linke Hälfte: Bewegung (nur unteres Drittel)
                            val leftPointers = pointers.values.filter { it.x < screenW / 2f && it.y >= controlTop }
                            if (leftPointers.isNotEmpty()) {
                                if (leftDragStart == null) leftDragStart = leftPointers.first()
                                leftDragCurrent = leftPointers.first()
                            } else {
                                leftDragStart   = null
                                leftDragCurrent = null
                            }
                            val ctrlMidTouch = (controlTop + screenH) / 2f
                            // Rechte Seite oben: Springen
                            val rightPointers = pointers.values.filter {
                                it.x >= screenW * 0.65f && it.y >= controlTop && it.y < ctrlMidTouch
                            }
                            jumpPressed = rightPointers.isNotEmpty()
                            if (!jumpPressed) jumpConsumed = false
                            // Rechte Seite unten: Röhren-Button (↓)
                            val downBtnPointers = pointers.values.filter {
                                it.x >= screenW * 0.65f && it.y >= ctrlMidTouch
                            }
                            enterPipePressed = downBtnPointers.isNotEmpty()
                            if (!enterPipePressed) enterPipeConsumed = false
                        }
                    }
                }
        ) {
            // Hintergrund – nur Spielbereich (obere 2/3)
            if (level == 2) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1A1A), Color(0xFF2D2D2D)),
                        startY = 0f, endY = controlTop
                    ),
                    size = Size(size.width, controlTop)
                )
            } else {
                // Hintergrund-Sprite (Hills)
                drawImage(sprites.bgHills,
                    dstOffset = androidx.compose.ui.unit.IntOffset(0, 0),
                    dstSize   = androidx.compose.ui.unit.IntSize(size.width.toInt().coerceAtLeast(1), controlTop.toInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Low)
            }
            // Steuerungsbereich Hintergrund (unteres Drittel)
            drawRect(
                Color(0xFF0D1B2A),
                Offset(0f, controlTop),
                Size(size.width, size.height - controlTop)
            )
            // Trennlinie
            drawLine(
                Color(0xFF3F51B5),
                Offset(0f, controlTop),
                Offset(size.width, controlTop),
                strokeWidth = 3f
            )

            // ── Welt-Elemente (gezoomt + Kamera-Offset) ──────────────────
            withTransform({ clipRect(0f, 0f, screenW, controlTop) }) {
            scale(ZOOM, pivot = Offset(0f, 0f)) {
            translate(left = 0f, top = -cameraY) {

            // Boden
            if (level == 2) {
                drawRect(Color(0xFF5D4037), Offset(-cameraX, groundY), Size(WORLD_WIDTH, 30f))
                drawRect(Color(0xFF3E2723), Offset(-cameraX, groundY + 30f), Size(WORLD_WIDTH, 60f))
            } else {
                // Gras-Kacheln gekachelt
                drawGroundTiled(sprites.groundTile, -cameraX, groundY, -cameraX + WORLD_WIDTH, 60f)
            }

            // Level 2: Schacht-Wände (Felsmauern)
            if (level == 2) {
                for (wall in walls) {
                    val wx = wall.x - cameraX
                    if (wx > -wall.w - 10f && wx < screenW + 10f) {
                        // Wandkörper (dunkler Fels)
                        drawRect(Color(0xFF263238), Offset(wx, wall.topY), Size(wall.w, wall.h))
                        // Helle Kante oben
                        drawRect(Color(0xFF546E7A), Offset(wx, wall.topY), Size(wall.w, 6f))
                        // Gesteins-Struktur (Linien)
                        drawLine(Color(0xFF37474F), Offset(wx + wall.w * 0.35f, wall.topY + 20f),
                            Offset(wx + wall.w * 0.35f, wall.topY + wall.h - 20f), strokeWidth = 2f)
                        drawLine(Color(0xFF37474F), Offset(wx + wall.w * 0.70f, wall.topY + 40f),
                            Offset(wx + wall.w * 0.70f, wall.topY + wall.h - 40f), strokeWidth = 2f)
                    }
                }
            }

            // Level 2: Höhlendecke + Stalaktiten
            if (level == 2) {
                val ceilY = groundY - 420f
                drawRect(
                    Color(0xFF111111),
                    Offset(-cameraX, ceilY - 300f),
                    Size(WORLD_WIDTH, 300f)
                )
                val stalPositions = listOf(
                    100f to 50f, 280f to 38f, 460f to 58f, 640f to 43f,
                    820f to 52f, 1000f to 38f, 1200f to 60f, 1400f to 42f,
                    1580f to 54f, 1760f to 38f, 1950f to 58f, 2150f to 48f,
                    2330f to 43f, 2520f to 54f, 2700f to 38f, 2880f to 60f,
                    3060f to 44f, 3250f to 50f, 3430f to 58f, 3620f to 38f,
                    3800f to 54f, 3990f to 44f, 4180f to 60f, 4360f to 48f,
                    4540f to 38f, 4720f to 54f
                )
                for ((sx, sh) in stalPositions) {
                    val sScreenX = sx - cameraX
                    if (sScreenX > -80f && sScreenX < screenW + 80f) {
                        val stalPath = Path().apply {
                            moveTo(sScreenX - 14f, ceilY)
                            lineTo(sScreenX + 14f, ceilY)
                            lineTo(sScreenX, ceilY + sh)
                            close()
                        }
                        drawPath(stalPath, Color(0xFF424242))
                        drawPath(stalPath, Color(0xFF616161), style = Stroke(1f))
                    }
                }
            }

            // Wolken (vereinzelt im Hintergrund)
            for ((cloudX, cloudY, cloudScale) in clouds) {
                val csx = cloudX - cameraX
                if (csx > -120f && csx < screenW + 120f) {
                    drawCloud(csx, cloudY, cloudScale)
                }
            }

            // Plattformen (ohne Boden und ohne Anhöhe – werden separat gezeichnet)
            for (p in platforms.drop(1)) {
                val sx = p.x - cameraX
                if (sx > -p.w - 10f && sx < screenW + 10f) {
                    if (level == 2) {
                        drawRoundRect(Color(0xFF546E7A), Offset(sx, p.y), Size(p.w, p.h), CornerRadius(4f))
                        drawRoundRect(Color(0xFF78909C), Offset(sx, p.y), Size(p.w, 5f), CornerRadius(4f))
                    } else {
                        val isSkyPlat = p.y < groundY - 400f
                        drawPlatformTiled(sprites, sx, p.y, p.w, p.h, isSkyPlat)
                    }
                }
            }

            // Fragezeichen-Blöcke (von unten treffen zum Münzen sammeln)
            for (block in localBlocks) {
                val bx = block.x - cameraX
                if (bx > -block.w - 10f && bx < screenW + 10f) {
                    val by = block.y + block.bumpOffset
                    val blockSprite = if (block.coinCollected) sprites.blockInactive else sprites.blockActive
                    drawSprite(blockSprite, bx, by, block.w, block.h)
                }
            }

            // Münz-Popups (erscheinen beim Treffen eines Blocks)
            for (popup in coinPopups) {
                val px = popup.x - cameraX
                if (px > -20f && px < screenW + 20f) {
                    drawCircle(
                        Color(0xFFFFD600).copy(alpha = popup.alpha),
                        8f, Offset(px + 8f, popup.y)
                    )
                    drawCircle(
                        Color(0xFFFFEE58).copy(alpha = popup.alpha * 0.6f),
                        5f, Offset(px + 8f, popup.y)
                    )
                }
            }

            // Röhren zeichnen
            for (pipe in pipes) {
                val px = pipe.x - cameraX
                if (px > -pipe.capW - 10f && px < screenW + 10f) {
                    val isWarp = pipe.x == WARP_PIPE_X
                    // Körper
                    drawRect(
                        Color(0xFF2E7D32),
                        Offset(px, pipe.topY), Size(pipe.w, pipe.h)
                    )
                    drawRect(
                        Color(0xFF388E3C),
                        Offset(px, pipe.topY), Size(6f, pipe.h)
                    )
                    // Kappe (breiter)
                    val capX = px - (pipe.capW - pipe.w) / 2f
                    drawRoundRect(
                        Color(0xFF1B5E20),
                        Offset(capX, pipe.capY), Size(pipe.capW, pipe.capH), CornerRadius(3f)
                    )
                    drawRoundRect(
                        Color(0xFF43A047),
                        Offset(capX, pipe.capY), Size(6f, pipe.capH), CornerRadius(3f)
                    )
                    // Warp-Röhre: goldener Halo oben
                    if (isWarp) {
                        drawCircle(
                            Color(0xFFFFD600).copy(alpha = 0.35f),
                            22f, Offset(px + pipe.w / 2f, pipe.capY - 14f)
                        )
                    }
                }
            }

            // Rückkehr-Röhre auf der Himmelsplattform – nach-unten-Röhre (orange, invertiert)
            run {
                val retX       = SKY_PLATFORM_X + SKY_RETURN_PIPE_REL_X
                val retGndY    = groundY - SKY_PLATFORM_Y_OFFSET
                val retH       = 80f; val retCapH = 12f; val retW = 55f; val retCapW = 63f
                val retTopY    = retGndY - retH
                val retCapY    = retTopY - retCapH
                val rpx        = retX - cameraX
                val rcapX      = rpx - (retCapW - retW) / 2f
                if (rpx > -retCapW - 10f && rpx < screenW + 10f) {
                    // Körper (grün – "nach unten")
                    drawRect(Color(0xFF2E7D32), Offset(rpx, retTopY), Size(retW, retH))
                    drawRect(Color(0xFF388E3C), Offset(rpx, retTopY), Size(6f, retH))
                    // Kappe oben (breitere Lippe)
                    drawRoundRect(Color(0xFF1B5E20), Offset(rcapX, retCapY),
                        Size(retCapW, retCapH), CornerRadius(3f))
                    drawRoundRect(Color(0xFF43A047), Offset(rcapX, retCapY),
                        Size(6f, retCapH), CornerRadius(3f))
                    // Pfeil ↓ auf Röhre
                    drawContext.canvas.nativeCanvas.apply {
                        val p = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 20f; isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            alpha = 220
                        }
                        drawText("↓", rpx + retW / 2f, retTopY + retH / 2f + 8f, p)
                    }
                    // Goldener Halo oben (wie Warp-Eingangsröhre)
                    drawCircle(
                        Color(0xFFFFD600).copy(alpha = 0.3f),
                        22f, Offset(rpx + retW / 2f, retCapY - 14f)
                    )
                }
            }

            // Anhöhe vor der Flagge
            val hillScreenX = HILL_X - cameraX
            if (hillScreenX < screenW + 20f && hillScreenX + HILL_W > -20f) {
                val hillTop = groundY - HILL_H
                // Hang-Dreieck links
                val slopePath = Path().apply {
                    moveTo(hillScreenX - 60f, groundY)
                    lineTo(hillScreenX, hillTop)
                    lineTo(hillScreenX, groundY)
                    close()
                }
                drawPath(slopePath, if (level == 2) Color(0xFF546E7A) else Color(0xFF4CAF50))
                // Plateau
                drawRect(if (level == 2) Color(0xFF546E7A) else Color(0xFF4CAF50), Offset(hillScreenX, hillTop), Size(HILL_W, 8f))
                drawRect(if (level == 2) Color(0xFF37474F) else Color(0xFF795548), Offset(hillScreenX, hillTop + 8f),
                    Size(HILL_W, HILL_H - 8f))
            }

            // Ziel-Flagge (auf der Anhöhe)
            val flagScreenX = FLAG_X - cameraX
            val hillTopY    = groundY - HILL_H
            if (flagScreenX > -20f && flagScreenX < screenW + 20f) {
                // Stange
                drawRect(Color(0xFFBDBDBD), Offset(flagScreenX, hillTopY - 180f),
                    Size(5f, 180f))
                // Flagge "Lethe"
                val flagPath = Path().apply {
                    moveTo(flagScreenX + 5f, hillTopY - 180f)
                    lineTo(flagScreenX + 65f, hillTopY - 165f)
                    lineTo(flagScreenX + 5f, hillTopY - 150f)
                    close()
                }
                drawPath(flagPath, Color(0xFFE53935))
                // Text "Lethe" auf Flagge
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 13f
                        isFakeBoldText = true
                    }
                    drawText("Lethe", flagScreenX + 12f, hillTopY - 163f, paint)
                }
            }

            // Münzen
            for (coin in localCoins) {
                if (!coin.collected) {
                    val cx = coin.x - cameraX
                    val cy = coin.y
                    if (cx > -20f && cx < screenW + 20f) {
                        val isSky = coin.id >= SKY_COIN_BASE_ID
                        val r = if (isSky) 13f else 10f
                        if (isSky) drawCircle(Color(0xFFFFD600).copy(alpha = 0.22f), 22f, Offset(cx, cy))
                        drawCircle(Color(0xFFFFD600), r, Offset(cx, cy))
                        drawCircle(Color(0xFFFF8F00), r, Offset(cx, cy), style = Stroke(2f))
                        drawCircle(Color(0xFFFFFFFF).copy(alpha = 0.4f),
                            if (isSky) 5f else 4f, Offset(cx - 3f, cy - 3f))
                    }
                }
            }

            // Partner-Charakter (y ist relativ zu partnerGroundY → + lokales groundY)
            if (partnerState != null) {
                val ppx = partnerState.x - cameraX
                val ppy = partnerState.y + groundY   // denormalisieren
                if (ppx > -50f && ppx < screenW + 50f) {
                    val pCharSprites = when (partnerCharacter) {
                        GameCharacter.MARIO_ALT -> sprites.mario
                        GameCharacter.LUIGI_ALT -> sprites.luigi
                        GameCharacter.PEACH     -> sprites.peach
                    }
                    val pSpriteW = PLAYER_W * 1.8f
                    val pSpriteH = PLAYER_H * 1.8f
                    val pSpriteX = ppx - (pSpriteW - PLAYER_W) / 2f
                    val pSpriteY = ppy - (pSpriteH - PLAYER_H)
                    drawCharSprite(pCharSprites, pSpriteX, pSpriteY, pSpriteW, pSpriteH,
                        facingRight = partnerState.facingRight, walkFrame = 1, onGround = partnerState.onGround)
                }
            }

            // Eigener Charakter
            val myScreenX = localState.x - cameraX
            val myScreenY = localState.y
            val myCharSprites = when (myCharacter) {
                GameCharacter.MARIO_ALT -> sprites.mario
                GameCharacter.LUIGI_ALT -> sprites.luigi
                GameCharacter.PEACH     -> sprites.peach
            }
            val mySpriteW = PLAYER_W * 1.8f
            val mySpriteH = PLAYER_H * 1.8f
            val mySpriteX = myScreenX - (mySpriteW - PLAYER_W) / 2f
            val mySpriteY = myScreenY - (mySpriteH - PLAYER_H)
            drawCharSprite(myCharSprites, mySpriteX, mySpriteY, mySpriteW, mySpriteH,
                facingRight = localState.facingRight, walkFrame = walkFrame, onGround = localState.onGround)

            } // Ende Welt-Translate
            } // Ende scale(ZOOM)
            } // Ende withTransform(clipRect)

            // HUD – Münzen (doppelte Größe)
            drawRoundRect(
                Color.Black.copy(alpha = 0.4f),
                Offset(8f, 8f), Size(200f, 60f), CornerRadius(10f)
            )
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40f
                    isFakeBoldText = true
                }
                drawText("🪙 ${localState.coinsCollected}", 18f, 58f, paint)
            }

            // HUD – Countdown (oben rechts, doppelte Größe)
            val countdownText = "⏱ $gameTimeLeft"
            val countdownPaint = android.graphics.Paint().apply {
                color = if (gameTimeLeft <= 10) android.graphics.Color.RED else android.graphics.Color.WHITE
                textSize = 40f
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            val cdBoxW = 180f
            val cdBoxX = screenW - cdBoxW - 8f
            drawRoundRect(
                Color.Black.copy(alpha = 0.4f),
                Offset(cdBoxX, 8f), Size(cdBoxW, 60f), CornerRadius(10f)
            )
            drawContext.canvas.nativeCanvas.apply {
                drawText(countdownText, screenW - 18f, 58f, countdownPaint)
            }

            // Touch-Overlay: Steuerungsbereich (unteres Drittel)
            val ctrlMidY = controlTop + (screenH - controlTop) / 2f
            // Linke Hälfte – Bewegungsindikator
            drawRoundRect(
                Color.White.copy(alpha = 0.08f),
                Offset(0f, controlTop), Size(screenW / 2f, screenH - controlTop),
                CornerRadius(0f)
            )
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(200, 255, 255, 255)
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("← Bewegen →", screenW / 4f, ctrlMidY + 8f, p)
            }
            // Rechte Seite oben – Sprung-Button
            val jumpColor = if (jumpPressed) Color(0xFF81D4FA) else Color.White
            val jumpCenterX = screenW * 0.825f
            val jumpCenterY = controlTop + (screenH - controlTop) / 4f
            drawCircle(
                jumpColor.copy(alpha = 0.25f),
                60f, Offset(jumpCenterX, jumpCenterY)
            )
            drawCircle(
                jumpColor.copy(alpha = 0.65f),
                60f, Offset(jumpCenterX, jumpCenterY),
                style = Stroke(3f)
            )
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 26f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("Sprung", jumpCenterX, jumpCenterY + 10f, p)
            }

            // Rechte Seite unten – Röhren-Button (↓)
            val downBtnCx = screenW * 0.825f
            val downBtnCy = controlTop + 3f * (screenH - controlTop) / 4f
            val isNearPipe = showWarpEntryHint || showWarpReturnHint
            val downBtnColor = when {
                showWarpEntryHint  -> Color(0xFF0D47A1)
                showWarpReturnHint -> Color(0xFFE65100)
                else               -> Color(0xFF37474F)
            }
            val downBtnAlpha = if (isNearPipe) 0.85f else 0.30f
            drawCircle(downBtnColor.copy(alpha = downBtnAlpha), 50f, Offset(downBtnCx, downBtnCy))
            drawCircle(downBtnColor.copy(alpha = (downBtnAlpha + 0.15f).coerceAtMost(1f)),
                50f, Offset(downBtnCx, downBtnCy), style = Stroke(3f))
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    color = if (isNearPipe) android.graphics.Color.WHITE
                            else android.graphics.Color.argb(120, 255, 255, 255)
                    textSize = 34f
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText("↓", downBtnCx, downBtnCy + 13f, p)
            }

            // Bewegungs-Drag-Indikator
            leftDragStart?.let { start ->
                leftDragCurrent?.let { cur ->
                    drawCircle(Color.White.copy(alpha = 0.3f), 30f, start)
                    drawLine(
                        Color.White.copy(alpha = 0.6f),
                        start, cur, strokeWidth = 3f
                    )
                    drawCircle(Color.White.copy(alpha = 0.5f), 16f, cur)
                }
            }

            // Hinweis: Röhren-Button leuchtet → Pfeiltaste drücken
            if (showWarpEntryHint || showWarpReturnHint) {
                val hintLabel = if (showWarpEntryHint) "↓  Röhre betreten" else "↓  Röhre verlassen"
                val hintColor = if (showWarpEntryHint) Color(0xFF0D47A1) else Color(0xFFE65100)
                val hintW = 200f; val hintH = 38f
                val hintX = screenW / 2f - hintW / 2f; val hintY = 14f
                drawRoundRect(hintColor.copy(alpha = 0.85f),
                    Offset(hintX, hintY), Size(hintW, hintH), CornerRadius(10f))
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 18f; isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(hintLabel, screenW / 2f, hintY + 25f, p)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Ergebnis-Screen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun ResultsScreen(
    coins: Int,
    duration: Int,
    won: Boolean,
    partnerName: String,
    myWantsPlayAgain: Boolean,
    partnerWantsPlayAgain: Boolean,
    onPlayAgain: () -> Unit,
    onBackToHub: () -> Unit
) {
    val minutes = duration / 60
    val seconds = duration % 60

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (won) stringResource(R.string.games_goal_reached) else stringResource(R.string.games_game_over),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (won) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurface
            )
            if (!won) Text(stringResource(R.string.games_partner_reached_goal, partnerName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ResultRow(stringResource(R.string.games_coins_collected), "🪙 $coins")
                    ResultRow(stringResource(R.string.games_play_time), "${minutes}m ${seconds}s")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBackToHub) { Text(stringResource(R.string.games_hub)) }
                Button(
                    onClick = onPlayAgain,
                    enabled = !myWantsPlayAgain
                ) {
                    val playAgainText = when {
                        myWantsPlayAgain && partnerWantsPlayAgain -> stringResource(R.string.games_starting_now)
                        myWantsPlayAgain -> stringResource(R.string.games_waiting_partner, partnerName)
                        else -> stringResource(R.string.games_play_again)
                    }
                    Text(text = playAgainText)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────
// Kontakt-Auswahl Dialog
// ─────────────────────────────────────────────────────────────────
@Composable
private fun ContactPickerDialog(
    contacts: List<ContactEntity>,
    onSelect: (ContactEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.games_invite_dialog_title)) },
        text = {
            if (contacts.isEmpty()) {
                Text(stringResource(R.string.games_no_contacts))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    itemsIndexed(contacts) { _, contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(contact) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(contact.username ?: contact.fakeNumber,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text(contact.fakeNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.games_decline_button)) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────
// Activity – Zeichen-/Ratespiel
// ─────────────────────────────────────────────────────────────────

@Composable
fun ActivityGameScreen(
    sketchLayer: GraphicsLayer,
    isDrawer: Boolean,
    currentWord: String,
    wordChoices: List<String>,
    wordChosen: Boolean,
    coins: Int,
    timer: Int,
    myStrokes: List<DrawStroke>,
    myCurrentStroke: DrawStroke?,
    partnerStrokes: List<DrawStroke>,
    partnerCurrentStroke: DrawStroke?,
    guessText: String,
    correctWordToShow: String,
    selectedColor: Long,
    strokeWidth: Float,
    onGuessTextChange: (String) -> Unit,
    onGuessSubmit: () -> Unit,
    onWordChosen: (String) -> Unit,
    onStrokeEnd: (DrawStroke) -> Unit,
    onStrokePartial: (DrawStroke) -> Unit,
    onClearCanvas: () -> Unit,
    onColorChange: (Long) -> Unit,
    onStrokeWidthChange: (Float) -> Unit
) {
    val timerColor = if (timer <= 30) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Kopfzeile: Münzen + Timer ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🪙 $coins",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${timer}s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = timerColor
            )
        }

        // ── Zeichenfläche ──────────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val canvasW = constraints.maxWidth.toFloat()
            val canvasH = constraints.maxHeight.toFloat()

            fun denorm(pts: List<Float>): List<Offset> {
                val result = mutableListOf<Offset>()
                var i = 0
                while (i + 1 < pts.size) {
                    result += Offset(pts[i] * canvasW, pts[i + 1] * canvasH)
                    i += 2
                }
                return result
            }

            fun drawStrokeOnScope(scope: androidx.compose.ui.graphics.drawscope.DrawScope, stroke: DrawStroke) {
                val pts = denorm(stroke.points)
                if (pts.size < 2) return
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (k in 1 until pts.size) lineTo(pts[k].x, pts[k].y)
                }
                scope.drawPath(
                    path,
                    color = Color(stroke.colorArgb),
                    style = Stroke(
                        width = stroke.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            val displayStrokes = if (isDrawer) myStrokes else partnerStrokes
            val displayCurrent = if (isDrawer) myCurrentStroke else partnerCurrentStroke

            if (isDrawer) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            sketchLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(sketchLayer)
                        }
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .pointerInput(canvasW, canvasH, selectedColor, strokeWidth) {
                            awaitEachGesture {
                                val pts = mutableListOf<Offset>()
                                var started = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!started && change.pressed) {
                                        started = true
                                        pts += change.position
                                    } else if (started && change.pressed) {
                                        pts += change.position
                                        val norm = mutableListOf<Float>()
                                        for (p in pts) { norm.add(p.x / canvasW); norm.add(p.y / canvasH) }
                                        onStrokePartial(DrawStroke(norm.toList(), selectedColor, strokeWidth))
                                    }
                                    change.consume()
                                    if (started && !change.pressed) {
                                        val norm = mutableListOf<Float>()
                                        for (p in pts) { norm.add(p.x / canvasW); norm.add(p.y / canvasH) }
                                        if (norm.size >= 4) {
                                            onStrokeEnd(DrawStroke(norm.toList(), selectedColor, strokeWidth))
                                        }
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    for (s in displayStrokes) drawStrokeOnScope(this, s)
                    displayCurrent?.let { drawStrokeOnScope(this, it) }
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    for (s in displayStrokes) drawStrokeOnScope(this, s)
                    displayCurrent?.let { drawStrokeOnScope(this, it) }
                }
            }
        }

        // ── Unterer Bereich ────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isDrawer) {
                if (!wordChosen) {
                    Text(
                        stringResource(R.string.games_choose_word),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (word in wordChoices) {
                            Button(
                                onClick = { onWordChosen(word) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(word, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.games_drawing_word, currentWord),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        for (c in ACTIVITY_COLORS) {
                            val isSelected = c == selectedColor
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 36.dp else 30.dp)
                                    .background(Color(c), CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { onColorChange(c) }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val strokeThin = stringResource(R.string.games_stroke_thin)
                        val strokeMedium = stringResource(R.string.games_stroke_medium)
                        val strokeThick = stringResource(R.string.games_stroke_thick)
                        listOf(strokeThin to 5f, strokeMedium to 12f, strokeThick to 22f).forEach { (label, w) ->
                            OutlinedButton(
                                onClick = { onStrokeWidthChange(w) },
                                modifier = Modifier.weight(1f),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (strokeWidth == w) 2.dp else 1.dp,
                                    color = if (strokeWidth == w) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                )
                            ) { Text(label) }
                        }
                        IconButton(onClick = onClearCanvas) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.general_delete),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                if (correctWordToShow.isNotBlank()) {
                    Text(
                        stringResource(R.string.games_correct_word, correctWordToShow),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF43A047),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!wordChosen) {
                    Text(
                        stringResource(R.string.games_partner_choosing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = guessText,
                            onValueChange = onGuessTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.games_tip_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onGuessSubmit() })
                        )
                        Button(
                            onClick = onGuessSubmit,
                            enabled = guessText.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.general_send))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Activity – Ergebnis-Screen
// ─────────────────────────────────────────────────────────────────

@Composable
fun ActivityResultsScreen(
    coins: Int,
    onBackToHub: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.games_activity_ended),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ResultRow(stringResource(R.string.games_coins_earned), "🪙 $coins")
                }
            }
            Button(onClick = onBackToHub) { Text(stringResource(R.string.games_back_to_hub)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Activity – Cover-Kachel (Spielauswahl)
// ─────────────────────────────────────────────────────────────────

@Composable
private fun ActivityCoverTile(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRect(Color(0xFFFFF9F0))
                val path1 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.12f, h * 0.55f)
                    cubicTo(w * 0.22f, h * 0.35f, w * 0.32f, h * 0.65f, w * 0.44f, h * 0.42f)
                }
                drawPath(path1, Color(0xFF1E88E5), style = Stroke(width = h * 0.045f, cap = StrokeCap.Round))
                val path2 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.50f, h * 0.30f)
                    cubicTo(w * 0.58f, h * 0.55f, w * 0.68f, h * 0.25f, w * 0.78f, h * 0.50f)
                }
                drawPath(path2, Color(0xFFE53935), style = Stroke(width = h * 0.038f, cap = StrokeCap.Round))
                val path3 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.15f, h * 0.75f); lineTo(w * 0.38f, h * 0.75f)
                }
                drawPath(path3, Color(0xFF43A047), style = Stroke(width = h * 0.042f, cap = StrokeCap.Round))
                drawCircle(Color(0xFFFFD600), radius = h * 0.16f, center = Offset(w * 0.82f, h * 0.32f))
                drawCircle(Color(0xFFFFF9F0), radius = h * 0.13f, center = Offset(w * 0.82f, h * 0.32f))
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000))
                    ))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.games_sketch_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text(stringResource(R.string.games_sketch_subtitle), style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.games_play_cd),
                    tint = Color.White, modifier = Modifier.padding(10.dp).size(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Tic Tac Toe – Cover-Kachel
// ─────────────────────────────────────────────────────────────────
@Composable
private fun TicTacToeCoverTile(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF1A237E), Color(0xFF283593))
                    )
                )
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Tic Tac Toe", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("2 Spieler • Taktik • 25 Münzen", style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f))
                }
                // Mini-Vorschau des 3×3 Feldes
                Canvas(modifier = Modifier.size(72.dp)) {
                    val cell = size.width / 3f
                    val stroke = Stroke(width = 2.dp.toPx())
                    // Gitter
                    for (i in 1..2) {
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(i * cell, 0f), Offset(i * cell, size.height), strokeWidth = 2.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.6f), Offset(0f, i * cell), Offset(size.width, i * cell), strokeWidth = 2.dp.toPx())
                    }
                    // X bei (0,0)
                    val pad = cell * 0.2f
                    drawLine(Color(0xFFEF5350), Offset(pad, pad), Offset(cell - pad, cell - pad), strokeWidth = 3.dp.toPx())
                    drawLine(Color(0xFFEF5350), Offset(cell - pad, pad), Offset(pad, cell - pad), strokeWidth = 3.dp.toPx())
                    // O bei (1,1)
                    drawCircle(Color(0xFF42A5F5), radius = cell * 0.3f,
                        center = Offset(cell * 1.5f, cell * 1.5f), style = stroke)
                    // X bei (2,2)
                    val ox2 = cell * 2 + pad; val oy2 = cell * 2 + pad
                    drawLine(Color(0xFFEF5350), Offset(ox2, oy2), Offset(ox2 + cell - 2*pad, oy2 + cell - 2*pad), strokeWidth = 3.dp.toPx())
                    drawLine(Color(0xFFEF5350), Offset(ox2 + cell - 2*pad, oy2), Offset(ox2, oy2 + cell - 2*pad), strokeWidth = 3.dp.toPx())
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = CircleShape,
                color = Color(0xFF1565C0).copy(alpha = 0.92f),
                tonalElevation = 4.dp
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null,
                    tint = Color.White, modifier = Modifier.padding(10.dp).size(24.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Neon Tilt 'n' Drop – Cover-Kachel
// ─────────────────────────────────────────────────────────────────
@Composable
private fun JumpOrDieCoverTile(onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(120.dp),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF1A0A3A), Color(0xFF0D1B4B), Color(0xFF112244))
                    )
                )
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "JUMP or Die",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "Solo • Springe so hoch wie möglich",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1A3A50)
                    ) {
                        Text(
                            "Solo",
                            fontSize = 10.sp,
                            color = Color(0xFF80DEEA),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                // Mini-Vorschau: Plattformen + Figur
                Canvas(modifier = Modifier.size(72.dp)) {
                    val w = size.width; val h = size.height
                    drawRect(Color(0xFF0D0820))
                    // Plattform Gras
                    drawRoundRect(Color(0xFF4CAF50), topLeft = Offset(w * 0.05f, h * 0.72f),
                        size = Size(w * 0.55f, 7f), cornerRadius = CornerRadius(3f))
                    // Plattform Stein
                    drawRoundRect(Color(0xFF78909C), topLeft = Offset(w * 0.38f, h * 0.48f),
                        size = Size(w * 0.55f, 7f), cornerRadius = CornerRadius(3f))
                    // Plattform Metall
                    drawRoundRect(Color(0xFFB0BEC5), topLeft = Offset(w * 0.08f, h * 0.25f),
                        size = Size(w * 0.50f, 7f), cornerRadius = CornerRadius(3f))
                    // Figur (vereinfachter Doodlestein)
                    val px = w * 0.22f; val py = h * 0.32f
                    drawRoundRect(Color(0xFF9E9E9E), topLeft = Offset(px, py + 5f),
                        size = Size(12f, 14f), cornerRadius = CornerRadius(4f))
                    drawRoundRect(Color(0xFFAAAAAA), topLeft = Offset(px + 1f, py),
                        size = Size(10f, 9f), cornerRadius = CornerRadius(5f))
                    drawCircle(Color(0xFF212121), radius = 1.5f, center = Offset(px + 3.5f, py + 3.5f))
                    drawCircle(Color(0xFF212121), radius = 1.5f, center = Offset(px + 7.5f, py + 3.5f))
                    // Trajektorie-Bogen
                    drawArc(Color(0xFFFFD600).copy(alpha = 0.5f), startAngle = -150f, sweepAngle = 120f,
                        useCenter = false, topLeft = Offset(px - 8f, py - 14f),
                        size = Size(24f, 22f), style = Stroke(1.5f))
                }
            }
        }
    }
}

@Composable
private fun PinballCoverTile(onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(120.dp),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF0A1530), Color(0xFF0D2147), Color(0xFF112A55))
                    )
                )
            )
            Row(
                modifier            = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Flipper",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                    Text(
                        "Solo • Lethe: In Vergessen",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1A3A50)
                    ) {
                        Text(
                            "Solo",
                            fontSize = 10.sp,
                            color    = Color(0xFF80DEEA),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                // Mini-Vorschau: Kugel + zwei Flipper
                Canvas(modifier = Modifier.size(72.dp)) {
                    val w = size.width; val h = size.height
                    drawRoundRect(Color(0xFF081226), size = Size(w, h), cornerRadius = CornerRadius(8f))
                    // Bumper-Kreise
                    drawCircle(Color(0xFFFFD600).copy(alpha = 0.85f), radius = w * 0.085f, center = Offset(w * 0.32f, h * 0.30f))
                    drawCircle(Color(0xFFFFD600).copy(alpha = 0.85f), radius = w * 0.085f, center = Offset(w * 0.66f, h * 0.30f))
                    // Kugel
                    drawCircle(Color(0xFFE0E0E0), radius = w * 0.075f, center = Offset(w * 0.50f, h * 0.52f))
                    // Flipper links
                    withTransform({
                        translate(w * 0.30f, h * 0.78f); rotate(-22f)
                    }) {
                        drawRoundRect(Color(0xFFFFC107), topLeft = Offset(0f, -4f),
                            size = Size(w * 0.26f, 8f), cornerRadius = CornerRadius(4f))
                    }
                    // Flipper rechts
                    withTransform({
                        translate(w * 0.70f, h * 0.78f); rotate(22f)
                    }) {
                        drawRoundRect(Color(0xFFFFC107), topLeft = Offset(-w * 0.26f, -4f),
                            size = Size(w * 0.26f, 8f), cornerRadius = CornerRadius(4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TiltNDropCoverTile(onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(120.dp),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF080818), Color(0xFF001220))
                    )
                )
            )
            Row(
                modifier            = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Neon Tilt 'n' Drop",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                    Text(
                        "2 Spieler • Gyroskop • Koop",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1A3A50)
                    ) {
                        Text(
                            "Phase 1",
                            fontSize = 10.sp,
                            color    = Color(0xFF80DEEA),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                // Mini-Vorschau: Kugel + Plattform + Glas
                Canvas(modifier = Modifier.size(72.dp)) {
                    val w = size.width; val h = size.height
                    drawRect(Color(0xFF001220))
                    // Obere Plattform (leicht geneigt)
                    withTransform({
                        translate(w * 0.5f, h * 0.35f)
                        rotate(-12f)
                    }) {
                        drawRoundRect(
                            Color(0xFFBBBBBB),
                            topLeft      = Offset(-w * 0.35f, -4f),
                            size         = Size(w * 0.70f, 8f),
                            cornerRadius = CornerRadius(4f)
                        )
                    }
                    // Kugel
                    drawCircle(Color(0xFFFFFFFF), radius = w * 0.09f, center = Offset(w * 0.38f, h * 0.18f))
                    // Glas (unten, U-Form)
                    val gx = w * 0.52f; val gy = h * 0.80f
                    val gw = w * 0.22f; val gh = h * 0.14f; val wt = 3f
                    drawRect(Color(0xFFFFFFFF), topLeft = Offset(gx - gw - wt, gy), size = Size(wt, gh))
                    drawRect(Color(0xFFFFFFFF), topLeft = Offset(gx + gw,       gy), size = Size(wt, gh))
                    drawRect(Color(0xFFFFFFFF), topLeft = Offset(gx - gw - wt, gy + gh), size = Size(gw * 2f + wt * 2f, wt))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Tic Tac Toe – Gewinner-Erkennung
// ─────────────────────────────────────────────────────────────────
private fun checkTttWinner(board: List<TttCell>): Char? {
    val lines = listOf(
        listOf(0 to 0, 0 to 1, 0 to 2), listOf(1 to 0, 1 to 1, 1 to 2), listOf(2 to 0, 2 to 1, 2 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0), listOf(0 to 1, 1 to 1, 2 to 1), listOf(0 to 2, 1 to 2, 2 to 2),
        listOf(0 to 0, 1 to 1, 2 to 2), listOf(0 to 2, 1 to 1, 2 to 0)
    )
    for (line in lines) {
        val (r0, c0) = line[0]; val (r1, c1) = line[1]; val (r2, c2) = line[2]
        val p0 = board.find { it.row == r0 && it.col == c0 }?.player ?: continue
        val p1 = board.find { it.row == r1 && it.col == c1 }?.player ?: continue
        val p2 = board.find { it.row == r2 && it.col == c2 }?.player ?: continue
        if (p0 == p1 && p1 == p2) return p0
    }
    return null
}

// ─────────────────────────────────────────────────────────────────
// Tic Tac Toe – Spielfeld
// ─────────────────────────────────────────────────────────────────
@Composable
private fun TicTacToeScreen(
    mySymbol: Char,
    board: List<TttCell>,
    isMyTurn: Boolean,
    partnerName: String,
    onMove: (row: Int, col: Int) -> Unit,
    onBackToHub: () -> Unit
) {
    val myPieces = board.filter { it.player == mySymbol }.sortedBy { it.placedAt }
    val oldestMyPiece = if (myPieces.size >= 3 && isMyTurn) myPieces.firstOrNull() else null

    val xColor  = Color(0xFFEF5350)
    val oColor  = Color(0xFF42A5F5)
    val fadedX  = Color(0x55EF5350)
    val fadedO  = Color(0x5542A5F5)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBackToHub) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text("Tic Tac Toe", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
        }

        // Status-Banner
        val statusText = when {
            isMyTurn -> "Du bist dran (${mySymbol})"
            else     -> "$partnerName ist dran"
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMyTurn) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(statusText,
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold)
        }

        if (oldestMyPiece != null) {
            Text("Dein ältester Stein verschwindet beim nächsten Zug",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(8.dp))

        // Spielfeld 3×3
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            // Gitterlinien
            val lineColor = MaterialTheme.colorScheme.outline
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cw = size.width / 3f
                val ch = size.height / 3f
                val lw = 2.dp.toPx()
                for (i in 1..2) {
                    drawLine(lineColor, Offset(i * cw, 0f), Offset(i * cw, size.height), lw)
                    drawLine(lineColor, Offset(0f, i * ch), Offset(size.width, i * ch), lw)
                }
            }
            // Klickbare Zellen + Symbole
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0..2) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0..2) {
                            val cellData = board.find { it.row == row && it.col == col }
                            val isOldest = oldestMyPiece?.let { it.row == row && it.col == col } == true
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(enabled = isMyTurn && cellData == null) {
                                        onMove(row, col)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (cellData != null) {
                                    val color = when {
                                        isOldest && cellData.player == 'X' -> fadedX
                                        isOldest && cellData.player == 'O' -> fadedO
                                        cellData.player == 'X' -> xColor
                                        else -> oColor
                                    }
                                    if (cellData.player == 'X') {
                                        Canvas(modifier = Modifier.fillMaxSize(0.55f)) {
                                            val sw = 6.dp.toPx()
                                            drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), sw, StrokeCap.Round)
                                            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
                                        }
                                    } else {
                                        Canvas(modifier = Modifier.fillMaxSize(0.55f)) {
                                            drawCircle(color, radius = size.width / 2f,
                                                style = Stroke(width = 6.dp.toPx()))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Legende
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(20.dp)) {
                    if (mySymbol == 'X') {
                        val s = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(xColor, Offset(0f,0f), Offset(size.width,size.height), s.width, s.cap)
                        drawLine(xColor, Offset(size.width,0f), Offset(0f,size.height), s.width, s.cap)
                    } else {
                        drawCircle(oColor, radius = size.width / 2f, style = Stroke(width = 3.dp.toPx()))
                    }
                }
                Text("Du ($mySymbol)", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val theirSymbol = if (mySymbol == 'X') 'O' else 'X'
                Canvas(Modifier.size(20.dp)) {
                    if (theirSymbol == 'X') {
                        val s = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(xColor, Offset(0f,0f), Offset(size.width,size.height), s.width, s.cap)
                        drawLine(xColor, Offset(size.width,0f), Offset(0f,size.height), s.width, s.cap)
                    } else {
                        drawCircle(oColor, radius = size.width / 2f, style = Stroke(width = 3.dp.toPx()))
                    }
                }
                Text("$partnerName ($theirSymbol)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Tic Tac Toe – Ergebnis-Screen
// ─────────────────────────────────────────────────────────────────
@Composable
private fun TicTacToeResultsScreen(
    won: Boolean,
    isDraw: Boolean,
    partnerName: String,
    countdown: Int = 10,
    onPlayAgain: () -> Unit,
    onBackToHub: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            val (emoji, title, sub) = when {
                isDraw -> Triple("🤝", "Unentschieden!", "Kein Gewinner diesmal.")
                won    -> Triple("🏆", "Du hast gewonnen!", "+25 Münzen gutgeschrieben")
                else   -> Triple("😞", "$partnerName hat gewonnen", "Besser beim nächsten Mal!")
            }
            Text(emoji, style = MaterialTheme.typography.displayMedium)
            Text(title, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (won && !isDraw) Color(0xFFFFD600) else MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            // Countdown-Anzeige
            Text(
                text = "Nochmal spielen? ${countdown}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBackToHub) { Text("Zurück") }
                Button(onClick = onPlayAgain) {
                    Text("Nochmal (${countdown}s)")
                }
            }
        }
    }
}
