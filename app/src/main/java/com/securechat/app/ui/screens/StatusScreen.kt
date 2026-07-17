package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.data.local.StatusEntity
import com.securechat.app.ui.MainViewModel
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.ui.theme.topBarTitleColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    viewModel: MainViewModel,
    onNavigateToCreate: () -> Unit = {},
    onNavigateToCreateWithImage: (Uri) -> Unit = {},
    onNavigateToCreateWithVideo: (Uri) -> Unit = {},
    onNavigateToStatus: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // 1. Lokale DB-Flows beobachten
    val statuses by viewModel.activeStatuses.collectAsState(initial = emptyList())
    val myStatuses by viewModel.myStatuses.collectAsState(initial = emptyList())
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val currentUser by viewModel.currentUser.collectAsState()

    // 2. Eigene, nicht abgelaufene Status-Beiträge
    val validMyStatuses = remember(myStatuses) {
        val now = System.currentTimeMillis()
        myStatuses.filter { it.expiresAt > now }
    }

    // 3. Kontakt-Statuse nach userId gruppieren (neueste zuerst pro Gruppe, Gruppen nach Aktualität)
    val groupedStatuses = remember(statuses, currentUser?.userId) {
        val now = System.currentTimeMillis()
        statuses
            // Abgelaufene Status-Beiträge herausfiltern
            .filter { it.expiresAt > now }
            // EIGENE Status herausfiltern, damit sie nicht unter "Kontakte" auftauchen
            .filter { it.userId != currentUser?.userId }
            .groupBy { it.userId }
            .values
            .map { it.sortedBy { s -> s.createdAt } }  // älteste zuerst innerhalb einer Gruppe
            .sortedByDescending { it.last().createdAt }  // Gruppe mit neuestem Status oben
    }

    // Kamera-URI für Foto
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraPhotoUri?.let { viewModel.setPendingStatusUri("image", it) }
    }
    // Video-URI für Video
    var cameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) cameraVideoUri?.let { viewModel.setPendingStatusUri("video", it) }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun launchPhotoCapture() {
        val file = File(context.cacheDir, "status/photo_${System.currentTimeMillis()}.jpg").also { it.parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraPhotoUri = uri
        cameraPhotoLauncher.launch(uri)
    }
    fun launchVideoCapture() {
        val file = File(context.cacheDir, "status/video_${System.currentTimeMillis()}.mp4").also { it.parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraVideoUri = uri
        cameraVideoLauncher.launch(uri)
    }

    // Navigiere mit URI via ViewModel-State
    // WICHTIG: NICHT vor Navigation löschen – StatusCreationScreen liest den Wert noch
    val pendingStatusUri by viewModel.pendingStatusUri.collectAsState()
    LaunchedEffect(pendingStatusUri) {
        pendingStatusUri?.let { (type, _) ->
            // URI bleibt gesetzt; StatusCreationScreen liest sie und löscht sie dann selbst
            if (type == "image") onNavigateToCreateWithImage(android.net.Uri.EMPTY)
            else onNavigateToCreateWithVideo(android.net.Uri.EMPTY)
        }
    }

    // 4. BEIM ÖFFNEN DES SCREENS: Server nach neuen Daten fragen!
    LaunchedEffect(Unit) {
        viewModel.cleanupExpiredStatuses()
        // Lädt die eigenen Statusbeiträge vom Server
        viewModel.loadMyStatuses()
        // GANZ WICHTIG: Lädt die freigegebenen Statusbeiträge der Kontakte vom Server!
        // Ohne diesen Aufruf bleibt die lokale Liste "activeStatuses" leer/veraltet.
        viewModel.loadContactStatuses()
    }

    // 5. Avatar-Klick aus Kontaktliste: Ausstehende Status-Navigation auflösen
    val pendingStatusNavContact by viewModel.pendingStatusNavContact.collectAsState()
    LaunchedEffect(pendingStatusNavContact, statuses) {
        val contactId = pendingStatusNavContact ?: return@LaunchedEffect
        val statusId = statuses.firstOrNull { it.userId == contactId }?.statusId
        if (statusId != null) {
            viewModel.clearPendingStatusNavContact()
            onNavigateToStatus(statusId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.status_title), color = topBarTitleColor()) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { launchPhotoCapture() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.status_take_photo_cd))
                }
                SmallFloatingActionButton(
                    onClick = { launchVideoCapture() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = stringResource(R.string.status_take_video_cd))
                }
                FloatingActionButton(
                    onClick = onNavigateToCreate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.status_create_headline))
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
                // ─── Mein Status ─────────────────────────────────────
                item {
                    Text(
                        stringResource(R.string.status_my_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (validMyStatuses.isEmpty()) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.status_create_headline), fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.status_create_hint),
                                    fontSize = 12.sp
                                )
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.clickable(onClick = onNavigateToCreate)
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                } else {
                    // Alle eigenen Statuse als EINE Gruppe – genau ein Eintrag in der Liste
                    item {
                        val sortedMine = validMyStatuses.sortedBy { it.createdAt }  // älteste zuerst
                        val newestOwn = sortedMine.last()  // neuester für Vorschau
                        StatusItem(
                            status = newestOwn,
                            avatarUrl = currentUser?.profileImageUrl ?: newestOwn.userImage,
                            isOwn = true,
                            statusCount = validMyStatuses.size,
                            onClick = {
                                viewModel.prepareStatusGroup(sortedMine)
                                onNavigateToStatus(sortedMine.first().statusId)  // ältester zuerst
                            }
                        )
                    }
                }

                // ─── Kontakt-Status ───────────────────────────────────
                if (groupedStatuses.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.status_contacts_section),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    // Pro Kontakt nur ein Eintrag, bei mehreren Statussen mit Zähler-Badge
                    items(groupedStatuses, key = { it.first().userId }) { userStatuses ->
                        val firstStatus = userStatuses.first()
                        // Profilbild aus der Kontaktliste priorisieren, sonst Fallback auf das Status-Bild
                        val avatarUrl = contacts.find { it.userId == firstStatus.userId }
                            ?.profileImageUrl
                            ?: firstStatus.userImage

                        StatusItem(
                            status = firstStatus,
                            avatarUrl = avatarUrl,
                            isOwn = false,
                            statusCount = userStatuses.size,
                            onClick = {
                                viewModel.prepareStatusGroup(userStatuses)
                                onNavigateToStatus(firstStatus.statusId)
                            }
                        )
                    }
                }
        }
    }
}


@Composable
private fun StatusItem(
    status: StatusEntity,
    avatarUrl: String?,
    isOwn: Boolean = false,
    statusCount: Int = 1,
    onClick: () -> Unit = {}
) {
    val justNowText = stringResource(R.string.status_just_now)
    val timeText = remember(status.createdAt, justNowText) {
        val diff = System.currentTimeMillis() - status.createdAt
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        when {
            minutes < 5 -> justNowText
            else        -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(status.createdAt))
        }
    }

    val typeIcon = when (status.mediaType) {
        "video" -> "📹"
        "audio" -> "🎵"
        else    -> "🖼"
    }

    ListItem(
        headlineContent = {
            Text(
                if (isOwn) stringResource(R.string.status_my_section) else status.userName,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Text(
                timeText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(3.dp)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Zähler-Badge bei mehreren Statussen
                if (statusCount > 1) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "$statusCount",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text(text = typeIcon, fontSize = 20.sp)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(Modifier.padding(start = 80.dp))
}

