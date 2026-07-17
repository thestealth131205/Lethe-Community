package com.lethe.mediaplayer.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Einfacher, dateibasierter App-Logger für die "App Infos"-Ansicht: schreibt normale
 * Log-Einträge ringgepuffert in `app_log.txt` und Absturz-Stacktraces zusätzlich in
 * `crash_log.txt`. Registriert dafür einen globalen Uncaught-Exception-Handler, der
 * den vorherigen (System-)Handler nach dem Protokollieren weiterhin aufruft.
 */
object AppLogger {
    private const val MAX_LINES = 500
    private const val LOG_FILE = "app_log.txt"
    private const val CRASH_FILE = "crash_log.txt"
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY)

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { appendCrash(thread.name, throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        appendLine("D/$tag: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val suffix = throwable?.let { " — ${it}" }.orEmpty()
        appendLine("E/$tag: $message$suffix")
    }

    fun readLog(): String = readFile(LOG_FILE)

    fun readCrashLog(): String = readFile(CRASH_FILE)

    fun clearLog() = logFile()?.writeText("")

    fun clearCrashLog() = crashFile()?.writeText("")

    private fun appendLine(line: String) {
        val file = logFile() ?: return
        runCatching {
            val timestamped = "${timeFormat.format(System.currentTimeMillis())}  $line"
            val existing = if (file.exists()) file.readLines() else emptyList()
            val updated = (existing + timestamped).takeLast(MAX_LINES)
            file.writeText(updated.joinToString("\n"))
        }
    }

    private fun appendCrash(threadName: String, throwable: Throwable) {
        val file = crashFile() ?: return
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val entry = buildString {
            append(timeFormat.format(System.currentTimeMillis()))
            append("  Thread: ")
            append(threadName)
            append('\n')
            append(sw.toString())
            append("\n----------------------------------------\n")
        }
        runCatching { file.appendText(entry) }
    }

    private fun readFile(name: String): String {
        val file = appContext?.let { File(it.filesDir, name) } ?: return ""
        return if (file.exists()) file.readText() else ""
    }

    private fun logFile(): File? = appContext?.let { File(it.filesDir, LOG_FILE) }

    private fun crashFile(): File? = appContext?.let { File(it.filesDir, CRASH_FILE) }
}
