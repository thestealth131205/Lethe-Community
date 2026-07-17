package com.securechat.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.data.network.AdminUpdateUserRequest
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserManagementScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = Color(0xFFA8A800),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Nutzer verwalten",
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Neuer User") },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bearbeiten / Löschen") },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
            }
            when (selectedTab) {
                0 -> CreateUserTab(viewModel = viewModel)
                1 -> EditUserTab(viewModel = viewModel)
            }
        }
    }
}

// ─── Tab 1: Neuer User ───────────────────────────────────────────────────────

@Composable
private fun CreateUserTab(viewModel: MainViewModel) {
    val message by viewModel.adminCreateUserMessage.collectAsState()

    var fakeNumber by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var isModerator by remember { mutableStateOf(false) }
    var isCreator by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Neuen Nutzer anlegen",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        OutlinedTextField(
            value = fakeNumber,
            onValueChange = { fakeNumber = it },
            label = { Text("Lethe-Nummer / Pseudonym *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Anzeigename *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort * (min. 12 Zeichen + Sonderzeichen)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            }
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Echte Telefonnummer (optional, E.164)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) }
        )

        Spacer(Modifier.height(4.dp))
        Text(
            "Rechte vergeben",
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoleToggleButton(
                modifier = Modifier.weight(1f),
                label = "Admin",
                icon = Icons.Default.AdminPanelSettings,
                selected = isAdmin,
                activeColor = Color(0xFFF87171),
                onClick = { isAdmin = !isAdmin }
            )
            RoleToggleButton(
                modifier = Modifier.weight(1f),
                label = "Moderator",
                icon = Icons.Default.Shield,
                selected = isModerator,
                activeColor = Color(0xFF60A5FA),
                onClick = { isModerator = !isModerator }
            )
            RoleToggleButton(
                modifier = Modifier.weight(1f),
                label = "Creator",
                icon = Icons.Default.Stars,
                selected = isCreator,
                activeColor = Color(0xFFA8A800),
                onClick = { isCreator = !isCreator }
            )
        }

        // Status-Nachricht
        message?.let { msg ->
            val isError = msg.startsWith("Fehler")
            Surface(
                color = if (isError) MaterialTheme.colorScheme.errorContainer
                        else Color(0xFF166534).copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        msg,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                                else Color(0xFF4ADE80),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearAdminCreateUserMessage() },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                isLoading = true
                viewModel.clearAdminCreateUserMessage()
                viewModel.adminCreateUser(
                    fakeNumber = fakeNumber.trim(),
                    name = name.trim(),
                    password = password,
                    phoneNumber = phoneNumber.trim().takeIf { it.isNotBlank() },
                    isAdmin = isAdmin,
                    isModerator = isModerator,
                    isCreator = isCreator,
                    onDone = { success, _ ->
                        isLoading = false
                        if (success) {
                            fakeNumber = ""
                            name = ""
                            password = ""
                            phoneNumber = ""
                            isAdmin = false
                            isModerator = false
                            isCreator = false
                        }
                    }
                )
            },
            enabled = fakeNumber.isNotBlank() && name.isNotBlank() && password.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8A800))
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("User anlegen", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// ─── Tab 2: Bearbeiten / Löschen ─────────────────────────────────────────────

@Composable
private fun EditUserTab(viewModel: MainViewModel) {
    val editDetails by viewModel.adminEditUserDetails.collectAsState()
    val message by viewModel.adminEditUserMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MainViewModel.AdminUserResult>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<MainViewModel.AdminUserResult?>(null) }
    val adminSearchResults by viewModel.adminSearchResults.collectAsState()

    // Wenn Suchergebnisse aktualisiert werden, übernehmen
    LaunchedEffect(adminSearchResults) {
        if (searchQuery.isNotBlank()) searchResults = adminSearchResults
    }

    // Formularfelder
    var name by remember { mutableStateOf("") }
    var fakeNumber by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var styx by remember { mutableStateOf("") }
    var instagram by remember { mutableStateOf("") }
    var tiktok by remember { mutableStateOf("") }
    var youtube by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var isModerator by remember { mutableStateOf(false) }
    var isCreator by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }
    var ageVerified by remember { mutableStateOf(false) }
    var isVerified by remember { mutableStateOf(false) }
    var showOnlineStatus by remember { mutableStateOf(true) }
    var showReadReceipts by remember { mutableStateOf(true) }
    var statusVisible by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Wenn Details geladen werden, Felder befüllen
    LaunchedEffect(editDetails) {
        editDetails?.let { d ->
            name = d.name
            fakeNumber = d.fakeNumber
            phoneNumber = d.phoneNumber
            info = d.info
            styx = d.styx.toString()
            instagram = d.instagram
            tiktok = d.tiktok
            youtube = d.youtube
            isAdmin = d.isAdmin
            isModerator = d.isModerator
            isCreator = d.isCreator
            isBlocked = d.isBlocked
            ageVerified = d.ageVerified
            isVerified = d.isVerified
            showOnlineStatus = d.showOnlineStatus
            showReadReceipts = d.showReadReceipts
            statusVisible = d.statusVisible
            newPassword = ""
        }
    }

    LaunchedEffect(message) {
        if (message != null) isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Suche ──
        Text(
            "Nutzer suchen",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Handynummer oder Benutzername") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = {
                    if (searchQuery.isNotBlank()) viewModel.adminSearchUsers(searchQuery.trim())
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Suchen")
                }
            }
        )

        // Suchergebnisse
        if (searchResults.isNotEmpty() && editDetails == null) {
            searchResults.forEach { user ->
                Surface(
                    onClick = {
                        selectedUser = user
                        searchResults = emptyList()
                        viewModel.adminLoadUserDetails(user.id)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(user.name.ifBlank { "–" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(user.fakeNumber, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }

        // ── Formular (wenn Nutzer geladen) ──
        if (editDetails != null) {
            val d = editDetails!!

            // Info-Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ID: ${d.id}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    if (d.letheId.isNotBlank())
                        Text("Lethe-ID: ${d.letheId}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    if (d.createdAt != null)
                        Text("Registriert: ${d.createdAt}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            HorizontalDivider()

            // Felder
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Anzeigename") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )
            OutlinedTextField(
                value = fakeNumber,
                onValueChange = { fakeNumber = it },
                label = { Text("Lethe-Nummer / Pseudonym") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Echte Telefonnummer (leer = entfernen)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) }
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Neues Passwort (leer = unverändert)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                }
            )
            OutlinedTextField(
                value = info,
                onValueChange = { info = it },
                label = { Text("Bio / Info") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            OutlinedTextField(
                value = styx,
                onValueChange = { styx = it.filter { c -> c.isDigit() } },
                label = { Text("Styx-Guthaben") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { Icon(Icons.Default.CurrencyBitcoin, contentDescription = null) }
            )
            OutlinedTextField(
                value = instagram,
                onValueChange = { instagram = it },
                label = { Text("Instagram") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
            )
            OutlinedTextField(
                value = tiktok,
                onValueChange = { tiktok = it },
                label = { Text("TikTok") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
            )
            OutlinedTextField(
                value = youtube,
                onValueChange = { youtube = it },
                label = { Text("YouTube") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null) }
            )

            // Rollen
            Text(
                "Rollen & Status",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoleToggleButton(
                    modifier = Modifier.weight(1f),
                    label = "Admin",
                    icon = Icons.Default.AdminPanelSettings,
                    selected = isAdmin,
                    activeColor = Color(0xFFF87171),
                    onClick = { isAdmin = !isAdmin }
                )
                RoleToggleButton(
                    modifier = Modifier.weight(1f),
                    label = "Moderator",
                    icon = Icons.Default.Shield,
                    selected = isModerator,
                    activeColor = Color(0xFF60A5FA),
                    onClick = { isModerator = !isModerator }
                )
                RoleToggleButton(
                    modifier = Modifier.weight(1f),
                    label = "Creator",
                    icon = Icons.Default.Stars,
                    selected = isCreator,
                    activeColor = Color(0xFFA8A800),
                    onClick = { isCreator = !isCreator }
                )
            }

            // Weitere Status-Checkboxen
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AdminCheckRow("Gesperrt", isBlocked) { isBlocked = it }
                AdminCheckRow("Alter verifiziert", ageVerified) { ageVerified = it }
                AdminCheckRow("Verifiziert (Haken)", isVerified) { isVerified = it }
                AdminCheckRow("Online-Status sichtbar", showOnlineStatus) { showOnlineStatus = it }
                AdminCheckRow("Lesebestätigungen", showReadReceipts) { showReadReceipts = it }
                AdminCheckRow("Status sichtbar", statusVisible) { statusVisible = it }
            }

            // Feedback-Nachricht
            message?.let { msg ->
                val isError = msg.startsWith("Fehler")
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer
                            else Color(0xFF166534).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            msg,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                                    else Color(0xFF4ADE80),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearAdminEditUserMessage() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Aktions-Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        isLoading = true
                        viewModel.clearAdminEditUserMessage()
                        viewModel.adminUpdateUser(
                            userId = d.id,
                            request = AdminUpdateUserRequest(
                                name = name.trim().takeIf { it.isNotBlank() },
                                fakeNumber = fakeNumber.trim().takeIf { it.isNotBlank() },
                                phoneNumber = phoneNumber.trim(),
                                password = newPassword.trim().takeIf { it.isNotBlank() },
                                isAdmin = isAdmin,
                                isModerator = isModerator,
                                isCreator = isCreator,
                                isBlocked = isBlocked,
                                ageVerified = ageVerified,
                                info = info.trim(),
                                styx = styx.toIntOrNull(),
                                instagram = instagram.trim(),
                                tiktok = tiktok.trim(),
                                youtube = youtube.trim(),
                                showOnlineStatus = showOnlineStatus,
                                showReadReceipts = showReadReceipts,
                                statusVisible = statusVisible,
                                isVerified = isVerified
                            )
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8A800))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Text("Speichern", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }

                Button(
                    onClick = { showDeleteConfirm = true },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Löschen", fontWeight = FontWeight.Bold)
                }
            }

            TextButton(
                onClick = {
                    viewModel.clearAdminEditUserDetails()
                    viewModel.clearAdminEditUserMessage()
                    selectedUser = null
                    searchQuery = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Auswahl aufheben")
            }
        }
    }

    // Löschen-Bestätigung
    if (showDeleteConfirm && editDetails != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("User löschen?") },
            text = {
                Text("${editDetails!!.name.ifBlank { editDetails!!.fakeNumber }} wird unwiderruflich gelöscht.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminDeleteUser(editDetails!!.id)
                        viewModel.clearAdminEditUserDetails()
                        viewModel.clearAdminEditUserMessage()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

// ─── Hilfs-Composables ───────────────────────────────────────────────────────

@Composable
private fun RoleToggleButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val containerColor = if (selected) activeColor.copy(alpha = 0.2f)
                         else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) activeColor
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val borderColor = if (selected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, color = contentColor, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AdminCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(32.dp)
        )
    }
}
