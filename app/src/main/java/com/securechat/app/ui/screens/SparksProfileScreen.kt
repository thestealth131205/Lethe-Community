package com.securechat.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.securechat.app.data.network.CreatorContentResponse
import com.securechat.app.data.network.PublicCreatorProfileResponse
import com.securechat.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparksProfileScreen(
    creatorId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSpark: (sparkId: String) -> Unit = {}
) {
    val currentUser by viewModel.currentUser.collectAsState()

    var profile by remember { mutableStateOf<PublicCreatorProfileResponse?>(null) }
    var isProfileLoading by remember { mutableStateOf(true) }
    var isContentLoading by remember { mutableStateOf(true) }
    val isLoading = isProfileLoading || isContentLoading
    val sparks by viewModel.creatorPublicFeed.collectAsState()
    val savedSparks by viewModel.savedSparks.collectAsState()
    val likedSparks by viewModel.likedSparks.collectAsState()

    // Eigenes Profil: direkt per creatorId == userId (zuverlässig, ohne auf Profilladen zu warten),
    // oder per userId-Vergleich aus der Server-Antwort als Fallback.
    val isOwnProfile = creatorId == currentUser?.userId
        || (profile?.userId != null && profile?.userId == currentUser?.userId)

    // 0 = Videos, 1 = Gespeichert, 2 = Geliked
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(creatorId) {
        isProfileLoading = true
        isContentLoading = true
        viewModel.loadPublicCreatorProfile(creatorId) { result ->
            profile = result
            isProfileLoading = false
        }
        viewModel.loadCreatorPublicContent(creatorId) {
            isContentLoading = false
        }
    }

    // Saved/Liked-Listen laden sobald klar ist dass es das eigene Profil ist
    LaunchedEffect(isOwnProfile) {
        if (isOwnProfile) {
            viewModel.loadSavedSparks()
            viewModel.loadLikedSparks()
        }
    }

    val sortedSparks = remember(sparks) {
        sparks
            .filter { it.mediaType == "spark" || it.mediaType == "image_spark" }
            .sortedByDescending { it.createdAt }
    }

    val displayedSparks = when {
        !isOwnProfile -> sortedSparks
        selectedTab == 0 -> sortedSparks
        selectedTab == 1 -> savedSparks
        else -> likedSparks
    }

    val displayName = profile?.username ?: profile?.name
        ?: if (isOwnProfile) (currentUser?.name?.takeIf { it.isNotBlank() } ?: currentUser?.fakeNumber ?: "") else ""
    val displayImageUrl = profile?.profileImageUrl
        ?: if (isOwnProfile) currentUser?.profileImageUrl else null

    var showShareDialogSpark by remember { mutableStateOf<CreatorContentResponse?>(null) }
    var showDeleteDialogSpark by remember { mutableStateOf<CreatorContentResponse?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Header spans all 3 columns
            item(span = { GridItemSpan(3) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profilbild
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!displayImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = displayImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = displayName.ifBlank { "—" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Abonnenten",
                            value = formatCount(profile?.subscriberCount ?: 0)
                        )
                        StatItem(
                            label = "Folge ich",
                            value = formatCount(profile?.mySubscriptionsCount ?: 0)
                        )
                        StatItem(
                            label = "Aufrufe",
                            value = formatCount(profile?.totalViews ?: 0)
                        )
                        StatItem(
                            label = "Likes",
                            value = formatCount(profile?.totalLikes ?: 0)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tabs nur für eigenes Profil
                    if (isOwnProfile) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            ) {
                                Icon(
                                    Icons.Filled.VideoLibrary,
                                    contentDescription = "Videos",
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            ) {
                                Icon(
                                    Icons.Filled.Bookmark,
                                    contentDescription = "Gespeichert",
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 }
                            ) {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = "Geliked",
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Spark-Kacheln
            if (displayedSparks.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (selectedTab) {
                                1 -> "Noch keine gespeicherten Videos"
                                2 -> "Noch keine gelikten Videos"
                                else -> "Noch keine Videos"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(displayedSparks) { spark ->
                    SparkTile(
                        spark = spark,
                        isOwn = isOwnProfile && selectedTab == 0,
                        onClick = {
                            // feedOverride setzen damit SparksFeedScreen die Profil-Sparks anzeigt,
                            // nicht den globalen VIP-Feed. loadVipFeed (ON_RESUME) überschreibt
                            // prependSparkToFeed, daher feedOverride nutzen.
                            viewModel.setSparkFeedOverride(displayedSparks)
                            viewModel.setPendingSparkId(spark.id)
                            onNavigateToSpark(spark.id)
                        },
                        onShare = { showShareDialogSpark = spark },
                        onDelete = { showDeleteDialogSpark = spark }
                    )
                }
            }
        }
    }

    showShareDialogSpark?.let { spark ->
        SparkShareSheet(
            spark = spark,
            viewModel = viewModel,
            onDismiss = { showShareDialogSpark = null }
        )
    }

    showDeleteDialogSpark?.let { spark ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogSpark = null },
            title = { Text("Spark löschen?") },
            text = { Text("Dieser Spark wird dauerhaft gelöscht. Alle Likes, Kommentare und Dateien werden entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialogSpark = null
                    viewModel.deleteCreatorContent(spark.id) { success ->
                        if (success) viewModel.loadCreatorPublicContent(creatorId)
                    }
                }) {
                    Text("Löschen", color = androidx.compose.ui.graphics.Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogSpark = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun SparkTile(
    spark: CreatorContentResponse,
    onClick: () -> Unit,
    isOwn: Boolean = false,
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val imageUrl = spark.previewImageUrl?.takeIf { it.isNotBlank() }
        ?: spark.mediaUrl?.takeIf { spark.mediaType == "image" }

    Box(
        modifier = Modifier
            .aspectRatio(9f / 16f)
            .background(Color(0xFF111111))
            .clickable(onClick = onClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dunkel-Verlauf unten für Lesbarkeit der Labels
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )

        // Aufrufe unten links
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = formatCount(spark.viewCount),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        // Likes unten rechts
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = formatCount(spark.likes),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        // Teilen-Button oben rechts
        IconButton(
            onClick = onShare,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(32.dp)
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "Teilen",
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                    .padding(2.dp)
            )
        }

        // Papierkorb-Button oben links (nur bei eigenen Sparks)
        if (isOwn) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = Color.Red,
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                        .padding(2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> count.toString()
}
