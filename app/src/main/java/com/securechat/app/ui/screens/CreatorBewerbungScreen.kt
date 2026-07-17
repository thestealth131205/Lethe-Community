package com.securechat.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    onNavigateBack: () -> Unit
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
            onNavigateBack()
        }
    }

    // Fehler-Feedback
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearCreatorApplicationState()
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
