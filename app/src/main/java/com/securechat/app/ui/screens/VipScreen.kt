package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.securechat.app.data.network.CreatorContentResponse
import com.securechat.app.data.network.VipDiscussionReplyResponse
import com.securechat.app.data.network.VipDiscussionResponse
import com.securechat.app.ui.MainViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import com.securechat.app.data.network.VipCategoryResponse
import com.securechat.app.data.network.VipThreadMessageResponse
import com.securechat.app.data.network.VipThreadResponse
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin

private val VIP_CATEGORIES = listOf("Alle", "Handwerklich", "Hobby", "Fotografie", "Beauty", "Musik", "Gaming", "Lifestyle", "Fitness", "Kochen", "18+")

/**
 * VIP-Bereich: Algorithmischer Feed mit Creator-Beiträgen (Content-Tab) und Forum (Diskussionen-Tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onNavigateBack: () -> Unit = {}
) {
    val vipFeed by viewModel.vipFeed.collectAsState()
    val vipSearchResults by viewModel.vipSearchResults.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val is18Verified = currentUser?.is18Verified ?: false
    val vipDiscussions by viewModel.vipDiscussions.collectAsState()
    val vipDiscussionReplies by viewModel.vipDiscussionReplies.collectAsState()
    val vipCategories by viewModel.vipCategories.collectAsState()
    val vipThreads by viewModel.vipThreads.collectAsState()
    val vipThreadMessages by viewModel.vipThreadMessages.collectAsState()
    val vipSubscribedCategories by viewModel.vipSubscribedCategories.collectAsState()

    // 0 = Content, 1 = Diskussionen
    var selectedVipTab by remember { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf("Alle") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // VIP Kategorie-System State (Accordion)
    var expandedRootId by remember { mutableStateOf<String?>(null) }
    var expandedSubId by remember { mutableStateOf<String?>(null) }
    val childrenByParent = remember { mutableStateMapOf<String, List<VipCategoryResponse>>() }
    val threadsByCategory = remember { mutableStateMapOf<String, List<VipThreadResponse>>() }
    var selectedVipThread by remember { mutableStateOf<VipThreadResponse?>(null) }
    var isRadarMode by remember { mutableStateOf(false) }
    var selectedLeafCategoryId by remember { mutableStateOf<String?>(null) }
    var showCreateThread by remember { mutableStateOf(false) }
    var createThreadCategoryId by remember { mutableStateOf<String?>(null) }
    var newThreadTitle by remember { mutableStateOf("") }
    var newThreadBody by remember { mutableStateOf("") }
    var newThreadUrls by remember { mutableStateOf(listOf<String>()) }
    var newThreadUrlInput by remember { mutableStateOf("") }
    var newThreadMediaUris by remember { mutableStateOf(listOf<Uri>()) }
    var isCreatingThread by remember { mutableStateOf(false) }
    var threadMessageInput by remember { mutableStateOf("") }
    var showEditThreadDialog by remember { mutableStateOf<VipThreadResponse?>(null) }
    var editThreadTitle by remember { mutableStateOf("") }

    val threadMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) newThreadMediaUris = (newThreadMediaUris + uris).distinct()
    }

    // Legacy-State (unverändert für Kompatibilität)
    var selectedDiscussion by remember { mutableStateOf<VipDiscussionResponse?>(null) }
    var showCreateDiscussion by remember { mutableStateOf(false) }
    var newDiscussionTitle by remember { mutableStateOf("") }
    var newDiscussionContent by remember { mutableStateOf("") }
    var replyInput by remember { mutableStateOf("") }

    // Kaufdialog
    var purchaseItem by remember { mutableStateOf<CreatorContentResponse?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var showCreatorProfile by remember { mutableStateOf<CreatorContentResponse?>(null) }
    var shareItem by remember { mutableStateOf<CreatorContentResponse?>(null) }

    // Feed laden beim Öffnen, bei Navigation zurück und bei Filter-Änderungen
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry, selectedCategory) {
        val cat = if (selectedCategory == "Alle") null else selectedCategory
        viewModel.loadVipFeed(category = cat, type = "content")
    }

    LaunchedEffect(selectedVipTab) {
        if (selectedVipTab == 1 && vipCategories.isEmpty()) {
            viewModel.loadVipCategories()
        }
    }

    LaunchedEffect(expandedRootId) {
        val rootId = expandedRootId ?: return@LaunchedEffect
        expandedSubId = null
        if (rootId in childrenByParent || rootId in threadsByCategory) return@LaunchedEffect
        val rootCat = vipCategories.find { it.id == rootId } ?: return@LaunchedEffect
        if (rootCat.childrenCount > 0) {
            viewModel.loadVipCategoriesWithCallback(rootId) { children ->
                childrenByParent[rootId] = children
            }
        } else {
            viewModel.loadVipThreadsWithCallback(rootId) { threads ->
                threadsByCategory[rootId] = threads.sortedByDescending { it.lastReplyAt ?: it.createdAt ?: "" }
            }
        }
    }

    LaunchedEffect(expandedSubId) {
        val subId = expandedSubId ?: return@LaunchedEffect
        if (subId in childrenByParent || subId in threadsByCategory) return@LaunchedEffect
        val subCat = childrenByParent.values.flatten().find { it.id == subId } ?: return@LaunchedEffect
        if (subCat.childrenCount > 0) {
            viewModel.loadVipCategoriesWithCallback(subId) { children ->
                childrenByParent[subId] = children
            }
        } else {
            viewModel.loadVipThreadsWithCallback(subId) { threads ->
                threadsByCategory[subId] = threads.sortedByDescending { it.lastReplyAt ?: it.createdAt ?: "" }
            }
        }
    }

    LaunchedEffect(selectedLeafCategoryId) {
        val leafId = selectedLeafCategoryId ?: return@LaunchedEffect
        if (leafId in threadsByCategory) return@LaunchedEffect
        viewModel.loadVipThreadsWithCallback(leafId) { threads ->
            threadsByCategory[leafId] = threads.sortedByDescending { it.lastReplyAt ?: it.createdAt ?: "" }
        }
    }

    LaunchedEffect(selectedVipThread) {
        selectedVipThread?.let { viewModel.loadVipThreadMessages(it.id) }
    }

    // Single-Canvas Viewport-State für Diskussionen-Tab
    val diskussionenPage = when {
        selectedVipThread != null -> 2
        selectedLeafCategoryId != null -> 1
        else -> 0
    }
    val diskussionenSlide = remember { Animatable(0f) }
    LaunchedEffect(diskussionenPage) {
        diskussionenSlide.animateTo(
            diskussionenPage.toFloat(),
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }

    purchaseItem?.let { item ->
        PurchaseDialog(
            item = item,
            userStyx = currentUser?.styx ?: 0,
            isProcessing = isProcessing,
            onDismiss = { purchaseItem = null },
            onPurchase = {
                isProcessing = true
                viewModel.purchaseContent(item.id) { success, message ->
                    isProcessing = false
                    purchaseItem = null
                    if (success) {
                        navController.navigate("content_view/${item.id}")
                    } else {
                        feedbackMessage = message
                    }
                }
            },
            onSubscribe = {
                isProcessing = true
                viewModel.subscribeToCreator(item.creatorId) { success, message ->
                    isProcessing = false
                    purchaseItem = null
                    feedbackMessage = message
                    if (success) viewModel.loadVipFeed(
                        category = if (selectedCategory == "Alle") null else selectedCategory,
                        type = "content"
                    )
                }
            }
        )
    }

    feedbackMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            feedbackMessage = null
        }
    }

    // Creator-Profil-Sheet
    showCreatorProfile?.let { creatorItem ->
        CreatorProfileSheet(
            creatorItem = creatorItem,
            viewModel = viewModel,
            onDismiss = { showCreatorProfile = null },
            onNavigateToContent = { contentId -> navController.navigate("content_view/$contentId") },
            onNavigateToSpark = { sparkId ->
                showCreatorProfile = null
                viewModel.setPendingSparkId(sparkId)
                navController.navigate("sparks_feed") {
                    launchSingleTop = true
                }
            },
            onNavigateToSparksProfile = { cid ->
                showCreatorProfile = null
                navController.navigate("sparks_profile/$cid")
            }
        )
    }

    // Share-Sheet
    shareItem?.let { item ->
        VipShareSheet(
            item = item,
            viewModel = viewModel,
            onDismiss = { shareItem = null }
        )
    }

    // Thread-Bearbeiten-Dialog
    showEditThreadDialog?.let { thread ->
        AlertDialog(
            onDismissRequest = { showEditThreadDialog = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = editThreadTitle.trim()
                        if (title.isNotBlank()) {
                            viewModel.editVipThread(thread.id, title) { success, updated ->
                                if (success && updated != null) {
                                    val catId = selectedLeafCategoryId
                                    if (catId != null) {
                                        threadsByCategory[catId] = (threadsByCategory[catId] ?: emptyList())
                                            .map { if (it.id == thread.id) updated else it }
                                    }
                                }
                                showEditThreadDialog = null
                            }
                        }
                    }
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showEditThreadDialog = null }) { Text("Abbrechen") }
            },
            title = { Text("Thread bearbeiten") },
            text = {
                OutlinedTextField(
                    value = editThreadTitle,
                    onValueChange = { editThreadTitle = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("VIP", color = MaterialTheme.colorScheme.onSurface) },
                    actions = {
                        // Kategorie-Filter nur im Content-Tab
                        if (selectedVipTab == 0) {
                            Box {
                                IconButton(onClick = { showCategoryMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Kategorie filtern")
                                }
                                DropdownMenu(
                                    expanded = showCategoryMenu,
                                    onDismissRequest = { showCategoryMenu = false }
                                ) {
                                    val cats = if (is18Verified) VIP_CATEGORIES else VIP_CATEGORIES.filter { it != "18+" }
                                    cats.forEach { cat ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (cat == selectedCategory) {
                                                        Text(cat, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    } else {
                                                        Text(cat)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedCategory = cat
                                                showCategoryMenu = false
                                                isSearchActive = false
                                                searchQuery = ""
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                // Tab-Leiste: Content | Diskussionen
                TabRow(
                    selectedTabIndex = selectedVipTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedVipTab == 0,
                        onClick = { selectedVipTab = 0; selectedDiscussion = null },
                        text = { Text("Content", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedVipTab == 1,
                        onClick = { selectedVipTab = 1; selectedDiscussion = null },
                        text = { Text("Diskussionen", fontSize = 13.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Dialog: Neuen Thread erstellen
        if (showCreateThread) {
            fun resetAndClose() {
                showCreateThread = false
                newThreadTitle = ""
                newThreadBody = ""
                newThreadUrls = emptyList()
                newThreadUrlInput = ""
                newThreadMediaUris = emptyList()
            }
            CreateThreadDialog(
                title = newThreadTitle,
                onTitleChange = { newThreadTitle = it },
                body = newThreadBody,
                onBodyChange = { newThreadBody = it },
                urls = newThreadUrls,
                urlInput = newThreadUrlInput,
                onUrlInputChange = { newThreadUrlInput = it },
                onAddUrl = {
                    val trimmed = newThreadUrlInput.trim()
                    if (trimmed.isNotBlank() && !newThreadUrls.contains(trimmed)) {
                        newThreadUrls = newThreadUrls + trimmed
                        newThreadUrlInput = ""
                    }
                },
                onRemoveUrl = { url -> newThreadUrls = newThreadUrls - url },
                mediaUris = newThreadMediaUris,
                onPickMedia = { threadMediaPicker.launch("*/*") },
                onRemoveMedia = { uri -> newThreadMediaUris = newThreadMediaUris - uri },
                isCreating = isCreatingThread,
                onDismiss = { resetAndClose() },
                onConfirm = {
                    val catId = createThreadCategoryId
                    if (!catId.isNullOrBlank() && newThreadTitle.isNotBlank()) {
                        isCreatingThread = true
                        viewModel.createVipThreadWithContent(
                            categoryId = catId,
                            title = newThreadTitle,
                            body = newThreadBody,
                            urls = newThreadUrls,
                            mediaUris = newThreadMediaUris,
                        ) { success ->
                            isCreatingThread = false
                            if (success) {
                                threadsByCategory.remove(catId)
                                resetAndClose()
                            }
                        }
                    }
                }
            )
        }

        when (selectedVipTab) {
            // ── Tab 0: Content ──────────────────────────────────────────────
            0 -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                feedbackMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Beiträge, Creators suchen…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) {
                            isSearchActive = true
                            viewModel.searchVip(searchQuery)
                        }
                    })
                )
                val displayItems = when {
                    isSearchActive && vipSearchResults != null -> vipSearchResults!!.posts + vipSearchResults!!.sparks
                    else -> vipFeed
                }
                if (displayItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (isSearchActive) "Keine Ergebnisse für \"$searchQuery\"" else "Kein Content verfügbar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(displayItems) { item ->
                            VipFeedCard(
                                item = item,
                                onClick = {
                                    if (item.mediaType == "spark" || item.mediaType == "image_spark") {
                                        viewModel.setPendingSparkId(item.id)
                                        navController.navigate("sparks_feed") { launchSingleTop = true }
                                    } else if (item.styxCost == 0) {
                                        navController.navigate("content_view/${item.id}")
                                    } else {
                                        purchaseItem = item
                                    }
                                },
                                onLike = { viewModel.likeContent(item.id) },
                                onCreatorClick = { showCreatorProfile = item },
                                onShare = { shareItem = item }
                            )
                        }
                    }
                }
            }

            // ── Tab 1: Diskussionen (Single-Canvas Viewport-Slide) ──────────
            1 -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    val maxWidthPx = constraints.maxWidth.toFloat()

                    // Seite 0: Kategorie-Waben (Accordion)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .offset { IntOffset(((0f - diskussionenSlide.value) * maxWidthPx).roundToInt(), 0) }
                    ) {
                        HoneycombDiskussionenList(
                            rootCategories = vipCategories,
                            expandedRootId = expandedRootId,
                            expandedSubId = expandedSubId,
                            childrenByParent = childrenByParent,
                            threadsByCategory = threadsByCategory,
                            subscribedCategoryIds = vipSubscribedCategories,
                            onToggleRoot = { id -> expandedRootId = if (expandedRootId == id) null else id },
                            onToggleSub = { id -> expandedSubId = if (expandedSubId == id) null else id },
                            onSelectLeaf = { id -> selectedLeafCategoryId = id },
                            onCreateThread = { catId ->
                                createThreadCategoryId = catId
                                showCreateThread = true
                            },
                            onSubscribeCategory = { cat ->
                                if (viewModel.isVipCategorySubscribed(cat.id)) {
                                    viewModel.unsubscribeFromVipCategory(cat.id)
                                } else {
                                    viewModel.subscribeToVipCategory(cat.id, cat.name)
                                }
                            }
                        )
                    }

                    // Seite 1: Thread-Waben (Mega-Hexe)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .offset { IntOffset(((1f - diskussionenSlide.value) * maxWidthPx).roundToInt(), 0) }
                    ) {
                        ThreadHexGrid(
                            threads = threadsByCategory[selectedLeafCategoryId] ?: emptyList(),
                            myUserId = currentUser?.userId ?: "",
                            onThreadClick = { thread -> selectedVipThread = thread },
                            onBack = { selectedLeafCategoryId = null },
                            onCreateThread = {
                                createThreadCategoryId = selectedLeafCategoryId
                                showCreateThread = true
                            },
                            onDeleteThread = { thread ->
                                viewModel.deleteVipThread(thread.id) { success ->
                                    if (success) {
                                        val catId = selectedLeafCategoryId
                                        if (catId != null) {
                                            threadsByCategory[catId] = (threadsByCategory[catId] ?: emptyList())
                                                .filter { it.id != thread.id }
                                        }
                                    }
                                }
                            },
                            onEditThread = { thread ->
                                showEditThreadDialog = thread
                                editThreadTitle = thread.title
                            }
                        )
                    }

                    // Seite 2: Thread-Detail-Ansicht
                    Box(
                        Modifier
                            .fillMaxSize()
                            .offset { IntOffset(((2f - diskussionenSlide.value) * maxWidthPx).roundToInt(), 0) }
                    ) {
                        selectedVipThread?.let { thread ->
                            VipThreadView(
                                thread = thread,
                                messages = vipThreadMessages,
                                myUserId = currentUser?.userId ?: "",
                                messageInput = threadMessageInput,
                                onMessageInputChange = { threadMessageInput = it },
                                onSendMessage = { mediaUri, mediaType ->
                                    if (threadMessageInput.isNotBlank() || mediaUri != null) {
                                        viewModel.createVipThreadMessageWithMedia(
                                            thread.id,
                                            threadMessageInput,
                                            mediaUri,
                                            mediaType
                                        ) { success -> if (success) threadMessageInput = "" }
                                    }
                                },
                                onLikeMessage = { messageId ->
                                    viewModel.likeVipThreadMessage(thread.id, messageId)
                                },
                                onBack = { selectedVipThread = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionThreadCard(
    discussion: VipDiscussionResponse,
    onClick: () -> Unit,
    onLike: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Kategorie-Badge
            if (!discussion.category.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        discussion.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                discussion.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                discussion.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        discussion.authorName ?: discussion.authorFakeNumber ?: "Anonym",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            if (discussion.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable(onClick = onLike),
                            tint = if (discussion.isLikedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${discussion.likesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.ChatBubble,
                            contentDescription = "Antworten",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${discussion.repliesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── VIP Kategorie-System: Honeycomb Navigation & Thread-View ─────────────────

/** Punktförmige Sechseck-Form (pointy-top hexagon). */
private class HexagonShape : Shape {
    override fun createOutline(size: ComposeSize, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        for (i in 0..5) {
            val angle = PI / 3.0 * i - PI / 6.0
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

private val hexagonShape = HexagonShape()

private fun relativeTime(isoString: String?): String? {
    if (isoString.isNullOrBlank()) return null
    return try {
        val normalized = isoString.replace(" ", "T").let {
            if (!it.endsWith("Z") && !it.contains("+")) "${it}Z" else it
        }
        val instant = java.time.Instant.parse(normalized)
        val seconds = java.time.Instant.now().epochSecond - instant.epochSecond
        when {
            seconds < 60 -> "gerade"
            seconds < 3600 -> "vor ${seconds / 60} Min."
            seconds < 86400 -> "vor ${seconds / 3600} Std."
            seconds < 604800 -> "vor ${seconds / 86400} Tagen"
            else -> "vor ${seconds / 604800} Wo."
        }
    } catch (_: Exception) { null }
}

/**
 * Wabenförmiges Layout: pointy-top Hexagone, 3-2-3 Muster.
 * Breite Reihen (3 Spalten) haben kein x-Offset; schmale Reihen (2 Spalten) sind um hexWidth/2 versetzt.
 * [firstRowIsWide] bestimmt ob Reihe 0 breit (3 Waben) oder schmal (2 Waben) ist.
 */
@Composable
private fun HoneycombLayout(
    modifier: Modifier = Modifier,
    hexRadius: Dp,
    firstRowIsWide: Boolean = true,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val hexRadiusPx = with(density) { hexRadius.toPx() }
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hexW = (sqrt(3.0) * hexRadiusPx).roundToInt()
        val hexH = (2.0 * hexRadiusPx).roundToInt()
        val rowSpacing = (1.5 * hexRadiusPx).roundToInt()

        val placeables = measurables.map { m ->
            m.measure(constraints.copy(minWidth = hexW, maxWidth = hexW, minHeight = hexH, maxHeight = hexH))
        }

        // Weise jedem Item eine (Reihe, Spalte) zu – 3-2-3 alternierend
        data class ItemPos(val row: Int, val col: Int)
        val positions = mutableListOf<ItemPos>()
        var itemIdx = 0
        var rowIdx = 0
        while (itemIdx < placeables.size) {
            val isWide = if (firstRowIsWide) rowIdx % 2 == 0 else rowIdx % 2 == 1
            val rowSize = if (isWide) 3 else 2
            for (col in 0 until rowSize) {
                if (itemIdx >= placeables.size) break
                positions.add(ItemPos(rowIdx, col))
                itemIdx++
            }
            rowIdx++
        }

        val totalRows = rowIdx
        val totalHeight = if (totalRows == 0) 0 else hexH + (totalRows - 1) * rowSpacing

        layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
            positions.forEachIndexed { idx, pos ->
                val isWide = if (firstRowIsWide) pos.row % 2 == 0 else pos.row % 2 == 1
                val xOffset = if (!isWide) hexW / 2 else 0
                placeables[idx].placeRelative(pos.col * hexW + xOffset, pos.row * rowSpacing)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HexCategoryTile(
    category: VipCategoryResponse,
    isExpanded: Boolean,
    isSubscribed: Boolean = false,
    hexRadius: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val hue = ((category.id.hashCode() and 0xFF) / 255f) * 360f
    val bgColor = if (isExpanded) Color.hsl(hue, 0.55f, 0.36f) else Color.hsl(hue, 0.42f, 0.22f)
    val borderColor = if (isSubscribed) Color.hsl(hue, 0.9f, 0.75f) else Color.hsl(hue, 0.65f, 0.52f)
    val borderWidth = if (isSubscribed) 2.5.dp else 1.5.dp
    val hexW = hexRadius * sqrt(3.0).toFloat()
    val hexH = hexRadius * 2f

    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(hexW, hexH)
            .clip(hexagonShape)
            .background(bgColor)
            .border(borderWidth, borderColor, hexagonShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = hexRadius * 0.18f, vertical = hexRadius * 0.22f)
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val totalPosts = if (category.totalThreadCount > 0) category.totalThreadCount
                             else category.threadCount
            if (totalPosts > 0) {
                Text(
                    text = "B: $totalPosts",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        if (isSubscribed) {
            // Kleines Benachrichtigungs-Indikator-Icon oben rechts
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = hexRadius * 0.14f, end = hexRadius * 0.14f)
                    .size(10.dp)
                    .background(Color.hsl(hue, 0.9f, 0.75f), CircleShape)
            )
        }
        if (category.childrenCount > 0) {
            Text(
                text = "${category.childrenCount}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = hexRadius * 0.22f)
            )
        }

        // Kontext-Menü (Langer Druck)
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(if (isSubscribed) "Benachrichtigungen deaktivieren" else "Kategorie abonnieren")
                },
                leadingIcon = {
                    Icon(
                        if (isSubscribed) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null
                    )
                },
                onClick = {
                    showMenu = false
                    onLongClick()
                }
            )
        }
    }
}

@Composable
private fun HoneycombDiskussionenList(
    rootCategories: List<VipCategoryResponse>,
    expandedRootId: String?,
    expandedSubId: String?,
    childrenByParent: Map<String, List<VipCategoryResponse>>,
    threadsByCategory: Map<String, List<VipThreadResponse>>,
    subscribedCategoryIds: Set<String> = emptySet(),
    onToggleRoot: (String) -> Unit,
    onToggleSub: (String) -> Unit,
    onSelectLeaf: (String) -> Unit,
    onCreateThread: (String) -> Unit,
    onSubscribeCategory: (VipCategoryResponse) -> Unit = {}
) {
    if (rootCategories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        return
    }

    // Hilfsfunktion: aufteilen in 3-2-3 Reihen
    fun toHoneycombRows(items: List<VipCategoryResponse>, firstRowIsWide: Boolean = true): List<List<VipCategoryResponse>> {
        val result = mutableListOf<List<VipCategoryResponse>>()
        var i = 0; var rowIdx = 0
        while (i < items.size) {
            val isWide = if (firstRowIsWide) rowIdx % 2 == 0 else rowIdx % 2 == 1
            val rowSize = if (isWide) 3 else 2
            result.add(items.subList(i, minOf(i + rowSize, items.size)))
            i += rowSize; rowIdx++
        }
        return result
    }

    val allRows = toHoneycombRows(rootCategories)

    // Reihe der angeklickten Wabe ermitteln
    val expandedRowIdx = if (expandedRootId != null) {
        allRows.indexOfFirst { row -> row.any { it.id == expandedRootId } }
    } else -1

    // Items aufteilen: oberhalb der Expansion (inkl. expandierter Reihe) und unterhalb
    val topItems: List<VipCategoryResponse>
    val bottomItems: List<VipCategoryResponse>
    val bottomFirstRowIsWide: Boolean

    if (expandedRowIdx < 0) {
        topItems = rootCategories
        bottomItems = emptyList()
        bottomFirstRowIsWide = true
    } else {
        topItems = allRows.subList(0, expandedRowIdx + 1).flatten()
        bottomItems = if (expandedRowIdx + 1 < allRows.size)
            allRows.subList(expandedRowIdx + 1, allRows.size).flatten()
        else emptyList()
        // Unterhalb-Gruppe: erste Reihe hat Parität (expandedRowIdx+1)
        bottomFirstRowIsWide = (expandedRowIdx + 1) % 2 == 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        // Level 1 – obere Gruppe (Reihen 0..expandedRowIdx oder alle)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val level1Radius = maxWidth / (3 * sqrt(3.0).toFloat())
            HoneycombLayout(modifier = Modifier.fillMaxWidth(), hexRadius = level1Radius) {
                topItems.forEach { cat ->
                    HexCategoryTile(
                        category = cat,
                        isExpanded = expandedRootId == cat.id,
                        isSubscribed = cat.id in subscribedCategoryIds,
                        hexRadius = level1Radius,
                        onClick = { onToggleRoot(cat.id) },
                        onLongClick = { onSubscribeCategory(cat) }
                    )
                }
            }
        }

        // Level 2 – klappt direkt unter der gewählten Reihe auf
        AnimatedVisibility(
            visible = expandedRootId != null,
            enter = expandVertically() + fadeIn() + scaleIn(initialScale = 0.65f),
            exit = shrinkVertically() + fadeOut() + scaleOut(targetScale = 0.65f)
        ) {
            val subCats = childrenByParent[expandedRootId]
            Column {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                Spacer(Modifier.height(8.dp))
                if (subCats == null) {
                    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    val sorted = subCats.sortedByDescending {
                        if (it.childrenCount > 0) it.childrenCount else it.threadCount
                    }
                    HoneycombLayout(modifier = Modifier.fillMaxWidth(), hexRadius = 53.dp) {
                        sorted.forEach { sub ->
                            HexCategoryTile(
                                category = sub,
                                isExpanded = expandedSubId == sub.id,
                                isSubscribed = sub.id in subscribedCategoryIds,
                                hexRadius = 53.dp,
                                onClick = { onToggleSub(sub.id) },
                                onLongClick = { onSubscribeCategory(sub) }
                            )
                        }
                    }
                }

                // Level 3: Sub-Sub-Kategorien der angeklickten Unterkategorie
                AnimatedVisibility(
                    visible = expandedSubId != null,
                    enter = expandVertically() + fadeIn() + scaleIn(initialScale = 0.65f),
                    exit = shrinkVertically() + fadeOut() + scaleOut(targetScale = 0.65f)
                ) {
                    val subSubCats = childrenByParent[expandedSubId]
                    val directThreads = threadsByCategory[expandedSubId]
                    Column {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                        Spacer(Modifier.height(8.dp))
                        when {
                            subSubCats == null && directThreads == null -> {
                                Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            subSubCats != null -> {
                                val sortedSub = subSubCats.sortedByDescending { it.threadCount }
                                HoneycombLayout(modifier = Modifier.fillMaxWidth(), hexRadius = 44.dp) {
                                    sortedSub.forEach { subSub ->
                                        HexCategoryTile(
                                            category = subSub,
                                            isExpanded = false,
                                            isSubscribed = subSub.id in subscribedCategoryIds,
                                            hexRadius = 44.dp,
                                            onClick = { onSelectLeaf(subSub.id) },
                                            onLongClick = { onSubscribeCategory(subSub) }
                                        )
                                    }
                                }
                            }
                            directThreads != null -> {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { onSelectLeaf(expandedSubId!!) }) {
                                        Icon(Icons.Default.ChatBubble, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("${directThreads.size} Threads anzeigen")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                Spacer(Modifier.height(10.dp))
            }
        }

        // Level 1 – untere Gruppe (Reihen nach der Expansion), verschiebt sich nach unten
        if (bottomItems.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val level1Radius = maxWidth / (3 * sqrt(3.0).toFloat())
                HoneycombLayout(
                    modifier = Modifier.fillMaxWidth(),
                    hexRadius = level1Radius,
                    firstRowIsWide = bottomFirstRowIsWide
                ) {
                    bottomItems.forEach { cat ->
                        HexCategoryTile(
                            category = cat,
                            isExpanded = expandedRootId == cat.id,
                            isSubscribed = cat.id in subscribedCategoryIds,
                            hexRadius = level1Radius,
                            onClick = { onToggleRoot(cat.id) },
                            onLongClick = { onSubscribeCategory(cat) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ThreadHexGrid(
    threads: List<VipThreadResponse>,
    myUserId: String = "",
    onThreadClick: (VipThreadResponse) -> Unit,
    onBack: () -> Unit,
    onCreateThread: () -> Unit,
    onDeleteThread: (VipThreadResponse) -> Unit = {},
    onEditThread: (VipThreadResponse) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück",
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Threads", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onCreateThread) {
                Icon(Icons.Default.Add, contentDescription = "Neuer Thread",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider()
        if (threads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Noch keine Threads.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onCreateThread) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thread erstellen")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                HoneycombLayout(modifier = Modifier.fillMaxWidth(), hexRadius = 80.dp) {
                    threads.forEach { thread ->
                        VipThreadHexTile(
                            thread = thread,
                            myUserId = myUserId,
                            onClick = { onThreadClick(thread) },
                            onDelete = { onDeleteThread(thread) },
                            onEdit = { onEditThread(thread) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Text mit dünner schwarzer Umrandung für Lesbarkeit auf Bildhintergründen. */
@Composable
private fun OutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Box(modifier) {
        Text(
            text = text,
            style = style.copy(drawStyle = Stroke(width = 4f, join = StrokeJoin.Round)),
            color = Color.Black.copy(alpha = 0.85f),
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VipThreadHexTile(
    thread: VipThreadResponse,
    myUserId: String = "",
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val hue = ((thread.id.hashCode() and 0xFF) / 255f) * 360f
    val bgColor = Color.hsl(hue, 0.38f, 0.20f)
    val borderColor = if (thread.heatLevel > 50f)
        MaterialTheme.colorScheme.primary
    else
        Color.hsl(hue, 0.50f, 0.40f)
    val hexRadius = 80.dp
    val hexW = hexRadius * sqrt(3.0).toFloat()
    val hexH = hexRadius * 2f

    // Helligkeit des Hintergrundbildes asynchron bestimmen (3 Stufen)
    var imgBrightness by remember(thread.firstImageUrl) { mutableStateOf<Float?>(null) }
    val context = LocalContext.current
    LaunchedEffect(thread.firstImageUrl) {
        val url = thread.firstImageUrl ?: return@LaunchedEffect
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(1, 1)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            val bmp = ((result as? SuccessResult)?.drawable
                as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bmp != null) {
                val pixel = bmp.getPixel(0, 0)
                val r = android.graphics.Color.red(pixel) / 255f
                val g = android.graphics.Color.green(pixel) / 255f
                val b = android.graphics.Color.blue(pixel) / 255f
                imgBrightness = 0.2126f * r + 0.7152f * g + 0.0722f * b
            }
        } catch (_: Exception) {}
    }

    // Textfarbe in 3 Helligkeitsstufen
    val contentColor: Color = when {
        thread.firstImageUrl == null -> Color.White
        imgBrightness == null       -> Color.White          // noch ladend → weiß
        imgBrightness!! < 0.33f     -> Color.White          // dunkel → weiß
        imgBrightness!! < 0.66f     -> Color(0xFFF0F0F0)   // mittelhell → fast-weiß
        else                        -> Color(0xFF1A1A1A)    // hell → dunkel
    }

    Box(
        modifier = Modifier
            .size(hexW, hexH)
            .clip(hexagonShape)
            .background(bgColor)
            .border(1.5.dp, borderColor, hexagonShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (myUserId == thread.authorId) showMenu = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Hintergrundbild (erstes Bild des Erstellers)
        if (thread.firstImageUrl != null) {
            AsyncImage(
                model = thread.firstImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Overlay: immer 40% Verdunklung für bessere Lesbarkeit
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            OutlinedText(
                text = thread.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val snippet = thread.lastContentSnippet
            if (!snippet.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                OutlinedText(
                    text = snippet.take(50),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = contentColor.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = null,
                    modifier = Modifier.size(8.dp),
                    tint = contentColor.copy(alpha = 0.7f)
                )
                OutlinedText(
                    text = "${thread.repliesCount}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = contentColor.copy(alpha = 0.85f)
                )
            }
            val lastReply = relativeTime(thread.lastReplyAt)
            if (lastReply != null) {
                OutlinedText(
                    text = lastReply,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = contentColor.copy(alpha = 0.70f),
                    textAlign = TextAlign.Center
                )
            }
        }
        if (myUserId == thread.authorId) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Bearbeiten") },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun VipThreadView(
    thread: VipThreadResponse,
    messages: List<VipThreadMessageResponse>,
    myUserId: String,
    messageInput: String,
    onMessageInputChange: (String) -> Unit,
    onSendMessage: (mediaUri: android.net.Uri?, mediaType: String) -> Unit,
    onLikeMessage: (String) -> Unit = {},
    onBack: () -> Unit
) {
    // Aufeinanderfolgende Nachrichten gleichen Autors zusammenfassen
    val messageGroups: List<List<VipThreadMessageResponse>> = remember(messages) {
        val groups = mutableListOf<List<VipThreadMessageResponse>>()
        if (messages.isEmpty()) return@remember groups
        var current = mutableListOf(messages[0])
        for (i in 1 until messages.size) {
            if (messages[i].authorId == current[0].authorId) {
                current.add(messages[i])
            } else {
                groups.add(current.toList())
                current = mutableListOf(messages[i])
            }
        }
        groups.add(current.toList())
        groups
    }

    val sideMap = remember(messages) {
        val map = mutableMapOf<String, Boolean>()
        var otherCount = 0
        for (msg in messages) {
            if (msg.authorId !in map) {
                map[msg.authorId] = if (msg.authorId == myUserId) true
                                    else (otherCount++ % 2 == 0)
            }
        }
        map
    }
    val colorMap = remember(messages) {
        val map = mutableMapOf<String, Color>()
        for (msg in messages) {
            if (msg.authorId !in map) {
                val h = ((msg.authorId.hashCode() and 0xFF) / 255f) * 360f
                map[msg.authorId] = Color.hsl(h, 0.35f, 0.26f)
            }
        }
        map
    }

    // Profil-Dialog-State
    var profileDialogMsg by remember { mutableStateOf<VipThreadMessageResponse?>(null) }

    // Medien-Anhang-State
    var pendingMediaUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingMediaType by remember { mutableStateOf("image") }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingMediaUri = uri
            pendingMediaType = "image" // default; video detection via MIME falls back to image
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück",
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(thread.title, color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        HorizontalDivider()

        if (messageGroups.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Noch keine Beiträge. Sei der Erste!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messageGroups, key = { it[0].id }) { group ->
                    val isLeft = sideMap[group[0].authorId] ?: true
                    val isMe = group[0].authorId == myUserId
                    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                      else colorMap[group[0].authorId] ?: MaterialTheme.colorScheme.surfaceVariant
                    ThreadMessageGroupBubble(
                        messages = group,
                        isLeft = isLeft,
                        bubbleColor = bubbleColor,
                        isMe = isMe,
                        onLike = onLikeMessage,
                        onProfileClick = { profileDialogMsg = group[0] }
                    )
                }
            }
        }

        // Medienvorschau
        if (pendingMediaUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                AsyncImage(
                    model = pendingMediaUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp))
                )
                IconButton(
                    onClick = { pendingMediaUri = null },
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { mediaPicker.launch("*/*") }) {
                Icon(Icons.Default.AttachFile, contentDescription = "Anhang",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            OutlinedTextField(
                value = messageInput, onValueChange = onMessageInputChange,
                placeholder = { Text("Beitrag schreiben…", fontSize = 13.sp) },
                modifier = Modifier.weight(1f), singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(4.dp))
            val canSend = messageInput.isNotBlank() || pendingMediaUri != null
            IconButton(
                onClick = {
                    onSendMessage(pendingMediaUri, pendingMediaType)
                    pendingMediaUri = null
                },
                enabled = canSend
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden",
                    tint = if (canSend) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }

    // Profil-Dialog
    profileDialogMsg?.let { msg ->
        val lastPosts = remember(messages, msg.authorId) {
            messages.filter { it.authorId == msg.authorId }
                .sortedByDescending { it.createdAt ?: "" }
                .take(2)
        }
        AlertDialog(
            onDismissRequest = { profileDialogMsg = null },
            confirmButton = {
                TextButton(onClick = { profileDialogMsg = null }) { Text("Schließen") }
            },
            title = { Text(msg.authorName ?: msg.authorFakeNumber ?: "Profil") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val imgUrl = msg.authorProfileImageUrl?.takeIf { it.isNotBlank() }
                        ?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" }
                    if (imgUrl != null) {
                        AsyncImage(
                            model = imgUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    } else {
                        Icon(Icons.Default.AccountCircle, contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        @Composable
                        fun Stat(value: Int, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$value", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Stat(msg.authorPostsCount, "Beiträge")
                        Stat(msg.authorThreadsCount, "Threads")
                        Stat(msg.authorLikesCount, "Likes")
                    }
                    if (lastPosts.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            "Letzte Beiträge",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        lastPosts.forEach { post ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (post.content.isNotBlank()) {
                                        Text(
                                            post.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val dateStr = relativeTime(post.createdAt)
                                    if (dateStr != null) {
                                        Text(
                                            dateStr,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

/** Gruppe aufeinanderfolgender Nachrichten desselben Autors als eine Blase darstellen. */
@Composable
private fun ThreadMessageGroupBubble(
    messages: List<VipThreadMessageResponse>,
    isLeft: Boolean,
    bubbleColor: Color,
    isMe: Boolean,
    onLike: (String) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val first = messages[0]
    val last = messages[messages.size - 1]

    // Textfarbe nach Hintergrundluminanz
    val lum = bubbleColor.luminance()
    val textColor = when {
        lum > 0.4f -> Color(0xFF1A1A1A)
        lum > 0.15f -> Color(0xFF888888)
        else -> Color.White
    }
    val linkColor = if (lum > 0.4f) Color(0xFF1565C0) else Color(0xFFFF69B4)

    // Profilbild-URL (relative → absolute)
    val profileImageUrl = first.authorProfileImageUrl?.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("http")) it else "https://letheapp.de$it" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
    ) {
        // Profilbild links
        if (isLeft) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                if (profileImageUrl != null) {
                    AsyncImage(model = profileImageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = null,
                        modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.widthIn(max = 285.dp)) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isLeft) 4.dp else 16.dp,
                    topEnd = if (isLeft) 16.dp else 4.dp,
                    bottomStart = 16.dp, bottomEnd = 16.dp
                ),
                color = bubbleColor
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Header: Username + Datum + Like-Anzahl
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                first.authorName ?: first.authorFakeNumber ?: "Anonym",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            if (first.authorIsVerified) {
                                Spacer(Modifier.width(3.dp))
                                VerifiedBadge(modifier = Modifier.size(11.dp))
                            }
                        }
                        val dateStr = relativeTime(first.createdAt)
                        if (dateStr != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(dateStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = textColor.copy(alpha = 0.6f))
                        }
                    }
                    if (!isMe) {
                        Text(
                            "Beiträge: ${first.authorPostsCount} · Likes: ${first.authorLikesCount}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.height(5.dp))

                    messages.forEachIndexed { idx, msg ->
                        val uriHandler = LocalUriHandler.current
                        when {
                            msg.mediaType == "image" && !msg.mediaUrl.isNullOrBlank() -> {
                                AsyncImage(
                                    model = msg.mediaUrl, contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                if (msg.content.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    VipClickableText(msg.content, textColor, linkColor)
                                }
                            }
                            msg.mediaType == "video" && !msg.mediaUrl.isNullOrBlank() -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .clickable { runCatching { uriHandler.openUri(msg.mediaUrl) } }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(24.dp))
                                    Text(msg.content.ifBlank { "Video" },
                                        style = MaterialTheme.typography.bodySmall, color = Color.White)
                                }
                            }
                            else -> VipClickableText(msg.content, textColor, linkColor)
                        }
                        if (idx < messages.size - 1) Spacer(Modifier.height(4.dp))
                    }
                }
            }

            // Like-Button unter der Blase
            val totalLikes = messages.sumOf { it.likesCount }
            val likedByMe = messages.any { it.isLikedByMe }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(if (isLeft) Alignment.Start else Alignment.End)
                    .clickable { onLike(last.id) }
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Icon(
                    if (likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    modifier = Modifier.size(14.dp),
                    tint = if (likedByMe) Color(0xFFE91E63)
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
                if (totalLikes > 0) {
                    Spacer(Modifier.width(3.dp))
                    Text("$totalLikes", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
        }

        // Profilbild rechts
        if (!isLeft) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                if (profileImageUrl != null) {
                    AsyncImage(model = profileImageUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = null,
                        modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Text mit klickbaren URLs. */
@Composable
private fun VipClickableText(text: String, textColor: Color, linkColor: Color) {
    if (text.isBlank()) return
    val urlRegex = remember { Regex("https?://[^\\s]+") }
    val annotated = buildAnnotatedString {
        var last = 0
        urlRegex.findAll(text).forEach { match ->
            if (match.range.first > last) {
                withStyle(SpanStyle(color = textColor)) { append(text.substring(last, match.range.first)) }
            }
            pushStringAnnotation("URL", match.value)
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                append(match.value)
            }
            pop()
            last = match.range.last + 1
        }
        if (last < text.length) {
            withStyle(SpanStyle(color = textColor)) { append(text.substring(last)) }
        }
    }
    val uriHandler = LocalUriHandler.current
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset)
                .firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } }
        }
    )
}

@Composable
private fun VipFeedCard(
    item: CreatorContentResponse,
    onClick: () -> Unit,
    onLike: () -> Unit = {},
    onCreatorClick: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Creator-Header
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.creatorProfileImageUrl,
                    contentDescription = "Creator Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.creatorName ?: item.creatorUsername ?: item.creatorFakeNumber ?: "Creator",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onCreatorClick)
                        )
                        Spacer(Modifier.width(6.dp))
                        // Subscribe + Button
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onCreatorClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 12.sp
                            )
                        }
                    }
                    val displayHandle = item.creatorUsername ?: item.creatorFakeNumber
                    if (!displayHandle.isNullOrBlank()) {
                        Text(
                            "@$displayHandle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!item.category.isNullOrBlank()) {
                        Text(
                            item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Titel & Beschreibung
            if (!item.title.isNullOrBlank()) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (!item.description.isNullOrBlank()) {
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            // Tags als Chips (wenn vorhanden)
            if (!item.tags.isNullOrBlank()) {
                val tags = try {
                    org.json.JSONArray(item.tags).let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    }
                } catch (_: Exception) { emptyList() }
                if (tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.take(4).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Preview-Bild
            val previewUrl = item.previewImageUrl ?: item.mediaUrl
            if (!previewUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = "Vorschau",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (item.styxCost > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.60f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Gesperrter Inhalt",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.MonetizationOn, contentDescription = "Styx", tint = Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                                    Text("${item.styxCost} Styx", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onLike, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (item.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (item.isLikedByMe) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "${item.likes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Teilen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (item.styxCost > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.MonetizationOn, contentDescription = "Styx", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text("${item.styxCost} Styx", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = onClick, modifier = Modifier.height(32.dp)) {
                        Text("Kaufen", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(
                        "Kostenlos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onClick, modifier = Modifier.height(32.dp)) {
                        Text("Ansehen", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateThreadDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    body: String,
    onBodyChange: (String) -> Unit,
    urls: List<String>,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onAddUrl: () -> Unit,
    onRemoveUrl: (String) -> Unit,
    mediaUris: List<Uri>,
    onPickMedia: () -> Unit,
    onRemoveMedia: (Uri) -> Unit,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Neuer Thread", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Titel *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    label = { Text("Beitragstext (optional)") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                // URL-Eingabe
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = onUrlInputChange,
                        label = { Text("URL hinzufügen") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onAddUrl() })
                    )
                    IconButton(onClick = onAddUrl) {
                        Icon(Icons.Default.Add, contentDescription = "URL hinzufügen")
                    }
                }

                // URL-Chips
                if (urls.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        urls.forEach { url ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(Modifier.width(6.dp))
                                    Text(url, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(onClick = { onRemoveUrl(url) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Entfernen", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Medien-Picker
                OutlinedButton(
                    onClick = onPickMedia,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bilder / Videos hinzufügen")
                }

                // Medien-Vorschau
                if (mediaUris.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(mediaUris) { uri ->
                            Box(modifier = Modifier.size(80.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick = { onRemoveMedia(uri) },
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Entfernen",
                                        modifier = Modifier.size(12.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { if (!isCreating) onDismiss() }) { Text("Abbrechen") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = title.isNotBlank() && !isCreating
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Erstellen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseDialog(
    item: CreatorContentResponse,
    userStyx: Int,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit,
    onSubscribe: () -> Unit
) {
    val hasEnoughStyx = userStyx >= item.styxCost
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Inhalt freischalten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\"${item.title ?: "Beitrag"}\"")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Dein Kontostand: ")
                    Icon(Icons.Default.MonetizationOn, contentDescription = "Styx", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text("$userStyx Styxs")
                }
                HorizontalDivider()
                if (!hasEnoughStyx) {
                    Text(
                        "Nicht genug Styxs. Du benötigst ${item.styxCost} Styxs.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Einzelkauf: ")
                        Icon(Icons.Default.MonetizationOn, contentDescription = "Styx", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text("${item.styxCost} Styxs")
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                if (hasEnoughStyx) {
                    Button(onClick = onPurchase, enabled = !isProcessing) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Einzelkauf")
                    }
                }
                OutlinedButton(onClick = onSubscribe, enabled = !isProcessing) {
                    Text("Abonnieren")
                }
                TextButton(onClick = { if (!isProcessing) onDismiss() }) { Text("Abbrechen") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorProfileSheet(
    creatorItem: CreatorContentResponse,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToContent: (String) -> Unit,
    onNavigateToSpark: (String) -> Unit,
    onNavigateToSparksProfile: (String) -> Unit
) {
    val creatorPublicFeed by viewModel.creatorPublicFeed.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSubscribing by remember { mutableStateOf(false) }
    var isAlreadySubscribed by remember { mutableStateOf(false) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(creatorItem.creatorId) {
        viewModel.loadCreatorPublicContent(creatorItem.creatorId)
        viewModel.checkSubscription(creatorItem.creatorId) { isAlreadySubscribed = it }
    }

    val creatorPosts = remember(creatorPublicFeed) { creatorPublicFeed.filter { it.mediaType != "spark" && it.mediaType != "image_spark" }.sortedByDescending { it.createdAt } }
    val creatorSparks = remember(creatorPublicFeed) { creatorPublicFeed.filter { it.mediaType == "spark" || it.mediaType == "image_spark" }.sortedByDescending { it.createdAt } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    val bannerUrl = creatorItem.creatorBannerUrl
                    if (!bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = if (bannerUrl.startsWith("http")) bannerUrl else "https://letheapp.de$bannerUrl",
                            contentDescription = "Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Profilbild über Banner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp)
                            .offset(y = 36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            val profileUrl = creatorItem.creatorProfileImageUrl
                            if (!profileUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = if (profileUrl.startsWith("http")) profileUrl else "https://letheapp.de$profileUrl",
                                    contentDescription = "Profilbild",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            // Creator-Name + Subscribe
            item {
                Spacer(Modifier.height(44.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            creatorItem.creatorName ?: creatorItem.creatorUsername ?: "Creator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        val handle = creatorItem.creatorUsername ?: creatorItem.creatorFakeNumber
                        if (!handle.isNullOrBlank()) {
                            Text("@$handle", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (isAlreadySubscribed) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Abonniert ✓")
                        }
                    } else {
                        Button(
                            onClick = {
                                isSubscribing = true
                                viewModel.subscribeToCreator(creatorItem.creatorId) { success, msg ->
                                    isSubscribing = false
                                    if (success) isAlreadySubscribed = true
                                    feedbackMsg = if (success) "Abo hinzugefügt!" else msg
                                }
                            },
                            enabled = !isSubscribing,
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (isSubscribing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("+ Abonnieren")
                            }
                        }
                    }
                }
                feedbackMsg?.let { msg ->
                    Text(
                        msg,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onNavigateToSparksProfile(creatorItem.creatorId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sparks-Profil ansehen")
                }
                Spacer(Modifier.height(16.dp))
            }

            // Beiträge
            if (creatorPosts.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        "Beiträge (${creatorPosts.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(creatorPosts) { post ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToContent(post.id); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val prevUrl = post.previewImageUrl
                        if (!prevUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = if (prevUrl.startsWith("http")) prevUrl else "https://letheapp.de$prevUrl",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(post.title ?: "Beitrag", fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!post.description.isNullOrBlank()) {
                                Text(post.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (post.styxCost > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Default.MonetizationOn, contentDescription = "Styx", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
                                    Text("${post.styxCost} Styx", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Sparks
            if (creatorSparks.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Text(
                        "Sparks (${creatorSparks.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(creatorSparks) { spark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSpark(spark.id); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val prevUrl = spark.previewImageUrl
                        if (!prevUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = if (prevUrl.startsWith("http")) prevUrl else "https://letheapp.de$prevUrl",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(spark.title ?: "Spark", fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${spark.viewCount} Aufrufe · ${spark.likes} Likes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (creatorPosts.isEmpty() && creatorSparks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Noch keine Inhalte verfügbar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VipShareSheet(
    item: CreatorContentResponse,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val creatorHandle = item.creatorUsername ?: item.creatorFakeNumber ?: ""
    val slug = item.slug ?: item.id
    val externalUrl = "https://letheapp.de/post?c=${item.creatorId}&s=${java.net.URLEncoder.encode(slug, "UTF-8")}"
    val internalUrl = when {
        item.mediaType == "spark" || item.mediaType == "image_spark" -> "lethe://sp?id=${item.id}"
        item.isLive -> "lethe://li?id=${item.id}"
        else -> "lethe://post?C=${item.id}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Beitrag teilen",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Extern teilen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "Schau dir diesen Beitrag von @$creatorHandle an!\n$externalUrl")
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Beitrag teilen"))
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Extern teilen", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("Link über andere Apps teilen", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            // Link kopieren
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Beitrag Link", externalUrl))
                        Toast.makeText(context, "Link kopiert!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Link kopieren", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text("Externer Link (Web/Browser)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider()

            // An Kontakt senden
            Spacer(Modifier.height(12.dp))
            Text(
                "An Kontakt senden",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val contacts by viewModel.contacts.collectAsState(initial = emptyList())
            val acceptedContacts = remember(contacts) { contacts.filter { it.status == "accepted" && !it.isBot } }
            if (acceptedContacts.isEmpty()) {
                Text(
                    "Keine Kontakte vorhanden",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    items(acceptedContacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.sendMessage(contact.userId, internalUrl)
                                    Toast.makeText(context, "Gesendet an ${contact.username ?: contact.fakeNumber}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (contact.profileImageUrl != null) {
                                coil.compose.AsyncImage(
                                    model = contact.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (contact.username ?: contact.fakeNumber).take(1).uppercase(),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = contact.username ?: contact.fakeNumber,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
