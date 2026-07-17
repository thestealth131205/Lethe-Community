package com.securechat.app.ui.screens

import android.app.Activity
import android.net.Uri
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import timber.log.Timber
import com.securechat.app.data.local.UserEntity
import com.securechat.app.data.network.CreatorContentResponse
import com.securechat.app.data.network.NearbyProfileResponse
import com.securechat.app.data.network.SparkCommentResponse
import com.securechat.app.data.network.SparkInteractionRequest
import com.securechat.app.data.network.VipSearchResponse
import com.securechat.app.ui.MainViewModel
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.cast.SafeMediaRouteChooserDialogFragment
import com.securechat.app.cast.SparkCastChannel
import com.securechat.app.cast.sendSparkMessage
import kotlinx.coroutines.delay
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SparksFeedScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToLiveRoom: (creatorId: String) -> Unit = {},
    onNavigateToContent: (contentId: String) -> Unit = {},
    onNavigateToSparkEditor: (encodedUri: String) -> Unit = {},
    onNavigateToSparkEditorMulti: (encodedUris: String) -> Unit = {},
    onNavigateToSoundScreen: (sparkId: String) -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSparksProfile: (creatorId: String) -> Unit = {},
    fontSizeMultiplier: Float = 1.0f
) {
    val vipFeed by viewModel.vipFeed.collectAsState()
    val feedOverride by viewModel.sparkFeedOverride.collectAsState()
    val sparks = feedOverride ?: vipFeed
    val currentUser by viewModel.currentUser.collectAsState()
    val searchResults by viewModel.vipSearchResults.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    var showSearchOverlay by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isUploadingSpark by remember { mutableStateOf(false) }
    val isCreator = currentUser?.isCreator == true || currentUser?.isAdmin == true

    // Multi-Image-Picker für den + Button (bis zu 10 Bilder für Slideshow-Spark)
    val sparkMultiImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1 && context.contentResolver.getType(uris[0])?.startsWith("video/") == true) {
                // Einzelnes Video → normaler Editor
                val encodedUri = android.net.Uri.encode(uris[0].toString())
                onNavigateToSparkEditor(encodedUri)
            } else {
                // Ein oder mehrere Bilder → Multi-Image-Editor
                val encoded = uris.joinToString("|") { android.net.Uri.encode(it.toString()) }
                onNavigateToSparkEditorMulti(encoded)
            }
        }
    }

    // Einzelner Video-Picker (Fallback, falls PickMultipleVisualMedia kein Video liefert)
    val sparkMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val encodedUri = android.net.Uri.encode(it.toString())
            onNavigateToSparkEditor(encodedUri)
        }
    }

    // Ausstehender Sound: sobald pendingSoundData gesetzt ist, Media-Picker automatisch öffnen
    val pendingSoundData by viewModel.pendingSoundData.collectAsState()
    var soundPickerLaunched by remember { mutableStateOf(false) }
    LaunchedEffect(pendingSoundData) {
        if (pendingSoundData != null && !soundPickerLaunched) {
            soundPickerLaunched = true
            sparkMultiImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
        if (pendingSoundData == null) soundPickerLaunched = false
    }

    // Feed-Override und synthetische URL-Sparks beim Verlassen zurücksetzen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setSparkFeedOverride(null)
            viewModel.clearSyntheticSparks()
        }
    }

    // Immersive fullscreen: Systemleisten ausblenden
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Portraitmodus sperren beim Betreten des Spark Feeds
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = prev ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Audio-Fokus beim Betreten des Spark-Feeds anfordern → pausiert Musik anderer Apps.
    // Delegiert an den zentralen AudioFocusManager in MainActivity.
    DisposableEffect(Unit) {
        val afm = (context as? com.securechat.app.MainActivity)?.audioFocusManager
        afm?.requestFocus()
        onDispose { afm?.abandonFocus() }
    }

    // Cast-Status vom zentralen CastDiscoveryManager
    val castAvailable by viewModel.castDiscoveryManager.castAvailable.collectAsState()
    val castConnected by viewModel.castDiscoveryManager.isCasting.collectAsState()
    var showCastStatusDialog by remember { mutableStateOf(false) }

    // Callback: Session-Start fehlgeschlagen
    DisposableEffect(Unit) {
        viewModel.castDiscoveryManager.onCastSessionStartFailed = {
            Toast.makeText(
                context,
                "Cast-Verbindung fehlgeschlagen. Bitte versuche es erneut.",
                Toast.LENGTH_LONG
            ).show()
        }
        onDispose { viewModel.castDiscoveryManager.onCastSessionStartFailed = null }
    }

    // TV-Fernbedienung: Hoch/Runter → nächsten/vorherigen Spark anzeigen
    var castNavDirection by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        viewModel.castDiscoveryManager.onSparkNavMessage = { dir -> castNavDirection = dir }
        onDispose { viewModel.castDiscoveryManager.onSparkNavMessage = null }
    }

    // Feed bei jedem Aufrufen des Screens (auch beim Zurückkehren) neu laden.
    val sparkFeedLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(sparkFeedLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadVipFeed(type = "spark")
            }
        }
        sparkFeedLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { sparkFeedLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Cast-Status-Dialog ────────────────────────────────────────────────────
    if (showCastStatusDialog) {
        val castIsPlaying by viewModel.castDiscoveryManager.castIsPlaying.collectAsState()
        val castCurrentMs by viewModel.castDiscoveryManager.castCurrentMs.collectAsState()
        val castTotalMs   by viewModel.castDiscoveryManager.castTotalMs.collectAsState()
        fun fmtMs(ms: Long): String {
            val sec = (ms / 1000L).toInt()
            return "%d:%02d".format(sec / 60, sec % 60)
        }
        AlertDialog(
            onDismissRequest = { showCastStatusDialog = false },
            icon = {
                Icon(
                    Icons.Default.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Stream aktiv", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (castIsPlaying) "▶  Wird gestreamt" else "⏸  Gestreamt · Pause",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (castTotalMs > 0L) {
                        Text(
                            "${fmtMs(castCurrentMs)} / ${fmtMs(castTotalMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCastStatusDialog = false }) {
                    Text("Schließen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCastStatusDialog = false
                        viewModel.castDiscoveryManager.stopCasting()
                    }
                ) {
                    Text(
                        "Stream beenden",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val pagerState = rememberPagerState(pageCount = { sparks.size })

        if (sparks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.sparks_feed_no_sparks), color = Color.White)
            }
        } else {

            // Engagement-Tracking: Sparks mit denen der User sich beschäftigt hat (≥50 % Video / Bild-Swipe)
            val engagedSparkIds = remember { mutableStateOf(setOf<String>()) }
            var prevPage by remember { androidx.compose.runtime.mutableIntStateOf(0) }

            // Seiten-Wechsel-Detection: bei Engagement ähnliche Sparks einfügen
            LaunchedEffect(pagerState.currentPage) {
                val newPage = pagerState.currentPage
                val oldPage = prevPage
                if (newPage != oldPage) {
                    val oldSpark = sparks.getOrNull(oldPage)
                    if (oldSpark != null && oldSpark.id in engagedSparkIds.value) {
                        viewModel.loadSimilarSparksAfterPage(newPage, oldSpark.id)
                    }
                    prevPage = newPage
                }
            }

            // Zum angeforderter Spark scrollen (z.B. Deep-Link)
            val pendingSparkId by viewModel.pendingSparkId.collectAsState()
            LaunchedEffect(pendingSparkId, sparks) {
                val targetId = pendingSparkId ?: return@LaunchedEffect
                val idx = sparks.indexOfFirst { it.id == targetId }
                if (idx >= 0) {
                    pagerState.scrollToPage(idx)
                    viewModel.clearPendingSparkId()
                }
            }

            // Nach prependSparkToFeed (Tap auf gespeichertes/geliktes Video) zu Seite 0 scrollen.
            // Zweiter Key sparks.isNotEmpty(): falls Feed beim Flag-Setzen noch leer war, erneut
            // prüfen sobald Daten eintreffen.
            val feedScrollToTop by viewModel.feedScrollToTop.collectAsState()
            LaunchedEffect(feedScrollToTop, sparks.isNotEmpty()) {
                if (feedScrollToTop && sparks.isNotEmpty()) {
                    pagerState.scrollToPage(0)
                    viewModel.consumeFeedScrollToTop()
                }
            }

            LaunchedEffect(pagerState.currentPage, sparks.size) {
                if (sparks.size > 0 && pagerState.currentPage >= sparks.size - 3) {
                    viewModel.loadMoreSparks()
                }
            }

            // TV-Fernbedienung Navigation: castNavDirection → Pager scrollen
            LaunchedEffect(castNavDirection) {
                val dir = castNavDirection ?: return@LaunchedEffect
                castNavDirection = null
                val target = when (dir) {
                    "up"   -> (pagerState.currentPage - 1).coerceAtLeast(0)
                    "down" -> (pagerState.currentPage + 1).coerceAtMost(sparks.size - 1)
                    else   -> return@LaunchedEffect
                }
                if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
            }
            // Cast: Aktuellen Spark laden – getrennt von loadMoreSparks, damit
            // kein Cast-Reset bei jedem Nachladen weiterer Seiten ausgelöst wird.
            LaunchedEffect(pagerState.currentPage, castConnected) {
                if (castConnected) {
                    val currentSpark = sparks.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
                    val isImageSpark = currentSpark.mediaType == "image_spark"

                    // Bild-URLs absolut machen
                    val absoluteImageUrls = currentSpark.sparkImageUrls?.map {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }
                    // Musik-URL absolut machen (für Image-Sparks mit Hintergrundaudio oder Video-Sparks mit geborgtem Sound)
                    val absoluteMusicUrl = currentSpark.musicUrl?.let {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }

                    // Image-Sparks: KEIN CAF-Media laden — Bilder + Sound laufen über den
                    // Custom Channel (Receiver zeigt Bild-Overlay + <audio>-Element).
                    // Video-Sparks: HLS/MP4 als CAF-Media laden damit der Cast-Player das Video rendert.
                    if (!isImageSpark) {
                        val rawUrl = currentSpark.mediaUrl
                        val absoluteUrl = rawUrl?.let {
                            if (it.startsWith("http")) it else "https://letheapp.de$it"
                        }
                        // Einziger Loader fuer das Spark-Video: feuert erst nach der Recomposition
                        // (castConnected = true) bzw. bei jedem Seitenwechsel, wenn der Receiver
                        // bereit ist. Da loadPendingMedia() das Video nicht mehr vorab laedt, gibt es
                        // hier keinen doppelten Load – deshalb ohne castCurrentUrl-Sperre.
                        if (absoluteUrl != null) {
                            val metadata = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                                putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, currentSpark.title ?: "Spark")
                                putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, currentSpark.creatorName ?: currentSpark.creatorFakeNumber ?: "")
                            }
                            viewModel.castDiscoveryManager.loadUrlOnCast(absoluteUrl, metadata, currentSpark.isLive)
                        }
                    }

                    // Spark-Nachricht mit allen Daten an Receiver senden
                    // (Bild-Overlay, Hintergrundmusik, Kommentar-Polling, Info-Overlay)
                    val absoluteVideoUrl = if (!isImageSpark) currentSpark.mediaUrl?.let {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    } else null
                    viewModel.castDiscoveryManager.sendSparkMessage(
                        SparkCastChannel.buildSparkMessage(
                            sparkId = currentSpark.id,
                            videoUrl = absoluteVideoUrl,
                            title = currentSpark.title,
                            creatorName = currentSpark.creatorName ?: currentSpark.creatorFakeNumber,
                            imageUrls = absoluteImageUrls,
                            musicUrl = absoluteMusicUrl,
                            description = currentSpark.description
                        )
                    )
                }
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
                // Inhalts-bewusster Key: bindet jede Seite an ihren Spark (id + Position).
                // Ohne Key identifiziert Compose Seiten rein über den Index und recycelt den
                // Composition-Slot wenn sich der Feed umsortiert (loadVipFeed bei jedem ON_RESUME,
                // loadMoreSparks-Cycling mit Duplikaten). Dabei blieben die per remember{} gehaltenen
                // ExoPlayer (player/audioPlayer) erhalten und liefen mit altem Sound weiter, während
                // das neue Medium zusätzlich abgespielt wurde → doppelter/versetzter Ton. Mit dem Key
                // bekommt ein Slot mit gewechseltem Spark eine frische Composition (neuer Player),
                // alte Compositions werden disposed und ihre Player freigegeben.
                key = { page -> "${sparks.getOrNull(page)?.id ?: page}#$page" }
            ) { page ->
                val spark = sparks.getOrNull(page) ?: return@VerticalPager
                SparksPage(
                    spark = spark,
                    index = page,
                    currentPage = pagerState.currentPage,
                    currentUser = currentUser,
                    viewModel = viewModel,
                    castConnected = castConnected,
                    onBack = onNavigateBack,
                    onNavigateToLiveRoom = onNavigateToLiveRoom,
                    onNavigateToSparksProfile = onNavigateToSparksProfile,
                    onNavigateToSoundScreen = onNavigateToSoundScreen,
                    onNavigateToSparkEditor = onNavigateToSparkEditor,
                    onLike = { viewModel.likeContent(spark.id) },
                    onEngaged = {
                        engagedSparkIds.value = engagedSparkIds.value + spark.id
                    },
                    onToggleControls = { show -> showControls = show },
                    fontSizeMultiplier = fontSizeMultiplier
                )
            }
        }

        // Top overlay: Navigation + Actions (toggle mit showControls)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zurück-Pfeil (größer)
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.sparks_feed_back),
                        tint = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .padding(6.dp)
                    )
                }

                // Rechts: Cast + Suche
                Row {
                    // Cast-Button: nur sichtbar wenn Chromecast-Geräte im Netz verfügbar sind.
                    if (castAvailable) {
                        val castIconTint = if (castConnected) Color(0xFFA8A800) else Color.White
                        IconButton(onClick = {
                            if (castConnected) {
                                showCastStatusDialog = true
                            } else {
                                // pendingCast* setzen damit loadPendingMedia beim
                                // Session-Start sofort den aktuellen Spark lädt.
                                val currentSpark = sparks.getOrNull(pagerState.currentPage)
                                if (currentSpark != null) {
                                    // Bewusst KEIN pendingCastUrl setzen: das Video-CAF-Media wird
                                    // ausschliesslich im LaunchedEffect(currentPage, castConnected)
                                    // geladen – erst nach der Recomposition, wenn der Receiver bereit
                                    // ist. Ein frueher Load in loadPendingMedia (onSessionStarted) kann
                                    // beim noch initialisierenden Custom-Receiver im Dauer-Puffern
                                    // haengen bleiben ("laedt dauerhaft"). Nur EIN Loader = kein
                                    // doppelter Ton und zuverlaessige Wiedergabe.
                                    viewModel.castDiscoveryManager.pendingCastSparkId = currentSpark.id
                                    viewModel.castDiscoveryManager.pendingCastImageUrls = currentSpark.sparkImageUrls?.map {
                                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                                    }
                                    viewModel.castDiscoveryManager.pendingCastMusicUrl = currentSpark.musicUrl?.let {
                                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                                    }
                                }
                                (context as? androidx.fragment.app.FragmentActivity)?.let { fa ->
                                    val frag = SafeMediaRouteChooserDialogFragment()
                                    frag.routeSelector = viewModel.castDiscoveryManager.selector
                                    frag.show(fa.supportFragmentManager, "cast_chooser")
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Cast",
                                tint = castIconTint,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .padding(6.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showSearchOverlay = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.sparks_feed_search),
                            tint = Color.White,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }

        // Suchoverlay
        AnimatedVisibility(
            visible = showSearchOverlay,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            SparkSearchOverlay(
                searchResults = searchResults,
                viewModel = viewModel,
                onNavigateToContent = onNavigateToContent,
                onDismiss = { showSearchOverlay = false }
            )
        }

        // Creator-Bar: + Button (Mitte) + eigenes Profilbild (Rechts) ─────────
        // Werden zusammen mit den anderen Controls ein-/ausgeblendet.
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // "+" Button – Neuen Spark aus diesem Feed erstellen (für alle User)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA8A800))
                        .clickable {
                            sparkMultiImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.sparks_feed_create),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Eigenes Profil – führt zur eigenen SparksProfileScreen
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clickable { onNavigateToSparksProfile(currentUser?.userId ?: "") },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = stringResource(R.string.sparks_feed_my_profile),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = stringResource(R.string.sparks_feed_profile_label),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkSearchOverlay(
    searchResults: VipSearchResponse?,
    viewModel: MainViewModel,
    onNavigateToContent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // Suchzeile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.sparks_feed_search_placeholder), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFA8A800),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = Color(0xFFA8A800)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.searchVip(query) })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.sparks_feed_search_close), tint = Color.White)
                }
            }


            // Suchergebnisse
            searchResults?.let { results ->
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Sparks
                    if (results.sparks.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.sparks_feed_section_sparks),
                                color = Color(0xFFA8A800),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        items(results.sparks) { spark ->
                            SearchResultItem(
                                title = spark.title ?: "Spark",
                                subtitle = spark.creatorName ?: spark.creatorFakeNumber ?: "",
                                imageUrl = spark.previewImageUrl,
                                onClick = { onNavigateToContent(spark.id); onDismiss() }
                            )
                        }
                    }

                    if (results.sparks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.sparks_feed_no_results, query), color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SparksPage(
    spark: CreatorContentResponse,
    index: Int,
    currentPage: Int,
    currentUser: UserEntity?,
    viewModel: MainViewModel,
    castConnected: Boolean = false,
    onBack: () -> Unit,
    onNavigateToLiveRoom: (creatorId: String) -> Unit = {},
    onNavigateToSparksProfile: (creatorId: String) -> Unit = {},
    onNavigateToSoundScreen: (sparkId: String) -> Unit = {},
    onNavigateToSparkEditor: (encodedUri: String) -> Unit = {},
    onLike: () -> Unit = {},
    onEngaged: () -> Unit = {},
    onToggleControls: (Boolean) -> Unit = {},
    fontSizeMultiplier: Float = 1.0f
) {
    val isImageSpark = spark.mediaType == "image_spark"
    val isVideoWithOriginalAudio = !isImageSpark && spark.musicTitle.isNullOrBlank()
    // Video-Spark mit geborgtem Sound: musicUrl vorhanden + soundOriginSparkId gesetzt
    val hasExternalSound = !isImageSpark && !spark.musicUrl.isNullOrBlank() && !spark.soundOriginSparkId.isNullOrBlank()
    val context = LocalContext.current
    val strSubscribed = stringResource(R.string.sparks_feed_subscribed)

    // ExoPlayer-Instanz — remember ohne Schlüssel = einmalige Erstellung pro Composition-Instanz.
    // DefaultLoadControl: max 15s Puffer statt Standard 50s → reduziert Heap-Verbrauch ~9 MB pro Instanz.
    // Lautstärke sofort auf 0 setzen wenn externer Sound aktiv → kein kurzes Aufflackern
    // der HLS-Originaltonspur bevor der LaunchedEffect greift.
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5_000, 15_000, 2_000, 5_000)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                volume = if (hasExternalSound) 0f else 1f
            }
    }

    // Zweiter ExoPlayer für geborgten Sound (Video-Sparks mit soundOriginSparkId)
    val audioPlayer = remember {
        if (hasExternalSound) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                volume = 1f
            }
        } else null
    }
    // HLS-Player stumm schalten wenn externer Sound aktiv oder Cast läuft.
    // Stale audioPlayer (aus altem Spark bei Recomposition) explizit auf 0 setzen,
    // damit er nicht hörbar bleibt wenn hasExternalSound jetzt false ist.
    LaunchedEffect(hasExternalSound, castConnected) {
        val mute = hasExternalSound || castConnected
        player.volume = if (mute) 0f else 1f
        if (hasExternalSound) {
            audioPlayer?.let { it.volume = if (castConnected) 0f else 1f }
        } else {
            audioPlayer?.volume = 0f
        }
    }
    DisposableEffect(Unit) {
        onDispose { audioPlayer?.release() }
    }
    // Audio-Source für geborgten Sound laden.
    // audioPlayer immer zuerst stoppen (verhindert dass ein veralteter Player weiterläuft
    // wenn hasExternalSound sich durch Spark-Wechsel von true→false ändert).
    LaunchedEffect(spark.musicUrl, hasExternalSound) {
        val ap = audioPlayer ?: return@LaunchedEffect
        ap.stop()
        if (!hasExternalSound) return@LaunchedEffect
        val rawUrl = spark.musicUrl ?: return@LaunchedEffect
        val absoluteUrl = if (rawUrl.startsWith("http")) rawUrl else "https://letheapp.de$rawUrl"
        val dsFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val audioSource = ProgressiveMediaSource.Factory(dsFactory)
            .createMediaSource(MediaItem.fromUri(absoluteUrl))
        ap.stop()
        ap.setMediaSource(audioSource)
        ap.prepare()
        // EPIC 1 startet die Wiedergabe sobald die Seite sichtbar ist
    }

    // Wird true wenn die Activity pausiert (App gewechselt, Sperrbildschirm usw.).
    var isLifecyclePaused by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    isLifecyclePaused = true
                    player.pause()
                    audioPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isLifecyclePaused = false
                    // EPIC 1 kümmert sich um das Wiederaufnehmen basierend auf currentPage/isPlaying
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Wenn ExoPlayer einen Fehler meldet (z.B. HLS nicht erreichbar, falscher Pfad),
    // wird playerError auf true gesetzt → Fallback-Thumbnail statt schwarzem Screen.
    var playerError by remember(spark.mediaUrl) { mutableStateOf(false) }
    var videoIsLandscape by remember(spark.mediaUrl) { mutableStateOf(false) }
    // true solange ExoPlayer im STATE_BUFFERING ist → Ladeindikator anzeigen
    var isBuffering by remember(spark.mediaUrl) { mutableStateOf(true) }
    // Verhindert Ladekreis-Blitz bei HLS-Nachpufferung: Indikator nur bis zum ersten READY-State.
    var isInitialBuffering by remember(spark.mediaUrl) { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Player.Listener für Fehler-Logging, Fallback-Thumbnail und Buffering-Indikator.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Timber.tag("LETHE_SPARK").e(
                    "Player-Fehler bei URL='${spark.mediaUrl}': [${error.errorCode}] ${error.message}"
                )
                playerError = true
                isBuffering = false
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoIsLandscape = videoSize.width > videoSize.height
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    isInitialBuffering = false
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Bildschirm-Rotation für Vollbild-Modus.
    // Beim Verlassen von Vollbild PORTRAIT wiederherstellen (nicht UNSPECIFIED),
    // da der Parent SparksFeedScreen Portrait-Modus erzwingt.
    DisposableEffect(isFullscreen) {
        val activity = context as? Activity
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            if (isFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // System-Zurück im Vollbild: nur Portrait wiederherstellen, Feed nicht verlassen.
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    // System-Zurück im Normal-Modus: Feed verlassen und Bottom Bar wiederherstellen.
    BackHandler(enabled = !isFullscreen) {
        onBack()
    }

    // EPIC 4: Cleanup wenn Seite die Composition verlässt — RAM und Codec sofort freigeben.
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // 18+ FLAG_SECURE: Screenshot und Bildschirmaufnahme bei 18+ Inhalten sperren
    val is18plusContent = spark.is18plus
    DisposableEffect(is18plusContent) {
        val activity = context as? Activity
        if (is18plusContent) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (is18plusContent) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    // Media-Source einmalig laden wenn URL bekannt ist.
    // setMediaSource + prepare() sind asynchron — kein Main-Thread-Blocking.
    // Bei Live-Streams wird kein Player geladen — der Feed zeigt nur das Preview-Bild.
    // Bei image_sparks mit Musik wird die music_url als Audio-Only-Source geladen.
    LaunchedEffect(spark.mediaUrl, spark.musicUrl) {
        if (spark.isLive) return@LaunchedEffect
        if (isImageSpark) {
            // image_spark: Musik abspielen falls vorhanden
            val musicRawUrl = spark.musicUrl ?: return@LaunchedEffect
            val absoluteMusicUrl = if (musicRawUrl.startsWith("http")) musicRawUrl
                                   else "https://letheapp.de$musicRawUrl"
            val musicDataSource = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
            val musicSource = ProgressiveMediaSource.Factory(musicDataSource)
                .createMediaSource(MediaItem.fromUri(absoluteMusicUrl))
            player.stop()
            player.setMediaSource(musicSource)
            player.prepare()
            if (currentPage == index && !isLifecyclePaused) {
                player.play()
            }
            return@LaunchedEffect
        }
        val rawUrl = spark.mediaUrl ?: return@LaunchedEffect
        val absoluteUrl = if (rawUrl.startsWith("http")) rawUrl else "https://letheapp.de$rawUrl"
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(absoluteUrl))
        player.stop()
        player.setMediaSource(hlsMediaSource)
        player.prepare()
        // Sofort play() setzen wenn diese Seite gerade sichtbar ist.
        // Verhindert das "ein Frame dann Einfrieren": EPIC 1 hat eine 300ms-Verzögerung
        // die durch Lifecycle-Events während der Navigation neu gestartet werden kann,
        // wodurch player.play() nie aufgerufen wird. Durch den direkten Aufruf hier
        // startet die Wiedergabe zuverlässig sobald genug gepuffert ist.
        // EPIC 1 übernimmt danach die weitere Play/Pause-Steuerung (Seitenwechsel, Pause-Tap).
        if (currentPage == index && !isLifecyclePaused) {
            player.play()
        }
    }

    // User-seitiger Play/Pause-State — startet als true für Autoplay.
    var isPlaying by remember { mutableStateOf(true) }

    // ── EPIC 1: Striktes Playback-Management — Der 24-Sekunden-Fix ───────────────────────
    // Minimale Verzögerung vor player.play(): gibt dem Playlist-State Zeit sich zu setzen,
    // bevor das Video geladen wird (z.B. nach prependSparkToFeed beim Tap auf gespeicherte Sparks).
    // OOM-Fix: player.stop() nach 500ms wenn Seite nicht sichtbar → gibt MediaCodec-Ressourcen frei.
    // player.prepare() wenn Player in STATE_IDLE und Seite wieder sichtbar wird.
    // Hilfsfunktion: RemoteMediaClient holen wenn Cast verbunden
    fun getCastRemoteClient(): com.google.android.gms.cast.framework.media.RemoteMediaClient? {
        if (!castConnected) return null
        return try {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
                .sessionManager?.currentCastSession?.remoteMediaClient
        } catch (_: Exception) { null }
    }

    LaunchedEffect(currentPage, index, isPlaying, isLifecyclePaused) {
        val isVisible = currentPage == index
        if (isVisible && isPlaying && !isLifecyclePaused) {
            delay(300)
            if (player.mediaItemCount > 0) {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                player.play()
            }
            if (hasExternalSound) {
                audioPlayer?.let { ap ->
                    if (ap.playbackState == Player.STATE_IDLE) ap.prepare()
                    ap.play()
                }
            }
            // Cast: Play synchronisieren
            getCastRemoteClient()?.play()
        } else {
            if (player.mediaItemCount > 0) player.pause()
            if (hasExternalSound) audioPlayer?.pause()
            // Cast: Pause synchronisieren
            if (isVisible) getCastRemoteClient()?.pause()
            if (!isVisible) {
                delay(500)
                // Nur stoppen wenn Seite immer noch nicht sichtbar (kein schnelles Hin-und-Her)
                if (currentPage != index) {
                    if (player.mediaItemCount > 0) player.stop()
                    if (hasExternalSound) audioPlayer?.stop()
                }
            }
        }
    }

    var showOverlay by remember { mutableStateOf(true) }
    var showComments by remember { mutableStateOf(false) }
    // Video-Zeitstrahl: aktuelle Position und Gesamtdauer in Millisekunden
    var videoPositionMs by remember { mutableStateOf(0L) }
    var videoDurationMs by remember { mutableStateOf(0L) }
    var isSeekingVideo by remember { mutableStateOf(false) }
    // Periodisches Polling der Playerposition für den Zeitstrahl
    LaunchedEffect(currentPage == index, isImageSpark) {
        if (isImageSpark) return@LaunchedEffect
        while (true) {
            delay(250)
            if (!isSeekingVideo) {
                val dur = player.duration.takeIf { it > 0 } ?: 0L
                val pos = player.currentPosition.coerceIn(0L, dur)
                videoDurationMs = dur
                videoPositionMs = pos
            }
        }
    }
    var showGiftDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showEditSparkDialog by remember { mutableStateOf(false) }
    // Like-State aus dem Feed (Server-seitig), lokal überschreibbar für optimistisches Update
    var isLiked by remember(spark.id) { mutableStateOf(spark.isLikedByMe) }
    var likesCount by remember(spark.id) { mutableStateOf(spark.likes) }
    var isFollowing by remember { mutableStateOf(false) }
    var isSaved by remember(spark.id) { mutableStateOf(spark.isSavedByMe) }

    // Pinch-to-Zoom für Videos (nicht für Livestreams).
    // transformable + canPan: Wischgesten bei Scale=1f nicht konsumieren → VerticalPager scrollt weiter.
    var videoScale by remember { mutableStateOf(1f) }
    var videoOffset by remember { mutableStateOf(Offset.Zero) }
    val videoTransformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        videoScale = (videoScale * zoomChange).coerceIn(1f, 4f)
        if (videoScale > 1f) videoOffset += offsetChange else videoOffset = Offset.Zero
    }
    LaunchedEffect(currentPage == index) {
        if (currentPage != index) {
            videoScale = 1f
            videoOffset = Offset.Zero
        }
    }

    // Lethe Algorithmus v1: Zeitmessung für Spark-Interaktionen
    val scope = rememberCoroutineScope()
    val viewStartMs = remember { mutableLongStateOf(0L) }
    var loopEventSent by remember(spark.id) { mutableStateOf(false) }

    // Abonnement-Status vom Server laden wenn Creator-ID bekannt
    LaunchedEffect(spark.creatorId) {
        if (spark.creatorId.isNotBlank()) {
            viewModel.checkSubscription(spark.creatorId) { subscribed ->
                isFollowing = subscribed
            }
        }
    }

    // Wenn diese Seite sichtbar wird: Startzeit merken
    LaunchedEffect(currentPage == index) {
        if (currentPage == index) {
            viewStartMs.longValue = System.currentTimeMillis()
            loopEventSent = false
        }
    }

    // VIEW beim ersten Abspielen senden (nicht erst beim Verlassen der Seite)
    var viewCounted by remember(spark.id) { mutableStateOf(false) }
    DisposableEffect(player) {
        val playListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !viewCounted && !spark.isLive && spark.mediaUrl != null) {
                    viewCounted = true
                    val elapsed = (System.currentTimeMillis() - viewStartMs.longValue) / 1000f
                    scope.launch {
                        try {
                            viewModel.trackSparkInteraction(spark.id, "VIEW", elapsed)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        player.addListener(playListener)
        onDispose { player.removeListener(playListener) }
    }

    // Beim Verlassen der Seite: SKIP senden wenn noch kein VIEW gezählt wurde
    DisposableEffect(spark.id) {
        onDispose {
            if (!spark.isLive && spark.mediaUrl != null && !viewCounted) {
                val elapsed = (System.currentTimeMillis() - viewStartMs.longValue) / 1000f
                scope.launch {
                    try {
                        viewModel.trackSparkInteraction(spark.id, "SKIP", elapsed)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // LOOP-Detection: wenn Player > 80% der Dauer abgespielt hat → LOOP senden
    DisposableEffect(player) {
        val loopListener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && !loopEventSent) {
                    loopEventSent = true
                    scope.launch {
                        try {
                            val elapsed = (System.currentTimeMillis() - viewStartMs.longValue) / 1000f
                            viewModel.trackSparkInteraction(spark.id, "LOOP", elapsed)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        player.addListener(loopListener)
        onDispose { player.removeListener(loopListener) }
    }

    // Engagement-Detection für Videos: ≥50 % der Laufzeit → onEngaged() auslösen
    var videoEngaged by remember(spark.id) { mutableStateOf(false) }
    LaunchedEffect(currentPage == index, isImageSpark) {
        if (currentPage != index || isImageSpark || spark.mediaUrl == null || spark.isLive) return@LaunchedEffect
        while (true) {
            delay(800)
            val dur = player.duration
            val pos = player.currentPosition
            if (dur > 0 && pos.toFloat() / dur >= 0.5f && !videoEngaged) {
                videoEngaged = true
                onEngaged()
                break
            }
        }
    }

    LaunchedEffect(showOverlay) {
        onToggleControls(showOverlay)
        if (showOverlay) {
            delay(5000)
            showOverlay = false
            onToggleControls(false)
        }
    }

    // Outer Box: nur Hintergrund, KEIN pointerInput.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (spark.isLive) {
            // Live-Stream im Feed: Preview-Bild anzeigen, Tap → LiveRoomScreen.
            // Der ExoPlayer wird nicht gestartet — HLS-Segmente sind ggf. noch nicht bereit.
            AsyncImage(
                model = spark.previewImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onNavigateToLiveRoom(spark.creatorId) }
            )
        } else if (isImageSpark) {
            // Bild-Slideshow-Spark: HorizontalPager mit Dots.
            // Fallback: wenn sparkImageUrls leer/null ist (z.B. bei älteren Einträgen ohne Migration),
            // wird previewImageUrl als statisches Standbild gezeigt.
            val imageUrls = spark.sparkImageUrls ?: emptyList()
            val imagePagerState = rememberPagerState(pageCount = { imageUrls.size })
            // Engagement: User hat mindestens zum zweiten Bild geswiped → onEngaged()
            var imageEngaged by remember(spark.id) { mutableStateOf(false) }
            LaunchedEffect(imagePagerState.currentPage) {
                if (imagePagerState.currentPage > 0 && !imageEngaged) {
                    imageEngaged = true
                    onEngaged()
                }
            }
            // Cast: Bild-Index bei Wischen an den Receiver melden
            LaunchedEffect(imagePagerState.currentPage, castConnected) {
                if (castConnected) {
                    val absoluteImageUrls = imageUrls.map {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }
                    val absoluteMusicUrl = spark.musicUrl?.let {
                        if (it.startsWith("http")) it else "https://letheapp.de$it"
                    }
                    try {
                        com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
                            .sessionManager?.currentCastSession?.sendSparkMessage(
                                SparkCastChannel.buildSparkMessage(
                                    sparkId = spark.id,
                                    videoUrl = null,
                                    title = spark.title,
                                    creatorName = spark.creatorName ?: spark.creatorFakeNumber,
                                    imageUrls = absoluteImageUrls,
                                    musicUrl = absoluteMusicUrl,
                                    imageIndex = imagePagerState.currentPage,
                                    description = spark.description
                                )
                            )
                    } catch (_: Exception) {}
                }
            }
            if (imageUrls.isEmpty()) {
                // Fallback: kein sparkImageUrls vorhanden → previewImageUrl als Standbild zeigen
                AsyncImage(
                    model = spark.previewImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isPlaying = !isPlaying
                            showOverlay = true
                            onToggleControls(true)
                        }
                )
            } else {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = imagePagerState,
                    modifier = Modifier.fillMaxSize()
                ) { imgPage ->
                    var imgIsLandscape by remember { mutableStateOf(false) }
                    val imageUrl = imageUrls.getOrNull(imgPage)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isPlaying = !isPlaying
                                showOverlay = true
                                onToggleControls(true)
                            }
                    ) {
                        if (imgIsLandscape) {
                            // Farbiger Unschärfe-Schimmer oben und unten
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(radiusX = 25.dp, radiusY = 25.dp)
                            )
                        }
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            onSuccess = { state ->
                                val d = state.result.drawable
                                imgIsLandscape = d.intrinsicWidth > d.intrinsicHeight
                            },
                            contentScale = if (imgIsLandscape) ContentScale.FillWidth else ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Dots-Indikator unten
                if (imageUrls.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(imageUrls.size) { dot ->
                            Box(
                                modifier = Modifier
                                    .size(if (dot == imagePagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (dot == imagePagerState.currentPage) Color.White
                                        else Color.White.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }
            }
            } // end else (imageUrls nicht leer)
        } else if (!spark.mediaUrl.isNullOrBlank()) {
            if (videoIsLandscape && !isFullscreen) {
                // Querformat-Video: zentriert angezeigt mit Vollbild-Button am unteren Videorand
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = videoScale
                            scaleY = videoScale
                            translationX = videoOffset.x
                            translationY = videoOffset.y
                        }
                        .transformable(
                            state = videoTransformableState,
                            canPan = { videoScale > 1f }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!playerError) {
                        PlayerSurface(
                            player = player,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = spark.previewImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // Tap-Handler (verdeckt nicht den Vollbild-Button)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (spark.isLive) onNavigateToLiveRoom(spark.creatorId)
                                else { isPlaying = !isPlaying; showOverlay = true; onToggleControls(true) }
                            }
                    )
                    // "In Vollbild anzeigen" Button – immer sichtbar am unteren Videorand
                    TextButton(
                        onClick = { isFullscreen = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 316.dp)
                    ) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sparks_feed_show_fullscreen), color = Color.White, fontSize = 14.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = videoScale
                            scaleY = videoScale
                            translationX = videoOffset.x
                            translationY = videoOffset.y
                        }
                        .transformable(
                            state = videoTransformableState,
                            canPan = { videoScale > 1f }
                        )
                ) {
                    if (!playerError) {
                        PlayerSurface(
                            player = player,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = spark.previewImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (spark.isLive) {
                                    onNavigateToLiveRoom(spark.creatorId)
                                } else {
                                    isPlaying = !isPlaying
                                    showOverlay = true
                                    onToggleControls(true)
                                }
                            }
                    )

                    // Zurück-Button im Vollbild-Modus
                    if (isFullscreen) {
                        IconButton(
                            onClick = { isFullscreen = false },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, top = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.sparks_feed_exit_fullscreen),
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        } else if (!spark.previewImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = spark.previewImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // EPIC 4: Leuchtendes LIVE-Badge
        if (spark.isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "● LIVE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Play-Icon Overlay
        AnimatedVisibility(
            visible = !isPlaying && !spark.isLive,
            enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f, animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.sparks_feed_play),
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Lade-Indikator: nur beim initialen Puffern vor erstem Play, nicht bei HLS-Nachpufferung.
        // Kein Spinner für image_sparks (Bilder sind sofort sichtbar, Musik lädt im Hintergrund).
        if (!spark.isLive && !isImageSpark && !playerError && isBuffering && isPlaying && isInitialBuffering && !castConnected) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        // OVERLAY (unten links): Creator-Infos, Titel, Beschreibung
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            val sparksDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(sparksDensity.density, sparksDensity.fontScale * fontSizeMultiplier)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 80.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNavigateToSparksProfile(spark.creatorId) }
                ) {
                    if (!spark.creatorProfileImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = spark.creatorProfileImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = spark.creatorName ?: spark.creatorFakeNumber ?: "Creator",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        style = TextStyle(shadow = Shadow(Color.Black, Offset(1f, 1f), 4f))
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (!spark.title.isNullOrBlank()) {
                    Text(
                        text = spark.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        style = TextStyle(shadow = Shadow(Color.Black, Offset(1f, 1f), 4f))
                    )
                }

                if (!spark.description.isNullOrBlank()) {
                    SparkDescriptionText(description = spark.description)
                }

                // Song-Info-Link (wenn Musik-Metadaten vorhanden)
                if (!spark.musicTitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable {
                                val encoded = android.net.Uri.encode(spark.sparkImageUrls?.firstOrNull() ?: "")
                                if (encoded.isNotBlank()) onNavigateToSparkEditor(encoded)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFA8A800),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                append(spark.musicTitle)
                                if (!spark.musicArtist.isNullOrBlank()) append(" · ${spark.musicArtist}")
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (isVideoWithOriginalAudio) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = buildString {
                                if (!spark.title.isNullOrBlank()) append(spark.title)
                                val creator = spark.creatorName ?: spark.creatorFakeNumber
                                if (!creator.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append("@$creator")
                                }
                                append(" (Original)")
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            } // end CompositionLocalProvider (fontSizeMultiplier)
        }

        // ACTION BAR (rechte Seite)
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(top = 0.dp, end = 12.dp, bottom = 76.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Profil-Symbol – ganz oben in der Spalte
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .size(43.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { onNavigateToSparksProfile(spark.creatorId) }
                    ) {
                        AsyncImage(
                            model = spark.creatorProfileImageUrl,
                            contentDescription = stringResource(R.string.sparks_feed_creator),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (!isFollowing) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .offset(y = 20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    viewModel.subscribeToCreator(spark.creatorId) { success, msg ->
                                        if (success) {
                                            isFollowing = true
                                            Toast.makeText(context, strSubscribed, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Like-Button: Tap = Like, LongPress = Geschenk-Dialog
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                            .combinedClickable(
                                onClick = {
                                    val nowLiked = !isLiked
                                    isLiked = nowLiked
                                    likesCount = if (nowLiked) likesCount + 1 else maxOf(0, likesCount - 1)
                                    onLike()
                                    if (nowLiked) {
                                        scope.launch {
                                            try { viewModel.trackSparkInteraction(spark.id, "LIKE", null) } catch (_: Exception) {}
                                        }
                                    }
                                },
                                onLongClick = { showGiftDialog = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = stringResource(R.string.sparks_feed_action_like),
                            tint = if (isLiked) Color(0xFFE53935) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = if (likesCount > 0) "$likesCount" else stringResource(R.string.sparks_feed_action_like),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

                SparkActionIcon(
                    icon = Icons.Default.ChatBubbleOutline,
                    label = if (spark.commentsCount > 0) spark.commentsCount.toString() else stringResource(R.string.sparks_feed_action_comment),
                    onClick = { showComments = true }
                )

                SparkActionIcon(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.sparks_feed_action_share),
                    showLabel = false,
                    onClick = { showShareDialog = true }
                )

                SparkActionIcon(
                    icon = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    tint = if (isSaved) Color(0xFFA8A800) else Color.White,
                    label = if (isSaved) "Gespeichert" else "Speichern",
                    showLabel = false,
                    onClick = {
                        isSaved = !isSaved
                        scope.launch {
                            try { viewModel.saveSpark(spark.id) } catch (_: Exception) {}
                        }
                    }
                )

                // Drei-Punkte (horizontal) → nur für Spark-Ersteller
                if (currentUser?.userId == spark.creatorUserId || currentUser?.isAdmin == true) {
                    SparkActionIcon(
                        icon = Icons.Default.MoreHoriz,
                        label = "Mehr",
                        onClick = { showMoreSheet = true }
                    )
                }

                // Vinyl-Kreis: Song-Cover (Musik oder Original-Ton bei Videos)
                if (!spark.musicTitle.isNullOrBlank() || isVideoWithOriginalAudio) {
                    val vinylRotation = rememberInfiniteTransition(label = "vinyl")
                    val angle by vinylRotation.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(4000, easing = androidx.compose.animation.core.LinearEasing)
                        ),
                        label = "vinylAngle"
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFA8A800), CircleShape)
                            .clickable { onNavigateToSoundScreen(spark.originalSound ?: spark.soundOriginSparkId ?: spark.id) }
                            .graphicsLayer { rotationZ = angle },
                        contentAlignment = Alignment.Center
                    ) {
                        val vinylCoverUrl = spark.musicCoverUrl
                            ?: spark.previewImageUrl
                            ?: spark.creatorProfileImageUrl
                        if (!vinylCoverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = vinylCoverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Sound",
                                    tint = Color(0xFFA8A800),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }

        // Video-Zeitstrahl (nur für Video-Sparks, ein-/ausblendbend mit Overlay)
        if (!isImageSpark && !spark.isLive) {
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(500)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    val progress = if (videoDurationMs > 0) {
                        (videoPositionMs.toFloat() / videoDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    fun formatMs(ms: Long): String {
                        val totalSec = ms / 1000
                        return "%d:%02d".format(totalSec / 60, totalSec % 60)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatMs(videoPositionMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.width(30.dp)
                        )
                        Slider(
                            value = progress,
                            onValueChange = { newVal ->
                                isSeekingVideo = true
                                videoPositionMs = (newVal * videoDurationMs).toLong()
                            },
                            onValueChangeFinished = {
                                player.seekTo(videoPositionMs)
                                // Cast: Seek synchronisieren
                                getCastRemoteClient()?.seek(videoPositionMs)
                                isSeekingVideo = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp),
                            thumb = {
                                Box(
                                    modifier = androidx.compose.ui.Modifier
                                        .size(10.dp)
                                        .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape)
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = androidx.compose.ui.Modifier.height(2.dp),
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color(0xFFA8A800),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                    )
                                )
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFFA8A800),
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                            )
                        )
                        Text(
                            text = formatMs(videoDurationMs),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.width(30.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }

        // Kommentare als BottomSheet
        if (showComments) {
            SparkCommentsSheet(
                spark = spark,
                currentUser = currentUser,
                viewModel = viewModel,
                onDismiss = { showComments = false },
                fontSizeMultiplier = fontSizeMultiplier
            )
        }

        if (showGiftDialog) {
            SparkGiftDialog(
                spark = spark,
                currentUser = currentUser,
                onDismiss = { showGiftDialog = false }
            )
        }

        if (showShareDialog) {
            SparkShareSheet(
                spark = spark,
                viewModel = viewModel,
                onDismiss = { showShareDialog = false },
                onNavigateToProfile = onNavigateToSparksProfile
            )
        }

        if (showMoreSheet) {
            val isOwnSpark = currentUser?.userId == spark.creatorUserId ||
                currentUser?.isAdmin == true
            SparkMoreOptionsSheet(
                isCreator = isOwnSpark,
                onDismiss = { showMoreSheet = false },
                onEditSpark = {
                    showMoreSheet = false
                    showEditSparkDialog = true
                }
            )
        }

        if (showEditSparkDialog) {
            SparkEditDialog(
                initialTitle = spark.title ?: "",
                initialDescription = spark.description ?: "",
                onDismiss = { showEditSparkDialog = false },
                onConfirm = { newTitle, newDesc ->
                    showEditSparkDialog = false
                    viewModel.editSpark(spark.id, newTitle, newDesc) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 18+ Blur-Overlay: Wenn 18+ Inhalt und User NICHT 18+ verifiziert
        if (is18plusContent && currentUser?.is18Verified != true) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                // Verschwommenes Vorschaubild im Hintergrund
                if (!spark.previewImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = spark.previewImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(20.dp)
                    )
                    // Abdunkelung über dem blur
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        stringResource(R.string.sparks_feed_18plus_title),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sparks_feed_18plus_subtitle),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { /* Navigation zur AgeVerificationScreen */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA8A800)
                        )
                    ) {
                        Text(stringResource(R.string.sparks_feed_18plus_verify_btn), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkMoreOptionsSheet(
    isCreator: Boolean,
    onDismiss: () -> Unit,
    onEditSpark: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Optionen",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            if (isCreator) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditSpark() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("Titel & Beschreibung bearbeiten", fontSize = 15.sp)
                }
            }
            // Weitere Optionen können hier ergänzt werden
        }
    }
}

@Composable
private fun SparkEditDialog(
    initialTitle: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spark bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, description) }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

/**
 * Beschreibungstext mit klickbaren #Tags.
 * @mentions und &-Links folgen in späteren Iterationen.
 */
@Composable
private fun SparkDescriptionText(description: String) {
    // Zerlege die Beschreibung in Segmente: normale Teile und #tags
    val parts = remember(description) {
        val regex = Regex("""(#\w+)""")
        val segments = mutableListOf<Pair<String, Boolean>>() // text, isTag
        var lastEnd = 0
        for (match in regex.findAll(description)) {
            if (match.range.first > lastEnd) {
                segments.add(description.substring(lastEnd, match.range.first) to false)
            }
            segments.add(match.value to true)
            lastEnd = match.range.last + 1
        }
        if (lastEnd < description.length) {
            segments.add(description.substring(lastEnd) to false)
        }
        segments
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Inline-Rendering nicht möglich mit verschiedenen Click-Bereichen in Compose ohne AnnotatedString + ClickableText
        // Wir nutzen Text mit AnnotatedString für die Anzeige
        Text(
            text = buildHashtagAnnotatedString(description),
            style = TextStyle(
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                shadow = Shadow(Color.Black, Offset(1f, 1f), 4f)
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildHashtagAnnotatedString(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val regex = Regex("""(#\w+)""")
    var lastEnd = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastEnd) {
            builder.append(text.substring(lastEnd, match.range.first))
        }
        builder.pushStyle(
            androidx.compose.ui.text.SpanStyle(
                color = Color(0xFFA8A800),
                fontWeight = FontWeight.SemiBold
            )
        )
        builder.append(match.value)
        builder.pop()
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        builder.append(text.substring(lastEnd))
    }
    return builder.toAnnotatedString()
}

// ── EPIC 3: Isolierter PlayerSurface-Composable ────────────────────────────────────────────
// TextureView statt SurfaceView: SurfaceView funktioniert nicht korrekt innerhalb von
// graphicsLayer-Containern (kein Hole-Punching durch Hardware-Layer möglich) → kein Bild.
// TextureView rendert als reguläre OpenGL-Textur in der Hauptoberfläche und ist
// graphicsLayer-kompatibel.
@Composable
private fun PlayerSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            android.view.TextureView(ctx).also { tv ->
                player.setVideoTextureView(tv)
            }
        },
        update = { tv ->
            player.setVideoTextureView(tv)
        },
        onRelease = { tv ->
            player.clearVideoTextureView(tv)
        },
        modifier = modifier
    )
}

@Composable
private fun SparkActionIcon(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    showLabel: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        if (showLabel) {
            Text(text = label, color = Color.White, fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkCommentsSheet(
    spark: CreatorContentResponse,
    currentUser: UserEntity?,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    fontSizeMultiplier: Float = 1.0f
) {
    val comments by viewModel.sparkComments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var replyToUser by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    val myProfileImageUrl = remember(currentUser?.profileImageUrl) {
        currentUser?.profileImageUrl?.let {
            if (it.startsWith("http")) it else "https://letheapp.de$it"
        }
    }

    LaunchedEffect(spark.id) {
        viewModel.loadSparkComments(spark.id)
    }

    LaunchedEffect(replyToUser) {
        replyToUser?.let { name ->
            commentText = "@$name "
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(Unit) { sheetState.expand() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.78f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(35.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.sparks_feed_comments_count, comments.size),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            HorizontalDivider()

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.sparks_feed_no_comments),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        val canDelete = comment.userId == currentUser?.userId
                        SparkCommentItem(
                            comment = comment,
                            currentUserId = currentUser?.userId ?: "",
                            canDelete = canDelete,
                            onLike = { viewModel.likeSparkComment(comment.id) },
                            onReply = { replyToUser = comment.userName ?: comment.userFakeNumber ?: "User" },
                            onDelete = { viewModel.deleteSparkComment(spark.id, comment.id) },
                            fontSizeMultiplier = fontSizeMultiplier
                        )
                    }
                }
            }

            HorizontalDivider()

            if (replyToUser != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sparks_feed_reply_to, replyToUser ?: ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { replyToUser = null; commentText = "" }
                            .padding(4.dp)
                    )
                }
            }

            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .size(80.dp)
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { selectedImageUri = null },
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Eigenes Profilbild
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!myProfileImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = myProfileImageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (currentUser?.name?.take(1) ?: "I").uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Eingabefeld – kompakt, abgerundet
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text(stringResource(R.string.sparks_feed_comment_placeholder), fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                )

                // Bild anhängen
                IconButton(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = if (selectedImageUri != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // GIF
                IconButton(
                    onClick = { showGifPicker = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Gif,
                        contentDescription = "GIF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Sticker / Emoji
                IconButton(
                    onClick = { showStickerPicker = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "Sticker",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Senden
                IconButton(
                    onClick = {
                        val text = commentText.trim()
                        val img = selectedImageUri
                        if ((text.isNotBlank() || img != null) && !isSending) {
                            isSending = true
                            commentText = ""
                            selectedImageUri = null
                            replyToUser = null
                            viewModel.postSparkComment(spark.id, text, img) { success ->
                                isSending = false
                                if (!success) commentText = text
                            }
                        }
                    },
                    enabled = (commentText.isNotBlank() || selectedImageUri != null) && !isSending,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.sparks_feed_send),
                        tint = if ((commentText.isNotBlank() || selectedImageUri != null) && !isSending)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }

    // GIF-Picker BottomSheet für Kommentare
    if (showGifPicker) {
        ModalBottomSheet(
            onDismissRequest = { showGifPicker = false },
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(35.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            }
        ) {
            GiphyPickerSheet(
                viewModel = viewModel,
                onGifSelected = { gifUrl ->
                    showGifPicker = false
                    if (!isSending) {
                        isSending = true
                        viewModel.postSparkComment(
                            sparkId = spark.id,
                            text = commentText.trim(),
                            gifUrl = gifUrl
                        ) { success ->
                            isSending = false
                            if (success) {
                                commentText = ""
                                selectedImageUri = null
                                replyToUser = null
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        }
    }

    // Sticker-Picker BottomSheet für Kommentare
    if (showStickerPicker) {
        ModalBottomSheet(
            onDismissRequest = { showStickerPicker = false },
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .size(35.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            }
        ) {
            StickerPickerSheet(
                viewModel = viewModel,
                onStickerSelected = { stickerUrl ->
                    showStickerPicker = false
                    if (!isSending) {
                        isSending = true
                        viewModel.postSparkComment(
                            sparkId = spark.id,
                            text = commentText.trim(),
                            gifUrl = stickerUrl
                        ) { success ->
                            isSending = false
                            if (success) {
                                commentText = ""
                                selectedImageUri = null
                                replyToUser = null
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            )
        }
    }
}

/**
 * Inline-Kommentare als rechtes Side-Panel neben dem Video.
 * Kein BottomSheet – erscheint als Overlay von rechts per Slide-Animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparkInlineComments(
    spark: CreatorContentResponse,
    currentUser: UserEntity?,
    viewModel: MainViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f
) {
    val comments by viewModel.sparkComments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var replyToUser by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    val myProfileImageUrl = remember(currentUser?.profileImageUrl) {
        currentUser?.profileImageUrl?.let {
            if (it.startsWith("http")) it else "https://letheapp.de$it"
        }
    }

    LaunchedEffect(spark.id) {
        viewModel.loadSparkComments(spark.id)
    }

    LaunchedEffect(replyToUser) {
        replyToUser?.let { name -> commentText = "@$name " }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sparks_feed_comments_count, comments.size),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        HorizontalDivider()

        // Kommentarliste
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.sparks_feed_no_comments),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    SparkCommentItem(
                        comment = comment,
                        currentUserId = currentUser?.userId ?: "",
                        canDelete = comment.userId == currentUser?.userId,
                        onLike = { viewModel.likeSparkComment(comment.id) },
                        onReply = { replyToUser = comment.userName ?: comment.userFakeNumber ?: "User" },
                        onDelete = { viewModel.deleteSparkComment(spark.id, comment.id) },
                        fontSizeMultiplier = fontSizeMultiplier
                    )
                }
            }
        }

        HorizontalDivider()

        // Antwort-Banner
        if (replyToUser != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sparks_feed_reply_to, replyToUser ?: ""),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "✕",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { replyToUser = null; commentText = "" }.padding(4.dp)
                )
            }
        }

        // Bild-Vorschau
        if (selectedImageUri != null) {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).size(56.dp)) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { selectedImageUri = null },
                    modifier = Modifier.size(14.dp).align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(9.dp))
                }
            }
        }

        // Eingabezeile
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (!myProfileImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = myProfileImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = (currentUser?.name?.take(1) ?: "I").uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(Modifier.width(3.dp))
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = { Text(stringResource(R.string.sparks_feed_comment_placeholder), fontSize = 10.sp) },
                modifier = Modifier.weight(1f).heightIn(min = 28.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
            )
            IconButton(
                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = if (selectedImageUri != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(onClick = { showGifPicker = true }, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Gif, contentDescription = "GIF",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
            IconButton(
                onClick = {
                    val text = commentText.trim()
                    val img = selectedImageUri
                    if ((text.isNotBlank() || img != null) && !isSending) {
                        isSending = true
                        commentText = ""
                        selectedImageUri = null
                        replyToUser = null
                        viewModel.postSparkComment(spark.id, text, img) { success ->
                            isSending = false
                            if (!success) commentText = text
                        }
                    }
                },
                enabled = (commentText.isNotBlank() || selectedImageUri != null) && !isSending,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = if ((commentText.isNotBlank() || selectedImageUri != null) && !isSending)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    if (showGifPicker) {
        ModalBottomSheet(
            onDismissRequest = { showGifPicker = false },
            dragHandle = {
                Box(modifier = Modifier.padding(vertical = 8.dp).size(35.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), shape = CircleShape))
                }
            }
        ) {
            GiphyPickerSheet(
                viewModel = viewModel,
                onGifSelected = { gifUrl ->
                    showGifPicker = false
                    if (!isSending) {
                        isSending = true
                        viewModel.postSparkComment(sparkId = spark.id, text = commentText.trim(), gifUrl = gifUrl) { success ->
                            isSending = false
                            if (success) { commentText = ""; selectedImageUri = null; replyToUser = null }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(420.dp)
            )
        }
    }

    if (showStickerPicker) {
        ModalBottomSheet(
            onDismissRequest = { showStickerPicker = false },
            dragHandle = {
                Box(modifier = Modifier.padding(vertical = 8.dp).size(35.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), shape = CircleShape))
                }
            }
        ) {
            StickerPickerSheet(
                viewModel = viewModel,
                onStickerSelected = { stickerUrl ->
                    showStickerPicker = false
                    if (!isSending) {
                        isSending = true
                        viewModel.postSparkComment(sparkId = spark.id, text = commentText.trim(), gifUrl = stickerUrl) { success ->
                            isSending = false
                            if (success) { commentText = ""; selectedImageUri = null; replyToUser = null }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(480.dp)
            )
        }
    }
}

@Composable
private fun SparkCommentItem(
    comment: SparkCommentResponse,
    currentUserId: String,
    canDelete: Boolean = false,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit = {},
    fontSizeMultiplier: Float = 1.0f
) {
    var showImageFullscreen by remember { mutableStateOf(false) }

    if (showImageFullscreen && !comment.imageUrl.isNullOrBlank()) {
        val imgUrl = if (comment.imageUrl.startsWith("http")) comment.imageUrl
        else "https://letheapp.de${comment.imageUrl}"
        Dialog(onDismissRequest = { showImageFullscreen = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showImageFullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }

    val commentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(commentDensity.density, commentDensity.fontScale * fontSizeMultiplier)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            if (!comment.userProfileImageUrl.isNullOrBlank()) {
                val absProfileUrl = if (comment.userProfileImageUrl.startsWith("http"))
                    comment.userProfileImageUrl
                else "https://letheapp.de${comment.userProfileImageUrl}"
                AsyncImage(
                    model = absProfileUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = (comment.userName?.take(1) ?: "?").uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = comment.userName ?: comment.userFakeNumber ?: stringResource(R.string.sparks_feed_unknown_user),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            // Spark-Link erkennen (lethe://sp?id=uuid) und als Karte rendern
            val sparkLinkRegex = remember { Regex("""^lethe://sp\?id=([a-zA-Z0-9_-]+)$""") }
            val sparkMatch = sparkLinkRegex.find(comment.text.trim())
            if (sparkMatch != null) {
                val contentId = sparkMatch.groupValues[1]
                val ctx = LocalContext.current
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            try {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("lethe://sp?id=$contentId")
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.sparks_feed_spark_link_label), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(stringResource(R.string.sparks_feed_spark_link_tap), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else {
                if (comment.text.isNotBlank()) {
                    val shortCodePattern = remember { Regex("""@([0-9A-Za-z]{15,25})""") }
                    val matches = remember(comment.text) { shortCodePattern.findAll(comment.text).toList() }
                    if (matches.isNotEmpty()) {
                        val ctx = LocalContext.current
                        val annotated = remember(comment.text) {
                            androidx.compose.ui.text.buildAnnotatedString {
                                var lastIdx = 0
                                for (m in matches) {
                                    append(comment.text.substring(lastIdx, m.range.first))
                                    val code = m.groupValues[1]
                                    val uuid = shortCodeToUuid(code)
                                    if (uuid != null) {
                                        pushStringAnnotation("spark", uuid)
                                        withStyle(androidx.compose.ui.text.SpanStyle(
                                            color = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                                            fontWeight = FontWeight.SemiBold
                                        )) { append(m.value) }
                                        pop()
                                    } else {
                                        append(m.value)
                                    }
                                    lastIdx = m.range.last + 1
                                }
                                if (lastIdx < comment.text.length) append(comment.text.substring(lastIdx))
                            }
                        }
                        @Suppress("DEPRECATION")
                        androidx.compose.foundation.text.ClickableText(
                            text = annotated,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(top = 2.dp),
                            onClick = { offset ->
                                annotated.getStringAnnotations("spark", offset, offset).firstOrNull()?.let { ann ->
                                    try {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse("lethe://sp?id=${ann.item}")
                                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    } else {
                        Text(
                            text = comment.text,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (!comment.imageUrl.isNullOrBlank()) {
                    val imgUrl = if (comment.imageUrl.startsWith("http")) comment.imageUrl
                    else "https://letheapp.de${comment.imageUrl}"
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(width = 130.dp, height = 100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showImageFullscreen = true },
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Text(
                text = stringResource(R.string.sparks_feed_reply),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onReply() }
                    .padding(top = 4.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            IconButton(
                onClick = onLike,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (comment.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.sparks_feed_action_like),
                    tint = if (comment.isLikedByMe) Color(0xFFE53935) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(
                    text = comment.likesCount.toString(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Kommentar löschen",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    } // end CompositionLocalProvider (fontSizeMultiplier)
}

@Composable
private fun SparkGiftDialog(
    spark: CreatorContentResponse,
    currentUser: UserEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val giftAmounts = listOf(50, 100, 150, 200)
    val userStyx = currentUser?.styx ?: 0
    val strGiftNotEnough = stringResource(R.string.sparks_feed_gift_not_enough)
    val creatorName = spark.creatorName ?: stringResource(R.string.sparks_feed_creator)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sparks_feed_gift_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sparks_feed_gift_support, creatorName))
                Text(text = stringResource(R.string.sparks_feed_gift_balance, userStyx), fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    giftAmounts.forEach { amount ->
                        val canAfford = userStyx >= amount
                        val strGiftSent = stringResource(R.string.sparks_feed_gift_sent, amount)
                        OutlinedButton(
                            onClick = {
                                if (canAfford) {
                                    Toast.makeText(context, strGiftSent, Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, strGiftNotEnough, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = if (canAfford) ButtonDefaults.outlinedButtonColors()
                                     else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(text = "$amount", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sparks_feed_gift_close)) }
        }
    )
}

private fun uuidToShortCode(uuid: String): String {
    val hex = uuid.replace("-", "")
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    var num = java.math.BigInteger(hex, 16)
    val base = java.math.BigInteger.valueOf(62)
    val sb = StringBuilder()
    while (num > java.math.BigInteger.ZERO) {
        val (q, r) = num.divideAndRemainder(base)
        sb.append(chars[r.toInt()])
        num = q
    }
    return sb.reverse().toString().ifEmpty { "0" }
}

private fun shortCodeToUuid(code: String): String? {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    val base = java.math.BigInteger.valueOf(62)
    var num = java.math.BigInteger.ZERO
    for (c in code) {
        val idx = chars.indexOf(c)
        if (idx < 0) return null
        num = num.multiply(base).add(java.math.BigInteger.valueOf(idx.toLong()))
    }
    val hex = num.toString(16).padStart(32, '0')
    if (hex.length != 32) return null
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SparkShareSheet(
    spark: CreatorContentResponse,
    viewModel: com.securechat.app.ui.MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToProfile: (creatorId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val strShareTitle = stringResource(R.string.sparks_feed_share_title)
    val strLinkCopied = stringResource(R.string.sparks_feed_share_link_copied)
    var showReportSheet by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(35.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            val creatorHandle = spark.creatorUsername ?: spark.creatorFakeNumber ?: ""
            // Externe URL: C= (Großbuchstabe) = direkte Content-UUID – keine fake_number in der URL
            val externalUrl = "https://letheapp.de/post?C=${spark.id}"
            // Interner Deep-Link: lethe://li?id= für Live-Streams, lethe://sp?url= für Sparks (HLS-URL direkt)
            val internalUrl = if (spark.isLive) {
                "lethe://li?id=${spark.id}"
            } else {
                val hlsUrl = spark.mediaUrl
                if (!hlsUrl.isNullOrBlank()) "lethe://sp?url=${java.net.URLEncoder.encode(hlsUrl, "UTF-8")}"
                else "lethe://sp?id=${spark.id}"
            }
            val shortCode = remember(spark.id) { uuidToShortCode(spark.id) }
            val strShortcodeCopied = stringResource(R.string.sparks_feed_share_shortcode_copied)

            // ─── Spark-Kurzcode: oben mittig ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.sparks_feed_share_shortcode),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        "@$shortCode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .clickable {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Spark Kurzcode", "@$shortCode"))
                                Toast.makeText(context, strShortcodeCopied, Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider()

            // ─── Extern + Link nebeneinander ────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                // Extern teilen
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "Schau dir diesen Spark von @$creatorHandle an!\n$externalUrl")
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, strShareTitle))
                            onDismiss()
                        }
                        .padding(vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.sparks_feed_share_external), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }

                // Link kopieren
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Spark Link", externalUrl))
                            Toast.makeText(context, strLinkCopied, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                        .padding(vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.sparks_feed_share_copy_link), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }

            HorizontalDivider()

            // ─── An Kontakt senden – horizontale Scroll-Zeile ───────────────────
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.sparks_feed_share_send_to),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val contacts by viewModel.contacts.collectAsState(initial = emptyList())
            val acceptedContacts = remember(contacts) { contacts.filter { it.status == "accepted" && !it.isBot } }
            if (acceptedContacts.isEmpty()) {
                Text(
                    stringResource(R.string.sparks_feed_no_contacts),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(acceptedContacts) { contact ->
                        val strSentTo = stringResource(R.string.sparks_feed_sent_to, contact.username ?: contact.fakeNumber)
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clickable {
                                    viewModel.sendMessage(contact.userId, internalUrl)
                                    Toast.makeText(context, strSentTo, Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (contact.profileImageUrl != null) {
                                coil.compose.AsyncImage(
                                    model = contact.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (contact.username ?: contact.fakeNumber).take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = contact.username ?: contact.fakeNumber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ─── Weitere Aktionen ───────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            val strReport = stringResource(R.string.sparks_feed_action_report)
            val strNotInterested = stringResource(R.string.sparks_feed_not_interested)
            val strCreatorProfile = stringResource(R.string.sparks_feed_creator_profile)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Melden
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        .clickable { showReportSheet = true }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(strReport, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                }

                // Nicht interessiert
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(strNotInterested, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                // Creator-Profil
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onNavigateToProfile(spark.creatorId)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(strCreatorProfile, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showReportSheet) {
        ReportUserBottomSheet(
            viewModel = viewModel,
            reportedUserId = spark.creatorUserId ?: spark.creatorId,
            contextSource = "VIP_SPARK",
            onDismiss = {
                showReportSheet = false
                onDismiss()
            }
        )
    }
}
