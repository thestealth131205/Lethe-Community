package com.securechat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.AppChangelog
import com.securechat.app.BuildConfig
import com.securechat.app.MediaPlayerLauncher
import com.securechat.app.R
import com.securechat.app.ui.theme.topBarTitleColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoScreen(
    onNavigateBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.info_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.info_back))
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
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            SettingsSection(title = stringResource(R.string.info_about_section)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.info_version),
                    subtitle = BuildConfig.VERSION_NAME
                ) {}
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.Copyright,
                    title = "Copyright",
                    subtitle = stringResource(R.string.info_copyright)
                ) {}
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Lethe im Web",
                    subtitle = "letheapp.de",
                    onClick = { uriHandler.openUri("https://letheapp.de") }
                )
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.MusicNote,
                    title = "Music by TheStealth",
                    subtitle = "music.thestealth.de",
                    onClick = { uriHandler.openUri("https://music.thestealth.de") }
                )
                HorizontalDivider()
                SettingsItem(
                    icon = Icons.Default.LibraryMusic,
                    title = "Lethe Media Player",
                    subtitle = "Neueste Version herunterladen",
                    onClick = { uriHandler.openUri(MediaPlayerLauncher.DOWNLOAD_URL) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ─── Changelog ───────────────────────────────────────────────────
            ChangelogSection()

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ChangelogSection() {
    SettingsSection(title = stringResource(R.string.info_changelog_section)) {
        AppChangelog.entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider()
            var expanded by remember { mutableStateOf(index == 0) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Header: Version + Titel + Expand-Icon
                SettingsItem(
                    icon = if (index == 0) Icons.Default.NewReleases else Icons.Default.History,
                    title = "Version ${entry.version}  –  ${entry.title}",
                    subtitle = if (expanded) "" else entry.shortSummary,
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Aufgeklappte Stichpunkte
                if (expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        entry.items.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "•",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    item,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
