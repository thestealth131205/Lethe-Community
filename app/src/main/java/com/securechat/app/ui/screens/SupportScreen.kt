package com.securechat.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.securechat.app.LetheLogger
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.app.R
import com.securechat.app.data.network.UserSupportTicket
import com.securechat.app.ui.MainViewModel
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.support_title), color = topBarTitleColor()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
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
                    text = { Text(stringResource(R.string.support_tab_create)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.loadMyTickets()
                    },
                    text = { Text(stringResource(R.string.support_tab_my_tickets)) }
                )
            }

            when (selectedTab) {
                0 -> CreateTicketTab(viewModel = viewModel)
                1 -> {
                    val tickets by viewModel.myTickets.collectAsState()
                    MyTicketsTab(tickets = tickets)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTicketTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val categories = listOf(
        context.getString(R.string.support_cat_technical),
        context.getString(R.string.support_cat_account),
        context.getString(R.string.support_cat_security),
        context.getString(R.string.support_cat_report),
        context.getString(R.string.support_cat_other)
    )
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var attachLogs by remember { mutableStateOf(false) }
    val hasLogFile = remember { LetheLogger.getLogFile()?.let { it.exists() && it.length() > 0 } == true }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitSuccess by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        selectedImages = (selectedImages + uris).take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (submitSuccess) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column {
                        Text(stringResource(R.string.support_ticket_sent), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.support_we_respond), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text(stringResource(R.string.support_category), style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach { cat ->
                    DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; categoryExpanded = false })
                }
            }
        }

        Text(stringResource(R.string.support_subject), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(stringResource(R.string.support_subject_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(stringResource(R.string.support_description), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            placeholder = { Text(stringResource(R.string.support_description_placeholder)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            maxLines = 8
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.support_screenshots_label), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { imagePicker.launch("image/*") }, enabled = selectedImages.size < 5) {
                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.support_add_screenshot))
            }
        }

        if (selectedImages.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedImages) { uri ->
                    Box(modifier = Modifier.size(80.dp)) {
                        AsyncImage(
                            model = uri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedImages = selectedImages.filter { it != uri } },
                            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close, null, tint = Color.White,
                                modifier = Modifier.size(16.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }
        }

        // Logs anhängen (nur anzeigen wenn Log-Datei vorhanden)
        if (hasLogFile) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = attachLogs, onCheckedChange = { attachLogs = it })
                Spacer(androidx.compose.ui.Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.support_attach_logs), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.support_attach_logs_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (attachLogs) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 48.dp)
                ) {
                    Icon(
                        Icons.Default.Attachment,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "lethe_logs.txt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        submitError?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                if (title.isBlank() || description.isBlank()) {
                    submitError = context.getString(R.string.support_validation_error)
                    return@Button
                }
                isSubmitting = true
                submitError = null
                viewModel.submitSupportTicket(
                    category = selectedCategory,
                    title = title,
                    description = description,
                    imageUris = selectedImages,
                    attachLogs = attachLogs,
                    onSuccess = {
                        isSubmitting = false
                        submitSuccess = true
                        title = ""
                        description = ""
                        selectedImages = emptyList()
                    },
                    onError = { err ->
                        isSubmitting = false
                        submitError = err
                    }
                )
            },
            enabled = !isSubmitting && !submitSuccess,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.support_send_button))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MyTicketsTab(tickets: List<UserSupportTicket>) {
    if (tickets.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.support_no_tickets), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tickets, key = { it.id }) { ticket ->
            TicketCard(ticket = ticket)
        }
    }
}

@Composable
private fun TicketCard(ticket: UserSupportTicket) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (ticket.status) {
        "closed" -> MaterialTheme.colorScheme.outline
        "in_progress" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val statusLabel = when (ticket.status) {
        "closed" -> stringResource(R.string.support_status_closed)
        "in_progress" -> stringResource(R.string.support_status_in_progress)
        else -> stringResource(R.string.support_status_open)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ticket.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.15f)) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(ticket.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ticket.imageUrls.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Attachment,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${ticket.imageUrls.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider()
                Text(ticket.description, style = MaterialTheme.typography.bodyMedium)

                if (ticket.imageUrls.isNotEmpty()) {
                    val context = LocalContext.current
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.support_attachments_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ticket.imageUrls.forEach { url ->
                            val filename = url.substringAfterLast("/")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable {
                                    val fullUrl = "https://letheapp.de$url"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                                }
                            ) {
                                Icon(
                                    Icons.Default.Attachment,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    filename,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (!ticket.adminReply.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.support_reply_from_support),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(ticket.adminReply, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                ticket.createdAt?.let { created ->
                    Text(
                        created.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
