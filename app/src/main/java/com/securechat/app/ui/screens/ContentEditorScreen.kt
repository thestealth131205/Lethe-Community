package com.securechat.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow

private val CATEGORIES = listOf(
    "Handwerklich", "Hobby", "Fotografie", "Beauty", "Musik",
    "Gaming", "Lifestyle", "Fitness", "Kochen", "Movie", "Comedy", "Songs", "18+"
)

/**
 * ContentEditorScreen: WYSIWYG-Editor für Creator-Beiträge und Sparks.
 * Route: "content_editor" (neu) | "content_editor/{contentId}" (edit)
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentEditorScreen(
    viewModel: MainViewModel,
    navController: NavController,
    contentId: String? = null,
    initialTab: Int = 0
) {
    val isEdit = contentId != null
    val existingContent by viewModel.contentEditorContent.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val creatorProfile by viewModel.creatorProfile.collectAsState()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val activity = LocalContext.current as? Activity

    // Tabs: 0 = Beitrag, 1 = Spark
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    // Gemeinsame Felder
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES[0]) }
    var styxCost by remember { mutableStateOf("0") }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var currentHtml by remember { mutableStateOf("") }

    // Spark-spezifisch
    var sparkVideoUrl by remember { mutableStateOf<String?>(null) }
    var sparkPreviewImageUrl by remember { mutableStateOf<String?>(null) }
    var sparkCategory by remember { mutableStateOf(CATEGORIES[0]) }
    var sparkCategoryExpanded by remember { mutableStateOf(false) }
    // Nach einem erfolgreichen Upload enthält uploadedSparkId die Content-ID des neuen Sparks.
    // doSave() nutzt sie, um einen PUT (Update) statt doppeltem POST zu machen.
    var uploadedSparkId by remember { mutableStateOf<String?>(null) }

    // Spark Upload Fortschritt aus dem ViewModel beobachten
    val sparkUploadProgress by viewModel.sparkUploadProgress.collectAsState()
    val sparkThumbnailUri by viewModel.sparkThumbnailUri.collectAsState()

    // UI-State
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var isUploadingMedia by remember { mutableStateOf(false) }

    // WebView-Referenz für HTML-Extraktion
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pendingHtmlForWebView by remember { mutableStateOf<String?>(null) }

    // Beim Edit-Mode: vorhandenen Content laden
    LaunchedEffect(contentId) {
        if (isEdit) {
            viewModel.loadContentForEditing(contentId!!)
        }
    }
    // Vorhandenen Content in Felder übertragen (nur beim ersten Laden)
    var alreadyApplied by remember { mutableStateOf(false) }
    LaunchedEffect(existingContent) {
        if (isEdit && existingContent != null && !alreadyApplied) {
            alreadyApplied = true
            val c = existingContent!!
            title = c.title ?: ""
            description = c.description ?: ""
            category = c.category ?: CATEGORIES[0]
            styxCost = c.styxCost.toString()
            previewImageUrl = c.previewImageUrl
            currentHtml = c.contentHtml ?: ""
            if (c.mediaType == "spark") {
                selectedTab = 1
                sparkVideoUrl = c.mediaUrl
                sparkPreviewImageUrl = c.previewImageUrl
                sparkCategory = c.category ?: CATEGORIES[0]
            } else {
                // HTML in WebView laden sobald WebView bereit
                pendingHtmlForWebView = c.contentHtml ?: ""
            }
        }
    }
    // softInputMode auf adjustResize setzen damit Keyboard die WebView nicht überdeckt
    @Suppress("DEPRECATION")
    DisposableEffect(Unit) {
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
    }

    // Sobald WebView bereit und pending HTML vorhanden → laden
    LaunchedEffect(webViewRef, pendingHtmlForWebView) {
        val wv = webViewRef
        val html = pendingHtmlForWebView
        if (wv != null && html != null) {
            val escaped = html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
            wv.evaluateJavascript("setHTML(\"$escaped\");", null)
            pendingHtmlForWebView = null
        }
    }

    // Image Picker für Editor-Bilder
    var pendingImageTarget by remember { mutableStateOf<String>("preview") } // "preview" oder "editor"
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploadingMedia = true
            viewModel.uploadContentImage(it) { url ->
                isUploadingMedia = false
                if (url != null) {
                    if (pendingImageTarget == "editor") {
                        val escaped = url.replace("\"", "\\\"")
                        webViewRef?.evaluateJavascript("insertImage(\"$escaped\");", null)
                    } else {
                        previewImageUrl = url
                    }
                }
            }
        }
    }

    // Video Picker für den WYSIWYG-Content-Editor (Beitrag-Tab)
    val contentVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploadingMedia = true
            viewModel.uploadContentVideo(it) { url ->
                isUploadingMedia = false
                if (url != null) {
                    val escaped = url.replace("\"", "\\\"")
                    webViewRef?.evaluateJavascript("insertVideo(\"$escaped\");", null)
                }
            }
        }
    }

    // Video Picker für Spark-Tab — navigiert zum SparkAddEditorScreen (Filter + Musik + Processing)
    val sparkVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val encodedUri = android.net.Uri.encode(it.toString())
            navController.navigate("spark_editor?videoUri=$encodedUri")
        }
    }

    // Verarbeitetes Spark-Video vom SparkAddEditorScreen empfangen (via SavedStateHandle).
    val backStackEntry = navController.currentBackStackEntry
    val processedSparkUri by remember(backStackEntry) {
        backStackEntry?.savedStateHandle?.getStateFlow("processed_spark_uri", null as String?)
            ?: MutableStateFlow(null)
    }.collectAsState()
    val processedSparkTitle by remember(backStackEntry) {
        backStackEntry?.savedStateHandle?.getStateFlow("processed_spark_title", null as String?)
            ?: MutableStateFlow(null)
    }.collectAsState()
    val processedSparkDesc by remember(backStackEntry) {
        backStackEntry?.savedStateHandle?.getStateFlow("processed_spark_description", null as String?)
            ?: MutableStateFlow(null)
    }.collectAsState()

    LaunchedEffect(processedSparkUri) {
        processedSparkUri?.let { uriString ->
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("processed_spark_uri")
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("processed_spark_title")
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("processed_spark_description")
            // Titel und Beschreibung aus dem Spark-Editor übernehmen (wenn Felder noch leer)
            if (title.isBlank() && !processedSparkTitle.isNullOrBlank()) {
                title = processedSparkTitle!!
            }
            if (description.isBlank() && !processedSparkDesc.isNullOrBlank()) {
                description = processedSparkDesc!!
            }
            val uri = Uri.parse(uriString)
            isUploadingMedia = true
            viewModel.uploadSparkHls(uri, title, sparkCategory, description) { spark, _ ->
                isUploadingMedia = false
                if (spark != null) {
                    sparkVideoUrl = spark.mediaUrl
                    sparkPreviewImageUrl = spark.previewImageUrl
                    uploadedSparkId = spark.id
                }
            }
        }
    }

    val creatorId = creatorProfile?.id ?: existingContent?.creatorId ?: ""
    val slug = title.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .replace(Regex("\\s+"), "-")
        .take(200)
    val directLink = "https://letheapp.de/post?c=$creatorId&s=$slug"
    val embedLink = "https://letheapp.de/post/embed?c=$creatorId&s=$slug"

    fun doSave() {
        isSaving = true
        saveError = null
        val cost = styxCost.toIntOrNull() ?: 0
        if (selectedTab == 1) {
            // Spark speichern.
            // uploadedSparkId ist gesetzt wenn gerade ein neuer Spark hochgeladen wurde →
            // in dem Fall ist der DB-Eintrag schon vorhanden (vom Upload-Endpoint erstellt),
            // also machen wir ein PUT (isEdit=true) um Titel / Kategorie zu aktualisieren.
            val effectiveIsEdit = uploadedSparkId != null || isEdit
            val effectiveContentId = uploadedSparkId ?: contentId
            viewModel.saveCreatorContent(
                title = title, category = sparkCategory, description = description,
                contentHtml = null, styxCost = 0,
                previewImageUrl = sparkPreviewImageUrl, mediaUrl = sparkVideoUrl, mediaType = "spark",
                isEdit = effectiveIsEdit, contentId = effectiveContentId
            ) { success, error ->
                isSaving = false
                if (success) navController.popBackStack() else saveError = error
            }
        } else {
            // HTML aus WebView extrahieren dann speichern
            webViewRef?.evaluateJavascript("getHTML();") { htmlResult ->
                val cleanHtml = run {
                    var s = htmlResult
                        ?.trim('"')
                        ?.replace("\\\"", "\"")
                        ?.replace("\\n", "\n")
                        ?.replace("\\/", "/")
                        ?: currentHtml
                    // JSON-Unicode-Escapes dekodieren (\u003C → <, \u003E → >, usw.)
                    val unicodeRegex = Regex("\\\\u([0-9a-fA-F]{4})")
                    s = unicodeRegex.replace(s) { match ->
                        match.groupValues[1].toInt(16).toChar().toString()
                    }
                    s
                }
                viewModel.saveCreatorContent(
                    title = title, category = category, description = description,
                    contentHtml = cleanHtml, styxCost = cost,
                    previewImageUrl = previewImageUrl, mediaUrl = null, mediaType = "image",
                    isEdit = isEdit, contentId = contentId
                ) { success, error ->
                    isSaving = false
                    if (success) navController.popBackStack() else saveError = error
                }
            } ?: run {
                isSaving = false; saveError = "WebView nicht verfügbar"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when {
                    isEdit && selectedTab == 1 -> stringResource(R.string.content_editor_edit_spark)
                    isEdit -> stringResource(R.string.content_editor_edit_post)
                    selectedTab == 1 -> stringResource(R.string.content_editor_new_spark)
                    else -> stringResource(R.string.content_editor_new_post)
                }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_editor_back_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        // Box erlaubt das Überlagern des schwebenden UploadProgressIndicators
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { if (!isEdit) selectedTab = 0 },
                    text = { Text(stringResource(R.string.content_editor_tab_post)) },
                    enabled = !isEdit || selectedTab == 0
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { if (!isEdit) selectedTab = 1 },
                    text = { Text(stringResource(R.string.content_editor_tab_spark)) },
                    enabled = !isEdit || selectedTab == 1
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Gemeinsame Felder (nur Beitrag-Tab) ---
                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text(stringResource(R.string.content_editor_title_label)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = description, onValueChange = { description = it },
                        label = { Text(stringResource(R.string.content_editor_description_label)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 10
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // === BEITRAG-FELDER ===
                        // Kategorie
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = category, onValueChange = {}, readOnly = true,
                                label = { Text(stringResource(R.string.content_editor_category_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                CATEGORIES.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = { category = cat; categoryExpanded = false }
                                    )
                                }
                            }
                        }

                        // WYSIWYG-Editor
                        Text(stringResource(R.string.content_editor_content_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        // Editor-Toolbar (Bild/Video einfügen via Compose)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    pendingImageTarget = "editor"
                                    imageLauncher.launch("image/*")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.content_editor_insert_image)) }
                            OutlinedButton(
                                onClick = { contentVideoLauncher.launch("video/*") },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.content_editor_insert_video)) }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 320.dp, max = 600.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F1E35))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).also { wv ->
                                        wv.settings.javaScriptEnabled = true
                                        wv.settings.domStorageEnabled = true
                                        wv.isNestedScrollingEnabled = false
                                        wv.addJavascriptInterface(
                                            object {
                                                @JavascriptInterface
                                                fun onContentChanged(html: String) { currentHtml = html }
                                                @JavascriptInterface
                                                fun pickImage() { /* unused – Compose handles this */ }
                                                @JavascriptInterface
                                                fun pickVideo() { /* unused – Compose handles this */ }
                                            },
                                            "Android"
                                        )
                                        wv.webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView, request: WebResourceRequest
                                            ) = false
                                        }
                                        wv.isFocusableInTouchMode = true
                                        wv.isFocusable = true
                                        wv.setOnTouchListener { v, event ->
                                            // Compose-Scroll darf bei jeder Geste in der WebView nicht intervenieren
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                            false // false = WebView verarbeitet Event selbst normal weiter
                                        }
                                        wv.loadUrl("file:///android_asset/editor.html")
                                        webViewRef = wv
                                    }
                                },
                                update = { wv -> webViewRef = wv },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Vorschaubild
                        Text(stringResource(R.string.content_editor_preview_image_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        if (previewImageUrl != null) {
                            AsyncImage(
                                model = previewImageUrl, contentDescription = stringResource(R.string.content_editor_preview_image_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                        OutlinedButton(
                            onClick = { pendingImageTarget = "preview"; imageLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (previewImageUrl != null) stringResource(R.string.content_editor_change_preview) else stringResource(R.string.content_editor_select_preview))
                        }

                        // Links (nur wenn Titel vorhanden)
                        if (title.isNotBlank()) {
                            HorizontalDivider()
                            Text(stringResource(R.string.content_editor_links_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            EditorLinkRow(stringResource(R.string.content_editor_direct_link), directLink) { clipboardManager.setText(AnnotatedString(it)) }
                            EditorLinkRow(stringResource(R.string.content_editor_embed_link), embedLink) { clipboardManager.setText(AnnotatedString(it)) }
                            HorizontalDivider()
                        }
                    }
                    1 -> {
                        // === SPARK-FELDER ===
                        // Kategorie für Spark
                        ExposedDropdownMenuBox(
                            expanded = sparkCategoryExpanded,
                            onExpandedChange = { sparkCategoryExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = sparkCategory, onValueChange = {}, readOnly = true,
                                label = { Text(stringResource(R.string.content_editor_category_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sparkCategoryExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = sparkCategoryExpanded,
                                onDismissRequest = { sparkCategoryExpanded = false }
                            ) {
                                CATEGORIES.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = { sparkCategory = cat; sparkCategoryExpanded = false }
                                    )
                                }
                            }
                        }

                        Text(stringResource(R.string.content_editor_video_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        if (sparkVideoUrl != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1A2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.content_editor_video_uploaded), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { sparkVideoLauncher.launch("video/*") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = sparkUploadProgress < 0f // während Upload deaktiviert
                        ) {
                            Text(if (sparkVideoUrl != null) stringResource(R.string.content_editor_change_video) else stringResource(R.string.content_editor_select_video))
                        }
                    }
                }

                // Preis (nur bei Beitrag-Tab)
                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = styxCost, onValueChange = { styxCost = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.content_editor_price_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }

                // Beim Beitrag-Tab: einfache Upload-Anzeige (kein HLS)
                if (isUploadingMedia && selectedTab == 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.content_editor_uploading), style = MaterialTheme.typography.bodySmall)
                }
                saveError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { doSave() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && !isUploadingMedia && title.isNotBlank()
                            && sparkUploadProgress < 0f
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (selectedTab == 1) stringResource(R.string.content_editor_save_spark) else stringResource(R.string.content_editor_save_post))
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // Schwebender Fortschritts-Indikator (nur für Spark-HLS-Uploads)
        UploadProgressIndicator(
            progress = sparkUploadProgress,
            thumbnailUri = sparkThumbnailUri,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        } // end outer Box
    }
}

@Composable
private fun EditorLinkRow(label: String, link: String, onCopy: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(link, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        TextButton(onClick = { onCopy(link) }) { Text(stringResource(R.string.content_editor_copy)) }
    }
}

/**
 * Schwebender kreisförmiger Upload-Fortschrittsindikator für Spark-HLS-Uploads.
 *
 * Zeigt sich nur wenn [progress] >= 0f.
 * Design:
 *   – Äußerer Kreis: [CircularProgressIndicator] in der primären Theme-Farbe.
 *   – Inneres Bild: Thumbnail des Sparks (sofern bereits verfügbar) oder Platzhalter.
 *   – Bei 100 % blendet sich das Element animiert (fade + scale) aus.
 *
 * Platzierung: Wird vom Aufrufer per [modifier] auf [Alignment.TopStart] gesetzt.
 */
@Composable
fun UploadProgressIndicator(
    progress: Float,
    thumbnailUri: Uri?,
    modifier: Modifier = Modifier
) {
    val isVisible = progress >= 0f
    val smoothProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400),
        label = "sparkUploadSmoothProgress"
    )

    AnimatedVisibility(
        visible = isVisible,
        exit = fadeOut(animationSpec = tween(600)) +
                scaleOut(targetScale = 0.6f, animationSpec = tween(600)),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            // Hintergrund-Kreis (leicht transluzent)
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            )

            // Kreisförmiger Fortschrittsring (primary-Farbe, strokeWidth 5 dp)
            CircularProgressIndicator(
                progress = { smoothProgress },
                modifier = Modifier.size(80.dp),
                strokeWidth = 5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            )

            // Thumbnail (62 dp Durchmesser, innerhalb des Rings)
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A2E)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUri != null) {
                    AsyncImage(
                        model = thumbnailUri,
                        contentDescription = stringResource(R.string.content_editor_spark_preview_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                    )
                } else {
                    // Platzhalter während Transkodierung läuft
                    Text(
                        text = "${(smoothProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
