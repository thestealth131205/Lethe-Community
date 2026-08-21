package com.securechat.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.securechat.app.AppChangelog
import com.securechat.app.data.network.InstanceInfo
import com.securechat.app.data.network.ServiceStatus
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendScreen(
    onNavigateBack: () -> Unit,
    adminLogs: List<String> = emptyList(),
    onLoadAdminLogs: () -> Unit = {},
    onRestartServer: () -> Unit = {},
    onRestartTurnServer: () -> Unit = {},
    onHealDatabase: () -> Unit = {},
    serverStatus: ServiceStatus = ServiceStatus.UNKNOWN,
    turnStatus: ServiceStatus = ServiceStatus.UNKNOWN,
    onRefreshStatus: () -> Unit = {},
    contactListAnimation: String = "none",
    onSetAnimation: (String) -> Unit = {},
    onSendGlobalLumis: (LumisType) -> Unit = {},
    restartLog: List<String> = emptyList(),
    isRestarting: Boolean = false,
    onClearRestartLog: () -> Unit = {},
    // Admin: Nutzerverwaltung
    adminSearchResults: List<MainViewModel.AdminUserResult> = emptyList(),
    adminActionMessage: String? = null,
    onAdminSearch: (String) -> Unit = {},
    onAdminBlock: (String) -> Unit = {},
    onAdminUnblock: (String) -> Unit = {},
    onAdminDelete: (String) -> Unit = {},
    onAdminVerifyAge: (String) -> Unit = {},
    onAdminMakeCreator: (String) -> Unit = {},
    onAdminMakeModerator: (String) -> Unit = {},
    onAdminRemoveModerator: (String) -> Unit = {},
    onAdminResetUsers: () -> Unit = {},
    onAdminResetAll: () -> Unit = {},
    onClearAdminActionMessage: () -> Unit = {},
    isModerator: Boolean = false,
    // Admin: Instanz-Health
    instancesHealth: List<InstanceInfo> = emptyList(),
    onRefreshInstances: () -> Unit = {},
    onStartInstance: (String) -> Unit = {},
    onStopInstance: (String) -> Unit = {},
    onStartBackupInstance: (String) -> Unit = {},
    onStopBackupInstance: (String) -> Unit = {},
    // Failover
    failoverLog: List<String> = emptyList(),
    failoverRunning: Boolean = false,
    onFailoverStatus: () -> Unit = {},
    onFailoverPromote: () -> Unit = {},
    onFailoverRecover: () -> Unit = {},
    onClearFailoverLog: () -> Unit = {},
    // Admin: Server-Ressourcen
    adminServerInfo: com.securechat.app.data.network.AdminServerInfoResponse? = null,
    onLoadAdminServerInfo: () -> Unit = {},
    // Admin: Statistiken
    adminStats: Pair<Int, Int>? = null,
    onLoadAdminStats: () -> Unit = {},
    // Admin: Support
    adminSupportTickets: List<com.securechat.app.data.network.AdminSupportTicket> = emptyList(),
    adminSupportActionMessage: String? = null,
    adminSupportLoading: Boolean = false,
    adminSupportLoadError: String? = null,
    onLoadAdminSupportTickets: (String) -> Unit = {},
    onAdminReplySupportTicket: (String, String, String) -> Unit = { _, _, _ -> },
    onAdminUpdateSupportTicketStatus: (String, String) -> Unit = { _, _ -> },
    onClearAdminSupportActionMessage: () -> Unit = {},
    // Admin: Creator-Bewerbungen
    adminCreatorApplications: List<com.securechat.app.data.network.CreatorApplicationResponse> = emptyList(),
    adminCreatorApplicationsLoading: Boolean = false,
    adminCreatorApplicationsError: String? = null,
    adminCreatorApplicationActionMessage: String? = null,
    onLoadAdminCreatorApplications: (String) -> Unit = {},
    onAdminApproveCreatorApplication: (String) -> Unit = {},
    onAdminRejectCreatorApplication: (String, String?) -> Unit = { _, _ -> },
    onAdminMessageCreatorApplication: (String, String, () -> Unit, (String) -> Unit) -> Unit = { _, _, _, _ -> },
    onClearAdminCreatorApplicationActionMessage: () -> Unit = {},
    // Admin: Nutzermeldungen
    adminUserReports: List<com.securechat.app.data.network.AdminUserReport> = emptyList(),
    adminUserReportsLoading: Boolean = false,
    adminUserReportsError: String? = null,
    onLoadAdminUserReports: (String) -> Unit = {},
    onAdminUpdateReportStatus: (String, String) -> Unit = { _, _ -> },
    onNavigateToAnnouncement: () -> Unit = {},
    onNavigateToUserManagement: () -> Unit = {},
    initialTab: Int = 0,
    // Diamant→Euro Umrechnungsfaktor
    diamondToEuroRate: Double = 0.01,
    onLoadDiamondRate: () -> Unit = {},
    onSetDiamondRate: (Double) -> Unit = {},
    // Nur-FCM-Modus
    onlyFcmMode: Boolean = false,
    onOnlyFcmModeChange: (Boolean) -> Unit = {},
    onLoadServerSettings: () -> Unit = {},
    // SMS-Gateway
    smsGatewayStatus: com.securechat.app.data.network.SmsGatewayStatusResponse? = null,
    onRefreshSmsGatewayStatus: () -> Unit = {},
    onRestartSmsGateway: () -> Unit = {},
    onSendGroupAnnouncement: (targetType: String, content: String) -> Unit = { _, _ -> },
    onNavigateToMusicLibrary: () -> Unit = {}
) {
    var showLogViewer by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showRestartTurnConfirm by remember { mutableStateOf(false) }
    var showLumisBroadcastDialog by remember { mutableStateOf(false) }
    var showResetUsersConfirm by remember { mutableStateOf(false) }
    var showResetAllConfirm by remember { mutableStateOf(false) }
    var adminSearchQuery by remember { mutableStateOf("") }
    var expandedUserId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmId by remember { mutableStateOf<String?>(null) }
    // Moderator startet auf Tab 1 (Nutzer) wenn kein expliziter initialTab gesetzt
    val effectiveInitialTab = if (isModerator && initialTab == 0) 1 else initialTab
    var selectedTab by remember { mutableStateOf(effectiveInitialTab) }

    val context = LocalContext.current
    val logListState = rememberLazyListState()
    val consoleListState = rememberLazyListState()

    // Moderatoren sehen nur Nutzer / Support / Bewerbungen / Meldungen (kein Server, keine Events)
    val tabs = if (isModerator) listOf(
        Triple("Nutzer", Icons.Default.People, 1),
        Triple("Support", Icons.Default.SupportAgent, 3),
        Triple("Bewerbungen", Icons.AutoMirrored.Filled.Assignment, 4),
        Triple("Meldungen", Icons.Default.Flag, 5)
    ) else listOf(
        Triple("Server", Icons.Default.Dns, 0),
        Triple("Nutzer", Icons.Default.People, 1),
        Triple("Events", Icons.Default.AutoAwesome, 2),
        Triple("Support", Icons.Default.SupportAgent, 3),
        Triple("Bewerbungen", Icons.AutoMirrored.Filled.Assignment, 4),
        Triple("Meldungen", Icons.Default.Flag, 5)
    )

    // Status (Server + TURN) sofort laden und alle 5s aktualisieren solange die Seite offen ist
    LaunchedEffect(Unit) {
        onRefreshStatus()
        while (true) {
            delay(5_000)
            onRefreshStatus()
        }
    }

    // Instanz-Health + Stats: sofort laden und alle 30s aktualisieren
    LaunchedEffect(Unit) {
        onRefreshInstances()
        onLoadAdminStats()
        while (true) {
            delay(30_000)
            onRefreshInstances()
            onLoadAdminStats()
        }
    }

    // Auto-Laden + alle 15s aktualisieren solange der Log-Viewer geöffnet ist
    LaunchedEffect(showLogViewer) {
        if (!showLogViewer) return@LaunchedEffect
        onLoadAdminLogs()
        while (true) {
            delay(15_000)
            onLoadAdminLogs()
        }
    }

    // Nach neuem Laden automatisch ans Ende scrollen (neueste Einträge)
    LaunchedEffect(adminLogs.size) {
        if (showLogViewer && adminLogs.isNotEmpty()) {
            logListState.animateScrollToItem(adminLogs.size - 1)
        }
    }

    // Konsole: automatisch zum letzten Eintrag scrollen
    LaunchedEffect(restartLog.size) {
        if (restartLog.isNotEmpty()) {
            consoleListState.animateScrollToItem(restartLog.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backend") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMusicLibrary) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = "Musik-Bibliothek")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = topBarTitleColor(),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEach { (title, icon, index) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                }
            }
            when (selectedTab) {
                0 -> ServerTab(
                    instancesHealth = instancesHealth,
                    onStartInstance = onStartInstance,
                    onStopInstance = onStopInstance,
                    onStartBackupInstance = onStartBackupInstance,
                    onStopBackupInstance = onStopBackupInstance,
                    failoverLog = failoverLog,
                    failoverRunning = failoverRunning,
                    onFailoverStatus = onFailoverStatus,
                    onFailoverPromote = onFailoverPromote,
                    onFailoverRecover = onFailoverRecover,
                    onClearFailoverLog = onClearFailoverLog,
                    adminLogs = adminLogs,
                    serverStatus = serverStatus,
                    turnStatus = turnStatus,
                    restartLog = restartLog,
                    isRestarting = isRestarting,
                    onClearRestartLog = onClearRestartLog,
                    onHealDatabase = onHealDatabase,
                    consoleListState = consoleListState,
                    onShowLogViewer = { showLogViewer = true },
                    onShowRestartConfirm = { showRestartConfirm = true },
                    onShowRestartTurnConfirm = { showRestartTurnConfirm = true },
                    adminServerInfo = adminServerInfo,
                    onLoadAdminServerInfo = onLoadAdminServerInfo,
                    diamondToEuroRate = diamondToEuroRate,
                    onLoadDiamondRate = onLoadDiamondRate,
                    onSetDiamondRate = onSetDiamondRate,
                    onlyFcmMode = onlyFcmMode,
                    onOnlyFcmModeChange = onOnlyFcmModeChange,
                    onLoadServerSettings = onLoadServerSettings,
                    smsGatewayStatus = smsGatewayStatus,
                    onRefreshSmsGatewayStatus = onRefreshSmsGatewayStatus,
                    onRestartSmsGateway = onRestartSmsGateway,
                    onSendGroupAnnouncement = onSendGroupAnnouncement
                )
                1 -> NutzerTab(
                    adminSearchQuery = adminSearchQuery,
                    onAdminSearchQueryChange = { adminSearchQuery = it },
                    adminSearchResults = adminSearchResults,
                    adminActionMessage = adminActionMessage,
                    onAdminSearch = onAdminSearch,
                    onAdminBlock = onAdminBlock,
                    onAdminUnblock = onAdminUnblock,
                    onAdminVerifyAge = onAdminVerifyAge,
                    onAdminMakeCreator = onAdminMakeCreator,
                    onAdminMakeModerator = onAdminMakeModerator,
                    onAdminRemoveModerator = onAdminRemoveModerator,
                    expandedUserId = expandedUserId,
                    onExpandedUserIdChange = { expandedUserId = it },
                    onShowDeleteConfirm = { showDeleteConfirmId = it },
                    onClearAdminActionMessage = onClearAdminActionMessage,
                    adminStats = adminStats,
                    callerIsModerator = isModerator,
                    onNavigateToAnnouncement = onNavigateToAnnouncement,
                    onNavigateToUserManagement = onNavigateToUserManagement
                )
                2 -> EventsTab(
                    contactListAnimation = contactListAnimation,
                    onSetAnimation = onSetAnimation,
                    onShowResetUsersConfirm = { showResetUsersConfirm = true },
                    onShowResetAllConfirm = { showResetAllConfirm = true },
                    onShowLumisBroadcastDialog = { showLumisBroadcastDialog = true }
                )
                3 -> SupportTab(
                    tickets = adminSupportTickets,
                    actionMessage = adminSupportActionMessage,
                    isLoading = adminSupportLoading,
                    loadError = adminSupportLoadError,
                    onLoad = onLoadAdminSupportTickets,
                    onReply = onAdminReplySupportTicket,
                    onUpdateStatus = onAdminUpdateSupportTicketStatus,
                    onClearActionMessage = onClearAdminSupportActionMessage
                )
                4 -> BewerbungenTab(
                    applications = adminCreatorApplications,
                    isLoading = adminCreatorApplicationsLoading,
                    loadError = adminCreatorApplicationsError,
                    actionMessage = adminCreatorApplicationActionMessage,
                    onLoad = onLoadAdminCreatorApplications,
                    onApprove = onAdminApproveCreatorApplication,
                    onReject = onAdminRejectCreatorApplication,
                    onSendMessage = onAdminMessageCreatorApplication,
                    onClearActionMessage = onClearAdminCreatorApplicationActionMessage
                )
                5 -> MeldungenTab(
                    reports = adminUserReports,
                    isLoading = adminUserReportsLoading,
                    loadError = adminUserReportsError,
                    onLoad = onLoadAdminUserReports,
                    onUpdateStatus = onAdminUpdateReportStatus
                )
            }
        }
    }

    // User-Löschen-Bestätigung
    if (showDeleteConfirmId != null) {
        val targetUser = adminSearchResults.find { it.id == showDeleteConfirmId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmId = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("User löschen?") },
            text = { Text("${targetUser?.name ?: showDeleteConfirmId} wird unwiderruflich gelöscht.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmId?.let { onAdminDelete(it) }
                        expandedUserId = null
                        showDeleteConfirmId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmId = null }) { Text("Abbrechen") }
            }
        )
    }

    // Alle User löschen
    if (showResetUsersConfirm) {
        AlertDialog(
            onDismissRequest = { showResetUsersConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Alle User löschen?") },
            text = { Text("ALLE Nutzeraccounts werden unwiderruflich gelöscht. Nur dein Admin-Account bleibt erhalten.") },
            confirmButton = {
                Button(
                    onClick = { onAdminResetUsers(); showResetUsersConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Alle löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showResetUsersConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    // Vollständiger Reset
    if (showResetAllConfirm) {
        AlertDialog(
            onDismissRequest = { showResetAllConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Vollständiger Reset?") },
            text = { Text("ALLE Daten in allen Tabellen werden gelöscht und der Server wird neu gestartet. Diese Aktion kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                Button(
                    onClick = { onAdminResetAll(); showResetAllConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Reset + Neustart") }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    // Log-Viewer: Vollbild-Dialog
    if (showLogViewer) {
        Dialog(
            onDismissRequest = { showLogViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Server-Log") },
                        navigationIcon = {
                            IconButton(onClick = { showLogViewer = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Schließen")
                            }
                        },
                        actions = {
                            // Manuell aktualisieren
                            IconButton(onClick = { onLoadAdminLogs() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            // Log als Text teilen / herunterladen
                            IconButton(onClick = {
                                val text = adminLogs.joinToString("\n")
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                    putExtra(Intent.EXTRA_SUBJECT, "Lethe Server-Log")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Log teilen"))
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Herunterladen",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = topBarTitleColor(),
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { innerPadding ->
                if (adminLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            "Lade Log-Einträge…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(adminLogs) { log ->
                            val color = when {
                                log.contains("[ERROR]", ignoreCase = true) -> MaterialTheme.colorScheme.error
                                log.contains("[WARNING]", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                log,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = color,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Lumis-Broadcast-Dialog
    if (showLumisBroadcastDialog) {
        AlertDialog(
            onDismissRequest = { showLumisBroadcastDialog = false },
            title = { Text("Lumis-Broadcast senden") },
            text = {
                androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Wähle einen Lumis-Effekt aus. Dieser wird an ALLE verbundenen Nutzer gesendet.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp)
                    )
                    LumisType.values().filter { it != LumisType.NONE }.forEach { type ->
                        val emoji = when (type) {
                            LumisType.LOVE            -> "❤️"
                            LumisType.SNOW            -> "❄️"
                            LumisType.KISS            -> "💋"
                            LumisType.RAIN            -> "🌧️"
                            LumisType.SUMMER          -> "🌴"
                            LumisType.CHRISTMAS       -> "🎅"
                            LumisType.STRANGER_THINGS -> "🔴"
                            LumisType.LAST_OF_US      -> "🍄"
                            LumisType.MARIO           -> "⭐"
                            LumisType.NONE            -> return@forEach
                        }
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.AutoAwesome,
                            title = "$emoji ${type.name.replace('_', ' ')}",
                            subtitle = ""
                        ) {
                            TextButton(onClick = {
                                onSendGlobalLumis(type)
                                showLumisBroadcastDialog = false
                            }) { Text("Senden") }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLumisBroadcastDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // TURN-Neustart-Bestätigung
    if (showRestartTurnConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartTurnConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Relay-Server neu starten?") },
            text = { Text("Der TURN/STUN-Server wird kurz unterbrochen. Laufende Videoanrufe können abbrechen.") },
            confirmButton = {
                Button(
                    onClick = { onRestartTurnServer(); showRestartTurnConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Neu starten") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartTurnConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    // Backend-Neustart-Bestätigung
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Server neu starten?") },
            text = {
                Text("Der Server wird für einige Sekunden nicht erreichbar sein. Alle aktiven Verbindungen werden getrennt.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartConfirm = false
                        onRestartServer()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Neu starten") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

// ─── TAB 1: SERVER ───────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SmsGatewayBroadcastDialog(
    onDismiss: () -> Unit,
    onSend: (targetType: String, content: String) -> Unit
) {
    // targetType-Werte entsprechen dem Backend /admin/announce
    val groups = listOf(
        "admins" to "Admin",
        "moderators" to "Moderatoren",
        "creators" to "Creator",
        "all" to "User"
    )
    var selectedTarget by remember { mutableStateOf("all") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Sms, contentDescription = null) },
        title = { Text("SMS über Gateway senden", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Sendet eine echte SMS über das Gateway an alle Nummern der gewählten Gruppe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Empfängergruppe", fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { (value, label) ->
                        FilterChip(
                            selected = selectedTarget == value,
                            onClick = { selectedTarget = value },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Textnachricht") },
                    placeholder = { Text("Wichtige Nachricht eingeben …") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    minLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(selectedTarget, message.trim()) },
                enabled = message.isNotBlank()
            ) { Text("Senden") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun ServerTab(
    instancesHealth: List<InstanceInfo>,
    onStartInstance: (String) -> Unit,
    onStopInstance: (String) -> Unit,
    onStartBackupInstance: (String) -> Unit,
    onStopBackupInstance: (String) -> Unit,
    failoverLog: List<String>,
    failoverRunning: Boolean,
    onFailoverStatus: () -> Unit,
    onFailoverPromote: () -> Unit,
    onFailoverRecover: () -> Unit,
    onClearFailoverLog: () -> Unit,
    adminLogs: List<String>,
    serverStatus: ServiceStatus,
    turnStatus: ServiceStatus,
    restartLog: List<String>,
    isRestarting: Boolean,
    onClearRestartLog: () -> Unit,
    onHealDatabase: () -> Unit,
    consoleListState: androidx.compose.foundation.lazy.LazyListState,
    onShowLogViewer: () -> Unit,
    onShowRestartConfirm: () -> Unit,
    onShowRestartTurnConfirm: () -> Unit,
    adminServerInfo: com.securechat.app.data.network.AdminServerInfoResponse? = null,
    onLoadAdminServerInfo: () -> Unit = {},
    diamondToEuroRate: Double = 0.01,
    onLoadDiamondRate: () -> Unit = {},
    onSetDiamondRate: (Double) -> Unit = {},
    onlyFcmMode: Boolean = false,
    onOnlyFcmModeChange: (Boolean) -> Unit = {},
    onLoadServerSettings: () -> Unit = {},
    smsGatewayStatus: com.securechat.app.data.network.SmsGatewayStatusResponse? = null,
    onRefreshSmsGatewayStatus: () -> Unit = {},
    onRestartSmsGateway: () -> Unit = {},
    onSendGroupAnnouncement: (targetType: String, content: String) -> Unit = { _, _ -> }
) {
    var showSmsBroadcastDialog by remember { mutableStateOf(false) }

    if (showSmsBroadcastDialog) {
        SmsGatewayBroadcastDialog(
            onDismiss = { showSmsBroadcastDialog = false },
            onSend = { targetType, content ->
                onSendGroupAnnouncement(targetType, content)
                showSmsBroadcastDialog = false
            }
        )
    }

    LaunchedEffect(Unit) {
        onLoadAdminServerInfo()
        onLoadServerSettings()
        onRefreshSmsGatewayStatus()
        while (true) {
            kotlinx.coroutines.delay(30_000)
            onRefreshSmsGatewayStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ─── SERVER-AUSLASTUNG ────────────────────────────────────────────────
        SettingsSection(title = "📊 Server-Auslastung") {
            if (adminServerInfo == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Lade Serverdaten…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // CPU
                    ResourceBar(
                        label = "CPU",
                        value = adminServerInfo.cpu.percent.toFloat(),
                        detail = "${adminServerInfo.cpu.percent.toInt()}%  ·  ${adminServerInfo.cpu.cores} Kerne"
                    )
                    // RAM
                    ResourceBar(
                        label = "RAM",
                        value = adminServerInfo.ram.percent.toFloat(),
                        detail = "${adminServerInfo.ram.usedMb} / ${adminServerInfo.ram.totalMb} MB  (${adminServerInfo.ram.percent.toInt()}%)"
                    )
                    // Disk
                    ResourceBar(
                        label = "Disk",
                        value = adminServerInfo.disk.percent.toFloat(),
                        detail = "${adminServerInfo.disk.usedGb} / ${adminServerInfo.disk.totalGb} GB  (${adminServerInfo.disk.percent.toInt()}%)"
                    )
                    // Uptime
                    val uptimeSec = adminServerInfo.uptimeSeconds
                    val days = uptimeSec / 86400
                    val hours = (uptimeSec % 86400) / 3600
                    val mins = (uptimeSec % 3600) / 60
                    val uptimeStr = when {
                        days > 0 -> "${days}d ${hours}h ${mins}m"
                        hours > 0 -> "${hours}h ${mins}m"
                        else -> "${mins}m"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Uptime", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(uptimeStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onLoadAdminServerInfo) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aktualisieren", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── INSTANZEN ────────────────────────────────────────────────────────
        SettingsSection(title = "🖥 Instanzen") {
            if (instancesHealth.isEmpty()) {
                Text(
                    "Keine Instanzen gefunden",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                val totalConnections = instancesHealth.sumOf { it.activeConnections }.coerceAtLeast(1)
                instancesHealth.forEach { instance ->
                    val loadFraction = instance.activeConnections.toFloat() / totalConnections.toFloat()
                    val loadPercent = (loadFraction * 100).toInt()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ServiceStatusDot(instance.status)
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "Instanz ${instance.id}",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            if (instance.instanceType == "backup") {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(
                                                        "Backup",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "Port ${instance.port}  ·  ${instance.activeConnections} Verbindung${if (instance.activeConnections != 1) "en" else ""}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { loadFraction },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(MaterialTheme.shapes.small),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        "$loadPercent%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(34.dp)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                if (instance.instanceType == "backup") {
                                    if (instance.status == ServiceStatus.ONLINE) onStopBackupInstance(instance.id)
                                    else onStartBackupInstance(instance.id)
                                } else {
                                    if (instance.status == ServiceStatus.ONLINE) onStopInstance(instance.id)
                                    else onStartInstance(instance.id)
                                }
                            }) {
                                Icon(
                                    if (instance.status == ServiceStatus.ONLINE) Icons.Default.Stop
                                    else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (instance.status == ServiceStatus.ONLINE)
                                        MaterialTheme.colorScheme.error
                                    else Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onStartInstance(((instancesHealth.filter { it.instanceType != "backup" }.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hauptinstanz", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        val nextNum = (instancesHealth.filter { it.instanceType == "backup" }
                            .mapNotNull { it.id.filter { c -> c.isDigit() }.toIntOrNull() }
                            .maxOrNull() ?: 0) + 1
                        onStartBackupInstance("b$nextNum")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Backup-Instanz", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── ADMINISTRATION ───────────────────────────────────────────────────
        SettingsSection(title = "⚙ Administration") {
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Article,
                title = "Server-Log",
                subtitle = if (adminLogs.isNotEmpty()) "${adminLogs.size} Einträge" else "Tippen zum Öffnen"
            ) {
                IconButton(onClick = onShowLogViewer) {
                    Icon(
                        Icons.AutoMirrored.Filled.Launch, contentDescription = "Log öffnen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.RestartAlt,
                title = "Server neu starten",
                subtitle = "Startet den Backend-Prozess neu"
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceStatusDot(serverStatus)
                    IconButton(onClick = onShowRestartConfirm) {
                        Icon(
                            Icons.Default.PowerSettingsNew, contentDescription = "Neustart",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Live-Konsole (erscheint nach Neustart-Befehl)
            if (restartLog.isNotEmpty()) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isRestarting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF4CAF50)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                "Konsole",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (!isRestarting) {
                            IconButton(
                                onClick = onClearRestartLog,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Konsole schließen",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 220.dp)
                            .background(
                                Color(0xFF0D1117),
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        LazyColumn(state = consoleListState) {
                            items(restartLog) { line ->
                                val lineColor = when {
                                    line.contains("✓") -> Color(0xFF4CAF50)
                                    line.contains("⚠") -> Color(0xFFFF9800)
                                    line.contains("Fehler") -> Color(0xFFF44336)
                                    line.contains("Verbindung getrennt") ||
                                    line.contains("startet neu") -> Color(0xFFFFEB3B)
                                    else -> Color(0xFFCCCCCC)
                                }
                                Text(
                                    text = line,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = lineColor,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Default.Router,
                title = "Relay-Server neu starten",
                subtitle = "Startet den TURN/STUN-Server (Coturn) neu"
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ServiceStatusDot(turnStatus)
                    IconButton(onClick = onShowRestartTurnConfirm) {
                        Icon(
                            Icons.Default.PowerSettingsNew, contentDescription = "TURN Neustart",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── SMS-Gateway ────────────────────────────────────────────────
            SettingsItem(
                icon = Icons.Default.Sms,
                title = "SMS-Gateway",
                onClick = { showSmsBroadcastDialog = true },
                subtitle = when {
                    smsGatewayStatus == null -> "Status unbekannt – tippen zum Aktualisieren"
                    smsGatewayStatus.isActive && smsGatewayStatus.gatewayReachable ->
                        "Aktiv · Queue: ${smsGatewayStatus.queueSize}"
                    smsGatewayStatus.isActive && !smsGatewayStatus.gatewayReachable ->
                        "Dienst läuft, Gateway nicht erreichbar"
                    else -> "Dienst inaktiv"
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Status-Dot
                    val smsColor = when {
                        smsGatewayStatus == null -> MaterialTheme.colorScheme.outline
                        smsGatewayStatus.isActive && smsGatewayStatus.gatewayReachable -> Color(0xFF4CAF50)
                        smsGatewayStatus.isActive -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.error
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(smsColor, CircleShape)
                    )
                    IconButton(onClick = onRefreshSmsGatewayStatus) {
                        Icon(
                            Icons.Default.Refresh, contentDescription = "Status aktualisieren",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onRestartSmsGateway) {
                        Icon(
                            Icons.Default.PowerSettingsNew, contentDescription = "SMS-Gateway neu starten",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedButton(
                    onClick = onHealDatabase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Datenbank reparieren")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── BENACHRICHTIGUNGS-MODUS ──────────────────────────────────────────
        SettingsSection(title = "🔔 Benachrichtigungen") {
            SettingsItem(
                icon = Icons.Default.NotificationsOff,
                title = "Nur FCM-Benachrichtigungen",
                subtitle = if (onlyFcmMode)
                    "Eigener Dienst stumm – nur Firebase-Push aktiv"
                else
                    "Eigener WS-Dienst + FCM aktiv"
            ) {
                Switch(
                    checked = onlyFcmMode,
                    onCheckedChange = onOnlyFcmModeChange
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── DATENBANK FAILOVER ───────────────────────────────────────────────
        var showPromoteConfirm by remember { mutableStateOf(false) }

        if (showPromoteConfirm) {
            AlertDialog(
                onDismissRequest = { showPromoteConfirm = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Failover durchführen?", fontWeight = FontWeight.Bold) },
                text = { Text("Der Backup-Server wird zum primären Datenbankmaster. Nur ausführen wenn der Hauptserver wirklich ausgefallen ist!") },
                confirmButton = {
                    Button(
                        onClick = { showPromoteConfirm = false; onFailoverPromote() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Promote") }
                },
                dismissButton = {
                    TextButton(onClick = { showPromoteConfirm = false }) { Text("Abbrechen") }
                }
            )
        }

        SettingsSection(title = "🔄 Datenbank Failover") {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onFailoverStatus,
                        modifier = Modifier.weight(1f),
                        enabled = !failoverRunning
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Status", fontSize = 13.sp)
                    }
                    Button(
                        onClick = { showPromoteConfirm = true },
                        modifier = Modifier.weight(1f),
                        enabled = !failoverRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Promote", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onFailoverRecover,
                        modifier = Modifier.weight(1f),
                        enabled = !failoverRunning
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Recover", fontSize = 13.sp)
                    }
                }
                if (failoverRunning || failoverLog.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 260.dp)
                            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val logState = rememberLazyListState()
                        LaunchedEffect(failoverLog.size) {
                            if (failoverLog.isNotEmpty()) logState.animateScrollToItem(failoverLog.size - 1)
                        }
                        LazyColumn(state = logState) {
                            if (failoverRunning && failoverLog.isEmpty()) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF4CAF50))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Verbinde…", fontSize = 11.sp, color = Color(0xFF4CAF50), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    }
                                }
                            }
                            items(failoverLog) { line ->
                                val color = when {
                                    line.contains("✓") || line.contains("OK") || line.contains("aktiv") -> Color(0xFF4CAF50)
                                    line.contains("WARN") || line.contains("⚠") -> Color(0xFFFF9800)
                                    line.contains("ERROR") || line.contains("Fehler") || line.contains("✗") -> Color(0xFFF44336)
                                    line.contains("FAILOVER") || line.contains("══") -> Color(0xFF64B5F6)
                                    else -> Color(0xFFCFD8DC)
                                }
                                Text(line, fontSize = 11.sp, color = color, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                        if (!failoverRunning && failoverLog.isNotEmpty()) {
                            IconButton(
                                onClick = onClearFailoverLog,
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Schließen", modifier = Modifier.size(14.dp), tint = Color(0xFF90A4AE))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ─── DIAMANT-KURS ──────────────────────────────────────────────────────
        DiamondRateSection(
            currentRate = diamondToEuroRate,
            onLoad = onLoadDiamondRate,
            onSave = onSetDiamondRate
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DiamondRateSection(
    currentRate: Double,
    onLoad: () -> Unit,
    onSave: (Double) -> Unit
) {
    var input by remember(currentRate) { androidx.compose.runtime.mutableStateOf("%.4f".format(currentRate)) }
    var saving by remember { androidx.compose.runtime.mutableStateOf(false) }
    var savedMsg by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onLoad() }
    LaunchedEffect(savedMsg) {
        if (savedMsg != null) { kotlinx.coroutines.delay(3000); savedMsg = null }
    }

    SettingsSection(title = "💎 Diamanten → Euro Umrechnungsfaktor") {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Legt fest, wie viel ein Diamant in Euro wert ist. Auszahlungen werden mit diesem Kurs berechnet (Mindestbetrag 20 €).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("€ pro Diamant") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            savedMsg?.let { msg ->
                Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = {
                    val rate = input.replace(",", ".").toDoubleOrNull()
                    if (rate != null && rate > 0.0) {
                        saving = true
                        onSave(rate)
                        saving = false
                        savedMsg = "Kurs gespeichert: %.4f €/Diamant".format(rate)
                    }
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kurs speichern", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── TAB 2: NUTZER ───────────────────────────────────────────────────────────

@Composable
private fun NutzerTab(
    adminSearchQuery: String,
    onAdminSearchQueryChange: (String) -> Unit,
    adminSearchResults: List<MainViewModel.AdminUserResult>,
    adminActionMessage: String?,
    onAdminSearch: (String) -> Unit,
    onAdminBlock: (String) -> Unit,
    onAdminUnblock: (String) -> Unit,
    onAdminVerifyAge: (String) -> Unit,
    onAdminMakeCreator: (String) -> Unit,
    onAdminMakeModerator: (String) -> Unit,
    onAdminRemoveModerator: (String) -> Unit,
    expandedUserId: String?,
    onExpandedUserIdChange: (String?) -> Unit,
    onShowDeleteConfirm: (String) -> Unit,
    onClearAdminActionMessage: () -> Unit,
    adminStats: Pair<Int, Int>? = null,
    callerIsModerator: Boolean = false,
    onNavigateToAnnouncement: () -> Unit = {},
    onNavigateToUserManagement: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Aktions-Buttons (nur für Admins, nicht für Moderatoren)
        if (!callerIsModerator) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onNavigateToAnnouncement,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFA8A800)
                    )
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Ankündigungen",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = onNavigateToUserManagement,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Nutzer",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 13.sp
                    )
                }
            }
        }
        // ─── Statistik-Karten ─────────────────────────────────────────────────
        adminStats?.let { (total, active) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Registriert",
                    value = total.toString(),
                    icon = Icons.Default.Group
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Online",
                    value = active.toString(),
                    icon = Icons.Default.Wifi,
                    highlight = true
                )
            }
        }
        SettingsSection(title = "👤 Nutzerverwaltung") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // Suchfeld
                OutlinedTextField(
                    value = adminSearchQuery,
                    onValueChange = onAdminSearchQueryChange,
                    label = { Text("Suche (ID, Telefon, Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (adminSearchQuery.isNotBlank()) onAdminSearch(adminSearchQuery.trim())
                    }),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (adminSearchQuery.isNotBlank()) onAdminSearch(adminSearchQuery.trim())
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Suchen")
                        }
                    }
                )

                // Aktions-Feedback
                if (adminActionMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = adminActionMessage,
                            color = if (adminActionMessage.contains("Fehler")) MaterialTheme.colorScheme.error
                                    else Color(0xFF4CAF50),
                            fontSize = 13.sp
                        )
                        IconButton(onClick = onClearAdminActionMessage, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Suchergebnisse
                if (adminSearchResults.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${adminSearchResults.size} Ergebnis(se)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    adminSearchResults.forEach { user ->
                        val isExpanded = expandedUserId == user.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onExpandedUserIdChange(if (isExpanded) null else user.id)
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(user.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        if (user.isBlocked) Text("GESPERRT",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (user.isModerator) Text("MOD",
                                            color = Color(0xFF7B1FA2),
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (user.isCreator) Text("CREATOR",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        if (user.isAdmin) Text("ADMIN",
                                            color = Color(0xFFFF9800),
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(user.fakeNumber, fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!user.phoneNumber.isNullOrBlank()) {
                                        Text(user.phoneNumber, fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        Modifier.size(8.dp).clip(CircleShape).background(
                                            if (user.isOnline) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                                        )
                                    )
                                    Icon(
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Aktions-Buttons (ausgeklappt)
                            if (isExpanded) {
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Block / Unblock
                                    if (user.isBlocked) {
                                        OutlinedButton(
                                            onClick = { onAdminUnblock(user.id) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.LockOpen, contentDescription = null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Entsperren", fontSize = 12.sp)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onAdminBlock(user.id) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.Block, contentDescription = null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Sperren", fontSize = 12.sp)
                                        }
                                    }
                                    // Alter verifizieren
                                    if (!user.ageVerified) {
                                        OutlinedButton(
                                            onClick = { onAdminVerifyAge(user.id) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.VerifiedUser, contentDescription = null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("18+ OK", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Creator machen
                                    if (!user.isCreator) {
                                        OutlinedButton(
                                            onClick = { onAdminMakeCreator(user.id) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Stars, contentDescription = null,
                                                modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Creator", fontSize = 12.sp)
                                        }
                                    }
                                    // Moderator ernennen / entfernen (nur für echte Admins)
                                    if (!callerIsModerator) {
                                        if (user.isModerator) {
                                            OutlinedButton(
                                                onClick = { onAdminRemoveModerator(user.id) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = Color(0xFF7B1FA2)
                                                )
                                            ) {
                                                Icon(Icons.Default.Shield, contentDescription = null,
                                                    modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Mod ent.", fontSize = 12.sp)
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { onAdminMakeModerator(user.id) },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = Color(0xFF7B1FA2)
                                                )
                                            ) {
                                                Icon(Icons.Default.Shield, contentDescription = null,
                                                    modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Moderator", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    // Löschen
                                    OutlinedButton(
                                        onClick = { onShowDeleteConfirm(user.id) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(Icons.Default.DeleteForever, contentDescription = null,
                                            modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Löschen", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── TAB 3: EVENTS ───────────────────────────────────────────────────────────

@Composable
private fun EventsTab(
    contactListAnimation: String,
    onSetAnimation: (String) -> Unit,
    onShowResetUsersConfirm: () -> Unit,
    onShowResetAllConfirm: () -> Unit,
    onShowLumisBroadcastDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ─── DANGER ZONE ─────────────────────────────────────────────────────
        SettingsSection(title = "⚠ Danger Zone") {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Diese Aktionen sind nicht rückgängig zu machen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = onShowResetUsersConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Icon(Icons.Default.PeopleAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Alle User löschen")
                }
                Button(
                    onClick = onShowResetAllConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vollständiger Reset + Neustart")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(title = "✨ Lumis-Broadcast (Admin)") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Sende einen Lumis-Effekt an ALLE Nutzer. Wird einmalig in der Kontaktliste abgespielt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedButton(
                    onClick = onShowLumisBroadcastDialog,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lumis-Broadcast senden")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(title = "🎭 Kontaktlisten-Animation") {
            val animOptions = listOf(
                Triple("none", "Keine", Icons.Default.Block),
                Triple("easter", "Ostern 🐰", Icons.Default.Egg),
                Triple("may", "Tanz 💃", Icons.Default.MusicNote)
            )
            animOptions.forEachIndexed { index, (value, label, icon) ->
                if (index > 0) HorizontalDivider()
                SettingsItem(
                    icon = icon,
                    title = label,
                    subtitle = if (contactListAnimation == value) "Aktiv" else ""
                ) {
                    RadioButton(
                        selected = contactListAnimation == value,
                        onClick = { onSetAnimation(value) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Changelog
        SettingsSection(title = "📋 App-Changelog") {
            AppChangelog.entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                var expanded by remember { mutableStateOf(index == 0) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsItem(
                        icon = if (index == 0) Icons.Default.NewReleases else Icons.Default.History,
                        title = "v${entry.version}  –  ${entry.title}",
                        subtitle = if (expanded) "" else entry.shortSummary,
                        onClick = { expanded = !expanded }
                    ) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            entry.items.forEach { item ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("•", fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold)
                                    Text(item, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlight: Boolean = false
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Pulsierender Status-Punkt im Ping-Rhythmus.
 * Grün = Online, Rot = Offline, Orange = Fehler, Grau = Unbekannt
 */
@Composable
private fun ServiceStatusDot(status: ServiceStatus) {
    val color = when (status) {
        ServiceStatus.ONLINE  -> Color(0xFF4CAF50)
        ServiceStatus.OFFLINE -> Color(0xFFF44336)
        ServiceStatus.ERROR   -> Color(0xFFFF9800)
        ServiceStatus.UNKNOWN -> Color(0xFF9E9E9E)
    }
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation   = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode  = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (status == ServiceStatus.UNKNOWN) 0.4f else alpha))
    )
}


// ─── TAB 4: SUPPORT ──────────────────────────────────────────────────────────

@Composable
private fun SupportTab(
    tickets: List<com.securechat.app.data.network.AdminSupportTicket>,
    actionMessage: String?,
    isLoading: Boolean = false,
    loadError: String? = null,
    onLoad: (String) -> Unit,
    onReply: (String, String, String) -> Unit,
    onUpdateStatus: (String, String) -> Unit,
    onClearActionMessage: () -> Unit
) {
    var selectedTicket by remember { mutableStateOf<com.securechat.app.data.network.AdminSupportTicket?>(null) }
    var filterStatus by remember { mutableStateOf("all") }

    LaunchedEffect(filterStatus) { onLoad(filterStatus) }

    if (actionMessage != null) {
        LaunchedEffect(actionMessage) {
            delay(2500)
            onClearActionMessage()
        }
    }

    if (selectedTicket != null) {
        SupportTicketDetail(
            ticket = selectedTicket!!,
            onBack = { selectedTicket = null },
            onReply = { reply, status ->
                onReply(selectedTicket!!.id, reply, status)
                selectedTicket = null
            },
            onUpdateStatus = { status -> onUpdateStatus(selectedTicket!!.id, status) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Snackbar
        if (actionMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    actionMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp
                )
            }
        }

        // Filter-Chips + Refresh Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("all" to "Alle", "open" to "Offen", "in_progress" to "In Bearb.", "closed" to "Geschlossen").forEach { (value, label) ->
                    FilterChip(
                        selected = filterStatus == value,
                        onClick = { if (filterStatus == value) onLoad(value) else filterStatus = value },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            IconButton(
                onClick = { onLoad(filterStatus) },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", modifier = Modifier.size(20.dp))
                }
            }
        }

        if (loadError != null && tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(loadError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = { onLoad(filterStatus) }) { Text("Erneut versuchen") }
                }
            }
        } else if (isLoading && tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (tickets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine Tickets", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tickets, key = { it.id }) { ticket ->
                    SupportTicketListItem(ticket = ticket, onClick = { selectedTicket = ticket })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SupportTicketListItem(
    ticket: com.securechat.app.data.network.AdminSupportTicket,
    onClick: () -> Unit
) {
    val statusColor = when (ticket.status) {
        "open" -> MaterialTheme.colorScheme.error
        "in_progress" -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFF4CAF50)
    }
    val statusLabel = when (ticket.status) {
        "open" -> "Offen"
        "in_progress" -> "In Bearb."
        else -> "Geschlossen"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    ticket.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        statusLabel,
                        fontSize = 10.sp,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${ticket.userFakeNumber ?: ticket.phoneNumber ?: ticket.userId ?: "unbekannt"}  ·  ${ticket.category}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ticket.imageUrls.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Attachment,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${ticket.imageUrls.size}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (ticket.description.isNotBlank()) {
                Text(
                    ticket.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportTicketDetail(
    ticket: com.securechat.app.data.network.AdminSupportTicket,
    onBack: () -> Unit,
    onReply: (String, String) -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var replyStatus by remember { mutableStateOf("closed") }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        TopAppBar(
            title = { Text(ticket.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = topBarTitleColor()
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Meta
            Card {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Von:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(ticket.userFakeNumber ?: ticket.phoneNumber ?: ticket.userId ?: "unbekannt", fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kategorie:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(ticket.category, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Status:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(ticket.status, fontSize = 13.sp, color = when (ticket.status) {
                            "open" -> MaterialTheme.colorScheme.error
                            "in_progress" -> MaterialTheme.colorScheme.tertiary
                            else -> Color(0xFF4CAF50)
                        })
                    }
                    if (ticket.createdAt != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Erstellt:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(ticket.createdAt.take(16).replace("T", " "), fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Beschreibung
            Text("Nachricht", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    ticket.description,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }

            // Anhänge (Bilder + Log-Dateien)
            if (ticket.imageUrls.isNotEmpty()) {
                val context = LocalContext.current
                val imageUrls = ticket.imageUrls.filter { url ->
                    val ext = url.substringAfterLast(".").lowercase()
                    ext in listOf("jpg", "jpeg", "png", "gif", "webp")
                }
                val fileUrls = ticket.imageUrls.filter { url ->
                    val ext = url.substringAfterLast(".").lowercase()
                    ext !in listOf("jpg", "jpeg", "png", "gif", "webp")
                }

                if (imageUrls.isNotEmpty()) {
                    Text("Bilder", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(imageUrls) { url ->
                            val fullUrl = "https://letheapp.de$url"
                            AsyncImage(
                                model = fullUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fullscreenImage = fullUrl },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                if (fileUrls.isNotEmpty()) {
                    Text("Dateien", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    fileUrls.forEach { url ->
                        val filename = url.substringAfterLast("/")
                        val fullUrl = "https://letheapp.de$url"
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(filename, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Bisherige Antwort
            if (!ticket.adminReply.isNullOrBlank()) {
                Text("Deine Antwort", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        ticket.adminReply,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Status ändern (ohne Antwort)
            if (ticket.status != "closed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ticket.status == "open") {
                        OutlinedButton(onClick = { onUpdateStatus("in_progress") }) {
                            Text("In Bearbeitung setzen", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { onUpdateStatus("closed") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Schließen", fontSize = 12.sp)
                    }
                }
            }

            // Antwort eingeben
            Text("Antworten", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            OutlinedTextField(
                value = replyText,
                onValueChange = { replyText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Deine Antwort an den Nutzer…") },
                minLines = 4,
                maxLines = 8
            )

            // Status nach Antwort
            Text("Status nach Antwort", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("in_progress" to "In Bearbeitung", "closed" to "Schließen").forEach { (value, label) ->
                    FilterChip(
                        selected = replyStatus == value,
                        onClick = { replyStatus = value },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            Button(
                onClick = { if (replyText.isNotBlank()) onReply(replyText, replyStatus) },
                modifier = Modifier.fillMaxWidth(),
                enabled = replyText.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Antwort senden")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Vollbild-Bildbetrachter
    fullscreenImage?.let { imgUrl ->
        Dialog(onDismissRequest = { fullscreenImage = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(12.dp))
                    .clickable { fullscreenImage = null }
            ) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

// ─── TAB 6: MELDUNGEN ────────────────────────────────────────────────────────

@Composable
private fun MeldungenTab(
    reports: List<com.securechat.app.data.network.AdminUserReport>,
    isLoading: Boolean,
    loadError: String?,
    onLoad: (String) -> Unit,
    onUpdateStatus: (String, String) -> Unit
) {
    var filterStatus by remember { mutableStateOf("all") }
    var selectedReport by remember { mutableStateOf<com.securechat.app.data.network.AdminUserReport?>(null) }

    LaunchedEffect(filterStatus) { onLoad(filterStatus) }

    if (selectedReport != null) {
        MeldungDetail(
            report = selectedReport!!,
            onBack = { selectedReport = null },
            onUpdateStatus = { status ->
                onUpdateStatus(selectedReport!!.id, status)
                selectedReport = null
            }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter-Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("all" to "Alle", "open" to "Offen", "reviewed" to "Geprüft", "closed" to "Geschlossen").forEach { (value, label) ->
                    FilterChip(
                        selected = filterStatus == value,
                        onClick = { if (filterStatus == value) onLoad(value) else filterStatus = value },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            IconButton(onClick = { onLoad(filterStatus) }, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", modifier = Modifier.size(20.dp))
            }
        }

        when {
            loadError != null && reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(loadError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = { onLoad(filterStatus) }) { Text("Erneut versuchen") }
                }
            }
            isLoading && reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            reports.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Keine Meldungen", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(reports, key = { it.id }) { report ->
                    MeldungListItem(report = report, onClick = { selectedReport = report })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MeldungListItem(
    report: com.securechat.app.data.network.AdminUserReport,
    onClick: () -> Unit
) {
    val statusColor = when (report.status) {
        "open" -> Color(0xFFE53935)
        "reviewed" -> Color(0xFFFB8C00)
        else -> Color(0xFF43A047)
    }
    val statusLabel = when (report.status) {
        "open" -> "Offen"
        "reviewed" -> "Geprüft"
        else -> "Geschlossen"
    }
    val contextLabel = when (report.context) {
        "CHAT" -> "Chat"
        "NEARBY" -> "Nearby"
        "VIP_SPARK" -> "VIP Spark"
        else -> report.context ?: "–"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Gemeldeter User Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!report.reportedAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = report.reportedAvatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = report.reportedName ?: report.reportedFakeNumber ?: "Unbekannt",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(statusLabel, fontSize = 10.sp, color = statusColor, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                Text(
                    text = "Grund: ${report.reason}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Von: ${report.reporterName ?: report.reporterFakeNumber ?: "Unbekannt"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text("·", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = contextLabel,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MeldungDetail(
    report: com.securechat.app.data.network.AdminUserReport,
    onBack: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    val contextLabel = when (report.context) {
        "CHAT" -> "Chat"
        "NEARBY" -> "Nearby"
        "VIP_SPARK" -> "VIP Spark"
        else -> report.context ?: "–"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Zurück")
            }
            Text("Meldungsdetails", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Melder-Karte
            item {
                Text("Gemeldet von", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!report.reporterAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = report.reporterAvatar,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column {
                            Text(report.reporterName ?: "–", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(report.reporterFakeNumber ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Gemeldeter-Karte
            item {
                Text("Gemeldeter Nutzer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!report.reportedAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = report.reportedAvatar,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column {
                            Text(report.reportedName ?: "–", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(report.reportedFakeNumber ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Meldungsinfos
            item {
                Text("Meldungsdetails", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRow(label = "Grund", value = report.reason)
                        if (!report.details.isNullOrBlank()) {
                            DetailRow(label = "Zusatzinfo", value = report.details)
                        }
                        DetailRow(label = "Bereich", value = contextLabel)
                        DetailRow(label = "Status", value = when (report.status) {
                            "open" -> "Offen"
                            "reviewed" -> "Geprüft"
                            else -> "Geschlossen"
                        })
                        if (!report.timestamp.isNullOrBlank()) {
                            DetailRow(label = "Zeitpunkt", value = report.timestamp.take(16).replace("T", " "))
                        }
                    }
                }
            }

            // Status-Aktionen
            item {
                Text("Status ändern", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (report.status != "reviewed") {
                        OutlinedButton(
                            onClick = { onUpdateStatus("reviewed") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Als geprüft markieren", fontSize = 12.sp)
                        }
                    }
                    if (report.status != "closed") {
                        Button(
                            onClick = { onUpdateStatus("closed") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Schließen", fontSize = 12.sp)
                        }
                    }
                    if (report.status != "open") {
                        OutlinedButton(
                            onClick = { onUpdateStatus("open") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wieder öffnen", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

// ─── TAB 5: BEWERBUNGEN ───────────────────────────────────────────────────────

@Composable
private fun BewerbungenTab(
    applications: List<com.securechat.app.data.network.CreatorApplicationResponse>,
    isLoading: Boolean,
    loadError: String?,
    actionMessage: String?,
    onLoad: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit,
    onSendMessage: (String, String, () -> Unit, (String) -> Unit) -> Unit,
    onClearActionMessage: () -> Unit
) {
    var filterStatus by remember { mutableStateOf("pending") }

    LaunchedEffect(filterStatus) { onLoad(filterStatus) }

    if (actionMessage != null) {
        LaunchedEffect(actionMessage) {
            kotlinx.coroutines.delay(2500)
            onClearActionMessage()
        }
    }

    val contentTypeLabels = mapOf(
        "images"     to "Bilder",
        "video"      to "Video",
        "services"   to "Dienstleistung",
        "3d_models"  to "3D Modelle / Designs",
        "livestream" to "Livestream"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Snackbar
        if (actionMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    actionMessage,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp
                )
            }
        }

        // Filter-Chips + Refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("pending" to "Ausstehend", "approved" to "Genehmigt", "all" to "Alle").forEach { (value, label) ->
                    FilterChip(
                        selected = filterStatus == value,
                        onClick = { if (filterStatus == value) onLoad(value) else filterStatus = value },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            IconButton(onClick = { onLoad(filterStatus) }, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", modifier = Modifier.size(20.dp))
                }
            }
        }

        when {
            loadError != null && applications.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(loadError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        TextButton(onClick = { onLoad(filterStatus) }) { Text("Erneut versuchen") }
                    }
                }
            }
            isLoading && applications.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            applications.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine Bewerbungen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(applications, key = { it.id }) { app ->
                        BewerbungCard(
                            app = app,
                            contentTypeLabels = contentTypeLabels,
                            onApprove = { onApprove(app.id) },
                            onReject = { note -> onReject(app.id, note) },
                            onSendMessage = { message, onSuccess, onError -> onSendMessage(app.id, message, onSuccess, onError) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BewerbungCard(
    app: com.securechat.app.data.network.CreatorApplicationResponse,
    contentTypeLabels: Map<String, String>,
    onApprove: () -> Unit,
    onReject: (String?) -> Unit,
    onSendMessage: (String, () -> Unit, (String) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var messageText by remember(app.id) { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectNote by remember { mutableStateOf("") }
    val statusColor = when (app.status) {
        "approved" -> Color(0xFF4CAF50)
        "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = when (app.status) {
        "approved" -> "Genehmigt"
        "rejected" -> "Abgelehnt"
        else -> "Ausstehend"
    }
    val contentTypes = try {
        com.google.gson.Gson().fromJson(app.contentTypes, Array<String>::class.java)?.toList() ?: emptyList()
    } catch (_: Exception) { emptyList() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Avatar + Name + Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!app.userProfileImage.isNullOrBlank()) {
                    AsyncImage(
                        model = app.userProfileImage,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (app.userName ?: "?").take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.userName ?: "–", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(app.userFakeNumber ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(statusLabel, fontSize = 11.sp, color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider()

            // Details-Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alter", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(app.age.toString(), fontSize = 14.sp)
                }
                Column(modifier = Modifier.weight(2f)) {
                    Text("E-Mail", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        app.email,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:${app.email}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Eingereicht am
            if (!app.createdAt.isNullOrBlank()) {
                Text(
                    "Eingereicht: ${app.createdAt.take(10)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bio
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Bio", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(app.bio, fontSize = 13.sp, lineHeight = 18.sp)
            }

            // Content-Typen
            if (contentTypes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Content-Typen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        contentTypes.forEach { ct ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    contentTypeLabels[ct] ?: ct,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Referenzen (als klickbarer Link falls URL)
            if (!app.references.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Referenzen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val isUrl = app.references.startsWith("http://") || app.references.startsWith("https://")
                    Text(
                        app.references,
                        fontSize = 13.sp,
                        color = if (isUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = if (isUrl) Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(app.references))
                            context.startActivity(intent)
                        } else Modifier
                    )
                }
            }

            // Admin-Notiz
            if (!app.adminNote.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Admin-Notiz", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(app.adminNote, fontSize = 13.sp)
                    }
                }
            }

            // Antwort-Thread (Rückfragen des Admins + Antworten des Bewerbers)
            if (app.messages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Verlauf", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    app.messages.forEach { msg ->
                        val isAdmin = msg.senderType == "admin"
                        Surface(
                            color = if (isAdmin) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (isAdmin) "Admin" else (app.userName ?: "Bewerber"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(msg.text, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Rückfrage an den Bewerber senden
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Rückfrage stellen…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )
                Button(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            isSendingMessage = true
                            onSendMessage(
                                messageText,
                                { isSendingMessage = false; messageText = "" },
                                { isSendingMessage = false }
                            )
                        }
                    },
                    enabled = !isSendingMessage && messageText.isNotBlank()
                ) {
                    if (isSendingMessage) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Senden")
                    }
                }
            }

            // Annehmen/Ablehnen-Buttons (nur bei pending)
            if (app.status == "pending") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Annehmen", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { showRejectDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ablehnen", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Bewerbung ablehnen") },
            text = {
                Column {
                    Text("Optional: Begründung für den Bewerber angeben.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectNote,
                        onValueChange = { rejectNote = it },
                        placeholder = { Text("Begründung (optional)…") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onReject(rejectNote.takeIf { it.isNotBlank() })
                    showRejectDialog = false
                    rejectNote = ""
                }) {
                    Text("Ablehnen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun ResourceBar(label: String, value: Float, detail: String) {
    val fraction = (value / 100f).coerceIn(0f, 1f)
    val barColor = when {
        fraction >= 0.9f -> Color(0xFFF44336)
        fraction >= 0.7f -> Color(0xFFFF9800)
        else -> Color(0xFF4CAF50)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
