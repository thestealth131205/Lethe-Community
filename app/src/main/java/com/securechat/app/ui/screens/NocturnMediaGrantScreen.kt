package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.data.network.NocturnMediaGrantInfo
import com.securechat.app.ui.MainViewModel

/**
 * Vollbild-Freigabe-Screen: wird geöffnet wenn NocTurn (Wecker-App) eine media:read-Kopplung
 * anfragt, um Weckertöne aus der persönlichen Lethe-Musikbibliothek lesen zu dürfen.
 *
 * Bewusst KEIN Passwort/Biometrie-Gate wie bei [DeviceAuthRequestScreen]: die Anfrage selbst
 * wurde bereits mit dem Lethe-Passwort auf dem NocTurn-Gerät ausgelöst (verhindert Spam-Pushes),
 * hier wird nur noch der enge Lese-Scope bestätigt oder abgelehnt — kein Login, kein
 * UMK-/Schlüssel-Zugriff wird dabei jemals erteilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NocturnMediaGrantScreen(
    requestId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var info by remember { mutableStateOf<NocturnMediaGrantInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(requestId) {
        info = viewModel.fetchNocturnMediaGrantInfo(requestId)
        loading = false
        if (info == null) loadError = "Anfrage nicht gefunden oder abgelaufen."
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("NocTurn-Medienzugriff") })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                done -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Erledigt", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text("Zurück") }
                    }
                }
                loadError != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(loadError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text("Zurück") }
                    }
                }
                info != null -> {
                    val i = info!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = i.deviceName ?: "NocTurn Wecker",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "möchte deine Weckertöne aus deiner Lethe-Musikbibliothek lesen und " +
                                "abspielen dürfen. Dein Konto, deine Chats und dein Passwort bleiben davon " +
                                "komplett unberührt — die Freigabe lässt sich jederzeit in \"Bekannte Geräte\" " +
                                "wieder widerrufen.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(i.ipAddress ?: "Unbekannte IP")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(i.approxLocation ?: "Unbekannter Standort")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                submitting = true
                                viewModel.approveNocturnMediaGrant(requestId) { _, _ ->
                                    submitting = false
                                    done = true
                                }
                            },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Text("Freigeben", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                submitting = true
                                viewModel.denyNocturnMediaGrant(requestId) { _ ->
                                    submitting = false
                                    done = true
                                }
                            },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ablehnen", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
