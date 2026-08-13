package com.securechat.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.securechat.app.R
import com.securechat.app.data.network.BotPublicResponse
import com.securechat.app.ui.components.PhoneInputField
import com.securechat.app.ui.components.buildE164

/**
 * Kontakt-hinzufügen-Dialog mit drei Tabs:
 *  1. Telefonnummer-Suche
 *  2. Direkteingabe der Lethe-ID (fake_number)
 *  3. Bot-Suche
 */
@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAddContact: (fakeNumber: String, name: String) -> Unit,
    onLookupByPhone: ((phoneE164: String, onResult: (fakeNumber: String?) -> Unit) -> Unit)? = null,
    onSearchBots: ((query: String, onResult: (List<BotPublicResponse>) -> Unit) -> Unit)? = null,
    /** Lädt die offiziellen Lethe-Bots (z.B. Lethe Assistant) — für alle User ohne Suche sichtbar. */
    onLoadFeaturedBots: ((onResult: (List<BotPublicResponse>) -> Unit) -> Unit)? = null,
    /** Sendet ein Nearby-Like per Benutzername (NearbyID). */
    onSendNearbyLike: ((username: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    isAdmin: Boolean = false,
    /** Wenn true: Telefonnummer-Feld wird hervorgehoben (Onboarding-Schritt). */
    isOnboardingPhoneStep: Boolean = false,
    /** Wird aufgerufen sobald der Nutzer eine Kontaktanfrage abschickt. */
    onContactRequestSent: (() -> Unit)? = null
) {
    var countryCode by remember { mutableStateOf("+49") }
    var localNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Tab: 0 = Telefon, 1 = Direkt, 2 = NearbyID, 3 = Bots
    var selectedTab by remember { mutableStateOf(0) }
    var directId by remember { mutableStateOf("") }

    // NearbyID-Tab
    var nearbyUsername by remember { mutableStateOf("") }
    var nearbyResult by remember { mutableStateOf<String?>(null) }

    // Bot-Suche (nur Admin)
    var botQuery by remember { mutableStateOf("") }
    var botResults by remember { mutableStateOf<List<BotPublicResponse>>(emptyList()) }
    var botSearchLoading by remember { mutableStateOf(false) }

    // Offizielle Bots (z.B. Lethe Assistant) - fuer alle User ohne Suche
    var featuredBots by remember { mutableStateOf<List<BotPublicResponse>>(emptyList()) }
    var featuredBotsLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (onLoadFeaturedBots != null) {
            featuredBotsLoading = true
            onLoadFeaturedBots { results ->
                featuredBots = results
                featuredBotsLoading = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.contact_add_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (!isLoading) onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.contact_add_close))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab-Leiste
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; errorMessage = null; nearbyResult = null },
                        text = { Text(stringResource(R.string.contact_add_tab_phone), fontSize = 12.sp) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; errorMessage = null; nearbyResult = null },
                        text = { Text(stringResource(R.string.contact_add_tab_lethaid), fontSize = 12.sp) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2; errorMessage = null; nearbyResult = null },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        text = { Text(stringResource(R.string.contact_add_tab_nearby), fontSize = 12.sp) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3; errorMessage = null; nearbyResult = null },
                        icon = { Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        text = { Text(stringResource(R.string.contact_add_tab_bots), fontSize = 12.sp) })
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    // ── Tab 0: Telefonnummer ──────────────────────────────
                    0 -> {
                        Text(
                            stringResource(R.string.contact_add_phone_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = if (isOnboardingPhoneStep) Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(4.dp)
                            else Modifier.fillMaxWidth()
                        ) {
                            PhoneInputField(
                                countryCode = countryCode,
                                localNumber = localNumber,
                                onCountryCodeChange = { countryCode = it },
                                onLocalNumberChange = { localNumber = it },
                                enabled = !isLoading,
                                label = stringResource(R.string.contact_add_phone_label)
                            )
                        }
                        if (isOnboardingPhoneStep) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.contact_add_phone_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.contact_add_nickname_label)) },
                            placeholder = { Text(stringResource(R.string.contact_add_nickname_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp)
                        )
                        errorMessage?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { if (!isLoading) onDismiss() }, enabled = !isLoading) {
                                Text(stringResource(R.string.contact_add_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    errorMessage = null
                                    val e164 = buildE164(countryCode, localNumber)
                                    if (localNumber.isNotBlank()) {
                                        if (onLookupByPhone != null) {
                                            isLoading = true
                                            onLookupByPhone(e164) { fakeNumber ->
                                                isLoading = false
                                                if (fakeNumber != null) {
                                                    onContactRequestSent?.invoke()
                                                    onAddContact(fakeNumber, name.ifBlank { fakeNumber })
                                                } else {
                                                    errorMessage = "Kein Lethe-Nutzer mit dieser Nummer gefunden."
                                                }
                                            }
                                        } else {
                                            onContactRequestSent?.invoke()
                                            onAddContact(e164, name.ifBlank { e164 })
                                        }
                                    }
                                },
                                enabled = !isLoading && localNumber.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                else Text(stringResource(R.string.contact_add_button))
                            }
                        }
                    }

                    // ── Tab 1: LetheID (Anonym) ──────────────────────────
                    1 -> {
                        Text(
                            stringResource(R.string.contact_add_lethaid_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = directId,
                            onValueChange = { directId = it },
                            label = { Text(stringResource(R.string.contact_add_lethaid_label)) },
                            placeholder = { Text(stringResource(R.string.contact_add_lethaid_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp)
                        )
                        errorMessage?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { if (!isLoading) onDismiss() }, enabled = !isLoading) {
                                Text(stringResource(R.string.contact_add_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (directId.isNotBlank()) {
                                        isLoading = true
                                        onAddContact(directId.trim(), directId.trim())
                                    }
                                },
                                enabled = !isLoading && directId.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                else Text(stringResource(R.string.contact_add_connect_anon))
                            }
                        }
                    }

                    // ── Tab 2: NearbyID ──────────────────────────────────
                    2 -> {
                        Text(
                            stringResource(R.string.contact_add_nearby_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = nearbyUsername,
                            onValueChange = { nearbyUsername = it; nearbyResult = null },
                            label = { Text(stringResource(R.string.contact_add_nearby_label)) },
                            placeholder = { Text(stringResource(R.string.contact_add_nearby_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                            leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        nearbyResult?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                it,
                                color = if (it.startsWith("Fehler") || it.startsWith("Kein"))
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { if (!isLoading) onDismiss() }, enabled = !isLoading) {
                                Text(stringResource(R.string.contact_add_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (nearbyUsername.isNotBlank() && onSendNearbyLike != null) {
                                        isLoading = true
                                        nearbyResult = null
                                        onSendNearbyLike(nearbyUsername.trim()) { success ->
                                            isLoading = false
                                            nearbyResult = if (success) "Like gesendet!" else "Kein Profil mit diesem Benutzernamen gefunden."
                                        }
                                    }
                                },
                                enabled = !isLoading && nearbyUsername.isNotBlank() && onSendNearbyLike != null,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                else Text(stringResource(R.string.contact_add_nearby_like))
                            }
                        }
                    }

                    // ── Tab 3: Bots ────────────────────────────────────────
                    3 -> if (isAdmin) {
                        OutlinedTextField(
                            value = botQuery,
                            onValueChange = { botQuery = it },
                            label = { Text(stringResource(R.string.contact_add_bot_search_label)) },
                            placeholder = { Text(stringResource(R.string.contact_add_bot_search_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (botSearchLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (botQuery.isNotBlank() && onSearchBots != null) {
                                    botSearchLoading = true
                                    onSearchBots(botQuery) { results ->
                                        botResults = results
                                        botSearchLoading = false
                                    }
                                }
                            },
                            enabled = botQuery.isNotBlank() && !botSearchLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.contact_add_bot_search_button))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (botResults.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                                items(botResults) { bot ->
                                    BotListItem(bot = bot, onClick = { onAddContact(bot.fakeNumber, bot.name) })
                                    HorizontalDivider(thickness = 0.5.dp)
                                }
                            }
                        } else if (!botSearchLoading && botQuery.isNotBlank()) {
                            Text(
                                stringResource(R.string.contact_add_bot_not_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        // Normale User: keine Suche, nur die offiziellen Lethe-Bots (z.B. Lethe Assistant)
                        if (featuredBotsLoading) {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else if (featuredBots.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                                items(featuredBots) { bot ->
                                    BotListItem(bot = bot, onClick = { onAddContact(bot.fakeNumber, bot.name) })
                                    HorizontalDivider(thickness = 0.5.dp)
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.contact_add_bot_not_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotListItem(bot: BotPublicResponse, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bot.name, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(stringResource(R.string.contact_add_bot_tag), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
        },
        supportingContent = {
            Text(
                bot.description ?: bot.fakeNumber,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmartToy, contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
