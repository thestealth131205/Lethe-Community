package com.securechat.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.securechat.app.data.network.PhoneLookupMatch
import com.securechat.app.ui.MainViewModel
import androidx.compose.ui.res.stringResource
import com.securechat.app.R
import com.securechat.app.ui.theme.topBarTitleColor
import com.securechat.app.ui.utils.PhoneNumberExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Datenmodell
// ---------------------------------------------------------------------------

data class PhoneBookContact(
    val contactId: Long,
    val firstName: String,
    val lastName: String,
    val alias: String?,
    val photoUri: String?,
    val phoneNumbers: List<String>          // normalisiert, E.164
) {
    /** Alias falls vorhanden, sonst Vorname */
    val primaryName: String
        get() = alias?.takeIf { it.isNotBlank() } ?: firstName
}

private class PhoneBookContactBuilder(
    val contactId: Long,
    val displayNameFallback: String,
    var firstName: String = "",
    var lastName: String = "",
    var alias: String? = null,
    val photoUri: String? = null,
    val phoneNumbers: MutableList<String> = mutableListOf()
) {
    fun build() = PhoneBookContact(
        contactId    = contactId,
        firstName    = firstName.ifBlank { displayNameFallback },
        lastName     = lastName,
        alias        = alias?.ifBlank { null },
        photoUri     = photoUri,
        phoneNumbers = phoneNumbers.distinct()
    )
}

// ---------------------------------------------------------------------------
// Telefonbuch lesen
// ---------------------------------------------------------------------------

private suspend fun readFullPhoneBook(context: android.content.Context): List<PhoneBookContact> =
    withContext(Dispatchers.IO) {
        val builders = mutableMapOf<Long, PhoneBookContactBuilder>()

        // 1. Alle Kontakte – ID, Anzeigename, Vorschaubild
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cur ->
            val idIdx    = cur.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx  = cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = cur.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            while (cur.moveToNext()) {
                val id = cur.getLong(idIdx)
                builders[id] = PhoneBookContactBuilder(
                    contactId           = id,
                    displayNameFallback = cur.getString(nameIdx)?.trim() ?: "",
                    photoUri            = cur.getString(photoIdx)
                )
            }
        }

        // 2. Vor- und Nachname (StructuredName)
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME
            ),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            null
        )?.use { cur ->
            val idIdx     = cur.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val givenIdx  = cur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME)
            val familyIdx = cur.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME)
            while (cur.moveToNext()) {
                val id = cur.getLong(idIdx)
                builders[id]?.apply {
                    val given  = cur.getString(givenIdx)?.trim() ?: ""
                    val family = cur.getString(familyIdx)?.trim() ?: ""
                    if (given.isNotBlank()  && firstName.isBlank()) firstName = given
                    if (family.isNotBlank() && lastName.isBlank())  lastName  = family
                }
            }
        }

        // 3. Alias / Nickname
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Nickname.NAME
            ),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE),
            null
        )?.use { cur ->
            val idIdx   = cur.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val nickIdx = cur.getColumnIndex(ContactsContract.CommonDataKinds.Nickname.NAME)
            while (cur.moveToNext()) {
                val id   = cur.getLong(idIdx)
                val nick = cur.getString(nickIdx)?.trim()
                if (!nick.isNullOrBlank()) builders[id]?.alias = nick
            }
        }

        // 4. Telefonnummern
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { cur ->
            val idIdx  = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numIdx = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cur.moveToNext()) {
                val id  = cur.getLong(idIdx)
                val raw = cur.getString(numIdx) ?: continue
                if (raw.isBlank()) continue
                val (cc, local) = PhoneNumberExtractor.parseForDisplay(raw)
                val normalized  = cc + local
                builders[id]?.phoneNumbers?.add(normalized)
            }
        }

        builders.values
            .filter  { it.phoneNumbers.isNotEmpty() }
            .map     { it.build() }
    }

