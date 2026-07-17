package com.securechat.app.ui.screens

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.data.network.NearbyMatch
import com.securechat.app.data.network.NearbyMessage
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyMatchChatScreen(
    matchId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTiltNDrop: () -> Unit = {},
    onNavigateToNearbyDetail: ((String) -> Unit)? = null,
    fontSizeMultiplier: Float = 1.0f
) {
    val messages by viewModel.nearbyMessages.collectAsState()
    val matches by viewModel.nearbyMatches.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val pendingLetheInvite by viewModel.pendingLetheInvite.collectAsState()
    val hasDatingPlus by viewModel.hasVisitorsSubscription.collectAsState()

    val match = matches.find { it.id == matchId }
    val myId = currentUser?.userId ?: ""

    var inputText by remember { mutableStateOf("") }
    var showLetheInviteConfirm by remember { mutableStateOf(false) }
    var showMenuExpanded by remember { mutableStateOf(false) }
    var showImageSendDialog by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showAgeVerifyDialog by remember { mutableStateOf(false) }
    var showDatingPlusDialog by remember { mutableStateOf(false) }

    val isAgeVerified = currentUser?.ageVerified == true
    val canChat = isAgeVerified || match?.viaUsername == true

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Eingehende Lethe-Einladung → Dialog anzeigen
    val incomingInvite = pendingLetheInvite
    var showIncomingLetheDialog by remember { mutableStateOf(false) }
    LaunchedEffect(incomingInvite) {
        if (incomingInvite?.id == matchId) {
            showIncomingLetheDialog = true
        }
    }

    // Nachrichten beim Öffnen laden + Match als aktiv markieren (unterdrückt Notifications + setzt Unread-Dot zurück)
    LaunchedEffect(matchId) {
        viewModel.onNearbyMatchOpened(matchId)
        viewModel.loadNearbyMessages(matchId)
    }

    // Beim Schließen activeMatchId zurücksetzen
    DisposableEffect(matchId) {
        onDispose {
            viewModel.clearActiveMatch()
            viewModel.onNearbyMatchClosed()
        }
    }

    // Automatisch nach unten scrollen bei neuen Nachrichten
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    // Bildauswahl-Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
            showImageSendDialog = true
        }
    }

    // Vollbild-Bild-Viewer (mit FLAG_SECURE)
    if (fullscreenImageUrl != null) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (context as? androidx.activity.ComponentActivity)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                fullscreenImageUrl = null
            }
        }
        Dialog(onDismissRequest = { fullscreenImageUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullscreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullscreenImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // NearbyImageSendDialog
    if (showImageSendDialog && pendingImageUri != null) {
        NearbyImageSendDialog(
            onConfirm = { viewMode ->
                showImageSendDialog = false
                val uri = pendingImageUri
                if (uri != null) {
                    viewModel.sendNearbyMessage(
                        matchId = matchId,
                        content = null,
                        mediaType = "image",
                        viewMode = viewMode,
                        imageUri = uri
                    )
                }
                pendingImageUri = null
            },
            onDismiss = {
                showImageSendDialog = false
                pendingImageUri = null
            }
        )
    }

    // Altersverifikation erforderlich
    if (showAgeVerifyDialog) {
        AlertDialog(
            onDismissRequest = { showAgeVerifyDialog = false },
            title = { Text("Altersverifikation erforderlich", fontWeight = FontWeight.Bold) },
            text = { Text("Bitte verifiziere erst dein Alter, um Nachrichten senden zu können.") },
            confirmButton = {
                Button(onClick = { showAgeVerifyDialog = false }) { Text("OK") }
            }
        )
    }

    // Lethe-Einladen-Dialog (selbst initiiert)
    if (showLetheInviteConfirm) {
        AlertDialog(
            onDismissRequest = { showLetheInviteConfirm = false },
            title = { Text(stringResource(R.string.nearby_chat_share_lethe_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(
                        R.string.nearby_chat_share_lethe_text,
                        match?.partnerUsername ?: stringResource(R.string.nearby_chat_title_fallback)
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.inviteToLethe(matchId)
                    showLetheInviteConfirm = false
                }) {
                    Text(stringResource(R.string.nearby_chat_share_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLetheInviteConfirm = false }) { Text(stringResource(R.string.general_cancel)) }
            }
        )
    }

    // Eingehende Lethe-Einladung Dialog
    if (showIncomingLetheDialog && incomingInvite != null) {
        AlertDialog(
            onDismissRequest = {
                showIncomingLetheDialog = false
                viewModel.clearPendingLetheInvite()
            },
            title = { Text(stringResource(R.string.nearby_chat_lethe_request_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.nearby_chat_lethe_request_text, incomingInvite.partnerUsername)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.inviteToLethe(matchId) // accept-lethe-invite
                    showIncomingLetheDialog = false
                    viewModel.clearPendingLetheInvite()
                }) {
                    Text(stringResource(R.string.nearby_chat_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showIncomingLetheDialog = false
                    viewModel.clearPendingLetheInvite()
                }) { Text(stringResource(R.string.nearby_chat_decline)) }
            }
        )
    }

    // Nearby Dating Plus – Kauf-Dialog (Game-Button ohne aktives Abo)
    if (showDatingPlusDialog) {
        AlertDialog(
            onDismissRequest = { showDatingPlusDialog = false },
            title = { Text("Nearby Dating Plus", fontWeight = FontWeight.Bold) },
            text = {
                Text("Diese Funktion steht nur mit dem Nearby Dating Plus Paket zur Verfügung.\n\nMonatspreis: 300 Styx")
            },
            confirmButton = {
                Button(onClick = {
                    showDatingPlusDialog = false
                    viewModel.subscribeToNearbyVisitors(
                        onSuccess = {
                            match?.let { m ->
                                viewModel.setTiltNDropParams(m.partnerId, m.partnerUsername, isHost = true)
                                viewModel.sendGameWsMessage(
                                    "game_invite", m.partnerId,
                                    mapOf(
                                        "from_name" to (currentUser?.name ?: ""),
                                        "game_type" to "TILT_N_DROP"
                                    )
                                )
                                onNavigateToTiltNDrop()
                            }
                        },
                        onError = { /* Styx-Fehler wird durch snackbar im ViewModel behandelt */ }
                    )
                }) {
                    Text("Bezahlen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatingPlusDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .then(
                                    if (onNavigateToNearbyDetail != null && match != null)
                                        Modifier.clickable {
                                            viewModel.loadNearbyProfileById(match.partnerId)
                                            onNavigateToNearbyDetail(match.partnerId)
                                        }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!match?.partnerImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = match!!.partnerImageUrl!!.let { url ->
                                        if (url.startsWith("http")) url else "https://letheapp.de$url"
                                    },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            match?.partnerUsername ?: stringResource(R.string.nearby_chat_title_fallback),
                            fontWeight = FontWeight.SemiBold,
                            color = topBarTitleColor()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.nearby_chat_menu_cd))
                        }
                        DropdownMenu(
                            expanded = showMenuExpanded,
                            onDismissRequest = { showMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.nearby_chat_add_as_lethe)) },
                                onClick = {
                                    showMenuExpanded = false
                                    showLetheInviteConfirm = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NearbyMessageInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (!canChat) {
                        showAgeVerifyDialog = true
                    } else if (inputText.isNotBlank()) {
                        viewModel.sendNearbyMessage(matchId, inputText.trim())
                        inputText = ""
                    }
                },
                onAttachImage = {
                    if (!canChat) showAgeVerifyDialog = true
                    else imagePickerLauncher.launch("image/*")
                },
                onGifClick = {
                    if (!canChat) showAgeVerifyDialog = true
                    else showGifPicker = true
                },
                onGameClick = {
                    if (hasDatingPlus) {
                        match?.let { m ->
                            viewModel.setTiltNDropParams(m.partnerId, m.partnerUsername, isHost = true)
                            viewModel.sendGameWsMessage(
                                "game_invite", m.partnerId,
                                mapOf(
                                    "from_name" to (currentUser?.name ?: ""),
                                    "game_type" to "TILT_N_DROP"
                                )
                            )
                            onNavigateToTiltNDrop()
                        }
                    } else {
                        showDatingPlusDialog = true
                    }
                }
            )
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.nearby_chat_no_messages),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.nearby_chat_write_first, match?.partnerUsername ?: stringResource(R.string.nearby_chat_title_fallback)),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    NearbyMessageBubble(
                        message = msg,
                        isOwnMessage = msg.senderId == myId,
                        onImageClick = { url -> fullscreenImageUrl = url },
                        onView = { viewModel.markNearbyMessageViewed(msg.id) },
                        fontSizeMultiplier = fontSizeMultiplier
                    )
                }
            }
        }
    }
    // GIF-Picker BottomSheet
    if (showGifPicker) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showGifPicker = false }
        ) {
            GiphyPickerSheet(
                viewModel = viewModel,
                onGifSelected = { gifUrl ->
                    showGifPicker = false
                    viewModel.sendNearbyMessage(
                        matchId = matchId,
                        content = null,
                        mediaType = "gif",
                        directMediaUrl = gifUrl
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            )
        }
    }
}

