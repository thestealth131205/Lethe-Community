package com.securechat.app

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

/**
 * Entscheidet EINHEITLICH an allen drei Empfangsstellen eines eingehenden Anrufs
 * (MainViewModel/WebSocket im Vordergrund, NotificationHandler/WebSocket im Hintergrund,
 * PushPayloadHandler/FCM), ob die kleine Heads-Up-Benachrichtigung oder die
 * Vollbild-Anruf-UI gezeigt wird - nie beide gleichzeitig.
 *
 * Regel: Ist der Bildschirm aus (Standby) oder der Sperrbildschirm aktiv, reicht die
 * kleine Benachrichtigung. Ist das Gerät dagegen gerade in Benutzung (Bildschirm an
 * und entsperrt), wird die Vollbild-Anruf-UI direkt gestartet statt nur eine
 * Heads-Up-Banner-Benachrichtigung zu posten.
 */
object CallDisplayPolicy {

    enum class Mode { NONE, SMALL, FULL_SCREEN }

    fun decide(context: Context, isAppForeground: Boolean): Mode {
        // App bereits offen mit eigener In-App-Anruf-UI (siehe _incomingCall-State) -
        // weder Notification noch separate Activity nötig.
        if (isAppForeground) return Mode.NONE

        val screenOn = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isInteractive == true
        val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.isKeyguardLocked == true

        return if (screenOn && !locked) Mode.FULL_SCREEN else Mode.SMALL
    }
}
