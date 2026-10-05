package com.securechat.app

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build

/**
 * Prozessweiter, einziger Klingelton-Player für eingehende Anrufe. Wird sowohl vom
 * Vordergrund-Pfad (MainViewModel, über laufende WebSocket-Verbindung) als auch von
 * den beiden Hintergrund-Pfaden (NotificationHandler, PushPayloadHandler) genutzt,
 * wenn CallDisplayPolicy sich für die Vollbild-Variante entscheidet - dort wird bewusst
 * KEINE Notification gepostet (sonst erscheinen Vollbild + Heads-Up gleichzeitig),
 * weshalb der sonst an die Notification gekoppelte Channel-Sound ausbleibt.
 */
object CallRingtonePlayer {

    @Volatile
    private var ringtone: Ringtone? = null

    @Synchronized
    fun start(context: Context) {
        if (ringtone?.isPlaying == true) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.also {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.isLooping = true
                }
                it.play()
            }
        } catch (e: Exception) {
            android.util.Log.w("CallRingtonePlayer", "Ringtone start fehlgeschlagen", e)
        }
    }

    @Synchronized
    fun stop() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
    }
}
