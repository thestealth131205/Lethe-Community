package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.data.network.LiveChatMessage
import androidx.compose.ui.res.stringResource
import com.securechat.app.ui.MainViewModel
import kotlinx.coroutines.launch

/**
 * EPIC 5+8: Vollbild-Livestream-Screen mit HLS-Video (ExoPlayer) und WebSocket-Chat-Overlay.
 * Moderatoren können per Long-Press auf fremde Nachrichten Aktionen auslösen (warn/kick/ban).
 * Wenn der aktuelle Nutzer der Creator ist, wird stattdessen der Creator-Flow angezeigt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRoomScreen(
    creatorId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    // Creator-Flow: eigenes Profil → Konfig-Screen → Broadcast-Screen
    val myCreatorProfile by viewModel.creatorProfile.collectAsState()
    if (myCreatorProfile?.id == creatorId) {
        var liveConfig by remember { mutableStateOf<LiveStreamConfig?>(null) }
        if (liveConfig == null) {
            LiveStreamConfigScreen(
                viewModel = viewModel,
                onStartStream = { config -> liveConfig = config },
                onNavigateBack = onNavigateBack
            )
        } else {
            CreatorLiveScreen(
                creatorId = creatorId,
                viewModel = viewModel,
                config = liveConfig!!,
                onNavigateBack = onNavigateBack
            )
        }
    } else {
        val liveChatMessages by viewModel.liveChatMessages.collectAsState()
        val liveViewerCount by viewModel.liveViewerCount.collectAsState()
        val isConnected by viewModel.isLiveChatConnected.collectAsState()
        val currentUser by viewModel.currentUser.collectAsState()

        var hlsUrl by remember { mutableStateOf<String?>(null) }
        var messageText by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val keyboardController = LocalSoftwareKeyboardController.current
        @Suppress("DEPRECATION")
        val clipboardManager = LocalClipboardManager.current
        var showShareToast by remember { mutableStateOf(false) }
        val shareUrl = "https://letheapp.de/live?c=$creatorId"

        // EPIC 8: Moderator-BottomSheet
        var selectedMessage by remember { mutableStateOf<LiveChatMessage?>(null) }
        var showModSheet by remember { mutableStateOf(false) }

        // Connect on entry, disconnect on leave
        DisposableEffect(creatorId) {
            viewModel.connectLiveChat(creatorId)
            onDispose { viewModel.disconnectLiveChat() }
        }

        // Load HLS URL for this creator
        LaunchedEffect(creatorId) {
            viewModel.loadCreatorLiveInfo(creatorId) { info ->
                hlsUrl = info?.hlsUrl
            }
        }

        // Auto-scroll to newest message
        LaunchedEffect(liveChatMessages.size) {
            if (liveChatMessages.isNotEmpty()) {
                coroutineScope.launch {
                    listState.animateScrollToItem(liveChatMessages.size - 1)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // === VIDEO PLAYER ===
            val hlsUrlValue = hlsUrl
            if (!hlsUrlValue.isNullOrBlank()) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val exoPlayer = remember(hlsUrlValue) {
                    ExoPlayer.Builder(ctx).build().apply {
                        setMediaItem(MediaItem.fromUri(hlsUrlValue))
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                )
            } else {
                // Placeholder while stream loads
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.live_room_stream_loading), color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            // Dark gradient overlay (bottom half for chat)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // === TOP BAR ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 8.dp, end = 16.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back), tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                // LIVE badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("● LIVE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(8.dp))
                // Viewer count
                Text("👁 $liveViewerCount", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                // Share button
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(shareUrl))
                    showShareToast = true
                }) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.live_room_share_cd), tint = Color.White)
                }
                // Connection dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF4CAF50) else Color(0xFFBDBDBD))
                )
            }

            // === CHAT OVERLAY (bottom) ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(liveChatMessages, key = { "${it.userId}_${it.timestamp}_${it.text}" }) { msg ->
                        LiveChatBubble(
                            message = msg,
                            isOwnMessage = msg.userId == currentUser?.userId,
                            onLongPress = {
                                if (msg.userId != currentUser?.userId && !msg.isSystem) {
                                    selectedMessage = msg
                                    showModSheet = true
                                }
                            }
                        )
                    }
                }

                // Input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text(stringResource(R.string.live_room_message_placeholder), color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.White
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendLiveChatMessage(messageText)
                                messageText = ""
                                keyboardController?.hide()
                            }
                        }),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendLiveChatMessage(messageText)
                                messageText = ""
                                keyboardController?.hide()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.live_room_send_cd), tint = Color(0xFFA8A800))
                    }
                }
            }

            // "Link kopiert"-Toast
            if (showShareToast) {
                LaunchedEffect(showShareToast) {
                    kotlinx.coroutines.delay(2000)
                    showShareToast = false
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.live_room_link_copied), color = Color.White, fontSize = 13.sp)
                }
            }
        }

        // EPIC 8: Moderator BottomSheet
        if (showModSheet && selectedMessage != null) {
            val msg = selectedMessage!!
            ModalBottomSheet(
                onDismissRequest = { showModSheet = false; selectedMessage = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.live_room_mod_title, msg.userName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    ModActionButton("⚠️ Verwarnen", Color(0xFFFFA726)) {
                        viewModel.sendLiveChatModAction("warn", msg.userId, "warn")
                        showModSheet = false; selectedMessage = null
                    }
                    ModActionButton("🚫 Kicken", Color(0xFFEF5350)) {
                        viewModel.sendLiveChatModAction("kick", msg.userId, "kick")
                        showModSheet = false; selectedMessage = null
                    }
                    ModActionButton("🔒 Bannen", Color(0xFFB71C1C)) {
                        viewModel.sendLiveChatModAction("ban", msg.userId, "ban")
                        showModSheet = false; selectedMessage = null
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showModSheet = false; selectedMessage = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.general_cancel)) }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveChatBubble(
    message: LiveChatMessage,
    isOwnMessage: Boolean,
    onLongPress: () -> Unit
) {
    if (message.isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                message.text,
                color = Color.Yellow.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isOwnMessage) {
            AsyncImage(
                model = message.profileImageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Gray.copy(alpha = 0.4f))
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isOwnMessage) Color(0xFFA8A800).copy(alpha = 0.85f)
                    else Color.Black.copy(alpha = 0.55f)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .widthIn(max = 260.dp)
        ) {
            if (!isOwnMessage) {
                Text(
                    message.userName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFD700),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                message.text,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModActionButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
