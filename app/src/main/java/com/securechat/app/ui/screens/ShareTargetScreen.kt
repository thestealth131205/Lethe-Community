package com.securechat.app.ui.screens

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.rememberAsyncImagePainter
import com.securechat.app.data.local.ContactEntity
import com.securechat.app.data.local.GroupEntity
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.PendingShare
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetScreen(
    viewModel: MainViewModel,
    onNavigateToChat: (String) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToImageEditor: ((Uri, String, String?) -> Unit)? = null,
    onNavigateToStatusCreate: (() -> Unit)? = null
) {
    val contacts by viewModel.contacts.collectAsState(initial = emptyList())
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val pendingShare by viewModel.pendingShare.collectAsState()
    val mostContactedIds by viewModel.mostContactedIds.collectAsState()
    val linkPreview by viewModel.linkPreview.collectAsState()
    val selfNotesLastMsg by viewModel.getLastMessageForChat("self_notes").collectAsState(initial = null)
    val hasSelfNotes = selfNotesLastMsg != null

    // CRASH FIX: Navigation-Ziel merken, dann EINMAL via LaunchedEffect navigieren
    var singleNavigationTarget by remember { mutableStateOf<String?>(null) }
    val hadShare = remember { pendingShare != null }

    LaunchedEffect(pendingShare) {
        if (hadShare && pendingShare == null) {
            val target = singleNavigationTarget
            if (target != null) onNavigateToChat(target)
            else onDismiss()
        }
    }

    // Häufig-kontaktierte Kontakte beim Öffnen laden
    LaunchedEffect(Unit) {
        viewModel.loadMostContactedContacts()
    }

    val share = pendingShare ?: return

    // Bild-Share-Erkennung
    val isImageShare = isImageMimeType(share.mimeType) && (share.uri != null || share.uris?.isNotEmpty() == true)
    val imageUriForShare: Uri? = if (isImageShare) share.uri ?: share.uris?.firstOrNull() else null

    // URL-Erkennung bei Text-Share: automatisch Link-Preview laden
    LaunchedEffect(share.text) {
        if (share.text != null && (share.text.startsWith("http://") || share.text.startsWith("https://"))) {
            viewModel.fetchLinkPreview(share.text.trim())
        }
    }

    // Multi-Select Status
    val selectedIds = remember { mutableStateSetOf<String>() }
    val selectedGroupIds = remember { mutableStateSetOf<String>() }
    var selectedStatus by remember { mutableStateOf(false) }
    var selectedSelfNotes by remember { mutableStateOf(false) }

    val totalSelected = selectedIds.size + selectedGroupIds.size + (if (selectedStatus) 1 else 0) + (if (selectedSelfNotes) 1 else 0)

    fun sendToSelected() {
        // Status-Share: Zur StatusCreationScreen navigieren damit User trimmen kann
        if (selectedStatus && share.uri != null) {
            val mediaType = mimeToMediaType(share.mimeType ?: "")
            val type = if (mediaType == "video") "video" else "image"
            viewModel.setPendingStatusUri(type, share.uri)
            viewModel.clearPendingShare()
            onNavigateToStatusCreate?.invoke()
            return
        }

        // Bild an GENAU EINEN Empfänger → Image Editor öffnen (Zeichnen/Text/Bildunterschrift möglich).
        // Bei MEHREREN Empfängern (Kontakte + Gruppen) fällt der Code unten durch und sendet
        // das unbearbeitete Bild an ALLE ausgewählten Ziele (statt nur an ein einziges).
        if (isImageShare && imageUriForShare != null && totalSelected == 1 && onNavigateToImageEditor != null) {
            val targetId = selectedIds.firstOrNull() ?: selectedGroupIds.firstOrNull() ?: if (selectedSelfNotes) "self_notes" else return
            onNavigateToImageEditor(imageUriForShare, targetId, share.caption)
            return
        }

        val selectedContacts = contacts.filter { it.userId in selectedIds }

        // Einzel-Text/Link-Share zu genau einem Kontakt → Chat öffnen, Text ins Textfeld setzen
        if (share.text != null && selectedContacts.size == 1 && selectedGroupIds.isEmpty() && !selectedSelfNotes) {
            viewModel.setPendingChatText(share.text)
            singleNavigationTarget = selectedContacts[0].userId
            viewModel.clearLinkPreview()
            viewModel.clearPendingShare()
            return
        }

        // Einzel-Text-Share nur zu Eigene Notizen → Chat öffnen, Text ins Textfeld setzen
        if (share.text != null && selectedSelfNotes && selectedContacts.isEmpty() && selectedGroupIds.isEmpty()) {
            viewModel.setPendingChatText(share.text)
            singleNavigationTarget = "self_notes"
            viewModel.clearLinkPreview()
            viewModel.clearPendingShare()
            return
        }

        // Kontakt-Nachrichten senden
        selectedContacts.forEach { contact ->
            when {
                linkPreview != null && share.text != null -> {
                    // Link als link-type senden
                    val json = org.json.JSONObject().apply {
                        put("url", linkPreview!!.url)
                        put("title", linkPreview!!.title)
                        put("description", linkPreview!!.description ?: "")
                        put("image", linkPreview!!.imageUrl ?: "")
                        put("site_name", linkPreview!!.siteName ?: "")
                        if (linkPreview!!.embedType != null) put("embed_type", linkPreview!!.embedType)
                        if (linkPreview!!.embedUrl != null) put("embed_url", linkPreview!!.embedUrl)
                    }
                    viewModel.sendMessage(contact.userId, json.toString(), mediaType = "link")
                }
                share.text != null -> {
                    viewModel.sendTextToContact(contact.userId, share.text)
                }
                share.uris != null -> {
                    share.uris.forEach { uri ->
                        val mediaType = mimeToMediaType(share.mimeType ?: "")
                        if (mediaType == "document") viewModel.sendDocumentMessage(contact.userId, uri, isGroup = false)
                        else viewModel.sendMediaMessage(contact.userId, uri, mediaType)
                    }
                }
                share.uri != null -> {
                    val mediaType = mimeToMediaType(share.mimeType ?: "")
                    if (mediaType == "document") viewModel.sendDocumentMessage(contact.userId, share.uri, isGroup = false)
                    else viewModel.sendMediaMessage(contact.userId, share.uri, mediaType)
                }
            }
        }

        // Gruppen-Nachrichten senden
        selectedGroupIds.forEach { groupId ->
            when {
                share.text != null -> viewModel.sendGroupMessage(groupId, share.text)
                share.uris != null -> share.uris.forEach { uri ->
                    val mediaType = mimeToMediaType(share.mimeType ?: "")
                    if (mediaType == "document") viewModel.sendDocumentMessage(groupId, uri, isGroup = true)
                    else viewModel.sendGroupMediaMessage(groupId, uri, mediaType)
                }
                share.uri != null -> {
                    val mediaType = mimeToMediaType(share.mimeType ?: "")
                    if (mediaType == "document") viewModel.sendDocumentMessage(groupId, share.uri, isGroup = true)
                    else viewModel.sendGroupMediaMessage(groupId, share.uri, mediaType)
                }
            }
        }

        // Eigene Notizen: Medien/Text direkt lokal speichern
        if (selectedSelfNotes) {
            when {
                share.text != null -> viewModel.sendTextToContact("self_notes", share.text)
                share.uris != null -> share.uris.forEach { uri ->
                    val mediaType = mimeToMediaType(share.mimeType ?: "")
                    if (mediaType == "document") viewModel.sendDocumentMessage("self_notes", uri, isGroup = false)
                    else viewModel.sendMediaMessage("self_notes", uri, mediaType)
                }
                share.uri != null -> {
                    val mediaType = mimeToMediaType(share.mimeType ?: "")
                    if (mediaType == "document") viewModel.sendDocumentMessage("self_notes", share.uri, isGroup = false)
                    else viewModel.sendMediaMessage("self_notes", share.uri, mediaType)
                }
            }
        }

        // Navigation-Ziel setzen (nur wenn genau 1 Empfänger)
        singleNavigationTarget = when {
            selectedContacts.size == 1 && selectedGroupIds.isEmpty() && !selectedStatus && !selectedSelfNotes -> selectedContacts[0].userId
            selectedSelfNotes && selectedContacts.isEmpty() && selectedGroupIds.isEmpty() && !selectedStatus -> "self_notes"
            else -> null
        }

        viewModel.clearLinkPreview()
        viewModel.clearPendingShare()  // Löst LaunchedEffect EINMAL aus
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Senden an…",
                        color = topBarTitleColor(),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearLinkPreview()
                        viewModel.clearPendingShare()
                        onDismiss()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Abbrechen",
                            tint = topBarTitleColor()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (isImageShare && imageUriForShare != null) {
                // WhatsApp-ähnliche Bild-Share-Leiste
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // firstTarget: erster ausgewählter Kontakt oder Gruppe
                            val firstTarget = selectedIds.firstOrNull() ?: selectedGroupIds.firstOrNull() ?: if (selectedSelfNotes) "self_notes" else null
                            // Bei genau 1 Ziel → Editor (Zeichnen/Text/Bildunterschrift möglich).
                            // Bei mehreren Zielen → direkt unbearbeitet an ALLE ausgewählten senden.
                            val onSendOrEdit: () -> Unit = {
                                if (totalSelected == 1 && firstTarget != null) {
                                    onNavigateToImageEditor?.invoke(imageUriForShare, firstTarget, share.caption)
                                } else if (totalSelected > 1) {
                                    sendToSelected()
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (firstTarget != null)
                                            Modifier.clickable(onClick = onSendOrEdit)
                                        else Modifier
                                    )
                            ) {
                                AsyncImage(
                                    model = imageUriForShare,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (firstTarget != null && totalSelected == 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            // "Beschreibung hinzufügen" – nur sinnvoll bei genau 1 Ziel (Editor); bei
                            // mehreren Zielen tappable → direkt senden
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .then(
                                        if (firstTarget != null)
                                            Modifier.clickable(onClick = onSendOrEdit)
                                        else Modifier
                                    )
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    if (totalSelected > 1) "An $totalSelected Empfänger senden" else "Beschreibung hinzufügen",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                            // FAB: erscheint wenn ≥1 ausgewählt; zeigt Anzahl wenn >1
                            if (firstTarget != null) {
                                Spacer(Modifier.width(10.dp))
                                androidx.compose.material3.FloatingActionButton(
                                    onClick = onSendOrEdit,
                                    modifier = Modifier.size(48.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    if (totalSelected > 1) {
                                        Text(
                                            text = "$totalSelected",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden")
                                    }
                                }
                            }
                        }
                        // Ausgewählte Namen anzeigen
                        if (totalSelected >= 1) {
                            val label = if (totalSelected == 1) {
                                val id = selectedIds.firstOrNull()
                                when {
                                    id != null -> contacts.find { it.userId == id }?.let { it.username ?: it.fakeNumber }
                                    selectedGroupIds.isNotEmpty() -> groups.find { it.groupId == selectedGroupIds.first() }?.name
                                    selectedSelfNotes -> "Eigene Notizen"
                                    else -> null
                                }
                            } else {
                                "$totalSelected ausgewählt"
                            }
                            if (label != null) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                                )
                            }
                        }
                    }
                }
            } else if (totalSelected > 0) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                    Button(
                        onClick = { sendToSelected() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (totalSelected == 1) "Senden" else "Senden ($totalSelected)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Vorschau-Karte (nur für Nicht-Bild-Shares; Bilder werden in der Bottom-Bar angezeigt)
            if (!isImageShare) {
                item {
                    SharePreviewCard(share = share, linkPreview = linkPreview)
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // --- Status ---
            item {
                SectionHeader("Status")
            }
            item {
                StatusShareItem(
                    isSelected = selectedStatus,
                    onClick = { selectedStatus = !selectedStatus }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 72.dp)
                )
            }

            // --- Häufig kontaktiert (Kontakte + Gruppen, nach Aktivität sortiert) ---
            val frequentContacts = contacts.filter { it.userId in mostContactedIds }
            val frequentGroups = groups.filter { it.groupId in mostContactedIds }
            val hasFrequent = frequentContacts.isNotEmpty() || frequentGroups.isNotEmpty()
            if (hasFrequent) {
                item { SectionHeader("Häufig kontaktiert") }
                // Gemeinsam nach Position in mostContactedIds sortieren
                val frequentItems: List<Any> = (frequentContacts + frequentGroups)
                    .sortedBy { item ->
                        when (item) {
                            is com.securechat.app.data.local.ContactEntity -> mostContactedIds.indexOf(item.userId)
                            is com.securechat.app.data.local.GroupEntity -> mostContactedIds.indexOf(item.groupId)
                            else -> Int.MAX_VALUE
                        }
                    }
                items(frequentItems, key = { item ->
                    when (item) {
                        is com.securechat.app.data.local.ContactEntity -> "freq_c_${item.userId}"
                        is com.securechat.app.data.local.GroupEntity -> "freq_g_${item.groupId}"
                        else -> item.hashCode().toString()
                    }
                }) { item ->
                    when (item) {
                        is com.securechat.app.data.local.ContactEntity -> {
                            val isSelected = item.userId in selectedIds
                            ShareContactItem(
                                contact = item,
                                isSelected = isSelected,
                                showCheckbox = true,
                                onClick = {
                                    if (isSelected) selectedIds.remove(item.userId)
                                    else if (totalSelected < 5) selectedIds.add(item.userId)
                                }
                            )
                        }
                        is com.securechat.app.data.local.GroupEntity -> {
                            val isSelected = item.groupId in selectedGroupIds
                            ShareGroupItem(
                                group = item,
                                isSelected = isSelected,
                                showCheckbox = true,
                                onClick = {
                                    if (isSelected) selectedGroupIds.remove(item.groupId)
                                    else if (totalSelected < 5) selectedGroupIds.add(item.groupId)
                                }
                            )
                        }
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 72.dp)
                    )
                }
            }

            // --- Letzte Chats (Kontakte + Gruppen gemischt, alphabetisch) ---
            item { SectionHeader("Letzte Chats") }
            val recentContacts = contacts.filter { it.userId !in mostContactedIds }
            val recentGroups = groups.filter { it.groupId !in mostContactedIds }
            val recentItems: List<Any> = (recentContacts + recentGroups).sortedWith(
                Comparator { a, b ->
                    val nameA = when (a) {
                        is com.securechat.app.data.local.ContactEntity -> a.username ?: a.fakeNumber
                        is com.securechat.app.data.local.GroupEntity -> a.name
                        else -> ""
                    }
                    val nameB = when (b) {
                        is com.securechat.app.data.local.ContactEntity -> b.username ?: b.fakeNumber
                        is com.securechat.app.data.local.GroupEntity -> b.name
                        else -> ""
                    }
                    nameA.compareTo(nameB, ignoreCase = true)
                }
            )
            items(recentItems, key = { item ->
                when (item) {
                    is com.securechat.app.data.local.ContactEntity -> "rec_c_${item.userId}"
                    is com.securechat.app.data.local.GroupEntity -> "rec_g_${item.groupId}"
                    else -> item.hashCode().toString()
                }
            }) { item ->
                when (item) {
                    is com.securechat.app.data.local.ContactEntity -> {
                        val isSelected = item.userId in selectedIds
                        ShareContactItem(
                            contact = item,
                            isSelected = isSelected,
                            showCheckbox = true,
                            onClick = {
                                if (isSelected) selectedIds.remove(item.userId)
                                else if (totalSelected < 5) selectedIds.add(item.userId)
                            }
                        )
                    }
                    is com.securechat.app.data.local.GroupEntity -> {
                        val isSelected = item.groupId in selectedGroupIds
                        ShareGroupItem(
                            group = item,
                            isSelected = isSelected,
                            showCheckbox = true,
                            onClick = {
                                if (isSelected) selectedGroupIds.remove(item.groupId)
                                else if (totalSelected < 5) selectedGroupIds.add(item.groupId)
                            }
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 72.dp)
                )
            }

            // --- Eigene Notizen (immer am Ende, sobald mind. eine Notiz existiert) ---
            if (hasSelfNotes) {
                item {
                    SelfNotesShareItem(
                        isSelected = selectedSelfNotes,
                        onClick = { selectedSelfNotes = !selectedSelfNotes }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun StatusShareItem(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else Color.Transparent,
        label = "statusBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Mein Status", fontWeight = FontWeight.Medium, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Text("In meinen Status teilen", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        CheckCircle(isSelected = isSelected)
    }
}

@Composable
private fun ShareGroupItem(
    group: GroupEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    showCheckbox: Boolean = true
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else Color.Transparent,
        label = "groupBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (!group.groupImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = group.groupImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Group, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, fontWeight = FontWeight.Medium, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text("${group.memberCount} Mitglieder", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showCheckbox) {
            Spacer(Modifier.width(8.dp))
            CheckCircle(isSelected = isSelected)
        }
    }
}

@Composable
private fun SharePreviewCard(share: PendingShare, linkPreview: com.securechat.app.ui.LinkPreviewData?) {
    // Link-Preview-Karte falls URL-Share
    if (linkPreview != null) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (linkPreview.thumbnailUrl != null || linkPreview.imageUrl != null) {
                    AsyncImage(
                        model = linkPreview.thumbnailUrl ?: linkPreview.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (linkPreview.embedType == "youtube") {
                        Text("YouTube", fontSize = 11.sp, color = Color(0xFFFF0000),
                            fontWeight = FontWeight.Bold)
                    } else if (linkPreview.siteName != null) {
                        Text(linkPreview.siteName, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Text(linkPreview.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        return
    }

    // Standard-Vorschau-Karte
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, label) = when {
                share.text != null                            -> Icons.Default.TextFields to "Text"
                isImageMimeType(share.mimeType)               -> Icons.Default.Image to "Bild"
                share.mimeType?.startsWith("video/") == true -> Icons.Default.PlayCircle to "Video"
                share.mimeType?.startsWith("audio/") == true -> Icons.Default.AudioFile to "Audio"
                share.uris != null -> Icons.Default.ContentCopy to "${share.uris.size} Dateien"
                else -> Icons.Default.AttachFile to "Datei"
            }
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                if (share.text != null) {
                    Text(
                        text = share.text.take(80) + if (share.text.length > 80) "…" else "",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (share.uris != null) {
                    Text("${share.uris.size} Elemente", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ShareContactItem(
    contact: ContactEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    showCheckbox: Boolean = true
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else Color.Transparent,
        label = "selectionBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (!contact.profileImageUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(contact.profileImageUrl),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = (contact.username ?: contact.fakeNumber).take(1).uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.username ?: contact.fakeNumber,
                fontWeight = FontWeight.Medium, fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (contact.username != null) {
                Text(contact.fakeNumber, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }

        if (showCheckbox) {
            Spacer(Modifier.width(8.dp))
            CheckCircle(isSelected = isSelected)
        }
    }
}

@Composable
private fun CheckCircle(isSelected: Boolean) {
    Box(
        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(50))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (isSelected) 0.dp else 1.5.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SelfNotesShareItem(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else Color.Transparent,
        label = "selfNotesBg"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Eigene Notizen",
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Nur für mich sichtbar",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        CheckCircle(isSelected = isSelected)
    }
}

/** Gibt true zurück für alle Bild-MIME-Typen inkl. RAW-Formate (image/x-*). */
internal fun isImageMimeType(mimeType: String?): Boolean =
    mimeType != null && mimeType.startsWith("image/")

internal fun mimeToMediaType(mimeType: String): String = when {
    isImageMimeType(mimeType)      -> "image"
    mimeType.startsWith("video/") -> "video"
    mimeType.startsWith("audio/") -> "audio"
    else -> "document"
}
