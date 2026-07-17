package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.data.network.MeshPeer
import com.securechat.app.data.network.MeshTransport
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecentralizedModeScreen(
    onNavigateBack: () -> Unit,
    decentralizedModeEnabled: Boolean,
    onDecentralizedModeChange: (Boolean) -> Unit,
    meshActive: Boolean,
    discoveredPeers: List<MeshPeer>,
    meshStatus: String?,
    myPeerId: String,
    onScanPeers: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dezentrales Messaging") },
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

            // Info-Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Im Dezentral-Modus kommuniziert die App direkt mit anderen Nutzern " +
                               "in der Nähe über Bluetooth und WLAN – ohne Server. " +
                               "Kein Internet erforderlich.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dezentral-Modus Toggle
            SettingsSection(title = "Dezentral-Modus") {
                SettingsItem(
                    icon = if (decentralizedModeEnabled) Icons.Default.WifiOff else Icons.Default.Wifi,
                    title = "Dezentral-Modus",
                    subtitle = if (decentralizedModeEnabled)
                        "Aktiv – direkte Peer-to-Peer-Verbindung"
                    else
                        "Inaktiv – normaler Server-Betrieb"
                ) {
                    Switch(
                        checked = decentralizedModeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) showConfirmDialog = true
                            else showDisableDialog = true
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Einschränkungen
            if (decentralizedModeEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Einschränkungen im Dezentral-Modus:",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "Bildversand deaktiviert",
                            "Videoversand deaktiviert",
                            "Sprachnachrichten max. 10 Sekunden"
                        ).forEach { restriction ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    restriction,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // Mesh-Status und Peers
            SettingsSection(title = "Mesh-Netzwerk") {
                Column(modifier = Modifier.padding(16.dp)) {

                    // Peer-ID
                    Text(
                        text = "Meine Peer-ID:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = myPeerId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(12.dp))

                    // Status
                    if (meshStatus != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (meshActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (meshActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = meshStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Scan-Button
                    if (decentralizedModeEnabled) {
                        OutlinedButton(
                            onClick = onScanPeers,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nach Peers suchen", fontSize = 13.sp)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Peers
                        if (discoveredPeers.isEmpty()) {
                            Text(
                                "Keine Peers in der Nähe gefunden.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            Text(
                                "${discoveredPeers.size} Peer(s) gefunden:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(4.dp))
                            discoveredPeers.forEach { peer ->
                                PeerItem(peer = peer)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Dialog: Dezentral-Modus aktivieren
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Default.Hub, contentDescription = null) },
            title = { Text("Dezentral-Modus aktivieren?") },
            text = {
                Text(
                    "Im Dezentral-Modus kommuniziert die App direkt mit anderen Nutzern in der Nähe " +
                    "über Bluetooth und WLAN. Bild- und Videoversand sind deaktiviert, " +
                    "Sprachnachrichten werden auf 10 Sekunden begrenzt."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDecentralizedModeChange(true)
                    showConfirmDialog = false
                }) { Text("Aktivieren") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // Dialog: Dezentral-Modus deaktivieren
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            icon = { Icon(Icons.Default.WifiOff, contentDescription = null) },
            title = { Text("Dezentral-Modus deaktivieren?") },
            text = { Text("Die App verbindet sich wieder mit dem Lethe-Server. Alle Einschränkungen werden aufgehoben.") },
            confirmButton = {
                TextButton(onClick = {
                    onDecentralizedModeChange(false)
                    showDisableDialog = false
                }) { Text("Deaktivieren") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun PeerItem(peer: MeshPeer) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (peer.transport) {
                    MeshTransport.BLUETOOTH_LE -> Icons.Default.Bluetooth
                    MeshTransport.WIFI_DIRECT -> Icons.Default.Wifi
                    MeshTransport.UNKNOWN -> Icons.Default.DevicesOther
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(
                    peer.peerId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            if (peer.rssi != -1 && peer.transport == MeshTransport.BLUETOOTH_LE) {
                Text(
                    "${peer.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
