package com.securechat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.securechat.app.data.network.GameProfileStats
import com.securechat.app.data.network.NearbyAnonQuestion
import com.securechat.app.data.network.NearbyProfileResponse
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NearbyDetailScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSparksProfile: (String) -> Unit = {}
) {
    val profile by viewModel.selectedNearbyProfile.collectAsState()
    val nearbyAnonQuestions by viewModel.nearbyAnonQuestions.collectAsState()
    val gameStats by viewModel.nearbyProfileGameStats.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val userId = profile?.userId ?: ""
    val questions = nearbyAnonQuestions[userId] ?: emptyList()
    val answeredQuestions = questions.filter { it.answer != null }

    var questionText by remember { mutableStateOf("") }
    var questionImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSendingQuestion by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        questionImageUri = uri
    }

    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            viewModel.loadNearbyAnonQuestions(userId)
            viewModel.trackNearbyProfileVisit(userId)
            viewModel.loadNearbyProfileGameStats(userId)
        }
    }

    // Vollbild-Bild-Viewer
    if (fullscreenImageUrl != null) {
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                title = {
                    Text(
                        profile?.username ?: "Profil",
                        fontWeight = FontWeight.Bold,
                        color = topBarTitleColor()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (profile == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // === FOTO-GALERIE ===
            item {
                val galleryImages = buildList {
                    profile?.nearbyImageUrl?.let { url ->
                        add(if (url.startsWith("http")) url else "https://letheapp.de$url")
                    }
                    profile?.galleryPhoto1?.let { url ->
                        add(if (url.startsWith("http")) url else "https://letheapp.de$url")
                    }
                    profile?.galleryPhoto2?.let { url ->
                        add(if (url.startsWith("http")) url else "https://letheapp.de$url")
                    }
                    profile?.galleryPhoto3?.let { url ->
                        add(if (url.startsWith("http")) url else "https://letheapp.de$url")
                    }
                }

                if (galleryImages.isNotEmpty()) {
                    val pagerState = rememberPagerState(pageCount = { galleryImages.size })
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            AsyncImage(
                                model = galleryImages[page],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { fullscreenImageUrl = galleryImages[page] }
                            )
                        }
                        // Gradient-Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                        startY = 600f
                                    )
                                )
                        )
                        // Seitenindikator (wenn mehr als 1 Bild)
                        if (galleryImages.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                galleryImages.indices.forEach { idx ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (idx == pagerState.currentPage) 8.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (idx == pagerState.currentPage)
                                                    Color.White
                                                else
                                                    Color.White.copy(alpha = 0.5f)
                                            )
                                    )
                                }
                            }
                            // Wisch-Hinweis
                            Text(
                                text = "← Wischen für weitere Fotos →",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 48.dp)
                            )
                        }
                        // Profilinfos unten
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                "${profile!!.username}, ${profile!!.age}",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            profile!!.height?.let { h ->
                                Text("$h cm", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${profile!!.distanceKm ?: 0.0} km entfernt",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    // Kein Foto – Platzhalter
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // === PROFIL-INFOS ===
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (!profile!!.description.isNullOrBlank()) {
                        Text(
                            text = profile!!.description!!,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    // Info-Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NearbyInfoChip(icon = Icons.Default.Wc, label = profile!!.gender)
                        if (!profile!!.lookingFor.isNullOrBlank()) {
                            val lookingForLabel = when (profile!!.lookingFor) {
                                "M" -> "Suche: Männer"
                                "F" -> "Suche: Frauen"
                                "D" -> "Suche: Divers"
                                "ALL" -> "Suche: Alle"
                                else -> "Suche: ${profile!!.lookingFor}"
                            }
                            NearbyInfoChip(icon = Icons.Default.Favorite, label = lookingForLabel)
                        }
                        if (!profile!!.hasChildren.isNullOrBlank()) {
                            NearbyInfoChip(icon = Icons.Default.ChildCare, label = profile!!.hasChildren!!)
                        }
                        if (!profile!!.wantsChildren.isNullOrBlank()) {
                            NearbyInfoChip(icon = Icons.Default.FamilyRestroom, label = "Kinderwunsch: ${profile!!.wantsChildren}")
                        }
                    }
                    if (userId.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { onNavigateToSparksProfile(userId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sparks-Profil ansehen")
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // === SPIEL-HIGHLIGHTS ===
            if (gameStats != null && (gameStats!!.jodHighScore > 0 || gameStats!!.mostPlayedGame != null)) {
                item {
                    NearbyGameStatsSection(gameStats!!)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            // === FRAGEN STELLEN ===
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Anonyme Frage stellen",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { if (it.length <= 200) questionText = it },
                        placeholder = { Text("Deine anonyme Frage...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4,
                        trailingIcon = {
                            IconButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(
                                    if (questionImageUri != null) Icons.Default.Image else Icons.Default.AttachFile,
                                    contentDescription = "Bild anhängen",
                                    tint = if (questionImageUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                    // Vorschau des angehängten Bildes
                    if (questionImageUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = questionImageUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { fullscreenImageUrl = questionImageUri.toString() }
                            )
                            IconButton(
                                onClick = { questionImageUri = null },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Bild entfernen", tint = Color.White)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (questionText.isNotBlank()) {
                                isSendingQuestion = true
                                viewModel.sendNearbyAnonQuestion(
                                    profileUserId = userId,
                                    question = questionText,
                                    onSuccess = {
                                        isSendingQuestion = false
                                        questionText = ""
                                        questionImageUri = null
                                        scope.launch { snackbarHostState.showSnackbar("Frage gesendet") }
                                    },
                                    onError = { err ->
                                        isSendingQuestion = false
                                        scope.launch { snackbarHostState.showSnackbar(err) }
                                    }
                                )
                            }
                        },
                        enabled = questionText.isNotBlank() && !isSendingQuestion,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSendingQuestion) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Anonym senden")
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // === BEANTWORTETE FRAGEN ===
            if (answeredQuestions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Noch keine beantworteten Fragen",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "Antworten",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(answeredQuestions, key = { it.id }) { question ->
                    NearbyAnsweredQuestionCard(
                        question = question,
                        onImageClick = { url -> fullscreenImageUrl = url },
                        onReact = { emoji -> viewModel.reactToNearbyQuestion(question.id, emoji, userId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyGameStatsSection(stats: GameProfileStats) {
    val gameLabel = when (stats.mostPlayedGame) {
        "jump_run" -> "Jump & Run"
        "activity" -> "Activity"
        "ttt"      -> "Tic Tac Toe"
        "sknch"    -> "Sketch N Check"
        else       -> stats.mostPlayedGame ?: ""
    }
    val scoreLabel = when (stats.mostPlayedGame) {
        "sknch" -> "Punkte"
        else    -> "Münzen"
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Spiel-Highlights",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (stats.jodHighScore > 0) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏃", fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "JUMP or Die",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${stats.jodHighScore} Pkt.",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            if (stats.mostPlayedGame != null) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎮", fontSize = 22.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            gameLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${stats.mostPlayedGameHighScore} $scoreLabel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "${stats.mostPlayedGameCount}× gespielt",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyInfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.wrapContentWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val REACTION_EMOJIS = listOf("❤️", "🤪", "🤔", "🥹", "😢")

@Composable
private fun NearbyAnsweredQuestionCard(
    question: NearbyAnonQuestion,
    onImageClick: (String) -> Unit,
    onReact: (String) -> Unit
) {
    var showEmojiPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Frage
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = question.question,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))
            // Antwort
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = question.answer ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    question.answerImageUrl?.let { imgUrl ->
                        val fullUrl = if (imgUrl.startsWith("http")) imgUrl else "https://letheapp.de$imgUrl"
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = "Antwortbild",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onImageClick(fullUrl) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reaktionszeile
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Vorhandene Reaktionen mit Zählern
                val sortedReactions = question.reactions.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sortedReactions.forEach { (emoji, count) ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (question.myReaction == emoji)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onReact(emoji) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(emoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "$count",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Emoji-Picker-Button (Herz wenn keine eigene Reaktion, sonst die eigene)
                Box {
                    IconButton(
                        onClick = { showEmojiPicker = !showEmojiPicker },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (question.myReaction != null) {
                            Text(question.myReaction, fontSize = 16.sp)
                        } else {
                            Icon(
                                Icons.Default.FavoriteBorder,
                                contentDescription = "Reagieren",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    // Emoji-Auswahl-Dropdown
                    DropdownMenu(
                        expanded = showEmojiPicker,
                        onDismissRequest = { showEmojiPicker = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            REACTION_EMOJIS.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 26.sp,
                                    modifier = Modifier
                                        .clickable {
                                            onReact(emoji)
                                            showEmojiPicker = false
                                        }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
