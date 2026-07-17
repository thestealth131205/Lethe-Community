package com.securechat.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.app.R

/**
 * Overlay-Dialog der beim App-Start angezeigt wird wenn ein Update verfügbar ist.
 *
 * Zustände:
 *  - Kein Download gestartet (progress == -1): Karte zentriert, Buttons sichtbar
 *  - Download läuft (0-99): Karte verschiebt sich nach oben, Ladebalken erscheint unten
 *  - Download fertig (100): Karte oben, Ladebalken voll, Installation wird gestartet
 */
@Composable
fun UpdateDialog(
    newVersion: String,
    changelog: String,
    downloadProgress: Int,           // -1 = nicht gestartet, 0..99 = läuft, 100 = fertig
    onDismiss: () -> Unit,
    onDownloadNow: () -> Unit,
    onInstallNow: () -> Unit
) {
    val isDownloading = downloadProgress in 0..99
    val isDone = downloadProgress == 100

    // Karte wandert nach oben wenn Download läuft
    val cardTopPadding by animateDpAsState(
        targetValue = if (isDownloading || isDone) 64.dp else 180.dp,
        animationSpec = tween(durationMillis = 400),
        label = "cardTop"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        // --- Update-Karte ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = cardTopPadding),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header: Icon + Titel
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.update_available),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Versionsnummer
                Text(
                    stringResource(R.string.update_version, newVersion),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                // Changelog
                Text(
                    changelog,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                Spacer(Modifier.height(20.dp))

                if (!isDownloading && !isDone) {
                    // --- Buttons: Download noch nicht gestartet ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                stringResource(R.string.update_later),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onDownloadNow,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.update_download))
                        }
                    }
                } else if (isDone) {
                    // --- Download fertig: Installieren-Button ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onInstallNow,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.update_install))
                        }
                    }
                } else {
                    // --- Download läuft ---
                    Text(
                        text = stringResource(R.string.update_downloading),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // --- Ladebalken am unteren Bildschirmrand ---
        if (isDownloading || isDone) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Lethe $newVersion",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${downloadProgress.coerceIn(0, 100)} %",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (downloadProgress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}
