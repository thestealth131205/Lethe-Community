package com.securechat.app

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Einfaches dateibasiertes Logging-System für Lethe.
 * Schreibt Logs in <filesDir>/lethe_logs.txt.
 * Rotiert (ältere Hälfte löschen) wenn die Datei 1 MB überschreitet.
 */
object LetheLogger {

    private const val LOG_FILE_NAME = "lethe_logs.txt"
    private const val MAX_SIZE_BYTES = 1L * 1024 * 1024 // 1 MB

    @Volatile
    private var logFile: File? = null

    /** Muss einmalig in Application.onCreate() aufgerufen werden. */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
    }

    fun i(tag: String, message: String) = write("INFO ", tag, message)
    fun d(tag: String, message: String) = write("DEBUG", tag, message)
    fun w(tag: String, message: String) = write("WARN ", tag, message)

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        write("ERROR", tag, full)
    }

    /** Schreibt einen Crash-Eintrag (wird vom CrashHandler genutzt). */
    fun crash(tag: String, message: String) = write("CRASH", tag, message)

    /** Gibt die Log-Datei zurück (null wenn Logger noch nicht initialisiert). */
    fun getLogFile(): File? = logFile

    /** Löscht den gesamten Log-Inhalt. */
    fun clear() {
        try {
            logFile?.writeText("")
        } catch (_: Exception) {}
    }

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun write(level: String, tag: String, message: String) {
        val file = logFile ?: return
        try {
            rotateIfNeeded(file)
            val timestamp = fmt.format(Date())
            val line = "[$timestamp][$level][$tag] $message\n"
            FileWriter(file, /* append = */ true).use { it.write(line) }
        } catch (_: Exception) {
            // Logging darf die App niemals zum Absturz bringen
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_SIZE_BYTES) return
        // Aktuelle Datei → lethe_logs.1.txt (ältere Backups werden gelöscht)
        val backup = File(file.parent, "lethe_logs.1.txt")
        backup.delete()
        file.renameTo(backup)
        // Neue leere Datei wird beim nächsten write() via FileWriter(append=true) angelegt
    }
}
