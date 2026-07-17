package com.securechat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.ui.MainViewModel

/**
 * Ankündigungsscreen für das Lethe Team.
 * Nur für Administratoren zugänglich.
 * Ermöglicht das Senden von Nachrichten oder Benachrichtigungen an Nutzergruppen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetheTeamAnnouncementScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val neonYellow = Color(0xFFA8A800)

    // Nachrichtentyp: "message" oder "notification"
    var messageType by remember { mutableStateOf("message") }

    // Zielgruppe
    var targetType by remember { mutableStateOf("all") }
    var specificUserId by remember { mutableStateOf("") }

    // Nachrichteninhalt
    var content by remember { mutableStateOf("") }

    // Status
    var isSending by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val targetOptions = listOf(
        "all" to "Alle Nutzer",
        "specific" to "Bestimmter Nutzer",
        "creators" to "Nur Creator",
        "moderators" to "Nur Moderatoren",
        "admins" to "Nur Admins"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = neonYellow,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Lethe Team Ankündigungen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Info-Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = neonYellow.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = neonYellow,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Offizieller Lethe Team Kanal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = neonYellow
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Nachrichten erscheinen bei Nutzern als Chat-Nachricht vom Lethe Team. " +
                            "Dieser Kanal ist unverschlüsselt und dient nur zur Information. " +
                            "Nutzer können nicht antworten.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Art: Nachricht oder Benachrichtigung
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Art der Mitteilung",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = messageType == "message",
                            onClick = { messageType = "message" },
                            label = { Text("Chat-Nachricht") },
                            leadingIcon = if (messageType == "message") {
                                { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = messageType == "notification",
                            onClick = { messageType = "notification" },
                            label = { Text("Nur Benachrichtigung") },
                            leadingIcon = if (messageType == "notification") {
                                { Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Zielgruppe
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Zielgruppe",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    targetOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = targetType == key,
                                onClick = { targetType = key }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }

                    // User-ID Eingabe bei "specific"
                    AnimatedVisibility(visible = targetType == "specific") {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = specificUserId,
                                onValueChange = { specificUserId = it },
                                label = { Text("Nutzer-ID (UUID)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nachricht eingeben
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Nachricht",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Nachricht eingeben…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 10
                    )
                    Text(
                        "${content.length} / 4000",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ergebnis-Feedback
            resultMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            Color(0xFF1B5E20).copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(msg, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Senden-Button
            Button(
                onClick = {
                    if (content.isBlank() || isSending) return@Button
                    isSending = true
                    resultMessage = null
                    viewModel.adminSendAnnouncement(
                        targetType = targetType,
                        targetUserId = if (targetType == "specific") specificUserId.trim().ifBlank { null } else null,
                        content = content.trim(),
                        isNotification = messageType == "notification",
                        onSuccess = { count ->
                            isSending = false
                            isError = false
                            resultMessage = "✅ Erfolgreich an $count Nutzer gesendet."
                            content = ""
                        },
                        onError = { err ->
                            isSending = false
                            isError = true
                            resultMessage = "❌ Fehler: $err"
                        }
                    )
                },
                enabled = content.isNotBlank() && !isSending &&
                        (targetType != "specific" || specificUserId.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = neonYellow)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isSending) "Wird gesendet…" else "Ankündigung senden",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