@Composable
private fun NearbyMessageBubble(
    message: NearbyMessage,
    isOwnMessage: Boolean,
    onImageClick: (String) -> Unit,
    onView: () -> Unit,
    fontSizeMultiplier: Float = 1.0f
) {
    val alignment = if (isOwnMessage) Alignment.Start else Alignment.End
    val bubbleColor = if (isOwnMessage)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else
        MaterialTheme.colorScheme.surfaceVariant

    val nearbyBubbleDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(nearbyBubbleDensity.density, nearbyBubbleDensity.fontScale * fontSizeMultiplier)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isOwnMessage) 4.dp else 16.dp,
                bottomEnd = if (isOwnMessage) 16.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                when (message.mediaType) {
                    "image" -> {
                        val isLimited = message.viewMode in listOf("once", "twice")
                        val maxViews = if (message.viewMode == "once") 1 else 2
                        val isExpired = isLimited && message.viewCount >= maxViews
                        val hasNoUrl = message.mediaUrl == null && message.previewUrl == null

                        if (isExpired || hasNoUrl) {
                            // Abgelaufen oder kein Bild verfügbar
                            Box(
                                modifier = Modifier
                                    .size(200.dp, 150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ImageNotSupported, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Text(stringResource(R.string.nearby_chat_expired), fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                }
                            }
                        } else if (isLimited) {
                            // Noch nicht oder nur teilweise angesehen: Blur-Preview anzeigen
                            // previewUrl = serverseitig generiertes Blur-Bild (bevorzugt)
                            // Fallback: Original-URL mit Compose-Blur
                            val displayUrl = when {
                                !message.previewUrl.isNullOrBlank() ->
                                    message.previewUrl.let { url ->
                                        if (url.startsWith("http")) url else "https://letheapp.de$url"
                                    }
                                !message.mediaUrl.isNullOrBlank() ->
                                    message.mediaUrl.let { url ->
                                        if (url.startsWith("http")) url else "https://letheapp.de$url"
                                    }
                                else -> null
                            }
                            val usesComposeBlur = message.previewUrl.isNullOrBlank()
                            val originalUrl = message.mediaUrl?.let { url ->
                                if (url.startsWith("http")) url else "https://letheapp.de$url"
                            }
                            val viewsLeft = maxViews - message.viewCount

                            if (displayUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .size(200.dp, 150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (originalUrl != null) {
                                                onView()
                                                onImageClick(originalUrl)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = displayUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (usesComposeBlur) Modifier.blur(50.dp)
                                                else Modifier
                                            )
                                    )
                                    // Overlay mit Auge-Icon und Anzahl
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.25f))
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (viewsLeft > 0) stringResource(R.string.nearby_chat_view_count, viewsLeft) else stringResource(R.string.nearby_chat_tap_to_view),
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Dauerhaft sichtbar: normal anzeigen
                            val absoluteUrl = message.mediaUrl!!.let { url ->
                                if (url.startsWith("http")) url else "https://letheapp.de$url"
                            }
                            AsyncImage(
                                model = absoluteUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp, 150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onImageClick(absoluteUrl) }
                            )
                        }
                    }

                    "gif" -> {
                        val gifUrl = message.mediaUrl
                        if (!gifUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = gifUrl,
                                contentDescription = "GIF",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp, 150.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    "audio" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (message.viewMode == "once") stringResource(R.string.nearby_chat_voice_once) else stringResource(R.string.nearby_chat_voice),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        // Text – Spark-Link erkennen (lethe://post?C=uuid)
                        val content = message.content
                        if (!content.isNullOrBlank()) {
                            val sparkLinkMatch = Regex("""^lethe://post\?C=([a-zA-Z0-9_-]+)$""")
                                .find(content.trim())
                            if (sparkLinkMatch != null) {
                                val contentId = sparkLinkMatch.groupValues[1]
                                val ctx = LocalContext.current
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        .clickable {
                                            try {
                                                ctx.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("lethe://post?C=$contentId")
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
                                        Text(stringResource(R.string.nearby_chat_spark_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(stringResource(R.string.nearby_chat_tap_to_open), fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                            } else {
                                Text(
                                    text = content,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    } // end CompositionLocalProvider (fontSizeMultiplier)
}

@Composable
private fun NearbyMessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    onGifClick: () -> Unit = {},
    onGameClick: () -> Unit = {}
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bildanhang
            IconButton(onClick = onAttachImage) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = stringResource(R.string.nearby_chat_attach_image_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // GIF-Button
            IconButton(onClick = onGifClick) {
                Icon(
                    Icons.Default.Gif,
                    contentDescription = "GIF senden",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Game-Button (Nearby Dating Plus)
            IconButton(onClick = onGameClick) {
                Icon(
                    Icons.Default.SportsEsports,
                    contentDescription = "Spiel starten",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Texteingabe
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.nearby_chat_message_placeholder), fontSize = 14.sp) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Senden
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.general_send),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun NearbyImageSendDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf("permanent") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nearby_chat_image_send_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val viewOnce = stringResource(R.string.nearby_chat_view_once)
                val viewTwice = stringResource(R.string.nearby_chat_view_twice)
                val viewPermanent = stringResource(R.string.nearby_chat_view_permanent)
                listOf(
                    "once" to viewOnce,
                    "twice" to viewTwice,
                    "permanent" to viewPermanent
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMode) }) { Text(stringResource(R.string.general_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}
