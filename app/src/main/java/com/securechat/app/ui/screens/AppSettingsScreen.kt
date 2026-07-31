package com.securechat.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.securechat.app.BuildConfig
import com.securechat.app.R
import com.securechat.app.data.BackupManager.BackupDestination
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDesign: (() -> Unit)? = null,
    onNavigateToPrivacy: (() -> Unit)? = null,
    onNavigateToNotifications: (() -> Unit)? = null,
    onExportBackup: ((password: String, destination: BackupDestination) -> Unit)? = null,
    onExportBackupNextcloud: ((password: String, serverUrl: String, ncUser: String, ncPassword: String) -> Unit)? = null,
    onImportBackup: ((password: String) -> Unit)? = null,
    backupProgress: Float = -1f,
    keyBackupInfo: com.securechat.app.ui.KeyBackupInfo? = null,
    onBackupKeys: ((passphrase: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onRestoreKeys: ((passphrase: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onNavigateToLogViewer: (() -> Unit)? = null,
    onNavigateToFamily: (() -> Unit)? = null,
    onNavigateToTor: (() -> Unit)? = null,
    onNavigateToDecentralized: (() -> Unit)? = null,
    onNavigateToAppEdit: (() -> Unit)? = null,
    onNavigateToAudio: (() -> Unit)? = null,
    onNavigateToMyMusic: (() -> Unit)? = null,
    enterToSend: Boolean = false,
    onEnterToSendChange: (Boolean) -> Unit = {}
) {
    // Key-Backup Dialog State
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupPassphraseConfirm by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }
    var keyBackupResultMessage by remember { mutableStateOf<String?>(null) }
    var keyBackupLoading by remember { mutableStateOf(false) }

    // Full-Backup Dialog State
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirm by remember { mutableStateOf("") }
    var importPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
                .padding(top = 8.dp)
        ) {
            if (onNavigateToNotifications != null) {
                SettingsSection(title = stringResource(R.string.app_settings_section_notifications)) {
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.app_settings_notifications_title),
                        subtitle = stringResource(R.string.app_settings_notifications_subtitle),
                        onClick = onNavigateToNotifications
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            SettingsSection(title = stringResource(R.string.app_settings_section_chat)) {
                SettingsItem(
                    icon = Icons.Default.Keyboard,
                    title = stringResource(R.string.app_settings_enter_to_send),
                    subtitle = if (enterToSend) stringResource(R.string.app_settings_enter_to_send_on) else stringResource(R.string.app_settings_enter_to_send_off)
                ) {
                    Switch(
                        checked = enterToSend,
                        onCheckedChange = onEnterToSendChange
                    )
                }
                if (onNavigateToAudio != null) {
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.VolumeUp,
                        title = "Audio",
                        subtitle = "Audio-Qualität und Bluetooth-Ausgang",
                        onClick = onNavigateToAudio
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                if (onNavigateToMyMusic != null) {
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.LibraryMusic,
                        title = "Meine Musik",
                        subtitle = "Gespeicherte Titel, Favoriten und Playlists",
                        onClick = onNavigateToMyMusic
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsSection(title = stringResource(R.string.app_settings_section_display)) {
                if (onNavigateToDesign != null) {
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.app_settings_design_title),
                        subtitle = stringResource(R.string.app_settings_design_subtitle),
                        onClick = onNavigateToDesign
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    if (onNavigateToPrivacy != null) HorizontalDivider()
                }
                if (onNavigateToPrivacy != null) {
                    SettingsItem(
                        icon = Icons.Default.Shield,
                        title = stringResource(R.string.app_settings_privacy_title),
                        subtitle = stringResource(R.string.app_settings_privacy_subtitle),
                        onClick = onNavigateToPrivacy
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                if (onNavigateToTor != null) {
                    if (onNavigateToPrivacy != null) HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.VpnKey,
                        title = stringResource(R.string.app_settings_tor_title),
                        subtitle = stringResource(R.string.app_settings_tor_subtitle),
                        onClick = onNavigateToTor
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                if (onNavigateToDecentralized != null) {
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Hub,
                        title = stringResource(R.string.app_settings_p2p_title),
                        subtitle = stringResource(R.string.app_settings_p2p_subtitle),
                        onClick = onNavigateToDecentralized
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                if (onNavigateToAppEdit != null) {
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.Dashboard,
                        title = stringResource(R.string.app_settings_nav_customize_title),
                        subtitle = stringResource(R.string.app_settings_nav_customize_subtitle),
                        onClick = onNavigateToAppEdit
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

            }

            Spacer(Modifier.height(8.dp))

            SettingsSection(title = stringResource(R.string.info_data_section)) {
                SettingsItem(
                    icon = Icons.Default.Upload,
                    title = stringResource(R.string.app_settings_backup_title),
                    subtitle = stringResource(R.string.app_settings_backup_subtitle)
                ) {
                    IconButton(onClick = {
                        exportPassword = ""
                        exportPasswordConfirm = ""
                        showExportDialog = true
                    }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Backup erstellen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (backupProgress in 0f..1f) {
                    LinearProgressIndicator(
                        progress = { backupProgress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.app_settings_restore_title),
                    subtitle = stringResource(R.string.app_settings_restore_subtitle)
                ) {
                    IconButton(onClick = {
                        importPassword = ""
                        showImportDialog = true
                    }) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = "Backup importieren",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.info_backup_keys_title),
                    subtitle = stringResource(R.string.info_backup_keys_subtitle)
                ) {
                    IconButton(onClick = {
                        backupPassphrase = ""
                        backupPassphraseConfirm = ""
                        showBackupDialog = true
                    }) {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription = stringResource(R.string.info_backup_button),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.LockOpen,
                    title = stringResource(R.string.info_restore_keys_title),
                    subtitle = stringResource(R.string.info_restore_keys_subtitle)
                ) {
                    IconButton(onClick = {
                        restorePassphrase = ""
                        showRestoreDialog = true
                    }) {
                        Icon(
                            Icons.Default.RestorePage,
                            contentDescription = stringResource(R.string.info_restore_button),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider()
                run {
                    val info = keyBackupInfo
                    val subtitle = if (info != null && info.dateKnown) {
                        val date = remember(info.timestamp) {
                            java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(info.timestamp))
                        }
                        val version = if (info.automatic)
                            stringResource(R.string.info_backup_status_v3)
                        else
                            stringResource(R.string.info_backup_status_v2)
                        stringResource(R.string.info_backup_status_subtitle, date, version)
                    } else if (info != null) {
                        stringResource(R.string.info_backup_status_exists)
                    } else {
                        stringResource(R.string.info_backup_status_none)
                    }
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = stringResource(R.string.info_backup_status_title),
                        subtitle = subtitle
                    )
                }
                if (onNavigateToFamily != null) {
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.FamilyRestroom,
                        title = stringResource(R.string.info_family_title),
                        subtitle = stringResource(R.string.info_family_subtitle),
                        onClick = onNavigateToFamily
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onNavigateToLogViewer != null) {
                    HorizontalDivider()
                    SettingsItem(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.info_app_logs_title),
                        subtitle = stringResource(R.string.info_app_logs_subtitle),
                        onClick = onNavigateToLogViewer
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Ergebnis-Snackbar (Key-Backup)
            keyBackupResultMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.startsWith("Schlüssel erfolgreich") || msg.startsWith("Schlüssel-Backup") ||
                            msg.startsWith("Keys successfully") || msg.startsWith("Key backup"))
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { keyBackupResultMessage = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // === BACKUP DIALOG ===
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { if (!keyBackupLoading) showBackupDialog = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.info_backup_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.info_backup_dialog_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text(stringResource(R.string.info_backup_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !keyBackupLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = backupPassphraseConfirm,
                        onValueChange = { backupPassphraseConfirm = it },
                        label = { Text(stringResource(R.string.info_backup_confirm_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !keyBackupLoading,
                        isError = backupPassphraseConfirm.isNotEmpty() && backupPassphrase != backupPassphraseConfirm,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (keyBackupLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (backupPassphrase.length < 8) return@TextButton
                        if (backupPassphrase != backupPassphraseConfirm) return@TextButton
                        keyBackupLoading = true
                        onBackupKeys?.invoke(backupPassphrase) { _, msg ->
                            keyBackupLoading = false
                            showBackupDialog = false
                            keyBackupResultMessage = msg
                        }
                    },
                    enabled = !keyBackupLoading &&
                            backupPassphrase.length >= 8 &&
                            backupPassphrase == backupPassphraseConfirm
                ) { Text(stringResource(R.string.info_backup_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackupDialog = false },
                    enabled = !keyBackupLoading
                ) { Text(stringResource(R.string.info_backup_cancel)) }
            }
        )
    }

    // === RESTORE DIALOG ===
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { if (!keyBackupLoading) showRestoreDialog = false },
            icon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
            title = { Text(stringResource(R.string.info_restore_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.info_restore_dialog_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text(stringResource(R.string.info_backup_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        enabled = !keyBackupLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (keyBackupLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (restorePassphrase.isBlank()) return@TextButton
                        keyBackupLoading = true
                        onRestoreKeys?.invoke(restorePassphrase) { _, msg ->
                            keyBackupLoading = false
                            showRestoreDialog = false
                            keyBackupResultMessage = msg
                        }
                    },
                    enabled = !keyBackupLoading && restorePassphrase.isNotBlank()
                ) { Text(stringResource(R.string.info_restore_button)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreDialog = false },
                    enabled = !keyBackupLoading
                ) { Text(stringResource(R.string.general_cancel)) }
            }
        )
    }

    // === EXPORT-BACKUP DIALOG (Passwort + Ziel) ===
    if (showExportDialog) {
        var selectedDestination by remember { mutableStateOf(BackupDestination.LOCAL) }
        var nextcloudUrl by remember { mutableStateOf("") }
        var nextcloudUser by remember { mutableStateOf("") }
        var nextcloudPassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Default.Backup, contentDescription = null) },
            title = { Text(stringResource(R.string.app_settings_backup_dialog_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(R.string.app_settings_backup_dialog_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        label = { Text(stringResource(R.string.app_settings_backup_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportPasswordConfirm,
                        onValueChange = { exportPasswordConfirm = it },
                        label = { Text(stringResource(R.string.app_settings_backup_password_confirm_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        isError = exportPasswordConfirm.isNotEmpty() && exportPassword != exportPasswordConfirm,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (exportPassword.isNotEmpty() && exportPassword.length < 8) {
                        Text(
                            stringResource(R.string.app_settings_backup_password_min),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(stringResource(R.string.app_settings_backup_storage_label), style = MaterialTheme.typography.titleSmall)

                    // Lokal
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDestination = BackupDestination.LOCAL }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedDestination == BackupDestination.LOCAL,
                            onClick = { selectedDestination = BackupDestination.LOCAL }
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.app_settings_backup_local_title), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.app_settings_backup_local_subtitle), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    // Google Drive – nur im playstore-Build (play-services-auth ist proprietär
                    // und für F-Droid nicht zulässig; siehe com.securechat.app.backup.GoogleAuthProvider).
                    if (!BuildConfig.IS_FOSS) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDestination = BackupDestination.GOOGLE_DRIVE }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedDestination == BackupDestination.GOOGLE_DRIVE,
                                onClick = { selectedDestination = BackupDestination.GOOGLE_DRIVE }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stringResource(R.string.app_settings_backup_drive_title), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.app_settings_backup_drive_subtitle), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }

                    // Nextcloud
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDestination = BackupDestination.NEXTCLOUD }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedDestination == BackupDestination.NEXTCLOUD,
                            onClick = { selectedDestination = BackupDestination.NEXTCLOUD }
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.app_settings_backup_nextcloud_title), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.app_settings_backup_nextcloud_subtitle), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    // Nextcloud-Felder
                    if (selectedDestination == BackupDestination.NEXTCLOUD) {
                        OutlinedTextField(
                            value = nextcloudUrl,
                            onValueChange = { nextcloudUrl = it },
                            label = { Text(stringResource(R.string.app_settings_backup_server_url)) },
                            placeholder = { Text("https://cloud.example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = nextcloudUser,
                            onValueChange = { nextcloudUser = it },
                            label = { Text(stringResource(R.string.app_settings_backup_username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = nextcloudPassword,
                            onValueChange = { nextcloudPassword = it },
                            label = { Text(stringResource(R.string.app_settings_backup_nextcloud_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                val nextcloudReady = selectedDestination != BackupDestination.NEXTCLOUD ||
                        (nextcloudUrl.isNotBlank() && nextcloudUser.isNotBlank() && nextcloudPassword.isNotBlank())
                TextButton(
                    onClick = {
                        showExportDialog = false
                        if (selectedDestination == BackupDestination.NEXTCLOUD) {
                            onExportBackupNextcloud?.invoke(exportPassword, nextcloudUrl, nextcloudUser, nextcloudPassword)
                        } else {
                            onExportBackup?.invoke(exportPassword, selectedDestination)
                        }
                    },
                    enabled = exportPassword.length >= 8 && exportPassword == exportPasswordConfirm && nextcloudReady
                ) { Text(stringResource(R.string.app_settings_backup_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.app_settings_backup_cancel)) }
            }
        )
    }

    // === IMPORT-BACKUP DIALOG (Passwort einfach) ===
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = { Icon(Icons.Default.RestorePage, contentDescription = null) },
            title = { Text(stringResource(R.string.app_settings_restore_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.app_settings_restore_dialog_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = { importPassword = it },
                        label = { Text(stringResource(R.string.app_settings_backup_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        onImportBackup?.invoke(importPassword)
                    },
                    enabled = importPassword.isNotBlank()
                ) { Text(stringResource(R.string.app_settings_restore_import)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(stringResource(R.string.app_settings_backup_cancel)) }
            }
        )
    }
}

// ─── App Edit Screen ────────────────────────────────────────────────────────

/**
 * Screen zum Ein-/Ausblenden von BottomBar-Tabs.
 * Chat und Status sind fest eingeblendet und können nicht deaktiviert werden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEditScreen(
    hiddenNavItems: Set<String>,
    onHiddenNavItemsChange: (Set<String>) -> Unit,
    onNavigateBack: () -> Unit
) {
    data class NavItem(val key: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

    val toggleableItems = listOf(
        NavItem("nearby", "Nearby", Icons.Default.LocationOn),
        NavItem("sparks", "Sparks", Icons.Default.VideoLibrary),
        NavItem("creator", "Creator", Icons.Default.Create),
        NavItem("vip", "VIP", Icons.Default.Star)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_nav_customize_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.app_settings_back_cd))
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
                .padding(top = 8.dp)
        ) {
            SettingsSection(title = stringResource(R.string.app_settings_nav_section)) {
                // Chat – immer sichtbar, nicht deaktivierbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_settings_nav_chats), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.app_settings_nav_always_visible), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Switch(checked = true, onCheckedChange = null, enabled = false)
                }
                HorizontalDivider()
                // Status – immer sichtbar, nicht deaktivierbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_settings_nav_status), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.app_settings_nav_always_visible), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Switch(checked = true, onCheckedChange = null, enabled = false)
                }
                // Togglebare Einträge
                toggleableItems.forEachIndexed { index, item ->
                    HorizontalDivider()
                    val enabled = item.key !in hiddenNavItems
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { isEnabled ->
                                val newHidden = if (isEnabled) {
                                    hiddenNavItems - item.key
                                } else {
                                    hiddenNavItems + item.key
                                }
                                onHiddenNavItemsChange(newHidden)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Chat und Status können nicht ausgeblendet werden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
