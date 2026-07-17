package com.securechat.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Hilfsfunktion für Android 14+ (API 34 / UPSIDE_DOWN_CAKE):
 * Prüft ob USE_FULL_SCREEN_INTENT gewährt ist – und leitet den Nutzer
 * bei fehlendem Recht exakt zu dem App-Schalter in den Systemeinstellungen.
 *
 * Hintergrund: Seit Android 14 entzieht das System (besonders Samsung OneUI)
 * das USE_FULL_SCREEN_INTENT-Recht still, falls es nicht aktiv erteilt wurde.
 * Ohne dieses Recht wird die Sperrbildschirm-Anruf-UI (IncomingCallActivity)
 * bei eingehenden Calls nicht gezeigt.
 *
 * Aufruf: checkAndRequestFullScreenPermission(context)
 * – Auf API < 34: No-op (Recht wird dort automatisch gewährt).
 * – Auf API >= 34: Öffnet Settings nur wenn canUseFullScreenIntent() == false.
 */
fun checkAndRequestFullScreenPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Vor Android 14 wird USE_FULL_SCREEN_INTENT automatisch gewährt – keine Aktion nötig.
        return
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (!nm.canUseFullScreenIntent()) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Gerätespezifische Einschränkungen (z. B. MIUI, Custom-ROM) können dazu führen,
            // dass diese Einstellungs-Action nicht existiert – wird still ignoriert.
        }
    }
}
