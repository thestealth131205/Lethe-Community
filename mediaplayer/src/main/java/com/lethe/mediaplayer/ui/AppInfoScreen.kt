package com.lethe.mediaplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lethe.mediaplayer.BuildConfig
import com.lethe.mediaplayer.util.AppLogger
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(
    vm: PlayerViewModel,
    onBack: () -> Unit
) {
    val account by vm.accountInfo.collectAsState()
    val cacheMaxBytes by vm.settings.cacheMaxBytes.collectAsState()
    val context = LocalContext.current

    var usedBytes by remember { mutableLongStateOf(vm.mediaCache.usedBytes) }
    var logText by remember { mutableStateOf(AppLogger.readLog()) }
    var crashLogText by remember { mutableStateOf(AppLogger.readCrashLog()) }

    LaunchedEffect(Unit) {
        vm.loadAccountInfo()
        while (true) {
            usedBytes = vm.mediaCache.usedBytes
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Infos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SectionTitle("Version")
            Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("Lethe-Konto")
            Text(account?.name?.ifBlank { null } ?: "Wird geladen…")
            if (!account?.fakeNumber.isNullOrBlank()) {
                Text(
                    account!!.fakeNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("SmartCache")
            Text("Belegt: ${formatBytes(usedBytes)} von max. ${formatBytes(cacheMaxBytes)}")
            Spacer(Modifier.height(8.dp))
            Text(
                "Maximale Cache-Größe: ${formatBytes(cacheMaxBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = (cacheMaxBytes / (1024f * 1024f * 1024f)),
                onValueChange = {
                    vm.settings.setCacheMaxBytes((it * 1024f * 1024f * 1024f).roundToLong())
                },
                valueRange = (PlaybackSettingsCacheMinGb)..(PlaybackSettingsCacheMaxGb)
            )
            Text(
                "Änderung wird nach einem Neustart der App wirksam.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("Log")
            LogBox(text = logText.ifBlank { "Keine Einträge." })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { copyToClipboard(context, "Lethe Media Player Log", logText) },
                    enabled = logText.isNotBlank()
                ) { Text("Log kopieren") }
                TextButton(onClick = { AppLogger.clearLog(); logText = "" }) { Text("Log leeren") }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("Crashlog")
            LogBox(text = crashLogText.ifBlank { "Keine Abstürze." })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { copyToClipboard(context, "Lethe Media Player Crashlog", crashLogText) },
                    enabled = crashLogText.isNotBlank()
                ) { Text("Crashlog kopieren") }
                TextButton(onClick = { AppLogger.clearCrashLog(); crashLogText = "" }) { Text("Crashlog leeren") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private val PlaybackSettingsCacheMinGb =
    (com.lethe.mediaplayer.player.PlaybackSettings.MIN_CACHE_BYTES / (1024f * 1024f * 1024f))
private val PlaybackSettingsCacheMaxGb =
    (com.lethe.mediaplayer.player.PlaybackSettings.MAX_CACHE_BYTES / (1024f * 1024f * 1024f))

private fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun LogBox(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.padding(12.dp)
        )
    }
}
