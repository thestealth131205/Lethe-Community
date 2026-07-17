package com.securechat.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.securechat.app.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    themeMode: com.securechat.app.data.local.ThemeMode,
    onThemeModeChange: (com.securechat.app.data.local.ThemeMode) -> Unit,
    primaryColor: Color,
    onPrimaryColorChange: (Color) -> Unit,
    accentColor: Color,
    onAccentColorChange: (Color) -> Unit,
    bubbleColor: Color,
    onBubbleColorChange: (Color) -> Unit,
    onResetToWhatsAppColors: () -> Unit = {},
    onExportData: () -> Unit,
    onImportData: () -> Unit,
    // Profil
    userName: String = "",
    fakeNumber: String = "",
    profileImageUrl: String? = null,
    onUpdateName: (String) -> Unit = {},
    onUploadProfileImage: (Uri) -> Unit = {},
    onShareInvite: (() -> Unit)? = null,
    // Datenschutz
    showOnlineStatus: Boolean = true,
    showReadReceipts: Boolean = true,
    onUpdatePrivacy: (showOnlineStatus: Boolean, showReadReceipts: Boolean) -> Unit = { _, _ -> },
    // Admin
    isAdmin: Boolean = false,
    adminLogs: List<String> = emptyList(),
    onLoadAdminLogs: () -> Unit = {},
    onRestartServer: () -> Unit = {},
    onHealDatabase: () -> Unit = {},
    // Dating
    datingRadiusKm: Float = 50f,
    onDatingRadiusChange: (Float) -> Unit = {},
    // Benachrichtigungen
    notificationsEnabled: Boolean = true,
    onNotificationsChange: (Boolean) -> Unit = {},
    vibrationEnabled: Boolean = true,
    onVibrationChange: (Boolean) -> Unit = {},
    soundEnabled: Boolean = true,
    onSoundChange: (Boolean) -> Unit = {}
) {
    var showPrimaryColorPicker by remember { mutableStateOf(false) }
    var showAccentColorPicker by remember { mutableStateOf(false) }
    var showBubbleColorPicker by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var copiedNumber by remember { mutableStateOf(false) }
    val settingsCtx = LocalContext.current

    // Datenschutz-Switches: lokaler State der sofort in der UI reagiert
    var onlineStatusEnabled by remember(showOnlineStatus) { mutableStateOf(showOnlineStatus) }
    var readReceiptsEnabled by remember(showReadReceipts) { mutableStateOf(showReadReceipts) }

    // Benachrichtigungs-Switches: lokaler State
    var notifEnabled by remember(notificationsEnabled) { mutableStateOf(notificationsEnabled) }
    var vibEnabled by remember(vibrationEnabled) { mutableStateOf(vibrationEnabled) }
    var sndEnabled by remember(soundEnabled) { mutableStateOf(soundEnabled) }

    // Bild-Picker
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onUploadProfileImage(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = topBarTitleColor(),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // === PROFIL SECTION ===
            SettingsSection(title = stringResource(R.string.account_section_profile)) {
                // Avatar + Name als visueller Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profilbild
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { imageLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profileImageUrl != null) {
                            AsyncImage(
                                model = profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        // Kamera-Badge unten rechts
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName.ifBlank { stringResource(R.string.account_no_name) },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.account_tap_image),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    IconButton(onClick = { showNameDialog = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                // --- Registrierte Nummer ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.account_lethe_number),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                        Text(
                            text = if (fakeNumber.isNotBlank()) fakeNumber else "–",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        val clipboard = settingsCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Lethe-Nummer", fakeNumber))
                        copiedNumber = true
                    }) {
                        Icon(
                            if (copiedNumber) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.account_lethe_number_copy),
                            tint = if (copiedNumber) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider()

                // --- QR-Code Bereich ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.account_qr_title),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    // QR-Code Platzhalter (wird später mit echtem QR gefüllt)
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.settings_qr_coming_soon),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                    if (onShareInvite != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onShareInvite,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.account_invite_share))
                        }
                    }
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.PhotoCamera,
                    title = stringResource(R.string.settings_change_photo),
                    subtitle = stringResource(R.string.settings_change_photo_subtitle)
                ) {
                    IconButton(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(
                            Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === DATENSCHUTZ SECTION ===
            SettingsSection(title = stringResource(R.string.privacy_title)) {
                SettingsItem(
                    icon = Icons.Default.Visibility,
                    title = stringResource(R.string.privacy_online_status_title),
                    subtitle = if (onlineStatusEnabled) stringResource(R.string.privacy_online_status_enabled)
                               else stringResource(R.string.privacy_online_status_disabled)
                ) {
                    Switch(
                        checked = onlineStatusEnabled,
                        onCheckedChange = { checked ->
                            onlineStatusEnabled = checked
                            onUpdatePrivacy(checked, readReceiptsEnabled)
                        }
                    )
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.DoneAll,
                    title = stringResource(R.string.privacy_read_receipts_title),
                    subtitle = if (readReceiptsEnabled) stringResource(R.string.privacy_read_receipts_enabled)
                               else stringResource(R.string.privacy_read_receipts_disabled)
                ) {
                    Switch(
                        checked = readReceiptsEnabled,
                        onCheckedChange = { checked ->
                            readReceiptsEnabled = checked
                            onUpdatePrivacy(onlineStatusEnabled, checked)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === BENACHRICHTIGUNGEN SECTION ===
            SettingsSection(title = stringResource(R.string.notifications_section)) {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notifications_enabled_title),
                    subtitle = if (notifEnabled) stringResource(R.string.notifications_enabled_subtitle)
                               else stringResource(R.string.notifications_disabled_subtitle)
                ) {
                    Switch(
                        checked = notifEnabled,
                        onCheckedChange = { checked ->
                            notifEnabled = checked
                            onNotificationsChange(checked)
                        }
                    )
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.Vibration,
                    title = stringResource(R.string.notifications_vibration_title),
                    subtitle = if (vibEnabled) stringResource(R.string.notifications_vibration_enabled)
                               else stringResource(R.string.notifications_vibration_disabled)
                ) {
                    Switch(
                        checked = vibEnabled,
                        onCheckedChange = { checked ->
                            vibEnabled = checked
                            onVibrationChange(checked)
                        }
                    )
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = stringResource(R.string.notifications_sound_title),
                    subtitle = if (sndEnabled) stringResource(R.string.notifications_sound_enabled)
                               else stringResource(R.string.notifications_sound_silent)
                ) {
                    Switch(
                        checked = sndEnabled,
                        onCheckedChange = { checked ->
                            sndEnabled = checked
                            onSoundChange(checked)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === DATING SECTION ===
            SettingsSection(title = stringResource(R.string.settings_dating_section)) {
                var sliderValue by remember(datingRadiusKm) { mutableStateOf(datingRadiusKm) }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.settings_dating_radius, sliderValue.toInt()))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onDatingRadiusChange(sliderValue) },
                        valueRange = 10f..200f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === DESIGN SECTION ===
            SettingsSection(title = stringResource(R.string.design_title)) {
                // Theme Mode Selection
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.design_mode_title),
                    subtitle = when (themeMode) {
                        com.securechat.app.data.local.ThemeMode.LIGHT -> stringResource(R.string.design_mode_light)
                        com.securechat.app.data.local.ThemeMode.DARK -> stringResource(R.string.design_mode_dark)
                        com.securechat.app.data.local.ThemeMode.SYSTEM -> stringResource(R.string.design_mode_system)
                    }
                ) {
                    IconButton(onClick = { showThemeModeDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                // Primary Color
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.design_primary_color),
                    subtitle = stringResource(R.string.design_primary_subtitle)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { showPrimaryColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                        )
                    ) {}
                }

                HorizontalDivider()

                // Accent Color
                SettingsItem(
                    icon = Icons.Default.Brush,
                    title = stringResource(R.string.design_accent_color),
                    subtitle = stringResource(R.string.design_accent_subtitle)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { showAccentColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                        )
                    ) {}
                }

                HorizontalDivider()

                // Bubble Color
                SettingsItem(
                    icon = Icons.Default.ChatBubble,
                    title = stringResource(R.string.design_bubble_color_own),
                    subtitle = stringResource(R.string.design_bubble_subtitle_own)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { showBubbleColorPicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = bubbleColor,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline
                        )
                    ) {}
                }

                HorizontalDivider()

                // Reset-Button: WhatsApp-Standardfarben
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // Farbvorschau der WhatsApp-Farben
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.design_reset_section),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                stringResource(R.string.design_reset_subtitle),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        // Drei Farb-Kreise als Vorschau
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                Color(0xFF075E54), // Primary
                                Color(0xFF128C7E), // Accent
                                Color(0xFFDCF8C6)  // Bubble
                            ).forEach { c ->
                                Surface(
                                    modifier = Modifier.size(18.dp),
                                    shape = CircleShape,
                                    color = c,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.5.dp, MaterialTheme.colorScheme.outline
                                    )
                                ) {}
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onResetToWhatsAppColors,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.design_reset_button))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === DATA SECTION ===
            SettingsSection(title = stringResource(R.string.info_data_section)) {
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.info_export_title),
                    subtitle = stringResource(R.string.info_export_subtitle)
                ) {
                    IconButton(onClick = onExportData) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                SettingsItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.info_import_title),
                    subtitle = stringResource(R.string.info_import_subtitle)
                ) {
                    IconButton(onClick = onImportData) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === INFO SECTION ===
            val uriHandler = LocalUriHandler.current
            SettingsSection(title = stringResource(R.string.settings_about_section)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.info_version),
                    subtitle = BuildConfig.VERSION_NAME
                ) {}
                SettingsItem(
                    icon = Icons.Default.Copyright,
                    title = "Copyright",
                    subtitle = stringResource(R.string.info_copyright)
                ) {}
                SettingsItem(
                    icon = Icons.Default.MusicNote,
                    title = "Music by TheStealth",
                    subtitle = "music.thestealth.de",
                    onClick = { uriHandler.openUri("https://music.thestealth.de") }
                )
            }

            // === ADMIN SECTION (nur für Admins sichtbar) ===
            if (isAdmin) {
                Spacer(modifier = Modifier.height(16.dp))

                SettingsSection(title = stringResource(R.string.settings_admin_section)) {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = stringResource(R.string.backend_server_log),
                        subtitle = stringResource(R.string.settings_admin_log_subtitle, adminLogs.size)
                    ) {
                        IconButton(onClick = {
                            onLoadAdminLogs()
                            showLogViewer = true
                        }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Default.RestartAlt,
                        title = stringResource(R.string.backend_server_restart_button),
                        subtitle = stringResource(R.string.backend_server_restart_subtitle)
                    ) {
                        IconButton(onClick = { showRestartConfirm = true }) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    HorizontalDivider()

                    // Heal-DB Button
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedButton(
                            onClick = onHealDatabase,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.backend_repair_db))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Color Pickers
    if (showPrimaryColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.design_primary_color_dialog),
            currentColor = primaryColor,
            onDismiss = { showPrimaryColorPicker = false },
            onColorSelected = {
                onPrimaryColorChange(it)
                showPrimaryColorPicker = false
            }
        )
    }

    if (showAccentColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.design_accent_color_dialog),
            currentColor = accentColor,
            onDismiss = { showAccentColorPicker = false },
            onColorSelected = {
                onAccentColorChange(it)
                showAccentColorPicker = false
            }
        )
    }

    if (showBubbleColorPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.design_bubble_color_dialog_own),
            currentColor = bubbleColor,
            showBubblePalette = true,
            onDismiss = { showBubbleColorPicker = false },
            onColorSelected = {
                onBubbleColorChange(it)
                showBubbleColorPicker = false
            }
        )
    }

    // Theme Mode Dialog
    if (showThemeModeDialog) {
        ThemeModeDialog(
            currentMode = themeMode,
            onDismiss = { showThemeModeDialog = false },
            onModeSelected = {
                onThemeModeChange(it)
                showThemeModeDialog = false
            }
        )
    }

    // Name-Änderungs-Dialog
    if (showNameDialog) {
        var newName by remember { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            title = { Text(stringResource(R.string.account_name_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.account_name_dialog_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onUpdateName(newName.trim())
                            showNameDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(R.string.account_name_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }

    // Admin: Server-Log Viewer
    if (showLogViewer) {
        AlertDialog(
            onDismissRequest = { showLogViewer = false },
            title = { Text(stringResource(R.string.backend_server_log)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (adminLogs.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.backend_no_logs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        items(adminLogs.size) { index ->
                            Text(
                                text = adminLogs[index],
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = androidx.compose.ui.Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onLoadAdminLogs()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = androidx.compose.ui.Modifier.size(16.dp))
                    Spacer(modifier = androidx.compose.ui.Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_admin_log_refresh))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogViewer = false }) {
                    Text(stringResource(R.string.general_close))
                }
            }
        )
    }

    // Admin: Neustart-Bestätigung
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.settings_admin_restart_title)) },
            text = {
                Text(stringResource(R.string.backend_server_restart_text))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestartServer()
                        showRestartConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.backend_server_restart_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: Color,
    showBubblePalette: Boolean = false,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    val presetColors = if (showBubblePalette) {
        // Spezielle Palette für Chat-Blasen – hell → mittel → dunkel je Farbfamilie
        listOf(
            // Rosa/Pink – hell → dunkel
            Color(0xFFFFCDD2), Color(0xFFFF8FA3), Color(0xFFE91E63), Color(0xFF880E4F),
            // Pfirsich/Orange – hell → dunkel
            Color(0xFFFFDFBA), Color(0xFFFFAB40), Color(0xFFFF6D00), Color(0xFFBF360C),
            // Gelb/Amber – hell → dunkel
            Color(0xFFFFF9C4), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFF57F17),
            // Grün – hell → dunkel
            Color(0xFFCCFF90), Color(0xFF69F0AE), Color(0xFF4CAF50), Color(0xFF1B5E20),
            // Hellgrün/Lime – hell → dunkel
            Color(0xFFF9FBE7), Color(0xFFCDDC39), Color(0xFF8BC34A), Color(0xFF33691E),
            // Blau – hell → dunkel
            Color(0xFFBAE1FF), Color(0xFF40C4FF), Color(0xFF2196F3), Color(0xFF0D47A1),
            // Hellblau/Sky – hell → dunkel
            Color(0xFFE1F5FE), Color(0xFF87CEEB), Color(0xFF03A9F4), Color(0xFF01579B),
            // Indigo/Navy – hell → dunkel
            Color(0xFFC5CAE9), Color(0xFF7986CB), Color(0xFF3949AB), Color(0xFF1A237E),
            // Lila/Purple – hell → dunkel
            Color(0xFFE0BBE4), Color(0xFFCE93D8), Color(0xFF9C27B0), Color(0xFF4A148C),
            // Lavendel/Flieder – hell → dunkel
            Color(0xFFC7CEEA), Color(0xFF9FA8DA), Color(0xFF5C6BC0), Color(0xFF283593),
            // Teal/Cyan – hell → dunkel
            Color(0xFFA8EFED), Color(0xFF80DEEA), Color(0xFF00BCD4), Color(0xFF006064),
            // Mintgrün – hell → dunkel
            Color(0xFFB5EAD7), Color(0xFF80CBC4), Color(0xFF009688), Color(0xFF004D40),
            // Braun – hell → dunkel
            Color(0xFFD7CCC8), Color(0xFFA1887F), Color(0xFF795548), Color(0xFF4E342E),
            // Blaugrau – hell → dunkel
            Color(0xFFCFD8DC), Color(0xFF90A4AE), Color(0xFF607D8B), Color(0xFF37474F),
            // Pastell-Extra
            Color(0xFFFFB3C6), Color(0xFFFFD6A5), Color(0xFFFFF1A8), Color(0xFFD4AAFF),
            // Neon & Spezial
            Color(0xFFFFFF00), Color(0xFFFF1493), Color(0xFF00FF7F), Color(0xFF7FDBFF),
            // Graustufen
            Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFFC0C0C0), Color(0xFF9E9E9E),
            Color(0xFF757575), Color(0xFF505050), Color(0xFF303030), Color(0xFF000000)
        )
    } else {
        // Standard-Palette für Primär/Akzent-Farben – nach Farbfamilien hell → dunkel
        listOf(
            // Schwarz & Weiß
            Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFF757575), Color(0xFF000000),
            // Pink/Rosa – hell → dunkel
            Color(0xFFFF8FA3), Color(0xFFE91E63), Color(0xFFC2185B), Color(0xFF880E4F),
            // Rot – hell → dunkel
            Color(0xFFEF9A9A), Color(0xFFF44336), Color(0xFFDC143C), Color(0xFFB71C1C),
            // Deep Orange – hell → dunkel
            Color(0xFFFF8A65), Color(0xFFFF5722), Color(0xFFE64A19), Color(0xFFBF360C),
            // Orange – hell → dunkel
            Color(0xFFFFCC80), Color(0xFFFF9800), Color(0xFFF57C00), Color(0xFFE65100),
            // Amber/Gelb – hell → dunkel
            Color(0xFFFFF176), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFF57F17),
            // Gold & Senf
            Color(0xFFFFFF00), Color(0xFFFFD700), Color(0xFFFFDB58), Color(0xFFF9A825),
            // Hellgrün/Lime – hell → dunkel
            Color(0xFFF0F4C3), Color(0xFFCDDC39), Color(0xFF8BC34A), Color(0xFF33691E),
            // Grün – hell → dunkel
            Color(0xFFA5D6A7), Color(0xFF4CAF50), Color(0xFF388E3C), Color(0xFF1B5E20),
            // Teal – hell → dunkel
            Color(0xFF80CBC4), Color(0xFF009688), Color(0xFF00796B), Color(0xFF004D40),
            // Cyan – hell → dunkel
            Color(0xFF80DEEA), Color(0xFF00BCD4), Color(0xFF018786), Color(0xFF006064),
            // Hellblau/Sky – hell → dunkel
            Color(0xFF7FDBFF), Color(0xFF03A9F4), Color(0xFF0288D1), Color(0xFF01579B),
            // Blau – hell → dunkel
            Color(0xFF90CAF9), Color(0xFF2196F3), Color(0xFF1565C0), Color(0xFF0D47A1),
            // Kobalt/Royal – hell → dunkel
            Color(0xFF87CEEB), Color(0xFF1E90FF), Color(0xFF0074D9), Color(0xFF001F3F),
            // Indigo/Navy – hell → dunkel
            Color(0xFF9FA8DA), Color(0xFF3949AB), Color(0xFF1A237E), Color(0xFF0047AB),
            // Kornblume & Dodger
            Color(0xFF6495ED), Color(0xFF7986CB), Color(0xFF5C6BC0), Color(0xFF283593),
            // Lila/Purple – hell → dunkel
            Color(0xFFCE93D8), Color(0xFF9C27B0), Color(0xFF7B1FA2), Color(0xFF4A148C),
            // Deep Purple – hell → dunkel
            Color(0xFFB39DDB), Color(0xFF673AB7), Color(0xFF512DA8), Color(0xFF311B92),
            // Lavendel/Flieder
            Color(0xFFE0BBE4), Color(0xFFC7CEEA), Color(0xFFD4AAFF), Color(0xFF9575CD),
            // Blaugrau – hell → dunkel
            Color(0xFFCFD8DC), Color(0xFF90A4AE), Color(0xFF607D8B), Color(0xFF37474F),
            // Braun – hell → dunkel
            Color(0xFFD7CCC8), Color(0xFFA1887F), Color(0xFF795548), Color(0xFF4E342E),
            // Pastell-Reihe
            Color(0xFFFFB3C6), Color(0xFFFFD6A5), Color(0xFFB5EAD7), Color(0xFFA8EFED),
            // Neon & Spezial
            Color(0xFFFF1493), Color(0xFF00FF7F), Color(0xFF7FDBFF), Color(0xFF6200EE),
            // Aus Chatblasen-Palette
            Color(0xFFFFCDD2), Color(0xFFFFDFBA), Color(0xFFFFAB40), Color(0xFFFF6D00),
            Color(0xFFFFF9C4), Color(0xFFFFF1A8), Color(0xFFCCFF90), Color(0xFF69F0AE),
            Color(0xFFF9FBE7), Color(0xFFBAE1FF), Color(0xFF40C4FF), Color(0xFFE1F5FE),
            Color(0xFFC5CAE9), Color(0xFFC0C0C0), Color(0xFF9E9E9E), Color(0xFF505050),
            Color(0xFF303030)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.design_color_picker_hint),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Grid of colors (4 Spalten)
                Column {
                    presetColors.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { color ->
                                Surface(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .padding(4.dp)
                                        .clickable { onColorSelected(color) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = color,
                                    border = if (color == currentColor) {
                                        androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        // Weiße Border für dunkle Farben, graue für helle
                                        val borderColor = if (color.luminance() < 0.5f) {
                                            Color.White.copy(alpha = 0.3f)
                                        } else {
                                            Color.Gray.copy(alpha = 0.3f)
                                        }
                                        androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                                    }
                                ) {
                                    // Für sehr helle Farben (Weiß) einen Rahmen zeigen
                                    if (color.luminance() > 0.95f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp)
                                                .background(
                                                    color = Color.LightGray.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                        )
                                    }
                                }
                            }
                            // Fülle leere Plätze in der letzten Reihe
                            repeat(4 - row.size) {
                                Spacer(modifier = Modifier.size(60.dp).padding(4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel))
            }
        }
    )
}

