package com.securechat.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import com.securechat.app.data.local.UserPreferences
import com.securechat.app.data.network.NearbyAnonQuestion
import com.securechat.app.data.network.NearbyLikeIncoming
import com.securechat.app.data.network.NearbyMatch
import com.securechat.app.data.network.NearbyProfileResponse
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyListScreen(
    viewModel: MainViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToNearbyChat: (String) -> Unit = {},
    onNavigateToNearbyDetail: (String) -> Unit = {},
    fontSizeMultiplier: Float = 1.0f
) {
    val profiles by viewModel.nearbyProfiles.collectAsState()
    val hiddenProfiles by viewModel.hiddenNearbyProfiles.collectAsState()
    val incomingLikes by viewModel.incomingLikes.collectAsState()
    val nearbyMatches by viewModel.nearbyMatches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val userPrefs by viewModel.userPrefs.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val myNearbyQuestions by viewModel.myNearbyQuestions.collectAsState()
    val openQuestionsCount by viewModel.openNearbyQuestionsCount.collectAsState()
    val nearbyProfileVisitors by viewModel.nearbyProfileVisitors.collectAsState()
    val hasVisitorsSubscription by viewModel.hasVisitorsSubscription.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHiddenDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val nearbyOnboardPrefs = remember { context.getSharedPreferences("lethe_nearby_onboard", Context.MODE_PRIVATE) }
    var showOnboardingHint by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Meldungen von anderen Screens nicht auf Nearby anzeigen
        viewModel.clearStatus()
        if (!nearbyOnboardPrefs.getBoolean("hint_seen", false)) {
            showOnboardingHint = true
        }
    }

    fun dismissHint() {
        showOnboardingHint = false
        nearbyOnboardPrefs.edit().putBoolean("hint_seen", true).apply()
    }

    // Blinking animation for profile icon
    val infiniteTransition = rememberInfiniteTransition(label = "profileIconBlink")
    val profileIconScaleAnimated by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "profileIconScale"
    )
    val profileIconScale = if (showOnboardingHint) profileIconScaleAnimated else 1f

    val hasNearbyProfile = currentUser != null

    val pagerState = rememberPagerState(pageCount = { 5 })

    fun reload() = viewModel.loadNearbyUsers(
        radiusKm = userPrefs.datingRadiusKm.toDouble(),
        genderFilter = userPrefs.nearbyGenderFilter,
        ageMin = userPrefs.nearbyAgeMin,
        ageMax = userPrefs.nearbyAgeMax,
        friendshipOnly = userPrefs.nearbyFriendshipOnly
    )

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            reload()
            viewModel.loadIncomingLikes()
            viewModel.loadNearbyMatches()
            viewModel.loadMyNearbyQuestions()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 2 && currentUser != null) {
            viewModel.loadNearbyMatches()
        }
        if (pagerState.currentPage == 3 && currentUser != null) {
            viewModel.loadNearbyProfileVisitors()
        }
        if (pagerState.currentPage == 4 && currentUser != null) {
            viewModel.loadMyNearbyQuestions()
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            snackbarMessage = null
        }
    }

    if (showSettingsDialog) {
        NearbySettingsDialog(
            viewModel = viewModel,
            userPrefs = userPrefs,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showHiddenDialog) {
        HiddenNearbyProfilesDialog(
            hiddenProfiles = hiddenProfiles,
            currentStyxCoins = currentUser?.styx ?: 0,
            onRestore = { userId ->
                viewModel.restoreNearbyProfileWithCoins(
                    targetUserId = userId,
                    onSuccess = { snackbarMessage = "Profil wiederhergestellt (5 Styx abgezogen)" },
                    onError = { err -> snackbarMessage = err }
                )
            },
            onDismiss = { showHiddenDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Nearby",
                        fontWeight = FontWeight.ExtraBold,
                        color = topBarTitleColor()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Fragezeichen-Badge für offene Fragen
                    if (hasNearbyProfile) {
                        IconButton(onClick = {
                            scope.launch { pagerState.animateScrollToPage(4) }
                        }) {
                            BadgedBox(
                                badge = {
                                    if (openQuestionsCount > 0) Badge { Text("$openQuestionsCount") }
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "Offene Fragen",
                                    tint = if (openQuestionsCount > 0)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        dismissHint()
                        onNavigateToProfile()
                    }) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Mein Profil",
                            modifier = Modifier.scale(if (showOnboardingHint) profileIconScale else 1f),
                            tint = if (showOnboardingHint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (hiddenProfiles.isNotEmpty()) {
                        IconButton(onClick = { showHiddenDialog = true }) {
                            BadgedBox(
                                badge = { Badge { Text("${hiddenProfiles.size}") } }
                            ) {
                                Icon(
                                    Icons.Default.Autorenew,
                                    contentDescription = "Ausgeblendete Profile wiederherstellen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        reload()
                        viewModel.loadIncomingLikes()
                        viewModel.loadNearbyMatches()
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Aktualisieren",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Einstellungen",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedVisibility(
                visible = showOnboardingHint,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dieser Bereich ist vollständig anonym. Tippe oben auf das Profilsymbol, um dein anonymes Profil einzurichten.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { dismissHint() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            // Tab-Leiste
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Liste", fontSize = 12.sp) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (incomingLikes.isNotEmpty()) Badge { Text("${incomingLikes.size}") }
                            }
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                    text = { Text("Likes", fontSize = 12.sp) }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    text = { Text("Matches", fontSize = 12.sp) }
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    icon = {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    text = { Text("Aufrufe", fontSize = 12.sp) }
                )
                Tab(
                    selected = pagerState.currentPage == 4,
                    onClick = { scope.launch { pagerState.animateScrollToPage(4) } },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (openQuestionsCount > 0) Badge { Text("$openQuestionsCount") }
                            }
                        ) {
                            Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    },
                    text = { Text("Fragen", fontSize = 12.sp) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> NearbyProfileListTab(
                        profiles = profiles,
                        isLoading = isLoading,
                        statusMessage = statusMessage,
                        viewModel = viewModel,
                        fontSizeMultiplier = fontSizeMultiplier,
                        isAgeVerified = currentUser?.ageVerified == true,
                        currentUserId = currentUser?.userId ?: "",
                        onAgeVerificationRequired = { snackbarMessage = "Bitte verifiziere erst dein Alter." },
                        onNavigateToDetail = { userId ->
                            viewModel.loadNearbyProfileById(userId)
                            onNavigateToNearbyDetail(userId)
                        }
                    )
                    1 -> NearbyMatchesTab(
                        incomingLikes = incomingLikes,
                        onAccept = { likeId ->
                            viewModel.acceptLike(likeId)
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                        onReject = { likeId -> viewModel.rejectLike(likeId) }
                    )
                    2 -> NearbyMessagesTab(
                        matches = nearbyMatches,
                        onOpenChat = { matchId -> onNavigateToNearbyChat(matchId) },
                        onOpenProfile = { partnerId ->
                            viewModel.loadNearbyProfileById(partnerId)
                            onNavigateToNearbyDetail(partnerId)
                        }
                    )
                    3 -> NearbyVisitorsTab(
                        visitors = nearbyProfileVisitors,
                        hasSubscription = hasVisitorsSubscription,
                        currentStyx = currentUser?.styx ?: 0,
                        onSubscribe = {
                            viewModel.subscribeToNearbyVisitors(
                                onSuccess = { snackbarMessage = "Aufrufe freigeschaltet! 300 Styx abgezogen." },
                                onError = { err -> snackbarMessage = err }
                            )
                        },
                        onOpenProfile = { visitorId -> /* TODO: navigate to nearby profile */ }
                    )
                    4 -> NearbyQuestionsTab(
                        questions = myNearbyQuestions,
                        viewModel = viewModel,
                        onSnackbar = { snackbarMessage = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyProfileListTab(
    profiles: List<NearbyProfileResponse>,
    isLoading: Boolean,
    statusMessage: String?,
    viewModel: MainViewModel,
    fontSizeMultiplier: Float = 1.0f,
    isAgeVerified: Boolean = false,
    currentUserId: String = "",
    onAgeVerificationRequired: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            profiles.isEmpty() -> NearbyEmptyState()
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(profiles) { index, profile ->
                        NearbyProfileCard(
                            profile = profile,
                            viewModel = viewModel,
                            currentUserId = currentUserId,
                            isFirstCard = index == 0,
                            onLike = {
                                if (isAgeVerified) {
                                    viewModel.sendLike(profile.userId)
                                } else {
                                    onAgeVerificationRequired()
                                }
                            },
                            onDismiss = { viewModel.dismissNearbyProfile(profile.userId) },
                            fontSizeMultiplier = fontSizeMultiplier,
                            onImageClick = { onNavigateToDetail(profile.userId) }
                        )
                    }
                }
            }
        }
        statusMessage?.let { msg ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = msg, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.clearStatus() }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun NearbyVisitorsTab(
    visitors: List<com.securechat.app.data.network.NearbyProfileVisitor>,
    hasSubscription: Boolean,
    currentStyx: Int,
    onSubscribe: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    var showPayDialog by remember { mutableStateOf(false) }

    // Bezahl-Dialog
    if (showPayDialog) {
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            icon = {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text("Aufrufe freischalten", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Für 300 Styx/Monat siehst du, wer dein Nearby-Profil aufgerufen hat.",
                        fontSize = 14.sp
                    )
                    Text(
                        "Dein Guthaben: $currentStyx Styx",
                        fontSize = 13.sp,
                        color = if (currentStyx >= 300) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPayDialog = false
                        onSubscribe()
                    },
                    enabled = currentStyx >= 300
                ) {
                    Text("Bezahlen (300 Styx)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPayDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (visitors.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Visibility,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Keine Aufrufe",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Wer dein Profil aufruft, erscheint hier.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            if (!hasSubscription) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showPayDialog = true }) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aufrufe freischalten (300 Styx/Monat)")
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Hinweis-Banner wenn kein Abo
            if (!hasSubscription) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPayDialog = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Profile unscharf. Tippe zum Freischalten (300 Styx/Monat).",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visitors, key = { it.visitorId }) { visitor ->
                    NearbyVisitorCard(
                        visitor = visitor,
                        hasSubscription = hasSubscription,
                        onClick = {
                            if (hasSubscription) {
                                onOpenProfile(visitor.visitorId)
                            } else {
                                showPayDialog = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyVisitorCard(
    visitor: com.securechat.app.data.network.NearbyProfileVisitor,
    hasSubscription: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profilbild (mit Blur wenn kein Abo)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!visitor.nearbyImageUrl.isNullOrBlank()) {
                    val imageUrl = if (visitor.nearbyImageUrl.startsWith("http"))
                        visitor.nearbyImageUrl
                    else
                        "https://letheapp.de${visitor.nearbyImageUrl}"
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (!hasSubscription) Modifier.blur(12.dp) else Modifier
                            )
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .then(if (!hasSubscription) Modifier.blur(8.dp) else Modifier)
                    )
                }
                if (!hasSubscription) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (hasSubscription) {
                    Text(
                        text = visitor.username,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    // Benutzername mit Sternchen, nur mittlerer Buchstabe sichtbar
                    val name = visitor.username
                    val maskedName = buildString {
                        name.forEachIndexed { index, c ->
                            append(if (index == name.length / 2) c else '*')
                        }
                    }
                    Text(
                        text = maskedName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
                Text(
                    text = "Hat dein Profil aufgerufen",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasSubscription) {
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = "Freischalten",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun NearbyQuestionsTab(
    questions: List<NearbyAnonQuestion>,
    viewModel: MainViewModel,
    onSnackbar: (String) -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { /* handled per-question */ }

    if (questions.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.QuestionAnswer,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Keine Fragen",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Wenn jemand dir eine anonyme Frage\ngestellt hat, erscheint sie hier.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(questions, key = { it.id }) { question ->
                NearbyQuestionOwnerCard(
                    question = question,
                    viewModel = viewModel,
                    onSnackbar = onSnackbar
                )
            }
        }
    }
}

@Composable
private fun NearbyQuestionOwnerCard(
    question: NearbyAnonQuestion,
    viewModel: MainViewModel,
    onSnackbar: (String) -> Unit
) {
    var answerText by remember(question.id) { mutableStateOf(question.answer ?: "") }
    var selectedImageUri by remember(question.id) { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    val isAnswered = question.answer != null

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> selectedImageUri = uri }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Frage
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = question.question,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (isAnswered) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                // Antwort anzeigen
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = question.answer!!,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        question.answerImageUrl?.let { imgUrl ->
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = if (imgUrl.startsWith("http")) imgUrl else "https://letheapp.de$imgUrl",
                                contentDescription = "Antwortbild",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    placeholder = { Text("Deine Antwort...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )
                if (selectedImageUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        IconButton(
                            onClick = { selectedImageUri = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Bild entfernen",
                                tint = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bild", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            if (answerText.isNotBlank()) {
                                isSending = true
                                viewModel.answerNearbyQuestion(
                                    questionId = question.id,
                                    answer = answerText,
                                    imageUri = selectedImageUri,
                                    onSuccess = {
                                        isSending = false
                                        onSnackbar("Antwort gespeichert")
                                    },
                                    onError = { err ->
                                        isSending = false
                                        onSnackbar(err)
                                    }
                                )
                            }
                        },
                        enabled = answerText.isNotBlank() && !isSending,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Antworten", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyMatchesTab(
    incomingLikes: List<NearbyLikeIncoming>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    if (incomingLikes.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Noch keine Likes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Wenn jemand dein Profil geliked hat,\nerscheint er hier.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(incomingLikes, key = { it.id }) { like ->
                NearbyLikeCard(
                    like = like,
                    onAccept = { onAccept(like.id) },
                    onReject = { onReject(like.id) }
                )
            }
        }
    }
}

@Composable
private fun NearbyLikeCard(
    like: NearbyLikeIncoming,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!like.nearbyImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = if (like.nearbyImageUrl.startsWith("http"))
                            like.nearbyImageUrl
                        else
                            "https://letheapp.de${like.nearbyImageUrl}",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${like.username}, ${like.age}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = like.gender,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Ablehnen
            IconButton(onClick = onReject) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Ablehnen",
                    tint = Color(0xFFE53935)
                )
            }
            // Annehmen
            IconButton(onClick = onAccept) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Annehmen",
                    tint = Color(0xFFE91E63)
                )
            }
        }
    }
}

@Composable
private fun NearbyMessagesTab(
    matches: List<NearbyMatch>,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit = {}
) {
    if (matches.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Keine Matches",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Gegenseitige Likes werden hier\nals Match-Chat angezeigt.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(matches, key = { it.id }) { match ->
                NearbyMatchListItem(
                    match = match,
                    onClick = { onOpenChat(match.id) },
                    onProfileClick = { onOpenProfile(match.partnerId) }
                )
            }
        }
    }
}

@Composable
private fun NearbyMatchListItem(match: NearbyMatch, onClick: () -> Unit, onProfileClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rundes Avatar – Klick öffnet Detailprofil
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            if (!match.partnerImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = if (match.partnerImageUrl.startsWith("http"))
                        match.partnerImageUrl
                    else
                        "https://letheapp.de${match.partnerImageUrl}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.partnerUsername,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = match.lastMessage ?: "Kein Nachrichten noch",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbySettingsDialog(
    viewModel: MainViewModel,
    userPrefs: UserPreferences,
    onDismiss: () -> Unit
) {
    var radius by remember(userPrefs.datingRadiusKm) { mutableStateOf(userPrefs.datingRadiusKm) }
    var genderFilter by remember(userPrefs.nearbyGenderFilter) { mutableStateOf(userPrefs.nearbyGenderFilter) }
    var ageMin by remember(userPrefs.nearbyAgeMin) { mutableStateOf(userPrefs.nearbyAgeMin.toFloat()) }
    var ageMax by remember(userPrefs.nearbyAgeMax) { mutableStateOf(userPrefs.nearbyAgeMax.toFloat()) }
    var friendshipOnly by remember(userPrefs.nearbyFriendshipOnly) { mutableStateOf(userPrefs.nearbyFriendshipOnly) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nearby Einstellungen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // --- Suchradius ---
                Column {
                    Text(
                        "Suchradius: ${radius.toInt()} km",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 10f..200f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // --- Geschlecht ---
                Text("Geschlecht anzeigen", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                val genderOptions = listOf(
                    "ALL" to "Alle",
                    "Männlich" to "Männer",
                    "Weiblich" to "Frauen",
                    "Divers" to "Divers"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    genderOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = genderFilter == value,
                            onClick = { genderFilter = value },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // --- Altersbereich ---
                Column {
                    Text(
                        "Altersbereich: ${ageMin.toInt()} – ${ageMax.toInt()} Jahre",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Von", fontSize = 12.sp, modifier = Modifier.width(32.dp))
                        Slider(
                            value = ageMin,
                            onValueChange = { if (it <= ageMax - 1) ageMin = it },
                            valueRange = 18f..80f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bis", fontSize = 12.sp, modifier = Modifier.width(32.dp))
                        Slider(
                            value = ageMax,
                            onValueChange = { if (it >= ageMin + 1) ageMax = it },
                            valueRange = 18f..80f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider()

                // --- Nur Freundschaft ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Nur Freundschaft suchen",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Switch(
                        checked = friendshipOnly,
                        onCheckedChange = { friendshipOnly = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.updateDatingRadius(radius)
                viewModel.updateNearbyGenderFilter(genderFilter)
                viewModel.updateNearbyAgeMin(ageMin.toInt())
                viewModel.updateNearbyAgeMax(ageMax.toInt())
                viewModel.updateNearbyFriendshipOnly(friendshipOnly)
                viewModel.loadNearbyUsers(
                    radiusKm = radius.toDouble(),
                    genderFilter = genderFilter,
                    ageMin = ageMin.toInt(),
                    ageMax = ageMax.toInt(),
                    friendshipOnly = friendshipOnly
                )
                onDismiss()
            }) {
                Text("Anwenden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NearbyProfileCard(
    profile: NearbyProfileResponse,
    viewModel: MainViewModel,
    currentUserId: String = "",
    isFirstCard: Boolean = false,
    onLike: () -> Unit,
    onDismiss: () -> Unit = {},
    fontSizeMultiplier: Float = 1.0f,
    onImageClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var questionText by remember { mutableStateOf("") }
    var isSendingQuestion by remember { mutableStateOf(false) }
    val questionFieldBringIntoView = remember { BringIntoViewRequester() }
    val cardScope = rememberCoroutineScope()
    // Sporadisch den Info-Hinweis anzeigen: nur beim ersten Profil und nur bei ~35% Chance
    val showInfoHint = remember(isFirstCard) { isFirstCard && kotlin.random.Random.nextFloat() < 0.35f }
    var infoHintDismissed by remember { mutableStateOf(false) }
    val anonQuestions by viewModel.nearbyAnonQuestions.collectAsState()
    val profileQuestions = anonQuestions[profile.userId] ?: emptyList()

    LaunchedEffect(expanded) {
        if (expanded) {
            viewModel.loadNearbyAnonQuestions(profile.userId)
            viewModel.trackNearbyProfileVisit(profile.userId)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column {
            // === Bildbereich (feste Höhe) ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .then(if (onImageClick != null) Modifier.clickable { onImageClick() } else Modifier)
            ) {
                // Hintergrundbild
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF90A4AE), Color(0xFF546E7A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!profile.nearbyImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = if (profile.nearbyImageUrl.startsWith("http"))
                                profile.nearbyImageUrl
                            else
                                "https://letheapp.de${profile.nearbyImageUrl}",
                            contentDescription = "Profilfoto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(140.dp),
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }

                // Gradient-Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 650f
                            )
                        )
                )

                val nearbyProfileDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(nearbyProfileDensity.density, nearbyProfileDensity.fontScale * fontSizeMultiplier)
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${profile.username}, ${profile.age}",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            profile.height?.let { h ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "$h cm",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${profile.distanceKm ?: 0.0} km entfernt",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 15.sp
                            )
                        }
                        if (!profile.description.isNullOrBlank()) {
                            Text(
                                text = profile.description,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Info-Hinweis: nur beim ersten Profil, sporadisch
                        if (showInfoHint && !infoHintDismissed) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .clickable { infoHintDismissed = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Black.copy(alpha = 0.65f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Klappe die Karte auf um dem Nutzer eine anonyme Frage zu stellen und seine Antworten zu lesen. Bei 100 gestellten Fragen erhältst du eine kleine Aufmerksamkeit. 🎁",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Schließen",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            NearbyCircularButton(
                                icon = Icons.Default.Close,
                                color = Color(0xFFE53935),
                                onClick = onDismiss
                            )
                            NearbyCircularButton(
                                icon = Icons.Default.Favorite,
                                color = Color(0xFFE91E63),
                                onClick = onLike
                            )
                        }
                    }
                }

                // Expand-Pfeil unten mittig
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                ) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Einklappen" else "Ausklappen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // === Ausgeklappter Bereich: Fragen & Antworten ===
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    // Anonyme Frage stellen
                    Text(
                        "Anonyme Frage stellen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            placeholder = { Text("Deine Frage...") },
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(questionFieldBringIntoView)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        cardScope.launch { questionFieldBringIntoView.bringIntoView() }
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (questionText.isNotBlank() && !isSendingQuestion) {
                                    isSendingQuestion = true
                                    viewModel.sendNearbyAnonQuestion(
                                        profileUserId = profile.userId,
                                        question = questionText,
                                        onSuccess = {
                                            questionText = ""
                                            isSendingQuestion = false
                                        },
                                        onError = { isSendingQuestion = false }
                                    )
                                }
                            },
                            enabled = questionText.isNotBlank() && !isSendingQuestion,
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            if (isSendingQuestion) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Senden",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Beantwortete Fragen anzeigen
                    val answeredQuestions = profileQuestions.filter { it.answer != null }
                    if (answeredQuestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Fragen & Antworten",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        answeredQuestions.forEach { q ->
                            NearbyProfileQAItem(
                                question = q,
                                onLike = { viewModel.likeAnonQuestion(q.id, profile.userId) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyProfileQAItem(question: NearbyAnonQuestion, onLike: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = question.question,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }
        if (question.answer != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = question.answer,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    question.answerImageUrl?.let { imgUrl ->
                        Spacer(modifier = Modifier.height(6.dp))
                        AsyncImage(
                            model = if (imgUrl.startsWith("http")) imgUrl else "https://letheapp.de$imgUrl",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (question.likeCount > 0) {
                    Text(
                        text = question.likeCount.toString(),
                        fontSize = 12.sp,
                        color = if (question.likedByMe) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onLike,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (question.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (question.likedByMe) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NearbyCircularButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color)
        }
    }
}

@Composable
fun NearbyEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Niemand in der Nähe",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Erstelle zuerst dein Nearby-Profil\noder passe den Suchradius über ⋮ an.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** Dialog zum Anzeigen und Wiederherstellen von ausgeblendeten Nearby-Profilen (mit Styx-Coins). */
@Composable
fun HiddenNearbyProfilesDialog(
    hiddenProfiles: List<NearbyProfileResponse>,
    currentStyxCoins: Int,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingRestoreUserId by remember { mutableStateOf<String?>(null) }

    if (pendingRestoreUserId != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUserId = null },
            title = { Text("5 Styx-Coins abziehen?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Dieses Profil kostet 5 Styx-Coins zum Wiederherstellen.\nDu hast aktuell $currentStyxCoins Styx.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreUserId?.let { onRestore(it) }
                        pendingRestoreUserId = null
                    }
                ) {
                    Text("Wiederherstellen")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUserId = null }) { Text("Abbrechen") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ausgeblendete Profile", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (hiddenProfiles.isEmpty()) {
                Text(
                    "Keine ausgeblendeten Profile.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    Text(
                        "Wiederherstellen kostet 5 Styx-Coins.\nDein Guthaben: $currentStyxCoins Styx.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(hiddenProfiles, key = { it.userId }) { profile ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!profile.nearbyImageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = if (profile.nearbyImageUrl.startsWith("http"))
                                                profile.nearbyImageUrl
                                            else
                                                "https://letheapp.de${profile.nearbyImageUrl}",
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${profile.username}, ${profile.age}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${profile.distanceKm ?: "?"} km entfernt",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Wiederherstellen-Button
                                IconButton(
                                    onClick = {
                                        if (currentStyxCoins >= 5) {
                                            pendingRestoreUserId = profile.userId
                                        } else {
                                            onRestore(profile.userId)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Autorenew,
                                        contentDescription = "Wiederherstellen (5 Styx)",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}
