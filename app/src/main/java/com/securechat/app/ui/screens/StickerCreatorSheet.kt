package com.securechat.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToInt

// ─── Datenklassen ─────────────────────────────────────────────────────────────

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

data class StickerTextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var offset: Offset,   // CENTER-Position auf der Zeichenfläche
    val color: Color,
    val sizeSp: Float,
    val fontIndex: Int = 0,   // 0 Normal | 1 Klassisch | 2 Code | 3 Handschrift | 4 Graffiti
    val rotation: Float = 0f, // Grad
    val scale: Float = 1f
)

data class StickerEmojiOverlay(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    var offset: Offset,   // CENTER-Position
    val sizeSp: Float,
    val rotation: Float = 0f,
    val scale: Float = 1f
)

/** Bild-Sticker-Overlay: platziert ein Nutzersticker-Bild an einer Position. */
data class StickerImageOverlay(
    val id: String = UUID.randomUUID().toString(),
    val url: String,           // relativer Pfad, z. B. /uploads/stickers/{uid}/{file}
    var offset: Offset,        // CENTER-Position auf der Zeichenfläche
    val sizeDp: Float = 80f,   // Basisgröße in dp
    val rotation: Float = 0f,
    val scale: Float = 1f
)

enum class StickerEditorTool { NONE, BRUSH, TEXT, EMOJI }

enum class OverlayDragMode { NONE, MOVE, ROTATE, SCALE }

private enum class CreatorPhase { SOURCE_PICK, RANGE_SELECT, CROP, EDITOR, PROCESSING, MULTI_IMAGE_GIF }

/**
 * Zuschneide-/Anzeigeformate für Sticker. Der Zuschneide-Rahmen hat ein festes Seitenverhältnis
 * (ratioW:ratioH); der Output wird auf outW×outH skaliert (gleiches Verhältnis → kein Quetschen).
 */
private enum class StickerFormat(val label: String, val ratioW: Float, val ratioH: Float, val outW: Int, val outH: Int) {
    STANDARD("Standard", 4f, 3f, 320, 240),
    STANDARD_ROT("Hochkant", 3f, 4f, 240, 320),
    PORTRAIT_43("4:3 Portrait", 3f, 4f, 240, 320),
    LANDSCAPE_43("4:3 Quer", 4f, 3f, 320, 240);

    val aspect: Float get() = ratioW / ratioH
}

// ─── Schriften ─────────────────────────────────────────────────────────────────

val FONT_NAMES = listOf("Normal", "Klassisch", "Code", "Handschrift", "Graffiti")

val FONT_FAMILIES = listOf(
    FontFamily.Default,
    FontFamily.Serif,
    FontFamily.Monospace,
    FontFamily.Cursive,
    FontFamily.SansSerif   // Graffiti: SansSerif Bold Italic (via fontWeight/fontStyle im Dialog)
)

val FONT_WEIGHTS = listOf(
    FontWeight.Normal, FontWeight.Normal, FontWeight.Normal, FontWeight.Normal, FontWeight.ExtraBold
)
val FONT_STYLES = listOf(
    FontStyle.Normal, FontStyle.Normal, FontStyle.Normal, FontStyle.Italic, FontStyle.Italic
)

/** Gibt den Android-Typeface für den gewählten Schrift-Index zurück. */
fun typefaceForIndex(idx: Int): Typeface = when (idx) {
    1 -> Typeface.SERIF
    2 -> Typeface.MONOSPACE
    3 -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    4 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    else -> Typeface.DEFAULT
}

// ─── Overlay-Geometrie-Hilfsfunktionen ────────────────────────────────────────

/** Rotiert Punkt (x, y) um den Winkel `angleDeg` Grad. */
fun rotateVec(x: Float, y: Float, angleDeg: Float): Offset {
    val rad = Math.toRadians(angleDeg.toDouble())
    val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
    return Offset(x * c - y * s, x * s + y * c)
}

data class OverlayInfo(
    val id: String,
    val cx: Float, val cy: Float,
    val halfW: Float, val halfH: Float,
    val rotation: Float, val scale: Float
)

fun textOverlayInfo(t: StickerTextOverlay, density: Float): OverlayInfo {
    val paint = Paint().apply { textSize = t.sizeSp * density; typeface = typefaceForIndex(t.fontIndex) }
    val tw = paint.measureText(t.text) / 2f + 10 * density
    val th = (paint.descent() - paint.ascent()) / 2f + 6 * density
    return OverlayInfo(t.id, t.offset.x, t.offset.y, tw, th, t.rotation, t.scale)
}

fun emojiOverlayInfo(e: StickerEmojiOverlay, density: Float): OverlayInfo {
    val paint = Paint().apply { textSize = e.sizeSp * density }
    val tw = paint.measureText(e.emoji) / 2f + 10 * density
    val th = (paint.descent() - paint.ascent()) / 2f + 6 * density
    return OverlayInfo(e.id, e.offset.x, e.offset.y, tw, th, e.rotation, e.scale)
}

/** Gibt die 4 Eckpunkte zurück: [TL, TR, BL, BR]. BR = Rotations-Handle. */
fun handlePositions(info: OverlayInfo): List<Offset> {
    val hw = info.halfW * info.scale; val hh = info.halfH * info.scale
    val c = Offset(info.cx, info.cy)
    return listOf(
        c + rotateVec(-hw, -hh, info.rotation),
        c + rotateVec( hw, -hh, info.rotation),
        c + rotateVec(-hw,  hh, info.rotation),
        c + rotateVec( hw,  hh, info.rotation)
    )
}

fun isInsideOverlay(pos: Offset, info: OverlayInfo): Boolean {
    val local = rotateVec(pos.x - info.cx, pos.y - info.cy, -info.rotation)
    val hw = info.halfW * info.scale + 24f; val hh = info.halfH * info.scale + 24f
    return abs(local.x) < hw && abs(local.y) < hh
}

/**
 * Checks if [pos] lies within [edgePx] of any edge connecting adjacent handles (TL↔TR, TL↔BL,
 * TR↔BR, BL↔BR). If so and the nearest endpoint is within [cornerProxPx], returns its index.
 * Returns -1 if no edge is hit near a corner.
 * Handles order: [TL=0, TR=1, BL=2, BR=3].
 */
fun snapHandleOnEdge(pos: Offset, handles: List<Offset>, edgePx: Float, cornerProxPx: Float): Int {
    val edges = listOf(0 to 1, 0 to 2, 1 to 3, 2 to 3)
    for ((ai, bi) in edges) {
        val a = handles[ai]; val b = handles[bi]
        val abx = b.x - a.x; val aby = b.y - a.y
        val abLen2 = abx * abx + aby * aby
        if (abLen2 < 1f) continue
        val t = ((pos.x - a.x) * abx + (pos.y - a.y) * aby) / abLen2
        val tc = t.coerceIn(0f, 1f)
        val px = a.x + abx * tc; val py = a.y + aby * tc
        val distToSeg = kotlin.math.sqrt((pos.x - px) * (pos.x - px) + (pos.y - py) * (pos.y - py))
        if (distToSeg < edgePx) {
            val dA = (pos - a).getDistance(); val dB = (pos - b).getDistance()
            if (dA < cornerProxPx && dA <= dB) return ai
            if (dB < cornerProxPx && dB < dA) return bi
        }
    }
    return -1
}

fun findOverlayAtPos(
    pos: Offset,
    textOverlays: List<StickerTextOverlay>,
    emojiOverlays: List<StickerEmojiOverlay>,
    density: Float
): String? {
    for (e in emojiOverlays.reversed()) {
        if (isInsideOverlay(pos, emojiOverlayInfo(e, density))) return e.id
    }
    for (t in textOverlays.reversed()) {
        if (isInsideOverlay(pos, textOverlayInfo(t, density))) return t.id
    }
    return null
}

