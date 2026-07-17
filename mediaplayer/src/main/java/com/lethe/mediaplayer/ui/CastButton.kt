package com.lethe.mediaplayer.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.lethe.mediaplayer.R
import com.lethe.mediaplayer.cast.SafeMediaRouteDialogFactory

/**
 * Google Cast überträgt nur innerhalb desselben lokalen Netzwerks. Ohne WLAN/Ethernet (z.B. über
 * Mobilfunk) findet die Geräte-Suche nie ein Ziel und dreht endlos "Suche nach Geräten läuft…".
 * Deshalb prüfen wir vor dem Öffnen des Auswahldialogs, ob überhaupt ein lokales Netzwerk aktiv ist.
 */
internal fun isCastNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

internal fun notifyCastNeedsWifi(context: Context) {
    Toast.makeText(
        context,
        "Google Cast benötigt eine WLAN-Verbindung.",
        Toast.LENGTH_SHORT
    ).show()
}

/**
 * Cast-Symbol (oben rechts, wie in der Lethe-Haupt-App).
 * MediaRouteButton benötigt ein AppCompat-Theme → per ContextThemeWrapper bereitgestellt.
 * WICHTIG: MediaRouteButton.performClick() ruft intern IMMER showDialogInternal() auf und
 * erzeugt das Auswahl-/Controller-Fragment über die Dialog-Factory. Ein eigener OnClickListener
 * verhindert das NICHT (das interne Fragment wird zusätzlich geöffnet und crasht mit
 * "background can not be translucent: #0"). Deshalb geben wir dem Button per dialogFactory
 * die SafeMediaRouteDialogFactory, sodass der eingebaute Pfad selbst die Safe-Fragmente nutzt.
 * Zusätzlich fängt ein transparentes Overlay den Klick ab, damit wir ihn ohne WLAN vor dem
 * (nutzlosen) internen Suchdialog abfangen und stattdessen einen Hinweis anzeigen können.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var buttonRef by remember { mutableStateOf<MediaRouteButton?>(null) }
    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                val themed = ContextThemeWrapper(
                    ctx,
                    R.style.Theme_LetheMediaPlayer_MediaRouter
                )
                MediaRouteButton(themed).apply {
                    dialogFactory = SafeMediaRouteDialogFactory()
                    runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, this) }
                    buttonRef = this
                }
            }
        )
        Box(
            Modifier
                .fillMaxSize()
                .clickable {
                    if (isCastNetworkAvailable(context)) buttonRef?.performClick()
                    else notifyCastNeedsWifi(context)
                }
        )
    }
}
