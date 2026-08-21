package com.securechat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.R
import com.securechat.app.data.network.CreatorApplicationResponse
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor

private val CONTENT_TYPE_OPTIONS = listOf(
    "images"    to "Bilder",
    "video"     to "Video",
    "services"  to "Dienstleistung",
    "3d_models" to "3D Modelle / Designs",
    "livestream" to "Livestream"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorBewerbungScreen(
    viewModel: MainViewModel,
    startTab: Int = 0,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(startTab) }

    LaunchedEffect(startTab) {
        if (startTab == 1) viewModel.loadMyCreatorApplications()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.creator_bewerbung_title), color = topBarTitleColor()) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.creator_bewerbung_tab_apply)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.loadMyCreatorApplications()
                    },
                    text = { Text(stringResource(R.string.creator_bewerbung_tab_my_application)) }
                )
            }

            when (selectedTab) {
                0 -> BewerbenFormTab(viewModel = viewModel, onSubmitted = { selectedTab = 1 })
                1 -> {
                    val applications by viewModel.myCreatorApplications.collectAsState()
                    MyApplicationTab(applications = applications, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun BewerbenFormTab(
    viewModel: MainViewModel,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val loading by viewModel.creatorApplicationLoading.collectAsState()
    val success by viewModel.creatorApplicationSuccess.collectAsState()
    val error by viewModel.creatorApplicationError.collectAsState()

    var bio by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var references by remember { mutableStateOf("") }
    var selectedTypes by remember { mutableStateOf(setOf<String>()) }

    val ageVerified = currentUser?.is18Verified ?: false
    val styxBalance = currentUser?.styx ?: 0

    // Erfolg-Feedback
    LaunchedEffect(success) {
        if (success) {
            Toast.makeText(context, "Bewerbung erfolgreich eingereicht!", Toast.LENGTH_LONG).show()
            viewModel.clearCreatorApplicationState()
            onSubmitted()
        }
    }

    // Fehler-Feedback
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearCreatorApplicationState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Altersverifikation-Hinweis
            if (!ageVerified) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        stringResource(R.string.creator_bewerbung_age_verification_required),
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }

            // Bio
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(stringResource(R.string.creator_bewerbung_bio_label)) },
                placeholder = { Text(stringResource(R.string.creator_bewerbung_bio_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = ageVerified
            )

            // Alter
            OutlinedTextField(
                value = age,
                onValueChange = { if (it.length <= 3) age = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.creator_bewerbung_age_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = ageVerified
            )

            // E-Mail
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.creator_bewerbung_email_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = ageVerified
            )

            // Referenzen (optional)
            OutlinedTextField(
                value = references,
                onValueChange = { references = it },
                label = { Text(stringResource(R.string.creator_bewerbung_references_label)) },
                placeholder = { Text(stringResource(R.string.creator_bewerbung_references_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                enabled = ageVerified
            )

            // Content-Typen
            Text(
                stringResource(R.string.creator_bewerbung_content_type_title),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                stringResource(R.string.creator_bewerbung_content_type_subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CONTENT_TYPE_OPTIONS.forEach { (key, label) ->
                    val isSelected = key in selectedTypes
                    val canSelect = isSelected || selectedTypes.size < 3
                    ContentTypeChip(
                        label = label,
                        selected = isSelected,
                        enabled = ageVerified && (isSelected || canSelect),
                        onClick = {
                            selectedTypes = if (isSelected) {
                                selectedTypes - key
                            } else if (selectedTypes.size < 3) {
                                selectedTypes + key
                            } else {
                                selectedTypes
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Kosten-Hinweis
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.creator_bewerbung_fee), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        stringResource(R.string.creator_bewerbung_balance, styxBalance),
                        fontSize = 12.sp,
                        color = if (styxBalance >= 1000) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.creator_bewerbung_approval_info),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Submit-Button
            val formValid = ageVerified
                && bio.length >= 10
                && age.toIntOrNull()?.let { it >= 18 } == true
                && email.contains("@")
                && selectedTypes.isNotEmpty()
                && styxBalance >= 1000

            Button(
                onClick = {
                    viewModel.submitCreatorApplication(
                        bio = bio.trim(),
                        age = age.toInt(),
                        email = email.trim(),
                        references = references.trim().ifBlank { null },
                        contentTypes = selectedTypes.toList()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = formValid && !loading,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.creator_bewerbung_submit), fontWeight = FontWeight.Bold)
                }
            }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MyApplicationTab(
    applications: List<CreatorApplicationResponse>,
    viewModel: MainViewModel
) {
    if (applications.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.creator_bewerbung_no_applications),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(applications, key = { it.id }) { app ->
            ApplicationCard(application = app, viewModel = viewModel)
        }
    }
}

@Composable
private fun ApplicationCard(
    application: CreatorApplicationResponse,
    viewModel: MainViewModel
) {
    var replyText by remember(application.id) { mutableStateOf("") }
    var isSendingReply by remember { mutableStateOf(false) }

    val statusColor = when (application.status) {
        "approved" -> Color(0xFF4CAF50)
        "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val statusLabel = when (application.status) {
        "approved" -> stringResource(R.string.creator_bewerbung_status_approved)
        "rejected" -> stringResource(R.string.creator_bewerbung_status_rejected)
        else -> stringResource(R.string.creator_bewerbung_status_pending)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(application.bio, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            application.createdAt?.let { created ->
                Text(
                    "${stringResource(R.string.creator_bewerbung_submitted_label)}: ${created.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp
                )
            }

            application.adminNote?.takeIf { it.isNotBlank() }?.let { note ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.creator_bewerbung_admin_note_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (application.messages.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.creator_bewerbung_conversation_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    application.messages.forEach { msg ->
                        val isAdmin = msg.senderType == "admin"
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAdmin) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    if (isAdmin) stringResource(R.string.creator_bewerbung_reply_from_team)
                                    else stringResource(R.string.creator_bewerbung_you_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (application.status == "pending" || application.messages.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text(stringResource(R.string.creator_bewerbung_reply_placeholder)) },
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )
                    Button(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                isSendingReply = true
                                viewModel.replyToCreatorApplication(
                                    applicationId = application.id,
                                    message = replyText,
                                    onSuccess = {
                                        isSendingReply = false
                                        replyText = ""
                                    },
                                    onError = { isSendingReply = false }
                                )
                            }
                        },
                        enabled = !isSendingReply && replyText.isNotBlank()
                    ) {
                        if (isSendingReply) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.creator_bewerbung_reply_send_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentTypeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surface

    val contentColor = if (selected)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .background(containerColor, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = contentColor, fontSize = 14.sp)
    }
}