fun getOverlayInfo(
    id: String,
    textOverlays: List<StickerTextOverlay>,
    emojiOverlays: List<StickerEmojiOverlay>,
    density: Float
): OverlayInfo? {
    textOverlays.find { it.id == id }?.let { return textOverlayInfo(it, density) }
    emojiOverlays.find { it.id == id }?.let { return emojiOverlayInfo(it, density) }
    return null
}

// ─── Haupt-Composable ─────────────────────────────────────────────────────────

@Composable
fun StickerCreatorSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val densityFloat = density.density
    val scope = rememberCoroutineScope()

    // ── Phasen-State ─────────────────────────────────────────────────────────
    var phase by remember { mutableStateOf(CreatorPhase.SOURCE_PICK) }

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceIsVideo by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var rangeStartMs by remember { mutableFloatStateOf(0f) }
    var rangeDurationMs by remember { mutableFloatStateOf(3000f) }

    // ── Editor-State ─────────────────────────────────────────────────────────
    var drawPaths by remember { mutableStateOf<List<DrawPath>>(emptyList()) }
    var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var textOverlays by remember { mutableStateOf<List<StickerTextOverlay>>(emptyList()) }
    var emojiOverlays by remember { mutableStateOf<List<StickerEmojiOverlay>>(emptyList()) }
    var activeTool by remember { mutableStateOf(StickerEditorTool.NONE) }
    var brushColor by remember { mutableStateOf(Color.Red) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Overlay-Auswahl & Drag-State
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var overlayDragMode by remember { mutableStateOf(OverlayDragMode.NONE) }
    var dragCenterX by remember { mutableFloatStateOf(0f) }
    var dragCenterY by remember { mutableFloatStateOf(0f) }
    var dragStartAngle by remember { mutableFloatStateOf(0f) }
    var dragStartRotation by remember { mutableFloatStateOf(0f) }
    var dragStartDist by remember { mutableFloatStateOf(1f) }
    var dragStartScale by remember { mutableFloatStateOf(1f) }

    // Text-Eingabe
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf(TextFieldValue("")) }
    var textDialogColor by remember { mutableStateOf(Color.White) }
    var textDialogFontIndex by remember { mutableIntStateOf(0) }

    // Emoji-Picker
    var showStickerEmojiOverlayPicker by remember { mutableStateOf(false) }

    // Zuschneiden
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }

    // Fester-Format-Zuschnitt: gewähltes Format + Bild-Transform (Zoom/Pan) im Rahmen
    var stickerFormat by remember { mutableStateOf(StickerFormat.STANDARD) }
    var cropImgScale by remember { mutableFloatStateOf(1f) }
    var cropImgOffsetX by remember { mutableFloatStateOf(0f) }
    var cropImgOffsetY by remember { mutableFloatStateOf(0f) }

    var isProcessing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Crop-Vorschau (zugeschnittenes Bild für Editor-Anzeige)
    var croppedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Hintergrund-Entfernen
    var bgRemoved by remember { mutableStateOf(false) }
    var bgRemovedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRemovingBg by remember { mutableStateOf(false) }

    // ── Multi-Image-GIF State ─────────────────────────────────────────────────
    var multiImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var gifFrameDelayMs by remember { mutableIntStateOf(500) }

    // ── Launcher ─────────────────────────────────────────────────────────────
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            sourceUri = uri; sourceIsVideo = false
            sourceBitmap = loadBitmapFromUri(context, uri, 480)
            cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
            cropImgScale = 1f; cropImgOffsetX = 0f; cropImgOffsetY = 0f
            phase = CreatorPhase.CROP
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            sourceUri = uri; sourceIsVideo = true
            scope.launch(Dispatchers.IO) {
                val dur = getVideoDurationMs(context, uri)
                withContext(Dispatchers.Main) {
                    videoDurationMs = dur
                    rangeDurationMs = minOf(3000f, dur.toFloat())
                    rangeStartMs = 0f
                    sourceBitmap = getVideoFrameBitmap(context, uri, 0L, 480)
                    phase = CreatorPhase.RANGE_SELECT
                }
            }
        }
    }

    // GIF wird 1:1 übernommen: direkt als Sticker hochladen (kein Zuschneiden/Editor).
    val gifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isProcessing = true
            phase = CreatorPhase.PROCESSING
            scope.launch(Dispatchers.IO) {
                val tmpGif = File(context.cacheDir, "sticker_gif_${UUID.randomUUID().toString().take(8)}.gif")
                val ok = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tmpGif).use { out -> input.copyTo(out) }
                    }
                    tmpGif.exists() && tmpGif.length() > 0L
                } catch (e: Exception) { false }
                if (!ok) {
                    cleanup(tmpGif)
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        errorMsg = "GIF konnte nicht gelesen werden."
                        phase = CreatorPhase.SOURCE_PICK
                    }
                    return@launch
                }
                val uploadSuccess = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                    viewModel.uploadSticker(tmpGif, { success -> if (cont.isActive) cont.resume(success) }, "image/gif")
                }
                cleanup(tmpGif)
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    if (uploadSuccess) onDismiss()
                    else { errorMsg = "Upload fehlgeschlagen."; phase = CreatorPhase.SOURCE_PICK }
                }
            }
        }
    }

    // Mehrere Bilder auswählen → animiertes GIF
    val multiImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            multiImageUris = uris
            gifFrameDelayMs = 500
            phase = CreatorPhase.MULTI_IMAGE_GIF
        }
    }

    // ── Handle-Dimensionen ───────────────────────────────────────────────────
    val handleRadius = with(density) { 11.dp.toPx() }
    val handleTouchRadius = with(density) { 32.dp.toPx() }

    // ── Dialog-Wrapper ────────────────────────────────────────────────────────
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

            when (phase) {

                // ── Phase 1: Quellauswahl ──────────────────────────────────
                CreatorPhase.SOURCE_PICK -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Start).padding(8.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen")
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Sticker erstellen", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { imageLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Bild auswählen", fontSize = 16.sp) }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { videoLauncher.launch("video/*") },
                            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Video auswählen", fontSize = 16.sp) }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { gifLauncher.launch("image/gif") },
                            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("GIF auswählen", fontSize = 16.sp) }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { multiImageLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Bilder als GIF", fontSize = 16.sp) }
                        Spacer(Modifier.weight(1f))
                    }
                }

                // ── Phase 2: Video-Bereichsauswahl ────────────────────────
                CreatorPhase.RANGE_SELECT -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { phase = CreatorPhase.SOURCE_PICK }) {
                                Icon(Icons.Default.Close, contentDescription = "Zurück")
                            }
                            Text("Bereich auswählen (max. 5 s)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val bmp = getVideoFrameBitmap(context, sourceUri!!, rangeStartMs.toLong(), 480)
                                    withContext(Dispatchers.Main) {
                                        sourceBitmap = bmp
                                        cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
                                        cropImgScale = 1f; cropImgOffsetX = 0f; cropImgOffsetY = 0f
                                        phase = CreatorPhase.CROP
                                    }
                                }
                            }) { Text("Weiter") }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (sourceBitmap != null) {
                            AsyncImage(
                                model = sourceBitmap, contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        val maxStartSec = ((videoDurationMs - rangeDurationMs) / 1000f).coerceAtLeast(0f)
                        Text("Start: ${(rangeStartMs / 1000f).format1()}s", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = rangeStartMs,
                            onValueChange = { v ->
                                rangeStartMs = v
                                scope.launch(Dispatchers.IO) {
                                    val bmp = getVideoFrameBitmap(context, sourceUri!!, v.toLong(), 320)
                                    withContext(Dispatchers.Main) { sourceBitmap = bmp }
                                }
                            },
                            valueRange = 0f..maxOf(0f, (videoDurationMs - rangeDurationMs).toFloat()),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Dauer: ${(rangeDurationMs / 1000f).format1()}s", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = rangeDurationMs,
                            onValueChange = { v ->
                                rangeDurationMs = v
                                rangeStartMs = rangeStartMs.coerceAtMost((videoDurationMs - v).toFloat().coerceAtLeast(0f))
                            },
                            valueRange = 500f..5000f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Ende: ${((rangeStartMs + rangeDurationMs) / 1000f).format1()}s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // ── Phase 2b: Zuschneiden ─────────────────────────────────
                CreatorPhase.CROP -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { phase = if (sourceIsVideo) CreatorPhase.RANGE_SELECT else CreatorPhase.SOURCE_PICK }) {
                                Icon(Icons.Default.Close, contentDescription = "Zurück")
                            }
                            Text("Zuschneiden", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val bmp = sourceBitmap
                                if (bmp != null) {
                                    val x = (cropLeft * bmp.width).toInt().coerceAtLeast(0)
                                    val y = (cropTop * bmp.height).toInt().coerceAtLeast(0)
                                    val w = ((cropRight - cropLeft) * bmp.width).toInt().coerceAtLeast(1)
                                    val h = ((cropBottom - cropTop) * bmp.height).toInt().coerceAtLeast(1)
                                    croppedPreviewBitmap = Bitmap.createBitmap(
                                        bmp, x, y,
                                        minOf(w, bmp.width - x),
                                        minOf(h, bmp.height - y)
                                    )
                                }
                                bgRemoved = false; bgRemovedBitmap?.recycle(); bgRemovedBitmap = null
                                phase = CreatorPhase.EDITOR
                            }) { Text("Weiter") }
                        }

                        // Format-Auswahl (festes Seitenverhältnis für den Zuschneide-Rahmen)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StickerFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = stickerFormat == fmt,
                                    onClick = { stickerFormat = fmt },
                                    label = { Text(fmt.label, fontSize = 13.sp) }
                                )
                            }
                        }

                        val bmpForCrop = sourceBitmap
                        if (bmpForCrop != null) {
                            val cropImg = remember(bmpForCrop) { bmpForCrop.asImageBitmap() }
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                                val cw = constraints.maxWidth.toFloat()
                                val ch = constraints.maxHeight.toFloat()
                                val cx = cw / 2f; val cy = ch / 2f
                                val frameAspect = stickerFormat.aspect

                                // Rahmengröße: Format-Verhältnis in verfügbare Fläche einpassen (mit Rand)
                                val availW = cw * 0.9f; val availH = ch * 0.9f
                                val frameW: Float; val frameH: Float
                                if (availW / availH > frameAspect) { frameH = availH; frameW = availH * frameAspect }
                                else { frameW = availW; frameH = availW / frameAspect }
                                val frameLeft = cx - frameW / 2f; val frameTop = cy - frameH / 2f

                                // Basis-Bildgröße (deckt Rahmen bei scale=1 vollständig ab)
                                val imgAspect = bmpForCrop.width.toFloat() / bmpForCrop.height.toFloat()
                                val baseW: Float; val baseH: Float
                                if (imgAspect > frameAspect) { baseH = frameH; baseW = frameH * imgAspect }
                                else { baseW = frameW; baseH = frameW / imgAspect }

                                // Zuschnitt-Bruchteile aus aktuellem Zoom/Pan berechnen
                                fun recomputeCrop() {
                                    val dispW = baseW * cropImgScale; val dispH = baseH * cropImgScale
                                    val localLeft = dispW / 2f - frameW / 2f - cropImgOffsetX
                                    val localTop = dispH / 2f - frameH / 2f - cropImgOffsetY
                                    cropLeft = (localLeft / dispW).coerceIn(0f, 1f)
                                    cropTop = (localTop / dispH).coerceIn(0f, 1f)
                                    cropRight = ((localLeft + frameW) / dispW).coerceIn(0f, 1f)
                                    cropBottom = ((localTop + frameH) / dispH).coerceIn(0f, 1f)
                                }

                                LaunchedEffect(stickerFormat, cw, ch, bmpForCrop) {
                                    cropImgScale = 1f; cropImgOffsetX = 0f; cropImgOffsetY = 0f
                                    recomputeCrop()
                                }

                                Canvas(
                                    modifier = Modifier.fillMaxSize()
                                        .pointerInput(stickerFormat, cw, ch, bmpForCrop) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                val newScale = (cropImgScale * zoom).coerceIn(1f, 6f)
                                                cropImgScale = newScale
                                                val dispW = baseW * newScale; val dispH = baseH * newScale
                                                val maxOffX = ((dispW - frameW) / 2f).coerceAtLeast(0f)
                                                val maxOffY = ((dispH - frameH) / 2f).coerceAtLeast(0f)
                                                cropImgOffsetX = (cropImgOffsetX + pan.x).coerceIn(-maxOffX, maxOffX)
                                                cropImgOffsetY = (cropImgOffsetY + pan.y).coerceIn(-maxOffY, maxOffY)
                                                recomputeCrop()
                                            }
                                        }
                                ) {
                                    val dispW = baseW * cropImgScale; val dispH = baseH * cropImgScale
                                    val imgLeft = cx + cropImgOffsetX - dispW / 2f
                                    val imgTop = cy + cropImgOffsetY - dispH / 2f
                                    // Bild nur innerhalb des Rahmens zeichnen
                                    clipRect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH) {
                                        drawImage(
                                            cropImg,
                                            dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                                            dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt())
                                        )
                                    }
                                    // Abdunkeln außerhalb des Rahmens
                                    val overlay = Color.Black.copy(alpha = 0.55f)
                                    val frameRight = frameLeft + frameW; val frameBottom = frameTop + frameH
                                    drawRect(overlay, size = Size(size.width, frameTop))
                                    drawRect(overlay, topLeft = Offset(0f, frameBottom), size = Size(size.width, size.height - frameBottom))
                                    drawRect(overlay, topLeft = Offset(0f, frameTop), size = Size(frameLeft, frameH))
                                    drawRect(overlay, topLeft = Offset(frameRight, frameTop), size = Size(size.width - frameRight, frameH))
                                    // Rahmen + Drittel-Raster
                                    drawRect(Color.White, topLeft = Offset(frameLeft, frameTop), size = Size(frameW, frameH), style = Stroke(width = 2.dp.toPx()))
                                    val thW = frameW / 3f; val thH = frameH / 3f
                                    for (i in 1..2) {
                                        drawLine(Color.White.copy(alpha = 0.25f), Offset(frameLeft + thW * i, frameTop), Offset(frameLeft + thW * i, frameBottom), strokeWidth = 1f)
                                        drawLine(Color.White.copy(alpha = 0.25f), Offset(frameLeft, frameTop + thH * i), Offset(frameRight, frameTop + thH * i), strokeWidth = 1f)
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Zoomen & verschieben für den Ausschnitt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { cropImgScale = 1f; cropImgOffsetX = 0f; cropImgOffsetY = 0f }) { Text("Zurücksetzen") }
                        }
                    }
                }

                // ── Phase 3: Editor ───────────────────────────────────────
                CreatorPhase.EDITOR -> {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // Toolbar oben
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { phase = CreatorPhase.CROP }) {
                                Icon(Icons.Default.Close, contentDescription = "Zurück")
                            }
                            // Hintergrund-Entfernen Button (Silhouette)
                            if (isRemovingBg) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = 4.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = {
                                        val bmp = croppedPreviewBitmap ?: sourceBitmap ?: return@IconButton
                                        if (bgRemoved) {
                                            // Toggle: Hintergrund zurücksetzen
                                            bgRemoved = false
                                            bgRemovedBitmap?.recycle()
                                            bgRemovedBitmap = null
                                        } else {
                                            isRemovingBg = true
                                            scope.launch(Dispatchers.IO) {
                                                val result = removeStickerBackground(bmp)
                                                withContext(Dispatchers.Main) {
                                                    bgRemovedBitmap = result
                                                    bgRemoved = true
                                                    isRemovingBg = false
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "Hintergrund entfernen",
                                        tint = if (bgRemoved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            // Undo
                            IconButton(onClick = {
                                when (activeTool) {
                                    StickerEditorTool.BRUSH -> if (drawPaths.isNotEmpty()) drawPaths = drawPaths.dropLast(1)
                                    StickerEditorTool.TEXT  -> if (textOverlays.isNotEmpty()) textOverlays = textOverlays.dropLast(1)
                                    StickerEditorTool.EMOJI -> if (emojiOverlays.isNotEmpty()) emojiOverlays = emojiOverlays.dropLast(1)
                                    else -> when {
                                        drawPaths.isNotEmpty()   -> drawPaths = drawPaths.dropLast(1)
                                        textOverlays.isNotEmpty() -> textOverlays = textOverlays.dropLast(1)
                                        emojiOverlays.isNotEmpty() -> emojiOverlays = emojiOverlays.dropLast(1)
                                    }
                                }
                                selectedOverlayId = null
                            }) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Rückgängig") }
                            // Speichern & Upload
                            IconButton(onClick = {
                                isProcessing = true
                                phase = CreatorPhase.PROCESSING
                                scope.launch(Dispatchers.IO) {
                                    processAndUpload(
                                        context = context, viewModel = viewModel,
                                        sourceUri = sourceUri!!, sourceIsVideo = sourceIsVideo,
                                        startMs = rangeStartMs.toLong(), durationMs = rangeDurationMs.toLong(),
                                        sourceBitmap = sourceBitmap,
                                        drawPaths = drawPaths, textOverlays = textOverlays, emojiOverlays = emojiOverlays,
                                        canvasSize = canvasSize, density = densityFloat,
                                        cropLeft = cropLeft, cropTop = cropTop, cropRight = cropRight, cropBottom = cropBottom,
                                        outW = stickerFormat.outW, outH = stickerFormat.outH,
                                        backgroundRemoved = bgRemoved,
                                        processedSourceBitmap = bgRemovedBitmap
                                    ) { success, msg ->
                                        isProcessing = false
                                        if (success) onDismiss()
                                        else { errorMsg = msg; phase = CreatorPhase.EDITOR }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Speichern", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // ── Zeichenfläche ─────────────────────────────────
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(if (bgRemoved) Color(0xFF1A1A2E) else Color.Transparent)) {
                            val displayBitmap = if (bgRemoved && bgRemovedBitmap != null) bgRemovedBitmap else (croppedPreviewBitmap ?: sourceBitmap)
                            if (displayBitmap != null) {
                                AsyncImage(model = displayBitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }

                            // Canvas 1: Pinsel-Pfade
                            Canvas(
                                modifier = Modifier.fillMaxSize()
                                    .onSizeChanged { canvasSize = it }
                                    .pointerInput(activeTool, brushColor) {
                                        if (activeTool == StickerEditorTool.BRUSH) {
                                            detectDragGestures(
                                                onDragStart = { offset -> currentPathPoints = listOf(offset) },
                                                onDrag = { change, _ -> currentPathPoints = currentPathPoints + change.position },
                                                onDragEnd = {
                                                    if (currentPathPoints.size > 1) {
                                                        drawPaths = drawPaths + DrawPath(currentPathPoints, brushColor, 8f)
                                                    }
                                                    currentPathPoints = emptyList()
                                                }
                                            )
                                        }
                                    }
                            ) {
                                drawPaths.forEach { dp ->
                                    if (dp.points.size > 1) {
                                        val path = androidx.compose.ui.graphics.Path()
                                        path.moveTo(dp.points[0].x, dp.points[0].y)
                                        dp.points.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
                                        drawPath(path, dp.color, style = Stroke(dp.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                }
                                if (currentPathPoints.size > 1) {
                                    val path = androidx.compose.ui.graphics.Path()
                                    path.moveTo(currentPathPoints[0].x, currentPathPoints[0].y)
                                    currentPathPoints.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
                                    drawPath(path, brushColor, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }

                            // Canvas 2: Text/Emoji-Overlays mit Handles
                            Canvas(
                                modifier = Modifier.fillMaxSize()
                                    // Tap: Auswahl / Text anlegen / Emoji anlegen
                                    .pointerInput(activeTool) {
                                        if (activeTool != StickerEditorTool.BRUSH) {
                                            detectTapGestures { pos ->
                                                when (activeTool) {
                                                    StickerEditorTool.TEXT -> {
                                                        pendingTextOffset = pos
                                                        showTextDialog = true
                                                        textInput = TextFieldValue("")
                                                    }
                                                    StickerEditorTool.EMOJI -> {
                                                        pendingEmojiOffset = pos
                                                        showStickerEmojiOverlayPicker = true
                                                    }
                                                    else -> {
                                                        // Overlay antippen → auswählen / abwählen
                                                        val hit = findOverlayAtPos(pos, textOverlays, emojiOverlays, densityFloat)
                                                        selectedOverlayId = if (hit == selectedOverlayId) null else hit
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // Drag: Verschieben / Rotieren / Skalieren (nicht wenn Pinsel aktiv)
                                    .pointerInput(activeTool) {
                                        if (activeTool != StickerEditorTool.BRUSH) detectDragGestures(
                                            onDragStart = { pos ->
                                                val selId = selectedOverlayId
                                                val info = if (selId != null) getOverlayInfo(selId, textOverlays, emojiOverlays, densityFloat) else null
                                                if (info != null) {
                                                    // Handle-Treffer prüfen
                                                    val handles = handlePositions(info)
                                                    val brHandle = handles[3]
                                                    val edgeSnap = snapHandleOnEdge(pos, handles, edgePx = 22f, cornerProxPx = handleTouchRadius * 1.8f)
                                                    when {
                                                        // BR → Rotieren
                                                        (pos - brHandle).getDistance() < handleTouchRadius || edgeSnap == 3 -> {
                                                            overlayDragMode = OverlayDragMode.ROTATE
                                                            dragCenterX = info.cx; dragCenterY = info.cy
                                                            dragStartAngle = atan2((pos.y - info.cy).toDouble(), (pos.x - info.cx).toDouble()).toFloat()
                                                            dragStartRotation = info.rotation
                                                        }
                                                        // TL/TR/BL → Skalieren
                                                        handles.take(3).any { (pos - it).getDistance() < handleTouchRadius } || (edgeSnap in 0..2) -> {
                                                            overlayDragMode = OverlayDragMode.SCALE
                                                            dragCenterX = info.cx; dragCenterY = info.cy
                                                            dragStartDist = (pos - Offset(info.cx, info.cy)).getDistance().coerceAtLeast(1f)
                                                            dragStartScale = info.scale
                                                        }
                                                        // Body → Verschieben
                                                        isInsideOverlay(pos, info) -> {
                                                            overlayDragMode = OverlayDragMode.MOVE
                                                        }
                                                        // Tap ins Leere → Auswahl aufheben
                                                        else -> {
                                                            selectedOverlayId = null
                                                            overlayDragMode = OverlayDragMode.NONE
                                                        }
                                                    }
                                                } else {
                                                    // Kein ausgewähltes Overlay: Body-Hit-Test
                                                    val hit = findOverlayAtPos(pos, textOverlays, emojiOverlays, densityFloat)
                                                    if (hit != null) {
                                                        selectedOverlayId = hit
                                                        overlayDragMode = OverlayDragMode.MOVE
                                                    } else {
                                                        overlayDragMode = OverlayDragMode.NONE
                                                    }
                                                }
                                            },
                                            onDrag = { change, delta ->
                                                val selId = selectedOverlayId ?: return@detectDragGestures
                                                when (overlayDragMode) {
                                                    OverlayDragMode.MOVE -> {
                                                        textOverlays = textOverlays.map { if (it.id == selId) it.copy(offset = Offset(it.offset.x + delta.x, it.offset.y + delta.y)) else it }
                                                        emojiOverlays = emojiOverlays.map { if (it.id == selId) it.copy(offset = Offset(it.offset.x + delta.x, it.offset.y + delta.y)) else it }
                                                    }
                                                    OverlayDragMode.ROTATE -> {
                                                        val curAngle = atan2((change.position.y - dragCenterY).toDouble(), (change.position.x - dragCenterX).toDouble()).toFloat()
                                                        val angleDeltaDeg = Math.toDegrees((curAngle - dragStartAngle).toDouble()).toFloat()
                                                        val newRot = dragStartRotation + angleDeltaDeg
                                                        textOverlays = textOverlays.map { if (it.id == selId) it.copy(rotation = newRot) else it }
                                                        emojiOverlays = emojiOverlays.map { if (it.id == selId) it.copy(rotation = newRot) else it }
                                                    }
                                                    OverlayDragMode.SCALE -> {
                                                        val curDist = (change.position - Offset(dragCenterX, dragCenterY)).getDistance().coerceAtLeast(1f)
                                                        val newScale = (dragStartScale * (curDist / dragStartDist)).coerceIn(0.3f, 6f)
                                                        textOverlays = textOverlays.map { if (it.id == selId) it.copy(scale = newScale) else it }
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

                                // Text-Overlays zeichnen
                                textOverlays.forEach { t ->
                                    val paint = Paint().apply {
                                        color = android.graphics.Color.argb(
                                            (t.color.alpha * 255).toInt(), (t.color.red * 255).toInt(),
                                            (t.color.green * 255).toInt(), (t.color.blue * 255).toInt()
                                        )
                                        textSize = t.sizeSp * densityFloat
                                        typeface = typefaceForIndex(t.fontIndex)
                                        isAntiAlias = true
                                        setShadowLayer(3f, 1.5f, 1.5f, android.graphics.Color.BLACK)
                                    }
                                    val bounds = android.graphics.Rect()
                                    paint.getTextBounds(t.text, 0, t.text.length, bounds)
                                    nc.save()
                                    nc.translate(t.offset.x, t.offset.y)
                                    nc.rotate(t.rotation)
                                    nc.scale(t.scale, t.scale)
                                    nc.drawText(t.text, -bounds.exactCenterX(), -bounds.exactCenterY(), paint)
                                    nc.restore()
                                }

                                // Emoji-Overlays zeichnen
                                emojiOverlays.forEach { e ->
                                    val paint = Paint().apply {
                                        textSize = e.sizeSp * densityFloat
                                        isAntiAlias = true
                                    }
                                    val tw = paint.measureText(e.emoji) / 2f
                                    val th = (-paint.ascent() + paint.descent()) / 2f - paint.descent()
                                    nc.save()
                                    nc.translate(e.offset.x, e.offset.y)
                                    nc.rotate(e.rotation)
                                    nc.scale(e.scale, e.scale)
                                    nc.drawText(e.emoji, -tw, th, paint)
                                    nc.restore()
                                }

                                // Auswahl-Rahmen & Handles für das ausgewählte Overlay
                                val selId = selectedOverlayId
                                val selInfo = if (selId != null) getOverlayInfo(selId, textOverlays, emojiOverlays, densityFloat) else null
                                if (selInfo != null) {
                                    val hw = selInfo.halfW * selInfo.scale
                                    val hh = selInfo.halfH * selInfo.scale

                                    // Gestrichelter Rahmen
                                    val borderPaint = Paint().apply {
                                        color = 0xCCFFFFFF.toInt()
                                        style = Paint.Style.STROKE
                                        strokeWidth = 2f
                                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 6f), 0f)
                                        isAntiAlias = true
                                    }
                                    nc.save()
                                    nc.translate(selInfo.cx, selInfo.cy)
                                    nc.rotate(selInfo.rotation)
                                    nc.drawRoundRect(RectF(-hw, -hh, hw, hh), 6f, 6f, borderPaint)
                                    nc.restore()

                                    // Eck-Handles
                                    val handles = handlePositions(selInfo)
                                    val scaleHandleColor = android.graphics.Color.rgb(72, 199, 142)
                                    val rotateHandleColor = android.graphics.Color.rgb(255, 87, 34)
                                    handles.forEachIndexed { i, pos ->
                                        val fillColor = if (i == 3) rotateHandleColor else scaleHandleColor
                                        nc.drawCircle(pos.x, pos.y, handleRadius + 3f, Paint().apply { color = 0x99000000.toInt(); isAntiAlias = true })
                                        nc.drawCircle(pos.x, pos.y, handleRadius, Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true })
                                        nc.drawCircle(pos.x, pos.y, handleRadius * 0.5f, Paint().apply { color = fillColor; isAntiAlias = true })
                                    }
                                }
                            }
                        }

                        // ── Werkzeug-Leiste unten ─────────────────────────
                        Column(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolButton(
                                    icon = { Icon(Icons.Default.Brush, contentDescription = "Malen") },
                                    label = "Malen", selected = activeTool == StickerEditorTool.BRUSH,
                                    onClick = { activeTool = if (activeTool == StickerEditorTool.BRUSH) StickerEditorTool.NONE else StickerEditorTool.BRUSH; selectedOverlayId = null }
                                )
                                ToolButton(
                                    icon = { Icon(Icons.Default.TextFields, contentDescription = "Text") },
                                    label = "Text", selected = activeTool == StickerEditorTool.TEXT,
                                    onClick = { activeTool = if (activeTool == StickerEditorTool.TEXT) StickerEditorTool.NONE else StickerEditorTool.TEXT; selectedOverlayId = null }
                                )
                                ToolButton(
                                    icon = { Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji") },
                                    label = "Emoji", selected = activeTool == StickerEditorTool.EMOJI,
                                    onClick = { activeTool = if (activeTool == StickerEditorTool.EMOJI) StickerEditorTool.NONE else StickerEditorTool.EMOJI; selectedOverlayId = null }
                                )
                            }

                            // Pinselfarb-Palette
                            if (activeTool == StickerEditorTool.BRUSH) {
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                    items(BRUSH_COLORS) { color ->
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(color)
                                                .then(if (color == brushColor) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                                .pointerInput(Unit) { detectTapGestures { brushColor = color } }
                                        )
                                    }
                                }
                            }

                            // Hinweis wenn TEXT oder EMOJI aktiv
                            if (activeTool == StickerEditorTool.TEXT || activeTool == StickerEditorTool.EMOJI) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (activeTool == StickerEditorTool.TEXT) "Bild antippen um Text hinzuzufügen"
                                    else "Bild antippen um Emoji hinzuzufügen",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }

                            // Hinweis wenn Overlay ausgewählt
                            if (selectedOverlayId != null && activeTool == StickerEditorTool.NONE) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Ziehen: Verschieben  •  Grüne Ecke: Skalieren  •  Orange Ecke: Drehen",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        errorMsg?.let { msg ->
                            Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                }

                // ── Phase 4: Verarbeitung ─────────────────────────────────
                CreatorPhase.PROCESSING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("GIF wird erstellt…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // ── Phase 5: Mehrere Bilder zu animiertem GIF ─────────────
                CreatorPhase.MULTI_IMAGE_GIF -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { phase = CreatorPhase.SOURCE_PICK }) {
                                Icon(Icons.Default.Close, contentDescription = "Zurück")
                            }
                            Text(
                                "Bilder als GIF (${multiImageUris.size})",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                isProcessing = true
                                phase = CreatorPhase.PROCESSING
                                scope.launch(Dispatchers.IO) {
                                    createGifFromImages(
                                        context = context,
                                        viewModel = viewModel,
                                        uris = multiImageUris,
                                        frameDelayMs = gifFrameDelayMs
                                    ) { success, errMsg ->
                                        isProcessing = false
                                        if (success) onDismiss()
                                        else {
                                            errorMsg = errMsg ?: "Fehler beim GIF-Erstellen."
                                            phase = CreatorPhase.MULTI_IMAGE_GIF
                                        }
                                    }
                                }
                            }) { Text("GIF erstellen") }
                        }

                        // Vorschau der ausgewählten Bilder
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(multiImageUris) { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Frame-Dauer Slider
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            val delaySec = gifFrameDelayMs / 1000f
                            Text(
                                "Bilddauer: ${delaySec.format1()}s  (${(1000f / gifFrameDelayMs).format1()} fps)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = gifFrameDelayMs.toFloat(),
                                onValueChange = { gifFrameDelayMs = it.toInt() },
                                valueRange = 100f..2000f,
                                steps = 18,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Schnell (0.1s) ◄──────► Langsam (2s)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        errorMsg?.let { msg ->
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Text-Eingabe-Dialog ───────────────────────────────────────────────────
    if (showTextDialog) {
        val textPaletteColors = listOf(
            Color.White, Color.Black, Color.Red, Color(0xFFFF6600), Color.Yellow,
            Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color(0xFFFF69B4)
        )
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Text hinzufügen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = textInput, onValueChange = { textInput = it },
                        singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text eingeben") }
                    )

                    // Farbauswahl
                    Text("Farbe", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(textPaletteColors) { color ->
                            Box(
                                modifier = Modifier.size(30.dp).clip(CircleShape).background(color)
                                    .then(if (color == textDialogColor) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .pointerInput(Unit) { detectTapGestures { textDialogColor = color } }
                            )
                        }
                    }

                    // Schriftauswahl
                    Text("Schrift", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FONT_NAMES.size) { i ->
                            val isSelected = i == textDialogFontIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(if (isSelected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .pointerInput(Unit) { detectTapGestures { textDialogFontIndex = i } },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Text(
                                    text = FONT_NAMES[i],
                                    fontFamily = FONT_FAMILIES[i],
                                    fontWeight = FONT_WEIGHTS[i],
                                    fontStyle = FONT_STYLES[i],
                                    fontSize = 14.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textInput.text.isNotBlank()) {
                        textOverlays = textOverlays + StickerTextOverlay(
                            text = textInput.text,
                            offset = pendingTextOffset,
                            color = textDialogColor,
                            sizeSp = 28f,
                            fontIndex = textDialogFontIndex
                        )
                        selectedOverlayId = textOverlays.last().id
                        activeTool = StickerEditorTool.NONE
                    }
                    showTextDialog = false
                }) { Text("Hinzufügen") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // ── Emoji-Overlay-Picker ──────────────────────────────────────────────────
    if (showStickerEmojiOverlayPicker) {
        AlertDialog(
            onDismissRequest = { showStickerEmojiOverlayPicker = false },
            title = { Text("Emoji wählen") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    gridItems(OVERLAY_EMOJIS) { emoji ->
                        TextButton(
                            onClick = {
                                emojiOverlays = emojiOverlays + StickerEmojiOverlay(
                                    emoji = emoji, offset = pendingEmojiOffset, sizeSp = 36f
                                )
                                selectedOverlayId = emojiOverlays.last().id
                                activeTool = StickerEditorTool.NONE
                                showStickerEmojiOverlayPicker = false
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) { Text(emoji, fontSize = 22.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showStickerEmojiOverlayPicker = false }) { Text("Abbrechen") } }
        )
    }
}

// ─── Modul-Level Zustand für Tap-Positionen ────────────────────────────────────
private var pendingTextOffset = Offset(100f, 100f)
private var pendingEmojiOffset = Offset(100f, 100f)

// ─── Werkzeug-Button ─────────────────────────────────────────────────────────

@Composable
private fun ToolButton(icon: @Composable () -> Unit, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) { icon() }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── Konstanten ───────────────────────────────────────────────────────────────

private val BRUSH_COLORS = listOf(
    Color.Red, Color(0xFFFF6600), Color.Yellow, Color.Green,
    Color.Cyan, Color.Blue, Color(0xFF8800FF), Color.White, Color.Black
)

val OVERLAY_EMOJIS = listOf(
    // Gesichter & Emotionen
    "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰",
    "😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥸","🤩","🥳",
    "😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤",
    "😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🤭","🤫",
    "🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵",
    // Herzen & Symbole
    "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖",
    "💘","💝","💟","☮️","✝️","☯️","🔥","💫","⭐","🌟","✨","💥","❄️","🌈","☀️","🌙",
    // Gesten & Hände
    "👍","👎","👌","🤌","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","👇","☝️","👋","🤚",
    "🖐️","✋","🖖","🤜","🤛","👊","✊","👏","🙌","👐","🤲","🙏","✍️","💪","🦾",
    // Tiere
    "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈",
    "🐔","🐧","🐦","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🐛","🦋","🐌","🐞",
    // Essen & Trinken
    "🍕","🍔","🍟","🌭","🍿","🧂","🥓","🥚","🍳","🧇","🥞","🧈","🍞","🥐","🥨","🧀",
    "🍎","🍊","🍋","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🥑","🍆",
    "🎂","🍰","🧁","🍩","🍪","🍫","🍬","🍭","🍮","🧁","☕","🍵","🧋","🥤","🍺","🥂",
    // Aktivitäten & Sport
    "⚽","🏀","🏈","⚾","🎾","🏐","🏉","🥏","🎯","🎱","🏓","🏸","🥊","🥋","🎽","🛹",
    "🛷","⛸️","🥅","⛳","🎣","🤿","🎿","🛷","🏋️","🤸","🏄","🚴","🤾","🏇","🏊","⛹️",
    // Objekte & Sonstiges
    "🎉","🎊","🎈","🎁","🎀","🪅","🎆","🎇","🧨","🎶","🎵","🎸","🎹","🥁","🎺","🎻",
    "🎤","🎧","📱","💻","⌨️","🖥️","🖨️","🖱️","💡","🔦","🕯️","🪔","📚","✏️","🖊️","📝",
    "💰","💎","🔑","🗝️","🔒","🔓","🔨","⚙️","🔧","🪛","🪚","⛏️","💣","🧲","🔮","🪄",
    "👑","💍","👒","🎩","🧢","⛑️","👓","🕶️","🥽","🌂","☂️","🎭","🎪","🎠","🎡","🎢"
)

// ─── Hilfsfunktionen ─────────────────────────────────────────────────────────

private fun Float.format1(): String = "%.1f".format(this)

private fun loadBitmapFromUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            val scale = maxOf(opts.outWidth, opts.outHeight) / maxDim
            opts.inJustDecodeBounds = false
            opts.inSampleSize = maxOf(1, scale)
            context.contentResolver.openInputStream(uri)?.use { s2 -> BitmapFactory.decodeStream(s2, null, opts) }
        }
    } catch (e: Exception) { null }
}

private fun getVideoDurationMs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
    } catch (e: Exception) { 5000L } finally { retriever.release() }
}

private fun getVideoFrameBitmap(context: Context, uri: Uri, timeMs: Long, maxDim: Int): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val frame = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        frame?.let { bmp ->
            val scale = maxOf(bmp.width, bmp.height).toFloat() / maxDim
            if (scale <= 1f) bmp else Bitmap.createScaledBitmap(bmp, (bmp.width / scale).toInt(), (bmp.height / scale).toInt(), true)
        }
    } catch (e: Exception) { null } finally { retriever.release() }
}

/**
 * Erstellt einen Overlay-Bitmap mit Zeichenpfaden, Texten (mit Rotation/Skalierung/Schrift)
 * und Emojis (mit Rotation/Skalierung). Wird via FFmpegKit über das GIF gelegt.
 */
fun buildOverlayBitmap(
    width: Int, height: Int,
    drawPaths: List<DrawPath>,
    textOverlays: List<StickerTextOverlay>,
    emojiOverlays: List<StickerEmojiOverlay>,
    canvasSize: IntSize,
    density: Float
): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val scaleX = if (canvasSize.width > 0) width.toFloat() / canvasSize.width else 1f
    val scaleY = if (canvasSize.height > 0) height.toFloat() / canvasSize.height else 1f
    val avgScale = (scaleX + scaleY) / 2f

    // Pinsel-Pfade
    drawPaths.forEach { dp ->
        val paint = Paint().apply {
            color = android.graphics.Color.argb(
                (dp.color.alpha * 255).toInt(), (dp.color.red * 255).toInt(),
                (dp.color.green * 255).toInt(), (dp.color.blue * 255).toInt()
            )
            strokeWidth = dp.strokeWidth * avgScale
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
        val path = AndroidPath()
        if (dp.points.isNotEmpty()) {
            path.moveTo(dp.points[0].x * scaleX, dp.points[0].y * scaleY)
            dp.points.drop(1).forEach { p -> path.lineTo(p.x * scaleX, p.y * scaleY) }
        }
        canvas.drawPath(path, paint)
    }

    // Text-Overlays (CENTER-Position, mit Rotation & Scale)
    textOverlays.forEach { t ->
        val paint = Paint().apply {
            color = android.graphics.Color.argb(
                (t.color.alpha * 255).toInt(), (t.color.red * 255).toInt(),
                (t.color.green * 255).toInt(), (t.color.blue * 255).toInt()
            )
            textSize = t.sizeSp * density * avgScale
            typeface = typefaceForIndex(t.fontIndex)
            isAntiAlias = true
            setShadowLayer(3f * avgScale, 1.5f * avgScale, 1.5f * avgScale, android.graphics.Color.BLACK)
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds(t.text, 0, t.text.length, bounds)
        canvas.save()
        canvas.translate(t.offset.x * scaleX, t.offset.y * scaleY)
        canvas.rotate(t.rotation)
        canvas.scale(t.scale, t.scale)
        canvas.drawText(t.text, -bounds.exactCenterX(), -bounds.exactCenterY(), paint)
        canvas.restore()
    }

    // Emoji-Overlays (CENTER-Position, mit Rotation & Scale)
    emojiOverlays.forEach { e ->
        val paint = Paint().apply {
            textSize = e.sizeSp * density * avgScale
            isAntiAlias = true
        }
        val tw = paint.measureText(e.emoji) / 2f
        val th = (-paint.ascent() + paint.descent()) / 2f - paint.descent()
        canvas.save()
        canvas.translate(e.offset.x * scaleX, e.offset.y * scaleY)
        canvas.rotate(e.rotation)
        canvas.scale(e.scale, e.scale)
        canvas.drawText(e.emoji, -tw, th, paint)
        canvas.restore()
    }

    return bmp
}

/** Kernfunktion: GIF/PNG erstellen via FFmpegKit + Upload. Läuft auf Dispatchers.IO. */
private suspend fun processAndUpload(
    context: Context, viewModel: MainViewModel,
    sourceUri: Uri, sourceIsVideo: Boolean,
    startMs: Long, durationMs: Long, sourceBitmap: Bitmap?,
    drawPaths: List<DrawPath>, textOverlays: List<StickerTextOverlay>,
    emojiOverlays: List<StickerEmojiOverlay>, canvasSize: IntSize, density: Float,
    cropLeft: Float = 0f, cropTop: Float = 0f, cropRight: Float = 1f, cropBottom: Float = 1f,
    outW: Int = 320, outH: Int = 240,
    backgroundRemoved: Boolean = false,
    processedSourceBitmap: Bitmap? = null,
    onDone: suspend (Boolean, String?) -> Unit
) {
    val cacheDir = context.cacheDir
    val id = UUID.randomUUID().toString().take(8)

    // ── PNG-Pfad wenn Hintergrund entfernt ──────────────────────────────────
    if (backgroundRemoved && processedSourceBitmap != null) {
        val outputPng = File(cacheDir, "sticker_$id.png")
        try {
            val w = processedSourceBitmap.width
            val h = processedSourceBitmap.height
            val finalBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(finalBmp)
            canvas.drawBitmap(processedSourceBitmap, 0f, 0f, null)
            if (drawPaths.isNotEmpty() || textOverlays.isNotEmpty() || emojiOverlays.isNotEmpty()) {
                val overlayBmp = buildOverlayBitmap(w, h, drawPaths, textOverlays, emojiOverlays, canvasSize, density)
                canvas.drawBitmap(overlayBmp, 0f, 0f, null)
                overlayBmp.recycle()
            }
            FileOutputStream(outputPng).use { out -> finalBmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            finalBmp.recycle()
        } catch (e: Exception) {
            cleanup(outputPng)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(false, "PNG-Erstellung fehlgeschlagen.")
            }
            return
        }
        val uploadSuccess = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
            viewModel.uploadSticker(outputPng, { success -> if (cont.isActive) cont.resume(success) }, "image/png")
        }
        cleanup(outputPng)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onDone(uploadSuccess, if (uploadSuccess) null else "Upload fehlgeschlagen.")
        }
        return
    }

    val outputGif = File(cacheDir, "sticker_$id.gif")
    val hasOverlays = drawPaths.isNotEmpty() || textOverlays.isNotEmpty() || emojiOverlays.isNotEmpty()

    val inputFile = File(cacheDir, "sticker_src_$id")
    try {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(inputFile).use { out -> input.copyTo(out) }
        }
    } catch (e: Exception) { onDone(false, "Quelldatei konnte nicht gelesen werden."); return }

    val gifTarget = if (hasOverlays) File(cacheDir, "sticker_base_$id.gif") else outputGif

    val step1Ok: Boolean
    if (sourceIsVideo) {
        val startSec = startMs / 1000f; val durSec = durationMs / 1000f
        val palFile = File(cacheDir, "sticker_pal_$id.png")
        val cW = cropRight - cropLeft; val cH = cropBottom - cropTop
        val cropVf = "crop=iw*$cW:ih*$cH:iw*$cropLeft:ih*$cropTop,"
        val pass1 = "-ss $startSec -t $durSec -i \"${inputFile.absolutePath}\" -vf \"${cropVf}fps=10,scale=$outW:$outH:flags=lanczos,palettegen\" -y \"${palFile.absolutePath}\""
        executeFFmpeg(pass1)
        if (!palFile.exists()) { cleanup(inputFile); onDone(false, "GIF-Palette konnte nicht erstellt werden."); return }
        val pass2 = "-ss $startSec -t $durSec -i \"${inputFile.absolutePath}\" -i \"${palFile.absolutePath}\" " +
                "-filter_complex \"${cropVf}fps=10,scale=$outW:$outH:flags=lanczos[x];[x][1:v]paletteuse\" -loop 0 -y \"${gifTarget.absolutePath}\""
        step1Ok = executeFFmpeg(pass2)
        cleanup(palFile)
    } else {
        val cW = cropRight - cropLeft; val cH = cropBottom - cropTop
        val cropVf = "crop=iw*$cW:ih*$cH:iw*$cropLeft:ih*$cropTop,"
        val imgCmd = "-i \"${inputFile.absolutePath}\" -vf \"${cropVf}scale=$outW:$outH:flags=lanczos\" -loop 0 -y \"${gifTarget.absolutePath}\""
        step1Ok = executeFFmpeg(imgCmd)
    }

    if (!step1Ok || !gifTarget.exists() || gifTarget.length() == 0L) {
        cleanup(inputFile); onDone(false, "GIF-Erstellung fehlgeschlagen."); return
    }

    if (hasOverlays) {
        val overlayBmp = buildOverlayBitmap(outW, outH, drawPaths, textOverlays, emojiOverlays, canvasSize, density)
        val overlayFile = File(cacheDir, "sticker_ovl_$id.png")
        FileOutputStream(overlayFile).use { out -> overlayBmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val ovCmd = "-i \"${gifTarget.absolutePath}\" -i \"${overlayFile.absolutePath}\" -filter_complex \"[0:v][1:v]overlay=0:0\" -loop 0 -y \"${outputGif.absolutePath}\""
        val ok2 = executeFFmpeg(ovCmd)
        cleanup(overlayFile, gifTarget)
        if (!ok2 || !outputGif.exists()) { cleanup(inputFile, outputGif); onDone(false, "Overlay konnte nicht angewendet werden."); return }
    }

    cleanup(inputFile)

    val uploadSuccess = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
        viewModel.uploadSticker(outputGif, { success -> if (cont.isActive) cont.resume(success) })
    }
    cleanup(outputGif)

    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        onDone(uploadSuccess, if (uploadSuccess) null else "Upload fehlgeschlagen.")
    }
}

/**
 * Erstellt ein animiertes GIF aus mehreren Bildern und lädt es als Sticker hoch.
 * Jedes Bild wird [frameDelayMs] ms lang angezeigt.
 * Läuft auf Dispatchers.IO.
 */
private suspend fun createGifFromImages(
    context: Context,
    viewModel: MainViewModel,
    uris: List<android.net.Uri>,
    frameDelayMs: Int,
    onDone: suspend (Boolean, String?) -> Unit
) {
    val cacheDir = context.cacheDir
    val id = UUID.randomUUID().toString().take(8)
    val frameFiles = mutableListOf<File>()
    val outputGif = File(cacheDir, "sticker_multi_$id.gif")
    val concatFile = File(cacheDir, "sticker_concat_$id.txt")
    val palFile = File(cacheDir, "sticker_mpal_$id.png")

    try {
        // Bilder in temporäre Dateien kopieren
        uris.forEachIndexed { i, uri ->
            val ext = context.contentResolver.getType(uri)?.substringAfterLast('/')?.take(4) ?: "jpg"
            val f = File(cacheDir, "sticker_mf_${id}_${i.toString().padStart(3, '0')}.$ext")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(f).use { out -> input.copyTo(out) }
                }
            } catch (_: Exception) {}
            if (f.exists() && f.length() > 0L) frameFiles.add(f)
        }

        if (frameFiles.size < 2) {
            cleanup(*frameFiles.toTypedArray(), outputGif, concatFile, palFile)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(false, "Mindestens 2 Bilder erforderlich.")
            }
            return
        }

        // FFmpeg-Concat-Datei schreiben (gibt jedem Bild die gewünschte Dauer)
        val delaySec = frameDelayMs / 1000.0
        val sb = StringBuilder("ffconcat version 1.0\n")
        frameFiles.forEach { f ->
            sb.append("file '${f.absolutePath}'\n")
            sb.append("duration $delaySec\n")
        }
        // letztes Bild nochmals ohne duration (FFmpeg-Anforderung für korrektes Looping)
        sb.append("file '${frameFiles.last().absolutePath}'\n")
        concatFile.writeText(sb.toString())

        // Pass 1: Palette generieren
        val pass1 = "-f concat -safe 0 -i \"${concatFile.absolutePath}\" " +
                "-vf \"scale=320:240:flags=lanczos,palettegen=stats_mode=diff\" -y \"${palFile.absolutePath}\""
        executeFFmpeg(pass1)
        if (!palFile.exists() || palFile.length() == 0L) {
            cleanup(*frameFiles.toTypedArray(), outputGif, concatFile, palFile)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(false, "GIF-Palette konnte nicht erstellt werden.")
            }
            return
        }

        // Pass 2: GIF erstellen mit Palette
        val pass2 = "-f concat -safe 0 -i \"${concatFile.absolutePath}\" -i \"${palFile.absolutePath}\" " +
                "-filter_complex \"scale=320:240:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer\" -loop 0 -y \"${outputGif.absolutePath}\""
        val ok = executeFFmpeg(pass2)

        cleanup(*frameFiles.toTypedArray(), concatFile, palFile)

        if (!ok || !outputGif.exists() || outputGif.length() == 0L) {
            cleanup(outputGif)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onDone(false, "GIF-Erstellung fehlgeschlagen.")
            }
            return
        }

        val uploadSuccess = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
            viewModel.uploadSticker(outputGif, { success -> if (cont.isActive) cont.resume(success) }, "image/gif")
        }
        cleanup(outputGif)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onDone(uploadSuccess, if (uploadSuccess) null else "Upload fehlgeschlagen.")
        }
    } catch (e: Exception) {
        cleanup(*frameFiles.toTypedArray(), outputGif, concatFile, palFile)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onDone(false, "Fehler: ${e.message}")
        }
    }
}

private fun executeFFmpeg(command: String): Boolean {
    val session = FFmpegKit.execute(command)
    return ReturnCode.isSuccess(session.returnCode)
}

private fun cleanup(vararg files: File) {
    files.forEach { f -> try { if (f.exists()) f.delete() } catch (_: Exception) {} }
}

/**
 * Entfernt den Hintergrund via ML Kit Selfie Segmentation.
 * Fallback: BFS-Flood-Fill falls ML Kit nicht verfügbar.
 * Liefert ein neues ARGB_8888-Bitmap mit transparentem Hintergrund.
 * Muss auf einem Hintergrund-Thread aufgerufen werden (Dispatchers.IO).
 */
fun removeStickerBackground(source: Bitmap): Bitmap {
    return try {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        val image = InputImage.fromBitmap(source, 0)
        val result = Tasks.await(segmenter.process(image))
        val mask = result.buffer
        val maskWidth = result.width
        val maskHeight = result.height

        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val scaleX = source.width.toFloat() / maskWidth
        val scaleY = source.height.toFloat() / maskHeight
        mask.rewind()
        val floatArray = FloatArray(maskWidth * maskHeight)
        mask.asFloatBuffer().get(floatArray)

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val maskX = (x / scaleX).toInt().coerceIn(0, maskWidth - 1)
                val maskY = (y / scaleY).toInt().coerceIn(0, maskHeight - 1)
                val confidence = floatArray[maskY * maskWidth + maskX]
                if (confidence > 0.5f) {
                    output.setPixel(x, y, source.getPixel(x, y))
                }
            }
        }
        segmenter.close()
        output
    } catch (_: Exception) {
        removeStickerBackgroundFallback(source)
    }
}

/**
 * Fallback: BFS-Flood-Fill von den Rändern für einheitliche Hintergründe.
 */
private fun removeStickerBackgroundFallback(source: Bitmap, tolerance: Int = 40): Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    fun colorDist(c1: Int, c2: Int): Int {
        val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
        val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
        val db = (c1 and 0xFF) - (c2 and 0xFF)
        return maxOf(Math.abs(dr), Math.abs(dg), Math.abs(db))
    }

    val cornerColors = listOf(pixels[0], pixels[w - 1], pixels[(h - 1) * w], pixels[h * w - 1])
    val visited = BooleanArray(w * h)
    val queue = ArrayDeque<Int>()

    for (x in 0 until w) {
        for (y in listOf(0, h - 1)) {
            val idx = y * w + x
            if (!visited[idx] && cornerColors.any { colorDist(pixels[idx], it) <= tolerance }) {
                visited[idx] = true; queue.add(idx)
            }
        }
    }
    for (y in 1 until h - 1) {
        for (x in listOf(0, w - 1)) {
            val idx = y * w + x
            if (!visited[idx] && cornerColors.any { colorDist(pixels[idx], it) <= tolerance }) {
                visited[idx] = true; queue.add(idx)
            }
        }
    }

    while (queue.isNotEmpty()) {
        val idx = queue.removeFirst()
        val origColor = pixels[idx]
        pixels[idx] = 0
        val x = idx % w; val y = idx / w
        val neighbors = listOf(
            if (x > 0) idx - 1 else -1,
            if (x < w - 1) idx + 1 else -1,
            if (y > 0) idx - w else -1,
            if (y < h - 1) idx + w else -1
        )
        for (ni in neighbors) {
            if (ni >= 0 && !visited[ni]) {
                visited[ni] = true
                if (colorDist(pixels[ni], origColor) <= tolerance) queue.add(ni)
            }
        }
    }

    val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}
