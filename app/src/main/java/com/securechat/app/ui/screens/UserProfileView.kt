package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.data.network.UserResponse
import androidx.compose.ui.res.stringResource
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileView(
    userId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToSparksProfile: ((String) -> Unit)? = null
) {
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val contact = contacts.find { it.userId == userId }

    var userProfile by remember { mutableStateOf<UserResponse?>(null) }
    var showFullscreenAvatar by remember { mutableStateOf(false) }
    var fullscreenMediaUrl by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current

    // Gemeinsame Medien aus dem Chat laden
    val allMessages by viewModel.getMessagesForChat(userId).collectAsState(initial = emptyList())
    val mediaMessages = remember(allMessages) {
        allMessages.filter { it.mediaType == "image" || it.mediaType == "video" }
            .sortedByDescending { it.timestamp }
    }

    // Lade Profil vom Server (enthält info/links)
    LaunchedEffect(userId) {
        userProfile = viewModel.getUserProfile(userId)
    }

    // Avatar-URL: bevorzuge Server-Profil, Fallback auf Room-Kontakt
    val avatarUrl = userProfile?.profileImageUrl ?: contact?.profileImageUrl
    val displayName = userProfile?.name ?: contact?.username ?: contact?.fakeNumber ?: stringResource(R.string.profile_unknown)
    val isAnonymous = contact?.isAnonymous ?: false
    val fakeNumber = userProfile?.fakeNumber ?: contact?.fakeNumber ?: ""
    val letheId = userProfile?.letheId ?: fakeNumber
    val info = userProfile?.info
    val links = userProfile?.links

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), color = topBarTitleColor()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_back),
                            tint = topBarTitleColor()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // --- Profilbild ---
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { if (avatarUrl != null) showFullscreenAvatar = true }
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = stringResource(R.string.profile_picture_cd),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Name ---
            Text(
                text = displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // --- Sparks-Profil-Link ---
            if (onNavigateToSparksProfile != null) {
                OutlinedButton(
                    onClick = { onNavigateToSparksProfile(userId) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sparks-Profil ansehen")
                }
                Spacer(Modifier.height(16.dp))
            }

            // --- Info-Feld ---
            if (!info.isNullOrBlank()) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.profile_info_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = info,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // --- Lethe-Nummer / LetheID ---
            if (isAnonymous) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LetheID",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = letheId,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = fakeNumber,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // --- Link-Feld ---
            if (!links.isNullOrBlank()) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable {
                            try { uriHandler.openUri(links) } catch (_: Exception) {}
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = links,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.profile_open_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // --- Medien ---
            if (mediaMessages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Medien",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        userScrollEnabled = false
                    ) {
                        items(mediaMessages) { msg ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (msg.mediaType == "image") {
                                            fullscreenMediaUrl = msg.mediaUrl
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = msg.mediaUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (msg.mediaType == "video") {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(16.dp))
        }

        // --- Vollbild-Profilbild-Dialog ---
        if (showFullscreenAvatar && avatarUrl != null) {
            Dialog(
                onDismissRequest = { showFullscreenAvatar = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { showFullscreenAvatar = false },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = stringResource(R.string.profile_picture_cd),
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // --- Vollbild-Medien-Dialog ---
        val fmUrl = fullscreenMediaUrl
        if (fmUrl != null) {
            Dialog(
                onDismissRequest = { fullscreenMediaUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { fullscreenMediaUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = fmUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}
