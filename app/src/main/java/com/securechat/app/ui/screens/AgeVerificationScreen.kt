package com.securechat.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.data.network.AgeVerificationResponse
import com.securechat.app.ui.MainViewModel
import timber.log.Timber
import java.io.File
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// ---------------------------------------------------------------------------
// State-Machine
// ---------------------------------------------------------------------------

enum class LivenessState {
    LOOK_STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    CAPTURING,
    UPLOADING,
    SUCCESS,
    ERROR
}

// ---------------------------------------------------------------------------
// Main Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeVerificationScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // --- State ---
    val livenessState = remember { mutableStateOf(LivenessState.LOOK_STRAIGHT) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var birthdate by remember { mutableStateOf("") }  // YYYY-MM-DD
    var showBirthdateStep by remember { mutableStateOf(true) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val ageVerificationResult by viewModel.ageVerificationResult.collectAsState()

    // CameraX refs held outside AndroidView so LaunchedEffect can access them
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val faceDetectionProvider = viewModel.faceDetectionProvider

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            viewModel.resetAgeVerificationResult()
        }
    }

    // Camera permission launcher – nach Gewährung direkt zur Kamera wechseln
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            showBirthdateStep = false
        } else {
            onNavigateBack()
        }
    }

    // --- React to CAPTURING state: auto-take photo ---
    LaunchedEffect(livenessState.value) {
        if (livenessState.value == LivenessState.CAPTURING) {
            val capture = imageCaptureRef ?: run {
                livenessState.value = LivenessState.ERROR
                errorMessage = context.getString(R.string.age_verify_error_camera_not_ready)
                return@LaunchedEffect
            }
            val photoFile = File(context.cacheDir, "age_verify_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        capturedFile = photoFile
                        livenessState.value = LivenessState.UPLOADING
                    }
                    override fun onError(exc: ImageCaptureException) {
                        errorMessage = "Foto-Fehler: ${exc.message}"
                        livenessState.value = LivenessState.ERROR
                    }
                }
            )
        }
    }

    // --- React to UPLOADING state: send to server ---
    LaunchedEffect(livenessState.value) {
        if (livenessState.value == LivenessState.UPLOADING) {
            val file = capturedFile
            if (file == null || birthdate.isBlank()) {
                errorMessage = context.getString(R.string.age_verify_error_missing_data)
                livenessState.value = LivenessState.ERROR
                return@LaunchedEffect
            }
            viewModel.verifyAge(file, birthdate)
        }
    }

    // --- React to server result ---
    LaunchedEffect(ageVerificationResult) {
        val result = ageVerificationResult ?: return@LaunchedEffect
        if (livenessState.value == LivenessState.UPLOADING) {
            if (result.success) {
                livenessState.value = LivenessState.SUCCESS
            } else {
                errorMessage = result.message
                livenessState.value = LivenessState.ERROR
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Step 1: Birthdate input
    // ---------------------------------------------------------------------------
    if (showBirthdateStep) {
        BirthdateInputStep(
            birthdate = birthdate,
            onBirthdateChange = { birthdate = it },
            onConfirm = {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    showBirthdateStep = false
                }
            },
            onBack = onNavigateBack
        )
        return
    }

    // ---------------------------------------------------------------------------
    // Step 2: Liveness Detection + Camera
    // ---------------------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.age_verify_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera Preview (hidden during SUCCESS/ERROR)
            if (livenessState.value !in listOf(LivenessState.SUCCESS, LivenessState.ERROR)) {
                val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
                val livenessStateForAnalyzer = livenessState   // pass State ref

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCaptureRef = imageCapture

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val current = livenessStateForAnalyzer.value
                                if (current != LivenessState.LOOK_STRAIGHT &&
                                    current != LivenessState.TURN_LEFT &&
                                    current != LivenessState.TURN_RIGHT
                                ) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                try {
                                    val bitmap = mediaImageToBitmap(mediaImage)
                                    val angle = bitmap?.let {
                                        val result = faceDetectionProvider.detectHeadYaw(it, imageProxy.imageInfo.rotationDegrees)
                                        it.recycle()
                                        result
                                    }
                                    if (angle != null) {
                                        when (livenessStateForAnalyzer.value) {
                                            LivenessState.LOOK_STRAIGHT -> {
                                                if (angle in -15f..15f) {
                                                    livenessStateForAnalyzer.value =
                                                        LivenessState.TURN_LEFT
                                                }
                                            }
                                            LivenessState.TURN_LEFT -> {
                                                if (angle < -30f) {
                                                    livenessStateForAnalyzer.value =
                                                        LivenessState.TURN_RIGHT
                                                }
                                            }
                                            LivenessState.TURN_RIGHT -> {
                                                if (angle > 30f) {
                                                    livenessStateForAnalyzer.value =
                                                        LivenessState.CAPTURING
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("AgeVerify").w("FaceDetector: ${e.message}")
                                } finally {
                                    imageProxy.close()
                                }
                            }

                            try {
                                cameraProvider.unbindAll()
                                // Frontkamera bevorzugen, Rückkamera als Fallback
                                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA))
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                else
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Timber.tag("AgeVerify").e("CameraX bind failed: ${e.message}")
                                livenessStateForAnalyzer.value = LivenessState.ERROR
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay: instruction / status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (livenessState.value) {
                    LivenessState.LOOK_STRAIGHT -> LivenessInstruction(
                        text = stringResource(R.string.age_verify_look_straight),
                        emoji = "👁️"
                    )
                    LivenessState.TURN_LEFT -> LivenessInstruction(
                        text = stringResource(R.string.age_verify_turn_right),
                        emoji = "➡️"
                    )
                    LivenessState.TURN_RIGHT -> LivenessInstruction(
                        text = stringResource(R.string.age_verify_turn_left),
                        emoji = "⬅️"
                    )
                    LivenessState.CAPTURING -> LivenessInstruction(
                        text = stringResource(R.string.age_verify_capturing),
                        emoji = "📸"
                    )
                    LivenessState.UPLOADING -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    stringResource(R.string.age_verify_uploading),
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                    LivenessState.SUCCESS -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF66BB6A),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    stringResource(R.string.age_verify_success),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onNavigateBack,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF66BB6A)
                                    )
                                ) {
                                    Text(stringResource(R.string.age_verify_success_button))
                                }
                            }
                        }
                    }
                    LivenessState.ERROR -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7F0000))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFEF9A9A),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    errorMessage.ifBlank { stringResource(R.string.age_verify_error_title) },
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(
                                        onClick = onNavigateBack,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        )
                                    ) { Text(stringResource(R.string.age_verify_error_cancel)) }
                                    Button(
                                        onClick = {
                                            livenessState.value = LivenessState.LOOK_STRAIGHT
                                            capturedFile = null
                                            errorMessage = ""
                                            viewModel.resetAgeVerificationResult()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) { Text(stringResource(R.string.age_verify_error_retry)) }
                                }
                            }
                        }
                    }
                }
            }

            // Progress-Dots overlay (top area)
            if (livenessState.value in listOf(
                    LivenessState.LOOK_STRAIGHT,
                    LivenessState.TURN_LEFT,
                    LivenessState.TURN_RIGHT,
                    LivenessState.CAPTURING
                )
            ) {
                LivenessProgressDots(
                    currentState = livenessState.value,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1: Birthdate Input (DatePicker, deutsches Format TT.MM.JJJJ)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdateInputStep(
    birthdate: String,
    onBirthdateChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    // Anzeige im deutschen Format TT.MM.JJJJ
    var displayDate by remember { mutableStateOf(birthdate) }
    var isError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // DatePickerState: Initial kein Datum gewählt, Startjahr = aktuelles Jahr - 18
    val initialYear = Calendar.getInstance().get(Calendar.YEAR) - 25
    val datePickerState = rememberDatePickerState(
        initialDisplayedMonthMillis = Calendar.getInstance().apply {
            set(Calendar.YEAR, initialYear)
            set(Calendar.MONTH, 0)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis,
        yearRange = 1920..Calendar.getInstance().get(Calendar.YEAR) - 13
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = millis
                        val day   = cal.get(Calendar.DAY_OF_MONTH)
                        val month = cal.get(Calendar.MONTH) + 1
                        val year  = cal.get(Calendar.YEAR)
                        displayDate = "%02d.%02d.%04d".format(day, month, year)
                        // API erwartet JJJJ-MM-TT
                        onBirthdateChange("%04d-%02d-%02d".format(year, month, day))
                        isError = false
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.general_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.general_cancel)) }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = { Text(stringResource(R.string.age_verify_date_picker_title), modifier = Modifier.padding(16.dp)) },
                headline = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = millis }
                        val day   = cal.get(Calendar.DAY_OF_MONTH)
                        val month = cal.get(Calendar.MONTH) + 1
                        val year  = cal.get(Calendar.YEAR)
                        Text(
                            "%02d.%02d.%04d".format(day, month, year),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    } else {
                        Text(
                            "TT.MM.JJJJ",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                showModeToggle = false
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.age_verify_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            Text(
                stringResource(R.string.age_verify_headline),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.age_verify_subtitle),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Klickbares Datum-Feld öffnet DatePicker
            OutlinedTextField(
                value = displayDate,
                onValueChange = {},
                label = { Text(stringResource(R.string.age_verify_birthdate_label)) },
                placeholder = { Text(stringResource(R.string.age_verify_birthdate_placeholder)) },
                readOnly = true,
                trailingIcon = {
                    Icon(
                        Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.age_verify_calendar_cd),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.age_verify_error_select_date)) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                singleLine = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = if (isError) MaterialTheme.colorScheme.error
                                         else MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                )
            )

            Button(
                onClick = {
                    if (birthdate.isBlank()) {
                        isError = true
                    } else {
                        onConfirm()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = birthdate.isNotBlank()
            ) {
                Text(stringResource(R.string.age_verify_button))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helper Composables
// ---------------------------------------------------------------------------

@Composable
private fun LivenessInstruction(text: String, emoji: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LivenessProgressDots(
    currentState: LivenessState,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        LivenessState.LOOK_STRAIGHT,
        LivenessState.TURN_LEFT,
        LivenessState.TURN_RIGHT,
        LivenessState.CAPTURING
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val done = steps.indexOf(currentState) > index
            val active = currentState == step
            Box(
                modifier = Modifier
                    .size(if (active) 14.dp else 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            done -> Color(0xFF66BB6A)
                            active -> MaterialTheme.colorScheme.primary
                            else -> Color.White.copy(alpha = 0.4f)
                        }
                    )
            )
        }
    }
}

/**
 * Konvertiert ein YUV_420_888 [android.media.Image] (CameraX ImageAnalysis) in ein
 * ARGB_8888-[android.graphics.Bitmap], damit der [com.securechat.app.facedetection.FaceDetectionProvider]
 * (Bitmap-basiertes Interface, flavor-unabhängig) darauf arbeiten kann.
 */
private fun mediaImageToBitmap(image: android.media.Image): android.graphics.Bitmap? = runCatching {
    val width = image.width
    val height = image.height
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    val yRowStride = yPlane.rowStride
    val uvRowStride = vPlane.rowStride
    val uvPixelStride = vPlane.pixelStride

    val nv21 = ByteArray(width * height * 3 / 2)
    val yBuffer = yPlane.buffer
    for (row in 0 until height) {
        yBuffer.position(row * yRowStride)
        yBuffer.get(nv21, row * width, width)
    }
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val uvBase = width * height
    for (row in 0 until height / 2) {
        for (col in 0 until width / 2) {
            val src = row * uvRowStride + col * uvPixelStride
            val dst = uvBase + row * width + col * 2
            vBuffer.position(src); nv21[dst] = vBuffer.get()
            uBuffer.position(src); nv21[dst + 1] = uBuffer.get()
        }
    }

    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
    android.graphics.BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}.getOrNull()
