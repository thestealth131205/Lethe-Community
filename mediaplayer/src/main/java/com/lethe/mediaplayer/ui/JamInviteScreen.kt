package com.lethe.mediaplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lethe.mediaplayer.data.FriendsMixContactDto

/** Generiert eine QR-Code-Bitmap aus einem String (reines ZXing, keine Google-Play-Services-Abhaengigkeit). */
private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (_: Exception) { null }
}

/**
 * Zeigt den laufenden Jam nach dem Start: QR-Code zum Beitreten, Teilnehmerliste und
 * Kontaktliste (rein informativ – der Beitritt erfolgt bei Freunden über den QR-Scan im
 * "Jam beitreten"-Button, es gibt keinen direkten Einladungsversand).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamInviteScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val jamState by vm.jamState.collectAsState()
    val jamError by vm.jamError.collectAsState()
    val contacts by vm.friendsContacts.collectAsState()
    val contactsLoading by vm.friendsContactsLoading.collectAsState()
    val ownUserId = vm.ownUserId
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        if (jamState == null) vm.startJam()
        vm.loadFriendsContacts()
    }

    val qrContent = jamState?.id?.let { PlayerViewModel.JAM_QR_PREFIX + it }
    val qrBitmap by remember(qrContent) {
        derivedStateOf { qrContent?.let { generateQrBitmap(it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jam") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (jamState != null) {
                        if (jamState?.hostUserId == ownUserId) {
                            IconButton(onClick = { vm.endJam(); onBack() }) {
                                Icon(Icons.Filled.Stop, contentDescription = "Jam beenden")
                            }
                        } else {
                            IconButton(onClick = { vm.leaveJam(); onBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Jam verlassen")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            jamState == null && jamError == null -> Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            jamState == null -> Box(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    jamError ?: "Jam konnte nicht gestartet werden.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                val state = jamState!!
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Freunde einladen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Freunde mit Lethe Media Player können diesen QR-Code über " +
                                    "\"Jam beitreten\" scannen und deiner geteilten Wiedergabeliste beitreten.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(
                                Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = qrBitmap!!.asImageBitmap(),
                                        contentDescription = "Jam-QR-Code",
                                        modifier = Modifier.size(220.dp)
                                    )
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    qrContent?.let { clipboard.setText(AnnotatedString(it)) }
                                }
                            ) {
                                Text(
                                    "Code kopieren",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Teilnehmer (${state.participants.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    items(state.participants, key = { it.userId }) { p ->
                        ParticipantRow(name = p.name, imageUrl = p.profileImageUrl, isHost = p.userId == state.hostUserId)
                    }

                    item {
                        Text(
                            "Deine Kontakte",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    if (contactsLoading && contacts.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (contacts.isEmpty()) {
                        item {
                            Text(
                                "Keine Kontakte gefunden.",
                                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(contacts, key = { it.userId }) { contact ->
                            ContactInviteRow(contact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(name: String, imageUrl: String?, isHost: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(avatarBrush(name)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(avatarInitials(name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (isHost) {
            Text("Host", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ContactInviteRow(contact: FriendsMixContactDto) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(avatarBrush(contact.name)),
            contentAlignment = Alignment.Center
        ) {
            if (!contact.profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = contact.profileImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(avatarInitials(contact.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
