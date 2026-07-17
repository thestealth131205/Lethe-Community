package com.securechat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.data.network.CreatorContentResponse
import com.securechat.app.data.network.CreatorProfileResponse
import com.securechat.app.ui.MainViewModel

/**
 * Creator-Dashboard: Profilbild, Banner, Tab-Leiste (Content | Medien | Sparks).
 * Nur erreichbar für Nutzer mit is_creator = true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorsDashboard(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    navController: NavController? = null,
    onNavigateToPayout: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val creatorProfile by viewModel.creatorProfile.collectAsState()
    val creatorContent by viewModel.creatorContent.collectAsState()
    val creatorSparks by viewModel.creatorSparks.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val diamonds by viewModel.creatorDiamonds.collectAsState()
    val subscriptionPrice by viewModel.creatorSubscriptionPrice.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.creator_dash_tab_content), stringResource(R.string.creator_dash_tab_sparks))

    // Upload-Dialog-Flags
    var showBannerHint by remember { mutableStateOf(false) }
    var showAvatarHint by remember { mutableStateOf(false) }

    // FAB-Menü-Flags
    var showFabMenu by remember { mutableStateOf(false) }

    // 3-Punkte-Menü
    var showTopMenu by remember { mutableStateOf(false) }

    // Beitrag/Spark löschen
    var deleteTargetContent by remember { mutableStateOf<CreatorContentResponse?>(null) }
    var isDeletingContent by remember { mutableStateOf(false) }

    // Preis-Dialog
    var showPriceDialog by remember { mutableStateOf(false) }
    var priceInput by remember { mutableStateOf(subscriptionPrice.toString()) }

    // Username-Dialog
    var showUsernameDialog by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf(creatorProfile?.username ?: "") }

    // Image Picker für Banner
    val bannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.uploadCreatorBanner(it) } }

    // Image Picker für Profilbild
    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.uploadCreatorProfileImage(it) } }

    // Multi-Media-Picker für Spark-Erstellung (wie SparksFeedScreen + Button)
    val sparkMultiImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1 && context.contentResolver.getType(uris[0])?.startsWith("video/") == true) {
                val encodedUri = android.net.Uri.encode(uris[0].toString())
                navController?.navigate("spark_editor?videoUri=$encodedUri")
            } else {
                val encoded = uris.joinToString("|") { android.net.Uri.encode(it.toString()) }
                navController?.navigate("spark_editor_multi?uris=$encoded")
            }
        }
    }

    // Daten laden
    LaunchedEffect(Unit) {
        viewModel.loadCreatorProfile()
        viewModel.loadCreatorContent()
        viewModel.loadCreatorSparks()
    }

    // Banner-Upload-Hinweis-Dialog
    if (showBannerHint) {
        UploadHintDialog(
            title = stringResource(R.string.creator_dash_banner_title),
            hint = stringResource(R.string.creator_dash_banner_hint),
            onConfirm = {
                showBannerHint = false
                bannerLauncher.launch("image/*")
            },
            onDismiss = { showBannerHint = false }
        )
    }

    // Avatar-Upload-Hinweis-Dialog
    if (showAvatarHint) {
        UploadHintDialog(
            title = stringResource(R.string.creator_dash_avatar_title),
            hint = stringResource(R.string.creator_dash_avatar_hint),
            onConfirm = {
                showAvatarHint = false
                avatarLauncher.launch("image/*")
            },
            onDismiss = { showAvatarHint = false }
        )
    }

    // Username-Edit-Dialog
    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text(stringResource(R.string.creator_dash_username_title)) },
            text = {
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it.take(50) },
                    label = { Text(stringResource(R.string.creator_dash_username_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    showUsernameDialog = false
                    viewModel.updateCreatorSettings(null, null, username = usernameInput.trim())
                }) { Text(stringResource(R.string.creator_dash_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) { Text(stringResource(R.string.creator_dash_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.creator_dash_title),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.creator_dash_more))
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.creator_dash_spark_stats)) },
                                leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    navController?.navigate("spark_stats")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.creator_dash_livestream)) },
                                leadingIcon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    creatorProfile?.id?.let { navController?.navigate("livestream_settings/$it") }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.creator_dash_payout)) },
                                leadingIcon = { Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFFFFD700)) },
                                onClick = {
                                    showTopMenu = false
                                    onNavigateToPayout?.invoke()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFabMenu = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.creator_dash_new_content), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- HEADER: Banner + Profilbild ---
            item {
                CreatorHeader(
                    profile = creatorProfile,
                    currentUserImageUrl = currentUser?.profileImageUrl,
                    onEditBanner = { showBannerHint = true },
                    onEditAvatar = { showAvatarHint = true }
                )
            }

            // --- NAME & BIO ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentUser?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        // LIVE-Badge wenn der Creator gerade streamt
                        if (creatorProfile?.isLive == true) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE53935))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.creator_dash_live_badge),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        // Diamanten-Anzeige (klickbar → Auszahlungsscreen)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateToPayout?.invoke() }
                        ) {
                            Icon(
                                Icons.Default.Diamond,
                                contentDescription = stringResource(R.string.creator_dash_diamonds),
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "$diamonds",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        // Abo-Preis
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showPriceDialog = true }
                        ) {
                            Text(
                                text = "⚡ $subscriptionPrice Styx",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.creator_dash_change_price),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Username-Zeile
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            usernameInput = creatorProfile?.username ?: ""
                            showUsernameDialog = true
                        }
                    ) {
                        Text(
                            text = if (!creatorProfile?.username.isNullOrBlank())
                                "@${creatorProfile!!.username}" else stringResource(R.string.creator_dash_set_username),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (!creatorProfile?.username.isNullOrBlank())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.creator_dash_edit_username),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!creatorProfile?.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = creatorProfile!!.bio!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }


            // --- TAB-LEISTE ---
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                HorizontalDivider()
            }

            // --- TAB-INHALT ---
            when (selectedTab) {
                0 -> {
                    // Content-Tab
                    if (creatorContent.isEmpty()) {
                        item {
                            ContentEmptyPlaceholder(message = stringResource(R.string.creator_dash_no_content))
                        }
                    } else {
                        items(creatorContent) { content ->
                            ContentCard(
                                content = content,
                                onEdit = { navController?.navigate("content_editor/${content.id}") },
                                onDelete = { deleteTargetContent = content }
                            )
                        }
                    }
                }
                1 -> {
                    // Sparks-Tab: Letzte 10 Kurzvideos
                    if (creatorSparks.isEmpty()) {
                        item {
                            ContentEmptyPlaceholder(message = stringResource(R.string.creator_dash_no_sparks))
                        }
                    } else {
                        items(creatorSparks) { spark ->
                            SparkCard(
                                spark = spark,
                                onEdit = { navController?.navigate("content_editor/${spark.id}") },
                                onDelete = { deleteTargetContent = spark }
                            )
                        }
                    }
                }
            }
        }
    }

    // FAB-Menü
    if (showFabMenu) {
        ModalBottomSheet(onDismissRequest = { showFabMenu = false }) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.creator_dash_create_post)) },
                supportingContent = { Text(stringResource(R.string.creator_dash_create_post_subtitle)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                modifier = Modifier.clickable {
                    showFabMenu = false
                    navController?.navigate("content_editor")
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.creator_dash_create_spark)) },
                supportingContent = { Text(stringResource(R.string.creator_dash_create_spark_subtitle)) },
                leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                modifier = Modifier.clickable {
                    showFabMenu = false
                    sparkMultiImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.creator_dash_add_article_title)) },
                supportingContent = { Text(stringResource(R.string.creator_dash_add_article_subtitle)) },
                leadingContent = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                modifier = Modifier.clickable {
                    showFabMenu = false
                    navController?.navigate("creator_add_article")
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.creator_dash_create_livestream)) },
                supportingContent = { Text(stringResource(R.string.creator_dash_create_livestream_subtitle)) },
                leadingContent = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                modifier = Modifier.clickable {
                    showFabMenu = false
                    creatorProfile?.id?.let { navController?.navigate("live_room/$it") }
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    // Abo-Preis-Dialog
    if (showPriceDialog) {
        AlertDialog(
            onDismissRequest = { showPriceDialog = false },
            title = { Text(stringResource(R.string.creator_dash_price_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.creator_dash_price_free))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.creator_dash_price_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val price = priceInput.toIntOrNull() ?: subscriptionPrice
                    if (price >= 0) {
                        viewModel.updateCreatorSettings(subscriptionPrice = price, bio = null)
                        showPriceDialog = false
                    }
                }) { Text(stringResource(R.string.creator_dash_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showPriceDialog = false }) { Text(stringResource(R.string.creator_dash_cancel)) }
            }
        )
    }

    // Beitrag/Spark löschen-Dialog
    deleteTargetContent?.let { content ->
        val isSpark = content.mediaType == "spark" || content.mediaType == "image_spark"
        AlertDialog(
            onDismissRequest = { if (!isDeletingContent) deleteTargetContent = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(if (isSpark) stringResource(R.string.creator_dash_delete_spark_title) else stringResource(R.string.creator_dash_delete_post_title), color = MaterialTheme.colorScheme.error) },
            text = {
                Text(stringResource(R.string.creator_dash_delete_confirm, content.title ?: (if (isSpark) stringResource(R.string.creator_dash_tab_sparks) else stringResource(R.string.creator_dash_tab_content))))
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingContent = true
                        viewModel.deleteCreatorContent(content.id) { success ->
                            isDeletingContent = false
                            deleteTargetContent = null
                        }
                    },
                    enabled = !isDeletingContent,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeletingContent) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text(stringResource(R.string.creator_dash_delete_button))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isDeletingContent) deleteTargetContent = null }) { Text(stringResource(R.string.creator_dash_cancel)) }
            }
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Creator Header: Banner (volle Breite) + Profilbild (links, überlagert)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun CreatorHeader(
    profile: CreatorProfileResponse?,
    currentUserImageUrl: String?,
    onEditBanner: () -> Unit,
    onEditAvatar: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!profile?.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile!!.bannerUrl,
                    contentDescription = stringResource(R.string.creator_dash_banner_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Bleistift-Button oben rechts
            IconButton(
                onClick = onEditBanner,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.creator_dash_edit_banner),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Profilbild (links unten, überlagert Banner)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp)
        ) {
            // Weißer Rahmen + Profilbild
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(3.dp)
            ) {
                AsyncImage(
                    model = profile?.profileImageUrl ?: currentUserImageUrl,
                    contentDescription = stringResource(R.string.creator_dash_profile_image_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            // Bleistift-Button unten rechts am Profilbild
            IconButton(
                onClick = onEditAvatar,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(26.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.creator_dash_edit_avatar),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Upload-Hinweis-Dialog
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun UploadHintDialog(
    title: String,
    hint: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.creator_dash_choose_image)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.creator_dash_cancel)) }
        }
    )
}

// ────────────────────────────────────────────────────────────────────────────
// Content-Karte (Facebook-Stil)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContentCard(content: CreatorContentResponse, onEdit: () -> Unit = {}, onDelete: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!content.title.isNullOrBlank()) {
                    Text(
                        text = content.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (!content.description.isNullOrBlank()) {
                    Text(
                        text = content.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Preview-Bild (geblurt um Creator zu zeigen wie es für User aussieht)
                val previewUrl = content.previewImageUrl ?: content.mediaUrl
                if (!previewUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = previewUrl,
                            contentDescription = stringResource(R.string.creator_dash_content_preview),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (content.styxCost > 0)
                                        Modifier.run { this } // Blur via renderscript nicht nötig, Placeholder reicht
                                    else Modifier
                                )
                        )
                        if (content.styxCost > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "⚡ ${content.styxCost} Styx",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                if (content.styxCost > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ ${content.styxCost} Styx",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            // Bearbeiten + Löschen oben rechts
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.creator_dash_edit),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.creator_dash_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Spark-Karte (Kurzvideo)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun SparkCard(spark: CreatorContentResponse, onEdit: () -> Unit = {}, onDelete: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 9:16 Portrait-Thumbnail
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A2E)),
                contentAlignment = Alignment.Center
            ) {
                val thumbnailUrl = spark.previewImageUrl?.takeIf { it.isNotBlank() }
                    ?: spark.mediaUrl?.takeIf { it.isNotBlank() }
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = stringResource(R.string.creator_dash_spark_thumbnail),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Play-Icon-Overlay
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.creator_dash_play),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spark.title ?: "Spark",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!spark.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = spark.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (spark.styxCost > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚡ ${spark.styxCost} Styx",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.creator_dash_edit),
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.creator_dash_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        } // end Box
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Leerer Zustand
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContentEmptyPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}
