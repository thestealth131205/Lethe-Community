package com.securechat.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Schaltet den Fenster-Farbmodus auf HDR (COLOR_MODE_HDR), solange dieser
 * Composable mit enabled=true in Komposition ist, und stellt beim Verlassen
 * bzw. bei enabled=false den vorherigen Modus wieder her. Ohne HDR-fähiges
 * Display/Panel ist das ein No-Op (System behält den SDR-Modus bei).
 */
@Composable
fun HdrWindowEffect(enabled: Boolean = true) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        val previousColorMode = window?.colorMode ?: ActivityInfo.COLOR_MODE_DEFAULT
        if (enabled) {
            try { window?.colorMode = ActivityInfo.COLOR_MODE_HDR } catch (_: Exception) {}
        }
        onDispose {
            try { window?.colorMode = previousColorMode } catch (_: Exception) {}
        }
    }
}
