package com.securechat.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Öffnet die eigenständige Companion-App „Lethe Media Player" (com.Lethe.mediaplayer),
 * die den früheren SYSTEM_ALERT_WINDOW-Mini-Player (FloatingMusicPlayerService) ersetzt.
 * Ist sie nicht installiert, zeigt der Aufrufer [MediaPlayerInstallDialog] an.
 */
object MediaPlayerLauncher {
    const val PACKAGE_NAME = "com.Lethe.mediaplayer"

    /** Download-Link zeigt immer auf die zuletzt deployte Version (Symlink, aktualisiert sich
     *  bei jedem push-and-deploy.sh-Lauf automatisch – siehe MP_DOWNLOAD_DIR/lethe-mediaplayer-latest.apk). */
    const val DOWNLOAD_URL = "https://letheapp.de/downloads/mediaplayer/lethe-mediaplayer-latest.apk"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Versucht die App zu öffnen. Gibt false zurück, wenn sie nicht installiert ist. */
    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) ?: return false
        context.startActivity(intent)
        return true
    }
}

@Composable
fun MediaPlayerInstallDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
        title = { Text("Lethe Media Player") },
        text = {
            Text(
                "Für die Musikwiedergabe wird die eigenständige App \u201eLethe Media Player\u201c " +
                    "benötigt. Sie ist noch nicht installiert."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MediaPlayerLauncher.DOWNLOAD_URL)))
                onDismiss()
            }) { Text("Herunterladen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
