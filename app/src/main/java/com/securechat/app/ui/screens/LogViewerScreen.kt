package com.securechat.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.securechat.app.LetheLogger
import com.securechat.app.R
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Logs beim Öffnen laden
    LaunchedEffect(Unit) {
        logLines = loadLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_viewer_title), color = topBarTitleColor()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                    }
                },
                actions = {
                    // Logs teilen
                    IconButton(onClick = {
                        val file = LetheLogger.getLogFile()
                        if (file != null && file.exists() && file.length() > 0) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Lethe App-Logs")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.log_viewer_share)))
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.log_viewer_share))
                    }
                    // Logs löschen
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = stringResource(R.string.log_viewer_clear))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (logLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.log_viewer_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logLines) { line ->
                    val color = when {
                        line.contains("[CRASH]") -> MaterialTheme.colorScheme.error
                        line.contains("[ERROR]") -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        line.contains("[WARN ]") -> MaterialTheme.colorScheme.tertiary
                        line.contains("[DEBUG]") -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.log_viewer_clear_confirm_title)) },
            text = { Text(stringResource(R.string.log_viewer_clear_confirm_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        LetheLogger.clear()
                        logLines = emptyList()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.log_viewer_clear_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.general_cancel))
                }
            }
        )
    }
}

private fun loadLogs(): List<String> {
    val file = LetheLogger.getLogFile() ?: return emptyList()
    if (!file.exists()) return emptyList()
    return try {
        file.readLines()
            .filter { it.isNotBlank() }
            .reversed() // neueste Einträge oben
    } catch (_: Exception) {
        emptyList()
    }
}