// ---------------------------------------------------------------------------
// Hauptscreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactImportScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context      = LocalContext.current
    val letheMatches by viewModel.phoneLookupMatches.collectAsState()
    val isLookingUp  by viewModel.phoneLookupLoading.collectAsState()
    val inviteUrl    by viewModel.inviteLinkUrl.collectAsState()

    // Bestehende Lethe-Kontakte → Set der fakeNumbers (inkl. Formatvarianten) + Set der userIds
    val existingContacts by viewModel.contacts.collectAsState(initial = emptyList())
    val existingFakeNumbers = remember(existingContacts) {
        buildSet {
            existingContacts.forEach { c ->
                val fn = c.fakeNumber
                add(fn)
                // Telefonnummern-Varianten hinzufügen damit "+49..." und "0..." zusammen matchen
                val cleaned = fn.replace(Regex("[\\s()\\-]"), "")
                when {
                    cleaned.startsWith("+49") && cleaned.length > 3 -> {
                        val base = cleaned.removePrefix("+49").trimStart('0')
                        add("+49$base")
                        add("0$base")
                    }
                    cleaned.startsWith("0") && cleaned.length >= 10 -> {
                        val base = cleaned.removePrefix("0")
                        add("+49$base")
                        add("0$base")
                    }
                }
            }
        }
    }
    val existingContactUserIds = remember(existingContacts) {
        existingContacts.map { it.userId }.toSet()
    }

    var allContacts by remember { mutableStateOf<List<PhoneBookContact>>(emptyList()) }
    var bookLoading by remember { mutableStateOf(false) }
    // Wird true sobald lookupPhoneContacts() aufgerufen wurde – verhindert rote Buttons vor dem ersten Lookup
    var lookupStarted by remember { mutableStateOf(false) }
    val lookupDone = lookupStarted && !isLookingUp

    // Map: normalisierte Nummer → Lethe-Match (O(1) Abfrage im UI)
    val letheMap = remember(letheMatches) { letheMatches.associateBy { it.phoneNumber } }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    // Kontakte-App-Integration ("Verbundene Apps")
    val prefs by viewModel.userPrefs.collectAsState()
    val writeContactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.setContactsAppIntegration(true) }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            return@LaunchedEffect
        }
        // Einladungslink vorab laden, damit er bei Klick sofort verfügbar ist
        viewModel.generateInviteLink()

        bookLoading  = true
        val contacts = readFullPhoneBook(context)
        allContacts  = contacts
        bookLoading  = false

        val allNumbers = contacts.flatMap { it.phoneNumbers }.distinct()
        lookupStarted = true
        viewModel.lookupPhoneContacts(allNumbers)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearPhoneLookup() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kontakte importieren", color = topBarTitleColor()) },
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

        // --- Berechtigung fehlt ---
        if (!permissionGranted) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.contact_import_permission_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        stringResource(R.string.contact_import_permission_text),
                        fontSize = 14.sp
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text(stringResource(R.string.contact_import_permission_button))
                    }
                }
            }
            return@Scaffold
        }

        // --- Telefonbuch wird geladen ---
        if (bookLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.contact_import_loading), fontSize = 14.sp)
                }
            }
            return@Scaffold
        }

        // --- Keine Kontakte ---
        if (allContacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.contact_import_empty),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            return@Scaffold
        }

        // --- Kontaktliste ---
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Schalter: Lethe in den Geräte-Kontakten unter "Verbundene Apps" anzeigen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.contacts_integration_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        stringResource(R.string.contacts_integration_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = prefs.contactsAppIntegration,
                    onCheckedChange = { wantOn ->
                        if (wantOn) {
                            if (ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.WRITE_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.setContactsAppIntegration(true)
                            } else {
                                writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                            }
                        } else {
                            viewModel.setContactsAppIntegration(false)
                        }
                    }
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (isLookingUp) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Suche auf Lethe…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(allContacts, key = { it.contactId }) { contact ->
                    PhoneBookContactCard(
                        contact                  = contact,
                        letheMap                 = letheMap,
                        existingFakeNumbers      = existingFakeNumbers,
                        existingContactUserIds   = existingContactUserIds,
                        lookupDone               = lookupDone,
                        inviteUrl                = inviteUrl,
                        onImport                 = { match ->
                            val importName = contact.alias?.takeIf { it.isNotBlank() }
                                ?: contact.firstName
                            viewModel.addContact(match.fakeNumber, importName)
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Kontaktkachel
// ---------------------------------------------------------------------------

@Composable
private fun PhoneBookContactCard(
    contact                : PhoneBookContact,
    letheMap               : Map<String, PhoneLookupMatch>,
    existingFakeNumbers    : Set<String>,
    existingContactUserIds : Set<String>,
    lookupDone             : Boolean,
    inviteUrl              : String?,
    onImport               : (PhoneLookupMatch) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Profilbild
        if (!contact.photoUri.isNullOrBlank()) {
            AsyncImage(
                model              = contact.photoUri,
                contentDescription = contact.primaryName,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier         = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Person,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier           = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {

            // Namenszeile: Alias fett + (Vorname Nachname) gedimmt  ODER  Vorname Nachname fett
            if (!contact.alias.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = contact.alias,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 15.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text     = "${contact.firstName} ${contact.lastName}".trim(),
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            } else {
                Text(
                    text       = "${contact.firstName} ${contact.lastName}".trim()
                        .ifBlank { "Unbekannt" },
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            // Alle Telefonnummern
            contact.phoneNumbers.forEach { number ->
                val match = letheMap[number]
                PhoneNumberRow(
                    number              = number,
                    match               = match,
                    isExistingContact   = (match != null && (match.isAlreadyContact
                                              || match.userId in existingContactUserIds
                                              || match.fakeNumber in existingFakeNumbers))
                                          || number in existingFakeNumbers,
                    lookupDone          = lookupDone,
                    inviteUrl           = inviteUrl,
                    onImport            = { match?.let { onImport(it) } }
                )
            }
        }
    }

    HorizontalDivider(
        modifier  = Modifier.padding(start = 82.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant
    )
}

// ---------------------------------------------------------------------------
// Telefonnummer-Zeile
// ---------------------------------------------------------------------------

@Composable
private fun PhoneNumberRow(
    number            : String,
    match             : PhoneLookupMatch?,
    isExistingContact : Boolean,
    lookupDone        : Boolean,
    inviteUrl         : String?,
    onImport          : () -> Unit
) {
    var imported by remember(number) { mutableStateOf(false) }
    var showInviteMenu by remember(number) { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text     = number,
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f)
        )

        when {
            // Bereits in der Kontaktliste → grüner Haken
            isExistingContact -> {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = "Bereits in Kontakten",
                    tint               = Color(0xFF4CAF50),
                    modifier           = Modifier
                        .size(32.dp)
                        .padding(6.dp)
                )
            }

            // Kontaktanfrage bereits gesendet in dieser Session
            imported -> {
                Text(
                    "Gesendet ✓",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.primary
                )
            }

            // Auf Lethe registriert, noch kein Kontakt → Akzentfarbe Plus
            match != null -> {
                FilledIconButton(
                    onClick  = { onImport(); imported = true },
                    modifier = Modifier.size(32.dp),
                    colors   = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Zu Lethe hinzufügen",
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }

            // Lookup abgeschlossen, Nummer nicht auf Lethe → rotes Plus mit Einladungsmenü
            lookupDone -> {
                Box {
                    FilledIconButton(
                        onClick  = { showInviteMenu = true },
                        modifier = Modifier.size(32.dp),
                        colors   = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Einladen",
                            modifier           = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded         = showInviteMenu,
                        onDismissRequest = { showInviteMenu = false }
                    ) {
                        DropdownMenuItem(
                            text    = { Text(stringResource(R.string.contact_import_sms)) },
                            onClick = {
                                showInviteMenu = false
                                val text = "Hey! Ich nutze Lethe für sichere Nachrichten. Tritt mir bei: ${inviteUrl ?: "https://letheapp.de"}"
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:$number")
                                    putExtra("sms_body", text)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        )
                        DropdownMenuItem(
                            text    = { Text(stringResource(R.string.contact_import_whatsapp)) },
                            onClick = {
                                showInviteMenu = false
                                val text = "Hey! Ich nutze Lethe für sichere Nachrichten. Tritt mir bei: ${inviteUrl ?: "https://letheapp.de"}"
                                val e164 = number.replace(Regex("[^0-9+]"), "").trimStart('+')
                                val encodedText = Uri.encode(text)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$e164?text=$encodedText"))
                                runCatching { context.startActivity(intent) }
                            }
                        )
                    }
                }
            }

            // Lookup läuft noch → deaktivierter grauer Button
            else -> {
                FilledIconButton(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
