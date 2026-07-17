@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.securechat.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.*
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.securechat.app.R
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun StatusViewer(
    statusId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onMusicClick: ((musicUrl: String, musicTitle: String?, musicArtist: String?) -> Unit)? = null,
    onNavigateToProfile: ((String) -> Unit)? = null
) {
    val viewingStatus by viewModel.viewingStatus.collectAsState()
    val statusGroup by viewModel.currentStatusGroup.collectAsState()
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val activeStatuses by viewModel.activeStatuses.collectAsState(initial = emptyList())
    val myStatuses by viewModel.myStatuses.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val statusViewers by viewModel.statusViewers.collectAsState()
    val likedStatusIds by viewModel.likedStatusIds.collectAsState()
    var showViewersDialog by remember { mutableStateOf(false) }
    var showReplyInput by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf(TextFieldValue("")) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Vorbereiteter Status – Fallback: in kombinierten Listen suchen
    val status = viewingStatus
        ?: (activeStatuses + myStatuses).find { it.statusId == statusId }

    // Schutz gegen null nach kurzer Wartezeit
    var waitedForStatus by remember { mutableStateOf(false) }
    LaunchedEffect(statusId) {
        kotlinx.coroutines.delay(800)
        waitedForStatus = true
    }
    if (status == null && waitedForStatus) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    if (status == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearViewingStatus() }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val avatarUrl = remember(status.userId, contacts) {
        contacts.find { it.userId == status.userId }?.profileImageUrl ?: status.userImage
    }

    val justNowStr = stringResource(R.string.status_viewer_just_now)
    val timeAgo = remember(status.createdAt, justNowStr) {
        val diff = System.currentTimeMillis() - status.createdAt
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        when {
            minutes < 5 -> justNowStr
            else        -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(status.createdAt))
        }
    }

    val durationMs = when (status.mediaType) {
        "video" -> 30_000
        "audio" -> 15_000
        else    -> if (!status.musicUrl.isNullOrBlank() && (status.musicDurationSec ?: 0) > 0)
            (status.musicDurationSec!! * 1000)
        else
            5_000
    }

    val expiredStr = stringResource(R.string.status_viewer_expired)
    val remainingHmStr = stringResource(R.string.status_viewer_remaining_hm)
    val remainingMStr = stringResource(R.string.status_viewer_remaining_m)
    val remainingHours = remember(status.expiresAt, expiredStr, remainingHmStr, remainingMStr) {
        val remaining = status.expiresAt - System.currentTimeMillis()
        val hours = remaining / 3_600_000L
        val minutes = (remaining % 3_600_000L) / 60_000L
        when {
            remaining <= 0 -> expiredStr
            hours >= 1     -> String.format(remainingHmStr, hours, minutes)
            else           -> String.format(remainingMStr, minutes)
        }
    }

    // ── ExoPlayer für Videos – via produceState gehoben (außerhalb des when-Blocks) ──
    // produceState handhabt Lebenszyklus: alte Player werden in awaitDispose freigegeben.
    val player by produceState<ExoPlayer?>(initialValue = null, status.statusId) {
        if (status.mediaType == "video") {
            val localPath = viewModel.getStatusVideoLocalPath(status.statusId)
            val videoUri =
                if (localPath != null) android.net.Uri.fromFile(java.io.File(localPath))
                else android.net.Uri.parse(status.mediaUrl)
            val p = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                prepare()
                playWhenReady = true
            }
            if (localPath == null) {
                viewModel.cacheStatusVideo(status.statusId, status.mediaUrl)
            }
            value = p
            awaitDispose { p.release() }
        }
    }

    // ── ExoPlayer für Musik bei Bild-Statuses ────────────────────────────────
    val musicPlayer by produceState<ExoPlayer?>(initialValue = null, status.statusId) {
        val musicUrl = status.musicUrl
        if (status.mediaType == "image" && !musicUrl.isNullOrBlank()) {
            val offsetMs = ((status.musicOffsetSec ?: 0f) * 1000).toLong()
            val p = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.parse(musicUrl)))
                prepare()
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                playWhenReady = true
                if (offsetMs > 0) seekTo(offsetMs)
            }
            value = p
            awaitDispose { p.release() }
        }
    }

    // ── Pause/Resume-Status ──────────────────────────────────────────────────
    // Wird beim Wechsel des Status (statusId) automatisch auf false zurückgesetzt.
    var isPaused by remember(status.statusId) { mutableStateOf(false) }

    // Animations-Progress
    val progressAnimatable = remember(status.statusId) { Animatable(0f) }

    // ── Status-View aufzeichnen + Like-Status prüfen + Animation zurücksetzen ────
    LaunchedEffect(status.statusId) {
        val currentUserId = viewModel.currentUser.value?.userId
        if (status.userId != currentUserId) {
            viewModel.recordStatusView(status.statusId)
            viewModel.markStatusViewed(status.statusId)
            // Like-Status vom Server laden (bleibt über Sessions erhalten)
            viewModel.checkStatusLiked(status.statusId)
        }
        progressAnimatable.snapTo(0f)
    }

    // ── Animation mit eingebautem Pause-Support ──────────────────────────
    // isPaused als Key: Coroutine wird bei jeder Änderung neu gestartet.
    // Bei isPaused=true → sofort return (laufendes animateTo wird durch
    // Coroutine-Abbruch gestoppt, letzter Framewert bleibt erhalten).
    // Bei isPaused=false → animateTo setzt vom gespeicherten Wert fort.
    LaunchedEffect(status.statusId, isPaused, showReplyInput) {
        if (isPaused || showReplyInput) return@LaunchedEffect
        val remaining = ((1f - progressAnimatable.value) * durationMs).toInt().coerceAtLeast(16)
        progressAnimatable.animateTo(1f, tween(remaining))
        if (progressAnimatable.value >= 0.99f) {
            if (!viewModel.advanceToNextStatus()) onBack()
        }
    }

    // ── Video-Player pausieren/fortsetzen ────────────────────────────────
    LaunchedEffect(isPaused, showReplyInput, player) {
        val p = player ?: return@LaunchedEffect
        if (isPaused || showReplyInput) p.pause() else p.play()
    }

    // ── Musik-Player pausieren/fortsetzen ─────────────────────────────────
    LaunchedEffect(isPaused, showReplyInput, musicPlayer) {
        val mp = musicPlayer ?: return@LaunchedEffect
        if (isPaused || showReplyInput) mp.pause() else mp.play()
    }

    // Gruppen-Infos für mehrere Fortschrittsbalken
    val currentIndex = statusGroup.indexOfFirst { it.statusId == status.statusId }
        .let { if (it < 0) 0 else it }
    val groupSize = maxOf(statusGroup.size, 1)

    // Schließen-Guard: verhindert dass die pointerInput-Zone nach dem X-Klick noch Touches konsumiert
    var isClosing by remember { mutableStateOf(false) }
    val safeOnBack: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            onBack()
        }
    }

    val goNext: () -> Unit = { if (!viewModel.advanceToNextStatus()) safeOnBack() }
    val goPrev: () -> Unit = { viewModel.advanceToPreviousStatus() }

    // Betrachter-Dialog (nur für eigene Statuses)
    if (showViewersDialog) {
        StatusViewersInlineDialog(
            viewers = statusViewers,
            onDismiss = { showViewersDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── Medieninhalt ────────────────────────────────────────────────────
        when (status.mediaType) {
            "video" -> {
                // Player via update-Callback zuweisen (fix für "nur Ton, kein Bild" beim zweiten Video)
                val currentPlayer = player
                if (currentPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false   // Keine Steuerelemente – Navigation via Tipp-Zonen
                            }
                        },
                        update = { view ->
                            view.player = currentPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            "audio" -> {
                val audioPlayer = remember(status.statusId) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(status.mediaUrl))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(status.statusId) { onDispose { audioPlayer.release() } }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MicNone,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.status_viewer_voice_cd),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp
                        )
                    }
                }
            }

            "image" -> {
                AsyncImage(
                    model = status.mediaUrl,
                    contentDescription = stringResource(R.string.status_viewer_image_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        if (status.mediaUrl.isNotBlank()) {
                            AsyncImage(
                                model = status.mediaUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TextSnippet,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = status.mediaUrl.ifBlank { stringResource(R.string.status_viewer_no_content) },
                                color = Color.White,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ─── Universelle Tipp-Zonen (alle Medientypen inkl. Video) ──────────
        // Links (30 %): vorheriger Status
        // Mitte (40 %): Haltegedrückt = pausieren, Loslassen = weiter
        // Rechts (30 %): nächster Status
        // isClosing als Key: beim Schließen wird der Block neu gestartet und sofort beendet,
        // damit keine weiteren Touches mehr konsumiert werden (verhindert gesperrte BottomBar).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(status.statusId, isClosing) {
                    if (isClosing) return@pointerInput
                    awaitEachGesture {
                        // requireUnconsumed = true: wenn ein Button (z.B. Like-Herz) den Touch
                        // bereits konsumiert hat, soll die Tipp-Zone ihn nicht auch noch verarbeiten
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()
                        val w = size.width.toFloat()
                        val x = down.position.x
                        when {
                            x < w * 0.30f -> {
                                waitForUpOrCancellation()
                                goPrev()
                            }
                            x > w * 0.70f -> {
                                waitForUpOrCancellation()
                                goNext()
                            }
                            else -> {
                                // Mittlere Zone: pausieren solange gedrückt
                                // try-finally garantiert, dass isPaused IMMER zurückgesetzt wird,
                                // auch wenn die Coroutine abgebrochen wird (z.B. durch Statuswechsel)
                                isPaused = true
                                try {
                                    waitForUpOrCancellation()
                                } finally {
                                    isPaused = false
                                }
                            }
                        }
                    }
                }
        )

        // ─── Kopfzeile ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
        ) {
            Spacer(Modifier.height(8.dp))

            // Fortschrittsbalken: ein Segment pro Status in der Gruppe
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until groupSize) {
                    val fraction = when {
                        i < currentIndex  -> 1f
                        i == currentIndex -> progressAnimatable.value
                        else              -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(1.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(Color.White, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }

            // Gradient-Hintergrund für Meta-Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(top = 10.dp, bottom = 28.dp, start = 12.dp, end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Name + Zeit
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.userName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = if (onNavigateToProfile != null)
                                Modifier.clickable { onNavigateToProfile(status.userId) }
                            else Modifier
                        )
                        Text(
                            text = "$timeAgo  ·  $remainingHours",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    // Auge: nur bei eigenen Statuses anzeigen
                    if (status.userId == currentUser?.userId) {
                        IconButton(
                            onClick = {
                                viewModel.loadStatusViewers(status.statusId)
                                showViewersDialog = true
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.RemoveRedEye,
                                contentDescription = stringResource(R.string.status_viewer_show_viewers_cd),
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Speichern: nur bei fremden Bild-Statuses (Videos dürfen nicht gespeichert werden)
                    if (status.userId != currentUser?.userId && status.mediaType == "image") {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val ok = saveImageStatusToGallery(context, status.mediaUrl)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) "Bild gespeichert" else "Speichern fehlgeschlagen",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.SaveAlt,
                                contentDescription = "Bild speichern",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Schließen
                    IconButton(
                        onClick = safeOnBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.status_viewer_close_cd),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // 3-Punkte-Menü (nur für Eigentümer: Löschen)
                    if (status.userId == currentUser?.userId) {
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Mehr",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Löschen") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.deleteStatus(status.statusId)
                                        safeOnBack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─── Like + Antwort-Buttons (nur bei fremden Statuses) ──────────────
        if (status.userId != currentUser?.userId) {
            val isLiked = likedStatusIds.contains(status.statusId)
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.likeStatus(status.statusId) }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) stringResource(R.string.status_viewer_unlike_cd) else stringResource(R.string.status_viewer_like_cd),
                        tint = if (isLiked) Color.Red else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = {
                    showReplyInput = true
                    replyText = TextFieldValue("")
                }) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Antworten",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // ─── Musik-Indikator für Bild-Statuses ───────────────────────────────
        if (status.mediaType == "image" && !status.musicUrl.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (status.userId != currentUser?.userId) 72.dp else 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .then(
                        if (onMusicClick != null) Modifier.clickable {
                            onMusicClick(status.musicUrl!!, status.musicTitle, status.musicArtist)
                        } else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFA8A800),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = buildString {
                        if (!status.musicTitle.isNullOrBlank()) append(status.musicTitle)
                        if (!status.musicArtist.isNullOrBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(status.musicArtist)
                        }
                        if (isEmpty()) append("Musik")
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // ─── Link-Chip (externer Link / APK-Download) ─────────────────────────
        if (!status.linkUrl.isNullOrBlank()) {
            val hasMusic = status.mediaType == "image" && !status.musicUrl.isNullOrBlank()
            val baseBottom = if (status.userId != currentUser?.userId) 72.dp else 20.dp
            val chipBottom = if (hasMusic) baseBottom + 44.dp else baseBottom
            val isApk = status.linkUrl!!.substringBefore('?').endsWith(".apk", ignoreCase = true)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = chipBottom, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2196F3).copy(alpha = 0.85f))
                    .clickable {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(status.linkUrl)
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "Link konnte nicht geöffnet werden",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isApk) Icons.Default.SaveAlt else Icons.Default.Link,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = status.linkLabel?.takeIf { it.isNotBlank() }
                        ?: if (isApk) "APK herunterladen" else "Link öffnen",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // ─── Antwort-Eingabe ─────────────────────────────────────────────────
        if (showReplyInput && status.userId != currentUser?.userId) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = {})  // Hintergrund-Taps konsumieren
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Antwort auf Status…", color = Color.White.copy(alpha = 0.6f)) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.7f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                        cursorColor = Color.White
                    ),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        val text = replyText.text.trim()
                        if (text.isNotBlank()) {
                            viewModel.sendMessage(
                                chatId = status.userId,
                                content = text,
                                replyToContent = status.mediaUrl.ifBlank { null },
                                replyToSenderId = status.userId,
                                replyToMediaType = "status"
                            )
                        }
                        showReplyInput = false
                        replyText = TextFieldValue("")
                    },
                    enabled = replyText.text.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Senden", tint = if (replyText.text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f))
                }
                TextButton(onClick = {
                    showReplyInput = false
                    replyText = TextFieldValue("")
                }) {
                    Text("Abbrechen", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Lädt ein fremdes Bild-Status-Bild herunter und speichert es in Pictures/Lethe.
 * Nur für Bilder – Videos werden bewusst nicht gespeichert.
 * Gibt true bei Erfolg zurück.
 */
private suspend fun saveImageStatusToGallery(context: android.content.Context, imageUrl: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            if (imageUrl.isBlank()) return@withContext false
            val resolver = context.contentResolver
            val ts = System.currentTimeMillis()
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "Lethe_Status_$ts.jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    "${android.os.Environment.DIRECTORY_PICTURES}/Lethe"
                )
            }
            val target = resolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return@withContext false
            val connection = (java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
            }
            connection.inputStream.use { input ->
                resolver.openOutputStream(target)?.use { out ->
                    input.copyTo(out)
                } ?: return@withContext false
            }
            connection.disconnect()
            true
        } catch (_: Exception) {
            false
        }
    }
}

// ─── Betrachter-Dialog direkt im Viewer ────────────────────────────────────────

@Composable
private fun StatusViewersInlineDialog(
    viewers: List<com.securechat.app.data.network.StatusViewerResponse>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.RemoveRedEye,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.status_viewer_count, viewers.size), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (viewers.isEmpty()) {
                Text(stringResource(R.string.status_viewer_nobody))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(viewers) { viewer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.DarkGray),
                                contentAlignment = Alignment.Center
                            ) {
                                if (viewer.viewerImage != null) {
                                    AsyncImage(
                                        model = if (viewer.viewerImage.startsWith("http")) viewer.viewerImage
                                                else "https://letheapp.de${viewer.viewerImage}",
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(viewer.viewerName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    if (viewer.liked) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                                    }
                                }
                                viewer.viewedAt?.let {
                                    Text(
                                        it.take(16).replace("T", " "),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.status_viewer_close_button)) }
        }
    )
}
