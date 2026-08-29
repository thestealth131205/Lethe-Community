package com.securechat.app.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface as AndroidTypeface
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.local.GroupEntity
import com.securechat.app.data.local.MessageEntity
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import com.securechat.app.ui.OnboardingStep
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private fun generateQrBitmapCL(content: String, size: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    } catch (_: Exception) { null }
}

/** Neon-Gelb für den Account-Zusatz eingemischter Kontakte anderer, nicht aktiver Accounts. */
private val NeonYellow = Color(0xFFFFFF33)

/** Eintrag der (ggf. gemischten) Kontakt-Chatliste: eigener Kontakt oder Kontakt eines anderen Accounts. */
private sealed class ContactRowEntry {
    data class Local(val contact: ContactEntity) : ContactRowEntry()
    data class Foreign(val entry: MainViewModel.ForeignContactDisplay) : ContactRowEntry()

    val sortKey: String get() = when (this) {
        is Local -> contact.customAlias ?: contact.username ?: contact.fakeNumber
        is Foreign -> entry.displayName
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactlistScreen(
    viewModel: MainViewModel,
    onNavigateToChat: (String) -> Unit,
    onNavigateToGroupChat: (String) -> Unit = {},
    onNavigateToSettings: (section: String) -> Unit,
    onNavigateToStatusView: (statusId: String) -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    onNavigateToCoins: () -> Unit = {},
    onNavigateToAgeVerification: () -> Unit = {},
    onNavigateToAppSettings: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToContactImport: () -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    onNavigateToPayForCreator: () -> Unit = {},
    onNavigateToSknChLobby: () -> Unit = {},
    onNavigateToGames: () -> Unit = {},
    onNavigateToJumpOrDieGame: () -> Unit = {},
    onNavigateToPinballGame: () -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    onAccountDeleted: () -> Unit = {}
) {
    val contacts by viewModel.contactsSortedByRecent.collectAsState(initial = emptyList())
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val mixedContactListEnabled by viewModel.mixedContactListEnabled.collectAsState()
    val otherAccountContacts by viewModel.otherAccountContacts.collectAsState()
    // Verhindert kurzes Aufblitzen von "keine Kontakte" während Room noch lädt
    var contactsInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.contactsSortedByRecent.first()
        contactsInitialized = true
    }
    val pinnedGroupIds by viewModel.pinnedGroupIds.collectAsState()
    val pinnedContactIds by viewModel.pinnedContactIds.collectAsState()

    val currentUser by viewModel.currentUser.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val pendingUpdateInfo by viewModel.pendingUpdateInfo.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    // Update-Icon in TopBar anzeigen wenn ein Update wartet, der Dialog aber gerade versteckt ist
    val showUpdateIcon = pendingUpdateInfo != null && updateInfo == null

    var profileCardContact by remember { mutableStateOf<ContactEntity?>(null) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showGameSelectionDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 0 = Chats (Standard), 1 = Gruppen, 2 = Anrufe
    var selectedContactTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    LaunchedEffect(selectedContactTab) {
        if (pagerState.currentPage != selectedContactTab) {
            pagerState.animateScrollToPage(selectedContactTab)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        selectedContactTab = pagerState.settledPage
    }
    LaunchedEffect(Unit) {
        viewModel.resetContactListTab.collect {
            selectedContactTab = 0
            pagerState.animateScrollToPage(0)
        }
    }
    val callMessages by viewModel.callMessages.collectAsState(initial = emptyList())
    val callBadgeCount by viewModel.callBadgeCount.collectAsState()
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountFinal by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }

    // Badge-Zähler zurücksetzen wenn dieser Screen sichtbar wird
    DisposableEffect(Unit) {
        viewModel.setContactListVisible(true)
        onDispose { viewModel.setContactListVisible(false) }
    }

    // Status-Umrandung sofort aktualisieren wenn Screen sichtbar wird
    LaunchedEffect(Unit) {
        viewModel.loadContactStatuses()
    }

    // Löschen-Modus: welches Element zeigt das Trash-Icon?
    var selectedDeleteId by remember { mutableStateOf<String?>(null) }
    var deleteTargetContact by remember { mutableStateOf<ContactEntity?>(null) }
    var deleteTargetGroup by remember { mutableStateOf<GroupEntity?>(null) }
    // Long-Press Aktions-Dialog für Kontakte
    var longPressContact by remember { mutableStateOf<ContactEntity?>(null) }
    // Blockieren-Bestätigungsdialog (nur blockieren, nicht löschen)
    var blockTargetContact by remember { mutableStateOf<ContactEntity?>(null) }
    // Namen-ändern-Dialog
    var renameTargetContact by remember { mutableStateOf<ContactEntity?>(null) }
    var renameAliasInput by remember { mutableStateOf("") }

    // Gruß-Overlay: 5x schnell tippen auf die Animations-Figuren
    var animTapCount by remember { mutableIntStateOf(0) }
    var animLastTapMs by remember { mutableLongStateOf(0L) }
    var showGreetingOverlay by remember { mutableStateOf(false) }

    // Onboarding-Tip-System
    val currentOnboardingStep by viewModel.currentOnboardingStep.collectAsState()
    var fabRect by remember { mutableStateOf(Rect.Zero) }
    var addContactMiniFabRect by remember { mutableStateOf(Rect.Zero) }
    var moreVertRect by remember { mutableStateOf(Rect.Zero) }
    var accountMenuItemRect by remember { mutableStateOf(Rect.Zero) }

    // QR-Modus im FAB: true = LetheID, false = Rufnummer/Invite
    var qrModeIsLetheId by remember { mutableStateOf(true) }
    val inviteLinkUrl by viewModel.inviteLinkUrl.collectAsState()

    // QR-Bitmaps für LetheID und Invite-Link
    val letheIdQrBitmap = remember(currentUser?.letheId) {
        currentUser?.letheId?.let { generateQrBitmapCL(it) }
    }
    val inviteQrBitmap = remember(inviteLinkUrl) {
        inviteLinkUrl?.let { generateQrBitmapCL(it) }
    }

    // QR-Scanner-Launcher (ZXing) – erkennt LetheID-QRs und Einladungslink-QRs
    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { content ->
            fabExpanded = false
            val trimmed = content.trim()
            when {
                trimmed.startsWith("LID") -> {
                    // LetheID-QR → direkt hinzufügen
                    viewModel.addContact(trimmed, trimmed)
                }
                trimmed.contains("letheapp.de/invite") -> {
                    // Einladungslink-QR: u-Parameter (base64url) enthält die fake_number,
                    // n-Parameter (base64url) enthält den Namen
                    try {
                        val uri = Uri.parse(trimmed)
                        val uParam = uri.getQueryParameter("u")
                        val nParam = uri.getQueryParameter("n")
                        if (uParam != null) {
                            // base64url (ohne Padding) → Standard-Base64 mit Padding auffüllen
                            val padded = uParam.replace('-', '+').replace('_', '/')
                                .let { it + "=".repeat((4 - it.length % 4) % 4) }
                            val fakeNumber = String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
                            val displayName = if (nParam != null) {
                                val nPadded = nParam.replace('-', '+').replace('_', '/')
                                    .let { it + "=".repeat((4 - it.length % 4) % 4) }
                                runCatching { String(Base64.decode(nPadded, Base64.DEFAULT), Charsets.UTF_8) }
                                    .getOrDefault(fakeNumber)
                            } else fakeNumber
                            viewModel.addContact(fakeNumber, displayName)
                        }
                    } catch (_: Exception) { /* ungültiger QR – ignorieren */ }
                }
                else -> {
                    // Telefonnummer → Server-Lookup
                    viewModel.lookupUserByPhone(trimmed) { fakeNumber ->
                        if (fakeNumber != null) viewModel.addContact(fakeNumber, fakeNumber)
                    }
                }
            }
        }
    }

    val filteredContacts = contacts.filter {
        searchQuery.isBlank() ||
        it.customAlias?.contains(searchQuery, ignoreCase = true) == true ||
        it.username?.contains(searchQuery, ignoreCase = true) == true ||
        it.fakeNumber.contains(searchQuery, ignoreCase = true)
    }

    val filteredForeignContacts = if (!mixedContactListEnabled) emptyList() else otherAccountContacts.filter {
        searchQuery.isBlank() || it.displayName.contains(searchQuery, ignoreCase = true)
    }

    val selfNotesLastMsg by viewModel.getLastMessageForChat("self_notes").collectAsState(initial = null)
    val hasSelfNotes = selfNotesLastMsg != null

    // "Eigene Notizen" erscheint bei Suche nach eigenem Namen/Nummer oder "Du"
    val showSelfNotesEntry = searchQuery.isNotBlank() && (
        currentUser?.name?.contains(searchQuery, ignoreCase = true) == true ||
        currentUser?.fakeNumber?.contains(searchQuery, ignoreCase = true) == true ||
        searchQuery.trim().equals("du", ignoreCase = true)
    )

    val contactScreenMessage by viewModel.contactScreenMessage.collectAsState()
    val contactSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(contactScreenMessage) {
        contactScreenMessage?.let {
            contactSnackbarHostState.showSnackbar(it)
            viewModel.clearContactScreenMessage()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sknChBg by viewModel.sknChBgGame.collectAsState()
    val pendingGameInvite by viewModel.pendingGameInvite.collectAsState()
    val sknChOpenGamesCount by viewModel.sknChOpenGamesCount.collectAsState()
    val eventAnimation by viewModel.contactListAnimation.collectAsState()

    // Event-Animation bei jedem Screen-Aufruf vom Server laden
    LaunchedEffect(Unit) {
        viewModel.loadAnimationState()
    }

    // Polling: Auto-Navigate wenn Spiel startet
    LaunchedEffect(sknChBg?.gameId) {
        val gameId = sknChBg?.gameId ?: return@LaunchedEffect
        while (true) {
            delay(5_000)
            val bg = viewModel.sknChBgGame.value ?: break
            val games = viewModel.getSknChGames()
            val game = games.find { it.gameId == gameId }
            if (game == null) {
                viewModel.sknChBgGame.value = null
                break
            }
            val updated = bg.copy(playersJoined = game.playersJoined)
            if (game.playersJoined >= game.playersRequired) {
                viewModel.sknChBgGame.value = updated.copy(autoStart = true)
                onNavigateToSknChLobby()
                break
            } else {
                viewModel.sknChBgGame.value = updated
            }
        }
    }

    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler {
        if (longPressContact != null) {
            longPressContact = null
            return@BackHandler
        }
        if (selectedDeleteId != null) {
            selectedDeleteId = null
            return@BackHandler
        }
        if (fabExpanded) {
            fabExpanded = false
            return@BackHandler
        }
        if (backPressedOnce) {
            (context as? Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, context.getString(com.securechat.app.R.string.contacts_back_to_exit), Toast.LENGTH_SHORT).show()
            scope.launch {
                delay(2000)
                backPressedOnce = false
            }
        }
    }

    // ─── Account-Löschen: Erster Bestätigungsdialog ───────────────────────────
    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(com.securechat.app.R.string.contacts_delete_account_title), color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    stringResource(com.securechat.app.R.string.contacts_delete_account_text)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountConfirm = false
                        showDeleteAccountFinal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(com.securechat.app.R.string.contacts_delete_account_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) { Text(stringResource(com.securechat.app.R.string.general_cancel)) }
            }
        )
    }

