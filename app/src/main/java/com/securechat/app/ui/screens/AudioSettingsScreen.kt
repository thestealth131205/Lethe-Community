package com.securechat.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.securechat.app.ui.theme.topBarTitleColor

/**
 * Einstellmenü rund um Audio in Lethe (Anrufe/Sprachnachrichten).
 * Oben: allgemeine Audio-Qualität. Darunter: Bluetooth-Ausgang & HFP-Unterstützung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    audioQuality: String,
    onAudioQualityChange: (String) -> Unit,
    audioOutputChannel: String,
    onAudioOutputChannelChange: (String) -> Unit,
    bluetoothHeadsetEnabled: Boolean,
    onBluetoothHeadsetEnabledChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio") },
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
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
        ) {
            // ── Allgemeine Audio-Qualität ────────────────────────────────
            SettingsSection(title = "Audio-Qualität") {
                AudioRadioRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Automatisch",
                    subtitle = "Lethe wählt die passende Qualität",
                    selected = audioQuality == "AUTO",
                    onClick = { onAudioQualityChange("AUTO") }
                )
                HorizontalDivider()
                AudioRadioRow(
                    icon = Icons.Default.GraphicEq,
                    title = "Hoch",
                    subtitle = "Beste Sprachqualität, alle Klangverbesserungen aktiv",
                    selected = audioQuality == "HIGH",
                    onClick = { onAudioQualityChange("HIGH") }
                )
                HorizontalDivider()
                AudioRadioRow(
                    icon = Icons.Default.DataSaverOn,
                    title = "Niedrig",
                    subtitle = "Nur Basis-Verarbeitung, spart Akku und Daten",
                    selected = audioQuality == "LOW",
                    onClick = { onAudioQualityChange("LOW") }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Bluetooth / Audio-Ausgang ────────────────────────────────
            SettingsSection(title = "Bluetooth") {
                SettingsItem(
                    icon = Icons.Default.BluetoothAudio,
                    title = "Bluetooth-Headset-Unterstützung",
                    subtitle = "Anrufe über ein verbundenes Bluetooth-Headset (HFP)"
                ) {
                    Switch(
                        checked = bluetoothHeadsetEnabled,
                        onCheckedChange = onBluetoothHeadsetEnabledChange
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            SettingsSection(title = "Audio-Ausgang") {
                AudioRadioRow(
                    icon = Icons.Default.SettingsSuggest,
                    title = "System",
                    subtitle = "Android entscheidet, welcher Kanal genutzt wird",
                    selected = audioOutputChannel == "SYSTEM",
                    onClick = { onAudioOutputChannelChange("SYSTEM") }
                )
                HorizontalDivider()
                AudioRadioRow(
                    icon = Icons.Default.PhoneInTalk,
                    title = "Hörer",
                    subtitle = "Audio über den Ohrhörer am Gerät",
                    selected = audioOutputChannel == "EARPIECE",
                    onClick = { onAudioOutputChannelChange("EARPIECE") }
                )
                HorizontalDivider()
                AudioRadioRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Lautsprecher",
                    subtitle = "Audio über den Freisprech-Lautsprecher",
                    selected = audioOutputChannel == "SPEAKER",
                    onClick = { onAudioOutputChannelChange("SPEAKER") }
                )
                HorizontalDivider()
                AudioRadioRow(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth",
                    subtitle = "Audio bevorzugt über ein Bluetooth-Headset",
                    selected = audioOutputChannel == "BLUETOOTH",
                    enabled = bluetoothHeadsetEnabled,
                    onClick = { if (bluetoothHeadsetEnabled) onAudioOutputChannelChange("BLUETOOTH") }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AudioRadioRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * contentAlpha)
            )
        }
        RadioButton(
            selected = selected,
            onClick = { if (enabled) onClick() },
            enabled = enabled
        )
    }
}
