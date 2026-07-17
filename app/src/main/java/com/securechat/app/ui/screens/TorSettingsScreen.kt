package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.data.network.TorMode
import com.securechat.app.ui.theme.topBarTitleColor
import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Einstellungsscreen für Tor Hidden Service / Onion-Routing.
 *
 * Tor ist direkt in die App eingebaut (Guardian Project tor-android).
 * Kein externes Orbot erforderlich – nur den Schalter umlegen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorSettingsScreen(
    onNavigateBack: () -> Unit,
    torMode: TorMode,
    onionAddress: String,
    isOrbotInstalled: Boolean = false,
    onTorModeChange: (TorMode) -> Unit,
    onOnionAddressChange: (String) -> Unit,
    onFetchOnionAddress: () -> Unit = {},
    homeServerUrl: String = "",
    onHomeServerUrlChange: (String) -> Unit = {}
) {
    var onionInput by remember(onionAddress) { mutableStateOf(onionAddress) }
    var homeInput by remember(homeServerUrl) { mutableStateOf(homeServerUrl) }
    var showConfirmDisableDialog by remember { mutableStateOf(false) }

    val torEnabled = torMode == TorMode.TOR_ONLY

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // "UNKNOWN" | "ON" | "OFF" | "STARTING" | "STOPPING"
    var torConnectionStatus by remember { mutableStateOf("UNKNOWN") }

    // Netzwerk-Statistiken
    var netRxTotal by remember { mutableStateOf(0L) }
    var netTxTotal by remember { mutableStateOf(0L) }
    var netDownloadSpeed by remember { mutableStateOf(0L) }
    var netUploadSpeed by remember { mutableStateOf(0L) }
    var netLatencyMs by remember { mutableStateOf(-1) }

    fun checkTorProxy() {
        coroutineScope.launch {
            val reachable = withContext(Dispatchers.IO) {
                try {
                    java.net.Socket().apply {
                        connect(java.net.InetSocketAddress("127.0.0.1", 9050), 600)
                        close()
                    }
                    true
                } catch (e: Exception) { false }
            }
            torConnectionStatus = if (reachable) "ON" else "OFF"
        }
    }

    DisposableEffect(lifecycleOwner) {
        // BroadcastReceiver für Tor-Statusmeldungen vom eingebetteten TorService
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                val status = intent.getStringExtra("org.torproject.android.intent.extra.STATUS")
                if (status != null) torConnectionStatus = status
            }
        }
        val filter = android.content.IntentFilter("org.torproject.android.intent.action.STATUS")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkTorProxy()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initialer Check beim ersten Compose
    LaunchedEffect(Unit) { checkTorProxy() }

    // Netzwerkstatistiken sekündlich aktualisieren (nur wenn Tor verbunden)
    LaunchedEffect(torConnectionStatus) {
        if (torConnectionStatus != "ON") return@LaunchedEffect
        val uid = Process.myUid()
        var lastRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
        var lastTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
        while (true) {
            delay(1_000L)
            val nowRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
            val nowTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
            netRxTotal = nowRx
            netTxTotal = nowTx
            netDownloadSpeed = (nowRx - lastRx).coerceAtLeast(0L)
            netUploadSpeed = (nowTx - lastTx).coerceAtLeast(0L)
            lastRx = nowRx
            lastTx = nowTx
            // Latenz über TCP-Connect zum Tor-SOCKS5-Port messen
            netLatencyMs = withContext(Dispatchers.IO) {
                val t0 = System.currentTimeMillis()
                try {
                    java.net.Socket().apply {
                        connect(java.net.InetSocketAddress("127.0.0.1", 9050), 2_000)
                        close()
                    }
                    (System.currentTimeMillis() - t0).toInt()
                } catch (e: Exception) { -1 }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tor & Onion-Routing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
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

            // --- Info-Banner ---
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
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Im Tor-Modus wird der gesamte App-Verkehr über das Tor-Netzwerk geleitet. " +
                               "Die Verbindung läuft direkt zum .onion-Server – ohne dass deine IP-Adresse " +
                               "sichtbar ist. Tor ist direkt in die App eingebaut – kein externes App nötig.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Tor-Modus Toggle ---
            SettingsSection(title = "Tor-Netzwerk") {
                SettingsItem(
                    icon = if (torEnabled) Icons.Default.VpnKey else Icons.Default.VpnKeyOff,
                    title = "Tor-Modus",
                    subtitle = if (torEnabled) "Aktiv – Traffic läuft via Orbot (.onion)" else "Inaktiv – Direktverbindung (Clearnet)"
                ) {
                    Switch(
                        checked = torEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                onTorModeChange(TorMode.TOR_ONLY)
                            } else {
                                showConfirmDisableDialog = true
                            }
                        }
                    )
                }

                HorizontalDivider()

                // Tor-Verbindungsstatus (eingebetteter Tor-Client)
                val torStatusSubtitle = when (torConnectionStatus) {
                    "ON"       -> "Verbunden – Tor-Netzwerk aktiv"
                    "STARTING" -> "Verbindet mit Tor..."
                    "STOPPING" -> "Tor wird beendet..."
                    "OFF"      -> if (torEnabled) "Tor startet..." else "Tor nicht aktiv"
                    else       -> "Bereit"
                }
                SettingsItem(
                    icon = when (torConnectionStatus) {
                        "ON"       -> Icons.Default.CheckCircle
                        "STARTING" -> Icons.Default.Sync
                        else       -> if (torEnabled) Icons.Default.Sync else Icons.Default.VpnKeyOff
                    },
                    title = "Tor-Verbindung",
                    subtitle = torStatusSubtitle
                ) {
                    when (torConnectionStatus) {
                        "ON" -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        "STARTING" -> CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        else -> if (torEnabled) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        ) else Icon(
                            Icons.Default.Circle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Netzwerk-Statistiken (nur sichtbar wenn Tor verbunden) ---
            if (torConnectionStatus == "ON") {
                SettingsSection(title = "Netzwerk-Statistiken") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Geschwindigkeit + Latenz
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TorStatTile(
                                icon = Icons.Default.ArrowDownward,
                                label = "Download",
                                value = formatTorSpeed(netDownloadSpeed),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            TorStatTile(
                                icon = Icons.Default.ArrowUpward,
                                label = "Upload",
                                value = formatTorSpeed(netUploadSpeed),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            TorStatTile(
                                icon = Icons.Default.Timer,
                                label = "Latenz",
                                value = if (netLatencyMs >= 0) "${netLatencyMs} ms" else "–",
                                tint = when {
                                    netLatencyMs < 0    -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    netLatencyMs < 500  -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                    netLatencyMs < 1500 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                                    else                -> androidx.compose.ui.graphics.Color(0xFFF44336)
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        // Gesamtvolumen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TorStatTile(
                                icon = Icons.Default.Download,
                                label = "Gesamt ↓",
                                value = formatTorBytes(netRxTotal),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            TorStatTile(
                                icon = Icons.Default.Upload,
                                label = "Gesamt ↑",
                                value = formatTorBytes(netTxTotal),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // --- .onion-Adresse ---
            SettingsSection(title = "Server .onion-Adresse") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Die .onion-Adresse wird beim App-Start automatisch vom Server geladen. " +
                               "Du kannst sie hier auch manuell überschreiben oder erneut laden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = onionInput,
                        onValueChange = { onionInput = it },
                        label = { Text(".onion-Adresse") },
                        placeholder = { Text("beispiel1234567890ab.onion") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null)
                        },
                        trailingIcon = {
                            if (onionInput != onionAddress) {
                                IconButton(onClick = { onOnionAddressChange(onionInput.trim()) }) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "Speichern",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onFetchOnionAddress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Vom Server laden", fontSize = 13.sp)
                        }
                        if (onionInput != onionAddress) {
                            Button(
                                onClick = { onOnionAddressChange(onionInput.trim()) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Speichern", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Backup / Home-Server (Load Balancing) ---
            SettingsSection(title = "Backup-Server (Load Balancing)") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Optionaler Zweit-Server (z.B. Home-Server). " +
                               "Die App wechselt automatisch auf diesen Server, wenn der primäre VPS (letheapp.de) " +
                               "nicht erreichbar ist. Im Tor-Modus wird stattdessen die .onion-Adresse genutzt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = homeInput,
                        onValueChange = { homeInput = it },
                        label = { Text("Backup-Server URL") },
                        placeholder = { Text("https://192.168.1.100:8000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null)
                        },
                        trailingIcon = {
                            if (homeInput != homeServerUrl) {
                                IconButton(onClick = { onHomeServerUrlChange(homeInput.trim()) }) {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "Speichern",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    )
                    if (homeInput != homeServerUrl) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onHomeServerUrlChange(homeInput.trim()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Backup-Server speichern", fontSize = 13.sp)
                        }
                    }
                    if (homeServerUrl.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Aktiv: $homeServerUrl",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- Bestätigungsdialog: Tor deaktivieren
    if (showConfirmDisableDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDisableDialog = false },
            icon = { Icon(Icons.Default.VpnKeyOff, contentDescription = null) },
            title = { Text("Tor deaktivieren?") },
            text = {
                Text(
                    "Wenn du Tor deaktivierst, verbindet sich die App direkt über das Clearnet mit " +
                    "letheapp.de. Deine IP-Adresse kann dann vom Server gesehen werden."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTorModeChange(TorMode.OFF)
                        showConfirmDisableDialog = false
                    }
                ) { Text("Tor deaktivieren") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDisableDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Hilfsfunktionen für Tor-Statistiken
// ---------------------------------------------------------------------------

private fun formatTorBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}

private fun formatTorSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_048_576L -> "%.1f MB/s".format(bytesPerSec / 1_048_576.0)
    bytesPerSec >= 1_024L     -> "%.1f KB/s".format(bytesPerSec / 1_024.0)
    else                      -> "$bytesPerSec B/s"
}

@Composable
private fun TorStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
