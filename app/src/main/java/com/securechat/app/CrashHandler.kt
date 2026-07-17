package com.securechat.app

import android.app.Application
import android.content.Intent

/**
 * Globaler Crash-Handler für Lethe.
 *
 * Registrierung in SecureChatApplication.onCreate():
 *   Thread.setDefaultUncaughtExceptionHandler(
 *       CrashHandler(this, Thread.getDefaultUncaughtExceptionHandler())
 *   )
 *
 * Sonderfall: Bekannter Compose-Framework-Bug (NPE in LegacyCursorAnchorInfoController)
 * wird nicht als Absturz behandelt – stattdessen wird die App transparent neu gestartet.
 *
 * Alle anderen unbehandelten Fehler werden mit dem Prefix [CRASH] in die lethe_logs.txt
 * geschrieben, bevor der Standard-Handler die App ordnungsgemäß beendet.
 */
class CrashHandler(
    private val application: Application,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Bekannter Compose-Bug: NPE in LegacyCursorAnchorInfoController beim Cursor-Positioning
        // (tritt beim Tippen in langen TextFields auf bestimmten Android-Versionen auf)
        val isComposeCursorNPE = throwable is NullPointerException &&
            throwable.stackTrace.any { it.className.contains("LegacyCursorAnchorInfoController") }

        if (isComposeCursorNPE) {
            try {
                LetheLogger.w("CRASH_HANDLER", "Compose Cursor NPE (bekannter Framework-Bug) – App wird transparent neu gestartet")
                val intent = application.packageManager.getLaunchIntentForPackage(application.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
            } catch (_: Exception) { }
            android.os.Process.killProcess(android.os.Process.myPid())
            return
        }

        try {
            val stackTrace = throwable.stackTraceToString()
            LetheLogger.crash(
                tag = "CRASH_HANDLER",
                message = "Unbehandelte Exception in Thread '${thread.name}':\n$stackTrace"
            )
        } catch (_: Exception) {
            // Fehler im Crash-Handler dürfen den normalen Shutdown nicht blockieren
        } finally {
            // Standard-Handler aufrufen → App schließt sich sauber
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
