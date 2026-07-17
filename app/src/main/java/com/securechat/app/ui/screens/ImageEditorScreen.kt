@file:OptIn(ExperimentalMaterial3Api::class)

package com.securechat.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.stringResource
import com.securechat.app.R

// --- Datenmodelle ---

private data class DrawingStroke(
    val path: Path,
    val androidPath: AndroidPath,
    val color: Color,
    val strokeWidth: Float
)

private data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var offset: Offset,
    val color: Color,
    val textSizeSp: Float = 32f
)

private enum class EditorTool { DRAW, CROP }

private enum class CropHandle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP, BOTTOM, LEFT, RIGHT }

/**
 * Gespeicherter Zustand eines einzelnen Bilds im Multi-Bild-Editor.
 * Wird beim Wechsel zwischen Bildern gespeichert/wiederhergestellt.
 */
private data class EditorSavedState(
    val strokes: List<DrawingStroke>,
    val textOverlays: List<TextOverlay>,
    val currentImageUri: Uri,
    val cropInitialized: Boolean,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val canvasWidth: Int,
    val canvasHeight: Int
)

// --- Bild verarbeiten und in Cache schreiben ---

private suspend fun processEditorStateToFile(
    context: Context,
    state: EditorSavedState
): Uri? = withContext(Dispatchers.IO) {
    val stream: InputStream = context.contentResolver.openInputStream(state.currentImageUri)
        ?: return@withContext null
    val rawBitmap = BitmapFactory.decodeStream(stream)
    stream.close()
    if (rawBitmap == null) return@withContext null

    // EXIF-Orientierung anwenden (BitmapFactory ignoriert EXIF)
    val exifDegrees = try {
        context.contentResolver.openInputStream(state.currentImageUri)?.use { s ->
            val exif = android.media.ExifInterface(s)
            when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    } catch (_: Exception) { 0f }
    val originalBitmap: Bitmap = if (exifDegrees != 0f) {
        val matrix = android.graphics.Matrix().apply { postRotate(exifDegrees) }
        val rotated = android.graphics.Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        rawBitmap.recycle()
        rotated
    } else rawBitmap

    val canvasW = state.canvasWidth.toFloat().coerceAtLeast(1f)
    val canvasH = state.canvasHeight.toFloat().coerceAtLeast(1f)

    val hasCrop = state.cropInitialized &&
        state.cropRight > state.cropLeft + 1f &&
        state.cropBottom > state.cropTop + 1f &&
        !(state.cropLeft < 1f && state.cropTop < 1f &&
          state.cropRight > canvasW - 2f && state.cropBottom > canvasH - 2f)

    val sourceBitmap: Bitmap
    val drawOffsetX: Float
    val drawOffsetY: Float
    val drawWidth: Float
    val drawHeight: Float

    if (hasCrop && state.canvasWidth > 1) {
        val origToCanvasX = originalBitmap.width.toFloat() / canvasW
        val origToCanvasY = originalBitmap.height.toFloat() / canvasH
        val bmpX = (state.cropLeft * origToCanvasX).toInt().coerceIn(0, originalBitmap.width - 1)
        val bmpY = (state.cropTop * origToCanvasY).toInt().coerceIn(0, originalBitmap.height - 1)
        val bmpW = ((state.cropRight - state.cropLeft) * origToCanvasX).toInt()
            .coerceIn(1, originalBitmap.width - bmpX)
        val bmpH = ((state.cropBottom - state.cropTop) * origToCanvasY).toInt()
            .coerceIn(1, originalBitmap.height - bmpY)
        sourceBitmap = Bitmap.createBitmap(originalBitmap, bmpX, bmpY, bmpW, bmpH)
        drawOffsetX = state.cropLeft
        drawOffsetY = state.cropTop
        drawWidth = state.cropRight - state.cropLeft
        drawHeight = state.cropBottom - state.cropTop
    } else {
        sourceBitmap = originalBitmap
        drawOffsetX = 0f
        drawOffsetY = 0f
        drawWidth = canvasW
        drawHeight = canvasH
    }

    // bmp ist eine vollständig unabhängige Kopie → danach sourceBitmap und originalBitmap freigeben
    val bmp = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
    if (sourceBitmap !== originalBitmap) sourceBitmap.recycle()
    originalBitmap.recycle()
    val aCanvas = AndroidCanvas(bmp)
    val scaleX = bmp.width.toFloat() / drawWidth
    val scaleY = bmp.height.toFloat() / drawHeight

    state.strokes.forEach { stroke ->
        val paint = AndroidPaint().apply {
            color = stroke.color.toArgb()
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            isAntiAlias = true
            this.strokeWidth = stroke.strokeWidth * scaleX
        }
        val matrix = android.graphics.Matrix().apply {
            postTranslate(-drawOffsetX, -drawOffsetY)
            postScale(scaleX, scaleY)
        }
        val scaledPath = AndroidPath()
        stroke.androidPath.transform(matrix, scaledPath)
        aCanvas.drawPath(scaledPath, paint)
    }

    state.textOverlays.forEach { overlay ->
        val paint = AndroidPaint().apply {
            color = overlay.color.toArgb()
            textSize = overlay.textSizeSp * context.resources.displayMetrics.density * scaleX
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            setShadowLayer(3f * scaleX, 2f * scaleX, 2f * scaleX, android.graphics.Color.BLACK)
        }
        aCanvas.drawText(
            overlay.text,
            (overlay.offset.x - drawOffsetX) * scaleX,
            (overlay.offset.y - drawOffsetY) * scaleY,
            paint
        )
    }

    val filename = "Lethe_${java.util.UUID.randomUUID()}.jpg"
    val outFile = java.io.File(context.cacheDir, filename)
    outFile.outputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
    }
    bmp.recycle()
    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
}

