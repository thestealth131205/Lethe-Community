package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.data.local.ProfileManager
import com.securechat.app.ui.theme.topBarTitleColor

/**
 * Multi-Account-Einstellungsscreen: Account wechseln (wie in AccountScreen) + Toggle für die
 * geräteweite Überwachung aller gespeicherten Accounts auf neue Nachrichten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiAccountScreen(
    onNavigateBack: () -> Unit,
    otherSavedAccounts: List<ProfileManager.SavedAccount>,
    onSwitchAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    monitorAllAccountsEnabled: Boolean,
    onMonitorAllAccountsChange: (Boolean) -> Unit,
    mixedContactListEnabled: Boolean,
    onMixedContactListChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.multi_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            // === KONTO WECHSELN ===
            SettingsSection(title = stringResource(R.string.multi_account_section_switch)) {
                if (otherSavedAccounts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.multi_account_no_saved_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                } else {
                    otherSavedAccounts.forEachIndexed { index, account ->
                        var showRemoveConfirm by remember(account.profileKey) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSwitchAccount(account.profileKey) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!account.profileImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = account.profileImageUrl,
                                    contentDescription = account.displayName,
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.displayName, fontWeight = FontWeight.Medium)
                                Text(
                                    account.fakeNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showRemoveConfirm = true }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.multi_account_remove_action),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index != otherSavedAccounts.lastIndex) HorizontalDivider()
                        if (showRemoveConfirm) {
                            AlertDialog(
                                onDismissRequest = { showRemoveConfirm = false },
                                title = { Text(stringResource(R.string.multi_account_remove_title)) },
                                text = { Text(stringResource(R.string.multi_account_remove_message, account.displayName)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onRemoveAccount(account.profileKey)
                                        showRemoveConfirm = false
                                    }) { Text(stringResource(R.string.multi_account_remove_confirm)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRemoveConfirm = false }) {
                                        Text(stringResource(R.string.multi_account_remove_cancel))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // === ALLE ACCOUNTS ÜBERWACHEN ===
            SettingsSection(title = stringResource(R.string.multi_account_section_monitoring)) {
                SettingsItem(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.multi_account_monitor_title),
                    subtitle = if (monitorAllAccountsEnabled)
                        stringResource(R.string.multi_account_monitor_subtitle_on)
                    else
                        stringResource(R.string.multi_account_monitor_subtitle_off)
                ) {
                    Switch(
                        checked = monitorAllAccountsEnabled,
                        onCheckedChange = onMonitorAllAccountsChange
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // === GEMISCHTE KONTAKTLISTE ===
            SettingsSection(title = stringResource(R.string.multi_account_section_mixed_list)) {
                SettingsItem(
                    icon = Icons.Default.People,
                    title = stringResource(R.string.multi_account_mixed_list_title),
                    subtitle = if (mixedContactListEnabled)
                        stringResource(R.string.multi_account_mixed_list_subtitle_on)
                    else
                        stringResource(R.string.multi_account_mixed_list_subtitle_off)
                ) {
                    Switch(
                        checked = mixedContactListEnabled,
                        onCheckedChange = onMixedContactListChange
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