@Composable
fun ThemeModeDialog(
    currentMode: com.securechat.app.data.local.ThemeMode,
    onDismiss: () -> Unit,
    onModeSelected: (com.securechat.app.data.local.ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.design_theme_mode_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.design_theme_mode_subtitle),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // System Standard
                ThemeModeOption(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.design_mode_system_option),
                    subtitle = stringResource(R.string.design_mode_system_subtitle),
                    isSelected = currentMode == com.securechat.app.data.local.ThemeMode.SYSTEM,
                    onClick = { onModeSelected(com.securechat.app.data.local.ThemeMode.SYSTEM) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Helles Design
                ThemeModeOption(
                    icon = Icons.Default.LightMode,
                    title = stringResource(R.string.design_mode_light_option),
                    subtitle = stringResource(R.string.design_mode_light_subtitle),
                    isSelected = currentMode == com.securechat.app.data.local.ThemeMode.LIGHT,
                    onClick = { onModeSelected(com.securechat.app.data.local.ThemeMode.LIGHT) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dunkles Design
                ThemeModeOption(
                    icon = Icons.Default.DarkMode,
                    title = stringResource(R.string.design_mode_dark_option),
                    subtitle = stringResource(R.string.design_mode_dark_subtitle),
                    isSelected = currentMode == com.securechat.app.data.local.ThemeMode.DARK,
                    onClick = { onModeSelected(com.securechat.app.data.local.ThemeMode.DARK) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_done))
            }
        }
    )
}

@Composable
fun ThemeModeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