// --- Haupt-Screen ---

@Composable
fun ImageEditorScreen(
    imageUri: Uri,
    chatId: String,
    partnerId: String,
    initialCaption: String? = null,
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val isGroup = groups.any { it.groupId == chatId }

    // Multi-Bild: lese pendingImageUris aus dem ViewModel
    val pendingUris by viewModel.pendingImageUris.collectAsState()
    // Beim ersten Aufruf: entweder pendingUris (multi) oder [imageUri] (single)
    val imageUris: List<Uri> = remember(pendingUris) {
        if (pendingUris.isNotEmpty()) pendingUris else listOf(imageUri)
    }

    // --- Pro-Bild-Zustand ---
    var currentIndex by remember { mutableIntStateOf(0) }
    val savedStates = remember { mutableMapOf<Int, EditorSavedState>() }

    // Aktueller Editorstatus (repräsentiert imageUris[currentIndex])
    var currentImageUri by remember { mutableStateOf(imageUris[0]) }
    var isApplyingCrop by remember { mutableStateOf(false) }

    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    val textOverlays = remember { mutableStateListOf<TextOverlay>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var currentAndroidPath by remember { mutableStateOf<AndroidPath?>(null) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableStateOf(12f) }
    var captionText by remember { mutableStateOf(initialCaption ?: "") }
    var isSending by remember { mutableStateOf(false) }
    var showBrushSlider by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    var activeTool by remember { mutableStateOf(EditorTool.DRAW) }

    // Crop state
    var cropLeft by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(0f) }
    var cropBottom by remember { mutableStateOf(0f) }
    var cropInitialized by remember { mutableStateOf(false) }
    var draggingHandle by remember { mutableStateOf(CropHandle.NONE) }
    var cropAspectRatio by remember { mutableStateOf<Float?>(null) }
    var showAspectDropdown by remember { mutableStateOf(false) }
    var imageAspectRatio by remember { mutableStateOf<Float?>(null) }

    val handleTouchPx = with(density) { 44.dp.toPx() }
    val handleVisualRadius = with(density) { 20.dp.toPx() }

    fun initCropRect() {
        cropLeft = 0f
        cropTop = 0f
        cropRight = canvasSize.width.toFloat()
        cropBottom = canvasSize.height.toFloat()
        cropInitialized = true
    }

    fun applyRatioToCrop(ratio: Float) {
        val canvasW = canvasSize.width.toFloat().coerceAtLeast(1f)
        val canvasH = canvasSize.height.toFloat().coerceAtLeast(1f)
        val centerX = canvasW / 2f
        val centerY = canvasH / 2f
        val candidateW = canvasW * 0.85f
        val candidateH = candidateW / ratio
        if (candidateH <= canvasH * 0.85f) {
            cropLeft = centerX - candidateW / 2f
            cropRight = centerX + candidateW / 2f
            cropTop = centerY - candidateH / 2f
            cropBottom = centerY + candidateH / 2f
        } else {
            val h = canvasH * 0.85f
            val w = h * ratio
            cropLeft = centerX - w / 2f
            cropRight = centerX + w / 2f
            cropTop = centerY - h / 2f
            cropBottom = centerY + h / 2f
        }
        cropInitialized = true
    }

    fun rotateImageCW90() {
        scope.launch(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(currentImageUri) ?: return@launch
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            if (bmp == null) return@launch
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            val rotated = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            bmp.recycle()
            val dir = java.io.File(context.cacheDir, "image_editor").also { it.mkdirs() }
            val file = java.io.File(dir, "rot_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            rotated.recycle()
            val newUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            withContext(Dispatchers.Main) {
                currentImageUri = newUri
                strokes.clear()
                textOverlays.clear()
                cropInitialized = false
                imageAspectRatio = imageAspectRatio?.let { 1f / it }
            }
        }
    }

    // --- Zwischen Bildern wechseln ---
    fun switchToImage(newIndex: Int) {
        if (newIndex == currentIndex || newIndex !in imageUris.indices) return
        // Aktuellen Zustand speichern
        savedStates[currentIndex] = EditorSavedState(
            strokes = strokes.toList(),
            textOverlays = textOverlays.toList(),
            currentImageUri = currentImageUri,
            cropInitialized = cropInitialized,
            cropLeft = cropLeft, cropTop = cropTop,
            cropRight = cropRight, cropBottom = cropBottom,
            canvasWidth = canvasSize.width, canvasHeight = canvasSize.height
        )
        // Zielzustand laden
        val target = savedStates[newIndex] ?: EditorSavedState(
            strokes = emptyList(), textOverlays = emptyList(),
            currentImageUri = imageUris[newIndex],
            cropInitialized = false,
            cropLeft = 0f, cropTop = 0f, cropRight = 0f, cropBottom = 0f,
            canvasWidth = 1, canvasHeight = 1
        )
        strokes.clear(); strokes.addAll(target.strokes)
        textOverlays.clear(); textOverlays.addAll(target.textOverlays)
        currentImageUri = target.currentImageUri
        cropInitialized = target.cropInitialized
        cropLeft = target.cropLeft; cropTop = target.cropTop
        cropRight = target.cropRight; cropBottom = target.cropBottom
        activeTool = EditorTool.DRAW
        showBrushSlider = false
        currentIndex = newIndex
    }

    LaunchedEffect(activeTool) {
        if (activeTool == EditorTool.CROP && canvasSize.width > 1) {
            if (!cropInitialized) initCropRect()
        }
    }

    LaunchedEffect(currentImageUri) {
        imageAspectRatio = withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(currentImageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    // EXIF-Rotation prüfen: bei 90°/270° sind Breite und Höhe vertauscht
                    val exifDeg = try {
                        context.contentResolver.openInputStream(currentImageUri)?.use { s ->
                            val exif = android.media.ExifInterface(s)
                            exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                        } ?: android.media.ExifInterface.ORIENTATION_NORMAL
                    } catch (_: Exception) { android.media.ExifInterface.ORIENTATION_NORMAL }
                    val swapped = exifDeg == android.media.ExifInterface.ORIENTATION_ROTATE_90 ||
                                  exifDeg == android.media.ExifInterface.ORIENTATION_ROTATE_270
                    if (swapped) opts.outHeight.toFloat() / opts.outWidth.toFloat()
                    else opts.outWidth.toFloat() / opts.outHeight.toFloat()
                } else null
            } catch (_: Exception) { null }
        }
    }

    val paletteColors = listOf(
        Color.Red, Color(0xFFFF6600), Color.Yellow,
        Color.Green, Color.Cyan, Color.Blue,
        Color.Magenta, Color.White
    )

    // --- Alle Bilder verarbeiten und senden ---
    fun saveAndSend() {
        if (isSending) return
        isSending = true
        // Aktuellen Zustand für aktuelles Bild speichern
        savedStates[currentIndex] = EditorSavedState(
            strokes = strokes.toList(),
            textOverlays = textOverlays.toList(),
            currentImageUri = currentImageUri,
            cropInitialized = cropInitialized,
            cropLeft = cropLeft, cropTop = cropTop,
            cropRight = cropRight, cropBottom = cropBottom,
            canvasWidth = canvasSize.width, canvasHeight = canvasSize.height
        )
        val snapCaptionText = captionText

        scope.launch {
            val resultUris = mutableListOf<Uri>()
            for (idx in imageUris.indices) {
                val state = savedStates[idx]
                if (state == null) {
                    resultUris.add(imageUris[idx])
                } else {
                    val savedUri = processEditorStateToFile(context, state)
                    if (savedUri != null) {
                        resultUris.add(savedUri)
                    } else {
                        isSending = false
                        return@launch
                    }
                }
            }

            // Sofort zum Chat navigieren – Upload läuft im Hintergrund
            // sendMultiImageMessageSuspend erstellt einen Placeholder und gibt sofort true zurück
            viewModel.clearPendingShare()
            viewModel.clearPendingImageUris()
            val navRoute = if (isGroup) "chat/$partnerId?isGroup=true" else "chat/$partnerId"
            navController.navigate(navRoute) {
                popUpTo("contacts") { inclusive = false }
            }

            // Upload im Hintergrund starten (ViewModel managed den Job)
            val success = viewModel.sendMultiImageMessageSuspend(partnerId, resultUris, isGroup)
            if (success && snapCaptionText.isNotBlank()) {
                if (isGroup) viewModel.sendGroupMessage(partnerId, snapCaptionText.trim())
                else viewModel.sendMessage(partnerId, snapCaptionText.trim())
            }
            isSending = false
        }
    }

    val thumbnailScrollState = rememberLazyListState()
    val isMulti = imageUris.size > 1

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearPendingImageUris()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.image_editor_close))
                    }
                },
                title = {
                    if (isMulti) {
                        Text("${currentIndex + 1} / ${imageUris.size}")
                    } else {
                        Text(stringResource(R.string.image_editor_title))
                    }
                },
                actions = {
                    if (activeTool == EditorTool.DRAW) {
                        IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeLastOrNull() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.image_editor_undo))
                        }
                        IconButton(onClick = { showTextDialog = true }) {
                            Icon(Icons.Default.TextFields, contentDescription = stringResource(R.string.image_editor_add_text))
                        }
                        IconButton(onClick = { showBrushSlider = !showBrushSlider }) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = stringResource(R.string.image_editor_brush_size),
                                tint = currentColor
                            )
                        }
                    }
                    if (activeTool == EditorTool.CROP) {
                        IconButton(onClick = { rotateImageCW90() }) {
                            Icon(Icons.Default.Rotate90DegreesCw, contentDescription = "Drehen")
                        }
                        Box {
                            IconButton(onClick = { showAspectDropdown = true }) {
                                Icon(
                                    Icons.Default.AspectRatio,
                                    contentDescription = "Seitenverhältnis",
                                    tint = if (cropAspectRatio != null) MaterialTheme.colorScheme.primary
                                           else LocalContentColor.current
                                )
                            }
                            DropdownMenu(
                                expanded = showAspectDropdown,
                                onDismissRequest = { showAspectDropdown = false }
                            ) {
                                val ratios = listOf(
                                    "Frei"  to null,
                                    "1:1"   to 1f,
                                    "4:3"   to 4f / 3f,
                                    "3:2"   to 3f / 2f,
                                    "16:9"  to 16f / 9f,
                                    "9:16"  to 9f / 16f,
                                    "3:4"   to 3f / 4f,
                                    "2:3"   to 2f / 3f
                                )
                                ratios.forEach { (label, ratio) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                color = if (cropAspectRatio == ratio)
                                                    MaterialTheme.colorScheme.primary
                                                else LocalContentColor.current
                                            )
                                        },
                                        onClick = {
                                            cropAspectRatio = ratio
                                            if (ratio != null) applyRatioToCrop(ratio) else initCropRect()
                                            showAspectDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        activeTool = if (activeTool == EditorTool.CROP) EditorTool.DRAW else EditorTool.CROP
                        showBrushSlider = false
                    }) {
                        Icon(
                            Icons.Default.Crop,
                            contentDescription = stringResource(R.string.image_editor_crop),
                            tint = if (activeTool == EditorTool.CROP) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current
                        )
                    }
                    FilledIconButton(
                        onClick = { saveAndSend() },
                        enabled = !isSending,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.image_editor_send),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Thumbnail-Strip für Multi-Bild
                if (isMulti) {
                    LazyRow(
                        state = thumbnailScrollState,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        itemsIndexed(imageUris) { idx, uri ->
                            val isActive = idx == currentIndex
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isActive) 2.5.dp else 1.dp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                    )
                                    .clickable { switchToImage(idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Nummer-Badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else Color.Black.copy(alpha = 0.55f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 9.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (activeTool == EditorTool.CROP) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { initCropRect() }) {
                            Text(stringResource(R.string.image_editor_crop_reset))
                        }
                        Button(
                            onClick = {
                                if (!cropInitialized || isApplyingCrop) return@Button
                                isApplyingCrop = true
                                val snapLeft = cropLeft
                                val snapTop = cropTop
                                val snapRight = cropRight
                                val snapBottom = cropBottom
                                val snapSize = canvasSize
                                scope.launch(Dispatchers.IO) {
                                    val newUri = try {
                                        val stream = context.contentResolver.openInputStream(currentImageUri)
                                        val orig = if (stream != null) BitmapFactory.decodeStream(stream).also { stream.close() } else null
                                        if (orig != null && snapSize.width > 1) {
                                            val sx = orig.width.toFloat() / snapSize.width.toFloat()
                                            val sy = orig.height.toFloat() / snapSize.height.toFloat()
                                            val bx = (snapLeft * sx).toInt().coerceIn(0, orig.width - 1)
                                            val by_ = (snapTop * sy).toInt().coerceIn(0, orig.height - 1)
                                            val bw = ((snapRight - snapLeft) * sx).toInt().coerceIn(1, orig.width - bx)
                                            val bh = ((snapBottom - snapTop) * sy).toInt().coerceIn(1, orig.height - by_)
                                            val cropped = Bitmap.createBitmap(orig, bx, by_, bw, bh)
                                            val dir = java.io.File(context.cacheDir, "image_editor").also { it.mkdirs() }
                                            val file = java.io.File(dir, "crop_${System.currentTimeMillis()}.jpg")
                                            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        } else null
                                    } catch (_: Exception) { null }
                                    withContext(Dispatchers.Main) {
                                        if (newUri != null) {
                                            currentImageUri = newUri
                                            strokes.clear()
                                            textOverlays.clear()
                                            cropInitialized = false
                                        }
                                        isApplyingCrop = false
                                        activeTool = EditorTool.DRAW
                                    }
                                }
                            },
                            enabled = !isApplyingCrop
                        ) {
                            if (isApplyingCrop) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.image_editor_crop_apply))
                            }
                        }
                    }
                } else {
                    if (showBrushSlider) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.image_editor_size_label), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
                            Slider(
                                value = strokeWidth,
                                onValueChange = { strokeWidth = it },
                                valueRange = 4f..40f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        paletteColors.forEach { color ->
                            val isSelected = color == currentColor
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { currentColor = color }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        placeholder = { Text(stringResource(R.string.image_editor_caption_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            val boxModifier = if (imageAspectRatio != null) {
                val heightIfFillWidth = maxWidth / imageAspectRatio!!
                if (heightIfFillWidth <= maxHeight) {
                    Modifier.fillMaxWidth().aspectRatio(imageAspectRatio!!)
                } else {
                    Modifier.fillMaxHeight().aspectRatio(imageAspectRatio!!)
                }
            } else {
                Modifier.fillMaxSize()
            }

            Box(modifier = boxModifier) {
                AsyncImage(
                    model = currentImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                if (activeTool == EditorTool.DRAW) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                canvasSize = coords.size
                            }
                            .pointerInput(currentColor, strokeWidth) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val newAndroidPath = AndroidPath().apply { moveTo(offset.x, offset.y) }
                                        val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                        currentAndroidPath = newAndroidPath
                                        currentPath = newPath
                                    },
                                    onDrag = { change, _ ->
                                        val pos = change.position
                                        currentPath?.lineTo(pos.x, pos.y)
                                        currentAndroidPath?.lineTo(pos.x, pos.y)
                                        currentPath = Path().apply { addPath(currentPath!!) }
                                    },
                                    onDragEnd = {
                                        val finishedPath = currentPath
                                        val finishedAndroidPath = currentAndroidPath
                                        if (finishedPath != null && finishedAndroidPath != null) {
                                            strokes.add(DrawingStroke(finishedPath, finishedAndroidPath, currentColor, strokeWidth))
                                        }
                                        currentPath = null
                                        currentAndroidPath = null
                                    }
                                )
                            }
                    ) {
                        strokes.forEach { stroke ->
                            drawPath(stroke.path, stroke.color, style = Stroke(stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        currentPath?.let { path ->
                            drawPath(path, currentColor, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                } else {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                canvasSize = coords.size
                                if (!cropInitialized) {
                                    cropLeft = 0f; cropTop = 0f
                                    cropRight = coords.size.width.toFloat()
                                    cropBottom = coords.size.height.toFloat()
                                    cropInitialized = true
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val edgePx = handleTouchPx * 0.6f
                                        val cornerProxPx = handleTouchPx * 1.8f
                                        val cropHandles = listOf(
                                            Offset(cropLeft, cropTop),    // 0: TL
                                            Offset(cropRight, cropTop),   // 1: TR
                                            Offset(cropLeft, cropBottom), // 2: BL
                                            Offset(cropRight, cropBottom) // 3: BR
                                        )
                                        draggingHandle = when {
                                            // Corners first (higher priority)
                                            (offset - cropHandles[0]).getDistance() < handleTouchPx -> CropHandle.TOP_LEFT
                                            (offset - cropHandles[1]).getDistance() < handleTouchPx -> CropHandle.TOP_RIGHT
                                            (offset - cropHandles[2]).getDistance() < handleTouchPx -> CropHandle.BOTTOM_LEFT
                                            (offset - cropHandles[3]).getDistance() < handleTouchPx -> CropHandle.BOTTOM_RIGHT
                                            // Edge lines: snap to nearest corner via snapHandleOnEdge, otherwise activate edge
                                            kotlin.math.abs(offset.y - cropTop) < edgePx && offset.x in cropLeft..cropRight -> {
                                                val snap = snapHandleOnEdge(offset, cropHandles, edgePx, cornerProxPx)
                                                when (snap) { 0 -> CropHandle.TOP_LEFT; 1 -> CropHandle.TOP_RIGHT; else -> CropHandle.TOP }
                                            }
                                            kotlin.math.abs(offset.y - cropBottom) < edgePx && offset.x in cropLeft..cropRight -> {
                                                val snap = snapHandleOnEdge(offset, cropHandles, edgePx, cornerProxPx)
                                                when (snap) { 2 -> CropHandle.BOTTOM_LEFT; 3 -> CropHandle.BOTTOM_RIGHT; else -> CropHandle.BOTTOM }
                                            }
                                            kotlin.math.abs(offset.x - cropLeft) < edgePx && offset.y in cropTop..cropBottom -> {
                                                val snap = snapHandleOnEdge(offset, cropHandles, edgePx, cornerProxPx)
                                                when (snap) { 0 -> CropHandle.TOP_LEFT; 2 -> CropHandle.BOTTOM_LEFT; else -> CropHandle.LEFT }
                                            }
                                            kotlin.math.abs(offset.x - cropRight) < edgePx && offset.y in cropTop..cropBottom -> {
                                                val snap = snapHandleOnEdge(offset, cropHandles, edgePx, cornerProxPx)
                                                when (snap) { 1 -> CropHandle.TOP_RIGHT; 3 -> CropHandle.BOTTOM_RIGHT; else -> CropHandle.RIGHT }
                                            }
                                            else -> CropHandle.NONE
                                        }
                                    },
                                    onDrag = { _, delta ->
                                        val minSize = 60f
                                        val maxW = canvasSize.width.toFloat()
                                        val maxH = canvasSize.height.toFloat()
                                        val ratio = cropAspectRatio
                                        if (ratio != null) {
                                            when (draggingHandle) {
                                                CropHandle.TOP_LEFT -> {
                                                    val newLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                                    val newWidth = cropRight - newLeft
                                                    val newHeight = newWidth / ratio
                                                    cropLeft = newLeft
                                                    cropTop = (cropBottom - newHeight).coerceIn(0f, cropBottom - minSize)
                                                }
                                                CropHandle.TOP_RIGHT -> {
                                                    val newRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                                    val newWidth = newRight - cropLeft
                                                    val newHeight = newWidth / ratio
                                                    cropRight = newRight
                                                    cropTop = (cropBottom - newHeight).coerceIn(0f, cropBottom - minSize)
                                                }
                                                CropHandle.BOTTOM_LEFT -> {
                                                    val newLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                                    val newWidth = cropRight - newLeft
                                                    val newHeight = newWidth / ratio
                                                    cropLeft = newLeft
                                                    cropBottom = (cropTop + newHeight).coerceIn(cropTop + minSize, maxH)
                                                }
                                                CropHandle.BOTTOM_RIGHT -> {
                                                    val newRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                                    val newWidth = newRight - cropLeft
                                                    val newHeight = newWidth / ratio
                                                    cropRight = newRight
                                                    cropBottom = (cropTop + newHeight).coerceIn(cropTop + minSize, maxH)
                                                }
                                                CropHandle.TOP -> {
                                                    val newTop = (cropTop + delta.y).coerceIn(0f, cropBottom - minSize)
                                                    val newHeight = cropBottom - newTop
                                                    val newWidth = newHeight * ratio
                                                    val cx = (cropLeft + cropRight) / 2f
                                                    val half = newWidth / 2f
                                                    if (cx - half >= 0f && cx + half <= maxW) { cropLeft = cx - half; cropRight = cx + half }
                                                    cropTop = newTop
                                                }
                                                CropHandle.BOTTOM -> {
                                                    val newBottom = (cropBottom + delta.y).coerceIn(cropTop + minSize, maxH)
                                                    val newHeight = newBottom - cropTop
                                                    val newWidth = newHeight * ratio
                                                    val cx = (cropLeft + cropRight) / 2f
                                                    val half = newWidth / 2f
                                                    if (cx - half >= 0f && cx + half <= maxW) { cropLeft = cx - half; cropRight = cx + half }
                                                    cropBottom = newBottom
                                                }
                                                CropHandle.LEFT -> {
                                                    val newLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                                    val newWidth = cropRight - newLeft
                                                    val newHeight = newWidth / ratio
                                                    val cy = (cropTop + cropBottom) / 2f
                                                    val half = newHeight / 2f
                                                    if (cy - half >= 0f && cy + half <= maxH) { cropTop = cy - half; cropBottom = cy + half }
                                                    cropLeft = newLeft
                                                }
                                                CropHandle.RIGHT -> {
                                                    val newRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                                    val newWidth = newRight - cropLeft
                                                    val newHeight = newWidth / ratio
                                                    val cy = (cropTop + cropBottom) / 2f
                                                    val half = newHeight / 2f
                                                    if (cy - half >= 0f && cy + half <= maxH) { cropTop = cy - half; cropBottom = cy + half }
                                                    cropRight = newRight
                                                }
                                                CropHandle.NONE -> Unit
                                            }
                                        } else {
                                            when (draggingHandle) {
                                                CropHandle.TOP_LEFT -> {
                                                    cropLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                                    cropTop = (cropTop + delta.y).coerceIn(0f, cropBottom - minSize)
                                                }
                                                CropHandle.TOP_RIGHT -> {
                                                    cropRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                                    cropTop = (cropTop + delta.y).coerceIn(0f, cropBottom - minSize)
                                                }
                                                CropHandle.BOTTOM_LEFT -> {
                                                    cropLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                                    cropBottom = (cropBottom + delta.y).coerceIn(cropTop + minSize, maxH)
                                                }
                                                CropHandle.BOTTOM_RIGHT -> {
                                                    cropRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                                    cropBottom = (cropBottom + delta.y).coerceIn(cropTop + minSize, maxH)
                                                }
                                                CropHandle.TOP -> cropTop = (cropTop + delta.y).coerceIn(0f, cropBottom - minSize)
                                                CropHandle.BOTTOM -> cropBottom = (cropBottom + delta.y).coerceIn(cropTop + minSize, maxH)
                                                CropHandle.LEFT -> cropLeft = (cropLeft + delta.x).coerceIn(0f, cropRight - minSize)
                                                CropHandle.RIGHT -> cropRight = (cropRight + delta.x).coerceIn(cropLeft + minSize, maxW)
                                                CropHandle.NONE -> Unit
                                            }
                                        }
                                    },
                                    onDragEnd = { draggingHandle = CropHandle.NONE }
                                )
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        val dim = Color.Black.copy(alpha = 0.55f)

                        drawRect(dim, topLeft = Offset(0f, 0f), size = Size(w, cropTop))
                        drawRect(dim, topLeft = Offset(0f, cropBottom), size = Size(w, h - cropBottom))
                        drawRect(dim, topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropBottom - cropTop))
                        drawRect(dim, topLeft = Offset(cropRight, cropTop), size = Size(w - cropRight, cropBottom - cropTop))

                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropLeft, cropTop),
                            size = Size(cropRight - cropLeft, cropBottom - cropTop),
                            style = Stroke(width = 2f)
                        )

                        val thirdW = (cropRight - cropLeft) / 3f
                        val thirdH = (cropBottom - cropTop) / 3f
                        for (i in 1..2) {
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft + thirdW * i, cropTop), Offset(cropLeft + thirdW * i, cropBottom), strokeWidth = 1f)
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(cropLeft, cropTop + thirdH * i), Offset(cropRight, cropTop + thirdH * i), strokeWidth = 1f)
                        }

                        listOf(
                            Offset(cropLeft, cropTop),
                            Offset(cropRight, cropTop),
                            Offset(cropLeft, cropBottom),
                            Offset(cropRight, cropBottom)
                        ).forEach { corner ->
                            drawCircle(Color.Black.copy(alpha = 0.35f), radius = handleVisualRadius + 5f, center = corner)
                            drawCircle(Color.White, radius = handleVisualRadius, center = corner)
                            drawCircle(Color.Black.copy(alpha = 0.5f), radius = handleVisualRadius, center = corner, style = Stroke(2.5f))
                            drawCircle(primaryColor, radius = handleVisualRadius * 0.35f, center = corner)
                        }
                    }
                }

                // Textüberlagerungen
                textOverlays.forEachIndexed { index, overlay ->
                    var pos by remember(overlay.id) { mutableStateOf(overlay.offset) }
                    Text(
                        text = overlay.text,
                        style = TextStyle(
                            color = overlay.color,
                            fontSize = overlay.textSizeSp.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 4f)
                        ),
                        modifier = Modifier
                            .offset { IntOffset(pos.x.toInt(), pos.y.toInt()) }
                            .pointerInput(overlay.id) {
                                detectDragGestures { _, dragAmount ->
                                    pos = Offset(pos.x + dragAmount.x, pos.y + dragAmount.y)
                                    textOverlays[index] = overlay.copy(offset = pos)
                                }
                            }
                    )
                }
            }
        }
    }

    // Text-Dialog
    if (showTextDialog) {
        var inputText by remember { mutableStateOf("") }
        var textColor by remember { mutableStateOf(Color.White) }
        val textPaletteColors = listOf(Color.White, Color.Black, Color.Red, Color.Yellow,
            Color.Green, Color.Cyan, Color.Blue, Color.Magenta)

        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text(stringResource(R.string.image_editor_text_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text(stringResource(R.string.image_editor_text_input_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                    Text(stringResource(R.string.image_editor_text_color_label), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        textPaletteColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (color == textColor) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { textColor = color }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            textOverlays.add(
                                TextOverlay(
                                    text = inputText,
                                    offset = Offset(canvasSize.width / 4f, canvasSize.height / 3f),
                                    color = textColor
                                )
                            )
                        }
                        showTextDialog = false
                    }
                ) { Text(stringResource(R.string.image_editor_add_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text(stringResource(R.string.image_editor_cancel)) }
            }
        )
    }
}
