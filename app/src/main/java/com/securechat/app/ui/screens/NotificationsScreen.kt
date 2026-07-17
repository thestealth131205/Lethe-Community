package com.securechat.app.ui.screens

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.NotificationHelper
import com.securechat.app.R
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    // Benachrichtigungen
    notificationsEnabled: Boolean = true,
    onNotificationsChange: (Boolean) -> Unit = {},
    vibrationEnabled: Boolean = true,
    onVibrationChange: (Boolean) -> Unit = {},
    soundEnabled: Boolean = true,
    onSoundChange: (Boolean) -> Unit = {},
    // Benachrichtigungston-Auswahl
    notificationSound: String = "default",
    onNotificationSoundChange: (String) -> Unit = {},
    // Chat-Sounds
    chatSoundReceiveEnabled: Boolean = true,
    onChatSoundReceiveChange: (Boolean) -> Unit = {},
    chatSoundSendEnabled: Boolean = true,
    onChatSoundSendChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    var notifEnabled by remember(notificationsEnabled) { mutableStateOf(notificationsEnabled) }
    var vibEnabled by remember(vibrationEnabled) { mutableStateOf(vibrationEnabled) }
    var sndEnabled by remember(soundEnabled) { mutableStateOf(soundEnabled) }
    var selectedSound by remember(notificationSound) { mutableStateOf(notificationSound) }
    var chatRcvEnabled by remember(chatSoundReceiveEnabled) { mutableStateOf(chatSoundReceiveEnabled) }
    var chatSndEnabled by remember(chatSoundSendEnabled) { mutableStateOf(chatSoundSendEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.notifications_back))
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
            // === BENACHRICHTIGUNGEN ===
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
                HorizontalDivider()
                // Ton ändern → öffnet Android Kanal-Einstellungen für Lethe Nachrichten
                SettingsItem(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.notifications_sound_change),
                    subtitle = stringResource(R.string.notifications_sound_change_subtitle)
                ) {
                    TextButton(
                        onClick = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID_MESSAGES)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra("app_package", context.packageName)
                                        putExtra("app_uid", context.applicationInfo.uid)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } catch (_: Exception) { }
                        }
                    ) {
                        Text(stringResource(R.string.notifications_sound_open))
                    }
                }
                HorizontalDivider()
                // In-App Benachrichtigungston-Auswahl
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.notifications_in_app_section),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.notifications_in_app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option: Standard
                        FilterChip(
                            selected = selectedSound == "default",
                            onClick = {
                                selectedSound = "default"
                                onNotificationSoundChange("default")
                            },
                            label = { Text(stringResource(R.string.notifications_in_app_default)) },
                            leadingIcon = if (selectedSound == "default") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        // Option: Pocker
                        FilterChip(
                            selected = selectedSound == "pocker",
                            onClick = {
                                selectedSound = "pocker"
                                onNotificationSoundChange("pocker")
                                // Vorschau abspielen
                                try {
                                    val mp = MediaPlayer.create(context, R.raw.pocker)
                                    mp?.setOnCompletionListener { it.release() }
                                    mp?.start()
                                } catch (_: Exception) {}
                            },
                            label = { Text(stringResource(R.string.notifications_in_app_pocker)) },
                            leadingIcon = if (selectedSound == "pocker") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // === CHAT-SOUNDS (in App) ===
            SettingsSection(title = stringResource(R.string.notifications_chat_sounds_section)) {
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.VolumeDown,
                    title = stringResource(R.string.notifications_received_sound),
                    subtitle = if (chatRcvEnabled)
                        stringResource(R.string.notifications_received_enabled)
                    else
                        stringResource(R.string.notifications_received_disabled)
                ) {
                    Switch(
                        checked = chatRcvEnabled,
                        onCheckedChange = { checked ->
                            chatRcvEnabled = checked
                            onChatSoundReceiveChange(checked)
                        }
                    )
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = stringResource(R.string.notifications_sent_sound),
                    subtitle = if (chatSndEnabled)
                        stringResource(R.string.notifications_sent_enabled)
                    else
                        stringResource(R.string.notifications_sent_disabled)
                ) {
                    Switch(
                        checked = chatSndEnabled,
                        onCheckedChange = { checked ->
                            chatSndEnabled = checked
                            onChatSoundSendChange(checked)
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
