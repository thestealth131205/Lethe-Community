package com.securechat.app.ui.screens

import android.net.Uri
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executor

@Composable
fun InAppCameraScreen(
    onPhotoCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context         = LocalContext.current
    val lifecycleOwner  = LocalLifecycleOwner.current
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var isVideoMode     by remember { mutableStateOf(false) }
    var isFrontCamera   by remember { mutableStateOf(false) }
    var flashMode       by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isRecording     by remember { mutableStateOf(false) }
    var zoomRatio       by remember { mutableFloatStateOf(1f) }
    var minZoom         by remember { mutableFloatStateOf(1f) }
    var maxZoom         by remember { mutableFloatStateOf(4f) }
    var videoFile       by remember { mutableStateOf<File?>(null) }

    val cameraRef       = remember { mutableStateOf<Camera?>(null) }
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    val videoCaptureRef = remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    val activeRecording = remember { mutableStateOf<Recording?>(null) }
    val providerRef     = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Physische Sensor-Rotation des Geräts, laufend über OrientationEventListener aktualisiert
    // (siehe DisposableEffect unten). Startwert = Display-Rotation beim ersten Binden.
    @Suppress("DEPRECATION")
    val initialDisplayRotation = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            (context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.rotation
        }
    }
    val currentRotation = remember { mutableIntStateOf(initialDisplayRotation) }

    // Sensor-Rotation kontinuierlich verfolgen: die Activity dreht ihr Fenster nicht mit
    // (configChanges="orientation"), daher liefert context.display.rotation nur EINMALIG beim
    // Kamera-Bind die korrekte Ausrichtung. Wird das Gerät danach gedreht (z.B. für ein Landscape-
    // Foto), blieb die targetRotation bisher auf dem alten Wert eingefroren → falsche EXIF-Rotation,
    // Foto erschien gedreht/im Portrait. Fix: bei jeder Sensor-Änderung targetRotation live nachziehen.
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135  -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else             -> Surface.ROTATION_0
                }
                if (currentRotation.intValue != rotation) {
                    currentRotation.intValue = rotation
                }
                imageCaptureRef.value?.targetRotation = rotation
                videoCaptureRef.value?.targetRotation = rotation
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }

    // Kamera binden wenn sich Modus oder Seite ändert
    LaunchedEffect(isFrontCamera, isVideoMode) {
        val displayRotation = currentRotation.intValue

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                providerRef.value = provider
                provider.unbindAll()

                val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
                               else CameraSelector.DEFAULT_BACK_CAMERA
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = CameraXPreview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val cam = if (isVideoMode) {
                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.fromOrderedList(
                                listOf(Quality.FHD, Quality.HD, Quality.SD),
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                            )
                        ).build()
                    val vc = VideoCapture.withOutput(recorder)
                    vc.targetRotation = displayRotation
                    videoCaptureRef.value = vc
                    imageCaptureRef.value = null
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                } else {
                    val imgCapture = ImageCapture.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setFlashMode(flashMode)
                        .setTargetRotation(displayRotation)
                        .build()
                    imageCaptureRef.value = imgCapture
                    videoCaptureRef.value = null
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imgCapture)
                }

                cameraRef.value = cam
                val zoomState = cam.cameraInfo.zoomState.value
                minZoom   = zoomState?.minZoomRatio ?: 1f
                maxZoom   = (zoomState?.maxZoomRatio ?: 4f).coerceAtLeast(minZoom + 0.1f)
                zoomRatio = minZoom
            } catch (e: Exception) {
                Timber.tag("LETHE_CAM").e(e, "Fehler beim Kamera-Binden")
            }
        }, executor)
    }

    // Flash aktualisieren ohne Neustart
    LaunchedEffect(flashMode) {
        imageCaptureRef.value?.flashMode = flashMode
    }

    // Ressourcen beim Verlassen freigeben
    DisposableEffect(Unit) {
        onDispose {
            activeRecording.value?.stop()
            activeRecording.value = null
            providerRef.value?.unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val cam = cameraRef.value ?: return@detectTransformGestures
                    val newZoom = (zoomRatio * zoom).coerceIn(minZoom, maxZoom)
                    zoomRatio = newZoom
                    cam.cameraControl.setZoomRatio(newZoom)
                }
            }
    ) {
        // Kamera-Vorschau (Vollbild)
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // ── Obere Leiste: Schließen + Blitz ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .then(if (isLandscape) Modifier else Modifier.background(Color(0xBB000000)))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                activeRecording.value?.stop()
                activeRecording.value = null
                onDismiss()
            }) {
                Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
            }

            // Blitz (nur Foto-Modus, Frontkamera hat keinen Blitz)
            if (!isVideoMode && !isFrontCamera) {
                IconButton(onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF  -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else                         -> ImageCapture.FLASH_MODE_OFF
                    }
                }) {
                    val (icon, tint) = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON   -> Icons.Default.FlashOn   to Color(0xFFFFDD00)
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto to Color(0xFFFFDD00)
                        else                         -> Icons.Default.FlashOff  to Color.White
                    }
                    Icon(icon, contentDescription = "Blitz", tint = tint)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        // ── Untere Bedienleiste ───────────────────────────────────────────────
        // Im Landscape: kleinere Buttons, näher am unteren Rand, kein Hintergrund
        val sideBtnSize    = if (isLandscape) 40.dp else 52.dp
        val sideIconSize   = if (isLandscape) 20.dp else 26.dp
        val shutterSize    = if (isLandscape) 56.dp else 76.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .then(if (isLandscape) Modifier else Modifier.background(Color(0xCC000000)))
                .navigationBarsPadding()
                .padding(top = if (isLandscape) 4.dp else 12.dp, bottom = if (isLandscape) 6.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom-Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(18.dp))
                Slider(
                    value = if (maxZoom > minZoom) (zoomRatio - minZoom) / (maxZoom - minZoom) else 0f,
                    onValueChange = { fraction ->
                        val newZoom = minZoom + fraction * (maxZoom - minZoom)
                        zoomRatio = newZoom
                        cameraRef.value?.cameraControl?.setZoomRatio(newZoom)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor        = Color.White,
                        activeTrackColor  = Color.White,
                        inactiveTrackColor = Color(0x66FFFFFF)
                    )
                )
                Icon(Icons.Default.ZoomIn, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(if (isLandscape) 4.dp else 10.dp))

            // Haupt-Zeile: [Kamera wechseln] [Auslöser] [Foto/Video toggle]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kamera wechseln
                Box(
                    modifier = Modifier
                        .size(sideBtnSize)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isRecording) {
                                isFrontCamera = !isFrontCamera
                                zoomRatio = minZoom
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Cameraswitch,
                        contentDescription = "Kamera wechseln",
                        tint = Color.White,
                        modifier = Modifier.size(sideIconSize)
                    )
                }

                // Auslöser / Aufnahme-Button
                Box(
                    modifier = Modifier
                        .size(shutterSize)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isVideoMode) {
                                if (isRecording) {
                                    // Aufnahme stoppen
                                    activeRecording.value?.stop()
                                    activeRecording.value = null
                                    isRecording = false
                                } else {
                                    // Aufnahme starten
                                    val f = File(context.cacheDir, "camera/video_${System.currentTimeMillis()}.mp4")
                                    f.parentFile?.mkdirs()
                                    videoFile = f
                                    val vc = videoCaptureRef.value ?: return@clickable
                                    val outputOptions = FileOutputOptions.Builder(f).build()
                                    val rec = vc.output
                                        .prepareRecording(context, outputOptions)
                                        .withAudioEnabled()
                                        .start(executor) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Finalize -> {
                                                    if (!event.hasError()) {
                                                        val uri = event.outputResults.outputUri
                                                        onVideoCaptured(uri)
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    activeRecording.value = rec
                                    isRecording = true
                                }
                            } else {
                                // Foto aufnehmen
                                val imgCapture = imageCaptureRef.value ?: return@clickable
                                val photoFile = File(context.cacheDir, "camera/photo_${System.currentTimeMillis()}.jpg")
                                photoFile.parentFile?.mkdirs()
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                imgCapture.takePicture(
                                    outputOptions,
                                    executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            val uri = output.savedUri
                                                ?: Uri.fromFile(photoFile)
                                            onPhotoCaptured(uri)
                                        }
                                        override fun onError(e: ImageCaptureException) {
                                            Timber.tag("LETHE_CAM").e(e, "Foto-Fehler")
                                        }
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        // Roter Stop-Quadrat
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 20.dp else 28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF3333))
                        )
                    } else if (isVideoMode) {
                        // Video-Aufnahme-Kreis
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 38.dp else 52.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3333))
                        )
                    } else {
                        // Foto-Auslöser
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 46.dp else 62.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEEEEEE))
                        )
                    }
                }

                // Foto/Video-Modus wechseln
                Box(
                    modifier = Modifier
                        .size(sideBtnSize)
                        .clip(CircleShape)
                        .background(if (isVideoMode) Color(0x88FF3333) else Color(0x44FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isRecording) {
                                isVideoMode = !isVideoMode
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isVideoMode) Icons.Default.PhotoCamera else Icons.Default.Videocam,
                        contentDescription = if (isVideoMode) "Foto-Modus" else "Video-Modus",
                        tint = Color.White,
                        modifier = Modifier.size(sideIconSize)
                    )
                }
            }

            Spacer(Modifier.height(if (isLandscape) 2.dp else 8.dp))

            // Modus-Label
            Text(
                if (isVideoMode) {
                    if (isRecording) "● Aufnahme läuft – tippen zum Stoppen" else "Video – tippen zum Starten"
                } else "Foto – tippen zum Aufnehmen",
                color     = if (isRecording) Color(0xFFFF8888) else Color(0xBBFFFFFF),
                fontSize  = 12.sp,
                fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
