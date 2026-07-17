package com.lethe.mediaplayer.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.lethe.mediaplayer.R
import com.lethe.mediaplayer.cast.CastManager
import com.lethe.mediaplayer.cast.SafeMediaRouteDialogFactory
import com.lethe.mediaplayer.player.PlaybackSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsSheet(
    settings: PlaybackSettings,
    castManager: CastManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val enabled by settings.crossfadeEnabled.collectAsState()
    val seconds by settings.crossfadeSeconds.collectAsState()
    val isCasting by castManager.isCasting.collectAsState()

    var showAudioSettings by remember { mutableStateOf(false) }
    var castButtonRef by remember { mutableStateOf<MediaRouteButton?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var volume by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                "Audioausgabe",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // Unsichtbarer Cast-Button: übernimmt den vom System bereitgestellten
            // Geräte-Auswahldialog (Chooser bzw. Controller bei aktiver Session).
            // Die "Google Cast"-Zeile löst performClick() aus. WICHTIG: der Button nutzt per
            // dialogFactory die SafeMediaRouteDialogFactory, sonst öffnet sein interner
            // showDialogInternal() das vanilla Fragment und crasht mit
            // "background can not be translucent: #0" (siehe CastButton.kt).
            AndroidView(
                modifier = Modifier.size(0.dp),
                factory = { ctx ->
                    val themed = ContextThemeWrapper(ctx, R.style.Theme_LetheMediaPlayer_MediaRouter)
                    MediaRouteButton(themed).apply {
                        dialogFactory = SafeMediaRouteDialogFactory()
                        runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, this) }
                        castButtonRef = this
                    }
                }
            )

            AudioOutputRow(
                icon = Icons.Filled.PhoneAndroid,
                title = "Mein Smartphone",
                selected = !isCasting,
                onClick = { if (isCasting) castManager.stopCasting() }
            )
            AudioOutputRow(
                icon = Icons.Filled.Bluetooth,
                title = "Bluetooth",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
            AudioOutputRow(
                icon = Icons.Filled.Cast,
                title = "Google Cast",
                selected = isCasting,
                onClick = {
                    // Bei aktiver Session immer den Controller öffnen; sonst nur wenn ein lokales
                    // Netzwerk existiert, da die Geräte-Suche über Mobilfunk endlos läuft.
                    if (isCasting || isCastNetworkAvailable(context)) castButtonRef?.performClick()
                    else notifyCastNeedsWifi(context)
                }
            )
            AudioOutputRow(
                icon = Icons.Filled.Equalizer,
                title = "Equalizer",
                onClick = {
                    runCatching {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                        context.startActivity(intent)
                    }
                }
            )
            AudioOutputRow(
                icon = Icons.Filled.Tune,
                title = "Audio-Einstellungen",
                subtitle = if (enabled) "Übergang zwischen Liedern · ${seconds}s" else "Übergang zwischen Liedern · aus",
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showAudioSettings = !showAudioSettings }
            )

            if (showAudioSettings) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Übergang zwischen Liedern", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = enabled, onCheckedChange = { settings.setCrossfadeEnabled(it) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Dauer: $seconds ${if (seconds == 1) "Sekunde" else "Sekunden"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = seconds.toFloat(),
                        onValueChange = { settings.setCrossfadeSeconds(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        enabled = enabled
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Lautstärke")
                Spacer(Modifier.width(16.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                    },
                    valueRange = 0f..maxVolume,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AudioOutputRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