    // ─── Account-Löschen: Finale Bestätigung ──────────────────────────────────
    if (showDeleteAccountFinal) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountFinal = false },
            title = { Text(stringResource(com.securechat.app.R.string.contacts_delete_account_final_title), color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(com.securechat.app.R.string.contacts_delete_account_final_text))
                    deleteAccountError?.let { err ->
                        Text(stringResource(com.securechat.app.R.string.contacts_delete_account_error, err), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingAccount = true
                        deleteAccountError = null
                        viewModel.deleteAccount { success, error ->
                            isDeletingAccount = false
                            if (success) {
                                showDeleteAccountFinal = false
                                onAccountDeleted()
                            } else {
                                deleteAccountError = error ?: context.getString(com.securechat.app.R.string.contacts_error_unknown)
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text(stringResource(com.securechat.app.R.string.contacts_delete_account_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isDeletingAccount) showDeleteAccountFinal = false }) { Text(stringResource(com.securechat.app.R.string.general_cancel)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(contactSnackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Lethe", color = topBarTitleColor())
                        // Styx-Coin-Guthaben neben dem App-Titel
                        currentUser?.styx?.let { styx ->
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable { onNavigateToCoins() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MonetizationOn,
                                        contentDescription = stringResource(com.securechat.app.R.string.contacts_styx_coins_cd),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$styx",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                },
                expandedHeight = 58.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = topBarTitleColor(),
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (eventAnimation != "none") {
                        Box {
                            Row(verticalAlignment = Alignment.Bottom) {
                                if (eventAnimation == "easter") {
                                    EasterEggsDecoration(
                                        heightDp = 30.dp,
                                        modifier = Modifier.align(Alignment.Bottom)
                                    )
                                }
                                if (eventAnimation == "may") {
                                    ContactListMascotAnimation(
                                        animationType = "may",
                                        sizeDp = 40.dp,
                                        enableHorizontalBounce = false,
                                        mirrorDance = true,
                                        modifier = Modifier.align(Alignment.Bottom)
                                    )
                                }
                                if (eventAnimation == "xmas") {
                                    XmasReindeerDecoration(
                                        heightDp = 40.dp,
                                        modifier = Modifier.align(Alignment.Bottom)
                                    )
                                }
                                ContactListMascotAnimation(
                                    animationType = eventAnimation,
                                    sizeDp = 40.dp,
                                    enableHorizontalBounce = false,
                                    modifier = Modifier.align(Alignment.Bottom)
                                )
                            }
                            // Transparentes Overlay: 5x schnell tippen öffnet Grußkarte
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        val now = System.currentTimeMillis()
                                        if (now - animLastTapMs > 2000L) animTapCount = 0
                                        animTapCount++
                                        animLastTapMs = now
                                        if (animTapCount >= 5) {
                                            showGreetingOverlay = true
                                            animTapCount = 0
                                        }
                                    }
                            )
                        }
                    }
                    if (showUpdateIcon) {
                        IconButton(onClick = { viewModel.reshowUpdateDialog() }) {
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = stringResource(com.securechat.app.R.string.update_available),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { showGameSelectionDialog = true }) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text("👥", fontSize = 14.sp, lineHeight = 14.sp)
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = "Spieleauswahl",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (sknChOpenGamesCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .size(14.dp)
                                        .background(MaterialTheme.colorScheme.error, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (sknChOpenGamesCount > 9) "9+" else sknChOpenGamesCount.toString(),
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onError,
                                        lineHeight = 8.sp
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            if (currentOnboardingStep == OnboardingStep.PRIVACY_THREE_DOTS) {
                                viewModel.completeOnboardingStep(OnboardingStep.PRIVACY_THREE_DOTS)
                            }
                            showMenu = true
                        },
                        modifier = Modifier.onGloballyPositioned { moreVertRect = it.boundsInRoot() }
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(com.securechat.app.R.string.contacts_menu_options_cd))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        // ── LetheID-Anzeige mit Kopieren-Button ──────────────
                        currentUser?.letheId?.let { lid ->
                            val clipCtx = LocalContext.current
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(stringResource(com.securechat.app.R.string.contacts_my_lethaid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(lid, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                onClick = {
                                    val clipboard = clipCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("LetheID", lid))
                                    Toast.makeText(clipCtx, clipCtx.getString(com.securechat.app.R.string.contacts_lethaid_copied), Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                                trailingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(com.securechat.app.R.string.general_copy), modifier = Modifier.size(18.dp)) }
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_account)) },
                            onClick = {
                                showMenu = false
                                if (currentOnboardingStep == OnboardingStep.PRIVACY_ACCOUNT_ITEM) {
                                    viewModel.completeOnboardingStep(OnboardingStep.PRIVACY_ACCOUNT_ITEM)
                                }
                                onNavigateToSettings("account")
                            },
                            leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                            modifier = Modifier
                                .onGloballyPositioned { accountMenuItemRect = it.boundsInRoot() }
                                .let { m ->
                                    if (currentOnboardingStep == OnboardingStep.PRIVACY_ACCOUNT_ITEM)
                                        m.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                    else m
                                }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_app_settings)) },
                            onClick = { showMenu = false; onNavigateToAppSettings() },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_devices)) },
                            onClick = { showMenu = false; onNavigateToDevices() },
                            leadingIcon = { Icon(Icons.Default.DevicesOther, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_coins)) },
                            onClick = { showMenu = false; onNavigateToCoins() },
                            leadingIcon = { Icon(Icons.Default.MonetizationOn, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_sknch_lobby)) },
                            onClick = { showMenu = false; showGameSelectionDialog = true },
                            leadingIcon = { Icon(Icons.Default.SportsEsports, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_become_creator)) },
                            onClick = { showMenu = false; onNavigateToPayForCreator() },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_blocked)) },
                            onClick = { showMenu = false; onNavigateToBlockedUsers() },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_support)) },
                            onClick = { showMenu = false; onNavigateToSupport() },
                            leadingIcon = { Icon(Icons.Default.HeadsetMic, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_info)) },
                            onClick = { showMenu = false; onNavigateToSettings("info") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_family)) },
                            onClick = { showMenu = false; onNavigateToFamily() },
                            leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null) }
                        )
                        HorizontalDivider()
                        run {
                            val prefs by viewModel.userPrefs.collectAsState()
                            val p2pOn = prefs.p2pInternetEnabled
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            stringResource(com.securechat.app.R.string.contacts_menu_p2p_internet),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = p2pOn,
                                            onCheckedChange = null,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                },
                                onClick = { viewModel.setP2pInternetEnabled(!p2pOn) },
                                leadingIcon = {
                                    Icon(
                                        if (p2pOn) Icons.Default.WifiTethering else Icons.Default.WifiTetheringOff,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                        if (currentUser?.ageVerified == false) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_age_verify)) },
                                onClick = { showMenu = false; onNavigateToAgeVerification() },
                                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) }
                            )
                        }
                        if (currentUser?.isAdmin == true || currentUser?.isModerator == true) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_backend)) },
                                onClick = { showMenu = false; onNavigateToSettings("backend") },
                                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(com.securechat.app.R.string.contacts_menu_delete_account), color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; showDeleteAccountConfirm = true },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            val fabYOffset by animateDpAsState(
                targetValue = if (selectedDeleteId != null || longPressContact != null) (-120).dp else 0.dp,
                label = "fabOffset"
            )
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.offset(y = fabYOffset)
            ) {
                // Mini-FABs (aufgeklappt)
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── QR-Code-Karte mit LetheID / Rufnummer-Switch ──────────────
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.widthIn(max = 248.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // QR-Code-Anzeige
                                val activeQrBitmap = if (qrModeIsLetheId) letheIdQrBitmap else inviteQrBitmap
                                Box(
                                    modifier = Modifier
                                        .size(168.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (activeQrBitmap != null) {
                                        Image(
                                            bitmap = activeQrBitmap.asImageBitmap(),
                                            contentDescription = if (qrModeIsLetheId) "LetheID QR" else "Rufnummer QR",
                                            modifier = Modifier.size(152.dp)
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            if ((qrModeIsLetheId && currentUser?.letheId != null) ||
                                                (!qrModeIsLetheId && inviteLinkUrl != null)) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.QrCode2,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = Color.Black.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (qrModeIsLetheId) stringResource(com.securechat.app.R.string.contacts_qr_mode_lethaid) else stringResource(com.securechat.app.R.string.contacts_qr_mode_phone),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(10.dp))
                                // Toggle-Pill: LetheID | Rufnummer
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50.dp),
                                        color = if (qrModeIsLetheId) MaterialTheme.colorScheme.primary
                                                else Color.Transparent,
                                        modifier = Modifier.clickable { qrModeIsLetheId = true }
                                    ) {
                                        Text(
                                            stringResource(com.securechat.app.R.string.contacts_qr_mode_lethaid),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                            fontSize = 12.sp,
                                            color = if (qrModeIsLetheId) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50.dp),
                                        color = if (!qrModeIsLetheId) MaterialTheme.colorScheme.primary
                                                else Color.Transparent,
                                        modifier = Modifier.clickable {
                                            qrModeIsLetheId = false
                                            if (inviteLinkUrl == null) viewModel.generateInviteLink()
                                        }
                                    ) {
                                        Text(
                                            stringResource(com.securechat.app.R.string.contacts_qr_mode_phone),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                                            fontSize = 12.sp,
                                            color = if (!qrModeIsLetheId) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Gruppe erstellen
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                shadowElevation = 2.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    stringResource(com.securechat.app.R.string.contacts_fab_create_group),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 13.sp
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    showCreateGroupDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.Group, contentDescription = stringResource(com.securechat.app.R.string.contacts_fab_create_group))
                            }
                        }

                        // Kontakt hinzufügen
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.onGloballyPositioned { addContactMiniFabRect = it.boundsInRoot() }
                        ) {
                            Surface(
                                color = if (currentOnboardingStep == OnboardingStep.ADD_CONTACT_BUTTON)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                shadowElevation = 2.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    stringResource(com.securechat.app.R.string.contacts_fab_add_contact),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 13.sp
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    if (currentOnboardingStep == OnboardingStep.ADD_CONTACT_BUTTON) {
                                        viewModel.completeOnboardingStep(OnboardingStep.ADD_CONTACT_BUTTON)
                                    }
                                    fabExpanded = false
                                    showAddContactDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = stringResource(com.securechat.app.R.string.contacts_fab_add_contact))
                            }
                        }

                        // QR-Code scannen
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                shadowElevation = 2.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    "QR-Code scannen",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 13.sp
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    val options = ScanOptions().apply {
                                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                        setPrompt("LetheID- oder Rufnummer-QR scannen")
                                        setBeepEnabled(false)
                                        setOrientationLocked(false)
                                    }
                                    qrScanLauncher.launch(options)
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "QR-Code scannen")
                            }
                        }

                        // Kontakte importieren
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                                shadowElevation = 2.dp,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    stringResource(com.securechat.app.R.string.contacts_fab_import_contacts),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 13.sp
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    onNavigateToContactImport()
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Icon(Icons.Default.Contacts, contentDescription = stringResource(com.securechat.app.R.string.contacts_fab_import_contacts))
                            }
                        }
                    }
                }

                // Haupt-FAB
                FloatingActionButton(
                    onClick = {
                        if (currentOnboardingStep == OnboardingStep.ADD_CONTACT_FAB) {
                            viewModel.completeOnboardingStep(OnboardingStep.ADD_CONTACT_FAB)
                        }
                        fabExpanded = !fabExpanded
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.onGloballyPositioned { fabRect = it.boundsInRoot() }
                ) {
                    Icon(
                        if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (fabExpanded) stringResource(com.securechat.app.R.string.general_close) else stringResource(com.securechat.app.R.string.contacts_fab_new_cd),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {

        if (contactsInitialized && contacts.isEmpty() && groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(com.securechat.app.R.string.contacts_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(com.securechat.app.R.string.contacts_empty_subtitle), color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Suchfeld (halbe Breite) + Gruppen/Anrufe-Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Suchfeld nimmt die Hälfte der Breite ein
                    val searchInteractionSource = remember { MutableInteractionSource() }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        interactionSource = searchInteractionSource,
                        decorationBox = { innerTextField ->
                            OutlinedTextFieldDefaults.DecorationBox(
                                value = searchQuery,
                                innerTextField = innerTextField,
                                enabled = true,
                                singleLine = true,
                                visualTransformation = VisualTransformation.None,
                                interactionSource = searchInteractionSource,
                                placeholder = { Text(stringResource(com.securechat.app.R.string.contacts_search_placeholder), fontSize = 12.sp, color = Color(0xFFAAAAAA)) },
                                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = Color(0xFFAAAAAA)) },
                                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                                    top = 6.dp,
                                    bottom = 6.dp,
                                    start = 8.dp,
                                    end = 8.dp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF444444),
                                    focusedContainerColor = Color(0xFF2A2A2A),
                                    unfocusedContainerColor = Color(0xFF2A2A2A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                container = {
                                    OutlinedTextFieldDefaults.Container(
                                        enabled = true,
                                        isError = false,
                                        interactionSource = searchInteractionSource,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color(0xFF444444),
                                            focusedContainerColor = Color(0xFF2A2A2A),
                                            unfocusedContainerColor = Color(0xFF2A2A2A)
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                }
                            )
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    // Tab: Gruppen
                    val gruppenSelected = selectedContactTab == 1
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (gruppenSelected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable { selectedContactTab = if (gruppenSelected) 0 else 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (gruppenSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFAAAAAA)
                            )
                            Text(
                                stringResource(com.securechat.app.R.string.contacts_groups_section),
                                fontSize = 11.sp,
                                color = if (gruppenSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFAAAAAA)
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // Tab: Anrufe
                    val anrufeSelected = selectedContactTab == 2
                    BadgedBox(badge = {
                        if (callBadgeCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) { Text(if (callBadgeCount > 99) "99+" else "$callBadgeCount", fontSize = 9.sp) }
                        }
                    }) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (anrufeSelected) MaterialTheme.colorScheme.primary else Color(0xFF2A2A2A),
                            modifier = Modifier
                                .height(36.dp)
                                .clickable { selectedContactTab = if (anrufeSelected) 0 else 2 }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (anrufeSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFAAAAAA)
                                )
                                Text(
                                    stringResource(com.securechat.app.R.string.contacts_tab_calls),
                                    fontSize = 11.sp,
                                    color = if (anrufeSelected) MaterialTheme.colorScheme.onPrimary else Color(0xFFAAAAAA)
                                )
                            }
                        }
                    }
                }

                // Sketch n Check Hintergrund-Spiel Banner
                AnimatedVisibility(visible = sknChBg != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clickable { onNavigateToSknChLobby() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Sketch n Check",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    stringResource(com.securechat.app.R.string.contacts_sknch_players_waiting, sknChBg?.playersJoined ?: 0, sknChBg?.playersRequired ?: 0),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                            }
                            TextButton(onClick = { onNavigateToSknChLobby() }) {
                                Text(stringResource(com.securechat.app.R.string.contacts_sknch_join_game), fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Spieleinladungs-Banner
                AnimatedVisibility(visible = pendingGameInvite != null) {
                    val invite = pendingGameInvite
                    if (invite != null) {
                        val gameName = when (invite.gameType) {
                            "TIC_TAC_TOE" -> "Tic Tac Toe"
                            "ACTIVITY"    -> "Zeichnen & Raten"
                            else          -> "Jump & Run"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable { onNavigateToGames() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(com.securechat.app.R.string.contacts_game_invite_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        stringResource(com.securechat.app.R.string.contacts_game_invite_text, invite.fromName, gameName),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { onNavigateToGames() }) {
                                    Text(stringResource(com.securechat.app.R.string.contacts_game_invite_view), fontSize = 12.sp)
                                }
                                IconButton(
                                    onClick = { viewModel.clearPendingGameInvite() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(com.securechat.app.R.string.general_close),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                val pinnedGroups = groups.filter { it.groupId in pinnedGroupIds }
                val unpinnedGroups = groups.filter { it.groupId !in pinnedGroupIds }
                val pinnedContacts = filteredContacts.filter { it.userId in pinnedContactIds }
                val unpinnedContacts = filteredContacts.filter { it.userId !in pinnedContactIds }
                // Gemischte Kontaktliste: eigene ungepinnte Kontakte + Kontakte anderer Accounts,
                // zusammen alphabetisch sortiert (keine gemeinsame Aktivitäts-Zeitbasis über Accounts hinweg).
                val unpinnedRowEntries: List<ContactRowEntry> =
                    if (filteredForeignContacts.isEmpty()) {
                        unpinnedContacts.map { ContactRowEntry.Local(it) }
                    } else {
                        (unpinnedContacts.map { ContactRowEntry.Local(it) } +
                            filteredForeignContacts.map { ContactRowEntry.Foreign(it) })
                            .sortedBy { it.sortKey.lowercase() }
                    }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                when (page) {
                    // ── Tab 0: Chats (Standard-Ansicht) ──────────────────────
                    0 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (pinnedGroups.isNotEmpty()) {
                            items(pinnedGroups) { group ->
                                GroupItem(
                                    group = group,
                                    viewModel = viewModel,
                                    isPinned = true,
                                    isSelectedForDelete = selectedDeleteId == group.groupId,
                                    onClick = {
                                        selectedDeleteId = null
                                        onNavigateToGroupChat(group.groupId)
                                    },
                                    onLongPress = { selectedDeleteId = group.groupId },
                                    onDeleteClick = { deleteTargetGroup = group },
                                    onPinClick = { viewModel.toggleGroupPin(group.groupId) }
                                )
                            }
                        }
                        if (showSelfNotesEntry) {
                            item {
                                SelfNotesListItem(onClick = { onNavigateToChat("self_notes") })
                            }
                        }
                        if (pinnedContacts.isNotEmpty()) {
                            items(pinnedContacts) { contact ->
                                val lastMsg by viewModel.getLastMessageForChat(contact.userId).collectAsState(initial = null)
                                ContactItem(
                                    contact = contact,
                                    lastMessage = lastMsg,
                                    viewModel = viewModel,
                                    primaryColor = primaryColor,
                                    onClick = {
                                        selectedDeleteId = null
                                        onNavigateToChat(contact.userId)
                                    },
                                    isSelectedForDelete = selectedDeleteId == contact.userId,
                                    onLongPress = { longPressContact = contact },
                                    onDeleteClick = { deleteTargetContact = contact },
                                    onNavigateToStatusView = onNavigateToStatusView,
                                    onNavigateToStatusTab = { contactId ->
                                        viewModel.requestNavigateToContactStatus(contactId)
                                    },
                                    onProfileClick = { profileCardContact = contact }
                                )
                            }
                        }
                        items(
                            unpinnedRowEntries,
                            key = { entry ->
                                when (entry) {
                                    is ContactRowEntry.Local -> "local_${entry.contact.userId}"
                                    is ContactRowEntry.Foreign -> "foreign_${entry.entry.accountProfileKey}_${entry.entry.userId}"
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is ContactRowEntry.Local -> {
                                    val contact = entry.contact
                                    val lastMsg by viewModel.getLastMessageForChat(contact.userId).collectAsState(initial = null)
                                    ContactItem(
                                        contact = contact,
                                        lastMessage = lastMsg,
                                        viewModel = viewModel,
                                        primaryColor = primaryColor,
                                        onClick = {
                                            selectedDeleteId = null
                                            onNavigateToChat(contact.userId)
                                        },
                                        isSelectedForDelete = selectedDeleteId == contact.userId,
                                        onLongPress = { longPressContact = contact },
                                        onDeleteClick = { deleteTargetContact = contact },
                                        onNavigateToStatusView = onNavigateToStatusView,
                                        onNavigateToStatusTab = { contactId ->
                                            viewModel.requestNavigateToContactStatus(contactId)
                                        },
                                        onProfileClick = { profileCardContact = contact }
                                    )
                                }
                                is ContactRowEntry.Foreign -> {
                                    val foreign = entry.entry
                                    val foreignContact = ContactEntity(
                                        userId = foreign.userId,
                                        fakeNumber = "",
                                        username = foreign.displayName,
                                        publicKey = "",
                                        profileImageUrl = foreign.profileImageUrl,
                                        isVerified = foreign.isVerified
                                    )
                                    ContactItem(
                                        contact = foreignContact,
                                        lastMessage = null,
                                        viewModel = viewModel,
                                        primaryColor = primaryColor,
                                        onClick = { viewModel.switchAccount(foreign.accountProfileKey) },
                                        foreignAccountLabel = foreign.accountDisplayName
                                    )
                                }
                            }
                        }
                        if (unpinnedGroups.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(com.securechat.app.R.string.contacts_groups_section),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                HorizontalDivider()
                            }
                            items(unpinnedGroups) { group ->
                                GroupItem(
                                    group = group,
                                    viewModel = viewModel,
                                    isPinned = false,
                                    isSelectedForDelete = selectedDeleteId == group.groupId,
                                    onClick = {
                                        selectedDeleteId = null
                                        onNavigateToGroupChat(group.groupId)
                                    },
                                    onLongPress = { selectedDeleteId = group.groupId },
                                    onDeleteClick = { deleteTargetGroup = group },
                                    onPinClick = {
                                        if (pinnedGroupIds.size < 2 || group.groupId in pinnedGroupIds) {
                                            viewModel.toggleGroupPin(group.groupId)
                                        }
                                    }
                                )
                            }
                        }
                        if (hasSelfNotes && !showSelfNotesEntry) {
                            item {
                                SelfNotesListItem(onClick = { onNavigateToChat("self_notes") })
                            }
                        }
                    }

                    // ── Tab 1: Gruppen-only ───────────────────────────────────
                    1 -> {
                        val allGroups = pinnedGroups + unpinnedGroups
                        if (allGroups.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(com.securechat.app.R.string.contacts_no_groups), color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(allGroups) { group ->
                                    GroupItem(
                                        group = group,
                                        viewModel = viewModel,
                                        isPinned = group.groupId in pinnedGroupIds,
                                        isSelectedForDelete = selectedDeleteId == group.groupId,
                                        onClick = {
                                            selectedDeleteId = null
                                            onNavigateToGroupChat(group.groupId)
                                        },
                                        onLongPress = { selectedDeleteId = group.groupId },
                                        onDeleteClick = { deleteTargetGroup = group },
                                        onPinClick = {
                                            if (pinnedGroupIds.size < 2 || group.groupId in pinnedGroupIds) {
                                                viewModel.toggleGroupPin(group.groupId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── Tab 2: Anruf-Verlauf ──────────────────────────────────
                    2 -> {
                        val myName = currentUser?.name ?: currentUser?.fakeNumber ?: ""
                        val callSummaries = remember(callMessages) {
                            buildCallSummaries(callMessages, myName)
                        }
                        val callRecordings by viewModel.callRecordings.collectAsState()
                        // Aufzeichnungen laden + Anruf-Benachrichtigungen als gelesen+erhalten
                        // markieren wenn der Tab geöffnet wird
                        DisposableEffect(Unit) {
                            viewModel.setCallListVisible(true)
                            onDispose { viewModel.setCallListVisible(false) }
                        }
                        LaunchedEffect(Unit) { viewModel.loadCallRecordings() }

                        // Jede Aufzeichnung GENAU EINEM Anruf-Eintrag zuordnen: dem Eintrag
                        // desselben Partners, dessen Zeitpunkt der Aufnahme-Startzeit am
                        // nächsten liegt. Sonst würden alle Aufnahmen eines Partners (auch
                        // künftiger Anrufe am selben Tag) an jedem Eintrag erscheinen.
                        val recAssignment = remember(callRecordings, callSummaries) {
                            val map = HashMap<CallSummary, MutableList<com.securechat.app.data.network.CallRecordingResponse>>()
                            for (rec in callRecordings) {
                                val recMs = parseRecStartMs(rec.startedAt)
                                val candidate = callSummaries
                                    .filter { it.chatId == rec.partnerId }
                                    .minByOrNull { kotlin.math.abs(it.timestamp - recMs) }
                                if (candidate != null) {
                                    map.getOrPut(candidate) { mutableListOf() }.add(rec)
                                }
                            }
                            map
                        }

                        // Dialog: Aufzeichnungen für einen bestimmten Anruf anzeigen
                        var recordingsDialogRecs by remember { mutableStateOf<List<com.securechat.app.data.network.CallRecordingResponse>>(emptyList()) }
                        var recordingsDialogPartnerName by remember { mutableStateOf("") }
                        if (recordingsDialogRecs.isNotEmpty()) {
                            val recs = recordingsDialogRecs
                            AlertDialog(
                                onDismissRequest = { recordingsDialogRecs = emptyList() },
                                title = { Text("Aufzeichnungen – $recordingsDialogPartnerName") },
                                text = {
                                    if (recs.isEmpty()) {
                                        Text("Keine Aufzeichnungen vorhanden.", fontSize = 14.sp)
                                    } else {
                                        Column {
                                            recs.forEach { rec ->
                                                val dStr = formatCallDuration(rec.durationSeconds.toLong())
                                                val label = when (rec.status) {
                                                    "ready" -> "📹 $dStr"
                                                    "processing" -> "⏳ wird verarbeitet…"
                                                    "failed" -> "❌ fehlgeschlagen"
                                                    else -> "⏺ aufzeichnend…"
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                                }
                                                if (rec.hasCombinedFile || rec.hasSelfFile || rec.hasPartnerFile) {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        if (rec.hasCombinedFile) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    viewModel.downloadCallRecording(rec.id, "combined")
                                                                    recordingsDialogRecs = emptyList()
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(bottom = 4.dp),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Gesamt (beide)", fontSize = 12.sp)
                                                            }
                                                        }
                                                        if (rec.hasSelfFile || rec.hasPartnerFile) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(bottom = 6.dp),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                if (rec.hasSelfFile) {
                                                                    OutlinedButton(
                                                                        onClick = {
                                                                            viewModel.downloadCallRecording(rec.id, "self")
                                                                            recordingsDialogRecs = emptyList()
                                                                        },
                                                                        modifier = Modifier.weight(1f),
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                        Spacer(Modifier.width(4.dp))
                                                                        Text("Dein Bild", fontSize = 12.sp)
                                                                    }
                                                                }
                                                                if (rec.hasPartnerFile) {
                                                                    OutlinedButton(
                                                                        onClick = {
                                                                            viewModel.downloadCallRecording(rec.id, "partner")
                                                                            recordingsDialogRecs = emptyList()
                                                                        },
                                                                        modifier = Modifier.weight(1f),
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                                    ) {
                                                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                        Spacer(Modifier.width(4.dp))
                                                                        Text("Partner", fontSize = 12.sp)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { recordingsDialogRecs = emptyList() }) { Text("Schließen") }
                                }
                            )
                        }

                        if (callSummaries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(com.securechat.app.R.string.contacts_no_calls), color = Color.Gray, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(callSummaries) { summary ->
                                    val partner = contacts.find { it.userId == summary.chatId }
                                    val partnerName = partner?.customAlias ?: partner?.username ?: summary.chatId
                                    val iconColor = when (summary.outcome) {
                                        CallOutcome.ACCEPTED -> Color(0xFF4CAF50)
                                        CallOutcome.MISSED -> Color(0xFFF44336)
                                        CallOutcome.REJECTED -> Color(0xFFF44336)
                                        CallOutcome.OUTGOING_MISSED -> Color(0xFFFF9800)
                                    }
                                    val callTypeIcon = if (summary.isVideo) Icons.Default.Videocam else Icons.Default.Call
                                    val timeStr = remember(summary.timestamp) {
                                        SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN)
                                            .format(Date(summary.timestamp))
                                    }
                                    val subtitleText = when (summary.outcome) {
                                        CallOutcome.ACCEPTED -> {
                                            val durationStr = formatCallDuration(summary.durationSeconds)
                                            if (summary.isOutgoing) stringResource(com.securechat.app.R.string.contacts_call_outgoing_duration, durationStr)
                                            else stringResource(com.securechat.app.R.string.contacts_call_incoming_duration, durationStr)
                                        }
                                        CallOutcome.MISSED -> stringResource(com.securechat.app.R.string.contacts_call_missed)
                                        CallOutcome.REJECTED -> stringResource(com.securechat.app.R.string.contacts_call_rejected)
                                        CallOutcome.OUTGOING_MISSED -> stringResource(com.securechat.app.R.string.contacts_call_not_answered)
                                    }
                                    // Aufzeichnungen, die GENAU diesem Anruf zugeordnet sind
                                    val partnerRecs = recAssignment[summary] ?: emptyList()
                                    val hasRecs = partnerRecs.isNotEmpty()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onNavigateToChat(summary.chatId) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = callTypeIcon,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                partnerName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                subtitleText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            timeStr,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Download-Symbol wenn Aufzeichnungen vorhanden
                                        if (hasRecs) {
                                            Spacer(Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    // Genau eine Aufnahme mit genau einer verfügbaren Datei → Direktdownload.
                                                    val single = partnerRecs.singleOrNull()
                                                    if (single != null && single.hasSelfFile && !single.hasPartnerFile) {
                                                        viewModel.downloadCallRecording(single.id, "self")
                                                    } else if (single != null && single.hasPartnerFile && !single.hasSelfFile) {
                                                        viewModel.downloadCallRecording(single.id, "partner")
                                                    } else {
                                                        recordingsDialogRecs = partnerRecs
                                                        recordingsDialogPartnerName = partnerName
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Download,
                                                    contentDescription = "Aufzeichnung herunterladen",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
                                }
                            }
                        }
                    }
                    else -> {}
                }
                } // closes HorizontalPager
            }
        }

        // Globaler Lumis-Broadcast (Admin-Feature): Wird einmalig abgespielt wenn ein neues Broadcast ankommt
        val globalLumis by viewModel.globalLumis.collectAsState()
        if (globalLumis != LumisType.NONE) {
            LumisPlayer(
                lumisType = globalLumis,
                onAnimationEnd = { viewModel.onGlobalLumisDismissed() },
                modifier = Modifier.fillMaxSize()
            )
        }
        } // closes Box
    } // closes Scaffold content lambda

    // ── Kontakt löschen ───────────────────────────────────────────────────────
    deleteTargetContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleteTargetContact = null; selectedDeleteId = null },
            title = { Text(stringResource(com.securechat.app.R.string.contacts_delete_title)) },
            text = {
                Text(stringResource(com.securechat.app.R.string.contacts_delete_text, contact.customAlias ?: contact.username ?: contact.fakeNumber))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteContact(contact)
                    deleteTargetContact = null
                    selectedDeleteId = null
                }) {
                    Text(stringResource(com.securechat.app.R.string.contacts_delete_title).replace("?",""), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetContact = null; selectedDeleteId = null }) {
                    Text(stringResource(com.securechat.app.R.string.general_cancel))
                }
            }
        )
    }

    // ── Gruppe verlassen ──────────────────────────────────────────────────────
    deleteTargetGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteTargetGroup = null; selectedDeleteId = null },
            title = { Text(stringResource(com.securechat.app.R.string.contacts_leave_group_title)) },
            text = { Text(stringResource(com.securechat.app.R.string.contacts_leave_group_text, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.leaveGroup(group)
                    deleteTargetGroup = null
                    selectedDeleteId = null
                }) {
                    Text(stringResource(com.securechat.app.R.string.contacts_leave_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetGroup = null; selectedDeleteId = null }) {
                    Text(stringResource(com.securechat.app.R.string.general_cancel))
                }
            }
        )
    }

    if (showAddContactDialog) {
        AddContactDialog(
            onDismiss = { showAddContactDialog = false },
            onAddContact = { num, name ->
                viewModel.addContact(num, name)
                showAddContactDialog = false
            },
            onLookupByPhone = { phoneE164, onResult ->
                viewModel.lookupUserByPhone(phoneE164, onResult)
            },
            onSearchBots = { query, onResult ->
                viewModel.searchBots(query, onResult)
            },
            onLoadFeaturedBots = { onResult ->
                viewModel.getFeaturedBots(onResult)
            },
            onSendNearbyLike = { username, onResult ->
                viewModel.sendNearbyLikeByUsername(username, onResult)
            },
            isAdmin = currentUser?.isAdmin == true,
            isOnboardingPhoneStep = currentOnboardingStep == OnboardingStep.ADD_CONTACT_PHONE,
            onContactRequestSent = {
                if (currentOnboardingStep == OnboardingStep.ADD_CONTACT_PHONE) {
                    viewModel.completeOnboardingStep(OnboardingStep.ADD_CONTACT_PHONE)
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = contacts,
            onDismiss = { showCreateGroupDialog = false },
            onCreateGroup = { name, memberIds ->
                viewModel.createGroup(name, memberIds)
                showCreateGroupDialog = false
            }
        )
    }

    // ── Long-Press Aktions-Dialog ─────────────────────────────────────────────
    longPressContact?.let { contact ->
        val isContactPinned = contact.userId in pinnedContactIds
        val blockedUsersLp by viewModel.blockedUsers.collectAsState()
        val isContactBlockedLp = remember(blockedUsersLp, contact.userId) {
            blockedUsersLp.any { it.blockedId == contact.userId }
        }
        AlertDialog(
            onDismissRequest = { longPressContact = null },
            title = { Text(contact.customAlias ?: contact.username ?: contact.fakeNumber) },
            text = { Text(stringResource(com.securechat.app.R.string.contacts_long_press_text)) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.toggleContactPin(contact.userId)
                            longPressContact = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isContactPinned) stringResource(com.securechat.app.R.string.contacts_unpin) else stringResource(com.securechat.app.R.string.contacts_pin))
                    }
                    TextButton(
                        onClick = {
                            renameAliasInput = contact.customAlias ?: contact.username ?: ""
                            renameTargetContact = contact
                            longPressContact = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.securechat.app.R.string.contacts_rename))
                    }
                    TextButton(
                        onClick = {
                            viewModel.renewHandshake(contact)
                            longPressContact = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.securechat.app.R.string.contacts_renew_handshake))
                    }
                    run {
                        val prefs by viewModel.userPrefs.collectAsState()
                        if (prefs.p2pInternetEnabled) {
                            val p2pEnabledForContact = contact.userId !in prefs.p2pDisabledContacts
                            TextButton(
                                onClick = {
                                    viewModel.setP2pContactEnabled(contact.userId, !p2pEnabledForContact)
                                    longPressContact = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (p2pEnabledForContact)
                                        stringResource(com.securechat.app.R.string.contacts_p2p_disable_contact)
                                    else
                                        stringResource(com.securechat.app.R.string.contacts_p2p_enable_contact)
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            longPressContact = null
                            deleteTargetContact = contact
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(com.securechat.app.R.string.contacts_delete_contact_cd), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = {
                            longPressContact = null
                            if (isContactBlockedLp) {
                                viewModel.unblockUser(contact.userId)
                            } else {
                                blockTargetContact = contact
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isContactBlockedLp)
                                stringResource(com.securechat.app.R.string.contacts_unblock_confirm)
                            else
                                stringResource(com.securechat.app.R.string.contacts_block_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { longPressContact = null }) { Text(stringResource(com.securechat.app.R.string.general_cancel)) }
            }
        )
    }

    // ── Namen-ändern-Dialog ───────────────────────────────────────────────────
    renameTargetContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { renameTargetContact = null },
            title = { Text(stringResource(com.securechat.app.R.string.contacts_rename)) },
            text = {
                OutlinedTextField(
                    value = renameAliasInput,
                    onValueChange = { renameAliasInput = it },
                    label = { Text(stringResource(com.securechat.app.R.string.contacts_display_name_label)) },
                    placeholder = { Text(contact.username ?: contact.fakeNumber) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameContact(contact, renameAliasInput)
                    renameTargetContact = null
                }) { Text(stringResource(com.securechat.app.R.string.general_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetContact = null }) {
                    Text(stringResource(com.securechat.app.R.string.general_cancel))
                }
            }
        )
    }

    // ── Blockieren-Bestätigungsdialog ─────────────────────────────────────────
    blockTargetContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { blockTargetContact = null },
            title = { Text(stringResource(com.securechat.app.R.string.contacts_block_title)) },
            text = {
                Text(
                    stringResource(com.securechat.app.R.string.contacts_block_text, contact.customAlias ?: contact.username ?: contact.fakeNumber)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.blockContact(contact)
                    blockTargetContact = null
                }) {
                    Text(stringResource(com.securechat.app.R.string.contacts_block_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockTargetContact = null }) { Text(stringResource(com.securechat.app.R.string.general_cancel)) }
            }
        )
    }

    // ── Onboarding-Tip-Overlays ────────────────────────────────────────────────
    when (currentOnboardingStep) {
        OnboardingStep.ADD_CONTACT_FAB -> {
            if (fabRect != Rect.Zero) {
                OnboardingTipOverlay(
                    targetRect = fabRect,
                    title = stringResource(R.string.onboarding_tip_add_contact_fab_title),
                    body = stringResource(R.string.onboarding_tip_add_contact_fab_body)
                )
            }
        }
        OnboardingStep.ADD_CONTACT_BUTTON -> {
            if (addContactMiniFabRect != Rect.Zero) {
                OnboardingTipOverlay(
                    targetRect = addContactMiniFabRect,
                    title = stringResource(R.string.onboarding_tip_add_contact_button_title),
                    body = stringResource(R.string.onboarding_tip_add_contact_button_body)
                )
            }
        }
        OnboardingStep.PRIVACY_THREE_DOTS -> {
            if (moreVertRect != Rect.Zero) {
                OnboardingTipOverlay(
                    targetRect = moreVertRect,
                    title = stringResource(R.string.onboarding_tip_privacy_dots_title),
                    body = stringResource(R.string.onboarding_tip_privacy_dots_body)
                )
            }
        }
        OnboardingStep.PRIVACY_ACCOUNT_ITEM -> {
            // Kein Pfeil-Overlay: DropdownMenu-Elemente liefern Koordinaten relativ
            // zum Popup-Fenster des Menüs, nicht zum Hauptfenster. Das Menüelement
            // hat bereits eine visuelle Hervorhebung (primaryContainer-Hintergrund).
        }
        else -> {}
    }

    // ── Saisonale Grußkarte (5x Tippen auf Animation) ─────────────────────────
    val greetingUsername = currentUser?.name?.takeIf { it.isNotBlank() }
        ?: currentUser?.fakeNumber ?: ""
    if (showGreetingOverlay) {
        when (eventAnimation) {
            "easter" -> EasterGreetingDialog(
                onDismiss = { showGreetingOverlay = false },
                username = greetingUsername,
                onShareCard = { uri ->
                    viewModel.setPendingShare(uri = uri, mimeType = "image/jpeg")
                    showGreetingOverlay = false
                }
            )
            "may" -> MayGreetingDialog(
                onDismiss = { showGreetingOverlay = false },
                username = greetingUsername,
                onShareCard = { uri ->
                    viewModel.setPendingShare(uri = uri, mimeType = "image/jpeg")
                    showGreetingOverlay = false
                }
            )
            "xmas" -> XmasGreetingDialog(onDismiss = { showGreetingOverlay = false })
        }
    }

    profileCardContact?.let { contact ->
        val profileMessages by viewModel.getMessagesForChat(contact.userId)
            .collectAsState(initial = emptyList())
        ContactProfileDialog(
            contact = contact,
            messages = profileMessages,
            viewModel = viewModel,
            onDismiss = { profileCardContact = null }
        )
    }

    if (showGameSelectionDialog) {
        GameSelectionDialog(
            sknChOpenGamesCount = sknChOpenGamesCount,
            onNavigateToSknChLobby = { showGameSelectionDialog = false; onNavigateToSknChLobby() },
            onNavigateToJumpOrDie = { showGameSelectionDialog = false; onNavigateToJumpOrDieGame() },
            onNavigateToPinball = { showGameSelectionDialog = false; onNavigateToPinballGame() },
            onDismiss = { showGameSelectionDialog = false }
        )
    }

}

// ── Spieleauswahl-Dialog ──────────────────────────────────────────────────

@Composable
private fun GameSelectionDialog(
    sknChOpenGamesCount: Int,
    onNavigateToSknChLobby: () -> Unit,
    onNavigateToJumpOrDie: () -> Unit,
    onNavigateToPinball: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Spieleauswahl",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ─── Multiplayer ──────────────────────────────────────────
                Text(
                    "Multiplayer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSknChLobby() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Brush,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sketch n Check",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Multiplayer öffentlich",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                        if (sknChOpenGamesCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (sknChOpenGamesCount > 9) "9+" else sknChOpenGamesCount.toString(),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onError,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ─── Singleplayer ─────────────────────────────────────────
                Text(
                    "Singleplayer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToJumpOrDie() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Jump or Die",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Singleplayer Highscore",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToPinball() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Flipper",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Lethe: Memory's End – Highscore",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

// ── Anruf-Zusammenfassung ─────────────────────────────────────────────────

private enum class CallOutcome { ACCEPTED, MISSED, REJECTED, OUTGOING_MISSED }

private data class CallSummary(
    val chatId: String,
    val timestamp: Long,        // Zeitpunkt des Anrufs (accepted oder initiated)
    val outcome: CallOutcome,
    val isVideo: Boolean,
    val isOutgoing: Boolean,
    val durationSeconds: Long   // Dauer in Sekunden (nur bei ACCEPTED > 0)
)

/**
 * Gruppiert die rohen Anruf-Nachrichten zu je einem Eintrag pro Anruf.
 * Logik: call_ended/call_accepted Nachrichten werden mit dem zeitlich nächsten
 * call_initiated für denselben chatId zusammengeführt. Übrig bleibende
 * call_initiated ohne Antwort = verpasst/abgelehnt.
 */
private fun buildCallSummaries(messages: List<MessageEntity>, myName: String): List<CallSummary> {
    if (messages.isEmpty()) return emptyList()

    // Nachrichten chronologisch sortieren (älteste zuerst) für korrekte Zuordnung
    val sorted = messages.sortedBy { it.timestamp }
    val used = mutableSetOf<Long>() // localIds die schon zugeordnet sind
    val summaries = mutableListOf<CallSummary>()

    // 1) Beendete Anrufe (call_ended) → suche zugehöriges call_accepted
    for (ended in sorted.filter { it.mediaType == "call_ended" }) {
        used.add(ended.localId)
        val content = ended.content ?: ""
        val isVideo = content.contains("Video", ignoreCase = true)
        val isOutgoing = myName.isNotBlank() && content.contains("von $myName", ignoreCase = true)

        // Zugehöriges call_accepted für selben chatId finden (zeitlich davor, noch nicht verwendet)
        val accepted = sorted.lastOrNull {
            it.mediaType == "call_accepted" && it.chatId == ended.chatId
                && it.timestamp <= ended.timestamp && it.localId !in used
        }
        val acceptedTs = accepted?.timestamp ?: ended.timestamp
        if (accepted != null) used.add(accepted.localId)

        // Zugehöriges call_initiated ebenfalls als verwendet markieren
        val initiated = sorted.lastOrNull {
            it.mediaType == "call_initiated" && it.chatId == ended.chatId
                && it.timestamp <= (accepted?.timestamp ?: ended.timestamp) && it.localId !in used
        }
        if (initiated != null) used.add(initiated.localId)

        val durationSec = if (accepted != null) (ended.timestamp - accepted.timestamp) / 1000 else 0L
        summaries.add(CallSummary(ended.chatId, acceptedTs, CallOutcome.ACCEPTED, isVideo, isOutgoing, durationSec))
    }

    // 2) Angenommene Anrufe ohne call_ended (noch laufend oder alte Daten)
    for (acc in sorted.filter { it.mediaType == "call_accepted" && it.localId !in used }) {
        used.add(acc.localId)
        val content = acc.content ?: ""
        val isVideo = content.contains("Video", ignoreCase = true)
        val isOutgoing = myName.isNotBlank() && content.contains("von $myName", ignoreCase = true)
        // Zugehöriges call_initiated markieren
        val initiated = sorted.lastOrNull {
            it.mediaType == "call_initiated" && it.chatId == acc.chatId
                && it.timestamp <= acc.timestamp && it.localId !in used
        }
        if (initiated != null) used.add(initiated.localId)
        summaries.add(CallSummary(acc.chatId, acc.timestamp, CallOutcome.ACCEPTED, isVideo, isOutgoing, 0L))
    }

    // 3) Verpasste Anrufe
    for (missed in sorted.filter { it.mediaType == "call_missed" && it.localId !in used }) {
        used.add(missed.localId)
        val content = missed.content ?: ""
        val isVideo = content.contains("Video", ignoreCase = true)
        val isOutgoing = myName.isNotBlank() && content.contains("von $myName", ignoreCase = true)
        summaries.add(CallSummary(missed.chatId, missed.timestamp, CallOutcome.MISSED, isVideo, isOutgoing, 0L))
    }

    // 4) Abgelehnte Anrufe
    for (rej in sorted.filter { it.mediaType == "call_rejected" && it.localId !in used }) {
        used.add(rej.localId)
        val content = rej.content ?: ""
        val isVideo = content.contains("Video", ignoreCase = true)
        val isOutgoing = myName.isNotBlank() && content.contains("von $myName", ignoreCase = true)
        summaries.add(CallSummary(rej.chatId, rej.timestamp, CallOutcome.REJECTED, isVideo, isOutgoing, 0L))
    }

    // 5) Übrige call_initiated ohne Zuordnung → ausgehend nicht angenommen oder eingehend verpasst
    for (init in sorted.filter { it.mediaType == "call_initiated" && it.localId !in used }) {
        val content = init.content ?: ""
        val isVideo = content.contains("Video", ignoreCase = true)
        val isOutgoing = myName.isNotBlank() && content.contains("von $myName", ignoreCase = true)
        val outcome = if (isOutgoing) CallOutcome.OUTGOING_MISSED else CallOutcome.MISSED
        summaries.add(CallSummary(init.chatId, init.timestamp, outcome, isVideo, isOutgoing, 0L))
    }

    // Neueste zuerst
    return summaries.sortedByDescending { it.timestamp }
}

private fun formatCallDuration(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

/**
 * Parst den ISO-Startzeitpunkt einer Aufzeichnung (z. B. "2026-06-18T17:12:34" oder mit
 * Mikrosekunden) in Millisekunden. Nur die ersten 19 Zeichen werden ausgewertet; bei
 * Fehler 0L (→ ältester möglicher Zeitpunkt, Zuordnung über minimale Distanz bleibt robust).
 */
private fun parseRecStartMs(startedAt: String): Long {
    return try {
        val base = if (startedAt.length >= 19) startedAt.substring(0, 19) else startedAt
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.parse(base)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactItem(
    contact: ContactEntity,
    lastMessage: MessageEntity?,
    viewModel: MainViewModel,
    primaryColor: Color,
    onClick: () -> Unit,
    isSelectedForDelete: Boolean = false,
    onLongPress: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onNavigateToStatusView: (statusId: String) -> Unit = {},
    onNavigateToStatusTab: ((contactId: String) -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    /** Falls gesetzt: Kontakt eines anderen, nicht aktiven Accounts (gemischte Kontaktliste) –
     * zeigt den Account-Namen als Zusatz in Neon-Gelb hinter dem Kontaktnamen. */
    foreignAccountLabel: String? = null
) {
    val allActiveStatuses by viewModel.activeStatuses.collectAsState(initial = emptyList())
    val viewedStatusIds by viewModel.viewedStatusIds.collectAsState()
    val contactStatuses = remember(allActiveStatuses, contact.userId) {
        allActiveStatuses.filter { it.userId == contact.userId }.sortedBy { it.createdAt }
    }
    val avatarMult = viewModel.userPrefs.collectAsState().value.avatarSizeMultiplier
    val hasStatus = contactStatuses.isNotEmpty() && contactStatuses.any { it.statusId !in viewedStatusIds }
    val firstStatusId = contactStatuses.firstOrNull()?.statusId

    // Ungelesen-Zählung als Echtzeit-Flow
    val unreadCount by viewModel.getUnreadCountForContact(contact.userId)
        .collectAsState(initial = 0)

    // Tipp-Indikator
    val typingContactIds by viewModel.typingContactIds.collectAsState()
    val isTyping = contact.userId in typingContactIds

    // Fett-Gewicht: deutlich stärker wenn ungelesen
    val headlineWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.SemiBold

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val avatarShape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .size((52f * avatarMult).dp)
                .then(
                    if (hasStatus)
                        Modifier.clickable {
                            viewModel.prepareStatusGroup(contactStatuses)
                            firstStatusId?.let { onNavigateToStatusView(it) }
                        }
                    else if (onProfileClick != null && !contact.isAnonymous)
                        Modifier.clickable { onProfileClick() }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasStatus) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, primaryColor, avatarShape)
                )
            }
            val profileImageAbsoluteUrl = contact.profileImageUrl?.let { url ->
                if (url.startsWith("http")) url else "https://letheapp.de$url"
            }
            if (!profileImageAbsoluteUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = profileImageAbsoluteUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size((46f * avatarMult).dp)
                        .clip(avatarShape)
                )
            } else {
                Surface(
                    modifier = Modifier.size((46f * avatarMult).dp),
                    shape = avatarShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size((28f * avatarMult).dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.customAlias ?: contact.username ?: contact.fakeNumber,
                    fontWeight = headlineWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f)
                )
                if (contact.isBot) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(com.securechat.app.R.string.contacts_bot_tag),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (contact.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verifiziert",
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (foreignAccountLabel != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "($foreignAccountLabel)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonYellow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                isTyping -> {
                    Text(
                        text = stringResource(com.securechat.app.R.string.contacts_typing),
                        fontSize = 13.sp,
                        color = primaryColor,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1
                    )
                }
                lastMessage != null -> {
                    val isFromMe = lastMessage.senderId != contact.userId
                    val preview = when (lastMessage.mediaType) {
                        "image" -> stringResource(com.securechat.app.R.string.contacts_preview_image)
                        "video" -> stringResource(com.securechat.app.R.string.contacts_preview_video)
                        "audio" -> stringResource(com.securechat.app.R.string.contacts_preview_audio)
                        "poll" -> stringResource(com.securechat.app.R.string.contacts_preview_poll)
                        "call" -> "📞 Keine Antwort"
                        else -> {
                            val c = lastMessage.content ?: ""
                            if (c.contains("📍 https://maps.google.com") || c.contains("maps.app.goo.gl") || c.contains("goo.gl/maps")) stringResource(com.securechat.app.R.string.contacts_preview_location) else c
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFromMe) {
                            Icon(
                                imageVector = if (lastMessage.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                tint = if (lastMessage.isRead) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            text = preview,
                            fontSize = 13.sp,
                            fontWeight = if (unreadCount > 0 && !isFromMe) FontWeight.Medium else FontWeight.Normal,
                            color = if (unreadCount > 0 && !isFromMe)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                contact.fakeNumber.isNotBlank() && (contact.customAlias != null || contact.username != null) -> {
                    Text(contact.fakeNumber, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (isSelectedForDelete) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(com.securechat.app.R.string.contacts_delete_contact_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (lastMessage != null) {
                    Text(
                        text = formatMessageTime(lastMessage.timestamp),
                        fontSize = 11.sp,
                        color = if (unreadCount > 0) primaryColor.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    group: GroupEntity,
    viewModel: MainViewModel,
    isPinned: Boolean = false,
    isSelectedForDelete: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onPinClick: () -> Unit = {}
) {
    val unreadCount by viewModel.getUnreadCountForGroup(group.groupId)
        .collectAsState(initial = 0)
    val lastMsg by viewModel.getLastMessageForChat(group.groupId).collectAsState(initial = null)
    val currentUser by viewModel.currentUser.collectAsState()
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())

    val previewImage = stringResource(com.securechat.app.R.string.contacts_preview_image)
    val previewVideo = stringResource(com.securechat.app.R.string.contacts_preview_video)
    val previewAudio = stringResource(com.securechat.app.R.string.contacts_preview_audio)
    val previewPoll = stringResource(com.securechat.app.R.string.contacts_preview_poll)
    val previewLocation = stringResource(com.securechat.app.R.string.contacts_preview_location)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (group.groupImageUrl != null) {
                coil.compose.AsyncImage(
                    model = group.groupImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                group.name,
                fontWeight = if (unreadCount > 0) FontWeight.ExtraBold else FontWeight.Medium
            )
            if (lastMsg != null) {
                val msg = lastMsg!!
                val senderName = if (msg.senderId == currentUser?.userId) {
                    "Du"
                } else {
                    val contact = contacts.find { it.userId == msg.senderId }
                    contact?.customAlias ?: contact?.username ?: contact?.fakeNumber ?: msg.senderId
                }
                val msgPreview = when (msg.mediaType) {
                    "image" -> previewImage
                    "video" -> previewVideo
                    "audio" -> previewAudio
                    "poll" -> previewPoll
                    "call" -> "📞 Keine Antwort"
                    else -> {
                        val c = msg.content ?: ""
                        if (c.contains("📍 https://maps.google.com") || c.contains("maps.app.goo.gl") || c.contains("goo.gl/maps")) previewLocation else c
                    }
                }
                Text(
                    text = "$senderName: $msgPreview",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(stringResource(com.securechat.app.R.string.contacts_group_members, group.memberCount), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        if (isSelectedForDelete) {
            // Pin-Symbol links vom Papierkorb
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = if (isPinned) stringResource(com.securechat.app.R.string.contacts_unpin) else stringResource(com.securechat.app.R.string.contacts_pin),
                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(com.securechat.app.R.string.contacts_leave_group_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            if (unreadCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else "$unreadCount",
                        fontSize = 11.sp
                    )
                }
            } else if (isPinned) {
                // Kleiner Pin-Indikator wenn angepinnt aber nicht im Delete-Modus
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SelfNotesListItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(com.securechat.app.R.string.contacts_own_notes),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(com.securechat.app.R.string.contacts_own_notes_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowCal = Calendar.getInstance()
    return if (
        msgCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) &&
        msgCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
    ) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("dd.MM.", Locale.getDefault()).format(Date(timestamp))
    }
}

// ─── Grußkarten-Generator ────────────────────────────────────────────────────

private fun generateAndSaveEasterCard(context: android.content.Context, username: String): Uri? {
    return try {
        val w = 900; val h = 1260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val p = AndroidPaint().apply { isAntiAlias = true }

        // Hintergrund: sanftes Rosa oben, hellgelb unten
        p.color = android.graphics.Color.parseColor("#FCE4EC")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.65f, p)
        p.color = android.graphics.Color.parseColor("#FFF9C4")
        canvas.drawRect(0f, h * 0.65f, w.toFloat(), h.toFloat(), p)
        // Gras
        p.color = android.graphics.Color.parseColor("#A5D6A7")
        canvas.drawRect(0f, h * 0.8f, w.toFloat(), h.toFloat(), p)

        // Osterhasen-Figur (links)
        val bx = 200f; val by_ = 430f
        // Körper
        p.color = android.graphics.Color.parseColor("#F5F5F5")
        canvas.drawOval(bx - 85f, by_ - 70f, bx + 85f, by_ + 130f, p)
        // Kopf
        canvas.drawCircle(bx, by_ - 145f, 90f, p)
        // Ohren
        p.color = android.graphics.Color.parseColor("#F5F5F5")
        canvas.drawOval(bx - 60f, by_ - 360f, bx - 18f, by_ - 145f, p)
        canvas.drawOval(bx + 18f, by_ - 360f, bx + 60f, by_ - 145f, p)
        p.color = android.graphics.Color.parseColor("#F48FB1")
        canvas.drawOval(bx - 53f, by_ - 350f, bx - 25f, by_ - 160f, p)
        canvas.drawOval(bx + 25f, by_ - 350f, bx + 53f, by_ - 160f, p)
        // Augen
        p.color = android.graphics.Color.parseColor("#4A148C")
        canvas.drawCircle(bx - 28f, by_ - 160f, 12f, p)
        canvas.drawCircle(bx + 28f, by_ - 160f, 12f, p)
        // Nase
        p.color = android.graphics.Color.parseColor("#F48FB1")
        canvas.drawCircle(bx, by_ - 125f, 10f, p)
        // Wangen
        p.alpha = 110
        canvas.drawCircle(bx - 48f, by_ - 140f, 24f, p)
        canvas.drawCircle(bx + 48f, by_ - 140f, 24f, p)
        p.alpha = 255
        // Schwanz
        p.color = android.graphics.Color.parseColor("#F5F5F5")
        canvas.drawCircle(bx + 90f, by_ + 60f, 30f, p)
        // Schleife
        p.color = android.graphics.Color.parseColor("#F48FB1")
        canvas.drawOval(bx - 30f, by_ - 10f, bx, by_ + 10f, p)
        canvas.drawOval(bx, by_ - 10f, bx + 30f, by_ + 10f, p)
        p.color = android.graphics.Color.parseColor("#E91E63")
        canvas.drawCircle(bx, by_, 9f, p)

        // Eier (rechts und Mitte unten)
        val eggColors = listOf("#F48FB1", "#CE93D8", "#A5D6A7", "#FFF176", "#FFCCBC", "#80DEEA")
        val eggData = listOf(
            Triple(680f, 430f, 0), Triple(760f, 520f, 1), Triple(620f, 520f, 2),
            Triple(350f, 880f, 3), Triple(500f, 900f, 4), Triple(650f, 875f, 5)
        )
        val stripePaint = AndroidPaint().apply { isAntiAlias = true; style = AndroidPaint.Style.STROKE; strokeWidth = 14f }
        eggData.forEach { (ex, ey, ci) ->
            p.color = android.graphics.Color.parseColor(eggColors[ci])
            canvas.drawOval(ex - 55f, ey - 75f, ex + 55f, ey + 75f, p)
            stripePaint.color = android.graphics.Color.parseColor("#FFFFFF")
            stripePaint.alpha = 160
            canvas.drawLine(ex - 55f, ey, ex + 55f, ey, stripePaint)
            stripePaint.alpha = 80
            canvas.drawLine(ex - 45f, ey - 35f, ex + 45f, ey - 35f, stripePaint)
        }

        // Headline
        val titlePaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = AndroidTypeface.create(AndroidTypeface.DEFAULT, AndroidTypeface.BOLD)
            textSize = 105f
            color = android.graphics.Color.parseColor("#AD1457")
            textAlign = AndroidPaint.Align.CENTER
            setShadowLayer(5f, 3f, 3f, android.graphics.Color.argb(60, 0, 0, 0))
        }
        canvas.drawText("Frohe Ostern!", w / 2f, 720f, titlePaint)

        // Wunschtext
        val bodyPaint = AndroidPaint().apply {
            isAntiAlias = true
            textSize = 58f
            color = android.graphics.Color.parseColor("#880E4F")
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText("Ein frohes Osterfest voller", w / 2f, 800f, bodyPaint)
        canvas.drawText("Freude und bunter Überraschungen!", w / 2f, 870f, bodyPaint)

        // Trennlinie
        val linePaint = AndroidPaint().apply {
            color = android.graphics.Color.parseColor("#F48FB1"); strokeWidth = 3f; isAntiAlias = true
        }
        canvas.drawLine(80f, 940f, (w - 80).toFloat(), 940f, linePaint)

        // Von-Zeile
        val vonPaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = AndroidTypeface.create(AndroidTypeface.DEFAULT, AndroidTypeface.ITALIC)
            textSize = 62f
            color = android.graphics.Color.parseColor("#AD1457")
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText("Von: $username", w / 2f, 1040f, vonPaint)

        // Kleine Eier-Deko unten
        listOf(130f to 1130f, 250f to 1150f, 650f to 1150f, 770f to 1130f).forEachIndexed { i, (ex, ey) ->
            p.color = android.graphics.Color.parseColor(eggColors[i])
            canvas.drawOval(ex - 30f, ey - 40f, ex + 30f, ey + 40f, p)
        }

        // Footer
        val footerPaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = AndroidTypeface.create(AndroidTypeface.DEFAULT, AndroidTypeface.ITALIC)
            textSize = 28f
            color = android.graphics.Color.parseColor("#AD1457")
            alpha = 160
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText("versendet aus dem Lethe Messenger \u2022 https://letheapp.de", w / 2f, 1242f, footerPaint)

        val dir = java.io.File(context.cacheDir, "greeting_cards").also { it.mkdirs() }
        val file = java.io.File(dir, "easter_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) { null }
}

private fun generateAndSaveMayCard(context: android.content.Context, username: String): Uri? {
    return try {
        val w = 900; val h = 1260
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val p = AndroidPaint().apply { isAntiAlias = true }

        // Hintergrund: Festlich grün-gelb
        p.color = android.graphics.Color.parseColor("#F1F8E9")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // Untere Wiese
        p.color = android.graphics.Color.parseColor("#DCEDC8")
        canvas.drawRect(0f, h * 0.72f, w.toFloat(), h.toFloat(), p)

        // Konfetti-Punkte
        val confettiColors = listOf("#FF8A65", "#FFB300", "#E91E63", "#4CAF50", "#7E57C2", "#29B6F6", "#FF5252")
        val rand = java.util.Random(2025L)
        repeat(30) {
            p.color = android.graphics.Color.parseColor(confettiColors[it % confettiColors.size])
            p.alpha = 180
            val cx = rand.nextFloat() * w
            val cy = rand.nextFloat() * (h * 0.7f)
            val r = 12f + rand.nextFloat() * 22f
            canvas.drawCircle(cx, cy, r, p)
        }
        p.alpha = 255

        // Tanzende Figur (Mitte)
        val fx = 450f; val fy = 480f
        // Körper
        p.color = android.graphics.Color.parseColor("#FF8A65")
        canvas.drawOval(fx - 60f, fy - 90f, fx + 60f, fy + 130f, p)
        // Kopf
        p.color = android.graphics.Color.parseColor("#FFCC80")
        canvas.drawCircle(fx, fy - 160f, 85f, p)
        // Blumenhut
        p.color = android.graphics.Color.parseColor("#4CAF50")
        canvas.drawOval(fx - 105f, fy - 270f, fx + 105f, fy - 210f, p)
        // Blüten am Hut
        val hatFlowerColors = listOf("#E91E63", "#FF8A65", "#FFEB3B", "#9C27B0")
        hatFlowerColors.forEachIndexed { i, c ->
            p.color = android.graphics.Color.parseColor(c)
            val angle = (i * 90.0) * Math.PI / 180.0
            val hfx = fx + (35f * Math.cos(angle)).toFloat()
            val hfy = (fy - 275f) + (18f * Math.sin(angle)).toFloat()
            canvas.drawCircle(hfx, hfy, 22f, p)
        }
        p.color = android.graphics.Color.parseColor("#FFB300")
        canvas.drawCircle(fx, fy - 275f, 18f, p)
        // Arme (tanzend – nach oben)
        val armPaint = AndroidPaint().apply {
            isAntiAlias = true; color = android.graphics.Color.parseColor("#FFCC80")
            strokeWidth = 26f; style = AndroidPaint.Style.STROKE; strokeCap = AndroidPaint.Cap.ROUND
        }
        canvas.drawLine(fx - 60f, fy - 30f, fx - 150f, fy - 150f, armPaint)
        canvas.drawLine(fx + 60f, fy - 30f, fx + 150f, fy - 150f, armPaint)
        // Hände
        p.color = android.graphics.Color.parseColor("#FFCC80")
        canvas.drawCircle(fx - 155f, fy - 158f, 22f, p)
        canvas.drawCircle(fx + 155f, fy - 158f, 22f, p)
        // Beine (Tanzpose)
        val legPaint = AndroidPaint().apply {
            isAntiAlias = true; color = android.graphics.Color.parseColor("#FF8A65")
            strokeWidth = 24f; style = AndroidPaint.Style.STROKE; strokeCap = AndroidPaint.Cap.ROUND
        }
        canvas.drawLine(fx - 25f, fy + 130f, fx - 75f, fy + 270f, legPaint)
        canvas.drawLine(fx + 25f, fy + 130f, fx + 90f, fy + 240f, legPaint)
        // Augen
        p.color = android.graphics.Color.parseColor("#4E342E"); p.style = AndroidPaint.Style.FILL
        canvas.drawCircle(fx - 26f, fy - 170f, 11f, p)
        canvas.drawCircle(fx + 26f, fy - 170f, 11f, p)
        // Lächeln
        val smilePaint = AndroidPaint().apply {
            isAntiAlias = true; color = android.graphics.Color.parseColor("#E64A19")
            strokeWidth = 7f; style = AndroidPaint.Style.STROKE; strokeCap = AndroidPaint.Cap.ROUND
        }
        val smilePath = AndroidPath().apply {
            moveTo(fx - 30f, fy - 132f)
            quadTo(fx, fy - 108f, fx + 30f, fy - 132f)
        }
        canvas.drawPath(smilePath, smilePaint)

        // Musiknoten
        val notePaint = AndroidPaint().apply {
            isAntiAlias = true; typeface = AndroidTypeface.DEFAULT_BOLD
            textSize = 110f; textAlign = AndroidPaint.Align.CENTER
            color = android.graphics.Color.parseColor("#2E7D32")
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.argb(60, 0, 0, 0))
        }
        canvas.drawText("♪", 140f, 320f, notePaint)
        canvas.drawText("♫", 760f, 280f, notePaint)
        canvas.drawText("♩", 115f, 530f, notePaint)
        canvas.drawText("♬", 785f, 500f, notePaint)

        // Headline
        val titlePaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = AndroidTypeface.create(AndroidTypeface.DEFAULT, AndroidTypeface.BOLD)
            textSize = 100f
            color = android.graphics.Color.parseColor("#1B5E20")
            textAlign = AndroidPaint.Align.CENTER
            setShadowLayer(5f, 3f, 3f, android.graphics.Color.argb(60, 0, 0, 0))
        }
        canvas.drawText("Tanz in den Mai!", w / 2f, 820f, titlePaint)

        // Wunschtext
        val bodyPaint = AndroidPaint().apply {
            isAntiAlias = true; textSize = 56f
            color = android.graphics.Color.parseColor("#2E7D32")
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText("Einen wunderschönen Start", w / 2f, 900f, bodyPaint)
        canvas.drawText("in den Wonnemonat!", w / 2f, 968f, bodyPaint)

        // Trennlinie
        val linePaint = AndroidPaint().apply {
            color = android.graphics.Color.parseColor("#81C784"); strokeWidth = 3f; isAntiAlias = true
        }
        canvas.drawLine(80f, 1010f, (w - 80).toFloat(), 1010f, linePaint)

        // Von-Zeile
        val vonPaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = AndroidTypeface.create(AndroidTypeface.DEFAULT, AndroidTypeface.ITALIC)
            textSize = 60f; color = android.graphics.Color.parseColor("#1B5E20")
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText("Von: $username", w / 2f, 1100f, vonPaint)

        // Blumen-Deko unten
        val flowerColors = listOf("#FF8A65", "#FFB300", "#E91E63", "#4CAF50", "#7E57C2")
        listOf(100f, 220f, 340f, 560f, 680f, 800f).forEachIndexed { i, fx2 ->
            p.color = android.graphics.Color.parseColor(flowerColors[i % flowerColors.size])
            canvas.drawCircle(fx2, 1160f, 22f, p)
            p.color = android.graphics.Color.parseColor("#4CAF50")
            canvas.drawLine(fx2, 1182f, fx2, 1200f, AndroidPaint().apply { strokeWidth = 5f; style = AndroidPaint.Style.STROKE; color = android.graphics.Color.parseColor("#4CAF50") })
        }

        // Footer
        val footerPaint = AndroidPaint().apply {
            isAntiAlias = true
            typeface = AndroidTypeface.create(AndroidTypeface.DEFAULT, AndroidTypeface.ITALIC)
            textSize = 28f
            color = android.graphics.Color.parseColor("#1B5E20")
            alpha = 160
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText("versendet aus dem Lethe Messenger \u2022 https://letheapp.de", w / 2f, 1242f, footerPaint)

        val dir = java.io.File(context.cacheDir, "greeting_cards").also { it.mkdirs() }
        val file = java.io.File(dir, "may_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) { null }
}

// ─── Saisonale Grußkarten ─────────────────────────────────────────────────────

@Composable
private fun EasterGreetingDialog(
    onDismiss: () -> Unit,
    username: String = "",
    onShareCard: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharingCard by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ostereier-Deko
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    listOf(
                        Color(0xFFF48FB1), Color(0xFFCE93D8), Color(0xFFA5D6A7),
                        Color(0xFFFFF176), Color(0xFFFFCCBC)
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(18.dp, 24.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Frohe Ostern!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFAD1457)
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ich wünsche dir ein frohes Osterfest –\nvoller Freude, Frieden und\nbunter Überraschungen!",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF880E4F)),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                // Untere Ei-Deko
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color(0xFFA5D6A7), Color(0xFFF48FB1), Color(0xFFCE93D8)).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(18.dp, 24.dp)
                                .clip(RoundedCornerShape(50))
                                .background(color)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAD1457))
                ) {
                    Text(stringResource(com.securechat.app.R.string.contacts_greeting_easter_btn), color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                // Glückwunschkarte teilen
                OutlinedButton(
                    onClick = {
                        if (isSharingCard) return@OutlinedButton
                        isSharingCard = true
                        scope.launch(Dispatchers.IO) {
                            val uri = generateAndSaveEasterCard(context, username)
                            withContext(Dispatchers.Main) {
                                isSharingCard = false
                                if (uri != null) onShareCard(uri)
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAD1457)),
                    enabled = !isSharingCard
                ) {
                    if (isSharingCard) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFAD1457))
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(com.securechat.app.R.string.contacts_share_greeting_card))
                }
            }
        }
    }
}

@Composable
private fun MayGreetingDialog(
    onDismiss: () -> Unit,
    username: String = "",
    onShareCard: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharingCard by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Blüten-Deko (farbige Kreise als Blüten)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    listOf(
                        Color(0xFFFF8A65), Color(0xFFFFB300), Color(0xFFE91E63),
                        Color(0xFF4CAF50), Color(0xFF7E57C2)
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tanz in den Mai!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Die Mainacht ruft! Ich wünsche dir\neine wundervolle Feier und einen\nfröhlichen Start in den Mai!",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF1B5E20)),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(stringResource(com.securechat.app.R.string.contacts_greeting_may_btn), color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                // Glückwunschkarte teilen
                OutlinedButton(
                    onClick = {
                        if (isSharingCard) return@OutlinedButton
                        isSharingCard = true
                        scope.launch(Dispatchers.IO) {
                            val uri = generateAndSaveMayCard(context, username)
                            withContext(Dispatchers.Main) {
                                isSharingCard = false
                                if (uri != null) onShareCard(uri)
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                    enabled = !isSharingCard
                ) {
                    if (isSharingCard) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF2E7D32))
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(com.securechat.app.R.string.contacts_share_greeting_card))
                }
            }
        }
    }
}

@Composable
private fun XmasGreetingDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Weihnachtliche Deko (rote + grüne Kreise)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    listOf(
                        Color(0xFFD32F2F), Color(0xFF2E7D32), Color(0xFFD32F2F),
                        Color(0xFF2E7D32), Color(0xFFFFD600)
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Besinnliche Weihnachten!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB71C1C)
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ich wünsche dir und deinen Liebsten\neine ruhige, friedvolle und\nbesinnliche Weihnachtszeit!",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF7F0000)),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                // Untere Tannenzweig-Deko
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(14.dp, 20.dp)
                                .clip(RoundedCornerShape(topStart = 50f, topEnd = 50f))
                                .background(Color(0xFF2E7D32))
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) {
                    Text(stringResource(com.securechat.app.R.string.contacts_greeting_christmas_btn), color = Color.White)
                }
            }
        }
    }
}
