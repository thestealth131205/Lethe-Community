package com.securechat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.data.network.ChildPermissionsResponse
import com.securechat.app.data.network.ChildPermissionsUpdateRequest
import com.securechat.app.data.network.FamilyChildEntry
import com.securechat.app.data.network.FamilyStatusResponse
import com.securechat.app.ui.MainViewModel
import androidx.compose.ui.graphics.Color
import com.securechat.app.ui.theme.topBarTitleColor

// ─────────────────────────────────────────────────────────────────────────────
// Lethe Family – Phase 4: Android-UI
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val familyStatus by viewModel.familyStatus.collectAsState()
    val familyLoading by viewModel.familyLoading.collectAsState()
    val familyError by viewModel.familyError.collectAsState()
    val familyMessage by viewModel.familyMessage.collectAsState()
    val childPermissionsMap by viewModel.childPermissionsMap.collectAsState()
    val childContactsMap by viewModel.childContactsMap.collectAsState()
    val myChildPermissions by viewModel.myChildPermissions.collectAsState()

    // Ausstehenden Kind-Einladungstoken aus FCM-Notification prüfen
    var pendingChildToken by remember { mutableStateOf<String?>(null) }

    // Beim Öffnen des Screens immer aktuellen Status laden
    LaunchedEffect(Unit) {
        viewModel.loadFamilyStatus()
        // Ausstehenden Token aus SharedPreferences holen (gesetzt durch FCM-Notification)
        val token = viewModel.consumePendingChildInviteToken()
        if (token != null) pendingChildToken = token
    }

    // Dialog zum Annehmen einer Kind-Einladung via FCM-Push
    if (pendingChildToken != null) {
        val token = pendingChildToken!!
        var accepted by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { pendingChildToken = null },
            icon = { Icon(Icons.Default.FamilyRestroom, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.family_invite_accept_title), fontWeight = FontWeight.Bold) },
            text = {
                if (accepted) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.family_invite_connected), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.family_invite_question))
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = {
                if (!accepted) {
                    Button(onClick = {
                        viewModel.acceptFamilyInvite(token, { accepted = true }, { err -> error = err })
                    }) { Text(stringResource(R.string.family_invite_accept_button)) }
                } else {
                    Button(onClick = { pendingChildToken = null }) { Text(stringResource(R.string.family_invite_close_button)) }
                }
            },
            dismissButton = {
                if (!accepted) TextButton(onClick = { pendingChildToken = null }) { Text(stringResource(R.string.family_invite_decline_button)) }
            }
        )
    }

    // Snackbar-Host
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(familyError) {
        familyError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFamilyError()
        }
    }
    LaunchedEffect(familyMessage) {
        familyMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFamilyMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.family_title),
                        color = topBarTitleColor()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.general_back)
                        )
                    }
                },
                actions = {
                    if (familyLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.loadFamilyStatus() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.family_refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val status = familyStatus

        if (status == null) {
            // Noch kein Status geladen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (familyLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        stringResource(R.string.family_loading_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        when {
            // ── Kind-Account-Ansicht ───────────────────────────────────────────
            status.isChildAccount -> ChildView(
                status = status,
                myPermissions = myChildPermissions,
                modifier = Modifier.padding(padding),
                onLeave = { relationId ->
                    viewModel.deleteFamilyRelation(relationId)
                }
            )

            // ── Eltern-Dashboard ──────────────────────────────────────────────
            status.isParent || status.children.isNotEmpty() || status.pendingInvites.isNotEmpty() -> ParentDashboard(
                status = status,
                childPermissionsMap = childPermissionsMap,
                childContactsMap = childContactsMap,
                modifier = Modifier.padding(padding),
                onLoadPermissions = { childId -> viewModel.loadChildPermissions(childId) },
                onLoadContacts = { childId -> viewModel.loadChildContacts(childId) },
                onToggleContactBlock = { childId, contactId, block -> viewModel.toggleChildContactBlock(childId, contactId, block) },
                onUpdatePermissions = { childId, update -> viewModel.updateChildPermissions(childId, update) },
                onSetPin = { childId, pin -> viewModel.setFamilyPin(childId, pin) },
                onCreateInvite = { onSuccess -> viewModel.createFamilyInvite(onSuccess, {}) },
                onInviteExisting = { phone, username, onSuccess, onError ->
                    viewModel.inviteExistingChild(phone, username, onSuccess, onError)
                },
                onRemoveChild = { relationId -> viewModel.deleteFamilyRelation(relationId) },
                onCancelInvite = { relationId -> viewModel.deleteFamilyRelation(relationId) }
            )

            // ── Noch nicht verbunden ───────────────────────────────────────────
            else -> NotConnectedView(
                modifier = Modifier.padding(padding),
                onCreateInvite = { onSuccess -> viewModel.createFamilyInvite(onSuccess, {}) },
                onAcceptInvite = { token -> viewModel.acceptFamilyInvite(token, {}, {}) },
                onInviteExisting = { phone, username, onSuccess, onError ->
                    viewModel.inviteExistingChild(phone, username, onSuccess, onError)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nicht verbunden: Auswahl Parent / Kind
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotConnectedView(
    modifier: Modifier = Modifier,
    onCreateInvite: (onSuccess: (String) -> Unit) -> Unit,
    onAcceptInvite: (token: String) -> Unit,
    onInviteExisting: (phone: String, username: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var mode by remember { mutableStateOf<String?>(null) }   // "parent" | "child"
    var inviteToken by remember { mutableStateOf("") }
    var generatedToken by remember { mutableStateOf<String?>(null) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var showInviteExistingDialog by remember { mutableStateOf(false) }
    var inviteExistingPhone by remember { mutableStateOf("") }
    var inviteExistingUsername by remember { mutableStateOf("") }
    var inviteExistingError by remember { mutableStateOf<String?>(null) }
    var inviteExistingSent by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Icon(
            Icons.Default.FamilyRestroom,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            stringResource(R.string.family_not_connected_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.family_not_connected_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // ── Option: Als Elternteil einrichten ─────────────────────────────
        Card(
            onClick = { mode = if (mode == "parent") null else "parent" },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (mode == "parent")
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.SupervisorAccount, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.family_setup_as_parent), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.family_setup_as_parent_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (mode == "parent") Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }

        // ── Dialog: Bestehendes Konto einladen ──────────────────────────────
        if (showInviteExistingDialog) {
            AlertDialog(
                onDismissRequest = {
                    showInviteExistingDialog = false
                    inviteExistingError = null
                    inviteExistingSent = false
                },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.family_invite_existing_title), fontWeight = FontWeight.Bold) },
                text = {
                    if (inviteExistingSent) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                            Text(stringResource(R.string.family_invite_sent), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.family_invite_sent_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.family_invite_enter_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = inviteExistingPhone,
                                onValueChange = { inviteExistingPhone = it.trim() },
                                label = { Text(stringResource(R.string.family_invite_phone_label)) },
                                placeholder = { Text(stringResource(R.string.family_invite_phone_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            OutlinedTextField(
                                value = inviteExistingUsername,
                                onValueChange = { inviteExistingUsername = it },
                                label = { Text(stringResource(R.string.family_invite_username_label)) },
                                placeholder = { Text(stringResource(R.string.family_invite_username_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            inviteExistingError?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!inviteExistingSent) {
                        Button(
                            onClick = {
                                inviteExistingError = null
                                onInviteExisting(
                                    inviteExistingPhone,
                                    inviteExistingUsername,
                                    { inviteExistingSent = true },
                                    { err -> inviteExistingError = err }
                                )
                            },
                            enabled = inviteExistingPhone.isNotBlank() && inviteExistingUsername.isNotBlank()
                        ) { Text(stringResource(R.string.family_invite_send_button)) }
                    } else {
                        Button(onClick = {
                            showInviteExistingDialog = false
                            inviteExistingSent = false
                        }) { Text(stringResource(R.string.family_invite_close_button)) }
                    }
                },
                dismissButton = {
                    if (!inviteExistingSent) {
                        TextButton(onClick = { showInviteExistingDialog = false; inviteExistingError = null }) {
                            Text(stringResource(R.string.family_close))
                        }
                    }
                }
            )
        }

        AnimatedVisibility(visible = mode == "parent", enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (generatedToken != null) {
                    // Token anzeigen + Kopieren
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.family_invite_token_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                generatedToken!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(stringResource(R.string.family_invite_token_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(generatedToken!!))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.family_copy_token))
                                }
                                OutlinedButton(
                                    onClick = { generatedToken = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.family_new_invite))
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { onCreateInvite { token -> generatedToken = token } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.family_create_invite))
                    }
                    OutlinedButton(
                        onClick = {
                            inviteExistingPhone = ""
                            inviteExistingUsername = ""
                            inviteExistingError = null
                            inviteExistingSent = false
                            showInviteExistingDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.family_invite_existing_button))
                    }
                }
            }
        }

        // ── Option: Mit Familie verbinden (Kind) ──────────────────────────
        Card(
            onClick = { mode = if (mode == "child") null else "child" },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (mode == "child")
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.ChildCare, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.family_join_as_child), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.family_join_as_child_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (mode == "child") Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }

        AnimatedVisibility(visible = mode == "child", enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = inviteToken,
                    onValueChange = { inviteToken = it.trim() },
                    label = { Text(stringResource(R.string.family_enter_token_label)) },
                    placeholder = { Text(stringResource(R.string.family_enter_token_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { if (inviteToken.isNotBlank()) onAcceptInvite(inviteToken) },
                    enabled = inviteToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.family_join_button))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kind-Ansicht: Zeigt den Parent + eigene Berechtigungen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChildView(
    status: FamilyStatusResponse,
    myPermissions: ChildPermissionsResponse?,
    modifier: Modifier = Modifier,
    onLeave: (relationId: String) -> Unit
) {
    var showLeaveConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Parent-Info ────────────────────────────────────────────────────
        val parent = status.parent
        if (parent != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!parent.parentImage.isNullOrBlank()) {
                        AsyncImage(
                            model = parent.parentImage,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.SupervisorAccount,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.family_your_parent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            parent.parentName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // ── Eigene Berechtigungen (read-only) ─────────────────────────────
        if (myPermissions != null) {
            Text(
                stringResource(R.string.family_my_permissions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            PermissionsReadOnly(perms = myPermissions)
        } else {
            Card(shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.family_permissions_synced_on_login),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Familie verlassen ──────────────────────────────────────────────
        OutlinedButton(
            onClick = { showLeaveConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.family_leave_family))
        }
    }

    if (showLeaveConfirm) {
        val relationId = status.parent?.relationId ?: ""
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            icon = { Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.family_leave_confirm_title)) },
            text = { Text(stringResource(R.string.family_leave_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = { showLeaveConfirm = false; onLeave(relationId) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.family_leave_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Eltern-Dashboard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParentDashboard(
    status: FamilyStatusResponse,
    childPermissionsMap: Map<String, ChildPermissionsResponse>,
    childContactsMap: Map<String, List<com.securechat.app.data.network.ChildContactEntry>>,
    modifier: Modifier = Modifier,
    onLoadPermissions: (childId: String) -> Unit,
    onLoadContacts: (childId: String) -> Unit,
    onToggleContactBlock: (childId: String, contactId: String, block: Boolean) -> Unit,
    onUpdatePermissions: (childId: String, update: ChildPermissionsUpdateRequest) -> Unit,
    onSetPin: (childId: String, pin: String) -> Unit,
    onCreateInvite: (onSuccess: (String) -> Unit) -> Unit,
    onInviteExisting: (phone: String, username: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onRemoveChild: (relationId: String) -> Unit,
    onCancelInvite: (relationId: String) -> Unit
) {
    var showInviteSheet by remember { mutableStateOf(false) }
    var generatedToken by remember { mutableStateOf<String?>(null) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var showInviteExistingDialog by remember { mutableStateOf(false) }
    var inviteExistingPhone by remember { mutableStateOf("") }
    var inviteExistingUsername by remember { mutableStateOf("") }
    var inviteExistingError by remember { mutableStateOf<String?>(null) }
    var inviteExistingSent by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.SupervisorAccount,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Column {
                        Text(
                            stringResource(R.string.family_parent_role),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            stringResource(R.string.family_children_count, status.children.size),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // ── Einladung erstellen ──────────────────────────────────────────────
        if (status.children.size < 5) {
            item {
                if (generatedToken != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.family_invite_token_label), style = MaterialTheme.typography.labelMedium)
                            Text(generatedToken!!, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.family_invite_token_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(generatedToken!!)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.family_copy_token))
                                }
                                OutlinedButton(
                                    onClick = { generatedToken = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.family_close))
                                }
                            }
                        }
                    }
                } else {
                    // Dialog für "Bestehendes Konto einladen"
                    if (showInviteExistingDialog) {
                        AlertDialog(
                            onDismissRequest = { showInviteExistingDialog = false; inviteExistingError = null; inviteExistingSent = false },
                            icon = { Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
                            title = { Text(stringResource(R.string.family_invite_existing_title), fontWeight = FontWeight.Bold) },
                            text = {
                                if (inviteExistingSent) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                                        Text(stringResource(R.string.family_invite_sent), fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.family_invite_sent_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(stringResource(R.string.family_invite_enter_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedTextField(
                                            value = inviteExistingPhone,
                                            onValueChange = { inviteExistingPhone = it.trim() },
                                            label = { Text(stringResource(R.string.family_invite_phone_label)) },
                                            placeholder = { Text(stringResource(R.string.family_invite_phone_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                                        )
                                        OutlinedTextField(
                                            value = inviteExistingUsername,
                                            onValueChange = { inviteExistingUsername = it },
                                            label = { Text(stringResource(R.string.family_invite_username_label)) },
                                            placeholder = { Text(stringResource(R.string.family_invite_username_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            leadingIcon = { Icon(Icons.Default.Person, null) },
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        inviteExistingError?.let { err ->
                                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                if (!inviteExistingSent) {
                                    Button(
                                        onClick = {
                                            inviteExistingError = null
                                            onInviteExisting(inviteExistingPhone, inviteExistingUsername, { inviteExistingSent = true }, { err -> inviteExistingError = err })
                                        },
                                        enabled = inviteExistingPhone.isNotBlank() && inviteExistingUsername.isNotBlank()
                                    ) { Text(stringResource(R.string.family_invite_send_button)) }
                                } else {
                                    Button(onClick = { showInviteExistingDialog = false; inviteExistingSent = false }) { Text(stringResource(R.string.family_invite_close_button)) }
                                }
                            },
                            dismissButton = {
                                if (!inviteExistingSent) TextButton(onClick = { showInviteExistingDialog = false; inviteExistingError = null }) { Text(stringResource(R.string.family_close)) }
                            }
                        )
                    }
                    Button(
                        onClick = { onCreateInvite { token -> generatedToken = token } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.family_invite_child))
                    }
                    OutlinedButton(
                        onClick = {
                            inviteExistingPhone = ""
                            inviteExistingUsername = ""
                            inviteExistingError = null
                            inviteExistingSent = false
                            showInviteExistingDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.family_invite_existing_button))
                    }
                }
            }
        }

        // ── Ausstehende Einladungen ─────────────────────────────────────────
        if (status.pendingInvites.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.family_pending_invites),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(status.pendingInvites) { invite ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.HourglassEmpty, null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.family_pending_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            Text(invite.inviteToken, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(invite.inviteToken)) }) {
                            Icon(Icons.Default.ContentCopy, null)
                        }
                        IconButton(onClick = { onCancelInvite(invite.relationId) }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // ── Kinder-Liste ────────────────────────────────────────────────────
        if (status.children.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.family_children_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(status.children, key = { it.childId }) { child ->
                ChildCard(
                    child = child,
                    permissions = childPermissionsMap[child.childId],
                    contacts = childContactsMap[child.childId],
                    onExpand = {
                        onLoadPermissions(child.childId)
                        onLoadContacts(child.childId)
                    },
                    onUpdatePermissions = { update -> onUpdatePermissions(child.childId, update) },
                    onSetPin = { pin -> onSetPin(child.childId, pin) },
                    onRemove = { onRemoveChild(child.relationId) },
                    onToggleContactBlock = { contactId, block -> onToggleContactBlock(child.childId, contactId, block) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Kind-Karte mit expandierbarem Berechtigungs-Editor
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChildCard(
    child: FamilyChildEntry,
    permissions: ChildPermissionsResponse?,
    contacts: List<com.securechat.app.data.network.ChildContactEntry>?,
    onExpand: () -> Unit,
    onUpdatePermissions: (ChildPermissionsUpdateRequest) -> Unit,
    onSetPin: (pin: String) -> Unit,
    onRemove: () -> Unit,
    onToggleContactBlock: (contactId: String, block: Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showContacts by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            // ── Kopfzeile der Karte ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!child.childImage.isNullOrBlank()) {
                    AsyncImage(
                        model = child.childImage,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.ChildCare,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(child.childName, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.family_child_id_label, child.childFakeNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expand / Collapse
                IconButton(onClick = {
                    expanded = !expanded
                    if (expanded && permissions == null) onExpand()
                }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }

            // ── Expandierter Bereich: Berechtigungs-Editor ───────────────
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                    if (permissions == null) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        PermissionsEditor(
                            permissions = permissions,
                            onSave = onUpdatePermissions
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // ── Kontakte des Kindes ──────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showContacts = !showContacts }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Contacts, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.family_child_contacts_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(
                            if (showContacts) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showContacts, enter = expandVertically(), exit = shrinkVertically()) {
                        val blockedIds = remember(permissions?.contactBlockedIds) {
                            try {
                                if (permissions?.contactBlockedIds != null)
                                    com.google.gson.Gson().fromJson(permissions.contactBlockedIds, Array<String>::class.java).toList()
                                else emptyList()
                            } catch (e: Exception) { emptyList() }
                        }
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (contacts == null) {
                                Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                            } else if (contacts.isEmpty()) {
                                Text(
                                    "Keine Kontakte vorhanden",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                contacts.forEach { contact ->
                                    val isBlocked = blockedIds.contains(contact.partnerId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!contact.partnerImage.isNullOrBlank()) {
                                            AsyncImage(
                                                model = contact.partnerImage,
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(contact.partnerName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text(contact.partnerFakeNumber, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(
                                            onClick = { onToggleContactBlock(contact.partnerId, !isBlocked) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                if (isBlocked) Icons.Default.Block else Icons.Default.LockOpen,
                                                contentDescription = if (isBlocked) "Entsperren" else "Sperren",
                                                tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    // Aktionen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPinDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.family_set_pin), fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { showRemoveConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.PersonRemove, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.family_remove_child), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Bestätigungs-Dialog: Kind entfernen
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            icon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.family_remove_confirm_title)) },
            text = { Text(stringResource(R.string.family_remove_confirm_text, child.childName)) },
            confirmButton = {
                Button(
                    onClick = { showRemoveConfirm = false; onRemove() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.family_remove_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }

    // PIN-Dialog
    if (showPinDialog) {
        PinDialog(
            childName = child.childName,
            onConfirm = { pin -> showPinDialog = false; onSetPin(pin) },
            onDismiss = { showPinDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Berechtigungs-Editor (für Parent)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionsEditor(
    permissions: ChildPermissionsResponse,
    onSave: (ChildPermissionsUpdateRequest) -> Unit
) {
    var canVideoCall by remember(permissions) { mutableStateOf(permissions.canVideoCall) }
    var canAudioCall by remember(permissions) { mutableStateOf(permissions.canAudioCall) }
    var canSendMedia by remember(permissions) { mutableStateOf(permissions.canSendMedia) }
    var canViewSparks by remember(permissions) { mutableStateOf(permissions.canViewSparks) }
    var canUseNearby by remember(permissions) { mutableStateOf(permissions.canUseNearby) }
    var canPlayGames by remember(permissions) { mutableStateOf(permissions.canPlayGames) }
    var isVip by remember(permissions) { mutableStateOf(permissions.isVip) }
    var gameMinutes by remember(permissions) { mutableStateOf(permissions.allowedGameMinutesPerDay.toString()) }
    var gameStart by remember(permissions) { mutableStateOf(permissions.gameTimeStart ?: "") }
    var gameEnd by remember(permissions) { mutableStateOf(permissions.gameTimeEnd ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.family_permissions_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        PermissionToggle(
            label = stringResource(R.string.family_perm_video_call),
            icon = Icons.Default.Videocam,
            checked = canVideoCall,
            onToggle = { canVideoCall = it }
        )
        PermissionToggle(
            label = stringResource(R.string.family_perm_audio_call),
            icon = Icons.Default.Phone,
            checked = canAudioCall,
            onToggle = { canAudioCall = it }
        )
        PermissionToggle(
            label = stringResource(R.string.family_perm_send_media),
            icon = Icons.Default.AttachFile,
            checked = canSendMedia,
            onToggle = { canSendMedia = it }
        )
        PermissionToggle(
            label = stringResource(R.string.family_perm_view_sparks),
            icon = Icons.Default.PlayCircle,
            checked = canViewSparks,
            onToggle = { canViewSparks = it }
        )
        PermissionToggle(
            label = stringResource(R.string.family_perm_nearby),
            icon = Icons.Default.NearMe,
            checked = canUseNearby,
            onToggle = { canUseNearby = it }
        )
        PermissionToggle(
            label = stringResource(R.string.family_perm_games),
            icon = Icons.Default.SportsEsports,
            checked = canPlayGames,
            onToggle = { canPlayGames = it }
        )
        PermissionToggle(
            label = stringResource(R.string.family_perm_vip),
            icon = Icons.Default.Star,
            checked = isVip,
            onToggle = { isVip = it }
        )

        // ── Spielzeit-Einstellungen (nur wenn Spiele erlaubt) ────────────
        AnimatedVisibility(visible = canPlayGames) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.family_game_limits),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = gameMinutes,
                        onValueChange = { gameMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.family_game_minutes_label)) },
                        suffix = { Text(stringResource(R.string.family_game_minutes_suffix)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gameStart,
                            onValueChange = { gameStart = it },
                            label = { Text(stringResource(R.string.family_game_time_start)) },
                            placeholder = { Text("08:00") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = gameEnd,
                            onValueChange = { gameEnd = it },
                            label = { Text(stringResource(R.string.family_game_time_end)) },
                            placeholder = { Text("21:00") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text(
                        stringResource(R.string.family_game_time_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                onSave(
                    ChildPermissionsUpdateRequest(
                        canVideoCall = canVideoCall,
                        canAudioCall = canAudioCall,
                        canSendMedia = canSendMedia,
                        canViewSparks = canViewSparks,
                        canUseNearby = canUseNearby,
                        canPlayGames = canPlayGames,
                        isVip = isVip,
                        allowedGameMinutesPerDay = gameMinutes.toIntOrNull() ?: 0,
                        gameTimeStart = gameStart.ifBlank { null },
                        gameTimeEnd = gameEnd.ifBlank { null }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.family_save_permissions))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Berechtigungen (read-only für Kind-Account)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionsReadOnly(perms: ChildPermissionsResponse) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermStatusRow(stringResource(R.string.family_perm_video_call), Icons.Default.Videocam, perms.canVideoCall)
            PermStatusRow(stringResource(R.string.family_perm_audio_call), Icons.Default.Phone, perms.canAudioCall)
            PermStatusRow(stringResource(R.string.family_perm_send_media), Icons.Default.AttachFile, perms.canSendMedia)
            PermStatusRow(stringResource(R.string.family_perm_view_sparks), Icons.Default.PlayCircle, perms.canViewSparks)
            PermStatusRow(stringResource(R.string.family_perm_nearby), Icons.Default.NearMe, perms.canUseNearby)
            PermStatusRow(stringResource(R.string.family_perm_games), Icons.Default.SportsEsports, perms.canPlayGames)
            PermStatusRow(stringResource(R.string.family_perm_vip), Icons.Default.Star, perms.isVip)

            if (perms.canPlayGames && perms.allowedGameMinutesPerDay > 0) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.family_game_limit_info, perms.allowedGameMinutesPerDay),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hilfs-Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionToggle(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun PermStatusRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    allowed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(
            if (allowed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PinDialog(
    childName: String,
    onConfirm: (pin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(stringResource(R.string.family_pin_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.family_pin_dialog_desc, childName))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) { pin = it.filter { c -> c.isDigit() }; pinError = false } },
                    label = { Text(stringResource(R.string.family_pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = pinError,
                    supportingText = if (pinError) ({ Text(stringResource(R.string.family_pin_error)) }) else null,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pin.length == 4) onConfirm(pin) else pinError = true
            }) {
                Text(stringResource(R.string.family_pin_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel))
            }
        }
    )
}
