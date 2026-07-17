package com.lethe.mediaplayer.auth

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.lethe.mediaplayer.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis der Konto-Abfrage bei der Lethe-Haupt-App. */
sealed class SessionResult {
    /** Lethe ist installiert und eine gültige Session vorhanden. */
    data class Available(val token: String, val userId: String) : SessionResult()
    /** Lethe ist installiert, aber niemand ist angemeldet. */
    object NotLoggedIn : SessionResult()
    /** Die Lethe-Haupt-App ist nicht installiert (oder zu alt / keine Brücke). */
    object AppMissing : SessionResult()
}

/**
 * Liest JWT + userId aus der Lethe-Haupt-App über deren signaturgeschützten
 * [AuthBridgeProvider]. Funktioniert nur, weil der Player mit DEMSELBEN Keystore
 * signiert ist (Signature-Permission `com.Lethe.app.permission.AUTH_BRIDGE`).
 */
@Singleton
class SessionBridge @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SessionBridge"
        private const val LETHE_PACKAGE = "com.Lethe.app"
        private val SESSION_URI: Uri = Uri.parse("content://com.Lethe.app.authbridge/session")
        private const val COL_TOKEN = "token"
        private const val COL_USER_ID = "user_id"
    }

    /** Zuletzt erfolgreich gelesenes Token (für den Auth-Interceptor). */
    @Volatile
    var cachedToken: String? = null
        private set

    private fun isLetheInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(LETHE_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun load(): SessionResult {
        // Provider muss auflösbar sein – sonst App fehlt oder zu alt
        val provider = context.packageManager.resolveContentProvider("com.Lethe.app.authbridge", 0)
        if (provider == null) {
            return if (isLetheInstalled()) SessionResult.NotLoggedIn else SessionResult.AppMissing
        }
        return try {
            context.contentResolver.query(SESSION_URI, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val token = c.getString(c.getColumnIndexOrThrow(COL_TOKEN)).orEmpty()
                    val userId = c.getString(c.getColumnIndexOrThrow(COL_USER_ID)).orEmpty()
                    if (token.isNotBlank()) {
                        cachedToken = token
                        SessionResult.Available(token, userId)
                    } else {
                        cachedToken = null
                        SessionResult.NotLoggedIn
                    }
                } else SessionResult.NotLoggedIn
            } ?: SessionResult.NotLoggedIn
        } catch (e: SecurityException) {
            // Signatur passt nicht – sollte im Release nie auftreten
            AppLogger.e(TAG, "Keine Berechtigung für Konto-Brücke (Signatur?): ${e.message}", e)
            SessionResult.AppMissing
        } catch (e: Exception) {
            AppLogger.e(TAG, "Konto-Abfrage fehlgeschlagen: ${e.message}", e)
            SessionResult.NotLoggedIn
        }
    }
}
