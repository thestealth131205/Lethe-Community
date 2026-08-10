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

    /** Vollqualifizierter Name der Einstiegs-Activity im Media-Player-Modul (com.lethe.mediaplayer). */
    private const val ACTIVITY_NAME = "com.lethe.mediaplayer.MainActivity"

    /** Intent-Extra, über das der Chat die vollständige Stream-URL eines Liedes an den
     *  Lethe Media Player übergibt. Der Player streamt die URL und liest die ID3-Tags
     *  (Titel, Künstler, Cover) selbst aus. */
    const val EXTRA_STREAM_URL = "com.Lethe.mediaplayer.extra.STREAM_URL"

    /** Download-Link zeigt immer auf die zuletzt deployte Version (Symlink, aktualisiert sich
     *  bei jedem push-and-deploy.sh-Lauf automatisch – siehe MP_DOWNLOAD_DIR/lethe-mediaplayer-latest.apk). */
    const val DOWNLOAD_URL = "https://letheapp.de/downloads/mediaplayer/lethe-mediaplayer-latest.apk"

    /** Landingpage mit Erklärung + Download-Button (Style der Lethe-Webseite), statt direktem APK-Download. */
    const val DOWNLOAD_PAGE_URL = "https://letheapp.de/Cast_lethe_mediaplayer.html"

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

    /**
     * Öffnet den Lethe Media Player und übergibt die vollständige Stream-URL eines Liedes, damit
     * dieser sie streamt (inkl. ID3-Tags: Titel, Künstler, Cover) und der Nutzer von dort casten kann.
     * Gibt false zurück, wenn die App nicht installiert ist oder das Öffnen fehlschlägt.
     */
    fun openWithStreamUrl(context: Context, url: String): Boolean {
        if (!isInstalled(context) || url.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(PACKAGE_NAME, ACTIVITY_NAME)
            putExtra(EXTRA_STREAM_URL, url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
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
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MediaPlayerLauncher.DOWNLOAD_PAGE_URL)))
                onDismiss()
            }) { Text("Herunterladen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
