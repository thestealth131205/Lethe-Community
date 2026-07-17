package com.securechat.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Signaturgeschützte Konto-Brücke für die eigenständige Companion-App
 * „Lethe Medie Player" (applicationId com.Lethe.mediaplayer).
 *
 * Der Player-Prozess kann nur dann das JWT + die userId lesen, wenn er mit
 * DEMSELBEN Keystore signiert wurde wie diese App – erzwungen über die
 * Signature-Permission `com.Lethe.app.permission.AUTH_BRIDGE` (siehe Manifest).
 * Fremd-Apps ohne identische Signatur erhalten SecurityException.
 *
 * Der Provider spiegelt bewusst die Lese-Logik aus [com.securechat.app.data.network.TokenManager]
 * (verschlüsselte Prefs mit Fallback), damit exakt dieselbe Session gelesen wird.
 * Es wird nur GELESEN – kein Schreib-/Update-/Delete-Pfad ist implementiert.
 */
class AuthBridgeProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.Lethe.app.authbridge"
        private const val PATH_SESSION = "session"

        // Muss mit TokenManager übereinstimmen
        private const val PREFS_ENC = "secure_chat_auth_enc"
        private const val PREFS_FALLBACK = "secure_chat_auth_fallback"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"

        const val COL_TOKEN = "token"
        const val COL_USER_ID = "user_id"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (uri.lastPathSegment != PATH_SESSION) return null
        val ctx = context ?: return null

        val prefs = openPrefs(ctx)
        val token = prefs?.getString(KEY_AUTH_TOKEN, null) ?: ""
        val userId = prefs?.getString(KEY_USER_ID, null) ?: ""

        val cursor = MatrixCursor(arrayOf(COL_TOKEN, COL_USER_ID))
        cursor.addRow(arrayOf(token, userId))
        return cursor
    }

    private fun openPrefs(ctx: Context): SharedPreferences? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_ENC,
                masterKeyAlias,
                ctx,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("AuthBridgeProvider", "Enc-Prefs nicht verfügbar, nutze Fallback: ${e.message}")
            ctx.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY.session"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
