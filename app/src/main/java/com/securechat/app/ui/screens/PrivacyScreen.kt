package com.securechat.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.R
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    // Datenschutz
    showOnlineStatus: Boolean = true,
    showReadReceipts: Boolean = true,
    statusVisible: Boolean = true,
    onUpdatePrivacy: (showOnlineStatus: Boolean, showReadReceipts: Boolean) -> Unit = { _, _ -> },
    onStatusVisibleChange: (Boolean) -> Unit = {},
    // Status-Erlaubte-Kontakte
    contacts: List<ContactEntity> = emptyList(),
    statusPermittedIds: List<String> = emptyList(),
    onStatusPermittedChange: (List<String>) -> Unit = {},
    // Lesebestätigung erst nach Antwort
    readReceiptAfterReply: Boolean = false,
    onReadReceiptAfterReplyChange: (Boolean) -> Unit = {},
    /** Wenn true: Onboarding-Sprechblase anzeigen */
    showOnboardingSpeechBubble: Boolean = false,
    onOnboardingSpeechBubbleDismiss: (() -> Unit)? = null,
    // App-Sperre
    appLockBiometricEnabled: Boolean = false,
    hasAppLockPin: Boolean = false,
    onSaveAppLockPin: (String) -> Unit = {},
    onClearAppLockPin: () -> Unit = {},
    onSetAppLockBiometricEnabled: (Boolean) -> Unit = {},
    // Kontakte-App-Integration ("Verbundene Apps")
    contactsAppIntegration: Boolean = false,
    onContactsAppIntegrationChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var onlineStatusEnabled by remember(showOnlineStatus) { mutableStateOf(showOnlineStatus) }
    var readReceiptsEnabled by remember(showReadReceipts) { mutableStateOf(showReadReceipts) }
    var statusVisibleEnabled by remember(statusVisible) { mutableStateOf(statusVisible) }
    var readReceiptAfterReplyEnabled by remember(readReceiptAfterReply) { mutableStateOf(readReceiptAfterReply) }
    var showContactPickerDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var biometricLockEnabled by remember(appLockBiometricEnabled) { mutableStateOf(appLockBiometricEnabled) }
    var pinSet by remember(hasAppLockPin) { mutableStateOf(hasAppLockPin) }
    var contactsIntegrationEnabled by remember(contactsAppIntegration) { mutableStateOf(contactsAppIntegration) }

    // WRITE_CONTACTS wird benötigt, um Lethe als "Verbundene App" bei jedem Kontakt einzutragen.
    val writeContactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactsIntegrationEnabled = true
            onContactsAppIntegrationChange(true)
        }
    }

    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetPinDialog = false
                pinInput = ""
                pinConfirm = ""
                pinError = null
            },
            title = { Text("PIN festlegen", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Der PIN wird als Fallback verwendet, wenn Biometrie fehlschlägt. Mindestens 4, maximal 8 Stellen.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("PIN (4–8 Ziffern)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pinConfirm = it },
                        label = { Text("PIN bestätigen") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (pinError != null) {
                        Text(pinError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pinInput.length < 4 -> pinError = "PIN muss mindestens 4 Stellen haben."
                        pinInput != pinConfirm -> pinError = "PINs stimmen nicht überein."
                        else -> {
                            onSaveAppLockPin(pinInput)
                            pinSet = true
                            showSetPinDialog = false
                            pinInput = ""
                            pinConfirm = ""
                            pinError = null
                        }
                    }
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetPinDialog = false
                    pinInput = ""
                    pinConfirm = ""
                    pinError = null
                }) { Text("Abbrechen") }
            }
        )
    }

    if (showContactPickerDialog) {
        StatusContactPickerDialog(
            contacts = contacts,
            selectedIds = statusPermittedIds,
            onDismiss = { showContactPickerDialog = false },
            onConfirm = { selectedIds ->
                onStatusPermittedChange(selectedIds)
                showContactPickerDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.privacy_back))
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
            // ─── App sichern ───────────────────────────────────────────────────
            SettingsSection(title = "App sichern") {
                SettingsItem(
                    icon = Icons.Default.Fingerprint,
                    title = "App sichern per Fingerabdruck / Gesichtserkennung",
                    subtitle = if (biometricLockEnabled) "Aktiv – App wird beim Öffnen gesperrt"
                               else if (!pinSet) "Zuerst einen PIN festlegen"
                               else "Deaktiviert"
                ) {
                    Switch(
                        checked = biometricLockEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !pinSet) {
                                showSetPinDialog = true
                            } else {
                                biometricLockEnabled = checked
                                onSetAppLockBiometricEnabled(checked)
                            }
                        },
                        enabled = pinSet || biometricLockEnabled
                    )
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.Pin,
                    title = "PIN festlegen",
                    subtitle = if (pinSet) "PIN ist gesetzt – tippe zum Ändern oder Entfernen" else "Noch kein PIN gesetzt"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pinSet) {
                            TextButton(onClick = {
                                biometricLockEnabled = false
                                onSetAppLockBiometricEnabled(false)
                                onClearAppLockPin()
                                pinSet = false
                            }) { Text("Entfernen") }
                        }
                        TextButton(onClick = { showSetPinDialog = true }) {
                            Text(if (pinSet) "Ändern" else "Festlegen")
                        }
                    }
                }
            }

            // ─── Privatsphäre ──────────────────────────────────────────────────
            SettingsSection(title = stringResource(R.string.privacy_section)) {
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
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.privacy_status_visible_title),
                    subtitle = if (statusVisibleEnabled) stringResource(R.string.privacy_status_visible_enabled)
                    else stringResource(R.string.privacy_status_visible_disabled)
                ) {
                    Switch(
                        checked = statusVisibleEnabled,
                        onCheckedChange = { checked ->
                            statusVisibleEnabled = checked
                            onStatusVisibleChange(checked)
                        }
                    )
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    title = stringResource(R.string.privacy_read_receipt_after_reply_title),
                    subtitle = if (readReceiptAfterReplyEnabled) stringResource(R.string.privacy_read_receipt_after_reply_enabled)
                    else stringResource(R.string.privacy_read_receipt_after_reply_disabled)
                ) {
                    Switch(
                        checked = readReceiptAfterReplyEnabled,
                        onCheckedChange = { checked ->
                            readReceiptAfterReplyEnabled = checked
                            onReadReceiptAfterReplyChange(checked)
                        }
                    )
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.Group,
                    title = stringResource(R.string.privacy_status_permitted_title),
                    subtitle = when {
                        statusPermittedIds.isEmpty() -> stringResource(R.string.privacy_status_permitted_all)
                        statusPermittedIds.size == 1 -> stringResource(R.string.privacy_status_permitted_count_one)
                        else -> stringResource(R.string.privacy_status_permitted_count, statusPermittedIds.size)
                    }
                ) {
                    TextButton(onClick = { showContactPickerDialog = true }) {
                        Text(stringResource(R.string.privacy_status_change))
                    }
                }
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.ContactPhone,
                    title = stringResource(R.string.privacy_contacts_integration_title),
                    subtitle = if (contactsIntegrationEnabled)
                        stringResource(R.string.privacy_contacts_integration_enabled)
                    else
                        stringResource(R.string.privacy_contacts_integration_disabled)
                ) {
                    Switch(
                        checked = contactsIntegrationEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.WRITE_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    contactsIntegrationEnabled = true
                                    onContactsAppIntegrationChange(true)
                                } else {
                                    // Schalter erst nach erteilter Berechtigung umlegen (im Launcher-Callback).
                                    writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                                }
                            } else {
                                contactsIntegrationEnabled = false
                                onContactsAppIntegrationChange(false)
                            }
                        }
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.privacy_legal_section)) {
                SettingsItem(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.privacy_privacy_policy),
                    subtitle = stringResource(R.string.privacy_policy_subtitle)
                ) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://letheapp.de/datenschutz.html"))
                        )
                    }) {
                        Text(stringResource(R.string.privacy_policy_open))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Onboarding-Sprechblase
    if (showOnboardingSpeechBubble && onOnboardingSpeechBubbleDismiss != null) {
        androidx.compose.ui.window.Popup(
            alignment = androidx.compose.ui.Alignment.Center,
            properties = androidx.compose.ui.window.PopupProperties(focusable = true)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = {}
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(8.dp),
                    modifier = Modifier.padding(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_tip_privacy_speech_title),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_tip_privacy_speech_body),
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onOnboardingSpeechBubbleDismiss) {
                            Text(stringResource(R.string.onboarding_tip_privacy_speech_btn))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusContactPickerDialog(
    contacts: List<ContactEntity>,
    selectedIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selected = remember(selectedIds) { mutableStateListOf<String>().also { it.addAll(selectedIds) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_status_contact_picker_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    stringResource(R.string.privacy_status_contact_picker_text),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                if (contacts.isEmpty()) {
                    Text(
                        stringResource(R.string.privacy_status_no_contacts),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = selected.size == contacts.size,
                            onCheckedChange = { checked ->
                                selected.clear()
                                if (checked) selected.addAll(contacts.map { it.userId })
                            }
                        )
                        Text(stringResource(R.string.privacy_status_select_all), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(contacts) { contact ->
                            val isChecked = contact.userId in selected
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) selected.add(contact.userId)
                                        else selected.remove(contact.userId)
                                    }
                                )
                                Column {
                                    Text(
                                        contact.username ?: contact.fakeNumber,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        contact.fakeNumber,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected.toList()) }) {
                Text(
                    if (selected.isEmpty()) stringResource(R.string.privacy_status_allow_all)
                    else stringResource(R.string.privacy_status_confirm, selected.size)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        }
    )
}
