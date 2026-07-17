package com.lethe.mediaplayer.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Verwaltung der FriendsMix-Gruppe: zeigt alle akzeptierten Kontakte (nur Benutzername +
 * Profilbild). Durch Antippen wird ein Kontakt zur Gruppe hinzugefügt oder entfernt. Der
 * FriendsMix wird serverseitig aus den Favoriten aller Gruppenmitglieder zusammengestellt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsGroupScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val contacts by vm.friendsContacts.collectAsState()
    val loading by vm.friendsContactsLoading.collectAsState()

    LaunchedEffect(Unit) { vm.loadFriendsContacts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FriendsMix-Gruppe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading && contacts.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            contacts.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize()) {
                Text(
                    "Keine Kontakte gefunden.\nFüge in Lethe Kontakte hinzu, um einen FriendsMix zu erstellen.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    Text(
                        "Wähle Kontakte für deinen FriendsMix. Von jedem Mitglied fließen der neueste " +
                            "und bis zu zwei zufällige Lieblingssongs in eine gemeinsame, sich stetig " +
                            "anpassende Playlist ein.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                items(contacts, key = { it.userId }) { contact ->
                    FriendRow(
                        name = contact.name,
                        imageUrl = contact.profileImageUrl,
                        inGroup = contact.inGroup,
                        onToggle = { vm.setFriendInGroup(contact.userId, !contact.inGroup) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    name: String,
    imageUrl: String?,
    inGroup: Boolean,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(avatarBrush(name)),
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
                Text(
                    avatarInitials(name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            name,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (inGroup) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (inGroup) "In Gruppe" else "Nicht in Gruppe",
            tint = if (inGroup) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun avatarInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

private val AvatarGradients = listOf(
    Color(0xFF7C4DFF) to Color(0xFFE91E63),
    Color(0xFF2196F3) to Color(0xFF21D4FD),
    Color(0xFF11998E) to Color(0xFF38EF7D),
    Color(0xFFF7971E) to Color(0xFFFFD200),
    Color(0xFFEE0979) to Color(0xFFFF6A00),
    Color(0xFF8E2DE2) to Color(0xFF4A00E0)
)

private fun avatarBrush(key: String): androidx.compose.ui.graphics.Brush {
    val idx = Math.floorMod(key.hashCode(), AvatarGradients.size)
    val (a, b) = AvatarGradients[idx]
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(a, b))
}
