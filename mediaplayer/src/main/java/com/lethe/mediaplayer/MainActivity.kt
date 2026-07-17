package com.lethe.mediaplayer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.lethe.mediaplayer.data.ExternalAudioImportBridge
import com.lethe.mediaplayer.ui.AppRoot
import com.lethe.mediaplayer.ui.theme.LetheMediaPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// MUSS FragmentActivity sein (nicht ComponentActivity): MediaRouteButton.performClick()
// zeigt den Cast-Geräteauswahl-Dialog per FragmentManager an. Bei einer reinen
// ComponentActivity ist die Activity keine FragmentActivity -> Absturz beim Antippen
// des Cast-Symbols. FragmentActivity erbt von ComponentActivity, setContent()/Hilt bleiben nutzbar.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var importBridge: ExternalAudioImportBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        // CastContext MUSS asynchron initialisiert werden, bevor irgendein Cast-UI-Element
        // (MediaRouteButton) genutzt wird – ein synchroner CastContext.getSharedInstance()-Aufruf
        // beim ersten Antippen des Cast-Symbols führte sonst zum Absturz (nicht initialisiert
        // = kein Play-Services-Check auf Main-Thread erlaubt). Gleiches Muster wie in der
        // Lethe-Haupt-App (MainActivity.kt).
        val castExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        runCatching {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this, castExecutor)
                .addOnSuccessListener { /* CastContext bereit */ }
                .addOnFailureListener { /* Cast nicht verfügbar (z.B. Google Play Services fehlt) */ }
        }

        setContent {
            LetheMediaPlayerTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Registrierung als Musik-Player: nimmt eine per "Öffnen mit" (ACTION_VIEW) oder
     * "Teilen" (ACTION_SEND) übergebene Audiodatei entgegen und reicht die URI an das
     * PlayerViewModel weiter (über die Bridge, da MainActivity kein ViewModel hält).
     */
    private fun handleIncomingIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        } ?: return
        importBridge.submit(uri)
    }
}
